plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("org.jetbrains.kotlin.kapt")
}

android {
    namespace = "com.xelabooks.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.xelabooks.app"
        minSdk = 26  // Increasing to 26 for Azure Core library compatibility
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
    
    // Add packaging options to handle duplicate files
    packaging {
        resources {
            excludes.add("META-INF/DEPENDENCIES")
            excludes.add("META-INF/LICENSE")
            excludes.add("META-INF/LICENSE.txt")
            excludes.add("META-INF/license.txt")
            excludes.add("META-INF/NOTICE")
            excludes.add("META-INF/NOTICE.txt")
            excludes.add("META-INF/notice.txt")
            excludes.add("META-INF/ASL2.0")
            excludes.add("META-INF/*.kotlin_module")
            // Add additional excludes for Microsoft Graph libraries
            excludes.add("META-INF/INDEX.LIST")
            excludes.add("META-INF/jersey-module-version")
            excludes.add("META-INF/MANIFEST.MF")
        }
    }
    
    lint {
        baseline = file("lint-baseline.xml")
        abortOnError = false
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        viewBinding = true
        dataBinding = true
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    
    // Room components
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)
    
    // Fix for Room + Kotlin 1.9.22 compatibility
    kapt(libs.kotlinx.metadata.jvm)
    
    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    
    // Glide for image loading
    implementation(libs.glide)
    
    // Google Drive API
    implementation("com.google.android.gms:play-services-auth:20.7.0")
    implementation("com.google.api-client:google-api-client-android:2.2.0")
    implementation("com.google.apis:google-api-services-drive:v3-rev20220815-2.0.0")
    implementation("com.google.http-client:google-http-client-android:1.43.3")
    implementation("com.google.oauth-client:google-oauth-client-jetty:1.34.1")
    
    // Microsoft Authentication and OneDrive Graph API
    implementation("com.microsoft.identity.client:msal:4.0.0")
    implementation("com.microsoft.graph:microsoft-graph:5.34.0") {
        exclude(group = "org.bouncycastle", module = "bcprov-jdk15on")
        exclude(group = "com.microsoft.graph", module = "microsoft-graph-core")
    }
    implementation("com.microsoft.graph:microsoft-graph-core:2.0.14") {
        exclude(group = "org.bouncycastle", module = "bcprov-jdk15on")
    }
    implementation("org.slf4j:slf4j-api:1.7.36")
    implementation("org.slf4j:slf4j-simple:1.7.36")
    
    // Force Kotlin version - using the version specified in the version catalog
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.22")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.9.22")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.22")
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}