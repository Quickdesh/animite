package eu.kanade.tachiyomi.ui.reader

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewPropertyAnimator
import androidx.core.graphics.withSave
import androidx.core.view.isVisible
import eu.kanade.tachiyomi.ui.reader.viewer.ViewerNavigation
import kotlin.math.abs

class ReaderNavigationOverlayView(context: Context, attributeSet: AttributeSet) : View(context, attributeSet) {

    private var viewPropertyAnimator: ViewPropertyAnimator? = null

    private var navigation: ViewerNavigation? = null

    fun setNavigation(navigation: ViewerNavigation, tappingEnabled: Boolean, showOnStart: Boolean) {
        val firstLaunch = this.navigation == null
        this.navigation = navigation
        invalidate()

        if (isVisible || (!showOnStart && firstLaunch) || !tappingEnabled) {
            return
        }

        viewPropertyAnimator = animate()
            .alpha(1f)
            .setDuration(FADE_DURATION)
            .withStartAction {
                isVisible = true
            }
            .withEndAction {
                viewPropertyAnimator = null
            }
        viewPropertyAnimator?.start()
    }

    private val regionPaint = Paint()

    private val textPaint = Paint().apply {
        textAlign = Paint.Align.CENTER
        color = Color.WHITE
        textSize = 64f
    }

    private val textBorderPaint = Paint().apply {
        textAlign = Paint.Align.CENTER
        color = Color.BLACK
        textSize = 64f
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }

    override fun onDraw(canvas: Canvas?) {
        if (navigation == null) return

        navigation?.regions?.forEach { region ->
            val rect = region.rectF

            canvas?.withSave {
                // Scale rect from 1f,1f to screen width and height
                scale(width.toFloat(), height.toFloat())
                regionPaint.color = context.getColor(region.type.colorRes)
                drawRect(rect, regionPaint)
            }
            // Don't want scale anymore because it messes with drawText
            canvas?.withSave {
                // Translate origin to rect start (left, top)
                translate((width * rect.left), (height * rect.top))

                // Calculate center of rect width on screen
                val x = width * (abs(rect.left - rect.right) / 2)

                // Calculate center of rect height on screen
                val y = height * (abs(rect.top - rect.bottom) / 2)

                drawText(context.getString(region.type.nameRes), x, y, textBorderPaint)
                drawText(context.getString(region.type.nameRes), x, y, textPaint)
            }
        }
    }

    override fun performClick(): Boolean {
        super.performClick()

        if (viewPropertyAnimator == null && isVisible) {
            viewPropertyAnimator = animate()
                .alpha(0f)
                .setDuration(FADE_DURATION)
                .withEndAction {
                    isVisible = false
                    viewPropertyAnimator = null
                }
            viewPropertyAnimator?.start()
        }

        return true
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        // Hide overlay if user start tapping or swiping
        performClick()
        return super.onTouchEvent(event)
    }
}

private const val FADE_DURATION = 1000L