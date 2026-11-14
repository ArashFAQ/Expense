plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
val repo = System.getenv("GITHUB_REPOSITORY") ?: ""

android {
    namespace = "com.arash.makharej"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.arash.makharej"
        minSdk = 26
        targetSdk = 34
        versionCode = buildNumber
        versionName = "0.$buildNumber"
        buildConfigField("String", "REPO", "\"$repo\"")
    }

    signingConfigs {
        create("release") {
            storeFile = file("release.jks")
            val storePw = System.getenv("KEYSTORE_PASSWORD")?.trim()
            storePassword = storePw
            keyAlias = System.getenv("KEY_ALIAS")?.trim().takeUnless { it.isNullOrEmpty() } ?: "makharej"
            keyPassword = System.getenv("KEY_PASSWORD")?.trim().takeUnless { it.isNullOrEmpty() } ?: storePw
        }
    }

    buildTypes {
        release {
            // حذف کدهای استفاده‌نشده (مخصوصا آیکون‌ها) برای کم شدن حجم
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
}
