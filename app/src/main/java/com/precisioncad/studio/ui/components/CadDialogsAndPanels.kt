package com.precisioncad.studio.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import com.precisioncad.studio.cad.data.CadProjectMetadataEntity
import com.precisioncad.studio.cad.dimension.DimensionAlignmentReport
import com.precisioncad.studio.cad.dimension.DimensionArrangementPreference
import com.precisioncad.studio.cad.dimension.QDimDensityPolicy
import com.precisioncad.studio.cad.dimension.QDimOperatingMode
import com.precisioncad.studio.cad.kernel.CadSnapAndCoordEngine
import com.precisioncad.studio.cad.model.BlockDefinition
import com.precisioncad.studio.cad.model.CADCommandMatrix
import com.precisioncad.studio.cad.model.CadCommandCategory
import com.precisioncad.studio.cad.model.CadCommandId
import com.precisioncad.studio.cad.model.CadEntity
import com.precisioncad.studio.cad.model.CadLayer
import com.precisioncad.studio.cad.model.CadLineType
import com.precisioncad.studio.cad.model.CadNumberFormatter
import com.precisioncad.studio.cad.model.CadUnit
import com.precisioncad.studio.cad.model.ConstraintType
import com.precisioncad.studio.cad.model.CursorControllerConfig
import com.precisioncad.studio.cad.model.DimensionType
import com.precisioncad.studio.cad.model.DraftingStandard
import com.precisioncad.studio.cad.model.GridAndSnapConfig
import com.precisioncad.studio.cad.model.OsnapMode
import com.precisioncad.studio.cad.model.Point2D
import com.precisioncad.studio.cad.model.PrecisionFormatConfig
import com.precisioncad.studio.cad.model.SheetOrientation
import com.precisioncad.studio.cad.model.SheetPreset
import com.precisioncad.studio.cad.model.SheetSetupConfig
import com.precisioncad.studio.cad.model.UnitChangeMode
import com.precisioncad.studio.ui.theme.CadAmberSnap
import com.precisioncad.studio.ui.theme.CadBorderHairline
import com.precisioncad.studio.ui.theme.CadBorderStrong
import com.precisioncad.studio.ui.theme.CadCrimsonAlert
import com.precisioncad.studio.ui.theme.CadCyanPrimary
import com.precisioncad.studio.ui.theme.CadEmeraldCrossing
import com.precisioncad.studio.ui.theme.CadPanelElevated
import com.precisioncad.studio.ui.theme.CadPanelSurface
import com.precisioncad.studio.ui.theme.CadTextMuted
import com.precisioncad.studio.ui.theme.CadTextPrimary
import com.precisioncad.studio.ui.theme.CadTextSecondary
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * 1. Drawing Units & Precision Dialog (Section 3.1, 3.2, 3.3 & v17 Section 9).
 * Enforces explicit choice between Mode A (Change displayed units without changing physical geometry)
 * and Mode B (Rescale the drawing model-space coordinates) and persists the selected mode across sessions
 * with explicit visual ACTIVE/SELECTED confirmation. Uses CadBinaryToggleCard for binary switches.
 */
@Composable
fun UnitsAndPrecisionDialog(
    currentStorageUnit: CadUnit,
    currentDisplayUnit: CadUnit,
    currentUnitChangeMode: UnitChangeMode = UnitChangeMode.CONVERT_DISPLAY_UNITS,
    currentPrecision: PrecisionFormatConfig,
    onApplyUnitAndPrecision: (targetUnit: CadUnit, mode: UnitChangeMode, newPrecision: PrecisionFormatConfig) -> Unit,
    onDismiss: () -> Unit
) {
    // V19 §9.2: Hoist ScrollState outside AlertDialog body slot so selecting units, modes,
    // decimal places, or toggle switches never resets or shifts scroll position.
    val unitsDialogScrollState = rememberScrollState()
    val unitsChipScrollState = rememberScrollState()

    var selectedUnit by rememberSaveable(currentDisplayUnit) { mutableStateOf(currentDisplayUnit) }
    var selectedMode by rememberSaveable(currentUnitChangeMode) { mutableStateOf(currentUnitChangeMode) }
    var linearDecimals by rememberSaveable(currentPrecision.linearDecimals) { mutableIntStateOf(currentPrecision.linearDecimals) }
    var angularDecimals by rememberSaveable(currentPrecision.angularDecimals) { mutableIntStateOf(currentPrecision.angularDecimals) }
    var showSuffix by rememberSaveable(currentPrecision.showUnitSuffix) { mutableStateOf(currentPrecision.showUnitSuffix) }
    var suppressTrailing by rememberSaveable(currentPrecision.suppressTrailingZeros) { mutableStateOf(currentPrecision.suppressTrailingZeros) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CadPanelSurface,
        title = {
            Column {
                Text(
                    text = "Drawing Units & Precision Setup",
                    style = MaterialTheme.typography.titleLarge,
                    color = CadTextPrimary
                )
                Text(
                    text = "Saved Active State: ${currentDisplayUnit.label} • ${if (currentUnitChangeMode == UnitChangeMode.CONVERT_DISPLAY_UNITS) "Option A (Display Conversion)" else "Option B (Rescale Geometry)"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = CadEmeraldCrossing
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(unitsDialogScrollState)
                    .testTag("units_dialog_scroll_column"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Active Working Unit (Stored Base: ${currentStorageUnit.symbol})",
                    style = MaterialTheme.typography.labelMedium,
                    color = CadCyanPrimary
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(unitsChipScrollState),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CadUnit.entries.forEach { unit ->
                        key(unit.name) {
                            val isSel = selectedUnit == unit
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) CadCyanPrimary.copy(alpha = 0.22f) else CadPanelElevated)
                                    .border(
                                        if (isSel) 1.5.dp else 1.dp,
                                        if (isSel) CadCyanPrimary else CadBorderHairline,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { selectedUnit = unit }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                    .testTag("unit_option_${unit.symbol}"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier.size(14.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSel) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Selected Unit",
                                            tint = CadCyanPrimary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = unit.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isSel) CadCyanPrimary else CadTextPrimary,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = CadBorderHairline)
                Text(
                    text = "Unit-Change Operation Mode (Persistent Selection)",
                    style = MaterialTheme.typography.titleSmall,
                    color = CadAmberSnap
                )

                UnitChangeMode.entries.forEach { mode ->
                    key(mode.name) {
                        val isSelected = selectedMode == mode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) CadAmberSnap.copy(alpha = 0.16f)
                                    else CadPanelElevated.copy(alpha = 0.5f)
                                )
                                .border(
                                    if (isSelected) 1.5.dp else 1.dp,
                                    if (isSelected) CadAmberSnap else CadBorderHairline,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { selectedMode = mode }
                                .padding(10.dp)
                                .testTag("unit_mode_${mode.name}"),
                            verticalAlignment = Alignment.Top
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { selectedMode = mode }
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = mode.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (isSelected) CadAmberSnap else CadTextPrimary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    // V19 §9.2: Fixed-width badge slot prevents any row height or scroll shift
                                    Box(
                                        modifier = Modifier.width(86.dp),
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        if (isSelected) {
                                            Row(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(CadEmeraldCrossing.copy(alpha = 0.22f))
                                                    .border(1.dp, CadEmeraldCrossing, RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    .testTag("unit_mode_selected_badge_${mode.name}"),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = "Active Mode",
                                                    tint = CadEmeraldCrossing,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Text(
                                                    text = "SELECTED",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = CadEmeraldCrossing,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                                Text(
                                    text = mode.explanation,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = CadTextSecondary
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = CadBorderHairline)
                Text(
                    text = "Numeric Format & Display Precision",
                    style = MaterialTheme.typography.titleSmall,
                    color = CadTextPrimary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Linear Decimal Places: $linearDecimals", style = MaterialTheme.typography.bodyMedium, color = CadTextSecondary)
                    Row {
                        (0..5).forEach { dec ->
                            TextButton(
                                onClick = { linearDecimals = dec },
                                modifier = Modifier.size(34.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                            ) {
                                Text(
                                    text = dec.toString(),
                                    color = if (linearDecimals == dec) CadCyanPrimary else CadTextSecondary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Show Unit Suffix (${selectedUnit.symbol})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CadTextSecondary,
                        modifier = Modifier.weight(1f)
                    )
                    CadBinaryToggleCard(
                        enabled = showSuffix,
                        onToggle = { showSuffix = it },
                        label = "SUFFIX",
                        showLabelText = false,
                        testTag = "unit_show_suffix_toggle",
                        compact = true
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Suppress Trailing Zeros",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CadTextSecondary,
                        modifier = Modifier.weight(1f)
                    )
                    CadBinaryToggleCard(
                        enabled = suppressTrailing,
                        onToggle = { suppressTrailing = it },
                        label = "TRIM 0S",
                        showLabelText = false,
                        testTag = "unit_suppress_trailing_toggle",
                        compact = true
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onApplyUnitAndPrecision(
                        selectedUnit,
                        selectedMode,
                        PrecisionFormatConfig(
                            linearDecimals = linearDecimals,
                            angularDecimals = angularDecimals,
                            showUnitSuffix = showSuffix,
                            suppressTrailingZeros = suppressTrailing
                        )
                    )
                    onDismiss()
                },
                modifier = Modifier.testTag("apply_units_button")
            ) {
                Text("Apply Unit Settings")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * 2. Sheet & Page Setup Dialog (Section 3.4 & v17 Section 9).
 * Independent paper presets (ISO A0-A4, ANSI A-D, Custom), Portrait/Landscape, Plot Scale, and Title Block.
 */
@Composable
fun SheetSetupDialog(
    currentSetup: SheetSetupConfig,
    onApplySheetSetup: (SheetSetupConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var preset by remember { mutableStateOf(currentSetup.preset) }
    var orientation by remember { mutableStateOf(currentSetup.orientation) }
    var standard by remember { mutableStateOf(currentSetup.draftingStandard) }
    var scaleNum by remember { mutableStateOf(currentSetup.plotScaleNumerator.toString()) }
    var scaleDen by remember { mutableStateOf(currentSetup.plotScaleDenominator.toString()) }
    var titleText by remember { mutableStateOf(currentSetup.titleBlockTitle) }
    var dwgNoText by remember { mutableStateOf(currentSetup.titleBlockDrawingNo) }
    var showTitleBlock by remember { mutableStateOf(currentSetup.showTitleBlock) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CadPanelSurface,
        title = {
            Text(
                text = "Sheet & Page Layout Setup (Model Geometry Untouched)",
                style = MaterialTheme.typography.titleLarge,
                color = CadTextPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Paper Size Preset", style = MaterialTheme.typography.labelMedium, color = CadCyanPrimary)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SheetPreset.entries.forEach { p ->
                        val isSel = preset == p
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .background(if (isSel) CadCyanPrimary.copy(alpha = 0.22f) else CadPanelElevated)
                                .border(1.dp, if (isSel) CadCyanPrimary else CadBorderHairline, RoundedCornerShape(7.dp))
                                .clickable { preset = p }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("sheet_preset_${p.name}")
                        ) {
                            Text(
                                text = p.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSel) CadCyanPrimary else CadTextPrimary
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SheetOrientation.entries.forEach { orient ->
                        val isSel = orientation == orient
                        OutlinedButton(
                            onClick = { orientation = orient },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sheet_orientation_${orient.name}"),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isSel) CadCyanPrimary.copy(alpha = 0.18f) else Color.Transparent
                            )
                        ) {
                            Text(orient.label, color = if (isSel) CadCyanPrimary else CadTextSecondary)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DraftingStandard.entries.forEach { std ->
                        val isSel = standard == std
                        OutlinedButton(
                            onClick = { standard = std },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isSel) CadAmberSnap.copy(alpha = 0.18f) else Color.Transparent
                            )
                        ) {
                            Text(std.label, color = if (isSel) CadAmberSnap else CadTextSecondary)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = scaleNum,
                        onValueChange = { scaleNum = it },
                        label = { Text("Plot Scale Numerator") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Text(":", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(
                        value = scaleDen,
                        onValueChange = { scaleDen = it },
                        label = { Text("Denominator") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = titleText,
                    onValueChange = { titleText = it },
                    label = { Text("Title Block Drawing Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = dwgNoText,
                    onValueChange = { dwgNoText = it },
                    label = { Text("Drawing Number") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Display ISO/ANSI Title Block in Paper Space",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    CadBinaryToggleCard(
                        enabled = showTitleBlock,
                        onToggle = { showTitleBlock = it },
                        label = "TITLE BLOCK",
                        testTag = "sheet_show_title_block_toggle",
                        compact = true
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onApplySheetSetup(
                        currentSetup.copy(
                            preset = preset,
                            orientation = orientation,
                            draftingStandard = standard,
                            plotScaleNumerator = scaleNum.toDoubleOrNull()?.coerceAtLeast(0.01) ?: 1.0,
                            plotScaleDenominator = scaleDen.toDoubleOrNull()?.coerceAtLeast(0.01) ?: 1.0,
                            titleBlockTitle = titleText.ifBlank { "UNTITLED DRAWING" },
                            titleBlockDrawingNo = dwgNoText.ifBlank { "DWG-001" },
                            showTitleBlock = showTitleBlock
                        )
                    )
                    onDismiss()
                },
                modifier = Modifier.testTag("apply_sheet_setup_button")
            ) {
                Text("Save Sheet Setup")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * 3. Quick Dimension (QDIM) & Alignment Dialog (Version 17 Section 5).
 * Implements:
 * - Continuous dark dialog surface (no trapped inner black box).
 * - Single scroll-responsive header (`qdim_scroll_header`) & single scroll-responsive footer (`qdim_scroll_footer`).
 * - Compact single-button rotary/wheel selectors (`qdim_wheel_mode`, `qdim_wheel_arrangement`, `qdim_wheel_density`)
 *   with a 2-second delayed speech-bubble description callout (`qdim_callout_bubble_*`).
 * - Compact icon-only Refresh button (`qdim_refresh_button`) that rotates while running (no redundant Quick Alignment switch row).
 * - Footer two-card action deck (`qdim_footer_action_deck`):
 *   - Green foreground card: `Enable & Generate` (`execute_qdim_confirm_button`)
 *   - Red rear card: `Clear All` (`clear_all_dimensions_button`)
 *   - Downward swipe swaps front/rear cards without executing.
 *   - Short tap on `Clear All` opens destructive confirmation (`clear_all_dimensions_confirm_dialog`).
 *   - Long press on `Clear All` (or tapping `Selective Clear`) opens the Category-First Selective Dimension Removal window (`qdim_selective_clear_dialog`).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QuickDimensionDialog(
    isQuickDimensionEnabled: Boolean,
    selectedCount: Int,
    currentDensity: QDimDensityPolicy,
    currentArrangement: DimensionArrangementPreference = DimensionArrangementPreference.AUTO_CHAIN,
    alignmentReport: DimensionAlignmentReport? = null,
    totalDimensionCount: Int = 0,
    dimensionEntities: List<CadEntity.DimensionEntity> = emptyList(),
    storageUnit: CadUnit = CadUnit.MM,
    displayUnit: CadUnit = CadUnit.MM,
    precision: PrecisionFormatConfig = PrecisionFormatConfig(),
    onToggleQuickDimension: (Boolean) -> Unit,
    onRefreshQuickDimension: () -> Unit,
    onClearAllDimensions: () -> Unit = {},
    onRemoveSelectedDimensions: (Set<String>) -> Unit = {},
    onExecuteQDim: (
        mode: QDimOperatingMode,
        density: QDimDensityPolicy,
        replaceAutoDims: Boolean,
        arrangement: DimensionArrangementPreference
    ) -> Unit,
    onDismiss: () -> Unit
) {
    var mode by remember(selectedCount) {
        mutableStateOf(
            if (selectedCount > 0) QDimOperatingMode.QDIM_SELECT else QDimOperatingMode.QDIM_ALL
        )
    }
    var density by remember(currentDensity) { mutableStateOf(currentDensity) }
    var arrangement by remember(currentArrangement) { mutableStateOf(currentArrangement) }
    var showClearAllConfirm by remember { mutableStateOf(false) }
    var showSelectiveClearDialog by remember { mutableStateOf(false) }
    var isClearCardInFront by rememberSaveable { mutableStateOf(false) }
    var refreshSpinCount by remember { mutableIntStateOf(0) }

    // V19 §10.1: Parent-owned single-active help callout state with 5-second (5,000ms) auto-dismiss.
    // Never appears on initial dialog open; appears ONLY when the user selects/changes an option.
    data class QDimActiveHelpCallout(
        val selectorKey: String,
        val description: String,
        val requestId: Int
    )
    var activeHelpCallout by remember { mutableStateOf<QDimActiveHelpCallout?>(null) }
    var calloutRequestCounter by remember { mutableIntStateOf(0) }

    val scrollState = rememberScrollState()
    val isHeaderPinned by remember { derivedStateOf { scrollState.value > 24 } }
    val isFooterPinned by remember { derivedStateOf { scrollState.maxValue > 0 && scrollState.value < scrollState.maxValue - 16 } }

    LaunchedEffect(activeHelpCallout?.requestId) {
        val current = activeHelpCallout ?: return@LaunchedEffect
        delay(5_000L)
        if (activeHelpCallout?.requestId == current.requestId) {
            activeHelpCallout = null
        }
    }

    LaunchedEffect(scrollState.isScrollInProgress) {
        if (scrollState.isScrollInProgress) {
            activeHelpCallout = null
        }
    }

    val refreshRotationDeg by animateFloatAsState(
        targetValue = refreshSpinCount * 360f,
        animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing),
        label = "QDimRefreshSpin"
    )

    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            containerColor = CadPanelSurface,
            modifier = Modifier.testTag("clear_all_dimensions_confirm_dialog"),
            title = {
                Text(
                    text = "Clear All Dimensions?",
                    style = MaterialTheme.typography.titleMedium,
                    color = CadCrimsonAlert,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (totalDimensionCount > 0) {
                        "Remove all $totalDimensionCount dimensions (manual and Quick Dimensions) and auto center marks from this sheet? All drawing geometry, text notes, and leaders will be preserved. You can undo this action in one step."
                    } else {
                        "No dimensions are currently present on this sheet. Confirming will reset Quick Dimension state to OFF."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = CadTextPrimary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearAllConfirm = false
                        onClearAllDimensions()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CadCrimsonAlert,
                        contentColor = Color(0xFF1A0808)
                    ),
                    modifier = Modifier.testTag("clear_all_dimensions_confirm_button")
                ) {
                    Text("Clear All ($totalDimensionCount)", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearAllConfirm = false },
                    modifier = Modifier.testTag("clear_all_dimensions_cancel_button")
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showSelectiveClearDialog) {
        SelectiveDimensionRemovalDialog(
            dimensionEntities = dimensionEntities,
            storageUnit = storageUnit,
            displayUnit = displayUnit,
            precision = precision,
            onConfirmRemoveSelected = { idsToRemove ->
                showSelectiveClearDialog = false
                onRemoveSelectedDimensions(idsToRemove)
                onDismiss()
            },
            onDismiss = { showSelectiveClearDialog = false }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 640.dp)
                .border(1.dp, CadBorderStrong, RoundedCornerShape(14.dp))
                .testTag("qdim_dialog_surface"),
            color = CadPanelSurface,
            shape = RoundedCornerShape(14.dp),
            tonalElevation = 0.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Section 5.2 — Single Logical Scroll-Responsive Header
                val headerVerticalPadding by animateDpAsState(
                    targetValue = if (isHeaderPinned) 8.dp else 14.dp,
                    animationSpec = tween(180),
                    label = "QDimHeaderPadding"
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isHeaderPinned) CadPanelElevated else CadPanelSurface)
                        .padding(horizontal = 16.dp, vertical = headerVerticalPadding)
                        .testTag("qdim_scroll_header")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Quick Dimension (QDIM) & Alignment",
                                style = if (isHeaderPinned) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                                color = CadCyanPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            AnimatedVisibility(visible = !isHeaderPinned) {
                                Text(
                                    text = if (isQuickDimensionEnabled) {
                                        "Status: ACTIVE ($totalDimensionCount dims on sheet)"
                                    } else {
                                        "Status: STANDBY ($totalDimensionCount dims on sheet)"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isQuickDimensionEnabled) CadEmeraldCrossing else CadTextSecondary
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Section 5.5 — Compact Icon-Only Rotating Refresh Button
                            IconButton(
                                onClick = {
                                    refreshSpinCount += 1
                                    onRefreshQuickDimension()
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(CadPanelElevated)
                                    .border(1.dp, CadCyanPrimary.copy(alpha = 0.65f), CircleShape)
                                    .testTag("qdim_refresh_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh Quick Alignment & Dimensions",
                                    tint = CadCyanPrimary,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .graphicsLayer { rotationZ = refreshRotationDeg }
                                )
                            }

                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("qdim_close_icon_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close Quick Dimension Dialog",
                                    tint = CadTextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                if (isHeaderPinned) {
                    HorizontalDivider(color = CadBorderStrong)
                }

                // Continuous dark scrollable content (no inner black border box)
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .testTag("qdim_scrollable_body"),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 1. Operating Scope Mode Horizontal Option Card Deck (V19 §10.1 & §10.2)
                    QDimHorizontalCardSelector(
                        selectorKey = "mode",
                        stageBadge = "01 / 03",
                        title = "1. Operating Scope Mode",
                        items = QDimOperatingMode.entries,
                        selectedItem = mode,
                        itemTitle = { "${it.commandToken} — ${it.label.substringBefore(" (")}" },
                        itemDescription = { it.label },
                        accentColor = CadCyanPrimary,
                        optionTagPrefix = "qdim_mode_",
                        optionName = { it.name },
                        activeCalloutDescription = activeHelpCallout?.takeIf { it.selectorKey == "mode" }?.description,
                        onDismissCallout = { activeHelpCallout = null },
                        onSelect = { nextMode ->
                            mode = nextMode
                            calloutRequestCounter += 1
                            activeHelpCallout = QDimActiveHelpCallout(
                                selectorKey = "mode",
                                description = nextMode.label,
                                requestId = calloutRequestCounter
                            )
                        }
                    )

                    // 2. Alignment & Arrangement Horizontal Option Card Deck (V19 §10.1 & §10.2)
                    QDimHorizontalCardSelector(
                        selectorKey = "arrangement",
                        stageBadge = "02 / 03",
                        title = "2. Alignment & Arrangement Mode (DimensionAlignmentEngine)",
                        items = DimensionArrangementPreference.entries,
                        selectedItem = arrangement,
                        itemTitle = { it.label },
                        itemDescription = { it.description },
                        accentColor = CadEmeraldCrossing,
                        optionTagPrefix = "qdim_arrangement_",
                        optionName = { it.name },
                        activeCalloutDescription = activeHelpCallout?.takeIf { it.selectorKey == "arrangement" }?.description,
                        onDismissCallout = { activeHelpCallout = null },
                        onSelect = { nextArr ->
                            arrangement = nextArr
                            calloutRequestCounter += 1
                            activeHelpCallout = QDimActiveHelpCallout(
                                selectorKey = "arrangement",
                                description = nextArr.description,
                                requestId = calloutRequestCounter
                            )
                        }
                    )

                    // 3. Dimension Density Policy Horizontal Option Card Deck (V19 §10.1 & §10.2)
                    QDimHorizontalCardSelector(
                        selectorKey = "density",
                        stageBadge = "03 / 03",
                        title = "3. Dimension Density Policy",
                        items = QDimDensityPolicy.entries,
                        selectedItem = density,
                        itemTitle = { it.label },
                        itemDescription = { it.description },
                        accentColor = CadAmberSnap,
                        optionTagPrefix = "qdim_density_",
                        optionName = { it.name },
                        activeCalloutDescription = activeHelpCallout?.takeIf { it.selectorKey == "density" }?.description,
                        onDismissCallout = { activeHelpCallout = null },
                        onSelect = { nextDensity ->
                            density = nextDensity
                            calloutRequestCounter += 1
                            activeHelpCallout = QDimActiveHelpCallout(
                                selectorKey = "density",
                                description = nextDensity.description,
                                requestId = calloutRequestCounter
                            )
                        }
                    )

                    if (alignmentReport != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(CadPanelElevated)
                                .border(1.dp, CadBorderHairline, RoundedCornerShape(10.dp))
                                .padding(10.dp)
                                .testTag("qdim_alignment_report_card"),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "DimensionAlignmentEngine Report • Completeness: ${String.format(Locale.ROOT, "%.0f", alignmentReport.generationCompletenessScore)}% • Layout: ${String.format(Locale.ROOT, "%.0f", alignmentReport.layoutQualityScore)}/100",
                                style = MaterialTheme.typography.labelLarge,
                                color = CadCyanPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            alignmentReport.completenessFindings.forEach { finding ->
                                Text(
                                    text = "• [${finding.category.name}] ${finding.axisOrFeature}: ${finding.description}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (finding.isSymmetricOrEqualVerified) CadEmeraldCrossing else CadTextSecondary
                                )
                            }
                            alignmentReport.unresolvedWarnings.forEach { warn ->
                                Text(
                                    text = "⚠ $warn",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CadCrimsonAlert
                                )
                            }
                        }
                    }

                    // Bottom inset reservation so the last item is never covered by the footer
                    Spacer(modifier = Modifier.height(14.dp))
                }

                if (isFooterPinned) {
                    HorizontalDivider(color = CadBorderStrong)
                }

                // V19 §10.3 — Compact QDIM Footer Row:
                // 1. Shared Two-Card Action Deck (Foreground Generate / Update ↔ Rear Clear All with trash icon + count badge)
                // 2. Adjacent Compact Icon-First Selective Clear Button with checklist icon + count badge
                val footerDeckTimerState = rememberCadDeckIdleTimerState(
                    deckId = "qdim_footer_action_deck",
                    isVisibleAndInteractive = true,
                    peekDirection = CardPeekDirection.ABOVE
                )
                val isFooterRearRevealed = footerDeckTimerState.isRearCardRevealed

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isFooterPinned) CadPanelElevated else CadPanelSurface)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .testTag("qdim_scroll_footer"),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Two-Card Action Deck: Green (Generate / Update) + Crimson (Clear All)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .pointerInput(isClearCardInFront) {
                                var dragAccumY = 0f
                                detectVerticalDragGestures(
                                    onDragStart = { dragAccumY = 0f },
                                    onVerticalDrag = { change, dragAmount ->
                                        dragAccumY += dragAmount
                                        if (abs(dragAccumY) > 20f) {
                                            change.consume()
                                            dragAccumY = 0f
                                            footerDeckTimerState.onCardExchange(isCommandPromptOrDeck = false)
                                            isClearCardInFront = !isClearCardInFront
                                        }
                                    }
                                )
                            }
                            .testTag("qdim_footer_action_deck"),
                        contentAlignment = Alignment.Center
                    ) {
                        val enableOffsetY by animateDpAsState(
                            targetValue = when {
                                !isClearCardInFront -> if (isFooterRearRevealed) 2.dp else 0.dp
                                isFooterRearRevealed -> (-5).dp
                                else -> 0.dp
                            },
                            animationSpec = tween(180),
                            label = "QDimEnableCardOffsetY"
                        )
                        val enableScale by animateFloatAsState(
                            targetValue = if (!isClearCardInFront) 1f else if (isFooterRearRevealed) 0.95f else 0.92f,
                            animationSpec = tween(180),
                            label = "QDimEnableCardScale"
                        )
                        val enableAlpha by animateFloatAsState(
                            targetValue = if (!isClearCardInFront) 1f else if (isFooterRearRevealed) 0.85f else 0f,
                            animationSpec = tween(180),
                            label = "QDimEnableCardAlpha"
                        )

                        val clearOffsetY by animateDpAsState(
                            targetValue = when {
                                isClearCardInFront -> if (isFooterRearRevealed) 2.dp else 0.dp
                                isFooterRearRevealed -> (-5).dp
                                else -> 0.dp
                            },
                            animationSpec = tween(180),
                            label = "QDimClearCardOffsetY"
                        )
                        val clearScale by animateFloatAsState(
                            targetValue = if (isClearCardInFront) 1f else if (isFooterRearRevealed) 0.95f else 0.92f,
                            animationSpec = tween(180),
                            label = "QDimClearCardScale"
                        )
                        val clearAlpha by animateFloatAsState(
                            targetValue = if (isClearCardInFront) 1f else if (isFooterRearRevealed) 0.85f else 0f,
                            animationSpec = tween(180),
                            label = "QDimClearCardAlpha"
                        )

                        // Green Card: Generate / Update
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .zIndex(if (!isClearCardInFront) 1f else 0f)
                                .offset { IntOffset(0, enableOffsetY.roundToPx()) }
                                .graphicsLayer {
                                    scaleX = enableScale
                                    scaleY = enableScale
                                    alpha = enableAlpha
                                }
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0E2B1E))
                                .border(
                                    width = if (!isClearCardInFront) 1.5.dp else 1.dp,
                                    color = CadEmeraldCrossing,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    if (isClearCardInFront) {
                                        footerDeckTimerState.onCardExchange(isCommandPromptOrDeck = false)
                                        isClearCardInFront = false
                                    } else {
                                        onExecuteQDim(mode, density, true, arrangement)
                                        onDismiss()
                                    }
                                }
                                .padding(horizontal = 14.dp)
                                .testTag("execute_qdim_confirm_button"),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isQuickDimensionEnabled) "Update Dimensions" else "Generate Dimensions",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = Color(0xFFECFDF5),
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "${mode.commandToken} • ${arrangement.label} • ${density.label}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CadEmeraldCrossing,
                                        maxLines = 1
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Generate or Update Quick Dimensions",
                                    tint = CadEmeraldCrossing,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Crimson Card: Clear All (Trash icon + count badge)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .zIndex(if (isClearCardInFront) 1f else 0f)
                                .offset { IntOffset(0, clearOffsetY.roundToPx()) }
                                .graphicsLayer {
                                    scaleX = clearScale
                                    scaleY = clearScale
                                    alpha = clearAlpha
                                }
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF2B1115))
                                .border(
                                    width = if (isClearCardInFront) 1.5.dp else 1.dp,
                                    color = CadCrimsonAlert,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .combinedClickable(
                                    onClick = {
                                        if (!isClearCardInFront) {
                                            footerDeckTimerState.onCardExchange(isCommandPromptOrDeck = false)
                                            isClearCardInFront = true
                                        } else {
                                            showClearAllConfirm = true
                                        }
                                    },
                                    onLongClick = {
                                        showSelectiveClearDialog = true
                                    }
                                )
                                .padding(horizontal = 14.dp)
                                .testTag("clear_all_dimensions_button"),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Clear All Dimensions",
                                        tint = CadCrimsonAlert,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Clear All",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = Color(0xFFFEE2E2),
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(CadCrimsonAlert.copy(alpha = 0.22f))
                                        .border(1.dp, CadCrimsonAlert, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 7.dp, vertical = 2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = totalDimensionCount.toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CadCrimsonAlert,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // 2. Adjacent Compact Icon-First Selective Clear Button (Checklist icon + count badge)
                    Row(
                        modifier = Modifier
                            .height(46.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(CadPanelElevated)
                            .border(1.dp, CadCrimsonAlert.copy(alpha = 0.75f), RoundedCornerShape(10.dp))
                            .clickable { showSelectiveClearDialog = true }
                            .padding(horizontal = 10.dp)
                            .testTag("qdim_open_selective_clear_button"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Checklist,
                            contentDescription = "Open Selective Dimension Removal",
                            tint = CadCrimsonAlert,
                            modifier = Modifier.size(18.dp)
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(CadCrimsonAlert.copy(alpha = 0.20f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = dimensionEntities.size.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = CadCrimsonAlert,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * V19 §10.1 & §10.2 — Horizontal Multi-Option Card Deck Selector with Parent-Owned Single-Active 5-Second Help Callout.
 */
@Composable
private fun <T> QDimHorizontalCardSelector(
    selectorKey: String,
    stageBadge: String,
    title: String,
    items: List<T>,
    selectedItem: T,
    itemTitle: (T) -> String,
    itemDescription: (T) -> String,
    accentColor: Color,
    optionTagPrefix: String,
    optionName: (T) -> String,
    activeCalloutDescription: String?,
    onDismissCallout: () -> Unit,
    onSelect: (T) -> Unit
) {
    val currentIndex = items.indexOf(selectedItem).coerceAtLeast(0)

    val cardOptions = remember(items, accentColor) {
        items.map { item ->
            val rawTitle = itemTitle(item)
            val shortBadge = rawTitle.substringBefore(" —").take(12).uppercase(Locale.ROOT)
            CadHorizontalCardOption(
                value = item,
                title = rawTitle,
                subtitle = itemDescription(item),
                shortBadge = shortBadge,
                accentColor = accentColor,
                containerColor = Color(0xFF101923)
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("qdim_wheel_$selectorKey"),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = CadTextPrimary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(accentColor.copy(alpha = 0.16f))
                        .border(1.dp, accentColor.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                        .testTag("qdim_stage_badge_$selectorKey")
                ) {
                    Text(
                        text = stageBadge,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        color = accentColor,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "${currentIndex + 1}/${items.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Fixed-height container holding both the horizontal card deck and the non-layout-shifting overlay callout
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                CadHorizontalOptionCardDeck(
                    deckId = "qdim_deck_$selectorKey",
                    options = cardOptions,
                    selectedValue = selectedItem,
                    onSelect = { nextItem ->
                        onSelect(nextItem)
                    },
                    testTag = "qdim_wheel_button_$selectorKey"
                )

                // Direct option chips for instant jump & testTag discovery
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    items.forEach { item ->
                        val isSel = item == selectedItem
                        val name = optionName(item)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(if (isSel) accentColor.copy(alpha = 0.24f) else CadPanelSurface)
                                .border(1.dp, if (isSel) accentColor else CadBorderHairline, RoundedCornerShape(5.dp))
                                .clickable {
                                    onSelect(item)
                                }
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                                .testTag("$optionTagPrefix$name")
                        ) {
                            Text(
                                text = itemTitle(item).substringBefore(" —").take(18),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                color = if (isSel) accentColor else CadTextSecondary,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // V19 §10.1 — Single-Active 5-Second Overlay Callout (0 px height change when appearing/disappearing)
            androidx.compose.animation.AnimatedVisibility(
                visible = activeCalloutDescription != null,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(120)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(10f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onDismissCallout() }
                        .testTag("qdim_callout_bubble_$selectorKey")
                ) {
                    androidx.compose.foundation.Canvas(
                        modifier = Modifier
                            .padding(start = 24.dp)
                            .width(12.dp)
                            .height(5.dp)
                    ) {
                        val path = Path().apply {
                            moveTo(size.width * 0.5f, 0f)
                            lineTo(0f, size.height)
                            lineTo(size.width, size.height)
                            close()
                        }
                        drawPath(path = path, color = accentColor)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xF20B131D))
                            .border(1.dp, accentColor, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = activeCalloutDescription ?: itemDescription(selectedItem),
                            style = MaterialTheme.typography.labelSmall,
                            color = CadTextPrimary,
                            maxLines = 2
                        )
                    }
                }
            }
        }
    }
}

/**
 * Phase 8 — Category-First Selective Dimension Removal Window with Transient Marquee Selection Notification.
 * - Removes the permanent master-selection status box from the list flow.
 * - Keeps `Select All` / `Deselect All` (`selective_clear_master_toggle`) in a compact header bar.
 * - Displays a transient single-line marquee selection banner (`selective_clear_transient_marquee`) as a
 *   non-layout-shifting overlay ONLY after a selection change, automatically retracting after one marquee pass.
 * - Preserves `LazyListState` scroll position across category and item toggles (`0 px` jump).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SelectiveDimensionRemovalDialog(
    dimensionEntities: List<CadEntity.DimensionEntity>,
    storageUnit: CadUnit,
    displayUnit: CadUnit,
    precision: PrecisionFormatConfig,
    onConfirmRemoveSelected: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val groupedByType = remember(dimensionEntities) {
        DimensionType.entries.mapNotNull { type ->
            val matching = dimensionEntities.filter { it.dimensionType == type }
            if (matching.isNotEmpty()) type to matching else null
        }
    }

    val lazyListState = rememberLazyListState()

    // Level 1: Categories checked by default
    var checkedCategories by remember(dimensionEntities) {
        mutableStateOf(groupedByType.map { it.first }.toSet())
    }
    // Level 2: Individual dimension IDs checked when their category is unchecked
    var individualCheckedIds by remember(dimensionEntities) {
        mutableStateOf(emptySet<String>())
    }
    var showConfirmAlert by remember { mutableStateOf(false) }

    // Transient marquee notification state (only triggered by user selection changes after initial open)
    var selectionChangeTick by remember { mutableIntStateOf(0) }
    var showTransientMarquee by remember { mutableStateOf(false) }

    LaunchedEffect(selectionChangeTick) {
        if (selectionChangeTick > 0) {
            showTransientMarquee = true
            delay(2500L)
            showTransientMarquee = false
        }
    }

    // Effective IDs selected for removal across both levels
    val effectiveSelectedIds = remember(checkedCategories, individualCheckedIds, groupedByType) {
        val result = mutableSetOf<String>()
        groupedByType.forEach { (type, dims) ->
            if (type in checkedCategories) {
                dims.forEach { result.add(it.id) }
            } else {
                dims.filter { it.id in individualCheckedIds }.forEach { result.add(it.id) }
            }
        }
        result.toSet()
    }

    if (showConfirmAlert) {
        AlertDialog(
            onDismissRequest = { showConfirmAlert = false },
            containerColor = CadPanelSurface,
            modifier = Modifier.testTag("selective_clear_confirm_alert"),
            title = {
                Text(
                    text = "Remove ${effectiveSelectedIds.size} Selected Dimension(s)?",
                    style = MaterialTheme.typography.titleMedium,
                    color = CadCrimsonAlert,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Only the ${effectiveSelectedIds.size} selected dimension(s) will be removed in a single undoable step. All unselected dimensions and drawing geometry will remain untouched.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CadTextPrimary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmAlert = false
                        onConfirmRemoveSelected(effectiveSelectedIds)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CadCrimsonAlert),
                    modifier = Modifier.testTag("selective_clear_confirm_alert_button")
                ) {
                    Text("Confirm Remove (${effectiveSelectedIds.size})", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmAlert = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 600.dp)
                .border(1.dp, CadBorderStrong, RoundedCornerShape(14.dp))
                .testTag("qdim_selective_clear_dialog"),
            color = CadPanelSurface,
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val allCount = dimensionEntities.size
                val allSelected = allCount > 0 && effectiveSelectedIds.size == allCount

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Selective Dimension Removal",
                            style = MaterialTheme.typography.titleLarge,
                            color = CadCrimsonAlert,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Categories lock children when checked • Uncheck category to pick items",
                            style = MaterialTheme.typography.labelSmall,
                            color = CadTextSecondary
                        )
                    }

                    // Compact Select All / Deselect All header action chip (replaces permanent status box)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(CadPanelElevated)
                            .border(1.dp, CadCyanPrimary.copy(alpha = 0.7f), RoundedCornerShape(7.dp))
                            .clickable {
                                if (allSelected) {
                                    checkedCategories = emptySet()
                                    individualCheckedIds = emptySet()
                                } else {
                                    checkedCategories = groupedByType.map { it.first }.toSet()
                                    individualCheckedIds = emptySet()
                                }
                                selectionChangeTick += 1
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("selective_clear_master_toggle")
                    ) {
                        Text(
                            text = if (allSelected) "DESELECT ALL" else "SELECT ALL",
                            style = MaterialTheme.typography.labelSmall,
                            color = CadCyanPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close Selective Removal", tint = CadTextSecondary)
                    }
                }

                // Box wrapping list/empty body + non-layout-shifting transient marquee notification overlay
                Box(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                ) {
                    if (groupedByType.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No dimensions currently exist on the sheet.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = CadTextSecondary
                            )
                        }
                    } else {
                        LazyColumn(
                            state = lazyListState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("selective_clear_lazy_list"),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(groupedByType, key = { it.first.name }) { (dimType, dims) ->
                                val isCatChecked = dimType in checkedCategories
                                val unlockedChildSelectedCount = dims.count { it.id in individualCheckedIds }
                                val triState = when {
                                    isCatChecked -> ToggleableState.On
                                    unlockedChildSelectedCount == 0 -> ToggleableState.Off
                                    unlockedChildSelectedCount == dims.size -> ToggleableState.On
                                    else -> ToggleableState.Indeterminate
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(CadPanelElevated)
                                        .border(
                                            width = 1.dp,
                                            color = if (isCatChecked) CadCrimsonAlert.copy(alpha = 0.7f) else CadBorderHairline,
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .padding(10.dp)
                                        .testTag("selective_clear_category_${dimType.name}"),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    // Level 1 Category Header Row
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                if (isCatChecked) {
                                                    checkedCategories = checkedCategories - dimType
                                                    individualCheckedIds = individualCheckedIds - dims.map { it.id }.toSet()
                                                } else {
                                                    checkedCategories = checkedCategories + dimType
                                                    individualCheckedIds = individualCheckedIds - dims.map { it.id }.toSet()
                                                }
                                                selectionChangeTick += 1
                                            },
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            TriStateCheckbox(
                                                state = triState,
                                                onClick = {
                                                    if (isCatChecked) {
                                                        checkedCategories = checkedCategories - dimType
                                                        individualCheckedIds = individualCheckedIds - dims.map { it.id }.toSet()
                                                    } else {
                                                        checkedCategories = checkedCategories + dimType
                                                        individualCheckedIds = individualCheckedIds - dims.map { it.id }.toSet()
                                                    }
                                                    selectionChangeTick += 1
                                                },
                                                modifier = Modifier.testTag("selective_clear_cat_checkbox_${dimType.name}")
                                            )
                                            Column(modifier = Modifier.padding(start = 6.dp)) {
                                                Text(
                                                    text = "${dimType.label} Dimension (${dims.size})",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    color = CadTextPrimary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = if (isCatChecked) {
                                                        "Category Checked • All ${dims.size} selected (Uncheck category to unlock individual items)"
                                                    } else {
                                                        "Category Unchecked • Individual items unlocked ($unlockedChildSelectedCount/${dims.size} selected)"
                                                    },
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (isCatChecked) CadAmberSnap else CadCyanPrimary
                                                )
                                            }
                                        }
                                        Icon(
                                            imageVector = if (isCatChecked) Icons.Default.Lock else Icons.Default.LockOpen,
                                            contentDescription = if (isCatChecked) "Children Locked" else "Children Unlocked",
                                            tint = if (isCatChecked) CadAmberSnap else CadCyanPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    // Level 2 Individual Child Dimension Rows
                                    dims.forEach { dim ->
                                        val isChildChecked = isCatChecked || (dim.id in individualCheckedIds)
                                        val formattedMeasurement = formatDimensionPreviewLabel(dim, storageUnit, displayUnit, precision)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 18.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (isChildChecked) Color(0xFF221216) else CadPanelSurface)
                                                .border(1.dp, CadBorderHairline, RoundedCornerShape(6.dp))
                                                .clickable(enabled = !isCatChecked) {
                                                    individualCheckedIds = if (dim.id in individualCheckedIds) {
                                                        individualCheckedIds - dim.id
                                                    } else {
                                                        individualCheckedIds + dim.id
                                                    }
                                                    selectionChangeTick += 1
                                                }
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                                .testTag("selective_clear_item_${dim.id}"),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Checkbox(
                                                    checked = isChildChecked,
                                                    onCheckedChange = if (!isCatChecked) {
                                                        { checked ->
                                                            individualCheckedIds = if (checked) {
                                                                individualCheckedIds + dim.id
                                                            } else {
                                                                individualCheckedIds - dim.id
                                                            }
                                                            selectionChangeTick += 1
                                                        }
                                                    } else null,
                                                    enabled = !isCatChecked,
                                                    modifier = Modifier.testTag("selective_clear_item_checkbox_${dim.id}")
                                                )
                                                Column(modifier = Modifier.padding(start = 6.dp)) {
                                                    Text(
                                                        text = formattedMeasurement,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = if (isCatChecked) CadTextSecondary else CadTextPrimary,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = "${if (dim.isAutoGenerated) "QDIM Auto" else "Manual"} • (${String.format(Locale.ROOT, "%.1f", dim.anchor1.x)}, ${String.format(Locale.ROOT, "%.1f", dim.anchor1.y)})",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = CadTextMuted
                                                    )
                                                }
                                            }
                                            Text(
                                                text = if (isCatChecked) "LOCKED" else if (isChildChecked) "REMOVE" else "KEEP",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = when {
                                                    isCatChecked -> CadAmberSnap
                                                    isChildChecked -> CadCrimsonAlert
                                                    else -> CadEmeraldCrossing
                                                },
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Phase 8.1 & 8.2 — Transient Marquee Selection Notification Overlay (Zero layout shift)
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showTransientMarquee,
                        enter = fadeIn(tween(150)),
                        exit = fadeOut(tween(180)),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .zIndex(10f)
                    ) {
                        val marqueeMessage = if (allSelected) {
                            "All $allCount dimensions selected for removal (${checkedCategories.size} categories locked)"
                        } else {
                            "${effectiveSelectedIds.size} of $allCount dimensions selected for removal • ${checkedCategories.size} categories locked"
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xF0101A26))
                                .border(1.dp, CadCyanPrimary, RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .testTag("selective_clear_transient_marquee")
                        ) {
                            Text(
                                text = marqueeMessage,
                                style = MaterialTheme.typography.labelMedium,
                                color = CadCyanPrimary,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .basicMarquee(iterations = 1, initialDelayMillis = 150)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("selective_dim_cancel_button")
                    ) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { showConfirmAlert = true },
                        enabled = effectiveSelectedIds.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = CadCrimsonAlert),
                        modifier = Modifier.testTag("selective_clear_confirm_button")
                    ) {
                        Text(
                            text = "Remove ${effectiveSelectedIds.size}",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

private fun formatDimensionPreviewLabel(
    dim: CadEntity.DimensionEntity,
    storageUnit: CadUnit,
    displayUnit: CadUnit,
    precision: PrecisionFormatConfig
): String {
    dim.textOverride?.takeIf { it.isNotBlank() }?.let { return it }
    val rawVal = dim.measuredModelValue()
    return when (dim.dimensionType) {
        DimensionType.DIAMETER -> "⌀${precision.formatLinear(rawVal, storageUnit, displayUnit)}"
        DimensionType.RADIUS -> "R${precision.formatLinear(rawVal, storageUnit, displayUnit)}"
        DimensionType.ANGULAR -> precision.formatAngle(rawVal)
        DimensionType.ORDINATE_X -> "X=${precision.formatCoord(rawVal, storageUnit, displayUnit)}"
        DimensionType.ORDINATE_Y -> "Y=${precision.formatCoord(rawVal, storageUnit, displayUnit)}"
        else -> "${dim.dimensionType.label}: ${precision.formatLinear(rawVal, storageUnit, displayUnit)}"
    }
}

/**
 * 4. Comprehensive CAD Command Compatibility Matrix Dialog (Section 5).
 * Displays all 43 commands with search & category filtering, options, input methods,
 * expected output, limitations, test case IDs, and direct "Run Command" buttons.
 */
@Composable
fun CommandCompatibilityMatrixDialog(
    onRunCommand: (CadCommandId) -> Unit,
    onDismiss: () -> Unit
) {
    val matrix = remember { CADCommandMatrix() }
    var selectedCategory by remember { mutableStateOf<CadCommandCategory?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredSpecs = remember(matrix, selectedCategory, searchQuery) {
        matrix.filter(category = selectedCategory, query = searchQuery)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.90f)
                .border(1.dp, CadBorderHairline, RoundedCornerShape(14.dp))
                .testTag("command_compatibility_matrix_dialog"),
            color = CadPanelSurface,
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "CAD Command Compatibility Matrix",
                            style = MaterialTheme.typography.titleLarge,
                            color = CadCyanPrimary
                        )
                        Text(
                            text = "${matrix.entries.size} Desktop 2D Commands • Verified Options, Limits & Test Cases",
                            style = MaterialTheme.typography.bodyMedium,
                            color = CadTextSecondary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close Matrix", tint = CadTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search command, alias (L, PL, QDIM, TR, F), or test ID...") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("matrix_search_input")
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Category filter chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MatrixFilterChip(
                        label = "ALL (${matrix.entries.size})",
                        selected = selectedCategory == null,
                        onClick = { selectedCategory = null }
                    )
                    CadCommandCategory.entries.forEach { cat ->
                        MatrixFilterChip(
                            label = cat.label,
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredSpecs, key = { it.commandId.name }) { spec ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(CadPanelElevated)
                                .border(1.dp, CadBorderHairline, RoundedCornerShape(10.dp))
                                .padding(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Verified",
                                        tint = CadEmeraldCrossing,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${spec.commandName} (${spec.aliases.joinToString(", ")})",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = CadCyanPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Button(
                                    onClick = {
                                        onRunCommand(spec.commandId)
                                        onDismiss()
                                    },
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("RUN", style = MaterialTheme.typography.labelSmall)
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Options: ${spec.supportedOptions.joinToString(" • ")}",
                                style = MaterialTheme.typography.labelMedium,
                                color = CadAmberSnap
                            )
                            Text(
                                text = "Input Methods: ${spec.inputMethods.joinToString(", ")}",
                                style = MaterialTheme.typography.labelSmall,
                                color = CadTextSecondary
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "Expected Output: ${spec.expectedOutput}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = CadTextPrimary
                            )
                            Text(
                                text = "Scope / Limitations: ${spec.limitations}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = CadTextMuted
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            spec.verificationTestCases.forEach { tc ->
                                Text(
                                    text = "✓ [${tc.testCaseId}] ${tc.title} → ${tc.expectedResult}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CadEmeraldCrossing
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MatrixFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) CadCyanPrimary.copy(alpha = 0.22f) else CadPanelElevated)
            .border(1.dp, if (selected) CadCyanPrimary else CadBorderHairline, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) CadCyanPrimary else CadTextSecondary,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

/**
 * 5. Layer & Block Manager Dialog.
 */
@Composable
fun LayerAndBlockManagerDialog(
    layers: List<CadLayer>,
    activeLayerName: String,
    blocks: List<BlockDefinition>,
    selectedCount: Int,
    onSetActiveLayer: (String) -> Unit,
    onAddLayer: (name: String, colorHex: Long, lineType: CadLineType, weightMm: Float) -> Unit,
    onUpdateLayer: (CadLayer) -> Unit,
    onCreateBlockFromSelection: (blockName: String) -> Unit,
    onInsertBlock: (blockName: String) -> Unit,
    onDismiss: () -> Unit
) {
    var newLayerName by remember { mutableStateOf("") }
    var newBlockName by remember { mutableStateOf("CUSTOM_PART_1") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CadPanelSurface,
        title = {
            Text("Layer & Reusable Block Manager", style = MaterialTheme.typography.titleLarge, color = CadTextPrimary)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Document Layers (Tap to set Active)", style = MaterialTheme.typography.titleSmall, color = CadCyanPrimary)
                layers.forEach { layer ->
                    val isCurrent = layer.name == activeLayerName
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isCurrent) CadCyanPrimary.copy(alpha = 0.14f) else CadPanelElevated)
                            .border(1.dp, if (isCurrent) CadCyanPrimary else CadBorderHairline, RoundedCornerShape(8.dp))
                            .clickable { onSetActiveLayer(layer.name) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(Color(layer.colorHex or 0xFF000000L))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = if (isCurrent) "${layer.name} (ACTIVE)" else layer.name,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = CadTextPrimary
                                )
                                Text(
                                    text = "${layer.lineType.label} • ${layer.lineWeightMm} mm",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CadTextSecondary
                                )
                            }
                        }

                        Row {
                            IconButton(
                                onClick = { onUpdateLayer(layer.copy(isVisible = !layer.isVisible)) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (layer.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Toggle Layer Visibility",
                                    tint = if (layer.isVisible) CadCyanPrimary else CadTextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = { onUpdateLayer(layer.copy(isLocked = !layer.isLocked)) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (layer.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                    contentDescription = "Toggle Layer Lock",
                                    tint = if (layer.isLocked) CadAmberSnap else CadTextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newLayerName,
                        onValueChange = { newLayerName = it },
                        label = { Text("New Layer Name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            if (newLayerName.isNotBlank()) {
                                onAddLayer(newLayerName.trim().uppercase(), 0xFF10B981L, CadLineType.CONTINUOUS, 0.25f)
                                newLayerName = ""
                            }
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("Add")
                    }
                }

                HorizontalDivider(color = CadBorderHairline)
                Text("Reusable Blocks (${blocks.size})", style = MaterialTheme.typography.titleSmall, color = CadAmberSnap)
                blocks.forEach { blk ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(CadPanelElevated)
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(blk.name, style = MaterialTheme.typography.labelLarge, color = CadTextPrimary)
                            Text("${blk.entities.size} entities", style = MaterialTheme.typography.labelSmall, color = CadTextSecondary)
                        }
                        OutlinedButton(
                            onClick = {
                                onInsertBlock(blk.name)
                                onDismiss()
                            }
                        ) {
                            Text("Insert at Cursor")
                        }
                    }
                }

                if (selectedCount > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newBlockName,
                            onValueChange = { newBlockName = it },
                            label = { Text("Block Name from $selectedCount Selected") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                if (newBlockName.isNotBlank()) {
                                    onCreateBlockFromSelection(newBlockName.trim().uppercase())
                                }
                            }
                        ) {
                            Text("Create Block")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

/**
 * 6. Direct Coordinate, Parameter & Controller Settings Dialogs.
 */
enum class CoordinateInputMode {
    CARTESIAN_XY,
    POLAR_OR_EXPRESSION
}

/**
 * Section 7.2 — Authoritative edit source for Direct Coordinate & Polar Input synchronization.
 */
enum class CoordinateEditSource {
    CARTESIAN_XY,
    BEARING_THETA,
    POLAR_EXPRESSION
}

enum class CoordinateDialogStage {
    ENTRY,
    PREVIEW
}

/**
 * 6. Direct Coordinate, Parameter & Controller Settings Dialogs (V19 §12).
 * Implements:
 * - Mutually exclusive Cartesian (`X/Y/θ` + two-card vertical-swipe ABS/REL reference deck with continuous marquee)
 *   vs Polar/Expression (`coord_input_polar_field` + read-only resolved preview) composition subtrees.
 * - Two-stage workflow:
 *   - Stage 1 (`ENTRY`): Opaque dialog surface (`coord_dialog_entry_state`) with `Cancel` and `Move Cursor` (`coord_move_cursor_button`).
 *   - Stage 2 (`PREVIEW`): Translucent dialog surface (`Color(0x6B0B1118)`, `coord_dialog_preview_state` / `coord_preview_translucent_surface`)
 *     showing the live CAD canvas and moved cursor underneath, with `Cancel` (restoring cursor) and `Pick` (`coord_pick_button` / `submit_coordinate_button`).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DirectCoordinateInputDialog(
    currentCursor: Point2D,
    referencePoint: Point2D?,
    displayUnit: CadUnit,
    onBeginPreviewSession: () -> Unit = {},
    onPreviewCoordinate: (String) -> Boolean = { true },
    onCommitPickCoordinate: (String?) -> Boolean = { false },
    onCancelPreviewSession: () -> Unit = {},
    onSubmitCoordinateExpression: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val originRef = referencePoint ?: Point2D(0.0, 0.0)
    val initialRadius = remember(currentCursor, originRef) {
        val d = originRef.distanceTo(currentCursor)
        if (d > 1e-6) d else 25.0
    }
    val initialBearing = remember(currentCursor, originRef) {
        if (originRef.distanceTo(currentCursor) > 1e-6) {
            ((originRef.angleToDeg(currentCursor) % 360.0) + 360.0) % 360.0
        } else {
            0.0
        }
    }

    var dialogStage by rememberSaveable { mutableStateOf(CoordinateDialogStage.ENTRY) }
    var mode by rememberSaveable { mutableStateOf(CoordinateInputMode.CARTESIAN_XY) }
    var editSource by rememberSaveable { mutableStateOf(CoordinateEditSource.CARTESIAN_XY) }
    var isRelativeCartesian by rememberSaveable { mutableStateOf(false) }
    var xText by rememberSaveable {
        mutableStateOf(String.format(Locale.ROOT, "%.2f", currentCursor.x))
    }
    var yText by rememberSaveable {
        mutableStateOf(String.format(Locale.ROOT, "%.2f", currentCursor.y))
    }
    var thetaText by rememberSaveable {
        mutableStateOf(String.format(Locale.ROOT, "%.1f", initialBearing))
    }
    var radialDistanceR by rememberSaveable {
        mutableDoubleStateOf(initialRadius)
    }
    var polarText by rememberSaveable {
        mutableStateOf("@30<45")
    }
    var validationError by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        onBeginPreviewSession()
    }

    val resolvedPolarPreview = remember(mode, polarText, referencePoint, currentCursor) {
        if (mode == CoordinateInputMode.POLAR_OR_EXPRESSION) {
            CadSnapAndCoordEngine.parseCoordinateInput(
                rawInput = polarText.trim(),
                referencePoint = referencePoint,
                currentCursor = currentCursor,
                orthoEnabled = false
            )
        } else {
            null
        }
    }

    fun buildValidatedExpression(): String? {
        return if (mode == CoordinateInputMode.CARTESIAN_XY) {
            val parsedX = xText.trim().toDoubleOrNull()?.takeIf { it.isFinite() }
            val parsedY = yText.trim().toDoubleOrNull()?.takeIf { it.isFinite() }
            if (parsedX == null || parsedY == null) {
                validationError = "Enter valid numeric X, Y, or θ (°) coordinates."
                null
            } else {
                val prefix = if (isRelativeCartesian) "@" else ""
                "$prefix${xText.trim()},${yText.trim()}"
            }
        } else {
            val rawExpr = polarText.trim()
            val resolved = CadSnapAndCoordEngine.parseCoordinateInput(
                rawInput = rawExpr,
                referencePoint = referencePoint,
                currentCursor = currentCursor,
                orthoEnabled = false
            )
            if (resolved == null) {
                validationError = "Invalid polar or coordinate expression (use e.g. 50<45, @30<90, @25,-10, or 40)."
                null
            } else {
                rawExpr
            }
        }
    }

    val isPreviewStage = dialogStage == CoordinateDialogStage.PREVIEW
    val surfaceColor = if (isPreviewStage) Color(0x6B0B1118) else CadPanelSurface

    Dialog(
        onDismissRequest = {
            onCancelPreviewSession()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .border(
                    width = 1.dp,
                    color = if (isPreviewStage) CadCyanPrimary else CadBorderStrong,
                    shape = RoundedCornerShape(14.dp)
                )
                .testTag("direct_coordinate_input_dialog"),
            color = surfaceColor,
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
                    .testTag(
                        if (isPreviewStage) "coord_dialog_preview_state" else "coord_dialog_entry_state"
                    ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isPreviewStage) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x880B1622))
                            .border(1.dp, CadCyanPrimary, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("coord_preview_translucent_surface")
                    ) {
                        Text(
                            text = "LIVE CURSOR PREVIEW ACTIVE — Inspect target on canvas, then tap Pick to commit or Cancel to restore.",
                            style = MaterialTheme.typography.labelSmall,
                            color = CadCyanPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Column {
                    Text(
                        text = "Direct Coordinate & Polar Bearing Input (${displayUnit.symbol})",
                        style = MaterialTheme.typography.titleLarge,
                        color = CadTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (mode == CoordinateInputMode.CARTESIAN_XY) {
                            "Active Mode: Cartesian X / Y + Bearing θ (${if (isRelativeCartesian) "Relative @ΔX,ΔY" else "Absolute X,Y"} • Source: ${editSource.name})"
                        } else {
                            "Active Mode: Polar / Expression (e.g., 50<45, @30<90, @25,-10, 40)"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = CadCyanPrimary
                    )
                }

                // Mode Selector: Cartesian (X/Y/θ) vs Polar / Expression
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isCartesian = mode == CoordinateInputMode.CARTESIAN_XY
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isCartesian) CadCyanPrimary.copy(alpha = 0.22f) else CadPanelElevated)
                            .border(
                                width = if (isCartesian) 1.5.dp else 1.dp,
                                color = if (isCartesian) CadCyanPrimary else CadBorderHairline,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                mode = CoordinateInputMode.CARTESIAN_XY
                                editSource = CoordinateEditSource.CARTESIAN_XY
                                validationError = null
                                dialogStage = CoordinateDialogStage.ENTRY
                            }
                            .padding(vertical = 8.dp, horizontal = 10.dp)
                            .testTag("coord_mode_cartesian"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Cartesian (X / Y / θ)",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isCartesian) CadCyanPrimary else CadTextSecondary,
                            fontWeight = if (isCartesian) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1
                        )
                    }

                    val isPolar = mode == CoordinateInputMode.POLAR_OR_EXPRESSION
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isPolar) CadAmberSnap.copy(alpha = 0.22f) else CadPanelElevated)
                            .border(
                                width = if (isPolar) 1.5.dp else 1.dp,
                                color = if (isPolar) CadAmberSnap else CadBorderHairline,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                mode = CoordinateInputMode.POLAR_OR_EXPRESSION
                                editSource = CoordinateEditSource.POLAR_EXPRESSION
                                validationError = null
                                dialogStage = CoordinateDialogStage.ENTRY
                            }
                            .padding(vertical = 8.dp, horizontal = 10.dp)
                            .testTag("coord_mode_polar"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Polar / Expression",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isPolar) CadAmberSnap else CadTextSecondary,
                            fontWeight = if (isPolar) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1
                        )
                    }
                }

                if (referencePoint != null) {
                    Text(
                        text = "Reference Origin: (${String.format(Locale.ROOT, "%.2f", referencePoint.x)}, ${String.format(Locale.ROOT, "%.2f", referencePoint.y)}) ${displayUnit.symbol} • R=${String.format(Locale.ROOT, "%.2f", radialDistanceR)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = CadAmberSnap
                    )
                }

                // V19 §12.1: Mutually Exclusive Mode Composition via AnimatedContent(targetState = mode)
                AnimatedContent(
                    targetState = mode,
                    transitionSpec = {
                        fadeIn(tween(160)) togetherWith fadeOut(tween(120))
                    },
                    label = "CoordinateInputModeSubtree"
                ) { activeMode ->
                    if (activeMode == CoordinateInputMode.CARTESIAN_XY) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // V19 §12.2: Two-Card Vertical-Swipe Reference Deck (ABS vs REL (@)) with Fixed Left Badge + Continuous Marquee
                            val absRelDeckTimer = rememberCadDeckIdleTimerState(
                                deckId = "coord_cartesian_reference_deck",
                                isVisibleAndInteractive = true,
                                peekDirection = CardPeekDirection.ABOVE
                            )
                            val isAbsRelRearRevealed = absRelDeckTimer.isRearCardRevealed

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp)
                                    .pointerInput(isRelativeCartesian) {
                                        var dragAccumY = 0f
                                        detectVerticalDragGestures(
                                            onDragStart = { dragAccumY = 0f },
                                            onVerticalDrag = { change, dragAmount ->
                                                dragAccumY += dragAmount
                                                if (abs(dragAccumY) > 16f) {
                                                    change.consume()
                                                    dragAccumY = 0f
                                                    absRelDeckTimer.onCardExchange(isCommandPromptOrDeck = false)
                                                    isRelativeCartesian = !isRelativeCartesian
                                                    validationError = null
                                                    dialogStage = CoordinateDialogStage.ENTRY
                                                }
                                            }
                                        )
                                    }
                                    .clickable {
                                        absRelDeckTimer.onCardExchange(isCommandPromptOrDeck = false)
                                        isRelativeCartesian = !isRelativeCartesian
                                        validationError = null
                                        dialogStage = CoordinateDialogStage.ENTRY
                                    }
                                    .testTag("coord_cartesian_abs_rel_toggle"),
                                contentAlignment = Alignment.Center
                            ) {
                                val rearOffsetY by animateDpAsState(
                                    targetValue = if (isAbsRelRearRevealed) (-4).dp else 0.dp,
                                    animationSpec = tween(180),
                                    label = "CoordAbsRelRearOffsetY"
                                )
                                val rearAlpha by animateFloatAsState(
                                    targetValue = if (isAbsRelRearRevealed) 0.72f else 0f,
                                    animationSpec = tween(180),
                                    label = "CoordAbsRelRearAlpha"
                                )

                                // Rear peek card
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(36.dp)
                                        .zIndex(0f)
                                        .offset { IntOffset(0, rearOffsetY.roundToPx()) }
                                        .graphicsLayer {
                                            scaleX = 0.96f
                                            scaleY = 0.96f
                                            alpha = rearAlpha
                                        }
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(CadPanelSurface)
                                        .border(
                                            1.dp,
                                            if (!isRelativeCartesian) CadEmeraldCrossing else CadCyanPrimary,
                                            RoundedCornerShape(8.dp)
                                        )
                                )

                                // Foreground active card: Left fixed badge + Right clipped continuous marquee
                                val activeAccent = if (isRelativeCartesian) CadEmeraldCrossing else CadCyanPrimary
                                val badgeCode = if (isRelativeCartesian) "REL (@)" else "ABS"
                                val fullReferenceLabel = if (isRelativeCartesian) {
                                    "Cartesian Reference: Relative (@ΔX, ΔY from last point)"
                                } else {
                                    "Cartesian Reference: Absolute (X, Y in model space)"
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(36.dp)
                                        .zIndex(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(CadPanelElevated)
                                        .border(1.5.dp, activeAccent, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(activeAccent.copy(alpha = 0.20f))
                                            .border(1.dp, activeAccent, RoundedCornerShape(5.dp))
                                            .padding(horizontal = 7.dp, vertical = 2.dp)
                                            .testTag("coord_cartesian_abs_rel_badge"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = badgeCode,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = activeAccent,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .widthIn(min = 0.dp)
                                            .clipToBounds()
                                    ) {
                                        key(isRelativeCartesian) {
                                            Text(
                                                text = fullReferenceLabel,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isRelativeCartesian) CadEmeraldCrossing else CadTextPrimary,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                softWrap = false,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .basicMarquee(
                                                        iterations = Int.MAX_VALUE,
                                                        initialDelayMillis = 0,
                                                        repeatDelayMillis = 0,
                                                        velocity = 25.dp
                                                    )
                                                    .testTag("coord_cartesian_abs_rel_marquee")
                                            )
                                        }
                                    }
                                }
                            }

                            // Directly Editable X, Y, and Bearing θ (°) Fields (ONLY rendered in CARTESIAN_XY mode)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = xText,
                                    onValueChange = { newXStr ->
                                        editSource = CoordinateEditSource.CARTESIAN_XY
                                        xText = newXStr
                                        validationError = null
                                        dialogStage = CoordinateDialogStage.ENTRY
                                        val px = newXStr.trim().toDoubleOrNull()
                                        val py = yText.trim().toDoubleOrNull()
                                        if (px != null && py != null && px.isFinite() && py.isFinite()) {
                                            val dx = if (isRelativeCartesian) px else px - originRef.x
                                            val dy = if (isRelativeCartesian) py else py - originRef.y
                                            val r = hypot(dx, dy)
                                            radialDistanceR = if (r > 1e-6) r else radialDistanceR
                                            val ang = if (r > 1e-6) {
                                                ((atan2(dy, dx) * 180.0 / PI) % 360.0 + 360.0) % 360.0
                                            } else 0.0
                                            thetaText = String.format(Locale.ROOT, "%.1f", ang)
                                        }
                                    },
                                    label = { Text("X (${displayUnit.symbol})") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("coord_input_x_field")
                                )

                                OutlinedTextField(
                                    value = yText,
                                    onValueChange = { newYStr ->
                                        editSource = CoordinateEditSource.CARTESIAN_XY
                                        yText = newYStr
                                        validationError = null
                                        dialogStage = CoordinateDialogStage.ENTRY
                                        val px = xText.trim().toDoubleOrNull()
                                        val py = newYStr.trim().toDoubleOrNull()
                                        if (px != null && py != null && px.isFinite() && py.isFinite()) {
                                            val dx = if (isRelativeCartesian) px else px - originRef.x
                                            val dy = if (isRelativeCartesian) py else py - originRef.y
                                            val r = hypot(dx, dy)
                                            radialDistanceR = if (r > 1e-6) r else radialDistanceR
                                            val ang = if (r > 1e-6) {
                                                ((atan2(dy, dx) * 180.0 / PI) % 360.0 + 360.0) % 360.0
                                            } else 0.0
                                            thetaText = String.format(Locale.ROOT, "%.1f", ang)
                                        }
                                    },
                                    label = { Text("Y (${displayUnit.symbol})") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("coord_input_y_field")
                                )

                                OutlinedTextField(
                                    value = thetaText,
                                    onValueChange = { newThetaStr ->
                                        editSource = CoordinateEditSource.BEARING_THETA
                                        thetaText = newThetaStr
                                        validationError = null
                                        dialogStage = CoordinateDialogStage.ENTRY
                                        val parsedDeg = newThetaStr.trim().toDoubleOrNull()
                                        if (parsedDeg != null && parsedDeg.isFinite()) {
                                            val normalizedDeg = ((parsedDeg % 360.0) + 360.0) % 360.0
                                            val rad = normalizedDeg * PI / 180.0
                                            val effectiveR = radialDistanceR.coerceAtLeast(1.0)
                                            val dx = effectiveR * cos(rad)
                                            val dy = effectiveR * sin(rad)
                                            val resolvedX = if (isRelativeCartesian) dx else originRef.x + dx
                                            val resolvedY = if (isRelativeCartesian) dy else originRef.y + dy
                                            xText = String.format(Locale.ROOT, "%.2f", resolvedX)
                                            yText = String.format(Locale.ROOT, "%.2f", resolvedY)
                                        }
                                    },
                                    label = { Text("θ (°)") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(0.9f)
                                        .testTag("coord_input_theta_field")
                                )
                            }
                        }
                    } else {
                        // POLAR_OR_EXPRESSION mode: ONLY renders the Polar / Expression field + read-only resolved X/Y/θ preview
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = polarText,
                                onValueChange = {
                                    editSource = CoordinateEditSource.POLAR_EXPRESSION
                                    polarText = it
                                    validationError = null
                                    dialogStage = CoordinateDialogStage.ENTRY
                                },
                                label = { Text("Polar / Expression") },
                                placeholder = { Text("50<45, @30<90, @25,-10, or 40") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("coord_input_polar_field")
                            )

                            val previewSummary = resolvedPolarPreview?.let { pt ->
                                val ang = if (originRef.distanceTo(pt) > 1e-6) {
                                    ((originRef.angleToDeg(pt) % 360.0) + 360.0) % 360.0
                                } else 0.0
                                "Resolved Target: X=${String.format(Locale.ROOT, "%.2f", pt.x)} ${displayUnit.symbol}, Y=${String.format(Locale.ROOT, "%.2f", pt.y)} ${displayUnit.symbol}, θ=${String.format(Locale.ROOT, "%.1f", ang)}°"
                            } ?: "Resolved Target: Enter a valid polar/coordinate expression (e.g. @30<45 or 50<90)"

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(CadPanelElevated)
                                    .border(1.dp, CadBorderHairline, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .testTag("coord_polar_resolved_preview")
                            ) {
                                Text(
                                    text = previewSummary,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (resolvedPolarPreview != null) CadEmeraldCrossing else CadTextSecondary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                Text(
                    text = "Stage 1: Tap Move Cursor to preview live on canvas • Stage 2: Tap Pick to commit point.",
                    style = MaterialTheme.typography.bodySmall,
                    color = CadTextSecondary
                )

                if (validationError != null) {
                    Text(
                        text = validationError!!,
                        style = MaterialTheme.typography.labelMedium,
                        color = CadCrimsonAlert,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("coord_validation_error_text")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            onCancelPreviewSession()
                            onDismiss()
                        },
                        modifier = Modifier.testTag("coord_cancel_button")
                    ) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))

                    if (!isPreviewStage) {
                        // Stage 1 (ENTRY): Move Cursor button previews live on canvas and transitions to PREVIEW
                        Button(
                            onClick = {
                                val expr = buildValidatedExpression() ?: return@Button
                                val previewOk = onPreviewCoordinate(expr)
                                if (!previewOk) {
                                    validationError = "Unable to preview coordinate expression."
                                    return@Button
                                }
                                dialogStage = CoordinateDialogStage.PREVIEW
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CadCyanPrimary),
                            modifier = Modifier.testTag("coord_move_cursor_button")
                        ) {
                            Text("Move Cursor", color = Color(0xFF031824), fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        // Direct Pick button also available for single-step test/automation convenience
                        OutlinedButton(
                            onClick = {
                                val expr = buildValidatedExpression() ?: return@OutlinedButton
                                onPreviewCoordinate(expr)
                                val committed = onCommitPickCoordinate(expr)
                                if (!committed) {
                                    onSubmitCoordinateExpression(expr)
                                }
                                onDismiss()
                            },
                            modifier = Modifier.testTag("submit_coordinate_button")
                        ) {
                            Text("Pick", color = CadEmeraldCrossing, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        // Stage 2 (PREVIEW): Edit button returns to ENTRY; Pick button commits point and closes
                        OutlinedButton(
                            onClick = {
                                dialogStage = CoordinateDialogStage.ENTRY
                            },
                            modifier = Modifier.testTag("coord_edit_again_button")
                        ) {
                            Text("Edit", color = CadCyanPrimary)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val expr = buildValidatedExpression() ?: return@Button
                                val committed = onCommitPickCoordinate(expr)
                                if (!committed) {
                                    onSubmitCoordinateExpression(expr)
                                }
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CadEmeraldCrossing),
                            modifier = Modifier.testTag("coord_pick_button")
                        ) {
                            Text("Pick", color = Color(0xFF022014), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Section 6 — Cursor Control, Grid & OSNAP Configuration Dialog.
 * - Hoists `scrollState = rememberScrollState()` outside the dialog body slot.
 * - Uses a custom `Dialog` with fixed header, stable scrollable body, and fixed footer.
 * - Uses stable keys (`key(mode.name)`) and fixed height (`54.dp`) with `CadBinaryToggleCard` for OSNAP tiles
 *   so toggling any OSNAP item produces zero scroll jump.
 * - Separates Visual Polar Tracking (`0°–359°` whole-number bearings + custom fractional angles, informational only)
 *   from PolarSnap (multi-select preset angles + custom PolarSnap angles).
 */
@Composable
fun ControllerAndSnapSettingsDialog(
    controllerConfig: CursorControllerConfig,
    gridSnapConfig: GridAndSnapConfig,
    onUpdateController: (CursorControllerConfig) -> Unit,
    onUpdateGridSnap: (GridAndSnapConfig) -> Unit,
    onDismiss: () -> Unit
) {
    // Section 6.2: Hoisted ScrollState survives all OSNAP and Polar toggle recompositions without resetting.
    val scrollState = rememberScrollState()

    var sensitivity by remember { mutableFloatStateOf(controllerConfig.touchpadSensitivity) }
    var maxSpeed by remember { mutableDoubleStateOf(controllerConfig.joystickMaxSpeedUnitsPerSec) }
    var customStepText by remember { mutableStateOf(controllerConfig.stepIncrement.toString()) }
    var gridSnapIntText by remember { mutableStateOf(gridSnapConfig.gridSnapInterval.toString()) }
    var majorGridText by remember { mutableStateOf(gridSnapConfig.majorGridInterval.toString()) }
    var enabledOsnaps by remember { mutableStateOf(gridSnapConfig.enabledOsnapModes) }
    var polarEnabled by remember { mutableStateOf(gridSnapConfig.polarTrackingEnabled) }
    var polarSnapEnabled by remember { mutableStateOf(gridSnapConfig.polarSnapEnabled) }
    var polarIncrementDeg by remember { mutableDoubleStateOf(gridSnapConfig.polarTrackingAngleDeg) }
    var polarSnapPresets by remember { mutableStateOf(gridSnapConfig.polarSnapPresetAnglesDeg) }
    var polarAdditionalAnglesText by remember {
        mutableStateOf(gridSnapConfig.polarAdditionalAnglesDeg.joinToString(", ") {
            if (it % 1.0 == 0.0) it.toInt().toString() else it.toString()
        })
    }
    var polarSnapCustomAnglesText by remember {
        mutableStateOf(gridSnapConfig.polarSnapCustomAnglesDeg.joinToString(", ") {
            if (it % 1.0 == 0.0) it.toInt().toString() else it.toString()
        })
    }
    var polarRelativeToLast by remember { mutableStateOf(gridSnapConfig.polarRelativeToLastSegment) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 650.dp)
                .border(1.dp, CadBorderStrong, RoundedCornerShape(14.dp))
                .testTag("controller_snap_settings_dialog"),
            color = CadPanelSurface,
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("controller_and_snap_settings_dialog")
            ) {
                // Fixed Dialog Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Cursor Controller, Grid & OSNAP Configuration",
                        style = MaterialTheme.typography.titleLarge,
                        color = CadTextPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("controller_settings_close_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close Settings", tint = CadTextSecondary)
                    }
                }
                HorizontalDivider(color = CadBorderHairline)

                // Stable Scrollable Body (Hoisted scrollState — never resets on toggle)
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .testTag("controller_snap_settings_scroll_body"),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Touchpad Sensitivity: ${String.format(Locale.ROOT, "%.1fx", sensitivity)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = CadCyanPrimary
                    )
                    Slider(
                        value = sensitivity,
                        onValueChange = { sensitivity = it },
                        valueRange = 0.2f..3.0f
                    )

                    Text(
                        text = "Joystick Max Speed: ${maxSpeed.toInt()} units/sec",
                        style = MaterialTheme.typography.labelMedium,
                        color = CadAmberSnap
                    )
                    Slider(
                        value = maxSpeed.toFloat(),
                        onValueChange = { maxSpeed = it.toDouble() },
                        valueRange = 30f..400f
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = customStepText,
                            onValueChange = { customStepText = it },
                            label = { Text("Custom Step Increment") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = gridSnapIntText,
                            onValueChange = { gridSnapIntText = it },
                            label = { Text("Grid Snap Interval") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = majorGridText,
                        onValueChange = { majorGridText = it },
                        label = { Text("Visible Major Grid Spacing (Independent of Snap)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    HorizontalDivider(color = CadBorderHairline)

                    // Section 6.4 — Visual Polar Tracking (0°–359° Whole-Number Bearings + Custom Fractional Angles)
                    Text(
                        text = "Visual Polar Tracking (0°–359° Informational Guide + R, θ, θp)",
                        style = MaterialTheme.typography.titleSmall,
                        color = CadEmeraldCrossing,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Covers all whole-number bearings 0°–359° (1° resolution) plus optional custom fractional angles. Informational only — never constrains cursor movement.",
                        style = MaterialTheme.typography.bodySmall,
                        color = CadTextSecondary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Enable Visual Polar Tracking (Disables ORTHO)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = CadTextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        CadBinaryToggleCard(
                            enabled = polarEnabled,
                            onToggle = { polarEnabled = it },
                            label = "ENABLE",
                            enabledStatusText = "ENABLE",
                            disabledStatusText = "DISABLE",
                            testTag = "settings_polar_tracking_switch",
                            deckId = "settings_polar_tracking_switch",
                            compact = true
                        )
                    }

                    OutlinedTextField(
                        value = polarAdditionalAnglesText,
                        onValueChange = { polarAdditionalAnglesText = it },
                        label = { Text("Custom Fractional Visual Angles (e.g., 22.5, 67.5, 126)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("polar_additional_angles_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Measure Polar Angle Relative to Last Segment",
                            style = MaterialTheme.typography.bodySmall,
                            color = CadTextSecondary,
                            modifier = Modifier.weight(1f)
                        )
                        CadBinaryToggleCard(
                            enabled = polarRelativeToLast,
                            onToggle = { polarRelativeToLast = it },
                            label = "ENABLE",
                            enabledStatusText = "ENABLE",
                            disabledStatusText = "DISABLE",
                            testTag = "polar_relative_reference_switch",
                            deckId = "polar_relative_reference_switch",
                            compact = true
                        )
                    }

                    HorizontalDivider(color = CadBorderHairline)

                    // Section 6.3 & 6.4 — PolarSnap (Independent Multi-Select Presets + Custom Snap Angles)
                    Text(
                        text = "PolarSnap (Angular Cursor Constraint — Independent Multi-Select Presets)",
                        style = MaterialTheme.typography.titleSmall,
                        color = CadAmberSnap,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "PolarSnap: Project Candidate Point Onto Acquired Ray",
                            style = MaterialTheme.typography.bodySmall,
                            color = CadTextSecondary,
                            modifier = Modifier.weight(1f)
                        )
                        CadBinaryToggleCard(
                            enabled = polarSnapEnabled,
                            onToggle = { polarSnapEnabled = it },
                            label = "ENABLE",
                            enabledStatusText = "ENABLE",
                            disabledStatusText = "DISABLE",
                            testTag = "polar_snap_projection_switch",
                            deckId = "polar_snap_projection_switch",
                            compact = true
                        )
                    }

                    Text(
                        text = "PolarSnap Angle Increment Presets (Multi-Select Allowed):",
                        style = MaterialTheme.typography.labelSmall,
                        color = CadTextSecondary
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        GridAndSnapConfig.standardPolarIncrementPresets.forEach { preset ->
                            val isSelected = polarSnapPresets.any { abs(it - preset) < 1e-3 } ||
                                abs(polarIncrementDeg - preset) < 1e-3
                            val label = if (preset % 1.0 == 0.0) "${preset.toInt()}°" else "${preset}°"
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) CadEmeraldCrossing.copy(alpha = 0.22f) else CadPanelElevated)
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) CadEmeraldCrossing else CadBorderHairline,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .clickable {
                                        polarIncrementDeg = preset
                                        polarSnapPresets = if (polarSnapPresets.any { abs(it - preset) < 1e-3 } && polarSnapPresets.size > 1) {
                                            polarSnapPresets.filterNot { abs(it - preset) < 1e-3 }.toSet()
                                        } else {
                                            polarSnapPresets + preset
                                        }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                                    .testTag("polar_preset_${preset}")
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) CadEmeraldCrossing else CadTextSecondary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = polarSnapCustomAnglesText,
                        onValueChange = { polarSnapCustomAnglesText = it },
                        label = { Text("Custom PolarSnap Angles (e.g., 126, 216)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("polarsnap_custom_angles_input")
                    )

                    HorizontalDivider(color = CadBorderHairline)
                    Text(
                        text = "Active Object Snap (OSNAP) Modes",
                        style = MaterialTheme.typography.titleSmall,
                        color = CadTextPrimary,
                        fontWeight = FontWeight.Bold
                    )

                    // Phase 10.2 & 10.3: Fixed-height keyed OSNAP rows using 3-region CadBinaryToggleCard
                    // Left: stationary [END] badge; Middle: marquee full label; Right: stationary ENABLE/DISABLE state pill
                    OsnapMode.entries.chunked(2).forEach { rowModes ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            rowModes.forEach { mode ->
                                key(mode.name) {
                                    val isOn = mode in enabledOsnaps
                                    CadBinaryToggleCard(
                                        enabled = isOn,
                                        onToggle = { nextOn ->
                                            enabledOsnaps = if (nextOn) enabledOsnaps + mode else enabledOsnaps - mode
                                        },
                                        label = mode.label,
                                        badgeText = "[${mode.badgeCode}]",
                                        enabledStatusText = "ENABLE",
                                        disabledStatusText = "DISABLE",
                                        useMarqueeLabel = true,
                                        deckId = "osnap_toggle_${mode.name}",
                                        testTag = "osnap_toggle_${mode.name}",
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            if (rowModes.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                HorizontalDivider(color = CadBorderHairline)
                // Fixed Dialog Footer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val customInc = customStepText.toDoubleOrNull()?.coerceAtLeast(0.001) ?: controllerConfig.stepIncrement
                            val updatedList = if (controllerConfig.availableIncrements.none { abs(it - customInc) < 1e-6 }) {
                                (controllerConfig.availableIncrements + customInc).sortedDescending()
                            } else controllerConfig.availableIncrements
                            val parsedAdditionalAngles = polarAdditionalAnglesText
                                .split(",", ";", " ")
                                .mapNotNull { it.trim().toDoubleOrNull() }
                                .filter { it.isFinite() }
                                .map { ((it % 360.0) + 360.0) % 360.0 }
                                .distinct()
                            val parsedSnapCustomAngles = polarSnapCustomAnglesText
                                .split(",", ";", " ")
                                .mapNotNull { it.trim().toDoubleOrNull() }
                                .filter { it.isFinite() }
                                .map { ((it % 360.0) + 360.0) % 360.0 }
                                .distinct()
                            onUpdateController(
                                controllerConfig.copy(
                                    touchpadSensitivity = sensitivity,
                                    joystickMaxSpeedUnitsPerSec = maxSpeed,
                                    stepIncrement = customInc,
                                    availableIncrements = updatedList
                                )
                            )
                            onUpdateGridSnap(
                                gridSnapConfig.copy(
                                    gridSnapInterval = gridSnapIntText.toDoubleOrNull()?.coerceAtLeast(0.001) ?: gridSnapConfig.gridSnapInterval,
                                    majorGridInterval = majorGridText.toDoubleOrNull()?.coerceAtLeast(1.0) ?: gridSnapConfig.majorGridInterval,
                                    enabledOsnapModes = enabledOsnaps,
                                    polarTrackingEnabled = polarEnabled,
                                    orthoEnabled = if (polarEnabled) false else gridSnapConfig.orthoEnabled,
                                    polarSnapEnabled = polarSnapEnabled,
                                    polarTrackingAngleDeg = polarIncrementDeg,
                                    polarAdditionalAnglesDeg = parsedAdditionalAngles,
                                    polarSnapPresetAnglesDeg = polarSnapPresets.ifEmpty { setOf(polarIncrementDeg) },
                                    polarSnapCustomAnglesDeg = parsedSnapCustomAngles,
                                    polarRelativeToLastSegment = polarRelativeToLast
                                )
                            )
                            onDismiss()
                        },
                        modifier = Modifier.testTag("save_controller_snap_config_button")
                    ) {
                        Text("Save Configuration")
                    }
                }
            }
        }
    }
}


/**
 * 7. Properties, Geometric Constraints & Export / Project Hub Dialogs.
 */
@Composable
fun PropertiesAndConstraintsDialog(
    selectedEntities: List<CadEntity>,
    layers: List<CadLayer>,
    onChangeSelectedLayer: (String) -> Unit,
    onChangeSelectedLineType: (CadLineType) -> Unit,
    onUpdateCircleRadius: (String, Double) -> Unit,
    onApplyConstraint: (ConstraintType) -> Unit,
    onDeleteSelected: () -> Unit,
    onDismiss: () -> Unit
) {
    val first = selectedEntities.firstOrNull()
    var radiusText by remember(first) {
        mutableStateOf(
            when (first) {
                is CadEntity.CircleEntity -> first.radius.toString()
                is CadEntity.ArcEntity -> first.radius.toString()
                else -> "10.0"
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CadPanelSurface,
        title = {
            Text("Object Properties & Parametric Constraints (${selectedEntities.size} selected)", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (selectedEntities.isEmpty()) {
                    Text("No objects currently selected. Pick objects or drag a selection window first.", color = CadTextSecondary)
                } else {
                    Text(
                        text = "Selected Types: ${selectedEntities.groupBy { it.typeName }.entries.joinToString { "${it.value.size}× ${it.key}" }}",
                        style = MaterialTheme.typography.labelLarge,
                        color = CadCyanPrimary
                    )

                    if (first is CadEntity.LineEntity) {
                        Text(
                            text = CadNumberFormatter.formatLineDiagnostic(
                                start = first.start,
                                end = first.end,
                                length = first.length,
                                angleDeg = first.angleDeg
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = CadTextSecondary
                        )
                    } else if (first is CadEntity.CircleEntity) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = radiusText,
                                onValueChange = { radiusText = it },
                                label = { Text("Circle Radius") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    radiusText.toDoubleOrNull()?.let { r ->
                                        onUpdateCircleRadius(first.id, r)
                                    }
                                }
                            ) {
                                Text("Update R")
                            }
                        }
                    }

                    Text("Assign Layer", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        layers.forEach { layer ->
                            OutlinedButton(onClick = { onChangeSelectedLayer(layer.name) }) {
                                Text(layer.name)
                            }
                        }
                    }

                    Text("Assign Line Type", style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CadLineType.entries.forEach { lt ->
                            OutlinedButton(onClick = { onChangeSelectedLineType(lt) }) {
                                Text(lt.label)
                            }
                        }
                    }

                    HorizontalDivider(color = CadBorderHairline)
                    Text("Apply 2D Geometric Constraint", style = MaterialTheme.typography.titleSmall, color = CadAmberSnap)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ConstraintType.entries.forEach { ct ->
                            OutlinedButton(
                                onClick = {
                                    onApplyConstraint(ct)
                                    onDismiss()
                                }
                            ) {
                                Text("${ct.badge} ${ct.label}")
                            }
                        }
                    }

                    Button(
                        onClick = {
                            onDeleteSelected()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CadCrimsonAlert),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Erase Selected Entities")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

private enum class ExportFormatCardSpec(
    val shortCode: String,
    val subtitle: String,
    val tag: String,
    val accentColor: Color,
    val surfaceBg: Color
) {
    DXF(
        shortCode = "DXF",
        subtitle = "AC1015 R2000 CAD Interchange (.dxf)",
        tag = "project_hub_export_card_dxf",
        accentColor = CadCyanPrimary,
        surfaceBg = Color(0xFF101B26)
    ),
    PDF(
        shortCode = "PDF",
        subtitle = "ISO / ASME Technical Sheet Plot (.pdf)",
        tag = "project_hub_export_card_pdf",
        accentColor = CadEmeraldCrossing,
        surfaceBg = Color(0xFF0E2019)
    ),
    SVG(
        shortCode = "SVG",
        subtitle = "Precision Vector Sheet (.svg)",
        tag = "project_hub_export_card_svg",
        accentColor = CadAmberSnap,
        surfaceBg = Color(0xFF211A0E)
    )
}

/**
 * Sections 63, 65, 66 & 67: Project Hub & Drawing Formats Dialog.
 * Replaces the horizontal DXF / PDF / SVG button row with a Three-Format Stacked Export Card Deck
 * supporting tap-to-export on the foreground card and downward-swipe cycling (DXF -> PDF -> SVG -> DXF)
 * with single-line non-wrapping format labels. Excludes DWG/DWF.
 */
@Composable
fun DxfAndProjectHubDialog(
    projects: List<CadProjectMetadataEntity>,
    currentProjectName: String,
    dxfPreviewText: String,
    onLoadProject: (String) -> Unit,
    onCreateBlankProject: (name: String, unit: CadUnit) -> Unit,
    onSaveCurrentProject: (name: String) -> Unit,
    onExportShareDxf: () -> Unit,
    onExportSharePdf: () -> Unit,
    onExportShareSvg: () -> Unit,
    onImportDxfString: (String) -> Unit,
    onDeleteProject: (String) -> Unit = {},
    onDismiss: () -> Unit
) {
    var saveName by remember(currentProjectName) { mutableStateOf(currentProjectName) }
    var dxfImportInput by remember { mutableStateOf("") }
    var showImportBox by remember { mutableStateOf(false) }
    var frontExportIndex by rememberSaveable { mutableIntStateOf(0) }

    val formats = ExportFormatCardSpec.entries

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CadPanelSurface,
        title = {
            Column {
                Text(
                    text = "Project Hub & Drawing Formats",
                    style = MaterialTheme.typography.titleLarge,
                    color = CadTextPrimary,
                    modifier = Modifier.testTag("project_hub_title")
                )
                Text(
                    text = "Supported Interchange: DXF • PDF • SVG",
                    style = MaterialTheme.typography.labelSmall,
                    color = CadCyanPrimary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 340.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Export & Share Current Drawing",
                        style = MaterialTheme.typography.titleSmall,
                        color = CadCyanPrimary,
                        modifier = Modifier.testTag("project_hub_export_subtitle")
                    )
                    Text(
                        text = "${formats[frontExportIndex].shortCode} (${frontExportIndex + 1}/${formats.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = CadTextSecondary,
                        fontWeight = FontWeight.Bold
                    )
                }

                // V19 §5.5 & §8.3: Three-Format (DXF / PDF / SVG) Horizontal-Swipe Export Card Deck
                // - Horizontal swipe (left/right) or vertical swipe cycles formats
                // - Rectangular card -> CardPeekDirection.ABOVE
                // - Only nearest rear card (depth == 1) peeks during 4.0s post-exchange/idle window; depth >= 2 remains occluded (alpha = 0f)
                val exportDeckTimerState = rememberCadDeckIdleTimerState(
                    deckId = "project_hub_export_card_deck",
                    isVisibleAndInteractive = true,
                    peekDirection = CardPeekDirection.ABOVE
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .padding(top = 6.dp, bottom = 4.dp)
                        .pointerInput(frontExportIndex) {
                            val touchSlop = viewConfiguration.touchSlop
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val startPos = down.position
                                var totalDx = 0f
                                var totalDy = 0f
                                var cycled = false
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull()
                                    if (change == null || !change.pressed) break
                                    totalDx = change.position.x - startPos.x
                                    totalDy = change.position.y - startPos.y
                                    val isHorizontalSwipe = abs(totalDx) >= touchSlop && abs(totalDx) > abs(totalDy) * 1.15f
                                    val isVerticalSwipe = abs(totalDy) >= touchSlop && abs(totalDy) > abs(totalDx) * 1.25f
                                    if (!cycled && (isHorizontalSwipe || isVerticalSwipe)) {
                                        cycled = true
                                        change.consume()
                                        exportDeckTimerState.onCardExchange(isCommandPromptOrDeck = false)
                                        frontExportIndex = if (isHorizontalSwipe && totalDx > 0f) {
                                            (frontExportIndex - 1 + formats.size) % formats.size
                                        } else {
                                            (frontExportIndex + 1) % formats.size
                                        }
                                        break
                                    }
                                }
                                if (!cycled && abs(totalDx) < touchSlop && abs(totalDy) < touchSlop) {
                                    when (formats[frontExportIndex]) {
                                        ExportFormatCardSpec.DXF -> onExportShareDxf()
                                        ExportFormatCardSpec.PDF -> onExportSharePdf()
                                        ExportFormatCardSpec.SVG -> onExportShareSvg()
                                    }
                                }
                            }
                        }
                        .testTag("project_hub_export_card_deck"),
                    contentAlignment = Alignment.Center
                ) {
                    formats.forEachIndexed { idx, spec ->
                        val depth = (idx - frontExportIndex + formats.size) % formats.size
                        val isFront = depth == 0
                        // V19 §5.5: Only nearest rear card (depth == 1) is visible during peek; depth >= 2 is always occluded
                        val isNearestRearVisible = exportDeckTimerState.isRearCardVisibleForDepth(depth)
                        val concealDelayMs = exportDeckTimerState.sequentialConcealDelayMsForDepth(depth, formats.size).toInt()

                        val targetOffsetY = when {
                            isFront -> if (isNearestRearVisible) 2.dp else 0.dp
                            isNearestRearVisible -> (-6).dp
                            else -> 0.dp
                        }
                        val targetScale = when {
                            isFront -> 1f
                            isNearestRearVisible -> 0.96f
                            else -> 0.92f
                        }
                        val targetAlpha = when {
                            isFront -> 1f
                            isNearestRearVisible -> 0.80f
                            else -> 0f
                        }
                        val animOffsetY by animateDpAsState(
                            targetValue = targetOffsetY,
                            animationSpec = tween(durationMillis = 180, delayMillis = concealDelayMs),
                            label = "ExportDeckOffsetY_${spec.name}"
                        )
                        val animScale by animateFloatAsState(
                            targetValue = targetScale,
                            animationSpec = tween(durationMillis = 180, delayMillis = concealDelayMs),
                            label = "ExportDeckScale_${spec.name}"
                        )
                        val animAlpha by animateFloatAsState(
                            targetValue = targetAlpha,
                            animationSpec = tween(durationMillis = 180, delayMillis = concealDelayMs),
                            label = "ExportDeckAlpha_${spec.name}"
                        )

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .zIndex((formats.size - depth).toFloat())
                                .offset { IntOffset(0, animOffsetY.roundToPx()) }
                                .graphicsLayer {
                                    scaleX = animScale
                                    scaleY = animScale
                                    alpha = animAlpha
                                }
                                .border(
                                    width = if (isFront) 1.5.dp else 1.dp,
                                    color = if (isFront) spec.accentColor else CadBorderHairline,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .testTag(spec.tag),
                            shape = RoundedCornerShape(10.dp),
                            color = if (isFront) spec.surfaceBg else CadPanelElevated
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(spec.accentColor.copy(alpha = 0.20f))
                                            .border(1.dp, spec.accentColor, RoundedCornerShape(6.dp))
                                            .padding(horizontal = 10.dp, vertical = 5.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = spec.shortCode,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = spec.accentColor,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Export ${spec.shortCode}",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = CadTextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                        Text(
                                            text = spec.subtitle,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = CadTextSecondary,
                                            maxLines = 1
                                        )
                                    }
                                }

                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Export ${spec.shortCode}",
                                    tint = spec.accentColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = { showImportBox = !showImportBox },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("project_hub_dxf_inspector_button")
                ) {
                    Text(if (showImportBox) "Hide DXF Inspector / Importer" else "Inspect / Paste ASCII DXF Content")
                }

                if (showImportBox) {
                    OutlinedTextField(
                        value = dxfImportInput.ifBlank { dxfPreviewText.take(1200) },
                        onValueChange = { dxfImportInput = it },
                        label = { Text("ASCII DXF R2000/R12 Stream") },
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (dxfImportInput.isNotBlank()) {
                        Button(
                            onClick = {
                                onImportDxfString(dxfImportInput)
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Import Pasted DXF into Workspace")
                        }
                    }
                }

                HorizontalDivider(color = CadBorderHairline)
                Text("Save Current Project Snapshot", style = MaterialTheme.typography.titleSmall, color = CadAmberSnap)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = saveName,
                        onValueChange = { saveName = it },
                        label = { Text("Drawing Name") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("project_hub_save_name_field")
                    )
                    Button(
                        onClick = { onSaveCurrentProject(saveName) },
                        modifier = Modifier.testTag("project_hub_save_button")
                    ) {
                        Text("Save")
                    }
                }

                HorizontalDivider(color = CadBorderHairline)
                Text("Saved Drawings (${projects.size})", style = MaterialTheme.typography.titleSmall, color = CadTextPrimary)
                if (projects.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(CadPanelElevated)
                            .border(1.dp, CadBorderHairline, RoundedCornerShape(8.dp))
                            .padding(12.dp)
                            .testTag("project_hub_empty_saved_drawings")
                    ) {
                        Text(
                            text = "No saved drawings yet. Save your current drawing to store it locally.",
                            style = MaterialTheme.typography.bodySmall,
                            color = CadTextSecondary
                        )
                    }
                } else {
                    projects.forEach { proj ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(CadPanelElevated)
                                .border(1.dp, CadBorderHairline, RoundedCornerShape(8.dp))
                                .clickable {
                                    onLoadProject(proj.projectId)
                                    onDismiss()
                                }
                                .padding(8.dp)
                                .testTag("project_hub_saved_item_${proj.projectId}"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(proj.name, style = MaterialTheme.typography.titleSmall, color = CadCyanPrimary)
                                Text(proj.description, style = MaterialTheme.typography.bodyMedium, color = CadTextSecondary, maxLines = 2)
                                Text(
                                    text = "Units: ${proj.workingUnitSymbol} • ${proj.entityCount} entities (${proj.dimensionCount} dims)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CadTextMuted
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("OPEN", style = MaterialTheme.typography.labelMedium, color = CadAmberSnap, fontWeight = FontWeight.Bold)
                                IconButton(
                                    onClick = { onDeleteProject(proj.projectId) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Saved Drawing",
                                        tint = CadCrimsonAlert,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = {
                        onCreateBlankProject("Untitled Drawing", CadUnit.MM)
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("project_hub_new_blank_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New Blank 2D Drawing (mm)")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("project_hub_close_button")
            ) {
                Text("Close")
            }
        }
    )
}

