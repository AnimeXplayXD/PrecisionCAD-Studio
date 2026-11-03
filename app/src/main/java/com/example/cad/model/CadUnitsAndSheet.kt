package com.example.cad.model

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Shared locale-independent CAD numerical formatter (Section 3.2).
 * Always uses [Locale.ROOT] so engineering coordinates, lengths, and bearings
 * use "." as the decimal separator regardless of the user's system locale.
 */
object CadNumberFormatter {
    fun formatFixed(value: Double, decimals: Int = 3): String {
        val safe = if (value.isFinite() && abs(value) >= 1e-12) value else 0.0
        val clampedDecimals = decimals.coerceIn(0, 8)
        return String.format(Locale.ROOT, "%.${clampedDecimals}f", safe)
    }

    fun formatDecimal(
        value: Double,
        decimals: Int = 3,
        suppressTrailingZeros: Boolean = false
    ): String {
        val raw = formatFixed(value, decimals)
        return if (suppressTrailingZeros && raw.contains('.')) {
            raw.trimEnd('0').trimEnd('.')
        } else {
            raw
        }
    }

    fun formatAngleDeg(
        angleDeg: Double,
        decimals: Int = 2,
        withDegreeSymbol: Boolean = true
    ): String {
        val safe = if (angleDeg.isFinite() && abs(angleDeg) >= 1e-12) angleDeg else 0.0
        val clampedDecimals = decimals.coerceIn(0, 6)
        val num = String.format(Locale.ROOT, "%.${clampedDecimals}f", safe)
        return if (withDegreeSymbol) "$num°" else num
    }

    fun formatLineDiagnostic(
        start: Point2D,
        end: Point2D,
        length: Double,
        angleDeg: Double
    ): String {
        return "Start: (${formatFixed(start.x, 3)}, ${formatFixed(start.y, 3)})\n" +
            "End: (${formatFixed(end.x, 3)}, ${formatFixed(end.y, 3)})\n" +
            "Length: ${formatFixed(length, 3)}   Angle: ${formatAngleDeg(angleDeg, 2, true)}"
    }
}

/**
 * Supported drawing units with physical conversion to millimetres (mm).
 */
enum class CadUnit(
    val symbol: String,
    val label: String,
    val toMillimetersFactor: Double,
    val dxfInsUnitsCode: Int
) {
    MM("mm", "Millimetres (mm)", 1.0, 4),
    CM("cm", "Centimetres (cm)", 10.0, 5),
    M("m", "Metres (m)", 1000.0, 6),
    IN("in", "Inches (in)", 25.4, 1),
    FT("ft", "Feet (ft)", 304.8, 2);

    companion object {
        fun fromSymbol(symbol: String): CadUnit =
            entries.firstOrNull { it.symbol.equals(symbol, ignoreCase = true) } ?: MM

        fun fromDxfCode(code: Int): CadUnit =
            entries.firstOrNull { it.dxfInsUnitsCode == code } ?: MM
    }
}

/**
 * Explicit distinction when changing units on an existing drawing:
 * - CONVERT_DISPLAY_UNITS: Stored model coordinates remain untouched; display readouts convert.
 * - RESCALE_MODEL_GEOMETRY: Stored model coordinates are scaled by the conversion factor.
 */
enum class UnitChangeMode(val title: String, val explanation: String) {
    CONVERT_DISPLAY_UNITS(
        title = "A. Change Displayed Units Only (Non-Destructive)",
        explanation = "Stored model coordinates remain untouched. Display readouts and dimensions convert by the unit ratio (e.g., 100 mm displays as 10.0 cm)."
    ),
    RESCALE_MODEL_GEOMETRY(
        title = "B. Rescale Model-Space Geometry Coordinates",
        explanation = "Multiplies all stored model-space coordinates, radii, and offsets by the conversion factor so stored numerical values match the new unit directly."
    )
}

enum class DraftingStandard(val label: String) {
    ISO("ISO (Metric Technical)"),
    ANSI("ANSI / ASME")
}

enum class WorkspaceSpace(val label: String) {
    MODEL_SPACE("Model Space (1:1)"),
    PAPER_SPACE("Paper Space Sheet")
}

enum class SheetOrientation(val label: String) {
    LANDSCAPE("Landscape"),
    PORTRAIT("Portrait")
}

enum class SheetPreset(
    val label: String,
    val widthMm: Double,
    val heightMm: Double,
    val nativeUnit: CadUnit
) {
    ISO_A4("ISO A4 (297 × 210 mm)", 297.0, 210.0, CadUnit.MM),
    ISO_A3("ISO A3 (420 × 297 mm)", 420.0, 297.0, CadUnit.MM),
    ISO_A2("ISO A2 (594 × 420 mm)", 594.0, 420.0, CadUnit.MM),
    ISO_A1("ISO A1 (841 × 594 mm)", 841.0, 594.0, CadUnit.MM),
    ISO_A0("ISO A0 (1189 × 841 mm)", 1189.0, 841.0, CadUnit.MM),
    ANSI_A("ANSI A (11 × 8.5 in)", 279.4, 215.9, CadUnit.IN),
    ANSI_B("ANSI B (17 × 11 in)", 431.8, 279.4, CadUnit.IN),
    ANSI_C("ANSI C (22 × 17 in)", 558.8, 431.8, CadUnit.IN),
    ANSI_D("ANSI D (34 × 22 in)", 863.6, 558.8, CadUnit.IN),
    CUSTOM("Custom Dimensions", 300.0, 200.0, CadUnit.MM)
}

data class PrecisionFormatConfig(
    val linearDecimals: Int = 3,
    val angularDecimals: Int = 1,
    val showUnitSuffix: Boolean = true,
    val suppressTrailingZeros: Boolean = false,
    val suppressLeadingZero: Boolean = false
) {
    fun formatLinear(
        rawModelValue: Double,
        storageUnit: CadUnit,
        displayUnit: CadUnit
    ): String {
        val converted = rawModelValue * (storageUnit.toMillimetersFactor / displayUnit.toMillimetersFactor)
        val cleanVal = if (abs(converted) < 1e-9) 0.0 else converted
        var formatted = String.format(Locale.ROOT, "%.${linearDecimals.coerceIn(0, 6)}f", cleanVal)
        if (suppressTrailingZeros && formatted.contains('.')) {
            formatted = formatted.trimEnd('0').trimEnd('.')
        }
        if (suppressLeadingZero && formatted.startsWith("0.")) {
            formatted = formatted.removePrefix("0")
        } else if (suppressLeadingZero && formatted.startsWith("-0.")) {
            formatted = "-" + formatted.removePrefix("-0")
        }
        return if (showUnitSuffix) "$formatted ${displayUnit.symbol}" else formatted
    }

    fun formatCoord(
        rawModelValue: Double,
        storageUnit: CadUnit,
        displayUnit: CadUnit
    ): String {
        val converted = rawModelValue * (storageUnit.toMillimetersFactor / displayUnit.toMillimetersFactor)
        val cleanVal = if (abs(converted) < 1e-9) 0.0 else converted
        val num = String.format(Locale.ROOT, "%.${linearDecimals.coerceIn(0, 6)}f", cleanVal)
        return if (showUnitSuffix) "$num ${displayUnit.symbol}" else num
    }

    fun formatAngle(angleDeg: Double): String {
        val clean = if (abs(angleDeg) < 1e-7) 0.0 else angleDeg
        val num = String.format(Locale.ROOT, "%.${angularDecimals.coerceIn(0, 4)}f", clean)
        return "$num°"
    }
}

data class SheetSetupConfig(
    val preset: SheetPreset = SheetPreset.ISO_A3,
    val orientation: SheetOrientation = SheetOrientation.LANDSCAPE,
    val customWidthMm: Double = 420.0,
    val customHeightMm: Double = 297.0,
    val marginMm: Double = 10.0,
    val plotScaleNumerator: Double = 1.0,
    val plotScaleDenominator: Double = 1.0,
    val sheetUnit: CadUnit = CadUnit.MM,
    val draftingStandard: DraftingStandard = DraftingStandard.ISO,
    val titleBlockTitle: String = "PRECISION MOUNTING PLATE",
    val titleBlockDrawingNo: String = "DWG-2026-104",
    val titleBlockAuthor: String = "ENG. DEPT",
    val titleBlockRevision: String = "REV A",
    val showTitleBlock: Boolean = true
) {
    val effectiveWidthMm: Double
        get() {
            val baseW = if (preset == SheetPreset.CUSTOM) customWidthMm else preset.widthMm
            val baseH = if (preset == SheetPreset.CUSTOM) customHeightMm else preset.heightMm
            return if (orientation == SheetOrientation.LANDSCAPE) maxOf(baseW, baseH) else minOf(baseW, baseH)
        }

    val effectiveHeightMm: Double
        get() {
            val baseW = if (preset == SheetPreset.CUSTOM) customWidthMm else preset.widthMm
            val baseH = if (preset == SheetPreset.CUSTOM) customHeightMm else preset.heightMm
            return if (orientation == SheetOrientation.LANDSCAPE) minOf(baseW, baseH) else maxOf(baseW, baseH)
        }

    val plotScaleRatio: Double
        get() = (plotScaleNumerator / plotScaleDenominator.coerceAtLeast(0.0001)).coerceIn(0.001, 1000.0)

    val scaleLabel: String
        get() {
            val n = if (plotScaleNumerator % 1.0 == 0.0) plotScaleNumerator.roundToInt().toString() else plotScaleNumerator.toString()
            val d = if (plotScaleDenominator % 1.0 == 0.0) plotScaleDenominator.roundToInt().toString() else plotScaleDenominator.toString()
            return "$n:$d"
        }
}

data class GridAndSnapConfig(
    val showGrid: Boolean = true,
    val majorGridInterval: Double = 50.0,
    val minorGridSubdivisions: Int = 5,
    val gridSnapEnabled: Boolean = true,
    val gridSnapInterval: Double = 5.0,
    val osnapEnabled: Boolean = true,
    val enabledOsnapModes: Set<OsnapMode> = OsnapMode.defaultEnabledModes,
    val temporaryOsnapOverride: OsnapMode? = null,
    val orthoEnabled: Boolean = false,
    val polarTrackingEnabled: Boolean = true,
    val polarSnapEnabled: Boolean = false,
    val polarTrackingAngleDeg: Double = 45.0,
    val polarAdditionalAnglesDeg: List<Double> = listOf(22.5, 126.0),
    val polarSnapPresetAnglesDeg: Set<Double> = setOf(45.0, 90.0),
    val polarSnapCustomAnglesDeg: List<Double> = listOf(126.0),
    val polarRelativeToLastSegment: Boolean = false,
    val polarAcquisitionToleranceDeg: Double = 5.0
) {
    val referenceBasisLabel: String
        get() = if (polarRelativeToLastSegment) "Last Segment" else "+X"

    companion object {
        val standardPolarIncrementPresets: List<Double> = listOf(
            90.0, 60.0, 45.0, 30.0, 22.5, 18.0, 15.0, 10.0, 5.0
        )
        val wholeNumberVisualBearingsDeg: List<Double> = (0..359).map { it.toDouble() }
    }
}

enum class OsnapMode(val label: String, val badgeCode: String) {
    ENDPOINT("Endpoint", "END"),
    MIDPOINT("Midpoint", "MID"),
    CENTER("Centre", "CEN"),
    INTERSECTION("Intersection", "INT"),
    QUADRANT("Quadrant", "QUA"),
    PERPENDICULAR("Perpendicular", "PER"),
    TANGENT("Tangent", "TAN"),
    NEAREST("Nearest", "NEA"),
    PARALLEL("Parallel", "PAR"),
    EXTENSION("Extension", "EXT"),
    INSERTION("Insertion", "INS");

    companion object {
        val defaultEnabledModes: Set<OsnapMode> = setOf(
            ENDPOINT, MIDPOINT, CENTER, INTERSECTION, QUADRANT, PERPENDICULAR, TANGENT, INSERTION
        )
    }
}

enum class VirtualControllerMode(val label: String) {
    RELATIVE_TOUCHPAD("Touchpad"),
    VELOCITY_JOYSTICK("Joystick")
}

data class CursorControllerConfig(
    val enabled: Boolean = true,
    val mode: VirtualControllerMode = VirtualControllerMode.VELOCITY_JOYSTICK,
    val touchpadSensitivity: Float = 1.0f,
    val joystickMaxSpeedUnitsPerSec: Double = 140.0,
    val joystickVelocityExponent: Double = 2.2, // > 1.0 provides fine-control region near center
    val stepIncrement: Double = 1.0,
    val availableIncrements: List<Double> = listOf(10.0, 5.0, 1.0, 0.5, 0.1, 0.01),
    val isCollapsed: Boolean = false
)

typealias VirtualControllerConfig = CursorControllerConfig
