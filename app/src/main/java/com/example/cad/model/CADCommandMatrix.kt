package com.example.cad.model

import java.util.Locale

/**
 * Verification test case definition for a CAD command in the compatibility matrix.
 */
data class CADVerificationTestCase(
    val testCaseId: String,
    val title: String,
    val procedure: String,
    val expectedResult: String
)

/**
 * Data class structure storing a single command's specification in the
 * CAD Command Compatibility Matrix.
 */
data class CADCommandMatrixEntry(
    val commandId: CadCommandId,
    val commandName: String,
    val aliases: List<String>,
    val category: CadCommandCategory,
    val supportedOptions: List<String>,
    val inputMethods: List<String>,
    val expectedOutput: String,
    val limitations: String,
    val verificationTestCases: List<CADVerificationTestCase>
)

/**
 * Top-level data class container for the PrecisionCAD Studio 2D CAD Command Compatibility Matrix.
 */
data class CADCommandMatrix(
    val matrixVersion: String = "2.0-2D-TECHNICAL",
    val entries: List<CADCommandMatrixEntry> = defaultEntries,
    val totalCommands: Int = entries.size
) {
    fun filterByCategory(category: CadCommandCategory?): List<CADCommandMatrixEntry> =
        if (category == null) entries else entries.filter { it.category == category }

    fun search(query: String): List<CADCommandMatrixEntry> {
        val q = query.trim().uppercase(Locale.ROOT)
        if (q.isEmpty()) return entries
        return entries.filter { entry ->
            entry.commandName.uppercase(Locale.ROOT).contains(q) ||
                entry.aliases.any { it.uppercase(Locale.ROOT).contains(q) } ||
                entry.supportedOptions.any { it.uppercase(Locale.ROOT).contains(q) } ||
                entry.expectedOutput.uppercase(Locale.ROOT).contains(q) ||
                entry.verificationTestCases.any {
                    it.testCaseId.uppercase(Locale.ROOT).contains(q) || it.title.uppercase(Locale.ROOT).contains(q)
                }
        }
    }

    fun filter(category: CadCommandCategory?, query: String): List<CADCommandMatrixEntry> {
        val byCat = filterByCategory(category)
        val q = query.trim().uppercase(Locale.ROOT)
        if (q.isEmpty()) return byCat
        return byCat.filter { entry ->
            entry.commandName.uppercase(Locale.ROOT).contains(q) ||
                entry.aliases.any { it.uppercase(Locale.ROOT).contains(q) } ||
                entry.supportedOptions.any { it.uppercase(Locale.ROOT).contains(q) } ||
                entry.expectedOutput.uppercase(Locale.ROOT).contains(q) ||
                entry.verificationTestCases.any {
                    it.testCaseId.uppercase(Locale.ROOT).contains(q) || it.title.uppercase(Locale.ROOT).contains(q)
                }
        }
    }

    fun findByCommandNameOrAlias(token: String): CADCommandMatrixEntry? {
        val upper = token.trim().uppercase(Locale.ROOT)
        return entries.firstOrNull { entry ->
            entry.commandName.equals(upper, ignoreCase = true) ||
                entry.aliases.any { it.equals(upper, ignoreCase = true) }
        }
    }

    companion object {
        val defaultEntries: List<CADCommandMatrixEntry> = listOf(
            // =========================================================================
            // 1. DRAWING COMMANDS (DRAW)
            // =========================================================================
            CADCommandMatrixEntry(
                commandId = CadCommandId.LINE,
                commandName = "LINE",
                aliases = listOf("LINE", "L"),
                category = CadCommandCategory.DRAW,
                supportedOptions = listOf("Chain Segments", "Close Loop (C)", "Undo Last Segment (U)", "Ortho / Polar Lock"),
                inputMethods = listOf("Virtual Cursor (Touchpad/Joystick)", "Direct Numeric (X,Y / @X,Y / L<θ / @L<θ)", "Command Line", "OSNAP", "Direct Pointer"),
                expectedOutput = "Creates one or more independent double-precision 2D LineEntity segments on the active layer.",
                limitations = "Operates in the 2D Z=0 model plane; segments remain independent unless joined via JOIN or drawn with POLYLINE.",
                verificationTestCases = listOf(
                    CADVerificationTestCase(
                        testCaseId = "TC-DRW-01A",
                        title = "Closed Right-Triangle Chain",
                        procedure = "Invoke LINE, enter 0,0 -> @100,0 -> @0,50 -> C.",
                        expectedResult = "3 LineEntity segments created with exact lengths 100.0, 50.0, and 111.8034 closing at (0,0)."
                    ),
                    CADVerificationTestCase(
                        testCaseId = "TC-DRW-01B",
                        title = "Relative Polar Segment",
                        procedure = "Invoke LINE, pick (10,10), enter @40<45.",
                        expectedResult = "LineEntity created from (10,10) to (38.2843, 38.2843) with length 40.0 and angle 45.0°."
                    )
                )
            ),
            CADCommandMatrixEntry(
                commandId = CadCommandId.POLYLINE,
                commandName = "POLYLINE",
                aliases = listOf("POLYLINE", "PL", "PLINE"),
                category = CadCommandCategory.DRAW,
                supportedOptions = listOf("Line Segments", "Arc Segments (Bulge)", "Close (C)", "Constant Global Width (W)", "Undo Vertex (U)"),
                inputMethods = listOf("Virtual Cursor", "Direct Numeric", "Command Line", "OSNAP", "Direct Pointer"),
                expectedOutput = "Creates a connected PolylineEntity (DXF LWPOLYLINE) storing ordered vertices (x, y, bulge) and closure state.",
                limitations = "Per-vertex variable start/end taper widths are normalized to uniform polyline width on DXF R12 export.",
                verificationTestCases = listOf(
                    CADVerificationTestCase(
                        testCaseId = "TC-DRW-02A",
                        title = "Multi-Vertex Closed Polyline with Arc Bulge",
                        procedure = "Create 4-vertex polyline with semicircle bulge=1.0 and Close option.",
                        expectedResult = "Closed LWPOLYLINE entity with accurate total arc + chord perimeter."
                    ),
                    CADVerificationTestCase(
                        testCaseId = "TC-DRW-02B",
                        title = "Polyline Decomposition for Fillet/Offset",
                        procedure = "Draw 3-segment open polyline and apply FILLET R=5 to corner.",
                        expectedResult = "Corner segments are trimmed and connected by a tangent R=5 arc."
                    )
                )
            ),
            CADCommandMatrixEntry(
                commandId = CadCommandId.CIRCLE,
                commandName = "CIRCLE",
                aliases = listOf("CIRCLE", "C"),
                category = CadCommandCategory.DRAW,
                supportedOptions = listOf("Center-Radius (Default)", "Center-Diameter (D)", "2-Point (2P)", "3-Point (3P)", "Tan-Tan-Radius (TTR)"),
                inputMethods = listOf("Virtual Cursor", "Direct Numeric", "Command Line", "OSNAP", "Direct Pointer"),
                expectedOutput = "Creates a CircleEntity(center, radius); 3P solves analytical circumcircle; TTR solves circle tangent to two entities.",
                limitations = "3P rejects collinear points with a command prompt notice; TTR requires non-parallel reference lines/circles.",
                verificationTestCases = listOf(
                    CADVerificationTestCase(
                        testCaseId = "TC-DRW-03A",
                        title = "3-Point Circumcircle Solver",
                        procedure = "Select CIRCLE 3P and pick (0,10), (10,0), (0,-10).",
                        expectedResult = "CircleEntity created at center (0.0, 0.0) with radius 10.0."
                    ),
                    CADVerificationTestCase(
                        testCaseId = "TC-DRW-03B",
                        title = "Tan-Tan-Radius (TTR) Analytical Solver",
                        procedure = "Select CIRCLE TTR with R=8, pick orthogonal axes X=0 and Y=0 in Quadrant I.",
                        expectedResult = "CircleEntity created at center (8.0, 8.0) with radius 8.0 tangent to both lines."
                    )
                )
            ),
            CADCommandMatrixEntry(
                commandId = CadCommandId.ARC,
                commandName = "ARC",
                aliases = listOf("ARC", "A"),
                category = CadCommandCategory.DRAW,
                supportedOptions = listOf("3-Point (3P)", "Start-Center-End (SCE)", "Center-Start-Angle (CSA)", "CCW / CW Direction"),
                inputMethods = listOf("Virtual Cursor", "Direct Numeric", "Command Line", "OSNAP", "Direct Pointer"),
                expectedOutput = "Creates an ArcEntity defined by (center, radius, startAngleDeg, sweepAngleDeg).",
                limitations = "Elliptical arcs are represented via ELLIPSE or SPLINE entities.",
                verificationTestCases = listOf(
                    CADVerificationTestCase(
                        testCaseId = "TC-DRW-04A",
                        title = "Start-Center-End 90° Arc",
                        procedure = "Select ARC SCE, pick Start (10,0), Center (0,0), End (0,10).",
                        expectedResult = "ArcEntity with R=10.0, startAngle=0°, sweepAngle=+90°, arcLength=15.7080."
                    )
                )
            ),
            CADCommandMatrixEntry(
                commandId = CadCommandId.RECTANGLE,
                commandName = "RECTANGLE",
                aliases = listOf("RECTANGLE", "REC", "RECT"),
                category = CadCommandCategory.DRAW,
                supportedOptions = listOf("Sharp Corners", "Fillet Corners (F)", "Chamfer Corners (CH)", "Corner-to-Corner"),
                inputMethods = listOf("Virtual Cursor", "Direct Numeric", "Command Line", "OSNAP", "Direct Pointer"),
                expectedOutput = "Creates a closed orthogonal PolylineEntity (4 vertices for sharp, 8 vertices for filleted/chamfered).",
                limitations = "Individual edges can be filleted directly with FILLET or decomposed via EXPLODE.",
                verificationTestCases = listOf(
                    CADVerificationTestCase(
                        testCaseId = "TC-DRW-05A",
                        title = "Rounded Corner Rectangle",
                        procedure = "Invoke RECTANGLE (Fillet R=5) from (10,10) to (90,50).",
                        expectedResult = "Closed 8-vertex PolylineEntity with 80×40 bounding box and four R=5 tangent arc corners."
                    )
                )
            ),
            CADCommandMatrixEntry(
                commandId = CadCommandId.POLYGON,
                commandName = "POLYGON",
                aliases = listOf("POLYGON", "POL"),
                category = CadCommandCategory.DRAW,
                supportedOptions = listOf("Sides (3..64)", "Inscribed in Circle (I)", "Circumscribed about Circle (C)"),
                inputMethods = listOf("Virtual Cursor", "Direct Numeric", "Command Line", "OSNAP", "Direct Pointer"),
                expectedOutput = "Creates a closed regular N-sided PolylineEntity with equal edge lengths and interior angles.",
                limitations = "Number of sides is bounded to 3..64 to preserve real-time OSNAP responsiveness.",
                verificationTestCases = listOf(
                    CADVerificationTestCase(
                        testCaseId = "TC-DRW-06A",
                        title = "Circumscribed Hexagon Across Flats",
                        procedure = "Create 6-sided Circumscribed polygon at (0,0) with R=10.",
                        expectedResult = "Closed 6-vertex hexagon with vertex radius 11.5470 and across-flats distance 20.0."
                    )
                )
            ),
            CADCommandMatrixEntry(
                commandId = CadCommandId.ELLIPSE,
                commandName = "ELLIPSE",
                aliases = listOf("ELLIPSE", "EL"),
                category = CadCommandCategory.DRAW,
                supportedOptions = listOf("Center-Axes (Center -> Major Endpoint -> Minor Radius)", "Rotated Major Axis"),
                inputMethods = listOf("Virtual Cursor", "Direct Numeric", "Command Line", "OSNAP", "Direct Pointer"),
                expectedOutput = "Creates an EllipseEntity(center, majorRadius, minorRadius, rotationAngleDeg).",
                limitations = "Trimming an ellipse converts the remaining span into a high-density polyline curve.",
                verificationTestCases = listOf(
                    CADVerificationTestCase(
                        testCaseId = "TC-DRW-07A",
                        title = "Orthogonal Ellipse & Quadrant OSNAP",
                        procedure = "Create ELLIPSE at (0,0), major endpoint (20,0), minor radius 10.",
                        expectedResult = "EllipseEntity with quadrant snaps at (±20,0) and (0,±10)."
                    )
                )
            ),
            CADCommandMatrixEntry(
                commandId = CadCommandId.SPLINE,
                commandName = "SPLINE",
                aliases = listOf("SPLINE", "SPL"),
                category = CadCommandCategory.DRAW,
                supportedOptions = listOf("Catmull-Rom Fit Points", "Control Vertices", "Close (C)"),
                inputMethods = listOf("Virtual Cursor", "Direct Numeric", "Command Line", "OSNAP", "Direct Pointer"),
                expectedOutput = "Creates a smooth cubic SplineEntity passing through the picked fit points.",
                limitations = "Intersections evaluate against adaptive curve subdivisions.",
                verificationTestCases = listOf(
                    CADVerificationTestCase(
                        testCaseId = "TC-DRW-08A",
                        title = "4-Point Interpolated Spline",
                        procedure = "Pick (0,0) -> (10,15) -> (25,-5) -> (40,10) and press Enter.",
                        expectedResult = "Smooth SplineEntity created and exported as DXF SPLINE record."
                    )
                )
            ),
            CADCommandMatrixEntry(
                commandId = CadCommandId.POINT,
                commandName = "POINT",
                aliases = listOf("POINT", "PO"),
                category = CadCommandCategory.DRAW,
                supportedOptions = listOf("Single Point", "PDMODE Crosshair Reticle Display"),
                inputMethods = listOf("Virtual Cursor", "Direct Numeric", "Command Line", "OSNAP", "Direct Pointer"),
                expectedOutput = "Places a PointEntity node in model space acting as an exact Endpoint/Node OSNAP target.",
                limitations = "Visual marker size is governed by viewport display settings.",
                verificationTestCases = listOf(
                    CADVerificationTestCase(
                        testCaseId = "TC-DRW-09A",
                        title = "Precision Node Placement & Snap",
                        procedure = "Place POINT at (33.25, 18.75) and move cursor near it with OSNAP active.",
                        expectedResult = "OSNAP locks onto exact coordinates (33.25, 18.75)."
                    )
                )
            ),
            CADCommandMatrixEntry(
                commandId = CadCommandId.RAY,
                commandName = "RAY",
                aliases = listOf("RAY"),
                category = CadCommandCategory.DRAW,
                supportedOptions = listOf("Base Point + Direction Point", "Angular Direction"),
                inputMethods = listOf("Virtual Cursor", "Direct Numeric", "Command Line", "OSNAP", "Direct Pointer"),
                expectedOutput = "Creates a semi-infinite RayEntity starting at basePoint along angleDeg on CONSTRUCTION layer.",
                limitations = "Clipped to viewport/sheet bounds during rendering.",
                verificationTestCases = listOf(
                    CADVerificationTestCase(
                        testCaseId = "TC-DRW-10A",
                        title = "45° Projection Ray Intersection",
                        procedure = "Create RAY from (0,0) through (10,10) and intersect with vertical line X=250.",
                        expectedResult = "Analytical intersection resolves at (250.0, 250.0)."
                    )
                )
            ),
            CADCommandMatrixEntry(
                commandId = CadCommandId.XLINE,
                commandName = "XLINE",
                aliases = listOf("XLINE", "XL"),
                category = CadCommandCategory.DRAW,
                supportedOptions = listOf("TwoPoint", "Horizontal", "Vertical"),
                inputMethods = listOf("Virtual Cursor", "Direct Numeric", "Command Line", "OSNAP", "Direct Pointer"),
                expectedOutput = "Creates an infinite construction line (XLineEntity) extending in both directions.",
                limitations = "Excluded from QDIM automatic bounding box calculations so infinite lines do not skew overall dimensions.",
                verificationTestCases = listOf(
                    CADVerificationTestCase(
                        testCaseId = "TC-DRW-11A",
                        title = "Horizontal & Vertical Datum Construction",
                        procedure = "Place Horizontal XLINE at Y=40 and Vertical XLINE at X=60.",
                        expectedResult = "Intersection OSNAP resolves at exact datum (60.0, 40.0)."
                    )
                )
            ),
            CADCommandMatrixEntry(
                commandId = CadCommandId.DONUT,
                commandName = "DONUT",
                aliases = listOf("DONUT", "DO"),
                category = CadCommandCategory.DRAW,
                supportedOptions = listOf("Outside Diameter (OD)", "Inside Diameter (ID = 0.5 * OD)"),
                inputMethods = listOf("Virtual Cursor", "Direct Numeric", "Command Line", "OSNAP", "Direct Pointer"),
                expectedOutput = "Creates an annular DonutEntity with concentric inner and outer circles.",
                limitations = "Requires OD > ID >= 0.",
                verificationTestCases = listOf(
                    CADVerificationTestCase(
                        testCaseId = "TC-DRW-12A",
                        title = "Annular Ring Creation & Explode",
                        procedure = "Place DONUT with OD=20 at (50,50), then run EXPLODE.",
                        expectedResult = "Decomposes into two concentric CircleEntities of radii 5.0 and 10.0."
                    )
                )
            ),
            CADCommandMatrixEntry(
                commandId = CadCommandId.HATCH,
                commandName = "HATCH",
                aliases = listOf("HATCH", "H", "BHATCH"),
                category = CadCommandCategory.DRAW,
                supportedOptions = listOf("ANSI31", "ANSI32", "ANSI37", "SOLID", "GRID", "DOTS", "Pick Internal Seed Point"),
                inputMethods = listOf("Virtual Cursor", "Command Line", "Direct Pointer"),
                expectedOutput = "Traces enclosing closed boundary around picked seed point and creates a HatchEntity.",
                limitations = "Boundary loop must be closed within 0.05 model units.",
                verificationTestCases = listOf(
                    CADVerificationTestCase(
                        testCaseId = "TC-DRW-13A",
                        title = "ANSI31 Section Hatch Inside Closed Rectangle",
                        procedure = "Draw 100×50 rectangle, invoke HATCH (ANSI31), pick (50,25).",
                        expectedResult = "HatchEntity created with 45° clipped section lines inside the 100×50 boundary."
                    )
                )
            ),
            CADCommandMatrixEntry(
                commandId = CadCommandId.BOUNDARY,
                commandName = "BOUNDARY",
                aliases = listOf("BOUNDARY", "BO"),
                category = CadCommandCategory.DRAW,
                supportedOptions = listOf("Pick Internal Seed Point", "Closed Polyline Output"),
                inputMethods = listOf("Virtual Cursor", "Command Line", "Direct Pointer"),
                expectedOutput = "Traces minimal enclosing closed loop of intersecting lines/polylines/circles around seed point.",
                limitations = "Reports warning if seed point is not enclosed by a closed boundary.",
                verificationTestCases = listOf(
                    CADVerificationTestCase(
                        testCaseId = "TC-DRW-14A",
                        title = "Overlapping Line Loop Boundary Extraction",
                        procedure = "Draw 4 overlapping lines forming a 40×40 square, invoke BOUNDARY at (20,20).",
                        expectedResult = "Closed 4-vertex 40×40 PolylineEntity created."
                    )
                )
            ),

            // =========================================================================
            // 2. MODIFICATION COMMANDS (MODIFY)
            // =========================================================================
            CADCommandMatrixEntry(
                commandId = CadCommandId.MOVE,
                commandName = "MOVE",
                aliases = listOf("MOVE", "M"),
                category = CadCommandCategory.MODIFY,
                supportedOptions = listOf("Base Point + Destination Point", "Relative Displacement (@dx,dy)"),
                inputMethods = listOf("Virtual Cursor", "Direct Numeric", "Command Line", "OSNAP", "Direct Pointer"),
                expectedOutput = "Translates selected unlocked entities by (dx,dy) and updates associative dimensions.",
                limitations = "Entities on locked layers are protected and excluded.",
                verificationTestCases = listOf(
                    CADVerificationTestCase(
                        testCaseId = "TC-MOD-01A",
                        title = "Precision Relative Translation",
                        procedure = "Select circle at (10,20), invoke MOVE, pick (0,0) -> @15,-5.",
                        expectedResult = "Circle center moves to (25.0, 15.0) and associative dimensions update."
                    )
                )
            ),
            CADCommandMatrixEntry(
                commandId = CadCommandId.COPY,
                commandName = "COPY",
                aliases = listOf("COPY", "CO", "CP"),
                category = CadCommandCategory.MODIFY,
                supportedOptions = listOf("Multiple Continuous Placement", "Base Point + Displacement"),
                inputMethods = listOf("Virtual Cursor", "Direct Numeric", "Command Line", "OSNAP", "Direct Pointer"),
                expectedOutput = "Clones selected entities with new UUIDs at each picked displacement.",
                limitations = "Copied geometry receives new IDs independent of source dimensions.",
                verificationTestCases = listOf(
                    CADVerificationTestCase(
                        testCaseId = "TC-MOD-02A",
                        title = "Multi-Drop Hole Copy",
                        procedure = "Select ⌀10 circle at (20,20), invoke COPY, pick (20,20) -> (50,20) -> (80,20).",
                        expectedResult = "Two new ⌀10 circles created at (50,20) and (80,20)."
                    )
                )
            ),
            CADCommandMatrixEntry(
                commandId = CadCommandId.ROTATE,
                commandName = "ROTATE",
                aliases = listOf("ROTATE", "RO"),
                category = CadCommandCategory.MODIFY,
                supportedOptions = listOf("Base Pivot + Angle Point / Degrees"),
                inputMethods = listOf("Virtual Cursor", "Direct Numeric", "Command Line", "OSNAP", "Direct Pointer"),
                expectedOutput = "Rotates selected entities around base pivot using exact 2D rotation matrix.",
                limitations = "Counter-clockwise (CCW) angles are positive.",
                verificationTestCases = listOf(
                    CADVerificationTestCase(
                        testCaseId = "TC-MOD-03A",
                        title = "90° Orthogonal Rotation",
                        procedure = "Rotate line (10,0)-(20,0) around pivot (0,0) by +90°.",
                        expectedResult = "Line endpoints update to (0.0, 10.0) and (0.0, 20.0)."
                    )
                )
            ),
            CADCommandMatrixEntry(
                commandId = CadCommandId.SCALE,
                commandName = "SCALE",
                aliases = listOf("SCALE", "SC"),
                category = CadCommandCategory.MODIFY,
                supportedOptions = listOf("Base Point + Scale Factor (s > 0)"),
                inputMethods = listOf("Virtual Cursor", "Direct Numeric", "Command Line", "OSNAP", "Direct Pointer"),
                expectedOutput = "Uniformly scales coordinates, radii, and widths relative to basePoint.",
                limitations = "Scale factor must be strictly positive.",
                verificationTestCases = listOf(
                    CADVerificationTestCase(
                        testCaseId = "TC-MOD-04A",
                        title = "1.5x Uniform Component Scale",
                        procedure = "Scale 50×30 rectangle from (0,0) by factor 1.5.",
                        expectedResult = "Rectangle dimensions become 75.0 × 45.0."
                    )
                )
            ),
            CADCommandMatrixEntry(
                commandId = CadCommandId.MIRROR,
                commandName = "MIRROR",
                aliases = listOf("MIRROR", "MI"),
                category = CadCommandCategory.MODIFY,
                supportedOptions = listOf("2-Point Mirror Axis", "Right-Reading Text Preservation"),
                inputMethods = listOf("Virtual Cursor", "Direct Numeric", "Command Line", "OSNAP", "Direct Pointer"),
                expectedOutput = "Creates reflected copies of selected entities across the axis line.",
                limitations = "Text entities preserve right-reading orientation (MIRRTEXT=0).",
                verificationTestCases = listOf(
                    CADVerificationTestCase(
                        testCaseId = "TC-MOD-05A",
                        title = "Symmetry Axis Reflection",
                        procedure = "Mirror arc at (20,10) across Y-axis (0,0)-(0,100).",
                        expectedResult = "Mirrored arc created at (-20.0, 10.0) with reversed sweep."
                    )
                )
            ),
            CADCommandMatrixEntry(
                commandId = CadCommandId.OFFSET,
                commandName = "OFFSET",
                aliases = listOf("OFFSET", "O"),
                category = CadCommandCategory.MODIFY,
                supportedOptions = listOf("Manual Offset Distance Input", "Quick Presets", "Pick Entity + Pick Side"),
                inputMethods = listOf("Virtual Cursor", "Direct Numeric", "Command Line", "OSNAP", "Direct Pointer"),
                expectedOutput = "Creates parallel lines, concentric circles/arcs, or inward/outward offset polylines.",
                limitations = "Inward offset of a circle with distance >= radius is rejected.",
                verificationTestCases = listOf(
                    CADVerificationTestCase(
                        testCaseId = "TC-MOD-06A",
                        title = "Inward Wall Thickness Offset",
                        procedure = "Offset 100×60 closed rectangle inward by distance 5.0.",
                        expectedResult = "Closed 90.0 × 50.0 inner rectangle created."
                    )
                )
            ),
            CADCommandMatrixEntry(
                commandId = CadCommandId.TRIM,
                commandName = "TRIM",
                aliases = listOf("TRIM", "TR"),
                category = CadCommandCategory.MODIFY,
                supportedOptions = listOf("Quick All-Edges Cutting Mode", "Pick Span to Remove"),
                inputMethods = listOf("Virtual Cursor", "Command Line", "OSNAP", "Direct Pointer"),
                expectedOutput = "Splits target curve at exact analytical intersections with cutting edges and removes the picked span.",
                limitations = "Requires at least one intersection with another visible entity.",
                verificationTestCases = listOf(
                    CADVerificationTestCase(
                        testCaseId = "TC-MOD-07A",
                        title = "Middle Segment Trim Between Two Cutters",
                        procedure = "Draw line (-50,0)-(50,0) crossed by X=-10 and X=10; invoke TRIM at (0,0).",
                        expectedResult = "Middle span removed; outer segments (-50,0)-(-10,0) and (10,0)-(50,0) remain."
                    )
                )
            ),
            CADCommandMatrixEntry(
                commandId = CadCommandId.EXTEND,
                commandName = "EXTEND",
                aliases = listOf("EXTEND", "EX"),
                category = CadCommandCategory.MODIFY,
                supportedOptions = listOf("Quick All-Boundaries Mode", "Pick Near Endpoint to Extend"),
                inputMethods = listOf("Virtual Cursor", "Command Line", "OSNAP", "Direct Pointer"),
                expectedOutput = "Projects nearer endpoint of line along its trajectory to the nearest boundary edge.",
                limitations = "Closed circles and closed polylines cannot be extended.",
                verificationTestCases = listOf(
                    CADVerificationTestCase(
                        testCaseId = "TC-MOD-08A",
                        title = "Boundary Projection Extend",
                        procedure = "Extend line (0,0)-(30,0) toward vertical boundary at X=50.",
                        expectedResult = "Line endpoint updates to (50.0, 0.0)."
                    )
                )
            ),
            CADCommandMatrixEntry(
                commandId = CadCommandId.FILLET,
                commandName = "FILLET",
                aliases = listOf("FILLET", "F"),
                category = CadCommandCategory.MODIFY,
                supportedOptions = listOf("Manual Radius Entry (Primary)", "Radius Presets (2, 5, 10)", "R=0 Sharp Corner", "Lines & Polylines / Rectangles"),
                inputMethods = listOf("Virtual Cursor", "Manual Radius Box / Direct Numeric", "Command Line", "OSNAP", "Direct Pointer"),
                expectedOutput = "Trims/extends two picked lines or polyline/rectangle edges and inserts a tangent ArcEntity of radius R.",
                limitations = "Parallel lines cannot be filleted; R must fit within the picked edge spans.",
                verificationTestCases = listOf(
                    CADVerificationTestCase(
                        testCaseId = "TC-MOD-09A",
                        title = "Orthogonal Lines Fillet (R=10)",
                        procedure = "Set FILLET R=10, pick line (0,0)-(50,0) and line (0,0)-(0,50).",
                        expectedResult = "Lines trimmed to (10,0) and (0,10) with tangent 90° arc centered at (10,10)."
                    ),
                    CADVerificationTestCase(
                        testCaseId = "TC-MOD-09B",
                        title = "Polyline / Rectangle Corner Fillet (AutoCAD Two-Side Pick)",
                        procedure = "Draw a RECTANGLE or POLYLINE, invoke FILLET with manual radius R=5, pick adjacent bottom and right sides.",
                        expectedResult = "Polyline corner is filleted with a tangent R=5 arc and trimmed sides."
                    )
                )
            ),
            CADCommandMatrixEntry(
                commandId = CadCommandId.CHAMFER,
                commandName = "CHAMFER",
                aliases = listOf("CHAMFER", "CHA"),
                category = CadCommandCategory.MODIFY,
                supportedOptions = listOf("Manual Chamfer Distance (Primary)", "Presets", "Lines & Polylines / Rectangles"),
                inputMethods = listOf("Virtual Cursor", "Manual Distance Box / Direct Numeric", "Command Line", "OSNAP", "Direct Pointer"),
                expectedOutput = "Bevels corner between two non-parallel lines or polyline edges by setback distances D1 and D2.",
                limitations = "Applies to linear segments; circular arcs use FILLET.",
                verificationTestCases = listOf(
                    CADVerificationTestCase(
                        testCaseId = "TC-MOD-10A",
                        title = "45° Equal Setback Chamfer (D=5)",
                        procedure = "Set CHAMFER D=5, pick (0,0)-(40,0) and (0,0)-(0,40).",
                        expectedResult = "Corner beveled between (5,0) and (0,5) with bevel length 7.0711."
                    )
                )
            ),
            CADCommandMatrixEntry(
                commandId = CadCommandId.STRETCH,
                commandName = "STRETCH",
                aliases = listOf("STRETCH", "S"),
                category = CadCommandCategory.MODIFY,
                supportedOptions = listOf("Crossing Window Region + Displacement Vector"),
                inputMethods = listOf("Virtual Cursor", "Direct Numeric", "Command Line", "OSNAP", "Direct Pointer"),
                expectedOutput = "Displaces vertices inside the crossing box while keeping outside vertices anchored.",
                limitations = "Circles translate only if their center lies inside the crossing window.",
                verificationTestCases = listOf(
                    CADVerificationTestCase(
                        testCaseId = "TC-MOD-11A",
                        title = "Parametric Plate Width Stretch",
                        procedure = "Stretch right edge of 100×50 rectangle by @25,0.",
                        expectedResult = "Rectangle becomes 125.0 × 50.0 and associative width dimension updates to 125.0."
                    )
                )
            ),
            CADCommandMatrixEntry(
                commandId = CadCommandId.BREAK,
                commandName = "BREAK",
                aliases = listOf("BREAK", "BR"),
                category = CadCommandCategory.MODIFY,
                supportedOptions = listOf("Two-Point Gap Break", "Circle-to-Arc Break"),
                inputMethods = listOf("Virtual Cursor", "Direct Numeric", "Command Line", "OSNAP", "Direct Pointer"),
                expectedOutput = "Removes segment between P1 and P2 on a line or converts a broken circle into an ArcEntity.",
                limitations = "Requires two distinct break points.",
                verificationTestCases = listOf(
                    CADVerificationTestCase(
                        testCaseId = "TC-MOD-12A",
                        title = "Linear Gap Break",
                        procedure = "Break line (0,0)-(100,0) between (30,0) and (60,0).",
                        expectedResult = "Two collinear segments (0,0)-(30,0) and (60,0)-(100,0) remain."
                    )
                )
            ),
            CADCommandMatrixEntry(
                commandId = CadCommandId.JOIN,
                commandName = "JOIN",
                aliases = listOf("JOIN", "J"),
                category = CadCommandCategory.MODIFY,
                supportedOptions = listOf("Collinear Line Merge", "Contiguous Endpoint Chain to Polyline"),
                inputMethods = listOf("Virtual Cursor", "Command Line", "Direct Pointer"),
                expectedOutput = "Merges collinear lines into a single LineEntity or contiguous segments into a PolylineEntity.",
                limitations = "Endpoints must meet within 0.01 model units.",
                verificationTestCases = listOf(
                    CADVerificationTestCase(
                        testCaseId = "TC-MOD-13A",
                        title = "Contiguous Segment Chain Join",
                        procedure = "Select (0,0)-(20,0) and (20,0)-(20,30) and invoke JOIN.",
                        expectedResult = "Single 3-vertex PolylineEntity of total length 50.0."
                    )
                )
            ),
            CADCommandMatrixEntry(
                commandId = CadCommandId.EXPLODE,
                commandName = "EXPLODE",
                aliases = listOf("EXPLODE", "X"),
                category = CadCommandCategory.MODIFY,
                supportedOptions = listOf("Polyline", "Rectangle / Polygon", "Block Insert", "Donut"),
                inputMethods = listOf("Virtual Cursor", "Command Line", "Direct Pointer"),
                expectedOutput = "Decomposes compound entities into constituent primitive LineEntity, ArcEntity, and CircleEntity objects.",
                limitations = "Primitive lines, circles, and points cannot be exploded further.",
                verificationTestCases = listOf(
                    CADVerificationTestCase(
                        testCaseId = "TC-MOD-14A",
                        title = "Rectangle Polyline Explode",
                        procedure = "Select closed 4-vertex rectangle and invoke EXPLODE.",
                        expectedResult = "Replaced by 4 independent LineEntity objects."
                    )
                )
            ),
            CADCommandMatrixEntry(
                commandId = CadCommandId.ARRAY,
                commandName = "ARRAY",
                aliases = listOf("ARRAY", "AR"),
                category = CadCommandCategory.MODIFY,
                supportedOptions = listOf("Rectangular Grid (2×3)", "Polar Bolt-Circle (6× 360°)"),
                inputMethods = listOf("Virtual Cursor", "Direct Numeric", "Command Line", "OSNAP", "Direct Pointer"),
                expectedOutput = "Generates rectangular grid or circular bolt-circle pattern from selected entities.",
                limitations = "Capped at 100 instances per operation.",
                verificationTestCases = listOf(
                    CADVerificationTestCase(
                        testCaseId = "TC-MOD-15A",
                        title = "6-Hole Polar Bolt Circle Array",
                        procedure = "Select ⌀8 hole at (30,0), invoke ARRAY (Polar) around (0,0).",
                        expectedResult = "6 holes spaced at 60° intervals on R=30 bolt circle."
                    )
                )
            ),
            CADCommandMatrixEntry(
                commandId = CadCommandId.ALIGN,
                commandName = "ALIGN",
                aliases = listOf("ALIGN", "AL"),
                category = CadCommandCategory.MODIFY,
                supportedOptions = listOf("2 Source-Destination Point Pairs with Auto-Scale"),
                inputMethods = listOf("Virtual Cursor", "Direct Numeric", "Command Line", "OSNAP", "Direct Pointer"),
                expectedOutput = "Translates, rotates, and scales selected geometry to align source points with destination points.",
                limitations = "2D planar transformation.",
                verificationTestCases = listOf(
                    CADVerificationTestCase(
                        testCaseId = "TC-MOD-16A",
                        title = "2-Pair Planar Alignment",
                        procedure = "Align line (0,0)-(50,0) to target (10,10)-(40,50).",
                        expectedResult = "Line endpoints match (10,10) and (40,50) with length 50.0."
                    )
                )
            ),
            CADCommandMatrixEntry(
                commandId = CadCommandId.LENGTHEN,
                commandName = "LENGTHEN",
                aliases = listOf("LENGTHEN", "LEN"),
                category = CadCommandCategory.MODIFY,
                supportedOptions = listOf("Manual Target Total Length", "Pick Near Endpoint to Adjust"),
                inputMethods = listOf("Virtual Cursor", "Manual Length Box", "Command Line", "Direct Pointer"),
                expectedOutput = "Sets total length of a LineEntity from the picked end while preserving angle.",
                limitations = "Applies to open linear segments.",
                verificationTestCases = listOf(
                    CADVerificationTestCase(
                        testCaseId = "TC-MOD-17A",
                        title = "Exact Total Length Adjustment",
                        procedure = "Set LENGTHEN value to 120, pick line (0,0)-(80,0) near (80,0).",
                        expectedResult = "Line endpoint updates to (120.0, 0.0)."
                    )
                )
            ),
            CADCommandMatrixEntry(
                commandId = CadCommandId.PEDIT,
                commandName = "PEDIT",
                aliases = listOf("PEDIT", "PE"),
                category = CadCommandCategory.MODIFY,
                supportedOptions = listOf("Toggle Close / Open Polyline"),
                inputMethods = listOf("Virtual Cursor", "Command Line", "Direct Pointer"),
                expectedOutput = "Toggles closed/open topology of selected PolylineEntity objects.",
                limitations = "Requires selected PolylineEntity.",
                verificationTestCases = listOf(
                    CADVerificationTestCase(
                        testCaseId = "TC-MOD-18A",
                        title = "Open-to-Closed Polyline Toggle",
                        procedure = "Select 3-segment open polyline and invoke PEDIT.",
                        expectedResult = "Polyline isClosed flag toggles to true."
                    )
                )
            ),

            // =========================================================================
            // 3. SELECTION & INQUIRY COMMANDS (SELECT_INQUIRY)
            // =========================================================================
            CADCommandMatrixEntry(
                commandId = CadCommandId.SELECT,
                commandName = "SELECT",
                aliases = listOf("SELECT", "SEL", "ALL"),
                category = CadCommandCategory.SELECT_INQUIRY,
                supportedOptions = listOf("Single Pick", "Window (L->R Blue Enclosed)", "Crossing (R->L Green Intersect)", "Select All"),
                inputMethods = listOf("Virtual Cursor", "Command Line", "Direct Pointer Drag"),
                expectedOutput = "Updates active selection set and displays grip handles.",
                limitations = "Entities on hidden or frozen layers cannot be selected.",
                verificationTestCases = listOf(
                    CADVerificationTestCase(
                        testCaseId = "TC-SEL-01A",
                        title = "Window vs Crossing Selection Discrimination",
                        procedure = "Drag L->R window partially over a line and fully over a circle; repeat R->L.",
                        expectedResult = "Window selects only the enclosed circle; Crossing selects both."
                    )
                )
            ),
            CADCommandMatrixEntry(
                commandId = CadCommandId.MEASURE,
                commandName = "DIST",
                aliases = listOf("DIST", "DI", "MEASUREGEOM"),
                category = CadCommandCategory.SELECT_INQUIRY,
                supportedOptions = listOf("Point-to-Point Euclidean Distance", "ΔX, ΔY, and Angle Readout"),
                inputMethods = listOf("Virtual Cursor", "Direct Numeric", "Command Line", "OSNAP", "Direct Pointer"),
                expectedOutput = "Displays exact distance, delta X/Y, and angle in the active display unit.",
                limitations = "Non-modifying inquiry command.",
                verificationTestCases = listOf(
                    CADVerificationTestCase(
                        testCaseId = "TC-INQ-01A",
                        title = "3-4-5 Right Triangle Measurement",
                        procedure = "Invoke DIST between (10,15) and (40,55).",
                        expectedResult = "Displays DIST = 50.00, ΔX = 30.00, ΔY = 40.00, ∠53.13°."
                    )
                )
            ),
            CADCommandMatrixEntry(
                commandId = CadCommandId.PROPERTIES,
                commandName = "PROPERTIES",
                aliases = listOf("PROPERTIES", "PR", "CHPROP"),
                category = CadCommandCategory.SELECT_INQUIRY,
                supportedOptions = listOf("Layer Assignment", "Linetype Assignment", "Circle/Arc Radius Edit", "Erase Selected"),
                inputMethods = listOf("Dialog Inspector", "Command Line", "Direct Pointer"),
                expectedOutput = "Updates selected entities' graphical or geometric properties and refreshes associative dimensions.",
                limitations = "Locked-layer objects are protected from edits.",
                verificationTestCases = listOf(
                    CADVerificationTestCase(
                        testCaseId = "TC-INQ-02A",
                        title = "Associative Radius Property Edit",
                        procedure = "Select ⌀30 circle, open PROPERTIES, update radius from 15.0 to 22.5.",
                        expectedResult = "Circle radius updates to 22.5 and linked diameter dimension updates to ⌀45.00."
                    )
                )
            ),
            CADCommandMatrixEntry(
                commandId = CadCommandId.ISOLATE,
                commandName = "ISOLATEOBJECTS",
                aliases = listOf("ISOLATEOBJECTS", "ISO", "UNISOLATE"),
                category = CadCommandCategory.SELECT_INQUIRY,
                supportedOptions = listOf("Isolate Selected", "Unisolate All"),
                inputMethods = listOf("Command Line", "Toolbar Button"),
                expectedOutput = "Temporarily hides unselected objects for uncluttered drafting and restores all on second tap.",
                limitations = "Session-scoped visibility filter.",
                verificationTestCases = listOf(
                    CADVerificationTestCase(
                        testCaseId = "TC-INQ-03A",
                        title = "Isolate and Restore Cycle",
                        procedure = "Select 2 entities out of 12, invoke ISOLATE, then invoke ISOLATE again.",
                        expectedResult = "Viewport shows only 2 entities during isolation, then restores all 12."
                    )
                )
            ),

            // =========================================================================
            // 4. ANNOTATION & QUICK DIMENSION COMMANDS (ANNOTATE_DIM)
            // =========================================================================
            CADCommandMatrixEntry(
                commandId = CadCommandId.QDIM,
                commandName = "QDIM",
                aliases = listOf("QDIM", "QD"),
                category = CadCommandCategory.ANNOTATE_DIM,
                supportedOptions = listOf("QDIM ALL", "QDIM SELECT", "QDIM REGION", "Density: Sparse / Balanced / Detailed"),
                inputMethods = listOf("Top Bar QDIM Button", "Command Line", "Dialog Studio"),
                expectedOutput = "Runs AutoDimensionEngine -> DimensionLayoutEngine -> AdaptiveAnnotationRenderer to generate collision-free overall, hole pattern, pitch, and offset dimensions.",
                limitations = "Never overwrites manual dimensions; suppresses redundant pairwise hole-to-hole clutter.",
                verificationTestCases = listOf(
                    CADVerificationTestCase(
                        testCaseId = "TC-DIM-01A",
                        title = "6-Hole Mounting Plate Automatic Dimensioning",
                        procedure = "Run QDIM ALL (Balanced) on 120×80 plate with 6× ⌀10 holes.",
                        expectedResult = "Generates overall 120 & 80 dimensions, 6× ⌀10.00 pattern callout, edge offsets, and hole pitch chains."
                    )
                )
            ),
            CADCommandMatrixEntry(
                commandId = CadCommandId.DIMLINEAR,
                commandName = "DIMLINEAR",
                aliases = listOf("DIMLINEAR", "DLI"),
                category = CadCommandCategory.ANNOTATE_DIM,
                supportedOptions = listOf("Horizontal", "Vertical", "2 Extension Origins + Track Position"),
                inputMethods = listOf("Virtual Cursor", "Direct Numeric", "Command Line", "OSNAP", "Direct Pointer"),
                expectedOutput = "Creates an associative horizontal or vertical linear DimensionEntity.",
                limitations = "True inclined length measurements use DIMALIGNED.",
                verificationTestCases = listOf(
                    CADVerificationTestCase(
                        testCaseId = "TC-DIM-02A",
                        title = "Horizontal Linear Dimension",
                        procedure = "Invoke DIMLINEAR, pick (0,0), (75.5,20), and track point (35,30).",
                        expectedResult = "Horizontal dimension created reading 75.50."
                    )
                )
            ),
            CADCommandMatrixEntry(
                commandId = CadCommandId.DIMALIGNED,
                commandName = "DIMALIGNED",
                aliases = listOf("DIMALIGNED", "DAL"),
                category = CadCommandCategory.ANNOTATE_DIM,
                supportedOptions = listOf("2 Extension Origins + Offset Point"),
                inputMethods = listOf("Virtual Cursor", "Direct Numeric", "Command Line", "OSNAP", "Direct Pointer"),
                expectedOutput = "Creates an associative DimensionEntity parallel to the measured segment.",
                limitations = "Requires two non-coincident anchor points.",
                verificationTestCases = listOf(
                    CADVerificationTestCase(
                        testCaseId = "TC-DIM-03A",
                        title = "Inclined Edge True-Length Dimension",
                        procedure = "Dimension inclined line (0,0)-(30,40) with DIMALIGNED.",
                        expectedResult = "Dimension reads exact Euclidean length 50.00."
                    )
                )
            ),
            CADCommandMatrixEntry(
                commandId = CadCommandId.DIMANGULAR,
                commandName = "DIMANGULAR",
                aliases = listOf("DIMANGULAR", "DAN"),
                category = CadCommandCategory.ANNOTATE_DIM,
                supportedOptions = listOf("Vertex + First Arm Point + Second Arm Point"),
                inputMethods = listOf("Virtual Cursor", "Direct Numeric", "Command Line", "OSNAP", "Direct Pointer"),
                expectedOutput = "Creates an angular DimensionEntity formatted in degrees (°).",
                limitations = "Requires non-collinear arm vectors.",
                verificationTestCases = listOf(
                    CADVerificationTestCase(
                        testCaseId = "TC-DIM-04A",
                        title = "45° Chamfer Angle Dimension",
                        procedure = "Pick vertex (0,0), arm 1 (50,0), arm 2 (50,50).",
                        expectedResult = "Angular dimension created displaying 45.0°."
                    )
                )
            ),
            CADCommandMatrixEntry(
                commandId = CadCommandId.DIMRADIUS,
                commandName = "DIMRADIUS",
                aliases = listOf("DIMRADIUS", "DRA"),
                category = CadCommandCategory.ANNOTATE_DIM,
                supportedOptions = listOf("Pick Circle or Arc", "Adaptive External Arrow Flip"),
                inputMethods = listOf("Virtual Cursor", "Command Line", "OSNAP", "Direct Pointer"),
                expectedOutput = "Creates an associative R dimension linked to the target circle or arc.",
                limitations = "Applies to CircleEntity and ArcEntity.",
                verificationTestCases = listOf(
                    CADVerificationTestCase(
                        testCaseId = "TC-DIM-05A",
                        title = "Fillet Arc Radius Callout",
                        procedure = "Invoke DIMRADIUS and pick R=6 fillet arc.",
                        expectedResult = "Associative radial dimension created reading R6.00."
                    )
                )
            ),
            CADCommandMatrixEntry(
                commandId = CadCommandId.DIMDIAMETER,
                commandName = "DIMDIAMETER",
                aliases = listOf("DIMDIAMETER", "DDI"),
                category = CadCommandCategory.ANNOTATE_DIM,
                supportedOptions = listOf("Pick Circle or Arc", "Automatic External Leader for Tight Holes"),
                inputMethods = listOf("Virtual Cursor", "Command Line", "OSNAP", "Direct Pointer"),
                expectedOutput = "Creates an associative ⌀ dimension linked to the target circle or arc.",
                limitations = "Applies to CircleEntity and ArcEntity.",
                verificationTestCases = listOf(
                    CADVerificationTestCase(
                        testCaseId = "TC-DIM-05B",
                        title = "Small Hole External Arrow Placement",
                        procedure = "Apply DIMDIAMETER to a ⌀2.5 mm hole.",
                        expectedResult = "Arrows and text automatically place externally so the hole is not obscured."
                    )
                )
            ),
            CADCommandMatrixEntry(
                commandId = CadCommandId.DIMORDINATE,
                commandName = "DIMORDINATE",
                aliases = listOf("DIMORDINATE", "DOR"),
                category = CadCommandCategory.ANNOTATE_DIM,
                supportedOptions = listOf("X-Datum", "Y-Datum"),
                inputMethods = listOf("Virtual Cursor", "Direct Numeric", "Command Line", "OSNAP", "Direct Pointer"),
                expectedOutput = "Places datum coordinate callouts relative to model origin (0,0).",
                limitations = "Measured from origin (0,0).",
                verificationTestCases = listOf(
                    CADVerificationTestCase(
                        testCaseId = "TC-DIM-06A",
                        title = "CNC Hole Datum Ordinate",
                        procedure = "Pick hole center (42.5, 18.0) and drag leader vertically.",
                        expectedResult = "Creates X-Datum callout reading X=42.50."
                    )
                )
            ),
            CADCommandMatrixEntry(
                commandId = CadCommandId.LEADER,
                commandName = "MLEADER",
                aliases = listOf("MLEADER", "LEADER", "LE"),
                category = CadCommandCategory.ANNOTATE_DIM,
                supportedOptions = listOf("Arrow Tip -> Shoulder -> Landing Note"),
                inputMethods = listOf("Virtual Cursor", "Direct Numeric", "Command Line", "OSNAP", "Direct Pointer"),
                expectedOutput = "Creates a LeaderEntity with arrowhead, angled leader, horizontal shoulder, and GD&T note.",
                limitations = "2-pick standard callout.",
                verificationTestCases = listOf(
                    CADVerificationTestCase(
                        testCaseId = "TC-DIM-07A",
                        title = "GD&T Tolerance Leader Callout",
                        procedure = "Pick bore edge (69.9, 49.9) and shoulder (86, 68).",
                        expectedResult = "LeaderEntity rendered and exported to DXF MLEADER."
                    )
                )
            ),
            CADCommandMatrixEntry(
                commandId = CadCommandId.TEXT,
                commandName = "MTEXT",
                aliases = listOf("MTEXT", "TEXT", "T", "DT"),
                category = CadCommandCategory.ANNOTATE_DIM,
                supportedOptions = listOf("Insertion Point", "Height", "Rotation Angle"),
                inputMethods = listOf("Virtual Cursor", "Direct Numeric", "Command Line", "OSNAP", "Direct Pointer"),
                expectedOutput = "Creates a technical TextEntity rendered in JetBrains Mono.",
                limitations = "Remains right-reading on mirror.",
                verificationTestCases = listOf(
                    CADVerificationTestCase(
                        testCaseId = "TC-DIM-08A",
                        title = "Technical Drawing Note Insertion",
                        procedure = "Invoke MTEXT and pick (10,10).",
                        expectedResult = "TextEntity placed at (10,10) and preserved on DXF round-trip."
                    )
                )
            ),
            CADCommandMatrixEntry(
                commandId = CadCommandId.CENTERMARK,
                commandName = "CENTERMARK",
                aliases = listOf("CENTERMARK", "CM", "CENTERLINE"),
                category = CadCommandCategory.ANNOTATE_DIM,
                supportedOptions = listOf("Pick Circle or Arc"),
                inputMethods = listOf("Virtual Cursor", "Command Line", "OSNAP", "Direct Pointer"),
                expectedOutput = "Generates an associative dash-dot CenterMarkEntity at the circle/arc center.",
                limitations = "Overshoot scales proportionally to radius.",
                verificationTestCases = listOf(
                    CADVerificationTestCase(
                        testCaseId = "TC-DIM-09A",
                        title = "Bore Center Mark Generation",
                        procedure = "Invoke CENTERMARK and pick ⌀28 bore at (60,40).",
                        expectedResult = "Crosshair CenterMarkEntity created at (60,40) on CENTERLINES layer."
                    )
                )
            ),

            // =========================================================================
            // 5. DOCUMENT, UNITS, CONSTRAINTS & IO COMMANDS (DOCUMENT_SYSTEM)
            // =========================================================================
            CADCommandMatrixEntry(
                commandId = CadCommandId.LAYER,
                commandName = "LAYER",
                aliases = listOf("LAYER", "LA"),
                category = CadCommandCategory.DOCUMENT_SYSTEM,
                supportedOptions = listOf("Add Layer", "Set Active", "Visibility On/Off", "Lock/Unlock", "Linetype & Weight"),
                inputMethods = listOf("Layer Dialog", "Command Line", "HUD Layer Pill"),
                expectedOutput = "Manages document layers and enforces lock/visibility rules in real time.",
                limitations = "Default layer '0' cannot be deleted.",
                verificationTestCases = listOf(
                    CADVerificationTestCase(
                        testCaseId = "TC-DOC-01A",
                        title = "Locked Layer Modification Protection",
                        procedure = "Lock layer '0' and attempt to MOVE or ERASE entities on layer '0'.",
                        expectedResult = "Locked entities remain protected and unmodified."
                    )
                )
            ),
            CADCommandMatrixEntry(
                commandId = CadCommandId.UNITS,
                commandName = "UNITS",
                aliases = listOf("UNITS", "UN", "DWGUNITS"),
                category = CadCommandCategory.DOCUMENT_SYSTEM,
                supportedOptions = listOf(
                    "mm / cm / m / in / ft",
                    "Mode A: Change Displayed Units Only (Non-Destructive)",
                    "Mode B: Rescale Model-Space Geometry Coordinates",
                    "Linear & Angular Decimal Precision"
                ),
                inputMethods = listOf("Top Bar UNIT Pill", "Command Line"),
                expectedOutput = "Persists selected UnitChangeMode (Mode A vs Mode B) and converts either display readouts or stored geometry.",
                limitations = "Prompts explicit selection between Mode A and Mode B.",
                verificationTestCases = listOf(
                    CADVerificationTestCase(
                        testCaseId = "TC-DOC-02A",
                        title = "Mode A vs Mode B Unit Conversion & Mode Persistence",
                        procedure = "Switch from Mode B to Mode A in Units dialog, apply, and reopen Units dialog.",
                        expectedResult = "Mode A remains persistently selected when reopening the dialog, and 100 mm displays as 10.00 cm without altering stored coordinates."
                    )
                )
            ),
            CADCommandMatrixEntry(
                commandId = CadCommandId.PAGESETUP,
                commandName = "PAGESETUP",
                aliases = listOf("PAGESETUP", "PS", "LAYOUT"),
                category = CadCommandCategory.DOCUMENT_SYSTEM,
                supportedOptions = listOf("Model Space vs Paper Space", "ISO A0..A4 / ANSI A..D / Custom", "Portrait / Landscape", "Plot Scale", "Title Block"),
                inputMethods = listOf("Top Bar Sheet Button", "MODEL/PAPER Toggle", "Command Line"),
                expectedOutput = "Configures sheet dimensions, orientation, plot scale, and title block independently of model geometry.",
                limitations = "Changing sheet size never rescales model-space entities.",
                verificationTestCases = listOf(
                    CADVerificationTestCase(
                        testCaseId = "TC-DOC-03A",
                        title = "Sheet Size Independence",
                        procedure = "Switch from ISO A4 Portrait to ISO A3 Landscape at 1:2 scale.",
                        expectedResult = "Paper Space frame updates while model-space coordinates remain untouched."
                    )
                )
            ),
            CADCommandMatrixEntry(
                commandId = CadCommandId.BLOCK,
                commandName = "BLOCK",
                aliases = listOf("BLOCK", "B", "INSERT", "I"),
                category = CadCommandCategory.DOCUMENT_SYSTEM,
                supportedOptions = listOf("Create Block from Selection", "Insert Block at Cursor"),
                inputMethods = listOf("Layer/Block Dialog", "Command Line"),
                expectedOutput = "Creates reusable BlockDefinition and places BlockInsertEntity instances.",
                limitations = "EXPLODE decomposes block instances back into independent entities.",
                verificationTestCases = listOf(
                    CADVerificationTestCase(
                        testCaseId = "TC-DOC-04A",
                        title = "Block Definition & Instance Placement",
                        procedure = "Insert M8_HEX_SOCKET block at cursor (50,50).",
                        expectedResult = "BlockInsertEntity placed at (50,50) and exported in DXF BLOCKS/INSERT."
                    )
                )
            ),
            CADCommandMatrixEntry(
                commandId = CadCommandId.CONSTRAINT,
                commandName = "GEOMCONSTRAINT",
                aliases = listOf("GEOMCONSTRAINT", "GC"),
                category = CadCommandCategory.DOCUMENT_SYSTEM,
                supportedOptions = listOf("Horizontal", "Vertical", "Parallel", "Perpendicular", "Equal", "Concentric", "Tangent", "Coincident", "Fixed"),
                inputMethods = listOf("Properties/Constraint Dialog", "Command Line"),
                expectedOutput = "Solves 2D geometric relationship between selected entities and updates associative dimensions.",
                limitations = "Requires 1 or 2 selected entities depending on constraint type.",
                verificationTestCases = listOf(
                    CADVerificationTestCase(
                        testCaseId = "TC-DOC-05A",
                        title = "Perpendicular & Equal Length Solver",
                        procedure = "Select two lines of lengths 40 and 60, apply Perpendicular and Equal constraints.",
                        expectedResult = "Second line rotates to 90° relative to first line and resizes to length 40.0."
                    )
                )
            ),
            CADCommandMatrixEntry(
                commandId = CadCommandId.DXF_IO,
                commandName = "DXFOUT",
                aliases = listOf("DXFOUT", "DXFIN", "EXPORTPDF", "EXPORTSVG"),
                category = CadCommandCategory.DOCUMENT_SYSTEM,
                supportedOptions = listOf("ASCII DXF Import/Export", "Plotted Sheet PDF Export", "Vector SVG Export"),
                inputMethods = listOf("Project Hub Dialog", "Command Line"),
                expectedOutput = "Round-trips layers, blocks, geometry, hatches, and dimensions to/from DXF and exports PDF/SVG sheets.",
                limitations = "Binary DWG requires DXF interchange.",
                verificationTestCases = listOf(
                    CADVerificationTestCase(
                        testCaseId = "TC-DOC-06A",
                        title = "Lossless DXF Export & Re-Import Round-Trip",
                        procedure = "Export drawing to DXF and re-import into workspace.",
                        expectedResult = "100% preservation of entity counts, coordinates, layers, units, and dimensions."
                    )
                )
            ),
            CADCommandMatrixEntry(
                commandId = CadCommandId.UNDO_REDO,
                commandName = "UNDO",
                aliases = listOf("UNDO", "U", "REDO"),
                category = CadCommandCategory.DOCUMENT_SYSTEM,
                supportedOptions = listOf("Multi-Step Transactional Undo", "Multi-Step Redo"),
                inputMethods = listOf("Top Bar Undo/Redo Icons", "Command Line"),
                expectedOutput = "Restores exact prior or subsequent document snapshot.",
                limitations = "Maintains up to 80 snapshots per session.",
                verificationTestCases = listOf(
                    CADVerificationTestCase(
                        testCaseId = "TC-DOC-07A",
                        title = "Multi-Step Command Undo & Redo",
                        procedure = "Execute LINE, FILLET, and QDIM, then Undo 3 times and Redo 3 times.",
                        expectedResult = "Document state restores bit-for-bit at every step."
                    )
                )
            )
        )
    }
}
