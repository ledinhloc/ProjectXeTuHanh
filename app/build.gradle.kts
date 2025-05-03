plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.projectxetuhanh"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.projectxetuhanh"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    implementation(libs.tensorflow.lite) // TensorFlow Lite
    implementation(libs.usbserial) // Giao tiếp USB
    implementation(libs.camera.core) // CameraX
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)

    implementation(libs.tensorflow.lite.support)
    implementation(libs.usbserial)
    //camera
    implementation(libs.camera.core.v130)
    implementation(libs.camera.camera2.v130)
    implementation(libs.camera.lifecycle.v130)
    implementation(libs.camera.view.v130)
}