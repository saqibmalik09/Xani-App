package app.xani.core.lens

import android.graphics.Bitmap
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.opengl.GLUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Thin GLES 3.0 helpers for the lens pipeline. Every call assumes a context is
 * current on this thread; the host ([LensEngine] / the video processor) owns
 * the context and the thread, this file never touches EGL.
 */
internal object GlKit {
    /** x, y, u, v — two triangles covering clip space, texcoord origin bottom-left. */
    private val QUAD: FloatBuffer = floatArrayOf(
        -1f, -1f, 0f, 0f,
        1f, -1f, 1f, 0f,
        -1f, 1f, 0f, 1f,
        1f, 1f, 1f, 1f,
    ).toBuffer()

    fun FloatArray.toBuffer(): FloatBuffer =
        ByteBuffer.allocateDirect(size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().also {
            it.put(this)
            it.position(0)
        }

    fun compile(type: Int, src: String): Int {
        val id = GLES30.glCreateShader(type)
        GLES30.glShaderSource(id, src)
        GLES30.glCompileShader(id)
        val ok = IntArray(1)
        GLES30.glGetShaderiv(id, GLES30.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(id)
            GLES30.glDeleteShader(id)
            throw IllegalStateException("lens shader: $log")
        }
        return id
    }

    fun link(vs: String, fs: String): Int {
        val v = compile(GLES30.GL_VERTEX_SHADER, vs)
        val f = compile(GLES30.GL_FRAGMENT_SHADER, fs)
        val p = GLES30.glCreateProgram()
        GLES30.glAttachShader(p, v)
        GLES30.glAttachShader(p, f)
        GLES30.glLinkProgram(p)
        GLES30.glDeleteShader(v)
        GLES30.glDeleteShader(f)
        val ok = IntArray(1)
        GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(p)
            GLES30.glDeleteProgram(p)
            throw IllegalStateException("lens program: $log")
        }
        return p
    }

    fun texture2D(
        w: Int,
        h: Int,
        internalFormat: Int = GLES30.GL_RGBA8,
        format: Int = GLES30.GL_RGBA,
        type: Int = GLES30.GL_UNSIGNED_BYTE,
        linear: Boolean = true,
    ): Int {
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ids[0])
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, internalFormat, w, h, 0, format, type, null)
        val f = if (linear) GLES30.GL_LINEAR else GLES30.GL_NEAREST
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, f)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, f)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        return ids[0]
    }

    fun textureFrom(bitmap: Bitmap): Int {
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ids[0])
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        return ids[0]
    }

    fun oesTexture(): Int {
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, ids[0])
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
        return ids[0]
    }

    fun framebuffer(texture: Int): Int {
        val ids = IntArray(1)
        GLES30.glGenFramebuffers(1, ids, 0)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, ids[0])
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, texture, 0)
        val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        if (status != GLES30.GL_FRAMEBUFFER_COMPLETE) {
            GLES30.glDeleteFramebuffers(1, ids, 0)
            throw IllegalStateException("lens fbo incomplete: $status")
        }
        return ids[0]
    }

    fun deleteTexture(id: Int) {
        if (id != 0) GLES30.glDeleteTextures(1, intArrayOf(id), 0)
    }

    fun deleteFramebuffer(id: Int) {
        if (id != 0) GLES30.glDeleteFramebuffers(1, intArrayOf(id), 0)
    }

    /** Full-screen quad with the given attribute slots; program must be in use. */
    fun drawQuad(aPos: Int, aTex: Int) {
        QUAD.position(0)
        GLES30.glEnableVertexAttribArray(aPos)
        GLES30.glVertexAttribPointer(aPos, 2, GLES30.GL_FLOAT, false, 16, QUAD)
        QUAD.position(2)
        GLES30.glEnableVertexAttribArray(aTex)
        GLES30.glVertexAttribPointer(aTex, 2, GLES30.GL_FLOAT, false, 16, QUAD)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glDisableVertexAttribArray(aPos)
        GLES30.glDisableVertexAttribArray(aTex)
        QUAD.position(0)
    }

    /** An arbitrary quad: 4 vertices of x, y, u, v (clip-space positions). */
    fun drawQuad(aPos: Int, aTex: Int, verts: FloatBuffer) {
        verts.position(0)
        GLES30.glEnableVertexAttribArray(aPos)
        GLES30.glVertexAttribPointer(aPos, 2, GLES30.GL_FLOAT, false, 16, verts)
        verts.position(2)
        GLES30.glEnableVertexAttribArray(aTex)
        GLES30.glVertexAttribPointer(aTex, 2, GLES30.GL_FLOAT, false, 16, verts)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glDisableVertexAttribArray(aPos)
        GLES30.glDisableVertexAttribArray(aTex)
        verts.position(0)
    }

    fun bind(unit: Int, target: Int, texture: Int, uniform: Int) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit)
        GLES30.glBindTexture(target, texture)
        GLES30.glUniform1i(uniform, unit)
    }

    val IDENTITY: FloatArray = FloatArray(16).also { android.opengl.Matrix.setIdentityM(it, 0) }
}

/** A linked program with cached attribute/uniform slots. */
internal class Prog(vs: String, fs: String) {
    val id: Int = GlKit.link(vs, fs)
    val aPos: Int = GLES30.glGetAttribLocation(id, "aPos")
    val aTex: Int = GLES30.glGetAttribLocation(id, "aTex")
    private val uniforms = HashMap<String, Int>()

    fun use() {
        GLES30.glUseProgram(id)
    }

    fun u(name: String): Int = uniforms.getOrPut(name) { GLES30.glGetUniformLocation(id, name) }

    fun set(name: String, v: Float) {
        GLES30.glUniform1f(u(name), v)
    }

    fun set(name: String, x: Float, y: Float) {
        GLES30.glUniform2f(u(name), x, y)
    }

    fun set(name: String, x: Float, y: Float, z: Float) {
        GLES30.glUniform3f(u(name), x, y, z)
    }

    fun set(name: String, v: Int) {
        GLES30.glUniform1i(u(name), v)
    }

    fun setMat4(name: String, m: FloatArray) {
        GLES30.glUniformMatrix4fv(u(name), 1, false, m, 0)
    }

    fun setColor(name: String, argb: Int) {
        GLES30.glUniform3f(
            u(name),
            ((argb shr 16) and 0xFF) / 255f,
            ((argb shr 8) and 0xFF) / 255f,
            (argb and 0xFF) / 255f,
        )
    }

    fun release() {
        GLES30.glDeleteProgram(id)
    }
}

/** Texture + framebuffer pair at a fixed size. */
internal class Target(val w: Int, val h: Int, internalFormat: Int = GLES30.GL_RGBA8, format: Int = GLES30.GL_RGBA) {
    val tex: Int = GlKit.texture2D(w, h, internalFormat, format)
    val fbo: Int = GlKit.framebuffer(tex)

    fun bind() {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
        GLES30.glViewport(0, 0, w, h)
    }

    fun release() {
        GlKit.deleteFramebuffer(fbo)
        GlKit.deleteTexture(tex)
    }
}
