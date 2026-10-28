plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "ua.cn.stu.randomgallery.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "ua.cn.stu.randomgallery.app"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(files("libs/randomgallery-client-2.0.jar"))
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.recyclerview)

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
