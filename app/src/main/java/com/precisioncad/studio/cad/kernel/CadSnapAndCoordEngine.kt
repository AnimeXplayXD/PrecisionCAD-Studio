package com.precisioncad.studio.cad.kernel

import androidx.compose.ui.geometry.Offset
import com.precisioncad.studio.cad.model.BoundingBox2D
import com.precisioncad.studio.cad.model.CadEntity
import com.precisioncad.studio.cad.model.GridAndSnapConfig
import com.precisioncad.studio.cad.model.OsnapMode
import com.precisioncad.studio.cad.model.Point2D
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.round
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

    fun containsScreenOffset(offset: Offset, marginPx: Float = 0f): Boolean {
        return offset.x >= leftPx + marginPx &&
            offset.x <= rightPx - marginPx &&
            offset.y >= topPx + marginPx &&
            offset.y <= bottomPx - marginPx
    }
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
 * Section 8 — Read-Only Visual Polar Tracking & Independent PolarSnap State.
 *
 * - [basePoint]: Accepted command base point B = (Bx, By).
 * - [rawCursorPoint]: Live raw virtual cursor position P = (Px, Py).
 * - [radialDistanceR]: Live radial distance R = hypot(Px - Bx, Py - By) in model units.
 * - [actualAngleThetaDeg]: Live unrounded Cursor Bearing θ in [0, 360) degrees (null if R is degenerate ~ 0).
 * - [referenceAngleDeg]: Reference zero-angle direction (0° absolute +X, or previous segment angle when relative mode is active).
 * - [referenceBasisLabel]: Explicit reference basis label ("+X" or "Last Segment").
 * - [activePolarAngleDeg]: Active Track Bearing θₚ in [0, 360) degrees ONLY when a visual guide angle in the
 *   Visual Polar Tracking collection is genuinely acquired within [CadSnapAndCoordEngine.VISUAL_POLAR_ACQUIRE_TOLERANCE_DEG]
 *   (or retained within [CadSnapAndCoordEngine.VISUAL_POLAR_RELEASE_TOLERANCE_DEG] hysteresis). Null when not acquired.
 * - [worldPolarAngleDeg]: Active visual polar direction in absolute world degrees [0, 360) for guide ray rendering.
 * - [activePolarSnapAngleDeg]: Independently acquired PolarSnap ray angle in [0, 360) when within tolerance of a
 *   selected PolarSnap preset/custom angle.
 * - [projectedPolarPoint]: Point B + R * (cos θ_snap, sin θ_snap) along the nearest active PolarSnap ray
 *   (used to project candidate ONLY when polarSnapEnabled is true).
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
    val worldPolarSnapAngleDeg: Double? = null,
    val projectedPolarPoint: Point2D? = null,
    val isPolarSnapRayVisibleForDisplay: Boolean = false
) {
    val hasValidDirection: Boolean get() = actualAngleThetaDeg != null && radialDistanceR > 1e-5
    val isPolarGuideAcquired: Boolean get() = activePolarAngleDeg != null && worldPolarAngleDeg != null && hasValidDirection
    val isPolarSnapRayAcquired: Boolean get() = activePolarSnapAngleDeg != null && projectedPolarPoint != null && hasValidDirection
    val acquiredAngleDeg: Double? get() = activePolarAngleDeg
}

object CadSnapAndCoordEngine {

    /** Dedicated visual polar guide acquisition tolerance (Section 8.4). */
    const val VISUAL_POLAR_ACQUIRE_TOLERANCE_DEG = 0.35

    /** Dedicated visual polar guide release hysteresis tolerance (Section 8.4). */
    const val VISUAL_POLAR_RELEASE_TOLERANCE_DEG = 0.55

    /**
     * V19 §13.2 — Strict angular match epsilon for displaying the blue dashed PolarSnap ray:
     * The blue dashed PolarSnap ray is shown ONLY when `polarSnapEnabled == true` AND
     * `|θ_effective - θ_polarSnap| <= POLAR_SNAP_RAY_DISPLAY_EPSILON_DEG` (0.01°).
     */
    const val POLAR_SNAP_RAY_DISPLAY_EPSILON_DEG = 0.01

    /** Angular deduplication epsilon for polar guide and snap collections (Section 8.3). */
    const val ANGLE_DEDUP_EPSILON_DEG = 1e-4

    /** Minimum positive finite Grid Snap step in model units (Section 7.2). */
    const val MIN_GRID_SNAP_INTERVAL = 1e-6

    /** Maximum finite Grid Snap step in model units (Section 7.2). */
    const val MAX_GRID_SNAP_INTERVAL = 1e6

    /** Default maximum screen-space snap displacement (~24 dp at 3x density, Section 7.4). */
    const val DEFAULT_MAX_SNAP_DISPLACEMENT_PX = 72f

    private val ENGINEERING_MANTISSAS = doubleArrayOf(1.0, 2.0, 2.5, 5.0, 10.0)

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
     * V19 §11.3 & §11.4 — Phase-Preserving Step Lattice Alignment on Touchpad Release:
     * Aligns [raw] to the nearest step lattice offset from [phase] by integer multiples of [step]:
     *   aligned(raw, phase, step) = phase + round((raw - phase) / step) * step
     */
    fun alignCoordinateToStepPhase(raw: Double, phase: Double, step: Double): Double {
        if (!raw.isFinite() || !phase.isFinite() || !step.isFinite() || step <= 0.0) return raw
        return phase + round((raw - phase) / step) * step
    }

    fun captureStepPhase(coord: Double, step: Double): Double {
        if (!coord.isFinite() || !step.isFinite() || step <= 0.0) return 0.0
        val rem = coord % step
        return if (abs(rem) < 1e-9 || abs(abs(rem) - step) < 1e-9) 0.0 else rem
    }

    /**
     * Section 7.2 & 7.3 — Computes the zoom-adaptive effective model-space Grid Snap interval:
     *   S_eff = S_0 * (Z_0 / Z)
     * quantized to a clean engineering progression (1–2–2.5–5 × 10^n) that never creates a
     * larger-than-configured screen-space jump, with hysteresis around [previousEffectiveInterval]
     * to prevent boundary flicker.
     */
    fun computeEffectiveGridSnapInterval(
        baseInterval: Double,
        currentPixelsPerModelUnit: Float,
        referencePixelsPerModelUnit: Double? = null,
        previousEffectiveInterval: Double? = null
    ): Double {
        val safeBase = if (baseInterval.isFinite() && baseInterval > 0.0) {
            baseInterval.coerceIn(MIN_GRID_SNAP_INTERVAL, MAX_GRID_SNAP_INTERVAL)
        } else {
            1.0
        }
        val currentZ = currentPixelsPerModelUnit.toDouble()
        val refZ = referencePixelsPerModelUnit
        if (refZ == null || !refZ.isFinite() || refZ <= 0.0 || !currentZ.isFinite() || currentZ <= 0.0) {
            return safeBase
        }

        val zoomRatio = refZ / currentZ
        if (abs(zoomRatio - 1.0) < 1e-4) {
            return safeBase
        }

        val rawTarget = (safeBase * zoomRatio).coerceIn(MIN_GRID_SNAP_INTERVAL, MAX_GRID_SNAP_INTERVAL)
        val quantized = quantizeToEngineeringStepNotExceeding(rawTarget)

        // Hysteresis guard (Section 7.4): retain previousEffectiveInterval if within 12% of rawTarget
        // and its screen-space step does not exceed the configured base screen-space step by > 12%.
        if (previousEffectiveInterval != null &&
            previousEffectiveInterval.isFinite() &&
            previousEffectiveInterval > 0.0
        ) {
            val relDiff = abs(previousEffectiveInterval - rawTarget) / rawTarget.coerceAtLeast(MIN_GRID_SNAP_INTERVAL)
            if (relDiff <= 0.12 && previousEffectiveInterval <= rawTarget * 1.12) {
                return previousEffectiveInterval.coerceIn(MIN_GRID_SNAP_INTERVAL, MAX_GRID_SNAP_INTERVAL)
            }
        }

        return quantized.coerceIn(MIN_GRID_SNAP_INTERVAL, MAX_GRID_SNAP_INTERVAL)
    }

    fun computeEffectiveGridSnapInterval(
        baseGridInterval: Double,
        currentPixelsPerModelUnit: Double,
        referencePixelsPerModelUnit: Double? = null,
        previousEffectiveInterval: Double? = null
    ): Double = computeEffectiveGridSnapInterval(
        baseInterval = baseGridInterval,
        currentPixelsPerModelUnit = currentPixelsPerModelUnit.toFloat(),
        referencePixelsPerModelUnit = referencePixelsPerModelUnit,
        previousEffectiveInterval = previousEffectiveInterval
    )

    private fun quantizeToEngineeringStepNotExceeding(value: Double): Double {
        if (!value.isFinite() || value <= MIN_GRID_SNAP_INTERVAL) return MIN_GRID_SNAP_INTERVAL
        val exp = floor(log10(value))
        val pow10 = 10.0.pow(exp)
        val mantissa = value / pow10
        var chosen = 1.0
        for (m in ENGINEERING_MANTISSAS) {
            if (m <= mantissa * 1.0001) {
                chosen = m
            }
        }
        return (chosen * pow10).coerceIn(MIN_GRID_SNAP_INTERVAL, MAX_GRID_SNAP_INTERVAL)
    }

    /**
     * Section 8.3 — Builds the complete Visual Polar Tracking angle set:
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
                if (angles.none { wrappedAngularDiffDeg(it, norm) < ANGLE_DEDUP_EPSILON_DEG }) {
                    angles.add(norm)
                }
            }
        }
        return angles.sorted()
    }

    /**
     * Section 8.5 — Builds the independent PolarSnap angle collection strictly from the multi-select
     * PolarSnap presets (`polarSnapPresetAnglesDeg`) plus custom PolarSnap angles (`polarSnapCustomAnglesDeg`).
     * Never mixes `polarAdditionalAnglesDeg` or the all-degree Visual Polar Tracking set into PolarSnap.
     */
    fun buildPolarSnapAngles(snapConfig: GridAndSnapConfig): List<Double> {
        val angles = mutableListOf<Double>()
        val presets = if (snapConfig.polarSnapPresetAnglesDeg.isNotEmpty()) {
            snapConfig.polarSnapPresetAnglesDeg
        } else {
            GridAndSnapConfig().polarSnapPresetAnglesDeg
        }
        for (incRaw in presets) {
            if (!incRaw.isFinite()) continue
            val inc = incRaw.coerceIn(1.0, 180.0)
            var deg = 0.0
            while (deg < 360.0 - 1e-5) {
                val norm = normalizeAngle360(deg)
                if (angles.none { wrappedAngularDiffDeg(it, norm) < ANGLE_DEDUP_EPSILON_DEG }) {
                    angles.add(norm)
                }
                deg += inc
            }
        }
        for (custom in snapConfig.polarSnapCustomAnglesDeg) {
            if (custom.isFinite()) {
                val norm = normalizeAngle360(custom)
                if (angles.none { wrappedAngularDiffDeg(it, norm) < ANGLE_DEDUP_EPSILON_DEG }) {
                    angles.add(norm)
                }
            }
        }
        return angles.sorted()
    }

    /**
     * Section 8.2–8.6: Computes the live read-only Polar Tracking preview (R, Cursor Bearing θ,
     * Track Bearing θₚ, reference basis, and independent PolarSnap projection) from the accepted
     * command [basePoint] to [rawCursorPoint] in model space.
     *
     * Visual Track Bearing θₚ ([PolarTrackingPreviewState.activePolarAngleDeg]) is acquired ONLY when
     * the raw cursor bearing is within [VISUAL_POLAR_ACQUIRE_TOLERANCE_DEG] (0.35°) of a visual guide
     * candidate, or retained within [VISUAL_POLAR_RELEASE_TOLERANCE_DEG] (0.55°) of [previouslyAcquiredVisualAngleDeg].
     * It is NEVER assigned from an acquired PolarSnap ray and NEVER falls back unconditionally to the
     * nearest whole degree when outside visual tolerance.
     */
    fun evaluatePolarTracking(
        basePoint: Point2D?,
        rawCursorPoint: Point2D,
        snapConfig: GridAndSnapConfig,
        previousBasePoint: Point2D? = null,
        previouslyAcquiredVisualAngleDeg: Double? = null,
        effectiveCursorPoint: Point2D = rawCursorPoint
    ): PolarTrackingPreviewState? = computePolarTrackingPreview(
        basePoint = basePoint,
        rawCursorPoint = rawCursorPoint,
        snapConfig = snapConfig,
        previousBasePoint = previousBasePoint,
        previouslyAcquiredVisualAngleDeg = previouslyAcquiredVisualAngleDeg,
        effectiveCursorPoint = effectiveCursorPoint
    )

    fun computePolarTrackingPreview(
        basePoint: Point2D?,
        rawCursorPoint: Point2D,
        snapConfig: GridAndSnapConfig,
        previousBasePoint: Point2D? = null,
        previouslyAcquiredVisualAngleDeg: Double? = null,
        effectiveCursorPoint: Point2D = rawCursorPoint
    ): PolarTrackingPreviewState? {
        if (basePoint == null) return null
        if (!basePoint.x.isFinite() || !basePoint.y.isFinite() ||
            !rawCursorPoint.x.isFinite() || !rawCursorPoint.y.isFinite()
        ) {
            return null
        }

        val dx = rawCursorPoint.x - basePoint.x
        val dy = rawCursorPoint.y - basePoint.y
        val r = hypot(dx, dy)

        val useLastSegment = snapConfig.polarRelativeToLastSegment &&
            previousBasePoint != null &&
            previousBasePoint.distanceTo(basePoint) > 1e-5
        val refDeg = if (useLastSegment) {
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
                worldPolarSnapAngleDeg = null,
                projectedPolarPoint = null,
                isPolarSnapRayVisibleForDisplay = false
            )
        }

        val rawWorldDeg = normalizeAngle360(Math.toDegrees(atan2(dy, dx)))
        val relativeThetaDeg = normalizeAngle360(rawWorldDeg - refDeg)
        val snapTolerance = snapConfig.polarAcquisitionToleranceDeg.coerceIn(0.5, 45.0)

        // Effective cursor angle (used for V19 §13.2 strict 0.01° blue PolarSnap ray display gate)
        val effDx = effectiveCursorPoint.x - basePoint.x
        val effDy = effectiveCursorPoint.y - basePoint.y
        val effR = hypot(effDx, effDy)
        val effectiveRelativeThetaDeg = if (effR.isFinite() && effR > 1e-5) {
            val effWorldDeg = normalizeAngle360(Math.toDegrees(atan2(effDy, effDx)))
            normalizeAngle360(effWorldDeg - refDeg)
        } else {
            relativeThetaDeg
        }

        // 1. Evaluate independent PolarSnap ray acquisition against PolarSnap presets + custom snap angles only
        val polarSnapCandidates = buildPolarSnapAngles(snapConfig)
        val bestSnapDeg = polarSnapCandidates.minByOrNull { cand ->
            wrappedAngularDiffDeg(relativeThetaDeg, cand)
        }
        val snapDiff = if (bestSnapDeg != null) wrappedAngularDiffDeg(relativeThetaDeg, bestSnapDeg) else Double.MAX_VALUE
        val acquiredSnapDeg = if (bestSnapDeg != null && snapDiff <= snapTolerance) bestSnapDeg else null
        val worldSnapDeg = acquiredSnapDeg?.let { normalizeAngle360(refDeg + it) }
        val projectedSnapPt = if (acquiredSnapDeg != null && worldSnapDeg != null) {
            basePoint.polarOffset(r, worldSnapDeg)
        } else {
            null
        }
        val showBluePolarSnapRay = snapConfig.polarSnapEnabled &&
            acquiredSnapDeg != null &&
            wrappedAngularDiffDeg(effectiveRelativeThetaDeg, acquiredSnapDeg) <= POLAR_SNAP_RAY_DISPLAY_EPSILON_DEG

        // 2. If Visual Polar Tracking is disabled, never acquire or draw a visual guide ray
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
                worldPolarSnapAngleDeg = worldSnapDeg,
                projectedPolarPoint = projectedSnapPt,
                isPolarSnapRayVisibleForDisplay = showBluePolarSnapRay
            )
        }

        // 3. Evaluate Visual Polar Tracking acquisition strictly against buildVisualPolarGuideAngles(snapConfig)
        //    using VISUAL_POLAR_ACQUIRE_TOLERANCE_DEG (0.35°) and VISUAL_POLAR_RELEASE_TOLERANCE_DEG (0.55°).
        val allVisualCandidates = buildVisualPolarGuideAngles(snapConfig)
        val closestVisualCandidate = allVisualCandidates.minByOrNull { cand ->
            wrappedAngularDiffDeg(relativeThetaDeg, cand)
        }
        val closestVisualDiff = if (closestVisualCandidate != null) {
            wrappedAngularDiffDeg(relativeThetaDeg, closestVisualCandidate)
        } else {
            Double.MAX_VALUE
        }

        val acquiredVisualDeg: Double? = when {
            closestVisualCandidate != null && closestVisualDiff <= VISUAL_POLAR_ACQUIRE_TOLERANCE_DEG -> {
                closestVisualCandidate
            }
            previouslyAcquiredVisualAngleDeg != null &&
                previouslyAcquiredVisualAngleDeg.isFinite() &&
                allVisualCandidates.any { wrappedAngularDiffDeg(it, previouslyAcquiredVisualAngleDeg) < ANGLE_DEDUP_EPSILON_DEG } &&
                wrappedAngularDiffDeg(relativeThetaDeg, previouslyAcquiredVisualAngleDeg) <= VISUAL_POLAR_RELEASE_TOLERANCE_DEG -> {
                normalizeAngle360(previouslyAcquiredVisualAngleDeg)
            }
            else -> null
        }

        val worldPolar = acquiredVisualDeg?.let { normalizeAngle360(refDeg + it) }
        return PolarTrackingPreviewState(
            basePoint = basePoint,
            rawCursorPoint = rawCursorPoint,
            radialDistanceR = r,
            actualAngleThetaDeg = relativeThetaDeg,
            referenceAngleDeg = refDeg,
            referenceBasisLabel = basisLabel,
            activePolarAngleDeg = acquiredVisualDeg,
            worldPolarAngleDeg = worldPolar,
            activePolarSnapAngleDeg = acquiredSnapDeg,
            worldPolarSnapAngleDeg = worldSnapDeg,
            projectedPolarPoint = projectedSnapPt,
            isPolarSnapRayVisibleForDisplay = showBluePolarSnapRay
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

    /**
     * Rounds [rawPoint] to the grid lattice of step [interval], guarding against non-finite
     * inputs and Long overflow on extreme coordinate magnitudes (Section 7.4).
     */
    fun applyGridSnap(rawPoint: Point2D, interval: Double): Point2D {
        if (!rawPoint.x.isFinite() || !rawPoint.y.isFinite()) return Point2D(0.0, 0.0)
        if (!interval.isFinite() || interval <= 0.0) return rawPoint
        val safeInterval = interval.coerceIn(MIN_GRID_SNAP_INTERVAL, MAX_GRID_SNAP_INTERVAL)
        val qx = rawPoint.x / safeInterval
        val qy = rawPoint.y / safeInterval
        if (!qx.isFinite() || !qy.isFinite() || abs(qx) > 1e14 || abs(qy) > 1e14) {
            return rawPoint
        }
        val snappedX = qx.roundToLong() * safeInterval
        val snappedY = qy.roundToLong() * safeInterval
        if (!snappedX.isFinite() || !snappedY.isFinite()) return rawPoint
        return Point2D(snappedX, snappedY)
    }

    /**
     * Section 7.2–7.4 — Applies zoom-adaptive Grid Snap with screen-space displacement and
     * usable-viewport boundary guards so Grid Snap never jumps off-screen at high magnification.
     */
    fun applyZoomAdaptiveGridSnap(
        rawPoint: Point2D,
        baseInterval: Double,
        viewport: ViewportTransform,
        referencePixelsPerModelUnit: Double? = null,
        previousEffectiveInterval: Double? = null,
        usableRect: UsableViewportRect? = null,
        maxScreenDisplacementPx: Float = DEFAULT_MAX_SNAP_DISPLACEMENT_PX
    ): Point2D {
        if (!rawPoint.x.isFinite() || !rawPoint.y.isFinite()) return Point2D(0.0, 0.0)
        val effectiveStep = computeEffectiveGridSnapInterval(
            baseInterval = baseInterval,
            currentPixelsPerModelUnit = viewport.pixelsPerModelUnit,
            referencePixelsPerModelUnit = referencePixelsPerModelUnit,
            previousEffectiveInterval = previousEffectiveInterval
        )
        val candidate = applyGridSnap(rawPoint, effectiveStep)
        val rawScreen = viewport.modelToScreen(rawPoint)
        val snappedScreen = viewport.modelToScreen(candidate)
        val screenDelta = hypot(
            (snappedScreen.x - rawScreen.x).toDouble(),
            (snappedScreen.y - rawScreen.y).toDouble()
        ).toFloat()

        // Guard against forcing a snap that exceeds the screen-space displacement limit
        if (referencePixelsPerModelUnit != null && maxScreenDisplacementPx > 0f && screenDelta > maxScreenDisplacementPx) {
            return rawPoint
        }

        // Guard against snapping a visible cursor outside the usable viewport
        if (usableRect != null &&
            usableRect.containsScreenOffset(rawScreen) &&
            !usableRect.containsScreenOffset(snappedScreen)
        ) {
            return rawPoint
        }

        return candidate
    }

    /**
     * Evaluates OSNAP candidates in screen-space proximity while preserving exact model-space coordinates.
     * Snap priority:
     * 1. Temporary OSNAP override (if active)
     * 2. Active OSNAP modes (Endpoint/Intersection/Center/Midpoint/Quadrant/Perpendicular/Tangent/Insertion/Nearest)
     * 3. Optional PolarSnap candidate (only when polarSnapEnabled && activePolarSnapAngleDeg != null)
     * 4. Ortho lock (when orthoEnabled)
     * 5. Zoom-adaptive Grid Snap (if enabled)
     * 6. Free cursor candidate
     */
    fun resolveCursorSnap(
        rawModelCursor: Point2D,
        referencePoint: Point2D?,
        entities: List<CadEntity>,
        snapConfig: GridAndSnapConfig,
        viewport: ViewportTransform,
        snapApertureScreenPx: Float = 26f,
        previousReferencePoint: Point2D? = null,
        referencePixelsPerModelUnit: Double? = null,
        previousEffectiveGridInterval: Double? = null,
        usableRect: UsableViewportRect? = null,
        usableViewportRect: UsableViewportRect? = null,
        previouslyAcquiredVisualAngleDeg: Double? = null,
        maxGridSnapDisplacementPx: Float = DEFAULT_MAX_SNAP_DISPLACEMENT_PX
    ): Pair<Point2D, SnapCandidate?> {
        if (!rawModelCursor.x.isFinite() || !rawModelCursor.y.isFinite()) {
            return Point2D(0.0, 0.0) to null
        }

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
                    val modeWeight = if (cand.mode == OsnapMode.NEAREST) 1.6 else 1.0
                    cand.point.distanceTo(rawModelCursor) * modeWeight
                }
            if (best != null) {
                return best.point to best
            }
        }

        var working = rawModelCursor
        val isOrthoLocked = snapConfig.orthoEnabled && referencePoint != null
        val isOrthoHorizontal = if (referencePoint != null && snapConfig.orthoEnabled) {
            abs(rawModelCursor.x - referencePoint.x) >= abs(rawModelCursor.y - referencePoint.y)
        } else {
            false
        }
        if (isOrthoLocked) {
            working = applyOrthoLock(working, referencePoint)
        } else if (snapConfig.polarSnapEnabled && referencePoint != null) {
            val polarPreview = computePolarTrackingPreview(
                basePoint = referencePoint,
                rawCursorPoint = working,
                snapConfig = snapConfig,
                previousBasePoint = previousReferencePoint,
                previouslyAcquiredVisualAngleDeg = previouslyAcquiredVisualAngleDeg
            )
            if (polarPreview?.projectedPolarPoint != null) {
                working = polarPreview.projectedPolarPoint
                return working to null
            }
        }
        if (snapConfig.gridSnapEnabled) {
            val gridSnapped = applyZoomAdaptiveGridSnap(
                rawPoint = working,
                baseInterval = snapConfig.gridSnapInterval,
                viewport = viewport,
                referencePixelsPerModelUnit = referencePixelsPerModelUnit,
                previousEffectiveInterval = previousEffectiveGridInterval,
                usableRect = usableRect ?: usableViewportRect,
                maxScreenDisplacementPx = maxGridSnapDisplacementPx
            )
            working = if (referencePoint != null && snapConfig.orthoEnabled) {
                if (isOrthoHorizontal) {
                    Point2D(gridSnapped.x, referencePoint.y)
                } else {
                    Point2D(referencePoint.x, gridSnapped.y)
                }
            } else {
                gridSnapped
            }
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
                            val deltaDeg = Math.toDegrees(kotlin.math.acos((e.radius / d).coerceIn(-1.0, 1.0)))
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
