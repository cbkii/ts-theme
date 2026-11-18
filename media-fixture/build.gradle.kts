plugins { id("com.android.application") }

// Instrumentation companion only. Never a launcher dependency or distributable product.
android {
    namespace = "com.cbkii.ts18mediafixture"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.cbkii.ts18mediafixture"
        minSdk = 29
        targetSdk = 29
        versionCode = 1
        versionName = "ipc-fixture"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}
dependencies {
    implementation("androidx.media3:media3-session:1.3.1")
    implementation("androidx.media3:media3-exoplayer:1.3.1")
}
