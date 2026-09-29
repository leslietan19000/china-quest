package org.chinaquest.core

/** No credentials or Android/network dependency lives in the learning domain. */
data class PendingSyncEvent(val id:String,val childId:String,val envelope:String,val attempts:Int=0)
interface SyncOutbox {
    fun pending(limit:Int):List<PendingSyncEvent>
    fun recordAttempt(ids:Set<String>)
    fun acknowledge(ids:Set<String>)
}
sealed interface SyncReply {
    data class Accepted(val ids:Set<String>):SyncReply
    data object AuthenticationRequired:SyncReply
    data object Unavailable:SyncReply
    data class Rejected(val reason:String):SyncReply
}
fun interface SyncTransport { fun send(events:List<PendingSyncEvent>):SyncReply }
enum class SyncStatus { OFFLINE, EMPTY, SYNCED, PARTIAL, PARENT_ACTION_REQUIRED, RETRY_LATER, REJECTED, RETRY_LIMIT }
data class SyncResult(val status:SyncStatus,val acknowledged:Int=0)

/** One bounded batch per call. Never loops, retries in the background or resets learning data. */
class SyncCoordinator(private val outbox:SyncOutbox,private val transport:SyncTransport) {
    fun syncOnce(online:Boolean,parentPaired:Boolean):SyncResult {
        if(!online) return SyncResult(SyncStatus.OFFLINE)
        if(!parentPaired) return SyncResult(SyncStatus.PARENT_ACTION_REQUIRED)
        val pending=outbox.pending(50)
        if(pending.isEmpty()) return SyncResult(SyncStatus.EMPTY)
        val eligible=pending.filter { it.attempts<2 }
        if(eligible.isEmpty()) return SyncResult(SyncStatus.RETRY_LIMIT)
        val ids=eligible.map { it.id }.toSet()
        require(ids.size==eligible.size) { "Outbox IDs must be unique" }
        outbox.recordAttempt(ids)
        val response=try { transport.send(eligible) } catch(_:Exception) { SyncReply.Unavailable }
        return when(response) {
            is SyncReply.Accepted -> {
                // A faulty or hostile response must never acknowledge an unsent event.
                if(!ids.containsAll(response.ids)) SyncResult(SyncStatus.REJECTED)
                else {
                    outbox.acknowledge(response.ids)
                    SyncResult(if(response.ids==ids) SyncStatus.SYNCED else SyncStatus.PARTIAL,response.ids.size)
                }
            }
            SyncReply.AuthenticationRequired -> SyncResult(SyncStatus.PARENT_ACTION_REQUIRED)
            SyncReply.Unavailable -> SyncResult(SyncStatus.RETRY_LATER)
            is SyncReply.Rejected -> SyncResult(SyncStatus.REJECTED)
        }
    }
}
