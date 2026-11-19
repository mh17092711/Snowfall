package com.example.snowfall

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.View
import android.view.WindowManager
import kotlin.math.sin
import kotlin.random.Random

class SnowfallService : Service() {

    private var windowManager: WindowManager? = null
    private var snowView: SnowView? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        addOverlay()
        isRunning = true
        return START_STICKY
    }

    private fun addOverlay() {
        if (snowView != null) return

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        val view = SnowView(this)
        try {
            windowManager?.addView(view, params)
            snowView = view
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
        }
    }

    private fun startAsForeground() {
        val channelId = "snowfall_channel"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Snowfall",
                NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(channel)
        }

        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        val notification = builder
            .setContentTitle("Snowfall")
            .setContentText("Qor yog'moqda. To'xtatish uchun ilovani oching.")
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        snowView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        snowView = null
        windowManager = null
        isRunning = false
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 7001

        @Volatile
        var isRunning: Boolean = false
    }

    /**
     * Qor parchalarini chizadigan view.
     * Parcha pastga yetganda to'planmaydi: y = -20f ga qaytadi va yangi x oladi.
     */
    private class SnowView(context: Context) : View(context) {

        private class Flake {
            var x = 0f
            var y = 0f
            var radius = 0f
            var speed = 0f
            var drift = 0f
            var phase = 0f
            var swaySpeed = 0f
            var alpha = 0
        }

        private val density = resources.displayMetrics.density
        private val flakes = ArrayList<Flake>()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            style = Paint.Style.FILL
        }
        private var lastFrameNanos = 0L
        private var time = 0f

        init {
            setLayerType(LAYER_TYPE_HARDWARE, null)
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            if (w <= 0 || h <= 0) return

            if (flakes.isEmpty()) {
                repeat(FLAKE_COUNT) {
                    val f = newFlake(w, h)
                    f.y = Random.nextFloat() * h
                    flakes.add(f)
                }
            } else if (oldw > 0 && oldh > 0) {
                val sx = w.toFloat() / oldw
                val sy = h.toFloat() / oldh
                for (f in flakes) {
                    f.x *= sx
                    f.y *= sy
                }
            }
        }

        private fun newFlake(w: Int, h: Int): Flake {
            val f = Flake()
            respawn(f, w)
            return f
        }

        private fun respawn(f: Flake, w: Int) {
            f.x = Random.nextFloat() * w
            f.y = -20f
            f.radius = (1.5f + Random.nextFloat() * 3.5f) * density
            f.speed = (40f + f.radius / density * 18f + Random.nextFloat() * 30f) * density
            f.drift = (6f + Random.nextFloat() * 18f) * density
            f.phase = Random.nextFloat() * 6.2831855f
            f.swaySpeed = 0.5f + Random.nextFloat() * 1.0f
            f.alpha = 120 + Random.nextInt(120)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val w = width
            val h = height
            if (w <= 0 || h <= 0) return

            val now = System.nanoTime()
            val dt = if (lastFrameNanos == 0L) 0.016f
            else ((now - lastFrameNanos) / 1_000_000_000f).coerceAtMost(0.05f)
            lastFrameNanos = now
            time += dt

            for (f in flakes) {
                f.y += f.speed * dt

                if (f.y - f.radius > h) {
                    respawn(f, w)
                }

                val drawX = f.x + sin(time * f.swaySpeed + f.phase) * f.drift

                paint.alpha = f.alpha / 4
                canvas.drawCircle(drawX, f.y, f.radius * 1.8f, paint)
                paint.alpha = f.alpha
                canvas.drawCircle(drawX, f.y, f.radius, paint)
            }

            postInvalidateOnAnimation()
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            lastFrameNanos = 0L
        }

        private companion object {
            const val FLAKE_COUNT = 90
        }
    }
}
