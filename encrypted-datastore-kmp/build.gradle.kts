import org.jetbrains.kotlin.gradle.plugin.mpp.TestExecutable
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest
import kotlinx.validation.KotlinApiBuildTask
import kotlinx.validation.KotlinApiCompareTask

plugins {
    convention.library.kmp
    convention.publish
    alias(libs.plugins.cryptography)
}

description = "Authenticated Kotlin Multiplatform DataStore for Android, JVM, iOS and macOS"

mavenPublishing {
    pom { description.set(project.description) }
}

cryptography {
    configureSwiftLinkerOpts = true
}

kotlin {
    android {
        withDeviceTestBuilder { sourceSetTreeName = "test" }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }
    iosArm64()
    iosSimulatorArm64 {
        binaries.withType<TestExecutable>().configureEach {
            // The simulator Security framework reads this test-only section (KT-61470).
            val entitlements = project.layout.projectDirectory.file("src/appleTest/resources/entitlements.plist")
            linkerOpts("-sectcreate", "__TEXT", "__entitlements",
                entitlements.asFile.absolutePath)
            linkTaskProvider.configure {
                inputs.file(entitlements).withPropertyName("keychainTestEntitlements")
                    .withPathSensitivity(PathSensitivity.RELATIVE)
            }
        }
    }
    macosArm64()

    sourceSets {
        commonMain.dependencies {
            api(libs.androidx.datastore.core.okio)
            api(libs.androidx.datastore.preferences.core)
            api(libs.coroutines.core)
            implementation(libs.cryptography.core)
            implementation(libs.cryptography.provider.optimal)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.test)
            implementation(libs.okio.fakefilesystem)
        }
        commonJvmTest.dependencies {
            implementation(kotlin("test-junit5"))
        }
        named("androidDeviceTest").dependencies {
            implementation(kotlin("test-junit"))
            implementation(libs.androidx.test.runner)
        }
        val appleMain = create("appleMain") { dependsOn(commonMain.get()) }
        val appleTest = create("appleTest") { dependsOn(commonTest.get()) }
        for (target in listOf("iosArm64", "iosSimulatorArm64", "macosArm64")) {
            named("${target}Main") { dependsOn(appleMain) }
            named("${target}Test") { dependsOn(appleTest) }
        }
    }
}

tasks.withType<KotlinNativeSimulatorTest>().configureEach {
    standalone.set(false)
    device.set(providers.gradleProperty("iosSimulatorDevice").orElse("booted"))
}

apiValidation {
    @OptIn(kotlinx.validation.ExperimentalBCVApi::class)
    klib { enabled = true }
}

// BCV 0.18.2 does not discover the new AGP KMP Android target automatically.
// Use its public tasks over the actual Android compilation, including Keystore-only APIs.
val androidMainCompilation = kotlin.targets.getByName("android").compilations.getByName("main")
val androidApiBuild = tasks.register<KotlinApiBuildTask>("androidApiBuild") {
    inputClassesDirs.from(androidMainCompilation.output.classesDirs)
    inputDependencies.from(androidMainCompilation.compileDependencyFiles)
    runtimeClasspath.from(tasks.named<KotlinApiBuildTask>("jvmApiBuild").map { it.runtimeClasspath })
    outputApiFile.set(layout.buildDirectory.file("api/android/${project.name}.api"))
}
val androidApiCheck = tasks.register<KotlinApiCompareTask>("androidApiCheck") {
    projectApiFile.set(layout.projectDirectory.file("api/android/${project.name}.api"))
    generatedApiFile.set(androidApiBuild.flatMap { it.outputApiFile })
}
val androidApiDump = tasks.register<Copy>("androidApiDump") {
    from(androidApiBuild.flatMap { it.outputApiFile })
    into(layout.projectDirectory.dir("api/android"))
}
tasks.named("apiCheck") { dependsOn(androidApiCheck) }
tasks.named("apiDump") { dependsOn(androidApiDump) }

val benchmarkCompilation = kotlin.targets.getByName<KotlinJvmTarget>("jvm").compilations.getByName("test")
tasks.register<JavaExec>("benchmark") {
    group = "verification"
    description = "Measures authenticated settings record roundtrips; no timing thresholds"
    dependsOn(benchmarkCompilation.compileTaskProvider)
    mainClass.set("com.dayanruben.datastore.encrypted.kmp.EncryptedDataStoreBenchmark")
    classpath(benchmarkCompilation.output.allOutputs, benchmarkCompilation.runtimeDependencyFiles)
    jvmArgs("-Xms256m", "-Xmx256m")
}
