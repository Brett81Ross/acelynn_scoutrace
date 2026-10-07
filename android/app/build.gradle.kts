plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

android {
    namespace = "com.cactusbyte.scouttrace"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cactusbyte.scouttrace"
        minSdk = 26
        targetSdk = 35
        versionCode = 20100
        versionName = "2.1.0"
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("play") {
            dimension = "distribution"
        }
        create("internal") {
            dimension = "distribution"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
