import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    kotlin("android")
    kotlin("plugin.serialization") version "2.3.20"
    kotlin("plugin.compose") version "2.3.20"
}

android {
    compileSdk = 36

    defaultConfig {
        minSdk = 21
        // A library ignores targetSdk for packaging, but Robolectric reads it to pick
        // the emulated SDK level, and ContextCompat.registerReceiver branches on that:
        // below 34 it emulates RECEIVER_NOT_EXPORTED via a runtime permission check that
        // LatinIME.onCreate cannot satisfy under test. Keep it at upstream's value.
        @Suppress("DEPRECATION")
        targetSdk = 36
        ndk {
            abiFilters.clear()
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64"))
        }
        // Shipped to whichever app consumes this library; the consumer runs
        // minification, this module does not.
        consumerProguardFiles("proguard-rules.pro")

        // A library generates no VERSION_NAME/VERSION_CODE. Both are pinned to
        // the upstream release this fork tracks rather than to the consuming
        // app's version, because that is what they mean here: AppUpgrade keys
        // its settings migrations off VERSION_CODE, and every other reader
        // reports the keyboard engine's version, not the product's.
        buildConfigField("String", "VERSION_NAME", "\"4.0-dev1\"")
        buildConfigField("int", "VERSION_CODE", "4006")
    }

    buildTypes {
        release {
        }
        create("nouserlib") { // same as release, but does not allow the user to provide a library
        }
        debug {
        }
        create("runTests") { // build variant for running tests on CI that skips tests known to fail
        }
        create("debugNoMinify") { // for faster builds in IDE
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
        compose = true
    }

    externalNativeBuild {
        ndkBuild {
            path = File("src/main/jni/Android.mk")
        }
    }
    ndkVersion = "28.0.13004108"

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        target {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_17)
            }
        }
    }

    namespace = "helium314.keyboard.latin"
    lint {
        abortOnError = true
    }
}

dependencies {
    // androidx
    implementation("androidx.core:core-ktx:1.17.0") // 1.18.0 requires minSdk 23
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.autofill:autofill:1.3.0")
    implementation("androidx.viewpager2:viewpager2:1.1.0")

    // kotlin
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    // compose
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    // newer than 2025.11.01 contains androidx.compose.material:material-android:1.10.0, which requires minSdk 23
    // maybe it's possible to use tools:overrideLibrary="androidx.compose.material" as it's not used explicitly, but probably this is just going to crash
    implementation(platform("androidx.compose:compose-bom:2025.11.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    "debugNoMinifyImplementation"("androidx.compose.ui:ui-tooling")
    implementation("androidx.navigation:navigation-compose:2.9.8")
    implementation("sh.calvin.reorderable:reorderable:3.1.0") // for easier re-ordering
    implementation("com.github.skydoves:colorpicker-compose:1.1.3") // for user-defined colors

    // test
    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.23.0")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("androidx.test:runner:1.7.0")
    testImplementation("androidx.test:core:1.7.0")
}
