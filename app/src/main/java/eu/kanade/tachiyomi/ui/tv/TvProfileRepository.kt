package eu.kanade.tachiyomi.ui.tv

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import eu.kanade.tachiyomi.data.backup.models.BackupTvEpisode
import eu.kanade.tachiyomi.data.backup.models.BackupTvProfile
import eu.kanade.tachiyomi.data.backup.models.BackupTvProfiles
import eu.kanade.tachiyomi.data.backup.models.BackupTvTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class TvProfile(
    val id: String,
    val name: String,
    val artwork: Int,
    val hasPin: Boolean,
) {
    val isMain: Boolean get() = id == MAIN_ID

    companion object {
        const val MAIN_ID = "main"
    }
}

data class TvEpisodeState(
    val seen: Boolean = false,
    val bookmark: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val lastSeenMs: Long = 0,
)

data class TvContinueItem(
    val source: Long,
    val titleUrl: String,
    val title: String,
    val episodeUrl: String,
    val episodeName: String,
    val state: TvEpisodeState,
)

data class TvSavedTitle(val source: Long, val titleUrl: String, val category: String)

/** Private, profile-scoped state. The existing anime tables remain authoritative for Main. */
class TvProfileRepository private constructor(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    "nyanime_tv_profiles.db",
    null,
    1,
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE profiles (
                id TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                artwork INTEGER NOT NULL DEFAULT 0,
                pin_salt BLOB,
                pin_hash BLOB,
                pin_length INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """CREATE TABLE title_state (
                profile_id TEXT NOT NULL,
                source INTEGER NOT NULL,
                title_url TEXT NOT NULL,
                title TEXT NOT NULL,
                favorite INTEGER NOT NULL DEFAULT 0,
                category TEXT NOT NULL DEFAULT '',
                updated_at INTEGER NOT NULL,
                PRIMARY KEY(profile_id, source, title_url),
                FOREIGN KEY(profile_id) REFERENCES profiles(id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            """CREATE TABLE episode_state (
                profile_id TEXT NOT NULL,
                source INTEGER NOT NULL,
                title_url TEXT NOT NULL,
                episode_url TEXT NOT NULL,
                episode_name TEXT NOT NULL,
                seen INTEGER NOT NULL DEFAULT 0,
                bookmark INTEGER NOT NULL DEFAULT 0,
                position_ms INTEGER NOT NULL DEFAULT 0,
                duration_ms INTEGER NOT NULL DEFAULT 0,
                last_seen_ms INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(profile_id, source, title_url, episode_url),
                FOREIGN KEY(profile_id) REFERENCES profiles(id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX episode_state_recent ON episode_state(profile_id, last_seen_ms DESC)")
        db.insertOrThrow(
            "profiles",
            null,
            ContentValues().apply {
                put("id", TvProfile.MAIN_ID)
                put("name", "Principale")
                put("artwork", 0)
                put("created_at", 0)
            },
        )
    }

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    suspend fun profiles(): List<TvProfile> = withContext(Dispatchers.IO) {
        buildList {
            readableDatabase.rawQuery(
                "SELECT id, name, artwork, pin_hash FROM profiles ORDER BY created_at, id",
                null,
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    add(TvProfile(cursor.getString(0), cursor.getString(1), cursor.getInt(2), !cursor.isNull(3)))
                }
            }
        }
    }

    suspend fun create(name: String, artwork: Int): TvProfile = withContext(Dispatchers.IO) {
        val cleanName = name.trim().take(40)
        require(cleanName.isNotBlank())
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.rawQuery("SELECT COUNT(*) FROM profiles", null).use { cursor ->
                cursor.moveToFirst()
                require(cursor.getInt(0) < 5) { "Sono disponibili al massimo cinque profili" }
            }
            val result = TvProfile(UUID.randomUUID().toString(), cleanName, artwork.coerceIn(0, 11), false)
            db.insertOrThrow(
                "profiles",
                null,
                ContentValues().apply {
                    put("id", result.id)
                    put("name", result.name)
                    put("artwork", result.artwork)
                    put("created_at", System.currentTimeMillis())
                },
            )
            db.setTransactionSuccessful()
            result
        } finally {
            db.endTransaction()
        }
    }

    suspend fun rename(id: String, name: String, artwork: Int) = withContext(Dispatchers.IO) {
        val cleanName = name.trim().take(40)
        require(cleanName.isNotBlank())
        require(
            writableDatabase.update(
                "profiles",
                ContentValues().apply {
                    put("name", cleanName)
                    put("artwork", artwork.coerceIn(0, 11))
                },
                "id=?",
                arrayOf(id),
            ) == 1,
        )
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        require(id != TvProfile.MAIN_ID) { "Il profilo principale non può essere eliminato" }
        writableDatabase.delete("profiles", "id=?", arrayOf(id))
    }

    suspend fun setPin(id: String, pin: String?) = withContext(Dispatchers.IO) {
        require(pin == null || pin.matches(Regex("[0-9]{4,8}")))
        val salt = pin?.let { ByteArray(24).also(SecureRandom()::nextBytes) }
        val digest = if (pin != null && salt != null) hashPin(pin, salt) else null
        require(
            writableDatabase.update(
                "profiles",
                ContentValues().apply {
                    put("pin_salt", salt)
                    put("pin_hash", digest)
                    put("pin_length", pin?.length ?: 0)
                },
                "id=?",
                arrayOf(id),
            ) == 1,
        )
    }

    suspend fun verifyPin(id: String, pin: String): Boolean = withContext(Dispatchers.IO) {
        readableDatabase.rawQuery("SELECT pin_salt, pin_hash FROM profiles WHERE id=?", arrayOf(id)).use { cursor ->
            if (!cursor.moveToFirst()) return@withContext false
            if (cursor.isNull(1)) return@withContext true
            val salt = cursor.getBlob(0)
            val expected = cursor.getBlob(1)
            MessageDigest.isEqual(expected, hashPin(pin, salt))
        }
    }

    suspend fun pinLength(id: String): Int = withContext(Dispatchers.IO) {
        readableDatabase.rawQuery("SELECT pin_length FROM profiles WHERE id=?", arrayOf(id)).use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    suspend fun favorite(id: String, source: Long, titleUrl: String): Boolean = withContext(Dispatchers.IO) {
        if (id == TvProfile.MAIN_ID) return@withContext false
        readableDatabase.rawQuery(
            "SELECT favorite FROM title_state WHERE profile_id=? AND source=? AND title_url=?",
            arrayOf(id, source.toString(), titleUrl),
        ).use { it.moveToFirst() && it.getInt(0) != 0 }
    }

    suspend fun category(id: String, source: Long, titleUrl: String): String = withContext(Dispatchers.IO) {
        if (id == TvProfile.MAIN_ID) return@withContext ""
        readableDatabase.rawQuery(
            "SELECT category FROM title_state WHERE profile_id=? AND source=? AND title_url=?",
            arrayOf(id, source.toString(), titleUrl),
        ).use { if (it.moveToFirst()) it.getString(0) else "" }
    }

    suspend fun setCategory(id: String, source: Long, titleUrl: String, title: String, category: String) =
        withContext(Dispatchers.IO) {
            require(id != TvProfile.MAIN_ID)
            require(category in categories || category.isEmpty())
            val db = writableDatabase
            db.insertWithOnConflict(
                "title_state",
                null,
                ContentValues().apply {
                    put("profile_id", id)
                    put("source", source)
                    put("title_url", titleUrl)
                    put("title", title)
                    put("updated_at", System.currentTimeMillis())
                },
                SQLiteDatabase.CONFLICT_IGNORE,
            )
            db.update(
                "title_state",
                ContentValues().apply {
                    put("title", title)
                    put("favorite", true)
                    put("category", category)
                    put("updated_at", System.currentTimeMillis())
                },
                "profile_id=? AND source=? AND title_url=?",
                arrayOf(id, source.toString(), titleUrl),
            )
        }

    suspend fun savedTitles(id: String, sources: Set<Long>, limit: Int = 60): List<TvSavedTitle> =
        withContext(Dispatchers.IO) {
            if (id == TvProfile.MAIN_ID || sources.isEmpty()) return@withContext emptyList()
            val placeholders = sources.joinToString(",") { "?" }
            val args = arrayOf(id, *sources.map(Long::toString).toTypedArray(), limit.coerceIn(1, 100).toString())
            readableDatabase.rawQuery(
                """SELECT source,title_url,category FROM title_state WHERE profile_id=? AND favorite=1
                    AND source IN ($placeholders) ORDER BY updated_at DESC LIMIT ?
                """.trimIndent(),
                args,
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            TvSavedTitle(
                                cursor.getLong(0),
                                cursor.getString(1),
                                cursor.getString(2),
                            ),
                        )
                    }
                }
            }
        }

    suspend fun setFavorite(id: String, source: Long, titleUrl: String, title: String, favorite: Boolean) =
        withContext(Dispatchers.IO) {
            require(id != TvProfile.MAIN_ID)
            val db = writableDatabase
            db.insertWithOnConflict(
                "title_state",
                null,
                ContentValues().apply {
                    put("profile_id", id)
                    put("source", source)
                    put("title_url", titleUrl)
                    put("title", title)
                    put("updated_at", System.currentTimeMillis())
                },
                SQLiteDatabase.CONFLICT_IGNORE,
            )
            db.update(
                "title_state",
                ContentValues().apply {
                    put("title", title)
                    put("favorite", favorite)
                    put("updated_at", System.currentTimeMillis())
                },
                "profile_id=? AND source=? AND title_url=?",
                arrayOf(id, source.toString(), titleUrl),
            )
        }

    suspend fun state(id: String, source: Long, titleUrl: String, episodeUrl: String): TvEpisodeState =
        withContext(Dispatchers.IO) {
            if (id == TvProfile.MAIN_ID) return@withContext TvEpisodeState()
            readableDatabase.rawQuery(
                """SELECT seen,bookmark,position_ms,duration_ms,last_seen_ms FROM episode_state
                    WHERE profile_id=? AND source=? AND title_url=? AND episode_url=?
                """.trimIndent(),
                arrayOf(id, source.toString(), titleUrl, episodeUrl),
            ).use { if (it.moveToFirst()) it.episodeState() else TvEpisodeState() }
        }

    suspend fun episodeStates(id: String, source: Long, titleUrl: String): Map<String, TvEpisodeState> =
        withContext(Dispatchers.IO) {
            if (id == TvProfile.MAIN_ID) return@withContext emptyMap()
            readableDatabase.rawQuery(
                """SELECT episode_url,seen,bookmark,position_ms,duration_ms,last_seen_ms
                    FROM episode_state WHERE profile_id=? AND source=? AND title_url=?
                """.trimIndent(),
                arrayOf(id, source.toString(), titleUrl),
            ).use { cursor ->
                buildMap {
                    while (cursor.moveToNext()) put(cursor.getString(0), cursor.episodeState(1))
                }
            }
        }

    suspend fun writeProgress(
        ids: List<String>,
        source: Long,
        titleUrl: String,
        title: String,
        episodeUrl: String,
        episodeName: String,
        seen: Boolean,
        bookmark: Boolean,
        positionMs: Long,
        durationMs: Long,
    ) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            ids.distinct().filter { it != TvProfile.MAIN_ID }.forEach { id ->
                db.insertWithOnConflict(
                    "title_state",
                    null,
                    ContentValues().apply {
                        put("profile_id", id)
                        put("source", source)
                        put("title_url", titleUrl)
                        put("title", title)
                        put("updated_at", System.currentTimeMillis())
                    },
                    SQLiteDatabase.CONFLICT_IGNORE,
                )
                db.update(
                    "title_state",
                    ContentValues().apply {
                        put("title", title)
                        put("updated_at", System.currentTimeMillis())
                    },
                    "profile_id=? AND source=? AND title_url=?",
                    arrayOf(id, source.toString(), titleUrl),
                )
                db.insertWithOnConflict(
                    "episode_state",
                    null,
                    ContentValues().apply {
                        put("profile_id", id)
                        put("source", source)
                        put("title_url", titleUrl)
                        put("episode_url", episodeUrl)
                        put("episode_name", episodeName)
                    },
                    SQLiteDatabase.CONFLICT_IGNORE,
                )
                db.update(
                    "episode_state",
                    ContentValues().apply {
                        put("episode_name", episodeName)
                        put("seen", seen)
                        put("bookmark", bookmark)
                        put("position_ms", positionMs.coerceAtLeast(0))
                        put("duration_ms", durationMs.coerceAtLeast(0))
                        put("last_seen_ms", System.currentTimeMillis())
                    },
                    "profile_id=? AND source=? AND title_url=? AND episode_url=?",
                    arrayOf(id, source.toString(), titleUrl, episodeUrl),
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    suspend fun continueWatching(id: String, sources: Set<Long>, limit: Int = 24): List<TvContinueItem> =
        withContext(Dispatchers.IO) {
            if (id == TvProfile.MAIN_ID || sources.isEmpty()) return@withContext emptyList()
            val placeholders = sources.joinToString(",") { "?" }
            val args = arrayOf(id, *sources.map(Long::toString).toTypedArray(), limit.coerceIn(1, 100).toString())
            readableDatabase.rawQuery(
                """SELECT e.source,e.title_url,t.title,e.episode_url,e.episode_name,
                    e.seen,e.bookmark,e.position_ms,e.duration_ms,e.last_seen_ms
                    FROM episode_state e JOIN title_state t ON t.profile_id=e.profile_id
                    AND t.source=e.source AND t.title_url=e.title_url
                    WHERE e.profile_id=? AND e.source IN ($placeholders) AND e.last_seen_ms>0
                    ORDER BY e.last_seen_ms DESC LIMIT ?
                """.trimIndent(),
                args,
            ).use { cursor ->
                buildList {
                    val used = HashSet<Pair<Long, String>>()
                    while (cursor.moveToNext()) {
                        val key = cursor.getLong(0) to cursor.getString(1)
                        if (used.add(key)) {
                            add(
                                TvContinueItem(
                                    key.first,
                                    key.second,
                                    cursor.getString(2),
                                    cursor.getString(3),
                                    cursor.getString(4),
                                    cursor.episodeState(5),
                                ),
                            )
                        }
                    }
                }
            }
        }

    suspend fun exportForBackup(): BackupTvProfiles = withContext(Dispatchers.IO) {
        val db = readableDatabase
        val people = buildList {
            db.rawQuery("SELECT id,name,artwork FROM profiles ORDER BY created_at,id", null).use { cursor ->
                while (cursor.moveToNext()) {
                    add(
                        BackupTvProfile(cursor.getString(0), cursor.getString(1), cursor.getInt(2)),
                    )
                }
            }
        }
        val titles = buildList {
            db.rawQuery(
                "SELECT profile_id,source,title_url,title,favorite,category FROM title_state",
                null,
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    add(
                        BackupTvTitle(
                            cursor.getString(0),
                            cursor.getLong(1),
                            cursor.getString(2),
                            cursor.getString(3),
                            cursor.getInt(4) != 0,
                            cursor.getString(5),
                        ),
                    )
                }
            }
        }
        val episodes = buildList {
            db.rawQuery(
                """SELECT profile_id,source,title_url,episode_url,episode_name,seen,bookmark,
                position_ms,duration_ms,last_seen_ms FROM episode_state
                """.trimIndent(),
                null,
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    add(
                        BackupTvEpisode(
                            cursor.getString(0), cursor.getLong(1),
                            cursor.getString(2), cursor.getString(3), cursor.getString(4), cursor.getInt(5) != 0,
                            cursor.getInt(6) != 0, cursor.getLong(7), cursor.getLong(8), cursor.getLong(9),
                        ),
                    )
                }
            }
        }
        BackupTvProfiles(people, titles, episodes)
    }

    /** Restore by stable references. PIN hashes and tracking logins are deliberately absent. */
    suspend fun restoreFromBackup(snapshot: BackupTvProfiles) = withContext(Dispatchers.IO) {
        val people = snapshot.profiles.distinctBy { it.id }
        require(people.size <= 5 && people.any { it.id == TvProfile.MAIN_ID })
        require(people.all { it.id.length in 1..64 && it.name.isNotBlank() && it.name.length <= 40 })
        require(snapshot.titles.size <= 200_000 && snapshot.episodes.size <= 1_000_000)
        val ids = people.map { it.id }.toSet()
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("episode_state", null, null)
            db.delete("title_state", null, null)
            db.delete("profiles", "id<>?", arrayOf(TvProfile.MAIN_ID))
            people.forEachIndexed { index, person ->
                val values = ContentValues().apply {
                    put("id", person.id)
                    put("name", person.name)
                    put("artwork", person.artwork.coerceIn(0, 11))
                    put("created_at", index.toLong())
                    putNull("pin_salt")
                    putNull("pin_hash")
                    put("pin_length", 0)
                }
                db.insertWithOnConflict("profiles", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
            snapshot.titles.filter { it.profileId in ids && it.profileId != TvProfile.MAIN_ID }
                .forEach { title ->
                    db.insertWithOnConflict(
                        "title_state",
                        null,
                        ContentValues().apply {
                            put("profile_id", title.profileId)
                            put("source", title.source)
                            put("title_url", title.titleUrl)
                            put("title", title.title)
                            put("favorite", title.favorite)
                            put("category", title.category)
                            put("updated_at", 0)
                        },
                        SQLiteDatabase.CONFLICT_REPLACE,
                    )
                }
            snapshot.episodes.filter { it.profileId in ids && it.profileId != TvProfile.MAIN_ID }
                .forEach { episode ->
                    db.insertWithOnConflict(
                        "episode_state",
                        null,
                        ContentValues().apply {
                            put("profile_id", episode.profileId)
                            put("source", episode.source)
                            put("title_url", episode.titleUrl)
                            put("episode_url", episode.episodeUrl)
                            put("episode_name", episode.episodeName)
                            put("seen", episode.seen)
                            put("bookmark", episode.bookmark)
                            put("position_ms", episode.positionMs.coerceAtLeast(0))
                            put("duration_ms", episode.durationMs.coerceAtLeast(0))
                            put("last_seen_ms", episode.lastSeenMs.coerceAtLeast(0))
                        },
                        SQLiteDatabase.CONFLICT_REPLACE,
                    )
                }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun Cursor.episodeState(offset: Int = 0) = TvEpisodeState(
        seen = getInt(offset) != 0,
        bookmark = getInt(offset + 1) != 0,
        positionMs = getLong(offset + 2),
        durationMs = getLong(offset + 3),
        lastSeenMs = getLong(offset + 4),
    )

    private fun hashPin(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 120_000, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    companion object {
        val categories = listOf("Da vedere", "In corso", "Completati", "In pausa", "Abbandonati")

        @Volatile private var instance: TvProfileRepository? = null
        fun get(context: Context): TvProfileRepository = instance ?: synchronized(this) {
            instance ?: TvProfileRepository(context).also { instance = it }
        }
    }
}
