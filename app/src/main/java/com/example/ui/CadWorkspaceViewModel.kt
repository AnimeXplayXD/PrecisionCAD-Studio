package com.example.ui

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cad.data.CadAppDatabase
import com.example.cad.data.CadProjectMetadataEntity
import com.example.cad.data.CadProjectRepository
import com.example.cad.dimension.AutoDimensionEngine
import com.example.cad.dimension.DimensionAlignmentReport
import com.example.cad.dimension.DimensionArrangementPreference
import com.example.cad.dimension.DimensionStyleConfig
import com.example.cad.dimension.QDimDensityPolicy
import com.example.cad.dimension.QDimOperatingMode
import com.example.cad.io.CadDxfInterchange
import com.example.cad.io.CadPcadPersistence
import com.example.cad.io.DxfDocumentPayload
import com.example.cad.io.PcadProjectDocument
import com.example.cad.kernel.CadGeometryKernel
import com.example.cad.kernel.CadModificationEngine
import com.example.cad.kernel.CadSnapAndCoordEngine
import com.example.cad.kernel.FitDrawingPolicy
import com.example.cad.kernel.PolarTrackingPreviewState
import com.example.cad.kernel.SnapCandidate
import com.example.cad.kernel.UsableViewportRect
import com.example.cad.kernel.ViewportTransform
import com.example.cad.model.BlockDefinition
import com.example.cad.model.BoundingBox2D
import com.example.cad.model.CadCommandCategory
import com.example.cad.model.CadCommandId
import com.example.cad.model.CadEntity
import com.example.cad.model.CadLayer
import com.example.cad.model.CadLineType
import com.example.cad.model.CadUnit
import com.example.cad.model.ConstraintType
import com.example.cad.model.CursorControllerConfig
import com.example.cad.model.DimensionType
import com.example.cad.model.GeometricConstraintRecord
import com.example.cad.model.GridAndSnapConfig
import com.example.cad.model.HatchPattern
import com.example.cad.model.Point2D
import com.example.cad.model.PolylineVertex
import com.example.cad.model.PrecisionFormatConfig
import com.example.cad.model.SheetSetupConfig
import com.example.cad.model.UnitChangeMode
import com.example.cad.model.VirtualControllerMode
import com.example.cad.model.WorkspaceSpace
import com.example.cad.update.CadGitHubUpdateRepository
import com.example.cad.update.CadReleaseEntry
import com.example.cad.update.CadUpdateUiState
import com.example.ui.components.ControllerDockState
import com.example.ui.components.SelectionBoxDragState
import com.example.ui.components.ViewportGestureState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

enum class ToolDrawerState {
    COLLAPSED,
    EXPANDED
}

sealed interface CommandPanelState {
    data object Hidden : CommandPanelState
    data class Editing(val query: String) : CommandPanelState
    data class Running(
        val command: CadCommandId,
        val prompt: String,
        val stepIndex: Int
    ) : CommandPanelState
}

enum class NotificationCategory {
    INFO,
    SUCCESS,
    WARNING,
    ERROR
}

data class TransientNotification(
    val id: String = UUID.randomUUID().toString(),
    val message: String,
    val category: NotificationCategory = NotificationCategory.INFO,
    val isPersistentError: Boolean = false,
    val timestampMs: Long = System.currentTimeMillis()
)

data class OverviewCardEvent(
    val id: Long,
    val title: String,
    val explanation: String,
    val category: NotificationCategory = NotificationCategory.INFO,
    val isPersistentSession: Boolean = false
)

data class DocumentSnapshot(
    val entities: List<CadEntity>,
    val layers: List<CadLayer>,
    val blocks: Map<String, BlockDefinition>,
    val storageUnit: CadUnit,
    val displayUnit: CadUnit,
    val unitChangeMode: UnitChangeMode = UnitChangeMode.CONVERT_DISPLAY_UNITS,
    val sheetSetup: SheetSetupConfig = SheetSetupConfig(),
    val dimensionStyle: DimensionStyleConfig = DimensionStyleConfig(),
    val isQuickDimensionEnabled: Boolean = false,
    val revision: Long = 0L
)

sealed interface PendingHomeReplacementAction {
    val actionSummaryLabel: String

    data class NewBlankDrawing(
        val name: String,
        val unit: CadUnit
    ) : PendingHomeReplacementAction {
        override val actionSummaryLabel: String
            get() = "Create New Blank Drawing ('$name', ${unit.symbol})"
    }

    data class OpenSavedProject(
        val projectId: String,
        val projectName: String = projectId
    ) : PendingHomeReplacementAction {
        override val actionSummaryLabel: String
            get() = "Open Saved Drawing ('$projectName')"
    }

    data object OpenDxfOrPcadPicker : PendingHomeReplacementAction {
        override val actionSummaryLabel: String
            get() = "Open DXF / PCAD File"
    }

    data class ImportDxfOrPcadText(
        val content: String
    ) : PendingHomeReplacementAction {
        override val actionSummaryLabel: String
            get() = "Import Pasted DXF / PCAD Content"
    }
}

data class CadWorkspaceUiState(
    val isHomeScreenVisible: Boolean = true,
    val hasActiveWorkspaceSession: Boolean = false,
    val documentRevision: Long = 0L,
    val lastExplicitSaveRevision: Long = 0L,
    val pendingCommandLineInput: String = "",
    val pendingHomeReplacementAction: PendingHomeReplacementAction? = null,
    val shouldLaunchHomeDxfPicker: Boolean = false,
    val currentProjectId: String = "untitled_drawing",
    val currentProjectName: String = "Untitled Drawing",
    val workspaceSpace: WorkspaceSpace = WorkspaceSpace.MODEL_SPACE,
    val storageUnit: CadUnit = CadUnit.MM,
    val displayUnit: CadUnit = CadUnit.MM,
    val unitChangeMode: UnitChangeMode = UnitChangeMode.CONVERT_DISPLAY_UNITS,
    val precision: PrecisionFormatConfig = PrecisionFormatConfig(linearDecimals = 2, angularDecimals = 1),
    val sheetSetup: SheetSetupConfig = SheetSetupConfig(),
    val dimensionStyle: DimensionStyleConfig = DimensionStyleConfig(),
    val gridSnapConfig: GridAndSnapConfig = GridAndSnapConfig(),
    val controllerConfig: CursorControllerConfig = CursorControllerConfig(),
    val toolDrawerState: ToolDrawerState = ToolDrawerState.COLLAPSED,
    val expandedToolSections: Set<CadCommandCategory> = setOf(CadCommandCategory.DRAW),
    val commandPanelState: CommandPanelState = CommandPanelState.Hidden,
    val controllerDockState: ControllerDockState = ControllerDockState.EXPANDED_CONTROLLER,
    val viewportGestureState: ViewportGestureState = ViewportGestureState.IDLE,
    val activeCategory: CadCommandCategory = CadCommandCategory.DRAW,
    val activeCommand: CadCommandId = CadCommandId.SELECT,
    val commandSubOption: String = "Default",
    val commandParameterValue: Double = 5.0,
    val commandStepPoints: List<Point2D> = emptyList(),
    val activeActionPrompt: String = "Position cursor over object and tap PICK PT",
    val commandPromptBanner: String = "READY • Use controller PICK PT, command bar, or + Launcher",
    val activeNotification: TransientNotification? = null,
    val notificationQueue: List<TransientNotification> = emptyList(),
    val activeOverview: OverviewCardEvent? = null,
    val commandHistoryLog: List<String> = listOf(
        "PrecisionCAD Studio initialized (2D Technical Viewport)."
    ),
    val recentCommands: List<CadCommandId> = listOf(
        CadCommandId.LINE,
        CadCommandId.POLYLINE,
        CadCommandId.CIRCLE,
        CadCommandId.RECTANGLE,
        CadCommandId.FILLET,
        CadCommandId.TRIM,
        CadCommandId.QDIM,
        CadCommandId.MEASURE
    ),
    val layers: List<CadLayer> = CadLayer.defaultLayers(),
    val activeLayerName: String = "0",
    val entities: List<CadEntity> = emptyList(),
    val blocks: Map<String, BlockDefinition> = emptyMap(),
    val selectedEntityIds: Set<String> = emptySet(),
    val previousSelectedEntityIds: Set<String> = emptySet(),
    val isolatedEntityIds: Set<String>? = null,
    val rawCursorModelPos: Point2D = Point2D(100.0, 150.0),
    val snappedCursorModelPos: Point2D = Point2D(100.0, 150.0),
    val activeSnapCandidate: SnapCandidate? = null,
    val polarPreview: PolarTrackingPreviewState? = null,
    val selectionBoxDrag: SelectionBoxDragState? = null,
    val viewport: ViewportTransform = ViewportTransform(),
    val viewportWidthPx: Float = 800f,
    val viewportHeightPx: Float = 600f,
    val usableViewportRect: UsableViewportRect = UsableViewportRect(0f, 0f, 800f, 600f),
    val referencePixelsPerModelUnit: Double? = null,
    val effectiveGridSnapInterval: Double = 5.0,
    val fitDrawingPolicy: FitDrawingPolicy = FitDrawingPolicy.ALL_VISIBLE,
    val isQuickDimensionEnabled: Boolean = false,
    val qdimDensity: QDimDensityPolicy = QDimDensityPolicy.BALANCED,
    val qdimArrangementPreference: DimensionArrangementPreference = DimensionArrangementPreference.AUTO_CHAIN,
    val lastAlignmentReport: DimensionAlignmentReport? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val isReleaseCatalogueOpen: Boolean = false
) {
    val isActiveCommandSession: Boolean
        get() = commandPanelState is CommandPanelState.Running ||
            activeCommand != CadCommandId.SELECT ||
            commandStepPoints.isNotEmpty() ||
            selectionBoxDrag != null

    val isDocumentDirty: Boolean
        get() = hasActiveWorkspaceSession && documentRevision != lastExplicitSaveRevision
}

/**
 * Orientation-safe StateFlow ViewModel preserving all drawing state, cursor coordinates,
 * active command steps, selection sets, zoom/pan, and undo/redo history across Portrait/Landscape rotation.
 */
class CadWorkspaceViewModel(application: Application) : AndroidViewModel(application) {

    private val database = CadAppDatabase.getInstance(application)
    private val repository = CadProjectRepository(application, database.projectDao())
    val updateRepository = CadGitHubUpdateRepository(application, viewModelScope)

    private val prefs = application.getSharedPreferences("precision_cad_prefs", Context.MODE_PRIVATE)

    val savedProjects: StateFlow<List<CadProjectMetadataEntity>> = repository.projectsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val releaseCatalogue: StateFlow<List<CadReleaseEntry>> = updateRepository.releases
    val updatesBehindCount: StateFlow<Int> = updateRepository.updatesBehindCount
    val updateUiState: StateFlow<CadUpdateUiState> = updateRepository.uiState

    private val _uiState = MutableStateFlow(createInitialState())
    val uiState: StateFlow<CadWorkspaceUiState> = _uiState.asStateFlow()

    private val undoStack = ArrayDeque<DocumentSnapshot>()
    private val redoStack = ArrayDeque<DocumentSnapshot>()
    private var notificationDismissJob: Job? = null
    private var overviewDismissJob: Job? = null
    private var autoQuickSaveJob: Job? = null
    private var overviewSeq: Long = 1L
    private var nextRevisionCounter: Long = 1L

    var lastQuickSaveFile: File? = null
        private set
    var lastExplicitSavedPcadFile: File? = null
        private set

    var simulateExplicitSaveFailureForTesting: Boolean = false

    init {
        viewModelScope.launch {
            repository.cleanupUntouchedLegacyTemplatesOnce()
        }
        updateRepository.checkForUpdates(manual = false)
    }

    fun openReleaseCatalogue() {
        _uiState.update { it.copy(isReleaseCatalogueOpen = true) }
        updateRepository.checkForUpdates(manual = false)
    }

    fun closeReleaseCatalogue() {
        _uiState.update { it.copy(isReleaseCatalogueOpen = false) }
    }

    fun refreshReleaseCatalogue() {
        updateRepository.checkForUpdates(manual = true)
    }

    fun startUpdateDownload(release: CadReleaseEntry) {
        updateRepository.downloadAndVerifyRelease(release)
    }

    fun requestInstallVerifiedUpdate() {
        updateRepository.requestInstallOrExecuteSession(getApplication())
    }

    fun cancelUpdateAction() {
        updateRepository.cancelOrReset()
    }

    fun onHostActivityResumed() {
        updateRepository.onHostResumed(getApplication())
    }

    fun buildCurrentPcadDocument(projectNameOverride: String? = null): PcadProjectDocument {
        val st = _uiState.value
        val effectiveName = (projectNameOverride ?: st.currentProjectName).trim().ifEmpty { "Untitled Drawing" }
        val canonicalTitle = CadPcadPersistence.canonicalBasename(effectiveName)
        return PcadProjectDocument(
            schemaVersion = CadPcadPersistence.CURRENT_SCHEMA_VERSION,
            projectId = st.currentProjectId,
            projectName = canonicalTitle,
            savedAtEpochMs = System.currentTimeMillis(),
            storageUnit = st.storageUnit,
            displayUnit = st.displayUnit,
            unitChangeMode = st.unitChangeMode,
            precision = st.precision,
            sheetSetup = st.sheetSetup.copy(titleBlockTitle = canonicalTitle),
            dimensionStyle = st.dimensionStyle,
            gridSnapConfig = st.gridSnapConfig,
            controllerConfig = st.controllerConfig,
            activeLayerName = st.activeLayerName,
            layers = st.layers,
            blocks = st.blocks.values.toList(),
            entities = st.entities,
            isQuickDimensionEnabled = st.isQuickDimensionEnabled,
            qdimDensity = st.qdimDensity,
            qdimArrangementPreference = st.qdimArrangementPreference
        )
    }

    fun triggerAutomaticQuickSave(immediate: Boolean = false) {
        autoQuickSaveJob?.cancel()
        if (immediate) {
            val doc = buildCurrentPcadDocument()
            lastQuickSaveFile = CadPcadPersistence.performAutomaticQuickSave(getApplication(), doc)
        } else {
            autoQuickSaveJob = viewModelScope.launch {
                delay(250L)
                val doc = buildCurrentPcadDocument()
                lastQuickSaveFile = CadPcadPersistence.performAutomaticQuickSave(getApplication(), doc)
            }
        }
    }

    private fun createInitialState(): CadWorkspaceUiState {
        val savedModeName = prefs.getString("unit_change_mode", UnitChangeMode.CONVERT_DISPLAY_UNITS.name)
        val initialMode = UnitChangeMode.entries.firstOrNull { it.name == savedModeName }
            ?: UnitChangeMode.CONVERT_DISPLAY_UNITS
        val savedDisplayUnitName = prefs.getString("display_unit", CadUnit.MM.name)
        val initialDisplayUnit = CadUnit.entries.firstOrNull { it.name == savedDisplayUnitName }
            ?: CadUnit.MM
        val savedLinearDecimals = prefs.getInt("linear_decimals", 2)
        val savedAngularDecimals = prefs.getInt("angular_decimals", 1)
        val savedShowSuffix = prefs.getBoolean("show_unit_suffix", false)
        val savedSuppressZeros = prefs.getBoolean("suppress_trailing_zeros", false)

        return CadWorkspaceUiState(
            isHomeScreenVisible = true,
            currentProjectId = "untitled_drawing",
            currentProjectName = "Untitled Drawing",
            storageUnit = initialDisplayUnit,
            displayUnit = initialDisplayUnit,
            unitChangeMode = initialMode,
            layers = CadLayer.defaultLayers(),
            entities = emptyList(),
            blocks = emptyMap(),
            sheetSetup = SheetSetupConfig(),
            precision = PrecisionFormatConfig(
                linearDecimals = savedLinearDecimals,
                angularDecimals = savedAngularDecimals,
                showUnitSuffix = savedShowSuffix,
                suppressTrailingZeros = savedSuppressZeros
            ),
            isQuickDimensionEnabled = false
        )
    }

    // --- Section 62 & V18.2 Requirement C: Home Screen, Return-to-Canvas & Unsaved Session Protection ---

    fun openHomeScreen() {
        val st = _uiState.value
        if (st.hasActiveWorkspaceSession) {
            // Flush an immediate automatic quick-save / checkpoint so no in-progress work is lost,
            // without marking an unsaved drawing as explicitly saved (lastExplicitSaveRevision unchanged).
            triggerAutomaticQuickSave(immediate = true)
        }
        _uiState.update { it.copy(isHomeScreenVisible = true) }
    }

    fun enterWorkspace() {
        _uiState.update {
            it.copy(
                isHomeScreenVisible = false,
                hasActiveWorkspaceSession = true,
                pendingHomeReplacementAction = null
            )
        }
    }

    fun returnToActiveWorkspace() {
        val st = _uiState.value
        if (!st.hasActiveWorkspaceSession) return
        _uiState.update {
            it.copy(
                isHomeScreenVisible = false,
                pendingHomeReplacementAction = null
            )
        }
    }

    fun updatePendingCommandLineInput(input: String) {
        _uiState.update { it.copy(pendingCommandLineInput = input) }
    }

    fun consumeHomeDxfPickerLaunchRequest() {
        _uiState.update { it.copy(shouldLaunchHomeDxfPicker = false) }
    }

    fun requestNewBlankDrawingFromHome(
        name: String = "Untitled Drawing",
        unit: CadUnit = _uiState.value.displayUnit
    ) {
        val st = _uiState.value
        val action = PendingHomeReplacementAction.NewBlankDrawing(name = name, unit = unit)
        if (st.hasActiveWorkspaceSession && st.isDocumentDirty) {
            _uiState.update { it.copy(pendingHomeReplacementAction = action) }
        } else {
            executeHomeReplacementAction(action)
        }
    }

    fun requestOpenSavedProjectFromHome(
        projectId: String,
        projectName: String = projectId
    ) {
        val st = _uiState.value
        val resolvedName = savedProjects.value.firstOrNull { it.projectId == projectId }?.name ?: projectName
        val action = PendingHomeReplacementAction.OpenSavedProject(projectId = projectId, projectName = resolvedName)
        if (st.hasActiveWorkspaceSession && st.isDocumentDirty) {
            _uiState.update { it.copy(pendingHomeReplacementAction = action) }
        } else {
            executeHomeReplacementAction(action)
        }
    }

    fun requestOpenDxfPickerFromHome() {
        val st = _uiState.value
        val action = PendingHomeReplacementAction.OpenDxfOrPcadPicker
        if (st.hasActiveWorkspaceSession && st.isDocumentDirty) {
            _uiState.update { it.copy(pendingHomeReplacementAction = action) }
        } else {
            executeHomeReplacementAction(action)
        }
    }

    fun requestImportDxfTextFromHome(content: String) {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return
        val st = _uiState.value
        val action = PendingHomeReplacementAction.ImportDxfOrPcadText(content = trimmed)
        if (st.hasActiveWorkspaceSession && st.isDocumentDirty) {
            _uiState.update { it.copy(pendingHomeReplacementAction = action) }
        } else {
            executeHomeReplacementAction(action)
        }
    }

    fun confirmSaveAndContinuePendingHomeAction(): Boolean {
        val pending = _uiState.value.pendingHomeReplacementAction ?: return false
        val savedOk = saveCurrentProject(_uiState.value.currentProjectName)
        if (!savedOk) {
            // Save failed: keep the user on the current session and surface an error notification
            // without replacing the active drawing.
            return false
        }
        _uiState.update { it.copy(pendingHomeReplacementAction = null) }
        executeHomeReplacementAction(pending)
        return true
    }

    fun confirmDiscardAndContinuePendingHomeAction() {
        val pending = _uiState.value.pendingHomeReplacementAction ?: return
        _uiState.update { it.copy(pendingHomeReplacementAction = null) }
        executeHomeReplacementAction(pending)
    }

    fun dismissPendingHomeAction() {
        _uiState.update { it.copy(pendingHomeReplacementAction = null) }
    }

    private fun executeHomeReplacementAction(action: PendingHomeReplacementAction) {
        when (action) {
            is PendingHomeReplacementAction.NewBlankDrawing -> {
                createNewBlankProject(name = action.name, unit = action.unit)
            }
            is PendingHomeReplacementAction.OpenSavedProject -> {
                loadProject(action.projectId)
            }
            PendingHomeReplacementAction.OpenDxfOrPcadPicker -> {
                _uiState.update { it.copy(shouldLaunchHomeDxfPicker = true) }
            }
            is PendingHomeReplacementAction.ImportDxfOrPcadText -> {
                importDxfString(action.content)
            }
        }
    }

    fun startNewBlankDrawing(
        name: String = "Untitled Drawing",
        unit: CadUnit = _uiState.value.displayUnit
    ) {
        createNewBlankProject(name = name, unit = unit)
    }

    fun renameSavedProject(projectId: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repository.renameProject(projectId, trimmed)
            _uiState.update { st ->
                if (st.currentProjectId == projectId) {
                    st.copy(currentProjectName = trimmed)
                } else {
                    st
                }
            }
            emitTransientNotification("PROJECT • Renamed drawing to '$trimmed'", NotificationCategory.SUCCESS)
        }
    }

    fun deleteSavedProject(projectId: String) {
        viewModelScope.launch {
            repository.deleteProject(projectId)
            emitTransientNotification("PROJECT • Deleted saved drawing", NotificationCategory.INFO)
        }
    }

    // --- Two-Layer Notification & Active-Session Floating Overview System (Sections 4, 30, 39.2 & 42.5) ---

    fun emitOverview(
        title: String,
        explanation: String,
        category: NotificationCategory = NotificationCategory.INFO,
        isPersistentSession: Boolean = false
    ) {
        val event = OverviewCardEvent(
            id = overviewSeq++,
            title = title.trim(),
            explanation = explanation.trim(),
            category = category,
            isPersistentSession = isPersistentSession
        )
        _uiState.update { it.copy(activeOverview = event) }
        overviewDismissJob?.cancel()
        if (!isPersistentSession) {
            overviewDismissJob = viewModelScope.launch {
                delay(2500L)
                _uiState.update { st ->
                    if (st.activeOverview?.id == event.id && !st.isActiveCommandSession) {
                        st.copy(activeOverview = null)
                    } else {
                        st
                    }
                }
            }
        }
    }

    fun dismissOverview() {
        overviewDismissJob?.cancel()
        if (!_uiState.value.isActiveCommandSession) {
            _uiState.update { it.copy(activeOverview = null) }
        }
    }

    /**
     * Section 39.2 & 42.5 — Two-Layer Notification Routing:
     * 1. When NO active command session occupies the bottom Overview (`!current.isActiveCommandSession`):
     *    Ordinary guidance/status messages appear ONLY in the bottom floating `OverviewOverlayCard`
     *    (fade in, hold ~2.5s, fade out), and the top notification lane stays hidden (`activeNotification = null`).
     * 2. When the bottom Overview IS occupied by an active command/session (`current.isActiveCommandSession`):
     *    The bottom active-session Overview persists undisturbed, and the additional event is routed to
     *    the top notification banner (`activeNotification`) with smooth replacement and 2.5s auto-dismiss.
     */
    fun emitTransientNotification(
        message: String,
        category: NotificationCategory = NotificationCategory.INFO,
        isPersistentError: Boolean = false
    ) {
        val current = _uiState.value
        val parts = message.split(" • ", limit = 2)
        val overviewTitle = if (parts.size == 2) parts[0] else "CAD OPERATION"
        val overviewBody = if (parts.size == 2) parts[1] else message

        if (!current.isActiveCommandSession && !isPersistentError) {
            // Idle / Non-Session state: use ONLY the bottom floating Overview card
            notificationDismissJob?.cancel()
            _uiState.update { it.copy(activeNotification = null, notificationQueue = emptyList()) }
            emitOverview(overviewTitle, overviewBody, category, isPersistentSession = false)
            return
        }

        // Active-session state (or persistent error): keep active-session Overview intact at the bottom
        // and route the secondary/overflow notice to the top notification banner.
        if (current.isActiveCommandSession) {
            emitOverview(
                title = current.activeCommand.commandName,
                explanation = current.commandPromptBanner,
                category = NotificationCategory.INFO,
                isPersistentSession = true
            )
        }

        val active = current.activeNotification
        if (active != null && active.message == message) {
            val refreshed = active.copy(timestampMs = System.currentTimeMillis(), isPersistentError = isPersistentError)
            _uiState.update { it.copy(activeNotification = refreshed) }
            scheduleNotificationAutoDismiss(refreshed)
            return
        }

        val incoming = TransientNotification(
            message = message,
            category = category,
            isPersistentError = isPersistentError
        )
        if (active == null) {
            _uiState.update { it.copy(activeNotification = incoming) }
            scheduleNotificationAutoDismiss(incoming)
        } else if (!active.isPersistentError) {
            _uiState.update {
                it.copy(
                    activeNotification = incoming,
                    notificationQueue = it.notificationQueue.filterNot { q -> q.message == message }.takeLast(2)
                )
            }
            scheduleNotificationAutoDismiss(incoming)
        } else {
            _uiState.update {
                val nextQ = (it.notificationQueue.filterNot { q -> q.message == message } + incoming).takeLast(3)
                it.copy(notificationQueue = nextQ)
            }
        }
    }

    private fun scheduleNotificationAutoDismiss(notification: TransientNotification) {
        notificationDismissJob?.cancel()
        if (notification.isPersistentError) return
        notificationDismissJob = viewModelScope.launch {
            delay(2500L)
            advanceNotificationQueue()
        }
    }

    fun dismissNotification() {
        notificationDismissJob?.cancel()
        advanceNotificationQueue()
    }

    private fun advanceNotificationQueue() {
        val st = _uiState.value
        val next = st.notificationQueue.firstOrNull()
        val remaining = if (st.notificationQueue.isNotEmpty()) st.notificationQueue.drop(1) else emptyList()
        _uiState.update {
            it.copy(
                activeNotification = next,
                notificationQueue = remaining
            )
        }
        if (next != null) {
            scheduleNotificationAutoDismiss(next)
        }
    }

    // --- Undo / Redo ---

    private fun recordUndoSnapshot() {
        val st = _uiState.value
        undoStack.addLast(
            DocumentSnapshot(
                entities = st.entities,
                layers = st.layers,
                blocks = st.blocks,
                storageUnit = st.storageUnit,
                displayUnit = st.displayUnit,
                unitChangeMode = st.unitChangeMode,
                sheetSetup = st.sheetSetup,
                dimensionStyle = st.dimensionStyle,
                isQuickDimensionEnabled = st.isQuickDimensionEnabled,
                revision = st.documentRevision
            )
        )
        if (undoStack.size > 80) undoStack.removeFirst()
        redoStack.clear()
        val nextRev = nextRevisionCounter++
        _uiState.update {
            it.copy(
                hasActiveWorkspaceSession = true,
                documentRevision = nextRev,
                canUndo = undoStack.isNotEmpty(),
                canRedo = false
            )
        }
        triggerAutomaticQuickSave()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val current = _uiState.value
        redoStack.addLast(
            DocumentSnapshot(
                entities = current.entities,
                layers = current.layers,
                blocks = current.blocks,
                storageUnit = current.storageUnit,
                displayUnit = current.displayUnit,
                unitChangeMode = current.unitChangeMode,
                sheetSetup = current.sheetSetup,
                dimensionStyle = current.dimensionStyle,
                isQuickDimensionEnabled = current.isQuickDimensionEnabled,
                revision = current.documentRevision
            )
        )
        val prev = undoStack.removeLast()
        val msg = "UNDO • Restored previous document state (${prev.entities.size} entities)"
        _uiState.update {
            it.copy(
                entities = prev.entities,
                layers = prev.layers,
                blocks = prev.blocks,
                storageUnit = prev.storageUnit,
                displayUnit = prev.displayUnit,
                unitChangeMode = prev.unitChangeMode,
                sheetSetup = prev.sheetSetup,
                dimensionStyle = prev.dimensionStyle,
                isQuickDimensionEnabled = prev.isQuickDimensionEnabled,
                documentRevision = prev.revision,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
                commandPromptBanner = msg
            )
        }
        triggerAutomaticQuickSave()
        emitTransientNotification(msg, NotificationCategory.INFO)
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val current = _uiState.value
        undoStack.addLast(
            DocumentSnapshot(
                entities = current.entities,
                layers = current.layers,
                blocks = current.blocks,
                storageUnit = current.storageUnit,
                displayUnit = current.displayUnit,
                unitChangeMode = current.unitChangeMode,
                sheetSetup = current.sheetSetup,
                dimensionStyle = current.dimensionStyle,
                isQuickDimensionEnabled = current.isQuickDimensionEnabled,
                revision = current.documentRevision
            )
        )
        val next = redoStack.removeLast()
        val msg = "REDO • Restored state (${next.entities.size} entities)"
        _uiState.update {
            it.copy(
                entities = next.entities,
                layers = next.layers,
                blocks = next.blocks,
                storageUnit = next.storageUnit,
                displayUnit = next.displayUnit,
                unitChangeMode = next.unitChangeMode,
                sheetSetup = next.sheetSetup,
                dimensionStyle = next.dimensionStyle,
                isQuickDimensionEnabled = next.isQuickDimensionEnabled,
                documentRevision = next.revision,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
                commandPromptBanner = msg
            )
        }
        triggerAutomaticQuickSave()
        emitTransientNotification(msg, NotificationCategory.INFO)
    }

    // --- Cursor Controller, Automatic Boundary Tracking (Section 17) & Coordinate Input ---

    fun moveCursorByDelta(dxModel: Double, dyModel: Double) {
        val st = _uiState.value
        val newRaw = Point2D(
            x = (st.rawCursorModelPos.x + dxModel).coerceIn(-50000.0, 50000.0),
            y = (st.rawCursorModelPos.y + dyModel).coerceIn(-50000.0, 50000.0)
        )
        updateCursorPositionInternal(newRaw)
    }

    fun stepNudgeCursor(dxDisplayUnits: Double, dyDisplayUnits: Double) {
        val st = _uiState.value
        val displayToStorage = st.displayUnit.toMillimetersFactor / st.storageUnit.toMillimetersFactor
        val base = st.snappedCursorModelPos
        val target = Point2D(
            x = base.x + dxDisplayUnits * displayToStorage,
            y = base.y + dyDisplayUnits * displayToStorage
        )
        val refPt = st.commandStepPoints.lastOrNull()
        val prevRefPt = if (st.commandStepPoints.size >= 2) st.commandStepPoints[st.commandStepPoints.size - 2] else null
        val trackedViewport = st.viewport.trackCursorIntoSafeBoundary(
            cursorModelPos = target,
            usableRect = st.usableViewportRect,
            referencePoint = refPt,
            minSafeMarginPx = 32f
        )
        val polarPreview = CadSnapAndCoordEngine.computePolarTrackingPreview(
            basePoint = refPt,
            rawCursorPoint = target,
            snapConfig = st.gridSnapConfig,
            previousBasePoint = prevRefPt,
            previouslyAcquiredVisualAngleDeg = st.polarPreview?.acquiredAngleDeg
        )
        val updatedSelectionBox = st.selectionBoxDrag?.copy(currentModel = target)
        _uiState.update {
            it.copy(
                rawCursorModelPos = target,
                snappedCursorModelPos = target,
                activeSnapCandidate = null,
                polarPreview = polarPreview,
                viewport = trackedViewport,
                selectionBoxDrag = updatedSelectionBox
            )
        }
    }

    private fun updateCursorPositionInternal(newRawModel: Point2D) {
        val st = _uiState.value
        val refPt = st.commandStepPoints.lastOrNull()
        val prevRefPt = if (st.commandStepPoints.size >= 2) st.commandStepPoints[st.commandStepPoints.size - 2] else null
        val effectiveGridInterval = CadSnapAndCoordEngine.computeEffectiveGridSnapInterval(
            baseGridInterval = st.gridSnapConfig.gridSnapInterval,
            currentPixelsPerModelUnit = st.viewport.pixelsPerModelUnit.toDouble(),
            referencePixelsPerModelUnit = st.referencePixelsPerModelUnit,
            previousEffectiveInterval = st.effectiveGridSnapInterval
        )
        val (snapped, candidate) = CadSnapAndCoordEngine.resolveCursorSnap(
            rawModelCursor = newRawModel,
            referencePoint = refPt,
            entities = st.entities,
            snapConfig = st.gridSnapConfig,
            viewport = st.viewport,
            previousReferencePoint = prevRefPt,
            usableViewportRect = st.usableViewportRect,
            referencePixelsPerModelUnit = st.referencePixelsPerModelUnit,
            previousEffectiveGridInterval = effectiveGridInterval,
            previouslyAcquiredVisualAngleDeg = st.polarPreview?.acquiredAngleDeg
        )
        val polarPreview = CadSnapAndCoordEngine.computePolarTrackingPreview(
            basePoint = refPt,
            rawCursorPoint = newRawModel,
            snapConfig = st.gridSnapConfig,
            previousBasePoint = prevRefPt,
            previouslyAcquiredVisualAngleDeg = st.polarPreview?.acquiredAngleDeg
        )
        // Section 17: Automatically pan/adjust viewport so the cursor never disappears off-screen
        val trackedViewport = st.viewport.trackCursorIntoSafeBoundary(
            cursorModelPos = snapped,
            usableRect = st.usableViewportRect,
            referencePoint = refPt,
            minSafeMarginPx = 32f
        )
        val updatedSelectionBox = st.selectionBoxDrag?.copy(currentModel = snapped)
        _uiState.update {
            it.copy(
                rawCursorModelPos = newRawModel,
                snappedCursorModelPos = snapped,
                activeSnapCandidate = candidate,
                polarPreview = polarPreview,
                viewport = trackedViewport,
                effectiveGridSnapInterval = effectiveGridInterval,
                selectionBoxDrag = updatedSelectionBox
            )
        }
    }

    private fun syncPolarAndActiveSessionState() {
        val st = _uiState.value
        val refPt = st.commandStepPoints.lastOrNull()
        val prevRefPt = if (st.commandStepPoints.size >= 2) st.commandStepPoints[st.commandStepPoints.size - 2] else null
        val preview = CadSnapAndCoordEngine.computePolarTrackingPreview(
            basePoint = refPt,
            rawCursorPoint = st.rawCursorModelPos,
            snapConfig = st.gridSnapConfig,
            previousBasePoint = prevRefPt,
            previouslyAcquiredVisualAngleDeg = st.polarPreview?.acquiredAngleDeg
        )
        if (st.isActiveCommandSession) {
            overviewDismissJob?.cancel()
            val persistentEvent = OverviewCardEvent(
                id = st.activeOverview?.takeIf { it.isPersistentSession }?.id ?: overviewSeq++,
                title = st.activeCommand.commandName,
                explanation = st.commandPromptBanner,
                category = NotificationCategory.INFO,
                isPersistentSession = true
            )
            _uiState.update {
                it.copy(
                    polarPreview = preview,
                    activeOverview = persistentEvent
                )
            }
        } else {
            _uiState.update {
                val clearedOverview = if (it.activeOverview?.isPersistentSession == true) null else it.activeOverview
                it.copy(
                    polarPreview = preview,
                    activeOverview = clearedOverview
                )
            }
        }
    }

    fun setControllerMode(mode: VirtualControllerMode) {
        _uiState.update {
            it.copy(controllerConfig = it.controllerConfig.copy(mode = mode))
        }
    }

    fun setStepIncrement(increment: Double) {
        _uiState.update {
            it.copy(controllerConfig = it.controllerConfig.copy(stepIncrement = increment))
        }
    }

    fun setControllerDockState(dockState: ControllerDockState) {
        val collapsed = dockState == ControllerDockState.COMPACT_DOCK
        _uiState.update {
            it.copy(
                controllerDockState = dockState,
                controllerConfig = it.controllerConfig.copy(isCollapsed = collapsed)
            )
        }
    }

    fun toggleControllerCollapse() {
        val nextCollapsed = !_uiState.value.controllerConfig.isCollapsed
        setControllerDockState(
            if (nextCollapsed) ControllerDockState.COMPACT_DOCK else ControllerDockState.EXPANDED_CONTROLLER
        )
    }

    fun setToolDrawerState(state: ToolDrawerState) {
        _uiState.update { it.copy(toolDrawerState = state) }
    }

    fun toggleToolDrawer() {
        _uiState.update {
            val next = if (it.toolDrawerState == ToolDrawerState.COLLAPSED) {
                ToolDrawerState.EXPANDED
            } else {
                ToolDrawerState.COLLAPSED
            }
            it.copy(toolDrawerState = next)
        }
    }

    /**
     * Section 18.3: Multi-section independent accordion toggle.
     * Expanding or collapsing one category section never closes other open sections.
     */
    fun toggleToolSection(category: CadCommandCategory) {
        _uiState.update { st ->
            val nextExpanded = if (category in st.expandedToolSections) {
                st.expandedToolSections - category
            } else {
                st.expandedToolSections + category
            }
            st.copy(
                activeCategory = category,
                expandedToolSections = nextExpanded
            )
        }
    }

    /**
     * Section 18.2: Vertical category side tile selection.
     * Focuses the category and ensures its accordion section is expanded without closing others.
     */
    fun selectCategoryTile(category: CadCommandCategory) {
        _uiState.update { st ->
            st.copy(
                activeCategory = category,
                expandedToolSections = st.expandedToolSections + category
            )
        }
    }

    fun onViewportGestureStateChanged(state: ViewportGestureState) {
        _uiState.update { it.copy(viewportGestureState = state) }
    }

    // --- Contextual Command Line State (Section 14.4) ---

    fun openCommandLineEditing(initialQuery: String = "") {
        _uiState.update {
            it.copy(commandPanelState = CommandPanelState.Editing(initialQuery))
        }
    }

    fun updateCommandInputQuery(query: String) {
        _uiState.update {
            it.copy(commandPanelState = CommandPanelState.Editing(query))
        }
    }

    fun closeCommandLineEditing() {
        _uiState.update { st ->
            val nextPanelState = if (st.activeCommand == CadCommandId.SELECT && st.commandStepPoints.isEmpty()) {
                CommandPanelState.Hidden
            } else {
                CommandPanelState.Running(
                    command = st.activeCommand,
                    prompt = st.commandPromptBanner,
                    stepIndex = st.commandStepPoints.size
                )
            }
            st.copy(commandPanelState = nextPanelState)
        }
    }

    fun updateControllerConfig(newConfig: CursorControllerConfig) {
        val dockState = if (newConfig.isCollapsed) ControllerDockState.COMPACT_DOCK else ControllerDockState.EXPANDED_CONTROLLER
        _uiState.update {
            it.copy(
                controllerConfig = newConfig,
                controllerDockState = dockState
            )
        }
    }

    fun updateGridSnapConfig(newConfig: GridAndSnapConfig) {
        val prev = _uiState.value.gridSnapConfig
        val sanitized = when {
            !prev.orthoEnabled && newConfig.orthoEnabled -> newConfig.copy(polarTrackingEnabled = false)
            !prev.polarTrackingEnabled && newConfig.polarTrackingEnabled -> newConfig.copy(orthoEnabled = false)
            newConfig.orthoEnabled && newConfig.polarTrackingEnabled -> newConfig.copy(orthoEnabled = false)
            else -> newConfig
        }
        _uiState.update { it.copy(gridSnapConfig = sanitized) }
        updateCursorPositionInternal(_uiState.value.rawCursorModelPos)
    }

    fun toggleGridVisibility() {
        _uiState.update {
            val next = !it.gridSnapConfig.showGrid
            it.copy(gridSnapConfig = it.gridSnapConfig.copy(showGrid = next))
        }
    }

    fun toggleGridSnap() {
        _uiState.update {
            val next = !it.gridSnapConfig.gridSnapEnabled
            it.copy(gridSnapConfig = it.gridSnapConfig.copy(gridSnapEnabled = next))
        }
        updateCursorPositionInternal(_uiState.value.rawCursorModelPos)
    }

    fun toggleOsnap() {
        _uiState.update {
            val next = !it.gridSnapConfig.osnapEnabled
            it.copy(gridSnapConfig = it.gridSnapConfig.copy(osnapEnabled = next))
        }
        updateCursorPositionInternal(_uiState.value.rawCursorModelPos)
    }

    /**
     * Section 43.9 — ORTHO and POLAR are mutually exclusive directional aids.
     * Enabling ORTHO automatically disables POLAR; enabling POLAR automatically disables ORTHO.
     */
    fun toggleOrtho() {
        val next = !_uiState.value.gridSnapConfig.orthoEnabled
        _uiState.update {
            it.copy(
                gridSnapConfig = it.gridSnapConfig.copy(
                    orthoEnabled = next,
                    polarTrackingEnabled = if (next) false else it.gridSnapConfig.polarTrackingEnabled
                )
            )
        }
        updateCursorPositionInternal(_uiState.value.rawCursorModelPos)
        val msg = if (next) {
            "ORTHO (ON) • Orthogonal 0°/90° lock enabled (Polar Tracking disabled)"
        } else {
            "ORTHO (OFF) • Orthogonal lock disabled"
        }
        emitTransientNotification(msg, NotificationCategory.INFO)
    }

    fun togglePolarTracking() {
        val next = !_uiState.value.gridSnapConfig.polarTrackingEnabled
        val inc = _uiState.value.gridSnapConfig.polarTrackingAngleDeg
        _uiState.update {
            it.copy(
                gridSnapConfig = it.gridSnapConfig.copy(
                    polarTrackingEnabled = next,
                    orthoEnabled = if (next) false else it.gridSnapConfig.orthoEnabled
                )
            )
        }
        updateCursorPositionInternal(_uiState.value.rawCursorModelPos)
        val msg = if (next) {
            "POLAR (ON) • Visual Polar Tracking enabled (${fmt(inc)}° increments; ORTHO disabled)"
        } else {
            "POLAR (OFF) • Visual Polar Tracking disabled"
        }
        emitTransientNotification(msg, NotificationCategory.INFO)
    }

    fun toggleWorkspaceSpace() {
        val next = if (_uiState.value.workspaceSpace == WorkspaceSpace.MODEL_SPACE) {
            WorkspaceSpace.PAPER_SPACE
        } else {
            WorkspaceSpace.MODEL_SPACE
        }
        val msg = "WORKSPACE • Switched to ${next.label}"
        _uiState.update {
            it.copy(
                workspaceSpace = next,
                commandPromptBanner = msg
            )
        }
        emitTransientNotification(msg, NotificationCategory.INFO)
    }

    // --- Viewport, Usable Rect Rebasing & Fit Drawing (Section 5 & Section 6) ---

    fun updateViewport(newViewport: ViewportTransform) {
        _uiState.update { st ->
            val refPpm = st.referencePixelsPerModelUnit ?: newViewport.pixelsPerModelUnit.toDouble()
            val nextEffective = CadSnapAndCoordEngine.computeEffectiveGridSnapInterval(
                baseGridInterval = st.gridSnapConfig.gridSnapInterval,
                currentPixelsPerModelUnit = newViewport.pixelsPerModelUnit.toDouble(),
                referencePixelsPerModelUnit = refPpm,
                previousEffectiveInterval = st.effectiveGridSnapInterval
            )
            st.copy(
                viewport = newViewport,
                referencePixelsPerModelUnit = refPpm,
                effectiveGridSnapInterval = nextEffective
            )
        }
    }

    fun updateViewportDimensions(widthPx: Float, heightPx: Float) {
        val prev = _uiState.value
        val isInitialResize = abs(prev.viewportWidthPx - 800f) < 1f && abs(prev.viewportHeightPx - 600f) < 1f
        val fallbackRect = if (prev.usableViewportRect.widthPx == 800f && prev.usableViewportRect.heightPx == 600f) {
            UsableViewportRect(0f, 0f, widthPx, heightPx)
        } else {
            prev.usableViewportRect
        }
        _uiState.update {
            it.copy(
                viewportWidthPx = widthPx,
                viewportHeightPx = heightPx,
                usableViewportRect = fallbackRect
            )
        }
        if (isInitialResize && prev.entities.isNotEmpty()) {
            fitDrawing()
        }
    }

    fun updateUsableViewportRect(newRect: UsableViewportRect) {
        if (newRect.widthPx <= 1f || newRect.heightPx <= 1f) return
        val prev = _uiState.value
        val wasDefault = abs(prev.usableViewportRect.widthPx - 800f) < 1f && abs(prev.usableViewportRect.heightPx - 600f) < 1f
        if (wasDefault) {
            _uiState.update { it.copy(usableViewportRect = newRect) }
            if (prev.entities.isNotEmpty()) {
                fitDrawing(notify = false)
            }
        } else {
            // Section 14.7, 18.5 & 20: Viewport Invariance Contract.
            // Updating InteractionSafeRect (usableViewportRect) when overlays open/close MUST NEVER
            // rebase, pan, or zoom the viewport transform.
            _uiState.update {
                it.copy(usableViewportRect = newRect)
            }
        }
    }

    fun setFitDrawingPolicy(policy: FitDrawingPolicy) {
        _uiState.update { it.copy(fitDrawingPolicy = policy) }
        fitDrawing(policy = policy, notify = true)
    }

    fun fitDrawing(
        policy: FitDrawingPolicy = _uiState.value.fitDrawingPolicy,
        notify: Boolean = true
    ) {
        val st = _uiState.value
        val visibleLayers = st.layers.filter { it.isVisible && !it.isFrozen }.map { it.name }.toSet()
        val baseVisible = st.entities
            .filter { it.layerName in visibleLayers }
            .filter { st.isolatedEntityIds == null || it.id in st.isolatedEntityIds }

        var fallbackNote = ""
        val targetEntities = when (policy) {
            FitDrawingPolicy.SELECTED_ONLY -> {
                val sel = baseVisible.filter { it.id in st.selectedEntityIds }
                if (sel.isEmpty()) {
                    fallbackNote = " (no selection; fitted all visible)"
                    baseVisible
                } else {
                    sel
                }
            }
            FitDrawingPolicy.GEOMETRY_ONLY -> {
                val geo = baseVisible.filterNot {
                    it is CadEntity.DimensionEntity || it is CadEntity.LeaderEntity || it is CadEntity.TextEntity
                }
                geo.ifEmpty { baseVisible }
            }
            FitDrawingPolicy.ALL_VISIBLE -> baseVisible
        }

        if (targetEntities.isEmpty()) {
            val resetBounds = BoundingBox2D(0.0, 0.0, 120.0, 80.0)
            val resetVp = st.viewport.fitToUsableViewport(resetBounds, st.usableViewportRect, marginPx = 28f)
            val refPpm = st.referencePixelsPerModelUnit ?: resetVp.pixelsPerModelUnit.toDouble()
            val nextEffective = CadSnapAndCoordEngine.computeEffectiveGridSnapInterval(
                baseGridInterval = st.gridSnapConfig.gridSnapInterval,
                currentPixelsPerModelUnit = resetVp.pixelsPerModelUnit.toDouble(),
                referencePixelsPerModelUnit = refPpm,
                previousEffectiveInterval = st.effectiveGridSnapInterval
            )
            val msg = "FIT DRAWING • Empty sheet — reset to default view"
            _uiState.update {
                it.copy(
                    viewport = resetVp,
                    referencePixelsPerModelUnit = refPpm,
                    effectiveGridSnapInterval = nextEffective,
                    fitDrawingPolicy = policy,
                    commandPromptBanner = msg
                )
            }
            if (notify) emitTransientNotification(msg, NotificationCategory.INFO)
            return
        }

        val combinedBounds = targetEntities.map { ent ->
            if (ent is CadEntity.BlockInsertEntity) ent.boundingBox(st.blocks) else ent.boundingBox()
        }.reduce { a, b -> a.union(b) }

        val fittedViewport = st.viewport.fitToUsableViewport(
            bounds = combinedBounds,
            usableRect = st.usableViewportRect,
            marginPx = 28f
        )
        val refPpm = st.referencePixelsPerModelUnit ?: fittedViewport.pixelsPerModelUnit.toDouble()
        val nextEffective = CadSnapAndCoordEngine.computeEffectiveGridSnapInterval(
            baseGridInterval = st.gridSnapConfig.gridSnapInterval,
            currentPixelsPerModelUnit = fittedViewport.pixelsPerModelUnit.toDouble(),
            referencePixelsPerModelUnit = refPpm,
            previousEffectiveInterval = st.effectiveGridSnapInterval
        )
        val msg = "FIT DRAWING • Fitted ${targetEntities.size} visible entities$fallbackNote"
        _uiState.update {
            it.copy(
                viewport = fittedViewport,
                referencePixelsPerModelUnit = refPpm,
                effectiveGridSnapInterval = nextEffective,
                fitDrawingPolicy = policy,
                commandPromptBanner = msg
            )
        }
        if (notify) emitTransientNotification(msg, NotificationCategory.SUCCESS)
    }

    fun zoomExtents() {
        fitDrawing(policy = _uiState.value.fitDrawingPolicy, notify = true)
    }

    // --- Units & Sheet Configuration ---

    fun applyUnitsAndPrecision(
        targetUnit: CadUnit,
        mode: UnitChangeMode,
        newPrecision: PrecisionFormatConfig
    ) {
        val st = _uiState.value
        prefs.edit {
            putString("unit_change_mode", mode.name)
            putString("display_unit", targetUnit.name)
            putInt("linear_decimals", newPrecision.linearDecimals)
            putInt("angular_decimals", newPrecision.angularDecimals)
            putBoolean("show_unit_suffix", newPrecision.showUnitSuffix)
            putBoolean("suppress_trailing_zeros", newPrecision.suppressTrailingZeros)
        }

        if (mode == UnitChangeMode.CONVERT_DISPLAY_UNITS) {
            recordUndoSnapshot()
            val msg = "UNITS (Option A Active) • Display unit set to ${targetUnit.symbol} (physical geometry preserved)"
            _uiState.update {
                it.copy(
                    displayUnit = targetUnit,
                    unitChangeMode = mode,
                    precision = newPrecision,
                    commandPromptBanner = msg
                )
            }
            emitTransientNotification(msg, NotificationCategory.SUCCESS)
        } else {
            recordUndoSnapshot()
            val scaleFactor = st.storageUnit.toMillimetersFactor / targetUnit.toMillimetersFactor
            val origin = Point2D(0.0, 0.0)
            val rescaledEntities = st.entities.map { it.scale(origin, scaleFactor) }
            val rescaledCursor = st.snappedCursorModelPos.scaleFrom(origin, scaleFactor)
            val msg = "UNITS (Option B Active) • Rescaled model coordinates by ${String.format(Locale.ROOT, "%.4f", scaleFactor)} to ${targetUnit.symbol}"
            _uiState.update {
                it.copy(
                    storageUnit = targetUnit,
                    displayUnit = targetUnit,
                    unitChangeMode = mode,
                    precision = newPrecision,
                    entities = rescaledEntities,
                    rawCursorModelPos = rescaledCursor,
                    snappedCursorModelPos = rescaledCursor,
                    commandPromptBanner = msg
                )
            }
            fitDrawing(notify = false)
            emitTransientNotification(msg, NotificationCategory.SUCCESS)
        }
    }

    fun applySheetSetup(newSetup: SheetSetupConfig) {
        val prev = _uiState.value
        if (prev.sheetSetup != newSetup) {
            recordUndoSnapshot()
        }
        val isNewSheetPage = prev.sheetSetup.preset != newSetup.preset ||
            prev.sheetSetup.titleBlockDrawingNo != newSetup.titleBlockDrawingNo
        val msg = "PAGESETUP • Sheet updated to ${newSetup.preset.label} (${newSetup.orientation.label}, Scale ${newSetup.scaleLabel})"
        _uiState.update {
            val cleanedEntities = if (isNewSheetPage) {
                AutoDimensionEngine.removeAutoDimensions(it.entities)
            } else {
                it.entities
            }
            it.copy(
                sheetSetup = newSetup,
                entities = cleanedEntities,
                isQuickDimensionEnabled = if (isNewSheetPage) false else it.isQuickDimensionEnabled,
                dimensionStyle = it.dimensionStyle.copy(standard = newSetup.draftingStandard),
                commandPromptBanner = msg
            )
        }
        emitTransientNotification(msg, NotificationCategory.INFO)
    }

    fun updateDimensionStyle(newStyle: DimensionStyleConfig) {
        val prev = _uiState.value
        if (prev.dimensionStyle == newStyle) return
        recordUndoSnapshot()
        val msg = "DIMSTYLE • Updated dimension style (${newStyle.standard.label})"
        _uiState.update {
            it.copy(
                dimensionStyle = newStyle,
                commandPromptBanner = msg
            )
        }
        emitTransientNotification(msg, NotificationCategory.INFO)
    }

    // --- Quick Dimension (QDIM) Toggle, Refresh, Clear All & Execution (Sections 35 & 37) ---

    fun setQuickDimensionEnabled(enabled: Boolean) {
        val st = _uiState.value
        // Section 35.2: Idempotent enabling/disabling — no-op if already in target state
        if (enabled == st.isQuickDimensionEnabled) return
        if (enabled) {
            val defaultMode = if (st.selectedEntityIds.isNotEmpty()) {
                QDimOperatingMode.QDIM_SELECT
            } else {
                QDimOperatingMode.QDIM_ALL
            }
            executeQuickDimension(
                mode = defaultMode,
                density = st.qdimDensity,
                replaceAutoDims = true
            )
        } else {
            recordUndoSnapshot()
            val withoutAutoDims = AutoDimensionEngine.removeAutoDimensions(st.entities)
            val msg = "QDIM (OFF) • Quick Dimension & Alignment disabled (manual dimensions preserved)"
            _uiState.update {
                it.copy(
                    entities = withoutAutoDims,
                    isQuickDimensionEnabled = false,
                    lastAlignmentReport = null,
                    commandPromptBanner = msg
                )
            }
            emitTransientNotification(msg, NotificationCategory.INFO)
        }
    }

    /**
     * Section 37: Destructive Clear All Dimensions master action.
     * Atomically removes all DimensionEntity objects (both manual and auto-generated) and
     * QDIM-generated CenterMarkEntity instances in one undoable transaction, preserving
     * all ordinary drawing geometry, text notes, leaders, and independent center marks.
     */
    fun clearAllDimensionsConfirmed() {
        val st = _uiState.value
        val removedDimCount = st.entities.count { it is CadEntity.DimensionEntity }
        val hasAutoCenterMarks = st.entities.any { it is CadEntity.CenterMarkEntity && it.sourceEntityId != null }
        if (removedDimCount == 0 && !hasAutoCenterMarks) {
            val msg = "CLEAR DIMENSIONS • No dimensions found"
            _uiState.update {
                it.copy(
                    isQuickDimensionEnabled = false,
                    lastAlignmentReport = null,
                    commandPromptBanner = msg
                )
            }
            emitTransientNotification(msg, NotificationCategory.INFO)
            return
        }

        recordUndoSnapshot()
        val remainingEntities = st.entities.filterNot { entity ->
            entity is CadEntity.DimensionEntity ||
                (entity is CadEntity.CenterMarkEntity && entity.sourceEntityId != null)
        }
        val remainingIds = remainingEntities.map { it.id }.toSet()
        val msg = "CLEAR DIMENSIONS • $removedDimCount dimensions cleared"
        _uiState.update {
            it.copy(
                entities = remainingEntities,
                selectedEntityIds = it.selectedEntityIds.intersect(remainingIds),
                isQuickDimensionEnabled = false,
                lastAlignmentReport = null,
                commandPromptBanner = msg
            )
        }
        emitTransientNotification(msg, NotificationCategory.SUCCESS)
    }

    /**
     * Section 5.7: Category-First Selective Dimension Removal.
     * Removes only the specified DimensionEntity IDs in a single undoable transaction,
     * preserving all unselected dimensions and all non-dimension drawing geometry.
     */
    fun removeSelectedDimensionsConfirmed(dimensionIdsToRemove: Set<String>) {
        if (dimensionIdsToRemove.isEmpty()) return
        val st = _uiState.value
        val matchingDims = st.entities.filter { it is CadEntity.DimensionEntity && it.id in dimensionIdsToRemove }
        if (matchingDims.isEmpty()) return

        recordUndoSnapshot()
        val remainingEntities = st.entities.filterNot { entity ->
            entity is CadEntity.DimensionEntity && entity.id in dimensionIdsToRemove
        }
        val remainingIds = remainingEntities.map { it.id }.toSet()
        val anyAutoDimsRemaining = remainingEntities.any { it is CadEntity.DimensionEntity && it.isAutoGenerated }
        val msg = "SELECTIVE CLEAR • Removed ${matchingDims.size} selected dimension(s)"
        _uiState.update {
            it.copy(
                entities = remainingEntities,
                selectedEntityIds = it.selectedEntityIds.intersect(remainingIds),
                isQuickDimensionEnabled = it.isQuickDimensionEnabled && anyAutoDimsRemaining,
                commandPromptBanner = msg
            )
        }
        emitTransientNotification(msg, NotificationCategory.SUCCESS)
    }

    fun refreshQuickDimensions() {
        val st = _uiState.value
        executeQuickDimension(
            mode = QDimOperatingMode.QDIM_ALL,
            density = st.qdimDensity,
            replaceAutoDims = true,
            arrangementPreference = st.qdimArrangementPreference
        )
        val msg = "QDIM REFRESH (${st.qdimArrangementPreference.label}) • Refreshed quick alignment & dimensions for all sheet structures"
        _uiState.update {
            it.copy(
                isQuickDimensionEnabled = true,
                commandPromptBanner = msg
            )
        }
        emitTransientNotification(msg, NotificationCategory.SUCCESS)
    }

    fun setQDimDensity(density: QDimDensityPolicy) {
        val st = _uiState.value
        if (st.isQuickDimensionEnabled) {
            executeQuickDimension(
                mode = if (st.selectedEntityIds.isNotEmpty()) QDimOperatingMode.QDIM_SELECT else QDimOperatingMode.QDIM_ALL,
                density = density,
                replaceAutoDims = true,
                arrangementPreference = st.qdimArrangementPreference
            )
        } else {
            _uiState.update { it.copy(qdimDensity = density) }
        }
    }

    fun setQDimArrangementPreference(preference: DimensionArrangementPreference) {
        val st = _uiState.value
        if (st.isQuickDimensionEnabled) {
            executeQuickDimension(
                mode = if (st.selectedEntityIds.isNotEmpty()) QDimOperatingMode.QDIM_SELECT else QDimOperatingMode.QDIM_ALL,
                density = st.qdimDensity,
                replaceAutoDims = true,
                arrangementPreference = preference
            )
        } else {
            _uiState.update { it.copy(qdimArrangementPreference = preference) }
        }
    }

    fun executeQuickDimension(
        mode: QDimOperatingMode = QDimOperatingMode.QDIM_ALL,
        density: QDimDensityPolicy = _uiState.value.qdimDensity,
        replaceAutoDims: Boolean = true,
        arrangementPreference: DimensionArrangementPreference = _uiState.value.qdimArrangementPreference
    ) {
        val st = _uiState.value
        val targets = when (mode) {
            QDimOperatingMode.QDIM_SELECT -> {
                val sel = st.entities.filter { it.id in st.selectedEntityIds }
                sel.ifEmpty { st.entities }
            }
            QDimOperatingMode.QDIM_ALL, QDimOperatingMode.QDIM_REGION -> st.entities
        }
        recordUndoSnapshot()
        val (updatedEntities, alignmentReport) = AutoDimensionEngine.generateQuickDimensionsWithAlignmentReport(
            allEntities = st.entities,
            targetEntities = targets,
            density = density,
            replaceExistingAutoDims = replaceAutoDims,
            arrangementPreference = arrangementPreference,
            style = st.dimensionStyle,
            precision = st.precision,
            storageUnit = st.storageUnit,
            displayUnit = st.displayUnit,
            viewport = st.viewport
        )
        val dimCount = updatedEntities.count { it is CadEntity.DimensionEntity }
        val unresolvedCount = alignmentReport.unresolvedWarnings.size
        val placedCount = alignmentReport.placements.size
        val msg = if (unresolvedCount > 0) {
            "QDIM placed $placedCount dimensions; $unresolvedCount could not be placed without overlap. Move the drawing to a clearer area or choose a less dense arrangement, then refresh."
        } else {
            "${mode.commandToken} (${density.label} • ${arrangementPreference.label} • ON) • Refreshed $dimCount associative dimensions"
        }
        _uiState.update {
            it.copy(
                activeCommand = CadCommandId.SELECT,
                activeCategory = CadCommandId.QDIM.category,
                commandPanelState = CommandPanelState.Hidden,
                entities = updatedEntities,
                isQuickDimensionEnabled = true,
                qdimDensity = density,
                qdimArrangementPreference = arrangementPreference,
                lastAlignmentReport = alignmentReport,
                activeActionPrompt = promptForCommand(CadCommandId.SELECT, 0, "Single"),
                commandPromptBanner = msg
            )
        }
        syncPolarAndActiveSessionState()
        if (unresolvedCount > 0) {
            emitOverview(
                title = "QDIM • Insufficient Clear Space ($unresolvedCount unresolved)",
                explanation = msg,
                category = NotificationCategory.WARNING,
                isPersistentSession = false
            )
            emitTransientNotification(msg, NotificationCategory.WARNING)
        } else {
            emitTransientNotification(msg, NotificationCategory.SUCCESS)
        }
    }

    // --- Command Selection & Execution State Machine (Section 14) ---

    fun selectCategory(category: CadCommandCategory) {
        _uiState.update {
            it.copy(
                activeCategory = category,
                expandedToolSections = it.expandedToolSections + category
            )
        }
    }

    fun activateCommand(command: CadCommandId, subOption: String = "Default") {
        val st = _uiState.value
        val updatedRecents = (listOf(command) + st.recentCommands.filter { it != command }).take(8)

        when (command) {
            CadCommandId.UNDO_REDO -> {
                undo()
                return
            }
            CadCommandId.QDIM -> {
                executeQuickDimension(
                    mode = if (st.selectedEntityIds.isNotEmpty()) QDimOperatingMode.QDIM_SELECT else QDimOperatingMode.QDIM_ALL,
                    density = st.qdimDensity,
                    replaceAutoDims = true
                )
                return
            }
            CadCommandId.JOIN -> {
                val sel = st.entities.filter { it.id in st.selectedEntityIds }
                if (sel.size >= 2) {
                    recordUndoSnapshot()
                    val joined = CadModificationEngine.joinEntities(sel)
                    val remaining = st.entities.filterNot { it.id in st.selectedEntityIds } + joined
                    val msg = "JOIN • Merged ${sel.size} segments into ${joined.size} polyline/line"
                    updateSelectionAndState(
                        entities = remaining,
                        nextSelection = joined.map { e -> e.id }.toSet(),
                        banner = msg,
                        returnToIdle = true
                    )
                    emitTransientNotification(msg, NotificationCategory.SUCCESS)
                    return
                }
            }
            CadCommandId.EXPLODE -> {
                val sel = st.entities.filter { it.id in st.selectedEntityIds }
                if (sel.isNotEmpty()) {
                    recordUndoSnapshot()
                    val exploded = sel.flatMap { CadModificationEngine.explodeEntity(it, st.blocks) }
                    val remaining = st.entities.filterNot { it.id in st.selectedEntityIds } + exploded
                    val msg = "EXPLODE • Decomposed ${sel.size} compound objects into ${exploded.size} primitives"
                    updateSelectionAndState(
                        entities = remaining,
                        nextSelection = emptySet(),
                        banner = msg,
                        returnToIdle = true
                    )
                    emitTransientNotification(msg, NotificationCategory.SUCCESS)
                    return
                }
            }
            CadCommandId.ISOLATE -> {
                if (st.isolatedEntityIds != null) {
                    val msg = "UNISOLATE • Restored visibility of all entities"
                    _uiState.update {
                        it.copy(
                            isolatedEntityIds = null,
                            commandPromptBanner = msg
                        )
                    }
                    emitTransientNotification(msg, NotificationCategory.INFO)
                } else if (st.selectedEntityIds.isNotEmpty()) {
                    val msg = "ISOLATE • Isolated ${st.selectedEntityIds.size} selected objects"
                    _uiState.update {
                        it.copy(
                            isolatedEntityIds = st.selectedEntityIds,
                            commandPromptBanner = msg
                        )
                    }
                    emitTransientNotification(msg, NotificationCategory.INFO)
                } else {
                    emitTransientNotification("ISOLATE • Select entities first using SELECT command", NotificationCategory.WARNING)
                }
                return
            }
            CadCommandId.ARRAY -> {
                val sel = getUnlockedSelectedEntities()
                if (sel.isNotEmpty()) {
                    recordUndoSnapshot()
                    val generated = if (subOption.equals("Polar", true)) {
                        CadModificationEngine.createPolarArray(sel, st.snappedCursorModelPos, itemCount = 6)
                    } else {
                        CadModificationEngine.createRectangularArray(sel, rows = 2, cols = 3, rowSpacing = 25.0, colSpacing = 30.0)
                    }
                    val msg = "ARRAY • Created ${generated.size} arrayed entities"
                    _uiState.update {
                        it.copy(
                            entities = it.entities + generated,
                            commandPromptBanner = msg,
                            commandPanelState = CommandPanelState.Hidden
                        )
                    }
                    emitTransientNotification(msg, NotificationCategory.SUCCESS)
                    return
                }
            }
            CadCommandId.PEDIT -> {
                val selPolys = getUnlockedSelectedEntities().filterIsInstance<CadEntity.PolylineEntity>()
                if (selPolys.isNotEmpty()) {
                    recordUndoSnapshot()
                    val toggledIds = selPolys.map { it.id }.toSet()
                    val updated = st.entities.map { ent ->
                        if (ent is CadEntity.PolylineEntity && ent.id in toggledIds) {
                            ent.copy(isClosed = !ent.isClosed)
                        } else ent
                    }
                    val msg = "PEDIT • Toggled closed state on ${selPolys.size} polylines"
                    _uiState.update {
                        it.copy(
                            entities = updated,
                            commandPromptBanner = msg,
                            commandPanelState = CommandPanelState.Hidden
                        )
                    }
                    emitTransientNotification(msg, NotificationCategory.SUCCESS)
                    return
                }
            }
            else -> Unit
        }

        val defaultParam = when (command) {
            CadCommandId.FILLET -> 5.0
            CadCommandId.CHAMFER -> 5.0
            CadCommandId.OFFSET -> 8.0
            CadCommandId.POLYGON -> 6.0
            CadCommandId.DONUT -> 12.0
            CadCommandId.LENGTHEN -> 50.0
            else -> st.commandParameterValue
        }

        val effectiveSubOption = if (command == CadCommandId.SELECT && subOption == "Default") "Single" else subOption
        val actionPrompt = promptForCommand(command, 0, effectiveSubOption)
        _uiState.update {
            it.copy(
                activeCategory = command.category,
                activeCommand = command,
                commandSubOption = effectiveSubOption,
                commandParameterValue = defaultParam,
                commandStepPoints = emptyList(),
                selectionBoxDrag = null,
                activeActionPrompt = actionPrompt,
                commandPromptBanner = actionPrompt,
                commandPanelState = CommandPanelState.Running(command, actionPrompt, 0),
                recentCommands = updatedRecents
            )
        }
        syncPolarAndActiveSessionState()
    }

    fun setCommandSubOption(subOption: String) {
        val st = _uiState.value
        if (st.activeCommand == CadCommandId.SELECT) {
            when (subOption) {
                "SelectAll" -> {
                    selectAllEntities()
                    return
                }
                "Previous" -> {
                    selectPreviousEntities()
                    return
                }
                "Clear" -> {
                    clearSelection()
                    return
                }
            }
        }
        // Section 2.1–2.3: Shared Live-Preview Preservation Across All Applicable Commands.
        // Changing a command mode, sub-option, or geometric style while a command is active
        // MUST retain all previously accepted points/operands (commandStepPoints), preserve
        // the viewport, and immediately recalculate the temporary preview.
        val retainedStepCount = st.commandStepPoints.size
        val actionPrompt = promptForCommand(st.activeCommand, retainedStepCount, subOption)
        _uiState.update {
            it.copy(
                commandSubOption = subOption,
                commandStepPoints = it.commandStepPoints,
                selectionBoxDrag = if (st.activeCommand == CadCommandId.SELECT) null else it.selectionBoxDrag,
                activeActionPrompt = actionPrompt,
                commandPromptBanner = actionPrompt,
                commandPanelState = CommandPanelState.Running(st.activeCommand, actionPrompt, retainedStepCount)
            )
        }
        syncPolarAndActiveSessionState()
    }

    fun setCommandParameterValue(value: Double) {
        val coerced = value.coerceAtLeast(0.0)
        _uiState.update { st ->
            val actionPrompt = promptForCommand(st.activeCommand, st.commandStepPoints.size, st.commandSubOption)
            st.copy(
                commandParameterValue = coerced,
                activeActionPrompt = actionPrompt,
                commandPromptBanner = actionPrompt,
                commandPanelState = CommandPanelState.Running(st.activeCommand, actionPrompt, st.commandStepPoints.size)
            )
        }
        syncPolarAndActiveSessionState()
    }

    /**
     * Triggered when user taps "PICK PT" on the Virtual Cursor Controller or submits a numeric coordinate.
     */
    fun pickPointAtCursor() {
        handlePointPick(_uiState.value.snappedCursorModelPos)
    }

    /**
     * Section 14.1: Direct single-finger taps on the sheet are strictly forbidden from picking points or selecting entities.
     */
    @Suppress("UNUSED_PARAMETER")
    fun handleDirectTapModelPoint(rawModelPt: Point2D) {
        // Intentional no-op per Section 14.1 navigation-only sheet contract.
    }

    fun submitCommandOrCoordinateInput(rawText: String) {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) {
            finishCurrentCommand()
            return
        }

        // 1. Check special keywords ("C" to close line/polyline, "ALL" to select all, "PREV" for previous selection, "QDIM ...")
        val upper = trimmed.uppercase()
        if (upper == "ALL" || upper == "SELECT ALL" || upper == "SELALL") {
            selectAllEntities()
            return
        }
        if (upper == "PREV" || upper == "PREVIOUS") {
            selectPreviousEntities()
            return
        }
        if (upper == "CLEAR" || upper == "DESELECT") {
            clearSelection()
            return
        }
        if (upper == "FIT" || upper == "ZOOM EXTENTS" || upper == "ZE") {
            fitDrawing()
            return
        }
        if (upper.startsWith("QDIM")) {
            when {
                upper.contains("OFF") || upper.contains("DISABLE") -> {
                    setQuickDimensionEnabled(false)
                }
                upper.contains("REFRESH") -> {
                    refreshQuickDimensions()
                }
                else -> {
                    val mode = when {
                        upper.contains("SELECT") -> QDimOperatingMode.QDIM_SELECT
                        upper.contains("REGION") -> QDimOperatingMode.QDIM_REGION
                        else -> QDimOperatingMode.QDIM_ALL
                    }
                    executeQuickDimension(mode = mode, replaceAutoDims = true)
                }
            }
            return
        }
        if (upper == "REFRESH" && (_uiState.value.activeCommand == CadCommandId.QDIM || _uiState.value.isQuickDimensionEnabled)) {
            refreshQuickDimensions()
            return
        }
        if (upper == "C" && (_uiState.value.activeCommand == CadCommandId.LINE || _uiState.value.activeCommand == CadCommandId.POLYLINE)) {
            finishCurrentCommand(closeLoop = true)
            return
        }

        // 2. Check explicit radius/distance assignments like "R=5", "R 5", "R5", "D=5" or plain numeric parameter
        val st = _uiState.value
        val radiusMatch = Regex("""^(?:R|RADIUS|D|DIST)\s*[=:]?\s*([0-9]+(?:\.[0-9]+)?)$""", RegexOption.IGNORE_CASE).matchEntire(trimmed)
        if (radiusMatch != null) {
            val v = radiusMatch.groupValues[1].toDoubleOrNull()
            if (v != null) {
                setCommandParameterValue(v)
                emitTransientNotification("${st.activeCommand.commandName} • Parameter set to ${fmt(v)}", NotificationCategory.INFO)
                return
            }
        }
        val plainNumber = trimmed.toDoubleOrNull()
        if (plainNumber != null && st.commandStepPoints.isEmpty() && st.activeCommand in setOf(
                CadCommandId.FILLET, CadCommandId.CHAMFER, CadCommandId.OFFSET,
                CadCommandId.POLYGON, CadCommandId.DONUT, CadCommandId.LENGTHEN
            )
        ) {
            setCommandParameterValue(plainNumber)
            emitTransientNotification("${st.activeCommand.commandName} • Parameter set to ${fmt(plainNumber)}", NotificationCategory.INFO)
            return
        }

        // 3. Check if input is a CAD Command Name or Alias
        val matchedCmd = CadCommandId.fromText(upper)
        if (matchedCmd != null) {
            activateCommand(matchedCmd)
            return
        }

        // 4. Otherwise parse as Direct Coordinate / Polar / Scalar Distance expression
        val displayToStorage = st.displayUnit.toMillimetersFactor / st.storageUnit.toMillimetersFactor
        val parsedDisplayPt = CadSnapAndCoordEngine.parseCoordinateInput(
            rawInput = trimmed,
            referencePoint = st.commandStepPoints.lastOrNull()?.let {
                Point2D(it.x / displayToStorage, it.y / displayToStorage)
            },
            currentCursor = Point2D(
                st.snappedCursorModelPos.x / displayToStorage,
                st.snappedCursorModelPos.y / displayToStorage
            ),
            orthoEnabled = st.gridSnapConfig.orthoEnabled
        )
        if (parsedDisplayPt != null) {
            val modelPt = Point2D(
                parsedDisplayPt.x * displayToStorage,
                parsedDisplayPt.y * displayToStorage
            )
            // Section 17: Track viewport to keep coordinate-entered cursor visible
            val trackedViewport = st.viewport.trackCursorIntoSafeBoundary(
                cursorModelPos = modelPt,
                usableRect = st.usableViewportRect,
                referencePoint = st.commandStepPoints.lastOrNull(),
                minSafeMarginPx = 32f
            )
            _uiState.update {
                it.copy(
                    rawCursorModelPos = modelPt,
                    snappedCursorModelPos = modelPt,
                    activeSnapCandidate = null,
                    viewport = trackedViewport
                )
            }
            handlePointPick(modelPt)
        } else {
            emitTransientNotification(
                "Unknown command or coordinate '$trimmed'",
                NotificationCategory.WARNING
            )
        }
    }

    private fun handlePointPick(pt: Point2D) {
        val st = _uiState.value
        val cmd = st.activeCommand
        val pts = st.commandStepPoints + pt
        val layer = st.activeLayerName

        when (cmd) {
            CadCommandId.SELECT -> {
                when (st.commandSubOption) {
                    "Window", "Crossing" -> {
                        val isCrossing = st.commandSubOption == "Crossing"
                        if (pts.size == 1) {
                            val prompt = "SELECT (${st.commandSubOption}) • Corner 1 set at (${fmt(pt.x)}, ${fmt(pt.y)}). Move cursor to opposite corner and tap PICK PT"
                            _uiState.update {
                                it.copy(
                                    commandStepPoints = pts,
                                    selectionBoxDrag = SelectionBoxDragState(
                                        startModel = pt,
                                        currentModel = pt
                                    ),
                                    commandPromptBanner = prompt,
                                    commandPanelState = CommandPanelState.Running(cmd, prompt, 1)
                                )
                            }
                        } else {
                            val box = BoundingBox2D.fromPoints(listOf(pts[0], pts[1]))
                            val frozenOrHiddenLayers = st.layers.filter { !it.isVisible || it.isFrozen }.map { it.name }.toSet()
                            val matchedIds = st.entities
                                .filter { it.layerName !in frozenOrHiddenLayers }
                                .filter { st.isolatedEntityIds == null || it.id in st.isolatedEntityIds }
                                .filter { CadGeometryKernel.isEntitySelectedByBox(it, box, isCrossingMode = isCrossing) }
                                .map { it.id }
                                .toSet()
                            val modeLabel = if (isCrossing) "CROSSING SELECTION" else "WINDOW SELECTION"
                            val msg = "$modeLabel • Selected ${matchedIds.size} object(s)"
                            val prevSel = if (st.selectedEntityIds.isNotEmpty()) st.selectedEntityIds else st.previousSelectedEntityIds
                            _uiState.update {
                                it.copy(
                                    selectedEntityIds = matchedIds,
                                    previousSelectedEntityIds = prevSel,
                                    commandStepPoints = emptyList(),
                                    selectionBoxDrag = null,
                                    commandPromptBanner = msg,
                                    commandPanelState = CommandPanelState.Running(cmd, msg, 0)
                                )
                            }
                            emitTransientNotification(msg, NotificationCategory.INFO)
                        }
                    }
                    else -> {
                        val hit = findNearestSelectableEntity(pt)
                        val nextSel = if (hit != null) {
                            if (hit.id in st.selectedEntityIds) st.selectedEntityIds - hit.id else st.selectedEntityIds + hit.id
                        } else {
                            emptySet()
                        }
                        val prevSel = if (st.selectedEntityIds.isNotEmpty()) st.selectedEntityIds else st.previousSelectedEntityIds
                        val msg = if (nextSel.isEmpty()) {
                            "SELECT • 0 objects selected (move cursor over entity and tap PICK PT)"
                        } else {
                            "SELECTED • ${nextSel.size} object(s) selected (${hit?.typeName ?: ""})"
                        }
                        _uiState.update {
                            it.copy(
                                selectedEntityIds = nextSel,
                                previousSelectedEntityIds = prevSel,
                                commandPromptBanner = msg,
                                commandPanelState = if (it.commandPanelState is CommandPanelState.Running) {
                                    CommandPanelState.Running(cmd, msg, 0)
                                } else {
                                    it.commandPanelState
                                }
                            )
                        }
                        if (hit != null) {
                            emitTransientNotification(msg, NotificationCategory.INFO)
                        }
                    }
                }
            }

            CadCommandId.LINE -> {
                if (pts.size >= 2) {
                    val p1 = pts[pts.size - 2]
                    val p2 = pts.last()
                    if (p1.distanceTo(p2) > 1e-4) {
                        recordUndoSnapshot()
                        val seg = CadEntity.LineEntity(layerName = layer, start = p1, end = p2)
                        val prompt = "LINE • Segment added (${st.precision.formatLinear(seg.length, st.storageUnit, st.displayUnit)}). Pick next point, 'C' to close, or ✓ to finish"
                        _uiState.update {
                            it.copy(
                                entities = it.entities + seg,
                                commandStepPoints = pts,
                                commandPromptBanner = prompt,
                                commandPanelState = CommandPanelState.Running(cmd, prompt, pts.size)
                            )
                        }
                    }
                } else {
                    val prompt = promptForCommand(cmd, pts.size, st.commandSubOption)
                    _uiState.update {
                        it.copy(
                            commandStepPoints = pts,
                            commandPromptBanner = prompt,
                            commandPanelState = CommandPanelState.Running(cmd, prompt, pts.size)
                        )
                    }
                }
            }

            CadCommandId.POLYLINE, CadCommandId.SPLINE -> {
                val prompt = "${cmd.commandName} • Vertex ${pts.size} added. Pick next point or tap ✓ (Enter) to complete"
                _uiState.update {
                    it.copy(
                        commandStepPoints = pts,
                        commandPromptBanner = prompt,
                        commandPanelState = CommandPanelState.Running(cmd, prompt, pts.size)
                    )
                }
            }

            CadCommandId.CIRCLE -> {
                when (st.commandSubOption) {
                    "3P" -> if (pts.size >= 3) {
                        val c = CadGeometryKernel.circleFrom3Points(pts[0], pts[1], pts[2])
                        if (c != null) {
                            commitSingleEntity(c.copy(layerName = layer), "CIRCLE (3P) • Created R=${st.precision.formatLinear(c.radius, st.storageUnit, st.displayUnit)}")
                        } else {
                            val err = "CIRCLE (3P) • Points are collinear; pick 3 non-collinear points"
                            _uiState.update {
                                it.copy(
                                    commandStepPoints = emptyList(),
                                    commandPromptBanner = err,
                                    commandPanelState = CommandPanelState.Running(cmd, err, 0)
                                )
                            }
                            emitTransientNotification(err, NotificationCategory.WARNING)
                        }
                    } else {
                        val prompt = promptForCommand(cmd, pts.size, "3P")
                        _uiState.update { it.copy(commandStepPoints = pts, commandPromptBanner = prompt, commandPanelState = CommandPanelState.Running(cmd, prompt, pts.size)) }
                    }

                    "2P" -> if (pts.size >= 2) {
                        val center = pts[0].midpointTo(pts[1])
                        val r = pts[0].distanceTo(pts[1]) * 0.5
                        if (r > 1e-4) {
                            commitSingleEntity(CadEntity.CircleEntity(layerName = layer, center = center, radius = r), "CIRCLE (2P) • Created ⌀=${st.precision.formatLinear(r * 2, st.storageUnit, st.displayUnit)}")
                        }
                    } else {
                        val prompt = promptForCommand(cmd, pts.size, "2P")
                        _uiState.update { it.copy(commandStepPoints = pts, commandPromptBanner = prompt, commandPanelState = CommandPanelState.Running(cmd, prompt, pts.size)) }
                    }

                    "TTR" -> if (pts.size >= 2) {
                        val l1 = findNearestEntityOfType<CadEntity.LineEntity>(pts[0])
                        val l2 = findNearestEntityOfType<CadEntity.LineEntity>(pts[1])
                        val r = st.commandParameterValue.coerceAtLeast(1.0)
                        val ttr = if (l1 != null && l2 != null) CadGeometryKernel.circleTanTanRadius(l1, l2, r, pts[1]) else null
                        if (ttr != null) {
                            commitSingleEntity(ttr.copy(layerName = layer), "CIRCLE (TTR) • Created tangent circle R=$r")
                        } else {
                            val err = "CIRCLE (TTR) • Pick two non-parallel lines"
                            _uiState.update { it.copy(commandStepPoints = emptyList(), commandPromptBanner = err, commandPanelState = CommandPanelState.Running(cmd, err, 0)) }
                            emitTransientNotification(err, NotificationCategory.WARNING)
                        }
                    } else {
                        val prompt = "CIRCLE (TTR) • Pick second tangent line"
                        _uiState.update { it.copy(commandStepPoints = pts, commandPromptBanner = prompt, commandPanelState = CommandPanelState.Running(cmd, prompt, pts.size)) }
                    }

                    else -> if (pts.size >= 2) {
                        val dist = pts[0].distanceTo(pts[1])
                        val r = if (st.commandSubOption == "Diameter") dist * 0.5 else dist
                        if (r > 1e-4) {
                            commitSingleEntity(
                                CadEntity.CircleEntity(layerName = layer, center = pts[0], radius = r),
                                "CIRCLE • Created center (${fmt(pts[0].x)}, ${fmt(pts[0].y)}) R=${fmt(r)}"
                            )
                        }
                    } else {
                        val prompt = promptForCommand(cmd, pts.size, st.commandSubOption)
                        _uiState.update { it.copy(commandStepPoints = pts, commandPromptBanner = prompt, commandPanelState = CommandPanelState.Running(cmd, prompt, pts.size)) }
                    }
                }
            }

            CadCommandId.ARC -> {
                if (pts.size >= 3) {
                    val arc = if (st.commandSubOption == "SCE") {
                        val startPt = pts[0]
                        val centerPt = pts[1]
                        val endPt = pts[2]
                        val r = centerPt.distanceTo(startPt).coerceAtLeast(1e-3)
                        val a1 = centerPt.angleToDeg(startPt)
                        val a2 = centerPt.angleToDeg(endPt)
                        val sweep = ((a2 - a1) + 360.0) % 360.0
                        CadEntity.ArcEntity(layerName = layer, center = centerPt, radius = r, startAngleDeg = a1, sweepAngleDeg = sweep)
                    } else {
                        CadGeometryKernel.arcFrom3Points(pts[0], pts[1], pts[2])?.copy(layerName = layer)
                    }
                    if (arc != null) {
                        commitSingleEntity(arc, "ARC • Created R=${fmt(arc.radius)}, sweep=${fmt(arc.sweepAngleDeg)}°")
                    }
                } else {
                    val prompt = promptForCommand(cmd, pts.size, st.commandSubOption)
                    _uiState.update { it.copy(commandStepPoints = pts, commandPromptBanner = prompt, commandPanelState = CommandPanelState.Running(cmd, prompt, pts.size)) }
                }
            }

            CadCommandId.RECTANGLE -> {
                if (pts.size >= 2) {
                    val corner1 = pts[0]
                    val rawCorner2 = pts[1]
                    val corner2 = if (st.commandSubOption == "Square") {
                        val dx = rawCorner2.x - corner1.x
                        val dy = rawCorner2.y - corner1.y
                        val side = maxOf(abs(dx), abs(dy)).coerceAtLeast(1.0)
                        Point2D(
                            x = corner1.x + if (dx >= 0.0) side else -side,
                            y = corner1.y + if (dy >= 0.0) side else -side
                        )
                    } else {
                        rawCorner2
                    }
                    val filletR = if (st.commandSubOption == "Fillet") st.commandParameterValue else 0.0
                    val chamferD = if (st.commandSubOption == "Chamfer") st.commandParameterValue else 0.0
                    val rect = CadGeometryKernel.createRectanglePolyline(
                        corner1 = corner1,
                        corner2 = corner2,
                        filletRadius = filletR,
                        chamferDistance = chamferD,
                        layerName = layer
                    )
                    commitSingleEntity(rect, "RECTANGLE • Created ${fmt(rect.boundingBox().width)} × ${fmt(rect.boundingBox().height)}")
                } else {
                    val prompt = promptForCommand(cmd, pts.size, st.commandSubOption)
                    _uiState.update { it.copy(commandStepPoints = pts, commandPromptBanner = prompt, commandPanelState = CommandPanelState.Running(cmd, prompt, pts.size)) }
                }
            }

            CadCommandId.POLYGON -> {
                if (pts.size >= 2) {
                    val r = pts[0].distanceTo(pts[1]).coerceAtLeast(1.0)
                    val sides = st.commandParameterValue.toInt().coerceIn(3, 64)
                    val circum = st.commandSubOption == "Circumscribed"
                    val poly = CadGeometryKernel.createRegularPolygon(
                        center = pts[0],
                        radius = r,
                        sides = sides,
                        circumscribed = circum,
                        rotationDeg = pts[0].angleToDeg(pts[1]),
                        layerName = layer
                    )
                    commitSingleEntity(poly, "POLYGON • Created $sides-sided polygon")
                } else {
                    val prompt = "POLYGON • Pick radius/corner point"
                    _uiState.update { it.copy(commandStepPoints = pts, commandPromptBanner = prompt, commandPanelState = CommandPanelState.Running(cmd, prompt, pts.size)) }
                }
            }

            CadCommandId.ELLIPSE -> {
                if (pts.size >= 3) {
                    val center = pts[0]
                    val majR = center.distanceTo(pts[1]).coerceAtLeast(1.0)
                    val minR = center.distanceTo(pts[2]).coerceAtLeast(0.5)
                    val rot = center.angleToDeg(pts[1])
                    commitSingleEntity(
                        CadEntity.EllipseEntity(
                            layerName = layer,
                            center = center,
                            majorRadius = majR,
                            minorRadius = minR,
                            rotationAngleDeg = rot
                        ),
                        "ELLIPSE • Created ${fmt(majR * 2)} × ${fmt(minR * 2)}"
                    )
                } else {
                    val prompt = promptForCommand(cmd, pts.size, st.commandSubOption)
                    _uiState.update { it.copy(commandStepPoints = pts, commandPromptBanner = prompt, commandPanelState = CommandPanelState.Running(cmd, prompt, pts.size)) }
                }
            }

            CadCommandId.POINT -> {
                commitSingleEntity(
                    CadEntity.PointEntity(layerName = layer, position = pt),
                    "POINT • Placed at (${fmt(pt.x)}, ${fmt(pt.y)})"
                )
            }

            CadCommandId.RAY -> {
                if (pts.size >= 2) {
                    val ang = pts[0].angleToDeg(pts[1])
                    commitSingleEntity(
                        CadEntity.RayEntity(basePoint = pts[0], angleDeg = ang),
                        "RAY • Created at ∠${fmt(ang)}°"
                    )
                } else {
                    val prompt = "RAY • Pick through point for direction"
                    _uiState.update { it.copy(commandStepPoints = pts, commandPromptBanner = prompt, commandPanelState = CommandPanelState.Running(cmd, prompt, pts.size)) }
                }
            }

            CadCommandId.XLINE -> {
                when (st.commandSubOption) {
                    "Horizontal" -> commitSingleEntity(CadEntity.XLineEntity(rootPoint = pt, angleDeg = 0.0), "XLINE (Horizontal) • Placed at Y=${fmt(pt.y)}")
                    "Vertical" -> commitSingleEntity(CadEntity.XLineEntity(rootPoint = pt, angleDeg = 90.0), "XLINE (Vertical) • Placed at X=${fmt(pt.x)}")
                    else -> if (pts.size >= 2) {
                        val ang = pts[0].angleToDeg(pts[1])
                        commitSingleEntity(CadEntity.XLineEntity(rootPoint = pts[0], angleDeg = ang), "XLINE • Placed at ∠${fmt(ang)}°")
                    } else {
                        val prompt = "XLINE • Pick second point for direction"
                        _uiState.update { it.copy(commandStepPoints = pts, commandPromptBanner = prompt, commandPanelState = CommandPanelState.Running(cmd, prompt, pts.size)) }
                    }
                }
            }

            CadCommandId.DONUT -> {
                val od = st.commandParameterValue.coerceAtLeast(2.0)
                val id = (od * 0.5)
                commitSingleEntity(
                    CadEntity.DonutEntity(layerName = layer, center = pt, innerDiameter = id, outerDiameter = od),
                    "DONUT • Placed ID=${fmt(id)}, OD=${fmt(od)}"
                )
            }

            CadCommandId.HATCH -> {
                val boundaryPoly = CadGeometryKernel.traceEnclosingBoundary(pt, st.entities, layerName = "HATCH")
                if (boundaryPoly != null) {
                    val pattern = HatchPattern.entries.firstOrNull { it.name.equals(st.commandSubOption, true) } ?: HatchPattern.ANSI31
                    val hatch = CadEntity.HatchEntity(
                        layerName = "HATCH",
                        boundaryPoints = boundaryPoly.vertices.map { it.position },
                        pattern = pattern,
                        patternScale = 1.0
                    )
                    commitSingleEntity(hatch, "HATCH (${pattern.name}) • Applied to closed boundary")
                } else {
                    val err = "HATCH • Pick a point inside a closed boundary loop"
                    _uiState.update { it.copy(commandPromptBanner = err, commandPanelState = CommandPanelState.Running(cmd, err, 0)) }
                    emitTransientNotification(err, NotificationCategory.WARNING)
                }
            }

            CadCommandId.BOUNDARY -> {
                val boundaryPoly = CadGeometryKernel.traceEnclosingBoundary(pt, st.entities, layerName = layer)
                if (boundaryPoly != null) {
                    commitSingleEntity(boundaryPoly, "BOUNDARY • Traced closed polyline (${boundaryPoly.vertices.size} vertices)")
                } else {
                    val err = "BOUNDARY • Closed boundary could not be determined at picked point"
                    _uiState.update { it.copy(commandPromptBanner = err, commandPanelState = CommandPanelState.Running(cmd, err, 0)) }
                    emitTransientNotification(err, NotificationCategory.WARNING)
                }
            }

            // --- MODIFY COMMANDS ---

            CadCommandId.MOVE -> {
                val sel = getUnlockedSelectedEntities()
                if (sel.isEmpty()) {
                    val hit = findNearestSelectableEntity(pt)
                    if (hit != null) {
                        val prompt = "MOVE • Pick base point"
                        _uiState.update { it.copy(selectedEntityIds = setOf(hit.id), commandPromptBanner = prompt, commandPanelState = CommandPanelState.Running(cmd, prompt, 0)) }
                    }
                } else if (pts.size >= 2) {
                    val dx = pts[1].x - pts[0].x
                    val dy = pts[1].y - pts[0].y
                    recordUndoSnapshot()
                    val selIds = sel.map { it.id }.toSet()
                    val moved = st.entities.map { if (it.id in selIds) it.translate(dx, dy) else it }
                    val updated = CadModificationEngine.updateAssociativeDimensions(moved)
                    val msg = "MOVE • Displaced ${sel.size} object(s) by (${fmt(dx)}, ${fmt(dy)})"
                    _uiState.update {
                        it.copy(
                            entities = updated,
                            activeCommand = CadCommandId.SELECT,
                            commandStepPoints = emptyList(),
                            commandPromptBanner = msg,
                            commandPanelState = CommandPanelState.Hidden
                        )
                    }
                    emitTransientNotification(msg, NotificationCategory.SUCCESS)
                } else {
                    val prompt = "MOVE • Pick destination point"
                    _uiState.update { it.copy(commandStepPoints = pts, commandPromptBanner = prompt, commandPanelState = CommandPanelState.Running(cmd, prompt, pts.size)) }
                }
            }

            CadCommandId.COPY -> {
                val sel = getUnlockedSelectedEntities()
                if (sel.isEmpty()) {
                    val hit = findNearestSelectableEntity(pt)
                    if (hit != null) {
                        val prompt = "COPY • Pick base point"
                        _uiState.update { it.copy(selectedEntityIds = setOf(hit.id), commandPromptBanner = prompt, commandPanelState = CommandPanelState.Running(cmd, prompt, 0)) }
                    }
                } else if (pts.size >= 2) {
                    val dx = pts.last().x - pts.first().x
                    val dy = pts.last().y - pts.first().y
                    recordUndoSnapshot()
                    val clones = sel.map { it.withNewId().translate(dx, dy) }
                    val prompt = "COPY • Placed ${clones.size} copy. Pick another destination or tap ✓ to finish"
                    _uiState.update {
                        it.copy(
                            entities = it.entities + clones,
                            commandStepPoints = listOf(pts.first()),
                            commandPromptBanner = prompt,
                            commandPanelState = CommandPanelState.Running(cmd, prompt, 1)
                        )
                    }
                    emitTransientNotification("COPY • Placed ${clones.size} copy", NotificationCategory.SUCCESS)
                } else {
                    val prompt = "COPY • Pick destination point"
                    _uiState.update { it.copy(commandStepPoints = pts, commandPromptBanner = prompt, commandPanelState = CommandPanelState.Running(cmd, prompt, pts.size)) }
                }
            }

            CadCommandId.ROTATE -> {
                val sel = getUnlockedSelectedEntities()
                if (sel.isEmpty()) {
                    val hit = findNearestSelectableEntity(pt)
                    if (hit != null) {
                        val prompt = "ROTATE • Pick base pivot point"
                        _uiState.update { it.copy(selectedEntityIds = setOf(hit.id), commandPromptBanner = prompt, commandPanelState = CommandPanelState.Running(cmd, prompt, 0)) }
                    }
                } else if (pts.size >= 2) {
                    val pivot = pts[0]
                    val angle = pivot.angleToDeg(pts[1])
                    recordUndoSnapshot()
                    val selIds = sel.map { it.id }.toSet()
                    val rotated = st.entities.map { if (it.id in selIds) it.rotate(pivot, angle) else it }
                    val updated = CadModificationEngine.updateAssociativeDimensions(rotated)
                    val msg = "ROTATE • Rotated ${sel.size} object(s) by ${fmt(angle)}°"
                    _uiState.update {
                        it.copy(
                            entities = updated,
                            activeCommand = CadCommandId.SELECT,
                            commandStepPoints = emptyList(),
                            commandPromptBanner = msg,
                            commandPanelState = CommandPanelState.Hidden
                        )
                    }
                    emitTransientNotification(msg, NotificationCategory.SUCCESS)
                } else {
                    val prompt = "ROTATE • Pick rotation angle point"
                    _uiState.update { it.copy(commandStepPoints = pts, commandPromptBanner = prompt, commandPanelState = CommandPanelState.Running(cmd, prompt, pts.size)) }
                }
            }

            CadCommandId.SCALE -> {
                val sel = getUnlockedSelectedEntities()
                if (sel.isEmpty()) {
                    val hit = findNearestSelectableEntity(pt)
                    if (hit != null) {
                        val prompt = "SCALE • Pick base point"
                        _uiState.update { it.copy(selectedEntityIds = setOf(hit.id), commandPromptBanner = prompt, commandPanelState = CommandPanelState.Running(cmd, prompt, 0)) }
                    }
                } else if (pts.size >= 2) {
                    val pivot = pts[0]
                    val dist = pivot.distanceTo(pts[1])
                    val factor = (dist / 25.0).coerceIn(0.1, 10.0)
                    recordUndoSnapshot()
                    val selIds = sel.map { it.id }.toSet()
                    val scaled = st.entities.map { if (it.id in selIds) it.scale(pivot, factor) else it }
                    val updated = CadModificationEngine.updateAssociativeDimensions(scaled)
                    val msg = "SCALE • Scaled ${sel.size} object(s) by ${fmt(factor)}x"
                    _uiState.update {
                        it.copy(
                            entities = updated,
                            activeCommand = CadCommandId.SELECT,
                            commandStepPoints = emptyList(),
                            commandPromptBanner = msg,
                            commandPanelState = CommandPanelState.Hidden
                        )
                    }
                    emitTransientNotification(msg, NotificationCategory.SUCCESS)
                } else {
                    val prompt = "SCALE • Pick scale factor point"
                    _uiState.update { it.copy(commandStepPoints = pts, commandPromptBanner = prompt, commandPanelState = CommandPanelState.Running(cmd, prompt, pts.size)) }
                }
            }

            CadCommandId.MIRROR -> {
                val sel = getUnlockedSelectedEntities()
                if (sel.isEmpty()) {
                    val hit = findNearestSelectableEntity(pt)
                    if (hit != null) {
                        val prompt = "MIRROR • Pick first point of mirror line"
                        _uiState.update { it.copy(selectedEntityIds = setOf(hit.id), commandPromptBanner = prompt, commandPanelState = CommandPanelState.Running(cmd, prompt, 0)) }
                    }
                } else if (pts.size >= 2) {
                    recordUndoSnapshot()
                    val mirrored = sel.map { it.withNewId().mirror(pts[0], pts[1]) }
                    val msg = "MIRROR • Mirrored ${mirrored.size} object(s)"
                    _uiState.update {
                        it.copy(
                            entities = it.entities + mirrored,
                            activeCommand = CadCommandId.SELECT,
                            commandStepPoints = emptyList(),
                            commandPromptBanner = msg,
                            commandPanelState = CommandPanelState.Hidden
                        )
                    }
                    emitTransientNotification(msg, NotificationCategory.SUCCESS)
                } else {
                    val prompt = "MIRROR • Pick second point of mirror axis"
                    _uiState.update { it.copy(commandStepPoints = pts, commandPromptBanner = prompt, commandPanelState = CommandPanelState.Running(cmd, prompt, pts.size)) }
                }
            }

            CadCommandId.OFFSET -> {
                if (pts.size == 1) {
                    val hit = findNearestSelectableEntity(pts[0])
                    if (hit != null) {
                        val prompt = "OFFSET (d=${fmt(st.commandParameterValue)}) • Pick side to offset"
                        _uiState.update {
                            it.copy(
                                selectedEntityIds = setOf(hit.id),
                                commandStepPoints = pts,
                                commandPromptBanner = prompt,
                                commandPanelState = CommandPanelState.Running(cmd, prompt, pts.size)
                            )
                        }
                    }
                } else if (pts.size >= 2) {
                    val target = st.entities.firstOrNull { it.id in st.selectedEntityIds }
                    if (target != null) {
                        val offsetEnt = CadModificationEngine.offsetEntity(target, st.commandParameterValue, pts[1])
                        if (offsetEnt != null) {
                            recordUndoSnapshot()
                            val msg = "OFFSET • Created parallel offset at d=${fmt(st.commandParameterValue)}"
                            _uiState.update {
                                it.copy(
                                    entities = it.entities + offsetEnt,
                                    commandStepPoints = emptyList(),
                                    commandPromptBanner = msg,
                                    commandPanelState = CommandPanelState.Running(cmd, msg, 0)
                                )
                            }
                            emitTransientNotification(msg, NotificationCategory.SUCCESS)
                        } else {
                            val err = "OFFSET • Invalid offset radius/distance"
                            _uiState.update { it.copy(commandStepPoints = emptyList(), commandPromptBanner = err) }
                            emitTransientNotification(err, NotificationCategory.WARNING)
                        }
                    }
                }
            }

            CadCommandId.TRIM -> {
                val hit = findNearestSelectableEntity(pt)
                if (hit != null) {
                    recordUndoSnapshot()
                    val trimmed = CadModificationEngine.trimEntityAtPick(hit, pt, st.entities)
                    val remaining = st.entities.filterNot { it.id == hit.id } + trimmed
                    val msg = "TRIM • Trimmed ${hit.typeName}"
                    _uiState.update {
                        it.copy(
                            entities = CadModificationEngine.updateAssociativeDimensions(remaining),
                            commandStepPoints = emptyList(),
                            commandPromptBanner = msg,
                            commandPanelState = CommandPanelState.Running(cmd, msg, 0)
                        )
                    }
                    emitTransientNotification(msg, NotificationCategory.SUCCESS)
                }
            }

            CadCommandId.EXTEND -> {
                val hit = findNearestSelectableEntity(pt)
                if (hit != null) {
                    recordUndoSnapshot()
                    val extended = CadModificationEngine.extendEntityAtPick(hit, pt, st.entities)
                    val updated = st.entities.map { if (it.id == hit.id) extended else it }
                    val msg = "EXTEND • Extended ${hit.typeName} to boundary"
                    _uiState.update {
                        it.copy(
                            entities = CadModificationEngine.updateAssociativeDimensions(updated),
                            commandStepPoints = emptyList(),
                            commandPromptBanner = msg,
                            commandPanelState = CommandPanelState.Running(cmd, msg, 0)
                        )
                    }
                    emitTransientNotification(msg, NotificationCategory.SUCCESS)
                }
            }

            CadCommandId.FILLET -> {
                val pickTolerance = (48f / st.viewport.pixelsPerModelUnit.coerceAtLeast(0.1f)).toDouble().coerceAtLeast(10.0)
                if (pts.size == 1) {
                    val hit1 = CadModificationEngine.hasLinearEdgeAtPick(st.entities, pts[0], pickTolerance)
                    if (hit1 != null) {
                        val prompt = "FILLET (R=${fmt(st.commandParameterValue)}) • First edge picked (${hit1.typeName}). Now pick second intersecting edge"
                        _uiState.update {
                            it.copy(
                                selectedEntityIds = setOf(hit1.id),
                                commandStepPoints = pts,
                                commandPromptBanner = prompt,
                                commandPanelState = CommandPanelState.Running(cmd, prompt, pts.size)
                            )
                        }
                    } else {
                        val prompt = "FILLET (R=${fmt(st.commandParameterValue)}) • Move cursor onto first line/rectangle edge and tap PICK PT"
                        _uiState.update {
                            it.copy(
                                commandStepPoints = emptyList(),
                                commandPromptBanner = prompt,
                                commandPanelState = CommandPanelState.Running(cmd, prompt, 0)
                            )
                        }
                    }
                } else if (pts.size >= 2) {
                    val filletedEntities = CadModificationEngine.filletAtPicks(
                        entities = st.entities,
                        pick1 = pts[0],
                        pick2 = pts[1],
                        radius = st.commandParameterValue,
                        pickTolerance = pickTolerance,
                        trimSegments = true
                    )
                    if (filletedEntities != null) {
                        recordUndoSnapshot()
                        val updated = CadModificationEngine.updateAssociativeDimensions(filletedEntities)
                        val msg = "FILLET • Created tangent fillet with R=${fmt(st.commandParameterValue)}"
                        _uiState.update {
                            it.copy(
                                entities = updated,
                                selectedEntityIds = emptySet(),
                                commandStepPoints = emptyList(),
                                commandPromptBanner = msg,
                                commandPanelState = CommandPanelState.Running(cmd, msg, 0)
                            )
                        }
                        emitTransientNotification(msg, NotificationCategory.SUCCESS)
                    } else {
                        val err = "FILLET • Could not fillet selected edges with R=${fmt(st.commandParameterValue)} (ensure edges are non-parallel and long enough)"
                        _uiState.update {
                            it.copy(
                                commandStepPoints = emptyList(),
                                commandPromptBanner = err,
                                commandPanelState = CommandPanelState.Running(cmd, err, 0)
                            )
                        }
                        emitTransientNotification(err, NotificationCategory.WARNING)
                    }
                }
            }

            CadCommandId.CHAMFER -> {
                val pickTolerance = (48f / st.viewport.pixelsPerModelUnit.coerceAtLeast(0.1f)).toDouble().coerceAtLeast(10.0)
                if (pts.size == 1) {
                    val hit1 = CadModificationEngine.hasLinearEdgeAtPick(st.entities, pts[0], pickTolerance)
                    if (hit1 != null) {
                        val prompt = "CHAMFER (D=${fmt(st.commandParameterValue)}) • First edge picked (${hit1.typeName}). Now pick second intersecting edge"
                        _uiState.update {
                            it.copy(
                                selectedEntityIds = setOf(hit1.id),
                                commandStepPoints = pts,
                                commandPromptBanner = prompt,
                                commandPanelState = CommandPanelState.Running(cmd, prompt, pts.size)
                            )
                        }
                    } else {
                        val prompt = "CHAMFER (D=${fmt(st.commandParameterValue)}) • Move cursor onto first line/rectangle edge and tap PICK PT"
                        _uiState.update {
                            it.copy(
                                commandStepPoints = emptyList(),
                                commandPromptBanner = prompt,
                                commandPanelState = CommandPanelState.Running(cmd, prompt, 0)
                            )
                        }
                    }
                } else if (pts.size >= 2) {
                    val d = st.commandParameterValue.coerceAtLeast(0.5)
                    val chamferedEntities = CadModificationEngine.chamferAtPicks(
                        entities = st.entities,
                        pick1 = pts[0],
                        pick2 = pts[1],
                        distance1 = d,
                        distance2 = d,
                        pickTolerance = pickTolerance,
                        trimSegments = true
                    )
                    if (chamferedEntities != null) {
                        recordUndoSnapshot()
                        val updated = CadModificationEngine.updateAssociativeDimensions(chamferedEntities)
                        val msg = "CHAMFER • Applied ${fmt(d)} × ${fmt(d)} bevel"
                        _uiState.update {
                            it.copy(
                                entities = updated,
                                selectedEntityIds = emptySet(),
                                commandStepPoints = emptyList(),
                                commandPromptBanner = msg,
                                commandPanelState = CommandPanelState.Running(cmd, msg, 0)
                            )
                        }
                        emitTransientNotification(msg, NotificationCategory.SUCCESS)
                    } else {
                        val err = "CHAMFER • Could not chamfer selected edges with D=${fmt(d)}"
                        _uiState.update {
                            it.copy(
                                commandStepPoints = emptyList(),
                                commandPromptBanner = err,
                                commandPanelState = CommandPanelState.Running(cmd, err, 0)
                            )
                        }
                        emitTransientNotification(err, NotificationCategory.WARNING)
                    }
                }
            }

            CadCommandId.STRETCH -> {
                if (pts.size >= 3) {
                    val box = BoundingBox2D.fromPoints(listOf(pts[0], pts[1]))
                    val dx = pts[2].x - pts[1].x
                    val dy = pts[2].y - pts[1].y
                    recordUndoSnapshot()
                    val stretched = st.entities.map { CadModificationEngine.stretchEntity(it, box, dx, dy) }
                    val msg = "STRETCH • Stretched crossing region by (${fmt(dx)}, ${fmt(dy)})"
                    _uiState.update {
                        it.copy(
                            entities = CadModificationEngine.updateAssociativeDimensions(stretched),
                            activeCommand = CadCommandId.SELECT,
                            commandStepPoints = emptyList(),
                            commandPromptBanner = msg,
                            commandPanelState = CommandPanelState.Hidden
                        )
                    }
                    emitTransientNotification(msg, NotificationCategory.SUCCESS)
                } else {
                    val prompt = promptForCommand(cmd, pts.size, st.commandSubOption)
                    _uiState.update { it.copy(commandStepPoints = pts, commandPromptBanner = prompt, commandPanelState = CommandPanelState.Running(cmd, prompt, pts.size)) }
                }
            }

            CadCommandId.BREAK -> {
                if (pts.size >= 2) {
                    val target = findNearestSelectableEntity(pts[0])
                    if (target != null) {
                        recordUndoSnapshot()
                        val broken = CadModificationEngine.breakEntity(target, pts[0], pts[1])
                        val remaining = st.entities.filterNot { it.id == target.id } + broken
                        val msg = "BREAK • Split ${target.typeName}"
                        _uiState.update {
                            it.copy(
                                entities = remaining,
                                activeCommand = CadCommandId.SELECT,
                                commandStepPoints = emptyList(),
                                commandPromptBanner = msg,
                                commandPanelState = CommandPanelState.Hidden
                            )
                        }
                        emitTransientNotification(msg, NotificationCategory.SUCCESS)
                    }
                } else {
                    val prompt = "BREAK • Pick second break point"
                    _uiState.update { it.copy(commandStepPoints = pts, commandPromptBanner = prompt, commandPanelState = CommandPanelState.Running(cmd, prompt, pts.size)) }
                }
            }

            CadCommandId.ALIGN -> {
                val sel = getUnlockedSelectedEntities()
                if (sel.isNotEmpty() && pts.size >= 4) {
                    recordUndoSnapshot()
                    val aligned = CadModificationEngine.alignEntities(sel, pts[0], pts[1], pts[2], pts[3], scaleToFit = true)
                    val alignedMap = aligned.associateBy { it.id }
                    val updated = st.entities.map { alignedMap[it.id] ?: it }
                    val msg = "ALIGN • Aligned ${sel.size} object(s)"
                    _uiState.update {
                        it.copy(
                            entities = CadModificationEngine.updateAssociativeDimensions(updated),
                            activeCommand = CadCommandId.SELECT,
                            commandStepPoints = emptyList(),
                            commandPromptBanner = msg,
                            commandPanelState = CommandPanelState.Hidden
                        )
                    }
                    emitTransientNotification(msg, NotificationCategory.SUCCESS)
                } else {
                    val prompt = "ALIGN • Pick point ${pts.size + 1} of 4 (Src1 -> Dst1 -> Src2 -> Dst2)"
                    _uiState.update { it.copy(commandStepPoints = pts, commandPromptBanner = prompt, commandPanelState = CommandPanelState.Running(cmd, prompt, pts.size)) }
                }
            }

            CadCommandId.LENGTHEN -> {
                val line = findNearestEntityOfType<CadEntity.LineEntity>(pt)
                if (line != null) {
                    recordUndoSnapshot()
                    val lengthened = CadModificationEngine.lengthenEntity(line, pt, targetTotalLength = st.commandParameterValue)
                    val newLen = (lengthened as? CadEntity.LineEntity)?.length ?: st.commandParameterValue
                    val updated = st.entities.map { if (it.id == line.id) lengthened else it }
                    val msg = "LENGTHEN • Set total length to ${fmt(newLen)}"
                    _uiState.update {
                        it.copy(
                            entities = CadModificationEngine.updateAssociativeDimensions(updated),
                            commandStepPoints = emptyList(),
                            commandPromptBanner = msg,
                            commandPanelState = CommandPanelState.Running(cmd, msg, 0)
                        )
                    }
                    emitTransientNotification(msg, NotificationCategory.SUCCESS)
                }
            }

            // --- INQUIRY & DIMENSION COMMANDS ---

            CadCommandId.MEASURE -> {
                if (pts.size >= 2) {
                    val p1 = pts[0]
                    val p2 = pts[1]
                    val dist = p1.distanceTo(p2)
                    val dx = p2.x - p1.x
                    val dy = p2.y - p1.y
                    val ang = p1.angleToDeg(p2)
                    val msg = "DIST = ${st.precision.formatLinear(dist, st.storageUnit, st.displayUnit)} (ΔX=${fmt(dx)}, ΔY=${fmt(dy)}, ∠${fmt(ang)}°)"
                    _uiState.update {
                        it.copy(
                            commandStepPoints = emptyList(),
                            commandPromptBanner = msg,
                            commandPanelState = CommandPanelState.Running(cmd, msg, 0),
                            commandHistoryLog = (it.commandHistoryLog + msg).takeLast(30)
                        )
                    }
                    emitTransientNotification(msg, NotificationCategory.INFO)
                } else {
                    val prompt = "MEASURE (DIST) • Pick second point"
                    _uiState.update { it.copy(commandStepPoints = pts, commandPromptBanner = prompt, commandPanelState = CommandPanelState.Running(cmd, prompt, pts.size)) }
                }
            }

            CadCommandId.DIMLINEAR, CadCommandId.DIMALIGNED -> {
                if (pts.size >= 3) {
                    val p1 = pts[0]
                    val p2 = pts[1]
                    val dimPos = pts[2]
                    val type = when {
                        cmd == CadCommandId.DIMALIGNED -> DimensionType.ALIGNED
                        abs(dimPos.y - (p1.y + p2.y) * 0.5) >= abs(dimPos.x - (p1.x + p2.x) * 0.5) -> DimensionType.LINEAR_HORIZONTAL
                        else -> DimensionType.LINEAR_VERTICAL
                    }
                    commitSingleEntity(
                        CadEntity.DimensionEntity(
                            dimensionType = type,
                            anchor1 = p1,
                            anchor2 = p2,
                            dimensionLinePoint = dimPos,
                            isAutoGenerated = false
                        ),
                        "${cmd.commandName} • Placed manual dimension"
                    )
                } else {
                    val prompt = promptForCommand(cmd, pts.size, st.commandSubOption)
                    _uiState.update { it.copy(commandStepPoints = pts, commandPromptBanner = prompt, commandPanelState = CommandPanelState.Running(cmd, prompt, pts.size)) }
                }
            }

            CadCommandId.DIMANGULAR -> {
                if (pts.size >= 3) {
                    commitSingleEntity(
                        CadEntity.DimensionEntity(
                            dimensionType = DimensionType.ANGULAR,
                            angularVertex = pts[0],
                            anchor1 = pts[1],
                            anchor2 = pts[2],
                            dimensionLinePoint = pts[1].midpointTo(pts[2]),
                            isAutoGenerated = false
                        ),
                        "DIMANGULAR • Placed angular dimension"
                    )
                } else {
                    val prompt = "DIMANGULAR • Pick point ${pts.size + 1} (Vertex -> Arm 1 -> Arm 2)"
                    _uiState.update { it.copy(commandStepPoints = pts, commandPromptBanner = prompt, commandPanelState = CommandPanelState.Running(cmd, prompt, pts.size)) }
                }
            }

            CadCommandId.DIMRADIUS, CadCommandId.DIMDIAMETER -> {
                val circularFeature = findNearestArcOrCircleFeature(pt)
                if (circularFeature != null) {
                    val type = if (cmd == CadCommandId.DIMDIAMETER) DimensionType.DIAMETER else DimensionType.RADIUS
                    val shoulderDist = circularFeature.radius + (circularFeature.radius * 0.65).coerceIn(10.0, 22.0)
                    val landing = circularFeature.center.polarOffset(shoulderDist, circularFeature.outwardAngleDeg)
                    val prefix = if (type == DimensionType.DIAMETER) "⌀" else "R"
                    commitSingleEntity(
                        CadEntity.DimensionEntity(
                            dimensionType = type,
                            anchor1 = circularFeature.center,
                            anchor2 = circularFeature.rimContactPoint,
                            dimensionLinePoint = landing,
                            sourceEntityIds = listOf(circularFeature.sourceEntityId),
                            isAutoGenerated = false
                        ),
                        "${cmd.commandName} • Placed ISO/ASME ${type.label} dimension ($prefix${fmt(if (type == DimensionType.DIAMETER) circularFeature.radius * 2 else circularFeature.radius)})"
                    )
                } else {
                    val prompt = "${cmd.commandName} • Move cursor over a Circle, Arc, or Filleted Corner and tap PICK PT"
                    _uiState.update { it.copy(commandStepPoints = emptyList(), commandPromptBanner = prompt, commandPanelState = CommandPanelState.Running(cmd, prompt, 0)) }
                    emitTransientNotification(prompt, NotificationCategory.WARNING)
                }
            }

            CadCommandId.DIMORDINATE -> {
                if (pts.size >= 2) {
                    val feat = pts[0]
                    val landing = pts[1]
                    val isXDatum = abs(landing.y - feat.y) >= abs(landing.x - feat.x)
                    commitSingleEntity(
                        CadEntity.DimensionEntity(
                            dimensionType = if (isXDatum) DimensionType.ORDINATE_X else DimensionType.ORDINATE_Y,
                            anchor1 = feat,
                            anchor2 = landing,
                            dimensionLinePoint = landing,
                            isAutoGenerated = false
                        ),
                        "DIMORDINATE • Placed ${if (isXDatum) "X-Datum" else "Y-Datum"} ordinate"
                    )
                } else {
                    val prompt = "DIMORDINATE • Pick leader landing point"
                    _uiState.update { it.copy(commandStepPoints = pts, commandPromptBanner = prompt, commandPanelState = CommandPanelState.Running(cmd, prompt, pts.size)) }
                }
            }

            CadCommandId.LEADER -> {
                if (pts.size >= 2) {
                    val tip = pts[0]
                    val sh = pts[1]
                    val end = sh.offset(if (sh.x >= tip.x) 14.0 else -14.0, 0.0)
                    commitSingleEntity(
                        CadEntity.LeaderEntity(
                            arrowTip = tip,
                            shoulderPoint = sh,
                            landingEnd = end,
                            text = "TOL ±0.02 mm"
                        ),
                        "MLEADER • Placed technical leader callout"
                    )
                } else {
                    val prompt = "MLEADER • Pick leader shoulder point"
                    _uiState.update { it.copy(commandStepPoints = pts, commandPromptBanner = prompt, commandPanelState = CommandPanelState.Running(cmd, prompt, pts.size)) }
                }
            }

            CadCommandId.TEXT -> {
                commitSingleEntity(
                    CadEntity.TextEntity(
                        position = pt,
                        text = "NOTE: DEBURR ALL SHARP EDGES",
                        height = 3.5
                    ),
                    "MTEXT • Placed technical note at (${fmt(pt.x)}, ${fmt(pt.y)})"
                )
            }

            CadCommandId.CENTERMARK -> {
                val circularFeature = findNearestArcOrCircleFeature(pt)
                if (circularFeature != null) {
                    commitSingleEntity(
                        CadEntity.CenterMarkEntity(
                            center = circularFeature.center,
                            radius = circularFeature.radius,
                            sourceEntityId = circularFeature.sourceEntityId
                        ),
                        "CENTERMARK • Placed associative center mark"
                    )
                } else {
                    val prompt = "CENTERMARK • Pick a Circle, Arc, or Filleted Corner"
                    _uiState.update { it.copy(commandPromptBanner = prompt, commandPanelState = CommandPanelState.Running(cmd, prompt, 0)) }
                    emitTransientNotification(prompt, NotificationCategory.WARNING)
                }
            }

            else -> Unit
        }
        _uiState.update { stAfter ->
            val nextActionPrompt = promptForCommand(
                stAfter.activeCommand,
                stAfter.commandStepPoints.size,
                stAfter.commandSubOption
            )
            stAfter.copy(activeActionPrompt = nextActionPrompt)
        }
        syncPolarAndActiveSessionState()
    }

    fun finishCurrentCommand(closeLoop: Boolean = false) {
        val st = _uiState.value
        val pts = st.commandStepPoints
        when (st.activeCommand) {
            CadCommandId.LINE -> {
                if (closeLoop && pts.size >= 3) {
                    recordUndoSnapshot()
                    val closeSeg = CadEntity.LineEntity(
                        layerName = st.activeLayerName,
                        start = pts.last(),
                        end = pts.first()
                    )
                    val msg = "LINE (Closed) • Closed loop to start point"
                    _uiState.update {
                        it.copy(
                            entities = it.entities + closeSeg,
                            activeCommand = CadCommandId.SELECT,
                            commandStepPoints = emptyList(),
                            polarPreview = null,
                            commandPromptBanner = msg,
                            commandPanelState = CommandPanelState.Hidden
                        )
                    }
                    emitTransientNotification(msg, NotificationCategory.SUCCESS)
                } else {
                    val msg = "LINE • Command completed"
                    _uiState.update {
                        it.copy(
                            activeCommand = CadCommandId.SELECT,
                            commandStepPoints = emptyList(),
                            polarPreview = null,
                            commandPromptBanner = msg,
                            commandPanelState = CommandPanelState.Hidden
                        )
                    }
                    emitTransientNotification(msg, NotificationCategory.INFO)
                }
            }
            CadCommandId.POLYLINE -> {
                if (pts.size >= 2) {
                    val bulge = if (st.commandSubOption == "Arc") 0.5 else 0.0
                    val poly = CadEntity.PolylineEntity(
                        layerName = st.activeLayerName,
                        vertices = pts.mapIndexed { idx, p ->
                            PolylineVertex(p, if (idx < pts.lastIndex) bulge else 0.0)
                        },
                        isClosed = closeLoop || st.commandSubOption == "Close"
                    )
                    commitSingleEntity(poly, "POLYLINE • Created ${pts.size}-vertex polyline", returnToIdle = true)
                } else {
                    cancelCurrentCommand()
                }
            }
            CadCommandId.SPLINE -> {
                if (pts.size >= 2) {
                    val spline = CadEntity.SplineEntity(
                        layerName = st.activeLayerName,
                        controlPoints = pts,
                        isClosed = closeLoop
                    )
                    commitSingleEntity(spline, "SPLINE • Created smooth spline through ${pts.size} points", returnToIdle = true)
                } else {
                    cancelCurrentCommand()
                }
            }
            else -> {
                val msg = "READY • ${st.activeCommand.commandName} finished"
                _uiState.update {
                    it.copy(
                        activeCommand = CadCommandId.SELECT,
                        commandStepPoints = emptyList(),
                        polarPreview = null,
                        selectionBoxDrag = null,
                        activeActionPrompt = promptForCommand(CadCommandId.SELECT, 0, "Single"),
                        commandPromptBanner = msg,
                        commandPanelState = CommandPanelState.Hidden
                    )
                }
                emitTransientNotification(msg, NotificationCategory.INFO)
            }
        }
    }

    fun cancelCurrentCommand() {
        val msg = "CANCELLED • Ready for command or controller action"
        _uiState.update {
            it.copy(
                activeCommand = CadCommandId.SELECT,
                commandStepPoints = emptyList(),
                polarPreview = null,
                selectionBoxDrag = null,
                activeActionPrompt = promptForCommand(CadCommandId.SELECT, 0, "Single"),
                commandPromptBanner = msg,
                commandPanelState = CommandPanelState.Hidden
            )
        }
        emitTransientNotification(msg, NotificationCategory.INFO)
    }

    // --- Selection Commands (Section 14.3 — No Sheet Taps Required) ---

    @Suppress("UNUSED_PARAMETER")
    fun onSelectionDragStart(modelPt: Point2D) {
        // Intentional no-op per Section 14.1 navigation-only sheet contract.
    }

    @Suppress("UNUSED_PARAMETER")
    fun onSelectionDragUpdate(modelPt: Point2D) {
        // Intentional no-op per Section 14.1 navigation-only sheet contract.
    }

    fun onSelectionDragEnd() {
        // Intentional no-op per Section 14.1 navigation-only sheet contract.
    }

    fun selectAllEntities() {
        val st = _uiState.value
        val visibleLayers = st.layers.filter { it.isVisible && !it.isFrozen }.map { it.name }.toSet()
        val allIds = st.entities
            .filter { it.layerName in visibleLayers }
            .filter { st.isolatedEntityIds == null || it.id in st.isolatedEntityIds }
            .map { it.id }
            .toSet()
        val prevSel = if (st.selectedEntityIds.isNotEmpty()) st.selectedEntityIds else st.previousSelectedEntityIds
        val msg = "SELECT ALL • Selected ${allIds.size} object(s)"
        _uiState.update {
            it.copy(
                selectedEntityIds = allIds,
                previousSelectedEntityIds = prevSel,
                commandPromptBanner = msg
            )
        }
        emitTransientNotification(msg, NotificationCategory.INFO)
    }

    fun selectPreviousEntities() {
        val st = _uiState.value
        val validIds = st.entities.map { it.id }.toSet()
        val restored = st.previousSelectedEntityIds.intersect(validIds)
        val msg = "SELECT PREVIOUS • Restored ${restored.size} object(s)"
        _uiState.update {
            it.copy(
                selectedEntityIds = restored,
                commandPromptBanner = msg
            )
        }
        emitTransientNotification(msg, NotificationCategory.INFO)
    }

    fun selectEntitiesByLayer(layerName: String) {
        val st = _uiState.value
        val matched = st.entities.filter { it.layerName == layerName }.map { it.id }.toSet()
        val prevSel = if (st.selectedEntityIds.isNotEmpty()) st.selectedEntityIds else st.previousSelectedEntityIds
        val msg = "SELECT LAYER '$layerName' • Selected ${matched.size} object(s)"
        _uiState.update {
            it.copy(
                selectedEntityIds = matched,
                previousSelectedEntityIds = prevSel,
                commandPromptBanner = msg
            )
        }
        emitTransientNotification(msg, NotificationCategory.INFO)
    }

    fun selectEntitiesByType(typeName: String) {
        val st = _uiState.value
        val visibleLayers = st.layers.filter { it.isVisible && !it.isFrozen }.map { it.name }.toSet()
        val matched = st.entities
            .filter { it.layerName in visibleLayers && it.typeName.equals(typeName, true) }
            .map { it.id }
            .toSet()
        val prevSel = if (st.selectedEntityIds.isNotEmpty()) st.selectedEntityIds else st.previousSelectedEntityIds
        val msg = "SELECT TYPE '$typeName' • Selected ${matched.size} object(s)"
        _uiState.update {
            it.copy(
                selectedEntityIds = matched,
                previousSelectedEntityIds = prevSel,
                commandPromptBanner = msg
            )
        }
        emitTransientNotification(msg, NotificationCategory.INFO)
    }

    fun clearSelection() {
        val st = _uiState.value
        val prevSel = if (st.selectedEntityIds.isNotEmpty()) st.selectedEntityIds else st.previousSelectedEntityIds
        val msg = "CLEAR SELECTION • 0 objects selected"
        _uiState.update {
            it.copy(
                selectedEntityIds = emptySet(),
                previousSelectedEntityIds = prevSel,
                selectionBoxDrag = null,
                commandPromptBanner = msg
            )
        }
        emitTransientNotification(msg, NotificationCategory.INFO)
    }

    fun deleteSelectedEntities() {
        val sel = getUnlockedSelectedEntities().map { it.id }.toSet()
        if (sel.isEmpty()) {
            emitTransientNotification("ERASE • No unlocked entities selected", NotificationCategory.WARNING)
            return
        }
        recordUndoSnapshot()
        val msg = "ERASE • Removed ${sel.size} object(s)"
        _uiState.update {
            it.copy(
                entities = it.entities.filterNot { e -> e.id in sel },
                selectedEntityIds = emptySet(),
                commandPromptBanner = msg,
                commandPanelState = CommandPanelState.Hidden
            )
        }
        emitTransientNotification(msg, NotificationCategory.SUCCESS)
    }

    // --- Layer, Block, Properties & Constraint Actions ---

    fun setActiveLayer(layerName: String) {
        _uiState.update { it.copy(activeLayerName = layerName) }
    }

    fun addLayer(name: String, colorHex: Long, lineType: CadLineType, weightMm: Float) {
        val st = _uiState.value
        if (st.layers.any { it.name.equals(name, true) }) return
        recordUndoSnapshot()
        val newLayer = CadLayer(name = name, colorHex = colorHex, lineType = lineType, lineWeightMm = weightMm)
        val msg = "LAYER • Added and activated layer '$name'"
        _uiState.update {
            it.copy(
                layers = it.layers + newLayer,
                activeLayerName = name,
                commandPromptBanner = msg
            )
        }
        emitTransientNotification(msg, NotificationCategory.SUCCESS)
    }

    fun updateLayer(updated: CadLayer) {
        recordUndoSnapshot()
        _uiState.update { st ->
            st.copy(layers = st.layers.map { if (it.id == updated.id) updated else it })
        }
    }

    fun changeSelectedEntitiesLayer(targetLayer: String) {
        val sel = getUnlockedSelectedEntities().map { it.id }.toSet()
        if (sel.isEmpty()) return
        recordUndoSnapshot()
        val msg = "PROPERTIES • Moved ${sel.size} object(s) to layer '$targetLayer'"
        _uiState.update { st ->
            st.copy(
                entities = st.entities.map { if (it.id in sel) it.withStyle(layerName = targetLayer) else it },
                commandPromptBanner = msg
            )
        }
        emitTransientNotification(msg, NotificationCategory.SUCCESS)
    }

    fun changeSelectedEntitiesLineType(lineType: CadLineType) {
        val sel = getUnlockedSelectedEntities().map { it.id }.toSet()
        if (sel.isEmpty()) return
        recordUndoSnapshot()
        val msg = "PROPERTIES • Set linetype to ${lineType.label}"
        _uiState.update { st ->
            st.copy(
                entities = st.entities.map { if (it.id in sel) it.withStyle(lineTypeOverride = lineType) else it },
                commandPromptBanner = msg
            )
        }
        emitTransientNotification(msg, NotificationCategory.SUCCESS)
    }

    fun updateCircleRadius(entityId: String, newRadius: Double) {
        val r = newRadius.coerceAtLeast(0.1)
        recordUndoSnapshot()
        val updated = _uiState.value.entities.map { ent ->
            when {
                ent.id == entityId && ent is CadEntity.CircleEntity -> ent.copy(radius = r)
                ent.id == entityId && ent is CadEntity.ArcEntity -> ent.copy(radius = r)
                else -> ent
            }
        }
        val msg = "PROPERTIES • Updated radius to ${fmt(r)} (associative dimensions updated)"
        _uiState.update {
            it.copy(
                entities = CadModificationEngine.updateAssociativeDimensions(updated),
                commandPromptBanner = msg
            )
        }
        emitTransientNotification(msg, NotificationCategory.SUCCESS)
    }

    fun applyGeometricConstraintToSelected(type: ConstraintType) {
        val sel = getUnlockedSelectedEntities()
        if (sel.isEmpty()) return
        recordUndoSnapshot()
        val rec = GeometricConstraintRecord(
            type = type,
            primaryEntityId = sel[0].id,
            secondaryEntityId = sel.getOrNull(1)?.id
        )
        val solved = CadGeometryKernel.applyGeometricConstraint(rec, _uiState.value.entities)
        val withDims = CadModificationEngine.updateAssociativeDimensions(solved)
        val msg = "CONSTRAINT (${type.label}) • Solved geometric constraint and updated associative dimensions"
        _uiState.update {
            it.copy(
                entities = withDims,
                commandPromptBanner = msg
            )
        }
        emitTransientNotification(msg, NotificationCategory.SUCCESS)
    }

    fun createBlockFromSelection(blockName: String) {
        val sel = getUnlockedSelectedEntities()
        if (sel.isEmpty()) return
        recordUndoSnapshot()
        val bounds = sel.map { it.boundingBox() }.reduce { a, b -> a.union(b) }
        val def = BlockDefinition(name = blockName, basePoint = bounds.center, entities = sel)
        val msg = "BLOCK • Created reusable block '$blockName' (${sel.size} entities)"
        _uiState.update {
            it.copy(
                blocks = it.blocks + (blockName to def),
                commandPromptBanner = msg
            )
        }
        emitTransientNotification(msg, NotificationCategory.SUCCESS)
    }

    fun insertBlockAtCursor(blockName: String) {
        val st = _uiState.value
        if (!st.blocks.containsKey(blockName)) return
        commitSingleEntity(
            CadEntity.BlockInsertEntity(
                layerName = st.activeLayerName,
                blockName = blockName,
                insertionPoint = st.snappedCursorModelPos
            ),
            "INSERT • Placed block '$blockName' at cursor"
        )
    }

    // --- Rubber-Band Preview Generation ---

    fun computePreviewEntities(): List<CadEntity> {
        val st = _uiState.value
        val pts = st.commandStepPoints
        val cur = st.snappedCursorModelPos

        // Commands that can show a preview before a point is picked (or from selection)
        if (pts.isEmpty()) {
            return when (st.activeCommand) {
                CadCommandId.XLINE -> when (st.commandSubOption) {
                    "Horizontal" -> listOf(CadEntity.XLineEntity(rootPoint = cur, angleDeg = 0.0))
                    "Vertical" -> listOf(CadEntity.XLineEntity(rootPoint = cur, angleDeg = 90.0))
                    else -> emptyList()
                }
                CadCommandId.DONUT -> {
                    val od = st.commandParameterValue.coerceAtLeast(2.0)
                    listOf(CadEntity.DonutEntity(center = cur, innerDiameter = od * 0.5, outerDiameter = od))
                }
                CadCommandId.OFFSET -> {
                    val target = st.entities.firstOrNull { it.id in st.selectedEntityIds }
                    if (target != null) {
                        listOfNotNull(CadModificationEngine.offsetEntity(target, st.commandParameterValue, cur))
                    } else emptyList()
                }
                else -> emptyList()
            }
        }

        return when (st.activeCommand) {
            CadCommandId.LINE -> listOf(CadEntity.LineEntity(start = pts.last(), end = cur))
            CadCommandId.POLYLINE -> {
                val activeBulge = if (st.commandSubOption == "Arc") 0.45 else 0.0
                val polyVertices = pts.mapIndexed { idx, p ->
                    val b = if (idx == pts.lastIndex) activeBulge else 0.0
                    PolylineVertex(p, bulge = b)
                } + PolylineVertex(cur)
                listOf(
                    CadEntity.PolylineEntity(
                        vertices = polyVertices,
                        isClosed = st.commandSubOption == "Close" && pts.size >= 2
                    )
                )
            }
            CadCommandId.CIRCLE -> when (st.commandSubOption) {
                "2P" -> listOf(
                    CadEntity.CircleEntity(
                        center = pts.first().midpointTo(cur),
                        radius = (pts.first().distanceTo(cur) * 0.5).coerceAtLeast(0.5)
                    )
                )
                "3P" -> if (pts.size >= 2) {
                    listOfNotNull(CadGeometryKernel.circleFrom3Points(pts[0], pts[1], cur))
                } else {
                    listOf(
                        CadEntity.CircleEntity(
                            center = pts.first().midpointTo(cur),
                            radius = (pts.first().distanceTo(cur) * 0.5).coerceAtLeast(0.5)
                        )
                    )
                }
                "TTR" -> {
                    val l1 = findNearestEntityOfType<CadEntity.LineEntity>(pts[0])
                    val l2 = findNearestEntityOfType<CadEntity.LineEntity>(cur)
                    val r = st.commandParameterValue.coerceAtLeast(1.0)
                    if (l1 != null && l2 != null && l1.id != l2.id) {
                        listOfNotNull(CadGeometryKernel.circleTanTanRadius(l1, l2, r, cur))
                    } else {
                        listOf(CadEntity.CircleEntity(center = pts.first(), radius = r))
                    }
                }
                "Diameter" -> listOf(
                    CadEntity.CircleEntity(
                        center = pts.first(),
                        radius = (pts.first().distanceTo(cur) * 0.5).coerceAtLeast(0.5)
                    )
                )
                else -> listOf(
                    CadEntity.CircleEntity(
                        center = pts.first(),
                        radius = pts.first().distanceTo(cur).coerceAtLeast(0.5)
                    )
                )
            }
            CadCommandId.ARC -> {
                if (pts.size >= 2) {
                    if (st.commandSubOption == "SCE") {
                        val startPt = pts[0]
                        val centerPt = pts[1]
                        val r = centerPt.distanceTo(startPt).coerceAtLeast(0.5)
                        val a1 = centerPt.angleToDeg(startPt)
                        val a2 = centerPt.angleToDeg(cur)
                        val sweep = ((a2 - a1) + 360.0) % 360.0
                        listOf(
                            CadEntity.ArcEntity(
                                center = centerPt,
                                radius = r,
                                startAngleDeg = a1,
                                sweepAngleDeg = sweep
                            )
                        )
                    } else {
                        listOfNotNull(CadGeometryKernel.arcFrom3Points(pts[0], pts[1], cur))
                    }
                } else {
                    listOf(CadEntity.LineEntity(start = pts.first(), end = cur))
                }
            }
            CadCommandId.RECTANGLE -> {
                val corner1 = pts.first()
                val corner2 = if (st.commandSubOption == "Square") {
                    val dx = cur.x - corner1.x
                    val dy = cur.y - corner1.y
                    val side = maxOf(abs(dx), abs(dy)).coerceAtLeast(1.0)
                    Point2D(
                        x = corner1.x + if (dx >= 0.0) side else -side,
                        y = corner1.y + if (dy >= 0.0) side else -side
                    )
                } else {
                    cur
                }
                listOf(
                    CadGeometryKernel.createRectanglePolyline(
                        corner1 = corner1,
                        corner2 = corner2,
                        filletRadius = if (st.commandSubOption == "Fillet") st.commandParameterValue else 0.0,
                        chamferDistance = if (st.commandSubOption == "Chamfer") st.commandParameterValue else 0.0
                    )
                )
            }
            CadCommandId.POLYGON -> listOf(
                CadGeometryKernel.createRegularPolygon(
                    center = pts.first(),
                    radius = pts.first().distanceTo(cur).coerceAtLeast(1.0),
                    sides = st.commandParameterValue.toInt().coerceIn(3, 64),
                    circumscribed = st.commandSubOption == "Circumscribed",
                    rotationDeg = pts.first().angleToDeg(cur)
                )
            )
            CadCommandId.ELLIPSE -> if (pts.size >= 2) {
                listOf(
                    CadEntity.EllipseEntity(
                        center = pts[0],
                        majorRadius = pts[0].distanceTo(pts[1]).coerceAtLeast(1.0),
                        minorRadius = pts[0].distanceTo(cur).coerceAtLeast(0.5),
                        rotationAngleDeg = pts[0].angleToDeg(pts[1])
                    )
                )
            } else {
                listOf(
                    CadEntity.CircleEntity(
                        center = pts.first(),
                        radius = pts.first().distanceTo(cur).coerceAtLeast(0.5)
                    )
                )
            }
            CadCommandId.RAY -> listOf(
                CadEntity.RayEntity(basePoint = pts.first(), angleDeg = pts.first().angleToDeg(cur))
            )
            CadCommandId.XLINE -> listOf(
                CadEntity.XLineEntity(rootPoint = pts.first(), angleDeg = pts.first().angleToDeg(cur))
            )
            CadCommandId.OFFSET -> {
                val target = st.entities.firstOrNull { it.id in st.selectedEntityIds }
                    ?: findNearestSelectableEntity(pts.first())
                if (target != null) {
                    listOfNotNull(CadModificationEngine.offsetEntity(target, st.commandParameterValue, cur))
                } else emptyList()
            }
            CadCommandId.FILLET -> {
                val pickTolerance = (48f / st.viewport.pixelsPerModelUnit.coerceAtLeast(0.1f)).toDouble().coerceAtLeast(10.0)
                val existingIds = st.entities.map { it.id }.toSet()
                val filleted = CadModificationEngine.filletAtPicks(
                    entities = st.entities,
                    pick1 = pts.first(),
                    pick2 = cur,
                    radius = st.commandParameterValue,
                    pickTolerance = pickTolerance,
                    trimSegments = true
                )
                filleted?.filterNot { it.id in existingIds } ?: emptyList()
            }
            CadCommandId.CHAMFER -> {
                val pickTolerance = (48f / st.viewport.pixelsPerModelUnit.coerceAtLeast(0.1f)).toDouble().coerceAtLeast(10.0)
                val existingIds = st.entities.map { it.id }.toSet()
                val d = st.commandParameterValue.coerceAtLeast(0.5)
                val chamfered = CadModificationEngine.chamferAtPicks(
                    entities = st.entities,
                    pick1 = pts.first(),
                    pick2 = cur,
                    distance1 = d,
                    distance2 = d,
                    pickTolerance = pickTolerance,
                    trimSegments = true
                )
                chamfered?.filterNot { it.id in existingIds } ?: emptyList()
            }
            CadCommandId.SPLINE -> listOf(
                CadEntity.SplineEntity(controlPoints = pts + cur)
            )
            else -> emptyList()
        }
    }

    // --- DXF / PDF / SVG Export & Project Persistence ---

    fun currentDxfPayload(): DxfDocumentPayload {
        val st = _uiState.value
        val canonicalTitle = CadPcadPersistence.canonicalBasename(st.currentProjectName)
        return DxfDocumentPayload(
            storageUnit = st.storageUnit,
            displayUnit = st.displayUnit,
            layers = st.layers,
            entities = st.entities,
            blocks = st.blocks.values.toList(),
            sheetSetup = st.sheetSetup.copy(titleBlockTitle = canonicalTitle),
            precision = st.precision
        )
    }

    fun exportAndShareDxf(context: Context) {
        val payload = currentDxfPayload()
        val dxfString = CadDxfInterchange.exportToDxf(payload)
        val baseName = CadPcadPersistence.canonicalBasename(_uiState.value.currentProjectName)
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "$baseName.dxf")
        file.writeText(dxfString)
        emitTransientNotification("DXF EXPORT • Exported AC1015 DXF '$baseName.dxf' (${payload.entities.size} entities)", NotificationCategory.SUCCESS)
        CadDxfInterchange.shareExportedFile(context, file, "application/dxf")
    }

    fun exportAndSharePdf(context: Context) {
        val baseName = CadPcadPersistence.canonicalBasename(_uiState.value.currentProjectName)
        val payload = currentDxfPayload()
        val file = CadDxfInterchange.exportToPdfFile(context, "$baseName.pdf", payload)
        emitTransientNotification("PDF EXPORT • Plotted '$baseName.pdf' (TITLE: $baseName)", NotificationCategory.SUCCESS)
        CadDxfInterchange.shareExportedFile(context, file, "application/pdf")
    }

    fun exportAndShareSvg(context: Context) {
        val payload = currentDxfPayload()
        val svgString = CadDxfInterchange.exportToSvg(payload)
        val baseName = CadPcadPersistence.canonicalBasename(_uiState.value.currentProjectName)
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "$baseName.svg")
        file.writeText(svgString)
        emitTransientNotification("SVG EXPORT • Exported vector SVG '$baseName.svg'", NotificationCategory.SUCCESS)
        CadDxfInterchange.shareExportedFile(context, file, "image/svg+xml")
    }

    fun importDxfString(dxfText: String) {
        // Also support opening native .pcad JSON if passed to import
        val trimmed = dxfText.trim()
        if (trimmed.startsWith("{") && trimmed.contains("\"format\"")) {
            importPcadJsonString(trimmed)
            return
        }
        val imported = CadDxfInterchange.importFromDxf(dxfText)
        if (imported.entities.isEmpty()) {
            emitTransientNotification("DXFIN • No entities found in DXF content", NotificationCategory.WARNING)
            return
        }
        recordUndoSnapshot()
        val cleaned = AutoDimensionEngine.removeAutoDimensions(imported.entities)
        val msg = "DXFIN • Imported ${cleaned.size} entities (${imported.displayUnit.symbol}) • Quick Dimension OFF"
        _uiState.update {
            it.copy(
                isHomeScreenVisible = false,
                hasActiveWorkspaceSession = true,
                pendingHomeReplacementAction = null,
                storageUnit = imported.storageUnit,
                displayUnit = imported.displayUnit,
                layers = imported.layers,
                entities = cleaned,
                blocks = imported.blocks.associateBy { b -> b.name },
                selectedEntityIds = emptySet(),
                referencePixelsPerModelUnit = null,
                effectiveGridSnapInterval = it.gridSnapConfig.gridSnapInterval,
                isQuickDimensionEnabled = false,
                activeActionPrompt = promptForCommand(CadCommandId.SELECT, 0, "Single"),
                commandPromptBanner = msg,
                commandPanelState = CommandPanelState.Hidden
            )
        }
        fitDrawing(notify = false)
        emitTransientNotification(msg, NotificationCategory.SUCCESS)
    }

    fun importPcadJsonString(pcadJson: String) {
        val doc = CadPcadPersistence.deserializeFromPcadJson(pcadJson)
        if (doc == null) {
            emitTransientNotification("PCAD OPEN • Invalid or corrupted .pcad project file", NotificationCategory.ERROR)
            return
        }
        undoStack.clear()
        redoStack.clear()
        val nextRev = nextRevisionCounter++
        val baseName = CadPcadPersistence.canonicalBasename(doc.projectName)
        val msg = "OPEN (.PCAD) • Restored '$baseName.pcad' (${doc.entities.size} entities, ${doc.displayUnit.symbol})"
        _uiState.update {
            it.copy(
                isHomeScreenVisible = false,
                hasActiveWorkspaceSession = true,
                documentRevision = nextRev,
                lastExplicitSaveRevision = nextRev,
                pendingCommandLineInput = "",
                pendingHomeReplacementAction = null,
                currentProjectId = doc.projectId,
                currentProjectName = baseName,
                storageUnit = doc.storageUnit,
                displayUnit = doc.displayUnit,
                unitChangeMode = doc.unitChangeMode,
                precision = doc.precision,
                sheetSetup = doc.sheetSetup.copy(titleBlockTitle = baseName),
                dimensionStyle = doc.dimensionStyle,
                gridSnapConfig = doc.gridSnapConfig,
                controllerConfig = doc.controllerConfig,
                activeLayerName = doc.activeLayerName,
                layers = doc.layers,
                entities = doc.entities,
                blocks = doc.blocks.associateBy { b -> b.name },
                selectedEntityIds = emptySet(),
                referencePixelsPerModelUnit = null,
                effectiveGridSnapInterval = doc.gridSnapConfig.gridSnapInterval,
                isQuickDimensionEnabled = doc.isQuickDimensionEnabled,
                qdimDensity = doc.qdimDensity,
                qdimArrangementPreference = doc.qdimArrangementPreference,
                canUndo = false,
                canRedo = false,
                activeActionPrompt = promptForCommand(CadCommandId.SELECT, 0, "Single"),
                commandPromptBanner = msg,
                commandPanelState = CommandPanelState.Hidden
            )
        }
        fitDrawing(notify = false)
        emitTransientNotification(msg, NotificationCategory.SUCCESS)
    }

    fun saveCurrentProject(projectName: String): Boolean {
        val st = _uiState.value
        val baseName = CadPcadPersistence.canonicalBasename(projectName)
        if (simulateExplicitSaveFailureForTesting) {
            val errMsg = "SAVE FAILED • Could not save '$baseName.pcad' to Documents/PrecisionCAD"
            emitTransientNotification(errMsg, NotificationCategory.ERROR, isPersistentError = true)
            return false
        }
        val saveId = if (st.currentProjectId == "untitled_drawing") {
            "proj_${UUID.randomUUID().toString().take(8)}"
        } else {
            st.currentProjectId
        }
        val updatedSheet = st.sheetSetup.copy(titleBlockTitle = baseName)
        val pcadDoc = buildCurrentPcadDocument(projectNameOverride = baseName).copy(projectId = saveId)
        val payload = currentDxfPayload()
        val explicitResult = runCatching {
            CadPcadPersistence.saveExplicitToDocumentsPrecisionCad(getApplication(), pcadDoc)
        }.getOrNull()
        if (explicitResult == null || !explicitResult.localFile.exists()) {
            val errMsg = "SAVE FAILED • Could not write '$baseName.pcad'"
            emitTransientNotification(errMsg, NotificationCategory.ERROR, isPersistentError = true)
            return false
        }
        lastExplicitSavedPcadFile = explicitResult.localFile
        lastQuickSaveFile = CadPcadPersistence.performAutomaticQuickSave(getApplication(), pcadDoc)
        val savedRev = _uiState.value.documentRevision
        val msg = "SAVE • Saved '$baseName.pcad' to Documents/PrecisionCAD/$baseName.pcad"
        _uiState.update {
            it.copy(
                hasActiveWorkspaceSession = true,
                lastExplicitSaveRevision = savedRev,
                currentProjectId = saveId,
                currentProjectName = baseName,
                sheetSetup = updatedSheet,
                commandPromptBanner = msg
            )
        }
        viewModelScope.launch {
            repository.saveProjectDocument(
                projectId = saveId,
                name = baseName,
                description = "PrecisionCAD Project ($baseName.pcad • ${st.entities.size} entities, ${st.displayUnit.symbol})",
                payload = payload,
                pcadDocument = pcadDoc
            )
            emitTransientNotification(msg, NotificationCategory.SUCCESS)
        }
        return true
    }

    fun loadProject(projectId: String) {
        val resetRev = nextRevisionCounter++
        _uiState.update {
            it.copy(
                isHomeScreenVisible = false,
                hasActiveWorkspaceSession = true,
                documentRevision = resetRev,
                lastExplicitSaveRevision = resetRev,
                pendingCommandLineInput = "",
                pendingHomeReplacementAction = null
            )
        }
        viewModelScope.launch {
            val loaded = repository.loadProjectDocument(projectId) ?: return@launch
            val meta = savedProjects.value.firstOrNull { it.projectId == projectId }
            undoStack.clear()
            redoStack.clear()
            val loadedRev = nextRevisionCounter++
            val entitiesWithoutAutoDim = AutoDimensionEngine.removeAutoDimensions(loaded.entities)
            val msg = "OPEN • Loaded '${meta?.name ?: projectId}' (${entitiesWithoutAutoDim.size} entities • Quick Dimension OFF)"
            _uiState.update {
                it.copy(
                    isHomeScreenVisible = false,
                    hasActiveWorkspaceSession = true,
                    documentRevision = loadedRev,
                    lastExplicitSaveRevision = loadedRev,
                    pendingCommandLineInput = "",
                    pendingHomeReplacementAction = null,
                    currentProjectId = projectId,
                    currentProjectName = meta?.name ?: projectId,
                    storageUnit = loaded.storageUnit,
                    displayUnit = loaded.displayUnit,
                    layers = loaded.layers,
                    entities = entitiesWithoutAutoDim,
                    blocks = loaded.blocks.associateBy { b -> b.name },
                    selectedEntityIds = emptySet(),
                    previousSelectedEntityIds = emptySet(),
                    referencePixelsPerModelUnit = null,
                    effectiveGridSnapInterval = it.gridSnapConfig.gridSnapInterval,
                    isQuickDimensionEnabled = false,
                    canUndo = false,
                    canRedo = false,
                    activeActionPrompt = promptForCommand(CadCommandId.SELECT, 0, "Single"),
                    commandPromptBanner = msg,
                    commandPanelState = CommandPanelState.Hidden
                )
            }
            fitDrawing(notify = false)
            emitTransientNotification(msg, NotificationCategory.SUCCESS)
        }
    }

    fun createNewBlankProject(name: String, unit: CadUnit) {
        undoStack.clear()
        redoStack.clear()
        val newId = "proj_${UUID.randomUUID().toString().take(8)}"
        val freshRev = nextRevisionCounter++
        prefs.edit { putString("display_unit", unit.name) }
        val msg = "NEW • Created blank drawing '$name' in ${unit.label} (Quick Dimension OFF)"
        _uiState.update {
            it.copy(
                isHomeScreenVisible = false,
                hasActiveWorkspaceSession = true,
                documentRevision = freshRev,
                lastExplicitSaveRevision = freshRev,
                pendingCommandLineInput = "",
                pendingHomeReplacementAction = null,
                currentProjectId = newId,
                currentProjectName = name,
                storageUnit = unit,
                displayUnit = unit,
                layers = CadLayer.defaultLayers(),
                entities = emptyList(),
                blocks = emptyMap(),
                selectedEntityIds = emptySet(),
                previousSelectedEntityIds = emptySet(),
                referencePixelsPerModelUnit = null,
                effectiveGridSnapInterval = it.gridSnapConfig.gridSnapInterval,
                isQuickDimensionEnabled = false,
                canUndo = false,
                canRedo = false,
                activeActionPrompt = promptForCommand(CadCommandId.SELECT, 0, "Single"),
                commandPromptBanner = msg,
                commandPanelState = CommandPanelState.Hidden
            )
        }
        fitDrawing(notify = false)
        emitTransientNotification(msg, NotificationCategory.INFO)
    }

    // --- Helpers ---

    private fun updateSelectionAndState(
        entities: List<CadEntity>,
        nextSelection: Set<String>,
        banner: String,
        returnToIdle: Boolean = false
    ) {
        val st = _uiState.value
        val prevSel = if (st.selectedEntityIds.isNotEmpty()) st.selectedEntityIds else st.previousSelectedEntityIds
        _uiState.update {
            it.copy(
                entities = entities,
                selectedEntityIds = nextSelection,
                previousSelectedEntityIds = prevSel,
                activeCommand = if (returnToIdle) CadCommandId.SELECT else it.activeCommand,
                commandStepPoints = emptyList(),
                commandPromptBanner = banner,
                commandPanelState = if (returnToIdle) CommandPanelState.Hidden else it.commandPanelState
            )
        }
    }

    private data class CircularFeaturePick(
        val sourceEntityId: String,
        val center: Point2D,
        val radius: Double,
        val rimContactPoint: Point2D,
        val outwardAngleDeg: Double
    )

    private fun findNearestArcOrCircleFeature(pt: Point2D): CircularFeaturePick? {
        val st = _uiState.value
        val pickToleranceModel = (42f / st.viewport.pixelsPerModelUnit.coerceAtLeast(0.1f)).toDouble().coerceAtLeast(10.0)
        var bestDist = Double.MAX_VALUE
        var bestFeature: CircularFeaturePick? = null

        for (ent in st.entities) {
            when (ent) {
                is CadEntity.CircleEntity -> {
                    val d = abs(pt.distanceTo(ent.center) - ent.radius)
                    if (d <= pickToleranceModel && d < bestDist) {
                        val rawAng = ent.center.angleToDeg(pt)
                        val cleanAng = if (abs(rawAng % 90.0) < 6.0 || abs(rawAng % 90.0) > 84.0) 45.0 else rawAng
                        bestDist = d
                        bestFeature = CircularFeaturePick(
                            sourceEntityId = ent.id,
                            center = ent.center,
                            radius = ent.radius,
                            rimContactPoint = ent.center.polarOffset(ent.radius, cleanAng),
                            outwardAngleDeg = cleanAng
                        )
                    }
                }
                is CadEntity.ArcEntity -> {
                    val d = CadGeometryKernel.distanceToEntity(pt, ent)
                    if (d <= pickToleranceModel && d < bestDist) {
                        val midPt = ent.midPoint
                        val midAng = ent.center.angleToDeg(midPt)
                        bestDist = d
                        bestFeature = CircularFeaturePick(
                            sourceEntityId = ent.id,
                            center = ent.center,
                            radius = ent.radius,
                            rimContactPoint = midPt,
                            outwardAngleDeg = midAng
                        )
                    }
                }
                is CadEntity.PolylineEntity -> {
                    val polyArcs = CadGeometryKernel.explodePolylineToPrimitives(ent)
                        .filterIsInstance<CadEntity.ArcEntity>()
                    for (arc in polyArcs) {
                        val d = CadGeometryKernel.distanceToEntity(pt, arc)
                        if (d <= pickToleranceModel && d < bestDist) {
                            val midPt = arc.midPoint
                            val midAng = arc.center.angleToDeg(midPt)
                            bestDist = d
                            bestFeature = CircularFeaturePick(
                                sourceEntityId = ent.id,
                                center = arc.center,
                                radius = arc.radius,
                                rimContactPoint = midPt,
                                outwardAngleDeg = midAng
                            )
                        }
                    }
                }
                else -> Unit
            }
        }
        return bestFeature
    }

    private fun commitSingleEntity(
        entity: CadEntity,
        bannerMessage: String,
        returnToIdle: Boolean = true
    ) {
        recordUndoSnapshot()
        _uiState.update {
            it.copy(
                entities = it.entities + entity,
                activeCommand = if (returnToIdle) CadCommandId.SELECT else it.activeCommand,
                commandStepPoints = emptyList(),
                polarPreview = null,
                commandPromptBanner = bannerMessage,
                commandPanelState = if (returnToIdle) CommandPanelState.Hidden else CommandPanelState.Running(it.activeCommand, bannerMessage, 0),
                commandHistoryLog = (it.commandHistoryLog + bannerMessage).takeLast(30)
            )
        }
        emitTransientNotification(bannerMessage, NotificationCategory.SUCCESS)
        if (!returnToIdle) {
            syncPolarAndActiveSessionState()
        }
    }

    private fun getUnlockedSelectedEntities(): List<CadEntity> {
        val st = _uiState.value
        val lockedLayers = st.layers.filter { it.isLocked || it.isFrozen || !it.isVisible }.map { it.name }.toSet()
        return st.entities.filter { it.id in st.selectedEntityIds && it.layerName !in lockedLayers }
    }

    private fun findNearestSelectableEntity(pt: Point2D): CadEntity? {
        val st = _uiState.value
        val pickToleranceModel = (24f / st.viewport.pixelsPerModelUnit.coerceAtLeast(0.1f)).toDouble()
        val lockedOrHidden = st.layers.filter { !it.isVisible || it.isFrozen }.map { it.name }.toSet()
        return st.entities
            .filter { it.layerName !in lockedOrHidden }
            .filter { st.isolatedEntityIds == null || it.id in st.isolatedEntityIds }
            .map { it to CadGeometryKernel.distanceToEntity(pt, it) }
            .filter { it.second <= pickToleranceModel }
            .minByOrNull { it.second }
            ?.first
    }

    private inline fun <reified T : CadEntity> findNearestEntityOfType(pt: Point2D): T? {
        val st = _uiState.value
        val pickToleranceModel = (36f / st.viewport.pixelsPerModelUnit.coerceAtLeast(0.1f)).toDouble()
        return st.entities
            .filterIsInstance<T>()
            .map { it to CadGeometryKernel.distanceToEntity(pt, it) }
            .filter { it.second <= pickToleranceModel }
            .minByOrNull { it.second }
            ?.first
    }

    private fun promptForCommand(cmd: CadCommandId, stepCount: Int, subOption: String): String = when (cmd) {
        CadCommandId.SELECT -> when (subOption) {
            "Window" -> if (stepCount == 0) "Pick first selection corner" else "Specify opposite selection corner"
            "Crossing" -> if (stepCount == 0) "Pick first selection corner" else "Specify opposite selection corner"
            else -> "Position cursor over object and tap PICK PT"
        }
        CadCommandId.LINE -> if (stepCount == 0) "Pick start point" else "Specify next point or Finish"
        CadCommandId.POLYLINE -> if (stepCount == 0) "Pick start vertex" else "Specify next vertex or Finish"
        CadCommandId.CIRCLE -> when (subOption) {
            "2P" -> if (stepCount == 0) "Pick first diameter point" else "Pick second diameter point"
            "3P" -> "Pick circumference point ${stepCount + 1} of 3"
            "TTR" -> if (stepCount == 0) "Select first tangent line" else "Select second tangent line"
            "Diameter" -> if (stepCount == 0) "Specify centre" else "Specify diameter"
            else -> if (stepCount == 0) "Specify centre" else "Specify radius"
        }
        CadCommandId.ARC -> when (subOption) {
            "SCE" -> when (stepCount) {
                0 -> "Pick start point"
                1 -> "Specify centre"
                else -> "Pick end point"
            }
            else -> "Pick arc point ${stepCount + 1} of 3"
        }
        CadCommandId.RECTANGLE -> if (stepCount == 0) "Pick first corner" else "Specify opposite corner"
        CadCommandId.POLYGON -> if (stepCount == 0) "Specify centre" else "Specify radius"
        CadCommandId.ELLIPSE -> when (stepCount) {
            0 -> "Specify centre"
            1 -> "Specify major axis endpoint"
            else -> "Specify minor axis radius"
        }
        CadCommandId.SPLINE -> if (stepCount == 0) "Pick first fit point" else "Pick next fit point or Finish"
        CadCommandId.OFFSET -> if (stepCount == 0) "Select object to offset" else "Pick side to offset"
        CadCommandId.TRIM -> "Pick segment to trim"
        CadCommandId.EXTEND -> "Pick endpoint to extend"
        CadCommandId.FILLET -> if (stepCount == 0) "Pick first intersection" else "Pick second intersection"
        CadCommandId.CHAMFER -> if (stepCount == 0) "Select first line" else "Select second line"
        CadCommandId.MOVE -> if (stepCount == 0) "Pick base point" else "Pick destination point"
        CadCommandId.COPY -> if (stepCount == 0) "Pick base point" else "Pick destination point"
        CadCommandId.ROTATE -> if (stepCount == 0) "Pick pivot point" else "Specify rotation angle point"
        CadCommandId.SCALE -> if (stepCount == 0) "Pick base point" else "Specify scale factor point"
        CadCommandId.MIRROR -> if (stepCount == 0) "Pick first mirror line point" else "Pick second mirror line point"
        CadCommandId.STRETCH -> when (stepCount) {
            0 -> "Pick crossing corner 1"
            1 -> "Pick crossing corner 2"
            else -> "Pick displacement point"
        }
        CadCommandId.BREAK -> if (stepCount == 0) "Pick first break point" else "Pick second break point"
        CadCommandId.ALIGN -> "Pick alignment point ${stepCount + 1} of 4"
        CadCommandId.LENGTHEN -> "Select line to lengthen"
        CadCommandId.MEASURE -> if (stepCount == 0) "Pick first measurement point" else "Pick second measurement point"
        CadCommandId.DIMLINEAR, CadCommandId.DIMALIGNED -> when (stepCount) {
            0 -> "Pick first extension origin"
            1 -> "Pick second extension origin"
            else -> "Pick dimension line location"
        }
        CadCommandId.DIMANGULAR -> when (stepCount) {
            0 -> "Pick vertex point"
            1 -> "Pick first angle arm"
            else -> "Pick second angle arm"
        }
        CadCommandId.DIMRADIUS, CadCommandId.DIMDIAMETER -> "Select circle, arc, or fillet"
        CadCommandId.DIMORDINATE -> if (stepCount == 0) "Pick feature datum point" else "Pick leader landing point"
        CadCommandId.LEADER -> if (stepCount == 0) "Pick leader arrow tip" else "Pick leader shoulder point"
        CadCommandId.TEXT -> "Pick note insertion point"
        CadCommandId.CENTERMARK -> "Select circle, arc, or fillet"
        else -> "Position cursor and tap PICK PT"
    }

    private fun fmt(v: Double): String = String.format(Locale.ROOT, "%.2f", v)
}
