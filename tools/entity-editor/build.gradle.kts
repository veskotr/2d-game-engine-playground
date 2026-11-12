plugins {
    kotlin("jvm") version "2.4.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21"
    id("org.jetbrains.compose") version "1.12.2"
}

group = "sle.editor"
version = "0.1.0"

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
    runtimeOnly("org.lwjgl:lwjgl::natives-windows")
    runtimeOnly("org.lwjgl:lwjgl-opengl::natives-windows")
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
            rootDir.resolve("../../build/debug/tools/editor-native/Debug/sle_editor_native.dll")
                .canonicalPath,
        ).get(),
    )
    systemProperty("sle.smokeReport", rootDir.resolve("build/reports/embedded-smoke.png").absolutePath)
    systemProperty("sle.authoringReport", rootDir.resolve("build/reports/authored-entity.prefab.json").absolutePath)
}
