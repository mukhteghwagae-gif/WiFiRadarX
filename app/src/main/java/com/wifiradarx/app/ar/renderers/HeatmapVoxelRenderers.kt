package com.wifiradarx.app.ar.renderers

import android.opengl.GLES20
import android.opengl.Matrix
import com.wifiradarx.app.ar.shaders.ShaderUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class HeatmapMeshRenderer {
    private var program = 0
    private var mvpMatrixHandle = 0
    private var positionHandle = 0
    private var rssiHandle = 0
    private var vertexBuffer: FloatBuffer? = null
    private var rssiBuffer: FloatBuffer? = null
    private var vertexCount = 0

    fun init() {
        program = ShaderUtils.createProgram(
            ShaderUtils.HEATMAP_VERTEX,
            ShaderUtils.HEATMAP_FRAGMENT
        )
        mvpMatrixHandle = GLES20.glGetUniformLocation(program, "u_ModelViewProjection")
        positionHandle  = GLES20.glGetAttribLocation(program, "a_Position")
        rssiHandle      = GLES20.glGetAttribLocation(program, "a_Rssi")
    }

    fun updateMesh(
        grid: Array<FloatArray>,
        minX: Float,
        minZ: Float,
        y: Float,
        resolution: Float
    ) {
        val rows = grid.size
        val cols = grid[0].size
        vertexCount = (rows - 1) * (cols - 1) * 6

        val vertices = FloatArray(vertexCount * 3)
        val rssis    = FloatArray(vertexCount)

        var vIdx = 0
        var rIdx = 0
        for (i in 0 until rows - 1) {
            for (j in 0 until cols - 1) {
                val x1 = minX + i * resolution
                val z1 = minZ + j * resolution
                val x2 = minX + (i + 1) * resolution
                val z2 = minZ + (j + 1) * resolution

                // Triangle 1
                vertices[vIdx++] = x1; vertices[vIdx++] = y; vertices[vIdx++] = z1
                rssis[rIdx++] = grid[i][j]
                vertices[vIdx++] = x2; vertices[vIdx++] = y; vertices[vIdx++] = z1
                rssis[rIdx++] = grid[i + 1][j]
                vertices[vIdx++] = x1; vertices[vIdx++] = y; vertices[vIdx++] = z2
                rssis[rIdx++] = grid[i][j + 1]

                // Triangle 2
                vertices[vIdx++] = x2; vertices[vIdx++] = y; vertices[vIdx++] = z1
                rssis[rIdx++] = grid[i + 1][j]
                vertices[vIdx++] = x2; vertices[vIdx++] = y; vertices[vIdx++] = z2
                rssis[rIdx++] = grid[i + 1][j + 1]
                vertices[vIdx++] = x1; vertices[vIdx++] = y; vertices[vIdx++] = z2
                rssis[rIdx++] = grid[i][j + 1]
            }
        }

        vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4).run {
            order(ByteOrder.nativeOrder())
            asFloatBuffer().apply { put(vertices); position(0) }
        }
        rssiBuffer = ByteBuffer.allocateDirect(rssis.size * 4).run {
            order(ByteOrder.nativeOrder())
            asFloatBuffer().apply { put(rssis); position(0) }
        }
    }

    fun draw(viewMatrix: FloatArray, projectionMatrix: FloatArray) {
        if (vertexBuffer == null || rssiBuffer == null || vertexCount == 0) return

        GLES20.glUseProgram(program)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        val modelMatrix = FloatArray(16).also { Matrix.setIdentityM(it, 0) }
        val mvMatrix    = FloatArray(16)
        val mvpMatrix   = FloatArray(16)
        Matrix.multiplyMM(mvMatrix,  0, viewMatrix,       0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, mvMatrix,    0)

        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)

        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(
            positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer
        )

        GLES20.glEnableVertexAttribArray(rssiHandle)
        GLES20.glVertexAttribPointer(
            rssiHandle, 1, GLES20.GL_FLOAT, false, 0, rssiBuffer
        )

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount)

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(rssiHandle)
        GLES20.glDisable(GLES20.GL_BLEND)
    }
}

class VoxelMapRenderer {
    private var program = 0
    private var mvpMatrixHandle = 0

    fun init() {
        // Stub — extend for full 3D tomography rendering
    }

    fun draw(viewMatrix: FloatArray, projectionMatrix: FloatArray) {
        // Stub — no-op until voxel geometry is populated
    }
}
