package com.example.service

import android.animation.ValueAnimator
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.SweepGradient
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import com.example.data.JarvisPreferences

class BorderlightOverlayService : Service() {

    companion object {
        private const val TAG = "BorderlightService"

        @Volatile
        var isRunning: Boolean = false
            private set

        fun start(context: Context) {
            if (!Settings.canDrawOverlays(context)) {
                Log.w(TAG, "Cannot start BorderlightOverlayService: Overlay permission not granted.")
                return
            }
            try {
                val intent = Intent(context, BorderlightOverlayService::class.java)
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start BorderlightOverlayService: ${e.message}")
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, BorderlightOverlayService::class.java)
                context.stopService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop BorderlightOverlayService: ${e.message}")
            }
        }

        fun toggle(context: Context): Boolean {
            return if (isRunning) {
                stop(context)
                false
            } else {
                start(context)
                true
            }
        }
    }

    private var windowManager: WindowManager? = null
    private var overlayView: BorderlightView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        try {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            overlayView = BorderlightView(this)

            val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )

            windowManager?.addView(overlayView, params)
            isRunning = true
            Log.d(TAG, "Borderlight overlay attached to screen window.")
        } catch (e: Exception) {
            Log.e(TAG, "Error attaching Borderlight overlay: ${e.message}")
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        try {
            overlayView?.stopAnimation()
            if (overlayView != null && windowManager != null) {
                windowManager?.removeView(overlayView)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error removing Borderlight overlay view: ${e.message}")
        }
        overlayView = null
        windowManager = null
        Log.d(TAG, "Borderlight overlay service destroyed.")
    }

    /**
     * Custom view that renders an animated, rotating RGB gradient around the display edges and corners.
     */
    class BorderlightView(context: Context) : View(context) {

        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 14f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 26f
            alpha = 110
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        private val neonColors = intArrayOf(
            Color.parseColor("#FF0055"), // Magenta Red
            Color.parseColor("#00E5FF"), // Neon Cyan
            Color.parseColor("#FFD600"), // Golden Yellow
            Color.parseColor("#00E676"), // Neon Emerald
            Color.parseColor("#D500F9"), // Electric Purple
            Color.parseColor("#2979FF"), // Vivid Blue
            Color.parseColor("#FF0055")  // Loop back
        )

        private val colorPositions = floatArrayOf(
            0.0f, 0.16f, 0.33f, 0.50f, 0.66f, 0.83f, 1.0f
        )

        private var rotationAngle = 0f
        private var animator: ValueAnimator? = null
        private val matrix = Matrix()
        private val rectF = RectF()
        private var cornerRadius = 60f

        init {
            setLayerType(LAYER_TYPE_SOFTWARE, null)
            val dm: DisplayMetrics = context.resources.displayMetrics
            cornerRadius = 32f * dm.density
            borderPaint.strokeWidth = 5f * dm.density
            glowPaint.strokeWidth = 12f * dm.density

            startAnimation()
        }

        fun startAnimation() {
            animator?.cancel()
            animator = ValueAnimator.ofFloat(0f, 360f).apply {
                duration = 2400L
                interpolator = LinearInterpolator()
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART
                addUpdateListener { va ->
                    rotationAngle = va.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }

        fun stopAnimation() {
            animator?.cancel()
            animator = null
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            val halfStroke = borderPaint.strokeWidth / 2f
            rectF.set(halfStroke, halfStroke, w - halfStroke, h - halfStroke)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (width <= 0 || height <= 0) return

            val cx = width / 2f
            val cy = height / 2f

            val shader = SweepGradient(cx, cy, neonColors, colorPositions)
            matrix.setRotate(rotationAngle, cx, cy)
            shader.setLocalMatrix(matrix)

            borderPaint.shader = shader
            glowPaint.shader = shader

            // Outer soft neon halo
            canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, glowPaint)
            // Crisp inner bright beam
            canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, borderPaint)

            // Prominent 4-corner neon light brackets
            val cornerSpan = cornerRadius * 1.35f
            val cornerPaint = Paint(borderPaint).apply {
                strokeWidth = borderPaint.strokeWidth * 1.5f
            }
            canvas.drawArc(rectF.left, rectF.top, rectF.left + cornerSpan * 2, rectF.top + cornerSpan * 2, 180f, 90f, false, cornerPaint)
            canvas.drawArc(rectF.right - cornerSpan * 2, rectF.top, rectF.right, rectF.top + cornerSpan * 2, 270f, 90f, false, cornerPaint)
            canvas.drawArc(rectF.right - cornerSpan * 2, rectF.bottom - cornerSpan * 2, rectF.right, rectF.bottom, 0f, 90f, false, cornerPaint)
            canvas.drawArc(rectF.left, rectF.bottom - cornerSpan * 2, rectF.left + cornerSpan * 2, rectF.bottom, 90f, 90f, false, cornerPaint)
        }
    }
}
