package eu.kanade.tachiyomi.data.community

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.serialization.encodeToString

/** Private data and pending signed envelopes are encrypted at rest with a non-exportable wrapping key. */
internal class CommunityStore(context: Context, private val vault: IdentityVault) : SQLiteOpenHelper(
    context,
    "community-v1.db",
    null,
    1,
) {
    var generation: Long = 0
        private set
    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE records (bucket TEXT NOT NULL, id TEXT NOT NULL, value BLOB NOT NULL, at INTEGER NOT NULL, PRIMARY KEY(bucket,id))",
        )
        db.execSQL(
            "CREATE TABLE outbox (id TEXT PRIMARY KEY NOT NULL, event BLOB NOT NULL, address TEXT NOT NULL, created INTEGER NOT NULL, expires INTEGER NOT NULL DEFAULT 0, attempted INTEGER NOT NULL DEFAULT 0)",
        )
        db.execSQL("CREATE TABLE receipts (event TEXT NOT NULL, relay TEXT NOT NULL, PRIMARY KEY(event,relay))")
        db.execSQL("CREATE TABLE received (id TEXT PRIMARY KEY NOT NULL, at INTEGER NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    fun <T> transaction(block: () -> T): T {
        val db = writableDatabase
        db.beginTransaction()
        return try {
            block().also { db.setTransactionSuccessful() }
        } finally {
            db.endTransaction()
        }
    }
    fun put(bucket: String, id: String, json: String, at: Long = System.currentTimeMillis()) {
        val inserted = writableDatabase.insertWithOnConflict(
            "records",
            null,
            ContentValues().apply {
                put("bucket", bucket)
                put("id", id)
                put("value", vault.seal(json.toByteArray()))
                put("at", at)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
        check(inserted != -1L) { "Impossibile salvare i dati locali" }
        if (bucket !in
            listOf("settings", "published-at", "winners", "sync", "inbox-relays", "group-rosters")
        ) {
            generation++
        }
    }
    inline fun <reified T> save(
        bucket: String,
        id: String,
        value: T,
        at: Long = System.currentTimeMillis(),
    ) = put(bucket, id, communityJson.encodeToString(value), at)
    fun get(
        bucket: String,
        id: String,
    ): String? = readableDatabase.rawQuery(
        "SELECT value FROM records WHERE bucket=? AND id=?",
        arrayOf(bucket, id),
    ).use {
        if (it.moveToFirst()) vault.open(it.getBlob(0)).decodeToString() else null
    }
    inline fun <reified T> read(
        bucket: String,
        id: String,
    ): T? = get(bucket, id)?.let { communityJson.decodeFromString<T>(it) }
    fun entries(bucket: String, limit: Int = 500): List<Pair<String, String>> = readableDatabase.rawQuery(
        "SELECT id,value FROM records WHERE bucket=? ORDER BY at DESC,id LIMIT ?",
        arrayOf(bucket, limit.coerceIn(1, 20_000).toString()),
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(cursor.getString(0) to vault.open(cursor.getBlob(1)).decodeToString())
            }
        }
    }
    inline fun <reified T> list(
        bucket: String,
        limit: Int = 500,
    ): List<T> = entries(bucket, limit).map { communityJson.decodeFromString<T>(it.second) }
    fun remove(
        bucket: String,
        id: String,
    ) {
        if (writableDatabase.delete("records", "bucket=? AND id=?", arrayOf(bucket, id)) > 0) generation++
    }
    fun trim(bucket: String, keep: Int) {
        writableDatabase.execSQL(
            "DELETE FROM records WHERE bucket=? AND id NOT IN (SELECT id FROM records WHERE bucket=? ORDER BY at DESC,id LIMIT ?)",
            arrayOf<Any>(bucket, bucket, keep),
        )
    }
    fun seen(
        id: String,
    ): Boolean = readableDatabase.rawQuery("SELECT 1 FROM received WHERE id=?", arrayOf(id)).use { it.moveToFirst() }
    fun markSeen(id: String) {
        writableDatabase.insertWithOnConflict(
            "received",
            null,
            ContentValues().apply {
                put("id", id)
                put("at", System.currentTimeMillis())
            },
            SQLiteDatabase.CONFLICT_IGNORE,
        )
    }
    fun enqueue(event: NostrEvent, address: String = "") = transaction {
        if (address.isNotEmpty()) writableDatabase.delete("outbox", "address=? AND id!=?", arrayOf(address, event.id))
        writableDatabase.execSQL("DELETE FROM receipts WHERE event NOT IN (SELECT id FROM outbox)")
        val inserted = writableDatabase.insertWithOnConflict(
            "outbox",
            null,
            ContentValues().apply {
                put("id", event.id)
                put("event", vault.seal(communityJson.encodeToString(event).toByteArray()))
                put("address", address)
                put("created", event.created_at)
                put("expires", event.tag("expiration")?.toLongOrNull()?.times(1000) ?: 0)
            },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
        check(inserted != -1L) { "Impossibile salvare l’invio. La modifica resta nella coda locale." }
    }
    fun pending(
        limit: Int = 32,
        includeSync: Boolean = true,
    ): List<NostrEvent> = readableDatabase.rawQuery(
        "SELECT event FROM outbox WHERE (expires=0 OR expires>?) AND (address NOT LIKE 'nyanime.sync.%' OR ?=1) ORDER BY attempted,created,id LIMIT ?",
        arrayOf(System.currentTimeMillis().toString(), if (includeSync) "1" else "0", limit.toString()),
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    communityJson.decodeFromString<NostrEvent>(vault.open(cursor.getBlob(0)).decodeToString()),
                )
            }
        }
    }.also { events ->
        transaction {
            events.forEach { event ->
                writableDatabase.execSQL(
                    "UPDATE outbox SET attempted=? WHERE id=?",
                    arrayOf<Any>(System.currentTimeMillis(), event.id),
                )
            }
        }
    }
    fun pendingCount(): Int = readableDatabase.rawQuery("SELECT count(*) FROM outbox", null).use {
        it.moveToFirst()
        it.getInt(0)
    }
    fun count(
        bucket: String,
    ): Int = readableDatabase.rawQuery("SELECT count(*) FROM records WHERE bucket=?", arrayOf(bucket)).use {
        it.moveToFirst()
        it.getInt(0)
    }
    fun contains(bucket: String, id: String): Boolean =
        readableDatabase.rawQuery("SELECT 1 FROM records WHERE bucket=? AND id=?", arrayOf(bucket, id)).use {
            it.moveToFirst()
        }

    fun syncAddresses(): List<String> = readableDatabase.rawQuery(
        "SELECT id FROM records WHERE bucket='sync-addresses' ORDER BY rowid",
        null,
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }
    fun recoveryBatch(): List<String> = transaction {
        val addresses = readableDatabase.rawQuery(
            "SELECT id FROM records WHERE bucket='sync-needed' ORDER BY at,id LIMIT 40",
            null,
        ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }
        addresses.forEach {
            writableDatabase.execSQL(
                "UPDATE records SET at=? WHERE bucket='sync-needed' AND id=?",
                arrayOf<Any>(System.currentTimeMillis(), it),
            )
        }
        addresses
    }
    fun cleanExpired() {
        writableDatabase.delete("outbox", "expires>0 AND expires<?", arrayOf(System.currentTimeMillis().toString()))
        writableDatabase.execSQL("DELETE FROM receipts WHERE event NOT IN (SELECT id FROM outbox)")
    }
    fun recipient(
        eventId: String,
    ): String? = readableDatabase.rawQuery("SELECT event FROM outbox WHERE id=?", arrayOf(eventId)).use {
        if (!it.moveToFirst()) {
            null
        } else {
            communityJson.decodeFromString<NostrEvent>(vault.open(it.getBlob(0)).decodeToString())
                .takeIf { event -> event.kind == 1059 }?.tag("p")
        }
    }
    fun acknowledge(id: String, relay: String, quorum: Int) {
        transaction {
            if (!readableDatabase.rawQuery("SELECT 1 FROM outbox WHERE id=?", arrayOf(id)).use {
                    it.moveToFirst()
                }
            ) {
                return@transaction
            }
            writableDatabase.insertWithOnConflict(
                "receipts",
                null,
                ContentValues().apply {
                    put("event", id)
                    put("relay", relay)
                },
                SQLiteDatabase.CONFLICT_IGNORE,
            )
            val count = readableDatabase.rawQuery("SELECT count(*) FROM receipts WHERE event=?", arrayOf(id)).use {
                it.moveToFirst()
                it.getInt(0)
            }
            if (count >= quorum) {
                writableDatabase.delete("outbox", "id=?", arrayOf(id))
                writableDatabase.delete("receipts", "event=?", arrayOf(id))
            }
        }
    }
}
