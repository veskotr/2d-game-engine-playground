package sle.editor.app

import java.util.Properties

/** Editor version from build.gradle.kts (`version`), generated into the editor resources. */
internal object EditorVersion {
    val text: String by lazy {
        EditorVersion::class.java.getResourceAsStream("/sle/editor/version.properties")?.use { stream ->
            Properties().apply { load(stream) }.getProperty("version")
        } ?: "dev"
    }
}
