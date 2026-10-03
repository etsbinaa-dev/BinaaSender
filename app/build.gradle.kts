plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.binaa.sender"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.binaa.sender"
        minSdk = 26
        targetSdk = 34
        // يزيد تلقائيًا مع كل تشغيل CI؛ البناء المحلي = 1
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "0.2"

        // الخادم الوحيد المسموح تنزيل صور الفواتير منه (Supabase الخاص بـ binaa-center-hub)
        buildConfigField("String", "ALLOWED_HOST", "\"qtlsyaqfmrxnhmnxfzdo.supabase.co\"")
        buildConfigField("String", "ALLOWED_PATH_PREFIX", "\"/storage/v1/object/sign/invoice-files/\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
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

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
}
