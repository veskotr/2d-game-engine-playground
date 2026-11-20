package sle.editor.command

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import sle.editor.model.*
import sle.editor.ui.inspector.EditorPropertyDefinition
import sle.editor.ui.inspector.PropertyKind
import sle.editor.ui.inspector.inspectorPathVisible
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException

internal interface DetailsClipboard {
    fun read(): PropertyClipboardValue
    fun write(value: PropertyClipboardValue)
}

internal object DesktopDetailsClipboard : DetailsClipboard {
    private val kindFlavor = DataFlavor("application/x-sle-property-kind;class=java.lang.String")

    override fun read(): PropertyClipboardValue {
        val contents = Toolkit.getDefaultToolkit().systemClipboard.getContents(null)
        require(contents != null && contents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
            "Clipboard has no JSON text"
        }
        val kind = if (contents.isDataFlavorSupported(kindFlavor)) {
            val name = contents.getTransferData(kindFlavor) as? String
            PropertyKind.entries.firstOrNull { it.name == name }
        } else null
        return PropertyClipboardValue(contents.getTransferData(DataFlavor.stringFlavor) as String, kind)
    }

    override fun write(value: PropertyClipboardValue) {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(object : Transferable {
            override fun getTransferDataFlavors() = arrayOf(DataFlavor.stringFlavor, kindFlavor)
            override fun isDataFlavorSupported(flavor: DataFlavor) = flavor in transferDataFlavors
            override fun getTransferData(flavor: DataFlavor): Any = when (flavor) {
                DataFlavor.stringFlavor -> value.text
                kindFlavor -> value.kind?.name.orEmpty()
                else -> throw UnsupportedFlavorException(flavor)
            }
        }, null)
    }
}

internal class DetailsTarget(
    val state: EntityWorkspaceState,
    val component: String,
    val readOnly: Boolean,
    context: CommandContext,
    val property: EditorPropertyDefinition? = null,
    private val mounted: () -> Boolean = { true },
) {
    constructor(state: EntityWorkspaceState, property: EditorPropertyDefinition, readOnly: Boolean,
        context: CommandContext, mounted: () -> Boolean = { true },
    ) : this(state, property.component, readOnly, context, property, mounted)

    private val expectedDocument = state.document
    private val expectedDrafts = state.drafts
    private val expectedTimeline = state.timeline
    private val expectedFile = (expectedTimeline as? SceneWorkspaceState)?.file ?: state.file
    private val generation = state.editContextVersion
    private val mode = context.mode
    private val projectGeneration = context.projectGeneration

    fun available(context: CommandContext): Boolean {
        if (!mounted() || context.modalOpen || context.projectBusy || context.entity !== state ||
            context.document !== expectedTimeline || state.timeline !== expectedTimeline ||
            context.mode != mode || context.projectGeneration != projectGeneration ||
            state.document !== expectedDocument || state.drafts !== expectedDrafts || state.editContextVersion != generation) return false
        val scene = expectedTimeline as? SceneWorkspaceState
        if (scene != null && scene.entity(expectedDocument.key) !== state) return false
        if ((scene?.file ?: state.file) != expectedFile) return false
        val fields = expectedDocument.components[component] as? JsonObject ?: return false
        val property = property ?: return component in state.catalog
        val value = property.path.fold<String, JsonElement?>(fields) { parent, key -> (parent as? JsonObject)?.get(key) }
        return value == property.value && inspectorPathVisible(fields, state.catalog[property.component], property.path)
    }
}

internal object DetailsCommands {
    private val actions = DetailsPropertyActions()

    private fun command(
        name: String,
        writable: Boolean,
        componentAction: Boolean = false,
        supports: (DetailsTarget) -> Boolean = { true },
        action: (CommandContext, DetailsTarget) -> Unit,
    ): EditorCommand {
        val scope = if (componentAction) "Component" else "Property"
        return EditorCommand(CommandId("details.${name.lowercase()}$scope"), "$name $scope", menuLabel = name,
            enabled = { ctx ->
                ctx.details?.let { target ->
                    val property = target.property
                    (property == null) == componentAction &&
                        target.available(ctx) && ctx.editable && !ctx.gameRunning && supports(target) &&
                        (!writable || !target.readOnly && property?.readOnly != true &&
                            property?.hasReadOnlyDescendants != true)
                } == true
            },
        ) { ctx ->
            val target = ctx.details ?: return@EditorCommand
            try {
                action(ctx, target)
            } catch (failure: Exception) {
                target.state.error = failure.message ?: "$name failed"
            }
        }
    }

    val reset = command("Reset", writable = true, supports = { it.property?.defaultValue != null }) { ctx, target ->
        val property = checkNotNull(target.property)
        actions.replace(target.state, property, property.defaultValue.toString(), "Reset",
            resolveProjectAsset = ctx.resolveDetailsAsset)
    }
    val copy = command("Copy", writable = false) { ctx, target ->
        val property = checkNotNull(target.property)
        ctx.detailsClipboard.write(PropertyClipboardValue(property.value.toString(), property.kind))
    }
    val paste = command("Paste", writable = true) { ctx, target ->
        val clipboard = ctx.detailsClipboard.read()
        actions.replace(target.state, checkNotNull(target.property), clipboard.text, "Paste", clipboard.kind, ctx.resolveDetailsAsset)
    }
    val componentReset = command("Reset", writable = true, componentAction = true) { ctx, target ->
        actions.replaceComponent(target.state, target.component, resolveProjectAsset = ctx.resolveDetailsAsset)
    }
    val componentCopy = command("Copy", writable = false, componentAction = true) { ctx, target ->
        ctx.detailsClipboard.write(PropertyClipboardValue(actions.copyComponent(target.state, target.component)))
    }
    val componentPaste = command("Paste", writable = true, componentAction = true) { ctx, target ->
        val clipboard = ctx.detailsClipboard.read()
        actions.replaceComponent(target.state, target.component, clipboard.text, ctx.resolveDetailsAsset)
    }
    val propertyActions = listOf(reset, copy, paste)
    val componentActions = listOf(componentReset, componentCopy, componentPaste)
    val all = propertyActions + componentActions
}
