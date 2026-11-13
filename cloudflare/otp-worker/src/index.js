const JSON_HEADERS = { "content-type": "application/json; charset=utf-8" };
const OTP_TTL_SECONDS = 10 * 60;
const RESEND_COOLDOWN_SECONDS = 60;
const MAX_ATTEMPTS = 5;

function response(body, status = 200, extra = {}) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...JSON_HEADERS, "cache-control": "no-store", ...corsHeaders(), ...extra }
  });
}

function corsHeaders() {
  return {
    "access-control-allow-origin": "*",
    "access-control-allow-methods": "POST, OPTIONS",
    "access-control-allow-headers": "authorization, content-type",
    "access-control-max-age": "86400"
  };
}

function b64url(value) {
  const bytes = typeof value === "string" ? new TextEncoder().encode(value) : value;
  let binary = "";
  for (const b of bytes) binary += String.fromCharCode(b);
  return btoa(binary).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
}

function pemToBytes(pem) {
  const body = pem.replace(/-----BEGIN PRIVATE KEY-----/g, "").replace(/-----END PRIVATE KEY-----/g, "").replace(/\s/g, "");
  const binary = atob(body);
  return Uint8Array.from(binary, c => c.charCodeAt(0));
}

async function getGoogleAccessToken(env) {
  const account = JSON.parse(env.FIREBASE_SERVICE_ACCOUNT_JSON);
  const now = Math.floor(Date.now() / 1000);
  const header = b64url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claims = b64url(JSON.stringify({
    iss: account.client_email,
    scope: "https://www.googleapis.com/auth/identitytoolkit",
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + (55 * 60)
  }));
  const unsigned = header + "." + claims;
  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemToBytes(account.private_key),
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"]
  );
  const signature = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(unsigned));
  const assertion = unsigned + "." + b64url(new Uint8Array(signature));
  const tokenResponse = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion
    })
  });
  const tokenData = await tokenResponse.json();
  if (!tokenResponse.ok || !tokenData.access_token) throw new Error("Firebase admin authentication failed");
  return tokenData.access_token;
}

async function authenticatedUser(request, env) {
  const auth = request.headers.get("authorization") || "";
  if (!auth.startsWith("Bearer ")) return null;
  const idToken = auth.slice(7).trim();
  if (!idToken) return null;
  const lookup = await fetch(
    "https://identitytoolkit.googleapis.com/v1/accounts:lookup?key=" + encodeURIComponent(env.FIREBASE_API_KEY),
    {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ idToken })
    }
  );
  if (!lookup.ok) return null;
  const data = await lookup.json();
  const user = data.users?.[0];
  if (!user?.localId || !user.email) return null;
  return {
    uid: user.localId,
    email: String(user.email).trim().toLowerCase(),
    emailVerified: user.emailVerified === true
  };
}

async function codeDigest(code, uid, secret) {
  const key = await crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"]
  );
  const bytes = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(uid + ":" + code));
  return Array.from(new Uint8Array(bytes), b => b.toString(16).padStart(2, "0")).join("");
}

function randomCode() {
  const bytes = crypto.getRandomValues(new Uint32Array(1));
  return String(bytes[0] % 1000000).padStart(6, "0");
}

async function sendOtp(request, env) {
  const user = await authenticatedUser(request, env);
  if (!user) return response({ error: "unauthorized" }, 401);
  if (user.emailVerified) return response({ status: "already_verified" });

  // A verified custom Resend sender enables delivery to every user's email.
  // Without it, keep the shared onboarding sender restricted to the test mailbox.
  const configuredSender = String(env.RESEND_FROM_EMAIL || "").trim();
  const testRecipient = String(env.TEST_RECIPIENT_EMAIL || "").trim().toLowerCase();
  if (!configuredSender && (!testRecipient || user.email !== testRecipient)) {
    return response({
      error: "sending_domain_not_configured",
      message: "A verified custom sending domain must be configured before sending to new users."
    }, 503);
  }
  const fromAddress = configuredSender || "Mawashi <onboarding@resend.dev>";

  const now = Math.floor(Date.now() / 1000);
  const existing = await env.OTP_DB.prepare(
    "SELECT last_sent_at FROM email_otps WHERE uid = ?"
  ).bind(user.uid).first();
  if (existing && now - existing.last_sent_at < RESEND_COOLDOWN_SECONDS) {
    return response({ error: "too_many_requests", retry_after: RESEND_COOLDOWN_SECONDS - (now - existing.last_sent_at) }, 429);
  }

  const code = randomCode();
  const digest = await codeDigest(code, user.uid, env.OTP_HMAC_SECRET);
  const expiresAt = now + OTP_TTL_SECONDS;
  await env.OTP_DB.prepare(
    "INSERT INTO email_otps (uid, email, code_hash, expires_at, attempts, last_sent_at, created_at) VALUES (?, ?, ?, ?, 0, ?, ?) ON CONFLICT(uid) DO UPDATE SET email=excluded.email, code_hash=excluded.code_hash, expires_at=excluded.expires_at, attempts=0, last_sent_at=excluded.last_sent_at, created_at=excluded.created_at"
  ).bind(user.uid, user.email, digest, expiresAt, now, now).run();

  const mail = await fetch("https://api.resend.com/emails", {
    method: "POST",
    headers: {
      "authorization": "Bearer " + env.RESEND_API_KEY,
      "content-type": "application/json"
    },
    body: JSON.stringify({
      from: fromAddress,
      to: [user.email],
      subject: "رمز التحقق من حساب مواشي",
      html: `<div dir="rtl" style="font-family:Arial,sans-serif"><h2>مرحباً بك في مواشي</h2><p>رمز التحقق الخاص بك:</p><p style="font-size:32px;font-weight:bold;letter-spacing:8px">${code}</p><p>تنتهي صلاحية الرمز خلال 10 دقائق. لا تشارك هذا الرمز مع أي شخص.</p></div>`
    })
  });
  if (!mail.ok) {
    const providerError = await mail.text().catch(() => "");
    console.error("Resend email delivery failed", JSON.stringify({
      status: mail.status,
      recipient: user.email,
      response: providerError.slice(0, 500)
    }));
    await env.OTP_DB.prepare("DELETE FROM email_otps WHERE uid = ?").bind(user.uid).run();
    return response({ error: "email_delivery_failed", provider_status: mail.status }, 502);
  }
  const mailResult = await mail.json().catch(() => ({}));
  console.log("Resend email accepted", JSON.stringify({
    recipient: user.email,
    email_id: mailResult.id || null
  }));
  return response({ status: "sent", expires_in: OTP_TTL_SECONDS, resend_after: RESEND_COOLDOWN_SECONDS });
}

async function verifyOtp(request, env) {
  const user = await authenticatedUser(request, env);
  if (!user) return response({ error: "unauthorized" }, 401);
  if (user.emailVerified) return response({ status: "already_verified" });

  let body;
  try { body = await request.json(); } catch { return response({ error: "invalid_json" }, 400); }
  const code = String(body.code || "").trim();
  if (!/^\d{6}$/.test(code)) return response({ error: "invalid_code_format" }, 400);

  const row = await env.OTP_DB.prepare(
    "SELECT code_hash, expires_at, attempts FROM email_otps WHERE uid = ? AND email = ?"
  ).bind(user.uid, user.email).first();
  if (!row) return response({ error: "code_not_found" }, 404);
  const now = Math.floor(Date.now() / 1000);
  if (now > row.expires_at) {
    await env.OTP_DB.prepare("DELETE FROM email_otps WHERE uid = ?").bind(user.uid).run();
    return response({ error: "code_expired" }, 410);
  }
  if (row.attempts >= MAX_ATTEMPTS) {
    await env.OTP_DB.prepare("DELETE FROM email_otps WHERE uid = ?").bind(user.uid).run();
    return response({ error: "too_many_attempts" }, 429);
  }

  const digest = await codeDigest(code, user.uid, env.OTP_HMAC_SECRET);
  if (digest !== row.code_hash) {
    await env.OTP_DB.prepare("UPDATE email_otps SET attempts = attempts + 1 WHERE uid = ?").bind(user.uid).run();
    return response({ error: "incorrect_code", attempts_remaining: MAX_ATTEMPTS - row.attempts - 1 }, 400);
  }

  const adminToken = await getGoogleAccessToken(env);
  const account = JSON.parse(env.FIREBASE_SERVICE_ACCOUNT_JSON);
  const update = await fetch(
    "https://identitytoolkit.googleapis.com/v1/projects/" + encodeURIComponent(account.project_id) + "/accounts:update",
    {
      method: "POST",
      headers: {
        "authorization": "Bearer " + adminToken,
        "content-type": "application/json"
      },
      body: JSON.stringify({ localId: user.uid, emailVerified: true, returnSecureToken: false })
    }
  );
  if (!update.ok) return response({ error: "firebase_verification_update_failed" }, 502);

  await env.OTP_DB.prepare("DELETE FROM email_otps WHERE uid = ?").bind(user.uid).run();
  return response({ status: "verified" });
}

export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: corsHeaders() });
    const url = new URL(request.url);
    if (request.method === "GET" && url.pathname === "/health") {
      return response({ service: "mawashi-otp-api", status: "ok" });
    }
    if (request.method !== "POST") return response({ error: "method_not_allowed" }, 405);
    try {
      if (url.pathname === "/v1/otp/send") return await sendOtp(request, env);
      if (url.pathname === "/v1/otp/verify") return await verifyOtp(request, env);
      return response({ error: "not_found" }, 404);
    } catch {
      return response({ error: "internal_error" }, 500);
    }
  }
};
