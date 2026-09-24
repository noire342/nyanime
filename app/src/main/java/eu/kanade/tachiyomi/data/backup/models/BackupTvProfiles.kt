package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/** Private family viewing state. References are extension source IDs and stable content URLs. */
@Serializable
data class BackupTvProfiles(
    @ProtoNumber(1) val profiles: List<BackupTvProfile> = emptyList(),
    @ProtoNumber(2) val titles: List<BackupTvTitle> = emptyList(),
    @ProtoNumber(3) val episodes: List<BackupTvEpisode> = emptyList(),
)

@Serializable
data class BackupTvProfile(
    @ProtoNumber(1) val id: String,
    @ProtoNumber(2) val name: String,
    @ProtoNumber(3) val artwork: Int,
)

@Serializable
data class BackupTvTitle(
    @ProtoNumber(1) val profileId: String,
    @ProtoNumber(2) val source: Long,
    @ProtoNumber(3) val titleUrl: String,
    @ProtoNumber(4) val title: String,
    @ProtoNumber(5) val favorite: Boolean,
    @ProtoNumber(6) val category: String,
)

@Serializable
data class BackupTvEpisode(
    @ProtoNumber(1) val profileId: String,
    @ProtoNumber(2) val source: Long,
    @ProtoNumber(3) val titleUrl: String,
    @ProtoNumber(4) val episodeUrl: String,
    @ProtoNumber(5) val episodeName: String,
    @ProtoNumber(6) val seen: Boolean,
    @ProtoNumber(7) val bookmark: Boolean,
    @ProtoNumber(8) val positionMs: Long,
    @ProtoNumber(9) val durationMs: Long,
    @ProtoNumber(10) val lastSeenMs: Long,
)
