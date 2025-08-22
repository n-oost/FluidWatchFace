package com.example.fluidwatchface

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.SurfaceHolder
import androidx.wear.watchface.CanvasRenderer
import androidx.wear.watchface.WatchState
import androidx.wear.watchface.style.CurrentUserStyleRepository

class MyWatchFaceRenderer(
    surfaceHolder: SurfaceHolder,
    currentUserStyleRepository: CurrentUserStyleRepository,
    watchState: WatchState
) : CanvasRenderer(
    surfaceHolder,
    currentUserStyleRepository,
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

    override fun render(canvas: Canvas, bounds: Rect, calendar: java.util.Calendar) {
        canvas.drawColor(Color.BLACK)
        val cx = bounds.exactCenterX()
        val cy = bounds.exactCenterY()
        canvas.drawCircle(cx, cy, 40f,