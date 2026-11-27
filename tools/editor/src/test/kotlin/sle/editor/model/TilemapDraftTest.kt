package sle.editor.model

import kotlinx.serialization.json.*
import java.nio.file.Files
import kotlin.test.*

class TilemapDraftTest {
    @Test fun externalEditsAndChangedIdentityCannotOverwriteSharedDraft() {
        val folder = Files.createTempDirectory("sle-map-draft-")
        val path = folder.resolve("World.tilemap.json")
        Files.writeString(path, TilemapDocument.empty("World").serialize())
        val draft = TilemapDraft.open(path)
        draft.document = TilemapDocument.empty("Changed")
        assertTrue(draft.dirty)
        Files.writeString(path, "external bytes")
        assertFailsWith<IllegalArgumentException> { draft.save() }
        assertTrue(draft.dirty)
        assertEquals("external bytes", Files.readString(path))
    }

    @Test fun explicitReloadRecoversFromExternalChanges() {
        val path = Files.createTempDirectory("sle-map-reload-").resolve("World.tilemap.json")
        Files.writeString(path, TilemapDocument.empty("World").serialize())
        val draft = TilemapDraft.open(path)
        draft.document = TilemapDocument.empty("Draft")
        Files.writeString(path, TilemapDocument.empty("External").serialize())
        draft.reload()
        assertEquals("External", draft.document.name)
        assertFalse(draft.dirty)
        draft.document = TilemapDocument.empty("Edited")
        draft.save()
        assertEquals("Edited", TilemapDocument.parse(Files.readString(path)).name)
    }

    @Test fun previewUsesImmutableCopiesAndSaveRetainsUndoableDocumentValues() {
        val path = Files.createTempDirectory("sle-map-preview-").resolve("World.tilemap.json")
        Files.writeString(path, TilemapDocument.empty("World").serialize())
        val draft = TilemapDraft.open(path)
        val original = draft.document
        val first = draft.previewPath()
        draft.document = TilemapDocument.empty("Changed")
        val second = draft.previewPath()
        assertNotEquals(first, second)
        assertEquals(original, TilemapDocument.parse(Files.readString(first)))
        draft.save(); assertFalse(draft.dirty)
        draft.document = original; assertTrue(draft.dirty)
        draft.save()
        assertEquals(original, TilemapDocument.parse(Files.readString(path)))
    }
}
