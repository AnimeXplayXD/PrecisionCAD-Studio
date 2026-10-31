package com.example.cad.dimension

import com.example.cad.kernel.CadGeometryKernel
import com.example.cad.kernel.ViewportTransform
import com.example.cad.model.BoundingBox2D
import com.example.cad.model.CadEntity
import com.example.cad.model.CadUnit
import com.example.cad.model.ConstraintType
import com.example.cad.model.DimensionHierarchyTier
import com.example.cad.model.DimensionType
import com.example.cad.model.GeometricConstraintRecord
import com.example.cad.model.Point2D
import com.example.cad.model.PrecisionFormatConfig
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sin

/**
 * Section 10.4 — DimensionAlignmentEngine: Dedicated Alignment and Arrangement Subsystem.
 *
 * Independently identifiable alignment, completeness analysis, hierarchy classification,
 * multi-primitive collision resolution, and layout scoring engine for Quick Dimension (QDIM).
 */
enum class DimensionArrangementMode(val label: String) {
    BASELINE("Baseline (Shared Datum)"),
    CONTINUOUS_CHAIN("Continuous Chain"),
    OVERALL("Overall Envelope"),
    ORDINATE("Ordinate Datum"),
    ALIGNED("Aligned Direction"),
    ANGULAR("Angular Arc"),
    RADIAL_DIAMETER_CALLOUT("Radial / Diameter Callout"),
    REPEATED_FEATURE_CALLOUT("Repeated-Feature Callout"),
    SYMMETRIC_OR_EQUAL_SPACING("Verified Symmetric / Equal Spacing")
}

enum class DimensionArrangementPreference(val label: String, val description: String) {
    AUTO_CHAIN(
        "Continuous Chain (Default)",
        "Adjacent feature-to-feature chains on inner band with overall envelope on outer band."
    ),
    BASELINE(
        "Baseline (Shared Datum)",
        "Parallel dimension bands measured from a common left/bottom datum origin."
    ),
    ORDINATE(
        "Ordinate (Datum X/Y)",
        "Coordinate callouts measured from datum origin without chain tolerance accumulation."
    )
}

enum class DimensionCompletenessCategory(val label: String) {
    EXPLICITLY_PRESENT("Explicitly Present"),
    ARITHMETICALLY_DERIVABLE("Arithmetically Derivable"),
    VERIFIED_GEOMETRIC_EQUALITY("Verified Geometric Equality"),
    INFERRED_UNVERIFIED("Inferred / Unverified (Explicit Value Required)")
}

enum class ArrowPlacementMode {
    INSIDE,
    OUTSIDE,
    SPLIT,
    LEADER_BASED
}

enum class TextPlacementMode {
    CENTERED_INTERNAL,
    EXTERNAL_OFFSET,
    LEADER_SHELF
}

enum class CollisionClearanceStatus {
    CLEAR,
    RESOLVED_BY_RELOCATION,
    UNRESOLVED_CROWDED
}

enum class EdgeDimensionStatus {
    EMITTED_EXPLICIT,
    COVERED_BY_PRIMARY_OVERALL,
    DERIVABLE_FROM_OVERALL_AND_FILLETS,
    SUPPRESSED_BY_SPARSE_POLICY,
    COVERED_BY_MANUAL_DIMENSION
}

data class EdgeDimensionDiagnostic(
    val sourceEntityId: String,
    val start: Point2D,
    val end: Point2D,
    val length: Double,
    val orientation: String,
    val status: EdgeDimensionStatus,
    val emittedDimensionId: String? = null,
    val reason: String
)

data class CompletenessFinding(
    val axisOrFeature: String,
    val category: DimensionCompletenessCategory,
    val measuredValue: Double,
    val isSymmetricOrEqualVerified: Boolean,
    val description: String
)

/**
 * Structured output contract for each aligned dimension produced by [DimensionAlignmentEngine].
 */
data class AlignedDimensionPlacementResult(
    val dimensionId: String,
    val dimension: CadEntity.DimensionEntity,
    val arrangementMode: DimensionArrangementMode,
    val groupId: String,
    val hierarchyTier: DimensionHierarchyTier,
    val datumReference: String,
    val dimLineStartModel: Point2D,
    val dimLineEndModel: Point2D,
    val ext1StartModel: Point2D,
    val ext1EndModel: Point2D,
    val ext2StartModel: Point2D,
    val ext2EndModel: Point2D,
    val textAnchorModel: Point2D,
    val textRotationDeg: Float,
    val textPlacementMode: TextPlacementMode,
    val arrowPlacementMode: ArrowPlacementMode,
    val completenessCategory: DimensionCompletenessCategory,
    val collisionClearanceStatus: CollisionClearanceStatus,
    val reviewFlags: List<String> = emptyList()
)

data class DimensionAlignmentReport(
    val placements: List<AlignedDimensionPlacementResult>,
    val completenessFindings: List<CompletenessFinding>,
    val unresolvedWarnings: List<String>,
    val layoutScore: Double,
    val arrangementPreference: DimensionArrangementPreference,
    val densityPolicy: QDimDensityPolicy,
    val edgeDiagnostics: List<EdgeDimensionDiagnostic> = emptyList(),
    val generationCompletenessScore: Double = 100.0,
    val layoutQualityScore: Double = layoutScore
) {
    val resolvedDimensions: List<CadEntity.DimensionEntity>
        get() = placements.map { it.dimension }
}

object DimensionAlignmentEngine {

    private const val STRICT_GEOMETRIC_EQUALITY_TOL = 1e-3

    /**
     * Analyzes geometric completeness, datum relationships, symmetric margins, and equal hole pitches.
     * Strictly distinguishes verified geometric equality from arithmetic remainders or inferred intent.
     */
    fun analyzeCompletenessAndRelationships(
        overallBounds: BoundingBox2D,
        circles: List<CadEntity.CircleEntity>,
        arcs: List<CadEntity.ArcEntity>,
        constraints: List<GeometricConstraintRecord> = emptyList()
    ): List<CompletenessFinding> {
        val findings = mutableListOf<CompletenessFinding>()

        if (overallBounds.width > 1e-3) {
            findings.add(
                CompletenessFinding(
                    axisOrFeature = "PRIMARY_WIDTH",
                    category = DimensionCompletenessCategory.EXPLICITLY_PRESENT,
                    measuredValue = overallBounds.width,
                    isSymmetricOrEqualVerified = false,
                    description = "Primary overall horizontal envelope width = ${fmt(overallBounds.width)}."
                )
            )
        }
        if (overallBounds.height > 1e-3) {
            findings.add(
                CompletenessFinding(
                    axisOrFeature = "PRIMARY_HEIGHT",
                    category = DimensionCompletenessCategory.EXPLICITLY_PRESENT,
                    measuredValue = overallBounds.height,
                    isSymmetricOrEqualVerified = false,
                    description = "Primary overall vertical envelope height = ${fmt(overallBounds.height)}."
                )
            )
        }

        if (circles.isNotEmpty()) {
            val rowsByY = circles.groupBy { (it.center.y * 10.0).roundToLong() }
            val bottomRow = rowsByY.minByOrNull { it.key }?.value?.sortedBy { it.center.x }.orEmpty()

            if (bottomRow.isNotEmpty()) {
                val leftMargin = bottomRow.first().center.x - overallBounds.minX
                val rightMargin = overallBounds.maxX - bottomRow.last().center.x
                val hasEqualConstraint = constraints.any { it.type == ConstraintType.EQUAL }

                val marginsStrictlyEqual = abs(leftMargin - rightMargin) <= STRICT_GEOMETRIC_EQUALITY_TOL ||
                    (hasEqualConstraint && abs(leftMargin - rightMargin) <= 0.05)

                if (marginsStrictlyEqual && leftMargin > 1e-2) {
                    findings.add(
                        CompletenessFinding(
                            axisOrFeature = "HORIZONTAL_MARGINS",
                            category = DimensionCompletenessCategory.VERIFIED_GEOMETRIC_EQUALITY,
                            measuredValue = leftMargin,
                            isSymmetricOrEqualVerified = true,
                            description = "Horizontal left/right hole margins verified symmetric (${fmt(leftMargin)}); right margin is arithmetically derivable."
                        )
                    )
                } else if (leftMargin > 1e-2 && rightMargin > 1e-2) {
                    findings.add(
                        CompletenessFinding(
                            axisOrFeature = "HORIZONTAL_MARGINS",
                            category = DimensionCompletenessCategory.INFERRED_UNVERIFIED,
                            measuredValue = rightMargin,
                            isSymmetricOrEqualVerified = false,
                            description = "Asymmetric horizontal margins (left=${fmt(leftMargin)}, right=${fmt(rightMargin)}); symmetry not assumed."
                        )
                    )
                }

                if (bottomRow.size >= 2) {
                    val pitches = (0 until bottomRow.size - 1).map { i ->
                        bottomRow[i + 1].center.x - bottomRow[i].center.x
                    }.filter { it > 1e-2 }

                    if (pitches.size >= 2) {
                        val maxDiff = (pitches.maxOrNull() ?: 0.0) - (pitches.minOrNull() ?: 0.0)
                        if (maxDiff <= STRICT_GEOMETRIC_EQUALITY_TOL) {
                            findings.add(
                                CompletenessFinding(
                                    axisOrFeature = "HORIZONTAL_HOLE_PITCH",
                                    category = DimensionCompletenessCategory.VERIFIED_GEOMETRIC_EQUALITY,
                                    measuredValue = pitches.first(),
                                    isSymmetricOrEqualVerified = true,
                                    description = "Verified equal horizontal hole spacing (${pitches.size}× ${fmt(pitches.first())})."
                                )
                            )
                        } else {
                            findings.add(
                                CompletenessFinding(
                                    axisOrFeature = "HORIZONTAL_HOLE_PITCH",
                                    category = DimensionCompletenessCategory.INFERRED_UNVERIFIED,
                                    measuredValue = pitches.first(),
                                    isSymmetricOrEqualVerified = false,
                                    description = "Unequal horizontal hole spacing (${pitches.joinToString(", ") { fmt(it) }}); individual intervals required."
                                )
                            )
                        }
                    }
                }
            }

            val colsByX = circles.groupBy { (it.center.x * 10.0).roundToLong() }
            val rightCol = colsByX.maxByOrNull { it.key }?.value?.sortedBy { it.center.y }.orEmpty()
            if (rightCol.isNotEmpty()) {
                val bottomMargin = rightCol.first().center.y - overallBounds.minY
                val topMargin = overallBounds.maxY - rightCol.last().center.y
                val vertMarginsEqual = abs(bottomMargin - topMargin) <= STRICT_GEOMETRIC_EQUALITY_TOL

                if (vertMarginsEqual && bottomMargin > 1e-2) {
                    findings.add(
                        CompletenessFinding(
                            axisOrFeature = "VERTICAL_MARGINS",
                            category = DimensionCompletenessCategory.VERIFIED_GEOMETRIC_EQUALITY,
                            measuredValue = bottomMargin,
                            isSymmetricOrEqualVerified = true,
                            description = "Vertical bottom/top hole margins verified symmetric (${fmt(bottomMargin)}); top margin is arithmetically derivable."
                        )
                    )
                } else if (bottomMargin > 1e-2 && topMargin > 1e-2) {
                    findings.add(
                        CompletenessFinding(
                            axisOrFeature = "VERTICAL_MARGINS",
                            category = DimensionCompletenessCategory.INFERRED_UNVERIFIED,
                            measuredValue = topMargin,
                            isSymmetricOrEqualVerified = false,
                            description = "Asymmetric vertical margins (bottom=${fmt(bottomMargin)}, top=${fmt(topMargin)}); symmetry not assumed."
                        )
                    )
                }

                if (rightCol.size >= 2) {
                    val vPitches = (0 until rightCol.size - 1).map { i ->
                        rightCol[i + 1].center.y - rightCol[i].center.y
                    }.filter { it > 1e-2 }

                    if (vPitches.size >= 2) {
                        val maxDiff = (vPitches.maxOrNull() ?: 0.0) - (vPitches.minOrNull() ?: 0.0)
                        if (maxDiff <= STRICT_GEOMETRIC_EQUALITY_TOL) {
                            findings.add(
                                CompletenessFinding(
                                    axisOrFeature = "VERTICAL_HOLE_PITCH",
                                    category = DimensionCompletenessCategory.VERIFIED_GEOMETRIC_EQUALITY,
                                    measuredValue = vPitches.first(),
                                    isSymmetricOrEqualVerified = true,
                                    description = "Verified equal vertical hole spacing (${vPitches.size}× ${fmt(vPitches.first())})."
                                )
                            )
                        } else {
                            findings.add(
                                CompletenessFinding(
                                    axisOrFeature = "VERTICAL_HOLE_PITCH",
                                    category = DimensionCompletenessCategory.INFERRED_UNVERIFIED,
                                    measuredValue = vPitches.first(),
                                    isSymmetricOrEqualVerified = false,
                                    description = "Unequal vertical hole spacing (${vPitches.joinToString(", ") { fmt(it) }}); individual intervals required."
                                )
                            )
                        }
                    }
                }
            }
        }

        // Repeated hole & fillet groups
        val diaGroups = circles.groupBy { (it.diameter * 10000.0).roundToLong() }
        for ((_, grp) in diaGroups) {
            if (grp.size > 1) {
                findings.add(
                    CompletenessFinding(
                        axisOrFeature = "HOLE_PATTERN_DIAMETER",
                        category = DimensionCompletenessCategory.VERIFIED_GEOMETRIC_EQUALITY,
                        measuredValue = grp.first().diameter,
                        isSymmetricOrEqualVerified = true,
                        description = "Verified ${grp.size}× equivalent holes of ⌀${fmt(grp.first().diameter)}."
                    )
                )
            }
        }

        val radGroups = arcs.groupBy { (it.radius * 10000.0).roundToLong() }
        for ((_, grp) in radGroups) {
            if (grp.size > 1) {
                findings.add(
                    CompletenessFinding(
                        axisOrFeature = "ARC_PATTERN_RADIUS",
                        category = DimensionCompletenessCategory.VERIFIED_GEOMETRIC_EQUALITY,
                        measuredValue = grp.first().radius,
                        isSymmetricOrEqualVerified = true,
                        description = "Verified ${grp.size}× equivalent arcs/fillets of R${fmt(grp.first().radius)}."
                    )
                )
            }
        }

        return findings
    }

    /**
     * Staged arrangement, hierarchy band placement, multi-primitive collision resolution,
     * and layout scoring pipeline (Section 10.4).
     */
    fun alignAndArrangeDimensions(
        candidateDimensions: List<CadEntity.DimensionEntity>,
        overallBounds: BoundingBox2D,
        existingManualDimensions: List<CadEntity.DimensionEntity>,
        obstacleCircles: List<CadEntity.CircleEntity> = emptyList(),
        obstacleArcs: List<CadEntity.ArcEntity> = emptyList(),
        obstacleLines: List<CadEntity.LineEntity> = emptyList(),
        completenessFindings: List<CompletenessFinding> = emptyList(),
        edgeDiagnostics: List<EdgeDimensionDiagnostic> = emptyList(),
        arrangementPreference: DimensionArrangementPreference = DimensionArrangementPreference.AUTO_CHAIN,
        densityPolicy: QDimDensityPolicy = QDimDensityPolicy.BALANCED,
        style: DimensionStyleConfig = DimensionStyleConfig(),
        precision: PrecisionFormatConfig = PrecisionFormatConfig(),
        storageUnit: CadUnit = CadUnit.MM,
        displayUnit: CadUnit = CadUnit.MM,
        viewport: ViewportTransform = ViewportTransform(pixelsPerModelUnit = 3.0f)
    ): DimensionAlignmentReport {
        val requiredEdges = edgeDiagnostics.filter {
            it.status == EdgeDimensionStatus.EMITTED_EXPLICIT ||
                it.status == EdgeDimensionStatus.COVERED_BY_PRIMARY_OVERALL ||
                it.status == EdgeDimensionStatus.COVERED_BY_MANUAL_DIMENSION
        }
        val genScore = if (edgeDiagnostics.isEmpty()) {
            100.0
        } else {
            val coveredCount = edgeDiagnostics.count {
                it.status == EdgeDimensionStatus.EMITTED_EXPLICIT ||
                    it.status == EdgeDimensionStatus.COVERED_BY_PRIMARY_OVERALL ||
                    it.status == EdgeDimensionStatus.DERIVABLE_FROM_OVERALL_AND_FILLETS ||
                    it.status == EdgeDimensionStatus.COVERED_BY_MANUAL_DIMENSION ||
                    (densityPolicy == QDimDensityPolicy.SPARSE && it.status == EdgeDimensionStatus.SUPPRESSED_BY_SPARSE_POLICY)
            }
            (coveredCount.toDouble() / edgeDiagnostics.size.toDouble()) * 100.0
        }

        if (candidateDimensions.isEmpty()) {
            return DimensionAlignmentReport(
                placements = emptyList(),
                completenessFindings = completenessFindings,
                unresolvedWarnings = emptyList(),
                layoutScore = 100.0,
                arrangementPreference = arrangementPreference,
                densityPolicy = densityPolicy,
                edgeDiagnostics = edgeDiagnostics,
                generationCompletenessScore = genScore,
                layoutQualityScore = 100.0
            )
        }

        val spanScale = max(overallBounds.width, overallBounds.height).coerceAtLeast(20.0)
        // Band 1 (closest to geometry): Secondary & local feature-location dimensions
        val band1Offset = max(style.baselineTrackSpacingMm, (spanScale * 0.085).coerceIn(8.0, 20.0))
        // Inter-band spacing for parallel tracks and outermost Primary overall envelope
        val interBandSpacing = max(style.baselineTrackSpacingMm * 0.9, (spanScale * 0.075).coerceIn(7.5, 16.0))
        // Primary overall dimensions always sit in an outer band relative to inner bands
        val primaryEnvelopeOffset = band1Offset + interBandSpacing

        val placements = mutableListOf<AlignedDimensionPlacementResult>()
        val unresolvedWarnings = mutableListOf<String>()
        val occupiedBoxes = mutableListOf<BoundingBox2D>()
        val occupiedSegments = mutableListOf<Pair<Point2D, Point2D>>()

        // Seed occupied boxes with existing manual dimensions so auto-dimensions never collide with them
        existingManualDimensions.forEach { manual ->
            occupiedBoxes.add(estimateTextBoundingBox(manual, spanScale, style))
        }

        // 1. Arrange Horizontal Linear Dimensions (Secondary/Local Feature Band vs Primary Overall Outer Band)
        val horizSecondaryRaw = candidateDimensions.filter {
            it.dimensionType == DimensionType.LINEAR_HORIZONTAL &&
                it.hierarchyTier != DimensionHierarchyTier.PRIMARY_OVERALL
        }.sortedWith(
            compareBy<CadEntity.DimensionEntity> { it.hierarchyTier.ordinal }
                .thenBy { min(it.anchor1.x, it.anchor2.x) }
        )

        val horizPrimaryRaw = candidateDimensions.filter {
            it.dimensionType == DimensionType.LINEAR_HORIZONTAL &&
                it.hierarchyTier == DimensionHierarchyTier.PRIMARY_OVERALL
        }

        val bottomDatumTag = "LEFT_DATUM_X=${fmt(overallBounds.minX)}"

        if (arrangementPreference == DimensionArrangementPreference.BASELINE && horizSecondaryRaw.isNotEmpty()) {
            val featureTargetsX = horizSecondaryRaw
                .flatMap { listOf(it.anchor1.x, it.anchor2.x) }
                .filter { it > overallBounds.minX + 1e-2 && it < overallBounds.maxX - 1e-2 }
                .sorted()
                .distinctBy { (it * 100.0).roundToLong() }

            featureTargetsX.forEachIndexed { idx, targetX ->
                val trackY = overallBounds.minY - band1Offset - (idx * interBandSpacing)
                val baseDim = horizSecondaryRaw.first().copy(
                    id = "${horizSecondaryRaw.first().id}_base_h_$idx",
                    anchor1 = Point2D(overallBounds.minX, overallBounds.minY),
                    anchor2 = Point2D(targetX, overallBounds.minY),
                    dimensionLinePoint = Point2D((overallBounds.minX + targetX) * 0.5, trackY),
                    hierarchyTier = DimensionHierarchyTier.SECONDARY_FEATURE
                )
                val result = buildLinearPlacementResult(
                    dim = baseDim,
                    arrangementMode = DimensionArrangementMode.BASELINE,
                    groupId = "HORIZ_BASELINE_BOTTOM",
                    datumReference = bottomDatumTag,
                    overallBounds = overallBounds,
                    interBandSpacing = interBandSpacing,
                    occupiedBoxes = occupiedBoxes,
                    occupiedSegments = occupiedSegments,
                    obstacleLines = obstacleLines,
                    style = style,
                    precision = precision,
                    storageUnit = storageUnit,
                    displayUnit = displayUnit,
                    viewport = viewport
                )
                placements.add(result)
            }
        } else {
            val bottomChainY = overallBounds.minY - band1Offset
            val topInnerChainY = overallBounds.maxY + band1Offset
            horizSecondaryRaw.forEachIndexed { idx, dim ->
                val midX = (dim.anchor1.x + dim.anchor2.x) * 0.5
                val avgAnchorY = (dim.anchor1.y + dim.anchor2.y) * 0.5
                // Place upper local features (top shoulders) on the upper inner band and lower/recess features on the bottom band
                val preferredTrackY = when {
                    dim.hierarchyTier == DimensionHierarchyTier.TERTIARY_LOCAL &&
                        avgAnchorY >= overallBounds.maxY - 1.0 -> topInnerChainY
                    dim.hierarchyTier == DimensionHierarchyTier.TERTIARY_LOCAL &&
                        avgAnchorY > overallBounds.minY + 1.0 && avgAnchorY < overallBounds.maxY - 1.0 -> bottomChainY
                    else -> bottomChainY
                }
                val alignedDim = dim.copy(dimensionLinePoint = Point2D(midX, preferredTrackY))
                val isVerifiedEqual = completenessFindings.any {
                    it.axisOrFeature == "HORIZONTAL_HOLE_PITCH" && it.isSymmetricOrEqualVerified
                }
                val mode = if (isVerifiedEqual && horizSecondaryRaw.size > 2 && idx > 0 &&
                    dim.hierarchyTier == DimensionHierarchyTier.SECONDARY_FEATURE
                ) {
                    DimensionArrangementMode.SYMMETRIC_OR_EQUAL_SPACING
                } else {
                    DimensionArrangementMode.CONTINUOUS_CHAIN
                }
                val result = buildLinearPlacementResult(
                    dim = alignedDim,
                    arrangementMode = mode,
                    groupId = if (preferredTrackY >= overallBounds.maxY) "HORIZ_CHAIN_TOP_INNER" else "HORIZ_CHAIN_BOTTOM",
                    datumReference = bottomDatumTag,
                    overallBounds = overallBounds,
                    interBandSpacing = interBandSpacing,
                    occupiedBoxes = occupiedBoxes,
                    occupiedSegments = occupiedSegments,
                    obstacleLines = obstacleLines,
                    style = style,
                    precision = precision,
                    storageUnit = storageUnit,
                    displayUnit = displayUnit,
                    viewport = viewport
                )
                placements.add(result)
            }
        }

        // Primary Overall Horizontal Dimensions: placed in Outer Envelope Band (strictly outside inner bands)
        val maxUpperPlacedY = placements
            .filter { it.dimension.dimensionType == DimensionType.LINEAR_HORIZONTAL && it.dimLineStartModel.y >= overallBounds.maxY }
            .maxOfOrNull { it.dimLineStartModel.y }
        val baseOuterTopY = if (maxUpperPlacedY != null) {
            max(overallBounds.maxY + primaryEnvelopeOffset, maxUpperPlacedY + interBandSpacing)
        } else {
            overallBounds.maxY + primaryEnvelopeOffset
        }

        horizPrimaryRaw.forEachIndexed { idx, dim ->
            val midX = (dim.anchor1.x + dim.anchor2.x) * 0.5
            val outerTopY = baseOuterTopY + (idx * interBandSpacing)
            val alignedDim = dim.copy(dimensionLinePoint = Point2D(midX, outerTopY))
            val result = buildLinearPlacementResult(
                dim = alignedDim,
                arrangementMode = DimensionArrangementMode.OVERALL,
                groupId = "PRIMARY_ENVELOPE_HORIZ",
                datumReference = "ENVELOPE_WIDTH_${fmt(overallBounds.minX)}_${fmt(overallBounds.maxX)}",
                overallBounds = overallBounds,
                interBandSpacing = interBandSpacing,
                occupiedBoxes = occupiedBoxes,
                occupiedSegments = occupiedSegments,
                obstacleLines = obstacleLines,
                style = style,
                precision = precision,
                storageUnit = storageUnit,
                displayUnit = displayUnit,
                viewport = viewport
            )
            placements.add(result)
        }

        // 2. Arrange Vertical Linear Dimensions (Secondary/Local Feature Band vs Primary Overall Outer Band)
        val vertSecondaryRaw = candidateDimensions.filter {
            it.dimensionType == DimensionType.LINEAR_VERTICAL &&
                it.hierarchyTier != DimensionHierarchyTier.PRIMARY_OVERALL
        }.sortedWith(
            compareBy<CadEntity.DimensionEntity> { it.hierarchyTier.ordinal }
                .thenBy { min(it.anchor1.y, it.anchor2.y) }
        )

        val vertPrimaryRaw = candidateDimensions.filter {
            it.dimensionType == DimensionType.LINEAR_VERTICAL &&
                it.hierarchyTier == DimensionHierarchyTier.PRIMARY_OVERALL
        }

        val verticalDatumTag = "BOTTOM_DATUM_Y=${fmt(overallBounds.minY)}"

        if (arrangementPreference == DimensionArrangementPreference.BASELINE && vertSecondaryRaw.isNotEmpty()) {
            val featureTargetsY = vertSecondaryRaw
                .flatMap { listOf(it.anchor1.y, it.anchor2.y) }
                .filter { it > overallBounds.minY + 1e-2 && it < overallBounds.maxY - 1e-2 }
                .sorted()
                .distinctBy { (it * 100.0).roundToLong() }

            featureTargetsY.forEachIndexed { idx, targetY ->
                val trackX = overallBounds.maxX + band1Offset + (idx * interBandSpacing)
                val baseDim = vertSecondaryRaw.first().copy(
                    id = "${vertSecondaryRaw.first().id}_base_v_$idx",
                    anchor1 = Point2D(overallBounds.maxX, overallBounds.minY),
                    anchor2 = Point2D(overallBounds.maxX, targetY),
                    dimensionLinePoint = Point2D(trackX, (overallBounds.minY + targetY) * 0.5),
                    hierarchyTier = DimensionHierarchyTier.SECONDARY_FEATURE
                )
                val result = buildLinearPlacementResult(
                    dim = baseDim,
                    arrangementMode = DimensionArrangementMode.BASELINE,
                    groupId = "VERT_BASELINE_RIGHT",
                    datumReference = verticalDatumTag,
                    overallBounds = overallBounds,
                    interBandSpacing = interBandSpacing,
                    occupiedBoxes = occupiedBoxes,
                    occupiedSegments = occupiedSegments,
                    obstacleLines = obstacleLines,
                    style = style,
                    precision = precision,
                    storageUnit = storageUnit,
                    displayUnit = displayUnit,
                    viewport = viewport
                )
                placements.add(result)
            }
        } else {
            val rightChainX = overallBounds.maxX + band1Offset
            val leftInnerChainX = overallBounds.minX - band1Offset
            vertSecondaryRaw.forEachIndexed { idx, dim ->
                val midY = (dim.anchor1.y + dim.anchor2.y) * 0.5
                val avgAnchorX = (dim.anchor1.x + dim.anchor2.x) * 0.5
                val preferredTrackX = if (dim.hierarchyTier == DimensionHierarchyTier.TERTIARY_LOCAL &&
                    avgAnchorX <= overallBounds.center.x
                ) {
                    leftInnerChainX
                } else {
                    rightChainX
                }
                val alignedDim = dim.copy(dimensionLinePoint = Point2D(preferredTrackX, midY))
                val isVerifiedEqual = completenessFindings.any {
                    it.axisOrFeature == "VERTICAL_HOLE_PITCH" && it.isSymmetricOrEqualVerified
                }
                val mode = if (isVerifiedEqual && vertSecondaryRaw.size > 2 && idx > 0 &&
                    dim.hierarchyTier == DimensionHierarchyTier.SECONDARY_FEATURE
                ) {
                    DimensionArrangementMode.SYMMETRIC_OR_EQUAL_SPACING
                } else {
                    DimensionArrangementMode.CONTINUOUS_CHAIN
                }
                val result = buildLinearPlacementResult(
                    dim = alignedDim,
                    arrangementMode = mode,
                    groupId = if (preferredTrackX <= overallBounds.minX) "VERT_CHAIN_LEFT_INNER" else "VERT_CHAIN_RIGHT",
                    datumReference = verticalDatumTag,
                    overallBounds = overallBounds,
                    interBandSpacing = interBandSpacing,
                    occupiedBoxes = occupiedBoxes,
                    occupiedSegments = occupiedSegments,
                    obstacleLines = obstacleLines,
                    style = style,
                    precision = precision,
                    storageUnit = storageUnit,
                    displayUnit = displayUnit,
                    viewport = viewport
                )
                placements.add(result)
            }
        }

        // Primary Overall Vertical Dimensions: placed in Outer Envelope Band (strictly outside inner bands)
        val minLeftPlacedX = placements
            .filter { it.dimension.dimensionType == DimensionType.LINEAR_VERTICAL && it.dimLineStartModel.x <= overallBounds.minX }
            .minOfOrNull { it.dimLineStartModel.x }
        val baseOuterLeftX = if (minLeftPlacedX != null) {
            min(overallBounds.minX - primaryEnvelopeOffset, minLeftPlacedX - interBandSpacing)
        } else {
            overallBounds.minX - primaryEnvelopeOffset
        }

        vertPrimaryRaw.forEachIndexed { idx, dim ->
            val midY = (dim.anchor1.y + dim.anchor2.y) * 0.5
            val outerLeftX = baseOuterLeftX - (idx * interBandSpacing)
            val alignedDim = dim.copy(dimensionLinePoint = Point2D(outerLeftX, midY))
            val result = buildLinearPlacementResult(
                dim = alignedDim,
                arrangementMode = DimensionArrangementMode.OVERALL,
                groupId = "PRIMARY_ENVELOPE_VERT",
                datumReference = "ENVELOPE_HEIGHT_${fmt(overallBounds.minY)}_${fmt(overallBounds.maxY)}",
                overallBounds = overallBounds,
                interBandSpacing = interBandSpacing,
                occupiedBoxes = occupiedBoxes,
                occupiedSegments = occupiedSegments,
                obstacleLines = obstacleLines,
                style = style,
                precision = precision,
                storageUnit = storageUnit,
                displayUnit = displayUnit,
                viewport = viewport
            )
            placements.add(result)
        }

        // 3. Ordinate Dimensions (if present or requested by Ordinate mode)
        val ordinateDims = candidateDimensions.filter {
            it.dimensionType == DimensionType.ORDINATE_X || it.dimensionType == DimensionType.ORDINATE_Y
        }
        for (ordDim in ordinateDims) {
            val plan = AdaptiveAnnotationRenderer.computeRenderPlan(
                dim = ordDim,
                precision = precision,
                storageUnit = storageUnit,
                displayUnit = displayUnit,
                style = style,
                viewport = viewport
            )
            val box = estimatePlanTextBox(plan, spanScale)
            occupiedBoxes.add(box)
            placements.add(
                AlignedDimensionPlacementResult(
                    dimensionId = ordDim.id,
                    dimension = ordDim,
                    arrangementMode = DimensionArrangementMode.ORDINATE,
                    groupId = "ORDINATE_DATUM_GROUP",
                    hierarchyTier = ordDim.hierarchyTier,
                    datumReference = "ORIGIN_DATUM_(${fmt(overallBounds.minX)},${fmt(overallBounds.minY)})",
                    dimLineStartModel = plan.dimLineStartModel,
                    dimLineEndModel = plan.dimLineEndModel,
                    ext1StartModel = plan.ext1StartModel,
                    ext1EndModel = plan.ext1EndModel,
                    ext2StartModel = plan.ext2StartModel,
                    ext2EndModel = plan.ext2EndModel,
                    textAnchorModel = plan.textPositionModel,
                    textRotationDeg = plan.textRotationDeg,
                    textPlacementMode = TextPlacementMode.EXTERNAL_OFFSET,
                    arrowPlacementMode = ArrowPlacementMode.LEADER_BASED,
                    completenessCategory = DimensionCompletenessCategory.EXPLICITLY_PRESENT,
                    collisionClearanceStatus = CollisionClearanceStatus.CLEAR
                )
            )
        }

        // 4. Arrange Tertiary & Geometric Callouts (Radial, Diametric, Aligned, Angular) with multi-primitive collision solver
        val calloutDims = candidateDimensions.filter {
            it.dimensionType != DimensionType.LINEAR_HORIZONTAL &&
                it.dimensionType != DimensionType.LINEAR_VERTICAL &&
                it.dimensionType != DimensionType.ORDINATE_X &&
                it.dimensionType != DimensionType.ORDINATE_Y
        }

        for (dim in calloutDims) {
            var adjusted = dim
            var plan = AdaptiveAnnotationRenderer.computeRenderPlan(
                dim = adjusted,
                precision = precision,
                storageUnit = storageUnit,
                displayUnit = displayUnit,
                style = style,
                viewport = viewport
            )
            var candidateBox = estimatePlanTextBox(plan, spanScale)
            var attempt = 0
            var wasRelocated = false

            while (attempt < 10 && hasMultiPrimitiveCollision(
                    dim = adjusted,
                    plan = plan,
                    candidateBox = candidateBox,
                    occupiedBoxes = occupiedBoxes,
                    occupiedSegments = occupiedSegments,
                    obstacleCircles = obstacleCircles,
                    obstacleArcs = obstacleArcs,
                    obstacleLines = obstacleLines,
                    overallBounds = overallBounds
                )
            ) {
                attempt++
                wasRelocated = true
                when (adjusted.dimensionType) {
                    DimensionType.DIAMETER -> {
                        val radius = adjusted.anchor1.distanceTo(adjusted.anchor2)
                        val baseAngle = adjusted.anchor1.angleToDeg(adjusted.anchor2)
                        val delta = if (attempt % 2 == 1) 20.0 * ((attempt + 1) / 2) else -20.0 * (attempt / 2)
                        val newAngle = baseAngle + delta
                        val newDist = adjusted.anchor1.distanceTo(adjusted.dimensionLinePoint) + (attempt * 4.0)
                        adjusted = adjusted.copy(
                            anchor2 = adjusted.anchor1.polarOffset(radius, newAngle),
                            dimensionLinePoint = adjusted.anchor1.polarOffset(newDist, newAngle)
                        )
                    }
                    DimensionType.RADIUS -> {
                        val rayAngle = adjusted.anchor1.angleToDeg(adjusted.anchor2)
                        val newDist = adjusted.anchor1.distanceTo(adjusted.dimensionLinePoint) + (attempt * 4.5)
                        adjusted = adjusted.copy(
                            dimensionLinePoint = adjusted.anchor1.polarOffset(newDist, rayAngle)
                        )
                    }
                    DimensionType.ALIGNED -> {
                        val mid = adjusted.anchor1.midpointTo(adjusted.anchor2)
                        val lineAng = adjusted.anchor1.angleToDeg(adjusted.anchor2)
                        val sign = if (attempt % 2 == 1) 1.0 else -1.0
                        val normalRad = Math.toRadians(lineAng + 90.0 * sign)
                        val offsetDist = 9.0 + (attempt * 4.0)
                        adjusted = adjusted.copy(
                            dimensionLinePoint = Point2D(
                                mid.x + offsetDist * cos(normalRad),
                                mid.y + offsetDist * sin(normalRad)
                            )
                        )
                    }
                    else -> {
                        val shiftAngle = 30.0 * attempt
                        val dist = adjusted.anchor1.distanceTo(adjusted.dimensionLinePoint) + (attempt * 3.5)
                        adjusted = adjusted.copy(
                            dimensionLinePoint = adjusted.anchor1.polarOffset(dist, shiftAngle)
                        )
                    }
                }
                plan = AdaptiveAnnotationRenderer.computeRenderPlan(
                    dim = adjusted,
                    precision = precision,
                    storageUnit = storageUnit,
                    displayUnit = displayUnit,
                    style = style,
                    viewport = viewport
                )
                candidateBox = estimatePlanTextBox(plan, spanScale)
            }

            val stillColliding = hasMultiPrimitiveCollision(
                dim = adjusted,
                plan = plan,
                candidateBox = candidateBox,
                occupiedBoxes = occupiedBoxes,
                occupiedSegments = occupiedSegments,
                obstacleCircles = obstacleCircles,
                obstacleArcs = obstacleArcs,
                obstacleLines = obstacleLines,
                overallBounds = overallBounds
            )

            val clearanceStatus = when {
                stillColliding -> CollisionClearanceStatus.UNRESOLVED_CROWDED
                wasRelocated -> CollisionClearanceStatus.RESOLVED_BY_RELOCATION
                else -> CollisionClearanceStatus.CLEAR
            }

            val flags = mutableListOf<String>()
            if (stillColliding) {
                val warn = "UNRESOLVED_PLACEMENT: Crowded region for ${plan.labelText} near (${fmt(plan.textPositionModel.x)}, ${fmt(plan.textPositionModel.y)}); exact value preserved."
                unresolvedWarnings.add(warn)
                flags.add(warn)
            }

            occupiedBoxes.add(candidateBox)
            if (plan.isRadialOrDiametric) {
                val rim = plan.rimContactPointModel ?: adjusted.anchor2
                val sh = plan.leaderShoulderModel ?: adjusted.dimensionLinePoint
                val end = plan.leaderLandingEndModel ?: sh
                occupiedSegments.add(rim to sh)
                occupiedSegments.add(sh to end)
            } else {
                occupiedSegments.add(plan.dimLineStartModel to plan.dimLineEndModel)
            }

            val mode = when {
                adjusted.customPrefix.contains("×") -> DimensionArrangementMode.REPEATED_FEATURE_CALLOUT
                adjusted.dimensionType == DimensionType.DIAMETER || adjusted.dimensionType == DimensionType.RADIUS ->
                    DimensionArrangementMode.RADIAL_DIAMETER_CALLOUT
                adjusted.dimensionType == DimensionType.ANGULAR -> DimensionArrangementMode.ANGULAR
                else -> DimensionArrangementMode.ALIGNED
            }

            val arrowMode = when {
                plan.isRadialOrDiametric -> ArrowPlacementMode.LEADER_BASED
                plan.arrowsInside -> ArrowPlacementMode.INSIDE
                else -> ArrowPlacementMode.OUTSIDE
            }

            val textMode = when {
                plan.isRadialOrDiametric -> TextPlacementMode.LEADER_SHELF
                plan.textInside -> TextPlacementMode.CENTERED_INTERNAL
                else -> TextPlacementMode.EXTERNAL_OFFSET
            }

            placements.add(
                AlignedDimensionPlacementResult(
                    dimensionId = adjusted.id,
                    dimension = adjusted,
                    arrangementMode = mode,
                    groupId = "FEATURE_CALLOUT_${adjusted.dimensionType.name}",
                    hierarchyTier = adjusted.hierarchyTier,
                    datumReference = "FEATURE_CENTER_(${fmt(adjusted.anchor1.x)},${fmt(adjusted.anchor1.y)})",
                    dimLineStartModel = plan.dimLineStartModel,
                    dimLineEndModel = plan.dimLineEndModel,
                    ext1StartModel = plan.ext1StartModel,
                    ext1EndModel = plan.ext1EndModel,
                    ext2StartModel = plan.ext2StartModel,
                    ext2EndModel = plan.ext2EndModel,
                    textAnchorModel = plan.textPositionModel,
                    textRotationDeg = plan.textRotationDeg,
                    textPlacementMode = textMode,
                    arrowPlacementMode = arrowMode,
                    completenessCategory = if (adjusted.customPrefix.contains("×")) {
                        DimensionCompletenessCategory.VERIFIED_GEOMETRIC_EQUALITY
                    } else {
                        DimensionCompletenessCategory.EXPLICITLY_PRESENT
                    },
                    collisionClearanceStatus = clearanceStatus,
                    reviewFlags = flags
                )
            )
        }

        // Compute overall layout quality score (0..100)
        val unresolvedPenalty = unresolvedWarnings.size * 15.0
        val relocatedPenalty = placements.count { it.collisionClearanceStatus == CollisionClearanceStatus.RESOLVED_BY_RELOCATION } * 2.0
        val score = (100.0 - unresolvedPenalty - relocatedPenalty).coerceIn(10.0, 100.0)

        return DimensionAlignmentReport(
            placements = placements,
            completenessFindings = completenessFindings,
            unresolvedWarnings = unresolvedWarnings,
            layoutScore = score,
            arrangementPreference = arrangementPreference,
            densityPolicy = densityPolicy,
            edgeDiagnostics = edgeDiagnostics,
            generationCompletenessScore = genScore,
            layoutQualityScore = score
        )
    }

    /**
     * Section 32 — Coherent Whole-Dimension Assembly Relocation.
     * Never moves `textAnchorModel` alone while leaving `dimLineStartModel`/`EndModel` and extension lines behind.
     * When a collision is detected, shifts the entire dimension assembly to a clean parallel offset track
     * and recomputes `AdaptiveAnnotationRenderer.computeRenderPlan(currentDim)` so extension lines,
     * dimension line, arrowheads, and text label stay geometrically attached.
     */
    private fun buildLinearPlacementResult(
        dim: CadEntity.DimensionEntity,
        arrangementMode: DimensionArrangementMode,
        groupId: String,
        datumReference: String,
        overallBounds: BoundingBox2D,
        interBandSpacing: Double,
        occupiedBoxes: MutableList<BoundingBox2D>,
        occupiedSegments: MutableList<Pair<Point2D, Point2D>>,
        obstacleLines: List<CadEntity.LineEntity>,
        style: DimensionStyleConfig,
        precision: PrecisionFormatConfig,
        storageUnit: CadUnit,
        displayUnit: CadUnit,
        viewport: ViewportTransform
    ): AlignedDimensionPlacementResult {
        val spanScale = max(abs(dim.anchor2.x - dim.anchor1.x), abs(dim.anchor2.y - dim.anchor1.y)).coerceAtLeast(10.0)
        var currentDim = dim
        var currentPlan = AdaptiveAnnotationRenderer.computeRenderPlan(
            dim = currentDim,
            precision = precision,
            storageUnit = storageUnit,
            displayUnit = displayUnit,
            style = style,
            viewport = viewport
        )

        var candidateBox = estimatePlanTextBox(currentPlan, spanScale)
        var status = CollisionClearanceStatus.CLEAR
        val reviewFlags = mutableListOf<String>()

        fun collidesWithOccupiedOrGeometry(plan: AdaptiveDimensionRenderPlan, box: BoundingBox2D): Boolean {
            if (occupiedBoxes.any { it.intersects(box, tolerance = 0.8) }) return true
            for (l in obstacleLines) {
                if (l.id in currentDim.sourceEntityIds) continue
                val dText = CadGeometryKernel.distancePointToSegment(plan.textPositionModel, l.start, l.end)
                if (dText < 2.0) return true
            }
            return false
        }

        val initialLinePt = dim.dimensionLinePoint
        val trackStep = max(interBandSpacing * 0.85, 7.5)
        var attempt = 0
        while (attempt < 6 && collidesWithOccupiedOrGeometry(currentPlan, candidateBox)) {
            attempt++
            status = CollisionClearanceStatus.RESOLVED_BY_RELOCATION
            currentDim = if (dim.dimensionType == DimensionType.LINEAR_HORIZONTAL) {
                val outwardSign = if (initialLinePt.y >= overallBounds.center.y) 1.0 else -1.0
                dim.copy(
                    dimensionLinePoint = Point2D(
                        initialLinePt.x,
                        initialLinePt.y + outwardSign * (attempt * trackStep)
                    )
                )
            } else {
                val outwardSign = if (initialLinePt.x >= overallBounds.center.x) 1.0 else -1.0
                dim.copy(
                    dimensionLinePoint = Point2D(
                        initialLinePt.x + outwardSign * (attempt * trackStep),
                        initialLinePt.y
                    )
                )
            }
            currentPlan = AdaptiveAnnotationRenderer.computeRenderPlan(
                dim = currentDim,
                precision = precision,
                storageUnit = storageUnit,
                displayUnit = displayUnit,
                style = style,
                viewport = viewport
            )
            candidateBox = estimatePlanTextBox(currentPlan, spanScale)
        }

        if (occupiedBoxes.any { it.intersects(candidateBox, tolerance = 0.4) }) {
            status = CollisionClearanceStatus.UNRESOLVED_CROWDED
            reviewFlags.add(
                "UNRESOLVED_PLACEMENT: Dense linear chain label near (${fmt(currentPlan.textPositionModel.x)}, ${fmt(currentPlan.textPositionModel.y)})"
            )
        }

        occupiedBoxes.add(candidateBox)
        occupiedSegments.add(currentPlan.dimLineStartModel to currentPlan.dimLineEndModel)
        occupiedSegments.add(currentPlan.ext1StartModel to currentPlan.ext1EndModel)
        occupiedSegments.add(currentPlan.ext2StartModel to currentPlan.ext2EndModel)

        return AlignedDimensionPlacementResult(
            dimensionId = currentDim.id,
            dimension = currentDim,
            arrangementMode = arrangementMode,
            groupId = groupId,
            hierarchyTier = currentDim.hierarchyTier,
            datumReference = datumReference,
            dimLineStartModel = currentPlan.dimLineStartModel,
            dimLineEndModel = currentPlan.dimLineEndModel,
            ext1StartModel = currentPlan.ext1StartModel,
            ext1EndModel = currentPlan.ext1EndModel,
            ext2StartModel = currentPlan.ext2StartModel,
            ext2EndModel = currentPlan.ext2EndModel,
            textAnchorModel = currentPlan.textPositionModel,
            textRotationDeg = currentPlan.textRotationDeg,
            textPlacementMode = if (currentPlan.textInside) TextPlacementMode.CENTERED_INTERNAL else TextPlacementMode.EXTERNAL_OFFSET,
            arrowPlacementMode = if (currentPlan.arrowsInside) ArrowPlacementMode.INSIDE else ArrowPlacementMode.OUTSIDE,
            completenessCategory = if (arrangementMode == DimensionArrangementMode.SYMMETRIC_OR_EQUAL_SPACING) {
                DimensionCompletenessCategory.VERIFIED_GEOMETRIC_EQUALITY
            } else {
                DimensionCompletenessCategory.EXPLICITLY_PRESENT
            },
            collisionClearanceStatus = status,
            reviewFlags = reviewFlags
        )
    }

    /**
     * Evaluates collisions across text boxes, leader segments, dimension lines, extension lines,
     * circles, arcs, and part boundaries (Section 10.4).
     */
    private fun hasMultiPrimitiveCollision(
        dim: CadEntity.DimensionEntity,
        plan: AdaptiveDimensionRenderPlan,
        candidateBox: BoundingBox2D,
        occupiedBoxes: List<BoundingBox2D>,
        occupiedSegments: List<Pair<Point2D, Point2D>>,
        obstacleCircles: List<CadEntity.CircleEntity>,
        obstacleArcs: List<CadEntity.ArcEntity>,
        obstacleLines: List<CadEntity.LineEntity>,
        overallBounds: BoundingBox2D
    ): Boolean {
        if (occupiedBoxes.any { it.intersects(candidateBox, tolerance = 1.2) }) return true

        if (plan.isRadialOrDiametric) {
            val rim = plan.rimContactPointModel ?: dim.anchor2
            val sh = plan.leaderShoulderModel ?: dim.dimensionLinePoint
            val end = plan.leaderLandingEndModel ?: sh

            for (c in obstacleCircles) {
                if (c.id in dim.sourceEntityIds) continue
                val dRay = CadGeometryKernel.distancePointToSegment(c.center, rim, sh)
                val dShelf = CadGeometryKernel.distancePointToSegment(c.center, sh, end)
                if (dRay < c.radius + 2.5 || dShelf < c.radius + 2.5) return true
                val cBox = BoundingBox2D(
                    c.center.x - c.radius - 2.0,
                    c.center.y - c.radius - 2.0,
                    c.center.x + c.radius + 2.0,
                    c.center.y + c.radius + 2.0
                )
                if (candidateBox.intersects(cBox, tolerance = 0.5)) return true
            }

            for (a in obstacleArcs) {
                if (a.center.distanceTo(dim.anchor1) < 0.5) continue
                if (a.midPoint.distanceTo(sh) < a.radius + 2.5) return true
            }

            if (abs(sh.y - overallBounds.maxY) < 3.5 || abs(sh.y - overallBounds.minY) < 3.5) return true

            for ((s1, s2) in occupiedSegments) {
                if (CadGeometryKernel.intersectSegments(sh, end, s1, s2) != null) return true
            }
        } else {
            for (c in obstacleCircles) {
                val cBox = BoundingBox2D(
                    c.center.x - c.radius - 1.5,
                    c.center.y - c.radius - 1.5,
                    c.center.x + c.radius + 1.5,
                    c.center.y + c.radius + 1.5
                )
                if (candidateBox.intersects(cBox, tolerance = 0.4)) return true
            }
            for (l in obstacleLines) {
                if (l.id in dim.sourceEntityIds) continue
                val dText = CadGeometryKernel.distancePointToSegment(plan.textPositionModel, l.start, l.end)
                if (dText < 2.2) return true
            }
        }

        return false
    }

    private fun estimatePlanTextBox(
        plan: AdaptiveDimensionRenderPlan,
        spanScale: Double
    ): BoundingBox2D {
        val baseW = max(plan.labelText.length * 2.2, (spanScale * 0.12).coerceIn(8.0, 26.0))
        val baseH = (spanScale * 0.055).coerceIn(4.2, 10.0)
        val isVerticalText = abs(abs(plan.textRotationDeg) - 90f) < 15f
        val w = if (isVerticalText) baseH else baseW
        val h = if (isVerticalText) baseW else baseH
        val p = plan.textPositionModel
        return BoundingBox2D(p.x - w * 0.5, p.y - h * 0.5, p.x + w * 0.5, p.y + h * 0.5)
    }

    private fun estimateTextBoundingBox(
        dim: CadEntity.DimensionEntity,
        spanScale: Double,
        style: DimensionStyleConfig
    ): BoundingBox2D {
        val w = max(style.baseTextHeightMm * 3.2, (spanScale * 0.12).coerceIn(8.0, 26.0))
        val h = max(style.baseTextHeightMm * 1.6, (spanScale * 0.055).coerceIn(4.2, 10.0))
        val p = dim.dimensionLinePoint
        return BoundingBox2D(p.x - w * 0.5, p.y - h * 0.5, p.x + w * 0.5, p.y + h * 0.5)
    }

    private fun fmt(v: Double): String = String.format(Locale.US, "%.2f", v)
}
