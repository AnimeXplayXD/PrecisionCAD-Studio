package com.example.cad.model

enum class CadCommandCategory(val label: String) {
    DRAW("Draw (2D Geometry)"),
    MODIFY("Modify & Edit"),
    SELECT_INQUIRY("Select & Inquiry"),
    ANNOTATE_DIM("Annotate & QDIM"),
    DOCUMENT_SYSTEM("Document, Units & IO")
}

enum class CadCommandId(
    val commandName: String,
    val primaryAlias: String,
    val category: CadCommandCategory,
    val shortLabel: String
) {
    // Draw Commands
    LINE("LINE", "L", CadCommandCategory.DRAW, "Line"),
    POLYLINE("POLYLINE", "PL", CadCommandCategory.DRAW, "Polyline"),
    CIRCLE("CIRCLE", "C", CadCommandCategory.DRAW, "Circle"),
    ARC("ARC", "A", CadCommandCategory.DRAW, "Arc"),
    RECTANGLE("RECTANGLE", "REC", CadCommandCategory.DRAW, "Rect"),
    POLYGON("POLYGON", "POL", CadCommandCategory.DRAW, "Polygon"),
    ELLIPSE("ELLIPSE", "EL", CadCommandCategory.DRAW, "Ellipse"),
    SPLINE("SPLINE", "SPL", CadCommandCategory.DRAW, "Spline"),
    POINT("POINT", "PO", CadCommandCategory.DRAW, "Point"),
    RAY("RAY", "RAY", CadCommandCategory.DRAW, "Ray"),
    XLINE("XLINE", "XL", CadCommandCategory.DRAW, "XLine"),
    DONUT("DONUT", "DO", CadCommandCategory.DRAW, "Donut"),
    HATCH("HATCH", "H", CadCommandCategory.DRAW, "Hatch"),
    BOUNDARY("BOUNDARY", "BO", CadCommandCategory.DRAW, "Boundary"),

    // Modify Commands
    MOVE("MOVE", "M", CadCommandCategory.MODIFY, "Move"),
    COPY("COPY", "CO", CadCommandCategory.MODIFY, "Copy"),
    ROTATE("ROTATE", "RO", CadCommandCategory.MODIFY, "Rotate"),
    SCALE("SCALE", "SC", CadCommandCategory.MODIFY, "Scale"),
    MIRROR("MIRROR", "MI", CadCommandCategory.MODIFY, "Mirror"),
    OFFSET("OFFSET", "O", CadCommandCategory.MODIFY, "Offset"),
    TRIM("TRIM", "TR", CadCommandCategory.MODIFY, "Trim"),
    EXTEND("EXTEND", "EX", CadCommandCategory.MODIFY, "Extend"),
    FILLET("FILLET", "F", CadCommandCategory.MODIFY, "Fillet"),
    CHAMFER("CHAMFER", "CHA", CadCommandCategory.MODIFY, "Chamfer"),
    STRETCH("STRETCH", "S", CadCommandCategory.MODIFY, "Stretch"),
    BREAK("BREAK", "BR", CadCommandCategory.MODIFY, "Break"),
    JOIN("JOIN", "J", CadCommandCategory.MODIFY, "Join"),
    EXPLODE("EXPLODE", "X", CadCommandCategory.MODIFY, "Explode"),
    ARRAY("ARRAY", "AR", CadCommandCategory.MODIFY, "Array"),
    ALIGN("ALIGN", "AL", CadCommandCategory.MODIFY, "Align"),
    LENGTHEN("LENGTHEN", "LEN", CadCommandCategory.MODIFY, "Lengthen"),
    PEDIT("PEDIT", "PE", CadCommandCategory.MODIFY, "PEdit"),

    // Selection & Inquiry
    SELECT("SELECT", "SEL", CadCommandCategory.SELECT_INQUIRY, "Select"),
    MEASURE("DIST", "DI", CadCommandCategory.SELECT_INQUIRY, "Measure"),
    PROPERTIES("PROPERTIES", "PR", CadCommandCategory.SELECT_INQUIRY, "Props"),
    ISOLATE("ISOLATEOBJECTS", "ISO", CadCommandCategory.SELECT_INQUIRY, "Isolate"),

    // Annotate & QDIM
    QDIM("QDIM", "QD", CadCommandCategory.ANNOTATE_DIM, "QDIM Auto"),
    DIMLINEAR("DIMLINEAR", "DLI", CadCommandCategory.ANNOTATE_DIM, "Dim Lin"),
    DIMALIGNED("DIMALIGNED", "DAL", CadCommandCategory.ANNOTATE_DIM, "Dim Align"),
    DIMANGULAR("DIMANGULAR", "DAN", CadCommandCategory.ANNOTATE_DIM, "Dim Ang"),
    DIMRADIUS("DIMRADIUS", "DRA", CadCommandCategory.ANNOTATE_DIM, "Dim Rad"),
    DIMDIAMETER("DIMDIAMETER", "DDI", CadCommandCategory.ANNOTATE_DIM, "Dim Dia"),
    DIMORDINATE("DIMORDINATE", "DOR", CadCommandCategory.ANNOTATE_DIM, "Dim Ord"),
    LEADER("MLEADER", "LE", CadCommandCategory.ANNOTATE_DIM, "Leader"),
    TEXT("MTEXT", "T", CadCommandCategory.ANNOTATE_DIM, "Text"),
    CENTERMARK("CENTERMARK", "CM", CadCommandCategory.ANNOTATE_DIM, "Center"),

    // Document, Constraints & IO
    LAYER("LAYER", "LA", CadCommandCategory.DOCUMENT_SYSTEM, "Layers"),
    UNITS("UNITS", "UN", CadCommandCategory.DOCUMENT_SYSTEM, "Units"),
    PAGESETUP("PAGESETUP", "PS", CadCommandCategory.DOCUMENT_SYSTEM, "Sheet"),
    BLOCK("BLOCK", "B", CadCommandCategory.DOCUMENT_SYSTEM, "Block"),
    CONSTRAINT("GEOMCONSTRAINT", "GC", CadCommandCategory.DOCUMENT_SYSTEM, "Constrain"),
    DXF_IO("DXFOUT", "DXF", CadCommandCategory.DOCUMENT_SYSTEM, "DXF/PDF"),
    UNDO_REDO("UNDO", "U", CadCommandCategory.DOCUMENT_SYSTEM, "Undo/Redo");

    companion object {
        fun fromText(input: String): CadCommandId? {
            val token = input.trim().uppercase()
            if (token.isEmpty()) return null
            // 1. Check exact primary alias or commandName on CadCommandId
            entries.firstOrNull {
                it.primaryAlias.equals(token, ignoreCase = true) ||
                    it.commandName.equals(token, ignoreCase = true)
            }?.let { return it }
            // 2. Check secondary aliases in CadCommandCompatibilityCatalog
            return CadCommandCompatibilityCatalog.allSpecs.firstOrNull { spec ->
                spec.aliases.any { alias -> alias.equals(token, ignoreCase = true) }
            }?.id
        }
    }
}

data class CadCommandCompatibilitySpec(
    val id: CadCommandId,
    val aliases: List<String>,
    val supportedOptions: List<String>,
    val inputMethods: List<String>,
    val expectedOutput: String,
    val limitations: String,
    val testCaseId: String,
    val testCaseDescription: String
)

object CadCommandCompatibilityCatalog {

    /**
     * Section 14.3 — Prefix-based command autocomplete ranking.
     * Ranks commands matching [rawQuery] strictly in order of:
     * 1. Exact primary alias match (e.g. "L" -> LINE)
     * 2. Exact secondary alias or full command name match
     * 3. Primary alias prefix match (e.g. "L" -> LA, LE, LEN)
     * 4. Command name or short label prefix match
     * 5. Secondary alias prefix match
     * Unrelated commands that do not match the prefix are excluded.
     */
    fun rankSuggestionsForPrefix(rawQuery: String): List<CadCommandCompatibilitySpec> {
        val q = rawQuery.trim().uppercase()
        if (q.isEmpty()) return emptyList()
        // If user is typing a coordinate expression (contains ',', '<', '@', or starts with digit/minus), do not show command suggestions
        if (q.startsWith("@") || q.contains(",") || q.contains("<") || q.first().isDigit() || q.startsWith("-")) {
            return emptyList()
        }

        data class RankedSpec(val spec: CadCommandCompatibilitySpec, val tier: Int, val tieBreaker: Int)

        val matches = mutableListOf<RankedSpec>()
        for (spec in allSpecs) {
            val cmd = spec.id
            val cmdName = cmd.commandName.uppercase()
            val primaryAlias = cmd.primaryAlias.uppercase()
            val shortLabel = cmd.shortLabel.uppercase()
            val upperAliases = spec.aliases.map { it.uppercase() }

            val tier: Int? = when {
                primaryAlias == q -> 1
                cmdName == q || upperAliases.any { it == q } -> 2
                primaryAlias.startsWith(q) -> 3
                cmdName.startsWith(q) || shortLabel.startsWith(q) -> 4
                upperAliases.any { it.startsWith(q) } -> 5
                else -> null
            }
            if (tier != null) {
                matches.add(RankedSpec(spec, tier, primaryAlias.length * 10 + cmdName.length))
            }
        }
        return matches
            .sortedWith(compareBy<RankedSpec> { it.tier }.thenBy { it.tieBreaker }.thenBy { it.spec.id.commandName })
            .map { it.spec }
    }

    val allSpecs: List<CadCommandCompatibilitySpec> = listOf(
        // DRAW
        CadCommandCompatibilitySpec(
            id = CadCommandId.LINE,
            aliases = listOf("LINE", "L"),
            supportedOptions = listOf("Chain", "Close (C)", "Undo (U)", "Ortho/Polar Lock"),
            inputMethods = listOf("VC (Touchpad/Joystick)", "NUM (X,Y / @X,Y / L<θ)", "CMD", "OSNAP", "PTR"),
            expectedOutput = "Creates independent double-precision 2D LineEntity segments on active layer.",
            limitations = "2D Z=0 plane only; segments are individual unless joined via JOIN or drawn via POLYLINE.",
            testCaseId = "TC-DRW-01",
            testCaseDescription = "Enter 0,0 -> @100,0 -> @0,50 -> C; verify 3 closed segments with lengths 100, 50, 111.8034."
        ),
        CadCommandCompatibilitySpec(
            id = CadCommandId.POLYLINE,
            aliases = listOf("POLYLINE", "PL", "PLINE"),
            supportedOptions = listOf("Line", "Arc (Bulge)", "Close (C)", "Width (W)", "Undo (U)"),
            inputMethods = listOf("VC", "NUM", "CMD", "OSNAP", "PTR"),
            expectedOutput = "Creates connected PolylineEntity (LWPOLYLINE) storing ordered vertices (x,y,bulge) and closed flag.",
            limitations = "Variable start/end segment taper is normalized to constant global width on DXF R12 fallback.",
            testCaseId = "TC-DRW-02",
            testCaseDescription = "Create 4-vertex polyline with semicircle bulge=1.0 and Close; verify closed perimeter and vertex count."
        ),
        CadCommandCompatibilitySpec(
            id = CadCommandId.CIRCLE,
            aliases = listOf("CIRCLE", "C"),
            supportedOptions = listOf("Center-Radius", "Center-Diameter (D)", "2-Point (2P)", "3-Point (3P)", "Tan-Tan-Radius (TTR)"),
            inputMethods = listOf("VC", "NUM", "CMD", "OSNAP", "PTR"),
            expectedOutput = "Creates CircleEntity(center, radius); 3P solves circumcircle; TTR solves tangent analytical circle.",
            limitations = "3P rejects collinear points; TTR requires radius large enough to bridge both reference lines/circles.",
            testCaseId = "TC-DRW-03",
            testCaseDescription = "Run CIRCLE 3P on (0,10), (10,0), (0,-10) -> verify center (0,0) and R=10.0."
        ),
        CadCommandCompatibilitySpec(
            id = CadCommandId.ARC,
            aliases = listOf("ARC", "A"),
            supportedOptions = listOf("3-Point (3P)", "Start-Center-End (SCE)", "Center-Start-Angle (CSA)", "CCW/CW Direction"),
            inputMethods = listOf("VC", "NUM", "CMD", "OSNAP", "PTR"),
            expectedOutput = "Creates ArcEntity(center, radius, startAngleDeg, sweepAngleDeg).",
            limitations = "Elliptical arcs are handled via ELLIPSE or SPLINE entities.",
            testCaseId = "TC-DRW-04",
            testCaseDescription = "Create SCE arc Start (10,0), Center (0,0), End (0,10); verify R=10, sweep=+90°, length=15.7080."
        ),
        CadCommandCompatibilitySpec(
            id = CadCommandId.RECTANGLE,
            aliases = listOf("RECTANGLE", "REC", "RECT"),
            supportedOptions = listOf("Corner-to-Corner", "Center-Corner (C)", "Dimensions (D)", "Fillet (F)", "Chamfer (CH)"),
            inputMethods = listOf("VC", "NUM", "CMD", "OSNAP", "PTR"),
            expectedOutput = "Creates closed 4- or 8-vertex PolylineEntity with exact orthogonal width and height.",
            limitations = "Editing individual edges independently requires EXPLODE or PEDIT vertex mode.",
            testCaseId = "TC-DRW-05",
            testCaseDescription = "Create REC at (10,10) with W=80, H=40 and Fillet R=5; verify 8-vertex closed rounded rectangle."
        ),
        CadCommandCompatibilitySpec(
            id = CadCommandId.POLYGON,
            aliases = listOf("POLYGON", "POL"),
            supportedOptions = listOf("Sides (3..64)", "Inscribed (I)", "Circumscribed (C)", "Edge (E)"),
            inputMethods = listOf("VC", "NUM", "CMD", "OSNAP", "PTR"),
            expectedOutput = "Creates regular closed N-sided PolylineEntity with equal sides and interior angles.",
            limitations = "Sides capped at 3..64 to maintain real-time OSNAP responsiveness.",
            testCaseId = "TC-DRW-06",
            testCaseDescription = "Create 6-sided Circumscribed hex at (0,0) R=10; verify vertex radius 11.5470 and 6 equal edges."
        ),
        CadCommandCompatibilitySpec(
            id = CadCommandId.ELLIPSE,
            aliases = listOf("ELLIPSE", "EL"),
            supportedOptions = listOf("Center-Axes (C)", "Axis-End", "Rotation Angle"),
            inputMethods = listOf("VC", "NUM", "CMD", "OSNAP", "PTR"),
            expectedOutput = "Creates EllipseEntity(center, majorRadius, minorRadius, rotationAngleDeg).",
            limitations = "Trimming an ellipse converts the trimmed span into a high-density polyline curve.",
            testCaseId = "TC-DRW-07",
            testCaseDescription = "Create ELLIPSE at (0,0), major (20,0), minor 10; verify quadrant OSNAPs at (±20,0) and (0,±10)."
        ),
        CadCommandCompatibilitySpec(
            id = CadCommandId.SPLINE,
            aliases = listOf("SPLINE", "SPL"),
            supportedOptions = listOf("Fit Points (Catmull-Rom)", "Control Vertices (CV)", "Close (C)"),
            inputMethods = listOf("VC", "NUM", "CMD", "OSNAP", "PTR"),
            expectedOutput = "Creates smooth cubic SplineEntity passing through fit points and exports DXF SPLINE.",
            limitations = "Intersections evaluate against adaptive curve subdivisions (tol <= 0.01).",
            testCaseId = "TC-DRW-08",
            testCaseDescription = "Create 4-point spline (0,0)->(10,15)->(25,-5)->(40,10); verify smooth interpolation and DXF export."
        ),
        CadCommandCompatibilitySpec(
            id = CadCommandId.POINT,
            aliases = listOf("POINT", "PO"),
            supportedOptions = listOf("Single", "Multiple", "PDMODE Cross/Circle Marker"),
            inputMethods = listOf("VC", "NUM", "CMD", "OSNAP", "PTR"),
            expectedOutput = "Places PointEntity node acting as an exact Endpoint/Node OSNAP target.",
            limitations = "Marker display size is controlled by document PDSIZE setting.",
            testCaseId = "TC-DRW-09",
            testCaseDescription = "Place point at (33.25, 18.75) and verify OSNAP locks onto exact (33.25, 18.75)."
        ),
        CadCommandCompatibilitySpec(
            id = CadCommandId.RAY,
            aliases = listOf("RAY"),
            supportedOptions = listOf("Base Point + Through Point", "Angle (∠θ)"),
            inputMethods = listOf("VC", "NUM", "CMD", "OSNAP", "PTR"),
            expectedOutput = "Creates semi-infinite RayEntity starting at basePoint along angleDeg.",
            limitations = "Converts to finite LineEntity when trimmed at outer span.",
            testCaseId = "TC-DRW-10",
            testCaseDescription = "Create RAY from (0,0) at 45°; verify intersection with X=250 resolves at (250,250)."
        ),
        CadCommandCompatibilitySpec(
            id = CadCommandId.XLINE,
            aliases = listOf("XLINE", "XL"),
            supportedOptions = listOf("Two-Point", "Horizontal (H)", "Vertical (V)", "Angle (A)", "Bisect (B)", "Offset (O)"),
            inputMethods = listOf("VC", "NUM", "CMD", "OSNAP", "PTR"),
            expectedOutput = "Creates infinite construction line XLineEntity on CONSTRUCTION layer.",
            limitations = "Trimming once converts XLINE to RAY; trimming twice converts to finite LineEntity.",
            testCaseId = "TC-DRW-11",
            testCaseDescription = "Create XLINE Bisect between X-axis and Y-axis at (0,0); verify infinite line at 45.0°."
        ),
        CadCommandCompatibilitySpec(
            id = CadCommandId.DONUT,
            aliases = listOf("DONUT", "DO"),
            supportedOptions = listOf("Inside Diameter (ID)", "Outside Diameter (OD)"),
            inputMethods = listOf("VC", "NUM", "CMD", "OSNAP", "PTR"),
            expectedOutput = "Creates annular DonutEntity with inner/outer diameter and filled ring display.",
            limitations = "Requires OD > ID >= 0.",
            testCaseId = "TC-DRW-12",
            testCaseDescription = "Create DONUT ID=10, OD=20 at (50,50); verify bounding box 30..70 and inner/outer radii 5 & 10."
        ),
        CadCommandCompatibilitySpec(
            id = CadCommandId.HATCH,
            aliases = listOf("HATCH", "H", "BHATCH"),
            supportedOptions = listOf("ANSI31", "ANSI32", "ANSI37", "SOLID", "GRID", "DOTS", "Scale", "Angle", "Pick Internal Point / Select Object"),
            inputMethods = listOf("VC", "NUM", "CMD", "OSNAP", "PTR"),
            expectedOutput = "Creates HatchEntity bound to closed boundary contour with section lines.",
            limitations = "Pick-point boundary trace requires closed loop within 0.05 gap tolerance.",
            testCaseId = "TC-DRW-13",
            testCaseDescription = "Hatch a 100×50 closed rectangle with ANSI31 (Scale=1, Angle=45°); verify hatch entity creation."
        ),
        CadCommandCompatibilitySpec(
            id = CadCommandId.BOUNDARY,
            aliases = listOf("BOUNDARY", "BO"),
            supportedOptions = listOf("Pick Internal Point", "Closed Polyline Output"),
            inputMethods = listOf("VC", "CMD", "PTR"),
            expectedOutput = "Traces minimal enclosing closed loop around seed point and creates closed PolylineEntity.",
            limitations = "Open contours report warning if loop is not closed.",
            testCaseId = "TC-DRW-14",
            testCaseDescription = "Draw 4 overlapping lines forming 40×40 square; run BOUNDARY at (20,20) -> verify closed 40×40 polyline."
        ),

        // MODIFY
        CadCommandCompatibilitySpec(
            id = CadCommandId.MOVE,
            aliases = listOf("MOVE", "M"),
            supportedOptions = listOf("Base Point + Second Point", "Displacement (@dx,dy / L<θ)"),
            inputMethods = listOf("VC", "NUM", "CMD", "OSNAP", "PTR"),
            expectedOutput = "Translates selected entities by (dx,dy) and updates associative dimensions.",
            limitations = "Entities on locked layers are automatically excluded.",
            testCaseId = "TC-MOD-01",
            testCaseDescription = "Move circle at (10,20) by @15,-5; verify center updates to (25,15)."
        ),
        CadCommandCompatibilitySpec(
            id = CadCommandId.COPY,
            aliases = listOf("COPY", "CO", "CP"),
            supportedOptions = listOf("Single", "Multiple", "Array (Count & Spacing)"),
            inputMethods = listOf("VC", "NUM", "CMD", "OSNAP", "PTR"),
            expectedOutput = "Duplicates selected entities with new UUIDs at target offsets.",
            limitations = "Unselected dimensions are not duplicated unless included in selection.",
            testCaseId = "TC-MOD-02",
            testCaseDescription = "Copy line with Array count=4 and spacing @20,0; verify 3 new lines at +20, +40, +60."
        ),
        CadCommandCompatibilitySpec(
            id = CadCommandId.ROTATE,
            aliases = listOf("ROTATE", "RO"),
            supportedOptions = listOf("Base Point + Angle (°)", "Copy (C)", "Reference (R)"),
            inputMethods = listOf("VC", "NUM", "CMD", "OSNAP", "PTR"),
            expectedOutput = "Rotates selected entities around pivot by exact 2D rotation matrix.",
            limitations = "Positive angles rotate counter-clockwise (CCW).",
            testCaseId = "TC-MOD-03",
            testCaseDescription = "Rotate line (10,0)-(20,0) around (0,0) by +90°; verify endpoints (0,10)-(0,20)."
        ),
        CadCommandCompatibilitySpec(
            id = CadCommandId.SCALE,
            aliases = listOf("SCALE", "SC"),
            supportedOptions = listOf("Base Point + Scale Factor (s > 0)", "Copy (C)", "Reference (R)"),
            inputMethods = listOf("VC", "NUM", "CMD", "OSNAP", "PTR"),
            expectedOutput = "Uniformly scales coordinates and radii from basePoint by factor s.",
            limitations = "Rejects scale factor s <= 0.",
            testCaseId = "TC-MOD-04",
            testCaseDescription = "Scale 50×30 rectangle from (0,0) by factor 1.5; verify bounds 75×45."
        ),
        CadCommandCompatibilitySpec(
            id = CadCommandId.MIRROR,
            aliases = listOf("MIRROR", "MI"),
            supportedOptions = listOf("2-Point Mirror Axis", "Erase Source Objects (Yes/No)"),
            inputMethods = listOf("VC", "NUM", "CMD", "OSNAP", "PTR"),
            expectedOutput = "Reflects geometry across axis line; preserves right-reading text (MIRRTEXT=0).",
            limitations = "Requires two distinct axis points.",
            testCaseId = "TC-MOD-05",
            testCaseDescription = "Mirror arc at (20,10) across Y-axis (0,0)-(0,100); verify mirrored arc at (-20,10)."
        ),
        CadCommandCompatibilitySpec(
            id = CadCommandId.OFFSET,
            aliases = listOf("OFFSET", "O"),
            supportedOptions = listOf("Distance (d > 0)", "Through Point (T)", "Side Pick"),
            inputMethods = listOf("VC", "NUM", "CMD", "OSNAP", "PTR"),
            expectedOutput = "Creates parallel line, concentric circle/arc, or mitered offset polyline.",
            limitations = "Inward circle offset with d >= radius is rejected.",
            testCaseId = "TC-MOD-06",
            testCaseDescription = "Offset 100×60 closed rectangle inward by 5.0; verify resulting 90×50 closed polyline."
        ),
        CadCommandCompatibilitySpec(
            id = CadCommandId.TRIM,
            aliases = listOf("TRIM", "TR"),
            supportedOptions = listOf("Quick All-Edges Mode", "Pick Segment to Remove"),
            inputMethods = listOf("VC", "CMD", "OSNAP", "PTR"),
            expectedOutput = "Splits target curve at analytical intersections with cutting edges and removes picked span.",
            limitations = "Entities with zero intersections cannot be trimmed.",
            testCaseId = "TC-MOD-07",
            testCaseDescription = "Trim middle span of line (-50,0)-(50,0) between X=-10 and X=10; verify 2 outer lines remain."
        ),
        CadCommandCompatibilitySpec(
            id = CadCommandId.EXTEND,
            aliases = listOf("EXTEND", "EX"),
            supportedOptions = listOf("Quick All-Boundaries Mode", "Pick Nearer Endpoint"),
            inputMethods = listOf("VC", "CMD", "OSNAP", "PTR"),
            expectedOutput = "Projects nearer endpoint of line/arc along its trajectory to nearest boundary intersection.",
            limitations = "Closed circles/polylines cannot be extended.",
            testCaseId = "TC-MOD-08",
            testCaseDescription = "Extend line (0,0)-(30,0) toward vertical line at X=50; verify endpoint becomes (50,0)."
        ),
        CadCommandCompatibilitySpec(
            id = CadCommandId.FILLET,
            aliases = listOf("FILLET", "F"),
            supportedOptions = listOf("Radius (R >= 0)", "Trim / No Trim", "Polyline (P)"),
            inputMethods = listOf("VC", "NUM", "CMD", "OSNAP", "PTR"),
            expectedOutput = "Trims/extends two lines and inserts a tangent ArcEntity of radius R (R=0 makes sharp corner).",
            limitations = "Parallel lines cannot be filleted; R must fit within segment lengths when Trim=true.",
            testCaseId = "TC-MOD-09",
            testCaseDescription = "Fillet (0,0)-(50,0) and (0,0)-(0,50) with R=10; verify tangent 90° arc centered at (10,10)."
        ),
        CadCommandCompatibilitySpec(
            id = CadCommandId.CHAMFER,
            aliases = listOf("CHAMFER", "CHA"),
            supportedOptions = listOf("Distances (D1, D2)", "Angle (A)", "Trim / No Trim", "Polyline (P)"),
            inputMethods = listOf("VC", "NUM", "CMD", "OSNAP", "PTR"),
            expectedOutput = "Bevels corner between two non-parallel lines by setback distances D1 and D2.",
            limitations = "Applies to linear segments; circular arcs use FILLET.",
            testCaseId = "TC-MOD-10",
            testCaseDescription = "Chamfer (0,0)-(40,0) and (0,0)-(0,40) with D1=5, D2=5; verify bevel line (5,0)-(0,5) length 7.0711."
        ),
        CadCommandCompatibilitySpec(
            id = CadCommandId.STRETCH,
            aliases = listOf("STRETCH", "S"),
            supportedOptions = listOf("Crossing Window Region + Displacement (@dx,dy)"),
            inputMethods = listOf("VC", "NUM", "CMD", "OSNAP", "PTR"),
            expectedOutput = "Displaces endpoints/vertices inside crossing region while anchoring outside vertices.",
            limitations = "Circles move only if their center lies inside the crossing window.",
            testCaseId = "TC-MOD-11",
            testCaseDescription = "Stretch right edge of 100×50 rectangle by @25,0; verify width becomes 125.0 and dimension updates."
        ),
        CadCommandCompatibilitySpec(
            id = CadCommandId.BREAK,
            aliases = listOf("BREAK", "BR"),
            supportedOptions = listOf("Two-Point Gap Break", "Single-Point Split (@)"),
            inputMethods = listOf("VC", "NUM", "CMD", "OSNAP", "PTR"),
            expectedOutput = "Splits line/arc/circle/polyline into two entities or removes segment between P1 and P2.",
            limitations = "Breaking a circle requires two distinct angles and produces an ArcEntity.",
            testCaseId = "TC-MOD-12",
            testCaseDescription = "Break line (0,0)-(100,0) between (30,0) and (60,0); verify two segments (0,0)-(30,0) and (60,0)-(100,0)."
        ),
        CadCommandCompatibilitySpec(
            id = CadCommandId.JOIN,
            aliases = listOf("JOIN", "J"),
            supportedOptions = listOf("Collinear Lines", "Contiguous Endpoint Chain"),
            inputMethods = listOf("VC", "CMD", "PTR"),
            expectedOutput = "Merges collinear lines into one LineEntity or contiguous segments into one PolylineEntity.",
            limitations = "Endpoints must meet within 1e-3 model tolerance.",
            testCaseId = "TC-MOD-13",
            testCaseDescription = "Join (0,0)-(20,0) and (20,0)-(20,30); verify 3-vertex PolylineEntity of total length 50.0."
        ),
        CadCommandCompatibilitySpec(
            id = CadCommandId.EXPLODE,
            aliases = listOf("EXPLODE", "X"),
            supportedOptions = listOf("Polyline", "Block Insert", "Dimension"),
            inputMethods = listOf("VC", "CMD", "PTR"),
            expectedOutput = "Decomposes compound polylines or blocks into primitive LineEntity and ArcEntity objects.",
            limitations = "Primitive lines, circles, and points cannot be exploded further.",
            testCaseId = "TC-MOD-14",
            testCaseDescription = "Explode 4-sided closed rectangle polyline; verify 4 independent LineEntity objects."
        ),
        CadCommandCompatibilitySpec(
            id = CadCommandId.ARRAY,
            aliases = listOf("ARRAY", "AR"),
            supportedOptions = listOf("Rectangular (Rows, Cols, RowSpacing, ColSpacing)", "Polar (Center, Count, FillAngle, Rotate)"),
            inputMethods = listOf("VC", "NUM", "CMD", "OSNAP", "PTR"),
            expectedOutput = "Generates rectangular grid or circular bolt-circle pattern of selected entities.",
            limitations = "Count per array operation capped at 100 items.",
            testCaseId = "TC-MOD-15",
            testCaseDescription = "Polar array ⌀8 hole at (30,0) around (0,0) Count=6; verify 6 holes at 60° intervals on R=30."
        ),
        CadCommandCompatibilitySpec(
            id = CadCommandId.ALIGN,
            aliases = listOf("ALIGN", "AL"),
            supportedOptions = listOf("2 Source-Destination Point Pairs", "Scale Objects (Yes/No)"),
            inputMethods = listOf("VC", "NUM", "CMD", "OSNAP", "PTR"),
            expectedOutput = "Translates, rotates, and optionally scales selected geometry to align with target points.",
            limitations = "2D planar transformation (2 reference pairs).",
            testCaseId = "TC-MOD-16",
            testCaseDescription = "Align horizontal 50mm line (0,0)-(50,0) to (10,10)-(40,50) with Scale=Yes; verify exact match."
        ),
        CadCommandCompatibilitySpec(
            id = CadCommandId.LENGTHEN,
            aliases = listOf("LENGTHEN", "LEN"),
            supportedOptions = listOf("Delta (DE)", "Percent (P)", "Total (T)"),
            inputMethods = listOf("VC", "NUM", "CMD", "OSNAP", "PTR"),
            expectedOutput = "Changes total length of LineEntity or sweep angle of ArcEntity from picked end.",
            limitations = "Not applicable to closed circles or closed polylines.",
            testCaseId = "TC-MOD-17",
            testCaseDescription = "Lengthen line (0,0)-(80,0) to Total=120 near end (80,0); verify endpoint updates to (120,0)."
        ),
        CadCommandCompatibilitySpec(
            id = CadCommandId.PEDIT,
            aliases = listOf("PEDIT", "PE"),
            supportedOptions = listOf("Close / Open", "Join", "Width", "Reverse", "Convert Line to Polyline"),
            inputMethods = listOf("VC", "NUM", "CMD", "OSNAP", "PTR"),
            expectedOutput = "Updates polyline closure, global width, or vertex order.",
            limitations = "Self-intersecting polylines trigger a hatch boundary warning.",
            testCaseId = "TC-MOD-18",
            testCaseDescription = "Toggle Open polyline to Closed and set Width=1.5 via PEDIT; verify closed state and width."
        ),

        // SELECT & INQUIRY
        CadCommandCompatibilitySpec(
            id = CadCommandId.SELECT,
            aliases = listOf("SELECT", "SEL", "ALL"),
            supportedOptions = listOf("Single Pick", "Window (L->R Blue Enclosed)", "Crossing (R->L Green Intersect)", "Select All", "Filter"),
            inputMethods = listOf("VC", "CMD", "PTR"),
            expectedOutput = "Updates active selection set and displays grip handles + object summary.",
            limitations = "Frozen layer entities are not selectable.",
            testCaseId = "TC-SEL-01",
            testCaseDescription = "Window-select vs Crossing-select over partial line and full circle; verify enclosed vs crossing logic."
        ),
        CadCommandCompatibilitySpec(
            id = CadCommandId.MEASURE,
            aliases = listOf("DIST", "DI", "MEASUREGEOM", "AREA"),
            supportedOptions = listOf("Distance (ΔX, ΔY, L, ∠θ)", "Area & Perimeter"),
            inputMethods = listOf("VC", "NUM", "CMD", "OSNAP", "PTR"),
            expectedOutput = "Computes exact Euclidean distance, delta components, angle, and closed area in active units.",
            limitations = "Read-only inquiry command; does not modify geometry.",
            testCaseId = "TC-INQ-01",
            testCaseDescription = "Measure between (10,15) and (40,55); verify Dist=50.000, ΔX=30.000, ΔY=40.000, Angle=53.13°."
        ),
        CadCommandCompatibilitySpec(
            id = CadCommandId.PROPERTIES,
            aliases = listOf("PROPERTIES", "PR", "CHPROP"),
            supportedOptions = listOf("Layer", "Color", "Linetype", "Lineweight", "Geometric Coordinates/Radii"),
            inputMethods = listOf("VC", "NUM", "CMD", "PTR"),
            expectedOutput = "Mutates selected entities' properties and recalculates associative dimensions.",
            limitations = "Locked layer entities are read-only until unlocked.",
            testCaseId = "TC-INQ-02",
            testCaseDescription = "Change circle radius from 15 to 22.5 in Properties; verify associative diameter dimension reads ⌀45.0."
        ),
        CadCommandCompatibilitySpec(
            id = CadCommandId.ISOLATE,
            aliases = listOf("ISOLATEOBJECTS", "ISO", "UNISOLATE"),
            supportedOptions = listOf("Isolate Selected", "Hide Selected", "Unisolate All"),
            inputMethods = listOf("CMD", "PTR"),
            expectedOutput = "Temporarily hides unselected objects for clean editing and restores them on UNISOLATE.",
            limitations = "Session-scoped visibility filter.",
            testCaseId = "TC-INQ-03",
            testCaseDescription = "Isolate 2 entities out of 10, then Unisolate; verify all 10 entities return."
        ),

        // ANNOTATE & QDIM
        CadCommandCompatibilitySpec(
            id = CadCommandId.QDIM,
            aliases = listOf("QDIM", "QD"),
            supportedOptions = listOf("QDIM ALL", "QDIM SELECT", "QDIM REGION", "Density: Sparse / Balanced / Detailed"),
            inputMethods = listOf("VC", "CMD", "PTR"),
            expectedOutput = "Automatically analyzes geometry and places collision-free overall, hole diameter, pitch, edge-offset, and radial dimensions.",
            limitations = "Preserves existing manual annotations; suppresses redundant pairwise hole-to-hole clutter.",
            testCaseId = "TC-DIM-01",
            testCaseDescription = "Run QDIM ALL (Balanced) on 120×80 plate with 6 holes; verify overall 120 & 80, 6× ⌀hole callout, and hole pitch chain."
        ),
        CadCommandCompatibilitySpec(
            id = CadCommandId.DIMLINEAR,
            aliases = listOf("DIMLINEAR", "DLI"),
            supportedOptions = listOf("Horizontal", "Vertical", "Baseline / Chain"),
            inputMethods = listOf("VC", "NUM", "CMD", "OSNAP", "PTR"),
            expectedOutput = "Creates associative horizontal or vertical linear DimensionEntity.",
            limitations = "Uses DIMALIGNED for non-orthogonal true-length measurements.",
            testCaseId = "TC-DIM-02",
            testCaseDescription = "Create horizontal DIMLINEAR from (0,0) to (75.5,20); verify measured value 75.500."
        ),
        CadCommandCompatibilitySpec(
            id = CadCommandId.DIMALIGNED,
            aliases = listOf("DIMALIGNED", "DAL"),
            supportedOptions = listOf("2 Extension Points", "Select Segment"),
            inputMethods = listOf("VC", "NUM", "CMD", "OSNAP", "PTR"),
            expectedOutput = "Creates associative DimensionEntity parallel to measured points.",
            limitations = "Requires non-coincident anchor points.",
            testCaseId = "TC-DIM-03",
            testCaseDescription = "Dimension inclined line (0,0)-(30,40) with DIMALIGNED; verify value 50.000."
        ),
        CadCommandCompatibilitySpec(
            id = CadCommandId.DIMANGULAR,
            aliases = listOf("DIMANGULAR", "DAN"),
            supportedOptions = listOf("2 Non-Parallel Lines", "3-Point Vertex & Arms"),
            inputMethods = listOf("VC", "NUM", "CMD", "OSNAP", "PTR"),
            expectedOutput = "Creates angular DimensionEntity showing angle in degrees (°).",
            limitations = "Rejects parallel lines.",
            testCaseId = "TC-DIM-04",
            testCaseDescription = "Dimension angle between (0,0)-(50,0) and (0,0)-(50,50); verify 45.0°."
        ),
        CadCommandCompatibilitySpec(
            id = CadCommandId.DIMRADIUS,
            aliases = listOf("DIMRADIUS", "DRA"),
            supportedOptions = listOf("Pick Circle/Arc", "Adaptive Internal/External Arrow Flip"),
            inputMethods = listOf("VC", "NUM", "CMD", "OSNAP", "PTR"),
            expectedOutput = "Creates associative R dimension with automatic external arrow flip for small radii.",
            limitations = "Applies only to CircleEntity and ArcEntity.",
            testCaseId = "TC-DIM-05A",
            testCaseDescription = "Apply DIMRADIUS to R=15 arc; verify R15.000 label and associative update on scale."
        ),
        CadCommandCompatibilitySpec(
            id = CadCommandId.DIMDIAMETER,
            aliases = listOf("DIMDIAMETER", "DDI"),
            supportedOptions = listOf("Pick Circle/Arc", "Pattern Count Prefix (e.g. 6× ⌀10)"),
            inputMethods = listOf("VC", "NUM", "CMD", "OSNAP", "PTR"),
            expectedOutput = "Creates associative ⌀ dimension with external leader placement on tight holes.",
            limitations = "Applies only to CircleEntity and ArcEntity.",
            testCaseId = "TC-DIM-05B",
            testCaseDescription = "Apply DIMDIAMETER to ⌀2.5mm hole; verify external arrow/text placement without obscuring hole."
        ),
        CadCommandCompatibilitySpec(
            id = CadCommandId.DIMORDINATE,
            aliases = listOf("DIMORDINATE", "DOR"),
            supportedOptions = listOf("X-Datum", "Y-Datum"),
            inputMethods = listOf("VC", "NUM", "CMD", "OSNAP", "PTR"),
            expectedOutput = "Creates datum coordinate callout relative to drawing origin (0,0).",
            limitations = "References model origin (0,0).",
            testCaseId = "TC-DIM-06",
            testCaseDescription = "Place X-Datum and Y-Datum on (42.5, 18.0); verify X=42.500 and Y=18.000."
        ),
        CadCommandCompatibilitySpec(
            id = CadCommandId.LEADER,
            aliases = listOf("MLEADER", "LEADER", "LE"),
            supportedOptions = listOf("Arrow Tip -> Shoulder -> Note", "GD&T Tolerance Callout"),
            inputMethods = listOf("VC", "NUM", "CMD", "OSNAP", "PTR"),
            expectedOutput = "Creates LeaderEntity with arrowhead, angled leader, horizontal landing, and note text.",
            limitations = "3-point standard callout geometry.",
            testCaseId = "TC-DIM-07",
            testCaseDescription = "Create MLEADER at (30,20) with text 'CHAMFER 1×45°'; verify entity and DXF export."
        ),
        CadCommandCompatibilitySpec(
            id = CadCommandId.TEXT,
            aliases = listOf("MTEXT", "TEXT", "T", "DT"),
            supportedOptions = listOf("Insertion Point", "Text Height", "Rotation Angle", "Content"),
            inputMethods = listOf("VC", "NUM", "CMD", "OSNAP", "PTR"),
            expectedOutput = "Creates technical TextEntity rendered in JetBrains Mono vector typography.",
            limitations = "Always remains right-reading when mirrored.",
            testCaseId = "TC-DIM-08",
            testCaseDescription = "Insert MTEXT 'SECTION A-A' height 4.0 at (10,10); verify bounds and DXF MTEXT output."
        ),
        CadCommandCompatibilitySpec(
            id = CadCommandId.CENTERMARK,
            aliases = listOf("CENTERMARK", "CM", "CENTERLINE"),
            supportedOptions = listOf("Pick Circle or Arc"),
            inputMethods = listOf("VC", "CMD", "OSNAP", "PTR"),
            expectedOutput = "Generates associative dash-dot CenterMarkEntity at circle/arc center.",
            limitations = "Overshoot scales proportionally to radius.",
            testCaseId = "TC-DIM-09",
            testCaseDescription = "Apply CENTERMARK to ⌀40 circle at (50,50); verify crosshairs centered at (50,50)."
        ),

        // DOCUMENT & SYSTEM
        CadCommandCompatibilitySpec(
            id = CadCommandId.LAYER,
            aliases = listOf("LAYER", "LA"),
            supportedOptions = listOf("New", "Set Current", "Visible", "Lock", "Freeze", "Color", "Linetype", "Lineweight"),
            inputMethods = listOf("CMD", "PTR"),
            expectedOutput = "Updates layer table and immediately refreshes viewport styling and lock protection.",
            limitations = "Layer '0' cannot be deleted.",
            testCaseId = "TC-DOC-01",
            testCaseDescription = "Lock layer CONSTRUCTION and attempt MOVE; verify locked entities remain immovable."
        ),
        CadCommandCompatibilitySpec(
            id = CadCommandId.UNITS,
            aliases = listOf("UNITS", "UN", "DWGUNITS"),
            supportedOptions = listOf("mm / cm / m / in / ft", "Mode A: Convert Display Units Only", "Mode B: Rescale Model Geometry", "Precision 0..6"),
            inputMethods = listOf("CMD", "PTR"),
            expectedOutput = "Mode A converts displayed measurements non-destructively; Mode B rescales stored coordinates.",
            limitations = "Requires explicit user confirmation between Mode A and Mode B.",
            testCaseId = "TC-DOC-02",
            testCaseDescription = "Draw 100mm line; switch to cm in Mode A -> reads 10.000 cm while stored length stays 100.0; Mode B rescales to 10.0."
        ),
        CadCommandCompatibilitySpec(
            id = CadCommandId.PAGESETUP,
            aliases = listOf("PAGESETUP", "PS", "LAYOUT"),
            supportedOptions = listOf("Model Space vs Paper Space", "ISO A0..A4 / ANSI A..D / Custom", "Portrait / Landscape", "Plot Scale", "Title Block"),
            inputMethods = listOf("CMD", "PTR"),
            expectedOutput = "Configures printable sheet size, orientation, scale, and title block without altering model geometry.",
            limitations = "Sheet dimensions and model-space coordinates remain strictly independent.",
            testCaseId = "TC-DOC-03",
            testCaseDescription = "Change sheet from ISO A4 Portrait to ISO A3 Landscape at 1:2 scale; verify model geometry is unchanged."
        ),
        CadCommandCompatibilitySpec(
            id = CadCommandId.BLOCK,
            aliases = listOf("BLOCK", "B", "INSERT", "I"),
            supportedOptions = listOf("Create Block Definition", "Insert Block Instance (Scale, Rotation)"),
            inputMethods = listOf("VC", "NUM", "CMD", "OSNAP", "PTR"),
            expectedOutput = "Stores reusable BlockDefinition and places BlockInsertEntity instances.",
            limitations = "Exploding an instance restores independent constituent entities.",
            testCaseId = "TC-DOC-04",
            testCaseDescription = "Create block 'M8_BOLT' and insert at (50,50) scale 1.0 rot 45°; verify placement and DXF INSERT record."
        ),
        CadCommandCompatibilitySpec(
            id = CadCommandId.CONSTRAINT,
            aliases = listOf("GEOMCONSTRAINT", "GC", "DIMCONSTRAINT"),
            supportedOptions = listOf("Horizontal", "Vertical", "Parallel", "Perpendicular", "Equal", "Concentric", "Tangent", "Coincident", "Fixed"),
            inputMethods = listOf("VC", "NUM", "CMD", "OSNAP", "PTR"),
            expectedOutput = "Applies persistent 2D geometric constraint and solves entity alignment.",
            limitations = "Flags conflicting over-constraints without corrupting valid geometry.",
            testCaseId = "TC-DOC-05",
            testCaseDescription = "Apply Perpendicular and Equal constraints to two lines; verify 90.0° angle and identical lengths."
        ),
        CadCommandCompatibilitySpec(
            id = CadCommandId.DXF_IO,
            aliases = listOf("DXFOUT", "DXFIN", "EXPORTPDF", "EXPORTSVG"),
            supportedOptions = listOf("DXF Import", "DXF Export", "PDF Sheet Plot", "SVG Vector Export"),
            inputMethods = listOf("CMD", "PTR"),
            expectedOutput = "Round-trips layers, entities, blocks, hatches, and dimensions to/from ASCII DXF and exports PDF/SVG.",
            limitations = "Binary DWG requires DXF interchange; 3D solid entities in external DXFs are ignored.",
            testCaseId = "TC-DOC-06",
            testCaseDescription = "Export drawing to DXF and re-import; verify 100% preservation of entities, layers, units, and dimensions."
        ),
        CadCommandCompatibilitySpec(
            id = CadCommandId.UNDO_REDO,
            aliases = listOf("UNDO", "U", "REDO"),
            supportedOptions = listOf("Multi-Step Undo", "Multi-Step Redo"),
            inputMethods = listOf("CMD", "PTR"),
            expectedOutput = "Restores exact prior or subsequent document state across all commands.",
            limitations = "Session transaction stack up to 100 snapshots.",
            testCaseId = "TC-DOC-07",
            testCaseDescription = "Execute LINE, FILLET, QDIM ALL, then UNDO 3x and REDO 3x; verify exact state restoration."
        )
    )
}
