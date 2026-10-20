package com.example.carlauncher.ui.lab.database

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.FileProvider
import java.io.File

enum class SnapshotShareResult(val message: String) {
    OPENED("已打开分享选择器，请在接收应用中确认读取结果"),
    NO_RECEIVER("没有可接收 JSON 的应用，请先安装或启用接收应用"),
    FAILED("无法打开 JSON 分享，请重试"),
}

/** Builds a fresh outgoing Intent; grants one URI for reading via the system chooser. */
class SnapshotShareLauncher(
    private val context: Context,
    private val startActivity: (Intent) -> Unit = context::startActivity,
) {
    fun createSendIntent(copy: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.snapshotfiles", copy)
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, "JSON 记录快照", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun launch(copy: File): SnapshotShareResult {
        val send = createSendIntent(copy)
        val receivers = context.packageManager.queryIntentActivities(send, PackageManager.MATCH_DEFAULT_ONLY)
        if (receivers.none {
                it.activityInfo.exported && it.activityInfo.enabled && it.activityInfo.applicationInfo.enabled
            }) {
            return SnapshotShareResult.NO_RECEIVER
        }
        return try {
            val chooser = Intent.createChooser(send, "分享 JSON 快照").apply {
                clipData = send.clipData
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(chooser)
            SnapshotShareResult.OPENED
        } catch (_: ActivityNotFoundException) {
            // The receiver/chooser may disappear between discovery and launch.
            SnapshotShareResult.NO_RECEIVER
        } catch (_: SecurityException) {
            SnapshotShareResult.FAILED
        }
    }
}
