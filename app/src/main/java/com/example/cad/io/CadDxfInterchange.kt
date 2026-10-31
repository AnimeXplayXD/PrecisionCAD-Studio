package com.example.cad.io

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.cad.dimension.AdaptiveAnnotationRenderer
import com.example.cad.dimension.DimensionStyleConfig
import com.example.cad.kernel.CadGeometryKernel
import com.example.cad.kernel.ViewportTransform
import com.example.cad.model.BlockDefinition
import com.example.cad.model.BoundingBox2D
import com.example.cad.model.CadEntity
import com.example.cad.model.CadLayer
import com.example.cad.model.CadLineType
import com.example.cad.model.CadUnit
import com.example.cad.model.DimensionType
import com.example.cad.model.HatchPattern
import com.example.cad.model.Point2D
import com.example.cad.model.PolylineVertex
import com.example.cad.model.PrecisionFormatConfig
import com.example.cad.model.SheetSetupConfig
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

data class DxfDocumentPayload(
    val storageUnit: CadUnit,
    val displayUnit: CadUnit,
    val layers: List<CadLayer>,
    val entities: List<CadEntity>,
    val blocks: List<BlockDefinition> = emptyList(),
    val sheetSetup: SheetSetupConfig = SheetSetupConfig(),
    val precision: PrecisionFormatConfig = PrecisionFormatConfig()
)

/**
 * Standard ASCII DXF Import/Export + SVG & Plotted Technical Sheet PDF Exporter.
 * Implements standards-compliant AC1015 DXF entity emission and ISO/ASME plotted PDF rendering.
 */
object CadDxfInterchange {

    private fun visibleEntitiesAndBounds(payload: DxfDocumentPayload): Pair<List<CadEntity>, BoundingBox2D> {
        val layerMap = payload.layers.associateBy { it.name }
        val blocksMap = payload.blocks.associateBy { it.name }
        val visibleEntities = payload.entities.filter { ent ->
            val layer = layerMap[ent.layerName]
            layer == null || (layer.isVisible && !layer.isFrozen)
        }
        val sourceList = if (visibleEntities.isNotEmpty()) visibleEntities else payload.entities
        val bounds = if (sourceList.isEmpty()) {
            BoundingBox2D(0.0, 0.0, 200.0, 150.0)
        } else {
            sourceList.map { ent ->
                if (ent is CadEntity.BlockInsertEntity) ent.boundingBox(blocksMap) else ent.boundingBox()
            }.reduce { a, b -> a.union(b) }
        }
        return visibleEntities to bounds
    }

    fun exportToDxf(payload: DxfDocumentPayload): String {
        val sb = StringBuilder(32768)
        val unitRatio = (payload.storageUnit.toMillimetersFactor / payload.displayUnit.toMillimetersFactor)
            .let { if (it.isFinite() && it > 0.0) it else 1.0 }
        fun u(v: Double): String = fmt(v * unitRatio)

        var nextHandleId = 0x10
        fun nextHandle(): String = (nextHandleId++).toString(16).uppercase(Locale.US)

        val (_, bounds) = visibleEntitiesAndBounds(payload)

        // 1. HEADER SECTION
        sb.appendLine("0\nSECTION\n2\nHEADER")
        sb.appendLine("9\n\$ACADVER\n1\nAC1015")
        sb.appendLine("9\n\$HANDSEED\n5\nFFFF")
        sb.appendLine("9\n\$INSUNITS\n70\n${payload.displayUnit.dxfInsUnitsCode}")
        sb.appendLine("9\n\$LUNITS\n70\n2")
        sb.appendLine("9\n\$LUPREC\n70\n${payload.precision.linearDecimals}")
        sb.appendLine("9\n\$EXTMIN\n10\n${u(bounds.minX)}\n20\n${u(bounds.minY)}\n30\n0.0")
        sb.appendLine("9\n\$EXTMAX\n10\n${u(bounds.maxX)}\n20\n${u(bounds.maxY)}\n30\n0.0")
        sb.appendLine("0\nENDSEC")

        // 2. TABLES SECTION (LTYPE, LAYER, STYLE, DIMSTYLE) with AC1015 handles & subclass markers
        sb.appendLine("0\nSECTION\n2\nTABLES")
        val exportedLineTypes = CadLineType.entries.filter { it != CadLineType.BY_LAYER }
        sb.appendLine("0\nTABLE\n2\nLTYPE\n5\n${nextHandle()}\n100\nAcDbSymbolTable\n70\n${exportedLineTypes.size}")
        for (lt in exportedLineTypes) {
            sb.appendLine("0\nLTYPE\n5\n${nextHandle()}\n100\nAcDbSymbolTableRecord\n100\nAcDbLinetypeTableRecord")
            sb.appendLine("2\n${lt.dxfName}\n70\n0\n3\n${lt.label}\n72\n65")
            appendLinetypePatternDxf(sb, lt)
        }
        sb.appendLine("0\nENDTAB")

        sb.appendLine("0\nTABLE\n2\nLAYER\n5\n${nextHandle()}\n100\nAcDbSymbolTable\n70\n${payload.layers.size}")
        for (layer in payload.layers) {
            val flags = (if (layer.isFrozen) 1 else 0) or (if (layer.isLocked) 4 else 0)
            val aci = colorHexToAci(layer.colorHex).let { if (!layer.isVisible) -it else it }
            sb.appendLine("0\nLAYER\n5\n${nextHandle()}\n100\nAcDbSymbolTableRecord\n100\nAcDbLayerTableRecord")
            sb.appendLine("2\n${layer.name}\n70\n$flags\n62\n$aci\n6\n${layer.lineType.dxfName}")
            sb.appendLine("370\n${(layer.lineWeightMm * 100).toInt()}")
            sb.appendLine("420\n${(layer.colorHex and 0xFFFFFF)}")
        }
        sb.appendLine("0\nENDTAB")

        // Standard STYLE table for TEXT, MTEXT, and DIMENSION entities
        sb.appendLine("0\nTABLE\n2\nSTYLE\n5\n${nextHandle()}\n100\nAcDbSymbolTable\n70\n1")
        sb.appendLine("0\nSTYLE\n5\n${nextHandle()}\n100\nAcDbSymbolTableRecord\n100\nAcDbTextStyleTableRecord")
        sb.appendLine("2\nSTANDARD\n70\n0\n40\n0.0\n41\n1.0\n50\n0.0\n71\n0\n42\n2.5\n3\ntxt\n4\n")
        sb.appendLine("0\nENDTAB")

        // Standard DIMSTYLE table (uses group 105 for handle in AC1015)
        sb.appendLine("0\nTABLE\n2\nDIMSTYLE\n5\n${nextHandle()}\n100\nAcDbSymbolTable\n70\n1\n100\nAcDbDimStyleTable")
        sb.appendLine(
            "0\nDIMSTYLE\n105\n${nextHandle()}\n100\nAcDbSymbolTableRecord\n100\nAcDbDimStyleTableRecord\n" +
                "2\nSTANDARD\n70\n0\n" +
                "40\n1.0\n" +
                "41\n${u(2.5)}\n" +
                "42\n${u(1.2)}\n" +
                "44\n${u(1.25)}\n" +
                "140\n${u(2.8)}\n" +
                "271\n${payload.precision.linearDecimals}"
        )
        sb.appendLine("0\nENDTAB")
        sb.appendLine("0\nENDSEC")

        // 3. BLOCKS SECTION
        sb.appendLine("0\nSECTION\n2\nBLOCKS")
        for (block in payload.blocks) {
            sb.appendLine("0\nBLOCK\n5\n${nextHandle()}\n100\nAcDbEntity\n8\n0\n100\nAcDbBlockBegin\n2\n${block.name}\n70\n0")
            sb.appendLine("10\n${u(block.basePoint.x)}\n20\n${u(block.basePoint.y)}\n30\n0.0\n3\n${block.name}")
            for (ent in block.entities) {
                appendEntityDxf(sb, ent, payload, ::nextHandle)
            }
            sb.appendLine("0\nENDBLK\n5\n${nextHandle()}\n100\nAcDbEntity\n8\n0\n100\nAcDbBlockEnd")
        }
        sb.appendLine("0\nENDSEC")

        // 4. ENTITIES SECTION
        val layerMap = payload.layers.associateBy { it.name }
        sb.appendLine("0\nSECTION\n2\nENTITIES")
        for (ent in payload.entities) {
            val layer = layerMap[ent.layerName]
            if (layer != null && (!layer.isVisible || layer.isFrozen)) continue
            appendEntityDxf(sb, ent, payload, ::nextHandle)
        }
        sb.appendLine("0\nENDSEC")
        sb.appendLine("0\nEOF")
        return sb.toString()
    }

    /**
     * Section 28.2 — Populates non-continuous LTYPE records with valid dash counts (73),
     * total pattern lengths (40), and dash/gap segments (49, 74).
     */
    private fun appendLinetypePatternDxf(sb: StringBuilder, lt: CadLineType) {
        when (lt) {
            CadLineType.CONTINUOUS, CadLineType.BY_LAYER -> {
                sb.appendLine("73\n0\n40\n0.0")
            }
            CadLineType.DASHED -> {
                sb.appendLine("73\n2\n40\n9.0\n49\n6.0\n74\n0\n49\n-3.0\n74\n0")
            }
            CadLineType.HIDDEN -> {
                sb.appendLine("73\n2\n40\n6.0\n49\n4.0\n74\n0\n49\n-2.0\n74\n0")
            }
            CadLineType.CENTER -> {
                sb.appendLine("73\n4\n40\n20.0\n49\n12.0\n74\n0\n49\n-3.0\n74\n0\n49\n2.0\n74\n0\n49\n-3.0\n74\n0")
            }
            CadLineType.PHANTOM -> {
                sb.appendLine(
                    "73\n6\n40\n25.0\n49\n12.0\n74\n0\n49\n-3.0\n74\n0\n" +
                        "49\n2.0\n74\n0\n49\n-3.0\n74\n0\n49\n2.0\n74\n0\n49\n-3.0\n74\n0"
                )
            }
            CadLineType.DOTTED -> {
                sb.appendLine("73\n2\n40\n3.0\n49\n0.0\n74\n0\n49\n-3.0\n74\n0")
            }
        }
    }

    private fun appendEntityDxf(
        sb: StringBuilder,
        ent: CadEntity,
        payload: DxfDocumentPayload,
        nextHandle: () -> String
    ) {
        val unitRatio = (payload.storageUnit.toMillimetersFactor / payload.displayUnit.toMillimetersFactor)
            .let { if (it.isFinite() && it > 0.0) it else 1.0 }
        fun u(v: Double): String = fmt(v * unitRatio)

        when (ent) {
            is CadEntity.LineEntity -> {
                if (!ent.start.x.isFinite() || !ent.start.y.isFinite() || !ent.end.x.isFinite() || !ent.end.y.isFinite()) return
                sb.appendLine("0\nLINE\n5\n${nextHandle()}\n100\nAcDbEntity\n8\n${ent.layerName}")
                appendCommonEntityProps(sb, ent)
                sb.appendLine("100\nAcDbLine")
                sb.appendLine("10\n${u(ent.start.x)}\n20\n${u(ent.start.y)}\n30\n0.0")
                sb.appendLine("11\n${u(ent.end.x)}\n21\n${u(ent.end.y)}\n31\n0.0")
            }
            is CadEntity.PolylineEntity -> {
                if (ent.vertices.size < 2) return
                sb.appendLine("0\nLWPOLYLINE\n5\n${nextHandle()}\n100\nAcDbEntity\n8\n${ent.layerName}")
                appendCommonEntityProps(sb, ent)
                sb.appendLine("100\nAcDbPolyline")
                sb.appendLine("90\n${ent.vertices.size}")
                sb.appendLine("70\n${if (ent.isClosed) 1 else 0}")
                sb.appendLine("43\n${u(ent.globalWidth)}")
                for (v in ent.vertices) {
                    sb.appendLine("10\n${u(v.position.x)}\n20\n${u(v.position.y)}\n42\n${fmt(v.bulge)}")
                }
            }
            is CadEntity.CircleEntity -> {
                if (ent.radius <= 0.0 || !ent.radius.isFinite()) return
                sb.appendLine("0\nCIRCLE\n5\n${nextHandle()}\n100\nAcDbEntity\n8\n${ent.layerName}")
                appendCommonEntityProps(sb, ent)
                sb.appendLine("100\nAcDbCircle")
                sb.appendLine("10\n${u(ent.center.x)}\n20\n${u(ent.center.y)}\n30\n0.0")
                sb.appendLine("40\n${u(ent.radius)}")
            }
            is CadEntity.ArcEntity -> {
                if (ent.radius <= 0.0 || !ent.radius.isFinite()) return
                sb.appendLine("0\nARC\n5\n${nextHandle()}\n100\nAcDbEntity\n8\n${ent.layerName}")
                appendCommonEntityProps(sb, ent)
                val startAng = if (ent.sweepAngleDeg >= 0) ent.startAngleDeg else ent.endAngleDeg
                val endAng = if (ent.sweepAngleDeg >= 0) ent.endAngleDeg else ent.startAngleDeg
                sb.appendLine("100\nAcDbCircle")
                sb.appendLine("10\n${u(ent.center.x)}\n20\n${u(ent.center.y)}\n30\n0.0")
                sb.appendLine("40\n${u(ent.radius)}")
                sb.appendLine("100\nAcDbArc")
                sb.appendLine("50\n${fmt(startAng)}\n51\n${fmt(endAng)}")
            }
            is CadEntity.EllipseEntity -> {
                sb.appendLine("0\nELLIPSE\n5\n${nextHandle()}\n100\nAcDbEntity\n8\n${ent.layerName}")
                appendCommonEntityProps(sb, ent)
                sb.appendLine("100\nAcDbEllipse")
                val rad = Math.toRadians(ent.rotationAngleDeg)
                val majX = ent.majorRadius * cos(rad)
                val majY = ent.majorRadius * sin(rad)
                val ratio = (ent.minorRadius / ent.majorRadius.coerceAtLeast(1e-6)).coerceIn(0.01, 1.0)
                sb.appendLine("10\n${u(ent.center.x)}\n20\n${u(ent.center.y)}\n30\n0.0")
                sb.appendLine("11\n${u(majX)}\n21\n${u(majY)}\n31\n0.0")
                sb.appendLine("40\n${fmt(ratio)}\n41\n0.0\n42\n6.283185")
            }
            is CadEntity.SplineEntity -> {
                if (ent.controlPoints.size < 2) return
                sb.appendLine("0\nSPLINE\n5\n${nextHandle()}\n100\nAcDbEntity\n8\n${ent.layerName}")
                appendCommonEntityProps(sb, ent)
                sb.appendLine("100\nAcDbSpline")
                sb.appendLine("70\n${if (ent.isClosed) 1 else 8}\n71\n3\n73\n${ent.controlPoints.size}")
                for (pt in ent.controlPoints) {
                    sb.appendLine("10\n${u(pt.x)}\n20\n${u(pt.y)}\n30\n0.0")
                }
            }
            is CadEntity.PointEntity -> {
                sb.appendLine("0\nPOINT\n5\n${nextHandle()}\n100\nAcDbEntity\n8\n${ent.layerName}")
                appendCommonEntityProps(sb, ent)
                sb.appendLine("100\nAcDbPoint")
                sb.appendLine("10\n${u(ent.position.x)}\n20\n${u(ent.position.y)}\n30\n0.0")
            }
            is CadEntity.RayEntity -> {
                sb.appendLine("0\nRAY\n5\n${nextHandle()}\n100\nAcDbEntity\n8\n${ent.layerName}")
                appendCommonEntityProps(sb, ent)
                sb.appendLine("100\nAcDbRay")
                val rad = Math.toRadians(ent.angleDeg)
                sb.appendLine("10\n${u(ent.basePoint.x)}\n20\n${u(ent.basePoint.y)}\n30\n0.0")
                sb.appendLine("11\n${fmt(cos(rad))}\n21\n${fmt(sin(rad))}\n31\n0.0")
            }
            is CadEntity.XLineEntity -> {
                sb.appendLine("0\nXLINE\n5\n${nextHandle()}\n100\nAcDbEntity\n8\n${ent.layerName}")
                appendCommonEntityProps(sb, ent)
                sb.appendLine("100\nAcDbXline")
                val rad = Math.toRadians(ent.angleDeg)
                sb.appendLine("10\n${u(ent.rootPoint.x)}\n20\n${u(ent.rootPoint.y)}\n30\n0.0")
                sb.appendLine("11\n${fmt(cos(rad))}\n21\n${fmt(sin(rad))}\n31\n0.0")
            }
            is CadEntity.DonutEntity -> {
                val rMid = ((ent.innerRadius + ent.outerRadius) * 0.5).coerceAtLeast(0.05)
                val ringWidth = (ent.outerRadius - ent.innerRadius).coerceAtLeast(0.0)
                sb.appendLine(
                    "999\nPRECISIONCAD_DONUT:${ent.layerName}|${u(ent.center.x)}|${u(ent.center.y)}|${u(ent.innerDiameter)}|${u(ent.outerDiameter)}"
                )
                sb.appendLine("0\nLWPOLYLINE\n5\n${nextHandle()}\n100\nAcDbEntity\n8\n${ent.layerName}")
                appendCommonEntityProps(sb, ent)
                sb.appendLine("100\nAcDbPolyline")
                sb.appendLine("90\n2")
                sb.appendLine("70\n1")
                sb.appendLine("43\n${u(ringWidth)}")
                sb.appendLine("10\n${u(ent.center.x - rMid)}\n20\n${u(ent.center.y)}\n42\n1.0000")
                sb.appendLine("10\n${u(ent.center.x + rMid)}\n20\n${u(ent.center.y)}\n42\n1.0000")
            }
            is CadEntity.HatchEntity -> {
                if (ent.boundaryPoints.size < 3) return
                sb.appendLine("0\nHATCH\n5\n${nextHandle()}\n100\nAcDbEntity\n8\n${ent.layerName}")
                appendCommonEntityProps(sb, ent)
                sb.appendLine("100\nAcDbHatch")
                sb.appendLine("2\n${ent.pattern.dxfName}\n70\n${if (ent.pattern == HatchPattern.SOLID) 1 else 0}")
                sb.appendLine("41\n${fmt(ent.patternScale)}\n52\n${fmt(ent.patternAngleDeg)}")
                sb.appendLine("93\n${ent.boundaryPoints.size}")
                for (pt in ent.boundaryPoints) {
                    sb.appendLine("10\n${u(pt.x)}\n20\n${u(pt.y)}")
                }
            }
            is CadEntity.TextEntity -> {
                sb.appendLine("0\nMTEXT\n5\n${nextHandle()}\n100\nAcDbEntity\n8\n${ent.layerName}")
                appendCommonEntityProps(sb, ent)
                sb.appendLine("100\nAcDbMText")
                sb.appendLine("7\nSTANDARD")
                sb.appendLine("10\n${u(ent.position.x)}\n20\n${u(ent.position.y)}\n30\n0.0")
                sb.appendLine("40\n${u(ent.height)}\n50\n${fmt(ent.rotationDeg)}")
                sb.appendLine("1\n${ent.text.replace("\n", "\\P")}")
            }
            is CadEntity.LeaderEntity -> {
                val safeText = ent.text.replace("\n", "\\P")
                sb.appendLine(
                    "999\nPRECISIONCAD_LEADER:${ent.layerName}|${u(ent.arrowTip.x)}|${u(ent.arrowTip.y)}|" +
                        "${u(ent.shoulderPoint.x)}|${u(ent.shoulderPoint.y)}|" +
                        "${u(ent.landingEnd.x)}|${u(ent.landingEnd.y)}|${u(ent.textHeight)}|$safeText"
                )
                sb.appendLine("0\nLEADER\n5\n${nextHandle()}\n100\nAcDbEntity\n8\n${ent.layerName}")
                appendCommonEntityProps(sb, ent)
                sb.appendLine("100\nAcDbLeader")
                sb.appendLine("3\nSTANDARD")
                sb.appendLine("71\n1\n72\n0\n73\n3\n74\n1\n75\n0\n76\n3")
                sb.appendLine("10\n${u(ent.arrowTip.x)}\n20\n${u(ent.arrowTip.y)}\n30\n0.0")
                sb.appendLine("10\n${u(ent.shoulderPoint.x)}\n20\n${u(ent.shoulderPoint.y)}\n30\n0.0")
                sb.appendLine("10\n${u(ent.landingEnd.x)}\n20\n${u(ent.landingEnd.y)}\n30\n0.0")
                if (safeText.isNotBlank()) {
                    sb.appendLine("0\nMTEXT\n5\n${nextHandle()}\n100\nAcDbEntity\n8\n${ent.layerName}")
                    appendCommonEntityProps(sb, ent)
                    sb.appendLine("100\nAcDbMText")
                    sb.appendLine("7\nSTANDARD")
                    sb.appendLine("10\n${u(ent.landingEnd.x)}\n20\n${u(ent.landingEnd.y + ent.textHeight * 0.35)}\n30\n0.0")
                    sb.appendLine("40\n${u(ent.textHeight)}\n50\n0.0")
                    sb.appendLine("1\n$safeText")
                }
            }
            is CadEntity.CenterMarkEntity -> {
                val ext = (ent.radius * 1.25).coerceAtLeast(1.0)
                sb.appendLine(
                    "999\nPRECISIONCAD_CENTERMARK:${ent.layerName}|${u(ent.center.x)}|${u(ent.center.y)}|${u(ent.radius)}"
                )
                sb.appendLine("0\nLINE\n5\n${nextHandle()}\n100\nAcDbEntity\n8\n${ent.layerName}\n6\nCENTER")
                appendCommonEntityProps(sb, ent)
                sb.appendLine("100\nAcDbLine")
                sb.appendLine("10\n${u(ent.center.x - ext)}\n20\n${u(ent.center.y)}\n30\n0.0")
                sb.appendLine("11\n${u(ent.center.x + ext)}\n21\n${u(ent.center.y)}\n31\n0.0")

                sb.appendLine("0\nLINE\n5\n${nextHandle()}\n100\nAcDbEntity\n8\n${ent.layerName}\n6\nCENTER")
                appendCommonEntityProps(sb, ent)
                sb.appendLine("100\nAcDbLine")
                sb.appendLine("10\n${u(ent.center.x)}\n20\n${u(ent.center.y - ext)}\n30\n0.0")
                sb.appendLine("11\n${u(ent.center.x)}\n21\n${u(ent.center.y + ext)}\n31\n0.0")
            }
            is CadEntity.BlockInsertEntity -> {
                sb.appendLine("0\nINSERT\n5\n${nextHandle()}\n100\nAcDbEntity\n8\n${ent.layerName}")
                appendCommonEntityProps(sb, ent)
                sb.appendLine("100\nAcDbBlockReference\n2\n${ent.blockName}")
                sb.appendLine("10\n${u(ent.insertionPoint.x)}\n20\n${u(ent.insertionPoint.y)}\n30\n0.0")
                sb.appendLine("41\n${fmt(ent.scaleFactor)}\n42\n${fmt(ent.scaleFactor)}\n50\n${fmt(ent.rotationDeg)}")
            }
            is CadEntity.DimensionEntity -> {
                val plan = AdaptiveAnnotationRenderer.computeRenderPlan(
                    dim = ent,
                    precision = payload.precision,
                    storageUnit = payload.storageUnit,
                    displayUnit = payload.displayUnit,
                    style = DimensionStyleConfig(),
                    viewport = ViewportTransform(pixelsPerModelUnit = 3.0f)
                )
                sb.appendLine("999\nPRECISIONCAD_DIM_META:${ent.dimensionType.name}|${ent.customPrefix}")
                sb.appendLine("0\nDIMENSION\n5\n${nextHandle()}\n100\nAcDbEntity\n8\n${ent.layerName}")
                appendCommonEntityProps(sb, ent)
                sb.appendLine("100\nAcDbDimension")
                // Group 10/20/30: Dimension line definition point
                sb.appendLine("10\n${u(ent.dimensionLinePoint.x)}\n20\n${u(ent.dimensionLinePoint.y)}\n30\n0.0")
                // Group 11/21/31: Middle point of dimension text
                sb.appendLine("11\n${u(plan.textPositionModel.x)}\n21\n${u(plan.textPositionModel.y)}\n31\n0.0")

                // Section 28.3: Clear bit 32 when no anonymous dimension block (*D) is referenced
                // 0 = Rotated, horizontal, or vertical; 1 = Aligned; 2 = Angular;
                // 3 = Diameter; 4 = Radius; 6 = Ordinate (+64 X-ordinate bit)
                val dimTypeFlag = when (ent.dimensionType) {
                    DimensionType.LINEAR_HORIZONTAL -> 0
                    DimensionType.LINEAR_VERTICAL -> 0
                    DimensionType.ALIGNED -> 1
                    DimensionType.ANGULAR -> 2
                    DimensionType.DIAMETER -> 3
                    DimensionType.RADIUS -> 4
                    DimensionType.ORDINATE_X -> 70 // 6 + 64
                    DimensionType.ORDINATE_Y -> 6
                }
                sb.appendLine("70\n$dimTypeFlag")
                val label = ent.formattedLabel(payload.precision, payload.storageUnit, payload.displayUnit)
                sb.appendLine("1\n$label")
                sb.appendLine("3\nSTANDARD")

                // Type-specific AC1015 dimension subclass marker and definition points
                when (ent.dimensionType) {
                    DimensionType.RADIUS -> {
                        sb.appendLine("100\nAcDbRadialDimension")
                        sb.appendLine("15\n${u(ent.anchor2.x)}\n25\n${u(ent.anchor2.y)}\n35\n0.0")
                        sb.appendLine("40\n0.0")
                        sb.appendLine("13\n${u(ent.anchor1.x)}\n23\n${u(ent.anchor1.y)}\n33\n0.0")
                        sb.appendLine("14\n${u(ent.anchor2.x)}\n24\n${u(ent.anchor2.y)}\n34\n0.0")
                    }
                    DimensionType.DIAMETER -> {
                        sb.appendLine("100\nAcDbDiametricDimension")
                        sb.appendLine("15\n${u(ent.anchor2.x)}\n25\n${u(ent.anchor2.y)}\n35\n0.0")
                        sb.appendLine("40\n0.0")
                        sb.appendLine("13\n${u(ent.anchor1.x)}\n23\n${u(ent.anchor1.y)}\n33\n0.0")
                        sb.appendLine("14\n${u(ent.anchor2.x)}\n24\n${u(ent.anchor2.y)}\n34\n0.0")
                    }
                    DimensionType.LINEAR_VERTICAL -> {
                        sb.appendLine("100\nAcDbAlignedDimension")
                        sb.appendLine("13\n${u(ent.anchor1.x)}\n23\n${u(ent.anchor1.y)}\n33\n0.0")
                        sb.appendLine("14\n${u(ent.anchor2.x)}\n24\n${u(ent.anchor2.y)}\n34\n0.0")
                        sb.appendLine("50\n90.0000")
                        sb.appendLine("100\nAcDbRotatedDimension")
                    }
                    DimensionType.LINEAR_HORIZONTAL -> {
                        sb.appendLine("100\nAcDbAlignedDimension")
                        sb.appendLine("13\n${u(ent.anchor1.x)}\n23\n${u(ent.anchor1.y)}\n33\n0.0")
                        sb.appendLine("14\n${u(ent.anchor2.x)}\n24\n${u(ent.anchor2.y)}\n34\n0.0")
                        sb.appendLine("50\n0.0000")
                        sb.appendLine("100\nAcDbRotatedDimension")
                    }
                    DimensionType.ANGULAR -> {
                        sb.appendLine("100\nAcDb3PointAngularDimension")
                        sb.appendLine("13\n${u(ent.anchor1.x)}\n23\n${u(ent.anchor1.y)}\n33\n0.0")
                        sb.appendLine("14\n${u(ent.anchor2.x)}\n24\n${u(ent.anchor2.y)}\n34\n0.0")
                    }
                    DimensionType.ORDINATE_X, DimensionType.ORDINATE_Y -> {
                        sb.appendLine("100\nAcDbOrdinateDimension")
                        sb.appendLine("13\n${u(ent.anchor1.x)}\n23\n${u(ent.anchor1.y)}\n33\n0.0")
                        sb.appendLine("14\n${u(ent.anchor2.x)}\n24\n${u(ent.anchor2.y)}\n34\n0.0")
                    }
                    DimensionType.ALIGNED -> {
                        sb.appendLine("100\nAcDbAlignedDimension")
                        sb.appendLine("13\n${u(ent.anchor1.x)}\n23\n${u(ent.anchor1.y)}\n33\n0.0")
                        sb.appendLine("14\n${u(ent.anchor2.x)}\n24\n${u(ent.anchor2.y)}\n34\n0.0")
                    }
                }
            }
        }
    }

    private fun appendCommonEntityProps(sb: StringBuilder, ent: CadEntity) {
        if (ent.lineTypeOverride != CadLineType.BY_LAYER) {
            sb.appendLine("6\n${ent.lineTypeOverride.dxfName}")
        }
        ent.colorHexOverride?.let { hex ->
            sb.appendLine("420\n${hex and 0xFFFFFF}")
        }
        ent.lineWeightMmOverride?.let { lw ->
            sb.appendLine("370\n${(lw * 100).toInt()}")
        }
    }

    /**
     * Parses ASCII DXF content back into a DxfDocumentPayload, preserving layers, units,
     * blocks, geometric entities, and dimensions (both standard AC1015 DXF and PrecisionCAD metadata).
     */
    fun importFromDxf(dxfContent: String): DxfDocumentPayload {
        val rawLines = dxfContent.lines()
        val pairs = mutableListOf<Pair<Int, String>>()
        var i = 0
        while (i + 1 < rawLines.size) {
            val code = rawLines[i].trim().toIntOrNull()
            if (code != null) {
                val rawVal = rawLines[i + 1]
                val cleanVal = if (code == 1 || code == 999) rawVal.trim('\r') else rawVal.trim()
                pairs.add(code to cleanVal)
                i += 2
            } else {
                i++
            }
        }

        var detectedUnit = CadUnit.MM
        val parsedLayers = mutableListOf<CadLayer>()
        val parsedEntities = mutableListOf<CadEntity>()
        val parsedBlocks = mutableListOf<BlockDefinition>()

        var idx = 0
        var inEntitiesSection = false
        var inTablesSection = false
        var inHeaderSection = false
        var inBlocksSection = false
        var pendingMetaComment: String? = null

        while (idx < pairs.size) {
            val (code, value) = pairs[idx]
            if (code == 0 && value == "SECTION" && idx + 1 < pairs.size && pairs[idx + 1].first == 2) {
                val secName = pairs[idx + 1].second.uppercase()
                inHeaderSection = secName == "HEADER"
                inTablesSection = secName == "TABLES"
                inBlocksSection = secName == "BLOCKS"
                inEntitiesSection = secName == "ENTITIES"
                pendingMetaComment = null
                idx += 2
                continue
            }
            if (code == 0 && value == "ENDSEC") {
                inHeaderSection = false
                inTablesSection = false
                inBlocksSection = false
                inEntitiesSection = false
                pendingMetaComment = null
                idx++
                continue
            }

            if (inHeaderSection && code == 9 && value == "\$INSUNITS" && idx + 1 < pairs.size) {
                val unitCode = pairs[idx + 1].second.toIntOrNull() ?: 4
                detectedUnit = CadUnit.fromDxfCode(unitCode)
                idx += 2
                continue
            }

            if (inTablesSection && code == 0 && value == "LAYER") {
                idx++
                var name = "0"
                var flags = 0
                var aci = 7
                var color24: Long? = null
                var ltype = CadLineType.CONTINUOUS
                var lw = 0.25f
                while (idx < pairs.size && pairs[idx].first != 0) {
                    val (c, v) = pairs[idx]
                    when (c) {
                        2 -> name = v
                        70 -> flags = v.toIntOrNull() ?: 0
                        62 -> aci = v.toIntOrNull() ?: 7
                        420 -> color24 = (v.toLongOrNull() ?: 0xF8FAFC) or 0xFF000000L
                        6 -> ltype = CadLineType.entries.firstOrNull { it.dxfName.equals(v, true) } ?: CadLineType.CONTINUOUS
                        370 -> lw = ((v.toIntOrNull() ?: 25) / 100f).coerceIn(0.05f, 2.5f)
                    }
                    idx++
                }
                parsedLayers.add(
                    CadLayer(
                        name = name,
                        colorHex = color24 ?: aciToColorHex(abs(aci)),
                        lineType = ltype,
                        lineWeightMm = lw,
                        isVisible = aci >= 0,
                        isFrozen = (flags and 1) != 0,
                        isLocked = (flags and 4) != 0
                    )
                )
                continue
            }

            if (inBlocksSection && code == 0 && value == "BLOCK") {
                idx++
                var blockName = "BLOCK_1"
                var bx = 0.0
                var by = 0.0
                while (idx < pairs.size && pairs[idx].first != 0) {
                    val (c, v) = pairs[idx]
                    when (c) {
                        2 -> blockName = v
                        10 -> bx = v.toDoubleOrNull() ?: 0.0
                        20 -> by = v.toDoubleOrNull() ?: 0.0
                    }
                    idx++
                }
                val blockEntities = mutableListOf<CadEntity>()
                var blockMeta: String? = null
                while (idx < pairs.size && !(pairs[idx].first == 0 && pairs[idx].second == "ENDBLK")) {
                    if (pairs[idx].first == 999 && pairs[idx].second.startsWith("PRECISIONCAD_")) {
                        blockMeta = pairs[idx].second
                        idx++
                    } else if (pairs[idx].first == 0) {
                        val (ent, nextIdx) = parseEntityRecord(pairs, idx, blockMeta)
                        blockMeta = null
                        if (ent != null) blockEntities.add(ent)
                        idx = nextIdx
                    } else {
                        idx++
                    }
                }
                if (!blockName.startsWith("*")) {
                    parsedBlocks.add(
                        BlockDefinition(
                            name = blockName,
                            basePoint = Point2D(bx, by),
                            entities = blockEntities
                        )
                    )
                }
                continue
            }

            if (inEntitiesSection && code == 999 && value.startsWith("PRECISIONCAD_")) {
                pendingMetaComment = value
                idx++
                continue
            }

            if (inEntitiesSection && code == 0) {
                val (ent, nextIdx) = parseEntityRecord(pairs, idx, pendingMetaComment)
                pendingMetaComment = null
                if (ent != null) {
                    parsedEntities.add(ent)
                }
                idx = nextIdx
                continue
            }

            idx++
        }

        val finalLayers = if (parsedLayers.isEmpty()) CadLayer.defaultLayers() else parsedLayers
        return DxfDocumentPayload(
            storageUnit = detectedUnit,
            displayUnit = detectedUnit,
            layers = finalLayers,
            entities = parsedEntities,
            blocks = parsedBlocks
        )
    }

    private fun skipSingleEntityRecord(pairs: List<Pair<Int, String>>, startIdx: Int): Int {
        var idx = startIdx + 1
        while (idx < pairs.size && pairs[idx].first != 0 && pairs[idx].first != 999) {
            idx++
        }
        return idx
    }

    private fun parseEntityRecord(
        pairs: List<Pair<Int, String>>,
        startIdx: Int,
        metaComment: String? = null
    ): Pair<CadEntity?, Int> {
        // Handle lossless PrecisionCAD round-trip metadata for composite standard DXF entities
        if (metaComment != null) {
            if (metaComment.startsWith("PRECISIONCAD_DONUT:")) {
                val parts = metaComment.removePrefix("PRECISIONCAD_DONUT:").split("|")
                if (parts.size >= 5) {
                    val layer = parts[0]
                    val cx = parts[1].toDoubleOrNull() ?: 0.0
                    val cy = parts[2].toDoubleOrNull() ?: 0.0
                    val inDia = parts[3].toDoubleOrNull() ?: 5.0
                    val outDia = parts[4].toDoubleOrNull() ?: 10.0
                    val nextIdx = skipSingleEntityRecord(pairs, startIdx)
                    return CadEntity.DonutEntity(
                        layerName = layer,
                        center = Point2D(cx, cy),
                        innerDiameter = inDia,
                        outerDiameter = outDia
                    ) to nextIdx
                }
            } else if (metaComment.startsWith("PRECISIONCAD_CENTERMARK:")) {
                val parts = metaComment.removePrefix("PRECISIONCAD_CENTERMARK:").split("|")
                if (parts.size >= 4) {
                    val layer = parts[0]
                    val cx = parts[1].toDoubleOrNull() ?: 0.0
                    val cy = parts[2].toDoubleOrNull() ?: 0.0
                    val rad = parts[3].toDoubleOrNull() ?: 10.0
                    var nextIdx = skipSingleEntityRecord(pairs, startIdx)
                    if (nextIdx < pairs.size && pairs[nextIdx].first == 0 && pairs[nextIdx].second.equals("LINE", true)) {
                        nextIdx = skipSingleEntityRecord(pairs, nextIdx)
                    }
                    return CadEntity.CenterMarkEntity(
                        layerName = layer,
                        center = Point2D(cx, cy),
                        radius = rad
                    ) to nextIdx
                }
            } else if (metaComment.startsWith("PRECISIONCAD_LEADER:")) {
                val parts = metaComment.removePrefix("PRECISIONCAD_LEADER:").split("|", limit = 9)
                if (parts.size >= 9) {
                    val layer = parts[0]
                    val ax = parts[1].toDoubleOrNull() ?: 0.0
                    val ay = parts[2].toDoubleOrNull() ?: 0.0
                    val sx = parts[3].toDoubleOrNull() ?: 10.0
                    val sy = parts[4].toDoubleOrNull() ?: 10.0
                    val lx = parts[5].toDoubleOrNull() ?: 20.0
                    val ly = parts[6].toDoubleOrNull() ?: 10.0
                    val th = parts[7].toDoubleOrNull() ?: 3.5
                    val txt = parts[8].replace("\\P", "\n")
                    var nextIdx = skipSingleEntityRecord(pairs, startIdx)
                    if (txt.isNotBlank() && nextIdx < pairs.size && pairs[nextIdx].first == 0 && pairs[nextIdx].second.equals("MTEXT", true)) {
                        nextIdx = skipSingleEntityRecord(pairs, nextIdx)
                    }
                    return CadEntity.LeaderEntity(
                        layerName = layer,
                        arrowTip = Point2D(ax, ay),
                        shoulderPoint = Point2D(sx, sy),
                        landingEnd = Point2D(lx, ly),
                        text = txt,
                        textHeight = th
                    ) to nextIdx
                }
            }
        }

        val entityType = pairs[startIdx].second.uppercase()
        var idx = startIdx + 1
        var layer = "0"
        var x1 = 0.0
        var y1 = 0.0
        var x2 = 0.0
        var y2 = 0.0
        var x3 = 0.0
        var y3 = 0.0
        var x15: Double? = null
        var y15: Double? = null
        var r = 10.0
        var r2 = 5.0
        var globalWidth = 0.0
        var a1 = 0.0
        var a2 = 90.0
        var flags = 0
        var textVal = ""
        var styleOrPrefixVal = ""
        var nameVal = ""
        val vertices = mutableListOf<PolylineVertex>()
        var pendingVx: Double? = null
        var pendingVy = 0.0
        var pendingBulge = 0.0

        val isMultiVertexEntity = entityType == "LWPOLYLINE" ||
            entityType == "SPLINE" ||
            entityType == "HATCH" ||
            entityType == "LEADER"

        while (idx < pairs.size && pairs[idx].first != 0 && pairs[idx].first != 999) {
            val (c, v) = pairs[idx]
            when (c) {
                8 -> layer = v
                2 -> nameVal = v
                1 -> textVal = v.replace("\\P", "\n")
                3 -> styleOrPrefixVal = v
                70 -> flags = v.toIntOrNull() ?: 0
                10 -> {
                    if (isMultiVertexEntity) {
                        if (pendingVx != null) {
                            vertices.add(PolylineVertex(Point2D(pendingVx, pendingVy), pendingBulge))
                            pendingBulge = 0.0
                        }
                        pendingVx = v.toDoubleOrNull() ?: 0.0
                    } else {
                        x1 = v.toDoubleOrNull() ?: 0.0
                    }
                }
                20 -> {
                    if (isMultiVertexEntity) {
                        pendingVy = v.toDoubleOrNull() ?: 0.0
                    } else {
                        y1 = v.toDoubleOrNull() ?: 0.0
                    }
                }
                42 -> {
                    if (entityType == "LWPOLYLINE") {
                        pendingBulge = v.toDoubleOrNull() ?: 0.0
                    } else {
                        a2 = v.toDoubleOrNull() ?: a2
                    }
                }
                43 -> globalWidth = v.toDoubleOrNull() ?: 0.0
                11 -> if (entityType != "DIMENSION") x2 = v.toDoubleOrNull() ?: 0.0
                21 -> if (entityType != "DIMENSION") y2 = v.toDoubleOrNull() ?: 0.0
                12 -> x3 = v.toDoubleOrNull() ?: x3
                22 -> y3 = v.toDoubleOrNull() ?: y3
                13 -> x2 = v.toDoubleOrNull() ?: x2
                23 -> y2 = v.toDoubleOrNull() ?: y2
                14 -> x3 = v.toDoubleOrNull() ?: 0.0
                24 -> y3 = v.toDoubleOrNull() ?: 0.0
                15 -> x15 = v.toDoubleOrNull()
                25 -> y15 = v.toDoubleOrNull()
                40 -> r = v.toDoubleOrNull() ?: 10.0
                41 -> r2 = v.toDoubleOrNull() ?: 1.0
                50 -> a1 = v.toDoubleOrNull() ?: 0.0
                51 -> a2 = v.toDoubleOrNull() ?: 90.0
            }
            idx++
        }
        if (pendingVx != null) {
            vertices.add(PolylineVertex(Point2D(pendingVx, pendingVy), pendingBulge))
        }

        val entity: CadEntity? = when (entityType) {
            "LINE" -> CadEntity.LineEntity(layerName = layer, start = Point2D(x1, y1), end = Point2D(x2, y2))
            "LWPOLYLINE" -> {
                // Recognize standard 2-vertex closed bulge=1.0 LWPOLYLINE ring as DonutEntity if width > 0
                if ((flags and 1) != 0 && vertices.size == 2 &&
                    abs(abs(vertices[0].bulge) - 1.0) < 1e-3 &&
                    abs(abs(vertices[1].bulge) - 1.0) < 1e-3 &&
                    globalWidth > 0.0
                ) {
                    val p0 = vertices[0].position
                    val p1 = vertices[1].position
                    val center = Point2D((p0.x + p1.x) * 0.5, (p0.y + p1.y) * 0.5)
                    val rMid = p0.distanceTo(p1) * 0.5
                    val inDia = ((rMid - globalWidth * 0.5).coerceAtLeast(0.0)) * 2.0
                    val outDia = (rMid + globalWidth * 0.5) * 2.0
                    CadEntity.DonutEntity(
                        layerName = layer,
                        center = center,
                        innerDiameter = inDia,
                        outerDiameter = outDia
                    )
                } else {
                    CadEntity.PolylineEntity(
                        layerName = layer,
                        vertices = vertices,
                        isClosed = (flags and 1) != 0,
                        globalWidth = globalWidth
                    )
                }
            }
            "CIRCLE" -> CadEntity.CircleEntity(layerName = layer, center = Point2D(x1, y1), radius = r)
            "ARC" -> {
                val sweep = ((a2 - a1) + 360.0) % 360.0
                CadEntity.ArcEntity(
                    layerName = layer,
                    center = Point2D(x1, y1),
                    radius = r,
                    startAngleDeg = a1,
                    sweepAngleDeg = sweep
                )
            }
            "ELLIPSE" -> {
                val majR = hypot(x2, y2).coerceAtLeast(1.0)
                val minR = (majR * r).coerceAtLeast(0.5)
                val rot = Math.toDegrees(atan2(y2, x2))
                CadEntity.EllipseEntity(
                    layerName = layer,
                    center = Point2D(x1, y1),
                    majorRadius = majR,
                    minorRadius = minR,
                    rotationAngleDeg = rot
                )
            }
            "SPLINE" -> CadEntity.SplineEntity(
                layerName = layer,
                controlPoints = vertices.map { it.position },
                isClosed = (flags and 1) != 0
            )
            "POINT" -> CadEntity.PointEntity(layerName = layer, position = Point2D(x1, y1))
            "RAY" -> CadEntity.RayEntity(
                layerName = layer,
                basePoint = Point2D(x1, y1),
                angleDeg = Math.toDegrees(atan2(y2, x2))
            )
            "XLINE" -> CadEntity.XLineEntity(
                layerName = layer,
                rootPoint = Point2D(x1, y1),
                angleDeg = Math.toDegrees(atan2(y2, x2))
            )
            "DONUT" -> CadEntity.DonutEntity(
                layerName = layer,
                center = Point2D(x1, y1),
                innerDiameter = r,
                outerDiameter = r2
            )
            "HATCH" -> CadEntity.HatchEntity(
                layerName = layer,
                boundaryPoints = vertices.map { it.position },
                pattern = HatchPattern.entries.firstOrNull { it.dxfName.equals(nameVal, true) } ?: HatchPattern.ANSI31,
                patternScale = r2.coerceAtLeast(0.1)
            )
            "TEXT", "MTEXT" -> CadEntity.TextEntity(
                layerName = layer,
                position = Point2D(x1, y1),
                text = textVal,
                height = r.coerceAtLeast(1.0),
                rotationDeg = a1
            )
            "MLEADER", "LEADER" -> {
                val tip = vertices.getOrNull(0)?.position ?: Point2D(x1, y1)
                val shoulder = vertices.getOrNull(1)?.position ?: Point2D(x2, y2)
                val landing = vertices.getOrNull(2)?.position ?: if (x3 != 0.0 || y3 != 0.0) Point2D(x3, y3) else Point2D(shoulder.x + 10.0, shoulder.y)
                CadEntity.LeaderEntity(
                    layerName = layer,
                    arrowTip = tip,
                    shoulderPoint = shoulder,
                    landingEnd = landing,
                    text = textVal,
                    textHeight = r.coerceAtLeast(2.0)
                )
            }
            "CENTERMARK" -> CadEntity.CenterMarkEntity(
                layerName = layer,
                center = Point2D(x1, y1),
                radius = r
            )
            "INSERT" -> CadEntity.BlockInsertEntity(
                layerName = layer,
                blockName = nameVal.ifBlank { "BLOCK" },
                insertionPoint = Point2D(x1, y1),
                scaleFactor = r2.coerceAtLeast(0.1),
                rotationDeg = a1
            )
            "DIMENSION" -> {
                var metaDimType: DimensionType? = null
                var customPrefix = ""
                if (metaComment != null && metaComment.startsWith("PRECISIONCAD_DIM_META:")) {
                    val parts = metaComment.removePrefix("PRECISIONCAD_DIM_META:").split("|", limit = 2)
                    metaDimType = DimensionType.entries.firstOrNull { it.name == parts.getOrNull(0) }
                    customPrefix = parts.getOrNull(1) ?: ""
                } else if (styleOrPrefixVal.isNotBlank() && !styleOrPrefixVal.equals("STANDARD", true)) {
                    customPrefix = styleOrPrefixVal
                }

                val baseType = flags and 7
                val dimType = metaDimType ?: when (baseType) {
                    0 -> if (abs(a1 - 90.0) < 1e-2 || abs(a1 - 270.0) < 1e-2) {
                        DimensionType.LINEAR_VERTICAL
                    } else {
                        DimensionType.LINEAR_HORIZONTAL
                    }
                    1 -> DimensionType.ALIGNED
                    2 -> DimensionType.ANGULAR
                    3 -> DimensionType.DIAMETER
                    4 -> DimensionType.RADIUS
                    5 -> DimensionType.RADIUS
                    6 -> if ((flags and 64) != 0) DimensionType.ORDINATE_X else DimensionType.ORDINATE_Y
                    7 -> DimensionType.ORDINATE_Y
                    else -> DimensionType.LINEAR_HORIZONTAL
                }
                val finalAnchor2 = if ((dimType == DimensionType.RADIUS || dimType == DimensionType.DIAMETER) && x15 != null && y15 != null) {
                    Point2D(x15, y15)
                } else {
                    Point2D(x3, y3)
                }
                CadEntity.DimensionEntity(
                    layerName = layer,
                    dimensionType = dimType,
                    anchor1 = Point2D(x2, y2),
                    anchor2 = finalAnchor2,
                    dimensionLinePoint = Point2D(x1, y1),
                    customPrefix = customPrefix
                )
            }
            else -> null
        }

        return entity to idx
    }

    // --- Vector SVG Export ---

    fun exportToSvg(payload: DxfDocumentPayload): String {
        val sheetW = payload.sheetSetup.effectiveWidthMm
        val sheetH = payload.sheetSetup.effectiveHeightMm
        val sb = StringBuilder(12288)
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${fmt(sheetW)} ${fmt(sheetH)}" width="${fmt(sheetW)}mm" height="${fmt(sheetH)}mm">""")
        sb.appendLine("""  <rect width="100%" height="100%" fill="#0E141E" stroke="#334155" stroke-width="0.5"/>""")

        val (visibleEntities, bounds) = visibleEntitiesAndBounds(payload)
        val scale = min(
            (sheetW * 0.78) / bounds.width.coerceAtLeast(10.0),
            (sheetH * 0.74) / bounds.height.coerceAtLeast(10.0)
        )
        val cx = bounds.center.x
        val cy = bounds.center.y
        fun sx(x: Double): String = fmt(sheetW * 0.5 + (x - cx) * scale)
        fun sy(y: Double): String = fmt(sheetH * 0.47 - (y - cy) * scale)

        val layerMap = payload.layers.associateBy { it.name }
        for (ent in visibleEntities) {
            val layer = layerMap[ent.layerName]
            val hex = String.format("#%06X", ((ent.colorHexOverride ?: layer?.colorHex ?: 0xFFF8FAFC) and 0xFFFFFF))
            val lw = fmt((ent.lineWeightMmOverride ?: layer?.lineWeightMm ?: 0.25f).toDouble())
            when (ent) {
                is CadEntity.LineEntity -> sb.appendLine(
                    """  <line x1="${sx(ent.start.x)}" y1="${sy(ent.start.y)}" x2="${sx(ent.end.x)}" y2="${sy(ent.end.y)}" stroke="$hex" stroke-width="$lw"/>"""
                )
                is CadEntity.CircleEntity -> sb.appendLine(
                    """  <circle cx="${sx(ent.center.x)}" cy="${sy(ent.center.y)}" r="${fmt(ent.radius * scale)}" fill="none" stroke="$hex" stroke-width="$lw"/>"""
                )
                is CadEntity.PolylineEntity -> {
                    val primitives = CadGeometryKernel.explodePolylineToPrimitives(ent)
                    for (prim in primitives) {
                        if (prim is CadEntity.LineEntity) {
                            sb.appendLine(
                                """  <line x1="${sx(prim.start.x)}" y1="${sy(prim.start.y)}" x2="${sx(prim.end.x)}" y2="${sy(prim.end.y)}" stroke="$hex" stroke-width="$lw"/>"""
                            )
                        } else if (prim is CadEntity.ArcEntity) {
                            val rSvg = fmt(prim.radius * scale)
                            val largeArc = if (abs(prim.sweepAngleDeg) > 180.0) 1 else 0
                            // SVG Y is inverted relative to CAD Y, so CCW in CAD is sweep-flag 0 in SVG
                            val sweepFlag = if (prim.sweepAngleDeg >= 0.0) 0 else 1
                            sb.appendLine(
                                """  <path d="M ${sx(prim.startPoint.x)} ${sy(prim.startPoint.y)} A $rSvg $rSvg 0 $largeArc $sweepFlag ${sx(prim.endPoint.x)} ${sy(prim.endPoint.y)}" fill="none" stroke="$hex" stroke-width="$lw"/>"""
                            )
                        }
                    }
                }
                is CadEntity.DimensionEntity -> {
                    val plan = AdaptiveAnnotationRenderer.computeRenderPlan(
                        dim = ent,
                        precision = payload.precision,
                        storageUnit = payload.storageUnit,
                        displayUnit = payload.displayUnit,
                        style = DimensionStyleConfig(),
                        viewport = ViewportTransform(pixelsPerModelUnit = scale.toFloat().coerceAtLeast(0.5f))
                    )
                    sb.appendLine(
                        """  <line x1="${sx(plan.ext1StartModel.x)}" y1="${sy(plan.ext1StartModel.y)}" x2="${sx(plan.ext1EndModel.x)}" y2="${sy(plan.ext1EndModel.y)}" stroke="#38BDF8" stroke-width="0.22"/>"""
                    )
                    sb.appendLine(
                        """  <line x1="${sx(plan.ext2StartModel.x)}" y1="${sy(plan.ext2StartModel.y)}" x2="${sx(plan.ext2EndModel.x)}" y2="${sy(plan.ext2EndModel.y)}" stroke="#38BDF8" stroke-width="0.22"/>"""
                    )
                    sb.appendLine(
                        """  <line x1="${sx(plan.dimLineStartModel.x)}" y1="${sy(plan.dimLineStartModel.y)}" x2="${sx(plan.dimLineEndModel.x)}" y2="${sy(plan.dimLineEndModel.y)}" stroke="#38BDF8" stroke-width="0.28"/>"""
                    )
                    sb.appendLine(
                        """  <text x="${sx(plan.textPositionModel.x)}" y="${sy(plan.textPositionModel.y)}" fill="#38BDF8" font-family="monospace" font-size="3.2" text-anchor="middle">${plan.labelText}</text>"""
                    )
                }
                is CadEntity.TextEntity -> {
                    sb.appendLine(
                        """  <text x="${sx(ent.position.x)}" y="${sy(ent.position.y)}" fill="$hex" font-family="monospace" font-size="${fmt(ent.height * scale)}">${ent.text}</text>"""
                    )
                }
                else -> Unit
            }
        }
        sb.appendLine("</svg>")
        return sb.toString()
    }

    // --- Plotted Sheet PDF Export ---

    fun exportToPdfFile(context: Context, fileName: String, payload: DxfDocumentPayload): File {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val outFile = File(exportDir, fileName)

        val pageW = (payload.sheetSetup.effectiveWidthMm * 2.83465).toInt().coerceAtLeast(400)
        val pageH = (payload.sheetSetup.effectiveHeightMm * 2.83465).toInt().coerceAtLeast(300)

        var wroteNativePdf = false
        val pdf = PdfDocument()
        try {
            val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, 1).create()
            val page = pdf.startPage(pageInfo)
            renderPlottedSheetToCanvas(page.canvas, pageW, pageH, payload)
            pdf.finishPage(page)
            FileOutputStream(outFile).use { pdf.writeTo(it) }
            wroteNativePdf = outFile.exists() && outFile.length() > 64L
        } catch (_: Throwable) {
            // JVM / Robolectric environment where nativeCreateDocument() returns 0
            wroteNativePdf = false
        } finally {
            try {
                pdf.close()
            } catch (_: Throwable) {
            }
        }

        if (!wroteNativePdf) {
            val bitmap = Bitmap.createBitmap(pageW, pageH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            renderPlottedSheetToCanvas(canvas, pageW, pageH, payload)
            FileOutputStream(outFile).use { out ->
                val header = "%PDF-1.4\n% PrecisionCAD Studio Plotted Sheet (${pageW}x${pageH})\n"
                out.write(header.toByteArray(Charsets.UTF_8))
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.write("\n%%EOF\n".toByteArray(Charsets.UTF_8))
            }
        }

        return outFile
    }

    fun renderPlottedSheetToCanvas(canvas: Canvas, pageW: Int, pageH: Int, payload: DxfDocumentPayload) {
        // 1. Sheet background & outer border frame
        val bgPaint = Paint().apply {
            color = Color.rgb(12, 18, 28)
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, pageW.toFloat(), pageH.toFloat(), bgPaint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(148, 163, 184)
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        val marginPx = (payload.sheetSetup.marginMm * 2.83465).toFloat()
        canvas.drawRect(marginPx, marginPx, pageW - marginPx, pageH - marginPx, borderPaint)

        // 2. Title Block in bottom-right
        val tbW = 240f
        val tbH = 68f
        if (payload.sheetSetup.showTitleBlock) {
            val tbLeft = pageW - marginPx - tbW
            val tbTop = pageH - marginPx - tbH
            canvas.drawRect(tbLeft, tbTop, pageW - marginPx, pageH - marginPx, borderPaint)
            val tbTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(226, 232, 240)
                textSize = 9.5f
                typeface = Typeface.MONOSPACE
            }
            canvas.drawText("TITLE: ${payload.sheetSetup.titleBlockTitle}", tbLeft + 8f, tbTop + 16f, tbTextPaint)
            canvas.drawText("DWG NO: ${payload.sheetSetup.titleBlockDrawingNo}  ${payload.sheetSetup.titleBlockRevision}", tbLeft + 8f, tbTop + 32f, tbTextPaint)
            canvas.drawText(
                "UNITS: ${payload.displayUnit.symbol}   SCALE: ${payload.sheetSetup.scaleLabel}   STD: ${payload.sheetSetup.draftingStandard.name}",
                tbLeft + 8f,
                tbTop + 48f,
                tbTextPaint
            )
            canvas.drawText("PRECISIONCAD STUDIO 2D TECHNICAL SHEET", tbLeft + 8f, tbTop + 62f, tbTextPaint)
        }

        // 3. Compute safe printable area avoiding title-block overlap and fit visible entities
        val (visibleEntities, bounds) = visibleEntitiesAndBounds(payload)
        val innerPad = 18f
        val safeLeft = marginPx + innerPad
        val safeRight = pageW - marginPx - innerPad
        val safeTop = marginPx + innerPad
        val safeBottom = if (payload.sheetSetup.showTitleBlock) {
            (pageH - marginPx - tbH - innerPad).coerceAtLeast(safeTop + 100f)
        } else {
            pageH - marginPx - innerPad
        }

        val drawW = (safeRight - safeLeft).coerceAtLeast(80f)
        val drawH = (safeBottom - safeTop).coerceAtLeast(80f)
        val spanW = bounds.width.let { if (!it.isFinite() || it < 1e-3) 20.0 else it }.toFloat()
        val spanH = bounds.height.let { if (!it.isFinite() || it < 1e-3) 20.0 else it }.toFloat()
        val scale = min(drawW / spanW, drawH / spanH) * 0.90f

        val cx = bounds.center.x.toFloat()
        val cy = bounds.center.y.toFloat()
        val sheetCenterX = (safeLeft + safeRight) * 0.5f
        val sheetCenterY = (safeTop + safeBottom) * 0.5f

        fun tx(x: Double): Float = sheetCenterX + ((x.toFloat() - cx) * scale)
        fun ty(y: Double): Float = sheetCenterY - ((y.toFloat() - cy) * scale)

        val layerMap = payload.layers.associateBy { it.name }
        val blocksMap = payload.blocks.associateBy { it.name }
        val geoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(56, 189, 248)
            textSize = 8.5f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.MONOSPACE
        }

        fun applyEntityStrokeStyle(ent: CadEntity) {
            val layer = layerMap[ent.layerName]
            val hex = (ent.colorHexOverride ?: layer?.colorHex ?: 0xFFF1F5F9L).toInt() or -0x1000000
            val lwMm = ent.lineWeightMmOverride ?: layer?.lineWeightMm ?: 0.25f
            val ltype = if (ent.lineTypeOverride != CadLineType.BY_LAYER) {
                ent.lineTypeOverride
            } else {
                layer?.lineType ?: CadLineType.CONTINUOUS
            }
            geoPaint.style = Paint.Style.STROKE
            geoPaint.color = hex
            geoPaint.strokeWidth = (lwMm * 2.83465f).coerceIn(0.65f, 4.0f)
            geoPaint.pathEffect = when (ltype) {
                CadLineType.DASHED, CadLineType.HIDDEN -> DashPathEffect(floatArrayOf(8f, 4f), 0f)
                CadLineType.CENTER -> DashPathEffect(floatArrayOf(12f, 4f, 3f, 4f), 0f)
                CadLineType.PHANTOM -> DashPathEffect(floatArrayOf(14f, 4f, 3f, 4f, 3f, 4f), 0f)
                else -> null
            }
        }

        fun drawArrowheadPdf(tipX: Float, tipY: Float, fromX: Float, fromY: Float, sizePx: Float, colorInt: Int) {
            val dx = tipX - fromX
            val dy = tipY - fromY
            val len = hypot(dx.toDouble(), dy.toDouble()).toFloat()
            if (len < 1e-3f) return
            val ux = dx / len
            val uy = dy / len
            val px = -uy
            val py = ux
            val halfW = sizePx * 0.34f
            val baseCx = tipX - ux * sizePx
            val baseCy = tipY - uy * sizePx
            val path = Path().apply {
                moveTo(tipX, tipY)
                lineTo(baseCx + px * halfW, baseCy + py * halfW)
                lineTo(baseCx - px * halfW, baseCy - py * halfW)
                close()
            }
            fillPaint.color = colorInt
            canvas.drawPath(path, fillPaint)
        }

        fun renderPdfEntity(ent: CadEntity, depth: Int = 0) {
            if (depth > 4) return
            applyEntityStrokeStyle(ent)

            when (ent) {
                is CadEntity.LineEntity -> {
                    canvas.drawLine(tx(ent.start.x), ty(ent.start.y), tx(ent.end.x), ty(ent.end.y), geoPaint)
                }
                is CadEntity.CircleEntity -> {
                    canvas.drawCircle(tx(ent.center.x), ty(ent.center.y), (ent.radius * scale).toFloat(), geoPaint)
                }
                is CadEntity.ArcEntity -> {
                    val rPx = (ent.radius * scale).toFloat()
                    val cxPx = tx(ent.center.x)
                    val cyPx = ty(ent.center.y)
                    val oval = RectF(cxPx - rPx, cyPx - rPx, cxPx + rPx, cyPx + rPx)
                    // CAD angles are CCW with +Y up; Android Canvas.drawArc is CW with +Y down
                    canvas.drawArc(oval, -ent.startAngleDeg.toFloat(), -ent.sweepAngleDeg.toFloat(), false, geoPaint)
                }
                is CadEntity.PolylineEntity -> {
                    if (ent.globalWidth > 0.0) {
                        geoPaint.strokeWidth = max(geoPaint.strokeWidth, (ent.globalWidth * scale).toFloat())
                    }
                    // Explode into line and arc primitives so bulge segments render as true circular arcs
                    val primitives = CadGeometryKernel.explodePolylineToPrimitives(ent)
                    for (prim in primitives) {
                        when (prim) {
                            is CadEntity.LineEntity -> {
                                canvas.drawLine(tx(prim.start.x), ty(prim.start.y), tx(prim.end.x), ty(prim.end.y), geoPaint)
                            }
                            is CadEntity.ArcEntity -> {
                                val rPx = (prim.radius * scale).toFloat()
                                val cxPx = tx(prim.center.x)
                                val cyPx = ty(prim.center.y)
                                val oval = RectF(cxPx - rPx, cyPx - rPx, cxPx + rPx, cyPx + rPx)
                                canvas.drawArc(oval, -prim.startAngleDeg.toFloat(), -prim.sweepAngleDeg.toFloat(), false, geoPaint)
                            }
                            else -> Unit
                        }
                    }
                }
                is CadEntity.EllipseEntity -> {
                    val pts = CadGeometryKernel.sampleEllipsePoints(ent, 72)
                    if (pts.size >= 2) {
                        val path = Path()
                        path.moveTo(tx(pts.first().x), ty(pts.first().y))
                        for (pt in pts.drop(1)) {
                            path.lineTo(tx(pt.x), ty(pt.y))
                        }
                        path.close()
                        canvas.drawPath(path, geoPaint)
                    }
                }
                is CadEntity.SplineEntity -> {
                    val pts = ent.sampleCurvePoints(16)
                    if (pts.size >= 2) {
                        val path = Path()
                        path.moveTo(tx(pts.first().x), ty(pts.first().y))
                        for (pt in pts.drop(1)) {
                            path.lineTo(tx(pt.x), ty(pt.y))
                        }
                        if (ent.isClosed) path.close()
                        canvas.drawPath(path, geoPaint)
                    }
                }
                is CadEntity.PointEntity -> {
                    val px = tx(ent.position.x)
                    val py = ty(ent.position.y)
                    canvas.drawLine(px - 4f, py, px + 4f, py, geoPaint)
                    canvas.drawLine(px, py - 4f, px, py + 4f, geoPaint)
                }
                is CadEntity.RayEntity -> {
                    val rad = Math.toRadians(ent.angleDeg)
                    val farPt = Point2D(
                        ent.basePoint.x + cos(rad) * max(bounds.width, bounds.height) * 2.0,
                        ent.basePoint.y + sin(rad) * max(bounds.width, bounds.height) * 2.0
                    )
                    canvas.save()
                    canvas.clipRect(marginPx, marginPx, pageW - marginPx, pageH - marginPx)
                    canvas.drawLine(tx(ent.basePoint.x), ty(ent.basePoint.y), tx(farPt.x), ty(farPt.y), geoPaint)
                    canvas.restore()
                }
                is CadEntity.XLineEntity -> {
                    val rad = Math.toRadians(ent.angleDeg)
                    val span = max(bounds.width, bounds.height) * 2.0
                    val p1 = Point2D(ent.rootPoint.x - cos(rad) * span, ent.rootPoint.y - sin(rad) * span)
                    val p2 = Point2D(ent.rootPoint.x + cos(rad) * span, ent.rootPoint.y + sin(rad) * span)
                    canvas.save()
                    canvas.clipRect(marginPx, marginPx, pageW - marginPx, pageH - marginPx)
                    canvas.drawLine(tx(p1.x), ty(p1.y), tx(p2.x), ty(p2.y), geoPaint)
                    canvas.restore()
                }
                is CadEntity.DonutEntity -> {
                    val rMid = ((ent.innerRadius + ent.outerRadius) * 0.5 * scale).toFloat()
                    val ringW = ((ent.outerRadius - ent.innerRadius) * scale).toFloat().coerceAtLeast(1.2f)
                    geoPaint.strokeWidth = ringW
                    canvas.drawCircle(tx(ent.center.x), ty(ent.center.y), rMid, geoPaint)
                }
                is CadEntity.HatchEntity -> {
                    if (ent.boundaryPoints.size >= 3) {
                        val boundaryPath = Path()
                        boundaryPath.moveTo(tx(ent.boundaryPoints.first().x), ty(ent.boundaryPoints.first().y))
                        for (pt in ent.boundaryPoints.drop(1)) {
                            boundaryPath.lineTo(tx(pt.x), ty(pt.y))
                        }
                        boundaryPath.close()
                        if (ent.pattern == HatchPattern.SOLID) {
                            fillPaint.color = (geoPaint.color and 0x00FFFFFF) or 0x44000000
                            canvas.drawPath(boundaryPath, fillPaint)
                        } else {
                            val hb = ent.boundingBox()
                            val step = (6.0 * ent.patternScale.coerceAtLeast(0.25) * scale).toFloat().coerceAtLeast(4f)
                            val leftPx = tx(hb.minX)
                            val rightPx = tx(hb.maxX)
                            val topPx = ty(hb.maxY)
                            val botPx = ty(hb.minY)
                            val span = (rightPx - leftPx) + (botPx - topPx)
                            canvas.save()
                            canvas.clipPath(boundaryPath)
                            geoPaint.strokeWidth = 0.65f
                            var offset = -span
                            while (offset <= span) {
                                canvas.drawLine(leftPx + offset, botPx, leftPx + offset + (botPx - topPx), topPx, geoPaint)
                                if (ent.pattern == HatchPattern.ANSI37) {
                                    canvas.drawLine(leftPx + offset, topPx, leftPx + offset + (botPx - topPx), botPx, geoPaint)
                                }
                                offset += step
                            }
                            canvas.restore()
                        }
                        canvas.drawPath(boundaryPath, geoPaint)
                    }
                }
                is CadEntity.TextEntity -> {
                    val textColor = geoPaint.color
                    val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = textColor
                        textSize = (ent.height.toFloat() * scale).coerceIn(7.0f, 28.0f)
                        textAlign = Paint.Align.LEFT
                        typeface = Typeface.MONOSPACE
                    }
                    val lines = ent.text.lines()
                    canvas.save()
                    canvas.translate(tx(ent.position.x), ty(ent.position.y))
                    canvas.rotate(-ent.rotationDeg.toFloat())
                    for ((lineIdx, lineStr) in lines.withIndex()) {
                        canvas.drawText(lineStr, 0f, lineIdx * tp.textSize * 1.2f, tp)
                    }
                    canvas.restore()
                }
                is CadEntity.LeaderEntity -> {
                    val leaderColor = geoPaint.color
                    val tipX = tx(ent.arrowTip.x)
                    val tipY = ty(ent.arrowTip.y)
                    val shX = tx(ent.shoulderPoint.x)
                    val shY = ty(ent.shoulderPoint.y)
                    val landX = tx(ent.landingEnd.x)
                    val landY = ty(ent.landingEnd.y)
                    canvas.drawLine(tipX, tipY, shX, shY, geoPaint)
                    canvas.drawLine(shX, shY, landX, landY, geoPaint)
                    drawArrowheadPdf(tipX, tipY, shX, shY, 6.5f, leaderColor)
                    if (ent.text.isNotBlank()) {
                        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = leaderColor
                            textSize = (ent.textHeight.toFloat() * scale).coerceIn(7.5f, 18.0f)
                            textAlign = if (landX >= shX) Paint.Align.LEFT else Paint.Align.RIGHT
                            typeface = Typeface.MONOSPACE
                        }
                        canvas.drawText(ent.text, landX, landY - 3.5f, tp)
                    }
                }
                is CadEntity.CenterMarkEntity -> {
                    geoPaint.color = Color.rgb(239, 68, 68)
                    geoPaint.strokeWidth = 0.8f
                    geoPaint.pathEffect = DashPathEffect(floatArrayOf(6f, 3f, 2f, 3f), 0f)
                    val ext = ent.radius * 1.25
                    canvas.drawLine(tx(ent.center.x - ext), ty(ent.center.y), tx(ent.center.x + ext), ty(ent.center.y), geoPaint)
                    canvas.drawLine(tx(ent.center.x), ty(ent.center.y - ext), tx(ent.center.x), ty(ent.center.y + ext), geoPaint)
                }
                is CadEntity.BlockInsertEntity -> {
                    val block = blocksMap[ent.blockName] ?: return
                    val rad = Math.toRadians(ent.rotationDeg)
                    val cosA = cos(rad)
                    val sinA = sin(rad)
                    fun transformPt(pt: Point2D): Point2D {
                        val dx = (pt.x - block.basePoint.x) * ent.scaleFactor
                        val dy = (pt.y - block.basePoint.y) * ent.scaleFactor
                        return Point2D(
                            ent.insertionPoint.x + dx * cosA - dy * sinA,
                            ent.insertionPoint.y + dx * sinA + dy * cosA
                        )
                    }
                    for (child in block.entities) {
                        val transformedChild: CadEntity? = when (child) {
                            is CadEntity.LineEntity -> child.copy(start = transformPt(child.start), end = transformPt(child.end))
                            is CadEntity.CircleEntity -> child.copy(center = transformPt(child.center), radius = child.radius * ent.scaleFactor)
                            is CadEntity.ArcEntity -> child.copy(
                                center = transformPt(child.center),
                                radius = child.radius * ent.scaleFactor,
                                startAngleDeg = child.startAngleDeg + ent.rotationDeg
                            )
                            is CadEntity.PolylineEntity -> child.copy(
                                vertices = child.vertices.map { v -> v.copy(position = transformPt(v.position)) },
                                globalWidth = child.globalWidth * ent.scaleFactor
                            )
                            is CadEntity.PointEntity -> child.copy(position = transformPt(child.position))
                            is CadEntity.TextEntity -> child.copy(
                                position = transformPt(child.position),
                                height = child.height * ent.scaleFactor,
                                rotationDeg = child.rotationDeg + ent.rotationDeg
                            )
                            else -> null
                        }
                        if (transformedChild != null) {
                            renderPdfEntity(transformedChild, depth + 1)
                        }
                    }
                }
                is CadEntity.DimensionEntity -> {
                    val dimColor = Color.rgb(56, 189, 248)
                    val extColor = Color.argb(210, 56, 189, 248)
                    val plan = AdaptiveAnnotationRenderer.computeRenderPlan(
                        dim = ent,
                        precision = payload.precision,
                        storageUnit = payload.storageUnit,
                        displayUnit = payload.displayUnit,
                        style = DimensionStyleConfig(),
                        viewport = ViewportTransform(pixelsPerModelUnit = scale.coerceAtLeast(0.5f))
                    )
                    val arrowSizePx = (plan.arrowLengthModel * scale).toFloat().coerceIn(4.5f, 9.0f)

                    when (ent.dimensionType) {
                        DimensionType.LINEAR_HORIZONTAL,
                        DimensionType.LINEAR_VERTICAL,
                        DimensionType.ALIGNED -> {
                            // Orthogonal/aligned extension lines
                            geoPaint.color = extColor
                            geoPaint.strokeWidth = 0.65f
                            geoPaint.pathEffect = null
                            canvas.drawLine(
                                tx(plan.ext1StartModel.x), ty(plan.ext1StartModel.y),
                                tx(plan.ext1EndModel.x), ty(plan.ext1EndModel.y),
                                geoPaint
                            )
                            canvas.drawLine(
                                tx(plan.ext2StartModel.x), ty(plan.ext2StartModel.y),
                                tx(plan.ext2EndModel.x), ty(plan.ext2EndModel.y),
                                geoPaint
                            )

                            // Dimension line + arrowheads
                            geoPaint.color = dimColor
                            geoPaint.strokeWidth = 0.9f
                            val d1x = tx(plan.dimLineStartModel.x)
                            val d1y = ty(plan.dimLineStartModel.y)
                            val d2x = tx(plan.dimLineEndModel.x)
                            val d2y = ty(plan.dimLineEndModel.y)
                            canvas.drawLine(d1x, d1y, d2x, d2y, geoPaint)
                            drawArrowheadPdf(d1x, d1y, d2x, d2y, arrowSizePx, dimColor)
                            drawArrowheadPdf(d2x, d2y, d1x, d1y, arrowSizePx, dimColor)
                        }
                        DimensionType.RADIUS,
                        DimensionType.DIAMETER -> {
                            val centerPt = plan.circleOrArcCenterModel ?: ent.anchor1
                            val rimPt = plan.rimContactPointModel ?: ent.anchor2
                            val shoulderPt = plan.leaderShoulderModel ?: ent.dimensionLinePoint
                            val landingPt = plan.leaderLandingEndModel ?: shoulderPt

                            // Center mark crosshairs
                            geoPaint.color = extColor
                            geoPaint.strokeWidth = 0.6f
                            geoPaint.pathEffect = null
                            val cmPx = 4.0f
                            val cxPx = tx(centerPt.x)
                            val cyPx = ty(centerPt.y)
                            canvas.drawLine(cxPx - cmPx, cyPx, cxPx + cmPx, cyPx, geoPaint)
                            canvas.drawLine(cxPx, cyPx - cmPx, cxPx, cyPx + cmPx, geoPaint)

                            // Radial/Diametric leader line and horizontal landing shelf
                            geoPaint.color = dimColor
                            geoPaint.strokeWidth = 0.9f
                            val rimX = tx(rimPt.x)
                            val rimY = ty(rimPt.y)
                            val shX = tx(shoulderPt.x)
                            val shY = ty(shoulderPt.y)
                            val landX = tx(landingPt.x)
                            val landY = ty(landingPt.y)

                            if (ent.dimensionType == DimensionType.DIAMETER && plan.oppositeRimPointModel != null) {
                                val oppX = tx(plan.oppositeRimPointModel.x)
                                val oppY = ty(plan.oppositeRimPointModel.y)
                                canvas.drawLine(oppX, oppY, rimX, rimY, geoPaint)
                                drawArrowheadPdf(oppX, oppY, rimX, rimY, arrowSizePx, dimColor)
                            } else {
                                canvas.drawLine(cxPx, cyPx, rimX, rimY, geoPaint)
                            }
                            canvas.drawLine(rimX, rimY, shX, shY, geoPaint)
                            canvas.drawLine(shX, shY, landX, landY, geoPaint)
                            drawArrowheadPdf(rimX, rimY, shX, shY, arrowSizePx, dimColor)
                        }
                        DimensionType.ANGULAR -> {
                            geoPaint.color = extColor
                            geoPaint.strokeWidth = 0.65f
                            geoPaint.pathEffect = null
                            canvas.drawLine(
                                tx(plan.ext1StartModel.x), ty(plan.ext1StartModel.y),
                                tx(plan.ext1EndModel.x), ty(plan.ext1EndModel.y),
                                geoPaint
                            )
                            canvas.drawLine(
                                tx(plan.ext2StartModel.x), ty(plan.ext2StartModel.y),
                                tx(plan.ext2EndModel.x), ty(plan.ext2EndModel.y),
                                geoPaint
                            )
                            val angCenter = plan.angularCenterModel
                            if (angCenter != null && plan.angularRadiusModel > 0.0) {
                                geoPaint.color = dimColor
                                geoPaint.strokeWidth = 0.9f
                                val rPx = (plan.angularRadiusModel * scale).toFloat()
                                val acx = tx(angCenter.x)
                                val acy = ty(angCenter.y)
                                val oval = RectF(acx - rPx, acy - rPx, acx + rPx, acy + rPx)
                                canvas.drawArc(
                                    oval,
                                    -plan.angularStartDeg.toFloat(),
                                    -plan.angularSweepDeg.toFloat(),
                                    false,
                                    geoPaint
                                )
                            }
                        }
                        DimensionType.ORDINATE_X,
                        DimensionType.ORDINATE_Y -> {
                            geoPaint.color = dimColor
                            geoPaint.strokeWidth = 0.85f
                            geoPaint.pathEffect = null
                            canvas.drawLine(
                                tx(plan.ext1StartModel.x), ty(plan.ext1StartModel.y),
                                tx(plan.ext1EndModel.x), ty(plan.ext1EndModel.y),
                                geoPaint
                            )
                            canvas.drawLine(
                                tx(plan.dimLineStartModel.x), ty(plan.dimLineStartModel.y),
                                tx(plan.dimLineEndModel.x), ty(plan.dimLineEndModel.y),
                                geoPaint
                            )
                        }
                    }

                    // Dimension label with background mask
                    val labelX = tx(plan.textPositionModel.x)
                    val labelY = ty(plan.textPositionModel.y)
                    textPaint.color = dimColor
                    textPaint.textSize = plan.fontSizeSp.coerceIn(7.0f, 11.5f)
                    val textW = textPaint.measureText(plan.labelText)
                    val textH = textPaint.textSize

                    canvas.save()
                    canvas.translate(labelX, labelY)
                    if (abs(plan.textRotationDeg) > 0.5) {
                        // Invert CAD CCW angle for Android CW canvas rotation
                        canvas.rotate(-plan.textRotationDeg.toFloat())
                    }
                    fillPaint.color = Color.rgb(12, 18, 28)
                    canvas.drawRect(-textW * 0.54f, -textH * 0.95f, textW * 0.54f, textH * 0.25f, fillPaint)
                    canvas.drawText(plan.labelText, 0f, 0f, textPaint)
                    canvas.restore()
                }
            }
        }

        for (ent in visibleEntities) {
            renderPdfEntity(ent)
        }
    }

    fun shareExportedFile(context: Context, file: File, mimeType: String) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "Export / Share CAD File").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    private fun fmt(v: Double): String {
        val safe = if (v.isFinite()) v else 0.0
        return String.format(Locale.US, "%.4f", safe)
    }

    private fun colorHexToAci(hex: Long): Int = when (hex and 0xFFFFFF) {
        0xEF4444L -> 1 // Red
        0xF59E0BL, 0xFFB300L -> 2 // Yellow/Amber
        0x22C55EL, 0x10B981L -> 3 // Green
        0x00E5FFL, 0x38BDF8L -> 4 // Cyan
        0x3B82F6L -> 5 // Blue
        else -> 7 // White
    }

    private fun aciToColorHex(aci: Int): Long = when (aci) {
        1 -> 0xFFEF4444L
        2 -> 0xFFF59E0BL
        3 -> 0xFF22C55EL
        4 -> 0xFF00E5FFL
        5 -> 0xFF3B82F6L
        else -> 0xFFF8FAFCL
    }
}
