plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.cactusbyte.scouttrace"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.cactusbyte.scouttrace"
        minSdk = 26
        targetSdk = 35
        versionCode = 20000
        versionName = "2.0.0"
    }
}
