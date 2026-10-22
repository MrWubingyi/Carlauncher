package com.zqautomotive.launcher.ui.lab.database

import android.content.ClipData
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.Process
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the existing page and the real Sharesheet; receiver lives in the test APK's own UID. */
@RunWith(AndroidJUnit4::class)
class SnapshotShareActivityTest {
    @get:Rule val compose = createAndroidComposeRule<DatabaseLabActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val receiverPackage get() = instrumentation.context.packageName
    private val receiverClass = "com.zqautomotive.launcher.shareprobe.JsonShareReceiverActivity"
    private val receiverComponent get() = "$receiverPackage/$receiverClass"
    private val user get() = Process.myUid() / 100000
    private lateinit var snapshot: File
    private var createdFixture = false

    @Before fun setup() {
        snapshot = File(compose.activity.filesDir, "records_snapshot.json")
        if (!snapshot.exists()) {
            snapshot.writeText("""[{"name":"分享验证","value":"21","note":"临时记录"}]""")
            createdFixture = true
        }
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasTestTag("shareSnapshotButton") and isEnabled())
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @After fun cleanup() {
        shell("am force-stop --user $user $receiverPackage")
        if (createdFixture) snapshot.delete()
    }

    @Test fun receiverReadsExistingJsonExactlyWithTemporaryReadOnlyGrant() {
        val expected = snapshot.readBytes()
        val started = System.currentTimeMillis()
        clickShare()
        // Automotive may auto-launch the sole receiver instead of displaying a selectable row.
        compose.waitUntil(10_000) {
            instrumentation.uiAutomation.rootInActiveWindow?.packageName == receiverPackage ||
                tryClickExternal("JSON Share Probe")
        }
        val receipt = awaitReceipt(started)
        assertTrue(receipt.toString(), receipt.getBoolean("success"))
        assertNotEquals(Process.myUid(), receipt.getInt("uid"))
        assertEquals("application/json", receipt.getString("mime"))
        assertTrue(receipt.getBoolean("clipMatches"))
        assertArrayEquals(expected, receipt.getString("content").toByteArray(Charsets.UTF_8))
        assertEquals(sha256(expected), receipt.getString("sha256"))
        assertEquals(expected.size, receipt.getInt("bytes"))
        assertTrue(receipt.getBoolean("writeDenied"))
        assertTrue(receipt.getBoolean("persistDenied"))
        val grantMask = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
        assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION, receipt.getInt("flags") and grantMask)
        assertArrayEquals(expected, snapshot.readBytes())
        val context = compose.activity.applicationContext
        val evidence = File(context.cacheDir, "json-share-test-evidence").apply { mkdirs() }
        File(evidence, "received.json").writeText(receipt.toString(2))

        val uri = Uri.parse(receipt.getString("uri"))
        assertEquals("content", uri.scheme)
        assertEquals("${context.packageName}.snapshotfiles", uri.authority)
        clickExternal("Close JSON receipt")
        compose.waitUntil(10_000) {
            context.checkUriPermission(uri, -1, receipt.getInt("uid"), Intent.FLAG_GRANT_READ_URI_PERMISSION) ==
                PackageManager.PERMISSION_DENIED
        }
        val retryStarted = System.currentTimeMillis()
        // Send the same URI again with no grant, after the receiver's task has finished.
        context.startActivity(Intent(Intent.ACTION_SEND).apply {
            component = ComponentName(receiverPackage, receiverClass)
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("Unprivileged retry", uri)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        val denied = awaitReceipt(retryStarted)
        assertFalse(denied.toString(), denied.getBoolean("success"))
        assertEquals("java.lang.SecurityException", denied.getString("error"))
        File(evidence, "after-task-finished.json").writeText(denied.toString(2))
    }

    @Test fun missingSnapshotShowsFeedbackWithoutCreatingCopy() {
        val backup = File(snapshot.parentFile, "snapshot-share-test-${UUID.randomUUID()}.backup")
        assertTrue(snapshot.renameTo(backup))
        val before = sharedFiles()
        try {
            clickShare()
            awaitStatus("快照文件不存在，请先保存记录快照")
            assertEquals(before, sharedFiles())
        } finally {
            assertTrue("Restore the user's snapshot", backup.renameTo(snapshot))
        }
    }

    @Test fun absentReceiverShowsFeedbackAndDiscardsUnsentCopy() {
        val before = sharedFiles()
        val initialState = compose.activity.packageManager.getComponentEnabledSetting(
            ComponentName(receiverPackage, receiverClass)
        )
        assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, initialState)
        try {
            val disabled = shell("run-as $receiverPackage --user $user pm disable --user $user $receiverComponent")
            assertTrue(disabled, disabled.contains("new state: disabled"))
            val send = Intent(Intent.ACTION_SEND).apply { type = "application/json" }
            compose.waitUntil(10_000) {
                compose.activity.packageManager.queryIntentActivities(send, PackageManager.MATCH_DEFAULT_ONLY).isEmpty()
            }
            clickShare()
            awaitStatus(SnapshotShareResult.NO_RECEIVER.message)
            assertEquals(before, sharedFiles())
        } finally {
            val restored = shell(
                "run-as $receiverPackage --user $user pm default-state --user $user $receiverComponent"
            )
            assertTrue(restored, restored.contains("new state: default"))
        }
    }

    private fun clickShare() {
        compose.onNodeWithTag("shareSnapshotButton").performScrollTo().performClick()
    }

    private fun awaitStatus(message: String) {
        compose.waitUntil(10_000) {
            compose.onAllNodes(
                hasTestTag("databaseStatus") and androidx.compose.ui.test.hasText(message)
            ).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("databaseStatus").assertTextEquals(message)
    }

    private fun clickExternal(text: String) {
        compose.waitUntil(10_000) { tryClickExternal(text) }
    }

    private fun tryClickExternal(text: String): Boolean {
        val nodes = instrumentation.uiAutomation.rootInActiveWindow
            ?.findAccessibilityNodeInfosByText(text).orEmpty()
        for (node in nodes) {
            var target: AccessibilityNodeInfo? = node
            while (target != null && !target.isClickable) target = target.parent
            if (target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) return true
        }
        return false
    }

    private fun awaitReceipt(started: Long): JSONObject {
        var result: JSONObject? = null
        compose.waitUntil(10_000) {
            val text = shell("run-as $receiverPackage --user $user cat files/json-share-receipt.json")
            val receipt = if (text.trimStart().startsWith("{")) JSONObject(text) else null
            if (receipt != null && receipt.optLong("receivedAt") >= started) result = receipt
            result != null
        }
        return requireNotNull(result)
    }

    private fun sharedFiles(): Set<String> =
        File(compose.activity.cacheDir, "json_shares").listFiles().orEmpty().map { it.name }.toSet()

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
            .bufferedReader().use { it.readText() }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
