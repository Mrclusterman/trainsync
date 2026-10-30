// Núcleo compartido: el motor de planes corre en el propio móvil (docs/VISION.md §7)
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvmToolchain(21)
    jvm()  // tests rápidos en el ordenador, también los que comparan con el prototipo en Python

    android {
        namespace = "io.github.mrclusterman.trainsync.shared"
        compileSdk = 37
        minSdk = 28
    }

    js {
        browser()  // versión web (web/): el mismo motor en el navegador
        binaries.library()
        generateTypeScriptDefinitions()
        useEsModules()
        outputModuleName = "trainsync-shared"
        nodejs {  // los tests, sin navegador
            testTask {
                environment("GOLDEN_DIR", file("src/jvmAndJsTest/resources/golden").absolutePath)
                useMocha { timeout = "120s" }  // los casos de referencia tardan más que los 2 s por defecto
            }
        }
    }

    // Los casos de referencia corren en la JVM y en JS; iOS no puede leerlos del disco del proyecto
    applyDefaultHierarchyTemplate {
        common { group("jvmAndJs") { withJvm(); withJs() } }
    }

    // Las Apple solo compilan en macOS (CI de GitHub); en Linux Gradle las omite
    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.java)
        }
        jsMain.dependencies {
            implementation(libs.ktor.client.js)
        }
        val jvmAndJsTest by getting {
            dependencies {
                implementation(libs.ktor.client.mock)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}
