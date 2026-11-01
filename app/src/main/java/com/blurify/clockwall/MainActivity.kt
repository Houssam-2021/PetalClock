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
        setContentView(root)
    }
}
