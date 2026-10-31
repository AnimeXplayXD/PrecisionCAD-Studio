package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cad.model.CadUnit
import com.example.cad.model.CursorControllerConfig
import com.example.cad.model.Point2D
import com.example.cad.model.PrecisionFormatConfig
import com.example.cad.model.VirtualControllerMode
import com.example.ui.theme.CadAmberSnap
import com.example.ui.theme.CadBorderHairline
import com.example.ui.theme.CadCrimsonAlert
import com.example.ui.theme.CadCyanPrimary
import com.example.ui.theme.CadEmeraldCrossing
import com.example.ui.theme.CadPanelElevated
import com.example.ui.theme.CadPanelSurface
import com.example.ui.theme.CadTextMuted
import com.example.ui.theme.CadTextPrimary
import com.example.ui.theme.CadTextSecondary
import kotlin.math.hypot
import kotlin.math.pow

enum class ControllerDockState {
    COMPACT_DOCK,
    EXPANDED_CONTROLLER
}

/**
 * Dedicated Velocity-Sensitive Virtual Cursor Controller (Sections 4 & 14.5 of Specification).
 * Supports:
 * - Mode A: Relative Touchpad (finger displacement moves cursor without relocating on lift/re-touch)
 * - Mode B: Velocity-Sensitive Virtual Joystick (deflection magnitude controls speed via fine-control curve,
 *   releasing stops cursor movement immediately with zero drift)
 * - Discrete Step Increment Nudging (10, 5, 1, 0.5, 0.1, 0.01, Custom)
 * - Pick Point / Confirm / Cancel triggers without touching the drawing viewport.
 * - Collapsible Compact Dock (`COMPACT_DOCK`) showing only Touchpad, Joystick, X, Y, and Settings,
 *   with swipe-down to collapse and swipe-up to expand on the header/drag handle.
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
    onSetCollapsed: ((Boolean) -> Unit)? = null,
    onOpenCoordinateDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Normalized joystick deflection vector in [-1f, +1f]. Reset to Offset.Zero immediately on release.
    var joystickDeflection by remember { mutableStateOf(Offset.Zero) }
    var isJoystickHeld by remember { mutableStateOf(false) }
    var headerDragAccumulatorY by remember { mutableFloatStateOf(0f) }

    // 60fps velocity integration loop for Velocity-Sensitive Virtual Joystick
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
            // [1. Touchpad Mode] [2. Joystick Mode] [3. X Coordinate] [4. Y Coordinate] [5. Controller Settings + Chevron]
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
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Touchpad vs Joystick Mode Pill (Opaque surfaces)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(CadPanelElevated)
                        .border(1.dp, CadBorderHairline, RoundedCornerShape(8.dp))
                        .padding(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isTouchpad = config.mode == VirtualControllerMode.RELATIVE_TOUCHPAD
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isTouchpad) Color(0xFF0B3544) else CadPanelElevated)
                            .clickable {
                                onModeChange(VirtualControllerMode.RELATIVE_TOUCHPAD)
                            }
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                            .testTag("controller_mode_touchpad")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.TouchApp,
                                contentDescription = "Relative Touchpad Mode",
                                tint = if (isTouchpad) CadCyanPrimary else CadTextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "TOUCHPAD",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isTouchpad) CadCyanPrimary else CadTextSecondary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (!isTouchpad) Color(0xFF3A2B09) else CadPanelElevated)
                            .clickable {
                                onModeChange(VirtualControllerMode.VELOCITY_JOYSTICK)
                            }
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                            .testTag("controller_mode_joystick")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Gamepad,
                                contentDescription = "Velocity Joystick Mode",
                                tint = if (!isTouchpad) CadAmberSnap else CadTextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "JOYSTICK",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (!isTouchpad) CadAmberSnap else CadTextSecondary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Tappable Live Cursor X & Y Readout -> Opens Direct Coordinate Input
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(CadPanelElevated)
                        .border(1.dp, CadCyanPrimary, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("cursor_coordinate_hud_button"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "X: ${precision.formatCoord(cursorModelPos.x, storageUnit, displayUnit)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = CadCyanPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { onOpenCoordinateDialog() }
                            .testTag("cursor_coord_x_text")
                    )
                    Text(
                        text = "Y: ${precision.formatCoord(cursorModelPos.y, storageUnit, displayUnit)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = CadCyanPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { onOpenCoordinateDialog() }
                            .testTag("cursor_coord_y_text")
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
                    IconButton(
                        onClick = onToggleCollapse,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("controller_collapse_button")
                    ) {
                        Icon(
                            imageVector = if (config.isCollapsed) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (config.isCollapsed) "Expand Cursor Controller" else "Collapse Cursor Controller",
                            tint = CadTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (!config.isCollapsed) {
                Spacer(modifier = Modifier.height(6.dp))

                // Step Increment Selector Strip
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
                                .background(if (isSelected) CadCyanPrimary.copy(alpha = 0.2f) else CadPanelElevated)
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) CadCyanPrimary else CadBorderHairline,
                                    shape = RoundedCornerShape(5.dp)
                                )
                                .clickable { onIncrementSelect(inc) }
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                                .testTag("step_increment_$label")
                        ) {
                            Text(
                                text = "$label ${displayUnit.symbol}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) CadCyanPrimary else CadTextSecondary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Main Interactive Control Deck: [Left: Step Nudge D-Pad] | [Center: Relative Touchpad or Velocity Joystick] | [Right: Pick / Enter / Esc]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(116.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 1. Discrete Step Nudge Cross Pad
                    StepNudgePad(
                        stepIncrement = config.stepIncrement,
                        onStepNudge = onStepNudge,
                        modifier = Modifier.width(104.dp)
                    )

                    // 2. Central Relative Touchpad OR Velocity-Sensitive Virtual Joystick
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF0D131D))
                            .border(
                                width = 1.5.dp,
                                color = if (config.mode == VirtualControllerMode.RELATIVE_TOUCHPAD)
                                    CadCyanPrimary.copy(alpha = 0.55f)
                                else
                                    CadAmberSnap.copy(alpha = 0.65f),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .semantics {
                                contentDescription = if (config.mode == VirtualControllerMode.RELATIVE_TOUCHPAD) {
                                    "Relative Touchpad Surface"
                                } else {
                                    "Velocity-Sensitive Virtual Joystick"
                                }
                            }
                            .testTag("virtual_cursor_surface")
                            .pointerInput(config.mode, config.touchpadSensitivity, pixelsPerModelUnit) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val centerPx = Offset(size.width * 0.5f, size.height * 0.5f)
                                    val maxRadiusPx = (minOf(size.width, size.height) * 0.42f).coerceAtLeast(24f)

                                    if (config.mode == VirtualControllerMode.VELOCITY_JOYSTICK) {
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
                                    }

                                    var pointerId = down.id
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == pointerId }
                                            ?: event.changes.firstOrNull()
                                        if (change == null || !change.pressed) {
                                            // Immediate stop on release: zero post-release drift
                                            isJoystickHeld = false
                                            joystickDeflection = Offset.Zero
                                            break
                                        }
                                        pointerId = change.id
                                        if (config.mode == VirtualControllerMode.RELATIVE_TOUCHPAD) {
                                            val deltaPx = change.positionChange()
                                            if (deltaPx != Offset.Zero) {
                                                val safeScale = pixelsPerModelUnit.coerceAtLeast(0.1f)
                                                val dxModel = (deltaPx.x * config.touchpadSensitivity / safeScale).toDouble()
                                                val dyModel = (-deltaPx.y * config.touchpadSensitivity / safeScale).toDouble()
                                                onRelativeTouchpadDelta(dxModel, dyModel)
                                            }
                                        } else {
                                            val rawDelta = change.position - centerPx
                                            val dist = hypot(rawDelta.x, rawDelta.y)
                                            val clamped = if (dist > maxRadiusPx) {
                                                rawDelta * (maxRadiusPx / dist)
                                            } else rawDelta
                                            joystickDeflection = Offset(
                                                x = (clamped.x / maxRadiusPx).coerceIn(-1f, 1f),
                                                y = (clamped.y / maxRadiusPx).coerceIn(-1f, 1f)
                                            )
                                        }
                                        change.consume()
                                    }
                                }
                            }
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val cx = size.width * 0.5f
                            val cy = size.height * 0.5f
                            if (config.mode == VirtualControllerMode.RELATIVE_TOUCHPAD) {
                                // Subtle relative trackpad gridlines
                                val stepX = size.width / 6f
                                val stepY = size.height / 4f
                                for (i in 1..5) {
                                    drawLine(
                                        color = CadBorderHairline.copy(alpha = 0.45f),
                                        start = Offset(i * stepX, 0f),
                                        end = Offset(i * stepX, size.height),
                                        strokeWidth = 1f
                                    )
                                }
                                for (j in 1..3) {
                                    drawLine(
                                        color = CadBorderHairline.copy(alpha = 0.45f),
                                        start = Offset(0f, j * stepY),
                                        end = Offset(size.width, j * stepY),
                                        strokeWidth = 1f
                                    )
                                }
                                // Center reticle
                                drawCircle(
                                    color = CadCyanPrimary.copy(alpha = 0.35f),
                                    radius = 12f,
                                    center = Offset(cx, cy),
                                    style = Stroke(width = 1.5f)
                                )
                            } else {
                                // Velocity Joystick Concentric Fine/Fast Zones
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
                        }

                        Text(
                            text = if (config.mode == VirtualControllerMode.RELATIVE_TOUCHPAD)
                                "RELATIVE TOUCHPAD (SLIDE TO MOVE CURSOR)"
                            else
                                "VELOCITY JOYSTICK (FINE CENTER • STOP ON RELEASE)",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            color = CadTextMuted,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 4.dp)
                        )
                    }

                    // 3. Primary CAD Cursor Action Buttons (PICK POINT / ENTER / ESC)
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
                                contentColor = Color(0xFF002229)
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
                icon = Icons.Default.KeyboardArrowLeft,
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
                icon = Icons.Default.KeyboardArrowRight,
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
