package com.rabbitcontrol.android.ui.components

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.TextToolbarStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RabbitTextToolbarTest {

    @Test
    fun initialStateIsHiddenAndEmpty() {
        val toolbar = RabbitTextToolbar()
        assertEquals(TextToolbarStatus.Hidden, toolbar.status)
        assertNull(toolbar.menuState)
    }

    @Test
    fun showMenuTransitionsStatusAndMenuStateAndHideResets() {
        val toolbar = RabbitTextToolbar()
        val rect = Rect(10f, 20f, 110f, 60f)

        toolbar.showMenu(
            rect = rect,
            onCopyRequested = {},
            onPasteRequested = {},
            onCutRequested = null,
            onSelectAllRequested = {},
        )
        assertEquals(TextToolbarStatus.Shown, toolbar.status)
        val firstState = toolbar.menuState
        assertNotNull(firstState)
        assertEquals(rect, firstState?.rect)
        assertNotNull(firstState?.onCopy)
        assertNotNull(firstState?.onPaste)
        assertNull(firstState?.onCut)
        assertNotNull(firstState?.onSelectAll)
        assertNull(firstState?.onAutofill)

        toolbar.showMenu(
            rect = rect,
            onCopyRequested = null,
            onPasteRequested = {},
            onCutRequested = null,
            onSelectAllRequested = null,
            onAutofillRequested = {},
        )
        assertEquals(TextToolbarStatus.Shown, toolbar.status)
        assertNotNull(toolbar.menuState?.onAutofill)

        toolbar.hide()
        assertEquals(TextToolbarStatus.Hidden, toolbar.status)
        assertNull(toolbar.menuState)
    }

    @Test
    fun showMenuWithAllNullCallbacksRemainsHidden() {
        val toolbar = RabbitTextToolbar()
        toolbar.showMenu(
            rect = Rect(0f, 0f, 10f, 10f),
            onCopyRequested = {},
            onPasteRequested = null,
            onCutRequested = null,
            onSelectAllRequested = null,
        )
        assertEquals(TextToolbarStatus.Shown, toolbar.status)

        toolbar.showMenu(
            rect = Rect(0f, 0f, 10f, 10f),
            onCopyRequested = null,
            onPasteRequested = null,
            onCutRequested = null,
            onSelectAllRequested = null,
            onAutofillRequested = null,
        )
        assertEquals(TextToolbarStatus.Hidden, toolbar.status)
        assertNull(toolbar.menuState)
    }

    @Test
    fun readingStatusDoesNotSubscribeToComposeSnapshot() {
        val toolbar = RabbitTextToolbar()
        var snapshotReads = 0
        var observedStatus: TextToolbarStatus? = null

        Snapshot.observe(
            readObserver = { snapshotReads++ },
        ) {
            observedStatus = toolbar.status
        }
        assertEquals(TextToolbarStatus.Hidden, observedStatus)
        assertEquals(0, snapshotReads)

        toolbar.showMenu(
            rect = Rect(0f, 0f, 50f, 50f),
            onCopyRequested = null,
            onPasteRequested = {},
            onCutRequested = null,
            onSelectAllRequested = null,
        )
        Snapshot.observe(
            readObserver = { snapshotReads++ },
        ) {
            observedStatus = toolbar.status
        }
        assertEquals(TextToolbarStatus.Shown, observedStatus)
        assertEquals(0, snapshotReads)
    }
}
