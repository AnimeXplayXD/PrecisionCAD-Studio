package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cad.dimension.DimensionArrangementPreference
import com.example.cad.dimension.QDimDensityPolicy
import com.example.cad.io.CadDxfInterchange
import com.example.cad.model.CadCommandCategory
import com.example.cad.model.CadCommandId
import com.example.cad.model.HatchPattern
import com.example.cad.model.UnitChangeMode
import com.example.cad.model.WorkspaceSpace
import com.example.ui.components.CadViewportCanvas
import com.example.ui.components.CommandCompatibilityMatrixDialog
import com.example.ui.components.ControllerAndSnapSettingsDialog
import com.example.ui.components.DirectCoordinateInputDialog
import com.example.ui.components.DxfAndProjectHubDialog
import com.example.ui.components.LayerAndBlockManagerDialog
import com.example.ui.components.PropertiesAndConstraintsDialog
import com.example.ui.components.QuickDimensionDialog
import com.example.ui.components.SheetSetupDialog
import com.example.ui.components.UnitsAndPrecisionDialog
import com.example.ui.components.VirtualCursorControllerDeck
import com.example.ui.theme.CadAmberSnap
import com.example.ui.theme.CadBorderHairline
import com.example.ui.theme.CadCyanPrimary
import com.example.ui.theme.CadEmeraldCrossing
import com.example.ui.theme.CadHudSurface
import com.example.ui.theme.CadModelSpaceBg
import com.example.ui.theme.CadPanelElevated
import com.example.ui.theme.CadPanelSurface
import com.example.ui.theme.CadTextMuted
import com.example.ui.theme.CadTextPrimary
import com.example.ui.theme.CadTextSecondary

@Composable
fun CadWorkspaceScreen(
    viewModel: CadWorkspaceViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val savedProjects by viewModel.savedProjects.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Dialog states preserved across orientation changes
    var showUnitsDialog by rememberSaveable { mutableStateOf(false) }
    var showSheetDialog by rememberSaveable { mutableStateOf(false) }
    var showQDimDialog by rememberSaveable { mutableStateOf(false) }
    var showMatrixDialog by rememberSaveable { mutableStateOf(false) }
    var showLayerBlockDialog by rememberSaveable { mutableStateOf(false) }
    var showCoordinateDialog by rememberSaveable { mutableStateOf(false) }
    var showControllerSettingsDialog by rememberSaveable { mutableStateOf(false) }
    var showPropertiesDialog by rememberSaveable { mutableStateOf(false) }
    var showProjectHubDialog by rememberSaveable { mutableStateOf(false) }
    var commandLineInput by rememberSaveable { mutableStateOf("") }

    val previewEntities = remember(
        uiState.activeCommand,
        uiState.commandStepPoints,
        uiState.snappedCursorModelPos,
        uiState.commandSubOption,
        uiState.commandParameterValue
    ) {
        viewModel.computePreviewEntities()
    }

    val handleCommandSelection: (CadCommandId) -> Unit = { cmd ->
        when (cmd) {
            CadCommandId.UNITS -> showUnitsDialog = true
            CadCommandId.PAGESETUP -> showSheetDialog = true
            CadCommandId.LAYER, CadCommandId.BLOCK -> showLayerBlockDialog = true
            CadCommandId.PROPERTIES, CadCommandId.CONSTRAINT -> showPropertiesDialog = true
            CadCommandId.DXF_IO -> showProjectHubDialog = true
            CadCommandId.QDIM -> {
                // Selecting Quick Dimension from the function bar refreshes/enables QDIM and shows the QDIM function bar controls (Toggle + Refresh)
                viewModel.activateCommand(cmd)
            }
            else -> viewModel.activateCommand(cmd)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(CadModelSpaceBg)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        val isLandscape = maxWidth >= 660.dp

        if (!isLandscape) {
            // --- PORTRAIT ADAPTIVE LAYOUT (Section 6.1) ---
            Column(modifier = Modifier.fillMaxSize()) {
                // 1. Top Compact Engineering Header Bar
                TopEngineeringHeaderBar(
                    projectName = uiState.currentProjectName,
                    displayUnitSymbol = uiState.displayUnit.symbol,
                    unitChangeMode = uiState.unitChangeMode,
                    isQuickDimensionEnabled = uiState.isQuickDimensionEnabled,
                    workspaceSpace = uiState.workspaceSpace,
                    canUndo = uiState.canUndo,
                    canRedo = uiState.canRedo,
                    onOpenProjectHub = { showProjectHubDialog = true },
                    onOpenUnits = { showUnitsDialog = true },
                    onToggleWorkspaceSpace = viewModel::toggleWorkspaceSpace,
                    onOpenSheetSetup = { showSheetDialog = true },
                    onOpenQDim = { showQDimDialog = true },
                    onOpenMatrix = { showMatrixDialog = true },
                    onUndo = viewModel::undo,
                    onRedo = viewModel::redo,
                    onZoomExtents = viewModel::zoomExtents
                )

                // 2. Main 2D Technical CAD Viewport (Upper Majority of Screen)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    CadViewportCanvas(
                        workspaceSpace = uiState.workspaceSpace,
                        sheetSetup = uiState.sheetSetup,
                        gridSnapConfig = uiState.gridSnapConfig,
                        storageUnit = uiState.storageUnit,
                        displayUnit = uiState.displayUnit,
                        precision = uiState.precision,
                        dimensionStyle = uiState.dimensionStyle,
                        layers = uiState.layers,
                        entities = uiState.entities,
                        blocks = uiState.blocks,
                        selectedEntityIds = uiState.selectedEntityIds,
                        isolatedEntityIds = uiState.isolatedEntityIds,
                        previewEntities = previewEntities,
                        cursorModelPos = uiState.snappedCursorModelPos,
                        activeSnapCandidate = uiState.activeSnapCandidate,
                        referencePoint = uiState.commandStepPoints.lastOrNull(),
                        selectionBoxDrag = uiState.selectionBoxDrag,
                        viewport = uiState.viewport,
                        onViewportChanged = viewModel::updateViewport,
                        onViewportSizeChanged = viewModel::updateViewportDimensions,
                        onDirectTapModelPoint = viewModel::handleDirectTapModelPoint,
                        onSelectionDragStart = viewModel::onSelectionDragStart,
                        onSelectionDragUpdate = viewModel::onSelectionDragUpdate,
                        onSelectionDragEnd = viewModel::onSelectionDragEnd
                    )

                    // Floating Top-Left Command Prompt & Snap Status HUD
                    ViewportFloatingStatusHud(
                        uiState = uiState,
                        onToggleGrid = viewModel::toggleGridVisibility,
                        onToggleGridSnap = viewModel::toggleGridSnap,
                        onToggleOsnap = viewModel::toggleOsnap,
                        onToggleOrtho = viewModel::toggleOrtho,
                        onOpenLayers = { showLayerBlockDialog = true },
                        onOpenProperties = { showPropertiesDialog = true },
                        onSelectAll = viewModel::selectAllEntities,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                    )
                }

                // 3. Compact Expandable Command Ribbon & Sub-Options + Command Line Bar
                CommandRibbonAndInputBar(
                    uiState = uiState,
                    commandLineInput = commandLineInput,
                    onCommandLineInputChange = { commandLineInput = it },
                    onSubmitCommandLine = {
                        viewModel.submitCommandOrCoordinateInput(commandLineInput)
                        commandLineInput = ""
                    },
                    onSelectCategory = viewModel::selectCategory,
                    onSelectCommand = handleCommandSelection,
                    onSelectSubOption = viewModel::setCommandSubOption,
                    onParameterChange = viewModel::setCommandParameterValue,
                    onToggleQDim = viewModel::setQuickDimensionEnabled,
                    onRefreshQDim = viewModel::refreshQuickDimensions,
                    onSelectQDimDensity = viewModel::setQDimDensity,
                    onSelectQDimArrangement = viewModel::setQDimArrangementPreference,
                    onOpenQDimDialog = { showQDimDialog = true }
                )

                // 4. Dedicated Velocity-Sensitive Virtual Cursor Controller (Lower Dock)
                VirtualCursorControllerDeck(
                    config = uiState.controllerConfig,
                    cursorModelPos = uiState.snappedCursorModelPos,
                    storageUnit = uiState.storageUnit,
                    displayUnit = uiState.displayUnit,
                    precision = uiState.precision,
                    pixelsPerModelUnit = uiState.viewport.pixelsPerModelUnit,
                    onRelativeTouchpadDelta = viewModel::moveCursorByDelta,
                    onJoystickVelocityStep = viewModel::moveCursorByDelta,
                    onStepNudge = viewModel::stepNudgeCursor,
                    onPickAtCursor = viewModel::pickPointAtCursor,
                    onFinishCommand = { viewModel.finishCurrentCommand() },
                    onCancelCommand = viewModel::cancelCurrentCommand,
                    onModeChange = viewModel::setControllerMode,
                    onIncrementSelect = viewModel::setStepIncrement,
                    onOpenControllerSettings = { showControllerSettingsDialog = true },
                    onToggleCollapse = viewModel::toggleControllerCollapse,
                    onOpenCoordinateDialog = { showCoordinateDialog = true }
                )
            }
        } else {
            // --- LANDSCAPE ADAPTIVE LAYOUT (Section 6.2) ---
            Column(modifier = Modifier.fillMaxSize()) {
                TopEngineeringHeaderBar(
                    projectName = uiState.currentProjectName,
                    displayUnitSymbol = uiState.displayUnit.symbol,
                    unitChangeMode = uiState.unitChangeMode,
                    isQuickDimensionEnabled = uiState.isQuickDimensionEnabled,
                    workspaceSpace = uiState.workspaceSpace,
                    canUndo = uiState.canUndo,
                    canRedo = uiState.canRedo,
                    onOpenProjectHub = { showProjectHubDialog = true },
                    onOpenUnits = { showUnitsDialog = true },
                    onToggleWorkspaceSpace = viewModel::toggleWorkspaceSpace,
                    onOpenSheetSetup = { showSheetDialog = true },
                    onOpenQDim = { showQDimDialog = true },
                    onOpenMatrix = { showMatrixDialog = true },
                    onUndo = viewModel::undo,
                    onRedo = viewModel::redo,
                    onZoomExtents = viewModel::zoomExtents
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    // Left-Side Drawing & Modification Command Dock
                    LandscapeLeftToolDock(
                        uiState = uiState,
                        onSelectCategory = viewModel::selectCategory,
                        onSelectCommand = handleCommandSelection
                    )

                    // Central 2D Technical CAD Viewport + Bottom Command Line
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            CadViewportCanvas(
                                workspaceSpace = uiState.workspaceSpace,
                                sheetSetup = uiState.sheetSetup,
                                gridSnapConfig = uiState.gridSnapConfig,
                                storageUnit = uiState.storageUnit,
                                displayUnit = uiState.displayUnit,
                                precision = uiState.precision,
                                dimensionStyle = uiState.dimensionStyle,
                                layers = uiState.layers,
                                entities = uiState.entities,
                                blocks = uiState.blocks,
                                selectedEntityIds = uiState.selectedEntityIds,
                                isolatedEntityIds = uiState.isolatedEntityIds,
                                previewEntities = previewEntities,
                                cursorModelPos = uiState.snappedCursorModelPos,
                                activeSnapCandidate = uiState.activeSnapCandidate,
                                referencePoint = uiState.commandStepPoints.lastOrNull(),
                                selectionBoxDrag = uiState.selectionBoxDrag,
                                viewport = uiState.viewport,
                                onViewportChanged = viewModel::updateViewport,
                                onViewportSizeChanged = viewModel::updateViewportDimensions,
                                onDirectTapModelPoint = viewModel::handleDirectTapModelPoint,
                                onSelectionDragStart = viewModel::onSelectionDragStart,
                                onSelectionDragUpdate = viewModel::onSelectionDragUpdate,
                                onSelectionDragEnd = viewModel::onSelectionDragEnd
                            )

                            ViewportFloatingStatusHud(
                                uiState = uiState,
                                onToggleGrid = viewModel::toggleGridVisibility,
                                onToggleGridSnap = viewModel::toggleGridSnap,
                                onToggleOsnap = viewModel::toggleOsnap,
                                onToggleOrtho = viewModel::toggleOrtho,
                                onOpenLayers = { showLayerBlockDialog = true },
                                onOpenProperties = { showPropertiesDialog = true },
                                onSelectAll = viewModel::selectAllEntities,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(8.dp)
                            )
                        }

                        // Bottom Command Line & Sub-options Bar
                        CompactCommandLineBar(
                            uiState = uiState,
                            commandLineInput = commandLineInput,
                            onCommandLineInputChange = { commandLineInput = it },
                            onSubmitCommandLine = {
                                viewModel.submitCommandOrCoordinateInput(commandLineInput)
                                commandLineInput = ""
                            },
                            onSelectSubOption = viewModel::setCommandSubOption,
                            onParameterChange = viewModel::setCommandParameterValue,
                            onToggleQDim = viewModel::setQuickDimensionEnabled,
                            onRefreshQDim = viewModel::refreshQuickDimensions,
                            onSelectQDimDensity = viewModel::setQDimDensity,
                            onSelectQDimArrangement = viewModel::setQDimArrangementPreference,
                            onOpenQDimDialog = { showQDimDialog = true }
                        )
                    }

                    // Right-Side Properties Summary + Dedicated Lower Virtual Cursor Controller Panel
                    Column(
                        modifier = Modifier
                            .width(310.dp)
                            .fillMaxHeight()
                            .background(CadPanelSurface)
                            .border(1.dp, CadBorderHairline),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Upper Right Quick Inspector
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(8.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "INSPECTOR & LAYERS",
                                style = MaterialTheme.typography.labelLarge,
                                color = CadCyanPrimary
                            )
                            Text(
                                text = "Active Layer: ${uiState.activeLayerName} • Entities: ${uiState.entities.size} • Selected: ${uiState.selectedEntityIds.size}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = CadTextSecondary
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = { showLayerBlockDialog = true },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp)
                                ) {
                                    Text("Layers/Blocks", style = MaterialTheme.typography.labelSmall)
                                }
                                Button(
                                    onClick = { showPropertiesDialog = true },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp)
                                ) {
                                    Text("Props/Constrain", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            Text(
                                text = "Recent Command Log:",
                                style = MaterialTheme.typography.labelSmall,
                                color = CadTextMuted
                            )
                            uiState.commandHistoryLog.takeLast(3).forEach { logLine ->
                                Text(
                                    text = "• $logLine",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CadTextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Lower Right Dedicated Virtual Cursor Controller
                        VirtualCursorControllerDeck(
                            config = uiState.controllerConfig,
                            cursorModelPos = uiState.snappedCursorModelPos,
                            storageUnit = uiState.storageUnit,
                            displayUnit = uiState.displayUnit,
                            precision = uiState.precision,
                            pixelsPerModelUnit = uiState.viewport.pixelsPerModelUnit,
                            onRelativeTouchpadDelta = viewModel::moveCursorByDelta,
                            onJoystickVelocityStep = viewModel::moveCursorByDelta,
                            onStepNudge = viewModel::stepNudgeCursor,
                            onPickAtCursor = viewModel::pickPointAtCursor,
                            onFinishCommand = { viewModel.finishCurrentCommand() },
                            onCancelCommand = viewModel::cancelCurrentCommand,
                            onModeChange = viewModel::setControllerMode,
                            onIncrementSelect = viewModel::setStepIncrement,
                            onOpenControllerSettings = { showControllerSettingsDialog = true },
                            onToggleCollapse = viewModel::toggleControllerCollapse,
                            onOpenCoordinateDialog = { showCoordinateDialog = true }
                        )
                    }
                }
            }
        }
    }

    // --- Modal Dialogs ---
    if (showUnitsDialog) {
        UnitsAndPrecisionDialog(
            currentStorageUnit = uiState.storageUnit,
            currentDisplayUnit = uiState.displayUnit,
            currentUnitChangeMode = uiState.unitChangeMode,
            currentPrecision = uiState.precision,
            onApplyUnitAndPrecision = viewModel::applyUnitsAndPrecision,
            onDismiss = { showUnitsDialog = false }
        )
    }

    if (showSheetDialog) {
        SheetSetupDialog(
            currentSetup = uiState.sheetSetup,
            onApplySheetSetup = viewModel::applySheetSetup,
            onDismiss = { showSheetDialog = false }
        )
    }

    if (showQDimDialog) {
        QuickDimensionDialog(
            isQuickDimensionEnabled = uiState.isQuickDimensionEnabled,
            selectedCount = uiState.selectedEntityIds.size,
            currentDensity = uiState.qdimDensity,
            currentArrangement = uiState.qdimArrangementPreference,
            alignmentReport = uiState.lastAlignmentReport,
            onToggleQuickDimension = viewModel::setQuickDimensionEnabled,
            onRefreshQuickDimension = viewModel::refreshQuickDimensions,
            onExecuteQDim = { mode, density, replaceAuto, arrangement ->
                viewModel.executeQuickDimension(mode, density, replaceAuto, arrangement)
            },
            onDismiss = { showQDimDialog = false }
        )
    }

    if (showMatrixDialog) {
        CommandCompatibilityMatrixDialog(
            onRunCommand = handleCommandSelection,
            onDismiss = { showMatrixDialog = false }
        )
    }

    if (showLayerBlockDialog) {
        LayerAndBlockManagerDialog(
            layers = uiState.layers,
            activeLayerName = uiState.activeLayerName,
            blocks = uiState.blocks.values.toList(),
            selectedCount = uiState.selectedEntityIds.size,
            onSetActiveLayer = viewModel::setActiveLayer,
            onAddLayer = viewModel::addLayer,
            onUpdateLayer = viewModel::updateLayer,
            onCreateBlockFromSelection = viewModel::createBlockFromSelection,
            onInsertBlock = viewModel::insertBlockAtCursor,
            onDismiss = { showLayerBlockDialog = false }
        )
    }

    if (showCoordinateDialog) {
        DirectCoordinateInputDialog(
            currentCursor = Point2DDisplayAdapter.toDisplay(
                uiState.snappedCursorModelPos,
                uiState.storageUnit,
                uiState.displayUnit
            ),
            referencePoint = uiState.commandStepPoints.lastOrNull()?.let {
                Point2DDisplayAdapter.toDisplay(it, uiState.storageUnit, uiState.displayUnit)
            },
            displayUnit = uiState.displayUnit,
            onSubmitCoordinateExpression = viewModel::submitCommandOrCoordinateInput,
            onDismiss = { showCoordinateDialog = false }
        )
    }

    if (showControllerSettingsDialog) {
        ControllerAndSnapSettingsDialog(
            controllerConfig = uiState.controllerConfig,
            gridSnapConfig = uiState.gridSnapConfig,
            onUpdateController = viewModel::updateControllerConfig,
            onUpdateGridSnap = viewModel::updateGridSnapConfig,
            onDismiss = { showControllerSettingsDialog = false }
        )
    }

    if (showPropertiesDialog) {
        PropertiesAndConstraintsDialog(
            selectedEntities = uiState.entities.filter { it.id in uiState.selectedEntityIds },
            layers = uiState.layers,
            onChangeSelectedLayer = viewModel::changeSelectedEntitiesLayer,
            onChangeSelectedLineType = viewModel::changeSelectedEntitiesLineType,
            onUpdateCircleRadius = viewModel::updateCircleRadius,
            onApplyConstraint = viewModel::applyGeometricConstraintToSelected,
            onDeleteSelected = viewModel::deleteSelectedEntities,
            onDismiss = { showPropertiesDialog = false }
        )
    }

    if (showProjectHubDialog) {
        val dxfPreview = remember(showProjectHubDialog, uiState.entities.size) {
            CadDxfInterchange.exportToDxf(viewModel.currentDxfPayload())
        }
        DxfAndProjectHubDialog(
            projects = savedProjects,
            currentProjectName = uiState.currentProjectName,
            dxfPreviewText = dxfPreview,
            onLoadProject = viewModel::loadProject,
            onCreateBlankProject = viewModel::createNewBlankProject,
            onSaveCurrentProject = viewModel::saveCurrentProject,
            onExportShareDxf = { viewModel.exportAndShareDxf(context) },
            onExportSharePdf = { viewModel.exportAndSharePdf(context) },
            onExportShareSvg = { viewModel.exportAndShareSvg(context) },
            onImportDxfString = viewModel::importDxfString,
            onDismiss = { showProjectHubDialog = false }
        )
    }
}

private object Point2DDisplayAdapter {
    fun toDisplay(
        pt: com.example.cad.model.Point2D,
        storageUnit: com.example.cad.model.CadUnit,
        displayUnit: com.example.cad.model.CadUnit
    ): com.example.cad.model.Point2D {
        val ratio = storageUnit.toMillimetersFactor / displayUnit.toMillimetersFactor
        return com.example.cad.model.Point2D(pt.x * ratio, pt.y * ratio)
    }
}

@Composable
private fun TopEngineeringHeaderBar(
    projectName: String,
    displayUnitSymbol: String,
    unitChangeMode: UnitChangeMode,
    isQuickDimensionEnabled: Boolean,
    workspaceSpace: WorkspaceSpace,
    canUndo: Boolean,
    canRedo: Boolean,
    onOpenProjectHub: () -> Unit,
    onOpenUnits: () -> Unit,
    onToggleWorkspaceSpace: () -> Unit,
    onOpenSheetSetup: () -> Unit,
    onOpenQDim: () -> Unit,
    onOpenMatrix: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onZoomExtents: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CadBorderHairline),
        color = CadPanelSurface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Project Hub Button
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(CadPanelElevated)
                    .border(1.dp, CadBorderHairline, RoundedCornerShape(7.dp))
                    .clickable(onClick = onOpenProjectHub)
                    .padding(horizontal = 8.dp, vertical = 5.dp)
                    .testTag("top_project_hub_button"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = "Projects & DXF/PDF", tint = CadCyanPrimary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = projectName,
                    style = MaterialTheme.typography.titleSmall,
                    color = CadTextPrimary,
                    maxLines = 1
                )
            }

            // Active Unit Pill -> Opens Units & Precision Dialog (shows active unit + saved mode)
            val modeBadge = if (unitChangeMode == UnitChangeMode.CONVERT_DISPLAY_UNITS) "Opt A" else "Opt B"
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(CadCyanPrimary.copy(alpha = 0.16f))
                    .border(1.dp, CadCyanPrimary, RoundedCornerShape(6.dp))
                    .clickable(onClick = onOpenUnits)
                    .padding(horizontal = 8.dp, vertical = 5.dp)
                    .testTag("top_units_button")
            ) {
                Text(
                    text = "UNIT: $displayUnitSymbol ($modeBadge)",
                    style = MaterialTheme.typography.labelMedium,
                    color = CadCyanPrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            // Model Space vs Paper Space Toggle
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (workspaceSpace == WorkspaceSpace.PAPER_SPACE) CadAmberSnap.copy(alpha = 0.22f)
                        else CadPanelElevated
                    )
                    .border(
                        1.dp,
                        if (workspaceSpace == WorkspaceSpace.PAPER_SPACE) CadAmberSnap else CadBorderHairline,
                        RoundedCornerShape(6.dp)
                    )
                    .clickable(onClick = onToggleWorkspaceSpace)
                    .padding(horizontal = 8.dp, vertical = 5.dp)
                    .testTag("top_space_toggle_button")
            ) {
                Text(
                    text = if (workspaceSpace == WorkspaceSpace.MODEL_SPACE) "MODEL" else "PAPER",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (workspaceSpace == WorkspaceSpace.PAPER_SPACE) CadAmberSnap else CadTextSecondary,
                    fontWeight = FontWeight.Bold
                )
            }

            // Sheet Setup Button
            IconButton(
                onClick = onOpenSheetSetup,
                modifier = Modifier
                    .size(32.dp)
                    .testTag("top_sheet_setup_button")
            ) {
                Icon(Icons.Default.Description, contentDescription = "Sheet & Page Setup", tint = CadTextSecondary, modifier = Modifier.size(18.dp))
            }

            // Quick Dimension (QDIM) Status + Popup Button (Refresh is exclusively inside the QDIM command & popup)
            Button(
                onClick = onOpenQDim,
                modifier = Modifier
                    .height(30.dp)
                    .testTag("top_qdim_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isQuickDimensionEnabled) CadEmeraldCrossing else CadPanelElevated,
                    contentColor = if (isQuickDimensionEnabled) Color(0xFF002418) else CadCyanPrimary
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                shape = RoundedCornerShape(7.dp)
            ) {
                Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isQuickDimensionEnabled) "QDIM: ON" else "QDIM: OFF",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // CAD Command Compatibility Matrix Button
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(CadPanelElevated)
                    .border(1.dp, CadEmeraldCrossing.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                    .clickable(onClick = onOpenMatrix)
                    .padding(horizontal = 8.dp, vertical = 5.dp)
                    .testTag("top_command_matrix_button"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.ListAlt, contentDescription = "Command Compatibility Matrix", tint = CadEmeraldCrossing, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "MATRIX",
                    style = MaterialTheme.typography.labelSmall,
                    color = CadEmeraldCrossing,
                    fontWeight = FontWeight.Bold
                )
            }

            // Undo / Redo / Zoom Extents
            IconButton(
                onClick = onUndo,
                enabled = canUndo,
                modifier = Modifier
                    .size(32.dp)
                    .testTag("top_undo_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Undo,
                    contentDescription = "Undo",
                    tint = if (canUndo) CadTextPrimary else CadTextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(
                onClick = onRedo,
                enabled = canRedo,
                modifier = Modifier
                    .size(32.dp)
                    .testTag("top_redo_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Redo,
                    contentDescription = "Redo",
                    tint = if (canRedo) CadTextPrimary else CadTextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(
                onClick = onZoomExtents,
                modifier = Modifier
                    .size(32.dp)
                    .testTag("top_zoom_extents_button")
            ) {
                Icon(
                    imageVector = Icons.Default.CenterFocusStrong,
                    contentDescription = "Zoom Extents / Fit Drawing",
                    tint = CadCyanPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun ViewportFloatingStatusHud(
    uiState: CadWorkspaceUiState,
    onToggleGrid: () -> Unit,
    onToggleGridSnap: () -> Unit,
    onToggleOsnap: () -> Unit,
    onToggleOrtho: () -> Unit,
    onOpenLayers: () -> Unit,
    onOpenProperties: () -> Unit,
    onSelectAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        // Active Command Prompt Banner
        Surface(
            color = CadHudSurface,
            shape = RoundedCornerShape(7.dp),
            modifier = Modifier
                .border(1.dp, CadBorderHairline, RoundedCornerShape(7.dp))
                .testTag("active_command_prompt_banner")
        ) {
            Text(
                text = uiState.commandPromptBanner,
                style = MaterialTheme.typography.labelMedium,
                color = CadCyanPrimary,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
            )
        }

        // Precision Snap & Layer Status Pill Bar
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            HudToggleBadge(
                label = "GRID",
                active = uiState.gridSnapConfig.showGrid,
                activeColor = CadCyanPrimary,
                tag = "hud_toggle_grid",
                onClick = onToggleGrid
            )
            HudToggleBadge(
                label = "GSNAP (${uiState.gridSnapConfig.gridSnapInterval})",
                active = uiState.gridSnapConfig.gridSnapEnabled,
                activeColor = CadCyanPrimary,
                tag = "hud_toggle_gsnap",
                onClick = onToggleGridSnap
            )
            HudToggleBadge(
                label = if (uiState.activeSnapCandidate != null)
                    "OSNAP [${uiState.activeSnapCandidate.mode.badgeCode}]"
                else "OSNAP",
                active = uiState.gridSnapConfig.osnapEnabled,
                activeColor = CadAmberSnap,
                tag = "hud_toggle_osnap",
                onClick = onToggleOsnap
            )
            HudToggleBadge(
                label = "ORTHO",
                active = uiState.gridSnapConfig.orthoEnabled,
                activeColor = CadEmeraldCrossing,
                tag = "hud_toggle_ortho",
                onClick = onToggleOrtho
            )
            HudToggleBadge(
                label = "LYR: ${uiState.activeLayerName}",
                active = true,
                activeColor = CadTextPrimary,
                tag = "hud_open_layers",
                onClick = onOpenLayers
            )
            HudToggleBadge(
                label = "SEL ALL",
                active = uiState.selectedEntityIds.isNotEmpty(),
                activeColor = CadCyanPrimary,
                tag = "hud_select_all",
                onClick = onSelectAll
            )
            if (uiState.selectedEntityIds.isNotEmpty()) {
                HudToggleBadge(
                    label = "PROPS (${uiState.selectedEntityIds.size})",
                    active = true,
                    activeColor = CadAmberSnap,
                    tag = "hud_open_properties",
                    onClick = onOpenProperties
                )
            }
        }
    }
}

@Composable
private fun HudToggleBadge(
    label: String,
    active: Boolean,
    activeColor: Color,
    tag: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(if (active) activeColor.copy(alpha = 0.20f) else CadHudSurface)
            .border(1.dp, if (active) activeColor else CadBorderHairline, RoundedCornerShape(5.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 3.dp)
            .testTag(tag)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.5.sp,
            color = if (active) activeColor else CadTextMuted,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun CommandRibbonAndInputBar(
    uiState: CadWorkspaceUiState,
    commandLineInput: String,
    onCommandLineInputChange: (String) -> Unit,
    onSubmitCommandLine: () -> Unit,
    onSelectCategory: (CadCommandCategory) -> Unit,
    onSelectCommand: (CadCommandId) -> Unit,
    onSelectSubOption: (String) -> Unit,
    onParameterChange: (Double) -> Unit,
    onToggleQDim: (Boolean) -> Unit,
    onRefreshQDim: () -> Unit,
    onSelectQDimDensity: (QDimDensityPolicy) -> Unit,
    onSelectQDimArrangement: (DimensionArrangementPreference) -> Unit,
    onOpenQDimDialog: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CadBorderHairline),
        color = CadPanelSurface
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
            // 1. Category Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                CadCommandCategory.entries.forEach { cat ->
                    val isSel = uiState.activeCategory == cat
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSel) CadCyanPrimary.copy(alpha = 0.22f) else CadPanelElevated)
                            .border(1.dp, if (isSel) CadCyanPrimary else CadBorderHairline, RoundedCornerShape(6.dp))
                            .clickable { onSelectCategory(cat) }
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                            .testTag("category_tab_${cat.name}")
                    ) {
                        Text(
                            text = cat.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSel) CadCyanPrimary else CadTextSecondary,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(5.dp))

            // 2. Commands in Active Category
            val commandsInCategory = remember(uiState.activeCategory) {
                CadCommandId.entries.filter { it.category == uiState.activeCategory }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                commandsInCategory.forEach { cmd ->
                    val isSel = uiState.activeCommand == cmd
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSel) CadAmberSnap.copy(alpha = 0.24f) else CadPanelElevated)
                            .border(1.dp, if (isSel) CadAmberSnap else CadBorderHairline, RoundedCornerShape(6.dp))
                            .clickable { onSelectCommand(cmd) }
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                            .testTag("cmd_button_${cmd.commandName}")
                    ) {
                        Text(
                            text = "${cmd.shortLabel} (${cmd.primaryAlias})",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isSel) CadAmberSnap else CadTextPrimary,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            // 3. Context-Sensitive Sub-Options & Command Line Input Bar
            CompactCommandLineBar(
                uiState = uiState,
                commandLineInput = commandLineInput,
                onCommandLineInputChange = onCommandLineInputChange,
                onSubmitCommandLine = onSubmitCommandLine,
                onSelectSubOption = onSelectSubOption,
                onParameterChange = onParameterChange,
                onToggleQDim = onToggleQDim,
                onRefreshQDim = onRefreshQDim,
                onSelectQDimDensity = onSelectQDimDensity,
                onSelectQDimArrangement = onSelectQDimArrangement,
                onOpenQDimDialog = onOpenQDimDialog
            )
        }
    }
}

@Composable
private fun CompactCommandLineBar(
    uiState: CadWorkspaceUiState,
    commandLineInput: String,
    onCommandLineInputChange: (String) -> Unit,
    onSubmitCommandLine: () -> Unit,
    onSelectSubOption: (String) -> Unit,
    onParameterChange: (Double) -> Unit,
    onToggleQDim: (Boolean) -> Unit,
    onRefreshQDim: () -> Unit,
    onSelectQDimDensity: (QDimDensityPolicy) -> Unit,
    onSelectQDimArrangement: (DimensionArrangementPreference) -> Unit,
    onOpenQDimDialog: () -> Unit
) {
    val subOptions = remember(uiState.activeCommand) {
        when (uiState.activeCommand) {
            CadCommandId.CIRCLE -> listOf("Radius", "Diameter", "2P", "3P", "TTR")
            CadCommandId.ARC -> listOf("3P", "SCE")
            CadCommandId.RECTANGLE -> listOf("Sharp", "Fillet", "Chamfer")
            CadCommandId.POLYGON -> listOf("Inscribed", "Circumscribed")
            CadCommandId.POLYLINE -> listOf("Line", "Arc", "Close")
            CadCommandId.XLINE -> listOf("TwoPoint", "Horizontal", "Vertical")
            CadCommandId.HATCH -> HatchPattern.entries.map { it.name }
            CadCommandId.ARRAY -> listOf("Rectangular", "Polar")
            else -> emptyList()
        }
    }

    val isQDimCommand = uiState.activeCommand == CadCommandId.QDIM

    val needsNumericParam = uiState.activeCommand in setOf(
        CadCommandId.FILLET, CadCommandId.CHAMFER, CadCommandId.OFFSET,
        CadCommandId.POLYGON, CadCommandId.DONUT, CadCommandId.LENGTHEN
    ) || (uiState.activeCommand == CadCommandId.RECTANGLE && uiState.commandSubOption != "Sharp") ||
        (uiState.activeCommand == CadCommandId.CIRCLE && uiState.commandSubOption == "TTR")

    val paramLabel = when (uiState.activeCommand) {
        CadCommandId.FILLET -> "MANUAL RADIUS (R):"
        CadCommandId.CHAMFER -> "MANUAL CHAMFER (D):"
        CadCommandId.OFFSET -> "MANUAL OFFSET (d):"
        CadCommandId.POLYGON -> "SIDES:"
        CadCommandId.RECTANGLE -> if (uiState.commandSubOption == "Fillet") "CORNER R:" else "CORNER D:"
        else -> "MANUAL VAL:"
    }

    var manualParamText by rememberSaveable(uiState.activeCommand) {
        mutableStateOf(
            if (uiState.commandParameterValue % 1.0 == 0.0) {
                uiState.commandParameterValue.toInt().toString()
            } else {
                uiState.commandParameterValue.toString()
            }
        )
    }

    LaunchedEffect(uiState.commandParameterValue) {
        val currentParsed = manualParamText.toDoubleOrNull()
        if (currentParsed == null || kotlin.math.abs(currentParsed - uiState.commandParameterValue) > 1e-4) {
            manualParamText = if (uiState.commandParameterValue % 1.0 == 0.0) {
                uiState.commandParameterValue.toInt().toString()
            } else {
                uiState.commandParameterValue.toString()
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (isQDimCommand) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Quick Alignment Toggle (ON / OFF) inside the QDIM command bar
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (uiState.isQuickDimensionEnabled) CadEmeraldCrossing.copy(alpha = 0.24f)
                            else CadPanelElevated
                        )
                        .border(
                            1.5.dp,
                            if (uiState.isQuickDimensionEnabled) CadEmeraldCrossing else CadBorderHairline,
                            RoundedCornerShape(6.dp)
                        )
                        .clickable { onToggleQDim(!uiState.isQuickDimensionEnabled) }
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                        .testTag("qdim_bar_toggle")
                ) {
                    Text(
                        text = if (uiState.isQuickDimensionEnabled) "QUICK ALIGN: ON" else "QUICK ALIGN: OFF",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (uiState.isQuickDimensionEnabled) CadEmeraldCrossing else CadTextSecondary,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Refresh button right beside the toggle inside the Quick Dimension command bar
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(CadCyanPrimary.copy(alpha = 0.20f))
                        .border(1.5.dp, CadCyanPrimary, RoundedCornerShape(6.dp))
                        .clickable(onClick = onRefreshQDim)
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                        .testTag("qdim_bar_refresh_button"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Quick Dimension & Alignment",
                        tint = CadCyanPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Refresh",
                        style = MaterialTheme.typography.labelSmall,
                        color = CadCyanPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Density policy chips
                QDimDensityPolicy.entries.forEach { policy ->
                    val isSel = uiState.qdimDensity == policy
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (isSel) CadAmberSnap.copy(alpha = 0.22f) else CadPanelElevated)
                            .border(1.dp, if (isSel) CadAmberSnap else CadBorderHairline, RoundedCornerShape(5.dp))
                            .clickable { onSelectQDimDensity(policy) }
                            .padding(horizontal = 7.dp, vertical = 4.dp)
                            .testTag("qdim_bar_density_${policy.name}")
                    ) {
                        Text(
                            text = policy.name.lowercase().replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSel) CadAmberSnap else CadTextSecondary
                        )
                    }
                }

                // Arrangement preference chips (Chain / Baseline / Ordinate)
                DimensionArrangementPreference.entries.forEach { pref ->
                    val isSel = uiState.qdimArrangementPreference == pref
                    val shortTag = when (pref) {
                        DimensionArrangementPreference.AUTO_CHAIN -> "Chain"
                        DimensionArrangementPreference.BASELINE -> "Baseline"
                        DimensionArrangementPreference.ORDINATE -> "Ordinate"
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (isSel) CadCyanPrimary.copy(alpha = 0.22f) else CadPanelElevated)
                            .border(1.dp, if (isSel) CadCyanPrimary else CadBorderHairline, RoundedCornerShape(5.dp))
                            .clickable { onSelectQDimArrangement(pref) }
                            .padding(horizontal = 7.dp, vertical = 4.dp)
                            .testTag("qdim_bar_arrangement_${pref.name}")
                    ) {
                        Text(
                            text = shortTag,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSel) CadCyanPrimary else CadTextSecondary
                        )
                    }
                }

                // Button to open full Quick Dimension Dialog
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(CadPanelElevated)
                        .border(1.dp, CadBorderHairline, RoundedCornerShape(5.dp))
                        .clickable(onClick = onOpenQDimDialog)
                        .padding(horizontal = 7.dp, vertical = 4.dp)
                        .testTag("qdim_bar_open_dialog")
                ) {
                    Text(
                        text = "Options…",
                        style = MaterialTheme.typography.labelSmall,
                        color = CadTextPrimary
                    )
                }
            }
        }

        if (subOptions.isNotEmpty() || needsNumericParam) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                subOptions.forEach { opt ->
                    val isSel = uiState.commandSubOption.equals(opt, true)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (isSel) CadCyanPrimary.copy(alpha = 0.22f) else CadPanelElevated)
                            .border(1.dp, if (isSel) CadCyanPrimary else CadBorderHairline, RoundedCornerShape(5.dp))
                            .clickable { onSelectSubOption(opt) }
                            .padding(horizontal = 7.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = opt,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSel) CadCyanPrimary else CadTextSecondary
                        )
                    }
                }

                if (needsNumericParam) {
                    // Primary Manual Radius / Parameter Input Box
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(CadPanelElevated)
                            .border(1.5.dp, CadAmberSnap, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = paramLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = CadAmberSnap,
                            fontWeight = FontWeight.Bold
                        )
                        BasicTextField(
                            value = manualParamText,
                            onValueChange = { newText ->
                                manualParamText = newText
                                val parsed = newText.trim().toDoubleOrNull()
                                if (parsed != null && parsed >= 0.0) {
                                    onParameterChange(parsed)
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            textStyle = MaterialTheme.typography.labelMedium.copy(
                                color = CadTextPrimary,
                                fontWeight = FontWeight.Bold
                            ),
                            cursorBrush = SolidColor(CadCyanPrimary),
                            modifier = Modifier
                                .width(72.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(CadModelSpaceBg)
                                .border(1.dp, CadCyanPrimary.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                                .testTag("manual_parameter_input_field")
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(CadAmberSnap)
                                .clickable {
                                    val parsed = manualParamText.trim().toDoubleOrNull()
                                    if (parsed != null && parsed >= 0.0) {
                                        onParameterChange(parsed)
                                    }
                                }
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                                .testTag("apply_manual_parameter_button")
                        ) {
                            Text(
                                text = "SET",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF1F1600),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Minimal Quick Presets (Secondary to Manual Input)
                    Text(
                        text = "Quick:",
                        style = MaterialTheme.typography.labelSmall,
                        color = CadTextMuted
                    )
                    listOf(2.0, 5.0, 10.0).forEach { presetVal ->
                        val isSel = kotlin.math.abs(uiState.commandParameterValue - presetVal) < 1e-4
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(if (isSel) CadAmberSnap.copy(alpha = 0.22f) else CadPanelElevated)
                                .border(1.dp, if (isSel) CadAmberSnap else CadBorderHairline, RoundedCornerShape(5.dp))
                                .clickable {
                                    manualParamText = presetVal.toInt().toString()
                                    onParameterChange(presetVal)
                                }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                                .testTag("preset_param_${presetVal.toInt()}")
                        ) {
                            Text(
                                text = presetVal.toInt().toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSel) CadAmberSnap else CadTextSecondary
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Command Line & Coordinate Input Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "CMD:",
                style = MaterialTheme.typography.labelMedium,
                color = CadCyanPrimary,
                fontWeight = FontWeight.Bold
            )
            OutlinedTextField(
                value = commandLineInput,
                onValueChange = onCommandLineInputChange,
                placeholder = {
                    Text(
                        text = "Enter command (L, PL, C, QDIM, TR) or coords (125,85 / @25,-10 / 50<45)",
                        style = MaterialTheme.typography.labelSmall,
                        color = CadTextMuted
                    )
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.labelMedium.copy(color = CadTextPrimary),
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .testTag("command_line_input_field")
            )
            IconButton(
                onClick = onSubmitCommandLine,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(CadCyanPrimary.copy(alpha = 0.2f))
                    .testTag("command_line_submit_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Execute Command or Coordinate",
                    tint = CadCyanPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun LandscapeLeftToolDock(
    uiState: CadWorkspaceUiState,
    onSelectCategory: (CadCommandCategory) -> Unit,
    onSelectCommand: (CadCommandId) -> Unit
) {
    Column(
        modifier = Modifier
            .width(164.dp)
            .fillMaxHeight()
            .background(CadPanelSurface)
            .border(1.dp, CadBorderHairline)
            .padding(6.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            text = "CAD TOOLBOX",
            style = MaterialTheme.typography.labelLarge,
            color = CadCyanPrimary
        )
        CadCommandCategory.entries.forEach { cat ->
            val isCat = uiState.activeCategory == cat
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isCat) CadCyanPrimary.copy(alpha = 0.2f) else CadPanelElevated)
                    .clickable { onSelectCategory(cat) }
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                Text(
                    text = cat.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isCat) CadCyanPrimary else CadTextSecondary,
                    fontWeight = FontWeight.Bold
                )
            }
            if (isCat) {
                CadCommandId.entries.filter { it.category == cat }.forEach { cmd ->
                    val isCmd = uiState.activeCommand == cmd
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (isCmd) CadAmberSnap.copy(alpha = 0.22f) else Color.Transparent)
                            .border(
                                1.dp,
                                if (isCmd) CadAmberSnap else CadBorderHairline,
                                RoundedCornerShape(5.dp)
                            )
                            .clickable { onSelectCommand(cmd) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${cmd.shortLabel} (${cmd.primaryAlias})",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isCmd) CadAmberSnap else CadTextPrimary
                        )
                    }
                }
            }
        }
    }
}
