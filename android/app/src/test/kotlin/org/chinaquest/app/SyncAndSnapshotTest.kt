package org.chinaquest.app

import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.chinaquest.core.*
import java.time.LocalDate
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class SyncAndSnapshotTest {
    @Test fun durableOutboxPreservesEvidenceAfterAcknowledgement() {
        val context=RuntimeEnvironment.getApplication();context.deleteDatabase("sync-test.db")
        val store=QuestStore(context,"sync-test.db");val day=LocalDate.of(2026,9,28)
        try {
            store.lesson("child-a",day);store.answer("child-a",day,0,null)
            assertEquals(1,store.pendingEventCount())
            val sync=SyncCoordinator(store) { SyncReply.Accepted(it.map { e->e.id }.toSet()) }
            assertEquals(1,sync.syncOnce(true,true).acknowledged)
            assertEquals(0,store.pendingEventCount())
            assertEquals(1,store.eventCount("child-a"))
            assertNotNull(store.mastery("child-a",store.cards().first().id))
            assertEquals(0,store.eventCount("child-b"))
        } finally { store.close();context.deleteDatabase("sync-test.db") }
    }
    @Test fun parentSnapshotCountsActualExposureAndExcludesCredentials() {
        val context=RuntimeEnvironment.getApplication();context.deleteDatabase("snapshot-test.db")
        val store=QuestStore(context,"snapshot-test.db");val day=LocalDate.of(2026,9,28)
        try {
            store.lesson("child-a",day);store.answer("child-a",day,0,null)
            val snapshot=store.parentSnapshot(day)
            // Real serialization from the Android repository, using synthetic test children.
            // The browser acceptance test imports this file across the app/web boundary.
            File("build/reports/parent-snapshot-acceptance.json").apply {
                parentFile!!.mkdirs();writeText(snapshot.toString(2),Charsets.UTF_8)
            }
            assertEquals(1,snapshot.getInt("schema_version"))
            val children=snapshot.getJSONArray("children")
            assertEquals(2,children.length())
            assertEquals(1,children.getJSONObject(0).getInt("introduced"))
            assertEquals(1,children.getJSONObject(0).getJSONArray("week").getJSONObject(6).getInt("new_count"))
            assertEquals(0,children.getJSONObject(1).getInt("introduced"))
            for(key in listOf("pin","salt","hash","token","hidden_budget")) assertFalse(snapshot.has(key))
        } finally { store.close();context.deleteDatabase("snapshot-test.db") }
    }
}
