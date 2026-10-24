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

/** Pantalla del celular: deja todo configurado para que después se use solo desde el auto. */
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

        column.addView(text("AutoMirror", 26f))
        column.addView(text("Espeja la pantalla del celular en Android Auto y permite usarla tocando la pantalla del auto.", 15f))

        column.addView(text("1. Servicio de accesibilidad", 19f, top = 28))
        column.addView(text("Hace falta para que los toques del auto lleguen al celular.", 15f))
        accessibilityStatus = text("", 15f)
        column.addView(accessibilityStatus)
        column.addView(button("Abrir ajustes de accesibilidad") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        column.addView(text(
            "Si Android dice que es un \"ajuste restringido\": abrí la info de la app, tocá ⋮ arriba a la derecha y elegí \"Permitir ajustes restringidos\". Después volvé a intentar.",
            13f
        ))
        column.addView(button("Abrir info de la app") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        })

        column.addView(text("2. Android Auto", 19f, top = 28))
        column.addView(text(
            "En la app de Android Auto: tocá varias veces \"Versión\" para activar el modo desarrollador, después ⋮ → Configuración para desarrolladores → activá \"Fuentes desconocidas\". AutoMirror va a aparecer en el menú de apps del auto.",
            15f
        ))

        column.addView(text("3. Probar", 19f, top = 28))
        column.addView(text("Al abrir AutoMirror en el auto se pide acá el permiso de captura. También podés iniciarlo a mano:", 15f))
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
        accessibilityStatus.text = if (TouchService.isEnabled(this)) "✔ Activado" else "✘ Desactivado"
        mirrorButton.text = if (Mirror.isMirroring) "Detener espejado" else "Iniciar espejado"
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
