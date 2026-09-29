plugins { id("com.android.application"); kotlin("android") }

android {
    namespace = "org.chinaquest.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "org.chinaquest.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "0.2.0-pilot"
        testInstrumentationRunner = "android.test.InstrumentationTestRunner"
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    sourceSets["main"].assets.srcDir("../../content")
    testOptions { unitTests.isIncludeAndroidResources = true }
    lint { abortOnError = true }
}

dependencies {
    implementation(project(":core"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}
