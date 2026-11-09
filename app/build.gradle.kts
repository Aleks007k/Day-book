plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.daybook.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.daybook.app"
        // Android 13+: нужны длинный сеанс распознавания и автопунктуация,
        // которые появились именно в этой версии.
        minSdk = 33
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    // Подпись релиза — так же, как в калькуляторе, но СВОИМ ключом.
    // Ключ лежит в signing/release.p12 и зашифрован паролем из секрета GitHub
    // SIGNING_PASSWORD. Без пароля сборка подписывается отладочным ключом
    // и в Releases не попадает.
    val keystoreFile = rootProject.file("signing/release.p12")
    val signingPassword: String? = System.getenv("SIGNING_PASSWORD")
    val hasRealSigning = keystoreFile.exists() && !signingPassword.isNullOrBlank()

    signingConfigs {
        if (hasRealSigning) {
            create("release") {
                storeFile = keystoreFile
                storePassword = signingPassword
                keyAlias = "daybook"
                keyPassword = signingPassword
                storeType = "PKCS12"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (hasRealSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
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
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.foundation)

    testImplementation(libs.junit)
}
