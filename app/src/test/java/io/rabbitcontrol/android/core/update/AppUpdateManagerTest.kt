package io.rabbitcontrol.android.core.update

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateManagerTest {

    @Test
    fun compareSemVer_handlesPrefixAndMultiDigitParts() {
        assertTrue(compareSemVer("v0.2.1", "0.2.0") > 0)
        assertTrue(compareSemVer("v0.10.0", "0.2.0") > 0)
        assertTrue(compareSemVer("1.0.0", "v0.9.9") > 0)
        assertEquals(0, compareSemVer("v0.2.0", "0.2.0"))
        assertTrue(compareSemVer("0.1.9", "0.2.0") < 0)
        assertEquals(0, compareSemVer("v0.2.0-beta.1", "0.2.0"))
    }

    @Test
    fun pickBestApkAsset_prefersArm64OverGenericApk() {
        val assets = JSONArray()
            .put(
                JSONObject()
                    .put("name", "rabbitcontrol-universal.apk")
                    .put("browser_download_url", "https://example.com/universal.apk")
                    .put("size", 20_000_000L),
            )
            .put(
                JSONObject()
                    .put("name", "rabbitcontrol-arm64-v8a-release.apk")
                    .put("browser_download_url", "https://example.com/arm64.apk")
                    .put("size", 15_000_000L),
            )
            .put(
                JSONObject()
                    .put("name", "checksums.txt")
                    .put("browser_download_url", "https://example.com/checksums.txt")
                    .put("size", 512L),
            )

        val picked = pickBestApkAsset(assets)
        assertEquals("https://example.com/arm64.apk", picked?.first)
        assertEquals("rabbitcontrol-arm64-v8a-release.apk", picked?.second)
        assertEquals(15_000_000L, picked?.third)
    }

    @Test
    fun pickBestApkAsset_fallsBackToFirstApkOrNull() {
        val fallbackAssets = JSONArray()
            .put(JSONObject().put("name", "notes.md").put("browser_download_url", "https://example.com/notes.md"))
            .put(JSONObject().put("name", "app-release.apk").put("browser_download_url", "https://example.com/app.apk").put("size", 1234L))

        val picked = pickBestApkAsset(fallbackAssets)
        assertEquals("https://example.com/app.apk", picked?.first)

        val noApk = JSONArray().put(JSONObject().put("name", "source.zip").put("browser_download_url", "https://example.com/src.zip"))
        assertNull(pickBestApkAsset(noApk))
    }

    @Test
    fun cleanReleaseNotes_formatsHeadersBulletsAndWhitespace() {
        val raw = """
            ## 更新内容
            - **新增** 支持 GitHub 自动检查更新
            * 修复 `竖屏` 适配问题


            ### 其他说明
        """.trimIndent()
        val expected = """
            更新内容
            • 新增 支持 GitHub 自动检查更新
            • 修复 竖屏 适配问题

            其他说明
        """.trimIndent()
        assertEquals(expected, cleanReleaseNotes(raw))
        assertEquals("暂无更新说明", cleanReleaseNotes("   \n  "))
    }

    @Test
    fun extractApkFromExpandedAssetsHtml_prefersArm64Link() {
        val html = """
            <ul>
              <li><a href="/TokenBoom/rabbitcontrol-android/releases/download/v0.2.1/app-universal.apk">universal</a></li>
              <li><a href="/TokenBoom/rabbitcontrol-android/releases/download/v0.2.1/rabbitcontrol-arm64-v8a.apk">arm64</a></li>
            </ul>
        """.trimIndent()
        val picked = extractApkFromExpandedAssetsHtml(html)
        assertEquals(
            "https://github.com/TokenBoom/rabbitcontrol-android/releases/download/v0.2.1/rabbitcontrol-arm64-v8a.apk",
            picked?.first,
        )
        assertEquals("rabbitcontrol-arm64-v8a.apk", picked?.second)
    }

    @Test
    fun extractReleaseNotesFromTagHtml_parsesMarkdownBody() {
        val html = """<div data-test-selector="body-content" class="markdown-body my-3"><p>新版本亮点：</p><ul><li>支持自动更新</li><li>优化竖屏体验</li></ul></div>"""
        val notes = extractReleaseNotesFromTagHtml(html)
        assertTrue(notes.contains("新版本亮点："))
        assertTrue(notes.contains("• 支持自动更新"))
        assertTrue(notes.contains("• 优化竖屏体验"))
    }
}
