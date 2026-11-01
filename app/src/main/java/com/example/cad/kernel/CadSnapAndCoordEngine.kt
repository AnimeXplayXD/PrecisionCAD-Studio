package com.example.cad.kernel

import androidx.compose.ui.geometry.Offset
import com.example.cad.model.BoundingBox2D
import com.example.cad.model.CadEntity
import com.example.cad.model.GridAndSnapConfig
import com.example.cad.model.OsnapMode
import com.example.cad.model.Point2D
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToLong
import kotlin.math.sin

/**
 * Stable screen-space dimensions of the underlying CAD viewport surface.
 * Used for model-to-screen mapping and culling; never changes when floating overlays open or close.
 */
data class ViewportSurfaceRect(
    val widthPx: Float = 800f,
    val heightPx: Float = 600f
) {
    val safeWidthPx: Float get() = widthPx.coerceAtLeast(1f)
    val safeHeightPx: Float get() = heightPx.coerceAtLeast(1f)
    val centerX: Float get() = safeWidthPx * 0.5f
    val centerY: Float get() = safeHeightPx * 0.5f
}

/**
 * Screen-space rectangle representing the unobstructed interaction-safe CAD viewport area
 * (excluding top header bar, floating HUD badges, expanded tool drawers, and controller dock).
 * Used for automatic cursor-boundary tracking and explicit Fit Drawing commands.
 */
data class UsableViewportRect(
    val leftPx: Float = 0f,
    val topPx: Float = 0f,
    val rightPx: Float = 800f,
    val bottomPx: Float = 600f
) {
    val widthPx: Float get() = (rightPx - leftPx).coerceAtLeast(1f)
    val heightPx: Float get() = (bottomPx - topPx).coerceAtLeast(1f)
    val centerX: Float get() = (leftPx + rightPx) * 0.5f
    val centerY: Float get() = (topPx + bottomPx) * 0.5f
}

enum class FitDrawingPolicy(val label: String) {
    ALL_VISIBLE("Fit All Visible (Geometry + Annotations)"),
    SELECTED_ONLY("Fit Selected Entities"),
    GEOMETRY_ONLY("Fit Geometry Only (Ignore Dimensions)")
}

data class FitDrawingResult(
    val fittedViewport: ViewportTransform,
    val fittedBounds: BoundingBox2D,
    val includedEntityCount: Int,
    val policy: FitDrawingPolicy,
    val isEmptyFallback: Boolean,
    val summaryMessage: String
)

/**
 * Separates model-space geometry from screen-space viewport presentation.
 * Model-space Y points UP (+Y is North); Screen-space Y points DOWN.
 */
data class ViewportTransform(
    val panScreenX: Float = 220f,
    val panScreenY: Float = 520f,
    val pixelsPerModelUnit: Float = 3.2f
) {
    companion object {
        const val MIN_PIXELS_PER_UNIT = 0.05f
        const val MAX_PIXELS_PER_UNIT = 500f
    }

    fun modelToScreen(pt: Point2D): Offset = Offset(
        x = panScreenX + (pt.x * pixelsPerModelUnit).toFloat(),
        y = panScreenY - (pt.y * pixelsPerModelUnit).toFloat()
    )

    fun screenToModel(screenOffset: Offset): Point2D {
        val safeScale = pixelsPerModelUnit.coerceAtLeast(0.001f)
        return Point2D(
            x = ((screenOffset.x - panScreenX) / safeScale).toDouble(),
            y = ((panScreenY - screenOffset.y) / safeScale).toDouble()
        )
    }

    fun panByScreenDelta(dxPx: Float, dyPx: Float): ViewportTransform {
        if (!dxPx.isFinite() || !dyPx.isFinite()) return this
        if (dxPx == 0f && dyPx == 0f) return this
        return copy(
            panScreenX = panScreenX + dxPx,
            panScreenY = panScreenY + dyPx
        )
    }

    fun zoomAroundScreenPoint(pivotScreen: Offset, zoomMultiplier: Float): ViewportTransform {
        if (!zoomMultiplier.isFinite() || zoomMultiplier <= 0f) return this
        val modelAtPivot = screenToModel(pivotScreen)
        val newScale = (pixelsPerModelUnit * zoomMultiplier).coerceIn(MIN_PIXELS_PER_UNIT, MAX_PIXELS_PER_UNIT)
        val newPanX = pivotScreen.x - (modelAtPivot.x * newScale).toFloat()
        val newPanY = pivotScreen.y + (modelAtPivot.y * newScale).toFloat()
        if (!newPanX.isFinite() || !newPanY.isFinite()) return this
        return copy(
            panScreenX = newPanX,
            panScreenY = newPanY,
            pixelsPerModelUnit = newScale
        )
    }

    /**
     * Computes the model-space axis-aligned bounding box currently visible in [canvasWidthPx] x [canvasHeightPx],
     * expanded by [overscanPx] in screen space for smooth viewport culling.
     */
    fun visibleModelBounds(
        canvasWidthPx: Float,
        canvasHeightPx: Float,
        overscanPx: Float = 48f
    ): BoundingBox2D {
        val pTopLeft = screenToModel(Offset(-overscanPx, -overscanPx))
        val pBottomRight = screenToModel(Offset(canvasWidthPx + overscanPx, canvasHeightPx + overscanPx))
        return BoundingBox2D(
            minX = minOf(pTopLeft.x, pBottomRight.x),
            minY = minOf(pTopLeft.y, pBottomRight.y),
            maxX = maxOf(pTopLeft.x, pBottomRight.x),
            maxY = maxOf(pTopLeft.y, pBottomRight.y)
        )
    }

    /**
     * Fits [bounds] into the unobstructed [usableRect] with [marginPx] screen-space padding on each side.
     * Handles single points and zero-width / zero-height lines without distorting aspect ratio.
     */
    fun fitToUsableViewport(
        bounds: BoundingBox2D,
        usableRect: UsableViewportRect,
        marginPx: Float = 24f
    ): ViewportTransform {
        val availW = (usableRect.widthPx - 2f * marginPx).coerceAtLeast(64f)
        val availH = (usableRect.heightPx - 2f * marginPx).coerceAtLeast(64f)

        val rawW = bounds.width
        val rawH = bounds.height

        // Handle degenerate single-point or strictly horizontal/vertical line bounds gracefully
        val minSpan = maxOf(rawW, rawH, 1.0) * 0.12
        val effectiveW = if (rawW < 1e-4) minSpan.coerceAtLeast(4.0) else rawW
        val effectiveH = if (rawH < 1e-4) minSpan.coerceAtLeast(4.0) else rawH

        val scaleX = availW / effectiveW.toFloat()
        val scaleY = availH / effectiveH.toFloat()
        val newScale = minOf(scaleX, scaleY).coerceIn(MIN_PIXELS_PER_UNIT, MAX_PIXELS_PER_UNIT)

        val center = bounds.center
        val newPanX = usableRect.centerX - (center.x * newScale).toFloat()
        val newPanY = usableRect.centerY + (center.y * newScale).toFloat()
        return ViewportTransform(
            panScreenX = newPanX,
            panScreenY = newPanY,
            pixelsPerModelUnit = newScale
        )
    }

    fun fitToBounds(
        bounds: BoundingBox2D,
        viewportWidthPx: Float,
        viewportHeightPx: Float,
        paddingFactor: Float = 0.84f
    ): ViewportTransform {
        val safeW = viewportWidthPx.coerceAtLeast(200f)
        val safeH = viewportHeightPx.coerceAtLeast(200f)
        val marginX = safeW * (1f - paddingFactor.coerceIn(0.5f, 0.96f)) * 0.5f
        val marginY = safeH * (1f - paddingFactor.coerceIn(0.5f, 0.96f)) * 0.5f
        val margin = minOf(marginX, marginY).coerceAtLeast(16f)
        return fitToUsableViewport(
            bounds = bounds,
            usableRect = UsableViewportRect(0f, 0f, safeW, safeH),
            marginPx = margin
        )
    }

    /**
     * Preserves the model-space point at the visual center when usable viewport bounds shift
     * (e.g., when a transient notification banner, command bar, or controller dock expands/collapses).
     */
    fun rebaseForUsableRectChange(
        oldRect: UsableViewportRect,
        newRect: UsableViewportRect
    ): ViewportTransform {
        val anchorModel = screenToModel(Offset(oldRect.centerX, oldRect.centerY))
        val newPanX = newRect.centerX - (anchorModel.x * pixelsPerModelUnit).toFloat()
        val newPanY = newRect.centerY + (anchorModel.y * pixelsPerModelUnit).toFloat()
        return copy(panScreenX = newPanX, panScreenY = newPanY)
    }

    /**
     * Section 17 — Cursor-Boundary Visibility & Automatic Viewport Tracking.
     * Ensures that when the virtual cursor moves via controller or coordinate entry beyond the safe inner
     * boundary of [usableRect], the viewport smoothly pans (and gradually zooms out only if required for
     * extreme coordinate jumps) so the cursor remains clearly visible with margin, without altering
     * the cursor's model-space coordinates.
     */
    fun trackCursorIntoSafeBoundary(
        cursorModelPos: Point2D,
        usableRect: UsableViewportRect,
        referencePoint: Point2D? = null,
        safeMarginFraction: Float = 0.12f,
        minSafeMarginPx: Float = 36f
    ): ViewportTransform {
        if (!cursorModelPos.x.isFinite() || !cursorModelPos.y.isFinite()) return this
        val marginX = maxOf(usableRect.widthPx * safeMarginFraction, minSafeMarginPx)
            .coerceAtMost(usableRect.widthPx * 0.35f)
        val marginY = maxOf(usableRect.heightPx * safeMarginFraction, minSafeMarginPx)
            .coerceAtMost(usableRect.heightPx * 0.35f)

        val safeLeft = usableRect.leftPx + marginX
        val safeRight = (usableRect.rightPx - marginX).coerceAtLeast(safeLeft + 1f)
        val safeTop = usableRect.topPx + marginY
        val safeBottom = (usableRect.bottomPx - marginY).coerceAtLeast(safeTop + 1f)

        var working = this

        // If an active command has a reference point and the span from referencePoint to cursorModelPos
        // is vastly larger than the usable viewport (e.g., > 2.5x usable span via direct coordinate entry),
        // apply a controlled, bounded zoom-out so both context and cursor remain comprehensible.
        if (referencePoint != null) {
            val refScreen = working.modelToScreen(referencePoint)
            val curScreen = working.modelToScreen(cursorModelPos)
            val spanX = abs(curScreen.x - refScreen.x)
            val spanY = abs(curScreen.y - refScreen.y)
            val maxAllowedSpanX = (safeRight - safeLeft) * 2.2f
            val maxAllowedSpanY = (safeBottom - safeTop) * 2.2f
            if (spanX > maxAllowedSpanX || spanY > maxAllowedSpanY) {
                val factorX = if (spanX > maxAllowedSpanX) maxAllowedSpanX / spanX else 1f
                val factorY = if (spanY > maxAllowedSpanY) maxAllowedSpanY / spanY else 1f
                val zoomOutFactor = minOf(factorX, factorY).coerceIn(0.35f, 0.92f)
                val pivot = Offset(usableRect.centerX, usableRect.centerY)
                working = working.zoomAroundScreenPoint(pivot, zoomOutFactor)
            }
        }

        val screenPos = working.modelToScreen(cursorModelPos)
        var deltaPanX = 0f
        var deltaPanY = 0f

        if (screenPos.x < safeLeft) {
            deltaPanX = safeLeft - screenPos.x
        } else if (screenPos.x > safeRight) {
            deltaPanX = safeRight - screenPos.x
        }

        if (screenPos.y < safeTop) {
            deltaPanY = safeTop - screenPos.y
        } else if (screenPos.y > safeBottom) {
            deltaPanY = safeBottom - screenPos.y
        }

        if (abs(deltaPanX) < 0.5f && abs(deltaPanY) < 0.5f) {
            return working
        }

        return working.copy(
            panScreenX = working.panScreenX + deltaPanX,
            panScreenY = working.panScreenY + deltaPanY
        )
    }
}

data class SnapCandidate(
    val point: Point2D,
    val mode: OsnapMode,
    val sourceEntityId: String? = null
)

/**
 * Sections 9.4–9.7 — Read-Only Visual Polar Tracking & Independent PolarSnap State.
 *
 * - [basePoint]: Accepted command base point B = (Bx, By).
 * - [rawCursorPoint]: Live raw virtual cursor position P = (Px, Py).
 * - [radialDistanceR]: Live radial distance R = hypot(Px - Bx, Py - By) in model units.
 * - [actualAngleThetaDeg]: Live unrounded Cursor Bearing θ in [0, 360) degrees (null if R is degenerate ~ 0).
 * - [referenceAngleDeg]: Reference zero-angle direction (0° absolute +X, or previous segment angle when relative mode is active).
 * - [referenceBasisLabel]: Explicit reference basis label ("+X" or "Last Segment").
 * - [activePolarAngleDeg]: Active Track Bearing θp in [0, 360) degrees when polarTrackingEnabled is true (supports all 360 whole-number bearings 0°–359° plus custom fractional visual angles).
 * - [worldPolarAngleDeg]: Active visual polar direction in absolute world degrees [0, 360) for guide ray rendering.
 * - [activePolarSnapAngleDeg]: Active PolarSnap ray angle in [0, 360) when within tolerance of a selected PolarSnap preset/custom angle.
 * - [projectedPolarPoint]: Point B + R * (cos θ_snap, sin θ_snap) along the nearest active PolarSnap ray (used to project candidate ONLY when polarSnapEnabled is true).
 */
data class PolarTrackingPreviewState(
    val basePoint: Point2D,
    val rawCursorPoint: Point2D,
    val radialDistanceR: Double,
    val actualAngleThetaDeg: Double?,
    val referenceAngleDeg: Double = 0.0,
    val referenceBasisLabel: String = "+X",
    val activePolarAngleDeg: Double? = null,
    val worldPolarAngleDeg: Double? = null,
    val activePolarSnapAngleDeg: Double? = null,
    val projectedPolarPoint: Point2D? = null
) {
    val hasValidDirection: Boolean get() = actualAngleThetaDeg != null && radialDistanceR > 1e-5
    val isPolarGuideAcquired: Boolean get() = activePolarAngleDeg != null && worldPolarAngleDeg != null && hasValidDirection
    val isPolarSnapRayAcquired: Boolean get() = activePolarSnapAngleDeg != null && projectedPolarPoint != null && hasValidDirection
}

object CadSnapAndCoordEngine {

    fun normalizeAngle360(deg: Double): Double {
        if (!deg.isFinite()) return 0.0
        val mod = deg % 360.0
        val pos = if (mod < 0.0) mod + 360.0 else mod
        return if (abs(pos - 360.0) < 1e-7) 0.0 else pos
    }

    fun wrappedAngularDiffDeg(a: Double, b: Double): Double {
        val d = abs(normalizeAngle360(a) - normalizeAngle360(b)) % 360.0
        return if (d > 180.0) 360.0 - d else d
    }

    /**
     * Section 9.5 — Builds the complete Visual Polar Tracking angle set:
     * all 360 whole-number bearings (0°..359°) plus custom fractional visual angles.
     */
    fun buildVisualPolarGuideAngles(snapConfig: GridAndSnapConfig): List<Double> {
        val angles = ArrayList<Double>(360 + snapConfig.polarAdditionalAnglesDeg.size)
        for (whole in 0..359) {
            angles.add(whole.toDouble())
        }
        for (custom in snapConfig.polarAdditionalAnglesDeg) {
            if (custom.isFinite()) {
                val norm = normalizeAngle360(custom)
                if (angles.none { abs(it - norm) < 1e-4 }) {
                    angles.add(norm)
                }
            }
        }
        return angles.sorted()
    }

    /**
     * Section 9.6 — Builds the independent PolarSnap angle collection from the multi-select
     * PolarSnap presets (`polarSnapPresetAnglesDeg`) plus custom PolarSnap angles (`polarSnapCustomAnglesDeg`).
     */
    fun buildPolarSnapAngles(snapConfig: GridAndSnapConfig): List<Double> {
        val angles = mutableListOf<Double>()
        val presets = if (snapConfig.polarSnapPresetAnglesDeg.isNotEmpty()) {
            snapConfig.polarSnapPresetAnglesDeg
        } else {
            setOf(snapConfig.polarTrackingAngleDeg.coerceIn(1.0, 180.0))
        }
        for (incRaw in presets) {
            val inc = incRaw.coerceIn(1.0, 180.0)
            var deg = 0.0
            while (deg < 360.0 - 1e-5) {
                val norm = normalizeAngle360(deg)
                if (angles.none { abs(it - norm) < 1e-4 }) {
                    angles.add(norm)
                }
                deg += inc
            }
        }
        for (custom in snapConfig.polarSnapCustomAnglesDeg + snapConfig.polarAdditionalAnglesDeg) {
            if (custom.isFinite()) {
                val norm = normalizeAngle360(custom)
                if (angles.none { abs(it - norm) < 1e-4 }) {
                    angles.add(norm)
                }
            }
        }
        return angles.sorted()
    }

    /**
     * Sections 9.4–9.7: Computes the live read-only Polar Tracking preview (R, Cursor Bearing θ,
     * Track Bearing θp, reference basis, and independent PolarSnap projection) from the accepted
     * command [basePoint] to [rawCursorPoint] in model space.
     * Returns null when [basePoint] is null.
     */
    fun computePolarTrackingPreview(
        basePoint: Point2D?,
        rawCursorPoint: Point2D,
        snapConfig: GridAndSnapConfig,
        previousBasePoint: Point2D? = null
    ): PolarTrackingPreviewState? {
        if (basePoint == null) return null
        val dx = rawCursorPoint.x - basePoint.x
        val dy = rawCursorPoint.y - basePoint.y
        val r = hypot(dx, dy)

        val useLastSegment = snapConfig.polarRelativeToLastSegment &&
            previousBasePoint != null &&
            previousBasePoint.distanceTo(basePoint) > 1e-5
        val refDeg = if (useLastSegment && previousBasePoint != null) {
            normalizeAngle360(previousBasePoint.angleToDeg(basePoint))
        } else {
            0.0
        }
        val basisLabel = if (useLastSegment) "Last Segment" else "+X"

        if (!r.isFinite() || r <= 1e-5) {
            return PolarTrackingPreviewState(
                basePoint = basePoint,
                rawCursorPoint = rawCursorPoint,
                radialDistanceR = 0.0,
                actualAngleThetaDeg = null,
                referenceAngleDeg = refDeg,
                referenceBasisLabel = basisLabel,
                activePolarAngleDeg = null,
                worldPolarAngleDeg = null,
                activePolarSnapAngleDeg = null,
                projectedPolarPoint = null
            )
        }

        val rawWorldDeg = normalizeAngle360(Math.toDegrees(kotlin.math.atan2(dy, dx)))
        val relativeThetaDeg = normalizeAngle360(rawWorldDeg - refDeg)
        val tolerance = snapConfig.polarAcquisitionToleranceDeg.coerceIn(0.5, 45.0)

        // Evaluate independent PolarSnap ray acquisition against PolarSnap multi-select presets + custom angles
        val polarSnapCandidates = buildPolarSnapAngles(snapConfig)
        val bestSnapDeg = polarSnapCandidates.minByOrNull { cand ->
            wrappedAngularDiffDeg(relativeThetaDeg, cand)
        }
        val snapDiff = if (bestSnapDeg != null) wrappedAngularDiffDeg(relativeThetaDeg, bestSnapDeg) else Double.MAX_VALUE
        val acquiredSnapDeg = if (bestSnapDeg != null && snapDiff <= tolerance) bestSnapDeg else null
        val projectedSnapPt = if (acquiredSnapDeg != null) {
            basePoint.polarOffset(r, normalizeAngle360(refDeg + acquiredSnapDeg))
        } else {
            null
        }

        if (!snapConfig.polarTrackingEnabled) {
            return PolarTrackingPreviewState(
                basePoint = basePoint,
                rawCursorPoint = rawCursorPoint,
                radialDistanceR = r,
                actualAngleThetaDeg = relativeThetaDeg,
                referenceAngleDeg = refDeg,
                referenceBasisLabel = basisLabel,
                activePolarAngleDeg = null,
                worldPolarAngleDeg = null,
                activePolarSnapAngleDeg = acquiredSnapDeg,
                projectedPolarPoint = projectedSnapPt
            )
        }

        // Visual Polar Tracking (Section 9.5): all 360 whole-number bearings (0°..359°) + custom fractional angles.
        // Priority:
        // 1. Custom fractional angle in polarAdditionalAnglesDeg within 0.45°
        // 2. Acquired PolarSnap / major increment ray within tolerance
        // 3. Nearest whole-number bearing in 0°..359° (or custom fractional angle)
        val fractionalCustom = snapConfig.polarAdditionalAnglesDeg
            .filter { it.isFinite() }
            .map { normalizeAngle360(it) }
        val nearFractional = fractionalCustom
            .filter { abs(it - kotlin.math.round(it)) > 1e-3 }
            .minByOrNull { wrappedAngularDiffDeg(relativeThetaDeg, it) }
            ?.takeIf { wrappedAngularDiffDeg(relativeThetaDeg, it) <= 0.48 }

        val visualGuideDeg: Double = when {
            nearFractional != null -> nearFractional
            acquiredSnapDeg != null -> acquiredSnapDeg
            else -> {
                val allVisual = buildVisualPolarGuideAngles(snapConfig)
                allVisual.minByOrNull { wrappedAngularDiffDeg(relativeThetaDeg, it) }
                    ?: normalizeAngle360(kotlin.math.round(relativeThetaDeg))
            }
        }

        val worldPolar = normalizeAngle360(refDeg + visualGuideDeg)
        return PolarTrackingPreviewState(
            basePoint = basePoint,
            rawCursorPoint = rawCursorPoint,
            radialDistanceR = r,
            actualAngleThetaDeg = relativeThetaDeg,
            referenceAngleDeg = refDeg,
            referenceBasisLabel = basisLabel,
            activePolarAngleDeg = visualGuideDeg,
            worldPolarAngleDeg = worldPolar,
            activePolarSnapAngleDeg = acquiredSnapDeg,
            projectedPolarPoint = projectedSnapPt
        )
    }

    /**
     * Parses direct numeric coordinate expressions:
     * - Cartesian Absolute: `125,85` or `125.5, 85.2`
     * - Cartesian Relative: `@25,-10`
     * - Polar Absolute: `50<45`
     * - Polar Relative: `@30<90`
     * - Scalar distance along active cursor/ortho direction: `40`
     */
    fun parseCoordinateInput(
        rawInput: String,
        referencePoint: Point2D?,
        currentCursor: Point2D,
        orthoEnabled: Boolean
    ): Point2D? {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return null

        val isRelative = trimmed.startsWith("@")
        val body = if (isRelative) trimmed.removePrefix("@").trim() else trimmed
        val base = if (isRelative) (referencePoint ?: Point2D(0.0, 0.0)) else Point2D(0.0, 0.0)

        // Check Polar: `L<θ`
        if (body.contains('<')) {
            val parts = body.split('<')
            if (parts.size == 2) {
                val dist = parts[0].trim().toDoubleOrNull() ?: return null
                val angleDeg = parts[1].trim().toDoubleOrNull() ?: return null
                return base.polarOffset(dist, angleDeg)
            }
            return null
        }

        // Check Cartesian: `X,Y`
        if (body.contains(',')) {
            val parts = body.split(',')
            if (parts.size >= 2) {
                val x = parts[0].trim().toDoubleOrNull() ?: return null
                val y = parts[1].trim().toDoubleOrNull() ?: return null
                return Point2D(base.x + x, base.y + y)
            }
            return null
        }

        // Check Scalar Distance along current vector from referencePoint to currentCursor
        val scalar = body.toDoubleOrNull()
        if (scalar != null && referencePoint != null) {
            val rawAngle = referencePoint.angleToDeg(currentCursor)
            val effectiveAngle = if (orthoEnabled) snapAngleToOrtho(rawAngle) else rawAngle
            return referencePoint.polarOffset(scalar, effectiveAngle)
        }
        return null
    }

    fun snapAngleToOrtho(angleDeg: Double): Double {
        val normalized = ((angleDeg % 360.0) + 360.0) % 360.0
        return when {
            normalized < 45.0 || normalized >= 315.0 -> 0.0
            normalized < 135.0 -> 90.0
            normalized < 225.0 -> 180.0
            else -> 270.0
        }
    }

    fun applyOrthoLock(rawPoint: Point2D, referencePoint: Point2D?): Point2D {
        if (referencePoint == null) return rawPoint
        val dx = rawPoint.x - referencePoint.x
        val dy = rawPoint.y - referencePoint.y
        return if (abs(dx) >= abs(dy)) {
            Point2D(rawPoint.x, referencePoint.y)
        } else {
            Point2D(referencePoint.x, rawPoint.y)
        }
    }

    fun applyGridSnap(rawPoint: Point2D, interval: Double): Point2D {
        val safeInterval = interval.coerceAtLeast(1e-4)
        val snappedX = (rawPoint.x / safeInterval).roundToLong() * safeInterval
        val snappedY = (rawPoint.y / safeInterval).roundToLong() * safeInterval
        return Point2D(snappedX, snappedY)
    }

    /**
     * Evaluates OSNAP candidates in screen-space proximity while preserving exact model-space coordinates.
     * Snap priority (Section 45.7):
     * 1. Temporary OSNAP override (if active)
     * 2. Active OSNAP modes (Endpoint/Intersection/Center/Midpoint/Quadrant/Perpendicular/Tangent/Insertion/Nearest)
     * 3. Optional PolarSnap candidate (only when polarTrackingEnabled && polarSnapEnabled && activePolarAngleDeg != null)
     * 4. Ortho lock (when orthoEnabled)
     * 5. Grid Snap (if enabled)
     * 6. Free cursor candidate
     */
    fun resolveCursorSnap(
        rawModelCursor: Point2D,
        referencePoint: Point2D?,
        entities: List<CadEntity>,
        snapConfig: GridAndSnapConfig,
        viewport: ViewportTransform,
        snapApertureScreenPx: Float = 26f,
        previousReferencePoint: Point2D? = null
    ): Pair<Point2D, SnapCandidate?> {
        val activeModes: Set<OsnapMode> = when {
            snapConfig.temporaryOsnapOverride != null -> setOf(snapConfig.temporaryOsnapOverride)
            snapConfig.osnapEnabled -> snapConfig.enabledOsnapModes
            else -> emptySet()
        }

        val maxModelDistance = (snapApertureScreenPx / viewport.pixelsPerModelUnit.coerceAtLeast(0.1f)).toDouble()

        if (activeModes.isNotEmpty()) {
            val candidates = collectOsnapCandidates(
                cursor = rawModelCursor,
                referencePoint = referencePoint,
                entities = entities,
                activeModes = activeModes,
                maxDistance = maxModelDistance * 1.5
            )
            val best = candidates
                .filter { it.point.distanceTo(rawModelCursor) <= maxModelDistance }
                .minByOrNull { cand ->
                    // Prioritize structural snaps slightly ahead of NEAREST
                    val modeWeight = if (cand.mode == OsnapMode.NEAREST) 1.6 else 1.0
                    cand.point.distanceTo(rawModelCursor) * modeWeight
                }
            if (best != null) {
                return best.point to best
            }
        }

        var working = rawModelCursor
        if (snapConfig.orthoEnabled && referencePoint != null) {
            working = applyOrthoLock(working, referencePoint)
        } else if (snapConfig.polarSnapEnabled && referencePoint != null) {
            val polarPreview = computePolarTrackingPreview(
                basePoint = referencePoint,
                rawCursorPoint = working,
                snapConfig = snapConfig,
                previousBasePoint = previousReferencePoint
            )
            if (polarPreview?.projectedPolarPoint != null) {
                working = polarPreview.projectedPolarPoint
                return working to null
            }
        }
        if (snapConfig.gridSnapEnabled) {
            working = applyGridSnap(working, snapConfig.gridSnapInterval)
        }
        return working to null
    }

    fun collectOsnapCandidates(
        cursor: Point2D,
        referencePoint: Point2D?,
        entities: List<CadEntity>,
        activeModes: Set<OsnapMode>,
        maxDistance: Double
    ): List<SnapCandidate> {
        val list = mutableListOf<SnapCandidate>()
        val searchBox = BoundingBox2D(
            cursor.x - maxDistance, cursor.y - maxDistance,
            cursor.x + maxDistance, cursor.y + maxDistance
        )
        val nearby = entities.filter { it.boundingBox().intersects(searchBox, tolerance = maxDistance) }

        for (e in nearby) {
            when (e) {
                is CadEntity.LineEntity -> {
                    if (OsnapMode.ENDPOINT in activeModes) {
                        list.add(SnapCandidate(e.start, OsnapMode.ENDPOINT, e.id))
                        list.add(SnapCandidate(e.end, OsnapMode.ENDPOINT, e.id))
                    }
                    if (OsnapMode.MIDPOINT in activeModes) {
                        list.add(SnapCandidate(e.midpoint, OsnapMode.MIDPOINT, e.id))
                    }
                    if (OsnapMode.PERPENDICULAR in activeModes && referencePoint != null) {
                        val perp = CadGeometryKernel.projectPointOnSegment(referencePoint, e.start, e.end)
                        list.add(SnapCandidate(perp, OsnapMode.PERPENDICULAR, e.id))
                    }
                    if (OsnapMode.EXTENSION in activeModes) {
                        val proj = CadGeometryKernel.projectPointOnInfiniteLine(cursor, e.start, e.end)
                        if (proj.distanceTo(cursor) <= maxDistance) {
                            list.add(SnapCandidate(proj, OsnapMode.EXTENSION, e.id))
                        }
                    }
                    if (OsnapMode.NEAREST in activeModes) {
                        list.add(SnapCandidate(CadGeometryKernel.projectPointOnSegment(cursor, e.start, e.end), OsnapMode.NEAREST, e.id))
                    }
                }
                is CadEntity.PolylineEntity -> {
                    val prims = CadGeometryKernel.explodePolylineToPrimitives(e)
                    for (v in e.vertices) {
                        if (OsnapMode.ENDPOINT in activeModes) {
                            list.add(SnapCandidate(v.position, OsnapMode.ENDPOINT, e.id))
                        }
                    }
                    for (prim in prims) {
                        if (prim is CadEntity.LineEntity) {
                            if (OsnapMode.MIDPOINT in activeModes) {
                                list.add(SnapCandidate(prim.midpoint, OsnapMode.MIDPOINT, e.id))
                            }
                            if (OsnapMode.PERPENDICULAR in activeModes && referencePoint != null) {
                                list.add(
                                    SnapCandidate(
                                        CadGeometryKernel.projectPointOnSegment(referencePoint, prim.start, prim.end),
                                        OsnapMode.PERPENDICULAR,
                                        e.id
                                    )
                                )
                            }
                            if (OsnapMode.NEAREST in activeModes) {
                                list.add(
                                    SnapCandidate(
                                        CadGeometryKernel.projectPointOnSegment(cursor, prim.start, prim.end),
                                        OsnapMode.NEAREST,
                                        e.id
                                    )
                                )
                            }
                        } else if (prim is CadEntity.ArcEntity) {
                            if (OsnapMode.CENTER in activeModes) {
                                list.add(SnapCandidate(prim.center, OsnapMode.CENTER, e.id))
                            }
                            if (OsnapMode.MIDPOINT in activeModes) {
                                list.add(SnapCandidate(prim.midPoint, OsnapMode.MIDPOINT, e.id))
                            }
                        }
                    }
                }
                is CadEntity.CircleEntity -> {
                    if (OsnapMode.CENTER in activeModes) {
                        list.add(SnapCandidate(e.center, OsnapMode.CENTER, e.id))
                    }
                    if (OsnapMode.QUADRANT in activeModes) {
                        e.quadrants().forEach { q -> list.add(SnapCandidate(q, OsnapMode.QUADRANT, e.id)) }
                    }
                    if (OsnapMode.TANGENT in activeModes && referencePoint != null) {
                        val d = referencePoint.distanceTo(e.center)
                        if (d > e.radius + 1e-4) {
                            val baseAngle = e.center.angleToDeg(referencePoint)
                            val deltaDeg = Math.toDegrees( kotlin.math.acos((e.radius / d).coerceIn(-1.0, 1.0)) )
                            list.add(SnapCandidate(e.center.polarOffset(e.radius, baseAngle + deltaDeg), OsnapMode.TANGENT, e.id))
                            list.add(SnapCandidate(e.center.polarOffset(e.radius, baseAngle - deltaDeg), OsnapMode.TANGENT, e.id))
                        }
                    }
                    if (OsnapMode.NEAREST in activeModes) {
                        val ang = e.center.angleToDeg(cursor)
                        list.add(SnapCandidate(e.center.polarOffset(e.radius, ang), OsnapMode.NEAREST, e.id))
                    }
                }
                is CadEntity.ArcEntity -> {
                    if (OsnapMode.ENDPOINT in activeModes) {
                        list.add(SnapCandidate(e.startPoint, OsnapMode.ENDPOINT, e.id))
                        list.add(SnapCandidate(e.endPoint, OsnapMode.ENDPOINT, e.id))
                    }
                    if (OsnapMode.MIDPOINT in activeModes) {
                        list.add(SnapCandidate(e.midPoint, OsnapMode.MIDPOINT, e.id))
                    }
                    if (OsnapMode.CENTER in activeModes) {
                        list.add(SnapCandidate(e.center, OsnapMode.CENTER, e.id))
                    }
                    if (OsnapMode.QUADRANT in activeModes) {
                        for (card in listOf(0.0, 90.0, 180.0, 270.0)) {
                            if (e.containsAngle(card)) {
                                list.add(SnapCandidate(e.center.polarOffset(e.radius, card), OsnapMode.QUADRANT, e.id))
                            }
                        }
                    }
                }
                is CadEntity.EllipseEntity -> {
                    if (OsnapMode.CENTER in activeModes) {
                        list.add(SnapCandidate(e.center, OsnapMode.CENTER, e.id))
                    }
                    if (OsnapMode.QUADRANT in activeModes) {
                        e.quadrantPoints().forEach { q -> list.add(SnapCandidate(q, OsnapMode.QUADRANT, e.id)) }
                    }
                }
                is CadEntity.PointEntity -> {
                    if (OsnapMode.ENDPOINT in activeModes || OsnapMode.INSERTION in activeModes) {
                        list.add(SnapCandidate(e.position, OsnapMode.ENDPOINT, e.id))
                    }
                }
                is CadEntity.DonutEntity -> {
                    if (OsnapMode.CENTER in activeModes) {
                        list.add(SnapCandidate(e.center, OsnapMode.CENTER, e.id))
                    }
                }
                is CadEntity.TextEntity -> {
                    if (OsnapMode.INSERTION in activeModes) {
                        list.add(SnapCandidate(e.position, OsnapMode.INSERTION, e.id))
                    }
                }
                is CadEntity.BlockInsertEntity -> {
                    if (OsnapMode.INSERTION in activeModes) {
                        list.add(SnapCandidate(e.insertionPoint, OsnapMode.INSERTION, e.id))
                    }
                }
                is CadEntity.CenterMarkEntity -> {
                    if (OsnapMode.CENTER in activeModes) {
                        list.add(SnapCandidate(e.center, OsnapMode.CENTER, e.id))
                    }
                }
                else -> Unit
            }
        }

        // Intersection OSNAP between nearby entities
        if (OsnapMode.INTERSECTION in activeModes && nearby.size >= 2) {
            for (i in nearby.indices) {
                for (j in i + 1 until nearby.size) {
                    val pts = CadGeometryKernel.intersectEntities(nearby[i], nearby[j])
                    for (pt in pts) {
                        list.add(SnapCandidate(pt, OsnapMode.INTERSECTION, nearby[i].id))
                    }
                }
            }
        }

        return list
    }
}
