package com.example.carlauncher.data.local

import java.io.File
import java.io.FileNotFoundException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SnapshotShareRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun copiesExactBytesAndNeverOverwritesEarlierShare() {
        val source = temporary.newFile("records_snapshot.json")
        val original = """[{"name":"温度","value":"21","note":"\n保留格式"}]""".toByteArray()
        source.writeBytes(original)
        val cache = temporary.newFolder("cache")
        val repository = SnapshotShareRepository(cache)
        val first = repository.prepare(source)
        assertArrayEquals(original, first.readBytes())
        assertArrayEquals(original, source.readBytes())
        source.writeText("[]")
        val second = repository.prepare(source)
        assertNotEquals(first, second)
        assertArrayEquals(original, first.readBytes())
        assertEquals("[]", second.readText())
        assertEquals(File(cache, "json_shares").canonicalFile, requireNotNull(first.parentFile).canonicalFile)
        repository.discard(second)
        assertTrue(first.isFile)
        assertTrue(source.isFile)
    }

    @Test fun missingSnapshotDoesNotCreateSharingFiles() {
        val cache = temporary.newFolder("cache")
        val repository = SnapshotShareRepository(cache)
        assertThrows(FileNotFoundException::class.java) {
            repository.prepare(File(temporary.root, "missing.json"))
        }
        assertTrue(cache.listFiles().isNullOrEmpty())
    }

    @Test fun discardCannotDeleteOutsideDedicatedDirectory() {
        val source = temporary.newFile("records_snapshot.json")
        val repository = SnapshotShareRepository(temporary.newFolder("cache"))
        assertThrows(IllegalArgumentException::class.java) { repository.discard(source) }
        assertTrue(source.isFile)
    }
}
