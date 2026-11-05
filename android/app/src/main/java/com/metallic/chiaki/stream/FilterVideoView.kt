// SPDX-License-Identifier: LicenseRef-AGPL-3.0-only-OpenSSL
package com.gameblabla.chiaki.stream

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.view.Surface
import androidx.preference.PreferenceManager
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** MediaCodec/MediaPlayer render to the external texture; this view applies an approximate equalizer. */
class FilterVideoView(context: Context, attrs: AttributeSet? = null) : GLSurfaceView(context, attrs) {
    var onInputSurface: ((Surface?) -> Unit)? = null
        set(value) {
            field = value
            deliveredSurface = null
            if (active) deliverInput(renderer.currentSurface())
        }
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    private val renderer = VideoRenderer()
    private val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key?.startsWith("filter_") == true) { updateFilters(); requestRender() }
    }
    @Volatile private var generation = 0
    @Volatile private var active = false
    // Only accessed on the UI thread, like the producer callback.
    private var deliveredSurface: Surface? = null

    init {
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
        updateFilters()
    }
    private fun updateFilters() {
        fun value(key: String): Int = try { prefs.getInt("filter_$key", 0) } catch (_: ClassCastException) { 0 }
        renderer.values = FilterValues(value("brightness"), value("saturation"), value("gamma"),
            value("contrast"), value("hue"), value("sharpness"))
    }
    fun resumeVideo() {
        active = true
        updateFilters()
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onResume()
        deliverInput(renderer.currentSurface())
    }
    private fun deliverInput(input: Surface?) {
        if (active && onInputSurface != null && input != null && input !== deliveredSurface) {
            onInputSurface?.invoke(input)
            deliveredSurface = input
        }
    }
    fun pauseVideo() {
        active = false
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
        generation++
        onInputSurface?.invoke(null)
        deliveredSurface = null
        // onPause waits until the GL thread stops using its input. Releasing via a
        // queued event can instead run after a subsequent resume and destroy its input.
        onPause()
        renderer.releaseInput()
    }
    private inner class VideoRenderer : Renderer {
        @Volatile var values = FilterValues(0, 0, 0, 0, 0, 0)
        private var texture: SurfaceTexture? = null
        @Volatile private var surface: Surface? = null
        private var program = 0
        private val matrix = FloatArray(16)
        private val vertices = ByteBuffer.allocateDirect(16 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(floatArrayOf(-1f,-1f,0f,0f, 1f,-1f,1f,0f, -1f,1f,0f,1f, 1f,1f,1f,1f)); position(0)
        }
        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            val oldSurface = surface
            val oldTexture = texture
            program = link(VERTEX, FRAGMENT)
            val ids = IntArray(1)
            GLES20.glGenTextures(1, ids, 0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, ids[0])
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            val st = SurfaceTexture(ids[0])
            texture = st
            val input = Surface(st)
            surface = input
            st.setOnFrameAvailableListener { requestRender() }
            val current = generation
            // Deliver every replacement in order; the producer must detach before its old
            // SurfaceTexture is abandoned, even if another recreation has already occurred.
            post {
                try {
                    // A newer GL surface may already exist. Switch to the newest one
                    // before retiring the old input, even for a superseded callback.
                    val input = surface
                    if (active && current == generation && onInputSurface != null && input != null && input !== deliveredSurface) {
                        onInputSurface?.invoke(input)
                        deliveredSurface = input
                    }
                } finally {
                    oldSurface?.release()
                    oldTexture?.release()
                }
            }
        }
        fun currentSurface(): Surface? = surface
        fun releaseInput() {
            surface?.release(); surface = null
            texture?.release(); texture = null
        }
        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) { GLES20.glViewport(0, 0, width, height) }
        override fun onDrawFrame(gl: GL10?) {
            val st = texture ?: return
            st.updateTexImage(); st.getTransformMatrix(matrix)
            GLES20.glUseProgram(program)
            val pos = GLES20.glGetAttribLocation(program, "position")
            val uv = GLES20.glGetAttribLocation(program, "texCoord")
            vertices.position(0); GLES20.glVertexAttribPointer(pos, 2, GLES20.GL_FLOAT, false, 16, vertices); GLES20.glEnableVertexAttribArray(pos)
            vertices.position(2); GLES20.glVertexAttribPointer(uv, 2, GLES20.GL_FLOAT, false, 16, vertices); GLES20.glEnableVertexAttribArray(uv)
            GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "texMatrix"), 1, false, matrix, 0)
            val v = values
            GLES20.glUniform4f(GLES20.glGetUniformLocation(program, "color"), v.brightness / 100f, v.saturation / 100f, v.gamma / 100f, v.contrast / 100f)
            GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "hue"), v.hue / 100f)
            GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "sharpness"), v.sharpness / 5f)
            // One output pixel; the sampling footprint is deliberately approximate when scaled.
            GLES20.glUniform2f(GLES20.glGetUniformLocation(program, "pixel"), 1f / width.coerceAtLeast(1), 1f / height.coerceAtLeast(1))
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        }
    }
    companion object {
        private fun link(vertex: String, fragment: String): Int {
            fun compile(type: Int, source: String): Int {
                val shader = GLES20.glCreateShader(type); GLES20.glShaderSource(shader, source); GLES20.glCompileShader(shader)
                val status = IntArray(1); GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
                check(status[0] != 0) { GLES20.glGetShaderInfoLog(shader) }; return shader
            }
            val p = GLES20.glCreateProgram(); val vs = compile(GLES20.GL_VERTEX_SHADER, vertex); val fs = compile(GLES20.GL_FRAGMENT_SHADER, fragment)
            GLES20.glAttachShader(p, vs); GLES20.glAttachShader(p, fs); GLES20.glLinkProgram(p)
            val status = IntArray(1); GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, status, 0)
            check(status[0] != 0) { GLES20.glGetProgramInfoLog(p) }
            GLES20.glDeleteShader(vs); GLES20.glDeleteShader(fs); return p
        }
        private const val VERTEX = """attribute vec4 position; attribute vec4 texCoord; uniform mat4 texMatrix; varying vec2 uv; void main() { gl_Position=position; uv=(texMatrix*texCoord).xy; }"""
        private const val FRAGMENT = """#extension GL_OES_EGL_image_external : require
precision mediump float;
uniform samplerExternalOES image; uniform vec4 color; uniform float hue; uniform float sharpness; uniform vec2 pixel; varying vec2 uv;
void main() {
 vec3 c=texture2D(image,uv).rgb;
 if (sharpness != 0.0) {
  vec3 blur=(texture2D(image,uv+vec2(pixel.x,0.0)).rgb+texture2D(image,uv-vec2(pixel.x,0.0)).rgb+texture2D(image,uv+vec2(0.0,pixel.y)).rgb+texture2D(image,uv-vec2(0.0,pixel.y)).rgb)*0.25;
  c += sharpness*(c-blur);
 }
 float y=dot(c,vec3(0.299,0.587,0.114));
 if (hue != 0.0) {
  float i=dot(c,vec3(0.596,-0.274,-0.322)); float q=dot(c,vec3(0.211,-0.523,0.312));
  float a=hue*3.14159265; float ni=i*cos(a)-q*sin(a); float nq=i*sin(a)+q*cos(a);
  c=vec3(y+0.956*ni+0.621*nq,y-0.272*ni-0.647*nq,y-1.106*ni+1.703*nq);
 }
 c=mix(vec3(y),c,1.0+color.y); c=(c-0.5)*(1.0+color.w)+0.5+color.x;
 c=pow(max(c,vec3(0.0)),vec3(exp2(-color.z))); gl_FragColor=vec4(clamp(c,0.0,1.0),1.0);
}"""
    }
}
