plugins {
    id("com.android.application")
}

val versionCodeAuthority = providers.gradleProperty("VERSION_CODE").get().toInt()
val versionNameAuthority = providers.gradleProperty("VERSION_NAME").get()

val signingValues = mapOf(
    "TS_THEME_KEYSTORE_FILE" to providers.environmentVariable("TS_THEME_KEYSTORE_FILE").orNull,
    "KEYSTORE_PASSWORD" to providers.environmentVariable("KEYSTORE_PASSWORD").orNull,
    "KEY_ALIAS" to providers.environmentVariable("KEY_ALIAS").orNull,
    "KEY_PASSWORD" to providers.environmentVariable("KEY_PASSWORD").orNull,
)
val releaseSigningReady = signingValues.values.all { !it.isNullOrBlank() }

android {
    namespace = "com.cbkii.ts18launcher"
    compileSdk = 29

    defaultConfig {
        applicationId = "com.cbkii.ts18launcher"
        minSdk = 29
        targetSdk = 29
        versionCode = versionCodeAuthority
        versionName = versionNameAuthority
    }

    signingConfigs {
        if (releaseSigningReady) {
            create("release") {
                storeFile = rootProject.file(signingValues.getValue("TS_THEME_KEYSTORE_FILE")!!)
                storePassword = signingValues.getValue("KEYSTORE_PASSWORD")
                keyAlias = signingValues.getValue("KEY_ALIAS")
                keyPassword = signingValues.getValue("KEY_PASSWORD")
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        debug {
            isDebuggable = true
        }
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (releaseSigningReady) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        buildConfig = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    lint {
        abortOnError = true
        checkDependencies = false
        checkReleaseBuilds = true
        lintConfig = rootProject.file("lint.xml")
        warningsAsErrors = true
    }
}

val fetchLeafletAssets = tasks.register<Exec>("fetchLeafletAssets") {
    group = "build setup"
    description = "Fetches and SHA-256 verifies the pinned Leaflet 1.9.4 runtime assets."
    commandLine("python3", rootProject.file("tools/fetch_leaflet.py").absolutePath)
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(fetchLeafletAssets)
}

val verifyReleaseSigningEnvironment = tasks.register("verifyReleaseSigningEnvironment") {
    group = "verification"
    description = "Fails closed when a standalone launcher release lacks signing inputs."
    val missingSigningInputs = signingValues.filterValues { it.isNullOrBlank() }.keys.sorted()
    val keystoreFile = signingValues["TS_THEME_KEYSTORE_FILE"]
        ?.takeIf { it.isNotBlank() }
        ?.let { rootProject.file(it) }

    doLast {
        require(missingSigningInputs.isEmpty()) {
            "Release signing environment is incomplete; missing: ${missingSigningInputs.joinToString(", ")}"
        }
        require(keystoreFile?.isFile == true) {
            "TS_THEME_KEYSTORE_FILE does not identify a regular file"
        }
    }
}

tasks.matching { it.name == "assembleRelease" }.configureEach {
    dependsOn(verifyReleaseSigningEnvironment)
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
