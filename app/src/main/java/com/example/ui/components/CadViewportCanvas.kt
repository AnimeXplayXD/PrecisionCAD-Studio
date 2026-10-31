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
import com.example.cad.dimension.AdaptiveAnnotationRenderer
import com.example.cad.dimension.DimensionStyleConfig
import com.example.cad.kernel.CadGeometryKernel
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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Section 14.1 & 14.7 — Authoritative Viewport Gesture State Machine.
 * The CAD sheet is strictly navigation-only:
 * - 1 pointer (`ONE_POINTER_SUPPRESSED`): suppressed; never picks points, draws, or selects entities.
 * - 2 pointers (`TWO_POINTER_TRANSFORM`): two-finger pinch-to-zoom and two-finger pan around centroid.
 * - If a 2-pointer gesture drops to 1 pointer, it returns to `ONE_POINTER_SUPPRESSED` until all pointers lift.
 */
enum class ViewportGestureState {
    IDLE,
    ONE_POINTER_SUPPRESSED,
    TWO_POINTER_TRANSFORM,
    CANCELLED
}

object ViewportGesturePolicy {
    fun resolveState(
        pressedPointerCount: Int,
        isCancelled: Boolean = false
    ): ViewportGestureState = when {
        isCancelled -> ViewportGestureState.CANCELLED
        pressedPointerCount <= 0 -> ViewportGestureState.IDLE
        pressedPointerCount == 1 -> ViewportGestureState.ONE_POINTER_SUPPRESSED
        pressedPointerCount == 2 -> ViewportGestureState.TWO_POINTER_TRANSFORM
        else -> ViewportGestureState.CANCELLED
    }

    fun transitionState(
        currentState: ViewportGestureState,
        activePointerCount: Int
    ): ViewportGestureState {
        if (activePointerCount <= 0) return ViewportGestureState.IDLE
        if (currentState == ViewportGestureState.CANCELLED) return ViewportGestureState.CANCELLED
        return when (activePointerCount) {
            1 -> ViewportGestureState.ONE_POINTER_SUPPRESSED
            2 -> ViewportGestureState.TWO_POINTER_TRANSFORM
            else -> ViewportGestureState.CANCELLED
        }
    }

    fun applyTwoFingerTransform(
        viewport: ViewportTransform,
        centroidScreen: Offset,
        panDeltaScreen: Offset,
        zoomFactor: Float
    ): ViewportTransform {
        var updated = viewport
        if (centroidScreen != Offset.Unspecified && zoomFactor.isFinite() && abs(zoomFactor - 1f) > 0.001f) {
            updated = updated.zoomAroundScreenPoint(centroidScreen, zoomFactor)
        }
        if (panDeltaScreen != Offset.Zero && panDeltaScreen.x.isFinite() && panDeltaScreen.y.isFinite()) {
            updated = updated.copy(
                panScreenX = updated.panScreenX + panDeltaScreen.x,
                panScreenY = updated.panScreenY + panDeltaScreen.y
            )
        }
        return updated
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
 * Pure 2D Technical CAD Viewport (Sections 5 & 14 of Specification).
 * Accepts ONLY two-finger pinch-to-zoom and two-finger pan gestures on the drawing surface.
 * All single-finger touches on the drawing surface are suppressed so the user never triggers
 * accidental point picks, direct drawing, or selection drags with a palm or single finger.
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
    onGestureStateChanged: (ViewportGestureState) -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onDirectTapModelPoint: (Point2D) -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onSelectionDragStart: (Point2D) -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onSelectionDragUpdate: (Point2D) -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onSelectionDragEnd: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val layerMap = remember(layers) { layers.associateBy { it.name } }
    val currentViewport by rememberUpdatedState(viewport)
    val currentOnViewportChanged by rememberUpdatedState(onViewportChanged)
    val currentOnGestureStateChanged by rememberUpdatedState(onGestureStateChanged)

    val textPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.MONOSPACE
            textAlign = Paint.Align.CENTER
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                onViewportSizeChanged(size.width.toFloat(), size.height.toFloat())
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var gestureState = ViewportGestureState.ONE_POINTER_SUPPRESSED
                    currentOnGestureStateChanged(gestureState)

                    do {
                        val event = awaitPointerEvent()
                        val canceled = event.changes.any { it.isConsumed }
                        val pressedChanges = event.changes.filter { it.pressed }
                        val pointerCount = pressedChanges.size

                        val nextState = ViewportGesturePolicy.resolveState(
                            pressedPointerCount = pointerCount,
                            isCancelled = canceled
                        )
                        if (nextState != gestureState) {
                            gestureState = nextState
                            currentOnGestureStateChanged(gestureState)
                        }

                        if (gestureState == ViewportGestureState.TWO_POINTER_TRANSFORM) {
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            val centroid = event.calculateCentroid(useCurrent = false)
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
                                if (change.positionChanged()) {
                                    change.consume()
                                }
                            }
                        } else {
                            // Suppress all 1-finger taps, drags, and long-presses on the sheet
                            event.changes.forEach { change ->
                                if (change.positionChanged()) {
                                    change.consume()
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

            // 2. Paper Space Sheet Frame & Title Block
            if (workspaceSpace == WorkspaceSpace.PAPER_SPACE) {
                drawPaperSpaceSheet(sheetSetup, displayUnit, viewport, textPaint)
            }

            // 3. Adaptive Grid & Origin Axes
            if (gridSnapConfig.showGrid) {
                drawAdaptiveGrid(gridSnapConfig, viewport)
            }
            drawOriginAxes(viewport)

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

            // 6. Rubber-Band Tracking Line from Reference Point to Cursor
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

private fun DrawScope.drawPaperSpaceSheet(
    sheetSetup: SheetSetupConfig,
    displayUnit: CadUnit,
    viewport: ViewportTransform,
    textPaint: Paint
) {
    val scaleInv = (1.0 / sheetSetup.plotScaleRatio).coerceIn(0.05, 50.0)
    val sheetWModel = sheetSetup.effectiveWidthMm * scaleInv
    val sheetHModel = sheetSetup.effectiveHeightMm * scaleInv
    val marginModel = sheetSetup.marginMm * scaleInv

    val pTopLeft = viewport.modelToScreen(Point2D(0.0, sheetHModel))
    val pBottomRight = viewport.modelToScreen(Point2D(sheetWModel, 0.0))
    val sheetRect = Rect(pTopLeft.x, pTopLeft.y, pBottomRight.x, pBottomRight.y)

    drawRect(
        color = CadSheetPaperFill,
        topLeft = sheetRect.topLeft,
        size = sheetRect.size
    )
    drawRect(
        color = Color(0xFF64748B),
        topLeft = sheetRect.topLeft,
        size = sheetRect.size,
        style = Stroke(width = 2f)
    )

    // Printable Margin Frame
    val mTopLeft = viewport.modelToScreen(Point2D(marginModel, sheetHModel - marginModel))
    val mBottomRight = viewport.modelToScreen(Point2D(sheetWModel - marginModel, marginModel))
    drawRect(
        color = Color(0xFF94A3B8),
        topLeft = mTopLeft,
        size = Size(mBottomRight.x - mTopLeft.x, mBottomRight.y - mTopLeft.y),
        style = Stroke(width = 1.4f)
    )

    // Engineering Title Block in bottom-right corner of printable frame
    if (sheetSetup.showTitleBlock) {
        val tbWModel = sheetWModel * 0.36
        val tbHModel = sheetHModel * 0.14
        val tbTopLeft = viewport.modelToScreen(
            Point2D(sheetWModel - marginModel - tbWModel, marginModel + tbHModel)
        )
        val tbSize = Size(
            width = mBottomRight.x - tbTopLeft.x,
            height = mBottomRight.y - tbTopLeft.y
        )
        drawRect(
            color = Color(0xFF141D2B),
            topLeft = tbTopLeft,
            size = tbSize
        )
        drawRect(
            color = Color(0xFF94A3B8),
            topLeft = tbTopLeft,
            size = tbSize,
            style = Stroke(width = 1.5f)
        )
        drawContext.canvas.nativeCanvas.apply {
            textPaint.color = android.graphics.Color.rgb(226, 232, 240)
            textPaint.textSize = (tbSize.height * 0.20f).coerceIn(9f, 18f)
            textPaint.textAlign = Paint.Align.LEFT
            drawText(
                sheetSetup.titleBlockTitle,
                tbTopLeft.x + 10f,
                tbTopLeft.y + tbSize.height * 0.32f,
                textPaint
            )
            textPaint.textSize = (tbSize.height * 0.16f).coerceIn(8f, 14f)
            textPaint.color = android.graphics.Color.rgb(148, 163, 184)
            drawText(
                "${sheetSetup.titleBlockDrawingNo} • ${sheetSetup.titleBlockRevision} • SCALE ${sheetSetup.scaleLabel}",
                tbTopLeft.x + 10f,
                tbTopLeft.y + tbSize.height * 0.62f,
                textPaint
            )
            drawText(
                "${sheetSetup.preset.label} • UNITS: ${displayUnit.symbol} • ${sheetSetup.draftingStandard.name}",
                tbTopLeft.x + 10f,
                tbTopLeft.y + tbSize.height * 0.88f,
                textPaint
            )
            textPaint.textAlign = Paint.Align.CENTER
        }
    }
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
            drawContext.canvas.nativeCanvas.apply {
                save()
                translate(s.x, s.y)
                rotate(-entity.rotationDeg.toFloat())
                textPaint.color = toAndroidColor(baseColor)
                textPaint.textSize = fontSizePx
                textPaint.textAlign = Paint.Align.LEFT
                entity.text.lines().forEachIndexed { idx, line ->
                    drawText(line, 0f, idx * (fontSizePx * 1.2f), textPaint)
                }
                textPaint.textAlign = Paint.Align.CENTER
                restore()
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
            drawContext.canvas.nativeCanvas.apply {
                save()
                translate(shelfMidX, labelY)
                textPaint.textSize = fontSizePx
                textPaint.textAlign = Paint.Align.CENTER
                val textW = textPaint.measureText(entity.text)
                val bgPaint = Paint().apply {
                    this.color = android.graphics.Color.argb(215, 10, 14, 20)
                    this.style = Paint.Style.FILL
                }
                drawRect(-textW * 0.54f, -fontSizePx * 0.95f, textW * 0.54f, fontSizePx * 0.22f, bgPaint)
                textPaint.color = toAndroidColor(baseColor)
                drawText(entity.text, 0f, 0f, textPaint)
                restore()
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
        drawContext.canvas.nativeCanvas.apply {
            save()
            translate(shelfMidX, labelY)
            textPaint.textSize = fontSizePx
            val textW = textPaint.measureText(plan.labelText)
            val bgPaint = Paint().apply {
                this.color = android.graphics.Color.argb(220, 10, 14, 20)
                this.style = Paint.Style.FILL
            }
            drawRect(-textW * 0.54f, -fontSizePx * 0.96f, textW * 0.54f, fontSizePx * 0.22f, bgPaint)
            textPaint.color = toAndroidColor(color)
            drawText(plan.labelText, 0f, 0f, textPaint)
            restore()
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
        drawContext.canvas.nativeCanvas.apply {
            save()
            translate(textScreen.x, textScreen.y)
            textPaint.textSize = fontSizePx
            val textW = textPaint.measureText(plan.labelText)
            val bgPaint = Paint().apply {
                this.color = android.graphics.Color.argb(215, 10, 14, 20)
                this.style = Paint.Style.FILL
            }
            drawRect(-textW * 0.55f, -fontSizePx * 0.95f, textW * 0.55f, fontSizePx * 0.25f, bgPaint)
            textPaint.color = toAndroidColor(color)
            drawText(plan.labelText, 0f, 0f, textPaint)
            restore()
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

    // Dimension text with subtle background pill for maximum blueprint legibility
    val textScreen = viewport.modelToScreen(plan.textPositionModel)
    drawContext.canvas.nativeCanvas.apply {
        save()
        translate(textScreen.x, textScreen.y)
        rotate(plan.textRotationDeg)
        textPaint.textSize = fontSizePx
        val textW = textPaint.measureText(plan.labelText)
        val bgPaint = Paint().apply {
            this.color = android.graphics.Color.argb(215, 10, 14, 20)
            this.style = Paint.Style.FILL
        }
        drawRect(-textW * 0.55f, -fontSizePx * 0.95f, textW * 0.55f, fontSizePx * 0.25f, bgPaint)
        textPaint.color = toAndroidColor(color)
        drawText(plan.labelText, 0f, 0f, textPaint)
        restore()
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

private fun toAndroidColor(c: Color): Int = android.graphics.Color.argb(
    (c.alpha * 255).toInt(),
    (c.red * 255).toInt(),
    (c.green * 255).toInt(),
    (c.blue * 255).toInt()
)
