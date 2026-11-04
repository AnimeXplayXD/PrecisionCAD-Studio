package com.precisioncad.studio.cad.kernel

import com.precisioncad.studio.cad.model.BoundingBox2D
import com.precisioncad.studio.cad.model.CadEntity
import com.precisioncad.studio.cad.model.ConstraintType
import com.precisioncad.studio.cad.model.GeometricConstraintRecord
import com.precisioncad.studio.cad.model.Point2D
import com.precisioncad.studio.cad.model.PolylineVertex
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure, deterministic double-precision 2D CAD Geometry Kernel.
 * Completely independent of Android UI / Viewport rendering.
 */
object CadGeometryKernel {

    const val EPSILON = 1e-6

    // --- Point-to-Segment & Point-to-Entity Distance ---

    fun projectPointOnSegment(pt: Point2D, a: Point2D, b: Point2D): Point2D {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val lenSq = dx * dx + dy * dy
        if (lenSq < 1e-12) return a
        val t = (((pt.x - a.x) * dx + (pt.y - a.y) * dy) / lenSq).coerceIn(0.0, 1.0)
        return Point2D(a.x + t * dx, a.y + t * dy)
    }

    fun projectPointOnInfiniteLine(pt: Point2D, a: Point2D, b: Point2D): Point2D {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val lenSq = dx * dx + dy * dy
        if (lenSq < 1e-12) return a
        val t = ((pt.x - a.x) * dx + (pt.y - a.y) * dy) / lenSq
        return Point2D(a.x + t * dx, a.y + t * dy)
    }

    fun distancePointToSegment(pt: Point2D, a: Point2D, b: Point2D): Double =
        pt.distanceTo(projectPointOnSegment(pt, a, b))

    fun closestPointOnEntity(pt: Point2D, entity: CadEntity): Point2D = when (entity) {
        is CadEntity.LineEntity -> projectPointOnSegment(pt, entity.start, entity.end)
        is CadEntity.PolylineEntity -> {
            val segs = explodePolylineToPrimitives(entity)
            segs.map { closestPointOnEntity(pt, it) }
                .minByOrNull { it.distanceTo(pt) } ?: pt
        }
        is CadEntity.CircleEntity -> {
            val angle = entity.center.angleToDeg(pt)
            entity.center.polarOffset(entity.radius, angle)
        }
        is CadEntity.ArcEntity -> {
            val angle = entity.center.angleToDeg(pt)
            if (entity.containsAngle(angle)) {
                entity.center.polarOffset(entity.radius, angle)
            } else {
                val pStart = entity.startPoint
                val pEnd = entity.endPoint
                if (pt.distanceTo(pStart) <= pt.distanceTo(pEnd)) pStart else pEnd
            }
        }
        is CadEntity.EllipseEntity -> {
            val samples = sampleEllipsePoints(entity, 48)
            samples.zipWithNext()
                .map { (a, b) -> projectPointOnSegment(pt, a, b) }
                .minByOrNull { it.distanceTo(pt) } ?: entity.center
        }
        is CadEntity.SplineEntity -> {
            val samples = entity.sampleCurvePoints(12)
            if (samples.size < 2) samples.firstOrNull() ?: pt
            else samples.zipWithNext()
                .map { (a, b) -> projectPointOnSegment(pt, a, b) }
                .minByOrNull { it.distanceTo(pt) } ?: pt
        }
        is CadEntity.PointEntity -> entity.position
        is CadEntity.RayEntity -> {
            val b = entity.basePoint.polarOffset(10000.0, entity.angleDeg)
            projectPointOnSegment(pt, entity.basePoint, b)
        }
        is CadEntity.XLineEntity -> {
            val a = entity.rootPoint.polarOffset(-10000.0, entity.angleDeg)
            val b = entity.rootPoint.polarOffset(10000.0, entity.angleDeg)
            projectPointOnInfiniteLine(pt, a, b)
        }
        is CadEntity.DonutEntity -> {
            val angle = entity.center.angleToDeg(pt)
            val d = entity.center.distanceTo(pt)
            val r = if (abs(d - entity.innerRadius) < abs(d - entity.outerRadius)) entity.innerRadius else entity.outerRadius
            entity.center.polarOffset(r, angle)
        }
        is CadEntity.HatchEntity -> {
            if (entity.boundaryPoints.size < 2) entity.boundaryPoints.firstOrNull() ?: pt
            else (entity.boundaryPoints + entity.boundaryPoints.first()).zipWithNext()
                .map { (a, b) -> projectPointOnSegment(pt, a, b) }
                .minByOrNull { it.distanceTo(pt) } ?: pt
        }
        is CadEntity.TextEntity -> entity.position
        is CadEntity.LeaderEntity -> projectPointOnSegment(pt, entity.arrowTip, entity.shoulderPoint)
        is CadEntity.CenterMarkEntity -> entity.center
        is CadEntity.BlockInsertEntity -> entity.insertionPoint
        is CadEntity.DimensionEntity -> projectPointOnSegment(pt, entity.anchor1, entity.dimensionLinePoint)
    }

    fun distanceToEntity(pt: Point2D, entity: CadEntity): Double =
        pt.distanceTo(closestPointOnEntity(pt, entity))

    // --- Analytical Intersections ---

    fun intersectInfiniteLines(a1: Point2D, a2: Point2D, b1: Point2D, b2: Point2D): Point2D? {
        val x1 = a1.x; val y1 = a1.y
        val x2 = a2.x; val y2 = a2.y
        val x3 = b1.x; val y3 = b1.y
        val x4 = b2.x; val y4 = b2.y
        val denom = (x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4)
        if (abs(denom) < 1e-10) return null
        val px = ((x1 * y2 - y1 * x2) * (x3 - x4) - (x1 - x2) * (x3 * y4 - y3 * x4)) / denom
        val py = ((x1 * y2 - y1 * x2) * (y3 - y4) - (y1 - y2) * (x3 * y4 - y3 * x4)) / denom
        return Point2D(px, py)
    }

    fun intersectSegments(a1: Point2D, a2: Point2D, b1: Point2D, b2: Point2D, onSegmentOnly: Boolean = true): Point2D? {
        val pt = intersectInfiniteLines(a1, a2, b1, b2) ?: return null
        if (!onSegmentOnly) return pt
        val onA = distancePointToSegment(pt, a1, a2) <= 1e-4
        val onB = distancePointToSegment(pt, b1, b2) <= 1e-4
        return if (onA && onB) pt else null
    }

    fun intersectLineAndCircle(
        lineStart: Point2D,
        lineEnd: Point2D,
        center: Point2D,
        radius: Double,
        clampToSegment: Boolean = true
    ): List<Point2D> {
        val dx = lineEnd.x - lineStart.x
        val dy = lineEnd.y - lineStart.y
        val fx = lineStart.x - center.x
        val fy = lineStart.y - center.y
        val a = dx * dx + dy * dy
        if (a < 1e-12) return emptyList()
        val b = 2.0 * (fx * dx + fy * dy)
        val c = fx * fx + fy * fy - radius * radius
        var discriminant = b * b - 4.0 * a * c
        if (discriminant < -1e-7) return emptyList()
        if (discriminant < 0.0) discriminant = 0.0
        val sqrtD = sqrt(discriminant)
        val tCandidates = if (sqrtD < 1e-7) listOf(-b / (2.0 * a)) else listOf(
            (-b - sqrtD) / (2.0 * a),
            (-b + sqrtD) / (2.0 * a)
        )
        return tCandidates.filter { t ->
            !clampToSegment || (t >= -1e-5 && t <= 1.0 + 1e-5)
        }.map { t ->
            Point2D(lineStart.x + t * dx, lineStart.y + t * dy)
        }
    }

    fun intersectCircles(c1: Point2D, r1: Double, c2: Point2D, r2: Double): List<Point2D> {
        val d = c1.distanceTo(c2)
        if (d > r1 + r2 + 1e-6 || d < abs(r1 - r2) - 1e-6 || d < 1e-9) return emptyList()
        val a = (r1 * r1 - r2 * r2 + d * d) / (2.0 * d)
        val hSq = (r1 * r1 - a * a).coerceAtLeast(0.0)
        val h = sqrt(hSq)
        val x2 = c1.x + a * (c2.x - c1.x) / d
        val y2 = c1.y + a * (c2.y - c1.y) / d
        if (h < 1e-6) return listOf(Point2D(x2, y2))
        val rx = -(c2.y - c1.y) * (h / d)
        val ry = (c2.x - c1.x) * (h / d)
        return listOf(Point2D(x2 + rx, y2 + ry), Point2D(x2 - rx, y2 - ry))
    }

    fun intersectEntities(e1: CadEntity, e2: CadEntity, apparentIntersection: Boolean = false): List<Point2D> {
        if (e1.id == e2.id) return emptyList()
        val prims1 = decomposeToCurvePrimitives(e1)
        val prims2 = decomposeToCurvePrimitives(e2)
        val results = mutableListOf<Point2D>()
        for (p1 in prims1) {
            for (p2 in prims2) {
                results.addAll(intersectPrimitives(p1, p2, apparentIntersection))
            }
        }
        return deduplicatePoints(results)
    }

    private fun decomposeToCurvePrimitives(entity: CadEntity): List<CadEntity> = when (entity) {
        is CadEntity.PolylineEntity -> explodePolylineToPrimitives(entity)
        is CadEntity.RayEntity -> listOf(
            CadEntity.LineEntity(
                start = entity.basePoint,
                end = entity.basePoint.polarOffset(10000.0, entity.angleDeg)
            )
        )
        is CadEntity.XLineEntity -> listOf(
            CadEntity.LineEntity(
                start = entity.rootPoint.polarOffset(-10000.0, entity.angleDeg),
                end = entity.rootPoint.polarOffset(10000.0, entity.angleDeg)
            )
        )
        else -> listOf(entity)
    }

    private fun intersectPrimitives(p1: CadEntity, p2: CadEntity, apparent: Boolean): List<Point2D> = when {
        p1 is CadEntity.LineEntity && p2 is CadEntity.LineEntity ->
            listOfNotNull(intersectSegments(p1.start, p1.end, p2.start, p2.end, onSegmentOnly = !apparent))

        p1 is CadEntity.LineEntity && p2 is CadEntity.CircleEntity ->
            intersectLineAndCircle(p1.start, p1.end, p2.center, p2.radius, clampToSegment = !apparent)

        p1 is CadEntity.CircleEntity && p2 is CadEntity.LineEntity ->
            intersectLineAndCircle(p2.start, p2.end, p1.center, p1.radius, clampToSegment = !apparent)

        p1 is CadEntity.LineEntity && p2 is CadEntity.ArcEntity ->
            intersectLineAndCircle(p1.start, p1.end, p2.center, p2.radius, clampToSegment = !apparent)
                .filter { apparent || p2.containsAngle(p2.center.angleToDeg(it)) }

        p1 is CadEntity.ArcEntity && p2 is CadEntity.LineEntity ->
            intersectLineAndCircle(p2.start, p2.end, p1.center, p1.radius, clampToSegment = !apparent)
                .filter { apparent || p1.containsAngle(p1.center.angleToDeg(it)) }

        p1 is CadEntity.CircleEntity && p2 is CadEntity.CircleEntity ->
            intersectCircles(p1.center, p1.radius, p2.center, p2.radius)

        p1 is CadEntity.CircleEntity && p2 is CadEntity.ArcEntity ->
            intersectCircles(p1.center, p1.radius, p2.center, p2.radius)
                .filter { apparent || p2.containsAngle(p2.center.angleToDeg(it)) }

        p1 is CadEntity.ArcEntity && p2 is CadEntity.CircleEntity ->
            intersectCircles(p1.center, p1.radius, p2.center, p2.radius)
                .filter { apparent || p1.containsAngle(p1.center.angleToDeg(it)) }

        p1 is CadEntity.ArcEntity && p2 is CadEntity.ArcEntity ->
            intersectCircles(p1.center, p1.radius, p2.center, p2.radius)
                .filter {
                    apparent || (p1.containsAngle(p1.center.angleToDeg(it)) && p2.containsAngle(p2.center.angleToDeg(it)))
                }

        else -> emptyList()
    }

    fun deduplicatePoints(points: List<Point2D>, tolerance: Double = 1e-4): List<Point2D> {
        val unique = mutableListOf<Point2D>()
        for (p in points) {
            if (unique.none { it.distanceTo(p) <= tolerance }) {
                unique.add(p)
            }
        }
        return unique
    }

    // --- Circle / Arc / Polygon / Rectangle Constructions ---

    fun circleFrom3Points(p1: Point2D, p2: Point2D, p3: Point2D): CadEntity.CircleEntity? {
        val temp = p2.x * p2.x + p2.y * p2.y
        val bc = (p1.x * p1.x + p1.y * p1.y - temp) / 2.0
        val cd = (temp - p3.x * p3.x - p3.y * p3.y) / 2.0
        val det = (p1.x - p2.x) * (p2.y - p3.y) - (p2.x - p3.x) * (p1.y - p2.y)
        if (abs(det) < 1e-8) return null // Collinear points
        val cx = (bc * (p2.y - p3.y) - cd * (p1.y - p2.y)) / det
        val cy = ((p1.x - p2.x) * cd - (p2.x - p3.x) * bc) / det
        val center = Point2D(cx, cy)
        val radius = center.distanceTo(p1)
        return CadEntity.CircleEntity(center = center, radius = radius)
    }

    fun circleTanTanRadius(
        line1: CadEntity.LineEntity,
        line2: CadEntity.LineEntity,
        radius: Double,
        hintPoint: Point2D
    ): CadEntity.CircleEntity? {
        if (radius <= 0.0) return null
        val intersection = intersectInfiniteLines(line1.start, line1.end, line2.start, line2.end) ?: return null
        val u1x = (line1.end.x - line1.start.x) / line1.length.coerceAtLeast(1e-9)
        val u1y = (line1.end.y - line1.start.y) / line1.length.coerceAtLeast(1e-9)
        val u2x = (line2.end.x - line2.start.x) / line2.length.coerceAtLeast(1e-9)
        val u2y = (line2.end.y - line2.start.y) / line2.length.coerceAtLeast(1e-9)

        val candidates = mutableListOf<Point2D>()
        for (s1 in listOf(-1.0, 1.0)) {
            for (s2 in listOf(-1.0, 1.0)) {
                val n1 = Point2D(-u1y * s1, u1x * s1)
                val n2 = Point2D(-u2y * s2, u2x * s2)
                val off1A = line1.start.offset(n1.x * radius, n1.y * radius)
                val off1B = line1.end.offset(n1.x * radius, n1.y * radius)
                val off2A = line2.start.offset(n2.x * radius, n2.y * radius)
                val off2B = line2.end.offset(n2.x * radius, n2.y * radius)
                intersectInfiniteLines(off1A, off1B, off2A, off2B)?.let { candidates.add(it) }
            }
        }
        val bestCenter = candidates.minByOrNull { it.distanceTo(hintPoint) } ?: intersection
        return CadEntity.CircleEntity(center = bestCenter, radius = radius)
    }

    fun arcFrom3Points(p1: Point2D, p2: Point2D, p3: Point2D): CadEntity.ArcEntity? {
        val circle = circleFrom3Points(p1, p2, p3) ?: return null
        val a1 = circle.center.angleToDeg(p1)
        val a2 = circle.center.angleToDeg(p2)
        val a3 = circle.center.angleToDeg(p3)
        val ccwSweepTo2 = ((a2 - a1) + 360.0) % 360.0
        val ccwSweepTo3 = ((a3 - a1) + 360.0) % 360.0
        val sweep = if (ccwSweepTo2 <= ccwSweepTo3) ccwSweepTo3 else -(360.0 - ccwSweepTo3)
        return CadEntity.ArcEntity(
            center = circle.center,
            radius = circle.radius,
            startAngleDeg = a1,
            sweepAngleDeg = sweep
        )
    }

    fun createRectanglePolyline(
        corner1: Point2D,
        corner2: Point2D,
        filletRadius: Double = 0.0,
        chamferDistance: Double = 0.0,
        layerName: String = "0"
    ): CadEntity.PolylineEntity {
        val minX = min(corner1.x, corner2.x)
        val maxX = max(corner1.x, corner2.x)
        val minY = min(corner1.y, corner2.y)
        val maxY = max(corner1.y, corner2.y)
        val w = maxX - minX
        val h = maxY - minY
        val maxCorner = min(w, h) * 0.49
        val r = filletRadius.coerceIn(0.0, maxCorner)
        val ch = chamferDistance.coerceIn(0.0, maxCorner)

        val vertices = when {
            r > 1e-4 -> {
                val bulge90 = kotlin.math.tan(PI / 8.0) // 90 deg CCW arc bulge
                listOf(
                    PolylineVertex(Point2D(minX + r, minY), 0.0),
                    PolylineVertex(Point2D(maxX - r, minY), bulge90),
                    PolylineVertex(Point2D(maxX, minY + r), 0.0),
                    PolylineVertex(Point2D(maxX, maxY - r), bulge90),
                    PolylineVertex(Point2D(maxX - r, maxY), 0.0),
                    PolylineVertex(Point2D(minX + r, maxY), bulge90),
                    PolylineVertex(Point2D(minX, maxY - r), 0.0),
                    PolylineVertex(Point2D(minX, minY + r), bulge90)
                )
            }
            ch > 1e-4 -> listOf(
                PolylineVertex(Point2D(minX + ch, minY)),
                PolylineVertex(Point2D(maxX - ch, minY)),
                PolylineVertex(Point2D(maxX, minY + ch)),
                PolylineVertex(Point2D(maxX, maxY - ch)),
                PolylineVertex(Point2D(maxX - ch, maxY)),
                PolylineVertex(Point2D(minX + ch, maxY)),
                PolylineVertex(Point2D(minX, maxY - ch)),
                PolylineVertex(Point2D(minX, minY + ch))
            )
            else -> listOf(
                PolylineVertex(Point2D(minX, minY)),
                PolylineVertex(Point2D(maxX, minY)),
                PolylineVertex(Point2D(maxX, maxY)),
                PolylineVertex(Point2D(minX, maxY))
            )
        }
        return CadEntity.PolylineEntity(
            layerName = layerName,
            vertices = vertices,
            isClosed = true
        )
    }

    fun createRegularPolygon(
        center: Point2D,
        radius: Double,
        sides: Int,
        circumscribed: Boolean = false,
        rotationDeg: Double = 0.0,
        layerName: String = "0"
    ): CadEntity.PolylineEntity {
        val n = sides.coerceIn(3, 64)
        val vertexRadius = if (circumscribed) {
            radius / cos(PI / n).coerceAtLeast(1e-6)
        } else {
            radius
        }
        val vertices = (0 until n).map { i ->
            val angle = rotationDeg + (360.0 * i.toDouble() / n.toDouble())
            PolylineVertex(center.polarOffset(vertexRadius, angle))
        }
        return CadEntity.PolylineEntity(
            layerName = layerName,
            vertices = vertices,
            isClosed = true
        )
    }

    fun explodePolylineToPrimitives(poly: CadEntity.PolylineEntity): List<CadEntity> {
        if (poly.vertices.size < 2) return emptyList()
        val primitives = mutableListOf<CadEntity>()
        val segCount = if (poly.isClosed) poly.vertices.size else poly.vertices.size - 1
        for (i in 0 until segCount) {
            val v1 = poly.vertices[i]
            val v2 = poly.vertices[(i + 1) % poly.vertices.size]
            if (abs(v1.bulge) < 1e-6) {
                primitives.add(
                    CadEntity.LineEntity(
                        layerName = poly.layerName,
                        colorHexOverride = poly.colorHexOverride,
                        lineTypeOverride = poly.lineTypeOverride,
                        lineWeightMmOverride = poly.lineWeightMmOverride,
                        start = v1.position,
                        end = v2.position
                    )
                )
            } else {
                val chord = v1.position.distanceTo(v2.position)
                val sweepRad = 4.0 * kotlin.math.atan(v1.bulge)
                val radius = abs(chord / (2.0 * sin(sweepRad * 0.5).coerceAtLeast(1e-8)))
                val mid = v1.position.midpointTo(v2.position)
                val sagitta = (v1.bulge * chord) * 0.5
                val distMidToCenter = (chord * 0.25) * (1.0 / v1.bulge - v1.bulge)
                val chordAngleRad = atan2(v2.position.y - v1.position.y, v2.position.x - v1.position.x)
                val normalAngleRad = chordAngleRad + PI * 0.5
                val cx = mid.x + distMidToCenter * cos(normalAngleRad)
                val cy = mid.y + distMidToCenter * sin(normalAngleRad)
                val center = Point2D(cx, cy)
                val startAngle = center.angleToDeg(v1.position)
                val sweepDeg = Math.toDegrees(sweepRad)
                val _unused = sagitta
                primitives.add(
                    CadEntity.ArcEntity(
                        layerName = poly.layerName,
                        colorHexOverride = poly.colorHexOverride,
                        lineTypeOverride = poly.lineTypeOverride,
                        lineWeightMmOverride = poly.lineWeightMmOverride,
                        center = center,
                        radius = radius,
                        startAngleDeg = startAngle,
                        sweepAngleDeg = sweepDeg
                    )
                )
            }
        }
        return primitives
    }

    fun sampleEllipsePoints(ellipse: CadEntity.EllipseEntity, segments: Int = 48): List<Point2D> {
        return (0..segments).map { i ->
            val t = 2.0 * PI * i.toDouble() / segments.toDouble()
            val local = Point2D(
                ellipse.center.x + ellipse.majorRadius * cos(t),
                ellipse.center.y + ellipse.minorRadius * sin(t)
            )
            local.rotateAround(ellipse.center, ellipse.rotationAngleDeg)
        }
    }

    // --- Window vs Crossing Selection ---

    fun isEntitySelectedByBox(
        entity: CadEntity,
        box: BoundingBox2D,
        isCrossingMode: Boolean
    ): Boolean {
        val entityBounds = entity.boundingBox()
        if (!isCrossingMode) {
            // Window mode (Left -> Right): Entire entity bounding box must be enclosed
            return box.fullyContains(entityBounds, tolerance = 1e-4)
        }
        // Crossing mode (Right -> Left): Enclosed OR intersects any edge of box
        if (!box.intersects(entityBounds, tolerance = 1e-4)) return false
        if (box.fullyContains(entityBounds, tolerance = 1e-4)) return true

        val boxCorners = listOf(
            Point2D(box.minX, box.minY),
            Point2D(box.maxX, box.minY),
            Point2D(box.maxX, box.maxY),
            Point2D(box.minX, box.maxY)
        )
        val boxEdges = listOf(
            CadEntity.LineEntity(start = boxCorners[0], end = boxCorners[1]),
            CadEntity.LineEntity(start = boxCorners[1], end = boxCorners[2]),
            CadEntity.LineEntity(start = boxCorners[2], end = boxCorners[3]),
            CadEntity.LineEntity(start = boxCorners[3], end = boxCorners[0])
        )
        for (edge in boxEdges) {
            if (intersectEntities(entity, edge).isNotEmpty()) return true
        }
        return box.contains(closestPointOnEntity(box.center, entity))
    }

    // --- Boundary Detection (BOUNDARY / HATCH Seed Pick) ---

    fun traceEnclosingBoundary(
        seedPoint: Point2D,
        entities: List<CadEntity>,
        layerName: String = "0"
    ): CadEntity.PolylineEntity? {
        // 1. Check if seedPoint is inside any closed Polyline or Circle directly
        val containingClosedPoly = entities.filterIsInstance<CadEntity.PolylineEntity>()
            .filter { it.isClosed && isPointInsidePolygon(seedPoint, it.vertices.map { v -> v.position }) }
            .minByOrNull { polygonArea(it.vertices.map { v -> v.position }) }

        if (containingClosedPoly != null) {
            return containingClosedPoly.withNewId().withStyle(layerName = layerName) as CadEntity.PolylineEntity
        }

        val containingCircle = entities.filterIsInstance<CadEntity.CircleEntity>()
            .filter { it.center.distanceTo(seedPoint) < it.radius }
            .minByOrNull { it.radius }

        if (containingCircle != null) {
            return createRegularPolygon(
                center = containingCircle.center,
                radius = containingCircle.radius,
                sides = 32,
                layerName = layerName
            )
        }

        // 2. Ray-cast in 4 cardinal directions against line segments to find minimal enclosing rectangle/loop
        val segments = entities.flatMap { explodeIfNeeded(it) }.filterIsInstance<CadEntity.LineEntity>()
        if (segments.size < 3) return null

        // Collect all pairwise segment intersections and build minimal bounding polygon around seedPoint
        val allIntersections = mutableListOf<Point2D>()
        for (i in segments.indices) {
            for (j in i + 1 until segments.size) {
                intersectSegments(segments[i].start, segments[i].end, segments[j].start, segments[j].end)
                    ?.let { allIntersections.add(it) }
            }
            allIntersections.add(segments[i].start)
            allIntersections.add(segments[i].end)
        }
        val uniqueNodes = deduplicatePoints(allIntersections, 0.05)
        val leftX = segments.mapNotNull { seg ->
            intersectSegments(seedPoint, seedPoint.offset(-10000.0, 0.0), seg.start, seg.end)?.x
        }.maxOrNull()
        val rightX = segments.mapNotNull { seg ->
            intersectSegments(seedPoint, seedPoint.offset(10000.0, 0.0), seg.start, seg.end)?.x
        }.minOrNull()
        val bottomY = segments.mapNotNull { seg ->
            intersectSegments(seedPoint, seedPoint.offset(0.0, -10000.0), seg.start, seg.end)?.y
        }.maxOrNull()
        val topY = segments.mapNotNull { seg ->
            intersectSegments(seedPoint, seedPoint.offset(0.0, 10000.0), seg.start, seg.end)?.y
        }.minOrNull()

        if (leftX != null && rightX != null && bottomY != null && topY != null && rightX > leftX && topY > bottomY) {
            return createRectanglePolyline(
                corner1 = Point2D(leftX, bottomY),
                corner2 = Point2D(rightX, topY),
                layerName = layerName
            )
        }

        if (uniqueNodes.size >= 3) {
            val hull = convexHull(uniqueNodes)
            if (hull.size >= 3 && isPointInsidePolygon(seedPoint, hull)) {
                return CadEntity.PolylineEntity(
                    layerName = layerName,
                    vertices = hull.map { PolylineVertex(it) },
                    isClosed = true
                )
            }
        }
        return null
    }

    private fun explodeIfNeeded(entity: CadEntity): List<CadEntity> = when (entity) {
        is CadEntity.PolylineEntity -> explodePolylineToPrimitives(entity)
        else -> listOf(entity)
    }

    fun isPointInsidePolygon(pt: Point2D, polygon: List<Point2D>): Boolean {
        if (polygon.size < 3) return false
        var inside = false
        var j = polygon.size - 1
        for (i in polygon.indices) {
            val xi = polygon[i].x; val yi = polygon[i].y
            val xj = polygon[j].x; val yj = polygon[j].y
            val intersect = ((yi > pt.y) != (yj > pt.y)) &&
                (pt.x < (xj - xi) * (pt.y - yi) / ((yj - yi).takeIf { abs(it) > 1e-12 } ?: 1e-12) + xi)
            if (intersect) inside = !inside
            j = i
        }
        return inside
    }

    fun polygonArea(polygon: List<Point2D>): Double {
        if (polygon.size < 3) return 0.0
        var sum = 0.0
        for (i in polygon.indices) {
            val p1 = polygon[i]
            val p2 = polygon[(i + 1) % polygon.size]
            sum += (p1.x * p2.y - p2.x * p1.y)
        }
        return abs(sum) * 0.5
    }

    private fun convexHull(points: List<Point2D>): List<Point2D> {
        if (points.size <= 3) return points
        val sorted = points.sortedWith(compareBy<Point2D> { it.x }.thenBy { it.y })
        fun cross(o: Point2D, a: Point2D, b: Point2D): Double =
            (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
        val lower = mutableListOf<Point2D>()
        for (p in sorted) {
            while (lower.size >= 2 && cross(lower[lower.size - 2], lower.last(), p) <= 0.0) {
                lower.removeAt(lower.lastIndex)
            }
            lower.add(p)
        }
        val upper = mutableListOf<Point2D>()
        for (p in sorted.asReversed()) {
            while (upper.size >= 2 && cross(upper[upper.size - 2], upper.last(), p) <= 0.0) {
                upper.removeAt(upper.lastIndex)
            }
            upper.add(p)
        }
        lower.removeAt(lower.lastIndex)
        upper.removeAt(upper.lastIndex)
        return lower + upper
    }

    // --- 2D Geometric Constraint Solver ---

    fun applyGeometricConstraint(
        constraint: GeometricConstraintRecord,
        entities: List<CadEntity>
    ): List<CadEntity> {
        val map = entities.associateBy { it.id }.toMutableMap()
        val primary = map[constraint.primaryEntityId] ?: return entities
        val secondary = constraint.secondaryEntityId?.let { map[it] }

        when (constraint.type) {
            ConstraintType.HORIZONTAL -> if (primary is CadEntity.LineEntity) {
                val len = primary.length
                map[primary.id] = primary.copy(end = Point2D(primary.start.x + len, primary.start.y))
            }
            ConstraintType.VERTICAL -> if (primary is CadEntity.LineEntity) {
                val len = primary.length
                map[primary.id] = primary.copy(end = Point2D(primary.start.x, primary.start.y + len))
            }
            ConstraintType.PARALLEL -> if (primary is CadEntity.LineEntity && secondary is CadEntity.LineEntity) {
                val targetAngle = primary.angleDeg
                val len = secondary.length
                map[secondary.id] = secondary.copy(end = secondary.start.polarOffset(len, targetAngle))
            }
            ConstraintType.PERPENDICULAR -> if (primary is CadEntity.LineEntity && secondary is CadEntity.LineEntity) {
                val targetAngle = (primary.angleDeg + 90.0) % 360.0
                val len = secondary.length
                map[secondary.id] = secondary.copy(end = secondary.start.polarOffset(len, targetAngle))
            }
            ConstraintType.EQUAL -> {
                if (primary is CadEntity.LineEntity && secondary is CadEntity.LineEntity) {
                    val targetLen = primary.length
                    val angle = secondary.angleDeg
                    map[secondary.id] = secondary.copy(end = secondary.start.polarOffset(targetLen, angle))
                } else if (primary is CadEntity.CircleEntity && secondary is CadEntity.CircleEntity) {
                    map[secondary.id] = secondary.copy(radius = primary.radius)
                }
            }
            ConstraintType.CONCENTRIC -> {
                val c1 = when (primary) {
                    is CadEntity.CircleEntity -> primary.center
                    is CadEntity.ArcEntity -> primary.center
                    else -> null
                }
                if (c1 != null && secondary != null) {
                    when (secondary) {
                        is CadEntity.CircleEntity -> map[secondary.id] = secondary.copy(center = c1)
                        is CadEntity.ArcEntity -> map[secondary.id] = secondary.copy(center = c1)
                        else -> Unit
                    }
                }
            }
            ConstraintType.TANGENT -> {
                if (primary is CadEntity.CircleEntity && secondary is CadEntity.LineEntity) {
                    val proj = projectPointOnInfiniteLine(primary.center, secondary.start, secondary.end)
                    val dist = primary.center.distanceTo(proj)
                    if (dist > 1e-6) {
                        val targetPt = primary.center.polarOffset(primary.radius, primary.center.angleToDeg(proj))
                        val dx = targetPt.x - proj.x
                        val dy = targetPt.y - proj.y
                        map[secondary.id] = secondary.translate(dx, dy)
                    }
                }
            }
            ConstraintType.COINCIDENT -> {
                if (primary is CadEntity.LineEntity && secondary is CadEntity.LineEntity) {
                    val dx = primary.end.x - secondary.start.x
                    val dy = primary.end.y - secondary.start.y
                    map[secondary.id] = secondary.translate(dx, dy)
                }
            }
            ConstraintType.FIXED -> Unit
        }
        return map.values.toList()
    }
}
