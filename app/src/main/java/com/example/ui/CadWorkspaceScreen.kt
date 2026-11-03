package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cad.data.CadProjectMetadataEntity
import com.example.cad.dimension.DimensionArrangementPreference
import com.example.cad.dimension.QDimDensityPolicy
import com.example.cad.io.CadDxfInterchange
import com.example.cad.kernel.FitDrawingPolicy
import com.example.cad.kernel.UsableViewportRect
import com.example.cad.model.CadCommandCategory
import com.example.cad.model.CadCommandCompatibilityCatalog
import com.example.cad.model.CadCommandId
import com.example.cad.model.CadEntity
import com.example.cad.model.CadNumberFormatter
import com.example.cad.model.CadUnit
import com.example.cad.model.HatchPattern
import com.example.cad.model.Point2D
import com.example.cad.model.UnitChangeMode
import com.example.cad.model.VirtualControllerMode
import com.example.cad.model.WorkspaceSpace
import com.example.cad.update.CadReleaseEntry
import com.example.cad.update.CadUpdateUiState
import java.util.Locale
import com.example.ui.components.CadDeckIdleTimerState
import com.example.ui.components.CadReleaseCatalogueDialog
import com.example.ui.components.CadViewportCanvas
import com.example.ui.components.CommandCompatibilityMatrixDialog
import com.example.ui.components.ControllerAndSnapSettingsDialog
import com.example.ui.components.ControllerDockState
import com.example.ui.components.DirectCoordinateInputDialog
import com.example.ui.components.DxfAndProjectHubDialog
import com.example.ui.components.HomeUpdateStatusIcon
import com.example.ui.components.LayerAndBlockManagerDialog
import com.example.ui.components.PropertiesAndConstraintsDialog
import com.example.ui.components.QuickDimensionDialog
import com.example.ui.components.SheetSetupDialog
import com.example.ui.components.UnitsAndPrecisionDialog
import com.example.ui.components.VirtualCursorControllerDeck
import com.example.ui.theme.CadAmberSnap
import com.example.ui.theme.CadBorderHairline
import com.example.ui.theme.CadBorderStrong
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

// Solid 100% opaque active surface tints (Section 3 & Section 33 — Pure-black neutral palette)
private val OpaqueCyanBadgeBg = Color(0xFF101923)
private val OpaqueAmberBadgeBg = Color(0xFF1F1A11)
private val OpaqueEmeraldBadgeBg = Color(0xFF0F1E18)
private val OpaqueRedBadgeBg = Color(0xFF241214)

@Composable
fun CadWorkspaceScreen(
    viewModel: CadWorkspaceViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val savedProjects by viewModel.savedProjects.collectAsStateWithLifecycle()
    val releaseCatalogue by viewModel.releaseCatalogue.collectAsStateWithLifecycle()
    val updatesBehindCount by viewModel.updatesBehindCount.collectAsStateWithLifecycle()
    val updateUiState by viewModel.updateUiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val density = LocalDensity.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onHostActivityResumed()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

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

    // SAF Document Picker for Open Drawing (.pcad / .dxf) from Home Screen
    val dxfFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()?.let { content ->
                val trimmed = content.trim()
                if (trimmed.isNotEmpty()) {
                    if (trimmed.startsWith("{") && trimmed.contains("\"schemaVersion\"")) {
                        viewModel.importPcadJsonString(trimmed)
                    } else {
                        viewModel.importDxfString(content)
                    }
                }
            }
        }
    }

    // Section 62: First-Run Home Screen (default launch entry point before workspace)
    if (uiState.isHomeScreenVisible) {
        CadHomeScreen(
            savedProjects = savedProjects,
            displayUnit = uiState.displayUnit,
            updatesBehindCount = updatesBehindCount,
            updateUiState = updateUiState,
            onOpenReleaseCatalogue = viewModel::openReleaseCatalogue,
            onNewBlankDrawing = { name, unit ->
                viewModel.startNewBlankDrawing(name, unit)
            },
            onOpenDxfFilePicker = {
                dxfFilePickerLauncher.launch(arrayOf("*/*"))
            },
            onImportDxfContent = { dxfText ->
                viewModel.importDxfString(dxfText)
            },
            onOpenSavedProject = { projectId ->
                viewModel.loadProject(projectId)
            },
            onRenameSavedProject = { projectId, newName ->
                viewModel.renameSavedProject(projectId, newName)
            },
            onDeleteSavedProject = { projectId ->
                viewModel.deleteSavedProject(projectId)
            }
        )
        if (uiState.isReleaseCatalogueOpen) {
            CadReleaseCatalogueDialog(
                releases = releaseCatalogue,
                updatesBehindCount = updatesBehindCount,
                updateUiState = updateUiState,
                onRefreshCatalogue = viewModel::refreshReleaseCatalogue,
                onDownloadRelease = viewModel::startUpdateDownload,
                onRequestInstall = viewModel::requestInstallVerifiedUpdate,
                onCancelOrReset = viewModel::cancelUpdateAction,
                onDismiss = viewModel::closeReleaseCatalogue
            )
        }
        return
    }

    // Section 16: Full-screen immersive container without status/navigation bar padding reservation
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(CadModelSpaceBg)
            .windowInsetsPadding(WindowInsets.displayCutout)
    ) {
        val isLandscape = maxWidth >= 660.dp

        if (uiState.toolDrawerState == ToolDrawerState.EXPANDED) {
            BackHandler {
                viewModel.setToolDrawerState(ToolDrawerState.COLLAPSED)
            }
        }

        if (!isLandscape) {
            // --- PORTRAIT OVERLAY-ONLY FULL-SCREEN WORKSPACE (Sections 14.7, 18.5, 19, 20) ---
            // CadViewportCanvas is rendered ONCE in a stable full-size root Box so opening/closing
            // any overlay (controller dock, tool browser, accordions, autocomplete, Overview) NEVER
            // alters the canvas size or rebases the viewport camera.
            var topChromeHeightPx by remember { mutableFloatStateOf(with(density) { 78.dp.toPx() }) }
            var bottomStackHeightPx by remember { mutableFloatStateOf(with(density) { 280.dp.toPx() }) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { size ->
                        val leftSafePx = if (uiState.toolDrawerState == ToolDrawerState.EXPANDED) {
                            with(density) { 244.dp.toPx() }.coerceAtMost(size.width * 0.48f)
                        } else {
                            with(density) { 12.dp.toPx() }
                        }
                        val topSafePx = topChromeHeightPx.coerceAtMost(size.height * 0.25f)
                        val bottomSafePx = (size.height - bottomStackHeightPx).coerceAtLeast(topSafePx + 120f)
                        viewModel.updateUsableViewportRect(
                            UsableViewportRect(
                                leftPx = leftSafePx,
                                topPx = topSafePx,
                                rightPx = size.width.toFloat(),
                                bottomPx = bottomSafePx
                            )
                        )
                    }
            ) {
                // 1. Stable Full-Screen 2D Technical CAD Viewport Canvas
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
                    polarPreview = uiState.polarPreview,
                    selectionBoxDrag = uiState.selectionBoxDrag,
                    viewport = uiState.viewport,
                    isToolDrawerOpen = uiState.toolDrawerState == ToolDrawerState.EXPANDED,
                    onViewportChanged = viewModel::updateViewport,
                    onViewportSizeChanged = viewModel::updateViewportDimensions,
                    onGestureStateChanged = viewModel::onViewportGestureStateChanged,
                    onRevealToolDrawer = { viewModel.setToolDrawerState(ToolDrawerState.EXPANDED) }
                )

                // 2. Top Overlay Stack: Engineering Header Bar + Transient Notification + Floating Status HUD
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .zIndex(2f)
                        .onSizeChanged { topChromeHeightPx = it.height.toFloat() }
                ) {
                    TopEngineeringHeaderBar(
                        projectName = uiState.currentProjectName,
                        displayUnitSymbol = uiState.displayUnit.symbol,
                        unitChangeMode = uiState.unitChangeMode,
                        isQuickDimensionEnabled = uiState.isQuickDimensionEnabled,
                        workspaceSpace = uiState.workspaceSpace,
                        fitDrawingPolicy = uiState.fitDrawingPolicy,
                        canUndo = uiState.canUndo,
                        canRedo = uiState.canRedo,
                        onOpenHome = viewModel::openHomeScreen,
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

                    ViewportFloatingStatusHud(
                        uiState = uiState,
                        onToggleGrid = viewModel::toggleGridVisibility,
                        onToggleGridSnap = viewModel::toggleGridSnap,
                        onToggleOsnap = viewModel::toggleOsnap,
                        onToggleOrtho = viewModel::toggleOrtho,
                        onTogglePolar = viewModel::togglePolarTracking,
                        onOpenLayers = { showLayerBlockDialog = true },
                        onOpenProperties = { showPropertiesDialog = true },
                        onSelectAll = viewModel::selectAllEntities,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // 3. Bottom Overlay Stack: Separated into Upper Command Region (Single Authoritative IME Offset) + Lower Stationary Controller Region (No .imePadding)
                // Phase 11.2 (v18): Single authoritative bottom-offset calculation (`maxOf(controllerDeckHeightPx, imeBottomPx)`)
                // eliminates double IME compensation while keeping the lower controller dock anchored at screen bottom.
                var controllerDeckHeightPx by remember { mutableFloatStateOf(with(density) { 188.dp.toPx() }) }
                var upperCommandRegionHeightPx by remember { mutableFloatStateOf(with(density) { 92.dp.toPx() }) }
                val imeBottomPx = WindowInsets.ime.getBottom(density).toFloat()
                val upperRegionBottomOffsetDp = with(density) {
                    maxOf(controllerDeckHeightPx, imeBottomPx).toDp()
                }

                LaunchedEffect(controllerDeckHeightPx, upperCommandRegionHeightPx) {
                    bottomStackHeightPx = controllerDeckHeightPx + upperCommandRegionHeightPx
                }

                // 3A. Lower Stationary Controller Region (Anchored at screen bottom — NO .imePadding())
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .zIndex(1f)
                        .onSizeChanged { controllerDeckHeightPx = it.height.toFloat() }
                        .testTag("lower_controller_stationary_region")
                ) {
                    VirtualCursorControllerDeck(
                        config = uiState.controllerConfig,
                        cursorModelPos = uiState.snappedCursorModelPos,
                        referencePoint = uiState.commandStepPoints.lastOrNull(),
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

                // 3B. Upper Command Region (OverviewOverlayCard + ContextualCommandAndOptionsDock — single authoritative bottom offset)
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = upperRegionBottomOffsetDp)
                        .zIndex(2f)
                        .onSizeChanged { upperCommandRegionHeightPx = it.height.toFloat() }
                        .testTag("upper_command_ime_region")
                ) {
                    // Floating Overview Overlay above the Function Toolbar
                    // Emerges upward from behind the function/command dock (lower zIndex than the dock).
                    OverviewOverlayCard(
                        uiState = uiState,
                        overview = uiState.activeOverview,
                        onDismiss = viewModel::dismissOverview,
                        onSelectSubOption = viewModel::setCommandSubOption,
                        onParameterChange = viewModel::setCommandParameterValue,
                        onToggleQDim = viewModel::setQuickDimensionEnabled,
                        onRefreshQDim = viewModel::refreshQuickDimensions,
                        onSelectQDimDensity = viewModel::setQDimDensity,
                        onSelectQDimArrangement = viewModel::setQDimArrangementPreference,
                        onOpenQDimDialog = { showQDimDialog = true },
                        onOpenCommandEditing = { viewModel.openCommandLineEditing(commandLineInput) },
                        onSelectByLayer = viewModel::selectEntitiesByLayer,
                        onSelectByType = viewModel::selectEntitiesByType,
                        onFinishCommand = { viewModel.finishCurrentCommand() },
                        onCancelCommand = viewModel::cancelCurrentCommand,
                        modifier = Modifier
                            .zIndex(0f)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                            .align(Alignment.Start)
                    )

                    // Tier 1 & Tier 2: Function/Action Bar + Persistent Command Entry & Prompt Bar
                    Box(modifier = Modifier.zIndex(1f)) {
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
                            onPickPointAtCursor = viewModel::pickPointAtCursor,
                            onFitDrawing = { viewModel.fitDrawing() },
                            onOpenProperties = { showPropertiesDialog = true },
                            onDeleteSelected = viewModel::deleteSelectedEntities,
                            onClearSelection = viewModel::clearSelection,
                            onSelectByLayer = viewModel::selectEntitiesByLayer,
                            onSelectByType = viewModel::selectEntitiesByType,
                            onFinishCommand = { viewModel.finishCurrentCommand() },
                            onCancelCommand = viewModel::cancelCurrentCommand
                        )
                    }
                }

                // 4. Left-Edge Swipe Tool Browser Overlay (5 Category Tiles + Single Selected Category Pane + Translucent Closed Arrow)
                RetractableSideToolDrawer(
                    uiState = uiState,
                    onOpenDrawer = { viewModel.setToolDrawerState(ToolDrawerState.EXPANDED) },
                    onCloseDrawer = { viewModel.setToolDrawerState(ToolDrawerState.COLLAPSED) },
                    onSelectCategoryTile = viewModel::selectCategoryTile,
                    onToggleAccordionSection = viewModel::toggleToolSection,
                    onSelectCommand = { cmd ->
                        handleCommandSelection(cmd)
                        viewModel.setToolDrawerState(ToolDrawerState.COLLAPSED)
                    },
                    onSelectAll = viewModel::selectAllEntities,
                    onSelectPrevious = viewModel::selectPreviousEntities,
                    onClearSelection = viewModel::clearSelection,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .zIndex(3f)
                        .padding(top = 68.dp, bottom = 12.dp)
                )
            }
        } else {
            // --- LANDSCAPE ADAPTIVE FULL-SCREEN WORKSPACE (Overlay-Invariant Viewport) ---
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
                    onOpenHome = viewModel::openHomeScreen,
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
                    // Central 2D Technical CAD Viewport (Full-height Box with overlays)
                    var landscapeBottomHeightPx by remember { mutableFloatStateOf(with(density) { 96.dp.toPx() }) }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .onSizeChanged { size ->
                                val topHudPx = with(density) { 38.dp.toPx() }
                                val leftDrawerPx = if (uiState.toolDrawerState == ToolDrawerState.EXPANDED) {
                                    with(density) { 244.dp.toPx() }.coerceAtMost(size.width * 0.45f)
                                } else {
                                    with(density) { 12.dp.toPx() }
                                }
                                val bottomSafePx = (size.height - landscapeBottomHeightPx).coerceAtLeast(topHudPx + 100f)
                                viewModel.updateUsableViewportRect(
                                    UsableViewportRect(
                                        leftPx = leftDrawerPx,
                                        topPx = topHudPx.coerceAtMost(size.height * 0.25f),
                                        rightPx = size.width.toFloat(),
                                        bottomPx = bottomSafePx
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
                            polarPreview = uiState.polarPreview,
                            selectionBoxDrag = uiState.selectionBoxDrag,
                            viewport = uiState.viewport,
                            isToolDrawerOpen = uiState.toolDrawerState == ToolDrawerState.EXPANDED,
                            onViewportChanged = viewModel::updateViewport,
                            onViewportSizeChanged = viewModel::updateViewportDimensions,
                            onGestureStateChanged = viewModel::onViewportGestureStateChanged,
                            onRevealToolDrawer = { viewModel.setToolDrawerState(ToolDrawerState.EXPANDED) }
                        )

                        ViewportFloatingStatusHud(
                            uiState = uiState,
                            onToggleGrid = viewModel::toggleGridVisibility,
                            onToggleGridSnap = viewModel::toggleGridSnap,
                            onToggleOsnap = viewModel::toggleOsnap,
                            onToggleOrtho = viewModel::toggleOrtho,
                            onTogglePolar = viewModel::togglePolarTracking,
                            onOpenLayers = { showLayerBlockDialog = true },
                            onOpenProperties = { showPropertiesDialog = true },
                            onSelectAll = viewModel::selectAllEntities,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        )

                        RetractableSideToolDrawer(
                            uiState = uiState,
                            onOpenDrawer = { viewModel.setToolDrawerState(ToolDrawerState.EXPANDED) },
                            onCloseDrawer = { viewModel.setToolDrawerState(ToolDrawerState.COLLAPSED) },
                            onSelectCategoryTile = viewModel::selectCategoryTile,
                            onToggleAccordionSection = viewModel::toggleToolSection,
                            onSelectCommand = { cmd ->
                                handleCommandSelection(cmd)
                                viewModel.setToolDrawerState(ToolDrawerState.COLLAPSED)
                            },
                            onSelectAll = viewModel::selectAllEntities,
                            onSelectPrevious = viewModel::selectPreviousEntities,
                            onClearSelection = viewModel::clearSelection,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(vertical = 36.dp)
                        )

                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .imePadding()
                                .onSizeChanged { landscapeBottomHeightPx = it.height.toFloat() }
                                .testTag("upper_command_ime_region")
                        ) {
                            OverviewOverlayCard(
                                uiState = uiState,
                                overview = uiState.activeOverview,
                                onDismiss = viewModel::dismissOverview,
                                onSelectSubOption = viewModel::setCommandSubOption,
                                onParameterChange = viewModel::setCommandParameterValue,
                                onToggleQDim = viewModel::setQuickDimensionEnabled,
                                onRefreshQDim = viewModel::refreshQuickDimensions,
                                onSelectQDimDensity = viewModel::setQDimDensity,
                                onSelectQDimArrangement = viewModel::setQDimArrangementPreference,
                                onOpenQDimDialog = { showQDimDialog = true },
                                onOpenCommandEditing = { viewModel.openCommandLineEditing(commandLineInput) },
                                onSelectByLayer = viewModel::selectEntitiesByLayer,
                                onSelectByType = viewModel::selectEntitiesByType,
                                onFinishCommand = { viewModel.finishCurrentCommand() },
                                onCancelCommand = viewModel::cancelCurrentCommand,
                                modifier = Modifier
                                    .zIndex(0f)
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                                    .align(Alignment.Start)
                            )

                            Box(modifier = Modifier.zIndex(1f)) {
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
                                    onPickPointAtCursor = viewModel::pickPointAtCursor,
                                    onFitDrawing = { viewModel.fitDrawing() },
                                    onOpenProperties = { showPropertiesDialog = true },
                                    onDeleteSelected = viewModel::deleteSelectedEntities,
                                    onClearSelection = viewModel::clearSelection,
                                    onSelectByLayer = viewModel::selectEntitiesByLayer,
                                    onSelectByType = viewModel::selectEntitiesByType,
                                    onFinishCommand = { viewModel.finishCurrentCommand() },
                                    onCancelCommand = viewModel::cancelCurrentCommand
                                )
                            }
                        }
                    }

                    // Right-Side Opaque Inspector + Stationary Collapsible Virtual Cursor Controller (NO .imePadding())
                    Column(
                        modifier = Modifier
                            .width(308.dp)
                            .fillMaxHeight()
                            .background(CadPanelSurface)
                            .border(1.dp, CadBorderHairline)
                            .testTag("lower_controller_stationary_region"),
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
                            referencePoint = uiState.commandStepPoints.lastOrNull(),
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
        val currentDimEntities = remember(uiState.entities) {
            uiState.entities.filterIsInstance<CadEntity.DimensionEntity>()
        }
        QuickDimensionDialog(
            isQuickDimensionEnabled = uiState.isQuickDimensionEnabled,
            selectedCount = uiState.selectedEntityIds.size,
            currentDensity = uiState.qdimDensity,
            currentArrangement = uiState.qdimArrangementPreference,
            alignmentReport = uiState.lastAlignmentReport,
            totalDimensionCount = currentDimEntities.size,
            dimensionEntities = currentDimEntities,
            storageUnit = uiState.storageUnit,
            displayUnit = uiState.displayUnit,
            precision = uiState.precision,
            onToggleQuickDimension = viewModel::setQuickDimensionEnabled,
            onRefreshQuickDimension = viewModel::refreshQuickDimensions,
            onClearAllDimensions = viewModel::clearAllDimensionsConfirmed,
            onRemoveSelectedDimensions = viewModel::removeSelectedDimensionsConfirmed,
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
            onDeleteProject = viewModel::deleteSavedProject,
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

// --- Section 4 & Section 59.2: Top Anchor-Origin Transient Notification Banner ---

@Composable
private fun TransientNotificationBanner(
    notification: TransientNotification?,
    onDismiss: () -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth().clipToBounds()) {
        AnimatedVisibility(
            visible = notification != null,
            enter = slideInVertically(animationSpec = tween(220)) { -it } +
                fadeIn(animationSpec = tween(200)) +
                scaleIn(initialScale = 0.98f, animationSpec = tween(220)),
            exit = slideOutVertically(animationSpec = tween(190)) { -it } +
                fadeOut(animationSpec = tween(180)) +
                scaleOut(targetScale = 0.98f, animationSpec = tween(190))
        ) {
            val currentNotification = notification ?: return@AnimatedVisibility
            AnimatedContent(
                targetState = currentNotification,
                contentKey = { it.id },
                transitionSpec = {
                    (fadeIn(animationSpec = tween(160)) + slideInVertically(animationSpec = tween(160)) { -it / 2 })
                        .togetherWith(
                            fadeOut(animationSpec = tween(140)) + slideOutVertically(animationSpec = tween(140)) { -it / 2 }
                        )
                },
                label = "TopNotificationReplacementTransition"
            ) { item ->
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
    onOpenHome: () -> Unit,
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
            // Home Screen Button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(CadPanelElevated)
                    .border(1.dp, CadBorderHairline, RoundedCornerShape(6.dp))
                    .clickable(onClick = onOpenHome)
                    .padding(horizontal = 7.dp, vertical = 4.dp)
                    .testTag("top_home_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = "Return to Home Screen",
                    tint = CadCyanPrimary,
                    modifier = Modifier.size(15.dp)
                )
            }

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
                Icon(Icons.AutoMirrored.Filled.ListAlt, contentDescription = "Command Compatibility Matrix", tint = CadEmeraldCrossing, modifier = Modifier.size(14.dp))
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
    onTogglePolar: () -> Unit,
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
        val effectiveGridStr = CadNumberFormatter.formatDecimal(
            uiState.effectiveGridSnapInterval,
            decimals = 2,
            suppressTrailingZeros = true
        )
        HudToggleBadge(
            label = "GSNAP ($effectiveGridStr)",
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
        val polarSnapSuffix = if (uiState.gridSnapConfig.polarSnapEnabled) " • SNAP" else ""
        HudToggleBadge(
            label = if (uiState.polarPreview?.isPolarGuideAcquired == true && uiState.polarPreview.activePolarAngleDeg != null) {
                val trackLabel = if (uiState.polarPreview.activePolarAngleDeg % 1.0 == 0.0) {
                    "${uiState.polarPreview.activePolarAngleDeg.toInt()}°"
                } else {
                    String.format(Locale.ROOT, "%.1f°", uiState.polarPreview.activePolarAngleDeg)
                }
                "POLAR [θ=${String.format(Locale.ROOT, "%.0f°", uiState.polarPreview.actualAngleThetaDeg ?: 0.0)} • θₚ=$trackLabel$polarSnapSuffix]"
            } else {
                "POLAR (0°–359°$polarSnapSuffix)"
            },
            active = uiState.gridSnapConfig.polarTrackingEnabled,
            activeBg = OpaqueCyanBadgeBg,
            activeColor = CadCyanPrimary,
            tag = "hud_toggle_polar",
            onClick = onTogglePolar
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

// --- Sections 39.3 & 42.4: Tool Browser with 5 Vertical Category Tiles, Single-Category Content Pane & Translucent Closed Arrow ---

@Composable
private fun RetractableSideToolDrawer(
    uiState: CadWorkspaceUiState,
    onOpenDrawer: () -> Unit,
    onCloseDrawer: () -> Unit,
    onSelectCategoryTile: (CadCommandCategory) -> Unit,
    @Suppress("UNUSED_PARAMETER") onToggleAccordionSection: (CadCommandCategory) -> Unit,
    onSelectCommand: (CadCommandId) -> Unit,
    onSelectAll: () -> Unit,
    onSelectPrevious: () -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isExpanded = uiState.toolDrawerState == ToolDrawerState.EXPANDED

    Box(modifier = modifier) {
        // Section 39.3 & 42.4: Subtle, low-opacity translucent arrow handle in closed state; hidden when expanded
        if (!isExpanded) {
            Box(
                modifier = Modifier
                    .width(20.dp)
                    .height(54.dp)
                    .clip(RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
                    .background(CadPanelSurface.copy(alpha = 0.42f))
                    .border(
                        1.dp,
                        CadCyanPrimary.copy(alpha = 0.38f),
                        RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)
                    )
                    .clickable(onClick = onOpenDrawer)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { change, dragAmount ->
                            if (dragAmount > 8f) {
                                change.consume()
                                onOpenDrawer()
                            }
                        }
                    }
                    .testTag("tool_drawer_handle"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "❯",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    color = CadCyanPrimary.copy(alpha = 0.72f),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandHorizontally(animationSpec = tween(200)) + fadeIn(animationSpec = tween(200)),
            exit = shrinkHorizontally(animationSpec = tween(180)) + fadeOut(animationSpec = tween(180))
        ) {
            Surface(
                modifier = Modifier
                    .width(248.dp)
                    .heightIn(max = 300.dp)
                    .border(1.dp, CadBorderStrong, RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp))
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(
                                requireUnconsumed = false,
                                pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial
                            )
                            val startPos = down.position
                            var closedBySwipe = false
                            while (true) {
                                val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                                val active = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull()
                                if (active == null || !active.pressed) break
                                val dx = active.position.x - startPos.x
                                val dy = active.position.y - startPos.y
                                if (!closedBySwipe && dx < -36f && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.3f) {
                                    closedBySwipe = true
                                    event.changes.forEach { it.consume() }
                                    onCloseDrawer()
                                    break
                                }
                            }
                        }
                    }
                    .testTag("tool_drawer_panel"),
                shape = RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp),
                color = CadPanelSurface
            ) {
                val activeCat = uiState.activeCategory
                val activeCategoryCommands = remember(activeCat) {
                    val raw = CadCommandId.entries.filter { it.category == activeCat }
                    if (activeCat == CadCommandCategory.DRAW) {
                        val priority = listOf(
                            CadCommandId.LINE,
                            CadCommandId.POLYGON,
                            CadCommandId.POLYLINE,
                            CadCommandId.CIRCLE,
                            CadCommandId.ARC,
                            CadCommandId.RECTANGLE
                        )
                        priority + raw.filterNot { it in priority }
                    } else {
                        raw
                    }
                }

                Row(modifier = Modifier.fillMaxWidth()) {
                    // Left Vertical Category Side Tile Rail (5 CAD Category Side Tiles — Section 39.3 & 42.4)
                    Column(
                        modifier = Modifier
                            .width(56.dp)
                            .verticalScroll(rememberScrollState())
                            .background(CadModelSpaceBg)
                            .border(1.dp, CadBorderHairline)
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "CATS",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 8.5.sp,
                            color = CadTextMuted,
                            fontWeight = FontWeight.Bold
                        )
                        CadCommandCategory.entries.forEach { cat ->
                            val isActive = activeCat == cat
                            val shortCode = when (cat) {
                                CadCommandCategory.DRAW -> "DRAW"
                                CadCommandCategory.MODIFY -> "MOD"
                                CadCommandCategory.SELECT_INQUIRY -> "SEL"
                                CadCommandCategory.ANNOTATE_DIM -> "DIM"
                                CadCommandCategory.DOCUMENT_SYSTEM -> "DOC"
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isActive) OpaqueCyanBadgeBg else CadHudSurface)
                                    .border(
                                        1.dp,
                                        if (isActive) CadCyanPrimary else CadBorderHairline,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .clickable { onSelectCategoryTile(cat) }
                                    .testTag("category_tile_${cat.name}"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = shortCode,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 9.5.sp,
                                    color = if (isActive) CadCyanPrimary else CadTextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Right Content Pane: Renders ONLY the currently selected category's commands (Section 39.3 & 42.4)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(8.dp)
                            .verticalScroll(rememberScrollState())
                            .testTag("active_category_content_pane"),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = activeCat.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = CadCyanPrimary,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("category_tab_${activeCat.name}")
                            )
                            Text(
                                text = "Swipe ←",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                color = CadTextMuted,
                                modifier = Modifier
                                    .clickable(onClick = onCloseDrawer)
                                    .testTag("tool_drawer_close_button")
                            )
                        }

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            activeCategoryCommands.chunked(2).forEach { rowCmds ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    rowCmds.forEach { cmd ->
                                        val isCmd = uiState.activeCommand == cmd
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(5.dp))
                                                .background(if (isCmd) OpaqueAmberBadgeBg else CadHudSurface)
                                                .border(
                                                    1.dp,
                                                    if (isCmd) CadAmberSnap else CadBorderHairline,
                                                    RoundedCornerShape(5.dp)
                                                )
                                                .clickable { onSelectCommand(cmd) }
                                                .padding(horizontal = 6.dp, vertical = 6.dp)
                                                .testTag("cmd_button_${cmd.commandName}")
                                        ) {
                                            Text(
                                                text = "${cmd.shortLabel} (${cmd.primaryAlias})",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 9.5.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                color = if (isCmd) CadAmberSnap else CadTextPrimary,
                                                fontWeight = if (isCmd) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    }
                                    if (rowCmds.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
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
        }
    }
}

// --- Sections 30, 39.2 & 42.5: Floating Bottom Overview Card (Active-Session Mode + Transient Guidance Mode) ---

@Composable
private fun OverviewOverlayCard(
    uiState: CadWorkspaceUiState,
    overview: OverviewCardEvent?,
    onDismiss: () -> Unit,
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
    onCancelCommand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isActiveSession = uiState.isActiveCommandSession
    val isVisible = isActiveSession || overview != null

    // Section 59.2: Anchor-Origin Reveal & Retract Motion from behind the Function/Command Toolbar
    Box(modifier = modifier.clipToBounds()) {
        AnimatedVisibility(
            visible = isVisible,
            enter = slideInVertically(animationSpec = tween(220)) { it } +
                fadeIn(animationSpec = tween(200)) +
                scaleIn(initialScale = 0.98f, animationSpec = tween(220)),
            exit = slideOutVertically(animationSpec = tween(190)) { it } +
                fadeOut(animationSpec = tween(180)) +
                scaleOut(targetScale = 0.98f, animationSpec = tween(190))
        ) {
            if (isActiveSession) {
                // Active-Session Mode: Persists continuously while the command/session is active
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CadAmberSnap, RoundedCornerShape(8.dp))
                        .testTag("overview_overlay_card"),
                    shape = RoundedCornerShape(8.dp),
                    color = CadSurfaceCard
                ) {
                    Box(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
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
                }
            } else if (overview != null) {
                // Transient Guidance Mode: Fades in, holds ~2.5s, fades out when no session is active
                AnimatedContent(
                    targetState = overview,
                    contentKey = { it.id },
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(180)) + slideInVertically(animationSpec = tween(180)) { it })
                            .togetherWith(
                                fadeOut(animationSpec = tween(160)) + slideOutVertically(animationSpec = tween(160)) { it }
                            )
                    },
                    label = "OverviewOverlayTransientTransition"
                ) { activeEvent ->
                val borderCol = when (activeEvent.category) {
                    NotificationCategory.SUCCESS -> CadEmeraldCrossing
                    NotificationCategory.WARNING -> CadAmberSnap
                    NotificationCategory.ERROR -> CadRedAlert
                    NotificationCategory.INFO -> CadCyanPrimary
                }
                Surface(
                    modifier = Modifier
                        .widthIn(min = 220.dp, max = 440.dp)
                        .border(1.dp, borderCol, RoundedCornerShape(8.dp))
                        .clickable(onClick = onDismiss)
                        .testTag("overview_overlay_card"),
                    shape = RoundedCornerShape(8.dp),
                    color = CadSurfaceCard
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "OVERVIEW",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                color = borderCol,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.labelSmall,
                                color = CadTextMuted
                            )
                            Text(
                                text = activeEvent.title,
                                style = MaterialTheme.typography.labelSmall,
                                color = CadTextPrimary,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = activeEvent.explanation,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = CadTextSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
}

// --- Sections 14.2 & 19: Persistent 3-Tier Bottom Control Stack (Tier 1 Function Bar + Tier 2 Persistent Command Entry) ---

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
    onPickPointAtCursor: () -> Unit,
    onFitDrawing: () -> Unit,
    onOpenProperties: () -> Unit,
    onDeleteSelected: () -> Unit,
    onClearSelection: () -> Unit,
    onSelectByLayer: (String) -> Unit,
    onSelectByType: (String) -> Unit,
    onFinishCommand: () -> Unit,
    onCancelCommand: () -> Unit
) {
    val isRunningActiveCommand = uiState.commandPanelState is CommandPanelState.Running ||
        uiState.activeCommand != CadCommandId.SELECT ||
        uiState.commandStepPoints.isNotEmpty() ||
        uiState.selectionBoxDrag != null

    val rankedSuggestions = remember(commandLineInput) {
        CadCommandCompatibilityCatalog.rankSuggestionsForPrefix(commandLineInput).take(6)
    }

    // Phase 9.1 (v18): Unbordered floating container so the CAD canvas remains visible behind floating controls
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("contextual_command_and_options_dock"),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // TIER 1 (Top of Bottom Stack): Persistent Function / Action Bar (No outer container border; floating chips over canvas)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .testTag("function_action_bar"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
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

            // Tool Browser Toggle (No arrow icon)
            OpaqueChipButton(
                label = if (uiState.toolDrawerState == ToolDrawerState.EXPANDED) "Close Tools" else "Tools Browser",
                active = uiState.toolDrawerState == ToolDrawerState.EXPANDED,
                activeBg = OpaqueCyanBadgeBg,
                activeBorder = CadCyanPrimary,
                textColor = CadTextPrimary,
                tag = "bottom_tools_toggle_button",
                onClick = onToggleToolDrawer
            )

            // PICK PT affordance in Function Bar when awaiting point or in Relative Touchpad mode
            if (isRunningActiveCommand || uiState.controllerConfig.mode == VirtualControllerMode.RELATIVE_TOUCHPAD) {
                OpaqueChipButton(
                    label = "PICK PT",
                    active = true,
                    activeBg = OpaqueCyanBadgeBg,
                    activeBorder = CadCyanPrimary,
                    textColor = CadCyanPrimary,
                    tag = "function_bar_pick_point_button",
                    onClick = onPickPointAtCursor
                )
            }

            OpaqueChipButton(
                label = "FIT",
                active = false,
                activeBg = CadPanelElevated,
                activeBorder = CadBorderHairline,
                textColor = CadTextPrimary,
                tag = "function_bar_fit_button",
                onClick = onFitDrawing
            )

            OpaqueChipButton(
                label = if (uiState.isQuickDimensionEnabled) "QDIM: ON" else "QDIM",
                active = uiState.isQuickDimensionEnabled,
                activeBg = OpaqueEmeraldBadgeBg,
                activeBorder = CadEmeraldCrossing,
                textColor = if (uiState.isQuickDimensionEnabled) CadEmeraldCrossing else CadTextPrimary,
                tag = "function_bar_qdim_button",
                onClick = onOpenQDimDialog
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
            }
        }

        // Ranked Prefix Autocomplete Strip (when typing a command prefix)
        if (commandLineInput.isNotBlank() && rankedSuggestions.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
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

        // TIER 2 (Middle of Bottom Stack): Persistent Command Input & Coordinate Entry Bar
        // Phase 9.2 & Phase 10.6 (v18):
        // CommandDeckVisible = isImeVisible || commandLineInput.isNotEmpty()
        val density = LocalDensity.current
        val focusManager = LocalFocusManager.current
        val scope = rememberCoroutineScope()
        val commandDeckTimer = remember(scope) { CadDeckIdleTimerState(scope) }
        val imeBottomPx = WindowInsets.ime.getBottom(density)
        var isCommandFieldFocused by remember { mutableStateOf(false) }
        var wasImeOpenWhileFocused by remember { mutableStateOf(false) }

        LaunchedEffect(imeBottomPx, isCommandFieldFocused) {
            if (!isCommandFieldFocused) {
                wasImeOpenWhileFocused = false
            } else if (imeBottomPx > 0) {
                wasImeOpenWhileFocused = true
            } else if (wasImeOpenWhileFocused && imeBottomPx == 0) {
                // Software keyboard was dismissed while focused
                wasImeOpenWhileFocused = false
                isCommandFieldFocused = false
                focusManager.clearFocus(force = true)
                if (commandLineInput.isEmpty()) {
                    onCloseCommandEditing()
                }
            }
        }

        val commandImeVisible = isCommandFieldFocused &&
            (imeBottomPx > 0 || !wasImeOpenWhileFocused) &&
            uiState.commandPanelState is CommandPanelState.Editing
        val isCommandDeckVisible = CadDeckIdleTimerState.isCommandDeckVisible(
            isImeVisible = commandImeVisible,
            commandLineInput = commandLineInput
        )

        var isSendCardInFront by rememberSaveable { mutableStateOf(true) }
        LaunchedEffect(isCommandDeckVisible) {
            if (isCommandDeckVisible) {
                commandDeckTimer.startOrReset()
            } else {
                isSendCardInFront = true
                commandDeckTimer.stop()
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("persistent_command_entry_bar"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, CadBorderStrong, RoundedCornerShape(8.dp)),
                shape = RoundedCornerShape(8.dp),
                color = Color(0xD80A0F14)
            ) {
                OutlinedTextField(
                    value = commandLineInput,
                    onValueChange = { nextText ->
                        commandDeckTimer.onUserInteraction()
                        onCommandLineInputChange(nextText)
                    },
                    placeholder = {
                        Text(
                            text = "Command or X,Y coordinates (L, PL, C, F, QDIM, 100,150, @25,-10)",
                            style = MaterialTheme.typography.labelSmall,
                            color = CadTextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (commandLineInput.isNotBlank()) {
                            onSubmitCommandLine()
                        }
                        focusManager.clearFocus(force = true)
                    }),
                    textStyle = MaterialTheme.typography.labelMedium.copy(color = CadTextPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .onFocusChanged { focusState ->
                            isCommandFieldFocused = focusState.isFocused
                            if (focusState.isFocused) {
                                commandDeckTimer.onUserInteraction()
                                onOpenCommandEditing()
                            } else if (commandLineInput.isEmpty()) {
                                onCloseCommandEditing()
                            }
                        }
                        .testTag("command_line_input_field")
                )
            }

            // Stacked Send / Cancel Action-Card Control on Right Side of Command Bar
            // Visible ONLY when CommandDeckVisible == true; rear card concealed at rest with 75s idle peek & 2.4s post-exchange reveal
            AnimatedVisibility(
                visible = isCommandDeckVisible,
                enter = fadeIn(animationSpec = tween(180)) + expandHorizontally(animationSpec = tween(180)),
                exit = fadeOut(animationSpec = tween(160)) + shrinkHorizontally(animationSpec = tween(160))
            ) {
                val isRearRevealed = commandDeckTimer.isRearCardRevealed
                val sendOffsetX by animateDpAsState(
                    targetValue = when {
                        isSendCardInFront -> 0.dp
                        isRearRevealed -> (-6).dp
                        else -> 0.dp
                    },
                    animationSpec = tween(180),
                    label = "SendCardOffsetX"
                )
                val sendRotation by animateFloatAsState(
                    targetValue = when {
                        isSendCardInFront -> 0f
                        isRearRevealed -> -7f
                        else -> 0f
                    },
                    animationSpec = tween(180),
                    label = "SendCardRotation"
                )
                val sendScale by animateFloatAsState(
                    targetValue = when {
                        isSendCardInFront -> 1f
                        isRearRevealed -> 0.91f
                        else -> 0.86f
                    },
                    animationSpec = tween(180),
                    label = "SendCardScale"
                )
                val sendAlpha by animateFloatAsState(
                    targetValue = when {
                        isSendCardInFront -> 1f
                        isRearRevealed -> 0.72f
                        else -> 0f
                    },
                    animationSpec = tween(180),
                    label = "SendCardAlpha"
                )

                val cancelOffsetX by animateDpAsState(
                    targetValue = when {
                        !isSendCardInFront -> 0.dp
                        isRearRevealed -> (-6).dp
                        else -> 0.dp
                    },
                    animationSpec = tween(180),
                    label = "CancelCardOffsetX"
                )
                val cancelRotation by animateFloatAsState(
                    targetValue = when {
                        !isSendCardInFront -> 0f
                        isRearRevealed -> -7f
                        else -> 0f
                    },
                    animationSpec = tween(180),
                    label = "CancelCardRotation"
                )
                val cancelScale by animateFloatAsState(
                    targetValue = when {
                        !isSendCardInFront -> 1f
                        isRearRevealed -> 0.91f
                        else -> 0.86f
                    },
                    animationSpec = tween(180),
                    label = "CancelCardScale"
                )
                val cancelAlpha by animateFloatAsState(
                    targetValue = when {
                        !isSendCardInFront -> 1f
                        isRearRevealed -> 0.72f
                        else -> 0f
                    },
                    animationSpec = tween(180),
                    label = "CancelCardAlpha"
                )

                Box(
                    modifier = Modifier
                        .width(50.dp)
                        .height(42.dp)
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(
                                    requireUnconsumed = false,
                                    pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial
                                )
                                val startPos = down.position
                                val touchSlop = viewConfiguration.touchSlop.coerceAtLeast(10f)
                                var swapped = false
                                while (true) {
                                    val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull()
                                    if (change == null || !change.pressed) break
                                    val dx = change.position.x - startPos.x
                                    val dy = change.position.y - startPos.y
                                    if (!swapped && dy >= touchSlop && dy > abs(dx) * 1.25f) {
                                        swapped = true
                                        change.consume()
                                        isSendCardInFront = !isSendCardInFront
                                        commandDeckTimer.onOrderExchanged()
                                        break
                                    }
                                }
                            }
                        }
                        .testTag("command_action_card_stack"),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    // Cancel Card (Red X — concealed at rest when Send is front; foreground when exchanged)
                    Surface(
                        modifier = Modifier
                            .size(38.dp)
                            .offset(x = cancelOffsetX, y = if (!isSendCardInFront) 0.dp else if (isRearRevealed) 1.dp else 0.dp)
                            .zIndex(if (!isSendCardInFront) 2f else 1f)
                            .graphicsLayer {
                                rotationZ = cancelRotation
                                scaleX = cancelScale
                                scaleY = cancelScale
                                alpha = cancelAlpha
                            }
                            .border(
                                1.2.dp,
                                CadRedAlert,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable(enabled = !isSendCardInFront || isRearRevealed) {
                                if (!isSendCardInFront) {
                                    commandDeckTimer.onUserInteraction()
                                    if (isRunningActiveCommand) {
                                        onCancelCommand()
                                    }
                                    isCommandFieldFocused = false
                                    focusManager.clearFocus(force = true)
                                    onCloseCommandEditing()
                                } else {
                                    isSendCardInFront = false
                                    commandDeckTimer.onOrderExchanged()
                                }
                            }
                            .testTag("command_card_cancel"),
                        shape = RoundedCornerShape(8.dp),
                        color = OpaqueRedBadgeBg
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel Active Command or Clear Input",
                                tint = CadRedAlert,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }

                    // Send Card (Green Send Arrow — foreground by default; concealed at rest when exchanged)
                    Surface(
                        modifier = Modifier
                            .size(38.dp)
                            .offset(x = sendOffsetX, y = if (isSendCardInFront) 0.dp else if (isRearRevealed) 1.dp else 0.dp)
                            .zIndex(if (isSendCardInFront) 2f else 1f)
                            .graphicsLayer {
                                rotationZ = sendRotation
                                scaleX = sendScale
                                scaleY = sendScale
                                alpha = sendAlpha
                            }
                            .border(
                                1.2.dp,
                                CadEmeraldCrossing,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable(enabled = isSendCardInFront || isRearRevealed) {
                                if (isSendCardInFront) {
                                    commandDeckTimer.onUserInteraction()
                                    if (commandLineInput.isNotBlank()) {
                                        onSubmitCommandLine()
                                        isCommandFieldFocused = false
                                        focusManager.clearFocus(force = true)
                                    }
                                } else {
                                    isSendCardInFront = true
                                    commandDeckTimer.onOrderExchanged()
                                }
                            }
                            .testTag("command_card_send"),
                        shape = RoundedCornerShape(8.dp),
                        color = OpaqueEmeraldBadgeBg
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Execute Command or Coordinate",
                                tint = CadEmeraldCrossing,
                                modifier = Modifier.size(17.dp)
                            )
                        }
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
    @Suppress("UNUSED_PARAMETER") onOpenCommandEditing: () -> Unit,
    onSelectByLayer: (String) -> Unit,
    onSelectByType: (String) -> Unit,
    onFinishCommand: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onCancelCommand: () -> Unit
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

    // Section 51 & 55.2: Standardized generic parameter label "VAL="
    val paramLabel = "VAL="
    val fixedValSummary = if (needsNumericParam) {
        if (uiState.activeCommand == CadCommandId.POLYGON) {
            "VAL=${uiState.commandParameterValue.toInt().coerceIn(3, 64)}"
        } else {
            val formattedVal = String.format(Locale.ROOT, "%.2f", uiState.commandParameterValue)
            "VAL=$formattedVal ${uiState.displayUnit.symbol}"
        }
    } else null

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
        // Section 51 & 55.2: Active Command Prompt Header Row
        // 1. Stationary yellow command badge
        // 2. Stationary fixed VAL= badge (when numeric parameter applies)
        // 3. Clipped action-only scrolling marquee (carries ONLY the step action instruction)
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
                        .testTag("active_command_name_badge")
                ) {
                    Text(
                        text = uiState.activeCommand.commandName,
                        style = MaterialTheme.typography.labelSmall,
                        color = CadAmberSnap,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                if (fixedValSummary != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(CadPanelElevated)
                            .border(1.dp, CadAmberSnap, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .testTag("active_command_fixed_val_badge")
                    ) {
                        Text(
                            text = fixedValSummary,
                            style = MaterialTheme.typography.labelSmall,
                            color = CadAmberSnap,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clipToBounds()
                ) {
                    key(uiState.activeCommand, uiState.commandStepPoints.size, uiState.commandSubOption) {
                        Text(
                            text = uiState.activeActionPrompt,
                            style = MaterialTheme.typography.labelSmall,
                            color = CadCyanPrimary,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier
                                .fillMaxWidth()
                                .basicMarquee(
                                    iterations = Int.MAX_VALUE,
                                    initialDelayMillis = 1200,
                                    repeatDelayMillis = 1200
                                )
                                .testTag("active_command_marquee_text")
                        )
                    }
                }
            }

            if (uiState.commandStepPoints.isNotEmpty()) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(OpaqueEmeraldBadgeBg)
                        .border(1.dp, CadEmeraldCrossing, RoundedCornerShape(5.dp))
                        .clickable(onClick = onFinishCommand)
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                        .testTag("active_cmd_finish_button")
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Finish Command",
                        tint = CadEmeraldCrossing,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        // QDIM Contextual Controls — Section 2.4 anchor-origin enter/exit transition
        AnimatedVisibility(
            visible = isQDimCommand,
            enter = slideInVertically(animationSpec = tween(200)) { it / 2 } +
                expandVertically(animationSpec = tween(200), expandFrom = Alignment.Bottom) +
                fadeIn(animationSpec = tween(180)),
            exit = slideOutVertically(animationSpec = tween(190)) { it / 2 } +
                shrinkVertically(animationSpec = tween(190), shrinkTowards = Alignment.Bottom) +
                fadeOut(animationSpec = tween(170))
        ) {
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

        // Sub-Options & Manual Numeric Parameter Controls — Section 2.4 anchor-origin enter/exit transition
        AnimatedVisibility(
            visible = subOptions.isNotEmpty() || needsNumericParam,
            enter = slideInVertically(animationSpec = tween(200)) { it / 2 } +
                expandVertically(animationSpec = tween(200), expandFrom = Alignment.Bottom) +
                fadeIn(animationSpec = tween(180)),
            exit = slideOutVertically(animationSpec = tween(190)) { it / 2 } +
                shrinkVertically(animationSpec = tween(190), shrinkTowards = Alignment.Bottom) +
                fadeOut(animationSpec = tween(170))
        ) {
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

// --- Section 62: First-Run Home Screen (New Drawing, Open Drawing .dxf, Saved Drawings) ---

@Composable
private fun CadHomeScreen(
    savedProjects: List<CadProjectMetadataEntity>,
    displayUnit: CadUnit,
    updatesBehindCount: Int,
    updateUiState: CadUpdateUiState,
    onOpenReleaseCatalogue: () -> Unit,
    onNewBlankDrawing: (String, CadUnit) -> Unit,
    onOpenDxfFilePicker: () -> Unit,
    onImportDxfContent: (String) -> Unit,
    onOpenSavedProject: (String) -> Unit,
    onRenameSavedProject: (String, String) -> Unit,
    onDeleteSavedProject: (String) -> Unit
) {
    var newDrawingName by rememberSaveable { mutableStateOf("Untitled Drawing") }
    var selectedUnitName by rememberSaveable { mutableStateOf(displayUnit.name) }
    val selectedUnit = remember(selectedUnitName) {
        CadUnit.entries.firstOrNull { it.name == selectedUnitName } ?: CadUnit.MM
    }
    var showInlineDxfImport by rememberSaveable { mutableStateOf(false) }
    var dxfImportBuffer by rememberSaveable { mutableStateOf("") }
    var renamingProjectId by rememberSaveable { mutableStateOf<String?>(null) }
    var renameText by rememberSaveable { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CadModelSpaceBg)
            .windowInsetsPadding(WindowInsets.displayCutout)
            .imePadding()
            .testTag("cad_home_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 620.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // App Branding Header + Top-Right Home Update Status & Release Catalogue Icon (Phase 12)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CadBorderStrong, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                color = CadPanelSurface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "PRECISIONCAD STUDIO",
                            style = MaterialTheme.typography.titleMedium,
                            color = CadCyanPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "2D Engineering CAD Workspace • Exact Double-Precision Geometry • DXF / PDF / SVG",
                            style = MaterialTheme.typography.bodySmall,
                            color = CadTextSecondary
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    HomeUpdateStatusIcon(
                        updatesBehindCount = updatesBehindCount,
                        updateUiState = updateUiState,
                        onClick = onOpenReleaseCatalogue
                    )
                }
            }

            // Primary Actions Card: New Drawing & Open Drawing (.dxf)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CadCyanPrimary, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                color = CadSurfaceCard
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "START OR OPEN DRAWING",
                        style = MaterialTheme.typography.labelLarge,
                        color = CadAmberSnap,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                onNewBlankDrawing(
                                    newDrawingName.trim().ifEmpty { "Untitled Drawing" },
                                    selectedUnit
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("home_new_drawing_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CadCyanPrimary,
                                contentColor = Color(0xFF001824)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("New Drawing", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = onOpenDxfFilePicker,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("home_open_drawing_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CadPanelElevated,
                                contentColor = CadTextPrimary
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, tint = CadAmberSnap, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open Drawing (.dxf)", fontWeight = FontWeight.Bold)
                        }
                    }

                    // Optional Blank Drawing Setup (Title + Working Unit)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newDrawingName,
                            onValueChange = { newDrawingName = it },
                            label = { Text("New Drawing Title") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("home_new_drawing_name_field")
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Units:",
                            style = MaterialTheme.typography.labelSmall,
                            color = CadTextSecondary,
                            fontWeight = FontWeight.Bold
                        )
                        CadUnit.entries.forEach { unit ->
                            val isSel = selectedUnit == unit
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSel) OpaqueCyanBadgeBg else CadPanelElevated)
                                    .border(
                                        1.dp,
                                        if (isSel) CadCyanPrimary else CadBorderHairline,
                                        RoundedCornerShape(6.dp)
                                    )
                                    .clickable { selectedUnitName = unit.name }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .testTag("home_unit_chip_${unit.symbol}")
                            ) {
                                Text(
                                    text = "${unit.label} (${unit.symbol})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSel) CadCyanPrimary else CadTextSecondary,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(CadPanelElevated)
                                .border(1.dp, CadBorderHairline, RoundedCornerShape(6.dp))
                                .clickable { showInlineDxfImport = !showInlineDxfImport }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("home_import_dxf_toggle_button")
                        ) {
                            Text(
                                text = if (showInlineDxfImport) "Hide DXF Paste" else "Paste DXF Text",
                                style = MaterialTheme.typography.labelSmall,
                                color = CadAmberSnap,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (showInlineDxfImport) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedTextField(
                                value = dxfImportBuffer,
                                onValueChange = { dxfImportBuffer = it },
                                label = { Text("Paste ASCII DXF Content") },
                                minLines = 3,
                                maxLines = 5,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("home_dxf_paste_field")
                            )
                            Button(
                                onClick = {
                                    if (dxfImportBuffer.isNotBlank()) {
                                        onImportDxfContent(dxfImportBuffer)
                                    }
                                },
                                enabled = dxfImportBuffer.isNotBlank(),
                                modifier = Modifier.testTag("home_dxf_paste_submit_button")
                            ) {
                                Text("Load Pasted DXF")
                            }
                        }
                    }
                }
            }

            // Saved Drawings Catalogue Section (Section 62.2 & 62.3)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CadBorderHairline, RoundedCornerShape(12.dp))
                    .testTag("home_saved_drawings_section"),
                shape = RoundedCornerShape(12.dp),
                color = CadPanelSurface
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "SAVED DRAWINGS",
                        style = MaterialTheme.typography.labelLarge,
                        color = CadCyanPrimary,
                        fontWeight = FontWeight.Bold
                    )

                    if (savedProjects.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(CadModelSpaceBg)
                                .border(1.dp, CadBorderHairline, RoundedCornerShape(8.dp))
                                .padding(18.dp)
                                .testTag("home_empty_saved_drawings"),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = CadTextMuted,
                                modifier = Modifier.size(26.dp)
                            )
                            Text(
                                text = "No saved drawings yet",
                                style = MaterialTheme.typography.titleSmall,
                                color = CadTextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Create a new drawing or open a DXF file to get started.",
                                style = MaterialTheme.typography.bodySmall,
                                color = CadTextSecondary
                            )
                        }
                    } else {
                        savedProjects.forEach { proj ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, CadBorderHairline, RoundedCornerShape(8.dp))
                                    .testTag("home_saved_drawing_${proj.projectId}"),
                                shape = RoundedCornerShape(8.dp),
                                color = CadPanelElevated
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable { onOpenSavedProject(proj.projectId) }
                                        ) {
                                            Text(
                                                text = proj.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = CadTextPrimary,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "${proj.entityCount} entities • Unit: ${proj.workingUnitSymbol} • Sheet: ${proj.sheetPresetName}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = CadTextSecondary
                                            )
                                        }

                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Button(
                                                onClick = { onOpenSavedProject(proj.projectId) },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                modifier = Modifier
                                                    .height(32.dp)
                                                    .testTag("home_open_saved_${proj.projectId}")
                                            ) {
                                                Text("Open", style = MaterialTheme.typography.labelSmall)
                                            }
                                            IconButton(
                                                onClick = {
                                                    if (renamingProjectId == proj.projectId) {
                                                        renamingProjectId = null
                                                    } else {
                                                        renamingProjectId = proj.projectId
                                                        renameText = proj.name
                                                    }
                                                },
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .testTag("home_rename_saved_${proj.projectId}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Edit,
                                                    contentDescription = "Rename Drawing",
                                                    tint = CadCyanPrimary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            IconButton(
                                                onClick = { onDeleteSavedProject(proj.projectId) },
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .testTag("home_delete_saved_${proj.projectId}")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete Drawing",
                                                    tint = CadRedAlert,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }

                                    if (renamingProjectId == proj.projectId) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OutlinedTextField(
                                                value = renameText,
                                                onValueChange = { renameText = it },
                                                label = { Text("Rename Drawing") },
                                                singleLine = true,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("home_rename_input_${proj.projectId}")
                                            )
                                            Button(
                                                onClick = {
                                                    if (renameText.isNotBlank()) {
                                                        onRenameSavedProject(proj.projectId, renameText.trim())
                                                        renamingProjectId = null
                                                    }
                                                },
                                                modifier = Modifier.testTag("home_rename_confirm_${proj.projectId}")
                                            ) {
                                                Text("Save")
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
    }
}

