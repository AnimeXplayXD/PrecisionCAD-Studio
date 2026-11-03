package com.example.cad.data

import android.content.Context
import androidx.core.content.edit
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.cad.dimension.AutoDimensionEngine
import com.example.cad.dimension.QDimDensityPolicy
import com.example.cad.io.CadDxfInterchange
import com.example.cad.io.DxfDocumentPayload
import com.example.cad.kernel.CadGeometryKernel
import com.example.cad.model.BlockDefinition
import com.example.cad.model.CadEntity
import com.example.cad.model.CadLayer
import com.example.cad.model.CadLineType
import com.example.cad.model.CadUnit
import com.example.cad.model.HatchPattern
import com.example.cad.model.Point2D
import com.example.cad.model.PrecisionFormatConfig
import com.example.cad.model.SheetPreset
import com.example.cad.model.SheetSetupConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

@Entity(tableName = "cad_projects")
data class CadProjectMetadataEntity(
    @PrimaryKey val projectId: String,
    val name: String,
    val description: String,
    val workingUnitSymbol: String = "mm",
    val sheetPresetName: String = "ISO_A3",
    val entityCount: Int = 0,
    val dimensionCount: Int = 0,
    val schemaVersion: Int = 1,
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)

@Dao
interface CadProjectDao {
    @Query("SELECT * FROM cad_projects ORDER BY updatedAtEpochMs DESC")
    fun observeAllProjects(): Flow<List<CadProjectMetadataEntity>>

    @Query("SELECT * FROM cad_projects ORDER BY updatedAtEpochMs DESC")
    suspend fun getAllProjectsOnce(): List<CadProjectMetadataEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProject(project: CadProjectMetadataEntity)

    @Query("DELETE FROM cad_projects WHERE projectId = :projectId")
    suspend fun deleteProjectById(projectId: String)
}

@Database(entities = [CadProjectMetadataEntity::class], version = 1, exportSchema = false)
abstract class CadAppDatabase : RoomDatabase() {
    abstract fun projectDao(): CadProjectDao

    companion object {
        @Volatile
        private var INSTANCE: CadAppDatabase? = null

        fun getInstance(context: Context): CadAppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    CadAppDatabase::class.java,
                    "precision_cad_studio.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}

/**
 * Manages Room metadata indexing and versioned CAD document storage on disk.
 */
class CadProjectRepository(
    private val context: Context,
    private val dao: CadProjectDao
) {
    val projectsFlow: Flow<List<CadProjectMetadataEntity>> = dao.observeAllProjects()

    private val projectsDir: File
        get() = File(context.filesDir, "projects").apply { mkdirs() }

    /**
     * Section 62.4 & 62.5 — Safe One-Time Legacy Template Cleanup Migration:
     * Never seeds starter drawings on launch. On upgrade from earlier versions, removes the three
     * known stock industrial templates ONLY if their ID, name, and stock description remain untouched.
     * Preserves all user-created or user-modified drawings.
     */
    suspend fun cleanupUntouchedLegacyTemplatesOnce() = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("precision_cad_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("v16_legacy_templates_cleaned", false)) return@withContext

        val stockTemplates = mapOf(
            "proj_mounting_plate" to (
                "CNC 6-Hole Mounting Plate" to
                    "136 × 96 mm rectangular plate with 6 equally spaced ⌀10 mm mounting holes and ⌀28 mm central bore (Section 23 & 28 QDIM Showcase)."
                ),
            "proj_flanged_housing" to (
                "Flanged Bearing Housing" to
                    "Circular ⌀140 mm industrial bearing flange with 6-bolt polar array, keyway bore, and centerlines."
                ),
            "proj_bellcrank_bracket" to (
                "Slotted Bellcrank & Section" to
                    "Machined bellcrank lever with ANSI31 hatched cross-section view, fillets, and GD&T callout leader."
                )
        )

        val existing = dao.getAllProjectsOnce()
        for (proj in existing) {
            val stock = stockTemplates[proj.projectId]
            if (stock != null && proj.name == stock.first && proj.description == stock.second) {
                deleteProject(proj.projectId)
            }
        }
        prefs.edit { putBoolean("v16_legacy_templates_cleaned", true) }
    }

    suspend fun saveProjectDocument(
        projectId: String,
        name: String,
        description: String,
        payload: DxfDocumentPayload,
        saveToPublicDocuments: Boolean = true,
        pcadDocument: com.example.cad.io.PcadProjectDocument? = null
    ) = withContext(Dispatchers.IO) {
        val cleanName = com.example.cad.io.CadPcadPersistence.canonicalBasename(name)
        val syncedSheet = payload.sheetSetup.copy(titleBlockTitle = cleanName)
        val syncedPayload = payload.copy(sheetSetup = syncedSheet)

        val pcadDoc = pcadDocument?.withSynchronizedProjectName(cleanName)
            ?: com.example.cad.io.PcadProjectDocument(
                projectId = projectId,
                projectName = cleanName,
                projectDescription = description,
                storageUnit = syncedPayload.storageUnit,
                displayUnit = syncedPayload.displayUnit,
                precision = syncedPayload.precision,
                sheetSetup = syncedSheet,
                layers = syncedPayload.layers,
                blocks = syncedPayload.blocks,
                entities = syncedPayload.entities
            )
        val pcadJson = com.example.cad.io.CadPcadPersistence.serializeToPcadJson(pcadDoc)
        val pcadFile = File(projectsDir, "$projectId.pcad")
        pcadFile.writeText(pcadJson, Charsets.UTF_8)

        // Also keep DXF interchange cache for backwards compatibility
        val dxfContent = CadDxfInterchange.exportToDxf(syncedPayload)
        val docFile = File(projectsDir, "$projectId.dxf")
        docFile.writeText(dxfContent, Charsets.UTF_8)

        if (saveToPublicDocuments) {
            runCatching {
                com.example.cad.io.CadPcadPersistence.saveExplicitToDocumentsPrecisionCad(
                    context = context,
                    document = pcadDoc,
                    requestedName = cleanName
                )
            }
        }

        val dimCount = syncedPayload.entities.count { it is CadEntity.DimensionEntity }
        val meta = CadProjectMetadataEntity(
            projectId = projectId,
            name = cleanName,
            description = description,
            workingUnitSymbol = syncedPayload.displayUnit.symbol,
            sheetPresetName = syncedPayload.sheetSetup.preset.name,
            entityCount = syncedPayload.entities.size,
            dimensionCount = dimCount,
            schemaVersion = com.example.cad.io.PcadProjectDocument.CURRENT_SCHEMA_VERSION,
            updatedAtEpochMs = System.currentTimeMillis()
        )
        dao.upsertProject(meta)
    }

    suspend fun renameProject(projectId: String, newName: String) = withContext(Dispatchers.IO) {
        val cleanName = com.example.cad.io.CadPcadPersistence.canonicalBasename(newName)
        if (cleanName.isEmpty()) return@withContext
        val existing = dao.getAllProjectsOnce().firstOrNull { it.projectId == projectId } ?: return@withContext
        dao.upsertProject(
            existing.copy(
                name = cleanName,
                updatedAtEpochMs = System.currentTimeMillis()
            )
        )
        val pcadFile = File(projectsDir, "$projectId.pcad")
        if (pcadFile.exists()) {
            runCatching {
                val doc = com.example.cad.io.CadPcadPersistence.deserializeFromPcadJson(pcadFile.readText(Charsets.UTF_8))
                    ?: return@runCatching
                val updated = doc.withSynchronizedProjectName(cleanName)
                pcadFile.writeText(com.example.cad.io.CadPcadPersistence.serializeToPcadJson(updated), Charsets.UTF_8)
                com.example.cad.io.CadPcadPersistence.saveExplicitToDocumentsPrecisionCad(context, updated, cleanName)
            }
        }
    }

    suspend fun loadProjectDocument(projectId: String): DxfDocumentPayload? = withContext(Dispatchers.IO) {
        val pcadFile = File(projectsDir, "$projectId.pcad")
        if (pcadFile.exists()) {
            runCatching {
                val doc = com.example.cad.io.CadPcadPersistence
                    .deserializeFromPcadJson(pcadFile.readText(Charsets.UTF_8))
                if (doc != null) {
                    return@withContext doc.toDxfPayload()
                }
            }
        }
        val docFile = File(projectsDir, "$projectId.dxf")
        if (!docFile.exists()) return@withContext null
        val content = docFile.readText(Charsets.UTF_8)
        CadDxfInterchange.importFromDxf(content)
    }

    suspend fun deleteProject(projectId: String) = withContext(Dispatchers.IO) {
        File(projectsDir, "$projectId.pcad").delete()
        File(projectsDir, "$projectId.dxf").delete()
        dao.deleteProjectById(projectId)
    }
}

object CadTemplateFactory {

    /**
     * Creates 3 realistic industrial 2D technical drawings demonstrating
     * QDIM automatic dimensioning, hole patterns, section hatching, centerlines, and blocks.
     */
    fun createIndustrialTemplates(): List<Pair<CadProjectMetadataEntity, DxfDocumentPayload>> {
        val platePayload = createSixHoleMountingPlate()
        val flangePayload = createFlangedValveHousing()
        val bracketPayload = createSlottedBellcrankBracket()

        return listOf(
            CadProjectMetadataEntity(
                projectId = "proj_mounting_plate",
                name = "CNC 6-Hole Mounting Plate",
                description = "136 × 96 mm rectangular plate with 6 equally spaced ⌀10 mm mounting holes and ⌀28 mm central bore (Section 23 & 28 QDIM Showcase)."
            ) to platePayload,
            CadProjectMetadataEntity(
                projectId = "proj_flanged_housing",
                name = "Flanged Bearing Housing",
                description = "Circular ⌀140 mm industrial bearing flange with 6-bolt polar array, keyway bore, and centerlines."
            ) to flangePayload,
            CadProjectMetadataEntity(
                projectId = "proj_bellcrank_bracket",
                name = "Slotted Bellcrank & Section",
                description = "Machined bellcrank lever with ANSI31 hatched cross-section view, fillets, and GD&T callout leader."
            ) to bracketPayload
        )
    }

    /**
     * Section 23.2 & 28.4 — Authoritative 136 × 96 mm CNC 6-Hole Mounting Plate:
     * - Outer profile: 136 × 96 mm (`X = 32..168`, `Y = 102..198`) with R6 filleted corners (`bulge = 0.41421356`).
     * - Central precision bore: ⌀28 mm (`R14`) at `(100.0, 150.0)`.
     * - Six ⌀10 mm (`R5`) mounting holes at `X = 60, 100, 140` and `Y = 126, 174`.
     * - Symmetry centerlines passing through `(100.0, 150.0)`.
     */
    fun createSixHoleMountingPlate(): DxfDocumentPayload {
        val layers = CadLayer.defaultLayers()
        val rawEntities = mutableListOf<CadEntity>()

        // Outer 136 x 96 mm plate (X: 32..168, Y: 102..198) with R6 filleted corners
        rawEntities.add(
            CadGeometryKernel.createRectanglePolyline(
                corner1 = Point2D(32.0, 102.0),
                corner2 = Point2D(168.0, 198.0),
                filletRadius = 6.0,
                layerName = "0"
            )
        )

        // Central ⌀28 mm precision bore at (100, 150)
        rawEntities.add(
            CadEntity.CircleEntity(
                layerName = "0",
                center = Point2D(100.0, 150.0),
                radius = 14.0
            )
        )

        // 6 equally spaced ⌀10 mm mounting holes (2 rows x 3 cols: X = 60, 100, 140; Y = 126, 174)
        for (y in listOf(126.0, 174.0)) {
            for (x in listOf(60.0, 100.0, 140.0)) {
                rawEntities.add(
                    CadEntity.CircleEntity(
                        layerName = "0",
                        center = Point2D(x, y),
                        radius = 5.0
                    )
                )
            }
        }

        // Symmetry centerlines through (100, 150)
        rawEntities.add(
            CadEntity.LineEntity(
                layerName = "CENTERLINES",
                lineTypeOverride = CadLineType.CENTER,
                start = Point2D(24.0, 150.0),
                end = Point2D(176.0, 150.0)
            )
        )
        rawEntities.add(
            CadEntity.LineEntity(
                layerName = "CENTERLINES",
                lineTypeOverride = CadLineType.CENTER,
                start = Point2D(100.0, 94.0),
                end = Point2D(100.0, 206.0)
            )
        )

        val boltBlock = BlockDefinition(
            name = "M8_HEX_SOCKET",
            basePoint = Point2D(0.0, 0.0),
            entities = listOf(
                CadEntity.CircleEntity(center = Point2D(0.0, 0.0), radius = 6.5),
                CadGeometryKernel.createRegularPolygon(Point2D(0.0, 0.0), 4.0, 6, circumscribed = true)
            )
        )

        // Note: Quick Dimension is OFF by default on a new sheet/page until explicitly enabled by the user.
        return DxfDocumentPayload(
            storageUnit = CadUnit.MM,
            displayUnit = CadUnit.MM,
            layers = layers,
            entities = rawEntities,
            blocks = listOf(boltBlock),
            sheetSetup = SheetSetupConfig(
                preset = SheetPreset.ISO_A3,
                titleBlockTitle = "CNC 6-HOLE MOUNTING PLATE",
                titleBlockDrawingNo = "DWG-2026-104"
            ),
            precision = PrecisionFormatConfig(linearDecimals = 2, angularDecimals = 1)
        )
    }

    fun createFlangedValveHousing(): DxfDocumentPayload {
        val layers = CadLayer.defaultLayers()
        val rawEntities = mutableListOf<CadEntity>()
        val center = Point2D(80.0, 60.0)

        // Outer flange ⌀140 (R70), Pitch circle ⌀108 (R54), Inner boss ⌀76 (R38), Shaft bore ⌀44 (R22)
        rawEntities.add(CadEntity.CircleEntity(layerName = "0", center = center, radius = 70.0))
        rawEntities.add(
            CadEntity.CircleEntity(
                layerName = "CENTERLINES",
                lineTypeOverride = CadLineType.CENTER,
                center = center,
                radius = 54.0
            )
        )
        rawEntities.add(CadEntity.CircleEntity(layerName = "0", center = center, radius = 38.0))
        rawEntities.add(CadEntity.CircleEntity(layerName = "0", center = center, radius = 22.0))

        // 6-bolt polar hole array (⌀12 holes on R54 bolt circle)
        for (i in 0 until 6) {
            val angle = i * 60.0
            val hc = center.polarOffset(54.0, angle)
            rawEntities.add(CadEntity.CircleEntity(layerName = "0", center = hc, radius = 6.0))
        }

        return DxfDocumentPayload(
            storageUnit = CadUnit.MM,
            displayUnit = CadUnit.MM,
            layers = layers,
            entities = rawEntities,
            sheetSetup = SheetSetupConfig(
                preset = SheetPreset.ISO_A3,
                titleBlockTitle = "FLANGED BEARING HOUSING",
                titleBlockDrawingNo = "DWG-2026-208"
            ),
            precision = PrecisionFormatConfig(linearDecimals = 2, angularDecimals = 1)
        )
    }

    fun createSlottedBellcrankBracket(): DxfDocumentPayload {
        val layers = CadLayer.defaultLayers()
        val rawEntities = mutableListOf<CadEntity>()

        // L-shaped bracket outer profile
        val profile = CadGeometryKernel.createRectanglePolyline(
            corner1 = Point2D(0.0, 0.0),
            corner2 = Point2D(100.0, 65.0),
            chamferDistance = 8.0,
            layerName = "0"
        )
        rawEntities.add(profile)

        // Two precision bearing bores
        rawEntities.add(CadEntity.CircleEntity(layerName = "0", center = Point2D(25.0, 32.5), radius = 10.0))
        rawEntities.add(CadEntity.CircleEntity(layerName = "0", center = Point2D(75.0, 32.5), radius = 8.0))

        // Cross-section hatched rib zone
        rawEntities.add(
            CadEntity.HatchEntity(
                layerName = "HATCH",
                boundaryPoints = listOf(
                    Point2D(40.0, 26.0),
                    Point2D(62.0, 26.0),
                    Point2D(62.0, 39.0),
                    Point2D(40.0, 39.0)
                ),
                pattern = HatchPattern.ANSI31,
                patternScale = 1.0,
                patternAngleDeg = 45.0
            )
        )

        return DxfDocumentPayload(
            storageUnit = CadUnit.MM,
            displayUnit = CadUnit.MM,
            layers = layers,
            entities = rawEntities,
            sheetSetup = SheetSetupConfig(
                preset = SheetPreset.ISO_A4,
                titleBlockTitle = "SLOTTED BELLCRANK BRACKET",
                titleBlockDrawingNo = "DWG-2026-312"
            ),
            precision = PrecisionFormatConfig(linearDecimals = 2, angularDecimals = 1)
        )
    }
}
