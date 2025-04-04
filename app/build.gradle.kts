plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "com.haykasatryan.utemqez"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.haykasatryan.utemqez"
        minSdk = 26
        targetSdk = 35
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
    packaging{
        resources{
            excludes += "META-INF/LICENSE.md"
            excludes += "META-INF/NOTICE.md"
        }
    }
}

dependencies {
    implementation(libs.cloudinary.android)
    implementation(libs.okhttp)
    implementation(libs.generativeai)
    implementation(libs.picasso)
    implementation(libs.android.mail)
    implementation(libs.firebase.firestore.v24100)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.storage.v2030)
    implementation(libs.imagepicker)
    implementation(libs.firebase.auth.v2200)
    implementation(libs.appcompat.v141)
    implementation(libs.material.v150)
    implementation(libs.constraintlayout.v213)
    implementation(libs.glide)
    annotationProcessor(libs.compiler)
    implementation(libs.firebase.auth.v2101)
    implementation(libs.firebase.firestore.v2401)
    implementation(libs.firebase.firestore)
    androidTestImplementation(libs.junit.v113)
    androidTestImplementation(libs.espresso.core.v340)
    implementation(libs.android.mail.v166)
    implementation(libs.generativeai)
    implementation(libs.guava)
    implementation(libs.firebase.storage.v2010)
    implementation(libs.firebase.firestore.v2450)
    implementation(libs.firebase.auth.v2110)
    implementation(libs.reactive.streams)
    implementation(libs.okhttp)
    implementation(libs.android.activation)
    implementation(libs.gbutton)
    implementation(libs.play.services.auth)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.database)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}