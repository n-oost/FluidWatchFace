package com.example.fluidwatchface

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.SurfaceHolder
import androidx.wear.watchface.CanvasRenderer
import androidx.wear.watchface.Renderer
import androidx.wear.watchface.WatchFace
import androidx.wear.watchface.WatchFaceService
import androidx.wear.watchface.style.UserStyleSchema

class MyWatchFaceService : WatchFaceService() {
    override fun createUserStyleSchema(): UserStyleSchema = UserStyleSchema(emptyList())

    override fun createWatchFace(surfaceHolder: SurfaceHolder, watchState: WatchState): WatchFace {
        return WatchFace(
            WatchFaceType.ANALOG,
            MyWatchFaceRenderer(
                context = this,
                surfaceHolder = surfaceHolder,
                watchState = watchState
            )
        )
    }
}
