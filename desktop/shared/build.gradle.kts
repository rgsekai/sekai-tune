import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    iosArm64()
    iosSimulatorArm64()

    // Android and desktop are both JVM, but the default hierarchy gives them no source set in
    // common.
    applyDefaultHierarchyTemplate()

    sourceSets {
        all {
            languageSettings {
                optIn("kotlin.concurrent.atomics.ExperimentalAtomicApi")
                optIn("kotlin.uuid.ExperimentalUuidApi")
                optIn("kotlin.time.ExperimentalTime")
                optIn("kotlin.io.encoding.ExperimentalEncodingApi")
            }
        }
        val jvmSharedMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                // Compile-only: the two applications supply the runtime artifact themselves.
                compileOnly("com.microsoft.onnxruntime:onnxruntime:1.20.0")
                // The network stack the lyrics providers and the YouTube Music
                // client run on, at the phone's versions — both applications
                // now call the same code, so they resolve the same libraries.
                api("io.ktor:ktor-client-okhttp:3.5.2")
                api("org.jsoup:jsoup:1.22.2")
                // NewPipe is the fallback resolver; compile-only because the phone ships a trimmed jar of it.
                compileOnly("com.github.TeamNewPipe:NewPipeExtractor:v0.26.3")
            }
        }
        androidMain.get().dependsOn(jvmSharedMain)
        jvmMain.get().dependsOn(jvmSharedMain)

        commonMain.dependencies {
            // Models in this module intentionally have no Android or Compose dependency.
            api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
            api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            api("io.ktor:ktor-client-core:3.5.2")
            api("io.ktor:ktor-client-websockets:3.5.2")
            api("io.ktor:ktor-client-content-negotiation:3.5.2")
            api("io.ktor:ktor-serialization-kotlinx-json:3.5.2")
            api("com.fleeksoft.ksoup:ksoup:0.2.6")
            api("com.squareup.okio:okio:3.17.0")
            // Settings storage: SharedPreferences on Android, NSUserDefaults on iOS.
            api("com.russhwolf:multiplatform-settings:1.3.0")
            // YouTube stream resolution: InnerTubeX's client catalog and cipher tiers.
            api("com.github.MetrolistGroup.innertubex:innertubex:v0.7.0")
            // Common locks/atomics; the version coroutines already pulls in.
            implementation("org.jetbrains.kotlinx:atomicfu:0.26.0")
        }
        iosMain.dependencies {
            implementation("io.ktor:ktor-client-darwin:3.5.2")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmTest.dependencies {
            implementation("com.russhwolf:multiplatform-settings-test:1.3.0")
            implementation("com.squareup.okhttp3:mockwebserver:5.3.2")
            implementation("com.microsoft.onnxruntime:onnxruntime:1.20.0")
        }
    }
}

android {
    // lintVital (run on every release build) prints Kotlin-metadata errors: AGP 8.10 lint embeds Kotlin 2.1. Full `lint` still runs on demand.
    lint { checkReleaseBuilds = false }
    namespace = "moe.rgsekai.sekaitune.shared"
    compileSdk = 36
}