package com.gymstatistics

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.Build
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Button
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.hypot
import kotlin.math.roundToInt

internal object WorkoutOverlay {
    var isTraining by mutableStateOf(false)
        private set
    var recordRequested by mutableStateOf(false)
        private set

    private var window: Pair<WindowManager, Button>? = null
    private var lastPosition: Point? = null

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
            gravity = Gravity.RIGHT or Gravity.BOTTOM
            x = lastPosition?.x ?: (16 * density).roundToInt()
            y = lastPosition?.y ?: (112 * density).roundToInt()
            setTitle("GymStatisticsWorkoutOverlay")
        }
        enableDragging(button, manager, layout)
        try {
            manager.addView(button, layout)
            window = manager to button
        } catch (_: WindowManager.BadTokenException) {
            stop()
        } catch (_: SecurityException) {
            stop()
        }
    }

    private fun enableDragging(button: Button, manager: WindowManager, layout: WindowManager.LayoutParams) {
        val touchSlop = ViewConfiguration.get(button.context).scaledTouchSlop
        val screen = Rect()
        val location = IntArray(2)
        var downX = 0f
        var downY = 0f
        var initialRight = 0
        var initialBottom = 0
        var tracking = false
        var dragging = false

        button.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    view.getWindowVisibleDisplayFrame(screen)
                    view.getLocationOnScreen(location)
                    initialRight = screen.right - location[0] - view.width
                    initialBottom = screen.bottom - location[1] - view.height
                    tracking = true
                    dragging = false
                    view.isPressed = true
                }
                MotionEvent.ACTION_MOVE -> if (tracking) {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (dragging || hypot(dx, dy) > touchSlop) {
                        dragging = true
                        view.isPressed = false
                        layout.x = (initialRight - dx).roundToInt().coerceIn(0, (screen.width() - view.width).coerceAtLeast(0))
                        layout.y = (initialBottom - dy).roundToInt().coerceIn(0, (screen.height() - view.height).coerceAtLeast(0))
                        manager.updateViewLayout(view, layout)
                        lastPosition = Point(layout.x, layout.y)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    view.isPressed = false
                    if (tracking && !dragging) view.performClick()
                    tracking = false
                }
                MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> {
                    view.isPressed = false
                    tracking = false
                }
            }
            true
        }
    }

    fun hide() {
        window?.let { (manager, button) -> manager.removeViewImmediate(button) }
        window = null
    }
}
