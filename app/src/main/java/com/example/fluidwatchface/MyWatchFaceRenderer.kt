package com.example.fluidwatchface

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.SurfaceHolder
import androidx.wear.watchface.CanvasRenderer
import androidx.wear.watchface.Renderer
import androidx.wear.watchface.WatchState
import androidx.wear.watchface.style.CurrentUserStyleRepository

class MyWatchFaceRenderer(
    context: Context,
    surfaceHolder: SurfaceHolder,
    watchState: WatchState
) : CanvasRenderer(
    surfaceHolder,
    CurrentUserStyleRepository(emptyList()),
    watchState,
    CanvasRenderer.RendererParameters(
        CanvasRenderer.RendererParameters.DRAW_MODE_INTERACTIVE,
        0,
        false,
        false
    )
) {
    private val paint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    override fun render(canvas: Canvas, bounds: android.graphics.Rect, calendar: java.util.Calendar) {
        canvas.drawColor(Color.BLACK)
        val cx = bounds.exactCenterX()
        val cy = bounds.exactCenterY()
        canvas.drawCircle(cx, cy, 40f, paint)
    }
}