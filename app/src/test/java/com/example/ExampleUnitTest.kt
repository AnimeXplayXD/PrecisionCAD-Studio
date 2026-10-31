package com.example

import androidx.compose.ui.geometry.Offset
import com.example.cad.dimension.AdaptiveAnnotationRenderer
import com.example.cad.dimension.AutoDimensionEngine
import com.example.cad.dimension.DimensionStyleConfig
import com.example.cad.dimension.QDimDensityPolicy
import com.example.cad.io.CadDxfInterchange
import com.example.cad.io.DxfDocumentPayload
import com.example.cad.kernel.CadGeometryKernel
import com.example.cad.kernel.CadModificationEngine
import com.example.cad.kernel.CadSnapAndCoordEngine
import com.example.cad.kernel.UsableViewportRect
import com.example.cad.kernel.ViewportTransform
import com.example.cad.model.BlockDefinition
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
import com.example.cad.model.PolylineVertex
import com.example.cad.model.PrecisionFormatConfig
import com.example.ui.components.ViewportGesturePolicy
import com.example.ui.components.ViewportGestureState
import com.example.ui.theme.CadHudSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

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
        val target = CadEntity.LineEntity(start = Point2D(-50.0, 0.0), end = Point2D(50.0, 0.0))
        val c1 = CadEntity.LineEntity(start = Point2D(-10.0, -20.0), end = Point2D(-10.0, 20.0))
        val c2 = CadEntity.LineEntity(start = Point2D(10.0, -20.0), end = Point2D(10.0, 20.0))
        val trimmed = CadModificationEngine.trimEntityAtPick(target, Point2D(0.0, 0.0), listOf(target, c1, c2))
        assertEquals(2, trimmed.size)

        val shortLine = CadEntity.LineEntity(start = Point2D(0.0, 0.0), end = Point2D(30.0, 0.0))
        val boundary = CadEntity.LineEntity(start = Point2D(50.0, -20.0), end = Point2D(50.0, 20.0))
        val extended = CadModificationEngine.extendEntityAtPick(shortLine, Point2D(29.0, 0.0), listOf(shortLine, boundary)) as CadEntity.LineEntity
        assertEquals(50.0, extended.end.x, 1e-4)

        val l1 = CadEntity.LineEntity(start = Point2D(0.0, 0.0), end = Point2D(50.0, 0.0))
        val l2 = CadEntity.LineEntity(start = Point2D(0.0, 0.0), end = Point2D(0.0, 50.0))
        val filleted = CadModificationEngine.filletTwoLines(l1, l2, radius = 5.0, trimSegments = true)
        assertNotNull(filleted)
        assertEquals(3, filleted!!.size)
        val arc = filleted.filterIsInstance<CadEntity.ArcEntity>().first()
        assertEquals(5.0, arc.center.x, 1e-4)
        assertEquals(5.0, arc.center.y, 1e-4)
        assertEquals(5.0, arc.radius, 1e-4)

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
        val cornerArcR5 = filletedRect!!.filterIsInstance<CadEntity.ArcEntity>().firstOrNull { abs(it.radius - 5.0) < 1e-4 }
        assertNotNull(cornerArcR5)
        assertEquals(115.0, cornerArcR5!!.center.x, 1e-3)
        assertEquals(5.0, cornerArcR5.center.y, 1e-3)

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
        assertTrue(dims.any { it.dimensionType == DimensionType.LINEAR_HORIZONTAL && abs(it.measuredModelValue() - 120.0) < 1e-3 })
        assertTrue(dims.any { it.dimensionType == DimensionType.LINEAR_VERTICAL && abs(it.measuredModelValue() - 80.0) < 1e-3 })
        val diaDim = dims.firstOrNull { it.dimensionType == DimensionType.DIAMETER }
        assertNotNull(diaDim)
        assertEquals("3× ", diaDim!!.customPrefix)
        val radDim = dims.firstOrNull { it.dimensionType == DimensionType.RADIUS }
        assertNotNull(radDim)
        assertEquals("4× ", radDim!!.customPrefix)
        assertEquals(6.0, radDim.measuredModelValue(), 1e-3)
        assertTrue(dims.any { !it.isAutoGenerated && it.id == manualDim.id })

        val afterToggleOff = AutoDimensionEngine.removeAutoDimensions(qdimResult)
        val remainingDims = afterToggleOff.filterIsInstance<CadEntity.DimensionEntity>()
        assertEquals(1, remainingDims.size)
        assertEquals(manualDim.id, remainingDims.first().id)

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
        assertEquals(90.0, abs(angPlan.angularSweepDeg), 1e-2)

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
    fun `TC-DOC-02 and TC-DOC-06 verify Unit Display Conversion vs Rescale and AC1015 DXF Compliance and Round-Trip`() {
        val prec = PrecisionFormatConfig(linearDecimals = 2, showUnitSuffix = true)
        val formattedCm = prec.formatLinear(100.0, storageUnit = CadUnit.MM, displayUnit = CadUnit.CM)
        assertEquals("10.00 cm", formattedCm)

        // Comprehensive AC1015 DXF export & round-trip test including DIMENSION, DONUT, CENTERMARK, and LEADER
        val payload = DxfDocumentPayload(
            storageUnit = CadUnit.MM,
            displayUnit = CadUnit.MM,
            layers = CadLayer.defaultLayers(),
            entities = listOf(
                CadEntity.LineEntity(start = Point2D(0.0, 0.0), end = Point2D(100.0, 50.0)),
                CadEntity.CircleEntity(center = Point2D(50.0, 25.0), radius = 12.5),
                CadGeometryKernel.createRectanglePolyline(Point2D(0.0, 0.0), Point2D(100.0, 50.0), filletRadius = 5.0),
                CadEntity.DonutEntity(center = Point2D(25.0, 25.0), innerDiameter = 6.0, outerDiameter = 12.0),
                CadEntity.CenterMarkEntity(center = Point2D(50.0, 25.0), radius = 12.5),
                CadEntity.LeaderEntity(
                    arrowTip = Point2D(50.0, 37.5),
                    shoulderPoint = Point2D(65.0, 55.0),
                    landingEnd = Point2D(80.0, 55.0),
                    text = "BORE H7"
                ),
                CadEntity.DimensionEntity(
                    dimensionType = DimensionType.LINEAR_HORIZONTAL,
                    anchor1 = Point2D(0.0, 0.0),
                    anchor2 = Point2D(100.0, 0.0),
                    dimensionLinePoint = Point2D(50.0, -15.0)
                ),
                CadEntity.DimensionEntity(
                    dimensionType = DimensionType.DIAMETER,
                    anchor1 = Point2D(50.0, 25.0),
                    anchor2 = Point2D(62.5, 25.0),
                    dimensionLinePoint = Point2D(72.0, 40.0),
                    customPrefix = "2× "
                )
            )
        )
        val dxfText = CadDxfInterchange.exportToDxf(payload)
        // Verify AC1015 required tables and standard entity records exist
        assertTrue(dxfText.contains("0\nTABLE\n2\nSTYLE"))
        assertTrue(dxfText.contains("0\nTABLE\n2\nDIMSTYLE"))
        assertTrue(dxfText.contains("3\nSTANDARD"))
        assertFalse(dxfText.contains("NaN"))
        assertFalse(dxfText.contains("Infinity"))
        // Non-standard entity records must be replaced with standard LWPOLYLINE, LINE, and LEADER+MTEXT
        assertFalse(dxfText.contains("0\nDONUT\n"))
        assertFalse(dxfText.contains("0\nCENTERMARK\n"))
        assertFalse(dxfText.contains("0\nMLEADER\n"))
        assertTrue(dxfText.contains("0\nLEADER\n"))

        val reimported = CadDxfInterchange.importFromDxf(dxfText)
        assertEquals(CadUnit.MM, reimported.displayUnit)
        assertEquals(payload.entities.size, reimported.entities.size)
        val reimportedDims = reimported.entities.filterIsInstance<CadEntity.DimensionEntity>()
        assertEquals(2, reimportedDims.size)
        assertTrue(reimportedDims.any { it.dimensionType == DimensionType.DIAMETER && it.customPrefix == "2× " })

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

        assertTrue(diaAndRadDims.size >= 3)

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

        val asymHoles = listOf(
            CadEntity.CircleEntity(id = "ah1", center = Point2D(18.0, 20.0), radius = 5.0),
            CadEntity.CircleEntity(id = "ah2", center = Point2D(60.0, 20.0), radius = 5.0),
            CadEntity.CircleEntity(id = "ah3", center = Point2D(98.0, 20.0), radius = 5.0)
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
        val horizDims = asymDims.filterIsInstance<CadEntity.DimensionEntity>()
            .filter { it.dimensionType == DimensionType.LINEAR_HORIZONTAL }
        assertTrue(horizDims.any { abs(it.measuredModelValue() - 22.0) < 1e-3 })

        val mixedDiaHoles = listOf(
            CadEntity.CircleEntity(id = "md1", center = Point2D(30.0, 40.0), radius = 5.0),
            CadEntity.CircleEntity(id = "md2", center = Point2D(90.0, 40.0), radius = 6.0)
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
            assertEquals(
                abs(feat.dimension.anchor2.x - feat.dimension.anchor1.x),
                feat.dimension.measuredModelValue(),
                1e-6
            )
        }

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

    @Test
    fun `TC-NAV-14 verify Navigation-Only Sheet Gesture State Machine and Focal-Point Pinch-Pan Invariant`() {
        // 1. 0 -> 1 pointer is suppressed (no drawing, no point pick, no pan)
        assertEquals(
            ViewportGestureState.ONE_POINTER_SUPPRESSED,
            ViewportGesturePolicy.transitionState(ViewportGestureState.IDLE, activePointerCount = 1)
        )
        // 2. 1 -> 2 pointers enters TWO_POINTER_TRANSFORM
        assertEquals(
            ViewportGestureState.TWO_POINTER_TRANSFORM,
            ViewportGesturePolicy.transitionState(ViewportGestureState.ONE_POINTER_SUPPRESSED, activePointerCount = 2)
        )
        // 3. 2 -> 1 pointer returns to ONE_POINTER_SUPPRESSED without jumping
        assertEquals(
            ViewportGestureState.ONE_POINTER_SUPPRESSED,
            ViewportGesturePolicy.transitionState(ViewportGestureState.TWO_POINTER_TRANSFORM, activePointerCount = 1)
        )
        // 4. >= 3 pointers cancels until all pointers lift
        val cancelled = ViewportGesturePolicy.transitionState(ViewportGestureState.TWO_POINTER_TRANSFORM, activePointerCount = 3)
        assertEquals(ViewportGestureState.CANCELLED, cancelled)
        assertEquals(ViewportGestureState.CANCELLED, ViewportGesturePolicy.transitionState(cancelled, activePointerCount = 2))
        assertEquals(ViewportGestureState.IDLE, ViewportGesturePolicy.transitionState(cancelled, activePointerCount = 0))

        // 5. Verify focal-point zoom + pan invariant: model point under previousCentroid lands at currentCentroid
        val vp0 = ViewportTransform(panScreenX = 240f, panScreenY = 360f, pixelsPerModelUnit = 4.0f)
        val prevCentroid = Offset(400f, 300f)
        val panDelta = Offset(30f, -20f)
        val currCentroid = prevCentroid + panDelta
        val modelAtPrevCentroid = vp0.screenToModel(prevCentroid)
        val vp1 = ViewportGesturePolicy.applyTwoFingerTransform(
            viewport = vp0,
            centroidScreen = prevCentroid,
            panDeltaScreen = panDelta,
            zoomFactor = 2.5f
        )
        val screenAfterTransform = vp1.modelToScreen(modelAtPrevCentroid)
        assertEquals(currCentroid.x, screenAfterTransform.x, 1e-2f)
        assertEquals(currCentroid.y, screenAfterTransform.y, 1e-2f)
    }

    @Test
    fun `TC-BBOX-06 verify Bulge Arc Polyline, Rotated Text, BlockInsert, and FitToUsableViewport`() {
        // 1. Semicircle bulge (bulge = 1.0) from (0,0) to (20,0) sweeps downward to Y = -10.0
        val polyWithBulge = CadEntity.PolylineEntity(
            vertices = listOf(
                PolylineVertex(Point2D(0.0, 0.0), bulge = 1.0),
                PolylineVertex(Point2D(20.0, 0.0), bulge = 0.0)
            ),
            isClosed = false
        )
        val polyBounds = polyWithBulge.boundingBox()
        assertEquals(0.0, polyBounds.minX, 1e-3)
        assertEquals(20.0, polyBounds.maxX, 1e-3)
        assertEquals(-10.0, polyBounds.minY, 1e-3)

        // 2. Rotated TextEntity (90 degrees) expands vertical extent
        val rotatedText = CadEntity.TextEntity(
            position = Point2D(10.0, 10.0),
            text = "MOUNTING PLATE",
            height = 4.0,
            rotationDeg = 90.0
        )
        val textBounds = rotatedText.boundingBox()
        assertTrue(textBounds.height > 20.0)

        // 3. BlockInsertEntity boundingBox with scale=2.0
        val blockDef = BlockDefinition(
            name = "TEST_BLK",
            basePoint = Point2D(0.0, 0.0),
            entities = listOf(CadEntity.CircleEntity(center = Point2D(0.0, 0.0), radius = 10.0))
        )
        val insert = CadEntity.BlockInsertEntity(
            blockName = "TEST_BLK",
            insertionPoint = Point2D(100.0, 50.0),
            scaleFactor = 2.0
        )
        val insertBounds = insert.boundingBox(mapOf("TEST_BLK" to blockDef))
        assertEquals(80.0, insertBounds.minX, 1e-3)
        assertEquals(120.0, insertBounds.maxX, 1e-3)
        assertEquals(30.0, insertBounds.minY, 1e-3)
        assertEquals(70.0, insertBounds.maxY, 1e-3)

        // 4. Fit Drawing to UsableViewportRect centers geometry at usableRect center and handles single-point degeneracy
        val usableRect = UsableViewportRect(leftPx = 30f, topPx = 50f, rightPx = 830f, bottomPx = 650f)
        val fitted = ViewportTransform().fitToUsableViewport(
            bounds = BoundingBox2D(0.0, 0.0, 120.0, 80.0),
            usableRect = usableRect,
            marginPx = 24f
        )
        val centerScreen = fitted.modelToScreen(Point2D(60.0, 40.0))
        assertEquals(usableRect.centerX, centerScreen.x, 1e-2f)
        assertEquals(usableRect.centerY, centerScreen.y, 1e-2f)

        // Single-point degenerate bounding box must not produce NaN or Infinity
        val singlePtFitted = ViewportTransform().fitToUsableViewport(
            bounds = BoundingBox2D(45.0, 45.0, 45.0, 45.0),
            usableRect = usableRect
        )
        assertTrue(singlePtFitted.pixelsPerModelUnit.isFinite())
        assertTrue(singlePtFitted.panScreenX.isFinite() && singlePtFitted.panScreenY.isFinite())

        // Rebase usable rect preserves center model point
        val smallerRect = UsableViewportRect(leftPx = 30f, topPx = 90f, rightPx = 830f, bottomPx = 650f)
        val modelAtOldCenter = fitted.screenToModel(Offset(usableRect.centerX, usableRect.centerY))
        val rebased = fitted.rebaseForUsableRectChange(usableRect, smallerRect)
        val modelAtNewCenter = rebased.screenToModel(Offset(smallerRect.centerX, smallerRect.centerY))
        assertEquals(modelAtOldCenter.x, modelAtNewCenter.x, 1e-3)
        assertEquals(modelAtOldCenter.y, modelAtNewCenter.y, 1e-3)
    }

    @Test
    fun `TC-CUR-17 verify Automatic Cursor-Boundary Viewport Tracking and Prefix Autocomplete Ranking`() {
        // 1. Opaque HUD surface color check (Section 3)
        assertEquals(1.0f, CadHudSurface.alpha, 1e-5f)

        // 2. Prefix Autocomplete Ranking (Section 14.4.2): typing "L" ranks LINE first
        val lSuggestions = CadCommandCompatibilityCatalog.rankSuggestionsForPrefix("L")
        assertTrue(lSuggestions.isNotEmpty())
        assertEquals(CadCommandId.LINE, lSuggestions.first().id)
        assertTrue(lSuggestions.any { it.id == CadCommandId.LAYER })
        assertTrue(lSuggestions.any { it.id == CadCommandId.LEADER })
        assertTrue(lSuggestions.any { it.id == CadCommandId.LENGTHEN })
        assertFalse(lSuggestions.any { it.id == CadCommandId.CIRCLE })

        val plSuggestions = CadCommandCompatibilityCatalog.rankSuggestionsForPrefix("PL")
        assertEquals(CadCommandId.POLYLINE, plSuggestions.first().id)

        // Coordinate inputs must return empty command autocomplete list
        assertTrue(CadCommandCompatibilityCatalog.rankSuggestionsForPrefix("125,85").isEmpty())
        assertTrue(CadCommandCompatibilityCatalog.rankSuggestionsForPrefix("@25,-10").isEmpty())
        assertTrue(CadCommandCompatibilityCatalog.rankSuggestionsForPrefix("50<45").isEmpty())

        // 3. Automatic Cursor-Boundary Viewport Tracking (Section 17)
        val usableRect = UsableViewportRect(leftPx = 28f, topPx = 42f, rightPx = 780f, bottomPx = 560f)
        val initialVp = ViewportTransform(panScreenX = 200f, panScreenY = 350f, pixelsPerModelUnit = 4.0f)
        // Move cursor far to the right and top (outside usableRect)
        val farCursor = Point2D(300.0, 200.0)
        val trackedVp = initialVp.trackCursorIntoSafeBoundary(
            cursorModelPos = farCursor,
            usableRect = usableRect,
            referencePoint = null,
            minSafeMarginPx = 32f
        )
        val cursorScreenAfter = trackedVp.modelToScreen(farCursor)
        assertTrue(cursorScreenAfter.x in (usableRect.leftPx + 31f)..(usableRect.rightPx - 31f))
        assertTrue(cursorScreenAfter.y in (usableRect.topPx + 31f)..(usableRect.bottomPx - 31f))
        // Model-space coordinate round-trip remains exact
        val roundTripModel = trackedVp.screenToModel(cursorScreenAfter)
        assertEquals(farCursor.x, roundTripModel.x, 1e-3)
        assertEquals(farCursor.y, roundTripModel.y, 1e-3)
    }
}
