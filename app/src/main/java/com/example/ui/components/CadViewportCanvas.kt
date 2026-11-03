package com.example.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.graphics.withClip
import androidx.core.graphics.withSave
import com.example.cad.dimension.AdaptiveAnnotationRenderer
import com.example.cad.dimension.DimensionStyleConfig
import com.example.cad.kernel.CadGeometryKernel
import com.example.cad.kernel.PolarTrackingPreviewState
import com.example.cad.kernel.SnapCandidate
import com.example.cad.kernel.ViewportTransform
import com.example.cad.model.BlockDefinition
import com.example.cad.model.BoundingBox2D
import com.example.cad.model.CadEntity
import com.example.cad.model.CadLayer
import com.example.cad.model.CadLineType
import com.example.cad.model.CadUnit
import com.example.cad.model.GridAndSnapConfig
import com.example.cad.model.HatchPattern
import com.example.cad.model.OsnapMode
import com.example.cad.model.Point2D
import com.example.cad.model.PrecisionFormatConfig
import com.example.cad.model.SheetSetupConfig
import com.example.cad.model.WorkspaceSpace
import com.example.ui.theme.CadAmberSnap
import com.example.ui.theme.CadAxisX
import com.example.ui.theme.CadAxisY
import com.example.ui.theme.CadBlueWindow
import com.example.ui.theme.CadCyanPrimary
import com.example.ui.theme.CadDimensionBlue
import com.example.ui.theme.CadEmeraldCrossing
import com.example.ui.theme.CadGridMajor
import com.example.ui.theme.CadGridMinor
import com.example.ui.theme.CadModelSpaceBg
import com.example.ui.theme.CadPaperSpaceBg
import com.example.ui.theme.CadSheetPaperFill
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Sections 14.1, 18.4, 22, 39.4 & 42.3 — Authoritative Viewport Gesture State Machine.
 * The CAD sheet is strictly navigation-only:
 * - `IDLE`: No active pointers.
 * - `ONE_POINTER_PAN`: Single-finger drag outside the left-edge drawer zone pans the viewport after exceeding touch slop.
 * - `TWO_POINTER_PINCH_ZOOM`: Simultaneous two-finger centroid-translation pan AND inter-pointer separation pinch zoom.
 * - `POST_PINCH_POINTER_SUPPRESSED`: When a 2-pointer gesture drops to 1 pointer, remaining single-finger movement is suppressed until all pointers lift.
 * - `LEFT_EDGE_DRAWER_REVEAL`: Single-finger inward swipe starting in the left-edge strip opens the tool drawer without panning the sheet.
 * - `CANCELLED`: Cancelled or 3+ pointer gesture.
 */
enum class ViewportGestureState {
    IDLE,
    ONE_POINTER_PAN,
    TWO_POINTER_PINCH_ZOOM,
    POST_PINCH_POINTER_SUPPRESSED,
    LEFT_EDGE_DRAWER_REVEAL,
    CANCELLED
}

object ViewportGesturePolicy {
    const val DEFAULT_LEFT_EDGE_STRIP_PX = 36f

    fun resolveState(
        pressedPointerCount: Int,
        isCancelled: Boolean = false,
        startedInLeftEdgeZone: Boolean = false,
        previousState: ViewportGestureState = ViewportGestureState.IDLE
    ): ViewportGestureState = when {
        pressedPointerCount <= 0 -> ViewportGestureState.IDLE
        isCancelled -> ViewportGestureState.CANCELLED
        previousState == ViewportGestureState.CANCELLED -> ViewportGestureState.CANCELLED
        previousState == ViewportGestureState.LEFT_EDGE_DRAWER_REVEAL && pressedPointerCount == 1 ->
            ViewportGestureState.LEFT_EDGE_DRAWER_REVEAL
        pressedPointerCount == 2 -> ViewportGestureState.TWO_POINTER_PINCH_ZOOM
        pressedPointerCount == 1 && (
            previousState == ViewportGestureState.TWO_POINTER_PINCH_ZOOM ||
                previousState == ViewportGestureState.POST_PINCH_POINTER_SUPPRESSED
            ) -> ViewportGestureState.POST_PINCH_POINTER_SUPPRESSED
        pressedPointerCount == 1 && startedInLeftEdgeZone -> ViewportGestureState.LEFT_EDGE_DRAWER_REVEAL
        pressedPointerCount == 1 -> ViewportGestureState.ONE_POINTER_PAN
        else -> ViewportGestureState.CANCELLED
    }

    fun transitionState(
        currentState: ViewportGestureState,
        activePointerCount: Int,
        startedInLeftEdgeZone: Boolean = false
    ): ViewportGestureState = resolveState(
        pressedPointerCount = activePointerCount,
        isCancelled = false,
        startedInLeftEdgeZone = startedInLeftEdgeZone,
        previousState = currentState
    )

    /**
     * Section 22.1 & 42.3 — Single-finger drag pans the viewport by exact screen delta.
     */
    fun applyOneFingerPan(
        viewport: ViewportTransform,
        panDeltaScreen: Offset
    ): ViewportTransform {
        if (panDeltaScreen == Offset.Zero || !panDeltaScreen.x.isFinite() || !panDeltaScreen.y.isFinite()) {
            return viewport
        }
        return viewport.panByScreenDelta(panDeltaScreen.x, panDeltaScreen.y)
    }

    /**
     * Zoom-only helper around a focal anchor when pan is zero.
     */
    fun applyTwoFingerPinchZoomOnly(
        viewport: ViewportTransform,
        focalAnchorScreen: Offset,
        zoomFactor: Float
    ): ViewportTransform {
        if (focalAnchorScreen == Offset.Unspecified || !zoomFactor.isFinite() || abs(zoomFactor - 1f) <= 0.001f) {
            return viewport
        }
        return viewport.zoomAroundScreenPoint(focalAnchorScreen, zoomFactor)
    }

    /**
     * Section 0.0 Rule 17, 39.4 & 42.3 — Simultaneous two-finger pan + pinch zoom.
     * Applies centroid-translation pan AND inter-pointer separation pinch zoom around the two-finger centroid
     * in one coherent ViewportTransform update.
     */
    fun applyTwoFingerTransform(
        viewport: ViewportTransform,
        centroidScreen: Offset,
        panDeltaScreen: Offset,
        zoomFactor: Float
    ): ViewportTransform {
        var next = viewport
        if (panDeltaScreen != Offset.Zero && panDeltaScreen.x.isFinite() && panDeltaScreen.y.isFinite()) {
            next = next.panByScreenDelta(panDeltaScreen.x, panDeltaScreen.y)
        }
        if (centroidScreen != Offset.Unspecified &&
            centroidScreen.x.isFinite() &&
            centroidScreen.y.isFinite() &&
            zoomFactor.isFinite() &&
            abs(zoomFactor - 1f) > 0.001f
        ) {
            next = next.zoomAroundScreenPoint(centroidScreen, zoomFactor)
        }
        return next
    }
}

data class SelectionBoxDragState(
    val startModel: Point2D,
    val currentModel: Point2D
) {
    val isCrossingMode: Boolean
        get() = currentModel.x < startModel.x

    val boundingBox: BoundingBox2D
        get() = BoundingBox2D(
            minX = min(startModel.x, currentModel.x),
            minY = min(startModel.y, currentModel.y),
            maxX = max(startModel.x, currentModel.x),
            maxY = max(startModel.y, currentModel.y)
        )
}

/**
 * Pure 2D Technical CAD Viewport (Sections 5, 14, 18.4, 21, 22, 39.4, 42.3 & 45–48 of Specification).
 * - 1-finger drag outside the left-edge drawer strip pans the viewport (`ONE_POINTER_PAN`).
 * - 1-finger inward swipe starting in the left-edge strip opens the tool drawer (`LEFT_EDGE_DRAWER_REVEAL`).
 * - 2-finger gesture performs simultaneous centroid-translation pan AND pinch zoom (`TWO_POINTER_PINCH_ZOOM`).
 * - Dropping from 2 pointers to 1 pointer enters `POST_PINCH_POINTER_SUPPRESSED` until all pointers lift.
 * - Stationary taps, double-taps, and long presses on the sheet perform no selection, OSNAP, or CAD edits.
 */
@Composable
fun CadViewportCanvas(
    workspaceSpace: WorkspaceSpace,
    sheetSetup: SheetSetupConfig,
    gridSnapConfig: GridAndSnapConfig,
    storageUnit: CadUnit,
    displayUnit: CadUnit,
    precision: PrecisionFormatConfig,
    dimensionStyle: DimensionStyleConfig,
    layers: List<CadLayer>,
    entities: List<CadEntity>,
    blocks: Map<String, BlockDefinition>,
    selectedEntityIds: Set<String>,
    isolatedEntityIds: Set<String>?,
    previewEntities: List<CadEntity>,
    cursorModelPos: Point2D,
    activeSnapCandidate: SnapCandidate?,
    referencePoint: Point2D?,
    selectionBoxDrag: SelectionBoxDragState?,
    viewport: ViewportTransform,
    onViewportChanged: (ViewportTransform) -> Unit,
    onViewportSizeChanged: (widthPx: Float, heightPx: Float) -> Unit,
    modifier: Modifier = Modifier,
    polarPreview: PolarTrackingPreviewState? = null,
    onGestureStateChanged: (ViewportGestureState) -> Unit = {},
    isToolDrawerOpen: Boolean = false,
    onRevealToolDrawer: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onDirectTapModelPoint: (Point2D) -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onSelectionDragStart: (Point2D) -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onSelectionDragUpdate: (Point2D) -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onSelectionDragEnd: () -> Unit = {}
) {
    val layerMap = remember(layers) { layers.associateBy { it.name } }
    val currentViewport by rememberUpdatedState(viewport)
    val currentOnViewportChanged by rememberUpdatedState(onViewportChanged)
    val currentOnGestureStateChanged by rememberUpdatedState(onGestureStateChanged)
    val currentIsToolDrawerOpen by rememberUpdatedState(isToolDrawerOpen)
    val currentOnRevealToolDrawer by rememberUpdatedState(onRevealToolDrawer)

    val textPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.MONOSPACE
            textAlign = Paint.Align.CENTER
        }
    }
    val titleBlockTextPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.MONOSPACE
            textAlign = Paint.Align.LEFT
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                onViewportSizeChanged(size.width.toFloat(), size.height.toFloat())
            }
            .pointerInput(Unit) {
                val touchSlopPx = viewConfiguration.touchSlop
                val leftEdgeStripPx = 28.dp.toPx()
                awaitEachGesture {
                    val firstDown = awaitFirstDown(requireUnconsumed = false)
                    val startedInLeftEdge = !currentIsToolDrawerOpen && firstDown.position.x <= leftEdgeStripPx
                    var gestureState = if (startedInLeftEdge) {
                        ViewportGestureState.LEFT_EDGE_DRAWER_REVEAL
                    } else {
                        ViewportGestureState.ONE_POINTER_PAN
                    }
                    currentOnGestureStateChanged(gestureState)

                    var oneFingerDragActivated = false
                    var accumulatedFromDown = Offset.Zero
                    var drawerRevealedInGesture = false
                    var lockedPinchFocalAnchor: Offset = Offset.Unspecified

                    do {
                        val event = awaitPointerEvent()
                        val canceled = event.changes.any { it.isConsumed }
                        val pressedChanges = event.changes.filter { it.pressed }
                        val pointerCount = pressedChanges.size

                        val nextState = ViewportGesturePolicy.resolveState(
                            pressedPointerCount = pointerCount,
                            isCancelled = canceled,
                            startedInLeftEdgeZone = startedInLeftEdge,
                            previousState = gestureState
                        )
                        if (nextState != gestureState) {
                            gestureState = nextState
                            currentOnGestureStateChanged(gestureState)
                            if (gestureState == ViewportGestureState.TWO_POINTER_PINCH_ZOOM) {
                                lockedPinchFocalAnchor = event.calculateCentroid(useCurrent = true)
                            }
                        }

                        when (gestureState) {
                            ViewportGestureState.LEFT_EDGE_DRAWER_REVEAL -> {
                                val panChange = event.calculatePan()
                                accumulatedFromDown += panChange
                                if (!drawerRevealedInGesture &&
                                    accumulatedFromDown.x > touchSlopPx &&
                                    abs(accumulatedFromDown.x) > abs(accumulatedFromDown.y)
                                ) {
                                    drawerRevealedInGesture = true
                                    currentOnRevealToolDrawer()
                                }
                                event.changes.forEach { change ->
                                    if (change.positionChanged()) change.consume()
                                }
                            }

                            ViewportGestureState.ONE_POINTER_PAN -> {
                                val panChange = event.calculatePan()
                                if (!oneFingerDragActivated) {
                                    accumulatedFromDown += panChange
                                    if (hypot(accumulatedFromDown.x, accumulatedFromDown.y) > touchSlopPx) {
                                        oneFingerDragActivated = true
                                    }
                                } else if (panChange != Offset.Zero) {
                                    val updated = ViewportGesturePolicy.applyOneFingerPan(
                                        viewport = currentViewport,
                                        panDeltaScreen = panChange
                                    )
                                    if (updated != currentViewport) {
                                        currentOnViewportChanged(updated)
                                    }
                                }
                                event.changes.forEach { change ->
                                    if (change.positionChanged()) change.consume()
                                }
                            }

                            ViewportGestureState.TWO_POINTER_PINCH_ZOOM -> {
                                val panChange = event.calculatePan()
                                val zoomChange = event.calculateZoom()
                                val centroid = event.calculateCentroid(useCurrent = true).let { c ->
                                    if (c != Offset.Unspecified) c else lockedPinchFocalAnchor
                                }
                                if (centroid != Offset.Unspecified) {
                                    lockedPinchFocalAnchor = centroid
                                }
                                val updated = ViewportGesturePolicy.applyTwoFingerTransform(
                                    viewport = currentViewport,
                                    centroidScreen = centroid,
                                    panDeltaScreen = panChange,
                                    zoomFactor = zoomChange
                                )
                                if (updated != currentViewport) {
                                    currentOnViewportChanged(updated)
                                }
                                event.changes.forEach { change ->
                                    if (change.positionChanged()) change.consume()
                                }
                            }

                            ViewportGestureState.POST_PINCH_POINTER_SUPPRESSED,
                            ViewportGestureState.CANCELLED,
                            ViewportGestureState.IDLE -> {
                                event.changes.forEach { change ->
                                    if (change.positionChanged()) change.consume()
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    currentOnGestureStateChanged(ViewportGestureState.IDLE)
                }
            }
            .testTag("cad_technical_viewport")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // 1. Background
            drawRect(
                color = if (workspaceSpace == WorkspaceSpace.PAPER_SPACE) CadPaperSpaceBg else CadModelSpaceBg,
                size = size
            )

            // 2. Paper Space Sheet Fill, Border & Printable Margin (drawn BEFORE grid)
            if (workspaceSpace == WorkspaceSpace.PAPER_SPACE) {
                drawPaperSpaceSheetBase(sheetSetup, viewport)
            }

            // 3. Adaptive Grid & Origin Axes
            if (gridSnapConfig.showGrid) {
                drawAdaptiveGrid(gridSnapConfig, viewport)
            }
            drawOriginAxes(viewport)

            // 4. Paper Space Title Block Opaque Island, Separators & Zoom-Independent Typography (drawn AFTER grid)
            if (workspaceSpace == WorkspaceSpace.PAPER_SPACE && sheetSetup.showTitleBlock) {
                drawPaperSpaceTitleBlockPass(sheetSetup, displayUnit, viewport, titleBlockTextPaint)
            }

            // 4. Stored Document Entities (with Viewport-Bounds Culling + Overscan Margin)
            val visibleModelRect = viewport.visibleModelBounds(
                canvasWidthPx = size.width,
                canvasHeightPx = size.height,
                overscanPx = 72f
            )
            val cullToleranceModel = (48f / viewport.pixelsPerModelUnit.coerceAtLeast(0.05f)).toDouble()

            for (entity in entities) {
                if (isolatedEntityIds != null && entity.id !in isolatedEntityIds) continue
                val layer = layerMap[entity.layerName]
                if (layer != null && (!layer.isVisible || layer.isFrozen)) continue

                // Cull off-screen finite entities while keeping semi-infinite/infinite rays & xlines visible
                if (entity !is CadEntity.RayEntity && entity !is CadEntity.XLineEntity) {
                    val entityBounds = if (entity is CadEntity.BlockInsertEntity) {
                        entity.boundingBox(blocks)
                    } else {
                        entity.boundingBox()
                    }
                    if (!visibleModelRect.intersects(entityBounds, tolerance = cullToleranceModel)) {
                        continue
                    }
                }

                val isSelected = entity.id in selectedEntityIds

                drawCadEntity(
                    entity = entity,
                    layer = layer,
                    blocks = blocks,
                    isSelected = isSelected,
                    isPreview = false,
                    precision = precision,
                    storageUnit = storageUnit,
                    displayUnit = displayUnit,
                    dimensionStyle = dimensionStyle,
                    viewport = viewport,
                    textPaint = textPaint
                )
            }

            // 5. Active Command Rubber-Band Preview Entities
            for (preview in previewEntities) {
                drawCadEntity(
                    entity = preview,
                    layer = layerMap[preview.layerName],
                    blocks = blocks,
                    isSelected = false,
                    isPreview = true,
                    precision = precision,
                    storageUnit = storageUnit,
                    displayUnit = displayUnit,
                    dimensionStyle = dimensionStyle,
                    viewport = viewport,
                    textPaint = textPaint
                )
            }

            // 6. Rubber-Band Tracking Line from Reference Point to Cursor & Polar Tracking Visual Overlay
            if (referencePoint != null) {
                val sRef = viewport.modelToScreen(referencePoint)
                val sCur = viewport.modelToScreen(cursorModelPos)
                drawLine(
                    color = CadAmberSnap.copy(alpha = 0.7f),
                    start = sRef,
                    end = sCur,
                    strokeWidth = 1.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                )
            }

            if (polarPreview != null) {
                drawPolarTrackingOverlay(
                    polarPreview = polarPreview,
                    storageUnit = storageUnit,
                    displayUnit = displayUnit,
                    precision = precision,
                    viewport = viewport,
                    textPaint = textPaint
                )
            }

            // 7. Window / Crossing Selection Box Overlay (driven by cursor PICK PT corners)
            if (selectionBoxDrag != null) {
                drawSelectionBoxOverlay(selectionBoxDrag, viewport)
            }

            // 8. Active OSNAP Target Marker
            if (activeSnapCandidate != null) {
                drawOsnapMarker(activeSnapCandidate, viewport, textPaint)
            }

            // 9. Precision CAD Crosshair Reticle
            drawCadCrosshair(cursorModelPos, viewport)
        }
    }
}

private fun DrawScope.drawAdaptiveGrid(
    config: GridAndSnapConfig,
    viewport: ViewportTransform
) {
    val topLeftModel = viewport.screenToModel(Offset.Zero)
    val bottomRightModel = viewport.screenToModel(Offset(size.width, size.height))
    val minX = min(topLeftModel.x, bottomRightModel.x)
    val maxX = max(topLeftModel.x, bottomRightModel.x)
    val minY = min(topLeftModel.y, bottomRightModel.y)
    val maxY = max(topLeftModel.y, bottomRightModel.y)

    var majorStep = config.majorGridInterval.coerceAtLeast(1.0)
    // Adapt grid density at extreme zoom-out or zoom-in
    while (majorStep * viewport.pixelsPerModelUnit < 45f) {
        majorStep *= 2.0
    }
    while (majorStep * viewport.pixelsPerModelUnit > 320f && majorStep > 1.0) {
        majorStep *= 0.5
    }
    val minorStep = majorStep / config.minorGridSubdivisions.coerceAtLeast(2).toDouble()

    if (minorStep * viewport.pixelsPerModelUnit >= 10f) {
        val startMinorX = floor(minX / minorStep) * minorStep
        var x = startMinorX
        var guard = 0
        while (x <= maxX && guard < 300) {
            val sx = viewport.modelToScreen(Point2D(x, 0.0)).x
            drawLine(
                color = CadGridMinor,
                start = Offset(sx, 0f),
                end = Offset(sx, size.height),
                strokeWidth = 1f
            )
            x += minorStep
            guard++
        }

        val startMinorY = floor(minY / minorStep) * minorStep
        var y = startMinorY
        guard = 0
        while (y <= maxY && guard < 300) {
            val sy = viewport.modelToScreen(Point2D(0.0, y)).y
            drawLine(
                color = CadGridMinor,
                start = Offset(0f, sy),
                end = Offset(size.width, sy),
                strokeWidth = 1f
            )
            y += minorStep
            guard++
        }
    }

    val startMajorX = floor(minX / majorStep) * majorStep
    var mx = startMajorX
    var guard = 0
    while (mx <= maxX && guard < 150) {
        val sx = viewport.modelToScreen(Point2D(mx, 0.0)).x
        drawLine(
            color = CadGridMajor,
            start = Offset(sx, 0f),
            end = Offset(sx, size.height),
            strokeWidth = 1.4f
        )
        mx += majorStep
        guard++
    }

    val startMajorY = floor(minY / majorStep) * majorStep
    var my = startMajorY
    guard = 0
    while (my <= maxY && guard < 150) {
        val sy = viewport.modelToScreen(Point2D(0.0, my)).y
        drawLine(
            color = CadGridMajor,
            start = Offset(0f, sy),
            end = Offset(size.width, sy),
            strokeWidth = 1.4f
        )
        my += majorStep
        guard++
    }
}

private fun DrawScope.drawOriginAxes(viewport: ViewportTransform) {
    val originScreen = viewport.modelToScreen(Point2D(0.0, 0.0))
    // X-Axis (Red)
    if (originScreen.y in -20f..(size.height + 20f)) {
        drawLine(
            color = CadAxisX.copy(alpha = 0.7f),
            start = Offset(0f, originScreen.y),
            end = Offset(size.width, originScreen.y),
            strokeWidth = 1.8f
        )
    }
    // Y-Axis (Green)
    if (originScreen.x in -20f..(size.width + 20f)) {
        drawLine(
            color = CadAxisY.copy(alpha = 0.7f),
            start = Offset(originScreen.x, 0f),
            end = Offset(originScreen.x, size.height),
            strokeWidth = 1.8f
        )
    }
    // Origin UCS Badge
    drawRect(
        color = Color.White,
        topLeft = Offset(originScreen.x - 4f, originScreen.y - 4f),
        size = Size(8f, 8f),
        style = Stroke(width = 1.5f)
    )
}

/**
 * Section 6.2 Pass 2 & 3 — Draws the Paper Space sheet fill, outer sheet border,
 * and printable margin frame BEFORE the adaptive grid pass.
 */
private fun DrawScope.drawPaperSpaceSheetBase(
    sheetSetup: SheetSetupConfig,
    viewport: ViewportTransform
) {
    val scaleInv = (1.0 / sheetSetup.plotScaleRatio).coerceIn(0.05, 50.0)
    val sheetWModel = sheetSetup.effectiveWidthMm * scaleInv
    val sheetHModel = sheetSetup.effectiveHeightMm * scaleInv
    val marginModel = sheetSetup.marginMm * scaleInv

    val pTopLeft = viewport.modelToScreen(Point2D(0.0, sheetHModel))
    val pBottomRight = viewport.modelToScreen(Point2D(sheetWModel, 0.0))
    val sheetRect = Rect(pTopLeft.x, pTopLeft.y, pBottomRight.x, pBottomRight.y)

    // Pass 2: Paper sheet fill
    drawRect(
        color = CadSheetPaperFill,
        topLeft = sheetRect.topLeft,
        size = sheetRect.size
    )
    // Pass 3: Paper sheet border & printable margin frame
    drawRect(
        color = Color(0xFF64748B),
        topLeft = sheetRect.topLeft,
        size = sheetRect.size,
        style = Stroke(width = 2f)
    )

    val mTopLeft = viewport.modelToScreen(Point2D(marginModel, sheetHModel - marginModel))
    val mBottomRight = viewport.modelToScreen(Point2D(sheetWModel - marginModel, marginModel))
    drawRect(
        color = Color(0xFF94A3B8),
        topLeft = mTopLeft,
        size = Size(mBottomRight.x - mTopLeft.x, mBottomRight.y - mTopLeft.y),
        style = Stroke(width = 1.4f)
    )
}

/**
 * Section 6.2 & 6.3 Pass 5, 6 & 7 — Draws the Paper Space Title Block AFTER the adaptive grid
 * and coordinate axes so its opaque background completely masks any grid lines beneath it,
 * and renders title-block text with zoom-independent screen-space typography.
 */
private fun DrawScope.drawPaperSpaceTitleBlockPass(
    sheetSetup: SheetSetupConfig,
    displayUnit: CadUnit,
    viewport: ViewportTransform,
    titleBlockTextPaint: Paint
) {
    if (!sheetSetup.showTitleBlock) return
    val scaleInv = (1.0 / sheetSetup.plotScaleRatio).coerceIn(0.05, 50.0)
    val sheetWModel = sheetSetup.effectiveWidthMm * scaleInv
    val sheetHModel = sheetSetup.effectiveHeightMm * scaleInv
    val marginModel = sheetSetup.marginMm * scaleInv

    val mBottomRight = viewport.modelToScreen(Point2D(sheetWModel - marginModel, marginModel))
    val tbWModel = sheetWModel * 0.36
    val tbHModel = sheetHModel * 0.14
    val tbTopLeft = viewport.modelToScreen(
        Point2D(sheetWModel - marginModel - tbWModel, marginModel + tbHModel)
    )
    val tbWidthPx = (mBottomRight.x - tbTopLeft.x).coerceAtLeast(0f)
    val tbHeightPx = (mBottomRight.y - tbTopLeft.y).coerceAtLeast(0f)
    if (tbWidthPx < 4f || tbHeightPx < 4f) return

    val tbSize = Size(width = tbWidthPx, height = tbHeightPx)

    // Pass 5: Opaque title-block background fill masking grid & axes beneath it
    drawRect(
        color = Color(0xFF141D2B),
        topLeft = tbTopLeft,
        size = tbSize
    )

    // Pass 6: Title-block outline & horizontal row separators
    drawRect(
        color = Color(0xFF94A3B8),
        topLeft = tbTopLeft,
        size = tbSize,
        style = Stroke(width = 1.5f)
    )

    // Pass 7: Zoom-independent screen-space typography (dp converted to px via Compose density)
    val titleFontSizePx = 14.dp.toPx()
    val metaFontSizePx = 10.5.dp.toPx()
    val horizontalPadPx = 8.dp.toPx()
    val topPadPx = 6.dp.toPx()
    val lineGapPx = 4.dp.toPx()

    // Deterministic line omission / clipping when zoomed far out so font never shrinks to illegibility
    if (tbWidthPx < 36.dp.toPx() || tbHeightPx < (titleFontSizePx + topPadPx)) {
        return
    }

    val row1SeparatorY = tbTopLeft.y + topPadPx + titleFontSizePx + lineGapPx
    val row2SeparatorY = row1SeparatorY + lineGapPx + metaFontSizePx + lineGapPx

    if (tbHeightPx >= (row1SeparatorY - tbTopLeft.y) + metaFontSizePx + topPadPx) {
        drawLine(
            color = Color(0xFF334155),
            start = Offset(tbTopLeft.x, row1SeparatorY),
            end = Offset(tbTopLeft.x + tbWidthPx, row1SeparatorY),
            strokeWidth = 1f
        )
    }
    if (tbHeightPx >= (row2SeparatorY - tbTopLeft.y) + metaFontSizePx + topPadPx) {
        drawLine(
            color = Color(0xFF334155),
            start = Offset(tbTopLeft.x, row2SeparatorY),
            end = Offset(tbTopLeft.x + tbWidthPx, row2SeparatorY),
            strokeWidth = 1f
        )
    }

    val maxTextWidthPx = (tbWidthPx - horizontalPadPx * 2f).coerceAtLeast(8f)

    drawContext.canvas.nativeCanvas.withClip(
        tbTopLeft.x + 2f,
        tbTopLeft.y + 2f,
        tbTopLeft.x + tbWidthPx - 2f,
        tbTopLeft.y + tbHeightPx - 2f
    ) {
        titleBlockTextPaint.textAlign = Paint.Align.LEFT
        titleBlockTextPaint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        titleBlockTextPaint.textSize = titleFontSizePx
        titleBlockTextPaint.color = android.graphics.Color.rgb(226, 232, 240)

        val titleBaselineY = tbTopLeft.y + topPadPx + titleFontSizePx * 0.86f
        val clippedTitle = clipTextToWidth(sheetSetup.titleBlockTitle, titleBlockTextPaint, maxTextWidthPx)
        drawText(clippedTitle, tbTopLeft.x + horizontalPadPx, titleBaselineY, titleBlockTextPaint)

        titleBlockTextPaint.typeface = Typeface.MONOSPACE
        titleBlockTextPaint.textSize = metaFontSizePx
        titleBlockTextPaint.color = android.graphics.Color.rgb(148, 163, 184)

        val meta1BaselineY = row1SeparatorY + lineGapPx + metaFontSizePx * 0.88f
        if (meta1BaselineY + 3f <= tbTopLeft.y + tbHeightPx) {
            val metaLine1 = "${sheetSetup.titleBlockDrawingNo} • ${sheetSetup.titleBlockRevision} • SCALE ${sheetSetup.scaleLabel}"
            drawText(
                clipTextToWidth(metaLine1, titleBlockTextPaint, maxTextWidthPx),
                tbTopLeft.x + horizontalPadPx,
                meta1BaselineY,
                titleBlockTextPaint
            )
        }

        val meta2BaselineY = row2SeparatorY + lineGapPx + metaFontSizePx * 0.88f
        if (meta2BaselineY + 3f <= tbTopLeft.y + tbHeightPx) {
            val metaLine2 = "${sheetSetup.preset.label} • UNITS: ${displayUnit.symbol} • ${sheetSetup.draftingStandard.name}"
            drawText(
                clipTextToWidth(metaLine2, titleBlockTextPaint, maxTextWidthPx),
                tbTopLeft.x + horizontalPadPx,
                meta2BaselineY,
                titleBlockTextPaint
            )
        }
    }
}

private fun clipTextToWidth(text: String, paint: Paint, maxWidthPx: Float): String {
    if (text.isEmpty() || paint.measureText(text) <= maxWidthPx) return text
    var end = text.length
    while (end > 1 && paint.measureText(text, 0, end) > maxWidthPx) {
        end--
    }
    return text.substring(0, end)
}

private fun DrawScope.drawCadEntity(
    entity: CadEntity,
    layer: CadLayer?,
    blocks: Map<String, BlockDefinition>,
    isSelected: Boolean,
    isPreview: Boolean,
    precision: PrecisionFormatConfig,
    storageUnit: CadUnit,
    displayUnit: CadUnit,
    dimensionStyle: DimensionStyleConfig,
    viewport: ViewportTransform,
    textPaint: Paint
) {
    val rawColorHex = entity.colorHexOverride ?: layer?.colorHex ?: 0xFFF8FAFC
    val baseColor = when {
        isSelected -> CadCyanPrimary
        isPreview -> CadAmberSnap
        else -> Color(rawColorHex or 0xFF000000L)
    }
    val lwMm = entity.lineWeightMmOverride ?: layer?.lineWeightMm ?: 0.25f
    val strokePx = ((lwMm * 5.8f) + if (isSelected) 1.6f else 0f).coerceIn(1.2f, 8f)
    val effectiveLineType = if (entity.lineTypeOverride != CadLineType.BY_LAYER) {
        entity.lineTypeOverride
    } else {
        layer?.lineType ?: CadLineType.CONTINUOUS
    }

    val pathEffect: PathEffect? = when {
        isSelected -> PathEffect.dashPathEffect(floatArrayOf(12f, 6f), 0f)
        isPreview -> PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
        effectiveLineType == CadLineType.DASHED -> PathEffect.dashPathEffect(floatArrayOf(14f, 8f), 0f)
        effectiveLineType == CadLineType.HIDDEN -> PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
        effectiveLineType == CadLineType.CENTER -> PathEffect.dashPathEffect(floatArrayOf(18f, 6f, 4f, 6f), 0f)
        effectiveLineType == CadLineType.PHANTOM -> PathEffect.dashPathEffect(floatArrayOf(20f, 5f, 4f, 5f, 4f, 5f), 0f)
        effectiveLineType == CadLineType.DOTTED -> PathEffect.dashPathEffect(floatArrayOf(3f, 6f), 0f)
        else -> null
    }

    when (entity) {
        is CadEntity.LineEntity -> {
            val s = viewport.modelToScreen(entity.start)
            val e = viewport.modelToScreen(entity.end)
            drawLine(
                color = baseColor,
                start = s,
                end = e,
                strokeWidth = strokePx,
                cap = StrokeCap.Round,
                pathEffect = pathEffect
            )
            if (isSelected) {
                drawGripHandle(s)
                drawGripHandle(e)
                drawGripHandle(viewport.modelToScreen(entity.midpoint), isMidpoint = true)
            }
        }

        is CadEntity.PolylineEntity -> {
            val prims = CadGeometryKernel.explodePolylineToPrimitives(entity)
            val polyStroke = if (entity.globalWidth > 0.0) {
                (entity.globalWidth.toFloat() * viewport.pixelsPerModelUnit).coerceAtLeast(strokePx)
            } else strokePx
            for (prim in prims) {
                when (prim) {
                    is CadEntity.LineEntity -> drawLine(
                        color = baseColor,
                        start = viewport.modelToScreen(prim.start),
                        end = viewport.modelToScreen(prim.end),
                        strokeWidth = polyStroke,
                        cap = StrokeCap.Round,
                        pathEffect = pathEffect
                    )
                    is CadEntity.ArcEntity -> drawArcPrimitive(prim, baseColor, polyStroke, pathEffect, viewport)
                    else -> Unit
                }
            }
            if (isSelected) {
                entity.vertices.forEach { v -> drawGripHandle(viewport.modelToScreen(v.position)) }
            }
        }

        is CadEntity.CircleEntity -> {
            val centerPx = viewport.modelToScreen(entity.center)
            val radiusPx = (entity.radius * viewport.pixelsPerModelUnit).toFloat().coerceAtLeast(1f)
            drawCircle(
                color = baseColor,
                radius = radiusPx,
                center = centerPx,
                style = Stroke(width = strokePx, pathEffect = pathEffect)
            )
            if (isSelected) {
                drawGripHandle(centerPx)
                entity.quadrants().forEach { q -> drawGripHandle(viewport.modelToScreen(q)) }
            }
        }

        is CadEntity.ArcEntity -> {
            drawArcPrimitive(entity, baseColor, strokePx, pathEffect, viewport)
            if (isSelected) {
                drawGripHandle(viewport.modelToScreen(entity.startPoint))
                drawGripHandle(viewport.modelToScreen(entity.endPoint))
                drawGripHandle(viewport.modelToScreen(entity.midPoint), isMidpoint = true)
            }
        }

        is CadEntity.EllipseEntity -> {
            val samples = CadGeometryKernel.sampleEllipsePoints(entity, 56)
            drawConnectedSamples(samples, baseColor, strokePx, pathEffect, viewport)
            if (isSelected) {
                drawGripHandle(viewport.modelToScreen(entity.center))
            }
        }

        is CadEntity.SplineEntity -> {
            val samples = entity.sampleCurvePoints(14)
            drawConnectedSamples(samples, baseColor, strokePx, pathEffect, viewport)
            if (isSelected) {
                entity.controlPoints.forEach { cp -> drawGripHandle(viewport.modelToScreen(cp)) }
            }
        }

        is CadEntity.PointEntity -> {
            val p = viewport.modelToScreen(entity.position)
            drawCircle(color = baseColor, radius = 5f, center = p, style = Stroke(width = 1.5f))
            drawLine(color = baseColor, start = Offset(p.x - 7f, p.y), end = Offset(p.x + 7f, p.y), strokeWidth = 1.5f)
            drawLine(color = baseColor, start = Offset(p.x, p.y - 7f), end = Offset(p.x, p.y + 7f), strokeWidth = 1.5f)
        }

        is CadEntity.RayEntity -> {
            val s = viewport.modelToScreen(entity.basePoint)
            val far = viewport.modelToScreen(entity.basePoint.polarOffset(5000.0, entity.angleDeg))
            drawLine(
                color = baseColor,
                start = s,
                end = far,
                strokeWidth = strokePx,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
            )
        }

        is CadEntity.XLineEntity -> {
            val p1 = viewport.modelToScreen(entity.rootPoint.polarOffset(-5000.0, entity.angleDeg))
            val p2 = viewport.modelToScreen(entity.rootPoint.polarOffset(5000.0, entity.angleDeg))
            drawLine(
                color = baseColor,
                start = p1,
                end = p2,
                strokeWidth = strokePx,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 6f, 3f, 6f), 0f)
            )
        }

        is CadEntity.DonutEntity -> {
            val centerPx = viewport.modelToScreen(entity.center)
            val rInnerPx = (entity.innerRadius * viewport.pixelsPerModelUnit).toFloat()
            val rOuterPx = (entity.outerRadius * viewport.pixelsPerModelUnit).toFloat()
            val ringRadius = (rInnerPx + rOuterPx) * 0.5f
            val ringStroke = (rOuterPx - rInnerPx).coerceAtLeast(2f)
            drawCircle(
                color = baseColor.copy(alpha = 0.65f),
                radius = ringRadius,
                center = centerPx,
                style = Stroke(width = ringStroke)
            )
            drawCircle(color = baseColor, radius = rOuterPx, center = centerPx, style = Stroke(width = 1.5f))
            if (rInnerPx > 1f) {
                drawCircle(color = baseColor, radius = rInnerPx, center = centerPx, style = Stroke(width = 1.5f))
            }
        }

        is CadEntity.HatchEntity -> {
            drawHatchEntity(entity, baseColor, viewport)
        }

        is CadEntity.TextEntity -> {
            val s = viewport.modelToScreen(entity.position)
            val fontSizePx = (entity.height * viewport.pixelsPerModelUnit).toFloat().coerceIn(11f, 36f)
            drawContext.canvas.nativeCanvas.withSave {
                translate(s.x, s.y)
                rotate(-entity.rotationDeg.toFloat())
                textPaint.color = toAndroidColor(baseColor)
                textPaint.textSize = fontSizePx
                textPaint.textAlign = Paint.Align.LEFT
                entity.text.lines().forEachIndexed { idx, line ->
                    drawText(line, 0f, idx * (fontSizePx * 1.2f), textPaint)
                }
                textPaint.textAlign = Paint.Align.CENTER
            }
        }

        is CadEntity.LeaderEntity -> {
            val tip = viewport.modelToScreen(entity.arrowTip)
            val sh = viewport.modelToScreen(entity.shoulderPoint)
            val end = viewport.modelToScreen(entity.landingEnd)
            drawLine(color = baseColor, start = tip, end = sh, strokeWidth = strokePx)
            drawLine(color = baseColor, start = sh, end = end, strokeWidth = strokePx)
            drawArrowheadScreen(tip, sh, 10f, 4f, baseColor)
            val shelfMidX = (sh.x + end.x) * 0.5f
            val labelY = sh.y - 5f
            val fontSizePx = (entity.textHeight * viewport.pixelsPerModelUnit).toFloat().coerceIn(10.5f, 15f)
            drawContext.canvas.nativeCanvas.withSave {
                translate(shelfMidX, labelY)
                textPaint.textSize = fontSizePx
                textPaint.textAlign = Paint.Align.CENTER
                textPaint.color = toAndroidColor(baseColor)
                drawText(entity.text, 0f, 0f, textPaint)
            }
        }

        is CadEntity.CenterMarkEntity -> {
            val c = viewport.modelToScreen(entity.center)
            val extPx = (entity.radius * 1.25 * viewport.pixelsPerModelUnit).toFloat().coerceAtLeast(10f)
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 5f, 4f, 5f), 0f)
            drawLine(
                color = baseColor,
                start = Offset(c.x - extPx, c.y),
                end = Offset(c.x + extPx, c.y),
                strokeWidth = 1.4f,
                pathEffect = dashEffect
            )
            drawLine(
                color = baseColor,
                start = Offset(c.x, c.y - extPx),
                end = Offset(c.x, c.y + extPx),
                strokeWidth = 1.4f,
                pathEffect = dashEffect
            )
        }

        is CadEntity.BlockInsertEntity -> {
            val def = blocks[entity.blockName]
            if (def != null) {
                for (child in def.entities) {
                    val transformed = child
                        .translate(-def.basePoint.x, -def.basePoint.y)
                        .scale(Point2D(0.0, 0.0), entity.scaleFactor)
                        .rotate(Point2D(0.0, 0.0), entity.rotationDeg)
                        .translate(entity.insertionPoint.x, entity.insertionPoint.y)
                    drawCadEntity(
                        entity = transformed,
                        layer = layer,
                        blocks = blocks,
                        isSelected = isSelected,
                        isPreview = isPreview,
                        precision = precision,
                        storageUnit = storageUnit,
                        displayUnit = displayUnit,
                        dimensionStyle = dimensionStyle,
                        viewport = viewport,
                        textPaint = textPaint
                    )
                }
            } else {
                val p = viewport.modelToScreen(entity.insertionPoint)
                drawRect(color = baseColor, topLeft = Offset(p.x - 8f, p.y - 8f), size = Size(16f, 16f), style = Stroke(2f))
            }
        }

        is CadEntity.DimensionEntity -> {
            drawAdaptiveDimension(
                dim = entity,
                color = if (isSelected) CadCyanPrimary else CadDimensionBlue,
                precision = precision,
                storageUnit = storageUnit,
                displayUnit = displayUnit,
                style = dimensionStyle,
                viewport = viewport,
                textPaint = textPaint
            )
        }
    }
}

private fun DrawScope.drawArcPrimitive(
    arc: CadEntity.ArcEntity,
    color: Color,
    strokeWidth: Float,
    pathEffect: PathEffect?,
    viewport: ViewportTransform
) {
    val steps = max(16, (abs(arc.sweepAngleDeg) / 4.0).toInt())
    val pts = (0..steps).map { i ->
        val ang = arc.startAngleDeg + arc.sweepAngleDeg * (i.toDouble() / steps.toDouble())
        arc.center.polarOffset(arc.radius, ang)
    }
    drawConnectedSamples(pts, color, strokeWidth, pathEffect, viewport)
}

private fun DrawScope.drawConnectedSamples(
    points: List<Point2D>,
    color: Color,
    strokeWidth: Float,
    pathEffect: PathEffect?,
    viewport: ViewportTransform
) {
    if (points.size < 2) return
    val path = Path()
    val first = viewport.modelToScreen(points.first())
    path.moveTo(first.x, first.y)
    for (pt in points.drop(1)) {
        val s = viewport.modelToScreen(pt)
        path.lineTo(s.x, s.y)
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round, pathEffect = pathEffect)
    )
}

private fun DrawScope.drawHatchEntity(
    hatch: CadEntity.HatchEntity,
    color: Color,
    viewport: ViewportTransform
) {
    if (hatch.boundaryPoints.size < 3) return
    val path = Path()
    val first = viewport.modelToScreen(hatch.boundaryPoints.first())
    path.moveTo(first.x, first.y)
    for (pt in hatch.boundaryPoints.drop(1)) {
        val s = viewport.modelToScreen(pt)
        path.lineTo(s.x, s.y)
    }
    path.close()

    if (hatch.pattern == HatchPattern.SOLID) {
        drawPath(path = path, color = color.copy(alpha = 0.28f))
    } else {
        // Draw boundary outline + clipped hatch lines
        drawPath(path = path, color = color.copy(alpha = 0.7f), style = Stroke(width = 1.2f))
        val bounds = hatch.boundingBox()
        val spacing = (3.5 * hatch.patternScale).coerceAtLeast(1.5)
        val diag = hypot(bounds.width, bounds.height)
        val center = bounds.center
        val angleRad = Math.toRadians(45.0 + hatch.patternAngleDeg)
        val dirX = cos(angleRad)
        val dirY = sin(angleRad)
        val normX = -dirY
        val normY = dirX

        var offset = -diag * 0.6
        var count = 0
        while (offset <= diag * 0.6 && count < 80) {
            val basePt = Point2D(center.x + normX * offset, center.y + normY * offset)
            val lStart = basePt.offset(-dirX * diag, -dirY * diag)
            val lEnd = basePt.offset(dirX * diag, dirY * diag)
            // Intersect line with boundary polygon edges
            val polyEdges = (hatch.boundaryPoints + hatch.boundaryPoints.first()).zipWithNext()
            val hits = polyEdges.mapNotNull { (a, b) ->
                CadGeometryKernel.intersectSegments(lStart, lEnd, a, b)
            }.sortedWith(compareBy<Point2D> { it.x }.thenBy { it.y })

            var k = 0
            while (k + 1 < hits.size) {
                val p1 = viewport.modelToScreen(hits[k])
                val p2 = viewport.modelToScreen(hits[k + 1])
                drawLine(color = color.copy(alpha = 0.65f), start = p1, end = p2, strokeWidth = 1.1f)
                k += 2
            }
            offset += spacing
            count++
        }
    }
}

private fun DrawScope.drawAdaptiveDimension(
    dim: CadEntity.DimensionEntity,
    color: Color,
    precision: PrecisionFormatConfig,
    storageUnit: CadUnit,
    displayUnit: CadUnit,
    style: DimensionStyleConfig,
    viewport: ViewportTransform,
    textPaint: Paint
) {
    val plan = AdaptiveAnnotationRenderer.computeRenderPlan(
        dim = dim,
        precision = precision,
        storageUnit = storageUnit,
        displayUnit = displayUnit,
        style = style,
        viewport = viewport
    )
    if (!plan.isVisibleAtCurrentZoom) return

    val arrowLenPx = (plan.arrowLengthModel * viewport.pixelsPerModelUnit).toFloat().coerceIn(6.5f, 14f)
    val arrowWidPx = (plan.arrowWidthModel * viewport.pixelsPerModelUnit).toFloat().coerceIn(2.5f, 5.5f)
    val fontSizePx = plan.fontSizeSp * density

    if (plan.isRadialOrDiametric) {
        val centerModel = plan.circleOrArcCenterModel ?: dim.anchor1
        val rimModel = plan.rimContactPointModel ?: dim.anchor2
        val shoulderModel = plan.leaderShoulderModel ?: dim.dimensionLinePoint
        val landingEndModel = plan.leaderLandingEndModel ?: shoulderModel

        val centerPx = viewport.modelToScreen(centerModel)
        val rimPx = viewport.modelToScreen(rimModel)
        val shoulderPx = viewport.modelToScreen(shoulderModel)
        val landingEndPx = viewport.modelToScreen(landingEndModel)

        // 1. Precision Center Crosshair (+) at Circle/Arc Center
        val crossArm = 5.5f
        drawLine(
            color = color.copy(alpha = 0.85f),
            start = Offset(centerPx.x - crossArm, centerPx.y),
            end = Offset(centerPx.x + crossArm, centerPx.y),
            strokeWidth = 1.3f
        )
        drawLine(
            color = color.copy(alpha = 0.85f),
            start = Offset(centerPx.x, centerPx.y - crossArm),
            end = Offset(centerPx.x, centerPx.y + crossArm),
            strokeWidth = 1.3f
        )

        // 2. Internal Radial or Diametric Reference Line + Circumference Arrowhead(s)
        if (plan.isDiameterCallout && plan.oppositeRimPointModel != null) {
            val oppRimPx = viewport.modelToScreen(plan.oppositeRimPointModel)
            // Draw diametric reference line across circle passing through center
            drawLine(
                color = color.copy(alpha = if (plan.arrowsInside) 0.9f else 0.55f),
                start = oppRimPx,
                end = rimPx,
                strokeWidth = if (plan.arrowsInside) 1.4f else 1.1f,
                pathEffect = if (plan.arrowsInside) null else PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)
            )
            if (plan.arrowsInside) {
                // Internal diametric arrows at both opposite circumference points
                drawArrowheadScreen(tip = rimPx, tailRef = centerPx, lengthPx = arrowLenPx, halfWidthPx = arrowWidPx, color = color)
                drawArrowheadScreen(tip = oppRimPx, tailRef = centerPx, lengthPx = arrowLenPx, halfWidthPx = arrowWidPx, color = color)
            } else {
                // External inward-pointing arrows at both opposite circumference points
                drawArrowheadScreen(tip = rimPx, tailRef = shoulderPx, lengthPx = arrowLenPx, halfWidthPx = arrowWidPx, color = color)
                val oppDx = oppRimPx.x - centerPx.x
                val oppDy = oppRimPx.y - centerPx.y
                val oppLen = hypot(oppDx, oppDy).coerceAtLeast(1f)
                val oppTailPx = Offset(
                    oppRimPx.x + (oppDx / oppLen) * (arrowLenPx * 1.9f),
                    oppRimPx.y + (oppDy / oppLen) * (arrowLenPx * 1.9f)
                )
                drawLine(color = color, start = oppRimPx, end = oppTailPx, strokeWidth = 1.4f)
                drawArrowheadScreen(tip = oppRimPx, tailRef = oppTailPx, lengthPx = arrowLenPx, halfWidthPx = arrowWidPx, color = color)
            }
        } else {
            // Radius (R) callout: draw thin radial reference line from arc center to arc midpoint
            drawLine(
                color = color.copy(alpha = 0.65f),
                start = centerPx,
                end = rimPx,
                strokeWidth = 1.15f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)
            )
            // Single filled arrowhead at arc circumference pointing along radial leader
            val tailForArrow = if (hypot(shoulderPx.x - rimPx.x, shoulderPx.y - rimPx.y) >= arrowLenPx * 0.8f) {
                shoulderPx
            } else {
                centerPx
            }
            drawArrowheadScreen(tip = rimPx, tailRef = tailForArrow, lengthPx = arrowLenPx, halfWidthPx = arrowWidPx, color = color)
        }

        // 3. Collinear Radial Leader Line (Rim -> Shoulder) + Horizontal Landing Shelf (Shoulder -> LandingEnd)
        drawLine(color = color, start = rimPx, end = shoulderPx, strokeWidth = 1.5f)
        drawLine(color = color, start = shoulderPx, end = landingEndPx, strokeWidth = 1.5f)

        // 4. Dimension Label centered cleanly above the horizontal landing shelf
        val shelfMidX = (shoulderPx.x + landingEndPx.x) * 0.5f
        val labelY = shoulderPx.y - 5f
        drawContext.canvas.nativeCanvas.withSave {
            translate(shelfMidX, labelY)
            textPaint.textSize = fontSizePx
            textPaint.color = toAndroidColor(color)
            drawText(plan.labelText, 0f, 0f, textPaint)
        }
        return
    }

    if (plan.isAngularArc && plan.angularCenterModel != null) {
        val e1S = viewport.modelToScreen(plan.ext1StartModel)
        val e1E = viewport.modelToScreen(plan.ext1EndModel)
        val e2S = viewport.modelToScreen(plan.ext2StartModel)
        val e2E = viewport.modelToScreen(plan.ext2EndModel)
        drawLine(color = color.copy(alpha = 0.8f), start = e1S, end = e1E, strokeWidth = 1.2f)
        drawLine(color = color.copy(alpha = 0.8f), start = e2S, end = e2E, strokeWidth = 1.2f)

        // Sample circular dimension arc
        val steps = max(16, (abs(plan.angularSweepDeg) / 3.0).toInt())
        val arcPts = (0..steps).map { i ->
            val ang = plan.angularStartDeg + plan.angularSweepDeg * (i.toDouble() / steps.toDouble())
            plan.angularCenterModel.polarOffset(plan.angularRadiusModel, ang)
        }
        drawConnectedSamples(arcPts, color, 1.5f, null, viewport)

        if (arcPts.size >= 2) {
            val startPx = viewport.modelToScreen(arcPts.first())
            val secondPx = viewport.modelToScreen(arcPts[1])
            val endPx = viewport.modelToScreen(arcPts.last())
            val prevEndPx = viewport.modelToScreen(arcPts[arcPts.size - 2])
            drawArrowheadScreen(tip = startPx, tailRef = secondPx, lengthPx = arrowLenPx, halfWidthPx = arrowWidPx, color = color)
            drawArrowheadScreen(tip = endPx, tailRef = prevEndPx, lengthPx = arrowLenPx, halfWidthPx = arrowWidPx, color = color)
        }

        val textScreen = viewport.modelToScreen(plan.textPositionModel)
        drawContext.canvas.nativeCanvas.withSave {
            translate(textScreen.x, textScreen.y)
            textPaint.textSize = fontSizePx
            textPaint.color = toAndroidColor(color)
            drawText(plan.labelText, 0f, 0f, textPaint)
        }
        return
    }

    val e1S = viewport.modelToScreen(plan.ext1StartModel)
    val e1E = viewport.modelToScreen(plan.ext1EndModel)
    val e2S = viewport.modelToScreen(plan.ext2StartModel)
    val e2E = viewport.modelToScreen(plan.ext2EndModel)
    val dS = viewport.modelToScreen(plan.dimLineStartModel)
    val dE = viewport.modelToScreen(plan.dimLineEndModel)

    // Extension lines
    if (e1S != e1E) {
        drawLine(color = color.copy(alpha = 0.8f), start = e1S, end = e1E, strokeWidth = 1.2f)
    }
    if (e2S != e2E) {
        drawLine(color = color.copy(alpha = 0.8f), start = e2S, end = e2E, strokeWidth = 1.2f)
    }

    // Dimension line
    drawLine(color = color, start = dS, end = dE, strokeWidth = 1.5f)

    if (plan.arrowsInside) {
        drawArrowheadScreen(tip = dS, tailRef = dE, lengthPx = arrowLenPx, halfWidthPx = arrowWidPx, color = color)
        drawArrowheadScreen(tip = dE, tailRef = dS, lengthPx = arrowLenPx, halfWidthPx = arrowWidPx, color = color)
    } else {
        // Flip arrows outside with short extension tails
        val dx = dE.x - dS.x
        val dy = dE.y - dS.y
        val len = hypot(dx, dy).coerceAtLeast(1f)
        val ux = dx / len
        val uy = dy / len
        val outTail1 = Offset(dS.x - ux * arrowLenPx * 2.2f, dS.y - uy * arrowLenPx * 2.2f)
        val outTail2 = Offset(dE.x + ux * arrowLenPx * 2.2f, dE.y + uy * arrowLenPx * 2.2f)
        drawLine(color = color, start = outTail1, end = dS, strokeWidth = 1.3f)
        drawLine(color = color, start = dE, end = outTail2, strokeWidth = 1.3f)
        drawArrowheadScreen(tip = dS, tailRef = outTail1, lengthPx = arrowLenPx, halfWidthPx = arrowWidPx, color = color)
        drawArrowheadScreen(tip = dE, tailRef = outTail2, lengthPx = arrowLenPx, halfWidthPx = arrowWidPx, color = color)
    }

    // Transparent dimension text rendered directly above sheet geometry (Section 21)
    val textScreen = viewport.modelToScreen(plan.textPositionModel)
    drawContext.canvas.nativeCanvas.withSave {
        translate(textScreen.x, textScreen.y)
        rotate(plan.textRotationDeg)
        textPaint.textSize = fontSizePx
        textPaint.color = toAndroidColor(color)
        drawText(plan.labelText, 0f, 0f, textPaint)
    }
}

private fun DrawScope.drawArrowheadScreen(
    tip: Offset,
    tailRef: Offset,
    lengthPx: Float,
    halfWidthPx: Float,
    color: Color
) {
    val dx = tailRef.x - tip.x
    val dy = tailRef.y - tip.y
    val dist = hypot(dx, dy)
    if (dist < 1e-3f) return
    val ux = dx / dist
    val uy = dy / dist
    val nx = -uy
    val ny = ux
    val baseCenter = Offset(tip.x + ux * lengthPx, tip.y + uy * lengthPx)
    val pLeft = Offset(baseCenter.x + nx * halfWidthPx, baseCenter.y + ny * halfWidthPx)
    val pRight = Offset(baseCenter.x - nx * halfWidthPx, baseCenter.y - ny * halfWidthPx)
    val path = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(pLeft.x, pLeft.y)
        lineTo(pRight.x, pRight.y)
        close()
    }
    drawPath(path = path, color = color)
}

private fun DrawScope.drawGripHandle(screenPos: Offset, isMidpoint: Boolean = false) {
    val color = if (isMidpoint) CadAmberSnap else CadCyanPrimary
    drawRect(
        color = color,
        topLeft = Offset(screenPos.x - 4.5f, screenPos.y - 4.5f),
        size = Size(9f, 9f)
    )
}

private fun DrawScope.drawSelectionBoxOverlay(
    drag: SelectionBoxDragState,
    viewport: ViewportTransform
) {
    val p1 = viewport.modelToScreen(drag.startModel)
    val p2 = viewport.modelToScreen(drag.currentModel)
    val left = min(p1.x, p2.x)
    val top = min(p1.y, p2.y)
    val w = abs(p2.x - p1.x)
    val h = abs(p2.y - p1.y)

    val boxColor = if (drag.isCrossingMode) CadEmeraldCrossing else CadBlueWindow
    val effect = if (drag.isCrossingMode) PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f) else null

    drawRect(
        color = boxColor.copy(alpha = 0.16f),
        topLeft = Offset(left, top),
        size = Size(w, h)
    )
    drawRect(
        color = boxColor,
        topLeft = Offset(left, top),
        size = Size(w, h),
        style = Stroke(width = 1.8f, pathEffect = effect)
    )
}

private fun DrawScope.drawOsnapMarker(
    snap: SnapCandidate,
    viewport: ViewportTransform,
    textPaint: Paint
) {
    val s = viewport.modelToScreen(snap.point)
    val r = 9f
    when (snap.mode) {
        OsnapMode.ENDPOINT, OsnapMode.INSERTION -> {
            drawRect(
                color = CadAmberSnap,
                topLeft = Offset(s.x - r, s.y - r),
                size = Size(r * 2, r * 2),
                style = Stroke(width = 2.4f)
            )
        }
        OsnapMode.MIDPOINT -> {
            val path = Path().apply {
                moveTo(s.x, s.y - r * 1.15f)
                lineTo(s.x - r * 1.1f, s.y + r * 0.9f)
                lineTo(s.x + r * 1.1f, s.y + r * 0.9f)
                close()
            }
            drawPath(path = path, color = CadAmberSnap, style = Stroke(width = 2.4f))
        }
        OsnapMode.CENTER, OsnapMode.TANGENT -> {
            drawCircle(
                color = CadAmberSnap,
                radius = r,
                center = s,
                style = Stroke(width = 2.4f)
            )
        }
        OsnapMode.INTERSECTION -> {
            drawLine(color = CadAmberSnap, start = Offset(s.x - r, s.y - r), end = Offset(s.x + r, s.y + r), strokeWidth = 2.5f)
            drawLine(color = CadAmberSnap, start = Offset(s.x - r, s.y + r), end = Offset(s.x + r, s.y - r), strokeWidth = 2.5f)
        }
        OsnapMode.QUADRANT -> {
            val path = Path().apply {
                moveTo(s.x, s.y - r * 1.2f)
                lineTo(s.x + r * 1.2f, s.y)
                lineTo(s.x, s.y + r * 1.2f)
                lineTo(s.x - r * 1.2f, s.y)
                close()
            }
            drawPath(path = path, color = CadAmberSnap, style = Stroke(width = 2.4f))
        }
        else -> {
            drawCircle(color = CadAmberSnap, radius = r * 0.85f, center = s, style = Stroke(width = 2.2f))
        }
    }

    drawContext.canvas.nativeCanvas.apply {
        textPaint.color = android.graphics.Color.rgb(255, 179, 0)
        textPaint.textSize = 24f
        drawText(snap.mode.badgeCode, s.x + 22f, s.y - 12f, textPaint)
    }
}

private fun DrawScope.drawCadCrosshair(
    cursorModelPos: Point2D,
    viewport: ViewportTransform
) {
    val c = viewport.modelToScreen(cursorModelPos)
    val armLen = 28f
    val apertureHalf = 5.5f

    // Pickbox Aperture Square
    drawRect(
        color = CadCyanPrimary,
        topLeft = Offset(c.x - apertureHalf, c.y - apertureHalf),
        size = Size(apertureHalf * 2f, apertureHalf * 2f),
        style = Stroke(width = 1.6f)
    )
    // Horizontal & Vertical Crosshair Hairlines
    drawLine(
        color = CadCyanPrimary,
        start = Offset(c.x - armLen, c.y),
        end = Offset(c.x + armLen, c.y),
        strokeWidth = 1.4f
    )
    drawLine(
        color = CadCyanPrimary,
        start = Offset(c.x, c.y - armLen),
        end = Offset(c.x, c.y + armLen),
        strokeWidth = 1.4f
    )
}

/**
 * Sections 43, 45, 46, 47 & 48 — Visual-Only Polar Tracking Overlay.
 * Draws:
 * 1. Dotted/dashed polar guide ray from basePoint along worldPolarAngleDeg when acquired.
 * 2. Dotted angular arc from reference axis to worldPolarAngleDeg with read-only angle label (e.g., 90°, 126°, 180°).
 * 3. Strictly non-interactive read-only cursor badge showing R, θ, and POLAR θp.
 * Never persists into document entities or DXF/PDF exports.
 */
private fun DrawScope.drawPolarTrackingOverlay(
    polarPreview: PolarTrackingPreviewState,
    storageUnit: CadUnit,
    displayUnit: CadUnit,
    precision: PrecisionFormatConfig,
    viewport: ViewportTransform,
    textPaint: Paint
) {
    val baseScreen = viewport.modelToScreen(polarPreview.basePoint)
    val cursorScreen = viewport.modelToScreen(polarPreview.rawCursorPoint)
    val polarColor = Color(0xFF38BDF8)
    val arcColor = Color(0xFFFBBF24)

    // 1. V19 §13.2 — Blue Dashed PolarSnap Ray:
    // Rendered IF AND ONLY IF PolarSnap is enabled AND the effective cursor bearing matches
    // an active PolarSnap angle within POLAR_SNAP_RAY_DISPLAY_EPSILON_DEG (0.01°).
    if (polarPreview.isPolarSnapRayVisibleForDisplay &&
        polarPreview.worldPolarSnapAngleDeg != null
    ) {
        val worldAngleRad = Math.toRadians(polarPreview.worldPolarSnapAngleDeg)
        val diagModel = (hypot(size.width, size.height) / viewport.pixelsPerModelUnit.coerceAtLeast(0.05f)).toDouble() * 1.8
        val farEndModel = Point2D(
            x = polarPreview.basePoint.x + cos(worldAngleRad) * diagModel,
            y = polarPreview.basePoint.y + sin(worldAngleRad) * diagModel
        )
        val rayEndScreen = viewport.modelToScreen(farEndModel)
        drawLine(
            color = polarColor.copy(alpha = 0.82f),
            start = baseScreen,
            end = rayEndScreen,
            strokeWidth = 1.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
        )
    }

    // 2. V19 §13.2 — Yellow Visual Polar Tracking Reference Angle Arc & Label (Read-Only HUD):
    // Rendered when Visual Polar Tracking acquires a guide within tolerance (0.35° / 0.55°),
    // without ever drawing the blue dashed PolarSnap ray unless isPolarSnapRayVisibleForDisplay is true.
    if (polarPreview.isPolarGuideAcquired &&
        polarPreview.activePolarAngleDeg != null
    ) {
        val screenDist = hypot(cursorScreen.x - baseScreen.x, cursorScreen.y - baseScreen.y)
        val arcRadiusPx = (screenDist * 0.35f).coerceIn(28f, 68f)
        val sweepCcwDeg = polarPreview.activePolarAngleDeg.toFloat()
        if (sweepCcwDeg >= 0.5f && screenDist >= 16f) {
            // In screen coordinates (+Y downward), model angle alpha maps to screen angle -alpha
            val startAngleScreenDeg = (-polarPreview.referenceAngleDeg).toFloat()
            val sweepAngleScreenDeg = -sweepCcwDeg
            drawArc(
                color = arcColor.copy(alpha = 0.85f),
                startAngle = startAngleScreenDeg,
                sweepAngle = sweepAngleScreenDeg,
                useCenter = false,
                topLeft = Offset(baseScreen.x - arcRadiusPx, baseScreen.y - arcRadiusPx),
                size = Size(arcRadiusPx * 2f, arcRadiusPx * 2f),
                style = Stroke(
                    width = 1.4f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                )
            )

            // Reference baseline tick for clarity
            val refRad = Math.toRadians(polarPreview.referenceAngleDeg)
            val refTipModel = Point2D(
                x = polarPreview.basePoint.x + cos(refRad) * (arcRadiusPx * 1.15f / viewport.pixelsPerModelUnit.coerceAtLeast(0.05f)),
                y = polarPreview.basePoint.y + sin(refRad) * (arcRadiusPx * 1.15f / viewport.pixelsPerModelUnit.coerceAtLeast(0.05f))
            )
            drawLine(
                color = arcColor.copy(alpha = 0.45f),
                start = baseScreen,
                end = viewport.modelToScreen(refTipModel),
                strokeWidth = 1.1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 5f), 0f)
            )

            // Arc Angle Label (e.g., "90°", "126°", "180°")
            val midModelDeg = polarPreview.referenceAngleDeg + polarPreview.activePolarAngleDeg * 0.5
            val midRad = Math.toRadians(midModelDeg)
            val labelRadiusPx = arcRadiusPx + 16f
            val labelX = baseScreen.x + cos(midRad).toFloat() * labelRadiusPx
            val labelY = baseScreen.y - sin(midRad).toFloat() * labelRadiusPx
            val formattedPolarAngle = formatPolarAngleCompact(polarPreview.activePolarAngleDeg)

            drawContext.canvas.nativeCanvas.apply {
                textPaint.textSize = 21f
                textPaint.color = android.graphics.Color.rgb(251, 191, 36)
                textPaint.textAlign = Paint.Align.CENTER
                drawText("${formattedPolarAngle}°", labelX, labelY + 7f, textPaint)
            }
        }
    }

    // 3. Read-Only Live Cursor Readout Badge (R, θ, θₚ) — Section 8.6 & 8.7
    if (!polarPreview.hasValidDirection) return

    val distanceText = precision.formatLinear(
        rawModelValue = polarPreview.radialDistanceR,
        storageUnit = storageUnit,
        displayUnit = displayUnit
    )
    val actualThetaText = String.format(Locale.ROOT, "%.1f°", polarPreview.actualAngleThetaDeg ?: 0.0)
    val polarBadgeSuffix = if (polarPreview.isPolarGuideAcquired && polarPreview.activePolarAngleDeg != null) {
        "  θₚ ${formatPolarAngleCompact(polarPreview.activePolarAngleDeg)}° (${polarPreview.referenceBasisLabel})"
    } else {
        ""
    }
    val readoutText = "R $distanceText  θ $actualThetaText$polarBadgeSuffix"

    drawContext.canvas.nativeCanvas.apply {
        textPaint.textSize = 20f
        textPaint.textAlign = Paint.Align.LEFT
        val textWidth = textPaint.measureText(readoutText)
        val padH = 10f
        val padV = 6f
        val boxW = textWidth + padH * 2f
        val boxH = 28f

        // Quadrant-shift badge away from crosshair & viewport boundaries
        val shiftLeft = cursorScreen.x + 26f + boxW > size.width - 12f
        val shiftDown = cursorScreen.y - 24f - boxH < 52f
        val boxLeft = if (shiftLeft) {
            (cursorScreen.x - 26f - boxW).coerceAtLeast(8f)
        } else {
            (cursorScreen.x + 26f).coerceAtMost((size.width - boxW - 8f).coerceAtLeast(8f))
        }
        val boxTop = if (shiftDown) {
            (cursorScreen.y + 24f).coerceAtMost((size.height - boxH - 8f).coerceAtLeast(8f))
        } else {
            (cursorScreen.y - 24f - boxH).coerceAtLeast(8f)
        }

        drawRoundRect(
            color = Color(0xD90A0F1A),
            topLeft = Offset(boxLeft, boxTop),
            size = Size(boxW, boxH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
        )
        drawRoundRect(
            color = if (polarPreview.isPolarGuideAcquired) polarColor.copy(alpha = 0.85f) else Color(0xFF475569),
            topLeft = Offset(boxLeft, boxTop),
            size = Size(boxW, boxH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f),
            style = Stroke(width = 1.2f)
        )

        textPaint.color = if (polarPreview.isPolarGuideAcquired) {
            android.graphics.Color.rgb(186, 230, 253)
        } else {
            android.graphics.Color.rgb(226, 232, 240)
        }
        drawText(readoutText, boxLeft + padH, boxTop + boxH - padV - 2f, textPaint)
        textPaint.textAlign = Paint.Align.CENTER
    }
}

private fun formatPolarAngleCompact(angleDeg: Double): String {
    val roundedInt = angleDeg.roundToInt()
    return if (abs(angleDeg - roundedInt.toDouble()) < 0.05) {
        roundedInt.toString()
    } else {
        String.format(Locale.ROOT, "%.1f", angleDeg)
    }
}

private fun toAndroidColor(c: Color): Int = android.graphics.Color.argb(
    (c.alpha * 255).toInt(),
    (c.red * 255).toInt(),
    (c.green * 255).toInt(),
    (c.blue * 255).toInt()
)
