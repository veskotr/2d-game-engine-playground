package sle.editor.viewport

import sle.editor.app.main
import sle.editor.model.EntityDocument

import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL30
import kotlin.math.*

/** Editor geometry drawn directly after SLE, in the same host framebuffer. */
internal class GizmoOverlay {
    private var program = 0
    private var vao = 0
    private var buffer = 0
    private fun initialize() {
        fun shader(type: Int, source: String): Int {
            val shader = GL20.glCreateShader(type)
            GL20.glShaderSource(shader, source); GL20.glCompileShader(shader)
            check(GL20.glGetShaderi(shader,GL20.GL_COMPILE_STATUS) != 0) { GL20.glGetShaderInfoLog(shader) }
            return shader
        }
        val vertex = shader(GL20.GL_VERTEX_SHADER,"#version 330 core\nlayout(location=0) in vec2 p; void main(){gl_Position=vec4(p,0,1);}")
        val fragment = shader(GL20.GL_FRAGMENT_SHADER,"#version 330 core\nout vec4 color; void main(){color=vec4(1.0,0.55,1.0,1.0);}")
        program = GL20.glCreateProgram()
        GL20.glAttachShader(program,vertex); GL20.glAttachShader(program,fragment); GL20.glLinkProgram(program)
        GL20.glDeleteShader(vertex); GL20.glDeleteShader(fragment)
        check(GL20.glGetProgrami(program,GL20.GL_LINK_STATUS) != 0) { GL20.glGetProgramInfoLog(program) }
        vao=GL30.glGenVertexArrays(); buffer=GL15.glGenBuffers()
    }
    fun draw(shape: GizmoShape, camera: PreviewCamera, entity: EntityDocument? = null) {
        val previousProgram=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)
        val previousVao=GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING)
        val previousBuffer=GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING)
        val disabled=listOf(GL11.GL_DEPTH_TEST,GL11.GL_CULL_FACE,GL11.GL_SCISSOR_TEST,GL11.GL_BLEND).associateWith { GL11.glIsEnabled(it) }
        try {
            if (program == 0) initialize()
            disabled.keys.forEach { GL11.glDisable(it) }
            GL20.glUseProgram(program); GL30.glBindVertexArray(vao); GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,buffer)
            GL20.glEnableVertexAttribArray(0); GL20.glVertexAttribPointer(0,2,GL11.GL_FLOAT,false,0,0L)
            fun draw(mode: Int, points: List<Vec2>) {
                if (points.isEmpty()) return
                val data=FloatArray(points.size*2)
                points.forEachIndexed { i,p -> data[i*2]=(p.x/camera.width*2-1).toFloat(); data[i*2+1]=(1-p.y/camera.height*2).toFloat() }
                GL15.glBufferData(GL15.GL_ARRAY_BUFFER,data,GL15.GL_STREAM_DRAW)
                GL11.glDrawArrays(mode,0,points.size)
            }
            val lines=mutableListOf<Vec2>()
            if (shape.target == "Entity") {
                val p=camera.screen(shape.position)
                lines.addAll(listOf(p-Vec2(24.0,0.0),p+Vec2(24.0,0.0),p-Vec2(0.0,24.0),p+Vec2(0.0,24.0)))
                if(entity!=null) {
                    val bounds=entitySelectionBounds(entity,camera)
                    bounds.zipWithNext().forEach { (a,b) -> lines.add(a);lines.add(b) }
                }
            } else if (shape.editable) {
                val outline=if (shape.circle) (0..64).map { i -> camera.screen(shape.center + Vec2(cos(i*PI/32),sin(i*PI/32))*shape.worldRadius) }
                else listOf(-1 to -1,1 to -1,1 to 1,-1 to 1,-1 to -1).map { (x,y) -> camera.screen(shape.world(shape.offset+Vec2(shape.size.x*x/2,shape.size.y*y/2))) }
                outline.zipWithNext().forEach { (a,b) -> lines.add(a); lines.add(b) }
            }
            draw(GL11.GL_LINES,lines)
            val squares=mutableListOf<Vec2>()
            GizmoMath.handles(shape).forEach { handle ->
                val p=camera.screen(GizmoMath.handleWorld(shape,handle))
                val r=if (handle.kind=="move") 5.0 else 4.0
                val a=p+Vec2(-r,-r); val b=p+Vec2(r,-r); val c=p+Vec2(r,r); val d=p+Vec2(-r,r)
                squares.addAll(listOf(a,b,c,a,c,d))
            }
            draw(GL11.GL_TRIANGLES,squares)
        } finally {
            GL20.glUseProgram(previousProgram); GL30.glBindVertexArray(previousVao); GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER,previousBuffer)
            disabled.forEach { (cap,enabled) -> if (enabled) GL11.glEnable(cap) else GL11.glDisable(cap) }
        }
    }
    fun dispose() {
        if (buffer != 0) GL15.glDeleteBuffers(buffer)
        if (vao != 0) GL30.glDeleteVertexArrays(vao)
        if (program != 0) GL20.glDeleteProgram(program)
        program=0; vao=0; buffer=0
    }
}
