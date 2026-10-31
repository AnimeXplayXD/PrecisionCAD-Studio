package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cad.dimension.DimensionArrangementPreference
import com.example.cad.dimension.QDimDensityPolicy
import com.example.cad.io.CadDxfInterchange
import com.example.cad.kernel.FitDrawingPolicy
import com.example.cad.kernel.UsableViewportRect
import com.example.ui.components.ControllerDockState
import com.example.cad.model.CadCommandCategory
import com.example.cad.model.CadCommandCompatibilityCatalog
import com.example.cad.model.CadCommandId
import com.example.cad.model.HatchPattern
import com.example.cad.model.Point2D
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
import com.example.ui.theme.CadRedAlert
import com.example.ui.theme.CadSurfaceCard
import com.example.ui.theme.CadTextMuted
import com.example.ui.theme.CadTextPrimary
import com.example.ui.theme.CadTextSecondary
import kotlin.math.abs

// Solid 100% opaque active surface tints (Section 3 — No alpha bleed-through)
private val OpaqueCyanBadgeBg = Color(0xFF0E2A38)
private val OpaqueAmberBadgeBg = Color(0xFF2D220E)
private val OpaqueEmeraldBadgeBg = Color(0xFF0C2920)
private val OpaqueRedBadgeBg = Color(0xFF331418)

@Composable
fun CadWorkspaceScreen(
    viewModel: CadWorkspaceViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val savedProjects by viewModel.savedProjects.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val density = LocalDensity.current

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
    var showActionLauncherDialog by rememberSaveable { mutableStateOf(false) }
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
            CadCommandId.QDIM -> viewModel.activateCommand(cmd)
            else -> viewModel.activateCommand(cmd)
        }
    }

    // Section 16: Full-screen immersive container without status/navigation bar padding reservation
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(CadModelSpaceBg)
            .windowInsetsPadding(WindowInsets.displayCutout)
    ) {
        val isLandscape = maxWidth >= 660.dp

        if (!isLandscape) {
            // --- PORTRAIT ADAPTIVE FULL-SCREEN WORKSPACE ---
            Column(modifier = Modifier.fillMaxSize()) {
                // 1. Top Compact Engineering Header Bar
                TopEngineeringHeaderBar(
                    projectName = uiState.currentProjectName,
                    displayUnitSymbol = uiState.displayUnit.symbol,
                    unitChangeMode = uiState.unitChangeMode,
                    isQuickDimensionEnabled = uiState.isQuickDimensionEnabled,
                    workspaceSpace = uiState.workspaceSpace,
                    fitDrawingPolicy = uiState.fitDrawingPolicy,
                    canUndo = uiState.canUndo,
                    canRedo = uiState.canRedo,
                    onOpenProjectHub = { showProjectHubDialog = true },
                    onOpenUnits = { showUnitsDialog = true },
                    onToggleWorkspaceSpace = viewModel::toggleWorkspaceSpace,
                    onOpenSheetSetup = { showSheetDialog = true },
                    onOpenQDim = { showQDimDialog = true },
                    onOpenMatrix = { showMatrixDialog = true },
                    onOpenLayers = { showLayerBlockDialog = true },
                    onOpenProperties = { showPropertiesDialog = true },
                    onOpenActionLauncher = { showActionLauncherDialog = true },
                    onUndo = viewModel::undo,
                    onRedo = viewModel::redo,
                    onFitDrawing = { viewModel.fitDrawing() },
                    onSelectFitPolicy = viewModel::setFitDrawingPolicy
                )

                // 2. Transient Operation Notification Banner (Section 4)
                TransientNotificationBanner(
                    notification = uiState.activeNotification,
                    onDismiss = viewModel::dismissNotification
                )

                // 3. Main 2D Technical CAD Viewport (Navigation-Only Sheet + Retractable Side Tool Drawer)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .onSizeChanged { size ->
                            val topHudPx = with(density) { 42.dp.toPx() }
                            val leftDrawerPx = if (uiState.toolDrawerState == ToolDrawerState.EXPANDED) {
                                with(density) { 188.dp.toPx() }
                            } else {
                                with(density) { 28.dp.toPx() }
                            }
                            viewModel.updateUsableViewportRect(
                                UsableViewportRect(
                                    leftPx = leftDrawerPx.coerceAtMost(size.width * 0.45f),
                                    topPx = topHudPx.coerceAtMost(size.height * 0.25f),
                                    rightPx = size.width.toFloat(),
                                    bottomPx = size.height.toFloat()
                                )
                            )
                        }
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
                        onGestureStateChanged = viewModel::onViewportGestureStateChanged
                    )

                    // Top-Left Opaque Status & Snap Toggle Strip
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
                            .padding(start = 34.dp, top = 6.dp, end = 6.dp)
                    )

                    // Section 14.5: Retractable Left-Edge Command-Category Drawer (Collapsed by default)
                    RetractableSideToolDrawer(
                        uiState = uiState,
                        onToggleDrawer = viewModel::toggleToolDrawer,
                        onSelectCategory = viewModel::selectCategory,
                        onSelectCommand = { cmd ->
                            handleCommandSelection(cmd)
                            viewModel.setToolDrawerState(ToolDrawerState.COLLAPSED)
                        },
                        onSelectAll = viewModel::selectAllEntities,
                        onSelectPrevious = viewModel::selectPreviousEntities,
                        onClearSelection = viewModel::clearSelection,
                        modifier = Modifier.align(Alignment.CenterStart)
                    )
                }

                // 4. Contextual Command Line, Prefix Autocomplete & Active Command Options (Section 14.4)
                ContextualCommandAndOptionsDock(
                    uiState = uiState,
                    commandLineInput = commandLineInput,
                    onCommandLineInputChange = { query ->
                        commandLineInput = query
                        viewModel.updateCommandInputQuery(query)
                    },
                    onOpenCommandEditing = {
                        viewModel.openCommandLineEditing(commandLineInput)
                    },
                    onCloseCommandEditing = {
                        commandLineInput = ""
                        viewModel.closeCommandLineEditing()
                    },
                    onSubmitCommandLine = {
                        viewModel.submitCommandOrCoordinateInput(commandLineInput)
                        commandLineInput = ""
                    },
                    onSelectCommand = { cmd ->
                        commandLineInput = ""
                        handleCommandSelection(cmd)
                    },
                    onSelectSubOption = viewModel::setCommandSubOption,
                    onParameterChange = viewModel::setCommandParameterValue,
                    onToggleQDim = viewModel::setQuickDimensionEnabled,
                    onRefreshQDim = viewModel::refreshQuickDimensions,
                    onSelectQDimDensity = viewModel::setQDimDensity,
                    onSelectQDimArrangement = viewModel::setQDimArrangementPreference,
                    onOpenQDimDialog = { showQDimDialog = true },
                    onOpenActionLauncher = { showActionLauncherDialog = true },
                    onToggleToolDrawer = viewModel::toggleToolDrawer,
                    onOpenProperties = { showPropertiesDialog = true },
                    onDeleteSelected = viewModel::deleteSelectedEntities,
                    onClearSelection = viewModel::clearSelection,
                    onSelectByLayer = viewModel::selectEntitiesByLayer,
                    onSelectByType = viewModel::selectEntitiesByType,
                    onFinishCommand = { viewModel.finishCurrentCommand() },
                    onCancelCommand = viewModel::cancelCurrentCommand
                )

                // 5. Collapsible Virtual Cursor Controller Dock (Section 14.6)
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
                    onSetCollapsed = { collapsed ->
                        viewModel.setControllerDockState(
                            if (collapsed) ControllerDockState.COMPACT_DOCK else ControllerDockState.EXPANDED_CONTROLLER
                        )
                    },
                    onOpenCoordinateDialog = { showCoordinateDialog = true }
                )
            }
        } else {
            // --- LANDSCAPE ADAPTIVE FULL-SCREEN WORKSPACE ---
            Column(modifier = Modifier.fillMaxSize()) {
                TopEngineeringHeaderBar(
                    projectName = uiState.currentProjectName,
                    displayUnitSymbol = uiState.displayUnit.symbol,
                    unitChangeMode = uiState.unitChangeMode,
                    isQuickDimensionEnabled = uiState.isQuickDimensionEnabled,
                    workspaceSpace = uiState.workspaceSpace,
                    fitDrawingPolicy = uiState.fitDrawingPolicy,
                    canUndo = uiState.canUndo,
                    canRedo = uiState.canRedo,
                    onOpenProjectHub = { showProjectHubDialog = true },
                    onOpenUnits = { showUnitsDialog = true },
                    onToggleWorkspaceSpace = viewModel::toggleWorkspaceSpace,
                    onOpenSheetSetup = { showSheetDialog = true },
                    onOpenQDim = { showQDimDialog = true },
                    onOpenMatrix = { showMatrixDialog = true },
                    onOpenLayers = { showLayerBlockDialog = true },
                    onOpenProperties = { showPropertiesDialog = true },
                    onOpenActionLauncher = { showActionLauncherDialog = true },
                    onUndo = viewModel::undo,
                    onRedo = viewModel::redo,
                    onFitDrawing = { viewModel.fitDrawing() },
                    onSelectFitPolicy = viewModel::setFitDrawingPolicy
                )

                TransientNotificationBanner(
                    notification = uiState.activeNotification,
                    onDismiss = viewModel::dismissNotification
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    // Central 2D Technical CAD Viewport + Contextual Bottom Command Dock
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .onSizeChanged { size ->
                                    val topHudPx = with(density) { 42.dp.toPx() }
                                    val leftDrawerPx = if (uiState.toolDrawerState == ToolDrawerState.EXPANDED) {
                                        with(density) { 188.dp.toPx() }
                                    } else {
                                        with(density) { 28.dp.toPx() }
                                    }
                                    viewModel.updateUsableViewportRect(
                                        UsableViewportRect(
                                            leftPx = leftDrawerPx.coerceAtMost(size.width * 0.4f),
                                            topPx = topHudPx.coerceAtMost(size.height * 0.25f),
                                            rightPx = size.width.toFloat(),
                                            bottomPx = size.height.toFloat()
                                        )
                                    )
                                }
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
                                onGestureStateChanged = viewModel::onViewportGestureStateChanged
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
                                    .padding(start = 34.dp, top = 6.dp, end = 6.dp)
                            )

                            RetractableSideToolDrawer(
                                uiState = uiState,
                                onToggleDrawer = viewModel::toggleToolDrawer,
                                onSelectCategory = viewModel::selectCategory,
                                onSelectCommand = { cmd ->
                                    handleCommandSelection(cmd)
                                    viewModel.setToolDrawerState(ToolDrawerState.COLLAPSED)
                                },
                                onSelectAll = viewModel::selectAllEntities,
                                onSelectPrevious = viewModel::selectPreviousEntities,
                                onClearSelection = viewModel::clearSelection,
                                modifier = Modifier.align(Alignment.CenterStart)
                            )
                        }

                        ContextualCommandAndOptionsDock(
                            uiState = uiState,
                            commandLineInput = commandLineInput,
                            onCommandLineInputChange = { query ->
                                commandLineInput = query
                                viewModel.updateCommandInputQuery(query)
                            },
                            onOpenCommandEditing = {
                                viewModel.openCommandLineEditing(commandLineInput)
                            },
                            onCloseCommandEditing = {
                                commandLineInput = ""
                                viewModel.closeCommandLineEditing()
                            },
                            onSubmitCommandLine = {
                                viewModel.submitCommandOrCoordinateInput(commandLineInput)
                                commandLineInput = ""
                            },
                            onSelectCommand = { cmd ->
                                commandLineInput = ""
                                handleCommandSelection(cmd)
                            },
                            onSelectSubOption = viewModel::setCommandSubOption,
                            onParameterChange = viewModel::setCommandParameterValue,
                            onToggleQDim = viewModel::setQuickDimensionEnabled,
                            onRefreshQDim = viewModel::refreshQuickDimensions,
                            onSelectQDimDensity = viewModel::setQDimDensity,
                            onSelectQDimArrangement = viewModel::setQDimArrangementPreference,
                            onOpenQDimDialog = { showQDimDialog = true },
                            onOpenActionLauncher = { showActionLauncherDialog = true },
                            onToggleToolDrawer = viewModel::toggleToolDrawer,
                            onOpenProperties = { showPropertiesDialog = true },
                            onDeleteSelected = viewModel::deleteSelectedEntities,
                            onClearSelection = viewModel::clearSelection,
                            onSelectByLayer = viewModel::selectEntitiesByLayer,
                            onSelectByType = viewModel::selectEntitiesByType,
                            onFinishCommand = { viewModel.finishCurrentCommand() },
                            onCancelCommand = viewModel::cancelCurrentCommand
                        )
                    }

                    // Right-Side Opaque Inspector + Collapsible Virtual Cursor Controller
                    Column(
                        modifier = Modifier
                            .width(308.dp)
                            .fillMaxHeight()
                            .background(CadPanelSurface)
                            .border(1.dp, CadBorderHairline),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(8.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "INSPECTOR & SELECTION",
                                style = MaterialTheme.typography.labelLarge,
                                color = CadCyanPrimary
                            )
                            Text(
                                text = "Layer: ${uiState.activeLayerName} • Entities: ${uiState.entities.size} • Selected: ${uiState.selectedEntityIds.size}",
                                style = MaterialTheme.typography.bodySmall,
                                color = CadTextSecondary
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = { showLayerBlockDialog = true },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(4.dp)
                                ) {
                                    Text("Layers/Blocks", style = MaterialTheme.typography.labelSmall)
                                }
                                Button(
                                    onClick = { showPropertiesDialog = true },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(4.dp)
                                ) {
                                    Text("Props/Constrain", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                OpaqueChipButton(
                                    label = "Select All",
                                    active = false,
                                    activeBg = OpaqueCyanBadgeBg,
                                    activeBorder = CadCyanPrimary,
                                    textColor = CadTextPrimary,
                                    onClick = viewModel::selectAllEntities
                                )
                                OpaqueChipButton(
                                    label = "Prev Sel",
                                    active = false,
                                    activeBg = OpaqueCyanBadgeBg,
                                    activeBorder = CadCyanPrimary,
                                    textColor = CadTextPrimary,
                                    onClick = viewModel::selectPreviousEntities
                                )
                                OpaqueChipButton(
                                    label = "Clear",
                                    active = false,
                                    activeBg = OpaqueCyanBadgeBg,
                                    activeBorder = CadCyanPrimary,
                                    textColor = CadTextSecondary,
                                    onClick = viewModel::clearSelection
                                )
                            }
                        }

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
                            onSetCollapsed = { collapsed ->
                                viewModel.setControllerDockState(
                                    if (collapsed) ControllerDockState.COMPACT_DOCK else ControllerDockState.EXPANDED_CONTROLLER
                                )
                            },
                            onOpenCoordinateDialog = { showCoordinateDialog = true }
                        )
                    }
                }
            }
        }
    }

    // --- Modal Dialogs ---
    if (showActionLauncherDialog) {
        ActionLauncherDialog(
            uiState = uiState,
            onSelectCommand = { cmd ->
                showActionLauncherDialog = false
                handleCommandSelection(cmd)
            },
            onSelectAll = {
                showActionLauncherDialog = false
                viewModel.selectAllEntities()
            },
            onSelectWindowMode = {
                showActionLauncherDialog = false
                viewModel.activateCommand(CadCommandId.SELECT, "Window")
            },
            onSelectCrossingMode = {
                showActionLauncherDialog = false
                viewModel.activateCommand(CadCommandId.SELECT, "Crossing")
            },
            onSelectPrevious = {
                showActionLauncherDialog = false
                viewModel.selectPreviousEntities()
            },
            onClearSelection = {
                showActionLauncherDialog = false
                viewModel.clearSelection()
            },
            onFitDrawing = {
                showActionLauncherDialog = false
                viewModel.fitDrawing()
            },
            onOpenMatrix = {
                showActionLauncherDialog = false
                showMatrixDialog = true
            },
            onDismiss = { showActionLauncherDialog = false }
        )
    }

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
        pt: Point2D,
        storageUnit: com.example.cad.model.CadUnit,
        displayUnit: com.example.cad.model.CadUnit
    ): Point2D {
        val ratio = storageUnit.toMillimetersFactor / displayUnit.toMillimetersFactor
        return Point2D(pt.x * ratio, pt.y * ratio)
    }
}

// --- Section 4: Animated Transient Notification Banner ---

@Composable
private fun TransientNotificationBanner(
    notification: TransientNotification?,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        visible = notification != null,
        enter = slideInVertically(animationSpec = tween(200)) { -it } + fadeIn(animationSpec = tween(200)),
        exit = slideOutVertically(animationSpec = tween(180)) { -it } + fadeOut(animationSpec = tween(180))
    ) {
        val item = notification ?: return@AnimatedVisibility
        val accentColor = when (item.category) {
            NotificationCategory.SUCCESS -> CadEmeraldCrossing
            NotificationCategory.WARNING -> CadAmberSnap
            NotificationCategory.ERROR -> CadRedAlert
            NotificationCategory.INFO -> CadCyanPrimary
        }
        val bgTint = when (item.category) {
            NotificationCategory.SUCCESS -> OpaqueEmeraldBadgeBg
            NotificationCategory.WARNING -> OpaqueAmberBadgeBg
            NotificationCategory.ERROR -> OpaqueRedBadgeBg
            NotificationCategory.INFO -> OpaqueCyanBadgeBg
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, accentColor, RoundedCornerShape(0.dp))
                .testTag("transient_notification_banner"),
            color = bgTint
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = item.message,
                    style = MaterialTheme.typography.labelMedium,
                    color = accentColor,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(CadSurfaceCard)
                        .border(1.dp, CadBorderHairline, RoundedCornerShape(4.dp))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .testTag("notification_dismiss_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss notification",
                        tint = CadTextPrimary,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}

// --- Section 2.1 & Section 6: Top Engineering Header Bar with Fit Drawing & + Launcher ---

@Composable
private fun TopEngineeringHeaderBar(
    projectName: String,
    displayUnitSymbol: String,
    unitChangeMode: UnitChangeMode,
    isQuickDimensionEnabled: Boolean,
    workspaceSpace: WorkspaceSpace,
    fitDrawingPolicy: FitDrawingPolicy,
    canUndo: Boolean,
    canRedo: Boolean,
    onOpenProjectHub: () -> Unit,
    onOpenUnits: () -> Unit,
    onToggleWorkspaceSpace: () -> Unit,
    onOpenSheetSetup: () -> Unit,
    onOpenQDim: () -> Unit,
    onOpenMatrix: () -> Unit,
    onOpenLayers: () -> Unit,
    onOpenProperties: () -> Unit,
    onOpenActionLauncher: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onFitDrawing: () -> Unit,
    onSelectFitPolicy: (FitDrawingPolicy) -> Unit
) {
    var showOverflowMenu by remember { mutableStateOf(false) }

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
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            // Project Hub Button
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(CadPanelElevated)
                    .border(1.dp, CadBorderHairline, RoundedCornerShape(6.dp))
                    .clickable(onClick = onOpenProjectHub)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .testTag("top_project_hub_button"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = "Projects & DXF/PDF", tint = CadCyanPrimary, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = projectName,
                    style = MaterialTheme.typography.labelMedium,
                    color = CadTextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }

            // Active Unit Pill -> Opens Units & Precision Dialog
            val modeBadge = if (unitChangeMode == UnitChangeMode.CONVERT_DISPLAY_UNITS) "Opt A" else "Opt B"
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(OpaqueCyanBadgeBg)
                    .border(1.dp, CadCyanPrimary, RoundedCornerShape(6.dp))
                    .clickable(onClick = onOpenUnits)
                    .padding(horizontal = 7.dp, vertical = 4.dp)
                    .testTag("top_units_button")
            ) {
                Text(
                    text = "UNIT: $displayUnitSymbol ($modeBadge)",
                    style = MaterialTheme.typography.labelSmall,
                    color = CadCyanPrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            // Model Space vs Paper Space Toggle
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (workspaceSpace == WorkspaceSpace.PAPER_SPACE) OpaqueAmberBadgeBg
                        else CadPanelElevated
                    )
                    .border(
                        1.dp,
                        if (workspaceSpace == WorkspaceSpace.PAPER_SPACE) CadAmberSnap else CadBorderHairline,
                        RoundedCornerShape(6.dp)
                    )
                    .clickable(onClick = onToggleWorkspaceSpace)
                    .padding(horizontal = 7.dp, vertical = 4.dp)
                    .testTag("top_space_toggle_button")
            ) {
                Text(
                    text = if (workspaceSpace == WorkspaceSpace.MODEL_SPACE) "MODEL" else "PAPER",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (workspaceSpace == WorkspaceSpace.PAPER_SPACE) CadAmberSnap else CadTextSecondary,
                    fontWeight = FontWeight.Bold
                )
            }

            // Undo & Redo
            IconButton(
                onClick = onUndo,
                enabled = canUndo,
                modifier = Modifier
                    .size(30.dp)
                    .testTag("top_undo_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Undo,
                    contentDescription = "Undo",
                    tint = if (canUndo) CadTextPrimary else CadTextMuted,
                    modifier = Modifier.size(17.dp)
                )
            }
            IconButton(
                onClick = onRedo,
                enabled = canRedo,
                modifier = Modifier
                    .size(30.dp)
                    .testTag("top_redo_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Redo,
                    contentDescription = "Redo",
                    tint = if (canRedo) CadTextPrimary else CadTextMuted,
                    modifier = Modifier.size(17.dp)
                )
            }

            // Primary Fit Drawing Action (Section 6.1)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(OpaqueCyanBadgeBg)
                    .border(1.dp, CadCyanPrimary, RoundedCornerShape(6.dp))
                    .clickable(onClick = onFitDrawing)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .testTag("top_zoom_extents_button"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CenterFocusStrong,
                    contentDescription = "Fit Drawing to Viewport",
                    tint = CadCyanPrimary,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "FIT",
                    style = MaterialTheme.typography.labelSmall,
                    color = CadCyanPrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            // + Action Launcher Button (Section 14.7)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(OpaqueAmberBadgeBg)
                    .border(1.dp, CadAmberSnap, RoundedCornerShape(6.dp))
                    .clickable(onClick = onOpenActionLauncher)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .testTag("top_action_launcher_button"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Open CAD Action Launcher",
                    tint = CadAmberSnap,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "ACTIONS",
                    style = MaterialTheme.typography.labelSmall,
                    color = CadAmberSnap,
                    fontWeight = FontWeight.Bold
                )
            }

            // Quick Dimension (QDIM) Status + Popup Button
            Button(
                onClick = onOpenQDim,
                modifier = Modifier
                    .height(28.dp)
                    .testTag("top_qdim_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isQuickDimensionEnabled) CadEmeraldCrossing else CadPanelElevated,
                    contentColor = if (isQuickDimensionEnabled) Color(0xFF002418) else CadCyanPrimary
                ),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                shape = RoundedCornerShape(6.dp)
            ) {
                Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = if (isQuickDimensionEnabled) "QDIM: ON" else "QDIM: OFF",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            // Sheet Setup Button
            IconButton(
                onClick = onOpenSheetSetup,
                modifier = Modifier
                    .size(30.dp)
                    .testTag("top_sheet_setup_button")
            ) {
                Icon(Icons.Default.Description, contentDescription = "Sheet & Page Setup", tint = CadTextSecondary, modifier = Modifier.size(17.dp))
            }

            // CAD Command Compatibility Matrix Button
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(CadPanelElevated)
                    .border(1.dp, CadEmeraldCrossing, RoundedCornerShape(6.dp))
                    .clickable(onClick = onOpenMatrix)
                    .padding(horizontal = 7.dp, vertical = 4.dp)
                    .testTag("top_command_matrix_button"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.ListAlt, contentDescription = "Command Compatibility Matrix", tint = CadEmeraldCrossing, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "MATRIX",
                    style = MaterialTheme.typography.labelSmall,
                    color = CadEmeraldCrossing,
                    fontWeight = FontWeight.Bold
                )
            }

            // Overflow Menu for Fit Policy & Secondary Tools
            Box {
                IconButton(
                    onClick = { showOverflowMenu = true },
                    modifier = Modifier
                        .size(30.dp)
                        .testTag("top_overflow_menu_button")
                ) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More CAD Tools", tint = CadTextPrimary, modifier = Modifier.size(17.dp))
                }
                DropdownMenu(
                    expanded = showOverflowMenu,
                    onDismissRequest = { showOverflowMenu = false },
                    modifier = Modifier.background(CadSurfaceCard)
                ) {
                    DropdownMenuItem(
                        text = { Text("Fit All Visible (${if (fitDrawingPolicy == FitDrawingPolicy.ALL_VISIBLE) "✓" else ""})", color = CadTextPrimary) },
                        onClick = {
                            showOverflowMenu = false
                            onSelectFitPolicy(FitDrawingPolicy.ALL_VISIBLE)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Fit Selected Only (${if (fitDrawingPolicy == FitDrawingPolicy.SELECTED_ONLY) "✓" else ""})", color = CadTextPrimary) },
                        onClick = {
                            showOverflowMenu = false
                            onSelectFitPolicy(FitDrawingPolicy.SELECTED_ONLY)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Fit Geometry Only (${if (fitDrawingPolicy == FitDrawingPolicy.GEOMETRY_ONLY) "✓" else ""})", color = CadTextPrimary) },
                        onClick = {
                            showOverflowMenu = false
                            onSelectFitPolicy(FitDrawingPolicy.GEOMETRY_ONLY)
                        }
                    )
                    HorizontalDivider(color = CadBorderHairline)
                    DropdownMenuItem(
                        text = { Text("Layers & Blocks Manager", color = CadTextPrimary) },
                        onClick = {
                            showOverflowMenu = false
                            onOpenLayers()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Properties & Constraints", color = CadTextPrimary) },
                        onClick = {
                            showOverflowMenu = false
                            onOpenProperties()
                        }
                    )
                }
            }
        }
    }
}

// --- Section 3 & Section 14: Opaque Floating Snap & Layer Status Strip ---

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
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HudToggleBadge(
            label = "GRID",
            active = uiState.gridSnapConfig.showGrid,
            activeBg = OpaqueCyanBadgeBg,
            activeColor = CadCyanPrimary,
            tag = "hud_toggle_grid",
            onClick = onToggleGrid
        )
        HudToggleBadge(
            label = "GSNAP (${uiState.gridSnapConfig.gridSnapInterval})",
            active = uiState.gridSnapConfig.gridSnapEnabled,
            activeBg = OpaqueCyanBadgeBg,
            activeColor = CadCyanPrimary,
            tag = "hud_toggle_gsnap",
            onClick = onToggleGridSnap
        )
        HudToggleBadge(
            label = if (uiState.activeSnapCandidate != null)
                "OSNAP [${uiState.activeSnapCandidate.mode.badgeCode}]"
            else "OSNAP",
            active = uiState.gridSnapConfig.osnapEnabled,
            activeBg = OpaqueAmberBadgeBg,
            activeColor = CadAmberSnap,
            tag = "hud_toggle_osnap",
            onClick = onToggleOsnap
        )
        HudToggleBadge(
            label = "ORTHO",
            active = uiState.gridSnapConfig.orthoEnabled,
            activeBg = OpaqueEmeraldBadgeBg,
            activeColor = CadEmeraldCrossing,
            tag = "hud_toggle_ortho",
            onClick = onToggleOrtho
        )
        HudToggleBadge(
            label = "LYR: ${uiState.activeLayerName}",
            active = true,
            activeBg = CadHudSurface,
            activeColor = CadTextPrimary,
            tag = "hud_open_layers",
            onClick = onOpenLayers
        )
        HudToggleBadge(
            label = "SEL ALL",
            active = uiState.selectedEntityIds.isNotEmpty(),
            activeBg = OpaqueCyanBadgeBg,
            activeColor = CadCyanPrimary,
            tag = "hud_select_all",
            onClick = onSelectAll
        )
        if (uiState.selectedEntityIds.isNotEmpty()) {
            HudToggleBadge(
                label = "PROPS (${uiState.selectedEntityIds.size})",
                active = true,
                activeBg = OpaqueAmberBadgeBg,
                activeColor = CadAmberSnap,
                tag = "hud_open_properties",
                onClick = onOpenProperties
            )
        }
    }
}

@Composable
private fun HudToggleBadge(
    label: String,
    active: Boolean,
    activeBg: Color,
    activeColor: Color,
    tag: String,
    onClick: () -> Unit
) {
    // 100% opaque surface fill (Section 3)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(if (active) activeBg else CadHudSurface)
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

// --- Section 14.5: Retractable Side Tool-Category Drawer ---

@Composable
private fun RetractableSideToolDrawer(
    uiState: CadWorkspaceUiState,
    onToggleDrawer: () -> Unit,
    onSelectCategory: (CadCommandCategory) -> Unit,
    onSelectCommand: (CadCommandId) -> Unit,
    onSelectAll: () -> Unit,
    onSelectPrevious: () -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isExpanded = uiState.toolDrawerState == ToolDrawerState.EXPANDED

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandHorizontally(animationSpec = tween(200)) + fadeIn(animationSpec = tween(200)),
            exit = shrinkHorizontally(animationSpec = tween(180)) + fadeOut(animationSpec = tween(180))
        ) {
            Surface(
                modifier = Modifier
                    .width(184.dp)
                    .heightIn(max = 380.dp)
                    .border(1.dp, CadCyanPrimary, RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp))
                    .testTag("tool_drawer_panel"),
                shape = RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp),
                color = CadPanelSurface
            ) {
                Column(
                    modifier = Modifier
                        .padding(8.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = "CAD TOOL CATEGORIES",
                        style = MaterialTheme.typography.labelMedium,
                        color = CadCyanPrimary,
                        fontWeight = FontWeight.Bold
                    )

                    CadCommandCategory.entries.forEach { cat ->
                        val isCat = uiState.activeCategory == cat
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isCat) OpaqueCyanBadgeBg else CadPanelElevated)
                                .border(
                                    1.dp,
                                    if (isCat) CadCyanPrimary else CadBorderHairline,
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable { onSelectCategory(cat) }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                                .testTag("category_tab_${cat.name}")
                        ) {
                            Text(
                                text = cat.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isCat) CadCyanPrimary else CadTextSecondary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (isCat) {
                            Column(
                                modifier = Modifier.padding(start = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                CadCommandId.entries.filter { it.category == cat }.forEach { cmd ->
                                    val isCmd = uiState.activeCommand == cmd
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(if (isCmd) OpaqueAmberBadgeBg else CadHudSurface)
                                            .border(
                                                1.dp,
                                                if (isCmd) CadAmberSnap else CadBorderHairline,
                                                RoundedCornerShape(5.dp)
                                            )
                                            .clickable { onSelectCommand(cmd) }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                            .testTag("cmd_button_${cmd.commandName}")
                                    ) {
                                        Text(
                                            text = "${cmd.shortLabel} (${cmd.primaryAlias})",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isCmd) CadAmberSnap else CadTextPrimary,
                                            fontWeight = if (isCmd) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = CadBorderHairline)
                    Text(
                        text = "QUICK SELECTION",
                        style = MaterialTheme.typography.labelSmall,
                        color = CadTextMuted
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        OpaqueChipButton(
                            label = "All",
                            active = false,
                            activeBg = OpaqueCyanBadgeBg,
                            activeBorder = CadCyanPrimary,
                            textColor = CadTextPrimary,
                            onClick = onSelectAll
                        )
                        OpaqueChipButton(
                            label = "Prev",
                            active = false,
                            activeBg = OpaqueCyanBadgeBg,
                            activeBorder = CadCyanPrimary,
                            textColor = CadTextPrimary,
                            onClick = onSelectPrevious
                        )
                        OpaqueChipButton(
                            label = "Clear",
                            active = false,
                            activeBg = OpaqueCyanBadgeBg,
                            activeBorder = CadCyanPrimary,
                            textColor = CadTextSecondary,
                            onClick = onClearSelection
                        )
                    }
                }
            }
        }

        // Retractable Handle Rail (100% Opaque, >= 48dp touch height)
        Surface(
            modifier = Modifier
                .width(26.dp)
                .height(74.dp)
                .border(
                    1.dp,
                    if (isExpanded) CadCyanPrimary else CadBorderHairline,
                    RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)
                )
                .clickable(onClick = onToggleDrawer)
                .testTag("tool_drawer_handle"),
            shape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp),
            color = CadHudSurface
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = if (isExpanded) {
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft
                    } else {
                        Icons.AutoMirrored.Filled.KeyboardArrowRight
                    },
                    contentDescription = if (isExpanded) "Collapse tools" else "Expand tools",
                    tint = CadCyanPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// --- Section 14.4: Contextual Command Line, Ranked Prefix Autocomplete & Active Command Bar ---

@Composable
private fun ContextualCommandAndOptionsDock(
    uiState: CadWorkspaceUiState,
    commandLineInput: String,
    onCommandLineInputChange: (String) -> Unit,
    onOpenCommandEditing: () -> Unit,
    onCloseCommandEditing: () -> Unit,
    onSubmitCommandLine: () -> Unit,
    onSelectCommand: (CadCommandId) -> Unit,
    onSelectSubOption: (String) -> Unit,
    onParameterChange: (Double) -> Unit,
    onToggleQDim: (Boolean) -> Unit,
    onRefreshQDim: () -> Unit,
    onSelectQDimDensity: (QDimDensityPolicy) -> Unit,
    onSelectQDimArrangement: (DimensionArrangementPreference) -> Unit,
    onOpenQDimDialog: () -> Unit,
    onOpenActionLauncher: () -> Unit,
    onToggleToolDrawer: () -> Unit,
    onOpenProperties: () -> Unit,
    onDeleteSelected: () -> Unit,
    onClearSelection: () -> Unit,
    onSelectByLayer: (String) -> Unit,
    onSelectByType: (String) -> Unit,
    onFinishCommand: () -> Unit,
    onCancelCommand: () -> Unit
) {
    val isEditingCommand = uiState.commandPanelState is CommandPanelState.Editing || commandLineInput.isNotBlank()
    val isRunningActiveCommand = uiState.commandPanelState is CommandPanelState.Running ||
        uiState.activeCommand != CadCommandId.SELECT ||
        uiState.commandStepPoints.isNotEmpty() ||
        uiState.selectionBoxDrag != null

    val rankedSuggestions = remember(commandLineInput) {
        CadCommandCompatibilityCatalog.rankSuggestionsForPrefix(commandLineInput).take(6)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CadBorderHairline),
        color = CadPanelSurface
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            // 1. IDLE STATE: Compact low-profile trigger strip only (Section 14.4.1)
            if (!isEditingCommand && !isRunningActiveCommand) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Compact CMD button
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(OpaqueCyanBadgeBg)
                            .border(1.dp, CadCyanPrimary, RoundedCornerShape(6.dp))
                            .clickable(onClick = onOpenCommandEditing)
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                            .testTag("cmd_activate_button"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = "Open Command Line",
                            tint = CadCyanPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "CMD / X,Y",
                            style = MaterialTheme.typography.labelSmall,
                            color = CadCyanPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // + Action Launcher button
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(OpaqueAmberBadgeBg)
                            .border(1.dp, CadAmberSnap, RoundedCornerShape(6.dp))
                            .clickable(onClick = onOpenActionLauncher)
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                            .testTag("action_launcher_fab"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Open Action Launcher",
                            tint = CadAmberSnap,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "+ LAUNCHER",
                            style = MaterialTheme.typography.labelSmall,
                            color = CadAmberSnap,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Tools Drawer Toggle
                    OpaqueChipButton(
                        label = if (uiState.toolDrawerState == ToolDrawerState.EXPANDED) "Hide Tools" else "Tools Drawer",
                        active = uiState.toolDrawerState == ToolDrawerState.EXPANDED,
                        activeBg = OpaqueCyanBadgeBg,
                        activeBorder = CadCyanPrimary,
                        textColor = CadTextPrimary,
                        tag = "bottom_tools_toggle_button",
                        onClick = onToggleToolDrawer
                    )

                    // Selection quick actions when objects are selected
                    if (uiState.selectedEntityIds.isNotEmpty()) {
                        OpaqueChipButton(
                            label = "Props (${uiState.selectedEntityIds.size})",
                            active = true,
                            activeBg = OpaqueAmberBadgeBg,
                            activeBorder = CadAmberSnap,
                            textColor = CadAmberSnap,
                            onClick = onOpenProperties
                        )
                        OpaqueChipButton(
                            label = "Erase",
                            active = true,
                            activeBg = OpaqueRedBadgeBg,
                            activeBorder = CadRedAlert,
                            textColor = CadRedAlert,
                            onClick = onDeleteSelected
                        )
                        OpaqueChipButton(
                            label = "Deselect",
                            active = false,
                            activeBg = CadPanelElevated,
                            activeBorder = CadBorderHairline,
                            textColor = CadTextSecondary,
                            onClick = onClearSelection
                        )
                    } else {
                        Text(
                            text = uiState.commandPromptBanner,
                            style = MaterialTheme.typography.labelSmall,
                            color = CadTextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // 2. ACTIVE-COMMAND STATE: Show only active command prompt & valid sub-options (Section 14.4.3)
            if (isRunningActiveCommand) {
                ActiveCommandContextBar(
                    uiState = uiState,
                    onSelectSubOption = onSelectSubOption,
                    onParameterChange = onParameterChange,
                    onToggleQDim = onToggleQDim,
                    onRefreshQDim = onRefreshQDim,
                    onSelectQDimDensity = onSelectQDimDensity,
                    onSelectQDimArrangement = onSelectQDimArrangement,
                    onOpenQDimDialog = onOpenQDimDialog,
                    onOpenCommandEditing = onOpenCommandEditing,
                    onSelectByLayer = onSelectByLayer,
                    onSelectByType = onSelectByType,
                    onFinishCommand = onFinishCommand,
                    onCancelCommand = onCancelCommand
                )
            }

            // 3. TYPING / AUTOCOMPLETE STATE: Prefix-ranked suggestion strip + input field (Section 14.4.2)
            if (isEditingCommand) {
                if (rankedSuggestions.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(bottom = 4.dp)
                            .testTag("command_suggestion_strip"),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        rankedSuggestions.forEachIndexed { idx, spec ->
                            val isTopMatch = idx == 0
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isTopMatch) OpaqueAmberBadgeBg else CadPanelElevated)
                                    .border(
                                        1.dp,
                                        if (isTopMatch) CadAmberSnap else CadCyanPrimary,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .clickable { onSelectCommand(spec.id) }
                                    .padding(horizontal = 9.dp, vertical = 4.dp)
                                    .testTag("suggestion_chip_${spec.id.commandName}")
                            ) {
                                Text(
                                    text = "${spec.id.commandName} (${spec.id.primaryAlias})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isTopMatch) CadAmberSnap else CadCyanPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

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
                                text = "Type command prefix (L, PL, C, F, QDIM) or coords (125,85 / @25,-10 / 50<45)",
                                style = MaterialTheme.typography.labelSmall,
                                color = CadTextMuted
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { onSubmitCommandLine() }),
                        textStyle = MaterialTheme.typography.labelMedium.copy(color = CadTextPrimary),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .testTag("command_line_input_field")
                    )
                    IconButton(
                        onClick = onSubmitCommandLine,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(OpaqueCyanBadgeBg)
                            .border(1.dp, CadCyanPrimary, RoundedCornerShape(7.dp))
                            .testTag("command_line_submit_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Execute Command or Coordinate",
                            tint = CadCyanPrimary,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    IconButton(
                        onClick = onCloseCommandEditing,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(CadPanelElevated)
                            .border(1.dp, CadBorderHairline, RoundedCornerShape(7.dp))
                            .testTag("cmd_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Command Input",
                            tint = CadTextSecondary,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveCommandContextBar(
    uiState: CadWorkspaceUiState,
    onSelectSubOption: (String) -> Unit,
    onParameterChange: (Double) -> Unit,
    onToggleQDim: (Boolean) -> Unit,
    onRefreshQDim: () -> Unit,
    onSelectQDimDensity: (QDimDensityPolicy) -> Unit,
    onSelectQDimArrangement: (DimensionArrangementPreference) -> Unit,
    onOpenQDimDialog: () -> Unit,
    onOpenCommandEditing: () -> Unit,
    onSelectByLayer: (String) -> Unit,
    onSelectByType: (String) -> Unit,
    onFinishCommand: () -> Unit,
    onCancelCommand: () -> Unit
) {
    val subOptions = remember(uiState.activeCommand) {
        when (uiState.activeCommand) {
            CadCommandId.SELECT -> listOf("Single", "Window", "Crossing", "SelectAll", "Previous", "Clear")
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
        if (currentParsed == null || abs(currentParsed - uiState.commandParameterValue) > 1e-4) {
            manualParamText = if (uiState.commandParameterValue % 1.0 == 0.0) {
                uiState.commandParameterValue.toInt().toString()
            } else {
                uiState.commandParameterValue.toString()
            }
        }
    }

    var showLayerFilterMenu by remember { mutableStateOf(false) }
    var showTypeFilterMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Active Command Prompt Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .testTag("active_command_prompt_banner"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(OpaqueAmberBadgeBg)
                        .border(1.dp, CadAmberSnap, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = uiState.activeCommand.commandName,
                        style = MaterialTheme.typography.labelSmall,
                        color = CadAmberSnap,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = uiState.commandPromptBanner,
                    style = MaterialTheme.typography.labelSmall,
                    color = CadCyanPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Quick CMD / Coordinate entry trigger while command is active
                OpaqueChipButton(
                    label = "CMD / X,Y",
                    active = false,
                    activeBg = OpaqueCyanBadgeBg,
                    activeBorder = CadCyanPrimary,
                    textColor = CadCyanPrimary,
                    tag = "cmd_activate_button",
                    onClick = onOpenCommandEditing
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(OpaqueEmeraldBadgeBg)
                        .border(1.dp, CadEmeraldCrossing, RoundedCornerShape(5.dp))
                        .clickable(onClick = onFinishCommand)
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                        .testTag("active_cmd_finish_button")
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Finish Command", tint = CadEmeraldCrossing, modifier = Modifier.size(14.dp))
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(OpaqueRedBadgeBg)
                        .border(1.dp, CadRedAlert, RoundedCornerShape(5.dp))
                        .clickable(onClick = onCancelCommand)
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                        .testTag("active_cmd_cancel_button")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel Command", tint = CadRedAlert, modifier = Modifier.size(14.dp))
                }
            }
        }

        // QDIM Contextual Controls
        if (isQDimCommand) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (uiState.isQuickDimensionEnabled) OpaqueEmeraldBadgeBg else CadPanelElevated)
                        .border(
                            1.5.dp,
                            if (uiState.isQuickDimensionEnabled) CadEmeraldCrossing else CadBorderHairline,
                            RoundedCornerShape(6.dp)
                        )
                        .clickable { onToggleQDim(!uiState.isQuickDimensionEnabled) }
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                        .testTag("qdim_bar_toggle")
                ) {
                    Text(
                        text = if (uiState.isQuickDimensionEnabled) "QUICK ALIGN: ON" else "QUICK ALIGN: OFF",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (uiState.isQuickDimensionEnabled) CadEmeraldCrossing else CadTextSecondary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(OpaqueCyanBadgeBg)
                        .border(1.5.dp, CadCyanPrimary, RoundedCornerShape(6.dp))
                        .clickable(onClick = onRefreshQDim)
                        .padding(horizontal = 9.dp, vertical = 4.dp)
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

                QDimDensityPolicy.entries.forEach { policy ->
                    val isSel = uiState.qdimDensity == policy
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (isSel) OpaqueAmberBadgeBg else CadPanelElevated)
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
                            .background(if (isSel) OpaqueCyanBadgeBg else CadPanelElevated)
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

        // Sub-Options & Manual Numeric Parameter Controls
        if (subOptions.isNotEmpty() || needsNumericParam) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                subOptions.forEach { opt ->
                    val isSel = uiState.commandSubOption.equals(opt, true)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (isSel) OpaqueCyanBadgeBg else CadPanelElevated)
                            .border(1.dp, if (isSel) CadCyanPrimary else CadBorderHairline, RoundedCornerShape(5.dp))
                            .clickable { onSelectSubOption(opt) }
                            .padding(horizontal = 7.dp, vertical = 4.dp)
                            .testTag("suboption_$opt")
                    ) {
                        Text(
                            text = opt,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSel) CadCyanPrimary else CadTextSecondary,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }

                // Section 14.3: Layer & Entity-Type Selection Filters when SELECT is active
                if (uiState.activeCommand == CadCommandId.SELECT) {
                    Box {
                        OpaqueChipButton(
                            label = "By Layer…",
                            active = false,
                            activeBg = CadPanelElevated,
                            activeBorder = CadBorderHairline,
                            textColor = CadTextPrimary,
                            onClick = { showLayerFilterMenu = true }
                        )
                        DropdownMenu(
                            expanded = showLayerFilterMenu,
                            onDismissRequest = { showLayerFilterMenu = false },
                            modifier = Modifier.background(CadSurfaceCard)
                        ) {
                            uiState.layers.forEach { layer ->
                                DropdownMenuItem(
                                    text = { Text("Layer: ${layer.name}", color = CadTextPrimary) },
                                    onClick = {
                                        showLayerFilterMenu = false
                                        onSelectByLayer(layer.name)
                                    }
                                )
                            }
                        }
                    }

                    Box {
                        OpaqueChipButton(
                            label = "By Type…",
                            active = false,
                            activeBg = CadPanelElevated,
                            activeBorder = CadBorderHairline,
                            textColor = CadTextPrimary,
                            onClick = { showTypeFilterMenu = true }
                        )
                        DropdownMenu(
                            expanded = showTypeFilterMenu,
                            onDismissRequest = { showTypeFilterMenu = false },
                            modifier = Modifier.background(CadSurfaceCard)
                        ) {
                            listOf("LINE", "LWPOLYLINE", "CIRCLE", "ARC", "DIMENSION", "MTEXT").forEach { tName ->
                                DropdownMenuItem(
                                    text = { Text("Type: $tName", color = CadTextPrimary) },
                                    onClick = {
                                        showTypeFilterMenu = false
                                        onSelectByType(tName)
                                    }
                                )
                            }
                        }
                    }
                }

                if (needsNumericParam) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(CadPanelElevated)
                            .border(1.5.dp, CadAmberSnap, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
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
                                .width(68.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(CadModelSpaceBg)
                                .border(1.dp, CadCyanPrimary, RoundedCornerShape(4.dp))
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

                    Text(
                        text = "Quick:",
                        style = MaterialTheme.typography.labelSmall,
                        color = CadTextMuted
                    )
                    listOf(2.0, 5.0, 10.0).forEach { presetVal ->
                        val isSel = abs(uiState.commandParameterValue - presetVal) < 1e-4
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(if (isSel) OpaqueAmberBadgeBg else CadPanelElevated)
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
    }
}

@Composable
private fun OpaqueChipButton(
    label: String,
    active: Boolean,
    activeBg: Color,
    activeBorder: Color,
    textColor: Color,
    tag: String? = null,
    onClick: () -> Unit
) {
    val baseMod = Modifier
        .clip(RoundedCornerShape(5.dp))
        .background(if (active) activeBg else CadPanelElevated)
        .border(1.dp, if (active) activeBorder else CadBorderHairline, RoundedCornerShape(5.dp))
        .clickable(onClick = onClick)
        .padding(horizontal = 8.dp, vertical = 4.dp)
    Box(modifier = if (tag != null) baseMod.testTag(tag) else baseMod) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
        )
    }
}

// --- Section 14.7: '+' Action Launcher Dialog ---

@Composable
private fun ActionLauncherDialog(
    uiState: CadWorkspaceUiState,
    onSelectCommand: (CadCommandId) -> Unit,
    onSelectAll: () -> Unit,
    onSelectWindowMode: () -> Unit,
    onSelectCrossingMode: () -> Unit,
    onSelectPrevious: () -> Unit,
    onClearSelection: () -> Unit,
    onFitDrawing: () -> Unit,
    onOpenMatrix: () -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val filteredCommands = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            CadCommandId.entries
        } else {
            val ranked = CadCommandCompatibilityCatalog.rankSuggestionsForPrefix(searchQuery).map { it.id }
            if (ranked.isNotEmpty()) {
                ranked
            } else {
                val q = searchQuery.trim().uppercase()
                CadCommandId.entries.filter {
                    it.commandName.contains(q) || it.shortLabel.uppercase().contains(q) || it.primaryAlias.contains(q)
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 620.dp)
                .heightIn(max = 640.dp)
                .border(1.dp, CadCyanPrimary, RoundedCornerShape(12.dp))
                .testTag("action_launcher_dialog"),
            shape = RoundedCornerShape(12.dp),
            color = CadSurfaceCard
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "CAD COMMAND & ACTION LAUNCHER",
                            style = MaterialTheme.typography.titleMedium,
                            color = CadCyanPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Launch any Draw, Modify, Selection, Dimension, or Sheet action",
                            style = MaterialTheme.typography.labelSmall,
                            color = CadTextSecondary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close Launcher", tint = CadTextPrimary)
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text("Filter by command name or alias (e.g., L, PL, FILLET, QDIM)…", style = MaterialTheme.typography.labelSmall, color = CadTextMuted)
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.labelMedium.copy(color = CadTextPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("action_launcher_search_field")
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Quick Selection & Viewport Actions
                    if (searchQuery.isBlank()) {
                        Text(
                            text = "QUICK SELECTION & VIEWPORT",
                            style = MaterialTheme.typography.labelMedium,
                            color = CadAmberSnap,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OpaqueChipButton("Fit Drawing", true, OpaqueCyanBadgeBg, CadCyanPrimary, CadCyanPrimary, onClick = onFitDrawing)
                            OpaqueChipButton("Select All", false, CadPanelElevated, CadBorderHairline, CadTextPrimary, onClick = onSelectAll)
                            OpaqueChipButton("Window Select", false, CadPanelElevated, CadBorderHairline, CadTextPrimary, onClick = onSelectWindowMode)
                            OpaqueChipButton("Crossing Select", false, CadPanelElevated, CadBorderHairline, CadTextPrimary, onClick = onSelectCrossingMode)
                            OpaqueChipButton("Select Previous", false, CadPanelElevated, CadBorderHairline, CadTextPrimary, onClick = onSelectPrevious)
                            OpaqueChipButton("Clear Selection", false, CadPanelElevated, CadBorderHairline, CadTextSecondary, onClick = onClearSelection)
                            OpaqueChipButton("Command Matrix", false, OpaqueEmeraldBadgeBg, CadEmeraldCrossing, CadEmeraldCrossing, onClick = onOpenMatrix)
                        }

                        // Recent Commands
                        if (uiState.recentCommands.isNotEmpty()) {
                            Text(
                                text = "RECENT COMMANDS",
                                style = MaterialTheme.typography.labelMedium,
                                color = CadCyanPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                uiState.recentCommands.forEach { cmd ->
                                    OpaqueChipButton(
                                        label = "${cmd.shortLabel} (${cmd.primaryAlias})",
                                        active = uiState.activeCommand == cmd,
                                        activeBg = OpaqueAmberBadgeBg,
                                        activeBorder = CadAmberSnap,
                                        textColor = if (uiState.activeCommand == cmd) CadAmberSnap else CadTextPrimary,
                                        onClick = { onSelectCommand(cmd) }
                                    )
                                }
                            }
                        }
                    }

                    // Categorized Commands
                    CadCommandCategory.entries.forEach { category ->
                        val catCmds = filteredCommands.filter { it.category == category }
                        if (catCmds.isNotEmpty()) {
                            Text(
                                text = category.label.uppercase(),
                                style = MaterialTheme.typography.labelMedium,
                                color = CadCyanPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                catCmds.forEach { cmd ->
                                    val isCurrent = uiState.activeCommand == cmd
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isCurrent) OpaqueAmberBadgeBg else CadPanelElevated)
                                            .border(
                                                1.dp,
                                                if (isCurrent) CadAmberSnap else CadBorderHairline,
                                                RoundedCornerShape(6.dp)
                                            )
                                            .clickable { onSelectCommand(cmd) }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                            .testTag("launcher_cmd_${cmd.commandName}")
                                    ) {
                                        Text(
                                            text = "${cmd.shortLabel} (${cmd.primaryAlias})",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isCurrent) CadAmberSnap else CadTextPrimary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
