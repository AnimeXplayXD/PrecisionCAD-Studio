package com.example.cad.io

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
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
import kotlin.math.cos
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
 */
object CadDxfInterchange {

    fun exportToDxf(payload: DxfDocumentPayload): String {
        val sb = StringBuilder(16384)
        val bounds = if (payload.entities.isEmpty()) {
            BoundingBox2D(0.0, 0.0, 200.0, 150.0)
        } else {
            payload.entities.map { it.boundingBox() }.reduce { a, b -> a.union(b) }
        }

        // 1. HEADER SECTION
        sb.appendLine("0\nSECTION\n2\nHEADER")
        sb.appendLine("9\n\$ACADVER\n1\nAC1015")
        sb.appendLine("9\n\$INSUNITS\n70\n${payload.displayUnit.dxfInsUnitsCode}")
        sb.appendLine("9\n\$LUNITS\n70\n2")
        sb.appendLine("9\n\$LUPREC\n70\n${payload.precision.linearDecimals}")
        sb.appendLine("9\n\$EXTMIN\n10\n${fmt(bounds.minX)}\n20\n${fmt(bounds.minY)}\n30\n0.0")
        sb.appendLine("9\n\$EXTMAX\n10\n${fmt(bounds.maxX)}\n20\n${fmt(bounds.maxY)}\n30\n0.0")
        sb.appendLine("0\nENDSEC")

        // 2. TABLES SECTION (LTYPE & LAYER)
        sb.appendLine("0\nSECTION\n2\nTABLES")
        sb.appendLine("0\nTABLE\n2\nLTYPE\n70\n${CadLineType.entries.size}")
        for (lt in CadLineType.entries.filter { it != CadLineType.BY_LAYER }) {
            sb.appendLine("0\nLTYPE\n2\n${lt.dxfName}\n70\n0\n3\n${lt.label}\n72\n65\n73\n0\n40\n0.0")
        }
        sb.appendLine("0\nENDTAB")

        sb.appendLine("0\nTABLE\n2\nLAYER\n70\n${payload.layers.size}")
        for (layer in payload.layers) {
            val flags = (if (layer.isFrozen) 1 else 0) or (if (layer.isLocked) 4 else 0)
            val aci = colorHexToAci(layer.colorHex).let { if (!layer.isVisible) -it else it }
            sb.appendLine("0\nLAYER\n2\n${layer.name}\n70\n$flags\n62\n$aci\n6\n${layer.lineType.dxfName}")
            sb.appendLine("370\n${(layer.lineWeightMm * 100).toInt()}")
            sb.appendLine("420\n${(layer.colorHex and 0xFFFFFF)}")
        }
        sb.appendLine("0\nENDTAB")
        sb.appendLine("0\nENDSEC")

        // 3. BLOCKS SECTION
        sb.appendLine("0\nSECTION\n2\nBLOCKS")
        for (block in payload.blocks) {
            sb.appendLine("0\nBLOCK\n8\n0\n2\n${block.name}\n70\n0")
            sb.appendLine("10\n${fmt(block.basePoint.x)}\n20\n${fmt(block.basePoint.y)}\n30\n0.0")
            for (ent in block.entities) {
                appendEntityDxf(sb, ent, payload)
            }
            sb.appendLine("0\nENDBLK\n8\n0")
        }
        sb.appendLine("0\nENDSEC")

        // 4. ENTITIES SECTION
        sb.appendLine("0\nSECTION\n2\nENTITIES")
        for (ent in payload.entities) {
            appendEntityDxf(sb, ent, payload)
        }
        sb.appendLine("0\nENDSEC")
        sb.appendLine("0\nEOF")
        return sb.toString()
    }

    private fun appendEntityDxf(sb: StringBuilder, ent: CadEntity, payload: DxfDocumentPayload) {
        val unitRatio = payload.storageUnit.toMillimetersFactor / payload.displayUnit.toMillimetersFactor
        fun u(v: Double): String = fmt(v * unitRatio)

        when (ent) {
            is CadEntity.LineEntity -> {
                sb.appendLine("0\nLINE\n8\n${ent.layerName}")
                appendCommonEntityProps(sb, ent)
                sb.appendLine("10\n${u(ent.start.x)}\n20\n${u(ent.start.y)}\n30\n0.0")
                sb.appendLine("11\n${u(ent.end.x)}\n21\n${u(ent.end.y)}\n31\n0.0")
            }
            is CadEntity.PolylineEntity -> {
                sb.appendLine("0\nLWPOLYLINE\n8\n${ent.layerName}")
                appendCommonEntityProps(sb, ent)
                sb.appendLine("90\n${ent.vertices.size}")
                sb.appendLine("70\n${if (ent.isClosed) 1 else 0}")
                sb.appendLine("43\n${u(ent.globalWidth)}")
                for (v in ent.vertices) {
                    sb.appendLine("10\n${u(v.position.x)}\n20\n${u(v.position.y)}\n42\n${fmt(v.bulge)}")
                }
            }
            is CadEntity.CircleEntity -> {
                sb.appendLine("0\nCIRCLE\n8\n${ent.layerName}")
                appendCommonEntityProps(sb, ent)
                sb.appendLine("10\n${u(ent.center.x)}\n20\n${u(ent.center.y)}\n30\n0.0")
                sb.appendLine("40\n${u(ent.radius)}")
            }
            is CadEntity.ArcEntity -> {
                sb.appendLine("0\nARC\n8\n${ent.layerName}")
                appendCommonEntityProps(sb, ent)
                val startAng = if (ent.sweepAngleDeg >= 0) ent.startAngleDeg else ent.endAngleDeg
                val endAng = if (ent.sweepAngleDeg >= 0) ent.endAngleDeg else ent.startAngleDeg
                sb.appendLine("10\n${u(ent.center.x)}\n20\n${u(ent.center.y)}\n30\n0.0")
                sb.appendLine("40\n${u(ent.radius)}")
                sb.appendLine("50\n${fmt(startAng)}\n51\n${fmt(endAng)}")
            }
            is CadEntity.EllipseEntity -> {
                sb.appendLine("0\nELLIPSE\n8\n${ent.layerName}")
                appendCommonEntityProps(sb, ent)
                val rad = Math.toRadians(ent.rotationAngleDeg)
                val majX = ent.majorRadius * cos(rad)
                val majY = ent.majorRadius * sin(rad)
                val ratio = (ent.minorRadius / ent.majorRadius.coerceAtLeast(1e-6)).coerceIn(0.01, 1.0)
                sb.appendLine("10\n${u(ent.center.x)}\n20\n${u(ent.center.y)}\n30\n0.0")
                sb.appendLine("11\n${u(majX)}\n21\n${u(majY)}\n31\n0.0")
                sb.appendLine("40\n${fmt(ratio)}\n41\n0.0\n42\n6.283185")
            }
            is CadEntity.SplineEntity -> {
                sb.appendLine("0\nSPLINE\n8\n${ent.layerName}")
                appendCommonEntityProps(sb, ent)
                sb.appendLine("70\n${if (ent.isClosed) 1 else 8}\n71\n3\n73\n${ent.controlPoints.size}")
                for (pt in ent.controlPoints) {
                    sb.appendLine("10\n${u(pt.x)}\n20\n${u(pt.y)}\n30\n0.0")
                }
            }
            is CadEntity.PointEntity -> {
                sb.appendLine("0\nPOINT\n8\n${ent.layerName}")
                appendCommonEntityProps(sb, ent)
                sb.appendLine("10\n${u(ent.position.x)}\n20\n${u(ent.position.y)}\n30\n0.0")
            }
            is CadEntity.RayEntity -> {
                sb.appendLine("0\nRAY\n8\n${ent.layerName}")
                val rad = Math.toRadians(ent.angleDeg)
                sb.appendLine("10\n${u(ent.basePoint.x)}\n20\n${u(ent.basePoint.y)}\n30\n0.0")
                sb.appendLine("11\n${fmt(cos(rad))}\n21\n${fmt(sin(rad))}\n31\n0.0")
            }
            is CadEntity.XLineEntity -> {
                sb.appendLine("0\nXLINE\n8\n${ent.layerName}")
                val rad = Math.toRadians(ent.angleDeg)
                sb.appendLine("10\n${u(ent.rootPoint.x)}\n20\n${u(ent.rootPoint.y)}\n30\n0.0")
                sb.appendLine("11\n${fmt(cos(rad))}\n21\n${fmt(sin(rad))}\n31\n0.0")
            }
            is CadEntity.DonutEntity -> {
                sb.appendLine("0\nDONUT\n8\n${ent.layerName}")
                sb.appendLine("10\n${u(ent.center.x)}\n20\n${u(ent.center.y)}")
                sb.appendLine("40\n${u(ent.innerDiameter)}\n41\n${u(ent.outerDiameter)}")
            }
            is CadEntity.HatchEntity -> {
                sb.appendLine("0\nHATCH\n8\n${ent.layerName}")
                sb.appendLine("2\n${ent.pattern.dxfName}\n70\n${if (ent.pattern == HatchPattern.SOLID) 1 else 0}")
                sb.appendLine("41\n${fmt(ent.patternScale)}\n52\n${fmt(ent.patternAngleDeg)}")
                sb.appendLine("93\n${ent.boundaryPoints.size}")
                for (pt in ent.boundaryPoints) {
                    sb.appendLine("10\n${u(pt.x)}\n20\n${u(pt.y)}")
                }
            }
            is CadEntity.TextEntity -> {
                sb.appendLine("0\nMTEXT\n8\n${ent.layerName}")
                appendCommonEntityProps(sb, ent)
                sb.appendLine("10\n${u(ent.position.x)}\n20\n${u(ent.position.y)}\n30\n0.0")
                sb.appendLine("40\n${u(ent.height)}\n50\n${fmt(ent.rotationDeg)}")
                sb.appendLine("1\n${ent.text.replace("\n", "\\P")}")
            }
            is CadEntity.LeaderEntity -> {
                sb.appendLine("0\nMLEADER\n8\n${ent.layerName}")
                sb.appendLine("10\n${u(ent.arrowTip.x)}\n20\n${u(ent.arrowTip.y)}")
                sb.appendLine("11\n${u(ent.shoulderPoint.x)}\n21\n${u(ent.shoulderPoint.y)}")
                sb.appendLine("12\n${u(ent.landingEnd.x)}\n22\n${u(ent.landingEnd.y)}")
                sb.appendLine("40\n${u(ent.textHeight)}\n1\n${ent.text}")
            }
            is CadEntity.CenterMarkEntity -> {
                sb.appendLine("0\nCENTERMARK\n8\n${ent.layerName}")
                sb.appendLine("10\n${u(ent.center.x)}\n20\n${u(ent.center.y)}\n40\n${u(ent.radius)}")
            }
            is CadEntity.BlockInsertEntity -> {
                sb.appendLine("0\nINSERT\n8\n${ent.layerName}\n2\n${ent.blockName}")
                sb.appendLine("10\n${u(ent.insertionPoint.x)}\n20\n${u(ent.insertionPoint.y)}\n30\n0.0")
                sb.appendLine("41\n${fmt(ent.scaleFactor)}\n42\n${fmt(ent.scaleFactor)}\n50\n${fmt(ent.rotationDeg)}")
            }
            is CadEntity.DimensionEntity -> {
                sb.appendLine("0\nDIMENSION\n8\n${ent.layerName}")
                val dimTypeCode = when (ent.dimensionType) {
                    DimensionType.LINEAR_HORIZONTAL -> 0
                    DimensionType.LINEAR_VERTICAL -> 1
                    DimensionType.ALIGNED -> 2
                    DimensionType.ANGULAR -> 3
                    DimensionType.DIAMETER -> 4
                    DimensionType.RADIUS -> 5
                    DimensionType.ORDINATE_X -> 6
                    DimensionType.ORDINATE_Y -> 7
                }
                sb.appendLine("70\n$dimTypeCode")
                sb.appendLine("10\n${u(ent.dimensionLinePoint.x)}\n20\n${u(ent.dimensionLinePoint.y)}\n30\n0.0")
                sb.appendLine("13\n${u(ent.anchor1.x)}\n23\n${u(ent.anchor1.y)}\n33\n0.0")
                sb.appendLine("14\n${u(ent.anchor2.x)}\n24\n${u(ent.anchor2.y)}\n34\n0.0")
                val label = ent.formattedLabel(payload.precision, payload.storageUnit, payload.displayUnit)
                sb.appendLine("1\n$label")
                if (ent.customPrefix.isNotBlank()) sb.appendLine("3\n${ent.customPrefix}")
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
     * blocks, geometric entities, and dimensions.
     */
    fun importFromDxf(dxfContent: String): DxfDocumentPayload {
        val rawLines = dxfContent.lines().map { it.trim() }
        val pairs = mutableListOf<Pair<Int, String>>()
        var i = 0
        while (i + 1 < rawLines.size) {
            val code = rawLines[i].toIntOrNull()
            if (code != null) {
                pairs.add(code to rawLines[i + 1])
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

        while (idx < pairs.size) {
            val (code, value) = pairs[idx]
            if (code == 0 && value == "SECTION" && idx + 1 < pairs.size && pairs[idx + 1].first == 2) {
                val secName = pairs[idx + 1].second.uppercase()
                inHeaderSection = secName == "HEADER"
                inTablesSection = secName == "TABLES"
                inBlocksSection = secName == "BLOCKS"
                inEntitiesSection = secName == "ENTITIES"
                idx += 2
                continue
            }
            if (code == 0 && value == "ENDSEC") {
                inHeaderSection = false
                inTablesSection = false
                inBlocksSection = false
                inEntitiesSection = false
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
                var bx = 0.0; var by = 0.0
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
                while (idx < pairs.size && !(pairs[idx].first == 0 && pairs[idx].second == "ENDBLK")) {
                    if (pairs[idx].first == 0) {
                        val (ent, nextIdx) = parseEntityRecord(pairs, idx)
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

            if (inEntitiesSection && code == 0) {
                val (ent, nextIdx) = parseEntityRecord(pairs, idx)
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

    private fun parseEntityRecord(
        pairs: List<Pair<Int, String>>,
        startIdx: Int
    ): Pair<CadEntity?, Int> {
        val entityType = pairs[startIdx].second.uppercase()
        var idx = startIdx + 1
        var layer = "0"
        var x1 = 0.0; var y1 = 0.0
        var x2 = 0.0; var y2 = 0.0
        var x3 = 0.0; var y3 = 0.0
        var r = 10.0; var r2 = 5.0
        var a1 = 0.0; var a2 = 90.0
        var flags = 0
        var textVal = ""
        var prefixVal = ""
        var nameVal = ""
        val vertices = mutableListOf<PolylineVertex>()
        var pendingVx: Double? = null
        var pendingVy: Double = 0.0
        var pendingBulge: Double = 0.0

        while (idx < pairs.size && pairs[idx].first != 0) {
            val (c, v) = pairs[idx]
            when (c) {
                8 -> layer = v
                2 -> nameVal = v
                1 -> textVal = v.replace("\\P", "\n")
                3 -> prefixVal = v
                70 -> flags = v.toIntOrNull() ?: 0
                10 -> {
                    if (entityType == "LWPOLYLINE" || entityType == "SPLINE" || entityType == "HATCH") {
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
                    if (entityType == "LWPOLYLINE" || entityType == "SPLINE" || entityType == "HATCH") {
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
                11 -> x2 = v.toDoubleOrNull() ?: 0.0
                21 -> y2 = v.toDoubleOrNull() ?: 0.0
                12, 13 -> x2 = v.toDoubleOrNull() ?: x2
                22, 23 -> y2 = v.toDoubleOrNull() ?: y2
                14 -> x3 = v.toDoubleOrNull() ?: 0.0
                24 -> y3 = v.toDoubleOrNull() ?: 0.0
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
            "LWPOLYLINE" -> CadEntity.PolylineEntity(
                layerName = layer,
                vertices = vertices,
                isClosed = (flags and 1) != 0
            )
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
                val majR = kotlin.math.hypot(x2, y2).coerceAtLeast(1.0)
                val minR = (majR * r).coerceAtLeast(0.5)
                val rot = Math.toDegrees(kotlin.math.atan2(y2, x2))
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
                angleDeg = Math.toDegrees(kotlin.math.atan2(y2, x2))
            )
            "XLINE" -> CadEntity.XLineEntity(
                layerName = layer,
                rootPoint = Point2D(x1, y1),
                angleDeg = Math.toDegrees(kotlin.math.atan2(y2, x2))
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
            "MLEADER", "LEADER" -> CadEntity.LeaderEntity(
                layerName = layer,
                arrowTip = Point2D(x1, y1),
                shoulderPoint = Point2D(x2, y2),
                landingEnd = Point2D(x2 + 10.0, y2),
                text = textVal,
                textHeight = r.coerceAtLeast(2.0)
            )
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
                val dimType = when (flags and 7) {
                    0 -> DimensionType.LINEAR_HORIZONTAL
                    1 -> DimensionType.LINEAR_VERTICAL
                    2 -> DimensionType.ALIGNED
                    3 -> DimensionType.ANGULAR
                    4 -> DimensionType.DIAMETER
                    5 -> DimensionType.RADIUS
                    6 -> DimensionType.ORDINATE_X
                    7 -> DimensionType.ORDINATE_Y
                    else -> DimensionType.LINEAR_HORIZONTAL
                }
                CadEntity.DimensionEntity(
                    layerName = layer,
                    dimensionType = dimType,
                    anchor1 = Point2D(x2, y2),
                    anchor2 = Point2D(x3, y3),
                    dimensionLinePoint = Point2D(x1, y1),
                    customPrefix = prefixVal
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
        val sb = StringBuilder(8192)
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${fmt(sheetW)} ${fmt(sheetH)}" width="${fmt(sheetW)}mm" height="${fmt(sheetH)}mm">""")
        sb.appendLine("""  <rect width="100%" height="100%" fill="#0E141E" stroke="#334155" stroke-width="0.5"/>""")

        val bounds = if (payload.entities.isEmpty()) BoundingBox2D(0.0, 0.0, 200.0, 150.0)
        else payload.entities.map { it.boundingBox() }.reduce { a, b -> a.union(b) }
        val scale = min(
            (sheetW * 0.75) / bounds.width.coerceAtLeast(10.0),
            (sheetH * 0.75) / bounds.height.coerceAtLeast(10.0)
        )
        val cx = bounds.center.x
        val cy = bounds.center.y
        fun sx(x: Double): String = fmt(sheetW * 0.5 + (x - cx) * scale)
        fun sy(y: Double): String = fmt(sheetH * 0.5 - (y - cy) * scale)

        val layerMap = payload.layers.associateBy { it.name }
        for (ent in payload.entities) {
            val layer = layerMap[ent.layerName]
            if (layer != null && (!layer.isVisible || layer.isFrozen)) continue
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
                    val pts = ent.vertices.joinToString(" ") { "${sx(it.position.x)},${sy(it.position.y)}" }
                    val tag = if (ent.isClosed) "polygon" else "polyline"
                    sb.appendLine("""  <$tag points="$pts" fill="none" stroke="$hex" stroke-width="$lw"/>""")
                }
                is CadEntity.DimensionEntity -> {
                    val label = ent.formattedLabel(payload.precision, payload.storageUnit, payload.displayUnit)
                    sb.appendLine(
                        """  <line x1="${sx(ent.anchor1.x)}" y1="${sy(ent.anchor1.y)}" x2="${sx(ent.dimensionLinePoint.x)}" y2="${sy(ent.dimensionLinePoint.y)}" stroke="#38BDF8" stroke-width="0.22"/>"""
                    )
                    sb.appendLine(
                        """  <text x="${sx(ent.dimensionLinePoint.x)}" y="${sy(ent.dimensionLinePoint.y)}" fill="#38BDF8" font-family="monospace" font-size="3.2" text-anchor="middle">$label</text>"""
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
        val pdf = PdfDocument()

        val pageW = (payload.sheetSetup.effectiveWidthMm * 2.83465).toInt().coerceAtLeast(400)
        val pageH = (payload.sheetSetup.effectiveHeightMm * 2.83465).toInt().coerceAtLeast(300)
        val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, 1).create()
        val page = pdf.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        // Sheet background & border
        val bgPaint = Paint().apply { color = Color.rgb(12, 18, 28); style = Paint.Style.FILL }
        canvas.drawRect(0f, 0f, pageW.toFloat(), pageH.toFloat(), bgPaint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(148, 163, 184)
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        val marginPx = (payload.sheetSetup.marginMm * 2.83465).toFloat()
        canvas.drawRect(marginPx, marginPx, pageW - marginPx, pageH - marginPx, borderPaint)

        // Title Block in bottom-right
        if (payload.sheetSetup.showTitleBlock) {
            val tbW = 240f
            val tbH = 68f
            val tbLeft = pageW - marginPx - tbW
            val tbTop = pageH - marginPx - tbH
            canvas.drawRect(tbLeft, tbTop, pageW - marginPx, pageH - marginPx, borderPaint)
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(226, 232, 240)
                textSize = 9.5f
            }
            canvas.drawText("TITLE: ${payload.sheetSetup.titleBlockTitle}", tbLeft + 8f, tbTop + 16f, textPaint)
            canvas.drawText("DWG NO: ${payload.sheetSetup.titleBlockDrawingNo}  ${payload.sheetSetup.titleBlockRevision}", tbLeft + 8f, tbTop + 32f, textPaint)
            canvas.drawText(
                "UNITS: ${payload.displayUnit.symbol}   SCALE: ${payload.sheetSetup.scaleLabel}   STD: ${payload.sheetSetup.draftingStandard.name}",
                tbLeft + 8f,
                tbTop + 48f,
                textPaint
            )
            canvas.drawText("PRECISIONCAD STUDIO 2D TECHNICAL SHEET", tbLeft + 8f, tbTop + 62f, textPaint)
        }

        // Render geometry centered in sheet printable area
        val bounds = if (payload.entities.isEmpty()) BoundingBox2D(0.0, 0.0, 200.0, 150.0)
        else payload.entities.map { it.boundingBox() }.reduce { a, b -> a.union(b) }
        val drawW = (pageW - 2 * marginPx) * 0.78f
        val drawH = (pageH - 2 * marginPx) * 0.72f
        val scale = min(drawW / bounds.width.coerceAtLeast(10.0).toFloat(), drawH / bounds.height.coerceAtLeast(10.0).toFloat())
        val cx = bounds.center.x.toFloat()
        val cy = bounds.center.y.toFloat()
        fun tx(x: Double): Float = pageW * 0.5f + ((x.toFloat() - cx) * scale)
        fun ty(y: Double): Float = pageH * 0.46f - ((y.toFloat() - cy) * scale)

        val geoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(56, 189, 248)
            textSize = 8.5f
            textAlign = Paint.Align.CENTER
        }

        for (ent in payload.entities) {
            geoPaint.color = Color.rgb(241, 245, 249)
            geoPaint.pathEffect = null
            when (ent) {
                is CadEntity.LineEntity -> canvas.drawLine(tx(ent.start.x), ty(ent.start.y), tx(ent.end.x), ty(ent.end.y), geoPaint)
                is CadEntity.CircleEntity -> canvas.drawCircle(tx(ent.center.x), ty(ent.center.y), (ent.radius * scale).toFloat(), geoPaint)
                is CadEntity.PolylineEntity -> {
                    if (ent.vertices.size >= 2) {
                        val path = Path()
                        path.moveTo(tx(ent.vertices.first().position.x), ty(ent.vertices.first().position.y))
                        for (v in ent.vertices.drop(1)) {
                            path.lineTo(tx(v.position.x), ty(v.position.y))
                        }
                        if (ent.isClosed) path.close()
                        canvas.drawPath(path, geoPaint)
                    }
                }
                is CadEntity.DimensionEntity -> {
                    geoPaint.color = Color.rgb(56, 189, 248)
                    geoPaint.strokeWidth = 0.8f
                    canvas.drawLine(tx(ent.anchor1.x), ty(ent.anchor1.y), tx(ent.dimensionLinePoint.x), ty(ent.dimensionLinePoint.y), geoPaint)
                    canvas.drawLine(tx(ent.anchor2.x), ty(ent.anchor2.y), tx(ent.dimensionLinePoint.x), ty(ent.dimensionLinePoint.y), geoPaint)
                    val lbl = ent.formattedLabel(payload.precision, payload.storageUnit, payload.displayUnit)
                    canvas.drawText(lbl, tx(ent.dimensionLinePoint.x), ty(ent.dimensionLinePoint.y) - 4f, textPaint)
                }
                is CadEntity.CenterMarkEntity -> {
                    geoPaint.color = Color.rgb(239, 68, 68)
                    geoPaint.pathEffect = DashPathEffect(floatArrayOf(6f, 3f, 2f, 3f), 0f)
                    val ext = ent.radius * 1.25
                    canvas.drawLine(tx(ent.center.x - ext), ty(ent.center.y), tx(ent.center.x + ext), ty(ent.center.y), geoPaint)
                    canvas.drawLine(tx(ent.center.x), ty(ent.center.y - ext), tx(ent.center.x), ty(ent.center.y + ext), geoPaint)
                }
                else -> Unit
            }
        }

        pdf.finishPage(page)
        FileOutputStream(outFile).use { pdf.writeTo(it) }
        pdf.close()
        return outFile
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

    private fun fmt(v: Double): String = String.format(Locale.US, "%.4f", v)

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
