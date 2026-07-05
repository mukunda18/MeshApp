plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.minor.meshcontrol"
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

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    api(libs.kotlinx.coroutines.android)
    implementation(libs.material)
    api(project(":model"))
    api(project(":routing"))
    api(project(":transport"))
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
