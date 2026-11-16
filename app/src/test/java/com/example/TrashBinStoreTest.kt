package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.TrashBinStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TrashBinStoreTest {

    @Test
    fun `addToTrash prevents duplicates, restores cleanly, and prunes missing files`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = TrashBinStore(context)
        val uri1 = Uri.parse("content://media/external/images/media/101")
        val uri2 = Uri.parse("content://media/external/video/media/202")

        // Clear any pre-existing state
        store.removeFromTrash(store.getTrashedUris())

        val firstAdd = store.addToTrash(listOf(uri1, uri1))
        assertEquals(listOf(uri1), firstAdd)
        assertEquals(setOf(uri1), store.getTrashedUris())

        // Re-adding uri1 is ignored; uri2 is added
        val secondAdd = store.addToTrash(listOf(uri1, uri2))
        assertEquals(listOf(uri2), secondAdd)
        assertEquals(setOf(uri1, uri2), store.getTrashedUris())

        // Prune when uri2 no longer exists on device
        store.pruneMissingUris(setOf(uri1))
        assertTrue(uri1 in store.getTrashedUris())
        assertFalse(uri2 in store.getTrashedUris())

        // Restore removes uri1 from trash
        val removed = store.removeFromTrash(listOf(uri1))
        assertEquals(listOf(uri1), removed)
        assertTrue(store.getTrashedUris().isEmpty())
    }
}
