package com.hafij.islamicalarm.permissions

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import com.hafij.islamicalarm.R
import kotlin.math.min

/**
 * Full-screen zoomable screenshot preview dialog so users can clearly inspect
 * where each Android permission switch lives inside system Settings.
 */
object PermissionImageZoomDialog {

    @SuppressLint("ClickableViewAccessibility")
    fun show(context: Context, step: PermStep, @DrawableRes imageRes: Int) {
        val dialog = Dialog(context, android.R.style.Theme_Material_Light_NoActionBar)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_permission_image_zoom, null)
        dialog.setContentView(view)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        val tvTitle = view.findViewById<TextView>(R.id.tvZoomPermTitle)
        val tvCaption = view.findViewById<TextView>(R.id.tvZoomPermCaption)
        val btnClose = view.findViewById<ImageButton>(R.id.btnClosePermZoom)
        val ivFull = view.findViewById<ImageView>(R.id.ivPermGuideFull)

        tvTitle.text = step.title
        val hint = PermissionHelper.guideHintFor(step.id)
        tvCaption.text = buildString {
            if (!hint.isNullOrBlank()) {
                append(hint)
                append("\n\n")
            }
            append(step.howTo)
        }
        ivFull.setImageResource(imageRes)

        btnClose.setOnClickListener { dialog.dismiss() }

        val matrix = Matrix()
        var currentScale = 1f
        var minScale = 1f
        val maxScale = 4.5f
        val lastTouch = PointF()
        var isDragging = false

        fun fitImageToCenter() {
            val drawable = ivFull.drawable ?: return
            val vw = ivFull.width.toFloat()
            val vh = ivFull.height.toFloat()
            val dw = drawable.intrinsicWidth.toFloat()
            val dh = drawable.intrinsicHeight.toFloat()
            if (vw <= 0f || vh <= 0f || dw <= 0f || dh <= 0f) return

            val baseScale = min(vw / dw, vh / dh)
            minScale = baseScale
            currentScale = baseScale
            val dx = (vw - dw * baseScale) / 2f
            val dy = (vh - dh * baseScale) / 2f
            matrix.reset()
            matrix.postScale(baseScale, baseScale)
            matrix.postTranslate(dx, dy)
            ivFull.imageMatrix = matrix
        }

        ivFull.post { fitImageToCenter() }

        val scaleDetector = ScaleGestureDetector(
            context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val factor = detector.scaleFactor
                    val target = (currentScale * factor).coerceIn(minScale, minScale * maxScale)
                    val applied = target / currentScale
                    currentScale = target
                    matrix.postScale(applied, applied, detector.focusX, detector.focusY)
                    ivFull.imageMatrix = matrix
                    return true
                }
            }
        )

        val gestureDetector = GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    if (currentScale > minScale * 1.4f) {
                        fitImageToCenter()
                    } else {
                        val target = minScale * 2.2f
                        val factor = target / currentScale
                        currentScale = target
                        matrix.postScale(factor, factor, e.x, e.y)
                        ivFull.imageMatrix = matrix
                    }
                    return true
                }
            }
        )

        ivFull.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            scaleDetector.onTouchEvent(event)

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastTouch.set(event.x, event.y)
                    isDragging = true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isDragging && !scaleDetector.isInProgress && currentScale > minScale * 1.02f) {
                        val dx = event.x - lastTouch.x
                        val dy = event.y - lastTouch.y
                        matrix.postTranslate(dx, dy)
                        ivFull.imageMatrix = matrix
                        lastTouch.set(event.x, event.y)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isDragging = false
                }
            }
            true
        }

        dialog.show()
    }
}
