package com.blurify.clockwall

import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

/** Tiny launcher screen: one button that opens the system "set live wallpaper" preview. */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (24 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(Color.rgb(8, 18, 58))
        }
        root.addView(TextView(this).apply {
            text = "Petal Clock"
            textSize = 30f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "A petal live wallpaper with a big soft clock. It wakes up from outlines every time you turn on the screen, and the petals blur when you reach the home screen."
            textSize = 15f
            setTextColor(Color.argb(190, 255, 255, 255))
            gravity = Gravity.CENTER
            setPadding(0, pad / 2, 0, pad)
        })
        root.addView(Button(this).apply {
            text = "Set as wallpaper"
            setOnClickListener {
                startActivity(
                    Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
                        WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                        ComponentName(this@MainActivity, ClockWallpaperService::class.java)
                    )
                )
            }
        })
        if (BuildConfig.CHROMATIC_FX) addExperimentalControls(root, pad)
        setContentView(root)
    }

    /** Experimental build only: switch the fingerprint effect off, or line the lens up with the fingerprint icon. */
    private fun addExperimentalControls(root: LinearLayout, pad: Int) {
        val prefs = getSharedPreferences("fingerprint_fx", MODE_PRIVATE)
        val dim = Color.argb(170, 255, 255, 255)
        root.addView(TextView(this).apply {
            text = "Experimental build: glass fingerprint lens and chromatic unlock wave (Android 13+). " +
                "To go back, set the normal Petal Clock wallpaper again or uninstall this app."
            textSize = 13f
            setTextColor(dim)
            gravity = Gravity.CENTER
            setPadding(0, pad, 0, pad / 2)
        })
        root.addView(Switch(this).apply {
            text = "Fingerprint effect"
            setTextColor(Color.WHITE)
            isChecked = prefs.getBoolean("enabled", true)
            setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean("enabled", on).apply() }
        })
        fun slider(label: String, key: String, def: Int, lo: Int, hi: Int, unit: (Int) -> String) {
            val caption = TextView(this).apply {
                textSize = 13f
                setTextColor(dim)
                setPadding(0, pad / 2, 0, 0)
            }
            val value = prefs.getInt(key, def)
            caption.text = "$label: ${unit(value)}"
            root.addView(caption)
            root.addView(SeekBar(this).apply {
                max = hi - lo
                progress = value - lo
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(bar: SeekBar, p: Int, fromUser: Boolean) {
                        caption.text = "$label: ${unit(p + lo)}"
                        prefs.edit().putInt(key, p + lo).apply()
                    }
                    override fun onStartTrackingTouch(bar: SeekBar) {}
                    override fun onStopTrackingTouch(bar: SeekBar) {}
                })
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        slider("Lens height", "offsetY", 0, -80, 80) { if (it == 0) "default" else if (it < 0) "${-it} up" else "$it down" }
        slider("Lens size", "size", 100, 60, 150) { "$it%" }
    }
}
