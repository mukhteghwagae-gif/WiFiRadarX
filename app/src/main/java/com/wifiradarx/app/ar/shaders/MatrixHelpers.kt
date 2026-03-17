package com.wifiradarx.app.ar.shaders

import android.opengl.Matrix
import kotlin.math.*

object MatrixHelpers {

    fun createMvp(model: FloatArray, view: FloatArray, projection: FloatArray): FloatArray {
        val mv = FloatArray(16)
        val mvp = FloatArray(16)
        Matrix.multiplyMM(mv, 0, view, 0, model, 0)
        Matrix.multiplyMM(mvp, 0, projection, 0, mv, 0)
        return mvp
    }

    fun identity(): FloatArray = FloatArray(16).also { Matrix.setIdentityM(it, 0) }

    fun translation(x: Float, y: Float, z: Float): FloatArray =
        identity().also { Matrix.translateM(it, 0, x, y, z) }

    fun rotation(angle: Float, x: Float, y: Float, z: Float): FloatArray =
        identity().also { Matrix.rotateM(it, 0, angle, x, y, z) }

    fun scale(x: Float, y: Float, z: Float): FloatArray =
        identity().also { Matrix.scaleM(it, 0, x, y, z) }

    fun lookAt(
        eyeX: Float, eyeY: Float, eyeZ: Float,
        cx: Float, cy: Float, cz: Float,
        ux: Float, uy: Float, uz: Float
    ): FloatArray = FloatArray(16).also {
        Matrix.setLookAtM(it, 0, eyeX, eyeY, eyeZ, cx, cy, cz, ux, uy, uz)
    }

    fun perspective(fovDeg: Float, aspect: Float, near: Float, far: Float): FloatArray =
        FloatArray(16).also { Matrix.perspectiveM(it, 0, fovDeg, aspect, near, far) }

    fun slerp(q1: FloatArray, q2In: FloatArray, t: Float): FloatArray {
        val q2 = q2In.copyOf()
        var dot = q1[0]*q2[0] + q1[1]*q2[1] + q1[2]*q2[2] + q1[3]*q2[3]
        if (dot < 0f) { dot = -dot; for (i in 0..3) q2[i] = -q2[i] }
        val result = FloatArray(4)
        if (dot > 0.9995f) {
            for (i in 0..3) result[i] = q1[i] + t * (q2[i] - q1[i])
        } else {
            val theta0 = acos(dot.toDouble()).toFloat()
            val theta = theta0 * t
            val sinT = sin(theta.toDouble()).toFloat()
            val sinT0 = sin(theta0.toDouble()).toFloat()
            val s0 = cos(theta.toDouble()).toFloat() - dot * sinT / sinT0
            val s1 = sinT / sinT0
            for (i in 0..3) result[i] = s0 * q1[i] + s1 * q2[i]
        }
        return result
    }
}
