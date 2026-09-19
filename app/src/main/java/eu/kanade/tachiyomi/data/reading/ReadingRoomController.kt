package eu.kanade.tachiyomi.data.reading

import eu.kanade.tachiyomi.data.watch.WatchRoomState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** A reading channel in the existing room, with no player reference and no second transport. */
class ReadingRoomController(
    private val scope: CoroutineScope,
    private val now: () -> Long,
    private val send: (ReadingEnvelope, String) -> Unit,
) {
    private val mutableState = MutableStateFlow(ReadingRoomState())
    val state = mutableState.asStateFlow()
    private var ticker: Job? = null
    private var local = ReadingPeer("Lettore")
    private var lastRoster = 0L
    private var operation = 0L
    private var revision = 0L
    private var presenceRevision = 0L
    private var lastRosterRevision = -1L
    private var evictedRevision = -1L
    private val presenceRevisions = mutableMapOf<String, Long>()
    private val rates = mutableMapOf<String, ArrayDeque<Long>>()
    private val accepted = mutableMapOf<String, Long>()
    private val acceptedErrors = mutableMapOf<String, String>()
    private val peers = linkedMapOf<String, Pair<ReadingPeer, Long>>()
    private var allowed = emptySet<String>()
    private var lastPresence = -5000L
    private var lastSend = -5000L
    private var lastBoardSend = -5000L
    private var rosterDirty = false
    private var sentPeer: ReadingPeer? = null
    private val pageReferences = linkedMapOf<String, ReadingPosition>()

    fun roomChanged(room: WatchRoomState) {
        val previous = state.value
        if (!room.active) {
            ticker?.cancel()
            ticker = null
            peers.clear()
            accepted.clear()
            acceptedErrors.clear()
            pageReferences.clear()
            operation = 0
            revision = 0
            presenceRevision = 0
            lastRosterRevision = -1
            evictedRevision = -1
            presenceRevisions.clear()
            rates.clear()
            sentPeer = null
            mutableState.value = ReadingRoomState()
            return
        }
        allowed = room.members.map { it.id }.toSet() + room.localMemberId
        local = local.copy(name = room.members.firstOrNull { it.id == room.localMemberId }?.name ?: local.name)
        if (previous.active && previous.localId != room.localMemberId) {
            roomChanged(WatchRoomState())
        }
        mutableState.value = state.value.copy(
            active = true,
            host = room.host,
            localId = room.localMemberId,
            invite = room.invite,
            relayCount = room.relayCount,
            supported = room.host || room.readingSupported || room.members.isEmpty(),
        )
        if (ticker == null) {
            lastRoster = now()
            lastPresence = -5000
            lastSend = -5000
            lastBoardSend = -5000
            rosterDirty = true
            ticker = scope.launch {
                while (state.value.active) {
                    tick()
                    delay(250)
                }
            }
        }
    }

    fun position(position: ReadingPosition?, reading: Boolean) {
        if (position != null && !position.valid()) return
        local = local.copy(position = position, reading = reading)
    }

    fun suspendReading() {
        local = local.copy(reading = false)
    }

    fun clearNotice() {
        mutableState.value = state.value.copy(notice = "")
    }

    fun draw(page: ReadingPosition, stroke: ReadingStroke): Boolean = enqueue(
        ReadingEnvelope(kind = ReadingKind.Ink, page = page, stroke = stroke, operation = operation + 1),
    )

    fun undo(page: ReadingPosition) {
        val last = state.value.strokes(page).lastOrNull { it.author == state.value.localId } ?: return
        enqueue(ReadingEnvelope(kind = ReadingKind.Ink, page = page, erase = last.id, operation = operation + 1))
    }

    fun clear(page: ReadingPosition) {
        enqueue(ReadingEnvelope(kind = ReadingKind.Ink, page = page, clear = true, operation = operation + 1))
    }

    private fun enqueue(value: ReadingEnvelope): Boolean {
        if (!state.value.active || !state.value.supported || !value.valid()) return false
        if (state.value.pending.size >= 16) {
            mutableState.value =
                state.value.copy(
                    notice = "Ci sono schizzi in attesa. Aspetta la riconnessione prima di aggiungerne altri.",
                )
            return false
        }
        operation++
        if (state.value.host) {
            ink(state.value.localId, value)
        } else {
            mutableState.value = state.value.copy(pending = state.value.pending + value)
            if (state.value.pending.size == 1) send(value, "")
        }
        return true
    }

    fun receive(sender: String, envelope: ReadingEnvelope, owner: Boolean) {
        if (!state.value.active || !envelope.valid()) return
        if (state.value.host) {
            if (sender !in allowed) return
            val rate = rates.getOrPut(sender) { ArrayDeque() }
            while (rate.isNotEmpty() && now() - rate.first() >= 1000) rate.removeFirst()
            if (rate.size >= 32) return
            rate.addLast(now())
            when (envelope.kind) {
                ReadingKind.Presence -> {
                    if (envelope.revision <= (presenceRevisions[sender] ?: -1)) return
                    presenceRevisions[sender] = envelope.revision
                    val previous = peers[sender]?.first
                    peers[sender] = envelope.peer!! to now()
                    mutableState.value = state.value.copy(members = state.value.members + (sender to envelope.peer))
                    rosterDirty = previous != envelope.peer || rosterDirty
                    if (previous?.position?.pageKey != envelope.peer.position?.pageKey) {
                        envelope.peer.position?.let { broadcastBoard(it, sender) }
                    }
                }
                ReadingKind.Ink -> ink(sender, envelope)
                else -> Unit
            }
        } else if (owner) {
            when (envelope.kind) {
                ReadingKind.Roster -> {
                    if (envelope.revision <= lastRosterRevision) return
                    lastRosterRevision = envelope.revision
                    lastRoster = now()
                    mutableState.value =
                        state.value.copy(
                            members = envelope.members,
                            connected = state.value.localId in envelope.members,
                        )
                }
                ReadingKind.Board -> {
                    val page = envelope.page!!
                    val old = state.value.boards[page.pageKey]
                    if (old == null && envelope.revision <= evictedRevision) return
                    if (old == null || envelope.revision >= old.revision) {
                        cache(page, ReadingBoard(envelope.revision, envelope.strokes))
                    }
                    val pending = state.value.pending
                    val head = pending.firstOrNull()
                    if (head != null &&
                        head.page?.pageKey == page.pageKey &&
                        envelope.acknowledgement >= head.operation
                    ) {
                        mutableState.value = state.value.copy(pending = pending.drop(1), notice = envelope.error)
                        state.value.pending.firstOrNull()?.let { send(it, "") }
                    }
                }
                else -> Unit
            }
        }
    }

    private fun tick() {
        val time = now()
        if (state.value.host) {
            peers.entries.removeAll { (id, value) -> id !in allowed || time - value.second > 15000 }
            if (time - lastSend >= 2000 || ((sentPeer != local || rosterDirty) && time - lastSend >= 350)) {
                broadcastRoster()
                lastSend = time
                sentPeer = local
                rosterDirty = false
            }
            if (time - lastBoardSend >= 2000) {
                (peers.values.mapNotNull { it.first.position } + listOfNotNull(local.position))
                    .distinctBy {
                        it.pageKey
                    }.filter { it.pageKey in state.value.boards }.forEach { broadcastBoard(it) }
                lastBoardSend = time
            }
            mutableState.value = state.value.copy(connected = state.value.relayCount > 0)
        } else {
            if (time - lastPresence >= 3000 || (sentPeer != local && time - lastPresence >= 350)) {
                send(ReadingEnvelope(kind = ReadingKind.Presence, peer = local, revision = ++presenceRevision), "")
                sentPeer = local
                lastPresence = time
            }
            if (time - lastSend >= 1500) {
                state.value.pending.firstOrNull()?.let { send(it, "") }
                lastSend = time
            }
            if (time - lastRoster > 12000) mutableState.value = state.value.copy(connected = false)
        }
    }

    private fun broadcastRoster() {
        val members = linkedMapOf(state.value.localId to local)
        peers.filterKeys { it in allowed }.forEach { (id, peer) -> members[id] = peer.first }
        mutableState.value = state.value.copy(members = members)
        send(ReadingEnvelope(kind = ReadingKind.Roster, members = members, revision = ++revision), "")
    }

    private fun ink(sender: String, value: ReadingEnvelope) {
        val page = value.page!!
        val last = accepted[sender] ?: 0
        if (value.operation <= last) {
            broadcastBoard(
                page,
                sender,
                last,
                acceptedErrors[sender].orEmpty().takeIf {
                    last == value.operation
                }.orEmpty(),
            )
            return
        }
        // A single outstanding operation per member avoids silently dropping holes after packet loss.
        if (value.operation != last + 1) return
        val strokes = state.value.boards[page.pageKey]?.strokes.orEmpty().toMutableList()
        var error = ""
        when {
            value.stroke != null -> {
                if (value.stroke.author != sender) return
                if (strokes.size >= 12) {
                    error = "Questa pagina ha già 12 schizzi. Cancella qualcosa prima di continuare."
                } else if (strokes.none { it.id == value.stroke.id }) {
                    strokes.add(value.stroke)
                }
            }
            value.erase.isNotEmpty() -> strokes.removeAll {
                it.id == value.erase &&
                    (it.author == sender || sender == state.value.localId)
            }
            value.clear -> strokes.removeAll { it.author == sender || sender == state.value.localId }
        }
        accepted[sender] = value.operation
        acceptedErrors[sender] = error
        revision++
        cache(page, ReadingBoard(revision, strokes))
        if (sender == state.value.localId) mutableState.value = state.value.copy(notice = error)
        broadcastBoard(page)
        broadcastBoard(page, sender, value.operation, error)
    }

    private fun cache(page: ReadingPosition, board: ReadingBoard) {
        pageReferences.remove(page.pageKey)
        pageReferences[page.pageKey] = page
        val boards = state.value.boards.toMutableMap()
        boards[page.pageKey] = board
        while (pageReferences.size > 32) {
            val key = pageReferences.keys.first()
            pageReferences.remove(key)
            if (!state.value.host) evictedRevision = maxOf(evictedRevision, boards[key]?.revision ?: 0)
            boards.remove(key)
        }
        mutableState.value = state.value.copy(boards = boards)
    }

    private fun broadcastBoard(page: ReadingPosition, target: String = "", ack: Long = 0, error: String = "") {
        val board = state.value.boards[page.pageKey] ?: ReadingBoard(revision)
        send(
            ReadingEnvelope(
                kind = ReadingKind.Board,
                page = page,
                revision = ++revision,
                strokes = board.strokes,
                acknowledgement = ack,
                error = error,
            ),
            target,
        )
    }
}
