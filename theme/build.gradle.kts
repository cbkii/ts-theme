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
    namespace = "launcher.variety.theme.plugin"
    compileSdk = 29

    defaultConfig {
        applicationId = "launcher.variety.theme.plugin.cbk_black"
        minSdk = 16
        targetSdk = 26
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
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isDebuggable = false
            isMinifyEnabled = false
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
        checkDependencies = true
        checkReleaseBuilds = true
        lintConfig = rootProject.file("lint.xml")
        warningsAsErrors = true
    }

    packaging {
        resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*")
    }
}

val verifyReleaseSigningEnvironment = tasks.register("verifyReleaseSigningEnvironment") {
    group = "verification"
    description = "Fails closed when a release build lacks one of the four signing inputs."
    doLast {
        val missing = signingValues.filterValues { it.isNullOrBlank() }.keys.sorted()
        require(missing.isEmpty()) {
            "Release signing environment is incomplete; missing: ${missing.joinToString(", ")}"
        }
        require(rootProject.file(signingValues.getValue("TS_THEME_KEYSTORE_FILE")!!).isFile) {
            "TS_THEME_KEYSTORE_FILE does not identify a regular file"
        }
    }
}

tasks.matching { it.name == "assembleRelease" }.configureEach {
    dependsOn(verifyReleaseSigningEnvironment)
}

dependencies {
    implementation(files(rootProject.file("build/dependencies/replugin-plugin-lib-2.3.4.aar")))
}
