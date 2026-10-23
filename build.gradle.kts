plugins {
    id("com.android.application") version "8.7.3"
    id("org.jetbrains.kotlin.android") version "2.0.21"
}
android {
    namespace = "com.roohu.roohux"
    compileSdk = 35
    defaultConfig { applicationId = "com.roohu.roohux"; minSdk = 23; targetSdk = 35; versionCode = 20; versionName = "2.0.0" }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    sourceSets.getByName("main") {
        manifest.srcFile("AndroidManifest.xml")
        java.srcDirs("src/main/java")
        kotlin.srcDirs("src/main/java")
    }
}
dependencies {
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("androidx.activity:activity-ktx:1.9.3")
}
