package com.gymstatistics

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Build
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt

internal object WorkoutOverlay {
    var isTraining by mutableStateOf(false)
        private set
    var recordRequested by mutableStateOf(false)
        private set

    private var window: Pair<WindowManager, Button>? = null

    fun start() {
        isTraining = true
    }

    fun stop() {
        isTraining = false
        recordRequested = false
        hide()
    }

    fun checkPermission(context: Context) {
        if (isTraining && !Settings.canDrawOverlays(context)) stop()
    }

    fun recordOpened() {
        recordRequested = false
    }

    fun show(context: Context) {
        checkPermission(context)
        if (!isTraining || window != null) return

        val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        val windowContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val display = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY) ?: return
            context.createDisplayContext(display).createWindowContext(type, null)
        } else {
            context
        }
        val manager = windowContext.getSystemService(WindowManager::class.java) ?: return
        val button = Button(windowContext).apply {
            setText(R.string.workout_overlay_record)
            setOnClickListener {
                val launch = Intent(context, MainActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
                )
                context.startActivity(launch)
                recordRequested = true
            }
        }
        val density = windowContext.resources.displayMetrics.density
        val layout = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.END or Gravity.BOTTOM
            x = (16 * density).roundToInt()
            y = (112 * density).roundToInt()
            setTitle("GymStatisticsWorkoutOverlay")
        }
        try {
            manager.addView(button, layout)
            window = manager to button
        } catch (_: WindowManager.BadTokenException) {
            stop()
        } catch (_: SecurityException) {
            stop()
        }
    }

    fun hide() {
        window?.let { (manager, button) -> manager.removeViewImmediate(button) }
        window = null
    }
}
