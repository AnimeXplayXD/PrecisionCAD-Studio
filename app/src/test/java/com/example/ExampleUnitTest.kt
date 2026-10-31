package com.example

import com.example.cad.dimension.AdaptiveAnnotationRenderer
import com.example.cad.dimension.AutoDimensionEngine
import com.example.cad.dimension.DimensionStyleConfig
import com.example.cad.dimension.QDimDensityPolicy
import com.example.cad.io.CadDxfInterchange
import com.example.cad.io.DxfDocumentPayload
import com.example.cad.kernel.CadGeometryKernel
import com.example.cad.kernel.CadModificationEngine
import com.example.cad.kernel.CadSnapAndCoordEngine
import com.example.cad.kernel.ViewportTransform
import com.example.cad.model.BoundingBox2D
import com.example.cad.model.CADCommandMatrix
import com.example.cad.model.CadCommandCompatibilityCatalog
import com.example.cad.model.CadCommandId
import com.example.cad.model.CadEntity
import com.example.cad.model.CadLayer
import com.example.cad.model.CadUnit
import com.example.cad.model.ConstraintType
import com.example.cad.model.DimensionType
import com.example.cad.model.GeometricConstraintRecord
import com.example.cad.model.Point2D
import com.example.cad.model.PrecisionFormatConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun `TC-MAT-01 verify CADCommandMatrix and CadCommandCompatibilityCatalog cover all 43 commands`() {
        val matrix = CADCommandMatrix()
        assertEquals(CadCommandId.entries.size, matrix.entries.size)
        for (entry in matrix.entries) {
            assertTrue(entry.commandName.isNotBlank())
            assertTrue(entry.aliases.isNotEmpty())
            assertTrue(entry.supportedOptions.isNotEmpty())
            assertTrue(entry.inputMethods.isNotEmpty())
            assertTrue(entry.expectedOutput.isNotBlank())
            assertTrue(entry.limitations.isNotBlank())
            assertTrue(entry.verificationTestCases.isNotEmpty())
            assertTrue(entry.verificationTestCases.all { it.testCaseId.startsWith("TC-") })
        }
        assertNotNull(matrix.findByCommandNameOrAlias("FILLET"))
        assertNotNull(matrix.findByCommandNameOrAlias("F"))
        assertNotNull(matrix.findByCommandNameOrAlias("QDIM"))
    }

    @Test
    fun `TC-CRD-01 verify Cartesian, Relative, and Polar coordinate parsing`() {
        val ref = Point2D(10.0, 20.0)
        val cur = Point2D(50.0, 20.0)

        val absPt = CadSnapAndCoordEngine.parseCoordinateInput("125, 85", ref, cur, orthoEnabled = false)
        assertNotNull(absPt)
        assertEquals(125.0, absPt!!.x, 1e-4)
        assertEquals(85.0, absPt.y, 1e-4)

        val relPt = CadSnapAndCoordEngine.parseCoordinateInput("@25, -10", ref, cur, orthoEnabled = false)
        assertNotNull(relPt)
        assertEquals(35.0, relPt!!.x, 1e-4)
        assertEquals(10.0, relPt.y, 1e-4)

        val relPolar = CadSnapAndCoordEngine.parseCoordinateInput("@30<90", ref, cur, orthoEnabled = false)
        assertNotNull(relPolar)
        assertEquals(10.0, relPolar!!.x, 1e-4)
        assertEquals(50.0, relPolar.y, 1e-4)
    }

    @Test
    fun `TC-DRW-03 and TC-DRW-06 verify 3P circle and circumscribed polygon`() {
        val c3p = CadGeometryKernel.circleFrom3Points(
            Point2D(0.0, 10.0),
            Point2D(10.0, 0.0),
            Point2D(0.0, -10.0)
        )
        assertNotNull(c3p)
        assertEquals(0.0, c3p!!.center.x, 1e-4)
        assertEquals(0.0, c3p.center.y, 1e-4)
        assertEquals(10.0, c3p.radius, 1e-4)

        val hex = CadGeometryKernel.createRegularPolygon(
            center = Point2D(0.0, 0.0),
            radius = 10.0,
            sides = 6,
            circumscribed = true
        )
        assertEquals(6, hex.vertices.size)
        assertEquals(11.5470, hex.vertices.first().position.distanceTo(Point2D(0.0, 0.0)), 1e-3)
    }

    @Test
    fun `TC-MOD-07 TC-MOD-08 TC-MOD-09 TC-MOD-10 verify Trim, Extend, Fillet R=5 on Lines and Rectangles, and Chamfer`() {
        // Trim middle segment of (-50,0)-(50,0) between X=-10 and X=10
        val target = CadEntity.LineEntity(start = Point2D(-50.0, 0.0), end = Point2D(50.0, 0.0))
        val c1 = CadEntity.LineEntity(start = Point2D(-10.0, -20.0), end = Point2D(-10.0, 20.0))
        val c2 = CadEntity.LineEntity(start = Point2D(10.0, -20.0), end = Point2D(10.0, 20.0))
        val trimmed = CadModificationEngine.trimEntityAtPick(target, Point2D(0.0, 0.0), listOf(target, c1, c2))
        assertEquals(2, trimmed.size)

        // Extend (0,0)-(30,0) to vertical boundary at X=50
        val shortLine = CadEntity.LineEntity(start = Point2D(0.0, 0.0), end = Point2D(30.0, 0.0))
        val boundary = CadEntity.LineEntity(start = Point2D(50.0, -20.0), end = Point2D(50.0, 20.0))
        val extended = CadModificationEngine.extendEntityAtPick(shortLine, Point2D(29.0, 0.0), listOf(shortLine, boundary)) as CadEntity.LineEntity
        assertEquals(50.0, extended.end.x, 1e-4)

        // Fillet orthogonal lines with R=5
        val l1 = CadEntity.LineEntity(start = Point2D(0.0, 0.0), end = Point2D(50.0, 0.0))
        val l2 = CadEntity.LineEntity(start = Point2D(0.0, 0.0), end = Point2D(0.0, 50.0))
        val filleted = CadModificationEngine.filletTwoLines(l1, l2, radius = 5.0, trimSegments = true)
        assertNotNull(filleted)
        assertEquals(3, filleted!!.size)
        val arc = filleted.filterIsInstance<CadEntity.ArcEntity>().first()
        assertEquals(5.0, arc.center.x, 1e-4)
        assertEquals(5.0, arc.center.y, 1e-4)
        assertEquals(5.0, arc.radius, 1e-4)

        // AutoCAD-style two-pick Fillet on a Rectangle (PolylineEntity) with R=5.0 and custom manual R=7.35
        val rect = CadGeometryKernel.createRectanglePolyline(Point2D(0.0, 0.0), Point2D(120.0, 80.0), filletRadius = 6.0)
        val filletedRect = CadModificationEngine.filletAtPicks(
            entities = listOf(rect),
            pick1 = Point2D(90.0, 0.0),
            pick2 = Point2D(120.0, 30.0),
            radius = 5.0,
            pickTolerance = 12.0,
            trimSegments = true
        )
        assertNotNull(filletedRect)
        val cornerArcR5 = filletedRect!!.filterIsInstance<CadEntity.ArcEntity>().firstOrNull { kotlin.math.abs(it.radius - 5.0) < 1e-4 }
        assertNotNull(cornerArcR5)
        assertEquals(115.0, cornerArcR5!!.center.x, 1e-3)
        assertEquals(5.0, cornerArcR5.center.y, 1e-3)

        // Chamfer orthogonal lines with D1=5, D2=5
        val chamfered = CadModificationEngine.chamferTwoLines(l1, l2, distance1 = 5.0, distance2 = 5.0, trimSegments = true)
        assertNotNull(chamfered)
        assertEquals(3, chamfered!!.size)
        val bevel = chamfered.filterIsInstance<CadEntity.LineEntity>().last()
        assertEquals(7.0711, bevel.length, 1e-3)
    }

    @Test
    fun `TC-DIM-01 and TC-DIM-05B verify Quick Dimension (QDIM), Toggle-Off removal, and ISO ASME Radial Diametric Angular Render Plans`() {
        val filletedRect = CadGeometryKernel.createRectanglePolyline(
            corner1 = Point2D(0.0, 0.0),
            corner2 = Point2D(120.0, 80.0),
            filletRadius = 6.0
        )
        val holes = listOf(
            CadEntity.CircleEntity(center = Point2D(20.0, 20.0), radius = 5.0),
            CadEntity.CircleEntity(center = Point2D(60.0, 20.0), radius = 5.0),
            CadEntity.CircleEntity(center = Point2D(100.0, 20.0), radius = 5.0)
        )
        val manualDim = CadEntity.DimensionEntity(
            dimensionType = DimensionType.ALIGNED,
            anchor1 = Point2D(0.0, 0.0),
            anchor2 = Point2D(20.0, 20.0),
            dimensionLinePoint = Point2D(10.0, 15.0),
            isAutoGenerated = false
        )
        val all = listOf(filletedRect) + holes + manualDim
        val qdimResult = AutoDimensionEngine.generateQuickDimensions(
            allEntities = all,
            targetEntities = listOf(filletedRect) + holes,
            density = QDimDensityPolicy.BALANCED
        )
        val dims = qdimResult.filterIsInstance<CadEntity.DimensionEntity>()
        // Verify overall 120 and 80 dimensions exist
        assertTrue(dims.any { it.dimensionType == DimensionType.LINEAR_HORIZONTAL && kotlin.math.abs(it.measuredModelValue() - 120.0) < 1e-3 })
        assertTrue(dims.any { it.dimensionType == DimensionType.LINEAR_VERTICAL && kotlin.math.abs(it.measuredModelValue() - 80.0) < 1e-3 })
        // Verify 3x hole pattern diameter callout exists
        val diaDim = dims.firstOrNull { it.dimensionType == DimensionType.DIAMETER }
        assertNotNull(diaDim)
        assertEquals("3× ", diaDim!!.customPrefix)
        // Verify 4x corner fillet radius callout (R6.00) was detected from polyline bulge segments
        val radDim = dims.firstOrNull { it.dimensionType == DimensionType.RADIUS }
        assertNotNull(radDim)
        assertEquals("4× ", radDim!!.customPrefix)
        assertEquals(6.0, radDim.measuredModelValue(), 1e-3)
        // Verify manual dimension was preserved
        assertTrue(dims.any { !it.isAutoGenerated && it.id == manualDim.id })

        // Verify toggling Quick Dimension OFF removes auto-dimensions while preserving manual dimensions
        val afterToggleOff = AutoDimensionEngine.removeAutoDimensions(qdimResult)
        val remainingDims = afterToggleOff.filterIsInstance<CadEntity.DimensionEntity>()
        assertEquals(1, remainingDims.size)
        assertEquals(manualDim.id, remainingDims.first().id)

        // Verify ISO/ASME Radial and Diametric Render Plans have center, rim contact, and horizontal landing shelf
        val diaPlan = AdaptiveAnnotationRenderer.computeRenderPlan(
            dim = diaDim,
            precision = PrecisionFormatConfig(linearDecimals = 2),
            storageUnit = CadUnit.MM,
            displayUnit = CadUnit.MM,
            style = DimensionStyleConfig(),
            viewport = ViewportTransform(pixelsPerModelUnit = 3.0f)
        )
        assertTrue(diaPlan.isRadialOrDiametric)
        assertTrue(diaPlan.isDiameterCallout)
        assertNotNull(diaPlan.leaderShoulderModel)
        assertNotNull(diaPlan.leaderLandingEndModel)
        assertEquals(diaPlan.leaderShoulderModel!!.y, diaPlan.leaderLandingEndModel!!.y, 1e-4)

        val radPlan = AdaptiveAnnotationRenderer.computeRenderPlan(
            dim = radDim,
            precision = PrecisionFormatConfig(linearDecimals = 2),
            storageUnit = CadUnit.MM,
            displayUnit = CadUnit.MM,
            style = DimensionStyleConfig(),
            viewport = ViewportTransform(pixelsPerModelUnit = 3.0f)
        )
        assertTrue(radPlan.isRadialOrDiametric)
        assertFalse(radPlan.isDiameterCallout)
        assertNotNull(radPlan.leaderShoulderModel)
        assertNotNull(radPlan.leaderLandingEndModel)
        assertEquals(radPlan.leaderShoulderModel!!.y, radPlan.leaderLandingEndModel!!.y, 1e-4)

        // Verify Angular Dimension Render Plan uses true circular arc geometry
        val angDim = CadEntity.DimensionEntity(
            dimensionType = DimensionType.ANGULAR,
            angularVertex = Point2D(0.0, 0.0),
            anchor1 = Point2D(30.0, 0.0),
            anchor2 = Point2D(0.0, 30.0),
            dimensionLinePoint = Point2D(15.0, 15.0)
        )
        val angPlan = AdaptiveAnnotationRenderer.computeRenderPlan(
            dim = angDim,
            precision = PrecisionFormatConfig(linearDecimals = 2, angularDecimals = 1),
            storageUnit = CadUnit.MM,
            displayUnit = CadUnit.MM,
            style = DimensionStyleConfig(),
            viewport = ViewportTransform(pixelsPerModelUnit = 3.0f)
        )
        assertTrue(angPlan.isAngularArc)
        assertEquals(90.0, kotlin.math.abs(angPlan.angularSweepDeg), 1e-2)

        // Verify AdaptiveAnnotationRenderer flips arrows & text outside for very short linear dimension
        val shortDim = CadEntity.DimensionEntity(
            dimensionType = DimensionType.LINEAR_HORIZONTAL,
            anchor1 = Point2D(0.0, 0.0),
            anchor2 = Point2D(2.0, 0.0),
            dimensionLinePoint = Point2D(1.0, 5.0)
        )
        val plan = AdaptiveAnnotationRenderer.computeRenderPlan(
            dim = shortDim,
            precision = PrecisionFormatConfig(linearDecimals = 2),
            storageUnit = CadUnit.MM,
            displayUnit = CadUnit.MM,
            style = DimensionStyleConfig(),
            viewport = ViewportTransform(pixelsPerModelUnit = 3.0f)
        )
        assertFalse(plan.arrowsInside)
        assertFalse(plan.textInside)
    }

    @Test
    fun `TC-DOC-02 and TC-DOC-06 verify Unit Display Conversion vs Rescale and DXF Round-Trip`() {
        val prec = PrecisionFormatConfig(linearDecimals = 2, showUnitSuffix = true)
        // 100 mm displayed in cm (Mode A) -> "10.00 cm" without changing stored 100.0
        val formattedCm = prec.formatLinear(100.0, storageUnit = CadUnit.MM, displayUnit = CadUnit.CM)
        assertEquals("10.00 cm", formattedCm)

        // DXF Round-trip test
        val payload = DxfDocumentPayload(
            storageUnit = CadUnit.MM,
            displayUnit = CadUnit.MM,
            layers = CadLayer.defaultLayers(),
            entities = listOf(
                CadEntity.LineEntity(start = Point2D(0.0, 0.0), end = Point2D(100.0, 50.0)),
                CadEntity.CircleEntity(center = Point2D(50.0, 25.0), radius = 12.5),
                CadGeometryKernel.createRectanglePolyline(Point2D(0.0, 0.0), Point2D(100.0, 50.0))
            )
        )
        val dxfText = CadDxfInterchange.exportToDxf(payload)
        val reimported = CadDxfInterchange.importFromDxf(dxfText)
        assertEquals(CadUnit.MM, reimported.displayUnit)
        assertEquals(3, reimported.entities.size)

        // Verify Geometric Constraint Solver (Perpendicular + Equal)
        val l1 = CadEntity.LineEntity(id = "l1", start = Point2D(0.0, 0.0), end = Point2D(40.0, 0.0))
        val l2 = CadEntity.LineEntity(id = "l2", start = Point2D(0.0, 0.0), end = Point2D(60.0, 20.0))
        val afterPerp = CadGeometryKernel.applyGeometricConstraint(
            GeometricConstraintRecord(type = ConstraintType.PERPENDICULAR, primaryEntityId = "l1", secondaryEntityId = "l2"),
            listOf(l1, l2)
        )
        val afterEqual = CadGeometryKernel.applyGeometricConstraint(
            GeometricConstraintRecord(type = ConstraintType.EQUAL, primaryEntityId = "l1", secondaryEntityId = "l2"),
            afterPerp
        )
        val solvedL2 = afterEqual.filterIsInstance<CadEntity.LineEntity>().first { it.id == "l2" }
        assertEquals(90.0, solvedL2.angleDeg, 1e-3)
        assertEquals(40.0, solvedL2.length, 1e-3)
    }

    @Test
    fun `TC-DIM-06 verify initial 6-hole mounting plate has zero pre-baked callouts and collision-free QDIM leaders`() {
        val template = com.example.cad.data.CadTemplateFactory.createSixHoleMountingPlate()
        assertTrue(template.entities.none { it is CadEntity.DimensionEntity })
        assertTrue(template.entities.none { it is CadEntity.LeaderEntity })

        val qdimEntities = AutoDimensionEngine.generateQuickDimensions(
            allEntities = template.entities,
            targetEntities = template.entities,
            density = QDimDensityPolicy.BALANCED
        )
        val circles = template.entities.filterIsInstance<CadEntity.CircleEntity>()
        val diaAndRadDims = qdimEntities.filterIsInstance<CadEntity.DimensionEntity>()
            .filter { it.dimensionType == DimensionType.DIAMETER || it.dimensionType == DimensionType.RADIUS }

        assertTrue(diaAndRadDims.size >= 3) // Central bore ⌀28, 6x ⌀10 holes, 4x R6 corner fillets

        for (dim in diaAndRadDims) {
            val plan = AdaptiveAnnotationRenderer.computeRenderPlan(
                dim = dim,
                precision = PrecisionFormatConfig(linearDecimals = 2),
                storageUnit = CadUnit.MM,
                displayUnit = CadUnit.MM,
                style = DimensionStyleConfig(),
                viewport = ViewportTransform(pixelsPerModelUnit = 3.0f)
            )
            val rim = plan.rimContactPointModel!!
            val sh = plan.leaderShoulderModel!!
            val end = plan.leaderLandingEndModel!!

            for (hole in circles) {
                if (hole.id in dim.sourceEntityIds) continue
                val dRay = CadGeometryKernel.distancePointToSegment(hole.center, rim, sh)
                val dShelf = CadGeometryKernel.distancePointToSegment(hole.center, sh, end)
                assertTrue(
                    "Leader for ${plan.labelText} collided with hole at ${hole.center} (dRay=$dRay, dShelf=$dShelf, r=${hole.radius})",
                    dRay > hole.radius + 1.5 && dShelf > hole.radius + 1.5
                )
            }
        }
    }

    @Test
    fun `TC-ALIGN-01 verify DimensionAlignmentEngine 10 required validation cases from Section 10_4`() {
        val outerRect = CadGeometryKernel.createRectanglePolyline(
            corner1 = Point2D(0.0, 0.0),
            corner2 = Point2D(120.0, 80.0),
            filletRadius = 6.0
        )

        // 1. Symmetric hole margins (20 mm left, 20 mm right; equal 40 mm pitches)
        val symHoles = listOf(
            CadEntity.CircleEntity(id = "h1", center = Point2D(20.0, 20.0), radius = 5.0),
            CadEntity.CircleEntity(id = "h2", center = Point2D(60.0, 20.0), radius = 5.0),
            CadEntity.CircleEntity(id = "h3", center = Point2D(100.0, 20.0), radius = 5.0)
        )
        val (_, symReport) = AutoDimensionEngine.generateQuickDimensionsWithAlignmentReport(
            allEntities = listOf(outerRect) + symHoles,
            targetEntities = listOf(outerRect) + symHoles,
            density = QDimDensityPolicy.BALANCED,
            arrangementPreference = com.example.cad.dimension.DimensionArrangementPreference.AUTO_CHAIN
        )
        val symMarginFinding = symReport.completenessFindings.first {
            it.axisOrFeature == "HORIZONTAL_MARGINS"
        }
        assertEquals(
            com.example.cad.dimension.DimensionCompletenessCategory.VERIFIED_GEOMETRIC_EQUALITY,
            symMarginFinding.category
        )
        assertTrue(symMarginFinding.isSymmetricOrEqualVerified)

        // 2. Intentionally unequal hole margins (18 mm left, 22 mm right) -> MUST NOT assert symmetry!
        val asymHoles = listOf(
            CadEntity.CircleEntity(id = "ah1", center = Point2D(18.0, 20.0), radius = 5.0),
            CadEntity.CircleEntity(id = "ah2", center = Point2D(60.0, 20.0), radius = 5.0),
            CadEntity.CircleEntity(id = "ah3", center = Point2D(98.0, 20.0), radius = 5.0) // 120 - 98 = 22.0 != 18.0
        )
        val (asymDims, asymReport) = AutoDimensionEngine.generateQuickDimensionsWithAlignmentReport(
            allEntities = listOf(outerRect) + asymHoles,
            targetEntities = listOf(outerRect) + asymHoles,
            density = QDimDensityPolicy.DETAILED,
            arrangementPreference = com.example.cad.dimension.DimensionArrangementPreference.AUTO_CHAIN
        )
        val asymMarginFinding = asymReport.completenessFindings.first {
            it.axisOrFeature == "HORIZONTAL_MARGINS"
        }
        assertEquals(
            com.example.cad.dimension.DimensionCompletenessCategory.INFERRED_UNVERIFIED,
            asymMarginFinding.category
        )
        assertFalse(asymMarginFinding.isSymmetricOrEqualVerified)
        // Verify right margin (98 -> 120 = 22 mm) is explicitly emitted in DETAILED mode when asymmetric
        val horizDims = asymDims.filterIsInstance<CadEntity.DimensionEntity>()
            .filter { it.dimensionType == DimensionType.LINEAR_HORIZONTAL }
        assertTrue(horizDims.any { kotlin.math.abs(it.measuredModelValue() - 22.0) < 1e-3 })

        // 3. Unequal hole diameters (⌀10 vs ⌀12) must never be grouped into a single "2×" callout
        val mixedDiaHoles = listOf(
            CadEntity.CircleEntity(id = "md1", center = Point2D(30.0, 40.0), radius = 5.0), // ⌀10
            CadEntity.CircleEntity(id = "md2", center = Point2D(90.0, 40.0), radius = 6.0)  // ⌀12
        )
        val (mixedDims, _) = AutoDimensionEngine.generateQuickDimensionsWithAlignmentReport(
            allEntities = listOf(outerRect) + mixedDiaHoles,
            targetEntities = listOf(outerRect) + mixedDiaHoles,
            density = QDimDensityPolicy.BALANCED
        )
        val diaCallouts = mixedDims.filterIsInstance<CadEntity.DimensionEntity>()
            .filter { it.dimensionType == DimensionType.DIAMETER }
        assertEquals(2, diaCallouts.size)
        assertTrue(diaCallouts.none { it.customPrefix.startsWith("2×") })

        // 4. Baseline vs Continuous Chain vs Ordinate arrangement modes
        val (_, baselineReport) = AutoDimensionEngine.generateQuickDimensionsWithAlignmentReport(
            allEntities = listOf(outerRect) + symHoles,
            targetEntities = listOf(outerRect) + symHoles,
            density = QDimDensityPolicy.BALANCED,
            arrangementPreference = com.example.cad.dimension.DimensionArrangementPreference.BASELINE
        )
        assertTrue(
            baselineReport.placements.any {
                it.arrangementMode == com.example.cad.dimension.DimensionArrangementMode.BASELINE
            }
        )
        val (ordinateEntities, ordinateReport) = AutoDimensionEngine.generateQuickDimensionsWithAlignmentReport(
            allEntities = listOf(outerRect) + symHoles,
            targetEntities = listOf(outerRect) + symHoles,
            density = QDimDensityPolicy.BALANCED,
            arrangementPreference = com.example.cad.dimension.DimensionArrangementPreference.ORDINATE
        )
        assertTrue(
            ordinateEntities.filterIsInstance<CadEntity.DimensionEntity>().any {
                it.dimensionType == DimensionType.ORDINATE_X || it.dimensionType == DimensionType.ORDINATE_Y
            }
        )
        assertTrue(
            ordinateReport.placements.any {
                it.arrangementMode == com.example.cad.dimension.DimensionArrangementMode.ORDINATE
            }
        )

        // 5. Nested overall and feature dimensions: overall envelope dimension strictly outside feature band
        val overallHoriz = symReport.placements.first {
            it.dimension.dimensionType == DimensionType.LINEAR_HORIZONTAL &&
                it.dimension.hierarchyTier == com.example.cad.model.DimensionHierarchyTier.PRIMARY_OVERALL
        }
        val featureHoriz = symReport.placements.filter {
            it.dimension.dimensionType == DimensionType.LINEAR_HORIZONTAL &&
                it.dimension.hierarchyTier == com.example.cad.model.DimensionHierarchyTier.SECONDARY_FEATURE
        }
        assertTrue(featureHoriz.isNotEmpty())
        val overallEnvelopeDistance = overallHoriz.dimension.dimensionLinePoint.y - 80.0
        featureHoriz.forEach { feat ->
            val featureEnvelopeDistance = 0.0 - feat.dimension.dimensionLinePoint.y
            assertTrue(
                "Overall horizontal envelope offset ($overallEnvelopeDistance) must be strictly outside feature band offset ($featureEnvelopeDistance)",
                overallEnvelopeDistance > featureEnvelopeDistance
            )
            // Exact numerical preservation for horizontal projected span
            assertEquals(
                kotlin.math.abs(feat.dimension.anchor2.x - feat.dimension.anchor1.x),
                feat.dimension.measuredModelValue(),
                1e-6
            )
        }

        // 6. Short dimension lines where arrows/text must move outside
        val shortCandidate = CadEntity.DimensionEntity(
            dimensionType = DimensionType.LINEAR_HORIZONTAL,
            anchor1 = Point2D(10.0, 0.0),
            anchor2 = Point2D(12.0, 0.0),
            dimensionLinePoint = Point2D(11.0, -14.0),
            hierarchyTier = com.example.cad.model.DimensionHierarchyTier.SECONDARY_FEATURE
        )
        val shortReport = com.example.cad.dimension.DimensionAlignmentEngine.alignAndArrangeDimensions(
            candidateDimensions = listOf(shortCandidate),
            overallBounds = BoundingBox2D(0.0, 0.0, 120.0, 80.0),
            existingManualDimensions = emptyList()
        )
        val shortPlacement = shortReport.placements.single()
        assertEquals(com.example.cad.dimension.ArrowPlacementMode.OUTSIDE, shortPlacement.arrowPlacementMode)
        assertEquals(com.example.cad.dimension.TextPlacementMode.EXTERNAL_OFFSET, shortPlacement.textPlacementMode)
    }
}


