package sle.editor.workspace

@JvmInline value class PanelId(val value: String) { init { require(value.isNotBlank()) } }
@JvmInline value class HostId(val value: String) { init { require(value.isNotBlank()) } }
@JvmInline value class GroupId(val value: String) { init { require(value.isNotBlank()) } }

data class PanelMinimum(val width: Float, val height: Float) {
    init { require(width.isFinite() && height.isFinite() && width >= 0 && height >= 0) }
}

data class PanelDescriptor(
    val id: PanelId,
    val title: String,
    val minimum: PanelMinimum,
    val available: Boolean = true,
    /** Opened on demand (e.g. a prefab document) and hidden by default; closing may be vetoed. */
    val temporary: Boolean = false,
)

/** Metadata only. Compose factories belong to the workspace binding layer. */
class PanelRegistry(val panels: List<PanelDescriptor>) {
    init { require(panels.map { it.id }.distinct().size == panels.size) { "Duplicate panel ID" } }
    val byId: Map<PanelId, PanelDescriptor> = panels.associateBy { it.id }
}

fun standardPanels(): List<PanelDescriptor> = listOf(
    PanelDescriptor(PanelId("hierarchy"), "Hierarchy", PanelMinimum(200f, 160f)),
    PanelDescriptor(PanelId("inspector"), "Inspector", PanelMinimum(260f, 200f)),
    PanelDescriptor(PanelId("assets"), "Assets", PanelMinimum(360f, 180f)),
    PanelDescriptor(PanelId("scene"), "Scene", PanelMinimum(320f, 200f)),
    PanelDescriptor(PanelId("game"), "Game", PanelMinimum(320f, 200f)),
    PanelDescriptor(PanelId("entity"), "Prefab", PanelMinimum(320f, 240f), temporary = true),
)
