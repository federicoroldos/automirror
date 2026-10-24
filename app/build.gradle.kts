plugins {
    id("com.android.application")
}

android {
    namespace = "com.automirror"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.automirror"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Personal use: signed with the debug key so it can be installed directly.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.car.app:app:1.7.0")
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.2")
}
