package dz.mawashi.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectIdentityTest {
    @Test fun debugApplicationIdentityMatchesFirebasePackage() {
        assertEquals("dz.mawashi.app", BuildConfig.APPLICATION_ID)
    }
}
