plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.meshapp.routing"
    compileSdk = 37

    defaultConfig {
        minSdk = 30
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

// This safely configures the Java/Kotlin target for all compilation tasks
// without needing the broken extension properties.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    // Converted hyphens to dots:
    implementation(libs.kotlinx.coroutines.android)

    implementation(project(":model"))
    implementation(project(":packetProcessor"))
    implementation(project(":transport"))
    implementation(project(":security"))
    implementation(project(":logger"))

    testImplementation(libs.junit)
    // Converted hyphens to dots:
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}