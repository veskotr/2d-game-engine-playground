package sle.editor.ui

import org.junit.Test
import sle.editor.command.standardKeymap
import sle.editor.ui.controls.EditorGlyph
import sle.editor.ui.theme.EditorThemeMode
import kotlin.test.*

class EditorIconsTest {
    @Test fun everyIconKeyHasBothThemesSizesAndSource() {
        for(key in EditorIcons.kinds + EditorIcons.actions) {
            for(mode in EditorThemeMode.entries) for(size in listOf(16, 24))
                assertNotNull(javaClass.getResource("/" + EditorIcons.path(key, mode, size)), "$key ${mode.name} $size")
            assertNotNull(javaClass.getResource("/sle/editor/icons/svg/$key.svg"), "$key.svg")
        }
    }

    @Test fun actionKeysResolveToThemselvesNotTheResourceFallback() {
        for(key in EditorIcons.actions)
            assertTrue(EditorIcons.path(key, EditorThemeMode.Dark).endsWith("/$key.png"), key)
        // Component types still map through their semantic kind.
        assertTrue(EditorIcons.path("Camera2D", EditorThemeMode.Light).endsWith("/camera.png"))
    }

    @Test fun glyphsAndCommandsOnlyNameSuppliedIcons() {
        for(glyph in EditorGlyph.entries) glyph.iconKey?.let { assertTrue(it in EditorIcons.actions + EditorIcons.kinds, "${glyph.name} -> $it") }
        // The window and tree primitives stay drawn in code.
        for(glyph in listOf(EditorGlyph.Close, EditorGlyph.Minimize, EditorGlyph.Detach, EditorGlyph.ChevronDown, EditorGlyph.Check, EditorGlyph.Grip))
            assertNull(glyph.iconKey, glyph.name)
        val commands = standardKeymap.registry.commands
        for(command in commands) command.icon?.let { assertTrue(it in EditorIcons.actions + EditorIcons.kinds, "${command.id.value} -> $it") }
        for(id in listOf("edit.undo", "edit.redo", "edit.delete", "file.save", "play.play", "tools.move", "view.frameSelected"))
            assertNotNull(commands.single { it.id.value == id }.icon, id)
    }
}
