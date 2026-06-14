package dev.openandroiduse.companion.agent

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises SessionStore against the real Android filesystem (filesDir) on a
 * device/emulator: the JVM unit tests cover the pure codec, this covers the file
 * I/O — save, list ordering, load, rename, archive, and delete — so a regression
 * in persistence is caught by the emulator-smoke CI.
 */
@RunWith(AndroidJUnit4::class)
class SessionStoreInstrumentedTest {

    private lateinit var store: SessionStore

    private fun payload(id: String, title: String, updatedAt: Long) = SessionPayload(
        id = id,
        title = title,
        createdAt = 1L,
        updatedAt = updatedAt,
        archived = false,
        transcript = listOf(
            StoredMessage(AgentController.KIND_USER, "Do $title"),
            StoredMessage(AgentController.KIND_ASSISTANT, "Done $title"),
        ),
    )

    @Before
    fun setUp() {
        store = SessionStore(ApplicationProvider.getApplicationContext())
        store.deleteAll()
    }

    @Test
    fun savesListsNewestFirstAndLoadsBack() {
        store.save(payload("a", "Alpha", updatedAt = 100L))
        store.save(payload("b", "Beta", updatedAt = 200L))

        val list = store.list()
        assertEquals(2, list.size)
        assertEquals("b", list[0].id) // newest updatedAt first
        assertEquals("a", list[1].id)

        val loaded = store.load("a")
        assertEquals("Alpha", loaded?.title)
        assertEquals(2, loaded?.transcript?.size)
    }

    @Test
    fun renameAndArchiveUpdateMetadata() {
        store.save(payload("a", "Alpha", updatedAt = 100L))
        store.rename("a", "Renamed")
        store.setArchived("a", true)

        val meta = store.list().single { it.id == "a" }
        assertEquals("Renamed", meta.title)
        assertTrue(meta.archived)
    }

    @Test
    fun deleteAndDeleteAllRemoveSessions() {
        store.save(payload("a", "Alpha", updatedAt = 100L))
        store.save(payload("b", "Beta", updatedAt = 200L))

        store.delete("a")
        assertNull(store.load("a"))
        assertEquals(1, store.list().size)

        store.deleteAll()
        assertEquals(0, store.list().size)
    }
}
