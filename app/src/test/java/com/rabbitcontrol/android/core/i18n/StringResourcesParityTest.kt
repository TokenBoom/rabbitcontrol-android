package com.rabbitcontrol.android.core.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class StringResourcesParityTest {

    @Test
    fun allThreeLocalesHaveIdenticalKeysNonBlankValuesAndMatchingPlaceholders() {
        val resDir = resolveResDir()
        val baseStrings = parseStrings(File(resDir, "values/strings.xml"))
        val scStrings = parseStrings(File(resDir, "values-zh-rCN/strings.xml"))
        val tcStrings = parseStrings(File(resDir, "values-b+zh+Hant/strings.xml"))

        assertTrue("Expected non-empty base strings", baseStrings.isNotEmpty())
        assertEquals(
            "Simplified Chinese keys differ from English base",
            baseStrings.keys,
            scStrings.keys,
        )
        assertEquals(
            "Traditional Chinese keys differ from English base",
            baseStrings.keys,
            tcStrings.keys,
        )

        val placeholderRegex = Regex("""%\d+\$[sd]|%[sd]""")
        for (key in baseStrings.keys) {
            val enVal = baseStrings.getValue(key)
            val scVal = scStrings.getValue(key)
            val tcVal = tcStrings.getValue(key)

            assertTrue("Blank English string for '$key'", enVal.isNotBlank())
            assertTrue("Blank Simplified Chinese string for '$key'", scVal.isNotBlank())
            assertTrue("Blank Traditional Chinese string for '$key'", tcVal.isNotBlank())

            val enArgs = placeholderRegex.findAll(enVal).map { it.value }.sorted().toList()
            val scArgs = placeholderRegex.findAll(scVal).map { it.value }.sorted().toList()
            val tcArgs = placeholderRegex.findAll(tcVal).map { it.value }.sorted().toList()

            assertEquals("Simplified Chinese placeholder mismatch for '$key'", enArgs, scArgs)
            assertEquals("Traditional Chinese placeholder mismatch for '$key'", enArgs, tcArgs)
        }
    }

    private fun resolveResDir(): File {
        val candidates = listOf(
            File("src/main/res"),
            File("app/src/main/res"),
        )
        return candidates.firstOrNull { File(it, "values/strings.xml").isFile }
            ?: error("Could not locate src/main/res from working directory ${File(".").absolutePath}")
    }

    private fun parseStrings(file: File): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(file)
        val nodes = doc.getElementsByTagName("string")
        val map = LinkedHashMap<String, String>()
        for (i in 0 until nodes.length) {
            val elem = nodes.item(i) as Element
            val name = elem.getAttribute("name")
            assertTrue("Duplicate string key '$name' in ${file.path}", !map.containsKey(name))
            map[name] = elem.textContent.orEmpty()
        }
        return map
    }
}
