plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.alawi.mawaqit"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.alawi.mawaqit"
        minSdk = 26
        targetSdk = 34
        // رقم الإصدار يزيد تلقائياً مع كل بناء على GitHub، عشان التحديث يتثبت فوق القديم
        val run = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = run
        versionName = "1.0.$run"
    }

    // مفتاح توقيع ثابت حتى تقدر تحدّث التطبيق بدون ما تحذفه
    signingConfigs {
        create("shared") {
            storeFile = file("mawaqit.keystore")
            storePassword = "mawaqit123"
            keyAlias = "mawaqit"
            keyPassword = "mawaqit123"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("shared")
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    androidResources {
        noCompress += listOf("ogg")
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
}
