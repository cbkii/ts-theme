// Optional N3 display host. A separate APK, never bundled with or required by the launcher, and
// inert unless built with an explicit caller identity and component allowlist. Deployment as a
// privileged app (for ACTIVITY_EMBEDDING) is a separate, owner-approved step outside this build.
plugins {
    id("com.android.application")
}

fun prop(name: String): String = (findProperty(name) as String?)?.trim().orEmpty()

android {
    namespace = "com.cbkii.ts18launcher.n3host"
    compileSdk = 29

    defaultConfig {
        applicationId = "com.cbkii.ts18launcher.n3host"
        minSdk = 29
        targetSdk = 29
        versionCode = 1
        versionName = "0.1.0-experimental"
        // All three must be supplied for the host to admit anything; the default admits nothing.
        buildConfigField("boolean", "HOST_ENABLED", (prop("n3host.enabled") == "true").toString())
        buildConfigField("String", "CALLER_PACKAGE", "\"" + prop("n3host.callerPackage").ifEmpty { "com.cbkii.ts18launcher" } + "\"")
        buildConfigField("String", "CALLER_CERT_SHA256", "\"" + prop("n3host.callerCertSha256") + "\"")
        buildConfigField("String", "COMPONENTS", "\"" + prop("n3host.components") + "\"")
    }

    sourceSets.getByName("main").java.srcDir("../shared/n3/java")
    sourceSets.getByName("main").aidl.srcDir("../shared/n3/aidl")

    buildFeatures {
        aidl = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
