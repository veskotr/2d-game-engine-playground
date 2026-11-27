import groovy.json.JsonSlurper
import java.security.MessageDigest
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.4.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21"
    id("org.jetbrains.compose") version "1.12.2"
}

group = "sle.editor"
// Editor version: 0.MINOR.PATCH while pre-1.0; bump MINOR for each added editor feature.
// The engine version lives in the top-level CMakeLists.txt project(VERSION).
version = "0.36.0"

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

// Exposes `version` to the running editor as sle/editor/version.properties.
val editorVersionResources = layout.buildDirectory.dir("generated/version")
val generateEditorVersion by tasks.registering {
    val text = project.version.toString()
    val output = editorVersionResources
    inputs.property("version", text)
    outputs.dir(output)
    doLast {
        val file = output.get().file("sle/editor/version.properties").asFile
        file.parentFile.mkdirs()
        file.writeText("version=$text\n")
    }
}
sourceSets.main { resources.srcDir(generateEditorVersion) }

// Packaged editors carry the engine library and shared assets as Compose app resources
// (read at runtime through compose.application.resources.dir). Package from a Release build.
val packagedNativeLibrary = providers.gradleProperty("slePackageNativeLibrary").orElse(
    rootDir.resolve(
        if (isWindowsHost) "../../build/release/tools/editor-native/Release/sle_editor_native.dll"
        else "../../build/linux-x64-release/tools/editor-native/libsle_editor_native.so",
    ).canonicalPath,
)
// Use a common resource directory so both export targets are available on either host.
val runtimeBundles = mapOf(
    "windows-x64" to providers.gradleProperty("slePackageWindowsRuntimeBundle")
        .orElse(providers.gradleProperty("slePackageRuntimeBundle"))
        .orElse(rootDir.resolve("../../build/release/runtime/Release").canonicalPath),
    "linux-x64" to providers.gradleProperty("slePackageLinuxRuntimeBundle")
        .orElse(rootDir.resolve("../../build/linux-x64-release/runtime/Release").canonicalPath),
)
val runtimeLaunchers = mapOf("windows-x64" to "engine_app.exe", "linux-x64" to "engine_app")
val engineCmake = rootDir.resolve("../../CMakeLists.txt")
val engineVersion = providers.fileContents(layout.projectDirectory.file("../../CMakeLists.txt")).asText.map { cmake ->
    requireNotNull(Regex("""project\(GameEngine\s+VERSION\s+([0-9]+\.[0-9]+\.[0-9]+)""").find(cmake)).groupValues[1]
}
val packagedResources = layout.buildDirectory.dir("packaged-resources")
val preparePackagedResources by tasks.registering(Sync::class) {
    into(packagedResources)
    from(rootDir.resolve("../../assets")) { into("common/assets") }
    from(packagedNativeLibrary.map { file(it) }) { into(if (isWindowsHost) "windows-x64" else "linux-x64") }
    for ((target, bundle) in runtimeBundles) {
        from(bundle.map { file(it) }) {
            into("common/runtime/$target")
            if (isLinuxHost && target == "linux-x64") {
                filesMatching("engine_app") { permissions { unix("755") } }
            }
        }
    }
}
val verifyPackagedResources by tasks.registering {
    dependsOn(preparePackagedResources)
    inputs.dir(packagedResources)
    inputs.file(engineCmake)
    doLast {
        val resources = packagedResources.get().asFile
        val library = file(packagedNativeLibrary.get())
        val host = if (isWindowsHost) "windows-x64" else "linux-x64"
        check(resources.resolve("$host/${library.name}").isFile) {
            "Engine library missing: $library. Build sle_editor_native in Release or pass -PslePackageNativeLibrary."
        }
        for ((target, bundle) in runtimeBundles) {
            val runtime = resources.resolve("common/runtime/$target")
            val inventory = runtime.resolve("sle-runtime.json")
            check(inventory.isFile) { "Missing $target Release runtime bundle at ${bundle.get()}" }
            val manifest = JsonSlurper().parse(inventory) as Map<*, *>
            val launcher = runtimeLaunchers.getValue(target)
            check(manifest["format"] == "sle-runtime" && manifest["version"] == 1 &&
                manifest["platform"] == target && manifest["configuration"] == "Release" &&
                manifest["engineVersion"] == engineVersion.get() && manifest["executable"] == launcher) {
                "Expected $target SLE ${engineVersion.get()} Release runtime at ${bundle.get()}"
            }
            val digest = MessageDigest.getInstance("SHA-256")
            val actual = runtime.walkTopDown().filter { it.isFile && it != inventory }.associate { payload ->
                payload.inputStream().buffered().use { stream ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                    }
                }
                payload.relativeTo(runtime).invariantSeparatorsPath to digest.digest().toHexString()
            }
            check(actual == manifest["files"] && launcher in actual && actual.keys.any { it.startsWith("assets/shaders/") }) {
                "Runtime bundle files are missing, modified or unexpected: ${bundle.get()}"
            }
        }
    }
}
tasks.matching { it.name == "prepareAppResources" }.configureEach { dependsOn(preparePackagedResources) }
tasks.matching { it.name == "createDistributable" || it.name.startsWith("package") }.configureEach {
    dependsOn(verifyPackagedResources)
    // Compose's packaging tasks otherwise stay UP-TO-DATE when only app resources change.
    inputs.files(preparePackagedResources).withPropertyName("slePackagedResources")
}

compose.desktop {
    application {
        mainClass = "sle.editor.app.MainKt"
        nativeDistributions {
            targetFormats(
                *(if (isWindowsHost) arrayOf(TargetFormat.Msi, TargetFormat.Exe) else arrayOf(TargetFormat.Deb)),
            )
            packageName = "SLE Editor"
            packageVersion = project.version.toString()
            description = "SLE scene and entity editor"
            vendor = "SLE"
            appResourcesRootDir.set(packagedResources)
            modules("java.instrument", "java.management", "jdk.unsupported") // from suggestRuntimeModules
            // Same mascot as the editor window icon; the .ico holds 16–256 px sizes.
            windows {
                iconFile.set(project.file("packaging/sle.ico"))
                menuGroup = "SLE"
                upgradeUuid = "6f1b3c2e-6a59-4c55-9a1e-6f0f6d1f5e21"
            }
            linux {
                iconFile.set(project.file("src/main/resources/sle/editor/branding/sle-256.png"))
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
