package com.lupadered.app.ar

import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** Lo que el dibujo de encima (Compose) necesita de cada cuadro de la cámara. */
class ArFrameInfo(
    val tracking: Boolean,
    /** Posición del teléfono en metros (eje Y hacia arriba). */
    val camX: Float,
    val camY: Float,
    val camZ: Float,
    /** Altura del piso detectado; null si todavía no vemos ninguno. */
    val floorY: Float?,
    val view: FloatArray,
    val projection: FloatArray,
)

/**
 * Pinta la imagen de la cámara de fondo (como en las muestras oficiales de ARCore)
 * y en cada cuadro le cuenta a [onFrame] dónde está el teléfono.
 * Todo esto corre en el hilo de OpenGL.
 */
class ArCameraRenderer(
    private val session: Session,
    private val displayRotation: () -> Int,
    private val onFrame: (ArFrameInfo) -> Unit,
) : GLSurfaceView.Renderer {

    private val background = CameraBackground()
    private var lowestFloor: Float? = null
    private var frameCount = 0

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        background.create()
        session.setCameraTextureName(background.textureId)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        session.setDisplayGeometry(displayRotation(), width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val frame: Frame = try {
            session.update()
        } catch (_: Throwable) {
            return // sesión en pausa o cámara ocupada: lo intentamos en el siguiente cuadro
        }
        background.draw(frame)
        val camera = frame.camera
        val tracking = camera.trackingState == TrackingState.TRACKING

        // El piso es el plano horizontal "hacia arriba" más bajo que haya encontrado ARCore
        if (frameCount++ % 15 == 0) {
            session.getAllTrackables(Plane::class.java)
                .filter { it.trackingState == TrackingState.TRACKING && it.type == Plane.Type.HORIZONTAL_UPWARD_FACING }
                .minOfOrNull { it.centerPose.ty() }
                ?.let { y -> lowestFloor = lowestFloor?.let { minOf(it, y) } ?: y }
        }

        val view = FloatArray(16)
        val projection = FloatArray(16)
        val pose = camera.displayOrientedPose
        if (tracking) {
            camera.getViewMatrix(view, 0)
            camera.getProjectionMatrix(projection, 0, 0.05f, 50f)
        }
        // Un piso detectado muy por encima del teléfono no tiene sentido (sería una mesa)
        val floor = lowestFloor?.takeIf { it < pose.ty() - 0.4f }
        onFrame(ArFrameInfo(tracking, pose.tx(), pose.ty(), pose.tz(), floor, view, projection))
    }
}

/** Dibuja la imagen de la cámara ocupando toda la pantalla. */
private class CameraBackground {
    var textureId = -1
        private set
    private var program = 0
    private var positionAttr = 0
    private var texCoordAttr = 0

    private val quad: FloatBuffer = floatBuffer(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
    private val texCoords: FloatBuffer = floatBuffer(FloatArray(8))

    fun create() {
        val tex = IntArray(1)
        GLES20.glGenTextures(1, tex, 0)
        textureId = tex[0]
        val target = GLES11Ext.GL_TEXTURE_EXTERNAL_OES
        GLES20.glBindTexture(target, textureId)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)

        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, shader(GLES20.GL_VERTEX_SHADER, VERTEX))
        GLES20.glAttachShader(program, shader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT))
        GLES20.glLinkProgram(program)
        positionAttr = GLES20.glGetAttribLocation(program, "a_Position")
        texCoordAttr = GLES20.glGetAttribLocation(program, "a_TexCoord")
    }

    fun draw(frame: Frame) {
        if (frame.hasDisplayGeometryChanged()) {
            quad.position(0)
            texCoords.position(0)
            frame.transformCoordinates2d(
                Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES, quad,
                Coordinates2d.TEXTURE_NORMALIZED, texCoords,
            )
        }
        if (frame.timestamp == 0L) return // la cámara todavía no entrega imágenes

        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthMask(false)
        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        quad.position(0)
        GLES20.glVertexAttribPointer(positionAttr, 2, GLES20.GL_FLOAT, false, 0, quad)
        texCoords.position(0)
        GLES20.glVertexAttribPointer(texCoordAttr, 2, GLES20.GL_FLOAT, false, 0, texCoords)
        GLES20.glEnableVertexAttribArray(positionAttr)
        GLES20.glEnableVertexAttribArray(texCoordAttr)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(positionAttr)
        GLES20.glDisableVertexAttribArray(texCoordAttr)
        GLES20.glDepthMask(true)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
    }

    private fun shader(type: Int, code: String): Int {
        val s = GLES20.glCreateShader(type)
        GLES20.glShaderSource(s, code)
        GLES20.glCompileShader(s)
        return s
    }

    companion object {
        private const val VERTEX = """
            attribute vec4 a_Position;
            attribute vec2 a_TexCoord;
            varying vec2 v_TexCoord;
            void main() {
                gl_Position = a_Position;
                v_TexCoord = a_TexCoord;
            }
        """
        private const val FRAGMENT = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 v_TexCoord;
            uniform samplerExternalOES sTexture;
            void main() {
                gl_FragColor = texture2D(sTexture, v_TexCoord);
            }
        """

        private fun floatBuffer(values: FloatArray): FloatBuffer =
            ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(values)
                position(0)
            }
    }
}
