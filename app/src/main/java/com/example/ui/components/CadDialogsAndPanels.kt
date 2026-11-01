package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.cad.data.CadProjectMetadataEntity
import com.example.cad.dimension.DimensionAlignmentReport
import com.example.cad.dimension.DimensionArrangementPreference
import com.example.cad.dimension.QDimDensityPolicy
import com.example.cad.dimension.QDimOperatingMode
import com.example.cad.model.BlockDefinition
import com.example.cad.model.CADCommandMatrix
import com.example.cad.model.CadCommandCategory
import com.example.cad.model.CadCommandId
import com.example.cad.model.CadEntity
import com.example.cad.model.CadLayer
import com.example.cad.model.CadLineType
import com.example.cad.model.CadUnit
import com.example.cad.model.ConstraintType
import com.example.cad.model.CursorControllerConfig
import com.example.cad.model.DraftingStandard
import com.example.cad.model.GridAndSnapConfig
import com.example.cad.model.OsnapMode
import com.example.cad.model.Point2D
import com.example.cad.model.PrecisionFormatConfig
import com.example.cad.model.SheetOrientation
import com.example.cad.model.SheetPreset
import com.example.cad.model.SheetSetupConfig
import com.example.cad.model.UnitChangeMode
import com.example.ui.theme.CadAmberSnap
import com.example.ui.theme.CadBorderHairline
import com.example.ui.theme.CadCrimsonAlert
import com.example.ui.theme.CadCyanPrimary
import com.example.ui.theme.CadEmeraldCrossing
import com.example.ui.theme.CadPanelElevated
import com.example.ui.theme.CadPanelSurface
import com.example.ui.theme.CadTextMuted
import com.example.ui.theme.CadTextPrimary
import com.example.ui.theme.CadTextSecondary

/**
 * 1. Drawing Units & Precision Dialog (Section 3.1, 3.2, 3.3).
 * Enforces explicit choice between Mode A (Change displayed units without changing physical geometry)
 * and Mode B (Rescale the drawing model-space coordinates) and persists the selected mode across sessions
 * with explicit visual ACTIVE/SELECTED confirmation.
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
                    .verticalScroll(rememberScrollState()),
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
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CadUnit.entries.forEach { unit ->
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
                            if (isSel) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Selected Unit",
                                    tint = CadCyanPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
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

                HorizontalDivider(color = CadBorderHairline)
                Text(
                    text = "Unit-Change Operation Mode (Persistent Selection)",
                    style = MaterialTheme.typography.titleSmall,
                    color = CadAmberSnap
                )

                UnitChangeMode.entries.forEach { mode ->
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
                            Text(
                                text = mode.explanation,
                                style = MaterialTheme.typography.bodyMedium,
                                color = CadTextSecondary
                            )
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
                    Text("Show Unit Suffix (${selectedUnit.symbol})", style = MaterialTheme.typography.bodyMedium, color = CadTextSecondary)
                    Switch(checked = showSuffix, onCheckedChange = { showSuffix = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Suppress Trailing Zeros", style = MaterialTheme.typography.bodyMedium, color = CadTextSecondary)
                    Switch(checked = suppressTrailing, onCheckedChange = { suppressTrailing = it })
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
 * 2. Sheet & Page Setup Dialog (Section 3.4).
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
                    Text("Display ISO/ANSI Title Block in Paper Space", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = showTitleBlock, onCheckedChange = { showTitleBlock = it })
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
 * 3. Quick Dimension (QDIM) Studio Dialog (Section 8).
 * Includes the Quick Alignment & Dimension Enable/Disable Toggle (OFF by default on new sheets,
 * stays active once enabled) and an adjacent Refresh button to re-align and dimension newly added structures.
 */
@Composable
fun QuickDimensionDialog(
    isQuickDimensionEnabled: Boolean,
    selectedCount: Int,
    currentDensity: QDimDensityPolicy,
    currentArrangement: DimensionArrangementPreference = DimensionArrangementPreference.AUTO_CHAIN,
    alignmentReport: DimensionAlignmentReport? = null,
    totalDimensionCount: Int = 0,
    onToggleQuickDimension: (Boolean) -> Unit,
    onRefreshQuickDimension: () -> Unit,
    onClearAllDimensions: () -> Unit = {},
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

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CadPanelSurface,
        title = {
            Text(
                text = "Quick Dimension (QDIM) & Alignment",
                style = MaterialTheme.typography.titleLarge,
                color = CadCyanPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Dedicated Quick Alignment Toggle + Adjacent Refresh Control Bar
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isQuickDimensionEnabled) CadEmeraldCrossing.copy(alpha = 0.14f)
                            else CadPanelElevated
                        )
                        .border(
                            1.5.dp,
                            if (isQuickDimensionEnabled) CadEmeraldCrossing else CadBorderHairline,
                            RoundedCornerShape(10.dp)
                        )
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isQuickDimensionEnabled) "Quick Alignment: ACTIVE (ON)" else "Quick Alignment: DISABLED (OFF)",
                                style = MaterialTheme.typography.titleSmall,
                                color = if (isQuickDimensionEnabled) CadEmeraldCrossing else CadTextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Off by default on new sheets. Stays active once enabled.",
                                style = MaterialTheme.typography.labelSmall,
                                color = CadTextSecondary
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Switch(
                                checked = isQuickDimensionEnabled,
                                onCheckedChange = { enabled ->
                                    onToggleQuickDimension(enabled)
                                },
                                modifier = Modifier.testTag("qdim_enable_toggle")
                            )

                            OutlinedButton(
                                onClick = {
                                    onRefreshQuickDimension()
                                },
                                modifier = Modifier.testTag("qdim_refresh_button"),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = CadCyanPrimary.copy(alpha = 0.16f)
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh Quick Alignment",
                                    tint = CadCyanPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Refresh",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = CadCyanPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Text(
                        text = "Tip: Click 'Refresh' after adding new lines, holes, or fillets to the sheet so newly added structures also receive quick alignment & ISO/ASME annotations.",
                        style = MaterialTheme.typography.labelSmall,
                        color = CadAmberSnap
                    )
                }

                Text(
                    text = "Analyzes sheet geometry, identifies overall bounds, local horizontal/vertical shoulders, hole diameters (⌀), arc/fillet radii (R), pattern counts, and pitches, and places collision-free ISO/ASME dimensions via DimensionAlignmentEngine.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CadTextSecondary
                )

                Text("Operating Mode", style = MaterialTheme.typography.titleSmall, color = CadTextPrimary)
                QDimOperatingMode.entries.forEach { m ->
                    val isSel = mode == m
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSel) CadPanelElevated else Color.Transparent)
                            .border(1.dp, if (isSel) CadCyanPrimary else CadBorderHairline, RoundedCornerShape(8.dp))
                            .clickable { mode = m }
                            .padding(8.dp)
                            .testTag("qdim_mode_${m.name}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = isSel, onClick = { mode = m })
                        Column(modifier = Modifier.padding(start = 6.dp)) {
                            Text(m.commandToken, style = MaterialTheme.typography.labelLarge, color = CadCyanPrimary)
                            Text(m.label, style = MaterialTheme.typography.bodyMedium, color = CadTextSecondary)
                        }
                    }
                }

                Text("Alignment & Arrangement Mode (DimensionAlignmentEngine)", style = MaterialTheme.typography.titleSmall, color = CadTextPrimary)
                DimensionArrangementPreference.entries.forEach { pref ->
                    val isSel = arrangement == pref
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSel) CadPanelElevated else Color.Transparent)
                            .border(1.dp, if (isSel) CadEmeraldCrossing else CadBorderHairline, RoundedCornerShape(8.dp))
                            .clickable { arrangement = pref }
                            .padding(8.dp)
                            .testTag("qdim_arrangement_${pref.name}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = isSel, onClick = { arrangement = pref })
                        Column(modifier = Modifier.padding(start = 6.dp)) {
                            Text(pref.label, style = MaterialTheme.typography.titleSmall, color = CadTextPrimary)
                            Text(pref.description, style = MaterialTheme.typography.bodyMedium, color = CadTextSecondary)
                        }
                    }
                }

                Text("Dimension Density Policy", style = MaterialTheme.typography.titleSmall, color = CadTextPrimary)
                QDimDensityPolicy.entries.forEach { d ->
                    val isSel = density == d
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSel) CadPanelElevated else Color.Transparent)
                            .border(1.dp, if (isSel) CadAmberSnap else CadBorderHairline, RoundedCornerShape(8.dp))
                            .clickable { density = d }
                            .padding(8.dp)
                            .testTag("qdim_density_${d.name}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = isSel, onClick = { density = d })
                        Column(modifier = Modifier.padding(start = 6.dp)) {
                            Text(d.label, style = MaterialTheme.typography.titleSmall, color = CadTextPrimary)
                            Text(d.description, style = MaterialTheme.typography.bodyMedium, color = CadTextSecondary)
                        }
                    }
                }

                if (alignmentReport != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(CadPanelElevated)
                            .border(1.dp, CadBorderHairline, RoundedCornerShape(8.dp))
                            .padding(8.dp)
                            .testTag("qdim_alignment_report_card"),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "DimensionAlignmentEngine Report • Completeness: ${"%.0f".format(alignmentReport.generationCompletenessScore)}% • Layout: ${"%.0f".format(alignmentReport.layoutQualityScore)}/100",
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

                // Section 37: Destructive Clear All Dimensions Master Action
                OutlinedButton(
                    onClick = { showClearAllConfirm = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("clear_all_dimensions_button"),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color(0xFF1A0C0E),
                        contentColor = CadCrimsonAlert
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CadCrimsonAlert)
                ) {
                    Text(
                        text = "CLEAR ALL DIMENSIONS ($totalDimensionCount)",
                        style = MaterialTheme.typography.labelMedium,
                        color = CadCrimsonAlert,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onExecuteQDim(mode, density, true, arrangement)
                    onDismiss()
                },
                modifier = Modifier.testTag("execute_qdim_confirm_button")
            ) {
                Text(if (isQuickDimensionEnabled) "Update & Refresh Dimensions" else "Enable & Generate Dimensions")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
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
@Composable
fun DirectCoordinateInputDialog(
    currentCursor: Point2D,
    referencePoint: Point2D?,
    displayUnit: CadUnit,
    onSubmitCoordinateExpression: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var inputText by remember {
        mutableStateOf("${String.format("%.2f", currentCursor.x)},${String.format("%.2f", currentCursor.y)}")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CadPanelSurface,
        title = {
            Text("Direct Coordinate & Polar Input (${displayUnit.symbol})", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Supported formats:\n• Absolute Cartesian: 125,85\n• Relative Cartesian: @25,-10\n• Absolute Polar: 50<45\n• Relative Polar: @30<90\n• Distance along cursor angle: 40",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CadTextSecondary
                )
                if (referencePoint != null) {
                    Text(
                        text = "Last Reference Point: (${String.format("%.2f", referencePoint.x)}, ${String.format("%.2f", referencePoint.y)})",
                        style = MaterialTheme.typography.labelMedium,
                        color = CadAmberSnap
                    )
                }
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    label = { Text("Coordinate or Polar Expression") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("direct_coordinate_text_field")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSubmitCoordinateExpression(inputText)
                    onDismiss()
                },
                modifier = Modifier.testTag("submit_coordinate_button")
            ) {
                Text("Move Cursor & Pick")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ControllerAndSnapSettingsDialog(
    controllerConfig: CursorControllerConfig,
    gridSnapConfig: GridAndSnapConfig,
    onUpdateController: (CursorControllerConfig) -> Unit,
    onUpdateGridSnap: (GridAndSnapConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var sensitivity by remember { mutableStateOf(controllerConfig.touchpadSensitivity) }
    var maxSpeed by remember { mutableDoubleStateOf(controllerConfig.joystickMaxSpeedUnitsPerSec) }
    var customStepText by remember { mutableStateOf(controllerConfig.stepIncrement.toString()) }
    var gridSnapIntText by remember { mutableStateOf(gridSnapConfig.gridSnapInterval.toString()) }
    var majorGridText by remember { mutableStateOf(gridSnapConfig.majorGridInterval.toString()) }
    var enabledOsnaps by remember { mutableStateOf(gridSnapConfig.enabledOsnapModes) }
    var polarEnabled by remember { mutableStateOf(gridSnapConfig.polarTrackingEnabled) }
    var polarSnapEnabled by remember { mutableStateOf(gridSnapConfig.polarSnapEnabled) }
    var polarIncrementDeg by remember { mutableDoubleStateOf(gridSnapConfig.polarTrackingAngleDeg) }
    var polarAdditionalAnglesText by remember {
        mutableStateOf(gridSnapConfig.polarAdditionalAnglesDeg.joinToString(", ") {
            if (it % 1.0 == 0.0) it.toInt().toString() else it.toString()
        })
    }
    var polarRelativeToLast by remember { mutableStateOf(gridSnapConfig.polarRelativeToLastSegment) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CadPanelSurface,
        title = {
            Text("Cursor Controller, Grid & OSNAP Configuration", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Touchpad Sensitivity: ${String.format("%.1fx", sensitivity)}", style = MaterialTheme.typography.labelMedium, color = CadCyanPrimary)
                Slider(
                    value = sensitivity,
                    onValueChange = { sensitivity = it },
                    valueRange = 0.2f..3.0f
                )

                Text("Joystick Max Speed: ${maxSpeed.toInt()} units/sec", style = MaterialTheme.typography.labelMedium, color = CadAmberSnap)
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
                Text("Visual Polar Tracking (R, θ, θp)", style = MaterialTheme.typography.titleSmall, color = CadEmeraldCrossing)
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
                    Switch(
                        checked = polarEnabled,
                        onCheckedChange = { polarEnabled = it },
                        modifier = Modifier.testTag("settings_polar_tracking_switch")
                    )
                }

                Text("Polar Increment Angle Presets", style = MaterialTheme.typography.labelSmall, color = CadTextSecondary)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    GridAndSnapConfig.standardPolarIncrementPresets.forEach { preset ->
                        val isSelected = kotlin.math.abs(polarIncrementDeg - preset) < 1e-3
                        val label = if (preset % 1.0 == 0.0) "${preset.toInt()}°" else "${preset}°"
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) CadEmeraldCrossing.copy(alpha = 0.22f) else CadPanelElevated)
                                .border(1.dp, if (isSelected) CadEmeraldCrossing else CadBorderHairline, RoundedCornerShape(6.dp))
                                .clickable { polarIncrementDeg = preset }
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
                    value = polarAdditionalAnglesText,
                    onValueChange = { polarAdditionalAnglesText = it },
                    label = { Text("Additional Custom Polar Angles (e.g., 126, 216)") },
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
                    Switch(
                        checked = polarRelativeToLast,
                        onCheckedChange = { polarRelativeToLast = it },
                        modifier = Modifier.testTag("polar_relative_reference_switch")
                    )
                }

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
                    Switch(
                        checked = polarSnapEnabled,
                        onCheckedChange = { polarSnapEnabled = it },
                        modifier = Modifier.testTag("polar_snap_projection_switch")
                    )
                }

                HorizontalDivider(color = CadBorderHairline)
                Text("Active Object Snap (OSNAP) Modes", style = MaterialTheme.typography.titleSmall, color = CadTextPrimary)
                OsnapMode.entries.chunked(2).forEach { rowModes ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        rowModes.forEach { mode ->
                            val isOn = mode in enabledOsnaps
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isOn) CadAmberSnap.copy(alpha = 0.2f) else CadPanelElevated)
                                    .border(1.dp, if (isOn) CadAmberSnap else CadBorderHairline, RoundedCornerShape(6.dp))
                                    .clickable {
                                        enabledOsnaps = if (isOn) enabledOsnaps - mode else enabledOsnaps + mode
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "[${mode.badgeCode}] ${mode.label}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isOn) CadAmberSnap else CadTextSecondary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val customInc = customStepText.toDoubleOrNull()?.coerceAtLeast(0.001) ?: controllerConfig.stepIncrement
                    val updatedList = if (controllerConfig.availableIncrements.none { kotlin.math.abs(it - customInc) < 1e-6 }) {
                        (controllerConfig.availableIncrements + customInc).sortedDescending()
                    } else controllerConfig.availableIncrements
                    val parsedAdditionalAngles = polarAdditionalAnglesText
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
                            polarRelativeToLastSegment = polarRelativeToLast
                        )
                    )
                    onDismiss()
                }
            ) {
                Text("Save Configuration")
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
                            text = "Start: (${String.format("%.3f", first.start.x)}, ${String.format("%.3f", first.start.y)})\nEnd: (${String.format("%.3f", first.end.x)}, ${String.format("%.3f", first.end.y)})\nLength: ${String.format("%.3f", first.length)}   Angle: ${String.format("%.2f°", first.angleDeg)}",
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
    onDismiss: () -> Unit
) {
    var saveName by remember { mutableStateOf(currentProjectName) }
    var dxfImportInput by remember { mutableStateOf("") }
    var showImportBox by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CadPanelSurface,
        title = {
            Text("Project Hub & DXF / PDF / SVG Interoperability", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Export & Share Current Drawing", style = MaterialTheme.typography.titleSmall, color = CadCyanPrimary)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = onExportShareDxf,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("export_dxf_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("DXF")
                    }
                    Button(
                        onClick = onExportSharePdf,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("export_pdf_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("PDF")
                    }
                    Button(
                        onClick = onExportShareSvg,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("export_svg_button")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("SVG")
                    }
                }

                OutlinedButton(
                    onClick = { showImportBox = !showImportBox },
                    modifier = Modifier.fillMaxWidth()
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
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = { onSaveCurrentProject(saveName) }) {
                        Text("Save")
                    }
                }

                HorizontalDivider(color = CadBorderHairline)
                Text("Saved Drawings & Industrial Templates", style = MaterialTheme.typography.titleSmall, color = CadTextPrimary)
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
                            .padding(8.dp),
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
                        Text("OPEN", style = MaterialTheme.typography.labelMedium, color = CadAmberSnap, fontWeight = FontWeight.Bold)
                    }
                }

                OutlinedButton(
                    onClick = {
                        onCreateBlankProject("NEW MECHANICAL DRAWING", CadUnit.MM)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New Blank 2D Drawing (mm)")
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
