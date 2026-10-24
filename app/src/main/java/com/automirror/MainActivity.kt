package com.automirror

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Phone screen: sets everything up so that afterwards it's used only from the car. */
class MainActivity : Activity() {

    private lateinit var accessibilityStatus: TextView
    private lateinit var mirrorButton: Button
    private val onMirrorChanged: () -> Unit = { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = dp(20)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
        }

        column.addView(text(getString(R.string.app_name), 26f))
        column.addView(text("Mirrors the phone's screen on Android Auto and lets you use it by touching the car's screen.", 15f))

        column.addView(text("1. Accessibility service", 19f, top = 28))
        column.addView(text("Needed so touches on the car reach the phone.", 15f))
        accessibilityStatus = text("", 15f)
        column.addView(accessibilityStatus)
        column.addView(button("Open accessibility settings") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        column.addView(text(
            "If Android says it's a \"restricted setting\": open the app info, tap ⋮ in the top right corner and choose \"Allow restricted settings\". Then try again.",
            13f
        ))
        column.addView(button("Open app info") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        })

        column.addView(text("2. Android Auto", 19f, top = 28))
        column.addView(text(
            "In the Android Auto app: tap \"Version\" several times to enable developer mode, then ⋮ → Developer settings → enable \"Unknown sources\". automirror will show up in the car's app menu.",
            15f
        ))

        column.addView(text("3. Try it", 19f, top = 28))
        column.addView(text("Opening automirror on the car asks for the capture permission here. You can also start it manually:", 15f))
        mirrorButton = button("") {
            if (Mirror.isMirroring) MirrorService.stop(this) else ProjectionActivity.launch(this)
        }
        column.addView(mirrorButton)

        setContentView(ScrollView(this).apply {
            fitsSystemWindows = true
            addView(column)
        })

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }
    }

    override fun onResume() {
        super.onResume()
        Mirror.addListener(onMirrorChanged)
        refresh()
    }

    override fun onPause() {
        Mirror.removeListener(onMirrorChanged)
        super.onPause()
    }

    private fun refresh() {
        accessibilityStatus.text = if (TouchService.isEnabled(this)) "✔ Enabled" else "✘ Disabled"
        mirrorButton.text = if (Mirror.isMirroring) "Stop mirroring" else "Start mirroring"
    }

    private fun text(value: String, sizeSp: Float, top: Int = 6) = TextView(this).apply {
        text = value
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(top) }
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        setOnClickListener { onClick() }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
