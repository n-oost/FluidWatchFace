package com.example.fluidwatchface

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.icu.text.SimpleDateFormat
import android.icu.util.Calendar
import android.view.SurfaceHolder
import androidx.wear.watchface.CanvasComplicationFactory
import androidx.wear.watchface.ComplicationHelperActivity
import androidx.wear.watchface.ComplicationSlot
import androidx.wear.watchface.ComplicationSlotsManager
import androidx.wear.watchface.ComplicationTapFilter
import androidx.wear.watchface.ComplicationType
import androidx.wear.watchface.DrawMode
import androidx.wear.watchface.Renderer
import androidx.wear.watchface.TapEvent
import androidx.wear.watchface.TapListener
import androidx.wear.watchface.WatchFace
import androidx.wear.watchface.WatchFaceService
import androidx.wear.watchface.WatchFaceType
import androidx.wear.watchface.WatchState
import androidx.wear.watchface.complications.DefaultComplicationDataSourcePolicy
import androidx.wear.watchface.complications.SystemDataSources
import androidx.wear.watchface.complications.rendering.CanvasComplicationDrawable
import androidx.wear.watchface.complications.rendering.ComplicationDrawable
import androidx.wear.watchface.style.CurrentUserStyleRepository
import androidx.wear.watchface.style.UserStyleSchema
import androidx.wear.watchface.style.UserStyleSetting
import androidx.wear.watchface.style.WatchFaceLayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import kotlin.math.sqrt
import kotlin.random.Random


/**
 * Represents a single particle in the fluid animation.
 *
 * @property x The current x-coordinate of the particle.
 * @property y The current y-coordinate of the particle.
 * @property velocityX The current velocity of the particle along the x-axis.
 * @property velocityY The current velocity of the particle along the y-axis.
 * @property radius The radius of the particle.
 * @property alpha The transparency of the particle.
 */
class Particle(
    var x: Float,
    var y: Float,
    var velocityX: Float,
    var velocityY: Float,
    var radius: Float,
    var alpha: Int = 255 // Default to fully opaque
) {
    /**
     * Updates the particle's position based on its velocity and elapsed time.
     * Implements wrap-around logic to keep particles on screen.
     * @param deltaTimeMillis The time elapsed since the last frame in milliseconds.
     * @param bounds The rectangular bounds within which the particle should move.
     */
    fun update(deltaTimeMillis: Long, bounds: Rect) {
        val dtSeconds = deltaTimeMillis / 1000f // Convert to seconds for velocity calculation
        x += velocityX * dtSeconds * 50 // Multiply by a factor to control apparent speed
        y += velocityY * dtSeconds * 50

        // Wrap-around logic: if a particle goes off one edge, it reappears on the opposite edge.
        if (x < bounds.left - radius) x = bounds.right + radius
        if (x > bounds.right + radius) x = bounds.left - radius
        if (y < bounds.top - radius) y = bounds.bottom + radius
        if (y > bounds.bottom + radius) y = bounds.top - radius
    }

    /**
     * Draws the particle on the canvas.
     * @param canvas The canvas to draw on.
     * @param paint The paint object used to draw the particle (color is usually set externally).
     */
    fun draw(canvas: Canvas, paint: Paint) {
        val originalAlpha = paint.alpha // Preserve original paint alpha
        paint.alpha = alpha // Apply particle-specific alpha
        canvas.drawCircle(x, y, radius, paint)
        paint.alpha = originalAlpha // Restore original paint alpha
    }
}

/**
 * Represents the rolling ball in the Zen Garden background.
 *
 * @property x The current x-coordinate of the ball.
 * @property y The current y-coordinate of the ball.
 * @property radius The radius of the ball.
 * @property velocityX The current velocity of the ball along the x-axis.
 * @property velocityY The current velocity of the ball along the y-axis.
 */
class Ball(
    var x: Float,
    var y: Float,
    var radius: Float,
    var velocityX: Float,
    var velocityY: Float
) {
    companion object {
        // Scales the effect of deltaTime on ball's speed. Adjust for desired speed.
        private const val BALL_SPEED_SCALE = 40f
    }

    /**
     * Updates the ball's position based on its velocity and elapsed time.
     * Implements bouncing logic to keep the ball within screen bounds.
     * @param deltaTimeMillis The time elapsed since the last frame in milliseconds.
     * @param bounds The rectangular bounds within which the ball should move.
     */
    fun update(deltaTimeMillis: Long, bounds: Rect) {
        val dtSeconds = deltaTimeMillis / 1000f // Convert to seconds
        x += velocityX * dtSeconds * BALL_SPEED_SCALE
        y += velocityY * dtSeconds * BALL_SPEED_SCALE

        // Bounce logic: if the ball hits an edge, reverse its velocity on that axis.
        if (x - radius < bounds.left) {
            x = bounds.left + radius
            velocityX = -velocityX
        } else if (x + radius > bounds.right) {
            x = bounds.right - radius
            velocityX = -velocityX
        }

        if (y - radius < bounds.top) {
            y = bounds.top + radius
            velocityY = -velocityY
        } else if (y + radius > bounds.bottom) {
            y = bounds.bottom - radius
            velocityY = -velocityY
        }
    }

    /**
     * Draws the ball on the main canvas, including a glossy highlight.
     * @param canvas The main canvas to draw the ball on.
     * @param bodyPaint The paint for the main body of the ball (e.g., silver).
     * @param highlightPaint The paint for the glossy highlight (e.g., white).
     */
    fun draw(canvas: Canvas, bodyPaint: Paint, highlightPaint: Paint) {
        // Draw the main body of the ball
        canvas.drawCircle(x, y, radius, bodyPaint)

        // Draw a simple glossy highlight as a smaller, offset circle.
        // A more advanced highlight could use a RadialGradient in highlightPaint.
        val highlightRadius = radius * 0.5f
        val highlightOffsetX = radius * 0.25f // Offset towards one side
        val highlightOffsetY = -radius * 0.35f // Offset towards top-left for typical lighting
        canvas.drawCircle(x + highlightOffsetX, y + highlightOffsetY, highlightRadius, highlightPaint)
    }

    /**
     * Draws the trail of the ball on the sand canvas (off-screen bitmap).
     * @param canvas The canvas to draw the trail on (typically the sandCanvas).
     * @param trailPaint The paint used for drawing the trail (e.g., a darker sand color).
     */
    fun drawTrail(canvas: Canvas, trailPaint: Paint) {
        // Draw a circle at the ball's current position on the provided canvas (sandCanvas).
        canvas.drawCircle(x, y, radius, trailPaint) // Trail radius matches ball radius
    }
}


/**
 * MyWatchFace defines the Wear OS watch face service.
 * It's responsible for creating the watch face instance and handling its lifecycle.
 */
class MyWatchFace : WatchFaceService() {

    // Scope for service-level coroutines, cancelled when the service is destroyed.
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // Instance of the canvas renderer, initialized in createWatchFace.
    private lateinit var canvasRendererInstance: MyCanvasRenderer

    /**
     * Called by the system to create the watch face.
     * This is where the UserStyleSchema, ComplicationSlotsManager, and Renderer are set up.
     */
    override suspend fun createWatchFace(
        surfaceHolder: SurfaceHolder,
        watchState: WatchState,
        complicationSlotsManagerParam: ComplicationSlotsManager, // Renamed to avoid conflict
        currentUserStyleRepository: CurrentUserStyleRepository
    ): WatchFace {

        // 1. Create UserStyleSchema
        val userStyleSchema = createUserStyleSchema(applicationContext)

        // 2. Create ComplicationSlotsManager using the schema and default data sources
        val complicationSlotsManager = createComplicationSlotsManagerAndStyle(
            applicationContext,
            currentUserStyleRepository,
            watchState,
            userStyleSchema
        )

        // 3. Create the CanvasRenderer
        canvasRendererInstance = MyCanvasRenderer(
            context = applicationContext,
            surfaceHolder = surfaceHolder,
            watchState = watchState,
            currentUserStyleRepository = currentUserStyleRepository,
            canvasType = Renderer.CanvasType.HARDWARE, // Use hardware acceleration
            userStyleSchema = userStyleSchema,
            complicationSlotsManager = complicationSlotsManager
        )

        // 4. Create and return the WatchFace
        return WatchFace(
            watchFaceType = WatchFaceType.DIGITAL,
            renderer = canvasRendererInstance
        )
            .setComplicationSlotsManager(complicationSlotsManager)
            .setUserStyleSchema(userStyleSchema)
    }

    /**
     * Provides a tap listener for the watch face.
     * This is used to handle touch interactions, particularly for the "Fluid (Touch)" background.
     * Returns null if the renderer isn't initialized yet.
     */
    override fun getTapListener(): TapListener? {
        // Ensure renderer is initialized before returning listener.
        if (!::canvasRendererInstance.isInitialized) return null
        return MyTapListener(canvasRendererInstance)
    }

    /**
     * Custom TapListener to delegate tap events to the MyCanvasRenderer.
     */
    private inner class MyTapListener(private val renderer: MyCanvasRenderer) : TapListener {
        /**
         * Called when a tap event occurs on the watch face.
         * @param tapEvent Details about the tap, including type and coordinates.
         */
        override fun onTap(tapEvent: TapEvent) {
            // We are interested in the "UP" event to trigger after the tap is completed.
            if (tapEvent.tapType == TapEvent.TYPE_UP) {
                // Only handle taps if the "Fluid (Touch)" background is active.
                if (renderer.currentSelectedBackgroundStyleId == BACKGROUND_FLUID_TOUCH_ID) {
                    renderer.handleTap(tapEvent.xPos, tapEvent.yPos)
                }
            }
        }
    }

    /**
     * Called when the watch face service is being destroyed.
     * Cancels the serviceScope to clean up coroutines.
     */
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    /**
     * Helper function to create and configure the ComplicationSlotsManager.
     * @param context The application context.
     * @param currentUserStyleRepository Repository for accessing the current user style.
     * @param watchState Provides information about the watch's current state.
     * @param userStyleSchema The schema defining user-configurable styles.
     * @return The configured ComplicationSlotsManager.
     */
    private fun createComplicationSlotsManagerAndStyle(
        context: Context,
        currentUserStyleRepository: CurrentUserStyleRepository,
        watchState: WatchState,
        userStyleSchema: UserStyleSchema
    ): ComplicationSlotsManager {
        // Define a default data source policy for complications.
        // This specifies what data sources to try if the user hasn't picked one.
        val defaultDataSourcePolicy = DefaultComplicationDataSourcePolicy(
            primaryDataSource = SystemDataSources.NO_DATA_SOURCE, // Default to empty
            secondaryDataSource = SystemDataSources.WATCH_BATTERY, // Fallback
            tertiaryDataSource = SystemDataSources.STEP_COUNT,    // Further fallback
            type = ComplicationType.SHORT_TEXT // Default type if source supports multiple
        )

        // Helper lambda to create a single complication slot.
        val createComplicationSlot = { id: Int, bounds: Rect ->
            val supportedTypes = listOf(
                ComplicationType.SHORT_TEXT,
                ComplicationType.RANGED_VALUE,
                ComplicationType.ICON,
                ComplicationType.SMALL_IMAGE
            )
            // Factory for creating complication drawables, styled according to user selection.
            val canvasComplicationFactory = CanvasComplicationFactory { ws, _ ->
                val accentColor = Companion.getAccentColor(
                    currentUserStyleRepository.userStyle.value, // Current style map
                    ws.immutableSystemTime.zonedDateTime,       // Current time for dynamic colors
                    userStyleSchema                             // Schema for defaults
                )
                ComplicationDrawable(context).apply {
                    activeStyle.textColor = accentColor
                    activeStyle.iconColor = accentColor
                    // Other style properties like text size, font can be set here.
                }
            }
            ComplicationSlot.createRoundRectComplicationSlotBuilder(
                id = id,
                canvasComplicationFactory = canvasComplicationFactory,
                supportedTypes = supportedTypes,
                defaultDataSourcePolicy = defaultDataSourcePolicy,
                bounds = bounds // Placeholder, will be updated in MyCanvasRenderer.invalidate()
            )
                // Intent to launch when the complication is tapped (usually for configuration).
                .setTapFilter(ComplicationTapFilter(ComplicationHelperActivity.createComplicationHelperIntent(context, id)))
                .build()
        }

        // Create three complication slots with placeholder bounds.
        // Actual bounds will be calculated and set in MyCanvasRenderer.invalidate().
        val placeholderBounds = Rect(0, 0, 1, 1) // Minimal placeholder
        val leftComplication = createComplicationSlot(LEFT_COMPLICATION_ID, placeholderBounds)
        val middleComplication = createComplicationSlot(MIDDLE_COMPLICATION_ID, placeholderBounds)
        val rightComplication = createComplicationSlot(RIGHT_COMPLICATION_ID, placeholderBounds)

        return ComplicationSlotsManager(
            listOf(leftComplication, middleComplication, rightComplication),
            userStyleSchema
        )
    }

    companion object {
        // Style Setting IDs
        const val COLOR_THEME_SETTING_ID = "color_theme_setting"
        const val BACKGROUND_STYLE_SETTING_ID = "background_style_setting"

        // Color Theme Option IDs
        const val COLOR_DYNAMIC_ID = "dynamic_color"
        const val COLOR_MINT_ID = "mint_color"
        const val COLOR_SKY_BLUE_ID = "sky_blue_color"
        const val COLOR_ORANGE_ID = "orange_color"
        const val COLOR_WHITE_ID = "white_color"

        // Background Style Option IDs
        const val BACKGROUND_STATIC_ID = "static_background"
        const val BACKGROUND_FLUID_MOTION_ID = "fluid_motion_background"
        const val BACKGROUND_FLUID_TOUCH_ID = "fluid_touch_background"
        const val BACKGROUND_ZEN_GARDEN_ID = "zen_garden_background"

        // Custom Color Definitions
        val MINT_COLOR = Color.rgb(152, 255, 179)
        val SKY_BLUE_COLOR = Color.rgb(135, 206, 235)
        val ORANGE_COLOR = Color.rgb(255, 165, 0)
        val SAND_COLOR = Color.rgb(210, 180, 140) // For Zen Garden
        val DARKEN_SAND_COLOR = Color.rgb(160, 130, 100) // For Zen Garden trails


        // Complication IDs
        const val LEFT_COMPLICATION_ID = 100
        const val MIDDLE_COMPLICATION_ID = 101
        const val RIGHT_COMPLICATION_ID = 102

        /**
         * Creates the UserStyleSchema for this watch face.
         * The schema defines user-configurable styles like color themes and background animations.
         * @param context The application context, used for retrieving string resources if any.
         * @return The configured UserStyleSchema.
         */
        fun createUserStyleSchema(context: Context): UserStyleSchema {
            // Color Theme Setting
            val colorThemeSetting = UserStyleSetting.ListUserStyleSetting(
                id = UserStyleSetting.Id(COLOR_THEME_SETTING_ID),
                displayName = "Color Theme",
                description = "Sets the accent color for time and icons",
                icon = null, // Optional: Icon for the setting
                options = listOf(
                    UserStyleSetting.ListUserStyleSetting.ListOption(UserStyleSetting.Option.Id(COLOR_DYNAMIC_ID), "Dynamic", null),
                    UserStyleSetting.ListUserStyleSetting.ListOption(UserStyleSetting.Option.Id(COLOR_MINT_ID), "Mint", null),
                    UserStyleSetting.ListUserStyleSetting.ListOption(UserStyleSetting.Option.Id(COLOR_SKY_BLUE_ID), "Sky Blue", null),
                    UserStyleSetting.ListUserStyleSetting.ListOption(UserStyleSetting.Option.Id(COLOR_ORANGE_ID), "Orange", null),
                    UserStyleSetting.ListUserStyleSetting.ListOption(UserStyleSetting.Option.Id(COLOR_WHITE_ID), "White", null)
                ),
                defaultOptionIndex = 0, // "Dynamic" is default
                affectsLayers = listOf(WatchFaceLayer.BASE, WatchFaceLayer.COMPLICATIONS, WatchFaceLayer.TOP_LAYER)
            )

            // Background Style Setting
            val backgroundStyleSetting = UserStyleSetting.ListUserStyleSetting(
                id = UserStyleSetting.Id(BACKGROUND_STYLE_SETTING_ID),
                displayName = "Background Style",
                description = "Sets the background animation",
                icon = null,
                options = listOf(
                    UserStyleSetting.ListUserStyleSetting.ListOption(UserStyleSetting.Option.Id(BACKGROUND_STATIC_ID), "Static", null),
                    UserStyleSetting.ListUserStyleSetting.ListOption(UserStyleSetting.Option.Id(BACKGROUND_FLUID_MOTION_ID), "Fluid (Motion)", null),
                    UserStyleSetting.ListUserStyleSetting.ListOption(UserStyleSetting.Option.Id(BACKGROUND_FLUID_TOUCH_ID), "Fluid (Touch)", null),
                    UserStyleSetting.ListUserStyleSetting.ListOption(UserStyleSetting.Option.Id(BACKGROUND_ZEN_GARDEN_ID), "Zen Garden", null)
                ),
                defaultOptionIndex = 0, // "Static" is default
                affectsLayers = listOf(WatchFaceLayer.BACKGROUND)
            )

            return UserStyleSchema(listOf(colorThemeSetting, backgroundStyleSetting))
        }

        /**
         * Determines the accent color based on the current user style and time (for dynamic theme).
         * @param userStyle A map of current style settings to their selected options.
         * @param zonedDateTime The current date and time.
         * @param schema The UserStyleSchema, used to get default options if not found in userStyle.
         * @return The integer value of the accent color.
         */
        fun getAccentColor(userStyle: Map<UserStyleSetting.Id, UserStyleSetting.Option>, zonedDateTime: ZonedDateTime, schema: UserStyleSchema): Int {
            // Get the ID of the selected color option, or the default if none is selected.
            val colorOptionId = (userStyle[UserStyleSetting.Id(COLOR_THEME_SETTING_ID)]
                ?: schema[UserStyleSetting.Id(COLOR_THEME_SETTING_ID)]!!.defaultOption).id.value

            return when (colorOptionId) {
                COLOR_DYNAMIC_ID -> when (zonedDateTime.hour) {
                    in 6..11 -> SKY_BLUE_COLOR   // Morning: Sky Blue
                    in 12..17 -> ORANGE_COLOR    // Afternoon: Orange
                    else -> MINT_COLOR           // Night/Early Morning: Mint
                }
                COLOR_MINT_ID -> MINT_COLOR
                COLOR_SKY_BLUE_ID -> SKY_BLUE_COLOR
                COLOR_ORANGE_ID -> ORANGE_COLOR
                COLOR_WHITE_ID -> Color.WHITE
                else -> Color.WHITE // Fallback
            }
        }
    }
}

/**
 * MyCanvasRenderer is responsible for all the drawing logic of the watch face.
 * It handles rendering the time, date, complications, and dynamic backgrounds.
 * It also manages resources for animations like particles and the Zen Garden.
 *
 * @param context The application context.
 * @param surfaceHolder The surface holder for drawing.
 * @param watchState Provides information about the watch's current state (e.g., ambient mode, screen bounds).
 * @param currentUserStyleRepository Repository for accessing and observing user style changes.
 * @param canvasType The type of canvas to use (e.g., hardware accelerated).
 * @param userStyleSchema The schema defining user-configurable styles.
 * @param complicationSlotsManager Manages the complication slots.
 */
class MyCanvasRenderer(
    private val context: Context,
    surfaceHolder: SurfaceHolder,
    private val watchState: WatchState,
    private val currentUserStyleRepository: CurrentUserStyleRepository,
    canvasType: Int,
    private val userStyleSchema: UserStyleSchema,
    private val complicationSlotsManager: ComplicationSlotsManager
) : Renderer.CanvasRenderer2<MyCanvasRenderer.SharedAssets>( // Use CanvasRenderer2 for createSharedAssets
    surfaceHolder,
    currentUserStyleRepository,
    watchState,
    canvasType,
    16L, // framePeriodMs for interactive mode (approx 60fps)
    false // clearWithBackgroundTintBeforeComplications - false as we manage full background
) {
    /**
     * Shared assets for the renderer. Can be used for resources that are expensive to create
     * and can be shared between interactive and ambient modes if needed.
     * Currently, no complex shared assets are used.
     */
    class SharedAssets : Renderer.SharedAssets {
        /** Called when the SharedAssets are no longer needed and should release resources. */
        override fun onDestroy() {
            // Release any resources held by SharedAssets here, if any.
        }
    }

    // Scope for renderer-specific coroutines, cancelled when the renderer is destroyed.
    private val rendererScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // Date and Time formatters, initialized with the device's current locale.
    private val timeFormat = SimpleDateFormat("HH:mm", context.resources.configuration.locales[0])
    private val dateFormat = SimpleDateFormat("EEE MMM dd", context.resources.configuration.locales[0])

    // Current selected style IDs, updated by observing currentUserStyleRepository.
    var currentSelectedColorThemeOptionId: String
    var currentSelectedBackgroundStyleId: String

    // --- Paint Objects ---
    // Initialized once to avoid re-creation during rendering.
    // Time paint: For drawing the digital time. Color is updated dynamically.
    private val timePaint = Paint().apply { isAntiAlias = true; textSize = 80f; textAlign = Paint.Align.CENTER }
    // Date paint: For drawing the date. Color is fixed (LTGRAY).
    private val datePaint = Paint().apply { isAntiAlias = true; textSize = 30f; textAlign = Paint.Align.CENTER; color = Color.LTGRAY }
    // Particle paint: For fluid background particles. Color is updated dynamically.
    private val particlePaint = Paint().apply { isAntiAlias = true }
    // Ball paints: For Zen Garden ball. Colors are fixed (silver and white).
    private val ballPaint = Paint().apply { isAntiAlias = true; color = Color.LTGRAY } // Silver
    private val ballHighlightPaint = Paint().apply { isAntiAlias = true; color = Color.WHITE }
    // Zen Garden specific paints
    private val trailPaint = Paint().apply { isAntiAlias = true; color = MyWatchFace.DARKEN_SAND_COLOR; style = Paint.Style.FILL }
    private val sandBitmapPaint = Paint() // Default paint for drawing the sand bitmap
    private val sandFadePaint: Paint // Initialized in init
    private val specklePaint: Paint // Initialized in init for sand texture

    // --- Animation & State Variables ---
    // List for fluid particles.
    private val particles = mutableListOf<Particle>()
    // Instance for the Zen Garden ball.
    private var ball: Ball? = null
    // Timestamp of the last rendered frame, used for calculating deltaTime.
    private var lastFrameTimeMillis: Long = 0L
    // Calendar instance for time/date formatting, reused to avoid re-creation.
    private val calendar: Calendar = Calendar.getInstance()
    // Off-screen bitmap and canvas for Zen Garden sand and trails.
    private var sandBitmap: Bitmap? = null
    private var sandCanvas: Canvas? = null


    // --- Complication Bounds ---
    // Rect objects to store the calculated bounds for each complication slot.
    // These are updated in invalidate() when screen dimensions are known.
    private var leftComplicationBounds = Rect()
    private var middleComplicationBounds = Rect()
    private var rightComplicationBounds = Rect()

    companion object {
        // Constants for Fluid Particle Animation
        private const val NUM_PARTICLES = 50       // Number of particles
        private const val PARTICLE_BASE_SPEED = 0.5f // Base speed factor for particles
        private const val PARTICLE_RADIUS_MIN = 2f   // Minimum radius of a particle
        private const val PARTICLE_RADIUS_MAX = 5f   // Maximum radius of a particle
        private const val TOUCH_INTERACTION_RADIUS = 75f // Radius for touch interaction with particles
        private const val PARTICLE_SCATTER_SPEED = 5f  // Speed at which particles scatter on touch

        // Constants for Zen Garden Ball
        private const val BALL_RADIUS = 20f          // Radius of the Zen Garden ball
        private const val INITIAL_BALL_SPEED = 2.5f  // Initial speed factor for the ball

        // Constants for Zen Garden Texture/Fading
        private const val FADE_ALPHA = 5 // Alpha value for trail fading (0-255, lower is slower fade)
        private const val SPECKLE_DENSITY_FACTOR = 30 // Lower means more speckles for sand texture
    }

    init {
        // Initialize paint objects that require context or dynamic properties
        val sandBaseRed = Color.red(MyWatchFace.SAND_COLOR)
        val sandBaseGreen = Color.green(MyWatchFace.SAND_COLOR)
        val sandBaseBlue = Color.blue(MyWatchFace.SAND_COLOR)
        sandFadePaint = Paint().apply {
            style = Paint.Style.FILL
            color = Color.argb(FADE_ALPHA, sandBaseRed, sandBaseGreen, sandBaseBlue)
        }
        specklePaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }


        // Initialize current style IDs from the repository or schema defaults.
        currentSelectedColorThemeOptionId =
            (currentUserStyleRepository.userStyle.value[UserStyleSetting.Id(MyWatchFace.COLOR_THEME_SETTING_ID)]
                ?: userStyleSchema[UserStyleSetting.Id(MyWatchFace.COLOR_THEME_SETTING_ID)]!!.defaultOption).id.value

        currentSelectedBackgroundStyleId =
            (currentUserStyleRepository.userStyle.value[UserStyleSetting.Id(MyWatchFace.BACKGROUND_STYLE_SETTING_ID)]
                ?: userStyleSchema[UserStyleSetting.Id(MyWatchFace.BACKGROUND_STYLE_SETTING_ID)]!!.defaultOption).id.value

        // Observe user style changes.
        rendererScope.launch {
            currentUserStyleRepository.userStyle.collect { userStyle ->
                // Update color theme ID
                currentSelectedColorThemeOptionId =
                    (userStyle[UserStyleSetting.Id(MyWatchFace.COLOR_THEME_SETTING_ID)]
                        ?: userStyleSchema[UserStyleSetting.Id(MyWatchFace.COLOR_THEME_SETTING_ID)]!!.defaultOption).id.value

                // Update background style ID and handle resource changes
                val newBackgroundStyleId =
                    (userStyle[UserStyleSetting.Id(MyWatchFace.BACKGROUND_STYLE_SETTING_ID)]
                        ?: userStyleSchema[UserStyleSetting.Id(MyWatchFace.BACKGROUND_STYLE_SETTING_ID)]!!.defaultOption).id.value

                if (newBackgroundStyleId != currentSelectedBackgroundStyleId) {
                    currentSelectedBackgroundStyleId = newBackgroundStyleId
                    // Handle background-specific resource initialization/clearing based on new style.
                    // This is done only if screenBounds are valid.
                    if (watchState.screenBounds.width() > 0 && watchState.screenBounds.height() > 0) {
                        when (currentSelectedBackgroundStyleId) {
                            MyWatchFace.BACKGROUND_FLUID_MOTION_ID, MyWatchFace.BACKGROUND_FLUID_TOUCH_ID -> {
                                clearZenGardenResources() // Clear Zen Garden if switching to fluid
                                initializeParticles(watchState.screenBounds)
                                ball = null // Clear ball if switching to fluid
                            }
                            MyWatchFace.BACKGROUND_ZEN_GARDEN_ID -> {
                                clearParticles() // Clear fluid if switching to Zen
                                initializeZenGardenResources(watchState.screenBounds)
                                initializeBall(watchState.screenBounds)
                                // particles.clear() is handled by clearParticles()
                            }
                            else -> { // Static background or others
                                clearParticles()
                                clearZenGardenResources()
                                ball = null
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Handles tap events delegated from MyWatchFace.TapListener.
     * Used for the "Fluid (Touch)" background to scatter particles.
     * @param tapX The x-coordinate of the tap.
     * @param tapY The y-coordinate of the tap.
     */
    fun handleTap(tapX: Int, tapY: Int) {
        // Only react to taps if "Fluid (Touch)" is active and not in ambient mode.
        if (currentSelectedBackgroundStyleId != MyWatchFace.BACKGROUND_FLUID_TOUCH_ID ||
            watchState.isAmbient // Use watchState.isAmbient
        ) return

        particles.forEach { particle ->
            val dx = particle.x - tapX
            val dy = particle.y - tapY
            val distance = sqrt(dx * dx + dy * dy) // Using Float version of sqrt
            // If particle is within interaction radius and not directly at the tap point
            if (distance < TOUCH_INTERACTION_RADIUS && distance > 0) {
                val scatterVx = dx / distance // Normalized vector component
                val scatterVy = dy / distance // Normalized vector component
                particle.velocityX = scatterVx * PARTICLE_SCATTER_SPEED
                particle.velocityY = scatterVy * PARTICLE_SCATTER_SPEED
            }
        }
    }

    /**
     * Initializes or re-initializes particles for fluid backgrounds.
     * Called when switching to a fluid style or when screen bounds change.
     * @param bounds The current screen bounds.
     */
    private fun initializeParticles(bounds: Rect) {
        if (bounds.isEmpty) return // Do nothing if bounds are invalid
        particles.clear()
        for (i in 0 until NUM_PARTICLES) {
            val randomX = Random.nextFloat() * bounds.width() + bounds.left
            val randomY = Random.nextFloat() * bounds.height() + bounds.top
            val randomVelocityX = (Random.nextFloat() * 2f - 1f) * PARTICLE_BASE_SPEED
            val randomVelocityY = (Random.nextFloat() * 2f - 1f) * PARTICLE_BASE_SPEED
            val randomRadius = Random.nextFloat() * (PARTICLE_RADIUS_MAX - PARTICLE_RADIUS_MIN) + PARTICLE_RADIUS_MIN
            particles.add(Particle(randomX, randomY, randomVelocityX, randomVelocityY, randomRadius))
        }
    }

    /** Clears all particles from the list. */
    private fun clearParticles() {
        particles.clear()
    }


    /**
     * Initializes or re-initializes the Zen Garden ball.
     * Called when switching to Zen Garden style or when screen bounds change.
     * @param bounds The current screen bounds.
     */
    private fun initializeBall(bounds: Rect) {
        if (bounds.isEmpty) return // Do nothing if bounds are invalid
        ball = Ball(
            x = bounds.exactCenterX(),
            y = bounds.exactCenterY(),
            radius = BALL_RADIUS,
            velocityX = (Random.nextFloat() * 2f - 1f) * INITIAL_BALL_SPEED,
            velocityY = (Random.nextFloat() * 2f - 1f) * INITIAL_BALL_SPEED
        )
    }

    /**
     * Initializes resources for the Zen Garden background (sand bitmap and canvas).
     * @param bounds The current screen bounds.
     */
    private fun initializeZenGardenResources(bounds: Rect) {
        if (bounds.isEmpty) return

        // Create or reuse bitmap only if necessary (size changed or not yet created)
        if (sandBitmap == null || sandBitmap!!.width != bounds.width() || sandBitmap!!.height != bounds.height()) {
            sandBitmap?.recycle() // Recycle old bitmap if it exists
            sandBitmap = Bitmap.createBitmap(bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888)
            sandCanvas = Canvas(sandBitmap!!)
        }

        sandCanvas?.let { canvas ->
            // Fill with base sand color
            canvas.drawColor(MyWatchFace.SAND_COLOR)

            // Add speckles for texture
            val numSpeckles = (bounds.width() * bounds.height()) / SPECKLE_DENSITY_FACTOR
            val baseRed = Color.red(MyWatchFace.SAND_COLOR)
            val baseGreen = Color.green(MyWatchFace.SAND_COLOR)
            val baseBlue = Color.blue(MyWatchFace.SAND_COLOR)

            for (i in 0 until numSpeckles) {
                val speckleX = Random.nextFloat() * bounds.width()
                val speckleY = Random.nextFloat() * bounds.height()
                val variation = Random.nextInt(-20, 21) // Color variation for speckles

                // Apply variation and clamp to 0-255 range
                val r = (baseRed + variation).coerceIn(0, 255)
                val g = (baseGreen + variation).coerceIn(0, 255)
                val b = (baseBlue + variation).coerceIn(0, 255)

                specklePaint.color = Color.rgb(r, g, b)
                canvas.drawCircle(speckleX, speckleY, 1f, specklePaint) // Draw 1px radius speckle
            }
        }
    }

    /** Clears Zen Garden specific resources like the sand bitmap. */
    private fun clearZenGardenResources() {
        sandBitmap?.recycle()
        sandBitmap = null
        sandCanvas = null // Canvas becomes invalid once bitmap is recycled
    }


    /**
     * Called when the watch face surface is created or its size changes.
     * This is where complication bounds are calculated and set.
     * It also triggers re-initialization of background elements if needed.
     * @param bounds The new screen bounds.
     */
    override fun invalidate(bounds: Rect) {
        super.invalidate(bounds)

        // Calculate and set bounds for the three complication slots at the bottom.
        val complicationRadius = bounds.width() * 0.10f // Radius of each circular slot
        val complicationSlotYPosition = bounds.height() * 0.85f // Vertical center for slots
        val interComplicationSpacing = bounds.width() * 0.05f // Horizontal spacing between slots
        val slotDiameter = (complicationRadius * 2).toInt()
        val totalComplicationWidth = (3 * slotDiameter) + (2 * interComplicationSpacing)
        var currentComplicationXStart = bounds.exactCenterX() - (totalComplicationWidth / 2f)

        // Left Complication
        leftComplicationBounds.set(currentComplicationXStart.toInt(), (complicationSlotYPosition - complicationRadius).toInt(), (currentComplicationXStart + slotDiameter).toInt(), (complicationSlotYPosition + complicationRadius).toInt())
        complicationSlotsManager[MyWatchFace.LEFT_COMPLICATION_ID]?.bounds = leftComplicationBounds
        currentComplicationXStart += slotDiameter + interComplicationSpacing

        // Middle Complication
        middleComplicationBounds.set(currentComplicationXStart.toInt(), (complicationSlotYPosition - complicationRadius).toInt(), (currentComplicationXStart + slotDiameter).toInt(), (complicationSlotYPosition + complicationRadius).toInt())
        complicationSlotsManager[MyWatchFace.MIDDLE_COMPLICATION_ID]?.bounds = middleComplicationBounds
        currentComplicationXStart += slotDiameter + interComplicationSpacing

        // Right Complication
        rightComplicationBounds.set(currentComplicationXStart.toInt(), (complicationSlotYPosition - complicationRadius).toInt(), (currentComplicationXStart + slotDiameter).toInt(), (complicationSlotYPosition + complicationRadius).toInt())
        complicationSlotsManager[MyWatchFace.RIGHT_COMPLICATION_ID]?.bounds = rightComplicationBounds


        // Re-initialize active background elements if bounds are valid.
        // This ensures animations/textures are correctly sized for the new bounds.
        if (!bounds.isEmpty) {
            when (currentSelectedBackgroundStyleId) {
                MyWatchFace.BACKGROUND_FLUID_MOTION_ID, MyWatchFace.BACKGROUND_FLUID_TOUCH_ID -> {
                    clearZenGardenResources() // Ensure Zen resources are cleared
                    initializeParticles(bounds)
                    ball = null
                }
                MyWatchFace.BACKGROUND_ZEN_GARDEN_ID -> {
                    clearParticles() // Ensure fluid resources are cleared
                    initializeZenGardenResources(bounds)
                    initializeBall(bounds)
                }
                // Static background: no specific re-initialization needed beyond clearing others.
                else -> {
                    clearParticles()
                    clearZenGardenResources()
                    ball = null
                }
            }
        }
    }


    /**
     * Factory method for creating SharedAssets.
     * SharedAssets can hold resources that are expensive to create and can be shared
     * between interactive and ambient modes (e.g., heavy bitmaps, shaders).
     * This implementation uses a simple SharedAssets instance.
     * @return An instance of SharedAssets.
     */
    override suspend fun createSharedAssets(): SharedAssets = SharedAssets()


    /**
     * The main rendering method, called for each frame.
     * @param canvas The canvas to draw on.
     * @param bounds The current screen bounds.
     * @param zonedDateTime The current date and time.
     * @param sharedAssets The shared assets instance.
     */
    override fun render(
        canvas: Canvas,
        bounds: Rect,
        zonedDateTime: ZonedDateTime,
        renderParameters: androidx.wear.watchface.RenderParameters, // Make sure this is the correct RenderParameters
        sharedAssets: SharedAssets
    ) {
        // Determine the actual accent color based on user style and current time.
        val actualAccentColor = MyWatchFace.getAccentColor(currentUserStyleRepository.userStyle.value, zonedDateTime, userStyleSchema)
        timePaint.color = actualAccentColor
        particlePaint.color = actualAccentColor
        // Ball color is fixed silver as per requirements.

        val isAmbient = renderParameters.drawMode == DrawMode.AMBIENT
        var currentBackgroundColor = Color.BLACK // Default background is black

        // --- Handle Background Style and Animations ---
        when (currentSelectedBackgroundStyleId) {
            MyWatchFace.BACKGROUND_ZEN_GARDEN_ID -> {
                currentBackgroundColor = MyWatchFace.SAND_COLOR // Base for Zen Garden (covered by bitmap)
                if (sandBitmap != null && sandCanvas != null) {
                    if (!isAmbient) {
                        // Apply fade effect to existing trails on sandCanvas
                        sandCanvas!!.drawRect(0f, 0f, sandBitmap!!.width.toFloat(), sandBitmap!!.height.toFloat(), sandFadePaint)

                        // Update and draw new trail for the ball if it exists
                        ball?.let {
                            val currentTimeMillis = zonedDateTime.toInstant().toEpochMilli()
                            val deltaTimeMillis = if (lastFrameTimeMillis == 0L) 0L else currentTimeMillis - lastFrameTimeMillis
                            lastFrameTimeMillis = currentTimeMillis
                            it.update(deltaTimeMillis, bounds)
                            it.drawTrail(sandCanvas!!, trailPaint)
                        }
                    }
                    // Draw the sand bitmap (with trails) onto the main canvas
                    canvas.drawBitmap(sandBitmap!!, 0f, 0f, sandBitmapPaint)
                } else {
                    // Fallback if sandBitmap isn't ready, draw solid sand color
                    canvas.drawColor(MyWatchFace.SAND_COLOR)
                }
            }
            MyWatchFace.BACKGROUND_FLUID_MOTION_ID, MyWatchFace.BACKGROUND_FLUID_TOUCH_ID -> {
                // Fluid backgrounds are drawn on a black canvas.
                canvas.drawColor(Color.BLACK) // Explicitly set black for fluid
                if (!isAmbient) {
                    val currentTimeMillis = zonedDateTime.toInstant().toEpochMilli()
                    val deltaTimeMillis = if (lastFrameTimeMillis == 0L) 0L else currentTimeMillis - lastFrameTimeMillis
                    lastFrameTimeMillis = currentTimeMillis
                    particles.forEach { particle ->
                        particle.update(deltaTimeMillis, bounds)
                        particle.draw(canvas, particlePaint) // Draw particles directly on main canvas
                    }
                }
            }
            MyWatchFace.BACKGROUND_STATIC_ID -> {
                canvas.drawColor(Color.BLACK) // Static background is plain black
                // No animation for static background. lastFrameTimeMillis reset for other modes.
                lastFrameTimeMillis = 0L
            }
            else -> {
                canvas.drawColor(currentBackgroundColor) // Fallback to default black
            }
        }
        // If not Zen Garden, and not fluid, ensure lastFrameTimeMillis is reset if it was animating
        if (currentSelectedBackgroundStyleId != MyWatchFace.BACKGROUND_FLUID_MOTION_ID &&
            currentSelectedBackgroundStyleId != MyWatchFace.BACKGROUND_FLUID_TOUCH_ID &&
            currentSelectedBackgroundStyleId != MyWatchFace.BACKGROUND_ZEN_GARDEN_ID) {
            lastFrameTimeMillis = 0L
        }


        // --- Draw Time and Date ---
        // Ensure calendar is set to the current ZonedDateTime for formatting.
        calendar.timeInMillis = zonedDateTime.toInstant().toEpochMilli()
        val timeText = timeFormat.format(calendar.time)
        val dateText = dateFormat.format(calendar.time).uppercase() // Example: "THU MAY 24"

        val centerX = bounds.exactCenterX()
        // Position time in the vertical center, date above it.
        val timeTextBaseY = bounds.exactCenterY() + (timePaint.textSize / 3f) // Adjusted for better centering
        val dateTextY = timeTextBaseY - timePaint.textSize - (datePaint.textSize / 2f) + 10f // Position date above time

        canvas.drawText(dateText, centerX, dateTextY, datePaint)
        canvas.drawText(timeText, centerX, timeTextBaseY, timePaint)

        // --- Draw Ball for Zen Garden (on top of sand bitmap, time, date) ---
        if (currentSelectedBackgroundStyleId == MyWatchFace.BACKGROUND_ZEN_GARDEN_ID && !isAmbient) {
            ball?.draw(canvas, ballPaint, ballHighlightPaint)
        }


        // --- Draw Complications ---
        // Iterate through complications and render them if enabled for the current draw mode.
        for ((_, complication) in complicationSlotsManager.complicationSlots) {
            if (complication.isEnabled(renderParameters.drawMode)) {
                complication.render(canvas, zonedDateTime, renderParameters)
            }
        }
    }

    /**
     * Renders the highlight layer for complications.
     * This is used by the system to indicate when a complication is tapped or focused.
     * @param canvas The canvas to draw on.
     * @param bounds The current screen bounds.
     * @param zonedDateTime The current date and time.
     * @param sharedAssets The shared assets instance.
     */
    override fun renderHighlightLayer(
        canvas: Canvas,
        bounds: Rect,
        zonedDateTime: ZonedDateTime,
        renderParameters: androidx.wear.watchface.RenderParameters, // Make sure this is the correct RenderParameters
        sharedAssets: SharedAssets
    ) {
        // Iterate through complications and render their highlight layer if enabled.
        for ((_, complication) in complicationSlotsManager.complicationSlots) {
            if (complication.isEnabled(renderParameters.drawMode)) {
                complication.renderHighlightLayer(canvas, zonedDateTime, renderParameters)
            }
        }
    }


    /**
     * Called when the renderer is being destroyed.
     * Cancels the rendererScope to clean up coroutines.
     */
    override fun onDestroy() {
        super.onDestroy()
        rendererScope.cancel()
        // Explicitly clear resources managed by the renderer
        clearParticles()
        clearZenGardenResources()
        ball = null
    }
}

