# مواشي | Mawashi

تطبيق أندرويد مستقل لإدارة المواشي والمزرعة، بواجهة عربية واتجاه RTL.

## الأقسام الأولية
- القطيع
- الأدوية والتطعيمات
- مخزون العلف
- المالية

## التقنية
- Kotlin و Jetpack Compose و Material 3
- Android SDK 35 و JDK 17
- هوية تطبيق مستقلة: `dz.mawashi.app` (Debug: `dz.mawashi.app.debug`)

## البناء والاختبار
```bash
gradle --no-daemon :app:testDebugUnitTest
gradle --no-daemon :app:assembleDebug
```

قاعدة البيانات المحلية والنسخ الاحتياطي سيضافان في مرحلة البيانات التالية بعد تثبيت أساس الواجهة والبناء.
المستودع القديم Murabbi-Alghanm مستقل ولم يتم تعديله.
