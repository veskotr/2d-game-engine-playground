package sle.editor.viewport

internal enum class ViewId { Scene, Game, Entity }
internal data class ViewRequest(
    val pixelWidth: Int, val pixelHeight: Int,
    val logicalWidth: Int, val logicalHeight: Int,
    val visible: Boolean, val generation: Long,
) {
    val drawable get() = visible && pixelWidth > 0 && pixelHeight > 0 && logicalWidth > 0 && logicalHeight > 0
}
/** Texture lifetime belongs to the owner. Consume synchronously on EDT, never cache it. */
internal data class ViewFrame(
    val view: ViewId, val sequence: Long, val request: ViewRequest,
    val texture: Int, val camera: PreviewCamera?, val gameRect: List<Float>,
    val cameraRotation: Float = 0f, val cameraZoom: Float = 1f,
)
internal data class ViewInput(
    val view: ViewId, val generation: Long, val type: Int,
    val code: Int = 0, val x: Double = 0.0, val y: Double = 0.0,
)
