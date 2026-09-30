import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    // Firebase is OPTIONAL / bring-your-own-project: the Google Services plugin is only
    // applied when a google-services.json is present for the current applicationId.
    // Without it the app builds and runs fully local (no sync) — a rider who doesn't
    // want multi-device sync just omits the file. To enable sync, drop your own
    // Firebase project's google-services.json in app/.
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
}

val localApplicationId = "com.vinalayan.app"
val playApplicationId = "com.vinalayan.app"
val mapboxTestApplicationId = "com.vinalayan.mapboxtest"
val debugApplicationIdSuffix = ".mui3"
val googleServicesFile = project.file("google-services.json")
val firebaseClientPackages = if (googleServicesFile.exists()) {
    """"package_name"\s*:\s*"([^"]+)"""".toRegex()
        .findAll(googleServicesFile.readText())
        .map { it.groupValues[1] }
        .toSet()
} else {
    emptySet()
}
val requestedTasks = gradle.startParameter.taskNames
val firebaseFlavorNames = listOf("local", "play", "mapboxTest")
val requestedTaskMentionsFirebaseFlavor = requestedTasks.any { task ->
    firebaseFlavorNames.any { flavor -> task.contains(flavor, ignoreCase = true) }
}
val requestedFirebasePackageCandidates = buildSet {
    fun addFlavor(flavorName: String, applicationId: String) {
        val mentionsFlavor = requestedTasks.isEmpty() ||
            !requestedTaskMentionsFirebaseFlavor ||
            requestedTasks.any { it.contains(flavorName, ignoreCase = true) }
        if (!mentionsFlavor) return
        val debugRequested = requestedTasks.isEmpty() || requestedTasks.any { it.contains("debug", ignoreCase = true) }
        val releaseRequested = requestedTasks.isEmpty() || requestedTasks.any {
            it.contains("release", ignoreCase = true) || it.contains("bundle", ignoreCase = true)
        }
        if (debugRequested) add(applicationId + debugApplicationIdSuffix)
        if (releaseRequested) add(applicationId)
        add(applicationId)
    }
    addFlavor("local", localApplicationId)
    addFlavor("play", playApplicationId)
    addFlavor("mapboxTest", mapboxTestApplicationId)
}
val hasFirebaseConfig = requestedFirebasePackageCandidates.any { it in firebaseClientPackages }
fun firebaseConfigIncludes(applicationId: String): Boolean =
    applicationId in firebaseClientPackages || applicationId + debugApplicationIdSuffix in firebaseClientPackages

if (hasFirebaseConfig) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}

val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}
val googleWebClientId = providers.gradleProperty("GOOGLE_WEB_CLIENT_ID").orNull
    ?: localProperties.getProperty("GOOGLE_WEB_CLIENT_ID").orEmpty()
val useMapboxNavigation =
    (providers.gradleProperty("USE_MAPBOX_NAVIGATION").orNull
        ?: localProperties.getProperty("USE_MAPBOX_NAVIGATION")
        ?: providers.gradleProperty("USE_MAPBOX_NAVIGATION_EXPERIMENTAL").orNull
        ?: localProperties.getProperty("USE_MAPBOX_NAVIGATION_EXPERIMENTAL")
        ?: "false").toBoolean()
val mapboxAccessToken = providers.gradleProperty("MAPBOX_ACCESS_TOKEN").orNull
    ?: localProperties.getProperty("MAPBOX_ACCESS_TOKEN").orEmpty()
val buildingBundle = gradle.startParameter.taskNames.any {
    it.contains("bundle", ignoreCase = true)
}

val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("key.properties")

if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

android {
    namespace = "com.example.opendash"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = localApplicationId
        minSdk = 24
        targetSdk = 36
        versionCode = providers.gradleProperty("VINALAYAN_VERSION_CODE").orNull?.toInt() ?: 1
        versionName = "0.1.5-preview"

        buildConfigField(
            "String",
            "GOOGLE_WEB_CLIENT_ID",
            "\"${googleWebClientId.replace("\\", "\\\\").replace("\"", "\\\"")}\"",
        )
        buildConfigField("boolean", "CRASHLYTICS_ENABLED", "false")
        buildConfigField("boolean", "USE_MAPBOX_NAVIGATION", useMapboxNavigation.toString())
        buildConfigField("boolean", "USE_MAPBOX_NAVIGATION_EXPERIMENTAL", useMapboxNavigation.toString())
        buildConfigField("boolean", "LAB_MODE", "false")
        buildConfigField(
            "String",
            "MAPBOX_ACCESS_TOKEN",
            "\"${mapboxAccessToken.replace("\\", "\\\\").replace("\"", "\\\"")}\"",
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("local") {
            dimension = "distribution"
            applicationId = localApplicationId
            buildConfigField("boolean", "CRASHLYTICS_ENABLED", "false")
        }
        create("play") {
            dimension = "distribution"
            applicationId = playApplicationId
            buildConfigField(
                "boolean",
                "CRASHLYTICS_ENABLED",
                firebaseConfigIncludes(playApplicationId).toString(),
            )
            buildConfigField("boolean", "USE_MAPBOX_NAVIGATION", "true")
            buildConfigField("boolean", "USE_MAPBOX_NAVIGATION_EXPERIMENTAL", "true")
        }
        create("mapboxTest") {
            dimension = "distribution"
            applicationId = mapboxTestApplicationId
            buildConfigField("boolean", "CRASHLYTICS_ENABLED", firebaseConfigIncludes(mapboxTestApplicationId).toString())
            buildConfigField("boolean", "USE_MAPBOX_NAVIGATION", "true")
            buildConfigField("boolean", "USE_MAPBOX_NAVIGATION_EXPERIMENTAL", "true")
        }
        create("lab") {
            dimension = "distribution"
            applicationId = "com.vinalayan.lab"
            versionCode = 2
            versionName = "0.1.5-preview-lab"
            buildConfigField("boolean", "LAB_MODE", "true")
            buildConfigField("boolean", "CRASHLYTICS_ENABLED", "false")
            resValue("string", "app_name", "Vinalayan Lab")
        }
    }

    val releaseStoreFilePath = providers.gradleProperty("OPENDASH_RELEASE_STORE_FILE").orNull
        ?: localProperties.getProperty("OPENDASH_RELEASE_STORE_FILE")
        ?: keystoreProperties.getProperty("storeFile")
    val releaseStorePassword = providers.gradleProperty("OPENDASH_RELEASE_STORE_PASSWORD").orNull
        ?: localProperties.getProperty("OPENDASH_RELEASE_STORE_PASSWORD")
        ?: keystoreProperties.getProperty("storePassword")
    val releaseKeyAlias = providers.gradleProperty("OPENDASH_RELEASE_KEY_ALIAS").orNull
        ?: localProperties.getProperty("OPENDASH_RELEASE_KEY_ALIAS")
        ?: keystoreProperties.getProperty("keyAlias")
    val releaseKeyPassword = providers.gradleProperty("OPENDASH_RELEASE_KEY_PASSWORD").orNull
        ?: localProperties.getProperty("OPENDASH_RELEASE_KEY_PASSWORD")
        ?: keystoreProperties.getProperty("keyPassword")
    val hasReleaseKeystore =
        !releaseStoreFilePath.isNullOrBlank() &&
            !releaseStorePassword.isNullOrBlank() &&
            !releaseKeyAlias.isNullOrBlank() &&
            !releaseKeyPassword.isNullOrBlank()

    signingConfigs {
        create("release") {
            if (hasReleaseKeystore) {
                storeFile = file(releaseStoreFilePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
        create("labSideload") {
            val labStore = rootProject.file("lab-signing/lab.p12")
            if (labStore.isFile) {
                storeFile = labStore
                storePassword = "bancada-lab"
                keyAlias = "bancada"
                keyPassword = "bancada-lab"
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".mui3"
            resValue("string", "app_name", "Vinalayan")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Release builds must never use the debug signing config. Local developers and CI
            // should provide their own release keystore through Gradle properties or CI secrets:
            // OPENDASH_RELEASE_STORE_FILE, OPENDASH_RELEASE_STORE_PASSWORD,
            // OPENDASH_RELEASE_KEY_ALIAS, and OPENDASH_RELEASE_KEY_PASSWORD.
            // When absent, Gradle produces an unsigned release artifact instead of falling
            // back to debug signing.
            if (hasReleaseKeystore) signingConfig = signingConfigs.getByName("release")
        }
    }
    productFlavors.getByName("lab").signingConfig = signingConfigs.getByName("labSideload")
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        buildConfig = true
        compose = true
        resValues = true
    }

    // GitHub APK releases can publish the matching ABI APK (arm64 for most current phones)
    // instead of forcing every device to download all four MapLibre native libraries.
    // Android App Bundles must not build multiple split APK artifacts during bundle tasks.
    splits {
        abi {
            isEnable = !buildingBundle
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }
}

androidComponents {
    // AGP 9 only enables debug unit tests by default; CI validates the release variant.
    beforeVariants(selector().withBuildType("release")) { variant ->
        variant.hostTests.getValue(com.android.build.api.variant.HostTestBuilder.UNIT_TEST_TYPE).enable = true
    }
    // Lab is a sideload build. R8 stays on for local/play/mapboxTest release.
    // Turning it off here keeps the motorcycle flavors unchanged and lets this
    // 4 GB environment (and CI) package the lab APK without the production shrink.
    beforeVariants(selector().withFlavor("distribution" to "lab")) { variant ->
        variant.isMinifyEnabled = false
        variant.shrinkResources = false
    }
    onVariants { variant ->
        val variantApplicationId = variant.applicationId.get()
        if (hasFirebaseConfig && variantApplicationId !in firebaseClientPackages) {
            val taskVariantName = variant.name.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase() else it.toString()
            }
            tasks.matching { it.name == "process${taskVariantName}GoogleServices" }
                .configureEach { enabled = false }
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.ui.text.google.fonts)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.crashlytics.ndk)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.androidx.security.crypto)
    implementation(libs.google.identity.googleid)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.maplibre)
    implementation(libs.maplibre.annotation)
    testImplementation(libs.junit)
    testImplementation("org.json:json:20250517")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

