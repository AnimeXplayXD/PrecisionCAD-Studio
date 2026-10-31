package com.example.cad.kernel

import com.example.cad.model.BoundingBox2D
import com.example.cad.model.BlockDefinition
import com.example.cad.model.CadEntity
import com.example.cad.model.DimensionType
import com.example.cad.model.Point2D
import com.example.cad.model.PolylineVertex
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

/**
 * Deterministic implementation of Desktop 2D CAD Modification commands
 * and associative dimension recalculation.
 */
object CadModificationEngine {

    /**
     * Updates associative dimensions whenever underlying source entities are modified.
     */
    fun updateAssociativeDimensions(entities: List<CadEntity>): List<CadEntity> {
        val byId = entities.associateBy { it.id }
        return entities.map { entity ->
            if (entity is CadEntity.DimensionEntity && entity.sourceEntityIds.isNotEmpty()) {
                recomputeAssociativeDimension(entity, byId)
            } else if (entity is CadEntity.CenterMarkEntity && entity.sourceEntityId != null) {
                val src = byId[entity.sourceEntityId]
                when (src) {
                    is CadEntity.CircleEntity -> entity.copy(center = src.center, radius = src.radius)
                    is CadEntity.ArcEntity -> entity.copy(center = src.center, radius = src.radius)
                    else -> entity
                }
            } else {
                entity
            }
        }
    }

    private fun recomputeAssociativeDimension(
        dim: CadEntity.DimensionEntity,
        byId: Map<String, CadEntity>
    ): CadEntity.DimensionEntity {
        val src1 = dim.sourceEntityIds.getOrNull(0)?.let { byId[it] } ?: return dim
        val src2 = dim.sourceEntityIds.getOrNull(1)?.let { byId[it] }

        return when {
            dim.dimensionType == DimensionType.DIAMETER || dim.dimensionType == DimensionType.RADIUS -> {
                when (src1) {
                    is CadEntity.CircleEntity -> {
                        val angle = src1.center.angleToDeg(dim.dimensionLinePoint)
                        val rimPt = src1.center.polarOffset(src1.radius, angle)
                        dim.copy(anchor1 = src1.center, anchor2 = rimPt)
                    }
                    is CadEntity.ArcEntity -> {
                        val angle = src1.center.angleToDeg(dim.dimensionLinePoint)
                        val rimPt = src1.center.polarOffset(src1.radius, angle)
                        dim.copy(anchor1 = src1.center, anchor2 = rimPt)
                    }
                    else -> dim
                }
            }
            src1 != null && src2 != null -> {
                val p1 = representativeAnchor(src1, dim.anchor1)
                val p2 = representativeAnchor(src2, dim.anchor2)
                val dx = p2.x - dim.anchor2.x
                val dy = p2.y - dim.anchor2.y
                dim.copy(
                    anchor1 = p1,
                    anchor2 = p2,
                    dimensionLinePoint = dim.dimensionLinePoint.offset(dx * 0.5, dy * 0.5)
                )
            }
            src1 is CadEntity.LineEntity -> {
                val offsetDx = dim.dimensionLinePoint.x - dim.anchor1.x
                val offsetDy = dim.dimensionLinePoint.y - dim.anchor1.y
                dim.copy(
                    anchor1 = src1.start,
                    anchor2 = src1.end,
                    dimensionLinePoint = src1.start.offset(offsetDx, offsetDy)
                )
            }
            src1 is CadEntity.PolylineEntity -> {
                val b = src1.boundingBox()
                when (dim.dimensionType) {
                    DimensionType.LINEAR_HORIZONTAL -> {
                        val yOff = dim.dimensionLinePoint.y - dim.anchor1.y
                        dim.copy(
                            anchor1 = Point2D(b.minX, b.maxY),
                            anchor2 = Point2D(b.maxX, b.maxY),
                            dimensionLinePoint = Point2D((b.minX + b.maxX) * 0.5, b.maxY + yOff)
                        )
                    }
                    DimensionType.LINEAR_VERTICAL -> {
                        val xOff = dim.dimensionLinePoint.x - dim.anchor1.x
                        dim.copy(
                            anchor1 = Point2D(b.minX, b.minY),
                            anchor2 = Point2D(b.minX, b.maxY),
                            dimensionLinePoint = Point2D(b.minX + xOff, (b.minY + b.maxY) * 0.5)
                        )
                    }
                    else -> dim
                }
            }
            else -> dim
        }
    }

    private fun representativeAnchor(entity: CadEntity, previousAnchor: Point2D): Point2D = when (entity) {
        is CadEntity.CircleEntity -> entity.center
        is CadEntity.ArcEntity -> entity.center
        is CadEntity.PointEntity -> entity.position
        is CadEntity.LineEntity -> if (previousAnchor.distanceTo(entity.start) <= previousAnchor.distanceTo(entity.end)) entity.start else entity.end
        is CadEntity.PolylineEntity -> entity.vertices.minByOrNull { it.position.distanceTo(previousAnchor) }?.position ?: previousAnchor
        else -> previousAnchor
    }

    // --- OFFSET ---

    fun offsetEntity(
        entity: CadEntity,
        distance: Double,
        sidePoint: Point2D
    ): CadEntity? {
        val d = abs(distance)
        if (d < 1e-6) return null
        return when (entity) {
            is CadEntity.LineEntity -> {
                val dx = entity.end.x - entity.start.x
                val dy = entity.end.y - entity.start.y
                val len = hypot(dx, dy)
                if (len < 1e-8) return null
                val nx = -dy / len
                val ny = dx / len
                val sideSign = if ((sidePoint.x - entity.start.x) * nx + (sidePoint.y - entity.start.y) * ny >= 0.0) 1.0 else -1.0
                val ox = nx * d * sideSign
                val oy = ny * d * sideSign
                entity.withNewId().copy(
                    start = entity.start.offset(ox, oy),
                    end = entity.end.offset(ox, oy)
                )
            }
            is CadEntity.CircleEntity -> {
                val isOutside = entity.center.distanceTo(sidePoint) >= entity.radius
                val newRadius = if (isOutside) entity.radius + d else entity.radius - d
                if (newRadius <= 1e-4) null else entity.withNewId().copy(radius = newRadius)
            }
            is CadEntity.ArcEntity -> {
                val isOutside = entity.center.distanceTo(sidePoint) >= entity.radius
                val newRadius = if (isOutside) entity.radius + d else entity.radius - d
                if (newRadius <= 1e-4) null else entity.withNewId().copy(radius = newRadius)
            }
            is CadEntity.PolylineEntity -> {
                val b = entity.boundingBox()
                val center = b.center
                val isOutside = !b.contains(sidePoint)
                val sign = if (isOutside) 1.0 else -1.0
                val newVerts = entity.vertices.map { v ->
                    val vx = v.position.x - center.x
                    val vy = v.position.y - center.y
                    val sx = if (vx >= 0.0) 1.0 else -1.0
                    val sy = if (vy >= 0.0) 1.0 else -1.0
                    v.copy(
                        position = Point2D(
                            x = v.position.x + sx * d * sign,
                            y = v.position.y + sy * d * sign
                        )
                    )
                }
                val newBounds = BoundingBox2D.fromPoints(newVerts.map { it.position })
                if (newBounds.width <= 1e-3 || newBounds.height <= 1e-3) null
                else entity.withNewId().copy(vertices = newVerts)
            }
            else -> null
        }
    }

    // --- TRIM ---

    fun trimEntityAtPick(
        target: CadEntity,
        pickPoint: Point2D,
        allEntities: List<CadEntity>
    ): List<CadEntity> {
        val cutters = allEntities.filter { it.id != target.id && it !is CadEntity.DimensionEntity }
        val intersections = cutters.flatMap { CadGeometryKernel.intersectEntities(target, it) }
        if (intersections.isEmpty()) return listOf(target)

        return when (target) {
            is CadEntity.LineEntity -> {
                val dx = target.end.x - target.start.x
                val dy = target.end.y - target.start.y
                val lenSq = (dx * dx + dy * dy).coerceAtLeast(1e-12)
                fun paramT(pt: Point2D): Double = ((pt.x - target.start.x) * dx + (pt.y - target.start.y) * dy) / lenSq

                val tList = (listOf(0.0, 1.0) + intersections.map { paramT(it).coerceIn(0.0, 1.0) })
                    .sorted()
                    .fold(mutableListOf<Double>()) { acc, v ->
                        if (acc.isEmpty() || abs(acc.last() - v) > 1e-4) acc.add(v)
                        acc
                    }
                if (tList.size <= 2) return listOf(target)
                val tPick = paramT(pickPoint).coerceIn(0.0, 1.0)
                val remaining = mutableListOf<CadEntity.LineEntity>()
                for (i in 0 until tList.size - 1) {
                    val t0 = tList[i]
                    val t1 = tList[i + 1]
                    val isPickedSpan = (tPick >= t0 - 1e-4 && tPick <= t1 + 1e-4)
                    if (!isPickedSpan && (t1 - t0) > 1e-4) {
                        val p0 = Point2D(target.start.x + t0 * dx, target.start.y + t0 * dy)
                        val p1 = Point2D(target.start.x + t1 * dx, target.start.y + t1 * dy)
                        remaining.add(
                            target.withNewId().copy(start = p0, end = p1)
                        )
                    }
                }
                remaining
            }
            is CadEntity.CircleEntity -> {
                if (intersections.size < 2) return listOf(target)
                val angles = intersections.map { target.center.angleToDeg(it) }.sorted()
                val pickAngle = target.center.angleToDeg(pickPoint)
                // Find interval containing pickAngle and keep the complementary arc
                var removeIdx = angles.size - 1
                for (i in 0 until angles.size - 1) {
                    if (pickAngle >= angles[i] && pickAngle <= angles[i + 1]) {
                        removeIdx = i
                        break
                    }
                }
                val startAng = angles[(removeIdx + 1) % angles.size]
                val endAng = angles[removeIdx]
                val sweep = ((endAng - startAng) + 360.0) % 360.0
                listOf(
                    CadEntity.ArcEntity(
                        layerName = target.layerName,
                        colorHexOverride = target.colorHexOverride,
                        lineTypeOverride = target.lineTypeOverride,
                        lineWeightMmOverride = target.lineWeightMmOverride,
                        center = target.center,
                        radius = target.radius,
                        startAngleDeg = startAng,
                        sweepAngleDeg = sweep
                    )
                )
            }
            else -> listOf(target)
        }
    }

    // --- EXTEND ---

    fun extendEntityAtPick(
        target: CadEntity,
        pickPoint: Point2D,
        allEntities: List<CadEntity>
    ): CadEntity {
        if (target !is CadEntity.LineEntity) return target
        val extendEnd = pickPoint.distanceTo(target.end) <= pickPoint.distanceTo(target.start)
        val anchor = if (extendEnd) target.start else target.end
        val movingEnd = if (extendEnd) target.end else target.start
        val rayAngle = anchor.angleToDeg(movingEnd)
        val farPoint = movingEnd.polarOffset(10000.0, rayAngle)
        val probeRay = CadEntity.LineEntity(start = movingEnd, end = farPoint)

        val hitPoints = allEntities
            .filter { it.id != target.id && it !is CadEntity.DimensionEntity }
            .flatMap { CadGeometryKernel.intersectEntities(probeRay, it) }
            .filter { it.distanceTo(movingEnd) > 1e-3 }

        val nearestHit = hitPoints.minByOrNull { it.distanceTo(movingEnd) } ?: return target
        return if (extendEnd) {
            target.copy(end = nearestHit)
        } else {
            target.copy(start = nearestHit)
        }
    }

    // --- FILLET & CHAMFER (AutoCAD-Style Two-Pick on Lines, Polylines, & Rectangles) ---

    fun hasLinearEdgeAtPick(
        entities: List<CadEntity>,
        pickPoint: Point2D,
        pickTolerance: Double
    ): CadEntity? {
        return entities
            .filter { it is CadEntity.LineEntity || it is CadEntity.PolylineEntity }
            .map { it to CadGeometryKernel.distanceToEntity(pickPoint, it) }
            .filter { it.second <= pickTolerance }
            .minByOrNull { it.second }
            ?.first
    }

    /**
     * AutoCAD-style two-pick Fillet that works seamlessly on standalone LineEntity objects
     * as well as closed/open PolylineEntity objects (including RECTANGLE and POLYGON).
     * Also cleanly replaces an existing corner fillet arc if the two picked edges were
     * previously joined by a tangent corner arc.
     */
    fun filletAtPicks(
        entities: List<CadEntity>,
        pick1: Point2D,
        pick2: Point2D,
        radius: Double,
        pickTolerance: Double,
        trimSegments: Boolean = true
    ): List<CadEntity>? {
        val hit1 = hasLinearEdgeAtPick(entities, pick1, pickTolerance) ?: return null
        val hit2 = hasLinearEdgeAtPick(entities, pick2, pickTolerance) ?: return null

        val workingEntities = entities.toMutableList()
        val expandedPool = mutableListOf<CadEntity.LineEntity>()
        val expandedArcs = mutableListOf<CadEntity.ArcEntity>()

        fun expandIfNeeded(target: CadEntity) {
            when (target) {
                is CadEntity.LineEntity -> if (expandedPool.none { it.id == target.id }) expandedPool.add(target)
                is CadEntity.PolylineEntity -> {
                    if (workingEntities.removeAll { it.id == target.id }) {
                        val primitives = CadGeometryKernel.explodePolylineToPrimitives(target)
                        workingEntities.addAll(primitives)
                        primitives.filterIsInstance<CadEntity.LineEntity>().forEach { expandedPool.add(it) }
                        primitives.filterIsInstance<CadEntity.ArcEntity>().forEach { expandedArcs.add(it) }
                    }
                }
                else -> Unit
            }
        }

        expandIfNeeded(hit1)
        expandIfNeeded(hit2)

        // Include all LineEntities in workingEntities as candidates if needed
        workingEntities.filterIsInstance<CadEntity.LineEntity>().forEach { l ->
            if (expandedPool.none { it.id == l.id }) expandedPool.add(l)
        }

        val line1 = expandedPool
            .map { it to CadGeometryKernel.distanceToEntity(pick1, it) }
            .filter { it.second <= pickTolerance * 1.5 }
            .minByOrNull { it.second }
            ?.first ?: return null

        val line2 = expandedPool
            .filter { it.id != line1.id }
            .filter { CadGeometryKernel.intersectInfiniteLines(line1.start, line1.end, it.start, it.end) != null }
            .map { it to CadGeometryKernel.distanceToEntity(pick2, it) }
            .filter { it.second <= pickTolerance * 1.5 }
            .minByOrNull { it.second }
            ?.first ?: return null

        val corner = CadGeometryKernel.intersectInfiniteLines(line1.start, line1.end, line2.start, line2.end)
            ?: return null

        // Determine which endpoints of line1 and line2 were on the corner side (to detect any old fillet arc)
        val nearCorner1 = if (line1.start.distanceTo(corner) <= line1.end.distanceTo(corner)) line1.start else line1.end
        val nearCorner2 = if (line2.start.distanceTo(corner) <= line2.end.distanceTo(corner)) line2.start else line2.end

        val filleted = filletTwoLines(
            line1 = line1,
            line2 = line2,
            radius = radius,
            trimSegments = trimSegments,
            pick1 = pick1,
            pick2 = pick2
        ) ?: return null

        // Remove any pre-existing corner fillet arc that connected nearCorner1 and nearCorner2
        val oldCornerArcIds = workingEntities.filterIsInstance<CadEntity.ArcEntity>()
            .filter { arc ->
                val s = arc.startPoint
                val e = arc.endPoint
                (s.distanceTo(nearCorner1) <= 1e-2 && e.distanceTo(nearCorner2) <= 1e-2) ||
                    (s.distanceTo(nearCorner2) <= 1e-2 && e.distanceTo(nearCorner1) <= 1e-2)
            }
            .map { it.id }
            .toSet()

        return workingEntities
            .filterNot { it.id == line1.id || it.id == line2.id || it.id in oldCornerArcIds } + filleted
    }

    fun chamferAtPicks(
        entities: List<CadEntity>,
        pick1: Point2D,
        pick2: Point2D,
        distance1: Double,
        distance2: Double,
        pickTolerance: Double,
        trimSegments: Boolean = true
    ): List<CadEntity>? {
        val hit1 = hasLinearEdgeAtPick(entities, pick1, pickTolerance) ?: return null
        val hit2 = hasLinearEdgeAtPick(entities, pick2, pickTolerance) ?: return null

        val workingEntities = entities.toMutableList()
        val expandedPool = mutableListOf<CadEntity.LineEntity>()

        fun expandIfNeeded(target: CadEntity) {
            when (target) {
                is CadEntity.LineEntity -> if (expandedPool.none { it.id == target.id }) expandedPool.add(target)
                is CadEntity.PolylineEntity -> {
                    if (workingEntities.removeAll { it.id == target.id }) {
                        val primitives = CadGeometryKernel.explodePolylineToPrimitives(target)
                        workingEntities.addAll(primitives)
                        primitives.filterIsInstance<CadEntity.LineEntity>().forEach { expandedPool.add(it) }
                    }
                }
                else -> Unit
            }
        }

        expandIfNeeded(hit1)
        expandIfNeeded(hit2)
        workingEntities.filterIsInstance<CadEntity.LineEntity>().forEach { l ->
            if (expandedPool.none { it.id == l.id }) expandedPool.add(l)
        }

        val line1 = expandedPool
            .map { it to CadGeometryKernel.distanceToEntity(pick1, it) }
            .filter { it.second <= pickTolerance * 1.5 }
            .minByOrNull { it.second }
            ?.first ?: return null

        val line2 = expandedPool
            .filter { it.id != line1.id }
            .filter { CadGeometryKernel.intersectInfiniteLines(line1.start, line1.end, it.start, it.end) != null }
            .map { it to CadGeometryKernel.distanceToEntity(pick2, it) }
            .filter { it.second <= pickTolerance * 1.5 }
            .minByOrNull { it.second }
            ?.first ?: return null

        val corner = CadGeometryKernel.intersectInfiniteLines(line1.start, line1.end, line2.start, line2.end)
            ?: return null
        val nearCorner1 = if (line1.start.distanceTo(corner) <= line1.end.distanceTo(corner)) line1.start else line1.end
        val nearCorner2 = if (line2.start.distanceTo(corner) <= line2.end.distanceTo(corner)) line2.start else line2.end

        val chamfered = chamferTwoLines(
            line1 = line1,
            line2 = line2,
            distance1 = distance1,
            distance2 = distance2,
            trimSegments = trimSegments,
            pick1 = pick1,
            pick2 = pick2
        ) ?: return null

        val oldCornerArcIds = workingEntities.filterIsInstance<CadEntity.ArcEntity>()
            .filter { arc ->
                val s = arc.startPoint
                val e = arc.endPoint
                (s.distanceTo(nearCorner1) <= 1e-2 && e.distanceTo(nearCorner2) <= 1e-2) ||
                    (s.distanceTo(nearCorner2) <= 1e-2 && e.distanceTo(nearCorner1) <= 1e-2)
            }
            .map { it.id }
            .toSet()

        return workingEntities
            .filterNot { it.id == line1.id || it.id == line2.id || it.id in oldCornerArcIds } + chamfered
    }

    private fun resolveKeptEndpoint(
        line: CadEntity.LineEntity,
        corner: Point2D,
        pick: Point2D?
    ): Point2D {
        if (pick != null) {
            val proj = CadGeometryKernel.projectPointOnSegment(pick, line.start, line.end)
            if (proj.distanceTo(corner) > 1e-3) {
                val dirX = proj.x - corner.x
                val dirY = proj.y - corner.y
                val dotStart = (line.start.x - corner.x) * dirX + (line.start.y - corner.y) * dirY
                val dotEnd = (line.end.x - corner.x) * dirX + (line.end.y - corner.y) * dirY
                return if (dotStart >= dotEnd) line.start else line.end
            }
        }
        return if (line.start.distanceTo(corner) >= line.end.distanceTo(corner)) line.start else line.end
    }

    fun filletTwoLines(
        line1: CadEntity.LineEntity,
        line2: CadEntity.LineEntity,
        radius: Double,
        trimSegments: Boolean = true,
        pick1: Point2D? = null,
        pick2: Point2D? = null
    ): List<CadEntity>? {
        val corner = CadGeometryKernel.intersectInfiniteLines(line1.start, line1.end, line2.start, line2.end)
            ?: return null

        val far1 = resolveKeptEndpoint(line1, corner, pick1)
        val far2 = resolveKeptEndpoint(line2, corner, pick2)

        val len1 = corner.distanceTo(far1)
        val len2 = corner.distanceTo(far2)
        if (len1 < 1e-4 || len2 < 1e-4) return null

        if (radius <= 1e-5) {
            // Sharp corner (R=0)
            return listOf(
                line1.copy(start = far1, end = corner),
                line2.copy(start = corner, end = far2)
            )
        }

        val u1x = (far1.x - corner.x) / len1
        val u1y = (far1.y - corner.y) / len1
        val u2x = (far2.x - corner.x) / len2
        val u2y = (far2.y - corner.y) / len2

        val dot = (u1x * u2x + u1y * u2y).coerceIn(-0.999999, 0.999999)
        val interiorAngleRad = acos(dot)
        if (interiorAngleRad < 1e-3 || abs(PI - interiorAngleRad) < 1e-3) return null

        val halfAngle = interiorAngleRad * 0.5
        val tangentDist = radius / tan(halfAngle)
        val centerDist = radius / sin(halfAngle)

        if (trimSegments && (tangentDist > len1 + 1e-3 || tangentDist > len2 + 1e-3)) {
            return null
        }

        val t1 = Point2D(corner.x + u1x * tangentDist, corner.y + u1y * tangentDist)
        val t2 = Point2D(corner.x + u2x * tangentDist, corner.y + u2y * tangentDist)

        val bx = u1x + u2x
        val by = u1y + u2y
        val bLen = hypot(bx, by)
        if (bLen < 1e-6) return null
        val arcCenter = Point2D(
            x = corner.x + (bx / bLen) * centerDist,
            y = corner.y + (by / bLen) * centerDist
        )

        val a1 = arcCenter.angleToDeg(t1)
        val a2 = arcCenter.angleToDeg(t2)
        var sweep = ((a2 - a1) + 360.0) % 360.0
        if (sweep > 180.0) sweep -= 360.0

        val filletArc = CadEntity.ArcEntity(
            layerName = line1.layerName,
            colorHexOverride = line1.colorHexOverride,
            lineTypeOverride = line1.lineTypeOverride,
            lineWeightMmOverride = line1.lineWeightMmOverride,
            center = arcCenter,
            radius = radius,
            startAngleDeg = a1,
            sweepAngleDeg = sweep
        )

        return if (trimSegments) {
            listOf(
                line1.copy(start = far1, end = t1),
                line2.copy(start = t2, end = far2),
                filletArc
            )
        } else {
            listOf(line1, line2, filletArc)
        }
    }

    // --- CHAMFER ---

    fun chamferTwoLines(
        line1: CadEntity.LineEntity,
        line2: CadEntity.LineEntity,
        distance1: Double,
        distance2: Double,
        trimSegments: Boolean = true,
        pick1: Point2D? = null,
        pick2: Point2D? = null
    ): List<CadEntity>? {
        if (distance1 <= 0.0 || distance2 <= 0.0) return null
        val corner = CadGeometryKernel.intersectInfiniteLines(line1.start, line1.end, line2.start, line2.end)
            ?: return null

        val far1 = resolveKeptEndpoint(line1, corner, pick1)
        val far2 = resolveKeptEndpoint(line2, corner, pick2)

        val len1 = corner.distanceTo(far1)
        val len2 = corner.distanceTo(far2)
        if (trimSegments && (distance1 > len1 + 1e-3 || distance2 > len2 + 1e-3)) {
            return null
        }

        val c1 = corner.polarOffset(distance1, corner.angleToDeg(far1))
        val c2 = corner.polarOffset(distance2, corner.angleToDeg(far2))
        val bevelLine = CadEntity.LineEntity(
            layerName = line1.layerName,
            colorHexOverride = line1.colorHexOverride,
            lineTypeOverride = line1.lineTypeOverride,
            lineWeightMmOverride = line1.lineWeightMmOverride,
            start = c1,
            end = c2
        )

        return if (trimSegments) {
            listOf(
                line1.copy(start = far1, end = c1),
                line2.copy(start = c2, end = far2),
                bevelLine
            )
        } else {
            listOf(line1, line2, bevelLine)
        }
    }

    // --- STRETCH ---

    fun stretchEntity(
        entity: CadEntity,
        crossingBox: BoundingBox2D,
        dx: Double,
        dy: Double
    ): CadEntity {
        if (crossingBox.fullyContains(entity.boundingBox())) {
            return entity.translate(dx, dy)
        }
        return when (entity) {
            is CadEntity.LineEntity -> {
                val sIn = crossingBox.contains(entity.start)
                val eIn = crossingBox.contains(entity.end)
                entity.copy(
                    start = if (sIn) entity.start.offset(dx, dy) else entity.start,
                    end = if (eIn) entity.end.offset(dx, dy) else entity.end
                )
            }
            is CadEntity.PolylineEntity -> {
                entity.copy(
                    vertices = entity.vertices.map { v ->
                        if (crossingBox.contains(v.position)) v.copy(position = v.position.offset(dx, dy))
                        else v
                    }
                )
            }
            is CadEntity.CircleEntity -> {
                if (crossingBox.contains(entity.center)) entity.translate(dx, dy) else entity
            }
            else -> entity
        }
    }

    // --- BREAK ---

    fun breakEntity(
        entity: CadEntity,
        breakPoint1: Point2D,
        breakPoint2: Point2D
    ): List<CadEntity> {
        return when (entity) {
            is CadEntity.LineEntity -> {
                val p1 = CadGeometryKernel.projectPointOnSegment(breakPoint1, entity.start, entity.end)
                val p2 = CadGeometryKernel.projectPointOnSegment(breakPoint2, entity.start, entity.end)
                val d1 = entity.start.distanceTo(p1)
                val d2 = entity.start.distanceTo(p2)
                val nearStart = if (d1 <= d2) p1 else p2
                val nearEnd = if (d1 <= d2) p2 else p1
                val result = mutableListOf<CadEntity>()
                if (entity.start.distanceTo(nearStart) > 1e-4) {
                    result.add(entity.withNewId().copy(start = entity.start, end = nearStart))
                }
                if (nearEnd.distanceTo(entity.end) > 1e-4) {
                    result.add(entity.withNewId().copy(start = nearEnd, end = entity.end))
                }
                result
            }
            is CadEntity.CircleEntity -> {
                val a1 = entity.center.angleToDeg(breakPoint1)
                val a2 = entity.center.angleToDeg(breakPoint2)
                val sweep = ((a1 - a2) + 360.0) % 360.0
                if (sweep < 1e-3) listOf(entity) else listOf(
                    CadEntity.ArcEntity(
                        layerName = entity.layerName,
                        colorHexOverride = entity.colorHexOverride,
                        lineTypeOverride = entity.lineTypeOverride,
                        lineWeightMmOverride = entity.lineWeightMmOverride,
                        center = entity.center,
                        radius = entity.radius,
                        startAngleDeg = a2,
                        sweepAngleDeg = sweep
                    )
                )
            }
            else -> listOf(entity)
        }
    }

    // --- JOIN ---

    fun joinEntities(selected: List<CadEntity>): List<CadEntity> {
        if (selected.size < 2) return selected
        val lines = selected.filterIsInstance<CadEntity.LineEntity>().toMutableList()
        if (lines.size < 2) return selected

        // Check if all lines are collinear and share endpoints -> merge into single LineEntity
        val first = lines.first()
        val allCollinear = lines.drop(1).all { other ->
            val d1 = CadGeometryKernel.projectPointOnInfiniteLine(other.start, first.start, first.end).distanceTo(other.start)
            val d2 = CadGeometryKernel.projectPointOnInfiniteLine(other.end, first.start, first.end).distanceTo(other.end)
            d1 < 1e-3 && d2 < 1e-3
        }
        if (allCollinear) {
            val pts = lines.flatMap { listOf(it.start, it.end) }
            val pMin = pts.minWithOrNull(compareBy<Point2D> { it.x }.thenBy { it.y }) ?: first.start
            val pMax = pts.maxWithOrNull(compareBy<Point2D> { it.x }.thenBy { it.y }) ?: first.end
            return listOf(first.withNewId().copy(start = pMin, end = pMax))
        }

        // Otherwise chain contiguous segments into a single PolylineEntity
        val chain = mutableListOf(first.start, first.end)
        val pool = lines.drop(1).toMutableList()
        var progress = true
        while (progress && pool.isNotEmpty()) {
            progress = false
            val tail = chain.last()
            val head = chain.first()
            val matchIdx = pool.indexOfFirst {
                it.start.distanceTo(tail) <= 1e-2 || it.end.distanceTo(tail) <= 1e-2 ||
                    it.start.distanceTo(head) <= 1e-2 || it.end.distanceTo(head) <= 1e-2
            }
            if (matchIdx >= 0) {
                val seg = pool.removeAt(matchIdx)
                when {
                    seg.start.distanceTo(tail) <= 1e-2 -> chain.add(seg.end)
                    seg.end.distanceTo(tail) <= 1e-2 -> chain.add(seg.start)
                    seg.end.distanceTo(head) <= 1e-2 -> chain.add(0, seg.start)
                    seg.start.distanceTo(head) <= 1e-2 -> chain.add(0, seg.end)
                }
                progress = true
            }
        }
        val isClosed = chain.size >= 4 && chain.first().distanceTo(chain.last()) <= 1e-2
        val finalPts = if (isClosed) chain.dropLast(1) else chain
        val joinedPoly = CadEntity.PolylineEntity(
            layerName = first.layerName,
            colorHexOverride = first.colorHexOverride,
            lineTypeOverride = first.lineTypeOverride,
            lineWeightMmOverride = first.lineWeightMmOverride,
            vertices = finalPts.map { PolylineVertex(it) },
            isClosed = isClosed
        )
        return listOf(joinedPoly) + pool
    }

    // --- EXPLODE ---

    fun explodeEntity(
        entity: CadEntity,
        blockDefinitions: Map<String, BlockDefinition> = emptyMap()
    ): List<CadEntity> = when (entity) {
        is CadEntity.PolylineEntity -> CadGeometryKernel.explodePolylineToPrimitives(entity)
        is CadEntity.BlockInsertEntity -> {
            val def = blockDefinitions[entity.blockName] ?: return listOf(entity)
            def.entities.map { child ->
                child.withNewId()
                    .translate(-def.basePoint.x, -def.basePoint.y)
                    .scale(Point2D(0.0, 0.0), entity.scaleFactor)
                    .rotate(Point2D(0.0, 0.0), entity.rotationDeg)
                    .translate(entity.insertionPoint.x, entity.insertionPoint.y)
            }
        }
        is CadEntity.DonutEntity -> listOf(
            CadEntity.CircleEntity(layerName = entity.layerName, center = entity.center, radius = entity.innerRadius),
            CadEntity.CircleEntity(layerName = entity.layerName, center = entity.center, radius = entity.outerRadius)
        )
        else -> listOf(entity)
    }

    // --- ARRAY (Rectangular & Polar) ---

    fun createRectangularArray(
        sources: List<CadEntity>,
        rows: Int,
        cols: Int,
        rowSpacing: Double,
        colSpacing: Double
    ): List<CadEntity> {
        val rCount = rows.coerceIn(1, 25)
        val cCount = cols.coerceIn(1, 25)
        val generated = mutableListOf<CadEntity>()
        for (r in 0 until rCount) {
            for (c in 0 until cCount) {
                if (r == 0 && c == 0) continue // Source already at (0,0)
                val dx = c * colSpacing
                val dy = r * rowSpacing
                sources.forEach { src ->
                    generated.add(src.withNewId().translate(dx, dy))
                }
            }
        }
        return generated
    }

    fun createPolarArray(
        sources: List<CadEntity>,
        center: Point2D,
        itemCount: Int,
        fillAngleDeg: Double = 360.0,
        rotateItems: Boolean = true
    ): List<CadEntity> {
        val count = itemCount.coerceIn(2, 100)
        val stepAngle = if (abs(fillAngleDeg - 360.0) < 1e-3) {
            360.0 / count.toDouble()
        } else {
            fillAngleDeg / (count - 1).coerceAtLeast(1).toDouble()
        }
        val generated = mutableListOf<CadEntity>()
        for (i in 1 until count) {
            val angle = stepAngle * i.toDouble()
            sources.forEach { src ->
                val item = if (rotateItems) {
                    src.withNewId().rotate(center, angle)
                } else {
                    val c = src.boundingBox().center
                    val rotatedC = c.rotateAround(center, angle)
                    src.withNewId().translate(rotatedC.x - c.x, rotatedC.y - c.y)
                }
                generated.add(item)
            }
        }
        return generated
    }

    // --- ALIGN ---

    fun alignEntities(
        sources: List<CadEntity>,
        srcPt1: Point2D,
        dstPt1: Point2D,
        srcPt2: Point2D,
        dstPt2: Point2D,
        scaleToFit: Boolean
    ): List<CadEntity> {
        val srcLen = srcPt1.distanceTo(srcPt2)
        val dstLen = dstPt1.distanceTo(dstPt2)
        if (srcLen < 1e-6 || dstLen < 1e-6) {
            val dx = dstPt1.x - srcPt1.x
            val dy = dstPt1.y - srcPt1.y
            return sources.map { it.translate(dx, dy) }
        }
        val rotAngle = dstPt1.angleToDeg(dstPt2) - srcPt1.angleToDeg(srcPt2)
        val scaleFactor = if (scaleToFit) dstLen / srcLen else 1.0
        val dx = dstPt1.x - srcPt1.x
        val dy = dstPt1.y - srcPt1.y

        return sources.map { e ->
            e.translate(dx, dy)
                .rotate(dstPt1, rotAngle)
                .scale(dstPt1, scaleFactor)
        }
    }

    // --- LENGTHEN ---

    fun lengthenEntity(
        entity: CadEntity,
        pickPoint: Point2D,
        targetTotalLength: Double? = null,
        deltaLength: Double? = null,
        percent: Double? = null
    ): CadEntity {
        if (entity !is CadEntity.LineEntity) return entity
        val currentLen = entity.length.coerceAtLeast(1e-6)
        val newLen = when {
            targetTotalLength != null -> targetTotalLength
            deltaLength != null -> currentLen + deltaLength
            percent != null -> currentLen * (percent / 100.0)
            else -> currentLen
        }.coerceAtLeast(1e-3)

        val moveEnd = pickPoint.distanceTo(entity.end) <= pickPoint.distanceTo(entity.start)
        return if (moveEnd) {
            val angle = entity.start.angleToDeg(entity.end)
            entity.copy(end = entity.start.polarOffset(newLen, angle))
        } else {
            val angle = entity.end.angleToDeg(entity.start)
            entity.copy(start = entity.end.polarOffset(newLen, angle))
        }
    }
}
