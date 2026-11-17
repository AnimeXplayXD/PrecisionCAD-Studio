package com.precisioncad.studio.test

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.precisioncad.studio.MainActivity
import com.precisioncad.studio.cad.io.CadDxfInterchange
import com.precisioncad.studio.cad.io.DxfDocumentPayload
import com.precisioncad.studio.cad.model.CadEntity
import com.precisioncad.studio.cad.model.CadLayer
import com.precisioncad.studio.cad.model.CadUnit
import com.precisioncad.studio.cad.model.Point2D
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Instrumented contract tests verifying real PackageManager intent-filter resolution
 * and ACTION_VIEW FileProvider launch behavior on Android devices/emulators.
 */
@RunWith(AndroidJUnit4::class)
class ExternalCadImportTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val exportsDir = File(context.cacheDir, "exports")

    @After
    fun cleanup() {
        exportsDir.listFiles()?.forEach { it.delete() }
    }

    @Test
    fun manifestResolvesSupportedCadMimeTypesAndRejectsUnsupported3dFormats() {
        val pm = context.packageManager

        fun canResolve(mime: String?, uri: String): Boolean {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                val parsed = Uri.parse(uri)
                if (mime != null) setDataAndType(parsed, mime) else data = parsed
            }
            return pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                .any { it.activityInfo.packageName == context.packageName }
        }

        assertTrue(canResolve("image/vnd.dxf", "content://com.lumina.gallery.fileprovider/part.dxf"))
        assertTrue(canResolve("application/octet-stream", "content://com.lumina.gallery.fileprovider/part.bin"))
        assertTrue(canResolve(null, "content://com.lumina.gallery.fileprovider/housing.dxf"))
        assertTrue(canResolve(null, "content://com.lumina.gallery.fileprovider/housing.pcad"))
        assertFalse(canResolve("application/step", "content://com.lumina.gallery.fileprovider/solid.step"))
        assertFalse(canResolve("model/stl", "content://com.lumina.gallery.fileprovider/mesh.stl"))
    }

    @Test
    fun coldStartActionViewWithFileProviderDxfLaunchesWorkspaceCleanly() {
        exportsDir.mkdirs()
        val dxfFile = File(exportsDir, "instrumented_flange.dxf")
        val payload = DxfDocumentPayload(
            storageUnit = CadUnit.MM,
            displayUnit = CadUnit.MM,
            layers = CadLayer.defaultLayers(),
            entities = listOf(
                CadEntity.LineEntity(
                    id = "inst_l1",
                    start = Point2D(0.0, 0.0),
                    end = Point2D(100.0, 0.0)
                )
            )
        )
        dxfFile.writeText(CadDxfInterchange.exportToDxf(payload))
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", dxfFile)

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setClass(context, MainActivity::class.java)
            setDataAndType(uri, "image/vnd.dxf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
            }
        }
    }
}
