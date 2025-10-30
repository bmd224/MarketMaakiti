plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)


    id("com.google.gms.google-services")
}

android {
    namespace = "uqac.dim.market"
    compileSdk = 35

    defaultConfig {
        applicationId = "uqac.dim.market"
        minSdk = 29
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
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "version-compose-compiler"
    }
    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt"
            )
        }
    }
}

dependencies {


    implementation (libs.google.firebase.firestore.ktx)
    implementation (libs.google.firebase.storage.ktx)
    implementation (libs.coil.compose.v250)

    implementation (libs.firebase.messaging.ktx)
    implementation (libs.google.auth.library.oauth2.http)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)

    //afficher les imagws
    implementation(libs.coil.compose)

    implementation (libs.kotlinx.coroutines.play.services)


    implementation(libs.google.firebase.auth.ktx)
    implementation(libs.firebase.storage.ktx)
    implementation(libs.firebase.firestore.ktx)

    implementation (libs.androidx.material.icons.extended)

    // Firebase Auth
    implementation(libs.firebase.auth.ktx)

    // Google Sign-In
    implementation(libs.play.services.auth)

    // Compose
    implementation(libs.ui)
    implementation(libs.androidx.activity.compose.v180)


    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}