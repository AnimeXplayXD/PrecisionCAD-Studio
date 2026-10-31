package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cad.data.CadAppDatabase
import com.example.cad.data.CadProjectMetadataEntity
import com.example.cad.data.CadProjectRepository
import com.example.cad.data.CadTemplateFactory
import com.example.cad.dimension.AutoDimensionEngine
import com.example.cad.dimension.DimensionAlignmentReport
import com.example.cad.dimension.DimensionArrangementPreference
import com.example.cad.dimension.DimensionStyleConfig
import com.example.cad.dimension.QDimDensityPolicy
import com.example.cad.dimension.QDimOperatingMode
import com.example.cad.io.CadDxfInterchange
import com.example.cad.io.DxfDocumentPayload
import com.example.cad.kernel.CadGeometryKernel
import com.example.cad.kernel.CadModificationEngine
import com.example.cad.kernel.CadSnapAndCoordEngine
import com.example.cad.kernel.SnapCandidate
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
import com.example.cad.model.DimensionHierarchyTier
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
import com.example.ui.components.SelectionBoxDragState
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

data class DocumentSnapshot(
    val entities: List<CadEntity>,
    val layers: List<CadLayer>,
    val blocks: Map<String, BlockDefinition>,
    val storageUnit: CadUnit,
    val displayUnit: CadUnit,
    val unitChangeMode: UnitChangeMode = UnitChangeMode.CONVERT_DISPLAY_UNITS,
    val isQuickDimensionEnabled: Boolean = false
)

data class CadWorkspaceUiState(
    val currentProjectId: String = "proj_mounting_plate",
    val currentProjectName: String = "CNC 6-Hole Mounting Plate",
    val workspaceSpace: WorkspaceSpace = WorkspaceSpace.MODEL_SPACE,
    val storageUnit: CadUnit = CadUnit.MM,
    val displayUnit: CadUnit = CadUnit.MM,
    val unitChangeMode: UnitChangeMode = UnitChangeMode.CONVERT_DISPLAY_UNITS,
    val precision: PrecisionFormatConfig = PrecisionFormatConfig(linearDecimals = 2, angularDecimals = 1),
    val sheetSetup: SheetSetupConfig = SheetSetupConfig(),
    val dimensionStyle: DimensionStyleConfig = DimensionStyleConfig(),
    val gridSnapConfig: GridAndSnapConfig = GridAndSnapConfig(),
    val controllerConfig: CursorControllerConfig = CursorControllerConfig(),
    val activeCategory: CadCommandCategory = CadCommandCategory.DRAW,
    val activeCommand: CadCommandId = CadCommandId.SELECT,
    val commandSubOption: String = "Default",
    val commandParameterValue: Double = 5.0,
    val commandStepPoints: List<Point2D> = emptyList(),
    val commandPromptBanner: String = "READY • Select objects or invoke a CAD command",
    val commandHistoryLog: List<String> = listOf(
        "PrecisionCAD Studio initialized (2D Technical Viewport).",
        "Loaded template: CNC 6-Hole Mounting Plate (120 × 80 mm)."
    ),
    val layers: List<CadLayer> = CadLayer.defaultLayers(),
    val activeLayerName: String = "0",
    val entities: List<CadEntity> = emptyList(),
    val blocks: Map<String, BlockDefinition> = emptyMap(),
    val selectedEntityIds: Set<String> = emptySet(),
    val isolatedEntityIds: Set<String>? = null,
    val rawCursorModelPos: Point2D = Point2D(60.0, 40.0),
    val snappedCursorModelPos: Point2D = Point2D(60.0, 40.0),
    val activeSnapCandidate: SnapCandidate? = null,
    val selectionBoxDrag: SelectionBoxDragState? = null,
    val viewport: ViewportTransform = ViewportTransform(),
    val viewportWidthPx: Float = 800f,
    val viewportHeightPx: Float = 600f,
    val isQuickDimensionEnabled: Boolean = false,
    val qdimDensity: QDimDensityPolicy = QDimDensityPolicy.BALANCED,
    val qdimArrangementPreference: DimensionArrangementPreference = DimensionArrangementPreference.AUTO_CHAIN,
    val lastAlignmentReport: DimensionAlignmentReport? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false
)

/**
 * Orientation-safe StateFlow ViewModel preserving all drawing state, cursor coordinates,
 * active command steps, selection sets, zoom/pan, and undo/redo history across Portrait/Landscape rotation.
 */
class CadWorkspaceViewModel(application: Application) : AndroidViewModel(application) {

    private val database = CadAppDatabase.getInstance(application)
    private val repository = CadProjectRepository(application, database.projectDao())

    private val prefs = application.getSharedPreferences("precision_cad_prefs", Context.MODE_PRIVATE)

    val savedProjects: StateFlow<List<CadProjectMetadataEntity>> = repository.projectsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(createInitialState())
    val uiState: StateFlow<CadWorkspaceUiState> = _uiState.asStateFlow()

    private val undoStack = ArrayDeque<DocumentSnapshot>()
    private val redoStack = ArrayDeque<DocumentSnapshot>()

    init {
        viewModelScope.launch {
            repository.ensureDefaultProjectsSeeded()
        }
    }

    private fun createInitialState(): CadWorkspaceUiState {
        val initialPayload = CadTemplateFactory.createSixHoleMountingPlate()
        val savedModeName = prefs.getString("unit_change_mode", UnitChangeMode.CONVERT_DISPLAY_UNITS.name)
        val initialMode = UnitChangeMode.entries.firstOrNull { it.name == savedModeName }
            ?: UnitChangeMode.CONVERT_DISPLAY_UNITS
        val savedDisplayUnitName = prefs.getString("display_unit", initialPayload.displayUnit.name)
        val initialDisplayUnit = CadUnit.entries.firstOrNull { it.name == savedDisplayUnitName }
            ?: initialPayload.displayUnit
        val savedLinearDecimals = prefs.getInt("linear_decimals", initialPayload.precision.linearDecimals)
        val savedAngularDecimals = prefs.getInt("angular_decimals", initialPayload.precision.angularDecimals)
        val savedShowSuffix = prefs.getBoolean("show_unit_suffix", initialPayload.precision.showUnitSuffix)
        val savedSuppressZeros = prefs.getBoolean("suppress_trailing_zeros", initialPayload.precision.suppressTrailingZeros)

        return CadWorkspaceUiState(
            storageUnit = initialPayload.storageUnit,
            displayUnit = initialDisplayUnit,
            unitChangeMode = initialMode,
            layers = initialPayload.layers,
            // Quick Dimension is always OFF by default when opening a sheet
            entities = AutoDimensionEngine.removeAutoDimensions(initialPayload.entities),
            blocks = initialPayload.blocks.associateBy { it.name },
            sheetSetup = initialPayload.sheetSetup,
            precision = PrecisionFormatConfig(
                linearDecimals = savedLinearDecimals,
                angularDecimals = savedAngularDecimals,
                showUnitSuffix = savedShowSuffix,
                suppressTrailingZeros = savedSuppressZeros
            ),
            isQuickDimensionEnabled = false
        )
    }

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
                isQuickDimensionEnabled = st.isQuickDimensionEnabled
            )
        )
        if (undoStack.size > 80) undoStack.removeFirst()
        redoStack.clear()
        _uiState.update { it.copy(canUndo = undoStack.isNotEmpty(), canRedo = false) }
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
                isQuickDimensionEnabled = current.isQuickDimensionEnabled
            )
        )
        val prev = undoStack.removeLast()
        _uiState.update {
            it.copy(
                entities = prev.entities,
                layers = prev.layers,
                blocks = prev.blocks,
                storageUnit = prev.storageUnit,
                displayUnit = prev.displayUnit,
                unitChangeMode = prev.unitChangeMode,
                isQuickDimensionEnabled = prev.isQuickDimensionEnabled,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
                commandPromptBanner = "UNDO • Restored previous document state (${prev.entities.size} entities)"
            )
        }
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
                isQuickDimensionEnabled = current.isQuickDimensionEnabled
            )
        )
        val next = redoStack.removeLast()
        _uiState.update {
            it.copy(
                entities = next.entities,
                layers = next.layers,
                blocks = next.blocks,
                storageUnit = next.storageUnit,
                displayUnit = next.displayUnit,
                unitChangeMode = next.unitChangeMode,
                isQuickDimensionEnabled = next.isQuickDimensionEnabled,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
                commandPromptBanner = "REDO • Restored state (${next.entities.size} entities)"
            )
        }
    }

    // --- Cursor Controller & Coordinate Input ---

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
        _uiState.update {
            it.copy(
                rawCursorModelPos = target,
                snappedCursorModelPos = target,
                activeSnapCandidate = null
            )
        }
    }

    private fun updateCursorPositionInternal(newRawModel: Point2D) {
        val st = _uiState.value
        val refPt = st.commandStepPoints.lastOrNull()
        val (snapped, candidate) = CadSnapAndCoordEngine.resolveCursorSnap(
            rawModelCursor = newRawModel,
            referencePoint = refPt,
            entities = st.entities,
            snapConfig = st.gridSnapConfig,
            viewport = st.viewport
        )
        _uiState.update {
            it.copy(
                rawCursorModelPos = newRawModel,
                snappedCursorModelPos = snapped,
                activeSnapCandidate = candidate
            )
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

    fun toggleControllerCollapse() {
        _uiState.update {
            it.copy(controllerConfig = it.controllerConfig.copy(isCollapsed = !it.controllerConfig.isCollapsed))
        }
    }

    fun updateControllerConfig(newConfig: CursorControllerConfig) {
        _uiState.update { it.copy(controllerConfig = newConfig) }
    }

    fun updateGridSnapConfig(newConfig: GridAndSnapConfig) {
        _uiState.update { it.copy(gridSnapConfig = newConfig) }
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
    }

    fun toggleOsnap() {
        _uiState.update {
            val next = !it.gridSnapConfig.osnapEnabled
            it.copy(gridSnapConfig = it.gridSnapConfig.copy(osnapEnabled = next))
        }
    }

    fun toggleOrtho() {
        _uiState.update {
            val next = !it.gridSnapConfig.orthoEnabled
            it.copy(gridSnapConfig = it.gridSnapConfig.copy(orthoEnabled = next))
        }
    }

    fun toggleWorkspaceSpace() {
        _uiState.update {
            val next = if (it.workspaceSpace == WorkspaceSpace.MODEL_SPACE) WorkspaceSpace.PAPER_SPACE else WorkspaceSpace.MODEL_SPACE
            it.copy(
                workspaceSpace = next,
                commandPromptBanner = "Switched to ${next.label}"
            )
        }
    }

    // --- Viewport & Orientation Sizing ---

    fun updateViewport(newViewport: ViewportTransform) {
        _uiState.update { it.copy(viewport = newViewport) }
    }

    fun updateViewportDimensions(widthPx: Float, heightPx: Float) {
        val prev = _uiState.value
        val isInitialResize = abs(prev.viewportWidthPx - 800f) < 1f && abs(prev.viewportHeightPx - 600f) < 1f
        _uiState.update { it.copy(viewportWidthPx = widthPx, viewportHeightPx = heightPx) }
        if (isInitialResize && prev.entities.isNotEmpty()) {
            zoomExtents()
        }
    }

    fun zoomExtents() {
        val st = _uiState.value
        val bounds = if (st.entities.isEmpty()) {
            BoundingBox2D(0.0, 0.0, 150.0, 100.0)
        } else {
            st.entities.map { it.boundingBox() }.reduce { a, b -> a.union(b) }
        }
        val fitted = st.viewport.fitToBounds(bounds, st.viewportWidthPx, st.viewportHeightPx)
        _uiState.update {
            it.copy(
                viewport = fitted,
                commandPromptBanner = "ZOOM EXTENTS • Fitted ${st.entities.size} entities to viewport"
            )
        }
    }

    // --- Units & Sheet Configuration (Section 3.1–3.4) ---

    fun applyUnitsAndPrecision(
        targetUnit: CadUnit,
        mode: UnitChangeMode,
        newPrecision: PrecisionFormatConfig
    ) {
        val st = _uiState.value
        prefs.edit()
            .putString("unit_change_mode", mode.name)
            .putString("display_unit", targetUnit.name)
            .putInt("linear_decimals", newPrecision.linearDecimals)
            .putInt("angular_decimals", newPrecision.angularDecimals)
            .putBoolean("show_unit_suffix", newPrecision.showUnitSuffix)
            .putBoolean("suppress_trailing_zeros", newPrecision.suppressTrailingZeros)
            .apply()

        if (mode == UnitChangeMode.CONVERT_DISPLAY_UNITS) {
            // Mode A: Physical geometry remains unchanged; display unit and selected mode persist
            recordUndoSnapshot()
            _uiState.update {
                it.copy(
                    displayUnit = targetUnit,
                    unitChangeMode = mode,
                    precision = newPrecision,
                    commandPromptBanner = "UNITS (Option A Active) • Display unit set to ${targetUnit.symbol} (physical geometry preserved)"
                )
            }
        } else {
            // Mode B: Rescale stored model-space coordinates by conversion factor and persist mode
            recordUndoSnapshot()
            val scaleFactor = st.storageUnit.toMillimetersFactor / targetUnit.toMillimetersFactor
            val origin = Point2D(0.0, 0.0)
            val rescaledEntities = st.entities.map { it.scale(origin, scaleFactor) }
            val rescaledCursor = st.snappedCursorModelPos.scaleFrom(origin, scaleFactor)
            _uiState.update {
                it.copy(
                    storageUnit = targetUnit,
                    displayUnit = targetUnit,
                    unitChangeMode = mode,
                    precision = newPrecision,
                    entities = rescaledEntities,
                    rawCursorModelPos = rescaledCursor,
                    snappedCursorModelPos = rescaledCursor,
                    commandPromptBanner = "UNITS (Option B Active) • Rescaled model coordinates by ${String.format(Locale.US, "%.4f", scaleFactor)} to ${targetUnit.symbol}"
                )
            }
            zoomExtents()
        }
    }

    fun applySheetSetup(newSetup: SheetSetupConfig) {
        val prev = _uiState.value
        val isNewSheetPage = prev.sheetSetup.preset != newSetup.preset ||
            prev.sheetSetup.titleBlockDrawingNo != newSetup.titleBlockDrawingNo
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
                commandPromptBanner = "PAGESETUP • Sheet updated to ${newSetup.preset.label} (${newSetup.orientation.label}, Scale ${newSetup.scaleLabel})"
            )
        }
    }

    // --- Quick Dimension (QDIM) Toggle, Refresh & Execution ---

    fun setQuickDimensionEnabled(enabled: Boolean) {
        val st = _uiState.value
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
            _uiState.update {
                it.copy(
                    entities = withoutAutoDims,
                    isQuickDimensionEnabled = false,
                    lastAlignmentReport = null,
                    commandPromptBanner = "QDIM (OFF) • Quick Dimension & Alignment disabled (manual dimensions preserved)"
                )
            }
        }
    }

    fun refreshQuickDimensions() {
        val st = _uiState.value
        executeQuickDimension(
            mode = QDimOperatingMode.QDIM_ALL,
            density = st.qdimDensity,
            replaceAutoDims = true,
            arrangementPreference = st.qdimArrangementPreference
        )
        _uiState.update {
            it.copy(
                isQuickDimensionEnabled = true,
                commandPromptBanner = "QDIM REFRESH (${st.qdimArrangementPreference.label}) • Refreshed quick alignment & dimensions for all sheet structures"
            )
        }
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
        _uiState.update {
            it.copy(
                activeCommand = CadCommandId.QDIM,
                activeCategory = CadCommandId.QDIM.category,
                entities = updatedEntities,
                isQuickDimensionEnabled = true,
                qdimDensity = density,
                qdimArrangementPreference = arrangementPreference,
                lastAlignmentReport = alignmentReport,
                commandPromptBanner = "${mode.commandToken} (${density.label} • ${arrangementPreference.label} • ON) • Refreshed $dimCount associative dimensions"
            )
        }
    }

    // --- Command Selection & Execution State Machine ---

    fun selectCategory(category: CadCommandCategory) {
        _uiState.update { it.copy(activeCategory = category) }
    }

    fun activateCommand(command: CadCommandId, subOption: String = "Default") {
        val st = _uiState.value
        when (command) {
            CadCommandId.UNDO_REDO -> {
                undo()
                return
            }
            CadCommandId.QDIM -> {
                // Running the Quick Dimension command enables & refreshes Quick Dimension and activates QDIM in the function bar
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
                    _uiState.update {
                        it.copy(
                            entities = remaining,
                            selectedEntityIds = joined.map { e -> e.id }.toSet(),
                            commandPromptBanner = "JOIN • Merged ${sel.size} segments into ${joined.size} polyline/line"
                        )
                    }
                    return
                }
            }
            CadCommandId.EXPLODE -> {
                val sel = st.entities.filter { it.id in st.selectedEntityIds }
                if (sel.isNotEmpty()) {
                    recordUndoSnapshot()
                    val exploded = sel.flatMap { CadModificationEngine.explodeEntity(it, st.blocks) }
                    val remaining = st.entities.filterNot { it.id in st.selectedEntityIds } + exploded
                    _uiState.update {
                        it.copy(
                            entities = remaining,
                            selectedEntityIds = emptySet(),
                            commandPromptBanner = "EXPLODE • Decomposed ${sel.size} compound objects into ${exploded.size} primitives"
                        )
                    }
                    return
                }
            }
            CadCommandId.ISOLATE -> {
                if (st.isolatedEntityIds != null) {
                    _uiState.update {
                        it.copy(
                            isolatedEntityIds = null,
                            commandPromptBanner = "UNISOLATE • Restored visibility of all entities"
                        )
                    }
                } else if (st.selectedEntityIds.isNotEmpty()) {
                    _uiState.update {
                        it.copy(
                            isolatedEntityIds = st.selectedEntityIds,
                            commandPromptBanner = "ISOLATE • Isolated ${st.selectedEntityIds.size} selected objects (tap Isolate again to restore)"
                        )
                    }
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
                    _uiState.update {
                        it.copy(
                            entities = it.entities + generated,
                            commandPromptBanner = "ARRAY • Created ${generated.size} arrayed entities"
                        )
                    }
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
                    _uiState.update {
                        it.copy(
                            entities = updated,
                            commandPromptBanner = "PEDIT • Toggled closed state on ${selPolys.size} polylines"
                        )
                    }
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

        _uiState.update {
            it.copy(
                activeCommand = command,
                commandSubOption = subOption,
                commandParameterValue = defaultParam,
                commandStepPoints = emptyList(),
                commandPromptBanner = promptForCommand(command, 0, subOption)
            )
        }
    }

    fun setCommandSubOption(subOption: String) {
        val st = _uiState.value
        _uiState.update {
            it.copy(
                commandSubOption = subOption,
                commandPromptBanner = promptForCommand(st.activeCommand, st.commandStepPoints.size, subOption)
            )
        }
    }

    fun setCommandParameterValue(value: Double) {
        val coerced = value.coerceAtLeast(0.0)
        _uiState.update { st ->
            val paramLabel = when (st.activeCommand) {
                CadCommandId.FILLET -> "R=${fmt(coerced)}"
                CadCommandId.CHAMFER -> "D=${fmt(coerced)}"
                CadCommandId.OFFSET -> "d=${fmt(coerced)}"
                CadCommandId.POLYGON -> "Sides=${coerced.toInt()}"
                else -> "Val=${fmt(coerced)}"
            }
            st.copy(
                commandParameterValue = coerced,
                commandPromptBanner = "${st.activeCommand.commandName} ($paramLabel) • ${promptForCommand(st.activeCommand, st.commandStepPoints.size, st.commandSubOption)}"
            )
        }
    }

    /**
     * Triggered when user taps "PICK PT" on the Virtual Cursor Controller,
     * submits a numeric coordinate, or taps directly in the viewport.
     */
    fun pickPointAtCursor() {
        handlePointPick(_uiState.value.snappedCursorModelPos)
    }

    fun handleDirectTapModelPoint(rawModelPt: Point2D) {
        updateCursorPositionInternal(rawModelPt)
        handlePointPick(_uiState.value.snappedCursorModelPos)
    }

    fun submitCommandOrCoordinateInput(rawText: String) {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) {
            finishCurrentCommand()
            return
        }

        // 1. Check special keywords ("C" to close line/polyline, "U" to undo, "ALL" to select all, "QDIM ALL")
        val upper = trimmed.uppercase()
        if (upper == "ALL") {
            selectAllEntities()
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

        // 2. Check explicit radius/distance assignments like "R=5", "R 5", "R5", "D=5" or plain numeric parameter for FILLET/CHAMFER/OFFSET/POLYGON/DONUT/LENGTHEN
        val st = _uiState.value
        val radiusMatch = Regex("""^(?:R|RADIUS|D|DIST)\s*[=:]?\s*([0-9]+(?:\.[0-9]+)?)$""", RegexOption.IGNORE_CASE).matchEntire(trimmed)
        if (radiusMatch != null) {
            val v = radiusMatch.groupValues[1].toDoubleOrNull()
            if (v != null) {
                setCommandParameterValue(v)
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
            return
        }

        // 3. Check if input is a CAD Command Name or Alias (e.g. L, PL, C, REC, TR, F, CHA, O, M, CO, RO)
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
            _uiState.update {
                it.copy(
                    rawCursorModelPos = modelPt,
                    snappedCursorModelPos = modelPt,
                    activeSnapCandidate = null
                )
            }
            handlePointPick(modelPt)
        }
    }

    private fun handlePointPick(pt: Point2D) {
        val st = _uiState.value
        val cmd = st.activeCommand
        val pts = st.commandStepPoints + pt
        val layer = st.activeLayerName

        when (cmd) {
            CadCommandId.SELECT -> {
                val hit = findNearestSelectableEntity(pt)
                val nextSel = if (hit != null) {
                    if (hit.id in st.selectedEntityIds) st.selectedEntityIds - hit.id else st.selectedEntityIds + hit.id
                } else {
                    emptySet()
                }
                _uiState.update {
                    it.copy(
                        selectedEntityIds = nextSel,
                        commandPromptBanner = if (nextSel.isEmpty()) "READY • 0 objects selected"
                        else "SELECTED • ${nextSel.size} object(s) selected (${hit?.typeName ?: ""})"
                    )
                }
            }

            CadCommandId.LINE -> {
                if (pts.size >= 2) {
                    val p1 = pts[pts.size - 2]
                    val p2 = pts.last()
                    if (p1.distanceTo(p2) > 1e-4) {
                        recordUndoSnapshot()
                        val seg = CadEntity.LineEntity(layerName = layer, start = p1, end = p2)
                        _uiState.update {
                            it.copy(
                                entities = it.entities + seg,
                                commandStepPoints = pts,
                                commandPromptBanner = "LINE • Segment added (${st.precision.formatLinear(seg.length, st.storageUnit, st.displayUnit)}). Pick next point, 'C' to close, or ✓ to finish"
                            )
                        }
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            commandStepPoints = pts,
                            commandPromptBanner = promptForCommand(cmd, pts.size, st.commandSubOption)
                        )
                    }
                }
            }

            CadCommandId.POLYLINE, CadCommandId.SPLINE -> {
                _uiState.update {
                    it.copy(
                        commandStepPoints = pts,
                        commandPromptBanner = "${cmd.commandName} • Vertex ${pts.size} added. Pick next point or tap ✓ (Enter) to complete"
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
                            _uiState.update { it.copy(commandStepPoints = emptyList(), commandPromptBanner = "CIRCLE (3P) • Points are collinear; pick 3 non-collinear points") }
                        }
                    } else {
                        _uiState.update { it.copy(commandStepPoints = pts, commandPromptBanner = promptForCommand(cmd, pts.size, "3P")) }
                    }

                    "2P" -> if (pts.size >= 2) {
                        val center = pts[0].midpointTo(pts[1])
                        val r = pts[0].distanceTo(pts[1]) * 0.5
                        if (r > 1e-4) {
                            commitSingleEntity(CadEntity.CircleEntity(layerName = layer, center = center, radius = r), "CIRCLE (2P) • Created ⌀=${st.precision.formatLinear(r * 2, st.storageUnit, st.displayUnit)}")
                        }
                    } else {
                        _uiState.update { it.copy(commandStepPoints = pts, commandPromptBanner = promptForCommand(cmd, pts.size, "2P")) }
                    }

                    "TTR" -> if (pts.size >= 2) {
                        val l1 = findNearestEntityOfType<CadEntity.LineEntity>(pts[0])
                        val l2 = findNearestEntityOfType<CadEntity.LineEntity>(pts[1])
                        val r = st.commandParameterValue.coerceAtLeast(1.0)
                        val ttr = if (l1 != null && l2 != null) CadGeometryKernel.circleTanTanRadius(l1, l2, r, pts[1]) else null
                        if (ttr != null) {
                            commitSingleEntity(ttr.copy(layerName = layer), "CIRCLE (TTR) • Created tangent circle R=$r")
                        } else {
                            _uiState.update { it.copy(commandStepPoints = emptyList(), commandPromptBanner = "CIRCLE (TTR) • Pick two non-parallel lines") }
                        }
                    } else {
                        _uiState.update { it.copy(commandStepPoints = pts, commandPromptBanner = "CIRCLE (TTR) • Pick second tangent line") }
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
                        _uiState.update { it.copy(commandStepPoints = pts, commandPromptBanner = promptForCommand(cmd, pts.size, st.commandSubOption)) }
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
                    _uiState.update { it.copy(commandStepPoints = pts, commandPromptBanner = promptForCommand(cmd, pts.size, st.commandSubOption)) }
                }
            }

            CadCommandId.RECTANGLE -> {
                if (pts.size >= 2) {
                    val filletR = if (st.commandSubOption == "Fillet") st.commandParameterValue else 0.0
                    val chamferD = if (st.commandSubOption == "Chamfer") st.commandParameterValue else 0.0
                    val rect = CadGeometryKernel.createRectanglePolyline(
                        corner1 = pts[0],
                        corner2 = pts[1],
                        filletRadius = filletR,
                        chamferDistance = chamferD,
                        layerName = layer
                    )
                    commitSingleEntity(rect, "RECTANGLE • Created ${fmt(rect.boundingBox().width)} × ${fmt(rect.boundingBox().height)}")
                } else {
                    _uiState.update { it.copy(commandStepPoints = pts, commandPromptBanner = "RECTANGLE • Pick opposite corner point") }
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
                    _uiState.update { it.copy(commandStepPoints = pts, commandPromptBanner = "POLYGON • Pick radius/corner point") }
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
                    _uiState.update { it.copy(commandStepPoints = pts, commandPromptBanner = promptForCommand(cmd, pts.size, st.commandSubOption)) }
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
                    _uiState.update { it.copy(commandStepPoints = pts, commandPromptBanner = "RAY • Pick through point for direction") }
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
                        _uiState.update { it.copy(commandStepPoints = pts, commandPromptBanner = "XLINE • Pick second point for direction") }
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
                    _uiState.update { it.copy(commandPromptBanner = "HATCH • Pick a point inside a closed boundary loop") }
                }
            }

            CadCommandId.BOUNDARY -> {
                val boundaryPoly = CadGeometryKernel.traceEnclosingBoundary(pt, st.entities, layerName = layer)
                if (boundaryPoly != null) {
                    commitSingleEntity(boundaryPoly, "BOUNDARY • Traced closed polyline (${boundaryPoly.vertices.size} vertices)")
                } else {
                    _uiState.update { it.copy(commandPromptBanner = "BOUNDARY • Closed boundary could not be determined at picked point") }
                }
            }

            // --- MODIFY COMMANDS ---

            CadCommandId.MOVE -> {
                val sel = getUnlockedSelectedEntities()
                if (sel.isEmpty()) {
                    val hit = findNearestSelectableEntity(pt)
                    if (hit != null) _uiState.update { it.copy(selectedEntityIds = setOf(hit.id), commandPromptBanner = "MOVE • Pick base point") }
                } else if (pts.size >= 2) {
                    val dx = pts[1].x - pts[0].x
                    val dy = pts[1].y - pts[0].y
                    recordUndoSnapshot()
                    val selIds = sel.map { it.id }.toSet()
                    val moved = st.entities.map { if (it.id in selIds) it.translate(dx, dy) else it }
                    val updated = CadModificationEngine.updateAssociativeDimensions(moved)
                    _uiState.update {
                        it.copy(
                            entities = updated,
                            commandStepPoints = emptyList(),
                            commandPromptBanner = "MOVE • Displaced ${sel.size} object(s) by (${fmt(dx)}, ${fmt(dy)})"
                        )
                    }
                } else {
                    _uiState.update { it.copy(commandStepPoints = pts, commandPromptBanner = "MOVE • Pick destination point") }
                }
            }

            CadCommandId.COPY -> {
                val sel = getUnlockedSelectedEntities()
                if (sel.isEmpty()) {
                    val hit = findNearestSelectableEntity(pt)
                    if (hit != null) _uiState.update { it.copy(selectedEntityIds = setOf(hit.id), commandPromptBanner = "COPY • Pick base point") }
                } else if (pts.size >= 2) {
                    val dx = pts.last().x - pts.first().x
                    val dy = pts.last().y - pts.first().y
                    recordUndoSnapshot()
                    val clones = sel.map { it.withNewId().translate(dx, dy) }
                    _uiState.update {
                        it.copy(
                            entities = it.entities + clones,
                            commandStepPoints = listOf(pts.first()), // Stay in multiple copy mode
                            commandPromptBanner = "COPY • Placed ${clones.size} copy. Pick another destination or tap ✓ to finish"
                        )
                    }
                } else {
                    _uiState.update { it.copy(commandStepPoints = pts, commandPromptBanner = "COPY • Pick destination point") }
                }
            }

            CadCommandId.ROTATE -> {
                val sel = getUnlockedSelectedEntities()
                if (sel.isEmpty()) {
                    val hit = findNearestSelectableEntity(pt)
                    if (hit != null) _uiState.update { it.copy(selectedEntityIds = setOf(hit.id), commandPromptBanner = "ROTATE • Pick base pivot point") }
                } else if (pts.size >= 2) {
                    val pivot = pts[0]
                    val angle = pivot.angleToDeg(pts[1])
                    recordUndoSnapshot()
                    val selIds = sel.map { it.id }.toSet()
                    val rotated = st.entities.map { if (it.id in selIds) it.rotate(pivot, angle) else it }
                    val updated = CadModificationEngine.updateAssociativeDimensions(rotated)
                    _uiState.update {
                        it.copy(
                            entities = updated,
                            commandStepPoints = emptyList(),
                            commandPromptBanner = "ROTATE • Rotated ${sel.size} object(s) by ${fmt(angle)}°"
                        )
                    }
                } else {
                    _uiState.update { it.copy(commandStepPoints = pts, commandPromptBanner = "ROTATE • Pick rotation angle point") }
                }
            }

            CadCommandId.SCALE -> {
                val sel = getUnlockedSelectedEntities()
                if (sel.isEmpty()) {
                    val hit = findNearestSelectableEntity(pt)
                    if (hit != null) _uiState.update { it.copy(selectedEntityIds = setOf(hit.id), commandPromptBanner = "SCALE • Pick base point") }
                } else if (pts.size >= 2) {
                    val pivot = pts[0]
                    val dist = pivot.distanceTo(pts[1])
                    val factor = (dist / 25.0).coerceIn(0.1, 10.0)
                    recordUndoSnapshot()
                    val selIds = sel.map { it.id }.toSet()
                    val scaled = st.entities.map { if (it.id in selIds) it.scale(pivot, factor) else it }
                    val updated = CadModificationEngine.updateAssociativeDimensions(scaled)
                    _uiState.update {
                        it.copy(
                            entities = updated,
                            commandStepPoints = emptyList(),
                            commandPromptBanner = "SCALE • Scaled ${sel.size} object(s) by ${fmt(factor)}x"
                        )
                    }
                } else {
                    _uiState.update { it.copy(commandStepPoints = pts, commandPromptBanner = "SCALE • Pick scale factor point") }
                }
            }

            CadCommandId.MIRROR -> {
                val sel = getUnlockedSelectedEntities()
                if (sel.isEmpty()) {
                    val hit = findNearestSelectableEntity(pt)
                    if (hit != null) _uiState.update { it.copy(selectedEntityIds = setOf(hit.id), commandPromptBanner = "MIRROR • Pick first point of mirror line") }
                } else if (pts.size >= 2) {
                    recordUndoSnapshot()
                    val mirrored = sel.map { it.withNewId().mirror(pts[0], pts[1]) }
                    _uiState.update {
                        it.copy(
                            entities = it.entities + mirrored,
                            commandStepPoints = emptyList(),
                            commandPromptBanner = "MIRROR • Mirrored ${mirrored.size} object(s)"
                        )
                    }
                } else {
                    _uiState.update { it.copy(commandStepPoints = pts, commandPromptBanner = "MIRROR • Pick second point of mirror axis") }
                }
            }

            CadCommandId.OFFSET -> {
                if (pts.size == 1) {
                    val hit = findNearestSelectableEntity(pts[0])
                    if (hit != null) {
                        _uiState.update {
                            it.copy(
                                selectedEntityIds = setOf(hit.id),
                                commandStepPoints = pts,
                                commandPromptBanner = "OFFSET (d=${fmt(st.commandParameterValue)}) • Pick side to offset"
                            )
                        }
                    }
                } else if (pts.size >= 2) {
                    val target = st.entities.firstOrNull { it.id in st.selectedEntityIds }
                    if (target != null) {
                        val offsetEnt = CadModificationEngine.offsetEntity(target, st.commandParameterValue, pts[1])
                        if (offsetEnt != null) {
                            recordUndoSnapshot()
                            _uiState.update {
                                it.copy(
                                    entities = it.entities + offsetEnt,
                                    commandStepPoints = emptyList(),
                                    commandPromptBanner = "OFFSET • Created parallel offset at d=${fmt(st.commandParameterValue)}"
                                )
                            }
                        } else {
                            _uiState.update { it.copy(commandStepPoints = emptyList(), commandPromptBanner = "OFFSET • Invalid offset radius/distance") }
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
                    _uiState.update {
                        it.copy(
                            entities = CadModificationEngine.updateAssociativeDimensions(remaining),
                            commandStepPoints = emptyList(),
                            commandPromptBanner = "TRIM • Trimmed ${hit.typeName}"
                        )
                    }
                }
            }

            CadCommandId.EXTEND -> {
                val hit = findNearestSelectableEntity(pt)
                if (hit != null) {
                    recordUndoSnapshot()
                    val extended = CadModificationEngine.extendEntityAtPick(hit, pt, st.entities)
                    val updated = st.entities.map { if (it.id == hit.id) extended else it }
                    _uiState.update {
                        it.copy(
                            entities = CadModificationEngine.updateAssociativeDimensions(updated),
                            commandStepPoints = emptyList(),
                            commandPromptBanner = "EXTEND • Extended ${hit.typeName} to boundary"
                        )
                    }
                }
            }

            CadCommandId.FILLET -> {
                val pickTolerance = (48f / st.viewport.pixelsPerModelUnit.coerceAtLeast(0.1f)).toDouble().coerceAtLeast(10.0)
                if (pts.size == 1) {
                    val hit1 = CadModificationEngine.hasLinearEdgeAtPick(st.entities, pts[0], pickTolerance)
                    if (hit1 != null) {
                        _uiState.update {
                            it.copy(
                                selectedEntityIds = setOf(hit1.id),
                                commandStepPoints = pts,
                                commandPromptBanner = "FILLET (R=${fmt(st.commandParameterValue)}) • First edge picked (${hit1.typeName}). Now pick second intersecting edge"
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                commandStepPoints = emptyList(),
                                commandPromptBanner = "FILLET (R=${fmt(st.commandParameterValue)}) • Move cursor onto first line/rectangle edge and tap PICK PT"
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
                        _uiState.update {
                            it.copy(
                                entities = updated,
                                selectedEntityIds = emptySet(),
                                commandStepPoints = emptyList(),
                                commandPromptBanner = "FILLET • Created tangent fillet with R=${fmt(st.commandParameterValue)}"
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                commandStepPoints = emptyList(),
                                commandPromptBanner = "FILLET • Could not fillet selected edges with R=${fmt(st.commandParameterValue)} (ensure edges are non-parallel and long enough)"
                            )
                        }
                    }
                }
            }

            CadCommandId.CHAMFER -> {
                val pickTolerance = (48f / st.viewport.pixelsPerModelUnit.coerceAtLeast(0.1f)).toDouble().coerceAtLeast(10.0)
                if (pts.size == 1) {
                    val hit1 = CadModificationEngine.hasLinearEdgeAtPick(st.entities, pts[0], pickTolerance)
                    if (hit1 != null) {
                        _uiState.update {
                            it.copy(
                                selectedEntityIds = setOf(hit1.id),
                                commandStepPoints = pts,
                                commandPromptBanner = "CHAMFER (D=${fmt(st.commandParameterValue)}) • First edge picked (${hit1.typeName}). Now pick second intersecting edge"
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                commandStepPoints = emptyList(),
                                commandPromptBanner = "CHAMFER (D=${fmt(st.commandParameterValue)}) • Move cursor onto first line/rectangle edge and tap PICK PT"
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
                        _uiState.update {
                            it.copy(
                                entities = updated,
                                selectedEntityIds = emptySet(),
                                commandStepPoints = emptyList(),
                                commandPromptBanner = "CHAMFER • Applied ${fmt(d)} × ${fmt(d)} bevel"
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                commandStepPoints = emptyList(),
                                commandPromptBanner = "CHAMFER • Could not chamfer selected edges with D=${fmt(d)}"
                            )
                        }
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
                    _uiState.update {
                        it.copy(
                            entities = CadModificationEngine.updateAssociativeDimensions(stretched),
                            commandStepPoints = emptyList(),
                            commandPromptBanner = "STRETCH • Stretched crossing region by (${fmt(dx)}, ${fmt(dy)})"
                        )
                    }
                } else {
                    _uiState.update { it.copy(commandStepPoints = pts, commandPromptBanner = promptForCommand(cmd, pts.size, st.commandSubOption)) }
                }
            }

            CadCommandId.BREAK -> {
                if (pts.size >= 2) {
                    val target = findNearestSelectableEntity(pts[0])
                    if (target != null) {
                        recordUndoSnapshot()
                        val broken = CadModificationEngine.breakEntity(target, pts[0], pts[1])
                        val remaining = st.entities.filterNot { it.id == target.id } + broken
                        _uiState.update {
                            it.copy(
                                entities = remaining,
                                commandStepPoints = emptyList(),
                                commandPromptBanner = "BREAK • Split ${target.typeName}"
                            )
                        }
                    }
                } else {
                    _uiState.update { it.copy(commandStepPoints = pts, commandPromptBanner = "BREAK • Pick second break point") }
                }
            }

            CadCommandId.ALIGN -> {
                val sel = getUnlockedSelectedEntities()
                if (sel.isNotEmpty() && pts.size >= 4) {
                    recordUndoSnapshot()
                    val aligned = CadModificationEngine.alignEntities(sel, pts[0], pts[1], pts[2], pts[3], scaleToFit = true)
                    val alignedMap = aligned.associateBy { it.id }
                    val updated = st.entities.map { alignedMap[it.id] ?: it }
                    _uiState.update {
                        it.copy(
                            entities = CadModificationEngine.updateAssociativeDimensions(updated),
                            commandStepPoints = emptyList(),
                            commandPromptBanner = "ALIGN • Aligned ${sel.size} object(s)"
                        )
                    }
                } else {
                    _uiState.update { it.copy(commandStepPoints = pts, commandPromptBanner = "ALIGN • Pick point ${pts.size + 1} of 4 (Src1 -> Dst1 -> Src2 -> Dst2)") }
                }
            }

            CadCommandId.LENGTHEN -> {
                val line = findNearestEntityOfType<CadEntity.LineEntity>(pt)
                if (line != null) {
                    recordUndoSnapshot()
                    val lengthened = CadModificationEngine.lengthenEntity(line, pt, targetTotalLength = st.commandParameterValue)
                    val newLen = (lengthened as? CadEntity.LineEntity)?.length ?: st.commandParameterValue
                    val updated = st.entities.map { if (it.id == line.id) lengthened else it }
                    _uiState.update {
                        it.copy(
                            entities = CadModificationEngine.updateAssociativeDimensions(updated),
                            commandStepPoints = emptyList(),
                            commandPromptBanner = "LENGTHEN • Set total length to ${fmt(newLen)}"
                        )
                    }
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
                            commandHistoryLog = (it.commandHistoryLog + msg).takeLast(30)
                        )
                    }
                } else {
                    _uiState.update { it.copy(commandStepPoints = pts, commandPromptBanner = "MEASURE (DIST) • Pick second point") }
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
                    _uiState.update { it.copy(commandStepPoints = pts, commandPromptBanner = promptForCommand(cmd, pts.size, st.commandSubOption)) }
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
                    _uiState.update { it.copy(commandStepPoints = pts, commandPromptBanner = "DIMANGULAR • Pick point ${pts.size + 1} (Vertex -> Arm 1 -> Arm 2)") }
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
                    _uiState.update { it.copy(commandStepPoints = emptyList(), commandPromptBanner = "${cmd.commandName} • Move cursor over a Circle, Arc, or Filleted Corner and tap PICK PT") }
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
                    _uiState.update { it.copy(commandStepPoints = pts, commandPromptBanner = "DIMORDINATE • Pick leader landing point") }
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
                    _uiState.update { it.copy(commandStepPoints = pts, commandPromptBanner = "MLEADER • Pick leader shoulder point") }
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
                    _uiState.update { it.copy(commandPromptBanner = "CENTERMARK • Pick a Circle, Arc, or Filleted Corner") }
                }
            }

            else -> Unit
        }
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
                    _uiState.update {
                        it.copy(
                            entities = it.entities + closeSeg,
                            commandStepPoints = emptyList(),
                            commandPromptBanner = "LINE (Closed) • Closed loop to start point"
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            commandStepPoints = emptyList(),
                            commandPromptBanner = "LINE • Command completed"
                        )
                    }
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
                    commitSingleEntity(poly, "POLYLINE • Created ${pts.size}-vertex polyline")
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
                    commitSingleEntity(spline, "SPLINE • Created smooth spline through ${pts.size} points")
                } else {
                    cancelCurrentCommand()
                }
            }
            else -> {
                _uiState.update {
                    it.copy(
                        commandStepPoints = emptyList(),
                        commandPromptBanner = "READY • ${st.activeCommand.commandName} finished"
                    )
                }
            }
        }
    }

    fun cancelCurrentCommand() {
        _uiState.update {
            it.copy(
                activeCommand = CadCommandId.SELECT,
                commandStepPoints = emptyList(),
                selectionBoxDrag = null,
                commandPromptBanner = "CANCELLED • Ready for selection or command"
            )
        }
    }

    // --- Window & Crossing Selection Drag ---

    fun onSelectionDragStart(modelPt: Point2D) {
        if (_uiState.value.activeCommand != CadCommandId.SELECT) return
        _uiState.update {
            it.copy(selectionBoxDrag = SelectionBoxDragState(startModel = modelPt, currentModel = modelPt))
        }
    }

    fun onSelectionDragUpdate(modelPt: Point2D) {
        val currentDrag = _uiState.value.selectionBoxDrag ?: return
        _uiState.update {
            it.copy(selectionBoxDrag = currentDrag.copy(currentModel = modelPt))
        }
    }

    fun onSelectionDragEnd() {
        val st = _uiState.value
        val drag = st.selectionBoxDrag ?: return
        val box = drag.boundingBox
        val isCrossing = drag.isCrossingMode
        val frozenOrHiddenLayers = st.layers.filter { !it.isVisible || it.isFrozen }.map { it.name }.toSet()

        val matchedIds = st.entities
            .filter { it.layerName !in frozenOrHiddenLayers }
            .filter { CadGeometryKernel.isEntitySelectedByBox(it, box, isCrossingMode = isCrossing) }
            .map { it.id }
            .toSet()

        val modeLabel = if (isCrossing) "CROSSING SELECTION" else "WINDOW SELECTION"
        _uiState.update {
            it.copy(
                selectedEntityIds = matchedIds,
                selectionBoxDrag = null,
                commandPromptBanner = "$modeLabel • Selected ${matchedIds.size} object(s)"
            )
        }
    }

    fun selectAllEntities() {
        val st = _uiState.value
        val visibleLayers = st.layers.filter { it.isVisible && !it.isFrozen }.map { it.name }.toSet()
        val allIds = st.entities.filter { it.layerName in visibleLayers }.map { it.id }.toSet()
        _uiState.update {
            it.copy(
                selectedEntityIds = allIds,
                commandPromptBanner = "SELECT ALL • Selected ${allIds.size} object(s)"
            )
        }
    }

    fun clearSelection() {
        _uiState.update {
            it.copy(
                selectedEntityIds = emptySet(),
                commandPromptBanner = "Selection cleared"
            )
        }
    }

    fun deleteSelectedEntities() {
        val sel = getUnlockedSelectedEntities().map { it.id }.toSet()
        if (sel.isEmpty()) return
        recordUndoSnapshot()
        _uiState.update {
            it.copy(
                entities = it.entities.filterNot { e -> e.id in sel },
                selectedEntityIds = emptySet(),
                commandPromptBanner = "ERASE • Removed ${sel.size} object(s)"
            )
        }
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
        _uiState.update {
            it.copy(
                layers = it.layers + newLayer,
                activeLayerName = name,
                commandPromptBanner = "LAYER • Added and activated layer '$name'"
            )
        }
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
        _uiState.update { st ->
            st.copy(
                entities = st.entities.map { if (it.id in sel) it.withStyle(layerName = targetLayer) else it },
                commandPromptBanner = "PROPERTIES • Moved ${sel.size} object(s) to layer '$targetLayer'"
            )
        }
    }

    fun changeSelectedEntitiesLineType(lineType: CadLineType) {
        val sel = getUnlockedSelectedEntities().map { it.id }.toSet()
        if (sel.isEmpty()) return
        recordUndoSnapshot()
        _uiState.update { st ->
            st.copy(
                entities = st.entities.map { if (it.id in sel) it.withStyle(lineTypeOverride = lineType) else it },
                commandPromptBanner = "PROPERTIES • Set linetype to ${lineType.label}"
            )
        }
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
        _uiState.update {
            it.copy(
                entities = CadModificationEngine.updateAssociativeDimensions(updated),
                commandPromptBanner = "PROPERTIES • Updated radius to ${fmt(r)} (associative dimensions updated)"
            )
        }
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
        _uiState.update {
            it.copy(
                entities = withDims,
                commandPromptBanner = "CONSTRAINT (${type.label}) • Solved geometric constraint and updated associative dimensions"
            )
        }
    }

    fun createBlockFromSelection(blockName: String) {
        val sel = getUnlockedSelectedEntities()
        if (sel.isEmpty()) return
        recordUndoSnapshot()
        val bounds = sel.map { it.boundingBox() }.reduce { a, b -> a.union(b) }
        val def = BlockDefinition(name = blockName, basePoint = bounds.center, entities = sel)
        _uiState.update {
            it.copy(
                blocks = it.blocks + (blockName to def),
                commandPromptBanner = "BLOCK • Created reusable block '$blockName' (${sel.size} entities)"
            )
        }
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
        if (pts.isEmpty()) return emptyList()

        return when (st.activeCommand) {
            CadCommandId.LINE -> listOf(CadEntity.LineEntity(start = pts.last(), end = cur))
            CadCommandId.POLYLINE -> listOf(
                CadEntity.PolylineEntity(
                    vertices = (pts + cur).map { PolylineVertex(it) },
                    isClosed = false
                )
            )
            CadCommandId.CIRCLE -> when (st.commandSubOption) {
                "2P" -> listOf(
                    CadEntity.CircleEntity(
                        center = pts.first().midpointTo(cur),
                        radius = (pts.first().distanceTo(cur) * 0.5).coerceAtLeast(0.5)
                    )
                )
                "3P" -> if (pts.size == 2) listOfNotNull(CadGeometryKernel.circleFrom3Points(pts[0], pts[1], cur)) else emptyList()
                else -> listOf(
                    CadEntity.CircleEntity(
                        center = pts.first(),
                        radius = pts.first().distanceTo(cur).coerceAtLeast(0.5)
                    )
                )
            }
            CadCommandId.ARC -> if (pts.size == 2) {
                listOfNotNull(CadGeometryKernel.arcFrom3Points(pts[0], pts[1], cur))
            } else emptyList()
            CadCommandId.RECTANGLE -> listOf(
                CadGeometryKernel.createRectanglePolyline(
                    corner1 = pts.first(),
                    corner2 = cur,
                    filletRadius = if (st.commandSubOption == "Fillet") st.commandParameterValue else 0.0,
                    chamferDistance = if (st.commandSubOption == "Chamfer") st.commandParameterValue else 0.0
                )
            )
            CadCommandId.POLYGON -> listOf(
                CadGeometryKernel.createRegularPolygon(
                    center = pts.first(),
                    radius = pts.first().distanceTo(cur).coerceAtLeast(1.0),
                    sides = st.commandParameterValue.toInt().coerceIn(3, 64),
                    circumscribed = st.commandSubOption == "Circumscribed",
                    rotationDeg = pts.first().angleToDeg(cur)
                )
            )
            CadCommandId.SPLINE -> listOf(
                CadEntity.SplineEntity(controlPoints = pts + cur)
            )
            else -> emptyList()
        }
    }

    // --- DXF / PDF / SVG Export & Project Persistence ---

    fun currentDxfPayload(): DxfDocumentPayload {
        val st = _uiState.value
        return DxfDocumentPayload(
            storageUnit = st.storageUnit,
            displayUnit = st.displayUnit,
            layers = st.layers,
            entities = st.entities,
            blocks = st.blocks.values.toList(),
            sheetSetup = st.sheetSetup,
            precision = st.precision
        )
    }

    fun exportAndShareDxf(context: Context) {
        val payload = currentDxfPayload()
        val dxfString = CadDxfInterchange.exportToDxf(payload)
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "${_uiState.value.currentProjectId}.dxf")
        file.writeText(dxfString)
        CadDxfInterchange.shareExportedFile(context, file, "application/dxf")
    }

    fun exportAndSharePdf(context: Context) {
        val payload = currentDxfPayload()
        val file = CadDxfInterchange.exportToPdfFile(context, "${_uiState.value.currentProjectId}.pdf", payload)
        CadDxfInterchange.shareExportedFile(context, file, "application/pdf")
    }

    fun exportAndShareSvg(context: Context) {
        val payload = currentDxfPayload()
        val svgString = CadDxfInterchange.exportToSvg(payload)
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "${_uiState.value.currentProjectId}.svg")
        file.writeText(svgString)
        CadDxfInterchange.shareExportedFile(context, file, "image/svg+xml")
    }

    fun importDxfString(dxfText: String) {
        val imported = CadDxfInterchange.importFromDxf(dxfText)
        if (imported.entities.isEmpty()) return
        recordUndoSnapshot()
        _uiState.update {
            it.copy(
                storageUnit = imported.storageUnit,
                displayUnit = imported.displayUnit,
                layers = imported.layers,
                // New imported sheet defaults to Quick Dimension OFF
                entities = AutoDimensionEngine.removeAutoDimensions(imported.entities),
                blocks = imported.blocks.associateBy { b -> b.name },
                selectedEntityIds = emptySet(),
                isQuickDimensionEnabled = false,
                commandPromptBanner = "DXFIN • Imported ${imported.entities.size} entities (${imported.displayUnit.symbol}) • Quick Dimension OFF"
            )
        }
        zoomExtents()
    }

    fun saveCurrentProject(projectName: String) {
        val st = _uiState.value
        val payload = currentDxfPayload()
        viewModelScope.launch {
            repository.saveProjectDocument(
                projectId = st.currentProjectId,
                name = projectName,
                description = "2D Technical Drawing (${st.entities.size} entities, ${st.displayUnit.symbol})",
                payload = payload
            )
            _uiState.update {
                it.copy(
                    currentProjectName = projectName,
                    commandPromptBanner = "SAVE • Saved '$projectName' to local Room & DXF store"
                )
            }
        }
    }

    fun loadProject(projectId: String) {
        viewModelScope.launch {
            val loaded = repository.loadProjectDocument(projectId) ?: return@launch
            val meta = savedProjects.value.firstOrNull { it.projectId == projectId }
            undoStack.clear()
            redoStack.clear()
            // On opening a sheet/project, Quick Dimension is always OFF by default
            val entitiesWithoutAutoDim = AutoDimensionEngine.removeAutoDimensions(loaded.entities)
            _uiState.update {
                it.copy(
                    currentProjectId = projectId,
                    currentProjectName = meta?.name ?: projectId,
                    storageUnit = loaded.storageUnit,
                    displayUnit = loaded.displayUnit,
                    layers = loaded.layers,
                    entities = entitiesWithoutAutoDim,
                    blocks = loaded.blocks.associateBy { b -> b.name },
                    selectedEntityIds = emptySet(),
                    isQuickDimensionEnabled = false,
                    canUndo = false,
                    canRedo = false,
                    commandPromptBanner = "OPEN • Loaded '${meta?.name ?: projectId}' (${entitiesWithoutAutoDim.size} entities • Quick Dimension OFF)"
                )
            }
            zoomExtents()
        }
    }

    fun createNewBlankProject(name: String, unit: CadUnit) {
        undoStack.clear()
        redoStack.clear()
        val newId = "proj_${UUID.randomUUID().toString().take(8)}"
        prefs.edit().putString("display_unit", unit.name).apply()
        _uiState.update {
            it.copy(
                currentProjectId = newId,
                currentProjectName = name,
                storageUnit = unit,
                displayUnit = unit,
                layers = CadLayer.defaultLayers(),
                entities = emptyList(),
                selectedEntityIds = emptySet(),
                isQuickDimensionEnabled = false,
                canUndo = false,
                canRedo = false,
                commandPromptBanner = "NEW • Created blank drawing '$name' in ${unit.label} (Quick Dimension OFF)"
            )
        }
    }

    // --- Helpers ---

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

    private fun commitSingleEntity(entity: CadEntity, bannerMessage: String) {
        recordUndoSnapshot()
        _uiState.update {
            it.copy(
                entities = it.entities + entity,
                commandStepPoints = emptyList(),
                commandPromptBanner = bannerMessage,
                commandHistoryLog = (it.commandHistoryLog + bannerMessage).takeLast(30)
            )
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
        CadCommandId.LINE -> if (stepCount == 0) "LINE • Pick start point (or enter X,Y)" else "LINE • Pick next point, 'C' to close, or ✓ to finish"
        CadCommandId.POLYLINE -> "POLYLINE ($subOption) • Pick vertex ${stepCount + 1}, or tap ✓ to finish"
        CadCommandId.CIRCLE -> when (subOption) {
            "2P" -> if (stepCount == 0) "CIRCLE (2P) • Pick first diameter endpoint" else "CIRCLE (2P) • Pick second diameter endpoint"
            "3P" -> "CIRCLE (3P) • Pick point ${stepCount + 1} of 3 on circumference"
            "TTR" -> "CIRCLE (TTR) • Pick tangent line ${stepCount + 1} of 2"
            else -> if (stepCount == 0) "CIRCLE ($subOption) • Pick center point" else "CIRCLE ($subOption) • Pick radius/diameter point"
        }
        CadCommandId.ARC -> "ARC ($subOption) • Pick point ${stepCount + 1} of 3"
        CadCommandId.RECTANGLE -> if (stepCount == 0) "RECTANGLE ($subOption) • Pick first corner" else "RECTANGLE ($subOption) • Pick opposite corner"
        CadCommandId.POLYGON -> if (stepCount == 0) "POLYGON • Pick center point" else "POLYGON • Pick radius/vertex point"
        CadCommandId.ELLIPSE -> "ELLIPSE • Pick point ${stepCount + 1} of 3 (Center -> Major Axis -> Minor Radius)"
        CadCommandId.SPLINE -> "SPLINE • Pick fit point ${stepCount + 1}, then tap ✓ to finish"
        CadCommandId.OFFSET -> "OFFSET • Pick entity to offset, then pick side"
        CadCommandId.TRIM -> "TRIM • Move cursor over segment to trim and tap PICK PT"
        CadCommandId.EXTEND -> "EXTEND • Move cursor near endpoint to extend and tap PICK PT"
        CadCommandId.FILLET -> "FILLET • Pick first intersecting line"
        CadCommandId.CHAMFER -> "CHAMFER • Pick first intersecting line"
        CadCommandId.STRETCH -> "STRETCH • Pick crossing corner 1 -> corner 2 -> displacement point"
        CadCommandId.DIMLINEAR -> "DIMLINEAR • Pick extension origin ${stepCount + 1} of 2, then dimension line location"
        CadCommandId.DIMALIGNED -> "DIMALIGNED • Pick extension origin ${stepCount + 1} of 2, then dimension line location"
        else -> "${cmd.commandName} • Position cursor and tap PICK PT"
    }

    private fun fmt(v: Double): String = String.format(Locale.US, "%.2f", v)
}
