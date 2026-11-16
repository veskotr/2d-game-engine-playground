import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.4.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21"
    id("org.jetbrains.compose") version "1.12.2"
}

group = "sle.editor"
version = "0.1.0"

val hostOs = System.getProperty("os.name")
val isWindowsHost = hostOs.startsWith("Windows")
val isLinuxHost = hostOs.startsWith("Linux")
require(isWindowsHost || isLinuxHost) { "The SLE editor supports Windows x64 and Linux x64 hosts, not $hostOs" }
val lwjglNatives = if (isWindowsHost) "natives-windows" else "natives-linux"

// Keeps a second host's outputs (e.g. WSL beside Windows) out of the shared checkout's build/.
providers.gradleProperty("sleBuildDir").orNull?.let { layout.buildDirectory.set(file(it)) }

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.compose.material:material:1.12.2")
    implementation("org.lwjglx:lwjgl3-awt:0.2.5") {
        // Its Maven OS profiles leave a literal ${lwjgl.natives} classifier in Gradle.
        // Declare the required LWJGL modules and native classifiers explicitly below.
        exclude(group = "org.lwjgl")
    }
    implementation(platform("org.lwjgl:lwjgl-bom:3.4.2"))
    implementation("org.lwjgl:lwjgl")
    implementation("org.lwjgl:lwjgl-opengl")
    implementation("org.lwjgl:lwjgl-jawt")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    testImplementation(kotlin("test-junit"))
    runtimeOnly("org.lwjgl:lwjgl::$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-opengl::$lwjglNatives")
}

tasks.test {
    systemProperty("sle.repoRoot", rootDir.resolve("../..").canonicalPath)
}

// Packaged editors carry the engine library and shared assets as Compose app resources
// (read at runtime through compose.application.resources.dir). Package from a Release build.
val packagedNativeLibrary = providers.gradleProperty("slePackageNativeLibrary").orElse(
    rootDir.resolve(
        if (isWindowsHost) "../../build/release/tools/editor-native/Release/sle_editor_native.dll"
        else "../../build/release/tools/editor-native/libsle_editor_native.so",
    ).canonicalPath,
)
val packagedResources = layout.buildDirectory.dir("packaged-resources")
val preparePackagedResources by tasks.registering(Sync::class) {
    into(packagedResources)
    from(rootDir.resolve("../../assets")) { into("common/assets") }
    from(packagedNativeLibrary.map { file(it) }) { into(if (isWindowsHost) "windows-x64" else "linux-x64") }
}
tasks.matching { it.name == "prepareAppResources" }.configureEach { dependsOn(preparePackagedResources) }
tasks.matching { it.name == "createDistributable" || it.name.startsWith("package") }.configureEach {
    // Compose's packaging tasks otherwise stay UP-TO-DATE when only app resources change.
    inputs.files(preparePackagedResources).withPropertyName("slePackagedResources")
    doFirst {
        val library = file(packagedNativeLibrary.get())
        check(library.isFile) {
            "Engine library missing: $library. Build sle_editor_native in Release (or pass -PslePackageNativeLibrary)."
        }
    }
}

compose.desktop {
    application {
        mainClass = "sle.editor.app.MainKt"
        nativeDistributions {
            targetFormats(
                *(if (isWindowsHost) arrayOf(TargetFormat.Msi, TargetFormat.Exe) else arrayOf(TargetFormat.Deb)),
            )
            packageName = "SLE Editor"
            packageVersion = "1.0.0"
            description = "SLE scene and entity editor"
            vendor = "SLE"
            appResourcesRootDir.set(packagedResources)
            modules("java.instrument", "java.management", "jdk.unsupported") // from suggestRuntimeModules
            windows {
                menuGroup = "SLE"
                upgradeUuid = "6f1b3c2e-6a59-4c55-9a1e-6f0f6d1f5e21"
            }
            linux {
                packageName = "sle-editor"
            }
        }
    }
}

tasks.withType<JavaExec>().configureEach {
    workingDir = rootDir.resolve("../..").canonicalFile
    systemProperty(
        "sle.nativeLibrary",
        providers.gradleProperty("sleNativeLibrary").orElse(
            rootDir.resolve(
                if (isWindowsHost) "../../build/debug/tools/editor-native/Debug/sle_editor_native.dll"
                else "../../build/debug/tools/editor-native/libsle_editor_native.so",
            ).canonicalPath,
        ).get(),
    )
    val reports = layout.buildDirectory.dir("reports").get().asFile
    systemProperty("sle.smokeReport", reports.resolve("embedded-smoke.png").absolutePath)
    systemProperty("sle.authoringReport", reports.resolve("authored-entity.prefab.json").absolutePath)
}
