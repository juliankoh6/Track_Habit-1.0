plugins {
    id("com.android.application")
    id("com.google.gms.google-services") // Firebase plugin
}
android {
    namespace = "com.trackhabit.myapplication.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.trackhabit.myapplication.app"
        minSdk = 34
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation(platform("com.google.firebase:firebase-bom:33.5.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth:23.1.0")
    implementation("com.google.firebase:firebase-firestore:24.4.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("com.google.android.material:material:1.8.0")
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("com.applandeo:material-calendar-view:1.9.2")
    implementation("androidx.activity:activity-ktx:1.7.2")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.work:work-runtime:2.8.1")
    implementation("androidx.gridlayout:gridlayout:1.0.0")
    implementation("com.squareup.okhttp3:okhttp:4.9.3")
    implementation("com.google.android.material:compose-theme-adapter:1.2.1")
    implementation("androidx.legacy:legacy-support-core-utils:1.0.0")
    implementation("androidx.preference:preference:1.2.0")
    // Additional dependencies
    implementation(libs.recyclerview)
    implementation(libs.places)
    // Testing dependencies
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}

