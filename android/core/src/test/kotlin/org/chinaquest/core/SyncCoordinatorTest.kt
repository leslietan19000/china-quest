package org.chinaquest.core

import org.junit.Assert.*
import org.junit.Test

class SyncCoordinatorTest {
    private class Outbox:SyncOutbox {
        val rows=mutableListOf(PendingSyncEvent("event-a","child-a","{}"),PendingSyncEvent("event-b","child-b","{}"))
        val ack=mutableSetOf<String>()
        override fun pending(limit:Int)=rows.filterNot { it.id in ack }.take(limit)
        override fun recordAttempt(ids:Set<String>) { rows.replaceAll { if(it.id in ids) it.copy(attempts=it.attempts+1) else it } }
        override fun acknowledge(ids:Set<String>) { ack.addAll(ids) }
    }
    @Test fun offlineAndUnpairedNeverContactService() {
        val box=Outbox();var calls=0
        val sync=SyncCoordinator(box) { calls++;SyncReply.Unavailable }
        assertEquals(SyncStatus.OFFLINE,sync.syncOnce(false,true).status)
        assertEquals(SyncStatus.PARENT_ACTION_REQUIRED,sync.syncOnce(true,false).status)
        assertEquals(0,calls);assertTrue(box.rows.all { it.attempts==0 })
    }
    @Test fun successfulAcknowledgementIsIdempotentAndRetainsHistoryRows() {
        val box=Outbox();val sync=SyncCoordinator(box) { SyncReply.Accepted(it.map { e->e.id }.toSet()) }
        assertEquals(2,sync.syncOnce(true,true).acknowledged)
        assertEquals(SyncStatus.EMPTY,sync.syncOnce(true,true).status)
        assertEquals(2,box.rows.size)
    }
    @Test fun twoFailuresStopFurtherCallsAndPreserveOutbox() {
        val box=Outbox();var calls=0;val sync=SyncCoordinator(box) { calls++;error("offline") }
        repeat(2) { assertEquals(SyncStatus.RETRY_LATER,sync.syncOnce(true,true).status) }
        assertEquals(SyncStatus.RETRY_LIMIT,sync.syncOnce(true,true).status)
        assertEquals(2,calls);assertTrue(box.ack.isEmpty())
    }
    @Test fun unauthorizedResponseNeverDropsEvents() {
        val box=Outbox();val sync=SyncCoordinator(box) { SyncReply.AuthenticationRequired }
        assertEquals(SyncStatus.PARENT_ACTION_REQUIRED,sync.syncOnce(true,true).status)
        assertTrue(box.ack.isEmpty())
    }
    @Test fun acknowledgementsOutsideTheSentBatchAreRejected() {
        val box=Outbox();val sync=SyncCoordinator(box) { SyncReply.Accepted(setOf("event-other-family")) }
        assertEquals(SyncStatus.REJECTED,sync.syncOnce(true,true).status)
        assertTrue(box.ack.isEmpty())
    }
    @Test fun partialAcknowledgementKeepsOnlyTheUnacknowledgedItemPending() {
        val box=Outbox();val sync=SyncCoordinator(box) { SyncReply.Accepted(setOf("event-a")) }
        assertEquals(SyncStatus.PARTIAL,sync.syncOnce(true,true).status)
        assertEquals(listOf("event-b"),box.pending(50).map { it.id })
    }
}
