plugins {
 id("com.android.application")
 id("org.jetbrains.kotlin.plugin.compose")
}
android {
 namespace = "com.daxiaamu.opluscameraenhance"
 compileSdk = 37
 buildToolsVersion = "36.0.0"
 defaultConfig {
  applicationId = "com.daxiaamu.opluscameraenhance"
  testInstrumentationRunner = providers.gradleProperty("instrumentationRunner").getOrElse("com.daxiaamu.opluscameraenhance.UpdateChecks")
  minSdk = 35
  targetSdk = 36
  versionCode = 18
  versionName = "0.3.10"
 }
 buildTypes {
  release {
   isMinifyEnabled = true
   isShrinkResources = true
   proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
  }
 }
 buildFeatures { compose = true; buildConfig = true }
 compileOptions {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
 }
 packaging { jniLibs { useLegacyPackaging = false } }
}
dependencies {
 compileOnly("io.github.libxposed:api:102.0.0")
 implementation("io.github.libxposed:service:102.0.0")
 implementation("org.luckypray:dexkit:2.2.0")
 implementation(platform("androidx.compose:compose-bom:2026.06.00"))
 implementation("androidx.activity:activity-compose:1.13.0")
 implementation("androidx.compose.material3:material3")
 implementation("androidx.compose.ui:ui")
 testImplementation("junit:junit:4.13.2")
}