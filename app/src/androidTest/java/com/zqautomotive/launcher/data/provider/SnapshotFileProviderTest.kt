package com.zqautomotive.launcher.data.provider

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zqautomotive.launcher.data.local.SnapshotShareRepository
import com.zqautomotive.launcher.ui.lab.database.SnapshotShareLauncher
import com.zqautomotive.launcher.ui.lab.database.SnapshotShareResult
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SnapshotFileProviderTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun onlyDedicatedCacheIsMappedAndChooserCarriesReadOnlyClip() {
        val authority = "${context.packageName}.snapshotfiles"
        listOf(
            File(context.filesDir, "records_snapshot.json"),
            File(context.cacheDir, "private.json"),
            context.getDatabasePath("car_launcher_lab_test.db"),
        ).forEach { privateFile ->
            assertThrows(IllegalArgumentException::class.java) {
                FileProvider.getUriForFile(context, authority, privateFile)
            }
        }
        val source = File.createTempFile("share-test-", ".json", context.cacheDir)
        val repository = SnapshotShareRepository(context.cacheDir)
        try {
            source.writeText("[]")
            val copy = repository.prepare(source)
            try {
                var chooser: Intent? = null
                val launcher = SnapshotShareLauncher(context) { chooser = it }
                assertEquals(SnapshotShareResult.OPENED, launcher.launch(copy))
                val actual = requireNotNull(chooser)
                assertEquals(Intent.ACTION_CHOOSER, actual.action)
                assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, actual.flags)
                assertNotNull(actual.clipData)
                val send = actual.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                assertNotNull(send)
                assertEquals(Intent.ACTION_SEND, send?.action)
                assertEquals("application/json", send?.type)
                assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, send?.flags)
                assertEquals(actual.clipData?.getItemAt(0)?.uri, send?.clipData?.getItemAt(0)?.uri)
            } finally {
                repository.discard(copy)
            }
        } finally {
            source.delete()
        }
    }

    @Test fun receiverDisappearingDuringLaunchReturnsNoReceiverFeedback() {
        val source = File.createTempFile("share-test-", ".json", context.cacheDir)
        val repository = SnapshotShareRepository(context.cacheDir)
        try {
            source.writeText("[]")
            val copy = repository.prepare(source)
            try {
                val launcher = SnapshotShareLauncher(context) { throw ActivityNotFoundException("gone") }
                assertEquals(SnapshotShareResult.NO_RECEIVER, launcher.launch(copy))
            } finally {
                repository.discard(copy)
            }
        } finally {
            source.delete()
        }
    }
}
