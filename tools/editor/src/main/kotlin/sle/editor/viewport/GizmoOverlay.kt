package sle.editor.viewport

import androidx.compose.ui.graphics.Color
import sle.editor.model.EntityDocument
import sle.editor.model.TileStampImage
import sle.editor.viewport.tools.MoveHandles
import sle.editor.viewport.tools.ScaleHandles

import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL12
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL14
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL30
import kotlin.math.*

/** Editor geometry drawn directly after SLE, in the same host framebuffer. */
internal class GizmoOverlay {
    private var program = 0
    private var vao = 0
    private var buffer = 0
    private val stampTextures = linkedMapOf<TileStampImage, Int>()
    private fun initialize() {
        fun shader(type: Int, source: String): Int {
            val shader = GL20.glCreateShader(type)
            GL20.glShaderSource(shader, source); GL20.glCompileShader(shader)
            check(GL20.glGetShaderi(shader,GL20.GL_COMPILE_STATUS) != 0) { GL20.glGetShaderInfoLog(shader) }
            return shader
        }
        val vertex = shader(GL20.GL_VERTEX_SHADER, """
            #version 330 core
            layout(location=0) in vec2 p;
            const vec2 coords[6] = vec2[6](vec2(0,0),vec2(1,0),vec2(1,1),vec2(0,0),vec2(1,1),vec2(0,1));
            out vec2 uv;
            void main() { gl_Position = vec4(p,0,1); uv = coords[gl_VertexID % 6]; }
        """.trimIndent())
        val fragment = shader(GL20.GL_FRAGMENT_SHADER, """
            #version 330 core
            uniform vec4 selectionColor;
            uniform bool textured;
            uniform sampler2D stamp;
            in vec2 uv;
            out vec4 color;
            void main() { color = textured ? selectionColor * texture(stamp,uv) : selectionColor; }
        """.trimIndent())
        program = GL20.glCreateProgram()
        GL20.glAttachShader(program,vertex); GL20.glAttachShader(program,fragment); GL20.glLinkProgram(program)
        GL20.glDeleteShader(vertex); GL20.glDeleteShader(fragment)
        check(GL20.glGetProgrami(program,GL20.GL_LINK_STATUS) != 0) { GL20.glGetProgramInfoLog(program) }
        vao=GL30.glGenVertexArrays(); buffer=GL15.glGenBuffers()
    }
    /** Selection bounds only, no handles: the other entities of a multi-selection. */
    fun outline(entities: List<EntityDocument>, camera: PreviewCamera, color: Color) {
        if (entities.isEmpty()) return
        render(camera, color) { draw ->
            val lines=mutableListOf<Vec2>()
            entities.forEach { entity -> entitySelectionBounds(entity,camera).zipWithNext().forEach { (a,b) -> lines.add(a);lines.add(b) } }
            draw(GL11.GL_LINES,lines)
        }
    }
    /** The active entity: its outline (or edited shape) plus handles. [entityHandles] is false when a
     * tool draws its own handles at the entity position (the Move tool's arrows). */
    fun draw(shape: GizmoShape, camera: PreviewCamera, entity: EntityDocument? = null, color: Color, entityHandles: Boolean = true) = render(camera, color) { draw ->
        val lines=mutableListOf<Vec2>()
        if (shape.target == "Entity") {
            val p=camera.screen(shape.position)
            if(entityHandles) lines.addAll(listOf(p-Vec2(24.0,0.0),p+Vec2(24.0,0.0),p-Vec2(0.0,24.0),p+Vec2(0.0,24.0)))
            if(entity!=null) {
                val bounds=entitySelectionBounds(entity,camera)
                bounds.zipWithNext().forEach { (a,b) -> lines.add(a);lines.add(b) }
            }
        } else if (shape.editable) {
            val outline=shape.outline().map(camera::screen)
            outline.zipWithNext().forEach { (a,b) -> lines.add(a); lines.add(b) }
        }
        draw(GL11.GL_LINES,lines)
        val squares=mutableListOf<Vec2>()
        GizmoMath.handles(shape).filter { entityHandles || shape.target!="Entity" }.forEach { handle ->
            val p=camera.screen(GizmoMath.handleWorld(shape,handle))
            val r=if (handle.kind=="move") 5.0 else 4.0
            val a=p+Vec2(-r,-r); val b=p+Vec2(r,-r); val c=p+Vec2(r,r); val d=p+Vec2(-r,r)
            squares.addAll(listOf(a,b,c,a,c,d))
        }
        draw(GL11.GL_TRIANGLES,squares)
    }
    /** Move tool: X/Y arrows in the axis colours and the free-move square; the dragged handle uses [highlight]. */
    fun moveHandles(origin: Vec2, camera: PreviewCamera, xColor: Color, yColor: Color, highlight: Color, active: DragAxis?) {
        fun quad(a: Vec2, b: Vec2, half: Vec2) = listOf(a-half,b-half,b+half,a-half,b+half,a+half)
        for((axis,color) in listOf(DragAxis.X to xColor,DragAxis.Y to yColor)) render(camera,if(active==axis) highlight else color) { draw ->
            val direction=if(axis==DragAxis.X) Vec2(1.0,0.0) else Vec2(0.0,-1.0)
            val side=Vec2(-direction.y,direction.x)
            val start=origin+direction*(MoveHandles.SQUARE+2);val tip=origin+direction*(MoveHandles.ARROW-10)
            draw(GL11.GL_TRIANGLES,quad(start,tip,side*1.0)+listOf(tip+side*5.0,tip-side*5.0,tip+direction*10.0))
        }
        render(camera,if(active==DragAxis.Free) highlight else xColor.copy(red=(xColor.red+yColor.red)/2,green=(xColor.green+yColor.green)/2,blue=(xColor.blue+yColor.blue)/2)) { draw ->
            val r=MoveHandles.SQUARE
            val corners=listOf(origin+Vec2(-r,-r),origin+Vec2(r,-r),origin+Vec2(r,r),origin+Vec2(-r,r))
            if(active==DragAxis.Free) draw(GL11.GL_TRIANGLES,listOf(corners[0],corners[1],corners[2],corners[0],corners[2],corners[3]))
            else draw(GL11.GL_LINES,(corners+corners.first()).zipWithNext().flatMap { (a,b) -> listOf(a,b) })
        }
    }
    /** Rotate tool: the ring in the blended axis colour (or [highlight] while dragging) and, during a
     * drag, spokes at the start and current screen angles of [sweep]. */
    fun rotateRing(center: Vec2, radius: Double, camera: PreviewCamera, xColor: Color, yColor: Color, highlight: Color, active: Boolean,
        sweep: Pair<Double, Double>?) = render(camera,if(active) highlight else blend(xColor,yColor)) { draw ->
        val ring=(0..72).map { i -> center+Vec2(cos(i*PI/36),sin(i*PI/36))*radius }
        val lines=ring.zipWithNext().flatMap { (a,b) -> listOf(a,b) }.toMutableList()
        // A second ring one pixel in makes the handle easier to see over sprites.
        (0..72).map { i -> center+Vec2(cos(i*PI/36),sin(i*PI/36))*(radius-1) }.zipWithNext().forEach { (a,b) -> lines.add(a);lines.add(b) }
        sweep?.let { (from,to) -> for(angle in listOf(from,to)) { lines.add(center);lines.add(center+Vec2(cos(angle),sin(angle))*radius) } }
        draw(GL11.GL_LINES,lines)
    }
    /** Scale tool: axis lines with end boxes in the axis colours and the uniform centre box; the dragged handle uses [highlight]. */
    fun scaleHandles(origin: Vec2, xTip: Vec2, yTip: Vec2, camera: PreviewCamera, xColor: Color, yColor: Color, highlight: Color, active: DragAxis?) {
        fun box(center: Vec2, half: Double) = listOf(center+Vec2(-half,-half),center+Vec2(half,-half),center+Vec2(half,half),
            center+Vec2(-half,-half),center+Vec2(half,half),center+Vec2(-half,half))
        val uniform=active==DragAxis.Free
        for((axis,tip,color) in listOf(Triple(DragAxis.X,xTip,xColor),Triple(DragAxis.Y,yTip,yColor))) render(camera,if(active==axis || uniform) highlight else color) { draw ->
            val direction=(tip-origin).let { it*(1.0/max(it.length(),1e-9)) }
            val side=Vec2(-direction.y,direction.x)
            val start=origin+direction*(ScaleHandles.CENTER+2)
            draw(GL11.GL_TRIANGLES,listOf(start-side,tip-side,tip+side,start-side,tip+side,start+side)+box(tip,5.0))
        }
        render(camera,if(uniform) highlight else blend(xColor,yColor)) { draw ->
            val r=ScaleHandles.CENTER
            val corners=listOf(origin+Vec2(-r,-r),origin+Vec2(r,-r),origin+Vec2(r,r),origin+Vec2(-r,r))
            if(uniform) draw(GL11.GL_TRIANGLES,box(origin,r))
            else draw(GL11.GL_LINES,(corners+corners.first()).zipWithNext().flatMap { (a,b) -> listOf(a,b) })
        }
    }
    private fun blend(a: Color, b: Color) = a.copy(red=(a.red+b.red)/2,green=(a.green+b.green)/2,blue=(a.blue+b.blue)/2)
    fun tileGhost(image: TileStampImage, stamps: List<Pair<Vec2, Vec2>>, camera: PreviewCamera) {
        val activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)
        GL13.glActiveTexture(GL13.GL_TEXTURE0)
        val previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)
        val previousSampler = GL11.glGetInteger(org.lwjgl.opengl.GL33.GL_SAMPLER_BINDING)
        val srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB)
        val dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB)
        val srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA)
        val dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA)
        try {
            org.lwjgl.opengl.GL33.glBindSampler(0, 0)
            val texture = stampTextures.remove(image) ?: run {
                if (stampTextures.size >= 16) {
                    val oldest = stampTextures.keys.first()
                    GL11.glDeleteTextures(checkNotNull(stampTextures.remove(oldest)))
                }
                val created = GL11.glGenTextures()
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, created)
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST)
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST)
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE)
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE)
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, image.imageWidth, image.imageHeight,
                    0, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, image.argb)
                created
            }
            stampTextures[image] = texture
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture)
            render(camera, Color.White.copy(alpha = 0.55f)) { draw ->
                val textured = GL20.glGetUniformLocation(program, "textured")
                GL20.glUniform1i(textured, 1)
                try {
                    GL11.glEnable(GL11.GL_BLEND)
                    GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA)
                    val vertices = buildList {
                        for ((bottomLeft, topRight) in stamps) {
                            val topLeft = Vec2(bottomLeft.x, topRight.y)
                            val bottomRight = Vec2(topRight.x, bottomLeft.y)
                            addAll(listOf(topLeft, topRight, bottomRight, topLeft, bottomRight, bottomLeft))
                        }
                    }
                    draw(GL11.GL_TRIANGLES, vertices)
                } finally {
                    GL20.glUniform1i(textured, 0)
                }
            }
        } finally {
            GL14.glBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha)
            org.lwjgl.opengl.GL33.glBindSampler(0, previousSampler)
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture)
            GL13.glActiveTexture(activeTexture)
        }
    }

    fun tiles(grid: List<Vec2>, stamps: List<Pair<Vec2, Vec2>>, camera: PreviewCamera, color: Color, eraseColor: Color, erase: Boolean) {
        if (grid.isNotEmpty()) render(camera, blend(color, Color.DarkGray)) { draw ->
            draw(GL11.GL_LINES, grid)
        }
        if (stamps.isNotEmpty()) render(camera, if (erase) eraseColor else color) { draw ->
            val lines = buildList {
                for ((a, c) in stamps) {
                    val b = Vec2(c.x, a.y)
                    val d = Vec2(a.x, c.y)
                    addAll(listOf(a, b, b, c, c, d, d, a))
                    if (erase) addAll(listOf(a, c, b, d))
                }
            }
            draw(GL11.GL_LINES, lines)
        }
    }

    /** Box selection rectangle in viewport pixels. */
    fun marquee(from: Vec2, to: Vec2, camera: PreviewCamera, color: Color) = render(camera, color) { draw ->
        val corners=listOf(from,Vec2(to.x,from.y),to,Vec2(from.x,to.y),from)
        draw(GL11.GL_LINES,corners.zipWithNext().flatMap { (a,b) -> listOf(a,b) })
    }
    private fun render(camera: PreviewCamera, color: Color, body: (draw: (Int, List<Vec2>) -> Unit) -> Unit) {
        val previousProgram=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)
        val previousVao=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING)
        val previousBuffer=GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING)
        val disabled=listOf(GL11.GL_DEPTH_TEST,GL11.GL_CULL_FACE,GL11.GL_SCISSOR_TEST,GL11.GL_BLEND).associateWith { GL11.glIsEnabled(it) }
        try {
            if (program == 0) initialize()
            disabled.keys.forEach { GL11.glDisable(it) }
            GL20.glUseProgram(program)
            GL20.glUniform4f(GL20.glGetUniformLocation(program,"selectionColor"),color.red,color.green,color.blue,color.alpha)
            GL30.glBindVertexArray(vao); GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,buffer)
            GL20.glEnableVertexAttribArray(0); GL20.glVertexAttribPointer(0,2,GL11.GL_FLOAT,false,0,0L)
            fun draw(mode: Int, points: List<Vec2>) {
                if (points.isEmpty()) return
                val data=FloatArray(points.size*2)
                points.forEachIndexed { i,p -> data[i*2]=(p.x/camera.width*2-1).toFloat(); data[i*2+1]=(1-p.y/camera.height*2).toFloat() }
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER,data,GL15.GL_STREAM_DRAW)
                GL11.glDrawArrays(mode,0,points.size)
            }
            body(::draw)
        } finally {
            GL20.glUseProgram(previousProgram); GL30.glBindVertexArray(previousVao); GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,previousBuffer)
            disabled.forEach { (cap,enabled) -> if (enabled) GL11.glEnable(cap) else GL11.glDisable(cap) }
        }
    }
    fun dispose() {
        if (buffer != 0) GL15.glDeleteBuffers(buffer)
        if (vao != 0) GL30.glDeleteVertexArrays(vao)
        if (program != 0) GL20.glDeleteProgram(program)
        stampTextures.values.forEach { GL11.glDeleteTextures(it) }
        stampTextures.clear()
        program=0; vao=0; buffer=0
    }
}
