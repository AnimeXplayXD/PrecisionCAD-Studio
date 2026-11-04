package com.precisioncad.studio

import androidx.compose.ui.geometry.Offset
import com.precisioncad.studio.cad.dimension.AdaptiveAnnotationRenderer
import com.precisioncad.studio.cad.dimension.AutoDimensionEngine
import com.precisioncad.studio.cad.dimension.DimensionStyleConfig
import com.precisioncad.studio.cad.dimension.QDimDensityPolicy
import com.precisioncad.studio.cad.io.CadDxfInterchange
import com.precisioncad.studio.cad.io.DxfDocumentPayload
import com.precisioncad.studio.cad.kernel.CadGeometryKernel
import com.precisioncad.studio.cad.kernel.CadModificationEngine
import com.precisioncad.studio.cad.kernel.CadSnapAndCoordEngine
import com.precisioncad.studio.cad.kernel.UsableViewportRect
import com.precisioncad.studio.cad.kernel.ViewportTransform
import com.precisioncad.studio.cad.model.BlockDefinition
import com.precisioncad.studio.cad.model.BoundingBox2D
import com.precisioncad.studio.cad.model.CADCommandMatrix
import com.precisioncad.studio.cad.model.CadCommandCompatibilityCatalog
import com.precisioncad.studio.cad.model.CadCommandId
import com.precisioncad.studio.cad.model.CadEntity
import com.precisioncad.studio.cad.model.CadLayer
import com.precisioncad.studio.cad.model.CadUnit
import com.precisioncad.studio.cad.model.ConstraintType
import com.precisioncad.studio.cad.model.DimensionType
import com.precisioncad.studio.cad.model.GeometricConstraintRecord
import com.precisioncad.studio.cad.model.Point2D
import com.precisioncad.studio.cad.model.PolylineVertex
import com.precisioncad.studio.cad.model.PrecisionFormatConfig
import com.precisioncad.studio.ui.components.ViewportGesturePolicy
import com.precisioncad.studio.ui.components.ViewportGestureState
import com.precisioncad.studio.ui.theme.CadHudSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PrecisionCADUnitTest {

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
        // Verify AC1015 required tables, unique hex handles (code 5), subclass markers (code 100), and dash patterns
        assertTrue(dxfText.contains("0\nTABLE\n2\nSTYLE"))
        assertTrue(dxfText.contains("0\nTABLE\n2\nDIMSTYLE"))
        assertTrue(dxfText.contains("3\nSTANDARD"))
        assertTrue(dxfText.contains("100\nAcDbEntity"))
        assertTrue(dxfText.contains("100\nAcDbDimension"))
        assertTrue(dxfText.contains("100\nAcDbAlignedDimension"))
        assertTrue(dxfText.contains("100\nAcDbDiametricDimension"))
        assertTrue(dxfText.contains("\n73\n2\n40\n6.0\n49\n4.0\n74\n0\n49\n-2.0\n74\n0"))
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
    fun `TC-DIM-06 verify initial 136x96mm 6-hole mounting plate geometry and collision-free QDIM leaders`() {
        val template = com.precisioncad.studio.cad.data.CadTemplateFactory.createSixHoleMountingPlate()
        assertTrue(template.entities.none { it is CadEntity.DimensionEntity })
        assertTrue(template.entities.none { it is CadEntity.LeaderEntity })

        val outerPlate = template.entities.filterIsInstance<CadEntity.PolylineEntity>().single()
        val plateBounds = outerPlate.boundingBox()
        assertEquals(32.0, plateBounds.minX, 1e-3)
        assertEquals(168.0, plateBounds.maxX, 1e-3)
        assertEquals(102.0, plateBounds.minY, 1e-3)
        assertEquals(198.0, plateBounds.maxY, 1e-3)
        assertEquals(136.0, plateBounds.width, 1e-3)
        assertEquals(96.0, plateBounds.height, 1e-3)

        val qdimEntities = AutoDimensionEngine.generateQuickDimensions(
            allEntities = template.entities,
            targetEntities = template.entities,
            density = QDimDensityPolicy.BALANCED
        )
        val circles = template.entities.filterIsInstance<CadEntity.CircleEntity>()
        assertEquals(7, circles.size)
        val centerBore = circles.first { abs(it.radius - 14.0) < 1e-3 }
        assertEquals(100.0, centerBore.center.x, 1e-3)
        assertEquals(150.0, centerBore.center.y, 1e-3)

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
            arrangementPreference = com.precisioncad.studio.cad.dimension.DimensionArrangementPreference.AUTO_CHAIN
        )
        val symMarginFinding = symReport.completenessFindings.first {
            it.axisOrFeature == "HORIZONTAL_MARGINS"
        }
        assertEquals(
            com.precisioncad.studio.cad.dimension.DimensionCompletenessCategory.VERIFIED_GEOMETRIC_EQUALITY,
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
            arrangementPreference = com.precisioncad.studio.cad.dimension.DimensionArrangementPreference.AUTO_CHAIN
        )
        val asymMarginFinding = asymReport.completenessFindings.first {
            it.axisOrFeature == "HORIZONTAL_MARGINS"
        }
        assertEquals(
            com.precisioncad.studio.cad.dimension.DimensionCompletenessCategory.INFERRED_UNVERIFIED,
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
            arrangementPreference = com.precisioncad.studio.cad.dimension.DimensionArrangementPreference.BASELINE
        )
        assertTrue(
            baselineReport.placements.any {
                it.arrangementMode == com.precisioncad.studio.cad.dimension.DimensionArrangementMode.BASELINE
            }
        )
        val (ordinateEntities, ordinateReport) = AutoDimensionEngine.generateQuickDimensionsWithAlignmentReport(
            allEntities = listOf(outerRect) + symHoles,
            targetEntities = listOf(outerRect) + symHoles,
            density = QDimDensityPolicy.BALANCED,
            arrangementPreference = com.precisioncad.studio.cad.dimension.DimensionArrangementPreference.ORDINATE
        )
        assertTrue(
            ordinateEntities.filterIsInstance<CadEntity.DimensionEntity>().any {
                it.dimensionType == DimensionType.ORDINATE_X || it.dimensionType == DimensionType.ORDINATE_Y
            }
        )
        assertTrue(
            ordinateReport.placements.any {
                it.arrangementMode == com.precisioncad.studio.cad.dimension.DimensionArrangementMode.ORDINATE
            }
        )

        val overallHoriz = symReport.placements.first {
            it.dimension.dimensionType == DimensionType.LINEAR_HORIZONTAL &&
                it.dimension.hierarchyTier == com.precisioncad.studio.cad.model.DimensionHierarchyTier.PRIMARY_OVERALL
        }
        val featureHoriz = symReport.placements.filter {
            it.dimension.dimensionType == DimensionType.LINEAR_HORIZONTAL &&
                it.dimension.hierarchyTier == com.precisioncad.studio.cad.model.DimensionHierarchyTier.SECONDARY_FEATURE
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
            hierarchyTier = com.precisioncad.studio.cad.model.DimensionHierarchyTier.SECONDARY_FEATURE
        )
        val shortReport = com.precisioncad.studio.cad.dimension.DimensionAlignmentEngine.alignAndArrangeDimensions(
            candidateDimensions = listOf(shortCandidate),
            overallBounds = BoundingBox2D(0.0, 0.0, 120.0, 80.0),
            existingManualDimensions = emptyList()
        )
        val shortPlacement = shortReport.placements.single()
        assertEquals(com.precisioncad.studio.cad.dimension.ArrowPlacementMode.OUTSIDE, shortPlacement.arrowPlacementMode)
        assertEquals(com.precisioncad.studio.cad.dimension.TextPlacementMode.EXTERNAL_OFFSET, shortPlacement.textPlacementMode)
    }

    @Test
    fun `TC-NAV-14 verify v15 Sheet Gesture State Machine (1-Finger Pan, Simultaneous 2-Finger Pan and Pinch Zoom, Left-Edge Drawer Reveal)`() {
        // 1. 0 -> 1 pointer outside left-edge zone enters ONE_POINTER_PAN
        assertEquals(
            ViewportGestureState.ONE_POINTER_PAN,
            ViewportGesturePolicy.transitionState(
                ViewportGestureState.IDLE,
                activePointerCount = 1,
                startedInLeftEdgeZone = false
            )
        )
        // 2. 0 -> 1 pointer inside left-edge zone enters LEFT_EDGE_DRAWER_REVEAL
        assertEquals(
            ViewportGestureState.LEFT_EDGE_DRAWER_REVEAL,
            ViewportGesturePolicy.transitionState(
                ViewportGestureState.IDLE,
                activePointerCount = 1,
                startedInLeftEdgeZone = true
            )
        )
        // 3. 1 -> 2 pointers enters TWO_POINTER_PINCH_ZOOM
        assertEquals(
            ViewportGestureState.TWO_POINTER_PINCH_ZOOM,
            ViewportGesturePolicy.transitionState(
                ViewportGestureState.ONE_POINTER_PAN,
                activePointerCount = 2
            )
        )
        // 4. 2 -> 1 pointer enters POST_PINCH_POINTER_SUPPRESSED so trailing finger does not jump
        assertEquals(
            ViewportGestureState.POST_PINCH_POINTER_SUPPRESSED,
            ViewportGesturePolicy.transitionState(
                ViewportGestureState.TWO_POINTER_PINCH_ZOOM,
                activePointerCount = 1
            )
        )
        // 5. >= 3 pointers cancels until all pointers lift
        val cancelled = ViewportGesturePolicy.transitionState(ViewportGestureState.TWO_POINTER_PINCH_ZOOM, activePointerCount = 3)
        assertEquals(ViewportGestureState.CANCELLED, cancelled)
        assertEquals(ViewportGestureState.CANCELLED, ViewportGesturePolicy.transitionState(cancelled, activePointerCount = 2))
        assertEquals(ViewportGestureState.IDLE, ViewportGesturePolicy.transitionState(cancelled, activePointerCount = 0))

        // 6. One-finger pan translates panScreenX / panScreenY without changing zoom scale
        val vp0 = ViewportTransform(panScreenX = 240f, panScreenY = 360f, pixelsPerModelUnit = 4.0f)
        val panned = ViewportGesturePolicy.applyOneFingerPan(vp0, Offset(35f, -20f))
        assertEquals(275f, panned.panScreenX, 1e-4f)
        assertEquals(340f, panned.panScreenY, 1e-4f)
        assertEquals(vp0.pixelsPerModelUnit, panned.pixelsPerModelUnit, 1e-5f)

        // 7. Simultaneous Two-Finger Pan + Pinch Zoom translates by centroid delta and scales around centroid
        val prevCentroid = Offset(400f, 300f)
        val panDelta = Offset(25f, -15f)
        val currentCentroid = prevCentroid + panDelta
        val modelAtPrevCentroid = vp0.screenToModel(prevCentroid)
        val twoFingerUpdated = ViewportGesturePolicy.applyTwoFingerTransform(
            viewport = vp0,
            centroidScreen = currentCentroid,
            panDeltaScreen = panDelta,
            zoomFactor = 2.0f
        )
        assertEquals(8.0f, twoFingerUpdated.pixelsPerModelUnit, 1e-4f)
        val screenAfterTwoFinger = twoFingerUpdated.modelToScreen(modelAtPrevCentroid)
        assertEquals(currentCentroid.x, screenAfterTwoFinger.x, 1e-2f)
        assertEquals(currentCentroid.y, screenAfterTwoFinger.y, 1e-2f)
    }

    @Test
    fun `TC-TPAD-24 verify v15 Reverted Direct 1-to-1 TouchpadMotionEngine without touch-slop delay or velocity acceleration`() {
        val engine = com.precisioncad.studio.ui.components.TouchpadMotionEngine()
        engine.onDown(Offset(100f, 100f))
        assertEquals(com.precisioncad.studio.ui.components.TouchpadGestureState.DRAG_ACTIVE, engine.state)

        // Stationary tap (zero delta) produces exact 0.0 movement
        val zeroDelta = engine.onMoveSample(Offset(100f, 100f), activePointerCount = 1)
        assertEquals(0f, zeroDelta.x, 1e-5f)
        assertEquals(0f, zeroDelta.y, 1e-5f)

        // First non-zero sample immediately emits exact 1:1 relative delta (no touch-slop discard or velocity multiplier)
        val firstMoveDelta = engine.onMoveSample(Offset(106f, 96f), activePointerCount = 1, velocityPxPerSec = 900f)
        assertEquals(6f, firstMoveDelta.x, 1e-5f)
        assertEquals(-4f, firstMoveDelta.y, 1e-5f)

        // Subsequent drag sample continues direct 1:1 delta
        val secondMoveDelta = engine.onMoveSample(Offset(118f, 90f), activePointerCount = 1, velocityPxPerSec = 1200f)
        assertEquals(12f, secondMoveDelta.x, 1e-5f)
        assertEquals(-6f, secondMoveDelta.y, 1e-5f)

        // Multi-pointer touch cancels touchpad motion and freezes cursor
        val multiDelta = engine.onMoveSample(Offset(140f, 100f), activePointerCount = 2)
        assertEquals(com.precisioncad.studio.ui.components.TouchpadGestureState.MULTI_POINTER_CANCELLED, engine.state)
        assertEquals(0f, multiDelta.x, 1e-5f)

        // Velocity gain is strictly 1.0f in v15 direct 1:1 mode
        assertEquals(1.0f, com.precisioncad.studio.ui.components.TouchpadMotionEngine.computeVelocityGain(0f), 1e-5f)
        assertEquals(1.0f, com.precisioncad.studio.ui.components.TouchpadMotionEngine.computeVelocityGain(1200f), 1e-5f)
    }

    @Test
    fun `TC-POLAR-43 verify v18 Visual Polar Tracking R, live bearing theta, narrow visual acquisition theta_p, and independent PolarSnap`() {
        val basePoint = Point2D(50.0, 50.0)
        val config = com.precisioncad.studio.cad.model.GridAndSnapConfig(
            polarTrackingEnabled = true,
            polarSnapEnabled = false,
            polarTrackingAngleDeg = 45.0,
            polarAdditionalAnglesDeg = listOf(22.5),
            polarSnapCustomAnglesDeg = listOf(126.0),
            polarAcquisitionToleranceDeg = 5.0
        )

        // 1. Within visual acquisition tolerance of 90 degrees (cursor at 89.80 deg, R = 40.0):
        //    live theta = 89.80 deg, acquired visual polarAngleDeg = 90.0 deg
        val cursorNear90 = basePoint.polarOffset(40.0, 89.80)
        val preview90 = CadSnapAndCoordEngine.computePolarTrackingPreview(
            basePoint = basePoint,
            rawCursorPoint = cursorNear90,
            snapConfig = config,
            previousBasePoint = null
        )
        assertNotNull(preview90)
        assertEquals(40.0, preview90!!.radialDistanceR, 1e-3)
        assertEquals(89.80, preview90.actualAngleThetaDeg!!, 1e-2)
        assertTrue(preview90.isPolarGuideAcquired)
        assertEquals(90.0, preview90.activePolarAngleDeg!!, 1e-3)

        // 2. Near custom PolarSnap angle 126 degrees (cursor at 124.5 deg, R = 60.0):
        //    PolarSnap acquires 126.0 deg (within 5 deg snap tolerance), while Visual Polar Tracking
        //    does NOT falsely snap to 126 deg because 124.5 deg is outside the 0.35 deg visual corridor!
        val cursorNear126 = basePoint.polarOffset(60.0, 124.5)
        val preview126 = CadSnapAndCoordEngine.computePolarTrackingPreview(
            basePoint = basePoint,
            rawCursorPoint = cursorNear126,
            snapConfig = config,
            previousBasePoint = null
        )
        assertNotNull(preview126)
        assertEquals(60.0, preview126!!.radialDistanceR, 1e-3)
        assertEquals(124.5, preview126.actualAngleThetaDeg!!, 1e-2)
        assertFalse(preview126.isPolarGuideAcquired)
        assertEquals(126.0, preview126.activePolarSnapAngleDeg!!, 1e-3)

        // 3. Within visual acquisition tolerance of 180 degrees (cursor at 180.20 deg, R = 75.0)
        val cursorNear180 = basePoint.polarOffset(75.0, 180.20)
        val preview180 = CadSnapAndCoordEngine.computePolarTrackingPreview(
            basePoint = basePoint,
            rawCursorPoint = cursorNear180,
            snapConfig = config,
            previousBasePoint = null
        )
        assertNotNull(preview180)
        assertEquals(180.20, preview180!!.actualAngleThetaDeg!!, 1e-2)
        assertTrue(preview180.isPolarGuideAcquired)
        assertEquals(180.0, preview180.activePolarAngleDeg!!, 1e-3)

        // 4. Exact whole-number bearing 108.0 deg acquires visual 108.0 deg while PolarSnap remains null
        val cursorFree = basePoint.polarOffset(50.0, 108.0)
        val previewFree = CadSnapAndCoordEngine.computePolarTrackingPreview(
            basePoint = basePoint,
            rawCursorPoint = cursorFree,
            snapConfig = config,
            previousBasePoint = null
        )
        assertNotNull(previewFree)
        assertEquals(50.0, previewFree!!.radialDistanceR, 1e-3)
        assertEquals(108.0, previewFree.actualAngleThetaDeg!!, 1e-2)
        assertTrue(previewFree.isPolarGuideAcquired)
        assertEquals(108.0, previewFree.activePolarAngleDeg!!, 1e-3)
        assertEquals(null, previewFree.activePolarSnapAngleDeg)

        // 5. Optional PolarSnap projects candidate point onto acquired 126.0 deg PolarSnap ray
        val (snappedWithPolar, _) = CadSnapAndCoordEngine.resolveCursorSnap(
            rawModelCursor = cursorNear126,
            referencePoint = basePoint,
            entities = emptyList(),
            snapConfig = config.copy(osnapEnabled = false, gridSnapEnabled = false, polarSnapEnabled = true),
            viewport = ViewportTransform(pixelsPerModelUnit = 3.0f)
        )
        val projectedAngle = basePoint.angleToDeg(snappedWithPolar)
        assertEquals(126.0, projectedAngle, 1e-3)
    }

    @Test
    fun `TC-QDIM-31 verify v15 QDIM Local Profile Feature Classification on Concave U-Notch and BELOW_MINIMUM_FEATURE_LENGTH diagnostic`() {
        // Concave U-notch polygon from Section 31.1 plus a tiny 0.5mm micro-segment to verify BELOW_MINIMUM_FEATURE_LENGTH diagnostic
        val uNotch = CadEntity.PolylineEntity(
            id = "u_notch_poly",
            vertices = listOf(
                PolylineVertex(Point2D(40.0, 60.0)),
                PolylineVertex(Point2D(196.0, 60.0)),
                PolylineVertex(Point2D(196.0, 198.0)),
                PolylineVertex(Point2D(158.0, 198.0)),
                PolylineVertex(Point2D(158.0, 102.0)),
                PolylineVertex(Point2D(82.0, 102.0)),
                PolylineVertex(Point2D(82.0, 198.0)),
                PolylineVertex(Point2D(40.0, 198.0))
            ),
            isClosed = true
        )
        val microLine = CadEntity.LineEntity(
            id = "micro_edge",
            start = Point2D(100.0, 100.0),
            end = Point2D(100.5, 100.0)
        )
        val (qdimEntities, report) = AutoDimensionEngine.generateQuickDimensionsWithAlignmentReport(
            allEntities = listOf(uNotch, microLine),
            targetEntities = listOf(uNotch, microLine),
            density = QDimDensityPolicy.BALANCED
        )
        val dims = qdimEntities.filterIsInstance<CadEntity.DimensionEntity>()
        val horizValues = dims.filter { it.dimensionType == DimensionType.LINEAR_HORIZONTAL }
            .map { it.measuredModelValue() }
        val vertValues = dims.filter { it.dimensionType == DimensionType.LINEAR_VERTICAL }
            .map { it.measuredModelValue() }

        // Overall 156 x 138
        assertTrue(horizValues.any { abs(it - 156.0) < 1e-3 })
        assertTrue(vertValues.any { abs(it - 138.0) < 1e-3 })
        // Local horizontal shoulders (42, 38) and inner recess shelf (76)
        assertTrue(horizValues.any { abs(it - 42.0) < 1e-3 })
        assertTrue(horizValues.any { abs(it - 76.0) < 1e-3 })
        assertTrue(horizValues.any { abs(it - 38.0) < 1e-3 })
        // Local vertical notch depth (96)
        assertTrue(vertValues.any { abs(it - 96.0) < 1e-3 })
        assertTrue(
            report.edgeDiagnostics.any {
                it.sourceEntityId == "micro_edge" &&
                    it.status == com.precisioncad.studio.cad.dimension.EdgeDimensionStatus.BELOW_MINIMUM_FEATURE_LENGTH
            }
        )
        assertTrue(report.generationCompletenessScore >= 90f)
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

    @Test
    fun `TC-DIM-16 verify v16 QDIM Triangle Angular Candidates, Donut Inner Outer Diameters, and Ellipse Major Minor Axes`() {
        // 1. Equilateral triangle (3-sided polygon) -> BALANCED emits 3× 60° and zero fabricated radius callouts;
        //    DETAILED emits all 3 individual 60° vertex angles
        val equilateralTriangle = CadGeometryKernel.createRegularPolygon(
            center = Point2D(50.0, 50.0),
            radius = 30.0,
            sides = 3,
            circumscribed = false
        )
        val balancedTriEntities = AutoDimensionEngine.generateQuickDimensions(
            allEntities = listOf(equilateralTriangle),
            targetEntities = listOf(equilateralTriangle),
            density = QDimDensityPolicy.BALANCED
        )
        val balancedTriDims = balancedTriEntities.filterIsInstance<CadEntity.DimensionEntity>()
        // Straight-edged triangle must NEVER receive a fabricated radius callout
        assertTrue(balancedTriDims.none { it.dimensionType == DimensionType.RADIUS })
        val balancedAngularDims = balancedTriDims.filter { it.dimensionType == DimensionType.ANGULAR }
        assertEquals(1, balancedAngularDims.size)
        assertEquals("3× ", balancedAngularDims.first().customPrefix)
        assertEquals(60.0, balancedAngularDims.first().measuredModelValue(), 0.2)

        val detailedTriEntities = AutoDimensionEngine.generateQuickDimensions(
            allEntities = listOf(equilateralTriangle),
            targetEntities = listOf(equilateralTriangle),
            density = QDimDensityPolicy.DETAILED
        )
        val detailedAngularDims = detailedTriEntities.filterIsInstance<CadEntity.DimensionEntity>()
            .filter { it.dimensionType == DimensionType.ANGULAR }
        // V19 §14.3.7: Triangle in Detailed mode caps interior angles at 2 so the 3rd is never redundant (A+B+C=180°)
        assertEquals(2, detailedAngularDims.size)
        assertTrue(detailedAngularDims.all { abs(it.measuredModelValue() - 60.0) < 0.2 })

        // 2. Scalene 3-4-5 right triangle from 3 connected LineEntity segments -> emits 3 distinct interior angles (90°, ~36.87°, ~53.13°)
        val scaleneLines = listOf(
            CadEntity.LineEntity(id = "s1", start = Point2D(0.0, 0.0), end = Point2D(40.0, 0.0)),
            CadEntity.LineEntity(id = "s2", start = Point2D(40.0, 0.0), end = Point2D(0.0, 30.0)),
            CadEntity.LineEntity(id = "s3", start = Point2D(0.0, 30.0), end = Point2D(0.0, 0.0))
        )
        val scaleneQDim = AutoDimensionEngine.generateQuickDimensions(
            allEntities = scaleneLines,
            targetEntities = scaleneLines,
            density = QDimDensityPolicy.BALANCED
        )
        val scaleneAngDims = scaleneQDim.filterIsInstance<CadEntity.DimensionEntity>()
            .filter { it.dimensionType == DimensionType.ANGULAR }
        assertEquals(3, scaleneAngDims.size)
        assertTrue(scaleneAngDims.any { abs(it.measuredModelValue() - 90.0) < 0.2 })
        assertTrue(scaleneAngDims.any { abs(it.measuredModelValue() - 36.87) < 0.5 })
        assertTrue(scaleneAngDims.any { abs(it.measuredModelValue() - 53.13) < 0.5 })

        // 3. DonutEntity -> QDIM extracts both outer diameter and inner diameter
        val donut = CadEntity.DonutEntity(
            id = "donut_1",
            center = Point2D(100.0, 100.0),
            innerDiameter = 12.0,
            outerDiameter = 28.0
        )
        val donutQDim = AutoDimensionEngine.generateQuickDimensions(
            allEntities = listOf(donut),
            targetEntities = listOf(donut),
            density = QDimDensityPolicy.BALANCED
        )
        val donutDims = donutQDim.filterIsInstance<CadEntity.DimensionEntity>()
            .filter { it.dimensionType == DimensionType.DIAMETER }
        assertEquals(2, donutDims.size)
        assertTrue(donutDims.any { abs(it.measuredModelValue() - 28.0) < 1e-3 })
        assertTrue(donutDims.any { abs(it.measuredModelValue() - 12.0) < 1e-3 })

        // 4. EllipseEntity -> QDIM extracts both major-axis (2a = 80) and minor-axis (2b = 40) ALIGNED dimensions
        val ellipse = CadEntity.EllipseEntity(
            id = "ellipse_1",
            center = Point2D(80.0, 60.0),
            majorRadius = 40.0,
            minorRadius = 20.0,
            rotationAngleDeg = 30.0
        )
        val ellipseQDim = AutoDimensionEngine.generateQuickDimensions(
            allEntities = listOf(ellipse),
            targetEntities = listOf(ellipse),
            density = QDimDensityPolicy.BALANCED
        )
        val ellipseAlignedDims = ellipseQDim.filterIsInstance<CadEntity.DimensionEntity>()
            .filter { it.dimensionType == DimensionType.ALIGNED }
        assertEquals(2, ellipseAlignedDims.size)
        assertTrue(ellipseAlignedDims.any { abs(it.measuredModelValue() - 80.0) < 1e-3 })
        assertTrue(ellipseAlignedDims.any { abs(it.measuredModelValue() - 40.0) < 1e-3 })
    }

    @Test
    fun `TC-V17-01 verify v17 Native PCAD v2 Round-Trip Serialization, Canonical Basename, and Separated Visual Polar Tracking vs PolarSnap`() {
        // 1. Canonical basename extraction (Section 4.6)
        assertEquals("Geo", com.precisioncad.studio.cad.io.CadPcadPersistence.canonicalBasename("Geo.pcad"))
        assertEquals("Geo", com.precisioncad.studio.cad.io.CadPcadPersistence.canonicalBasename("Geo.dxf"))
        assertEquals("Geo", com.precisioncad.studio.cad.io.CadPcadPersistence.canonicalBasename("Geo.pdf"))
        assertEquals("Untitled_Drawing", com.precisioncad.studio.cad.io.CadPcadPersistence.canonicalBasename("   "))

        // 2. Native .pcad v2 round-trip serialization & deserialization (Section 4.1–4.5)
        val baseTemplate = com.precisioncad.studio.cad.data.CadTemplateFactory.createSixHoleMountingPlate()
        val snapCfg = com.precisioncad.studio.cad.model.GridAndSnapConfig(
            polarTrackingEnabled = true,
            polarSnapEnabled = true,
            polarSnapPresetAnglesDeg = setOf(30.0, 45.0, 90.0),
            polarSnapCustomAnglesDeg = listOf(126.0, 152.5),
            polarAdditionalAnglesDeg = listOf(22.5, 126.5)
        )
        val pcadDoc = com.precisioncad.studio.cad.io.PcadProjectDocument(
            schemaVersion = com.precisioncad.studio.cad.io.CadPcadPersistence.CURRENT_SCHEMA_VERSION,
            projectId = "proj_geo_v17",
            projectName = "Geo",
            savedAtEpochMs = 1762000000000L,
            explicitSavePath = "Documents/PrecisionCAD/Geo.pcad",
            storageUnit = CadUnit.MM,
            displayUnit = CadUnit.MM,
            unitChangeMode = com.precisioncad.studio.cad.model.UnitChangeMode.CONVERT_DISPLAY_UNITS,
            precision = PrecisionFormatConfig(linearDecimals = 3, angularDecimals = 1),
            workspaceSpace = com.precisioncad.studio.cad.model.WorkspaceSpace.PAPER_SPACE,
            sheetSetup = baseTemplate.sheetSetup.copy(
                titleBlockTitle = "Geo"
            ),
            gridSnapConfig = snapCfg,
            controllerConfig = com.precisioncad.studio.cad.model.VirtualControllerConfig(),
            isQuickDimensionEnabled = true,
            qdimDensity = QDimDensityPolicy.DETAILED,
            qdimArrangement = com.precisioncad.studio.cad.dimension.DimensionArrangementPreference.BASELINE,
            layers = baseTemplate.layers,
            blocks = baseTemplate.blocks,
            entities = baseTemplate.entities,
            constraints = emptyList()
        )
        val jsonText = com.precisioncad.studio.cad.io.CadPcadPersistence.serializeToPcadJson(pcadDoc)
        assertTrue(jsonText.contains("\"schemaVersion\": 2"))
        assertTrue(jsonText.contains("\"projectName\": \"Geo\""))

        val restored = com.precisioncad.studio.cad.io.CadPcadPersistence.deserializeFromPcadJson(jsonText)
        assertNotNull(restored)
        assertEquals(2, restored!!.schemaVersion)
        assertEquals("Geo", restored.projectName)
        assertEquals("Geo", restored.sheetSetup.titleBlockTitle)
        assertEquals(pcadDoc.entities.size, restored.entities.size)
        assertEquals(setOf(30.0, 45.0, 90.0), restored.gridSnapConfig.polarSnapPresetAnglesDeg)
        assertEquals(listOf(126.0, 152.5), restored.gridSnapConfig.polarSnapCustomAnglesDeg)
        assertEquals(listOf(22.5, 126.5), restored.gridSnapConfig.polarAdditionalAnglesDeg)

        // 3. Separated Visual Polar Tracking (0°..359° whole numbers + fractional custom) vs PolarSnap
        val refPt = Point2D(0.0, 0.0)
        // Visual guide acquires whole-number bearing 37° when within 0.35° even though 37° is NOT in PolarSnap presets
        val cursorAt37 = refPt.polarOffset(50.0, 37.0)
        val visualPreview37 = CadSnapAndCoordEngine.computePolarTrackingPreview(
            basePoint = refPt,
            rawCursorPoint = cursorAt37,
            snapConfig = snapCfg.copy(polarSnapEnabled = false),
            previousBasePoint = null
        )
        assertNotNull(visualPreview37)
        assertTrue(visualPreview37!!.isPolarGuideAcquired)
        assertEquals(37.0, visualPreview37.activePolarAngleDeg!!, 1e-3)

        // When PolarSnap is enabled, cursor at 43° (within 5° of 45° PolarSnap preset) snaps to 45°, NOT 43°
        val cursorAt43 = refPt.polarOffset(50.0, 43.0)
        val (snappedPt, _) = CadSnapAndCoordEngine.resolveCursorSnap(
            rawModelCursor = cursorAt43,
            referencePoint = refPt,
            entities = emptyList(),
            snapConfig = snapCfg.copy(osnapEnabled = false, gridSnapEnabled = false, polarSnapEnabled = true),
            viewport = ViewportTransform(pixelsPerModelUnit = 3.0f)
        )
        assertEquals(45.0, refPt.angleToDeg(snappedPt), 1e-3)
    }

    @Test
    fun `TC-V18-01 verify Locale-Independent CadNumberFormatter and PrecisionFormatConfig across US, GERMANY, bn-IN, and tr-TR`() {
        val originalLocale = java.util.Locale.getDefault()
        val testLocales = listOf(
            java.util.Locale.US,
            java.util.Locale.GERMANY,
            java.util.Locale.forLanguageTag("bn-IN"),
            java.util.Locale.forLanguageTag("tr-TR")
        )
        val prec = PrecisionFormatConfig(linearDecimals = 2, angularDecimals = 2, showUnitSuffix = false)
        try {
            for (loc in testLocales) {
                java.util.Locale.setDefault(loc)
                assertEquals("13.62", com.precisioncad.studio.cad.model.CadNumberFormatter.formatFixed(13.62, 2))
                assertEquals("13.620", com.precisioncad.studio.cad.model.CadNumberFormatter.formatFixed(13.62, 3))
                assertEquals("135.25°", com.precisioncad.studio.cad.model.CadNumberFormatter.formatAngleDeg(135.25, 2, true))
                assertEquals("13.62", prec.formatLinear(13.62, CadUnit.MM, CadUnit.MM))
                assertEquals("45.50°", prec.formatAngle(45.5))

                val diag = com.precisioncad.studio.cad.model.CadNumberFormatter.formatLineDiagnostic(
                    start = Point2D(13.62, 24.5),
                    end = Point2D(100.125, 80.0),
                    length = 104.25,
                    angleDeg = 37.5
                )
                assertTrue(diag.contains("(13.620, 24.500)"))
                assertTrue(diag.contains("(100.125, 80.000)"))
                assertTrue(diag.contains("Length: 104.250"))
                assertTrue(diag.contains("Angle: 37.50°"))
                assertFalse("Decimal comma must never appear in $loc", diag.contains("13,620"))
            }
        } finally {
            java.util.Locale.setDefault(originalLocale)
        }
    }

    @Test
    fun `TC-V18-02 verify Zoom-Adaptive Grid Snap effective step quantization, hysteresis, jump guard, and ORTHO independence`() {
        // 1. At reference zoom (Z == Z_0), effective interval equals configured base interval (10.0)
        val step1x = CadSnapAndCoordEngine.computeEffectiveGridSnapInterval(
            baseInterval = 10.0,
            currentPixelsPerModelUnit = 4.0f,
            referencePixelsPerModelUnit = 4.0
        )
        assertEquals(10.0, step1x, 1e-6)

        // 2. At 2x zoom in (currentZ = 8.0, refZ = 4.0 -> rawTarget = 5.0), effective interval halves to 5.0
        val step2x = CadSnapAndCoordEngine.computeEffectiveGridSnapInterval(
            baseInterval = 10.0,
            currentPixelsPerModelUnit = 8.0f,
            referencePixelsPerModelUnit = 4.0
        )
        assertEquals(5.0, step2x, 1e-6)

        // 3. At 4x zoom in (currentZ = 16.0, refZ = 4.0 -> rawTarget = 2.5), effective interval scales to 2.5
        val step4x = CadSnapAndCoordEngine.computeEffectiveGridSnapInterval(
            baseInterval = 10.0,
            currentPixelsPerModelUnit = 16.0f,
            referencePixelsPerModelUnit = 4.0
        )
        assertEquals(2.5, step4x, 1e-6)

        // 4. At 10x zoom in (currentZ = 40.0, refZ = 4.0 -> rawTarget = 1.0), effective interval scales to 1.0
        val step10x = CadSnapAndCoordEngine.computeEffectiveGridSnapInterval(
            baseInterval = 10.0,
            currentPixelsPerModelUnit = 40.0f,
            referencePixelsPerModelUnit = 4.0
        )
        assertEquals(1.0, step10x, 1e-6)

        // 5. Hysteresis guard: slight zoom fluctuation within 12% retains previousEffectiveInterval (5.0)
        val stepHysteresis = CadSnapAndCoordEngine.computeEffectiveGridSnapInterval(
            baseInterval = 10.0,
            currentPixelsPerModelUnit = 8.4f,
            referencePixelsPerModelUnit = 4.0,
            previousEffectiveInterval = 5.0
        )
        assertEquals(5.0, stepHysteresis, 1e-6)

        // 6. Usable-viewport boundary guard: if snapping would push a visible cursor outside UsableViewportRect, keep rawPoint
        val vp = ViewportTransform(panScreenX = 100f, panScreenY = 500f, pixelsPerModelUnit = 10.0f)
        val usable = UsableViewportRect(leftPx = 105f, topPx = 50f, rightPx = 800f, bottomPx = 600f)
        // rawPoint at x = 0.6 -> screenX = 100 + 6 = 106 (inside usable.leftPx = 105),
        // nearest grid at step 5.0 is x = 0.0 -> screenX = 100 (outside usable.leftPx = 105)
        val guardedPt = CadSnapAndCoordEngine.applyZoomAdaptiveGridSnap(
            rawPoint = Point2D(0.6, 10.0),
            baseInterval = 5.0,
            viewport = vp,
            referencePixelsPerModelUnit = 10.0,
            usableRect = usable
        )
        assertEquals(0.6, guardedPt.x, 1e-5)

        // 7. ORTHO + Zoom-Adaptive Grid Snap: dominant axis stays locked to referencePoint while free axis snaps to effective grid
        val refPt = Point2D(12.34, 27.89)
        val rawMoved = Point2D(26.1, 28.2)
        val (orthoGridSnapped, _) = CadSnapAndCoordEngine.resolveCursorSnap(
            rawModelCursor = rawMoved,
            referencePoint = refPt,
            entities = emptyList(),
            snapConfig = com.precisioncad.studio.cad.model.GridAndSnapConfig(
                osnapEnabled = false,
                polarSnapEnabled = false,
                orthoEnabled = true,
                gridSnapEnabled = true,
                gridSnapInterval = 10.0
            ),
            viewport = ViewportTransform(panScreenX = 200f, panScreenY = 400f, pixelsPerModelUnit = 16.0f),
            referencePixelsPerModelUnit = 4.0
        )
        // Effective grid step at 4x zoom is 2.5 -> X snaps 26.1 to 25.0, while Y remains locked to refPt.y (27.89)!
        assertEquals(25.0, orthoGridSnapped.x, 1e-4)
        assertEquals(refPt.y, orthoGridSnapped.y, 1e-6)
    }

    @Test
    fun `TC-V18-03 verify Visual Polar Tracking release hysteresis, stale 135 deg disappearance, and Relative-to-Last-Segment basis`() {
        val basePt = Point2D(0.0, 0.0)
        val prevPt = Point2D(-50.0, -50.0) // segment from (-50,-50) to (0,0) has bearing 45°
        val cfg = com.precisioncad.studio.cad.model.GridAndSnapConfig(
            polarTrackingEnabled = true,
            polarSnapEnabled = false,
            polarAdditionalAnglesDeg = listOf(22.5),
            polarSnapCustomAnglesDeg = listOf(126.0)
        )

        // 1. At raw angle 135.1 deg (within 0.35 deg acquire tolerance of 135 deg), visual 135 deg guide is acquired
        val p135_1 = basePt.polarOffset(80.0, 135.1)
        val prev1 = CadSnapAndCoordEngine.computePolarTrackingPreview(
            basePoint = basePt,
            rawCursorPoint = p135_1,
            snapConfig = cfg
        )
        assertNotNull(prev1)
        assertTrue(prev1!!.isPolarGuideAcquired)
        assertEquals(135.0, prev1.activePolarAngleDeg!!, 1e-3)

        // 2. At raw angle 135.45 deg (between 0.35 deg acquire and 0.55 deg release), retained when previouslyAcquired = 135.0
        val p135_45 = basePt.polarOffset(80.0, 135.45)
        val prevRetained = CadSnapAndCoordEngine.computePolarTrackingPreview(
            basePoint = basePt,
            rawCursorPoint = p135_45,
            snapConfig = cfg,
            previouslyAcquiredVisualAngleDeg = 135.0
        )
        assertTrue(prevRetained!!.isPolarGuideAcquired)
        assertEquals(135.0, prevRetained.activePolarAngleDeg!!, 1e-3)

        // 3. At raw angle 127.4 deg (away from 135 deg and away from integer 127 deg by 0.4 > 0.35), stale 135 deg guide disappears!
        val p127_4 = basePt.polarOffset(80.0, 127.4)
        val prevStaleGone = CadSnapAndCoordEngine.computePolarTrackingPreview(
            basePoint = basePt,
            rawCursorPoint = p127_4,
            snapConfig = cfg,
            previouslyAcquiredVisualAngleDeg = 135.0
        )
        assertFalse(prevStaleGone!!.isPolarGuideAcquired)
        assertEquals(null, prevStaleGone.activePolarAngleDeg)

        // 4. Custom fractional visual angle 22.5 deg acquires visually without being inserted into PolarSnap
        val p22_5 = basePt.polarOffset(80.0, 22.5)
        val prev22_5 = CadSnapAndCoordEngine.computePolarTrackingPreview(
            basePoint = basePt,
            rawCursorPoint = p22_5,
            snapConfig = cfg
        )
        assertTrue(prev22_5!!.isPolarGuideAcquired)
        assertEquals(22.5, prev22_5.activePolarAngleDeg!!, 1e-3)
        assertFalse(CadSnapAndCoordEngine.buildPolarSnapAngles(cfg).contains(22.5))

        // 5. Relative-to-last-segment uses prior segment bearing (45 deg) and labels basis "Last Segment"
        val pWorld75 = basePt.polarOffset(60.0, 75.0) // 30 deg relative to 45 deg segment
        val prevRel = CadSnapAndCoordEngine.computePolarTrackingPreview(
            basePoint = basePt,
            rawCursorPoint = pWorld75,
            snapConfig = cfg.copy(polarRelativeToLastSegment = true),
            previousBasePoint = prevPt
        )
        assertEquals("Last Segment", prevRel!!.referenceBasisLabel)
        assertEquals(45.0, prevRel.referenceAngleDeg, 1e-3)
        assertEquals(30.0, prevRel.actualAngleThetaDeg!!, 1e-3)
        assertEquals(30.0, prevRel.activePolarAngleDeg!!, 1e-3)
        assertEquals(75.0, prevRel.worldPolarAngleDeg!!, 1e-3)
    }

    @Test
    fun `TC-V18-04 verify Section-Local CadDeckIdleTimerState isolation, CommandDeckVisible rule, and binary label normalization`() {
        val deckA = com.precisioncad.studio.ui.components.CadDeckIdleTimerState(deckId = "deck_a")
        val deckB = com.precisioncad.studio.ui.components.CadDeckIdleTimerState(deckId = "deck_b")

        assertEquals(60_000L, com.precisioncad.studio.ui.components.CadDeckIdleTimerState.IDLE_PEEK_INTERVAL_MS)
        assertEquals(4_000L, com.precisioncad.studio.ui.components.CadDeckIdleTimerState.IDLE_PEEK_HOLD_MS)
        assertEquals(4_000L, com.precisioncad.studio.ui.components.CadDeckIdleTimerState.POST_EXCHANGE_REVEAL_MS)

        // Initially at rest: rear card is completely concealed on both decks
        assertFalse(deckA.isRearCardVisible)
        assertFalse(deckB.isRearCardVisible)

        // Direct card exchange on Deck A triggers post-exchange reveal on Deck A ONLY; Deck B remains untouched
        val genB0 = deckB.interactionGeneration
        deckA.onCardExchanged()
        assertTrue(deckA.isPostExchangeRevealActive)
        assertTrue(deckA.isRearCardVisible)
        assertFalse(deckB.isRearCardVisible)
        assertEquals(genB0, deckB.interactionGeneration)

        // Cancelling eligibility (e.g., dialog hidden) immediately clears reveal/peek state
        deckA.cancelAllVisualPeeks()
        assertFalse(deckA.isRearCardVisible)

        // Section 9.8 & 13.2 Command Send/Cancel visibility matrix
        assertTrue(com.precisioncad.studio.ui.components.CadDeckIdleTimerState.isCommandDeckVisible(keyboardVisible = true, commandText = ""))
        assertTrue(com.precisioncad.studio.ui.components.CadDeckIdleTimerState.isCommandDeckVisible(keyboardVisible = true, commandText = "LINE"))
        assertTrue(com.precisioncad.studio.ui.components.CadDeckIdleTimerState.isCommandDeckVisible(keyboardVisible = false, commandText = "LINE"))
        assertFalse(com.precisioncad.studio.ui.components.CadDeckIdleTimerState.isCommandDeckVisible(keyboardVisible = false, commandText = ""))

        // Section 9.3 & 12.1 Binary toggle label normalization
        assertEquals("ENABLE", com.precisioncad.studio.ui.components.CadDeckIdleTimerState.normalizeBinaryStateLabel(true, "ON"))
        assertEquals("DISABLE", com.precisioncad.studio.ui.components.CadDeckIdleTimerState.normalizeBinaryStateLabel(false, "OFF"))
        assertEquals("ENABLE", com.precisioncad.studio.ui.components.CadDeckIdleTimerState.normalizeBinaryStateLabel(true, "POLAR"))
        assertEquals("DISABLE", com.precisioncad.studio.ui.components.CadDeckIdleTimerState.normalizeBinaryStateLabel(false, "PSNAP"))
    }

    @Test
    fun `TC-V18-05 verify GitHub Release Parser, HomeUpdateIconVisualState badge rules, and UpdatesBehindCount`() {
        // 1. Home update icon visual rules (Section 14.5 & 16.9):
        //    0 -> White, no badge; 1 -> Gold, no badge; >1 -> Gold, integer count beside icon
        val icon0 = com.precisioncad.studio.cad.update.HomeUpdateIconVisualState.fromUpdatesBehindCount(0)
        assertEquals(com.precisioncad.studio.cad.update.HomeUpdateIconColor.WHITE, icon0.iconColor)
        assertFalse(icon0.showBadgeCount)
        assertEquals(null, icon0.badgeCountText)

        val icon1 = com.precisioncad.studio.cad.update.HomeUpdateIconVisualState.fromUpdatesBehindCount(1)
        assertEquals(com.precisioncad.studio.cad.update.HomeUpdateIconColor.GOLD, icon1.iconColor)
        assertFalse(icon1.showBadgeCount)
        assertEquals(null, icon1.badgeCountText)

        val icon3 = com.precisioncad.studio.cad.update.HomeUpdateIconVisualState.fromUpdatesBehindCount(3)
        assertEquals(com.precisioncad.studio.cad.update.HomeUpdateIconColor.GOLD, icon3.iconColor)
        assertTrue(icon3.showBadgeCount)
        assertEquals("3", icon3.badgeCountText)

        // 2. Parse GitHub Releases JSON containing installed (b18), two newer stable (b19, b20),
        //    a prerelease (b21), a draft (b22), and a wrong-package release
        val sampleReleasesJson = """
            [
              {
                "id": 105,
                "tag_name": "v1.22.0-b22",
                "name": "Draft Release",
                "draft": true,
                "prerelease": false,
                "published_at": "2026-11-05T10:00:00Z",
                "body": "## New\n- Draft item",
                "assets": []
              },
              {
                "id": 104,
                "tag_name": "v1.21.0-rc1-b21",
                "name": "PrecisionCAD Studio v1.21.0-rc1",
                "draft": false,
                "prerelease": true,
                "published_at": "2026-11-04T10:00:00Z",
                "body": "## New\n- Preview feature\n<!-- PRECISIONCAD-RELEASE-METADATA\npackageId: com.precisioncad.studio\nversionName: 1.21.0-rc1\nversionCode: 21\nassetName: PrecisionCAD-Studio-v1.21.0-rc1-b21-release.apk\nsha256: aaaa\nchannel: prerelease\n-->",
                "assets": [
                  {
                    "name": "PrecisionCAD-Studio-v1.21.0-rc1-b21-release.apk",
                    "browser_download_url": "https://github.com/AnimeXplayXD/PrecisionCAD-Studio/releases/download/v1.21.0-rc1-b21/PrecisionCAD-Studio-v1.21.0-rc1-b21-release.apk",
                    "size": 18400000,
                    "content_type": "application/vnd.android.package-archive"
                  }
                ]
              },
              {
                "id": 103,
                "tag_name": "v1.20.0-b20",
                "name": "PrecisionCAD Studio v1.20.0",
                "draft": false,
                "prerelease": false,
                "published_at": "2026-11-03T12:00:00Z",
                "body": "## New\n- Zoom-adaptive Grid Snap\n- GitHub Release Catalogue\n\n## Fixes\n- Paper Space title-block opacity\n\n## Optimisations\n- Section-local card timers\n\n---\n\n<!-- PRECISIONCAD-RELEASE-METADATA\npackageId: com.precisioncad.studio\nversionName: 1.20.0\nversionCode: 20\nassetName: PrecisionCAD-Studio-v1.20.0-b20-release.apk\ncommitSha: 0123456789abcdef\nsha256: e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855\nchannel: stable\n-->",
                "assets": [
                  {
                    "name": "PrecisionCAD-Studio-v1.20.0-b20-release.apk",
                    "browser_download_url": "https://github.com/AnimeXplayXD/PrecisionCAD-Studio/releases/download/v1.20.0-b20/PrecisionCAD-Studio-v1.20.0-b20-release.apk",
                    "size": 18250000,
                    "content_type": "application/vnd.android.package-archive"
                  }
                ]
              },
              {
                "id": 102,
                "tag_name": "v1.19.0-b19",
                "name": "PrecisionCAD Studio v1.19.0",
                "draft": false,
                "prerelease": false,
                "published_at": "2026-11-02T12:00:00Z",
                "body": "## Fixes\n- Locale.ROOT formatting\n\n<!-- PRECISIONCAD-RELEASE-METADATA\npackageId: com.precisioncad.studio\nversionName: 1.19.0\nversionCode: 19\nassetName: PrecisionCAD-Studio-v1.19.0-b19-release.apk\nsha256: bbbb\nchannel: stable\n-->",
                "assets": [
                  {
                    "name": "PrecisionCAD-Studio-v1.19.0-b19-release.apk",
                    "browser_download_url": "https://github.com/AnimeXplayXD/PrecisionCAD-Studio/releases/download/v1.19.0-b19/PrecisionCAD-Studio-v1.19.0-b19-release.apk",
                    "size": 18100000,
                    "content_type": "application/vnd.android.package-archive"
                  }
                ]
              },
              {
                "id": 101,
                "tag_name": "v1.18.0-b18",
                "name": "PrecisionCAD Studio v1.18.0",
                "draft": false,
                "prerelease": false,
                "published_at": "2026-11-01T12:00:00Z",
                "body": "## New\n- Baseline release\n\n<!-- PRECISIONCAD-RELEASE-METADATA\npackageId: com.precisioncad.studio\nversionName: 1.18.0\nversionCode: 18\nassetName: PrecisionCAD-Studio-v1.18.0-b18-release.apk\nsha256: cccc\nchannel: stable\n-->",
                "assets": [
                  {
                    "name": "PrecisionCAD-Studio-v1.18.0-b18-release.apk",
                    "browser_download_url": "https://github.com/AnimeXplayXD/PrecisionCAD-Studio/releases/download/v1.18.0-b18/PrecisionCAD-Studio-v1.18.0-b18-release.apk",
                    "size": 18000000,
                    "content_type": "application/vnd.android.package-archive"
                  }
                ]
              }
            ]
        """.trimIndent()

        val catalogueWithPre = com.precisioncad.studio.cad.update.CadReleaseParser.parseReleasesJson(
            jsonArrayString = sampleReleasesJson,
            installedVersionCode = 18,
            includePrereleases = true
        )
        // Draft is excluded; 4 published records remain (21 prerelease, 20 latest stable, 19 intermediate stable, 18 installed)
        assertEquals(4, catalogueWithPre.size)
        val latestStable = catalogueWithPre.first { it.isLatestStable }
        assertEquals(20, latestStable.versionCode)
        assertEquals("1.20.0", latestStable.versionName)
        assertEquals("2026-11-03", latestStable.publishedDateFormatted)
        assertEquals(3, latestStable.sections.size)

        val installedEntry = catalogueWithPre.first { it.isInstalledVersion }
        assertEquals(18, installedEntry.versionCode)

        // Eligible stable updates behind installed (18) are 19 and 20 -> count = 2 (prerelease 21 is excluded)
        val behindCount = com.precisioncad.studio.cad.update.CadReleaseParser.computeUpdatesBehindCount(
            releases = catalogueWithPre,
            installedVersionCode = 18,
            includePrereleases = false
        )
        assertEquals(2, behindCount)
    }

    @Test
    fun `TC-V18_2-01 verify v18_2 Version 1_18_2, Gradle 9_8_2 wrapper, Requirement A concealed-at-rest command deck, and Requirement D licensing inventory rules`() {
        // 1. Requirement A: Command Send/Cancel deck uses postExchangeRevealMs = 0L so rear card stays 100% concealed at rest and after exchange
        val commandDeck = com.precisioncad.studio.ui.components.CadDeckIdleTimerState(
            deckId = "command_send_cancel",
            postExchangeRevealMs = 0L
        )
        assertFalse(commandDeck.isRearCardVisible)
        assertFalse(commandDeck.isRearCardRevealed)
        val gen0 = commandDeck.interactionGeneration
        commandDeck.onOrderExchanged(isCommandPromptOrDeck = true)
        assertFalse(commandDeck.isPostExchangeRevealActive)
        assertFalse(commandDeck.isRearCardVisible)
        assertFalse(commandDeck.isRearCardRevealed)
        assertTrue(commandDeck.interactionGeneration > gen0)

        // When 60s idle timer triggers a 4000ms peek, rear card is revealed only during the peek window
        commandDeck.advanceTimeBy(60_000L)
        assertTrue(commandDeck.isIdlePeeking)
        assertTrue(commandDeck.isRearCardVisible)
        assertTrue(commandDeck.isRearCardRevealed)
        commandDeck.advanceTimeBy(4_000L)
        assertFalse(commandDeck.isIdlePeeking)
        assertFalse(commandDeck.isRearCardVisible)
        assertFalse(commandDeck.isRearCardRevealed)

        // 2. Verify root version.properties (1.18.3, VERSION_CODE>=20) and gradle-wrapper.properties (9.8.2)
        val repoRoot = java.io.File("..").takeIf { java.io.File(it, "version.properties").exists() }
            ?: java.io.File(".")
        val versionPropsFile = java.io.File(repoRoot, "version.properties")
        if (versionPropsFile.exists()) {
            val vProps = java.util.Properties().apply {
                versionPropsFile.inputStream().use { load(it) }
            }
            assertEquals("1", vProps.getProperty("VERSION_MAJOR"))
            assertEquals("18", vProps.getProperty("VERSION_MINOR"))
            assertEquals("3", vProps.getProperty("VERSION_PATCH"))
            val vCode = vProps.getProperty("VERSION_CODE", "0").toInt()
            assertTrue(vCode >= 20)
        }
        val wrapperPropsFile = java.io.File(repoRoot, "gradle/wrapper/gradle-wrapper.properties")
        if (wrapperPropsFile.exists()) {
            val wrapperText = wrapperPropsFile.readText()
            assertTrue(wrapperText.contains("gradle-9.8.2-bin.zip"))
            assertTrue(wrapperText.contains("21bd5da2de49b642904077f38c0efdc7778e92b3ed0889392dca61b97e389607"))
        }

        // 3. Verify V19.3 Apache-2.0 LICENSE, NOTICE, THIRD_PARTY_NOTICES.md, TRADEMARKS.md, and OFL-1.1.txt
        val licenseFile = java.io.File(repoRoot, "LICENSE")
        assertTrue("Root LICENSE file must exist", licenseFile.exists())
        assertTrue(licenseFile.readText().contains("Apache License"))
        val noticeFile = java.io.File(repoRoot, "NOTICE")
        assertTrue("Root NOTICE file must exist", noticeFile.exists())
        val noticeText = noticeFile.readText()
        assertTrue(noticeText.contains("All Rights Reserved"))
        assertTrue(noticeText.contains("precisioncad-studio-app-icon.png"))
        val trademarksFile = java.io.File(repoRoot, "TRADEMARKS.md")
        assertTrue(trademarksFile.exists())
        val trademarksText = trademarksFile.readText()
        assertTrue(trademarksText.contains("All Rights Reserved"))
        assertTrue(trademarksText.contains("precisioncad-studio-app-icon.png"))
        assertTrue(java.io.File(repoRoot, "THIRD_PARTY_NOTICES.md").exists())
        assertTrue(java.io.File(repoRoot, "third_party_licenses/OFL-1.1.txt").exists())
        assertFalse(java.io.File(repoRoot, ".github/workflows/licensing-inventory.yml").exists())
        assertFalse(java.io.File(repoRoot, "scripts/generate_licensing_inventory.py").exists())
    }

    @Test
    fun `TC-V18_3-01 verify V18_3 apksigner SHA-256 certificate parser and single timer driver per CadDeckIdleTimerState`() {
        val repoRoot = java.io.File("..").takeIf { java.io.File(it, "version.properties").exists() }
            ?: java.io.File(".")

        // 1. Requirement A (§2.4): Verify scripts/verify_apksigner_certificate.py self-tests and exact user-supplied V2 Signer fixture
        val verifierScript = java.io.File(repoRoot, "scripts/verify_apksigner_certificate.py")
        assertTrue("verify_apksigner_certificate.py must exist", verifierScript.exists())

        val selfTestProc = ProcessBuilder("python3", verifierScript.absolutePath, "--self-test")
            .redirectErrorStream(true)
            .start()
        val selfTestOutput = selfTestProc.inputStream.bufferedReader().readText()
        val selfTestExit = selfTestProc.waitFor()
        assertEquals("Self-test failed: $selfTestOutput", 0, selfTestExit)
        assertTrue(selfTestOutput.contains("SELF_TEST_OK"))

        val exactUserSignerOutput = """
            Verified using v1 scheme (JAR signing): false
            Verified using v2 scheme (APK Signature Scheme v2): true
            Verified using v3 scheme (APK Signature Scheme v3): false
            Verified using v3.1 scheme (APK Signature Scheme v3.1): false
            Verified using v3.2 scheme (APK Signature Scheme v3.2): false
            Verified using v4 scheme (APK Signature Scheme v4): false
            Verified for SourceStamp: false
            Number of signers: 1
            V2 Signer: certificate SHA-256 digest: 7bbcda1924e95ca1758c7e0ac763b56ae9f7eafef4c6e7362bf2d63c63531e59
        """.trimIndent()
        val trustedHex = "7BBCDA1924E95CA1758C7E0AC763B56AE9F7EAFEF4C6E7362BF2D63C63531E59"

        val parseProc = ProcessBuilder(
            "python3",
            verifierScript.absolutePath,
            "--expected",
            trustedHex,
            "--quiet-digest-only"
        ).start()
        parseProc.outputStream.bufferedWriter().use { it.write(exactUserSignerOutput) }
        val parsedDigest = parseProc.inputStream.bufferedReader().readText().trim()
        val parseExit = parseProc.waitFor()
        assertEquals(0, parseExit)
        assertEquals(
            "7bbcda1924e95ca1758c7e0ac763b56ae9f7eafef4c6e7362bf2d63c63531e59",
            parsedDigest
        )
        assertFalse(parsedDigest.contains("certificatesha-256digest"))

        // Verify workflow uses verify_apksigner_certificate.py and no longer uses awk -F':' '{print $2}' on apksigner output
        val buildWorkflow = java.io.File(repoRoot, ".github/workflows/build-signed-apk.yml")
        if (buildWorkflow.exists()) {
            val buildWfText = buildWorkflow.readText()
            assertTrue(buildWfText.contains("scripts/verify_apksigner_certificate.py"))
            assertFalse(buildWfText.contains("grep -i \"certificate SHA-256 digest:\" | head -n 1 | awk -F':' '{print $2}'"))
        }

        // 2. Requirement B (§3.2 & §3.6): Verify single lifecycle observer and single timer driver ownership per CadDeckIdleTimerState
        val deckA = com.precisioncad.studio.ui.components.CadDeckIdleTimerState(deckId = "deck_a")
        val deckB = com.precisioncad.studio.ui.components.CadDeckIdleTimerState(deckId = "deck_b")

        assertEquals(0, deckA.activeLifecycleObserverCount)
        assertEquals(0, deckA.activeTimerDriverCount)
        assertTrue(deckA.attachLifecycleObserver())
        assertEquals(1, deckA.activeLifecycleObserverCount)
        // Second observer attempt on the same state must be rejected
        assertFalse(deckA.attachLifecycleObserver())
        assertEquals(1, deckA.activeLifecycleObserverCount)

        assertTrue(deckA.attachTimerDriver())
        assertEquals(1, deckA.activeTimerDriverCount)
        // Second timer driver attempt on the same state must be rejected
        assertFalse(deckA.attachTimerDriver())
        assertEquals(1, deckA.activeTimerDriverCount)

        deckA.detachTimerDriver()
        assertEquals(0, deckA.activeTimerDriverCount)
        deckA.detachLifecycleObserver()
        assertEquals(0, deckA.activeLifecycleObserverCount)

        // Verify 59,999 ms does not reveal rear card; +1 ms starts peek; +4,000 ms conceals it; deckB clock is independent
        deckA.advanceTimeBy(59_999L)
        assertFalse(deckA.isIdlePeeking)
        assertFalse(deckA.isRearCardRevealed)
        assertEquals(0L, deckB.elapsedIdleMs)

        deckA.advanceTimeBy(1L)
        assertTrue(deckA.isIdlePeeking)
        assertTrue(deckA.isRearCardRevealed)
        assertFalse(deckB.isIdlePeeking)

        // Direct interaction during active peek terminates peek immediately and resets elapsedIdleMs
        deckA.onDirectInteraction()
        assertFalse(deckA.isIdlePeeking)
        assertEquals(0L, deckA.elapsedIdleMs)

        // Verify BindCadDeckIdleTimerEffect has been completely removed from source files
        val binaryCardSource = java.io.File(
            repoRoot,
            "app/src/main/java/com/example/ui/components/CadBinaryToggleCard.kt"
        )
        val workspaceSource = java.io.File(
            repoRoot,
            "app/src/main/java/com/example/ui/CadWorkspaceScreen.kt"
        )
        val releaseDialogSource = java.io.File(
            repoRoot,
            "app/src/main/java/com/example/ui/components/CadReleaseCatalogueDialog.kt"
        )
        if (binaryCardSource.exists()) {
            assertFalse(binaryCardSource.readText().contains("fun BindCadDeckIdleTimerEffect"))
        }
        if (workspaceSource.exists()) {
            assertFalse(workspaceSource.readText().contains("BindCadDeckIdleTimerEffect"))
        }
        if (releaseDialogSource.exists()) {
            assertFalse(releaseDialogSource.readText().contains("BindCadDeckIdleTimerEffect"))
        }
    }

    @Test
    fun `TC-V19-01 verify V19 Card Shape Peek Geometry, 60s_4s_4s Deck State Machine, Step-Phase Touchpad Release Alignment, PolarSnap Ray Separation, and Shape-Aware QDIM`() {
        // 1. V19 §5 & §6: CardShapeClass classification, peek direction, 60s/4s/4s state machine, and 3-card depth visibility
        assertEquals(
            com.precisioncad.studio.ui.components.CardShapeClass.SQUARE,
            com.precisioncad.studio.ui.components.CadDeckIdleTimerState.classifyCardShape(40f, 40f)
        )
        assertEquals(
            com.precisioncad.studio.ui.components.CardPeekDirection.SIDE,
            com.precisioncad.studio.ui.components.CadDeckIdleTimerState.peekDirectionForShape(40f, 40f)
        )
        assertEquals(
            com.precisioncad.studio.ui.components.CardShapeClass.RECTANGULAR,
            com.precisioncad.studio.ui.components.CadDeckIdleTimerState.classifyCardShape(180f, 48f)
        )
        assertEquals(
            com.precisioncad.studio.ui.components.CardPeekDirection.ABOVE,
            com.precisioncad.studio.ui.components.CadDeckIdleTimerState.peekDirectionForShape(180f, 48f)
        )

        val exportDeck = com.precisioncad.studio.ui.components.CadDeckIdleTimerState(deckId = "project_hub_export_card_deck")
        assertEquals(com.precisioncad.studio.ui.components.CardDeckPhase.IDLE_WAIT, exportDeck.phase)
        assertFalse(exportDeck.isRearCardVisibleForDepth(1))
        assertFalse(exportDeck.isRearCardVisibleForDepth(2))

        // Card exchange -> 4,000ms post-exchange peek where depth==1 is visible and depth==2 remains occluded
        exportDeck.onCardExchanged()
        assertEquals(com.precisioncad.studio.ui.components.CardDeckPhase.POST_EXCHANGE_PEEK, exportDeck.phase)
        assertTrue(exportDeck.isRearCardVisibleForDepth(1))
        assertFalse(exportDeck.isRearCardVisibleForDepth(2))
        assertTrue(exportDeck.sequentialConcealDelayMsForDepth(1, 3) > exportDeck.sequentialConcealDelayMsForDepth(2, 3))

        exportDeck.advanceTimeBy(3_999L)
        assertTrue(exportDeck.isPostExchangeRevealActive)
        exportDeck.advanceTimeBy(1L)
        assertFalse(exportDeck.isPostExchangeRevealActive)
        assertEquals(com.precisioncad.studio.ui.components.CardDeckPhase.IDLE_WAIT, exportDeck.phase)

        // 2. V19 §11.3 & §11.4: Step-phase quantization on touchpad release
        val phaseX = CadSnapAndCoordEngine.captureStepPhase(12.34, 5.0)
        assertEquals(2.34, phaseX, 1e-6)
        // Dragging from 12.34 to 26.9 -> nearest phase-aligned coordinate is 2.34 + 5 * 5 = 27.34
        val alignedX = CadSnapAndCoordEngine.alignCoordinateToStepPhase(26.9, phaseX, 5.0)
        assertEquals(27.34, alignedX, 1e-6)

        // 3. V19 §13: Blue dashed PolarSnap ray visibility vs yellow Visual Polar Tracking arc
        val basePt = Point2D(0.0, 0.0)
        val snapCfg = com.precisioncad.studio.cad.model.GridAndSnapConfig(
            polarTrackingEnabled = true,
            polarSnapEnabled = true,
            polarSnapPresetAnglesDeg = setOf(45.0, 90.0),
            polarAcquisitionToleranceDeg = 5.0
        )
        // At 37.0 deg: Visual Polar Tracking is acquired (whole-number bearing 37°), but PolarSnap is NOT acquired -> blue ray hidden
        val prevAt37 = CadSnapAndCoordEngine.evaluatePolarTracking(
            basePoint = basePt,
            rawCursorPoint = basePt.polarOffset(50.0, 37.0),
            snapConfig = snapCfg
        )
        assertNotNull(prevAt37)
        assertTrue(prevAt37!!.isPolarGuideAcquired)
        assertEquals(37.0, prevAt37.activePolarAngleDeg!!, 1e-3)
        assertFalse(prevAt37.isPolarSnapRayVisibleForDisplay)

        // At 45.0 deg (after PolarSnap projection onto 45° ray): PolarSnap is acquired AND cursor lies on 45° within 0.01° -> blue ray visible
        val prevAt45 = CadSnapAndCoordEngine.evaluatePolarTracking(
            basePoint = basePt,
            rawCursorPoint = basePt.polarOffset(50.0, 45.0),
            snapConfig = snapCfg
        )
        assertNotNull(prevAt45)
        assertEquals(45.0, prevAt45!!.activePolarSnapAngleDeg!!, 1e-3)
        assertTrue(prevAt45.isPolarSnapRayVisibleForDisplay)

        // At 43.5 deg raw (within 5° PolarSnap acquisition corridor, but cursor itself is at 43.5°, not on 45.0° within 0.01°): blue ray hidden until projected
        val prevAt43_5 = CadSnapAndCoordEngine.evaluatePolarTracking(
            basePoint = basePt,
            rawCursorPoint = basePt.polarOffset(50.0, 43.5),
            snapConfig = snapCfg
        )
        assertNotNull(prevAt43_5)
        assertEquals(45.0, prevAt43_5!!.activePolarSnapAngleDeg!!, 1e-3)
        assertFalse(prevAt43_5.isPolarSnapRayVisibleForDisplay)

        // 4. V19 §14.3.1: Standalone Complete Circle (R = 181.10, Ø = 362.20)
        //    - Sparse & Balanced: 1 Diameter (Ø362.20), 0 Radius, 0 Linear Horizontal/Vertical (no bounding box or half-radius 90.55!)
        //    - Detailed: 1 Diameter (Ø362.20 at 38°) + 1 Radius (R181.10 at 142°), 0 Linear Horizontal/Vertical
        val standaloneCircle = CadEntity.CircleEntity(
            id = "standalone_circle",
            center = Point2D(200.0, 200.0),
            radius = 181.10
        )
        for (policy in listOf(QDimDensityPolicy.SPARSE, QDimDensityPolicy.BALANCED)) {
            val (circleEntities, circleReport) = AutoDimensionEngine.generateQuickDimensionsWithAlignmentReport(
                allEntities = listOf(standaloneCircle),
                targetEntities = listOf(standaloneCircle),
                density = policy
            )
            assertEquals(
                com.precisioncad.studio.cad.dimension.QDimShapeFamily.STANDALONE_CIRCLE,
                circleReport.geometryClassification?.shapeFamily
            )
            val cDims = circleEntities.filterIsInstance<CadEntity.DimensionEntity>()
            assertEquals("Expected exactly 1 Diameter dimension for standalone circle in $policy", 1, cDims.size)
            assertEquals(DimensionType.DIAMETER, cDims.single().dimensionType)
            assertEquals(362.20, cDims.single().measuredModelValue(), 1e-2)
            assertTrue(cDims.none { it.dimensionType == DimensionType.LINEAR_HORIZONTAL || it.dimensionType == DimensionType.LINEAR_VERTICAL })
        }

        val (detailedCircleEntities, detailedCircleReport) = AutoDimensionEngine.generateQuickDimensionsWithAlignmentReport(
            allEntities = listOf(standaloneCircle),
            targetEntities = listOf(standaloneCircle),
            density = QDimDensityPolicy.DETAILED
        )
        assertEquals(
            com.precisioncad.studio.cad.dimension.QDimShapeFamily.STANDALONE_CIRCLE,
            detailedCircleReport.geometryClassification?.shapeFamily
        )
        val detailedCircleDims = detailedCircleEntities.filterIsInstance<CadEntity.DimensionEntity>()
        assertEquals(2, detailedCircleDims.size)
        assertEquals(1, detailedCircleDims.count { it.dimensionType == DimensionType.DIAMETER })
        assertEquals(1, detailedCircleDims.count { it.dimensionType == DimensionType.RADIUS })
        assertTrue(detailedCircleDims.none { it.dimensionType == DimensionType.LINEAR_HORIZONTAL || it.dimensionType == DimensionType.LINEAR_VERTICAL })

        // 5. V19 §14.3.2: Concentric Circles -> Diameter callouts only, zero linear bounding-box or half-span dimensions
        val concentricCircles = listOf(
            CadEntity.CircleEntity(id = "cc1", center = Point2D(100.0, 100.0), radius = 20.0),
            CadEntity.CircleEntity(id = "cc2", center = Point2D(100.0, 100.0), radius = 45.0)
        )
        val (concentricOut, concentricReport) = AutoDimensionEngine.generateQuickDimensionsWithAlignmentReport(
            allEntities = concentricCircles,
            targetEntities = concentricCircles,
            density = QDimDensityPolicy.BALANCED
        )
        assertEquals(
            com.precisioncad.studio.cad.dimension.QDimShapeFamily.CONCENTRIC_CIRCLES,
            concentricReport.geometryClassification?.shapeFamily
        )
        val concentricDims = concentricOut.filterIsInstance<CadEntity.DimensionEntity>()
        assertEquals(2, concentricDims.size)
        assertTrue(concentricDims.all { it.dimensionType == DimensionType.DIAMETER })

        // 6. V19 §14.3.3: Standalone Partial Arc -> Radius in Sparse; Radius + Included Angle in Balanced/Detailed; zero linear bounding box
        val partialArc = CadEntity.ArcEntity(
            id = "arc_1",
            center = Point2D(100.0, 100.0),
            radius = 50.0,
            startAngleDeg = 0.0,
            sweepAngleDeg = 120.0
        )
        val (sparseArcOut, sparseArcReport) = AutoDimensionEngine.generateQuickDimensionsWithAlignmentReport(
            allEntities = listOf(partialArc),
            targetEntities = listOf(partialArc),
            density = QDimDensityPolicy.SPARSE
        )
        assertEquals(
            com.precisioncad.studio.cad.dimension.QDimShapeFamily.PARTIAL_ARC,
            sparseArcReport.geometryClassification?.shapeFamily
        )
        val sparseArcDims = sparseArcOut.filterIsInstance<CadEntity.DimensionEntity>()
        assertEquals(1, sparseArcDims.size)
        assertEquals(DimensionType.RADIUS, sparseArcDims.single().dimensionType)

        val (balancedArcOut, _) = AutoDimensionEngine.generateQuickDimensionsWithAlignmentReport(
            allEntities = listOf(partialArc),
            targetEntities = listOf(partialArc),
            density = QDimDensityPolicy.BALANCED
        )
        val balancedArcDims = balancedArcOut.filterIsInstance<CadEntity.DimensionEntity>()
        assertEquals(2, balancedArcDims.size)
        assertEquals(1, balancedArcDims.count { it.dimensionType == DimensionType.RADIUS })
        assertEquals(1, balancedArcDims.count { it.dimensionType == DimensionType.ANGULAR })
        assertTrue(balancedArcDims.none { it.dimensionType == DimensionType.LINEAR_HORIZONTAL || it.dimensionType == DimensionType.LINEAR_VERTICAL })
    }
}


