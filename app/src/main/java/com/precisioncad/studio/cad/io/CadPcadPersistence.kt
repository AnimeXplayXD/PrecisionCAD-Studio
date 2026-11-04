package com.precisioncad.studio.cad.io

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.util.Locale
import com.precisioncad.studio.cad.dimension.DimensionArrangementPreference
import com.precisioncad.studio.cad.dimension.DimensionStyleConfig
import com.precisioncad.studio.cad.dimension.QDimDensityPolicy
import com.precisioncad.studio.cad.model.BlockDefinition
import com.precisioncad.studio.cad.model.CadEntity
import com.precisioncad.studio.cad.model.CadLayer
import com.precisioncad.studio.cad.model.CadLineType
import com.precisioncad.studio.cad.model.CadUnit
import com.precisioncad.studio.cad.model.ConstraintType
import com.precisioncad.studio.cad.model.CursorControllerConfig
import com.precisioncad.studio.cad.model.DimensionHierarchyTier
import com.precisioncad.studio.cad.model.DimensionType
import com.precisioncad.studio.cad.model.DraftingStandard
import com.precisioncad.studio.cad.model.GeometricConstraintRecord
import com.precisioncad.studio.cad.model.GridAndSnapConfig
import com.precisioncad.studio.cad.model.HatchPattern
import com.precisioncad.studio.cad.model.OsnapMode
import com.precisioncad.studio.cad.model.Point2D
import com.precisioncad.studio.cad.model.PolylineVertex
import com.precisioncad.studio.cad.model.PrecisionFormatConfig
import com.precisioncad.studio.cad.model.SheetOrientation
import com.precisioncad.studio.cad.model.SheetPreset
import com.precisioncad.studio.cad.model.SheetSetupConfig
import com.precisioncad.studio.cad.model.UnitChangeMode
import com.precisioncad.studio.cad.model.VirtualControllerMode
import com.precisioncad.studio.cad.model.WorkspaceSpace
import com.squareup.moshi.Moshi
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Pure-Kotlin JSON DOM wrapper backed by Moshi so `.pcad` serialization and deserialization
 * execute identically in both local JVM unit tests and Android runtime without relying on `android.jar` `org.json` stubs.
 */
internal class JSONObject(
    internal val map: MutableMap<String, Any?> = LinkedHashMap()
) {
    constructor(jsonContent: String) : this(parseJsonToMap(jsonContent))

    fun put(key: String, value: Any?): JSONObject {
        map[key] = when (value) {
            is JSONObject -> value.map
            is JSONArray -> value.list
            else -> value
        }
        return this
    }

    fun has(key: String): Boolean = map.containsKey(key) && map[key] != null

    fun keys(): Iterator<String> = map.keys.iterator()

    fun optString(key: String, default: String = ""): String {
        val v = map[key] ?: return default
        return v.toString()
    }

    fun optInt(key: String, default: Int = 0): Int {
        val v = map[key] ?: return default
        return when (v) {
            is Number -> v.toInt()
            is String -> v.toDoubleOrNull()?.toInt() ?: default
            else -> default
        }
    }

    fun optLong(key: String, default: Long = 0L): Long {
        val v = map[key] ?: return default
        return when (v) {
            is Number -> v.toLong()
            is String -> v.toDoubleOrNull()?.toLong() ?: default
            else -> default
        }
    }

    fun optDouble(key: String, default: Double = 0.0): Double {
        val v = map[key] ?: return default
        return when (v) {
            is Number -> v.toDouble()
            is String -> v.toDoubleOrNull() ?: default
            else -> default
        }
    }

    fun optBoolean(key: String, default: Boolean = false): Boolean {
        val v = map[key] ?: return default
        return when (v) {
            is Boolean -> v
            is String -> v.toBooleanStrictOrNull() ?: default
            else -> default
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun optJSONObject(key: String): JSONObject? {
        return when (val v = map[key]) {
            is JSONObject -> v
            is Map<*, *> -> JSONObject((v as Map<String, Any?>).toMutableMap())
            else -> null
        }
    }

    fun optJSONArray(key: String): JSONArray? {
        return when (val v = map[key]) {
            is JSONArray -> v
            is List<*> -> JSONArray(v.toMutableList())
            else -> null
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun toString(indentSpaces: Int): String {
        val raw = moshiMapAdapter.toJson(normalizeNumbersForOutput(map))
        // Ensure integer schemaVersion is formatted as an integer literal
        return raw.replace("\"schemaVersion\": 2.0", "\"schemaVersion\": 2")
    }

    override fun toString(): String = toString(2)

    companion object {
        private val moshi: Moshi = Moshi.Builder().build()

        @Suppress("UNCHECKED_CAST")
        private val moshiMapAdapter = moshi.adapter<Map<String, Any?>>(Map::class.java as Class<Map<String, Any?>>).indent("  ")

        private fun parseJsonToMap(json: String): MutableMap<String, Any?> {
            val parsed = moshiMapAdapter.fromJson(json)
                ?: throw IllegalArgumentException("Empty or invalid .pcad JSON")
            return LinkedHashMap(parsed)
        }

        private fun normalizeNumbersForOutput(map: Map<String, Any?>): Map<String, Any?> {
            val result = LinkedHashMap<String, Any?>(map.size)
            for ((k, v) in map) {
                result[k] = normalizeValue(v)
            }
            return result
        }

        @Suppress("UNCHECKED_CAST")
        private fun normalizeValue(v: Any?): Any? = when (v) {
            is JSONObject -> normalizeNumbersForOutput(v.map)
            is JSONArray -> v.list.map { normalizeValue(it) }
            is Map<*, *> -> normalizeNumbersForOutput(v as Map<String, Any?>)
            is List<*> -> v.map { normalizeValue(it) }
            else -> v
        }
    }
}

internal class JSONArray(
    internal val list: MutableList<Any?> = mutableListOf()
) {
    constructor(collection: Collection<*>) : this(
        collection.map {
            when (it) {
                is JSONObject -> it.map
                is JSONArray -> it.list
                else -> it
            }
        }.toMutableList()
    )

    fun put(value: Any?): JSONArray {
        list.add(
            when (value) {
                is JSONObject -> value.map
                is JSONArray -> value.list
                else -> value
            }
        )
        return this
    }

    fun length(): Int = list.size

    fun getString(index: Int): String = list.getOrNull(index)?.toString() ?: ""

    fun optString(index: Int, default: String = ""): String = list.getOrNull(index)?.toString() ?: default

    @Suppress("UNCHECKED_CAST")
    fun getJSONObject(index: Int): JSONObject {
        return optJSONObject(index) ?: JSONObject()
    }

    @Suppress("UNCHECKED_CAST")
    fun optJSONObject(index: Int): JSONObject? {
        return when (val v = list.getOrNull(index)) {
            is JSONObject -> v
            is Map<*, *> -> JSONObject((v as Map<String, Any?>).toMutableMap())
            else -> null
        }
    }

    fun optDouble(index: Int, default: Double = 0.0): Double {
        return when (val v = list.getOrNull(index)) {
            is Number -> v.toDouble()
            is String -> v.toDoubleOrNull() ?: default
            else -> default
        }
    }
}


/**
 * Full editable native `.pcad` project state (Section 4.1).
 * Distinct from `.dxf` interchange: preserves exact double-precision coordinates,
 * schema version, project metadata, units, precision, sheet setup, layers, blocks,
 * all geometric and dimension entities (including auto/manual provenance and source entity IDs),
 * geometric constraints, and custom visual/snap settings.
 */
data class PcadProjectDocument(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val projectId: String = "proj_active",
    val projectName: String = "Untitled_Drawing",
    val projectDescription: String = "",
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
    val savedAtEpochMs: Long = updatedAtEpochMs,
    val explicitSavePath: String = "",
    val customTitleLocked: Boolean = false,
    val storageUnit: CadUnit = CadUnit.MM,
    val displayUnit: CadUnit = CadUnit.MM,
    val unitChangeMode: UnitChangeMode = UnitChangeMode.CONVERT_DISPLAY_UNITS,
    val precision: PrecisionFormatConfig = PrecisionFormatConfig(linearDecimals = 2, angularDecimals = 1),
    val workspaceSpace: WorkspaceSpace = WorkspaceSpace.MODEL_SPACE,
    val sheetSetup: SheetSetupConfig = SheetSetupConfig(titleBlockTitle = "Untitled_Drawing"),
    val dimensionStyle: DimensionStyleConfig = DimensionStyleConfig(),
    val activeLayerName: String = "0",
    val layers: List<CadLayer> = CadLayer.defaultLayers(),
    val blocks: List<BlockDefinition> = emptyList(),
    val entities: List<CadEntity> = emptyList(),
    val constraints: List<GeometricConstraintRecord> = emptyList(),
    val gridSnapConfig: GridAndSnapConfig = GridAndSnapConfig(),
    val snapConfig: GridAndSnapConfig = gridSnapConfig,
    val controllerConfig: CursorControllerConfig = CursorControllerConfig(),
    val isQuickDimensionEnabled: Boolean = false,
    val qdimDensity: QDimDensityPolicy = QDimDensityPolicy.BALANCED,
    val qdimArrangementPreference: DimensionArrangementPreference = DimensionArrangementPreference.AUTO_CHAIN,
    val qdimArrangement: DimensionArrangementPreference = qdimArrangementPreference
) {
    val canonicalBasename: String
        get() = CadPcadPersistence.canonicalBasename(projectName)

    val defaultPcadFileName: String
        get() = "${canonicalBasename}.pcad"

    val defaultPdfFileName: String
        get() = "${canonicalBasename}.pdf"

    fun toDxfPayload(): DxfDocumentPayload {
        val syncedSheet = if (!customTitleLocked) {
            sheetSetup.copy(titleBlockTitle = canonicalBasename)
        } else {
            sheetSetup
        }
        return DxfDocumentPayload(
            storageUnit = storageUnit,
            displayUnit = displayUnit,
            layers = layers,
            entities = entities,
            blocks = blocks,
            sheetSetup = syncedSheet,
            precision = precision
        )
    }

    fun withSynchronizedProjectName(
        rawName: String,
        lockCustomTitle: Boolean = this.customTitleLocked
    ): PcadProjectDocument {
        val cleanBase = CadPcadPersistence.canonicalBasename(rawName)
        val updatedSheet = if (!lockCustomTitle) {
            sheetSetup.copy(titleBlockTitle = cleanBase)
        } else {
            sheetSetup
        }
        val now = System.currentTimeMillis()
        return copy(
            projectName = cleanBase,
            customTitleLocked = lockCustomTitle,
            sheetSetup = updatedSheet,
            updatedAtEpochMs = now,
            savedAtEpochMs = now
        )
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 2
    }
}

data class ExplicitPcadSaveResult(
    val canonicalBasename: String,
    val fileName: String,
    val relativeDirectoryPath: String,
    val file: File,
    val mediaStoreUri: Uri?,
    val document: PcadProjectDocument
) {
    val localFile: File
        get() = file

    val basename: String
        get() = canonicalBasename

    val displayPath: String
        get() = "$relativeDirectoryPath/$fileName"
}

/**
 * Section 4 — Native `.pcad` Project Persistence, Private Quick Save & Explicit `Documents/PrecisionCAD` Saving.
 */
object CadPcadPersistence {

    const val CURRENT_SCHEMA_VERSION = 2
    const val PUBLIC_DOCUMENTS_SUBFOLDER = "PrecisionCAD"
    const val RELATIVE_DOCUMENTS_PATH = "Documents/PrecisionCAD"
    private const val QUICK_SAVE_DIR_NAME = "pcad_autosave"
    private const val QUICK_SAVE_FILE_NAME = "quick_save_working.pcad"

    private val retainedMediaStoreUris = mutableMapOf<String, Uri>()
    private val retainedExplicitFiles = mutableMapOf<String, File>()

    /**
     * Extracts the canonical project basename without `.pcad`, `.pdf`, or `.dxf` extensions.
     * Example: `"Geo.pcad"` -> `"Geo"`, `"  Geo.pdf "` -> `"Geo"`.
     */
    fun canonicalBasename(rawName: String): String {
        var cleaned = rawName.trim()
        val lower = cleaned.lowercase(Locale.ROOT)
        when {
            lower.endsWith(".pcad") -> cleaned = cleaned.dropLast(5).trim()
            lower.endsWith(".pdf") -> cleaned = cleaned.dropLast(4).trim()
            lower.endsWith(".dxf") -> cleaned = cleaned.dropLast(4).trim()
            lower.endsWith(".svg") -> cleaned = cleaned.dropLast(4).trim()
        }
        cleaned = cleaned.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        return cleaned.ifEmpty { "Untitled_Drawing" }
    }

    /**
     * Serializes [doc] into a versioned `.pcad` JSON string with exact double-precision coordinates.
     */
    fun serializeToPcadJson(doc: PcadProjectDocument): String {
        val synced = doc.withSynchronizedProjectName(doc.projectName, doc.customTitleLocked)
        val effectiveSnap = if (synced.gridSnapConfig != GridAndSnapConfig()) synced.gridSnapConfig else synced.snapConfig
        val effectiveArrangement = if (synced.qdimArrangement != DimensionArrangementPreference.AUTO_CHAIN) {
            synced.qdimArrangement
        } else {
            synced.qdimArrangementPreference
        }
        val root = JSONObject()
        root.put("format", "PrecisionCAD_Native_PCAD")
        root.put("schemaVersion", synced.schemaVersion)
        root.put("projectId", synced.projectId)
        root.put("projectName", synced.canonicalBasename)
        root.put("projectDescription", synced.projectDescription)
        root.put("createdAtEpochMs", synced.createdAtEpochMs)
        root.put("updatedAtEpochMs", synced.updatedAtEpochMs)
        root.put("savedAtEpochMs", synced.savedAtEpochMs)
        root.put("explicitSavePath", synced.explicitSavePath)
        root.put("customTitleLocked", synced.customTitleLocked)
        root.put("storageUnit", synced.storageUnit.name)
        root.put("displayUnit", synced.displayUnit.name)
        root.put("unitChangeMode", synced.unitChangeMode.name)
        root.put("workspaceSpace", synced.workspaceSpace.name)
        root.put("activeLayerName", synced.activeLayerName)
        root.put("isQuickDimensionEnabled", synced.isQuickDimensionEnabled)
        root.put("qdimDensity", synced.qdimDensity.name)
        root.put("qdimArrangementPreference", effectiveArrangement.name)

        // Precision
        root.put(
            "precision",
            JSONObject().apply {
                put("linearDecimals", synced.precision.linearDecimals)
                put("angularDecimals", synced.precision.angularDecimals)
                put("showUnitSuffix", synced.precision.showUnitSuffix)
                put("suppressTrailingZeros", synced.precision.suppressTrailingZeros)
                put("suppressLeadingZero", synced.precision.suppressLeadingZero)
            }
        )

        // Sheet Setup
        root.put(
            "sheetSetup",
            JSONObject().apply {
                put("preset", synced.sheetSetup.preset.name)
                put("orientation", synced.sheetSetup.orientation.name)
                put("customWidthMm", synced.sheetSetup.customWidthMm)
                put("customHeightMm", synced.sheetSetup.customHeightMm)
                put("marginMm", synced.sheetSetup.marginMm)
                put("plotScaleNumerator", synced.sheetSetup.plotScaleNumerator)
                put("plotScaleDenominator", synced.sheetSetup.plotScaleDenominator)
                put("sheetUnit", synced.sheetSetup.sheetUnit.name)
                put("draftingStandard", synced.sheetSetup.draftingStandard.name)
                put("titleBlockTitle", synced.sheetSetup.titleBlockTitle)
                put("titleBlockDrawingNo", synced.sheetSetup.titleBlockDrawingNo)
                put("titleBlockAuthor", synced.sheetSetup.titleBlockAuthor)
                put("titleBlockRevision", synced.sheetSetup.titleBlockRevision)
                put("showTitleBlock", synced.sheetSetup.showTitleBlock)
            }
        )

        // Snap & Polar Settings
        root.put(
            "snapConfig",
            JSONObject().apply {
                put("showGrid", effectiveSnap.showGrid)
                put("majorGridInterval", effectiveSnap.majorGridInterval)
                put("minorGridSubdivisions", effectiveSnap.minorGridSubdivisions)
                put("gridSnapEnabled", effectiveSnap.gridSnapEnabled)
                put("gridSnapInterval", effectiveSnap.gridSnapInterval)
                put("osnapEnabled", effectiveSnap.osnapEnabled)
                put("enabledOsnapModes", JSONArray(effectiveSnap.enabledOsnapModes.map { it.name }))
                put("orthoEnabled", effectiveSnap.orthoEnabled)
                put("polarTrackingEnabled", effectiveSnap.polarTrackingEnabled)
                put("polarSnapEnabled", effectiveSnap.polarSnapEnabled)
                put("polarTrackingAngleDeg", effectiveSnap.polarTrackingAngleDeg)
                put("polarAdditionalAnglesDeg", JSONArray(effectiveSnap.polarAdditionalAnglesDeg))
                put("polarSnapPresetAnglesDeg", JSONArray(effectiveSnap.polarSnapPresetAnglesDeg.toList()))
                put("polarSnapCustomAnglesDeg", JSONArray(effectiveSnap.polarSnapCustomAnglesDeg))
                put("polarRelativeToLastSegment", effectiveSnap.polarRelativeToLastSegment)
                put("polarAcquisitionToleranceDeg", effectiveSnap.polarAcquisitionToleranceDeg)
            }
        )

        // Controller Settings
        root.put(
            "controllerConfig",
            JSONObject().apply {
                put("enabled", synced.controllerConfig.enabled)
                put("mode", synced.controllerConfig.mode.name)
                put("touchpadSensitivity", synced.controllerConfig.touchpadSensitivity.toDouble())
                put("joystickMaxSpeedUnitsPerSec", synced.controllerConfig.joystickMaxSpeedUnitsPerSec)
                put("joystickVelocityExponent", synced.controllerConfig.joystickVelocityExponent)
                put("stepIncrement", synced.controllerConfig.stepIncrement)
                put("isCollapsed", synced.controllerConfig.isCollapsed)
            }
        )

        // Layers
        val layersArr = JSONArray()
        for (layer in synced.layers) {
            layersArr.put(
                JSONObject().apply {
                    put("id", layer.id)
                    put("name", layer.name)
                    put("colorHex", layer.colorHex)
                    put("lineType", layer.lineType.name)
                    put("lineWeightMm", layer.lineWeightMm.toDouble())
                    put("isVisible", layer.isVisible)
                    put("isLocked", layer.isLocked)
                    put("isFrozen", layer.isFrozen)
                }
            )
        }
        root.put("layers", layersArr)

        // Blocks
        val blocksArr = JSONArray()
        for (block in synced.blocks) {
            val blockEnts = JSONArray()
            block.entities.forEach { blockEnts.put(serializeEntity(it)) }
            val attrsObj = JSONObject()
            block.defaultAttributes.forEach { (k, v) -> attrsObj.put(k, v) }
            blocksArr.put(
                JSONObject().apply {
                    put("name", block.name)
                    put("basePoint", serializePoint(block.basePoint))
                    put("entities", blockEnts)
                    put("defaultAttributes", attrsObj)
                }
            )
        }
        root.put("blocks", blocksArr)

        // Entities
        val entitiesArr = JSONArray()
        for (ent in synced.entities) {
            entitiesArr.put(serializeEntity(ent))
        }
        root.put("entities", entitiesArr)

        // Constraints
        val constraintsArr = JSONArray()
        for (c in synced.constraints) {
            constraintsArr.put(
                JSONObject().apply {
                    put("id", c.id)
                    put("type", c.type.name)
                    put("primaryEntityId", c.primaryEntityId)
                    if (c.secondaryEntityId != null) {
                        put("secondaryEntityId", c.secondaryEntityId)
                    }
                }
            )
        }
        root.put("constraints", constraintsArr)

        return root.toString(2)
    }

    /**
     * Deserializes a `.pcad` JSON string back into a [PcadProjectDocument], returning null if invalid.
     * Supports schema migration from older versions and validates format integrity.
     */
    fun deserializeFromPcadJson(jsonContent: String): PcadProjectDocument? = runCatching {
        val root = JSONObject(jsonContent)
        val format = root.optString("format", "")
        if (format != "PrecisionCAD_Native_PCAD" && !root.has("entities")) {
            throw IllegalArgumentException("Invalid .pcad document header")
        }

        val schemaVersion = root.optInt("schemaVersion", 1).coerceAtLeast(1)
        val projectId = root.optString("projectId", "proj_active")
        val rawProjectName = root.optString("projectName", "Untitled_Drawing")
        val cleanBasename = canonicalBasename(rawProjectName)
        val projectDescription = root.optString("projectDescription", "")
        val createdAt = root.optLong("createdAtEpochMs", System.currentTimeMillis())
        val updatedAt = root.optLong("updatedAtEpochMs", System.currentTimeMillis())
        val savedAt = root.optLong("savedAtEpochMs", updatedAt)
        val explicitSavePath = root.optString("explicitSavePath", "")
        val customTitleLocked = root.optBoolean("customTitleLocked", false)

        val storageUnit = runCatching { CadUnit.valueOf(root.optString("storageUnit", "MM")) }.getOrDefault(CadUnit.MM)
        val displayUnit = runCatching { CadUnit.valueOf(root.optString("displayUnit", "MM")) }.getOrDefault(CadUnit.MM)
        val unitChangeMode = runCatching {
            UnitChangeMode.valueOf(root.optString("unitChangeMode", "CONVERT_DISPLAY_UNITS"))
        }.getOrDefault(UnitChangeMode.CONVERT_DISPLAY_UNITS)
        val workspaceSpace = runCatching {
            WorkspaceSpace.valueOf(root.optString("workspaceSpace", "MODEL_SPACE"))
        }.getOrDefault(WorkspaceSpace.MODEL_SPACE)
        val activeLayerName = root.optString("activeLayerName", "0")
        val isQuickDimensionEnabled = root.optBoolean("isQuickDimensionEnabled", false)
        val qdimDensity = runCatching {
            QDimDensityPolicy.valueOf(root.optString("qdimDensity", "BALANCED"))
        }.getOrDefault(QDimDensityPolicy.BALANCED)
        val qdimArrangement = runCatching {
            DimensionArrangementPreference.valueOf(root.optString("qdimArrangementPreference", "AUTO_CHAIN"))
        }.getOrDefault(DimensionArrangementPreference.AUTO_CHAIN)

        val precObj = root.optJSONObject("precision")
        val precision = if (precObj != null) {
            PrecisionFormatConfig(
                linearDecimals = precObj.optInt("linearDecimals", 2),
                angularDecimals = precObj.optInt("angularDecimals", 1),
                showUnitSuffix = precObj.optBoolean("showUnitSuffix", true),
                suppressTrailingZeros = precObj.optBoolean("suppressTrailingZeros", false),
                suppressLeadingZero = precObj.optBoolean("suppressLeadingZero", false)
            )
        } else {
            PrecisionFormatConfig(linearDecimals = 2, angularDecimals = 1)
        }

        val sheetObj = root.optJSONObject("sheetSetup")
        val rawSheet = if (sheetObj != null) {
            SheetSetupConfig(
                preset = runCatching { SheetPreset.valueOf(sheetObj.optString("preset", "ISO_A3")) }.getOrDefault(SheetPreset.ISO_A3),
                orientation = runCatching { SheetOrientation.valueOf(sheetObj.optString("orientation", "LANDSCAPE")) }.getOrDefault(SheetOrientation.LANDSCAPE),
                customWidthMm = sheetObj.optDouble("customWidthMm", 420.0),
                customHeightMm = sheetObj.optDouble("customHeightMm", 297.0),
                marginMm = sheetObj.optDouble("marginMm", 10.0),
                plotScaleNumerator = sheetObj.optDouble("plotScaleNumerator", 1.0),
                plotScaleDenominator = sheetObj.optDouble("plotScaleDenominator", 1.0),
                sheetUnit = runCatching { CadUnit.valueOf(sheetObj.optString("sheetUnit", "MM")) }.getOrDefault(CadUnit.MM),
                draftingStandard = runCatching { DraftingStandard.valueOf(sheetObj.optString("draftingStandard", "ISO")) }.getOrDefault(DraftingStandard.ISO),
                titleBlockTitle = sheetObj.optString("titleBlockTitle", cleanBasename),
                titleBlockDrawingNo = sheetObj.optString("titleBlockDrawingNo", "DWG-2026-104"),
                titleBlockAuthor = sheetObj.optString("titleBlockAuthor", "ENG. DEPT"),
                titleBlockRevision = sheetObj.optString("titleBlockRevision", "REV A"),
                showTitleBlock = sheetObj.optBoolean("showTitleBlock", true)
            )
        } else {
            SheetSetupConfig(titleBlockTitle = cleanBasename)
        }
        val sheetSetup = if (!customTitleLocked) {
            rawSheet.copy(titleBlockTitle = cleanBasename)
        } else {
            rawSheet
        }

        val snapObj = root.optJSONObject("snapConfig")
        val snapConfig = if (snapObj != null) {
            val osnapArr = snapObj.optJSONArray("enabledOsnapModes")
            val osnapModes = if (osnapArr != null) {
                buildSet {
                    for (i in 0 until osnapArr.length()) {
                        runCatching { OsnapMode.valueOf(osnapArr.getString(i)) }.getOrNull()?.let { add(it) }
                    }
                }
            } else {
                OsnapMode.defaultEnabledModes
            }
            val addAnglesArr = snapObj.optJSONArray("polarAdditionalAnglesDeg")
            val addAngles = if (addAnglesArr != null) {
                buildList {
                    for (i in 0 until addAnglesArr.length()) {
                        val v = addAnglesArr.optDouble(i)
                        if (v.isFinite()) add(v)
                    }
                }
            } else {
                listOf(22.5, 126.0)
            }
            val snapPresetsArr = snapObj.optJSONArray("polarSnapPresetAnglesDeg")
            val snapPresets = if (snapPresetsArr != null) {
                buildSet {
                    for (i in 0 until snapPresetsArr.length()) {
                        val v = snapPresetsArr.optDouble(i)
                        if (v.isFinite() && v > 0.0) add(v)
                    }
                }
            } else {
                setOf(45.0, 90.0)
            }
            val snapCustomArr = snapObj.optJSONArray("polarSnapCustomAnglesDeg")
            val snapCustom = if (snapCustomArr != null) {
                buildList {
                    for (i in 0 until snapCustomArr.length()) {
                        val v = snapCustomArr.optDouble(i)
                        if (v.isFinite()) add(v)
                    }
                }
            } else {
                listOf(126.0)
            }
            GridAndSnapConfig(
                showGrid = snapObj.optBoolean("showGrid", true),
                majorGridInterval = snapObj.optDouble("majorGridInterval", 50.0),
                minorGridSubdivisions = snapObj.optInt("minorGridSubdivisions", 5),
                gridSnapEnabled = snapObj.optBoolean("gridSnapEnabled", true),
                gridSnapInterval = snapObj.optDouble("gridSnapInterval", 5.0),
                osnapEnabled = snapObj.optBoolean("osnapEnabled", true),
                enabledOsnapModes = osnapModes,
                orthoEnabled = snapObj.optBoolean("orthoEnabled", false),
                polarTrackingEnabled = snapObj.optBoolean("polarTrackingEnabled", true),
                polarSnapEnabled = snapObj.optBoolean("polarSnapEnabled", false),
                polarTrackingAngleDeg = snapObj.optDouble("polarTrackingAngleDeg", 45.0),
                polarAdditionalAnglesDeg = addAngles,
                polarSnapPresetAnglesDeg = snapPresets,
                polarSnapCustomAnglesDeg = snapCustom,
                polarRelativeToLastSegment = snapObj.optBoolean("polarRelativeToLastSegment", false),
                polarAcquisitionToleranceDeg = snapObj.optDouble("polarAcquisitionToleranceDeg", 5.0)
            )
        } else {
            GridAndSnapConfig()
        }

        val ctrlObj = root.optJSONObject("controllerConfig")
        val controllerConfig = if (ctrlObj != null) {
            CursorControllerConfig(
                enabled = ctrlObj.optBoolean("enabled", true),
                mode = runCatching { VirtualControllerMode.valueOf(ctrlObj.optString("mode", "VELOCITY_JOYSTICK")) }
                    .getOrDefault(VirtualControllerMode.VELOCITY_JOYSTICK),
                touchpadSensitivity = ctrlObj.optDouble("touchpadSensitivity", 1.0).toFloat(),
                joystickMaxSpeedUnitsPerSec = ctrlObj.optDouble("joystickMaxSpeedUnitsPerSec", 140.0),
                joystickVelocityExponent = ctrlObj.optDouble("joystickVelocityExponent", 2.2),
                stepIncrement = ctrlObj.optDouble("stepIncrement", 1.0),
                isCollapsed = ctrlObj.optBoolean("isCollapsed", false)
            )
        } else {
            CursorControllerConfig()
        }

        val layersArr = root.optJSONArray("layers")
        val layers = if (layersArr != null && layersArr.length() > 0) {
            buildList {
                for (i in 0 until layersArr.length()) {
                    val lo = layersArr.getJSONObject(i)
                    add(
                        CadLayer(
                            id = lo.optString("id", "layer_$i"),
                            name = lo.optString("name", "0"),
                            colorHex = lo.optLong("colorHex", 0xFFF8FAFCL),
                            lineType = runCatching { CadLineType.valueOf(lo.optString("lineType", "CONTINUOUS")) }
                                .getOrDefault(CadLineType.CONTINUOUS),
                            lineWeightMm = lo.optDouble("lineWeightMm", 0.25).toFloat(),
                            isVisible = lo.optBoolean("isVisible", true),
                            isLocked = lo.optBoolean("isLocked", false),
                            isFrozen = lo.optBoolean("isFrozen", false)
                        )
                    )
                }
            }
        } else {
            CadLayer.defaultLayers()
        }

        val blocksArr = root.optJSONArray("blocks")
        val blocks = if (blocksArr != null) {
            buildList {
                for (i in 0 until blocksArr.length()) {
                    val bo = blocksArr.getJSONObject(i)
                    val bEntsArr = bo.optJSONArray("entities") ?: JSONArray()
                    val bEnts = buildList {
                        for (j in 0 until bEntsArr.length()) {
                            deserializeEntity(bEntsArr.getJSONObject(j))?.let { add(it) }
                        }
                    }
                    val attrsObj = bo.optJSONObject("defaultAttributes")
                    val attrs = mutableMapOf<String, String>()
                    if (attrsObj != null) {
                        for (key in attrsObj.keys()) {
                            attrs[key] = attrsObj.optString(key, "")
                        }
                    }
                    add(
                        BlockDefinition(
                            name = bo.optString("name", "BLOCK_$i"),
                            basePoint = deserializePoint(bo.optJSONObject("basePoint")),
                            entities = bEnts,
                            defaultAttributes = attrs
                        )
                    )
                }
            }
        } else {
            emptyList()
        }

        val entitiesArr = root.optJSONArray("entities") ?: JSONArray()
        val entities = buildList {
            for (i in 0 until entitiesArr.length()) {
                deserializeEntity(entitiesArr.getJSONObject(i))?.let { add(it) }
            }
        }

        val constraintsArr = root.optJSONArray("constraints")
        val constraints = if (constraintsArr != null) {
            buildList {
                for (i in 0 until constraintsArr.length()) {
                    val co = constraintsArr.getJSONObject(i)
                    val type = runCatching { ConstraintType.valueOf(co.optString("type", "HORIZONTAL")) }
                        .getOrDefault(ConstraintType.HORIZONTAL)
                    add(
                        GeometricConstraintRecord(
                            id = co.optString("id", "const_$i"),
                            type = type,
                            primaryEntityId = co.optString("primaryEntityId", ""),
                            secondaryEntityId = co.optString("secondaryEntityId", "").takeIf { it.isNotBlank() }
                        )
                    )
                }
            }
        } else {
            emptyList()
        }

        PcadProjectDocument(
            schemaVersion = maxOf(schemaVersion, PcadProjectDocument.CURRENT_SCHEMA_VERSION),
            projectId = projectId,
            projectName = cleanBasename,
            projectDescription = projectDescription,
            createdAtEpochMs = createdAt,
            updatedAtEpochMs = updatedAt,
            savedAtEpochMs = savedAt,
            explicitSavePath = explicitSavePath,
            customTitleLocked = customTitleLocked,
            storageUnit = storageUnit,
            displayUnit = displayUnit,
            unitChangeMode = unitChangeMode,
            precision = precision,
            workspaceSpace = workspaceSpace,
            sheetSetup = sheetSetup,
            activeLayerName = activeLayerName,
            layers = layers,
            blocks = blocks,
            entities = entities,
            constraints = constraints,
            gridSnapConfig = snapConfig,
            snapConfig = snapConfig,
            controllerConfig = controllerConfig,
            isQuickDimensionEnabled = isQuickDimensionEnabled,
            qdimDensity = qdimDensity,
            qdimArrangementPreference = qdimArrangement,
            qdimArrangement = qdimArrangement
        )
    }.getOrNull()

    /**
     * Section 4.2.A — Automatic Quick Save (Private Application Storage).
     * Performs a crash-safe atomic write (`.tmp` write + flush + `.bak` rotation + rename)
     * so an interrupted write never corrupts the last valid `.pcad` save.
     */
    fun performAutomaticQuickSave(
        context: Context,
        document: PcadProjectDocument,
        simulateInterruptBeforeCommit: Boolean = false
    ): File {
        val dir = File(context.filesDir, QUICK_SAVE_DIR_NAME).apply { mkdirs() }
        val targetFile = File(dir, QUICK_SAVE_FILE_NAME)
        val tmpFile = File(dir, "$QUICK_SAVE_FILE_NAME.tmp")
        val bakFile = File(dir, "$QUICK_SAVE_FILE_NAME.bak")

        val json = serializeToPcadJson(document)
        FileOutputStream(tmpFile).use { fos ->
            val bytes = json.toByteArray(Charsets.UTF_8)
            if (simulateInterruptBeforeCommit) {
                fos.write(bytes, 0, (bytes.size / 3).coerceAtLeast(1))
                fos.flush()
                throw IOException("Simulated interrupted write during .tmp stage")
            }
            fos.write(bytes)
            fos.fd.sync()
        }

        if (targetFile.exists() && targetFile.length() > 0L) {
            runCatching { targetFile.copyTo(bakFile, overwrite = true) }
        }
        if (!tmpFile.renameTo(targetFile)) {
            tmpFile.copyTo(targetFile, overwrite = true)
            tmpFile.delete()
        }
        return targetFile
    }

    /**
     * Restores the latest valid Automatic Quick Save from private app storage,
     * automatically falling back to `.bak` if the primary file was externally damaged.
     */
    fun loadLatestQuickSave(context: Context): PcadProjectDocument? {
        val dir = File(context.filesDir, QUICK_SAVE_DIR_NAME)
        val targetFile = File(dir, QUICK_SAVE_FILE_NAME)
        val bakFile = File(dir, "$QUICK_SAVE_FILE_NAME.bak")

        if (targetFile.exists()) {
            deserializeFromPcadJson(targetFile.readText(Charsets.UTF_8))?.let { return it }
        }
        if (bakFile.exists()) {
            deserializeFromPcadJson(bakFile.readText(Charsets.UTF_8))?.let { return it }
        }
        return null
    }

    fun saveExplicitToDocumentsPrecisionCad(
        context: Context,
        projectName: String,
        document: PcadProjectDocument
    ): ExplicitPcadSaveResult = saveExplicitToDocumentsPrecisionCad(
        context = context,
        document = document,
        requestedName = projectName
    )

    /**
     * Section 4.2.B — Explicit Save to `Documents/PrecisionCAD/<project-name>.pcad`.
     * - Automatically creates `Documents/PrecisionCAD` if it does not already exist.
     * - Retains the project's saved file/URI reference so subsequent Save calls update the existing `.pcad` file cleanly.
     * - Does not require a manual SAF folder picker.
     * - Synchronizes the project title and PDF title-block title with the saved `<basename>`.
     */
    fun saveExplicitToDocumentsPrecisionCad(
        context: Context,
        document: PcadProjectDocument,
        requestedName: String = document.projectName
    ): ExplicitPcadSaveResult {
        val cleanBase = canonicalBasename(requestedName)
        val fileName = "$cleanBase.pcad"
        val synchronizedDoc = document.withSynchronizedProjectName(cleanBase)
        val jsonBytes = serializeToPcadJson(synchronizedDoc).toByteArray(Charsets.UTF_8)

        // 1. Resolve user-visible Documents/PrecisionCAD directory
        val publicDocsRoot = runCatching {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        }.getOrNull()

        val targetDir = if (publicDocsRoot != null) {
            File(publicDocsRoot, PUBLIC_DOCUMENTS_SUBFOLDER)
        } else {
            File(context.filesDir, RELATIVE_DOCUMENTS_PATH)
        }

        val resolvedFile: File = try {
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }
            val file = retainedExplicitFiles[cleanBase]?.takeIf { it.parentFile == targetDir }
                ?: File(targetDir, fileName)
            writeFileAtomically(file, jsonBytes)
            retainedExplicitFiles[cleanBase] = file
            file
        } catch (_: Throwable) {
            val fallbackDir = File(context.filesDir, RELATIVE_DOCUMENTS_PATH).apply { mkdirs() }
            val fallbackFile = File(fallbackDir, fileName)
            writeFileAtomically(fallbackFile, jsonBytes)
            retainedExplicitFiles[cleanBase] = fallbackFile
            fallbackFile
        }

        // 2. On Android 10+ (API 29+), also register/update in MediaStore.Files under "Documents/PrecisionCAD/"
        var mediaUri: Uri? = retainedMediaStoreUris[cleanBase]
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                val resolver = context.contentResolver
                if (mediaUri != null) {
                    resolver.openOutputStream(mediaUri!!, "wt")?.use { out ->
                        out.write(jsonBytes)
                        out.flush()
                    }
                } else {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "$RELATIVE_DOCUMENTS_PATH/")
                    }
                    val collection = MediaStore.Files.getContentUri("external")
                    val inserted = resolver.insert(collection, values)
                    if (inserted != null) {
                        resolver.openOutputStream(inserted, "wt")?.use { out ->
                            out.write(jsonBytes)
                            out.flush()
                        }
                        mediaUri = inserted
                        retainedMediaStoreUris[cleanBase] = inserted
                    }
                }
            }
        }

        // 3. Also keep private Quick Save synchronized
        runCatching { performAutomaticQuickSave(context, synchronizedDoc) }

        return ExplicitPcadSaveResult(
            canonicalBasename = cleanBase,
            fileName = fileName,
            relativeDirectoryPath = RELATIVE_DOCUMENTS_PATH,
            file = resolvedFile,
            mediaStoreUri = mediaUri,
            document = synchronizedDoc
        )
    }

    /**
     * Lists `.pcad` files currently stored in `Documents/PrecisionCAD`.
     */
    fun listSavedDocumentsPcadFiles(context: Context): List<File> {
        val results = LinkedHashMap<String, File>()
        runCatching {
            val pubDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                PUBLIC_DOCUMENTS_SUBFOLDER
            )
            if (pubDir.exists()) {
                pubDir.listFiles { f -> f.isFile && f.name.endsWith(".pcad", ignoreCase = true) }
                    ?.forEach { results[it.name] = it }
            }
        }
        runCatching {
            val fallbackDir = File(context.filesDir, RELATIVE_DOCUMENTS_PATH)
            if (fallbackDir.exists()) {
                fallbackDir.listFiles { f -> f.isFile && f.name.endsWith(".pcad", ignoreCase = true) }
                    ?.forEach { results.putIfAbsent(it.name, it) }
            }
        }
        return results.values.sortedByDescending { it.lastModified() }
    }

    private fun writeFileAtomically(targetFile: File, bytes: ByteArray) {
        val parent = targetFile.parentFile ?: throw IOException("Missing parent directory for ${targetFile.path}")
        if (!parent.exists()) {
            parent.mkdirs()
        }
        val tmpFile = File(parent, "${targetFile.name}.tmp")
        FileOutputStream(tmpFile).use { fos ->
            fos.write(bytes)
            fos.fd.sync()
        }
        if (!tmpFile.renameTo(targetFile)) {
            tmpFile.copyTo(targetFile, overwrite = true)
            tmpFile.delete()
        }
    }

    private fun serializePoint(pt: Point2D): JSONObject = JSONObject().apply {
        put("x", pt.x)
        put("y", pt.y)
    }

    private fun deserializePoint(obj: JSONObject?): Point2D {
        if (obj == null) return Point2D(0.0, 0.0)
        return Point2D(
            x = obj.optDouble("x", 0.0),
            y = obj.optDouble("y", 0.0)
        )
    }

    private fun serializeEntity(ent: CadEntity): JSONObject {
        val obj = JSONObject()
        obj.put("id", ent.id)
        obj.put("type", ent.typeName)
        obj.put("layerName", ent.layerName)
        ent.colorHexOverride?.let { obj.put("colorHexOverride", it) }
        obj.put("lineTypeOverride", ent.lineTypeOverride.name)
        ent.lineWeightMmOverride?.let { obj.put("lineWeightMmOverride", it.toDouble()) }

        when (ent) {
            is CadEntity.LineEntity -> {
                obj.put("start", serializePoint(ent.start))
                obj.put("end", serializePoint(ent.end))
            }
            is CadEntity.PolylineEntity -> {
                val verts = JSONArray()
                for (v in ent.vertices) {
                    verts.put(
                        JSONObject().apply {
                            put("x", v.position.x)
                            put("y", v.position.y)
                            put("bulge", v.bulge)
                        }
                    )
                }
                obj.put("vertices", verts)
                obj.put("isClosed", ent.isClosed)
                obj.put("globalWidth", ent.globalWidth)
            }
            is CadEntity.CircleEntity -> {
                obj.put("center", serializePoint(ent.center))
                obj.put("radius", ent.radius)
            }
            is CadEntity.ArcEntity -> {
                obj.put("center", serializePoint(ent.center))
                obj.put("radius", ent.radius)
                obj.put("startAngleDeg", ent.startAngleDeg)
                obj.put("sweepAngleDeg", ent.sweepAngleDeg)
            }
            is CadEntity.EllipseEntity -> {
                obj.put("center", serializePoint(ent.center))
                obj.put("majorRadius", ent.majorRadius)
                obj.put("minorRadius", ent.minorRadius)
                obj.put("rotationAngleDeg", ent.rotationAngleDeg)
            }
            is CadEntity.SplineEntity -> {
                val pts = JSONArray()
                ent.controlPoints.forEach { pts.put(serializePoint(it)) }
                obj.put("controlPoints", pts)
                obj.put("isClosed", ent.isClosed)
            }
            is CadEntity.PointEntity -> {
                obj.put("position", serializePoint(ent.position))
            }
            is CadEntity.RayEntity -> {
                obj.put("basePoint", serializePoint(ent.basePoint))
                obj.put("angleDeg", ent.angleDeg)
            }
            is CadEntity.XLineEntity -> {
                obj.put("rootPoint", serializePoint(ent.rootPoint))
                obj.put("angleDeg", ent.angleDeg)
            }
            is CadEntity.DonutEntity -> {
                obj.put("center", serializePoint(ent.center))
                obj.put("innerDiameter", ent.innerDiameter)
                obj.put("outerDiameter", ent.outerDiameter)
            }
            is CadEntity.HatchEntity -> {
                val pts = JSONArray()
                ent.boundaryPoints.forEach { pts.put(serializePoint(it)) }
                obj.put("boundaryPoints", pts)
                obj.put("pattern", ent.pattern.name)
                obj.put("patternScale", ent.patternScale)
                obj.put("patternAngleDeg", ent.patternAngleDeg)
            }
            is CadEntity.TextEntity -> {
                obj.put("position", serializePoint(ent.position))
                obj.put("text", ent.text)
                obj.put("height", ent.height)
                obj.put("rotationDeg", ent.rotationDeg)
            }
            is CadEntity.LeaderEntity -> {
                obj.put("arrowTip", serializePoint(ent.arrowTip))
                obj.put("shoulderPoint", serializePoint(ent.shoulderPoint))
                obj.put("landingEnd", serializePoint(ent.landingEnd))
                obj.put("text", ent.text)
                obj.put("textHeight", ent.textHeight)
            }
            is CadEntity.CenterMarkEntity -> {
                obj.put("center", serializePoint(ent.center))
                obj.put("radius", ent.radius)
                ent.sourceEntityId?.let { obj.put("sourceEntityId", it) }
            }
            is CadEntity.BlockInsertEntity -> {
                obj.put("blockName", ent.blockName)
                obj.put("insertionPoint", serializePoint(ent.insertionPoint))
                obj.put("scaleFactor", ent.scaleFactor)
                obj.put("rotationDeg", ent.rotationDeg)
                val attrs = JSONObject()
                ent.attributes.forEach { (k, v) -> attrs.put(k, v) }
                obj.put("attributes", attrs)
            }
            is CadEntity.DimensionEntity -> {
                obj.put("dimensionType", ent.dimensionType.name)
                obj.put("anchor1", serializePoint(ent.anchor1))
                obj.put("anchor2", serializePoint(ent.anchor2))
                obj.put("dimensionLinePoint", serializePoint(ent.dimensionLinePoint))
                ent.angularVertex?.let { obj.put("angularVertex", serializePoint(it)) }
                obj.put("customPrefix", ent.customPrefix)
                obj.put("toleranceText", ent.toleranceText)
                ent.textOverride?.let { obj.put("textOverride", it) }
                obj.put("sourceEntityIds", JSONArray(ent.sourceEntityIds))
                obj.put("isAutoGenerated", ent.isAutoGenerated)
                obj.put("hierarchyTier", ent.hierarchyTier.name)
            }
        }
        return obj
    }

    private fun deserializeEntity(obj: JSONObject): CadEntity? {
        val id = obj.optString("id", java.util.UUID.randomUUID().toString())
        val type = obj.optString("type", "")
        val layerName = obj.optString("layerName", "0")
        val colorHexOverride = if (obj.has("colorHexOverride")) obj.optLong("colorHexOverride") else null
        val lineTypeOverride = runCatching {
            CadLineType.valueOf(obj.optString("lineTypeOverride", "BY_LAYER"))
        }.getOrDefault(CadLineType.BY_LAYER)
        val lineWeightMmOverride = if (obj.has("lineWeightMmOverride")) {
            obj.optDouble("lineWeightMmOverride").toFloat()
        } else {
            null
        }

        return when (type) {
            "LINE" -> CadEntity.LineEntity(
                id = id,
                layerName = layerName,
                colorHexOverride = colorHexOverride,
                lineTypeOverride = lineTypeOverride,
                lineWeightMmOverride = lineWeightMmOverride,
                start = deserializePoint(obj.optJSONObject("start")),
                end = deserializePoint(obj.optJSONObject("end"))
            )
            "LWPOLYLINE", "POLYLINE" -> {
                val vertsArr = obj.optJSONArray("vertices") ?: JSONArray()
                val verts = buildList {
                    for (i in 0 until vertsArr.length()) {
                        val vo = vertsArr.getJSONObject(i)
                        add(
                            PolylineVertex(
                                position = Point2D(vo.optDouble("x", 0.0), vo.optDouble("y", 0.0)),
                                bulge = vo.optDouble("bulge", 0.0)
                            )
                        )
                    }
                }
                CadEntity.PolylineEntity(
                    id = id,
                    layerName = layerName,
                    colorHexOverride = colorHexOverride,
                    lineTypeOverride = lineTypeOverride,
                    lineWeightMmOverride = lineWeightMmOverride,
                    vertices = verts,
                    isClosed = obj.optBoolean("isClosed", false),
                    globalWidth = obj.optDouble("globalWidth", 0.0)
                )
            }
            "CIRCLE" -> CadEntity.CircleEntity(
                id = id,
                layerName = layerName,
                colorHexOverride = colorHexOverride,
                lineTypeOverride = lineTypeOverride,
                lineWeightMmOverride = lineWeightMmOverride,
                center = deserializePoint(obj.optJSONObject("center")),
                radius = obj.optDouble("radius", 10.0)
            )
            "ARC" -> CadEntity.ArcEntity(
                id = id,
                layerName = layerName,
                colorHexOverride = colorHexOverride,
                lineTypeOverride = lineTypeOverride,
                lineWeightMmOverride = lineWeightMmOverride,
                center = deserializePoint(obj.optJSONObject("center")),
                radius = obj.optDouble("radius", 10.0),
                startAngleDeg = obj.optDouble("startAngleDeg", 0.0),
                sweepAngleDeg = obj.optDouble("sweepAngleDeg", 90.0)
            )
            "ELLIPSE" -> CadEntity.EllipseEntity(
                id = id,
                layerName = layerName,
                colorHexOverride = colorHexOverride,
                lineTypeOverride = lineTypeOverride,
                lineWeightMmOverride = lineWeightMmOverride,
                center = deserializePoint(obj.optJSONObject("center")),
                majorRadius = obj.optDouble("majorRadius", 20.0),
                minorRadius = obj.optDouble("minorRadius", 10.0),
                rotationAngleDeg = obj.optDouble("rotationAngleDeg", 0.0)
            )
            "SPLINE" -> {
                val ptsArr = obj.optJSONArray("controlPoints") ?: JSONArray()
                val pts = buildList {
                    for (i in 0 until ptsArr.length()) {
                        add(deserializePoint(ptsArr.optJSONObject(i)))
                    }
                }
                CadEntity.SplineEntity(
                    id = id,
                    layerName = layerName,
                    colorHexOverride = colorHexOverride,
                    lineTypeOverride = lineTypeOverride,
                    lineWeightMmOverride = lineWeightMmOverride,
                    controlPoints = pts,
                    isClosed = obj.optBoolean("isClosed", false)
                )
            }
            "POINT" -> CadEntity.PointEntity(
                id = id,
                layerName = layerName,
                colorHexOverride = colorHexOverride,
                lineTypeOverride = lineTypeOverride,
                lineWeightMmOverride = lineWeightMmOverride,
                position = deserializePoint(obj.optJSONObject("position"))
            )
            "RAY" -> CadEntity.RayEntity(
                id = id,
                layerName = layerName,
                colorHexOverride = colorHexOverride,
                lineTypeOverride = lineTypeOverride,
                lineWeightMmOverride = lineWeightMmOverride,
                basePoint = deserializePoint(obj.optJSONObject("basePoint")),
                angleDeg = obj.optDouble("angleDeg", 0.0)
            )
            "XLINE" -> CadEntity.XLineEntity(
                id = id,
                layerName = layerName,
                colorHexOverride = colorHexOverride,
                lineTypeOverride = lineTypeOverride,
                lineWeightMmOverride = lineWeightMmOverride,
                rootPoint = deserializePoint(obj.optJSONObject("rootPoint")),
                angleDeg = obj.optDouble("angleDeg", 0.0)
            )
            "DONUT" -> CadEntity.DonutEntity(
                id = id,
                layerName = layerName,
                colorHexOverride = colorHexOverride,
                lineTypeOverride = lineTypeOverride,
                lineWeightMmOverride = lineWeightMmOverride,
                center = deserializePoint(obj.optJSONObject("center")),
                innerDiameter = obj.optDouble("innerDiameter", 5.0),
                outerDiameter = obj.optDouble("outerDiameter", 10.0)
            )
            "HATCH" -> {
                val ptsArr = obj.optJSONArray("boundaryPoints") ?: JSONArray()
                val pts = buildList {
                    for (i in 0 until ptsArr.length()) {
                        add(deserializePoint(ptsArr.optJSONObject(i)))
                    }
                }
                CadEntity.HatchEntity(
                    id = id,
                    layerName = layerName,
                    colorHexOverride = colorHexOverride,
                    lineTypeOverride = lineTypeOverride,
                    lineWeightMmOverride = lineWeightMmOverride,
                    boundaryPoints = pts,
                    pattern = runCatching { HatchPattern.valueOf(obj.optString("pattern", "ANSI31")) }.getOrDefault(HatchPattern.ANSI31),
                    patternScale = obj.optDouble("patternScale", 1.0),
                    patternAngleDeg = obj.optDouble("patternAngleDeg", 0.0)
                )
            }
            "MTEXT", "TEXT" -> CadEntity.TextEntity(
                id = id,
                layerName = layerName,
                colorHexOverride = colorHexOverride,
                lineTypeOverride = lineTypeOverride,
                lineWeightMmOverride = lineWeightMmOverride,
                position = deserializePoint(obj.optJSONObject("position")),
                text = obj.optString("text", ""),
                height = obj.optDouble("height", 3.5),
                rotationDeg = obj.optDouble("rotationDeg", 0.0)
            )
            "MLEADER", "LEADER" -> CadEntity.LeaderEntity(
                id = id,
                layerName = layerName,
                colorHexOverride = colorHexOverride,
                lineTypeOverride = lineTypeOverride,
                lineWeightMmOverride = lineWeightMmOverride,
                arrowTip = deserializePoint(obj.optJSONObject("arrowTip")),
                shoulderPoint = deserializePoint(obj.optJSONObject("shoulderPoint")),
                landingEnd = deserializePoint(obj.optJSONObject("landingEnd")),
                text = obj.optString("text", ""),
                textHeight = obj.optDouble("textHeight", 3.0)
            )
            "CENTERMARK" -> CadEntity.CenterMarkEntity(
                id = id,
                layerName = layerName,
                colorHexOverride = colorHexOverride,
                lineTypeOverride = lineTypeOverride,
                lineWeightMmOverride = lineWeightMmOverride,
                center = deserializePoint(obj.optJSONObject("center")),
                radius = obj.optDouble("radius", 10.0),
                sourceEntityId = obj.optString("sourceEntityId", "").takeIf { it.isNotBlank() }
            )
            "INSERT" -> {
                val attrsObj = obj.optJSONObject("attributes")
                val attrs = mutableMapOf<String, String>()
                if (attrsObj != null) {
                    for (k in attrsObj.keys()) {
                        attrs[k] = attrsObj.optString(k, "")
                    }
                }
                CadEntity.BlockInsertEntity(
                    id = id,
                    layerName = layerName,
                    colorHexOverride = colorHexOverride,
                    lineTypeOverride = lineTypeOverride,
                    lineWeightMmOverride = lineWeightMmOverride,
                    blockName = obj.optString("blockName", "BLOCK"),
                    insertionPoint = deserializePoint(obj.optJSONObject("insertionPoint")),
                    scaleFactor = obj.optDouble("scaleFactor", 1.0),
                    rotationDeg = obj.optDouble("rotationDeg", 0.0),
                    attributes = attrs
                )
            }
            "DIMENSION" -> {
                val srcArr = obj.optJSONArray("sourceEntityIds")
                val srcIds = if (srcArr != null) {
                    buildList {
                        for (i in 0 until srcArr.length()) {
                            add(srcArr.optString(i))
                        }
                    }
                } else {
                    emptyList()
                }
                val dimType = runCatching {
                    DimensionType.valueOf(obj.optString("dimensionType", "LINEAR_HORIZONTAL"))
                }.getOrDefault(DimensionType.LINEAR_HORIZONTAL)
                val tier = runCatching {
                    DimensionHierarchyTier.valueOf(obj.optString("hierarchyTier", "PRIMARY_OVERALL"))
                }.getOrDefault(DimensionHierarchyTier.PRIMARY_OVERALL)

                CadEntity.DimensionEntity(
                    id = id,
                    layerName = layerName,
                    colorHexOverride = colorHexOverride,
                    lineTypeOverride = lineTypeOverride,
                    lineWeightMmOverride = lineWeightMmOverride,
                    dimensionType = dimType,
                    anchor1 = deserializePoint(obj.optJSONObject("anchor1")),
                    anchor2 = deserializePoint(obj.optJSONObject("anchor2")),
                    dimensionLinePoint = deserializePoint(obj.optJSONObject("dimensionLinePoint")),
                    angularVertex = obj.optJSONObject("angularVertex")?.let { deserializePoint(it) },
                    customPrefix = obj.optString("customPrefix", ""),
                    toleranceText = obj.optString("toleranceText", ""),
                    textOverride = obj.optString("textOverride", "").takeIf { it.isNotBlank() },
                    sourceEntityIds = srcIds,
                    isAutoGenerated = obj.optBoolean("isAutoGenerated", false),
                    hierarchyTier = tier
                )
            }
            else -> null
        }
    }
}
