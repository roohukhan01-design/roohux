plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.roohu.roohux"
    compileSdk = 35
    defaultConfig { applicationId = "com.roohu.roohux"; minSdk = 24; targetSdk = 35; versionCode = 1; versionName = "1.0.0" }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("androidx.activity:activity-ktx:1.9.3")
}
