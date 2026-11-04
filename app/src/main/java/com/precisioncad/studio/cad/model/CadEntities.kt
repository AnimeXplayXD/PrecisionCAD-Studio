package com.precisioncad.studio.cad.model

import java.util.UUID
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Immutable double-precision 2D model-space coordinate.
 */
data class Point2D(
    val x: Double,
    val y: Double
) {
    fun distanceTo(other: Point2D): Double = hypot(other.x - x, other.y - y)

    fun angleToDeg(other: Point2D): Double {
        val deg = Math.toDegrees(atan2(other.y - y, other.x - x))
        return if (deg < 0.0) deg + 360.0 else deg
    }

    fun offset(dx: Double, dy: Double): Point2D = Point2D(x + dx, y + dy)

    fun polarOffset(distance: Double, angleDeg: Double): Point2D {
        val rad = Math.toRadians(angleDeg)
        return Point2D(x + distance * cos(rad), y + distance * sin(rad))
    }

    fun rotateAround(pivot: Point2D, angleDeg: Double): Point2D {
        val rad = Math.toRadians(angleDeg)
        val c = cos(rad)
        val s = sin(rad)
        val dx = x - pivot.x
        val dy = y - pivot.y
        return Point2D(
            x = pivot.x + dx * c - dy * s,
            y = pivot.y + dx * s + dy * c
        )
    }

    fun scaleFrom(pivot: Point2D, factor: Double): Point2D = Point2D(
        x = pivot.x + (x - pivot.x) * factor,
        y = pivot.y + (y - pivot.y) * factor
    )

    fun mirrorAcross(lineStart: Point2D, lineEnd: Point2D): Point2D {
        val dx = lineEnd.x - lineStart.x
        val dy = lineEnd.y - lineStart.y
        val lenSq = dx * dx + dy * dy
        if (lenSq < 1e-12) return this
        val t = ((x - lineStart.x) * dx + (y - lineStart.y) * dy) / lenSq
        val projX = lineStart.x + t * dx
        val projY = lineStart.y + t * dy
        return Point2D(2.0 * projX - x, 2.0 * projY - y)
    }

    fun midpointTo(other: Point2D): Point2D =
        Point2D((x + other.x) * 0.5, (y + other.y) * 0.5)
}

data class BoundingBox2D(
    val minX: Double,
    val minY: Double,
    val maxX: Double,
    val maxY: Double
) {
    val width: Double get() = (maxX - minX).coerceAtLeast(0.0)
    val height: Double get() = (maxY - minY).coerceAtLeast(0.0)
    val center: Point2D get() = Point2D((minX + maxX) * 0.5, (minY + maxY) * 0.5)

    fun contains(pt: Point2D, tolerance: Double = 1e-6): Boolean =
        pt.x >= minX - tolerance && pt.x <= maxX + tolerance &&
            pt.y >= minY - tolerance && pt.y <= maxY + tolerance

    fun fullyContains(other: BoundingBox2D, tolerance: Double = 1e-6): Boolean =
        other.minX >= minX - tolerance && other.maxX <= maxX + tolerance &&
            other.minY >= minY - tolerance && other.maxY <= maxY + tolerance

    fun intersects(other: BoundingBox2D, tolerance: Double = 1e-6): Boolean =
        other.maxX >= minX - tolerance && other.minX <= maxX + tolerance &&
            other.maxY >= minY - tolerance && other.minY <= maxY + tolerance

    fun union(other: BoundingBox2D): BoundingBox2D = BoundingBox2D(
        minX = min(minX, other.minX),
        minY = min(minY, other.minY),
        maxX = max(maxX, other.maxX),
        maxY = max(maxY, other.maxY)
    )

    companion object {
        fun fromPoints(points: List<Point2D>): BoundingBox2D {
            if (points.isEmpty()) return BoundingBox2D(0.0, 0.0, 100.0, 100.0)
            var minX = Double.POSITIVE_INFINITY
            var minY = Double.POSITIVE_INFINITY
            var maxX = Double.NEGATIVE_INFINITY
            var maxY = Double.NEGATIVE_INFINITY
            for (p in points) {
                if (p.x < minX) minX = p.x
                if (p.y < minY) minY = p.y
                if (p.x > maxX) maxX = p.x
                if (p.y > maxY) maxY = p.y
            }
            return BoundingBox2D(minX, minY, maxX, maxY)
        }
    }
}

enum class CadLineType(val label: String, val dxfName: String) {
    BY_LAYER("ByLayer", "BYLAYER"),
    CONTINUOUS("Continuous", "CONTINUOUS"),
    DASHED("Dashed", "DASHED"),
    HIDDEN("Hidden", "HIDDEN"),
    CENTER("Center (Dash-Dot)", "CENTER"),
    PHANTOM("Phantom", "PHANTOM"),
    DOTTED("Dotted", "DOT")
}

data class CadLayer(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val colorHex: Long = 0xFFF8FAFC,
    val lineType: CadLineType = CadLineType.CONTINUOUS,
    val lineWeightMm: Float = 0.25f,
    val isVisible: Boolean = true,
    val isLocked: Boolean = false,
    val isFrozen: Boolean = false
) {
    companion object {
        fun defaultLayers(): List<CadLayer> = listOf(
            CadLayer(id = "layer_0", name = "0", colorHex = 0xFFF8FAFC, lineType = CadLineType.CONTINUOUS, lineWeightMm = 0.35f),
            CadLayer(id = "layer_center", name = "CENTERLINES", colorHex = 0xFFEF4444, lineType = CadLineType.CENTER, lineWeightMm = 0.18f),
            CadLayer(id = "layer_hidden", name = "HIDDEN", colorHex = 0xFFF59E0B, lineType = CadLineType.HIDDEN, lineWeightMm = 0.20f),
            CadLayer(id = "layer_dims", name = "DIMENSIONS", colorHex = 0xFF38BDF8, lineType = CadLineType.CONTINUOUS, lineWeightMm = 0.18f),
            CadLayer(id = "layer_hatch", name = "HATCH", colorHex = 0xFF94A3B8, lineType = CadLineType.CONTINUOUS, lineWeightMm = 0.13f),
            CadLayer(id = "layer_const", name = "CONSTRUCTION", colorHex = 0xFF64748B, lineType = CadLineType.DASHED, lineWeightMm = 0.13f)
        )
    }
}

data class PolylineVertex(
    val position: Point2D,
    val bulge: Double = 0.0 // tan(sweepAngle / 4); 0 = straight segment
)

enum class HatchPattern(val label: String, val dxfName: String) {
    ANSI31("ANSI31 (45° Section)", "ANSI31"),
    ANSI32("ANSI32 (Double Steel)", "ANSI32"),
    ANSI37("ANSI37 (Crosshatch)", "ANSI37"),
    SOLID("SOLID (Filled)", "SOLID"),
    GRID("GRID (Orthogonal)", "NET"),
    DOTS("DOTS (Stipple Matrix)", "DOTS")
}

enum class DimensionType(val label: String) {
    LINEAR_HORIZONTAL("Linear Horizontal"),
    LINEAR_VERTICAL("Linear Vertical"),
    ALIGNED("Aligned"),
    ANGULAR("Angular"),
    RADIUS("Radius"),
    DIAMETER("Diameter"),
    ORDINATE_X("Ordinate X-Datum"),
    ORDINATE_Y("Ordinate Y-Datum")
}

enum class DimensionHierarchyTier(val priority: Int, val label: String) {
    PRIMARY_OVERALL(1, "Primary Overall"),
    SECONDARY_FEATURE(2, "Secondary Feature / Pitch"),
    TERTIARY_LOCAL(3, "Tertiary Local / Offset"),
    GEOMETRIC_CALLOUT(4, "Geometric Diameter / Radius / Angle")
}

sealed class CadEntity {
    abstract val id: String
    abstract val layerName: String
    abstract val colorHexOverride: Long? // null = ByLayer
    abstract val lineTypeOverride: CadLineType // BY_LAYER by default
    abstract val lineWeightMmOverride: Float? // null = ByLayer
    abstract val typeName: String

    abstract fun boundingBox(): BoundingBox2D
    abstract fun translate(dx: Double, dy: Double): CadEntity
    abstract fun rotate(pivot: Point2D, angleDeg: Double): CadEntity
    abstract fun scale(pivot: Point2D, factor: Double): CadEntity
    abstract fun mirror(lineStart: Point2D, lineEnd: Point2D): CadEntity
    abstract fun withNewId(): CadEntity
    abstract fun withStyle(
        layerName: String = this.layerName,
        colorHexOverride: Long? = this.colorHexOverride,
        lineTypeOverride: CadLineType = this.lineTypeOverride,
        lineWeightMmOverride: Float? = this.lineWeightMmOverride
    ): CadEntity

    data class LineEntity(
        override val id: String = UUID.randomUUID().toString(),
        override val layerName: String = "0",
        override val colorHexOverride: Long? = null,
        override val lineTypeOverride: CadLineType = CadLineType.BY_LAYER,
        override val lineWeightMmOverride: Float? = null,
        val start: Point2D,
        val end: Point2D
    ) : CadEntity() {
        override val typeName: String get() = "LINE"
        val length: Double get() = start.distanceTo(end)
        val midpoint: Point2D get() = start.midpointTo(end)
        val angleDeg: Double get() = start.angleToDeg(end)

        override fun boundingBox(): BoundingBox2D = BoundingBox2D(
            minX = min(start.x, end.x),
            minY = min(start.y, end.y),
            maxX = max(start.x, end.x),
            maxY = max(start.y, end.y)
        )

        override fun translate(dx: Double, dy: Double) = copy(
            start = start.offset(dx, dy),
            end = end.offset(dx, dy)
        )

        override fun rotate(pivot: Point2D, angleDeg: Double) = copy(
            start = start.rotateAround(pivot, angleDeg),
            end = end.rotateAround(pivot, angleDeg)
        )

        override fun scale(pivot: Point2D, factor: Double) = copy(
            start = start.scaleFrom(pivot, factor),
            end = end.scaleFrom(pivot, factor)
        )

        override fun mirror(lineStart: Point2D, lineEnd: Point2D) = copy(
            start = start.mirrorAcross(lineStart, lineEnd),
            end = end.mirrorAcross(lineStart, lineEnd)
        )

        override fun withNewId() = copy(id = UUID.randomUUID().toString())
        override fun withStyle(
            layerName: String,
            colorHexOverride: Long?,
            lineTypeOverride: CadLineType,
            lineWeightMmOverride: Float?
        ) = copy(
            layerName = layerName,
            colorHexOverride = colorHexOverride,
            lineTypeOverride = lineTypeOverride,
            lineWeightMmOverride = lineWeightMmOverride
        )
    }

    data class PolylineEntity(
        override val id: String = UUID.randomUUID().toString(),
        override val layerName: String = "0",
        override val colorHexOverride: Long? = null,
        override val lineTypeOverride: CadLineType = CadLineType.BY_LAYER,
        override val lineWeightMmOverride: Float? = null,
        val vertices: List<PolylineVertex>,
        val isClosed: Boolean = false,
        val globalWidth: Double = 0.0
    ) : CadEntity() {
        override val typeName: String get() = "LWPOLYLINE"

        val totalLength: Double
            get() {
                if (vertices.size < 2) return 0.0
                var sum = 0.0
                val count = if (isClosed) vertices.size else vertices.size - 1
                for (i in 0 until count) {
                    val v1 = vertices[i]
                    val v2 = vertices[(i + 1) % vertices.size]
                    val chord = v1.position.distanceTo(v2.position)
                    if (abs(v1.bulge) < 1e-6) {
                        sum += chord
                    } else {
                        val theta = 4.0 * kotlin.math.atan(abs(v1.bulge))
                        val radius = chord / (2.0 * sin(theta * 0.5).coerceAtLeast(1e-6))
                        sum += radius * theta
                    }
                }
                return sum
            }

        override fun boundingBox(): BoundingBox2D {
            if (vertices.isEmpty()) return BoundingBox2D(0.0, 0.0, 0.0, 0.0)
            if (vertices.size == 1) {
                val p = vertices.first().position
                return BoundingBox2D(p.x, p.y, p.x, p.y)
            }
            val pts = vertices.map { it.position }.toMutableList()
            val segCount = if (isClosed) vertices.size else vertices.size - 1
            for (i in 0 until segCount) {
                val v1 = vertices[i]
                val v2 = vertices[(i + 1) % vertices.size]
                if (abs(v1.bulge) >= 1e-6) {
                    val chord = v1.position.distanceTo(v2.position)
                    if (chord > 1e-9) {
                        val sweepRad = 4.0 * kotlin.math.atan(v1.bulge)
                        val sinHalf = sin(abs(sweepRad) * 0.5).coerceAtLeast(1e-8)
                        val radius = abs(chord / (2.0 * sinHalf))
                        val mid = v1.position.midpointTo(v2.position)
                        val distMidToCenter = (chord * 0.25) * (1.0 / v1.bulge - v1.bulge)
                        val chordAngleRad = atan2(v2.position.y - v1.position.y, v2.position.x - v1.position.x)
                        val normalAngleRad = chordAngleRad + PI * 0.5
                        val center = Point2D(
                            mid.x + distMidToCenter * cos(normalAngleRad),
                            mid.y + distMidToCenter * sin(normalAngleRad)
                        )
                        val startAngle = center.angleToDeg(v1.position)
                        val sweepDeg = Math.toDegrees(sweepRad)
                        val arc = ArcEntity(
                            center = center,
                            radius = radius,
                            startAngleDeg = startAngle,
                            sweepAngleDeg = sweepDeg
                        )
                        val arcBox = arc.boundingBox()
                        pts.add(Point2D(arcBox.minX, arcBox.minY))
                        pts.add(Point2D(arcBox.maxX, arcBox.maxY))
                    }
                }
            }
            val baseBox = BoundingBox2D.fromPoints(pts)
            return if (globalWidth > 0.0) {
                val hw = globalWidth * 0.5
                BoundingBox2D(baseBox.minX - hw, baseBox.minY - hw, baseBox.maxX + hw, baseBox.maxY + hw)
            } else {
                baseBox
            }
        }

        override fun translate(dx: Double, dy: Double) = copy(
            vertices = vertices.map { it.copy(position = it.position.offset(dx, dy)) }
        )

        override fun rotate(pivot: Point2D, angleDeg: Double) = copy(
            vertices = vertices.map { it.copy(position = it.position.rotateAround(pivot, angleDeg)) }
        )

        override fun scale(pivot: Point2D, factor: Double) = copy(
            vertices = vertices.map { it.copy(position = it.position.scaleFrom(pivot, factor)) },
            globalWidth = globalWidth * factor
        )

        override fun mirror(lineStart: Point2D, lineEnd: Point2D) = copy(
            vertices = vertices.map {
                it.copy(
                    position = it.position.mirrorAcross(lineStart, lineEnd),
                    bulge = -it.bulge
                )
            }
        )

        override fun withNewId() = copy(id = UUID.randomUUID().toString())
        override fun withStyle(
            layerName: String,
            colorHexOverride: Long?,
            lineTypeOverride: CadLineType,
            lineWeightMmOverride: Float?
        ) = copy(
            layerName = layerName,
            colorHexOverride = colorHexOverride,
            lineTypeOverride = lineTypeOverride,
            lineWeightMmOverride = lineWeightMmOverride
        )
    }

    data class CircleEntity(
        override val id: String = UUID.randomUUID().toString(),
        override val layerName: String = "0",
        override val colorHexOverride: Long? = null,
        override val lineTypeOverride: CadLineType = CadLineType.BY_LAYER,
        override val lineWeightMmOverride: Float? = null,
        val center: Point2D,
        val radius: Double
    ) : CadEntity() {
        override val typeName: String get() = "CIRCLE"
        val diameter: Double get() = radius * 2.0
        val circumference: Double get() = 2.0 * PI * radius
        val area: Double get() = PI * radius * radius

        fun quadrants(): List<Point2D> = listOf(
            Point2D(center.x + radius, center.y),
            Point2D(center.x, center.y + radius),
            Point2D(center.x - radius, center.y),
            Point2D(center.x, center.y - radius)
        )

        override fun boundingBox(): BoundingBox2D = BoundingBox2D(
            minX = center.x - radius,
            minY = center.y - radius,
            maxX = center.x + radius,
            maxY = center.y + radius
        )

        override fun translate(dx: Double, dy: Double) = copy(center = center.offset(dx, dy))
        override fun rotate(pivot: Point2D, angleDeg: Double) = copy(center = center.rotateAround(pivot, angleDeg))
        override fun scale(pivot: Point2D, factor: Double) = copy(
            center = center.scaleFrom(pivot, factor),
            radius = (radius * abs(factor)).coerceAtLeast(1e-4)
        )
        override fun mirror(lineStart: Point2D, lineEnd: Point2D) = copy(
            center = center.mirrorAcross(lineStart, lineEnd)
        )
        override fun withNewId() = copy(id = UUID.randomUUID().toString())
        override fun withStyle(
            layerName: String,
            colorHexOverride: Long?,
            lineTypeOverride: CadLineType,
            lineWeightMmOverride: Float?
        ) = copy(
            layerName = layerName,
            colorHexOverride = colorHexOverride,
            lineTypeOverride = lineTypeOverride,
            lineWeightMmOverride = lineWeightMmOverride
        )
    }

    data class ArcEntity(
        override val id: String = UUID.randomUUID().toString(),
        override val layerName: String = "0",
        override val colorHexOverride: Long? = null,
        override val lineTypeOverride: CadLineType = CadLineType.BY_LAYER,
        override val lineWeightMmOverride: Float? = null,
        val center: Point2D,
        val radius: Double,
        val startAngleDeg: Double,
        val sweepAngleDeg: Double // positive = CCW
    ) : CadEntity() {
        override val typeName: String get() = "ARC"
        val endAngleDeg: Double get() = startAngleDeg + sweepAngleDeg
        val startPoint: Point2D get() = center.polarOffset(radius, startAngleDeg)
        val endPoint: Point2D get() = center.polarOffset(radius, endAngleDeg)
        val midPoint: Point2D get() = center.polarOffset(radius, startAngleDeg + sweepAngleDeg * 0.5)
        val arcLength: Double get() = abs(Math.toRadians(sweepAngleDeg)) * radius

        override fun boundingBox(): BoundingBox2D {
            val pts = mutableListOf(startPoint, endPoint, midPoint)
            for (cardinal in listOf(0.0, 90.0, 180.0, 270.0)) {
                if (containsAngle(cardinal)) {
                    pts.add(center.polarOffset(radius, cardinal))
                }
            }
            return BoundingBox2D.fromPoints(pts)
        }

        fun containsAngle(angleDeg: Double): Boolean {
            val normTarget = ((angleDeg % 360.0) + 360.0) % 360.0
            val normStart = ((startAngleDeg % 360.0) + 360.0) % 360.0
            return if (sweepAngleDeg >= 0.0) {
                val delta = ((normTarget - normStart) + 360.0) % 360.0
                delta <= sweepAngleDeg + 1e-5
            } else {
                val delta = ((normStart - normTarget) + 360.0) % 360.0
                delta <= abs(sweepAngleDeg) + 1e-5
            }
        }

        override fun translate(dx: Double, dy: Double) = copy(center = center.offset(dx, dy))
        override fun rotate(pivot: Point2D, angleDeg: Double) = copy(
            center = center.rotateAround(pivot, angleDeg),
            startAngleDeg = (startAngleDeg + angleDeg) % 360.0
        )
        override fun scale(pivot: Point2D, factor: Double) = copy(
            center = center.scaleFrom(pivot, factor),
            radius = (radius * abs(factor)).coerceAtLeast(1e-4)
        )
        override fun mirror(lineStart: Point2D, lineEnd: Point2D): ArcEntity {
            val newCenter = center.mirrorAcross(lineStart, lineEnd)
            val newStart = startPoint.mirrorAcross(lineStart, lineEnd)
            val newStartAngle = newCenter.angleToDeg(newStart)
            return copy(
                center = newCenter,
                startAngleDeg = newStartAngle,
                sweepAngleDeg = -sweepAngleDeg
            )
        }
        override fun withNewId() = copy(id = UUID.randomUUID().toString())
        override fun withStyle(
            layerName: String,
            colorHexOverride: Long?,
            lineTypeOverride: CadLineType,
            lineWeightMmOverride: Float?
        ) = copy(
            layerName = layerName,
            colorHexOverride = colorHexOverride,
            lineTypeOverride = lineTypeOverride,
            lineWeightMmOverride = lineWeightMmOverride
        )
    }

    data class EllipseEntity(
        override val id: String = UUID.randomUUID().toString(),
        override val layerName: String = "0",
        override val colorHexOverride: Long? = null,
        override val lineTypeOverride: CadLineType = CadLineType.BY_LAYER,
        override val lineWeightMmOverride: Float? = null,
        val center: Point2D,
        val majorRadius: Double,
        val minorRadius: Double,
        val rotationAngleDeg: Double = 0.0
    ) : CadEntity() {
        override val typeName: String get() = "ELLIPSE"

        fun quadrantPoints(): List<Point2D> = listOf(
            Point2D(center.x + majorRadius, center.y).rotateAround(center, rotationAngleDeg),
            Point2D(center.x - majorRadius, center.y).rotateAround(center, rotationAngleDeg),
            Point2D(center.x, center.y + minorRadius).rotateAround(center, rotationAngleDeg),
            Point2D(center.x, center.y - minorRadius).rotateAround(center, rotationAngleDeg)
        )

        override fun boundingBox(): BoundingBox2D = BoundingBox2D.fromPoints(quadrantPoints())

        override fun translate(dx: Double, dy: Double) = copy(center = center.offset(dx, dy))
        override fun rotate(pivot: Point2D, angleDeg: Double) = copy(
            center = center.rotateAround(pivot, angleDeg),
            rotationAngleDeg = (rotationAngleDeg + angleDeg) % 360.0
        )
        override fun scale(pivot: Point2D, factor: Double) = copy(
            center = center.scaleFrom(pivot, factor),
            majorRadius = majorRadius * abs(factor),
            minorRadius = minorRadius * abs(factor)
        )
        override fun mirror(lineStart: Point2D, lineEnd: Point2D): EllipseEntity {
            val newCenter = center.mirrorAcross(lineStart, lineEnd)
            val majPt = center.polarOffset(majorRadius, rotationAngleDeg).mirrorAcross(lineStart, lineEnd)
            return copy(
                center = newCenter,
                rotationAngleDeg = newCenter.angleToDeg(majPt)
            )
        }
        override fun withNewId() = copy(id = UUID.randomUUID().toString())
        override fun withStyle(
            layerName: String,
            colorHexOverride: Long?,
            lineTypeOverride: CadLineType,
            lineWeightMmOverride: Float?
        ) = copy(
            layerName = layerName,
            colorHexOverride = colorHexOverride,
            lineTypeOverride = lineTypeOverride,
            lineWeightMmOverride = lineWeightMmOverride
        )
    }

    data class SplineEntity(
        override val id: String = UUID.randomUUID().toString(),
        override val layerName: String = "0",
        override val colorHexOverride: Long? = null,
        override val lineTypeOverride: CadLineType = CadLineType.BY_LAYER,
        override val lineWeightMmOverride: Float? = null,
        val controlPoints: List<Point2D>,
        val isClosed: Boolean = false
    ) : CadEntity() {
        override val typeName: String get() = "SPLINE"

        fun sampleCurvePoints(samplesPerSegment: Int = 16): List<Point2D> {
            if (controlPoints.size < 2) return controlPoints
            if (controlPoints.size == 2) return controlPoints
            val result = mutableListOf<Point2D>()
            val pts = if (isClosed) {
                listOf(controlPoints.last()) + controlPoints + listOf(controlPoints[0], controlPoints[1])
            } else {
                listOf(controlPoints.first()) + controlPoints + listOf(controlPoints.last())
            }
            for (i in 0 until pts.size - 3) {
                val p0 = pts[i]
                val p1 = pts[i + 1]
                val p2 = pts[i + 2]
                val p3 = pts[i + 3]
                for (step in 0 until samplesPerSegment) {
                    val t = step.toDouble() / samplesPerSegment.toDouble()
                    val t2 = t * t
                    val t3 = t2 * t
                    val x = 0.5 * (
                        (2.0 * p1.x) +
                            (-p0.x + p2.x) * t +
                            (2.0 * p0.x - 5.0 * p1.x + 4.0 * p2.x - p3.x) * t2 +
                            (-p0.x + 3.0 * p1.x - 3.0 * p2.x + p3.x) * t3
                        )
                    val y = 0.5 * (
                        (2.0 * p1.y) +
                            (-p0.y + p2.y) * t +
                            (2.0 * p0.y - 5.0 * p1.y + 4.0 * p2.y - p3.y) * t2 +
                            (-p0.y + 3.0 * p1.y - 3.0 * p2.y + p3.y) * t3
                        )
                    result.add(Point2D(x, y))
                }
            }
            result.add(if (isClosed) controlPoints.first() else controlPoints.last())
            return result
        }

        override fun boundingBox(): BoundingBox2D = BoundingBox2D.fromPoints(sampleCurvePoints(8))

        override fun translate(dx: Double, dy: Double) = copy(
            controlPoints = controlPoints.map { it.offset(dx, dy) }
        )
        override fun rotate(pivot: Point2D, angleDeg: Double) = copy(
            controlPoints = controlPoints.map { it.rotateAround(pivot, angleDeg) }
        )
        override fun scale(pivot: Point2D, factor: Double) = copy(
            controlPoints = controlPoints.map { it.scaleFrom(pivot, factor) }
        )
        override fun mirror(lineStart: Point2D, lineEnd: Point2D) = copy(
            controlPoints = controlPoints.map { it.mirrorAcross(lineStart, lineEnd) }
        )
        override fun withNewId() = copy(id = UUID.randomUUID().toString())
        override fun withStyle(
            layerName: String,
            colorHexOverride: Long?,
            lineTypeOverride: CadLineType,
            lineWeightMmOverride: Float?
        ) = copy(
            layerName = layerName,
            colorHexOverride = colorHexOverride,
            lineTypeOverride = lineTypeOverride,
            lineWeightMmOverride = lineWeightMmOverride
        )
    }

    data class PointEntity(
        override val id: String = UUID.randomUUID().toString(),
        override val layerName: String = "0",
        override val colorHexOverride: Long? = null,
        override val lineTypeOverride: CadLineType = CadLineType.BY_LAYER,
        override val lineWeightMmOverride: Float? = null,
        val position: Point2D
    ) : CadEntity() {
        override val typeName: String get() = "POINT"
        override fun boundingBox(): BoundingBox2D = BoundingBox2D(
            position.x - 0.5, position.y - 0.5, position.x + 0.5, position.y + 0.5
        )
        override fun translate(dx: Double, dy: Double) = copy(position = position.offset(dx, dy))
        override fun rotate(pivot: Point2D, angleDeg: Double) = copy(position = position.rotateAround(pivot, angleDeg))
        override fun scale(pivot: Point2D, factor: Double) = copy(position = position.scaleFrom(pivot, factor))
        override fun mirror(lineStart: Point2D, lineEnd: Point2D) = copy(position = position.mirrorAcross(lineStart, lineEnd))
        override fun withNewId() = copy(id = UUID.randomUUID().toString())
        override fun withStyle(
            layerName: String,
            colorHexOverride: Long?,
            lineTypeOverride: CadLineType,
            lineWeightMmOverride: Float?
        ) = copy(
            layerName = layerName,
            colorHexOverride = colorHexOverride,
            lineTypeOverride = lineTypeOverride,
            lineWeightMmOverride = lineWeightMmOverride
        )
    }

    data class RayEntity(
        override val id: String = UUID.randomUUID().toString(),
        override val layerName: String = "CONSTRUCTION",
        override val colorHexOverride: Long? = null,
        override val lineTypeOverride: CadLineType = CadLineType.BY_LAYER,
        override val lineWeightMmOverride: Float? = null,
        val basePoint: Point2D,
        val angleDeg: Double
    ) : CadEntity() {
        override val typeName: String get() = "RAY"
        val directionPoint: Point2D get() = basePoint.polarOffset(100.0, angleDeg)
        override fun boundingBox(): BoundingBox2D = BoundingBox2D.fromPoints(listOf(basePoint, directionPoint))
        override fun translate(dx: Double, dy: Double) = copy(basePoint = basePoint.offset(dx, dy))
        override fun rotate(pivot: Point2D, angleDeg: Double) = copy(
            basePoint = basePoint.rotateAround(pivot, angleDeg),
            angleDeg = (this.angleDeg + angleDeg) % 360.0
        )
        override fun scale(pivot: Point2D, factor: Double) = copy(basePoint = basePoint.scaleFrom(pivot, factor))
        override fun mirror(lineStart: Point2D, lineEnd: Point2D): RayEntity {
            val p1 = basePoint.mirrorAcross(lineStart, lineEnd)
            val p2 = directionPoint.mirrorAcross(lineStart, lineEnd)
            return copy(basePoint = p1, angleDeg = p1.angleToDeg(p2))
        }
        override fun withNewId() = copy(id = UUID.randomUUID().toString())
        override fun withStyle(
            layerName: String,
            colorHexOverride: Long?,
            lineTypeOverride: CadLineType,
            lineWeightMmOverride: Float?
        ) = copy(
            layerName = layerName,
            colorHexOverride = colorHexOverride,
            lineTypeOverride = lineTypeOverride,
            lineWeightMmOverride = lineWeightMmOverride
        )
    }

    data class XLineEntity(
        override val id: String = UUID.randomUUID().toString(),
        override val layerName: String = "CONSTRUCTION",
        override val colorHexOverride: Long? = null,
        override val lineTypeOverride: CadLineType = CadLineType.BY_LAYER,
        override val lineWeightMmOverride: Float? = null,
        val rootPoint: Point2D,
        val angleDeg: Double
    ) : CadEntity() {
        override val typeName: String get() = "XLINE"
        override fun boundingBox(): BoundingBox2D = BoundingBox2D.fromPoints(
            listOf(rootPoint.polarOffset(-100.0, angleDeg), rootPoint.polarOffset(100.0, angleDeg))
        )
        override fun translate(dx: Double, dy: Double) = copy(rootPoint = rootPoint.offset(dx, dy))
        override fun rotate(pivot: Point2D, angleDeg: Double) = copy(
            rootPoint = rootPoint.rotateAround(pivot, angleDeg),
            angleDeg = (this.angleDeg + angleDeg) % 360.0
        )
        override fun scale(pivot: Point2D, factor: Double) = copy(rootPoint = rootPoint.scaleFrom(pivot, factor))
        override fun mirror(lineStart: Point2D, lineEnd: Point2D): XLineEntity {
            val p1 = rootPoint.mirrorAcross(lineStart, lineEnd)
            val p2 = rootPoint.polarOffset(50.0, angleDeg).mirrorAcross(lineStart, lineEnd)
            return copy(rootPoint = p1, angleDeg = p1.angleToDeg(p2))
        }
        override fun withNewId() = copy(id = UUID.randomUUID().toString())
        override fun withStyle(
            layerName: String,
            colorHexOverride: Long?,
            lineTypeOverride: CadLineType,
            lineWeightMmOverride: Float?
        ) = copy(
            layerName = layerName,
            colorHexOverride = colorHexOverride,
            lineTypeOverride = lineTypeOverride,
            lineWeightMmOverride = lineWeightMmOverride
        )
    }

    data class DonutEntity(
        override val id: String = UUID.randomUUID().toString(),
        override val layerName: String = "0",
        override val colorHexOverride: Long? = null,
        override val lineTypeOverride: CadLineType = CadLineType.BY_LAYER,
        override val lineWeightMmOverride: Float? = null,
        val center: Point2D,
        val innerDiameter: Double,
        val outerDiameter: Double
    ) : CadEntity() {
        override val typeName: String get() = "DONUT"
        val innerRadius: Double get() = innerDiameter * 0.5
        val outerRadius: Double get() = outerDiameter * 0.5
        override fun boundingBox(): BoundingBox2D = BoundingBox2D(
            center.x - outerRadius, center.y - outerRadius,
            center.x + outerRadius, center.y + outerRadius
        )
        override fun translate(dx: Double, dy: Double) = copy(center = center.offset(dx, dy))
        override fun rotate(pivot: Point2D, angleDeg: Double) = copy(center = center.rotateAround(pivot, angleDeg))
        override fun scale(pivot: Point2D, factor: Double) = copy(
            center = center.scaleFrom(pivot, factor),
            innerDiameter = innerDiameter * abs(factor),
            outerDiameter = outerDiameter * abs(factor)
        )
        override fun mirror(lineStart: Point2D, lineEnd: Point2D) = copy(center = center.mirrorAcross(lineStart, lineEnd))
        override fun withNewId() = copy(id = UUID.randomUUID().toString())
        override fun withStyle(
            layerName: String,
            colorHexOverride: Long?,
            lineTypeOverride: CadLineType,
            lineWeightMmOverride: Float?
        ) = copy(
            layerName = layerName,
            colorHexOverride = colorHexOverride,
            lineTypeOverride = lineTypeOverride,
            lineWeightMmOverride = lineWeightMmOverride
        )
    }

    data class HatchEntity(
        override val id: String = UUID.randomUUID().toString(),
        override val layerName: String = "HATCH",
        override val colorHexOverride: Long? = null,
        override val lineTypeOverride: CadLineType = CadLineType.BY_LAYER,
        override val lineWeightMmOverride: Float? = null,
        val boundaryPoints: List<Point2D>,
        val pattern: HatchPattern = HatchPattern.ANSI31,
        val patternScale: Double = 1.0,
        val patternAngleDeg: Double = 0.0
    ) : CadEntity() {
        override val typeName: String get() = "HATCH"
        override fun boundingBox(): BoundingBox2D = BoundingBox2D.fromPoints(boundaryPoints)
        override fun translate(dx: Double, dy: Double) = copy(
            boundaryPoints = boundaryPoints.map { it.offset(dx, dy) }
        )
        override fun rotate(pivot: Point2D, angleDeg: Double) = copy(
            boundaryPoints = boundaryPoints.map { it.rotateAround(pivot, angleDeg) },
            patternAngleDeg = (patternAngleDeg + angleDeg) % 360.0
        )
        override fun scale(pivot: Point2D, factor: Double) = copy(
            boundaryPoints = boundaryPoints.map { it.scaleFrom(pivot, factor) },
            patternScale = patternScale * abs(factor)
        )
        override fun mirror(lineStart: Point2D, lineEnd: Point2D) = copy(
            boundaryPoints = boundaryPoints.map { it.mirrorAcross(lineStart, lineEnd) }
        )
        override fun withNewId() = copy(id = UUID.randomUUID().toString())
        override fun withStyle(
            layerName: String,
            colorHexOverride: Long?,
            lineTypeOverride: CadLineType,
            lineWeightMmOverride: Float?
        ) = copy(
            layerName = layerName,
            colorHexOverride = colorHexOverride,
            lineTypeOverride = lineTypeOverride,
            lineWeightMmOverride = lineWeightMmOverride
        )
    }

    data class TextEntity(
        override val id: String = UUID.randomUUID().toString(),
        override val layerName: String = "DIMENSIONS",
        override val colorHexOverride: Long? = null,
        override val lineTypeOverride: CadLineType = CadLineType.BY_LAYER,
        override val lineWeightMmOverride: Float? = null,
        val position: Point2D,
        val text: String,
        val height: Double = 3.5,
        val rotationDeg: Double = 0.0
    ) : CadEntity() {
        override val typeName: String get() = "MTEXT"
        override fun boundingBox(): BoundingBox2D {
            val lines = text.lines().ifEmpty { listOf(text) }
            val maxChars = lines.maxOfOrNull { it.length } ?: text.length
            val estWidth = (maxChars * height * 0.65).coerceAtLeast(height)
            val totalHeight = (lines.size * height * 1.2).coerceAtLeast(height)
            val localCorners = listOf(
                Point2D(position.x, position.y - totalHeight * 0.8),
                Point2D(position.x + estWidth, position.y - totalHeight * 0.8),
                Point2D(position.x + estWidth, position.y + height * 0.4),
                Point2D(position.x, position.y + height * 0.4)
            )
            val rotatedCorners = if (abs(rotationDeg) > 1e-4) {
                localCorners.map { it.rotateAround(position, rotationDeg) }
            } else {
                localCorners
            }
            return BoundingBox2D.fromPoints(rotatedCorners)
        }
        override fun translate(dx: Double, dy: Double) = copy(position = position.offset(dx, dy))
        override fun rotate(pivot: Point2D, angleDeg: Double) = copy(
            position = position.rotateAround(pivot, angleDeg),
            rotationDeg = (rotationDeg + angleDeg) % 360.0
        )
        override fun scale(pivot: Point2D, factor: Double) = copy(
            position = position.scaleFrom(pivot, factor),
            height = (height * abs(factor)).coerceAtLeast(0.5)
        )
        override fun mirror(lineStart: Point2D, lineEnd: Point2D) = copy(
            // Keep right-reading text (MIRRTEXT = 0 behavior)
            position = position.mirrorAcross(lineStart, lineEnd)
        )
        override fun withNewId() = copy(id = UUID.randomUUID().toString())
        override fun withStyle(
            layerName: String,
            colorHexOverride: Long?,
            lineTypeOverride: CadLineType,
            lineWeightMmOverride: Float?
        ) = copy(
            layerName = layerName,
            colorHexOverride = colorHexOverride,
            lineTypeOverride = lineTypeOverride,
            lineWeightMmOverride = lineWeightMmOverride
        )
    }

    data class LeaderEntity(
        override val id: String = UUID.randomUUID().toString(),
        override val layerName: String = "DIMENSIONS",
        override val colorHexOverride: Long? = null,
        override val lineTypeOverride: CadLineType = CadLineType.BY_LAYER,
        override val lineWeightMmOverride: Float? = null,
        val arrowTip: Point2D,
        val shoulderPoint: Point2D,
        val landingEnd: Point2D,
        val text: String,
        val textHeight: Double = 3.0
    ) : CadEntity() {
        override val typeName: String get() = "MLEADER"
        override fun boundingBox(): BoundingBox2D {
            val shelfMidX = (shoulderPoint.x + landingEnd.x) * 0.5
            val halfTextW = (text.length * textHeight * 0.36).coerceAtLeast(textHeight)
            val topY = max(shoulderPoint.y, landingEnd.y) + textHeight * 1.4
            val botY = min(shoulderPoint.y, landingEnd.y) - textHeight * 0.4
            return BoundingBox2D.fromPoints(
                listOf(
                    arrowTip,
                    shoulderPoint,
                    landingEnd,
                    Point2D(shelfMidX - halfTextW, botY),
                    Point2D(shelfMidX + halfTextW, topY)
                )
            )
        }
        override fun translate(dx: Double, dy: Double) = copy(
            arrowTip = arrowTip.offset(dx, dy),
            shoulderPoint = shoulderPoint.offset(dx, dy),
            landingEnd = landingEnd.offset(dx, dy)
        )
        override fun rotate(pivot: Point2D, angleDeg: Double) = copy(
            arrowTip = arrowTip.rotateAround(pivot, angleDeg),
            shoulderPoint = shoulderPoint.rotateAround(pivot, angleDeg),
            landingEnd = landingEnd.rotateAround(pivot, angleDeg)
        )
        override fun scale(pivot: Point2D, factor: Double) = copy(
            arrowTip = arrowTip.scaleFrom(pivot, factor),
            shoulderPoint = shoulderPoint.scaleFrom(pivot, factor),
            landingEnd = landingEnd.scaleFrom(pivot, factor),
            textHeight = textHeight * abs(factor)
        )
        override fun mirror(lineStart: Point2D, lineEnd: Point2D) = copy(
            arrowTip = arrowTip.mirrorAcross(lineStart, lineEnd),
            shoulderPoint = shoulderPoint.mirrorAcross(lineStart, lineEnd),
            landingEnd = landingEnd.mirrorAcross(lineStart, lineEnd)
        )
        override fun withNewId() = copy(id = UUID.randomUUID().toString())
        override fun withStyle(
            layerName: String,
            colorHexOverride: Long?,
            lineTypeOverride: CadLineType,
            lineWeightMmOverride: Float?
        ) = copy(
            layerName = layerName,
            colorHexOverride = colorHexOverride,
            lineTypeOverride = lineTypeOverride,
            lineWeightMmOverride = lineWeightMmOverride
        )
    }

    data class CenterMarkEntity(
        override val id: String = UUID.randomUUID().toString(),
        override val layerName: String = "CENTERLINES",
        override val colorHexOverride: Long? = null,
        override val lineTypeOverride: CadLineType = CadLineType.CENTER,
        override val lineWeightMmOverride: Float? = null,
        val center: Point2D,
        val radius: Double,
        val sourceEntityId: String? = null
    ) : CadEntity() {
        override val typeName: String get() = "CENTERMARK"
        override fun boundingBox(): BoundingBox2D {
            val ext = radius * 1.25
            return BoundingBox2D(center.x - ext, center.y - ext, center.x + ext, center.y + ext)
        }
        override fun translate(dx: Double, dy: Double) = copy(center = center.offset(dx, dy))
        override fun rotate(pivot: Point2D, angleDeg: Double) = copy(center = center.rotateAround(pivot, angleDeg))
        override fun scale(pivot: Point2D, factor: Double) = copy(
            center = center.scaleFrom(pivot, factor),
            radius = radius * abs(factor)
        )
        override fun mirror(lineStart: Point2D, lineEnd: Point2D) = copy(center = center.mirrorAcross(lineStart, lineEnd))
        override fun withNewId() = copy(id = UUID.randomUUID().toString())
        override fun withStyle(
            layerName: String,
            colorHexOverride: Long?,
            lineTypeOverride: CadLineType,
            lineWeightMmOverride: Float?
        ) = copy(
            layerName = layerName,
            colorHexOverride = colorHexOverride,
            lineTypeOverride = lineTypeOverride,
            lineWeightMmOverride = lineWeightMmOverride
        )
    }

    data class BlockInsertEntity(
        override val id: String = UUID.randomUUID().toString(),
        override val layerName: String = "0",
        override val colorHexOverride: Long? = null,
        override val lineTypeOverride: CadLineType = CadLineType.BY_LAYER,
        override val lineWeightMmOverride: Float? = null,
        val blockName: String,
        val insertionPoint: Point2D,
        val scaleFactor: Double = 1.0,
        val rotationDeg: Double = 0.0,
        val attributes: Map<String, String> = emptyMap()
    ) : CadEntity() {
        override val typeName: String get() = "INSERT"
        override fun boundingBox(): BoundingBox2D = BoundingBox2D(
            insertionPoint.x - 10.0 * scaleFactor,
            insertionPoint.y - 10.0 * scaleFactor,
            insertionPoint.x + 10.0 * scaleFactor,
            insertionPoint.y + 10.0 * scaleFactor
        )

        fun boundingBox(blocks: Map<String, BlockDefinition>): BoundingBox2D {
            val def = blocks[blockName] ?: return boundingBox()
            if (def.entities.isEmpty()) return boundingBox()
            val transformedBoxes = def.entities.map { child ->
                child
                    .translate(-def.basePoint.x, -def.basePoint.y)
                    .scale(Point2D(0.0, 0.0), scaleFactor)
                    .rotate(Point2D(0.0, 0.0), rotationDeg)
                    .translate(insertionPoint.x, insertionPoint.y)
                    .boundingBox()
            }
            return transformedBoxes.reduce { a, b -> a.union(b) }
        }
        override fun translate(dx: Double, dy: Double) = copy(insertionPoint = insertionPoint.offset(dx, dy))
        override fun rotate(pivot: Point2D, angleDeg: Double) = copy(
            insertionPoint = insertionPoint.rotateAround(pivot, angleDeg),
            rotationDeg = (rotationDeg + angleDeg) % 360.0
        )
        override fun scale(pivot: Point2D, factor: Double) = copy(
            insertionPoint = insertionPoint.scaleFrom(pivot, factor),
            scaleFactor = scaleFactor * abs(factor)
        )
        override fun mirror(lineStart: Point2D, lineEnd: Point2D) = copy(
            insertionPoint = insertionPoint.mirrorAcross(lineStart, lineEnd)
        )
        override fun withNewId() = copy(id = UUID.randomUUID().toString())
        override fun withStyle(
            layerName: String,
            colorHexOverride: Long?,
            lineTypeOverride: CadLineType,
            lineWeightMmOverride: Float?
        ) = copy(
            layerName = layerName,
            colorHexOverride = colorHexOverride,
            lineTypeOverride = lineTypeOverride,
            lineWeightMmOverride = lineWeightMmOverride
        )
    }

    data class DimensionEntity(
        override val id: String = UUID.randomUUID().toString(),
        override val layerName: String = "DIMENSIONS",
        override val colorHexOverride: Long? = null,
        override val lineTypeOverride: CadLineType = CadLineType.CONTINUOUS,
        override val lineWeightMmOverride: Float? = null,
        val dimensionType: DimensionType,
        val anchor1: Point2D,
        val anchor2: Point2D,
        val dimensionLinePoint: Point2D, // Controls offset or leader landing
        val angularVertex: Point2D? = null,
        val customPrefix: String = "", // e.g., "4× " for hole patterns
        val toleranceText: String = "", // e.g., "±0.05"
        val textOverride: String? = null,
        val sourceEntityIds: List<String> = emptyList(),
        val isAutoGenerated: Boolean = false,
        val hierarchyTier: DimensionHierarchyTier = DimensionHierarchyTier.PRIMARY_OVERALL
    ) : CadEntity() {
        override val typeName: String get() = "DIMENSION"

        fun measuredModelValue(): Double = when (dimensionType) {
            DimensionType.LINEAR_HORIZONTAL -> abs(anchor2.x - anchor1.x)
            DimensionType.LINEAR_VERTICAL -> abs(anchor2.y - anchor1.y)
            DimensionType.ALIGNED -> anchor1.distanceTo(anchor2)
            DimensionType.RADIUS -> anchor1.distanceTo(anchor2)
            DimensionType.DIAMETER -> anchor1.distanceTo(anchor2) * 2.0
            DimensionType.ORDINATE_X -> anchor1.x
            DimensionType.ORDINATE_Y -> anchor1.y
            DimensionType.ANGULAR -> {
                val v = angularVertex ?: anchor1
                val a1 = v.angleToDeg(anchor1)
                val a2 = v.angleToDeg(anchor2)
                val diff = abs(a2 - a1) % 360.0
                if (diff > 180.0) 360.0 - diff else diff
            }
        }

        fun formattedLabel(
            precision: PrecisionFormatConfig,
            storageUnit: CadUnit,
            displayUnit: CadUnit
        ): String {
            if (!textOverride.isNullOrBlank()) return textOverride
            val value = measuredModelValue()
            val core = when (dimensionType) {
                DimensionType.RADIUS -> "R" + precision.formatLinear(value, storageUnit, displayUnit)
                DimensionType.DIAMETER -> "⌀" + precision.formatLinear(value, storageUnit, displayUnit)
                DimensionType.ANGULAR -> precision.formatAngle(value)
                DimensionType.ORDINATE_X -> "X=" + precision.formatLinear(value, storageUnit, displayUnit)
                DimensionType.ORDINATE_Y -> "Y=" + precision.formatLinear(value, storageUnit, displayUnit)
                else -> precision.formatLinear(value, storageUnit, displayUnit)
            }
            val withPrefix = if (customPrefix.isNotBlank()) "$customPrefix$core" else core
            return if (toleranceText.isNotBlank()) "$withPrefix ($toleranceText)" else withPrefix
        }

        override fun boundingBox(): BoundingBox2D {
            val pts = mutableListOf(anchor1, anchor2, dimensionLinePoint)
            angularVertex?.let { pts.add(it) }
            val estCharCount = (textOverride?.length ?: 8) + customPrefix.length + toleranceText.length
            val estTextHalfW = (estCharCount * 1.35).coerceIn(4.0, 22.0)
            val estTextPadH = 4.0

            when (dimensionType) {
                DimensionType.LINEAR_HORIZONTAL -> {
                    val x1 = min(anchor1.x, anchor2.x)
                    val x2 = max(anchor1.x, anchor2.x)
                    val dimY = dimensionLinePoint.y
                    val overshoot = if (dimY >= max(anchor1.y, anchor2.y)) 2.5 else -2.5
                    pts.add(Point2D(x1, dimY + overshoot))
                    pts.add(Point2D(x2, dimY + overshoot))
                    val midX = (x1 + x2) * 0.5
                    pts.add(Point2D(midX - estTextHalfW, dimY - estTextPadH))
                    pts.add(Point2D(midX + estTextHalfW, dimY + estTextPadH))
                }
                DimensionType.LINEAR_VERTICAL -> {
                    val y1 = min(anchor1.y, anchor2.y)
                    val y2 = max(anchor1.y, anchor2.y)
                    val dimX = dimensionLinePoint.x
                    val overshoot = if (dimX >= max(anchor1.x, anchor2.x)) 2.5 else -2.5
                    pts.add(Point2D(dimX + overshoot, y1))
                    pts.add(Point2D(dimX + overshoot, y2))
                    val midY = (y1 + y2) * 0.5
                    pts.add(Point2D(dimX - estTextPadH, midY - estTextHalfW))
                    pts.add(Point2D(dimX + estTextPadH, midY + estTextHalfW))
                }
                DimensionType.RADIUS, DimensionType.DIAMETER -> {
                    val center = anchor1
                    val radiusModel = center.distanceTo(anchor2).coerceAtLeast(0.5)
                    val rayAngleDeg = if (center.distanceTo(anchor2) > 1e-4) {
                        center.angleToDeg(anchor2)
                    } else {
                        center.angleToDeg(dimensionLinePoint)
                    }
                    val rimContact = center.polarOffset(radiusModel, rayAngleDeg)
                    pts.add(rimContact)
                    if (dimensionType == DimensionType.DIAMETER) {
                        pts.add(center.polarOffset(radiusModel, rayAngleDeg + 180.0))
                    }
                    val shoulderDist = max(radiusModel + 6.0, center.distanceTo(dimensionLinePoint))
                    val shoulderPt = center.polarOffset(shoulderDist, rayAngleDeg)
                    val shelfDir = if (cos(Math.toRadians(rayAngleDeg)) >= -0.05) 1.0 else -1.0
                    val shelfLen = (estTextHalfW * 2.1).coerceAtLeast(8.0)
                    val landingEndPt = shoulderPt.offset(shelfDir * shelfLen, 0.0)
                    pts.add(shoulderPt)
                    pts.add(landingEndPt)
                    pts.add(Point2D(min(shoulderPt.x, landingEndPt.x) - 2.0, shoulderPt.y - 2.0))
                    pts.add(Point2D(max(shoulderPt.x, landingEndPt.x) + 2.0, shoulderPt.y + estTextPadH * 1.4))
                }
                DimensionType.ANGULAR -> {
                    val v = angularVertex ?: anchor1
                    val r = max(v.distanceTo(dimensionLinePoint), max(v.distanceTo(anchor1), v.distanceTo(anchor2)) * 0.65)
                    val a1 = v.angleToDeg(anchor1)
                    val a2 = v.angleToDeg(anchor2)
                    var sweep = ((a2 - a1) + 360.0) % 360.0
                    val startAng = if (sweep > 180.0) {
                        sweep = 360.0 - sweep
                        a2
                    } else a1
                    val arc = ArcEntity(center = v, radius = r + 3.0, startAngleDeg = startAng, sweepAngleDeg = sweep)
                    val ab = arc.boundingBox()
                    pts.add(Point2D(ab.minX, ab.minY))
                    pts.add(Point2D(ab.maxX, ab.maxY))
                }
                else -> {
                    val mid = anchor1.midpointTo(anchor2)
                    val offX = dimensionLinePoint.x - mid.x
                    val offY = dimensionLinePoint.y - mid.y
                    val d1 = anchor1.offset(offX, offY)
                    val d2 = anchor2.offset(offX, offY)
                    pts.add(d1)
                    pts.add(d2)
                    pts.add(Point2D(dimensionLinePoint.x - estTextHalfW, dimensionLinePoint.y - estTextPadH))
                    pts.add(Point2D(dimensionLinePoint.x + estTextHalfW, dimensionLinePoint.y + estTextPadH))
                }
            }
            return BoundingBox2D.fromPoints(pts)
        }

        override fun translate(dx: Double, dy: Double) = copy(
            anchor1 = anchor1.offset(dx, dy),
            anchor2 = anchor2.offset(dx, dy),
            dimensionLinePoint = dimensionLinePoint.offset(dx, dy),
            angularVertex = angularVertex?.offset(dx, dy)
        )

        override fun rotate(pivot: Point2D, angleDeg: Double) = copy(
            anchor1 = anchor1.rotateAround(pivot, angleDeg),
            anchor2 = anchor2.rotateAround(pivot, angleDeg),
            dimensionLinePoint = dimensionLinePoint.rotateAround(pivot, angleDeg),
            angularVertex = angularVertex?.rotateAround(pivot, angleDeg)
        )

        override fun scale(pivot: Point2D, factor: Double) = copy(
            anchor1 = anchor1.scaleFrom(pivot, factor),
            anchor2 = anchor2.scaleFrom(pivot, factor),
            dimensionLinePoint = dimensionLinePoint.scaleFrom(pivot, factor),
            angularVertex = angularVertex?.scaleFrom(pivot, factor)
        )

        override fun mirror(lineStart: Point2D, lineEnd: Point2D) = copy(
            anchor1 = anchor1.mirrorAcross(lineStart, lineEnd),
            anchor2 = anchor2.mirrorAcross(lineStart, lineEnd),
            dimensionLinePoint = dimensionLinePoint.mirrorAcross(lineStart, lineEnd),
            angularVertex = angularVertex?.mirrorAcross(lineStart, lineEnd)
        )

        override fun withNewId() = copy(id = UUID.randomUUID().toString())
        override fun withStyle(
            layerName: String,
            colorHexOverride: Long?,
            lineTypeOverride: CadLineType,
            lineWeightMmOverride: Float?
        ) = copy(
            layerName = layerName,
            colorHexOverride = colorHexOverride,
            lineTypeOverride = lineTypeOverride,
            lineWeightMmOverride = lineWeightMmOverride
        )
    }
}

data class BlockDefinition(
    val name: String,
    val basePoint: Point2D,
    val entities: List<CadEntity>,
    val defaultAttributes: Map<String, String> = emptyMap()
)

enum class ConstraintType(val label: String, val badge: String) {
    HORIZONTAL("Horizontal", "H"),
    VERTICAL("Vertical", "V"),
    PARALLEL("Parallel", "∥"),
    PERPENDICULAR("Perpendicular", "⊥"),
    EQUAL("Equal Length/Radius", "="),
    CONCENTRIC("Concentric", "◎"),
    TANGENT("Tangent", "T"),
    COINCIDENT("Coincident", "●"),
    FIXED("Fixed Position", "🔒")
}

data class GeometricConstraintRecord(
    val id: String = UUID.randomUUID().toString(),
    val type: ConstraintType,
    val primaryEntityId: String,
    val secondaryEntityId: String? = null
)
