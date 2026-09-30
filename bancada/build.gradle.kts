plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val keystorePassword = "bancada-lab"
val keystoreFile = rootProject.file("lab-signing/lab.p12")

android {
    namespace = "com.vinalayan.bancada"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }
    defaultConfig {
        applicationId = "com.vinalayan.bancada"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = "0.1.1-bancada"
    }
    signingConfigs {
        create("labSideload") {
            if (keystoreFile.isFile) {
                storeFile = keystoreFile
                storePassword = keystorePassword
                keyAlias = "bancada"
                keyPassword = keystorePassword
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystoreFile.isFile) {
                signingConfig = signingConfigs.getByName("labSideload")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    lint {
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(project(":dash-protocol"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
