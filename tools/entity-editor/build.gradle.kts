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

compose.desktop {
    application {
        mainClass = "sle.editor.app.MainKt"
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
