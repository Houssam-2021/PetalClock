package com.blurify.clockwall

import android.app.KeyguardManager
import android.os.BatteryManager
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.text.format.DateFormat
import android.view.Choreographer
import android.view.MotionEvent
import android.view.SurfaceHolder
import androidx.core.content.ContextCompat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.exp
import kotlin.math.max

/**
 * An original petal wallpaper clock inspired by the user-provided HyperOS reference recording.
 *
 * Lock screen: the clock wakes up from thin outlines on black, fills with white, the petals fade in and the digits
 * settle into a soft glow. Home screen: the same petals, heavily blurred, with a blur transition in between.
 *
 * All layout numbers are in the units of the reference recording's phone screen (460 x 1024), scaled to cover the
 * real screen.
 */
class ClockWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = ClockEngine()

    private enum class Phase { AOD, WAKE, LOCK, SLEEP, TO_HOME, HOME }

    /** Where one digit sits and how far it is tilted (degrees, clockwise). */
    private class Slot(val x: Float, val y: Float, val rot: Float)

    private companion object {
        const val DEBUG_ACTION = "com.blurify.clockwall.reference.DEBUG"
        const val RW = 460f
        const val RH = 1024f

        // Source: 8.37-second 30fps recording. Preview has a long AOD hold
        // matching the recorded sequence; actual screen wake must be responsive.
        const val PREVIEW_AOD_HOLD = 1.17f
        const val REAL_AOD_HOLD = 0.13f
        const val WAKE_MORPH_END = 0.91f
        const val SLEEP_END = 0.96f
        const val TO_HOME_END = 0.65f
        const val SIGMA_HOME = 34f
        const val SIGMA_CLOCK = 15.5f     // strongly diffused top row as in the video
        const val GLYPH_SCALE = 1.33f
        const val GLYPH_SCALE_LOW = 1.50f
        const val LOW_BLUR = 1.43f        // lower row is paler and softer

        // The small outlined layout (AOD) and the big settled one
        val aod = arrayOf(Slot(220f, 287f, 43f), Slot(285f, 355f, 29f), Slot(176f, 415f, 23f), Slot(246f, 498f, 15f))
        val hold = arrayOf(Slot(202f, 329f, 44f), Slot(312f, 456f, 31f), Slot(158f, 521f, 26f), Slot(250f, 665f, 16f))
        val dotAod = arrayOf(PointF(229f, 362f), PointF(230f, 414f))
        val dotHold = arrayOf(PointF(228f, 444f), PointF(230f, 529f))

        val white = Color.rgb(255, 255, 255)
        val lavender = Color.rgb(215, 221, 253)
        val dotColor = Color.rgb(189, 217, 255)
        val ringColor = Color.rgb(100, 116, 146)
        val dateColor = Color.rgb(155, 184, 224)

        // Experimental fingerprint lens: centre and size in reference units, measured from the user's lock-screen
        // photo so the lens sits right under the system fingerprint icon. The height can be nudged in the app.
        const val FP_X = 230f
        const val FP_Y = 908f
        const val FP_LENS_R = 46.7f
        const val FP_RIM = 2.2f
        const val FP_STRIP = 95f
        const val FX_PREFS = "fingerprint_fx"
    }

    inner class ClockEngine : Engine() {
        private val handler = Handler(Looper.getMainLooper())
        private val choreographer = Choreographer.getInstance()
        private val keyguard by lazy { getSystemService(KeyguardManager::class.java) }
        private val glyphs = BubbleGlyphs()
        private var petalBmp: Bitmap? = null

        private var visible = false
        private var w = 0
        private var h = 0
        private var k = 1f
        private var ox = 0f
        private var oy = 0f

        private var phase = Phase.AOD
        private var phaseStart = 0L
        private var lastWake = 0L
        private var zoom = 0f

        // what the current frame looks like
        private var petalA = 0f
        private var petalSigma = 0f
        private var dim = 0f
        private var pose = 0f
        private var outlineA = 1f
        private var fillA = 0f
        private var fillV = 0.5f
        private var digitA = 1f
        private var digitSigma = 0f
        private var dateA = 0f
        private var fineA = 1f
        private var trailStrength = 0f
        private var batteryPercent = -1

        private val petalNode = RenderNode("petals")
        private val digitNode = RenderNode("digits")
        private val lowNode = RenderNode("digits low")
        private val coreNode = RenderNode("digits core")
        private val coreLowNode = RenderNode("digits core low")
        private val dotNode = RenderNode("dots")
        private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
        }
        private val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            textSize = 19.7f
        }
        private val aodDatePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            textSize = 15.0f
            textAlign = Paint.Align.CENTER
        }
        private val batteryPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val batteryTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            textSize = 12.8f
        }
        private val effectCache = mutableMapOf<Int, RenderEffect>()

        // experimental fingerprint lens + chromatic unlock wave (null in the normal builds, or if the shader fails)
        private val fx: FingerprintFx? by lazy {
            if (!BuildConfig.CHROMATIC_FX || Build.VERSION.SDK_INT < 33) null
            else try { FingerprintFx() } catch (e: Throwable) { null }
        }
        private val sceneNode = RenderNode("scene")
        private val fxNode = RenderNode("fx")
        private var sceneCached = false   // sceneNode holds the current settled lock-screen frame
        private var fxOn = true
        private var fxSize = 1f
        private var fxOffsetY = 0
        private var lensA = 0f
        private var lensScale = 1f
        private var dropA = 0f
        private var waveT = -1f
        private var lastLensFrame = 0L

        private val frameCallback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (animating()) {
                    draw()
                    if (animating() || lensLive()) choreographer.postFrameCallback(this)
                } else if (lensLive()) {
                    // experimental: keep the liquid lens moving on the lock screen, at ~30 fps to spare the battery
                    if (frameTimeNanos - lastLensFrame >= 30_000_000L) {
                        lastLensFrame = frameTimeNanos
                        draw(lensOnly = true)
                    }
                    if (lensLive()) choreographer.postFrameCallback(this)
                } else {
                    draw()
                }
            }
        }

        private val minuteTick = object : Runnable {
            override fun run() {
                if (visible && phase == Phase.LOCK) draw()
                scheduleMinuteTick()
            }
        }

        // debug hooks (debuggable builds only): freeze the timeline and force the digits
        // adb shell am broadcast -a com.blurify.clockwall.DEBUG --es phase wake --ef t 0.5 --es digits 1824
        private var debugT: Float? = null
        private var debugNoGlow = false
        private var debugNoSharp = false
        private var debugDigits: String? = null
        private val debugReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                debugDigits = intent.getStringExtra("digits")?.takeIf { it.length == 4 }
                debugNoGlow = intent.hasExtra("noglow")
                debugNoSharp = intent.hasExtra("nosharp")
                debugT = if (intent.hasExtra("t")) intent.getFloatExtra("t", 0f) else null
                when (intent.getStringExtra("phase")) {
                    "aod" -> phase = Phase.AOD
                    "wake" -> { phase = Phase.WAKE; phaseStart = SystemClock.uptimeMillis() }
                    "sleep" -> { phase = Phase.SLEEP; phaseStart = SystemClock.uptimeMillis() }
                    "lock" -> phase = Phase.LOCK
                    "home" -> phase = Phase.HOME
                    "live" -> { debugT = null; startWake() }
                }
                draw()
            }
        }

        private val screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_USER_PRESENT -> if (visible && (phase == Phase.WAKE || phase == Phase.LOCK)) {
                        if (!isLockOnly()) startToHome()
                    }
                    Intent.ACTION_SCREEN_OFF -> startSleepOrAod()
                    Intent.ACTION_BATTERY_CHANGED -> {
                        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                        if (level >= 0 && scale > 0) batteryPercent = (100 * level / scale).coerceIn(0, 100)
                        if (visible && phase == Phase.AOD) draw()
                    }
                    Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_DATE_CHANGED -> {
                        if (visible) draw()
                        if (phase == Phase.LOCK) scheduleMinuteTick()
                    }
                }
            }
        }

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)
            petalBmp = BitmapFactory.decodeResource(resources, R.drawable.petals, BitmapFactory.Options().apply {
                inScaled = false
            })
            registerReceiver(screenReceiver, IntentFilter().apply {
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
                addAction(Intent.ACTION_DATE_CHANGED)
                addAction(Intent.ACTION_BATTERY_CHANGED)
            })
            if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
                ContextCompat.registerReceiver(
                    this@ClockWallpaperService, debugReceiver, IntentFilter(DEBUG_ACTION), ContextCompat.RECEIVER_EXPORTED
                )
            }
            if (isPreview) setTouchEventsEnabled(true)
            loadFxPrefs()
            phase = if (lockLook()) Phase.AOD else Phase.HOME
        }

        override fun onDestroy() {
            choreographer.removeFrameCallback(frameCallback)
            handler.removeCallbacksAndMessages(null)
            unregisterReceiver(screenReceiver)
            if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) unregisterReceiver(debugReceiver)
            petalBmp?.recycle()
            petalBmp = null
            super.onDestroy()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            w = width
            h = height
            k = max(width / RW, height / RH)
            ox = (width - RW * k) / 2f
            oy = (height - RH * k) / 2f
            for (n in arrayOf(petalNode, digitNode, lowNode, coreNode, coreLowNode, dotNode, sceneNode, fxNode)) n.setPosition(0, 0, width, height)
            sceneCached = false
            draw()
        }

        override fun onSurfaceRedrawNeeded(holder: SurfaceHolder) {
            draw()
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            visible = isVisible
            if (isVisible) loadFxPrefs()   // the settings may have changed in the app meanwhile
            if (isVisible) { debugDigits = null; debugT = null } // test overrides never outlive a screen-on
            choreographer.removeFrameCallback(frameCallback)
            handler.removeCallbacks(minuteTick)
            if (!isVisible) {
                // The system controls AOD once the surface is invisible.
                // Never keep rendering behind the system AOD.
                phase = Phase.AOD
                return
            }
            if (isPreview) {
                // the picker hides and shows the preview while it animates: only (re)start when it was idle
                if (phase == Phase.AOD || lastWake == 0L) startWake() else if (animating()) postFrame() else draw()
            } else if (lockLook()) {
                if (SystemClock.uptimeMillis() - lastWake > 700L || phase == Phase.AOD) startWake() else postFrame()
            } else if (phase == Phase.WAKE || phase == Phase.LOCK) {
                startToHome()
            } else {
                phase = Phase.HOME
                draw()
            }
            if (phase == Phase.LOCK) scheduleMinuteTick()
        }

        override fun onCommand(action: String, x: Int, y: Int, z: Int, extras: Bundle?, resultRequested: Boolean): Bundle? {
            // SystemUI tells the wallpaper when the keyguard starts to leave: begin the blur a little before USER_PRESENT
            if (action == "android.wallpaper.keyguardgoingaway" && visible && !isLockOnly() &&
                (phase == Phase.WAKE || phase == Phase.LOCK)
            ) startToHome()
            return super.onCommand(action, x, y, z, extras, resultRequested)
        }

        override fun onZoomChanged(z: Float) {
            super.onZoomChanged(z)
            zoom = z
            if (visible && (phase == Phase.HOME || phase == Phase.LOCK)) draw()
            if (lensLive()) postFrame()
        }

        override fun onTouchEvent(event: MotionEvent) {
            // In the picker a tap shows the lock <-> home blur transition
            if (isPreview && event.actionMasked == MotionEvent.ACTION_UP) {
                if (phase == Phase.HOME) startWake() else startToHome()
            }
        }

        // --- state ---------------------------------------------------------------------------------------------

        private fun isLockOnly(): Boolean =
            !isPreview && wallpaperFlags and WallpaperManager.FLAG_LOCK != 0 &&
                wallpaperFlags and WallpaperManager.FLAG_SYSTEM == 0

        /** Whether the wallpaper should look like the lock screen right now. */
        private fun lockLook(): Boolean {
            if (isPreview) return true
            val f = wallpaperFlags
            val lock = f and WallpaperManager.FLAG_LOCK != 0
            val home = f and WallpaperManager.FLAG_SYSTEM != 0
            return when {
                lock && !home -> true
                home && !lock -> false
                else -> keyguard.isKeyguardLocked
            }
        }

        private val wakeDelay get() = if (isPreview) PREVIEW_AOD_HOLD else REAL_AOD_HOLD

        private fun startWake() {
            phase = Phase.WAKE
            phaseStart = SystemClock.uptimeMillis()
            lastWake = phaseStart
            postFrame()
            scheduleMinuteTick()
        }

        private fun startToHome() {
            phase = Phase.TO_HOME
            phaseStart = SystemClock.uptimeMillis()
            postFrame()
        }

        /** Best effort: render a reverse transition only while Android still exposes our surface.
         *  It is not an AOD replacement, and Samsung may hide the wallpaper immediately. */
        private fun startSleepOrAod() {
            handler.removeCallbacks(minuteTick)
            val wakesLocked = isLockOnly() || (!isPreview && keyguard.isDeviceSecure)
            if (!wakesLocked) return
            phaseStart = SystemClock.uptimeMillis()
            phase = if (visible) Phase.SLEEP else Phase.AOD
            if (visible) postFrame() else draw()
        }

        private fun animating() = visible && (phase == Phase.WAKE || phase == Phase.SLEEP || phase == Phase.TO_HOME)

        /** Experimental: the lens is showing on a settled lock screen and its liquid swirl needs frames. */
        private fun lensLive() = fx != null && !fxBroken && visible && phase == Phase.LOCK && lensA > 0.003f && fxEnabled()

        private fun fxEnabled() = fxOn

        private fun loadFxPrefs() {
            if (fx == null) return
            val prefs = getSharedPreferences(FX_PREFS, Context.MODE_PRIVATE)
            fxOn = prefs.getBoolean("enabled", true)
            fxSize = prefs.getInt("size", 100) / 100f
            fxOffsetY = prefs.getInt("offsetY", 0)
        }

        private fun postFrame() {
            choreographer.removeFrameCallback(frameCallback)
            choreographer.postFrameCallback(frameCallback)
        }

        private fun scheduleMinuteTick() {
            handler.removeCallbacks(minuteTick)
            val now = System.currentTimeMillis()
            handler.postDelayed(minuteTick, 60_000L - now % 60_000L + 30L)
        }

        // --- timeline ------------------------------------------------------------------------------------------

        private fun smooth(x: Float): Float {
            val t = x.coerceIn(0f, 1f)
            return t * t * (3f - 2f * t)
        }

        /** 0 -> 1 without overshoot, quick at the start, like a critically damped spring. */
        private fun settle(s: Float, rate: Float): Float {
            val x = max(s, 0f) * rate
            return 1f - (1f + x) * exp(-x)
        }

        private fun aodScene() {
            petalA = 0f; petalSigma = 0f; dim = 0f
            pose = 0f; outlineA = 1f; fillA = 0f; fillV = 0.5f
            digitA = 1f; digitSigma = 0f; dateA = 0f
            fineA = 1f; trailStrength = 0f
        }

        private fun lockScene() {
            petalA = 1f; petalSigma = zoom * 20f; dim = zoom * 0.25f
            pose = 1f; outlineA = 0f; fillA = 1f; fillV = 1f
            digitA = 1f; digitSigma = SIGMA_CLOCK; dateA = 0.9f
            fineA = 0.58f; trailStrength = 0.26f
        }

        private fun homeScene() {
            petalA = 1f; petalSigma = SIGMA_HOME * (1f + 0.5f * zoom); dim = zoom * 0.25f
            pose = 1f; outlineA = 0f; fillA = 1f; fillV = 1f
            digitA = 0f; digitSigma = SIGMA_CLOCK; dateA = 0f
            fineA = 0f; trailStrength = 0f
        }

        private fun elapsed(): Float = debugT ?: ((SystemClock.uptimeMillis() - phaseStart) / 1000f)

        private fun updateScene() {
            val t = elapsed()
            if (phase == Phase.WAKE && t > wakeDelay + WAKE_MORPH_END) phase = Phase.LOCK
            if (phase == Phase.SLEEP && t > SLEEP_END) phase = Phase.AOD
            if (phase == Phase.TO_HOME && t > TO_HOME_END) phase = Phase.HOME
            when (phase) {
                Phase.AOD -> aodScene()
                Phase.LOCK -> lockScene()
                Phase.HOME -> homeScene()
                Phase.WAKE -> {
                    val u = t - wakeDelay
                    if (u <= 0f) {
                        aodScene()
                        // Only the preview simulates the first slow appearance
                        // of the black-screen outline clock in the full reference.
                        if (isPreview) {
                            outlineA = smooth((t - 0.12f) / 0.56f)
                            digitA = outlineA
                        }
                    } else {
                        val enter = smooth((u - 0.07f) / 0.42f)
                        val morph = smooth((u - 0.03f) / 0.74f)
                        petalA = enter
                        petalSigma = zoom * 20f
                        dim = 0f
                        pose = morph
                        outlineA = 1f - smooth((u - 0.06f) / 0.32f)
                        fillA = smooth((u - 0.11f) / 0.38f)
                        fillV = 0.48f + 0.52f * smooth((u - 0.13f) / 0.40f)
                        digitA = 1f
                        digitSigma = lerp(0.6f, SIGMA_CLOCK, smooth((u - 0.21f) / 0.52f))
                        dateA = enter * .9f
                        fineA = lerp(1f, .58f, smooth((u - .13f) / .46f))
                        trailStrength = .56f * smooth((u - .13f) / .22f) *
                            (1f - .52f * smooth((u - .45f) / .45f))
                    }
                }
                Phase.SLEEP -> {
                    // A genuine reverse animation, not a hard jump to AOD.
                    // Whether it is actually visible depends on Samsung/Android.
                    val u = smooth(t / SLEEP_END)
                    val shrink = smooth((t - 0.05f) / (SLEEP_END - 0.09f))
                    petalA = 1f - smooth((t - .02f) / .43f)
                    petalSigma = 0f
                    dim = 0f
                    pose = 1f - shrink
                    fillA = 1f - smooth((t - .22f) / .54f)
                    outlineA = smooth((t - .28f) / .53f)
                    fillV = 1f - .52f * u
                    digitA = 1f
                    digitSigma = SIGMA_CLOCK * (1f - smooth((t - .18f) / .60f))
                    dateA = .9f * (1f - smooth(t / .72f))
                    fineA = lerp(.58f, 1f, smooth((t - .27f) / .55f))
                    trailStrength = .25f * (1f - u)
                }
                Phase.TO_HOME -> {
                    homeScene()
                    petalSigma = SIGMA_HOME * smooth(t / 0.6f) * (1f + 0.5f * zoom)
                    digitA = 1f - smooth(t / 0.35f)
                    digitSigma = SIGMA_CLOCK + 22f * smooth(t / 0.4f)
                    dateA = 0.9f * (1f - smooth(t / 0.25f))
                    fineA = 0.58f * (1f - smooth(t / 0.25f))
                }
            }
            fxScene(t)
        }

        /** Experimental: the lens follows the petals in and out, and the unlock wave runs at the start of TO_HOME. */
        private fun fxScene(t: Float) {
            lensA = when (phase) {
                Phase.WAKE, Phase.LOCK, Phase.SLEEP -> petalA * (1f - zoom)
                Phase.TO_HOME -> 1f - smooth((t - 0.15f) / 0.07f)
                else -> 0f
            }
            // unlock: the lens swells into a water drop, then bursts outward while it fades
            if (phase == Phase.TO_HOME) {
                dropA = smooth(t / 0.14f)
                lensScale = 1f + 0.6f * dropA + 0.35f * smooth((t - 0.15f) / 0.07f)
            } else {
                dropA = 0f
                lensScale = 1f
            }
            waveT = if (phase == Phase.TO_HOME) t else -1f
        }

        // --- drawing -------------------------------------------------------------------------------------------

        /** [lensOnly]: an experimental lens-animation frame, where only the shader changes on a settled lock screen. */
        private fun draw(lensOnly: Boolean = false) {
            if (w == 0) return
            val canvas = try { surfaceHolder.lockHardwareCanvas() } catch (e: Exception) { null } ?: return
            try {
                updateScene()
                val effect = fxEffect()
                if (effect == null) {
                    sceneCached = false
                    drawScene(canvas)
                } else {
                    // experimental: the same frame, drawn through the fingerprint lens / unlock wave shader.
                    // A settled lock frame is kept in a GPU layer, so the lens animation does not re-render the
                    // petals and the blurred clock 30 times a second: only the shader pass runs.
                    if (!(lensOnly && sceneCached && phase == Phase.LOCK)) {
                        val rc = sceneNode.beginRecording(w, h)
                        drawScene(rc)
                        sceneNode.endRecording()
                        sceneNode.setUseCompositingLayer(phase == Phase.LOCK, null)
                        sceneCached = phase == Phase.LOCK
                    }
                    val fc = fxNode.beginRecording(w, h)
                    fc.drawRenderNode(sceneNode)
                    fxNode.endRecording()
                    fxNode.setRenderEffect(effect)
                    canvas.drawRenderNode(fxNode)
                }
            } finally {
                surfaceHolder.unlockCanvasAndPost(canvas)
            }
        }

        private fun drawScene(canvas: Canvas) {
            canvas.drawColor(Color.BLACK)
            drawPetals(canvas)
            drawDate(canvas)
            drawAodDate(canvas)
            drawClock(canvas)
        }

        private var fxBroken = false

        /** Never lets the experiment break the wallpaper: any shader failure switches the effect off for good. */
        private fun fxEffect(): RenderEffect? = if (fxBroken) null else try {
            buildFxEffect()
        } catch (e: Throwable) {
            fxBroken = true
            null
        }

        private fun buildFxEffect(): RenderEffect? {
            val fx = fx ?: return null
            if (!fxOn) return null
            val wave = fx.wave(waveT)
            if (wave == null && lensA <= 0.003f) return null
            val size = fxSize
            val cy = FP_Y + fxOffsetY
            return fx.effect(
                w, h, ox + FP_X * k, oy + cy * k, FP_LENS_R * size * lensScale * k, FP_RIM * k,
                lensA.coerceIn(0f, 1f), wave, FP_STRIP * size * k, (SystemClock.uptimeMillis() % 600_000L) / 1000f,
                dropA,
            )
        }

        /** A blur of the given standard deviation (reference px); RenderEffect takes a radius, sigma = 0.577 r + 0.5. */
        private fun blur(sigmaRef: Float): RenderEffect? {
            val sigma = sigmaRef * k
            if (sigma < .8f) return null
            // Quarter-pixel cache avoids allocating a new RenderEffect per frame.
            // Hardware RenderEffect supports the phone's original SDK 31+ target.
            val quarter = (sigma * 4f).toInt().coerceAtLeast(1)
            if (effectCache.size > 380) effectCache.clear()
            return effectCache.getOrPut(quarter) {
                val radius = quarter / 4f
                RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
            }
        }

        private fun drawPetals(c: Canvas) {
            val bmp = petalBmp ?: return
            if (petalA <= 0.003f) return
            val rc = petalNode.beginRecording(w, h)
            rc.translate(ox, oy)
            rc.scale(k / 2f, k / 2f) // the artwork is twice the reference size
            rc.drawBitmap(bmp, 0f, 0f, bmpPaint)
            petalNode.endRecording()
            petalNode.setAlpha(petalA)
            petalNode.setRenderEffect(blur(petalSigma))
            c.drawRenderNode(petalNode)
            if (dim > 0.003f) c.drawColor(Color.argb((dim * 255).toInt(), 0, 0, 0))
        }

        private fun drawDate(c: Canvas) {
            if (dateA <= 0.003f) return
            val now = Date()
            val day = DateFormat.format("MMMM d", now).toString()
            val week = DateFormat.format(DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEE"), now).toString()
            datePaint.color = tint(dateColor, 1f, dateA)
            c.save()
            c.translate(ox, oy)
            c.scale(k, k)
            c.drawText(day, 24f, 77f, datePaint)
            c.drawText(week, 24f, 108f, datePaint)
            c.restore()
        }

        private fun drawAodDate(c: Canvas) {
            val a = outlineA * (1f - smooth((pose - .12f) / .6f))
            if (a <= .003f) return
            val text = DateFormat.format("EEE, MMMM d", Date()).toString()
            aodDatePaint.color = Color.argb((a * 192).toInt().coerceIn(0, 255), 168, 172, 190)
            c.save()
            c.translate(ox, oy)
            c.scale(k, k)
            c.drawText(text, 230f, 595f, aodDatePaint)
            // The AOD battery pill is visual only while the wallpaper surface is
            // active. It cannot draw inside Samsung's system-owned real AOD.
            if (batteryPercent >= 0) {
                val pct = batteryPercent
                batteryPaint.style = Paint.Style.STROKE
                batteryPaint.strokeWidth = 1.5f
                batteryPaint.color = Color.argb((a * 165).toInt().coerceIn(0, 255), 175, 179, 195)
                c.drawRoundRect(210f, 604f, 234f, 617f, 3.2f, 3.2f, batteryPaint)
                batteryPaint.style = Paint.Style.FILL
                batteryPaint.color = Color.argb((a * 155).toInt().coerceIn(0, 255), 168, 172, 188)
                c.drawRoundRect(212.4f, 606.4f, 212.4f + 19f * pct / 100f, 614.6f, 1f, 1f, batteryPaint)
                c.drawRect(235f, 608f, 237f, 613f, batteryPaint)
                batteryTextPaint.color = Color.argb((a * 190).toInt().coerceIn(0, 255), 170, 176, 190)
                c.drawText("$pct%", 242f, 615f, batteryTextPaint)
            }
            c.restore()
        }

        private fun digits(): IntArray {
            val cal = Calendar.getInstance()
            var hour = cal.get(Calendar.HOUR_OF_DAY)
            if (!DateFormat.is24HourFormat(this@ClockWallpaperService)) {
                hour %= 12
                if (hour == 0) hour = 12
            }
            val m = cal.get(Calendar.MINUTE)
            return intArrayOf(hour / 10, hour % 10, m / 10, m % 10)
        }

        private fun lerp(a: Float, b: Float, f: Float) = a + (b - a) * f

        private fun tint(base: Int, v: Float, a: Float) = Color.argb(
            (a * 255).toInt(), (Color.red(base) * v).toInt(), (Color.green(base) * v).toInt(), (Color.blue(base) * v).toInt()
        )

        private fun recordRow(node: RenderNode, core: RenderNode, from: Int, to: Int, sigma: Float, c: Canvas) {
            val rc = node.beginRecording(w, h)
            val cc = core.beginRecording(w, h)
            rc.translate(ox, oy)
            rc.scale(k, k)
            cc.translate(ox, oy)
            cc.scale(k, k)
            val ds = debugDigits?.let { d -> IntArray(4) { (d[it] - '0').coerceIn(0, 9) } } ?: digits()
            val s = lerp(1f, if (from >= 2) GLYPH_SCALE_LOW else GLYPH_SCALE, pose)
            for (i in from until to) {
                val a = aod[i]
                val b = hold[i]
                val base = if (i >= 2) lavender else white
                val x = lerp(a.x, b.x, pose)
                val y = lerp(a.y, b.y, pose)
                val rotation = lerp(a.rot, b.rot, pose)
                val path = glyphs.path(ds[i])

                rc.save()
                rc.translate(x, y)
                rc.rotate(rotation)
                rc.scale(s, s)
                if (fillA > .003f) {
                    fill.color = tint(base, fillV, fillA)
                    rc.drawPath(path, fill)
                    // Soft directional upper-right ghosting, carefully weaker than
                    // the primary fill so it does not turn into a hard duplicate.
                    if (trailStrength > .003f) {
                        fill.color = tint(base, fillV, fillA * trailStrength * .34f)
                        rc.save()
                        rc.translate(5.3f * pose, -8.0f * pose)
                        rc.drawPath(path, fill)
                        rc.restore()
                        fill.color = tint(base, fillV, fillA * trailStrength * .19f)
                        rc.save()
                        rc.translate(10.5f * pose, -14f * pose)
                        rc.drawPath(path, fill)
                        rc.restore()
                    }
                }
                rc.restore()

                cc.save()
                cc.translate(x, y)
                cc.rotate(rotation)
                cc.scale(s, s)
                if (fillA > .003f) {
                    // The main fill stays soft. Only a tiny opacity of sharp fill
                    // prevents complete loss of the silhouette at peak blur.
                    fill.color = tint(base, fillV, fillA)
                    cc.drawPath(path, fill)
                }
                if (outlineA > .003f) {
                    line.style = Paint.Style.STROKE
                    line.color = tint(base, 1f, outlineA)
                    line.strokeWidth = 2.5f / s
                    cc.drawPath(path, line)
                    // Secondary hairline contour seen around the AOD outlines.
                    line.color = tint(base, .72f, outlineA * .42f)
                    line.strokeWidth = 1.1f / s
                    cc.drawPath(path, line)
                }
                cc.restore()
            }
            node.endRecording()
            core.endRecording()
            node.setAlpha(digitA)
            node.setRenderEffect(blur(sigma))
            if (!debugNoGlow) c.drawRenderNode(node)
            core.setAlpha(digitA * fineA * (if (from >= 2) .78f else 1f))
            core.setRenderEffect(null)
            if (!debugNoSharp) c.drawRenderNode(core)
        }

        private fun drawClock(c: Canvas) {
            if (digitA <= 0.003f) return
            recordRow(lowNode, coreLowNode, 2, 4, digitSigma * LOW_BLUR, c)
            recordRow(digitNode, coreNode, 0, 2, digitSigma, c)

            // the two dots between the rows: rings on the AOD, light-blue discs when awake
            val dc = dotNode.beginRecording(w, h)
            dc.translate(ox, oy)
            dc.scale(k, k)
            val r = lerp(12.5f, 21f, pose)
            for (i in 0 until 2) {
                val x = lerp(dotAod[i].x, dotHold[i].x, pose)
                val y = lerp(dotAod[i].y, dotHold[i].y, pose)
                if (fillA > 0.003f) {
                    fill.color = tint(dotColor, fillV, fillA)
                    dc.drawCircle(x, y, r, fill)
                }
                if (outlineA > 0.003f) {
                    line.color = tint(ringColor, 1f, outlineA)
                    line.strokeWidth = 3.2f
                    dc.drawCircle(x, y, r, line)
                }
            }
            dotNode.endRecording()
            dotNode.setAlpha(digitA)
            dotNode.setRenderEffect(blur(1.2f * fillA))
            c.drawRenderNode(dotNode)
        }
    }
}
