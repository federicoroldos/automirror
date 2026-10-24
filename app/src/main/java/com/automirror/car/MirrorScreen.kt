package com.automirror.car

import android.graphics.PointF
import android.graphics.Rect
import android.util.Log
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.MessageInfo
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.automirror.Mirror
import com.automirror.ProjectionActivity
import com.automirror.R
import com.automirror.TouchService

/**
 * Car screen. It presents itself as a navigation app so Android Auto gives it a free surface
 * (the "map" one), and the phone is mirrored onto that surface.
 */
class MirrorScreen(carContext: CarContext) : Screen(carContext), SurfaceCallback {

    private companion object {
        const val TAG = "AutoMirror"
    }

    private val onMirrorChanged: () -> Unit = { invalidate() }

    init {
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(this)
        Mirror.addListener(onMirrorChanged)
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                Mirror.removeListener(onMirrorChanged)
            }
        })
        if (!Mirror.isMirroring) requestCaptureOnPhone()
    }

    override fun onGetTemplate(): Template {
        val builder = NavigationTemplate.Builder()
            .setActionStrip(if (Mirror.isMirroring) mirroringActions() else startActions())
            // Without the PAN button the host doesn't send taps or swipes to the surface.
            .setMapActionStrip(ActionStrip.Builder().addAction(Action.PAN).build())
            .setPanModeListener { }

        if (!Mirror.isMirroring) {
            val message = if (TouchService.isEnabled(carContext)) {
                "Tap \"Start\" and accept the screen capture on the phone"
            } else {
                "On the phone, enable the automirror accessibility service"
            }
            builder.setNavigationInfo(MessageInfo.Builder(carContext.getString(R.string.app_name)).setText(message).build())
        }
        return builder.build()
    }

    private fun startActions() = ActionStrip.Builder()
        .addAction(Action.Builder().setTitle("Start").setOnClickListener { requestCaptureOnPhone() }.build())
        .build()

    private fun mirroringActions() = ActionStrip.Builder()
        .addAction(iconAction(R.drawable.ic_back) { TouchService.instance?.back() })
        .addAction(iconAction(R.drawable.ic_home) { TouchService.instance?.home() })
        .addAction(iconAction(R.drawable.ic_recents) { TouchService.instance?.recents() })
        .addAction(iconAction(R.drawable.ic_notifications) { TouchService.instance?.notifications() })
        .build()

    private fun iconAction(icon: Int, onClick: () -> Unit) = Action.Builder()
        .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, icon)).build())
        .setOnClickListener { onClick() }
        .build()

    private fun requestCaptureOnPhone() {
        // Opening an Activity from the background is allowed because the accessibility service
        // is enabled; if it isn't, the user has to open the app manually.
        try {
            ProjectionActivity.launch(carContext)
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't open the capture request on the phone", e)
        }
    }

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        Log.i(TAG, "Surface ${surfaceContainer.width}x${surfaceContainer.height} @${surfaceContainer.dpi}")
        Mirror.setCarSurface(
            surfaceContainer.surface, surfaceContainer.width, surfaceContainer.height, surfaceContainer.dpi
        )
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        Mirror.setCarSurface(null, 0, 0, 0)
    }

    override fun onVisibleAreaChanged(visibleArea: Rect) {
        Log.d(TAG, "Visible area $visibleArea")
    }

    override fun onStableAreaChanged(stableArea: Rect) {
        Log.d(TAG, "Stable area $stableArea")
    }

    override fun onClick(x: Float, y: Float) {
        val p = Mirror.carToPhone(x, y)
        Log.d(TAG, "onClick car=($x, $y) phone=$p")
        if (p != null) TouchService.instance?.tap(p)
    }

    override fun onScroll(distanceX: Float, distanceY: Float) {
        Log.d(TAG, "onScroll ($distanceX, $distanceY)")
        // Same as GestureDetector: distance = previous position - current position.
        val s = Mirror.carToPhoneScale()
        TouchService.instance?.dragBy(-distanceX * s, -distanceY * s)
    }

    override fun onFling(velocityX: Float, velocityY: Float) {
        Log.d(TAG, "onFling ($velocityX, $velocityY)")
        val s = Mirror.carToPhoneScale()
        TouchService.instance?.fling(velocityX * s, velocityY * s)
    }

    override fun onScale(focusX: Float, focusY: Float, scaleFactor: Float) {
        Log.d(TAG, "onScale ($focusX, $focusY) x$scaleFactor")
        val p = Mirror.carToPhone(focusX, focusY) ?: Mirror.phoneSize().let { PointF(it.x / 2, it.y / 2) }
        TouchService.instance?.scale(p, scaleFactor)
    }
}
