package com.zqautomotive.launcher.data.local

import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/** Copies the existing snapshot byte-for-byte; never exposes or rewrites the source file. */
class SnapshotShareRepository(cacheDirectory: File) {
    private val shareDirectory = File(cacheDirectory, "json_shares")

    fun prepare(snapshot: File): File {
        if (!snapshot.isFile) throw FileNotFoundException("Snapshot does not exist")
        if (!shareDirectory.isDirectory && !shareDirectory.mkdirs()) {
            throw IOException("Cannot create snapshot sharing directory")
        }
        val copy = File.createTempFile("records_snapshot-", ".json", shareDirectory)
        try {
            snapshot.inputStream().use { input ->
                copy.outputStream().use { output -> input.copyTo(output) }
            }
            return copy
        } catch (exception: Exception) {
            copy.delete()
            throw exception
        }
    }

    /** Only failed/unlaunched copies are removed here; recipients may still be reading launched ones. */
    fun discard(copy: File) {
        require(copy.parentFile?.canonicalFile == shareDirectory.canonicalFile) {
            "Only snapshot sharing copies can be discarded"
        }
        copy.delete()
    }
}
