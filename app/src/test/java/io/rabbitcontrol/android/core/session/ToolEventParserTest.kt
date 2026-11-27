package io.rabbitcontrol.android.core.session

import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolEventParserTest {

    @Test
    fun parsesRcToolsFileEvents() {
        val remoteDirJson = JSONObject(
            """
            {
              "type": "remoteDir",
              "path": "/Users/test/Desktop",
              "entries": [
                { "name": "src", "path": "/Users/test/Desktop/src", "isDir": true, "size": 0, "modifiedSec": 1700000100, "kind": "dir" },
                { "name": "demo.txt", "path": "/Users/test/Desktop/demo.txt", "isDir": false, "size": 2048, "modifiedSec": 1700000200, "kind": "file" }
              ],
              "error": null
            }
            """.trimIndent(),
        )
        val dirEv = parseFileToolEvent(remoteDirJson) as FileToolEvent.RemoteDir
        assertEquals("/Users/test/Desktop", dirEv.path)
        assertEquals(2, dirEv.entries.size)
        assertTrue(dirEv.entries[0].isDir)
        assertEquals(2048L, dirEv.entries[1].size)
        assertEquals(1700000200L, dirEv.entries[1].modifiedUnixSec)

        val mkdirJson = JSONObject("""{"type":"mkdirResult","path":"/Users/test/newdir","ok":true,"error":null}""")
        val mkdirEv = parseFileToolEvent(mkdirJson) as FileToolEvent.MkdirResult
        assertTrue(mkdirEv.ok)
        assertEquals("/Users/test/newdir", mkdirEv.path)

        val deleteOkJson = JSONObject("""{"type":"deleteResult","path":"/Users/test/old.txt","ok":true,"error":null}""")
        val deleteOk = parseFileToolEvent(deleteOkJson) as FileToolEvent.DeleteResult
        assertTrue(deleteOk.ok)
        assertEquals("/Users/test/old.txt", deleteOk.path)
        assertNull(deleteOk.error)

        val deleteErrJson = JSONObject("""{"type":"deleteResult","path":"/Users/test/locked.txt","ok":false,"error":"permission denied"}""")
        val deleteErr = parseFileToolEvent(deleteErrJson) as FileToolEvent.DeleteResult
        assertFalse(deleteErr.ok)
        assertEquals("permission denied", deleteErr.error)

        val renameOkJson = JSONObject("""{"type":"renameResult","path":"/Users/test/a.txt","newPath":"/Users/test/b.txt","ok":true,"error":null}""")
        val renameOk = parseFileToolEvent(renameOkJson) as FileToolEvent.RenameResult
        assertTrue(renameOk.ok)
        assertEquals("/Users/test/a.txt", renameOk.path)
        assertEquals("/Users/test/b.txt", renameOk.newPath)
        assertNull(renameOk.error)

        val renameErrJson = JSONObject("""{"type":"renameResult","path":"/Users/test/a.txt","newPath":null,"ok":false,"error":"already exists"}""")
        val renameErr = parseFileToolEvent(renameErrJson) as FileToolEvent.RenameResult
        assertFalse(renameErr.ok)
        assertNull(renameErr.newPath)
        assertEquals("already exists", renameErr.error)

        val uploadJson = JSONObject(
            """
            {
              "type": "transferUpdated",
              "id": 7,
              "name": "photo.png",
              "direction": "upload",
              "status": "active",
              "totalBytes": 4096,
              "doneBytes": 1024,
              "fromPath": "/data/cache/photo.png",
              "toPath": "/Users/test/Desktop/photo.png",
              "error": null
            }
            """.trimIndent(),
        )
        val uploadEv = parseFileToolEvent(uploadJson) as FileToolEvent.TransferUpdated
        assertEquals(7L, uploadEv.item.id)
        assertEquals("upload", uploadEv.item.direction)
        assertEquals("active", uploadEv.item.status)
        assertEquals("photo.png", uploadEv.item.fileName)
        assertEquals("/data/cache/photo.png", uploadEv.item.localPath)
        assertEquals("/Users/test/Desktop/photo.png", uploadEv.item.remotePath)
        assertEquals(1024L, uploadEv.item.transferredBytes)

        val downloadJson = JSONObject(
            """
            {
              "type": "transferUpdated",
              "id": 8,
              "name": "report.pdf",
              "direction": "download",
              "status": "done",
              "totalBytes": 8192,
              "doneBytes": 8192,
              "fromPath": "/Users/test/report.pdf",
              "toPath": "/storage/app/RabbitControl/report.pdf",
              "error": null
            }
            """.trimIndent(),
        )
        val downloadEv = parseFileToolEvent(downloadJson) as FileToolEvent.TransferUpdated
        assertEquals("done", downloadEv.item.status)
        assertEquals("/storage/app/RabbitControl/report.pdf", downloadEv.item.localPath)
        assertEquals("/Users/test/report.pdf", downloadEv.item.remotePath)
    }

    @Test
    fun parsesRcToolsTerminalEvents() {
        val listJson = JSONObject(
            """
            {
              "type": "sessionList",
              "sessions": [
                { "termId": 1, "name": "session1", "title": "zsh", "cwd": "/Users/test", "createdAtUnixSec": 1700000000, "cols": 80, "rows": 24, "attached": true }
              ]
            }
            """.trimIndent(),
        )
        val listEv = parseTerminalToolEvent(listJson) as TerminalToolEvent.SessionList
        assertEquals(1, listEv.sessions.size)
        assertEquals(1, listEv.sessions[0].termId)
        assertEquals("session1", listEv.sessions[0].name)

        val openedJson = JSONObject(
            """
            {
              "type": "opened",
              "termId": 2,
              "name": "session2",
              "createdAtUnixSec": 1700000050,
              "title": "zsh",
              "cwd": "/Users/test",
              "attached": true,
              "ok": true,
              "error": null
            }
            """.trimIndent(),
        )
        val openedEv = parseTerminalToolEvent(openedJson) as TerminalToolEvent.Opened
        assertTrue(openedEv.ok)
        assertEquals(2, openedEv.termId)

        val attachedJson = JSONObject(
            """
            {
              "type": "attached",
              "termId": 2,
              "session": { "termId": 2, "name": "session2", "title": "zsh", "cwd": "/Users/test", "createdAtUnixSec": 1700000050, "cols": 80, "rows": 24, "attached": true },
              "ok": true,
              "error": null
            }
            """.trimIndent(),
        )
        val attachedEv = parseTerminalToolEvent(attachedJson) as TerminalToolEvent.Attached
        assertTrue(attachedEv.ok)
        assertNotNull(attachedEv.session)
        assertEquals("session2", attachedEv.session?.name)

        val outputJson = JSONObject(
            """
            {
              "type": "output",
              "termId": 2,
              "textUtf8Lossy": "hi",
              "bytes": [104, 105, 13, 10]
            }
            """.trimIndent(),
        )
        val outputEv = parseTerminalToolEvent(outputJson) as TerminalToolEvent.Output
        assertArrayEquals(byteArrayOf(104, 105, 13, 10), outputEv.bytes)

        val outputFallbackJson = JSONObject(
            """
            {
              "type": "output",
              "termId": 2,
              "textUtf8Lossy": "ok",
              "bytes": []
            }
            """.trimIndent(),
        )
        val outputFallback = parseTerminalToolEvent(outputFallbackJson) as TerminalToolEvent.Output
        assertArrayEquals("ok".toByteArray(Charsets.UTF_8), outputFallback.bytes)

        val exitedJson = JSONObject("""{"type":"exited","termId":2,"code":130}""")
        val exitedEv = parseTerminalToolEvent(exitedJson) as TerminalToolEvent.Exited
        assertEquals(130, exitedEv.exitCode)
    }
}
