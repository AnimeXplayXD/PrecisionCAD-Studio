package com.precisioncad.studio.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.precisioncad.studio.cad.model.CadUnit
import com.precisioncad.studio.cad.model.CursorControllerConfig
import com.precisioncad.studio.cad.model.Point2D
import com.precisioncad.studio.cad.model.PrecisionFormatConfig
import com.precisioncad.studio.cad.model.VirtualControllerMode
import com.precisioncad.studio.ui.theme.CadAmberSnap
import com.precisioncad.studio.ui.theme.CadBorderHairline
import com.precisioncad.studio.ui.theme.CadBorderStrong
import com.precisioncad.studio.ui.theme.CadCrimsonAlert
import com.precisioncad.studio.ui.theme.CadCyanPrimary
import com.precisioncad.studio.ui.theme.CadEmeraldCrossing
import com.precisioncad.studio.ui.theme.CadPanelElevated
import com.precisioncad.studio.ui.theme.CadPanelSurface
import com.precisioncad.studio.ui.theme.CadSurfaceCard
import com.precisioncad.studio.ui.theme.CadTextMuted
import com.precisioncad.studio.ui.theme.CadTextPrimary
import com.precisioncad.studio.ui.theme.CadTextSecondary
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt

enum class ControllerDockState {
    COMPACT_DOCK,
    EXPANDED_CONTROLLER
}

/**
 * Section 39.1 & 42.2 — Direct Relative Touchpad Gesture State.
 * - `IDLE`: No active pointer on the touchpad.
 * - `DRAG_ACTIVE`: Single pointer down/moving; stationary contact emits `Offset.Zero`, movement emits exact 1:1 relative delta.
 * - `MULTI_POINTER_CANCELLED`: A second finger touched the touchpad; cursor freezes until all pointers lift.
 * - `CANCELLED`: Gesture was cancelled; emits `Offset.Zero`.
 */
enum class TouchpadGestureState {
    IDLE,
    PRESSED_UNDECIDED,
    DRAG_ACTIVE,
    MULTI_POINTER_CANCELLED,
    CANCELLED
}

/**
 * Direct relative delta evaluator for Relative Touchpad mode (Section 39.1 & 42.2).
 * Restores predictable 1:1 relative pointer displacement without touch-slop lag, discarded first movement,
 * or velocity-acceleration multipliers, while keeping stationary taps at exact 0.0 displacement and
 * freezing on multi-pointer contact.
 */
class TouchpadMotionEngine(
    val touchSlopPx: Float = 0f
) {
    companion object {
        fun computeVelocityGain(speedPxPerSec: Float): Float = 1.0f
    }

    var state: TouchpadGestureState = TouchpadGestureState.IDLE
        private set

    var downPosition: Offset = Offset.Zero
        private set

    var lastTrackedPosition: Offset = Offset.Zero
        private set

    fun onDown(position: Offset) {
        state = TouchpadGestureState.DRAG_ACTIVE
        downPosition = position
        lastTrackedPosition = position
    }

    /**
     * Processes a move/pointer sample and returns the direct relative screen-space delta (in px).
     * Stationary taps (`position == lastTrackedPosition`) and multi-pointer/cancelled events return `Offset.Zero`.
     */
    fun onMoveSample(
        position: Offset,
        activePointerCount: Int,
        isCancelled: Boolean = false,
        velocityPxPerSec: Float = 0f
    ): Offset {
        if (isCancelled) {
            state = TouchpadGestureState.CANCELLED
            return Offset.Zero
        }
        if (activePointerCount > 1) {
            state = TouchpadGestureState.MULTI_POINTER_CANCELLED
            return Offset.Zero
        }
        return when (state) {
            TouchpadGestureState.IDLE,
            TouchpadGestureState.MULTI_POINTER_CANCELLED,
            TouchpadGestureState.CANCELLED -> Offset.Zero

            TouchpadGestureState.PRESSED_UNDECIDED,
            TouchpadGestureState.DRAG_ACTIVE -> {
                state = TouchpadGestureState.DRAG_ACTIVE
                val rawDelta = position - lastTrackedPosition
                lastTrackedPosition = position
                if (rawDelta == Offset.Zero || !rawDelta.x.isFinite() || !rawDelta.y.isFinite()) {
                    Offset.Zero
                } else {
                    rawDelta
                }
            }
        }
    }

    fun onAllPointersUp() {
        state = TouchpadGestureState.IDLE
    }
}

/**
 * Dedicated Virtual Cursor Controller (Sections 4, 14.4, 39.1, 42.2, v17 Sections 7.4 & 8).
 * Supports:
 * - Mode A (`RELATIVE_TOUCHPAD`): Direct relative touchpad movement using `touchpadSensitivity` and `pixelsPerModelUnit`.
 * - Mode B (`VELOCITY_JOYSTICK`): Preserves full layout (`StepNudgePad`, step-increment strip, joystick,
 *   `PICK PT`, `Enter`, `Escape`) and 60fps frame-integrated velocity curve unchanged.
 * - Vertical-Peek Two-Card Mode Selector (`controller_mode_card_selector`): Active mode in foreground,
 *   alternate mode peeking from above (`offsetY < 0`), Touchpad in restrained blue, Joystick in restrained gold,
 *   tap-only with animation-in-flight lock.
 * - Coordinate HUD (`cursor_coordinate_hud_button`): Displays live `X`, `Y`, and current bearing `θ (°)`.
 */
@Composable
fun VirtualCursorControllerDeck(
    config: CursorControllerConfig,
    cursorModelPos: Point2D,
    storageUnit: CadUnit,
    displayUnit: CadUnit,
    precision: PrecisionFormatConfig,
    pixelsPerModelUnit: Float,
    onRelativeTouchpadDelta: (dxModel: Double, dyModel: Double) -> Unit,
    onJoystickVelocityStep: (dxModel: Double, dyModel: Double) -> Unit,
    onStepNudge: (dxModel: Double, dyModel: Double) -> Unit,
    onPickAtCursor: () -> Unit,
    onFinishCommand: () -> Unit,
    onCancelCommand: () -> Unit,
    onModeChange: (VirtualControllerMode) -> Unit,
    onIncrementSelect: (Double) -> Unit,
    onOpenControllerSettings: () -> Unit,
    onToggleCollapse: () -> Unit,
    onOpenCoordinateDialog: () -> Unit,
    modifier: Modifier = Modifier,
    onSetCollapsed: ((Boolean) -> Unit)? = null,
    referencePoint: Point2D? = null,
    onTouchpadGestureStart: () -> Unit = {},
    onTouchpadGestureEnd: (cancelled: Boolean) -> Unit = {}
) {
    // Normalized joystick deflection vector in [-1f, +1f]. Reset to Offset.Zero immediately on release.
    var joystickDeflection by remember { mutableStateOf(Offset.Zero) }
    var isJoystickHeld by remember { mutableStateOf(false) }
    var headerDragAccumulatorY by remember { mutableFloatStateOf(0f) }

    // 60fps velocity integration loop for Velocity-Sensitive Virtual Joystick (Unchanged per Section 24.3 & 39.1)
    LaunchedEffect(isJoystickHeld, config.mode) {
        if (!isJoystickHeld || config.mode != VirtualControllerMode.VELOCITY_JOYSTICK) {
            joystickDeflection = Offset.Zero
            return@LaunchedEffect
        }
        var lastFrameNanos = withFrameNanos { it }
        while (isJoystickHeld && config.mode == VirtualControllerMode.VELOCITY_JOYSTICK) {
            val nowNanos = withFrameNanos { it }
            val dtSec = ((nowNanos - lastFrameNanos) / 1_000_000_000.0).coerceIn(0.0, 0.05)
            lastFrameNanos = nowNanos

            val def = joystickDeflection
            val mag = hypot(def.x.toDouble(), def.y.toDouble()).coerceIn(0.0, 1.0)
            // Fine-control deadzone < 0.04; non-linear power curve mag^exponent for precise slow speeds near center
            if (mag > 0.04) {
                val curvedMag = mag.pow(config.joystickVelocityExponent)
                // Scale speed inversely with zoom so visual cursor speed remains predictable at any zoom level
                val zoomNormalization = (3.2 / pixelsPerModelUnit.coerceAtLeast(0.2f)).coerceIn(0.15, 8.0)
                val speedUnitsPerSec = config.joystickMaxSpeedUnitsPerSec * curvedMag * zoomNormalization
                val dirX = def.x / mag
                val dirY = -def.y / mag // Screen Y down -> Model Y up
                val dx = dirX * speedUnitsPerSec * dtSec
                val dy = dirY * speedUnitsPerSec * dtSec
                onJoystickVelocityStep(dx, dy)
            }
        }
    }

    val refPt = referencePoint ?: Point2D(0.0, 0.0)
    val cursorBearingDeg = if (refPt.distanceTo(cursorModelPos) > 1e-6) {
        ((refPt.angleToDeg(cursorModelPos) % 360.0) + 360.0) % 360.0
    } else {
        0.0
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            .border(1.dp, CadBorderHairline, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            .testTag("virtual_cursor_controller_deck"),
        color = CadPanelSurface,
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)) {
            // Drag Handle Pill for Swipe-Down to Collapse / Swipe-Up to Expand (isolated from touchpad/joystick)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(config.isCollapsed) {
                        detectVerticalDragGestures(
                            onDragStart = { headerDragAccumulatorY = 0f },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                headerDragAccumulatorY += dragAmount
                                if (!config.isCollapsed && headerDragAccumulatorY > 24f) {
                                    headerDragAccumulatorY = 0f
                                    if (onSetCollapsed != null) onSetCollapsed(true) else onToggleCollapse()
                                } else if (config.isCollapsed && headerDragAccumulatorY < -24f) {
                                    headerDragAccumulatorY = 0f
                                    if (onSetCollapsed != null) onSetCollapsed(false) else onToggleCollapse()
                                }
                            }
                        )
                    }
                    .testTag("controller_drag_handle"),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .padding(vertical = 2.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(CadBorderHairline)
                )
            }

            // Top Header / Compact Dock Row:
            // [1. Vertical-Peek Two-Card Mode Selector] [2. X, Y & θ Coordinate HUD] [3. Controller Settings + Chevron]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(config.isCollapsed) {
                        detectVerticalDragGestures(
                            onDragStart = { headerDragAccumulatorY = 0f },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                headerDragAccumulatorY += dragAmount
                                if (!config.isCollapsed && headerDragAccumulatorY > 24f) {
                                    headerDragAccumulatorY = 0f
                                    if (onSetCollapsed != null) onSetCollapsed(true) else onToggleCollapse()
                                } else if (config.isCollapsed && headerDragAccumulatorY < -24f) {
                                    headerDragAccumulatorY = 0f
                                    if (onSetCollapsed != null) onSetCollapsed(false) else onToggleCollapse()
                                }
                            }
                        )
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Section 8 — Vertical-Peek Two-Card Controller Mode Selector (Tap-only, animation lock)
                ControllerModeVerticalPeekCardSelector(
                    activeMode = config.mode,
                    onModeChange = onModeChange
                )

                // Section 7.4 — Tappable Live Cursor X, Y & Bearing θ (°) Readout -> Opens Direct Coordinate Input
                // Settings button remains a separate control outside this coordinate tap target.
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(CadPanelElevated)
                        .border(1.dp, CadBorderStrong, RoundedCornerShape(6.dp))
                        .clickable { onOpenCoordinateDialog() }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                        .testTag("cursor_coordinate_hud_button"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "X: ${precision.formatCoord(cursorModelPos.x, storageUnit, displayUnit)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = CadTextPrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clickable { onOpenCoordinateDialog() }
                            .testTag("cursor_coord_x_text")
                    )
                    Text(
                        text = "Y: ${precision.formatCoord(cursorModelPos.y, storageUnit, displayUnit)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = CadTextPrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clickable { onOpenCoordinateDialog() }
                            .testTag("cursor_coord_y_text")
                    )
                    Text(
                        text = "θ: ${precision.formatAngle(cursorBearingDeg)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = CadAmberSnap,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clickable { onOpenCoordinateDialog() }
                            .testTag("cursor_coord_theta_text")
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onOpenControllerSettings,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("controller_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Controller Sensitivity & Step Settings",
                            tint = CadTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    val collapseChevronRotation by animateFloatAsState(
                        targetValue = if (config.isCollapsed) 0f else 180f,
                        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                        label = "CollapseChevronRotation"
                    )
                    IconButton(
                        onClick = onToggleCollapse,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("controller_collapse_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ExpandLess,
                            contentDescription = if (config.isCollapsed) "Expand Cursor Controller" else "Collapse Cursor Controller",
                            tint = CadTextSecondary,
                            modifier = Modifier
                                .size(18.dp)
                                .graphicsLayer { rotationZ = collapseChevronRotation }
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = !config.isCollapsed,
                enter = expandVertically(
                    expandFrom = Alignment.Bottom,
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(durationMillis = 180)),
                exit = shrinkVertically(
                    shrinkTowards = Alignment.Bottom,
                    animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(durationMillis = 150))
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.height(6.dp))

                    // V19 §11.1 — Numeric-Only Step Increment Selector Strip (`10`, `5`, `1`, `0.5`, `0.1`, `0.01`)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "STEP:",
                            style = MaterialTheme.typography.labelSmall,
                            color = CadTextMuted
                        )
                        config.availableIncrements.forEach { inc ->
                            val isSelected = kotlin.math.abs(config.stepIncrement - inc) < 1e-6
                            val label = if (inc >= 1.0 && inc % 1.0 == 0.0) inc.toInt().toString() else inc.toString()
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(if (isSelected) CadSurfaceCard else CadPanelElevated)
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) CadCyanPrimary else CadBorderHairline,
                                        shape = RoundedCornerShape(5.dp)
                                    )
                                    .semantics {
                                        contentDescription = "Cursor step $label ${displayUnit.label}"
                                    }
                                    .clickable { onIncrementSelect(inc) }
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                                    .testTag("step_increment_$label")
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) CadTextPrimary else CadTextSecondary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(116.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (config.mode == VirtualControllerMode.RELATIVE_TOUCHPAD) {
                            // V19 §11.2, §11.3 & §11.6 — Relative Touchpad Mode:
                            // Raw continuous motion during drag; single release alignment on pointer up;
                            // multi-pointer / cancelled gesture guard.
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(CadPanelElevated)
                                    .border(
                                        width = 1.dp,
                                        color = CadBorderStrong,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .semantics {
                                        contentDescription = "Relative Touchpad Surface"
                                    }
                                    .testTag("controller_touchpad_surface")
                                    .pointerInput(config.touchpadSensitivity, pixelsPerModelUnit) {
                                        val motionEngine = TouchpadMotionEngine()

                                        awaitEachGesture {
                                            val down = awaitFirstDown(requireUnconsumed = false)
                                            down.consume()
                                            motionEngine.onDown(down.position)
                                            onTouchpadGestureStart()

                                            val primaryPointerId = down.id
                                            var gestureCancelled = false
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                val pressedChanges = event.changes.filter { it.pressed }
                                                val isEventCancelled = event.changes.any { it.isConsumed }

                                                // Consume all touchpad events immediately so no gesture ever leaks to the sheet
                                                event.changes.forEach { it.consume() }

                                                if (pressedChanges.isEmpty()) {
                                                    motionEngine.onAllPointersUp()
                                                    onTouchpadGestureEnd(gestureCancelled)
                                                    break
                                                }

                                                if (pressedChanges.size > 1 || isEventCancelled) {
                                                    gestureCancelled = true
                                                    motionEngine.onMoveSample(
                                                        position = pressedChanges.first().position,
                                                        activePointerCount = pressedChanges.size.coerceAtLeast(2),
                                                        isCancelled = true
                                                    )
                                                    continue
                                                }

                                                val activeChange = pressedChanges.firstOrNull { it.id == primaryPointerId }
                                                if (activeChange == null || gestureCancelled) {
                                                    gestureCancelled = true
                                                    continue
                                                }

                                                val effectiveDeltaPx = motionEngine.onMoveSample(
                                                    position = activeChange.position,
                                                    activePointerCount = 1,
                                                    isCancelled = false
                                                )

                                                if (effectiveDeltaPx != Offset.Zero) {
                                                    val safeScale = pixelsPerModelUnit.coerceAtLeast(0.1f)
                                                    val dxModel = (effectiveDeltaPx.x * config.touchpadSensitivity / safeScale).toDouble()
                                                    val dyModel = (-effectiveDeltaPx.y * config.touchpadSensitivity / safeScale).toDouble()
                                                    onRelativeTouchpadDelta(dxModel, dyModel)
                                                }
                                            }
                                        }
                                    }
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val cx = size.width * 0.5f
                                val cy = size.height * 0.5f
                                val stepX = size.width / 6f
                                val stepY = size.height / 4f
                                for (i in 1..5) {
                                    drawLine(
                                        color = CadBorderHairline.copy(alpha = 0.5f),
                                        start = Offset(i * stepX, 0f),
                                        end = Offset(i * stepX, size.height),
                                        strokeWidth = 1f
                                    )
                                }
                                for (j in 1..3) {
                                    drawLine(
                                        color = CadBorderHairline.copy(alpha = 0.5f),
                                        start = Offset(0f, j * stepY),
                                        end = Offset(size.width, j * stepY),
                                        strokeWidth = 1f
                                    )
                                }
                                drawCircle(
                                    color = CadCyanPrimary.copy(alpha = 0.35f),
                                    radius = 12f,
                                    center = Offset(cx, cy),
                                    style = Stroke(width = 1.5f)
                                )
                            }

                            Text(
                                text = "RELATIVE TOUCHPAD (DIRECT 1:1 DISPLACEMENT)",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                color = CadTextMuted,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 4.dp)
                            )
                        }
                    } else {
                        // Section 24.3 & 39.1 — Velocity Joystick Mode Layout (Preserved Unchanged):
                        // 1. Discrete Step Nudge Cross Pad
                        StepNudgePad(
                            stepIncrement = config.stepIncrement,
                            onStepNudge = onStepNudge,
                            modifier = Modifier.width(104.dp)
                        )

                        // 2. Central Velocity-Sensitive Virtual Joystick
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .clip(RoundedCornerShape(10.dp))
                                .background(CadPanelElevated)
                                .border(
                                    width = 1.dp,
                                    color = CadAmberSnap.copy(alpha = 0.65f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .semantics {
                                    contentDescription = "Velocity-Sensitive Virtual Joystick"
                                }
                                .testTag("controller_joystick_surface")
                                .pointerInput(config.mode, pixelsPerModelUnit) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        down.consume()
                                        val centerPx = Offset(size.width * 0.5f, size.height * 0.5f)
                                        val maxRadiusPx = (minOf(size.width, size.height) * 0.42f).coerceAtLeast(24f)

                                        isJoystickHeld = true
                                        val initDelta = down.position - centerPx
                                        val dist = hypot(initDelta.x, initDelta.y)
                                        val clamped = if (dist > maxRadiusPx) {
                                            initDelta * (maxRadiusPx / dist)
                                        } else initDelta
                                        joystickDeflection = Offset(
                                            x = (clamped.x / maxRadiusPx).coerceIn(-1f, 1f),
                                            y = (clamped.y / maxRadiusPx).coerceIn(-1f, 1f)
                                        )

                                        var pointerId = down.id
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull { it.id == pointerId }
                                                ?: event.changes.firstOrNull()
                                            if (change == null || !change.pressed) {
                                                // Immediate stop on release: zero post-release drift
                                                isJoystickHeld = false
                                                joystickDeflection = Offset.Zero
                                                event.changes.forEach { it.consume() }
                                                break
                                            }
                                            pointerId = change.id
                                            val rawDelta = change.position - centerPx
                                            val d = hypot(rawDelta.x, rawDelta.y)
                                            val c = if (d > maxRadiusPx) {
                                                rawDelta * (maxRadiusPx / d)
                                            } else rawDelta
                                            joystickDeflection = Offset(
                                                x = (c.x / maxRadiusPx).coerceIn(-1f, 1f),
                                                y = (c.y / maxRadiusPx).coerceIn(-1f, 1f)
                                            )
                                            change.consume()
                                        }
                                    }
                                }
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val cx = size.width * 0.5f
                                val cy = size.height * 0.5f
                                val maxR = minOf(size.width, size.height) * 0.42f
                                val fineR = maxR * 0.45f
                                drawCircle(
                                    color = CadBorderHairline,
                                    radius = maxR,
                                    center = Offset(cx, cy),
                                    style = Stroke(width = 1.5f)
                                )
                                drawCircle(
                                    color = CadAmberSnap.copy(alpha = 0.25f),
                                    radius = fineR,
                                    center = Offset(cx, cy),
                                    style = Stroke(width = 1f)
                                )
                                drawLine(
                                    color = CadBorderHairline,
                                    start = Offset(cx - maxR, cy),
                                    end = Offset(cx + maxR, cy),
                                    strokeWidth = 1f
                                )
                                drawLine(
                                    color = CadBorderHairline,
                                    start = Offset(cx, cy - maxR),
                                    end = Offset(cx, cy + maxR),
                                    strokeWidth = 1f
                                )

                                val knobPos = Offset(
                                    x = cx + joystickDeflection.x * maxR,
                                    y = cy + joystickDeflection.y * maxR
                                )
                                drawLine(
                                    color = CadAmberSnap,
                                    start = Offset(cx, cy),
                                    end = knobPos,
                                    strokeWidth = 3f
                                )
                                drawCircle(
                                    color = CadAmberSnap.copy(alpha = 0.3f),
                                    radius = 20f,
                                    center = knobPos
                                )
                                drawCircle(
                                    color = CadAmberSnap,
                                    radius = 11f,
                                    center = knobPos
                                )
                            }

                            Text(
                                text = "VELOCITY JOYSTICK (FINE CENTER • STOP ON RELEASE)",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                color = CadTextMuted,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 4.dp)
                            )
                        }
                    }

                    // Primary CAD Cursor Action Buttons (PICK POINT / ENTER / ESC — Present in both modes per Section 39.1)
                    Column(
                        modifier = Modifier
                            .width(96.dp)
                            .fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Button(
                            onClick = onPickAtCursor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1.25f)
                                .testTag("cursor_pick_point_button"),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CadCyanPrimary,
                                contentColor = Color(0xFF050608)
                            ),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "PICK PT",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            OutlinedButton(
                                onClick = onFinishCommand,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .testTag("cursor_enter_button"),
                                shape = RoundedCornerShape(7.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Finish / Enter Command",
                                    tint = CadEmeraldCrossing,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            OutlinedButton(
                                onClick = onCancelCommand,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .testTag("cursor_esc_button"),
                                shape = RoundedCornerShape(7.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancel / Escape Command",
                                    tint = CadCrimsonAlert,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun StepNudgePad(
    stepIncrement: Double,
    onStepNudge: (dxModel: Double, dyModel: Double) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // UP (+Y)
        NudgeButton(
            icon = Icons.Default.KeyboardArrowUp,
            contentDescription = "Step Cursor +Y",
            tag = "nudge_up_button",
            onClick = { onStepNudge(0.0, stepIncrement) }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // LEFT (-X)
            NudgeButton(
                icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Step Cursor -X",
                tag = "nudge_left_button",
                onClick = { onStepNudge(-stepIncrement, 0.0) }
            )
            Text(
                text = "NUDGE",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 8.sp,
                color = CadTextMuted
            )
            // RIGHT (+X)
            NudgeButton(
                icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Step Cursor +X",
                tag = "nudge_right_button",
                onClick = { onStepNudge(stepIncrement, 0.0) }
            )
        }
        // DOWN (-Y)
        NudgeButton(
            icon = Icons.Default.KeyboardArrowDown,
            contentDescription = "Step Cursor -Y",
            tag = "nudge_down_button",
            onClick = { onStepNudge(0.0, -stepIncrement) }
        )
    }
}

@Composable
private fun NudgeButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tag: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(CadPanelElevated)
            .border(1.dp, CadBorderHairline, CircleShape)
            .clickable(onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = CadTextPrimary,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Section 8 & v18 Phase 9 — Touchpad / Joystick Vertical-Peek Two-Card Selector.
 * - Uses isolated section-local `CadDeckIdleTimerState` (`deckId = "controller_mode_deck"`).
 * - At rest: only the front card is visible; the rear card is completely concealed (`offsetY = 0.dp`, `scale = 0.92f`, `alpha = 0f`).
 * - Post-exchange: rear card peeks from above (`offsetY = (-4).dp`, `scale = 0.94f`, `alpha = 0.88f`) for `2,400ms`, then hides.
 * - Idle reminder: after `75s` of deck-local inactivity, rear card peeks from above for `1,000ms`, then hides.
 * - Tap-only interaction with animation-in-flight lock (`transition.currentState != transition.targetState`); no swipe gesture.
 */
@Composable
fun ControllerModeVerticalPeekCardSelector(
    activeMode: VirtualControllerMode,
    onModeChange: (VirtualControllerMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val isTouchpad = activeMode == VirtualControllerMode.RELATIVE_TOUCHPAD
    val transition = updateTransition(targetState = isTouchpad, label = "ControllerModePeekTransition")
    val isAnimating = transition.currentState != transition.targetState
    val interactionSource = remember { MutableInteractionSource() }
    val deckTimerState = rememberCadDeckIdleTimerState(
        deckId = "controller_mode_deck",
        isVisibleAndInteractive = true,
        peekDirection = CardPeekDirection.ABOVE
    )
    val isRearRevealed = deckTimerState.isRearCardRevealed

    val touchpadTargetOffsetY = when {
        isTouchpad -> if (isRearRevealed) 2.dp else 0.dp
        isRearRevealed -> (-4).dp
        else -> 0.dp
    }
    val touchpadOffsetY by animateDpAsState(
        targetValue = touchpadTargetOffsetY,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "TouchpadOffsetY"
    )
    val touchpadScale by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 200, easing = FastOutSlowInEasing) },
        label = "TouchpadScale"
    ) { tp -> if (tp) 1.0f else if (isRearRevealed) 0.94f else 0.92f }
    val touchpadAlpha by animateFloatAsState(
        targetValue = if (isTouchpad) 1f else if (isRearRevealed) 0.88f else 0f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "TouchpadAlpha"
    )

    val joystickTargetOffsetY = when {
        !isTouchpad -> if (isRearRevealed) 2.dp else 0.dp
        isRearRevealed -> (-4).dp
        else -> 0.dp
    }
    val joystickOffsetY by animateDpAsState(
        targetValue = joystickTargetOffsetY,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "JoystickOffsetY"
    )
    val joystickScale by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 200, easing = FastOutSlowInEasing) },
        label = "JoystickScale"
    ) { tp -> if (!tp) 1.0f else if (isRearRevealed) 0.94f else 0.92f }
    val joystickAlpha by animateFloatAsState(
        targetValue = if (!isTouchpad) 1f else if (isRearRevealed) 0.88f else 0f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "JoystickAlpha"
    )

    val toggleMode: () -> Unit = {
        if (!isAnimating) {
            deckTimerState.onCardExchange(isCommandPromptOrDeck = false)
            val next = if (isTouchpad) {
                VirtualControllerMode.VELOCITY_JOYSTICK
            } else {
                VirtualControllerMode.RELATIVE_TOUCHPAD
            }
            onModeChange(next)
        }
    }

    Box(
        modifier = modifier
            .width(98.dp)
            .height(34.dp)
            .semantics {
                contentDescription = if (isTouchpad) {
                    "Controller Mode: Touchpad active"
                } else {
                    "Controller Mode: Joystick active"
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = !isAnimating,
                onClick = toggleMode
            )
            .testTag("controller_mode_card_selector"),
        contentAlignment = Alignment.Center
    ) {
        // Touchpad Card (Restrained Blue)
        Box(
            modifier = Modifier
                .zIndex(if (isTouchpad) 1f else 0f)
                .offset { IntOffset(x = 0, y = touchpadOffsetY.roundToPx()) }
                .graphicsLayer {
                    scaleX = touchpadScale
                    scaleY = touchpadScale
                    alpha = touchpadAlpha
                }
                .fillMaxWidth()
                .height(27.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (isTouchpad) Color(0xFF0C2330) else Color(0xFF091822))
                .border(
                    width = 1.dp,
                    color = if (isTouchpad) CadCyanPrimary else CadCyanPrimary.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(6.dp)
                )
                .then(
                    if (isTouchpad) {
                        Modifier.clickable(
                            enabled = !isAnimating,
                            onClick = toggleMode
                        )
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = 7.dp)
                .testTag("controller_mode_touchpad"),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.TouchApp,
                    contentDescription = "Relative Touchpad Mode",
                    tint = CadCyanPrimary,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "TOUCHPAD",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = if (isTouchpad) CadTextPrimary else CadTextSecondary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }

        // Joystick Card (Restrained Gold)
        Box(
            modifier = Modifier
                .zIndex(if (!isTouchpad) 1f else 0f)
                .offset { IntOffset(x = 0, y = joystickOffsetY.roundToPx()) }
                .graphicsLayer {
                    scaleX = joystickScale
                    scaleY = joystickScale
                    alpha = joystickAlpha
                }
                .fillMaxWidth()
                .height(27.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (!isTouchpad) Color(0xFF282010) else Color(0xFF1E180C))
                .border(
                    width = 1.dp,
                    color = if (!isTouchpad) CadAmberSnap else CadAmberSnap.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(6.dp)
                )
                .then(
                    if (!isTouchpad) {
                        Modifier.clickable(
                            enabled = !isAnimating,
                            onClick = toggleMode
                        )
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = 7.dp)
                .testTag("controller_mode_joystick"),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Gamepad,
                    contentDescription = "Velocity Joystick Mode",
                    tint = CadAmberSnap,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "JOYSTICK",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = if (!isTouchpad) CadTextPrimary else CadTextSecondary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
    }
}


