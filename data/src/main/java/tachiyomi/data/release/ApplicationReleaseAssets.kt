package tachiyomi.data.release

import tachiyomi.domain.release.interactor.GetApplicationRelease

/** Select application packages only: extension APKs, checksums and source archives are not app updates. */
object ApplicationReleaseAssets {
    private val abis = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
    private val stableTag = Regex("[vr][0-9][A-Za-z0-9.+-]*")
    private val tvTag = GetApplicationRelease.PreviewChannel.TV.tagRegex

    fun select(
        assets: List<GitHubAsset>,
        supportedAbis: List<String>,
        channel: GetApplicationRelease.PreviewChannel = GetApplicationRelease.PreviewChannel.STANDARD,
    ): String? {
        val packages = assets.mapNotNull { asset ->
            val name = asset.name
            val abi = if (channel == GetApplicationRelease.PreviewChannel.TV) {
                when {
                    name.startsWith("Nyanime-TV-") &&
                        name.endsWith(".apk") &&
                        name.removePrefix("Nyanime-TV-").removeSuffix(".apk").matches(tvTag) -> "universal"
                    else -> abis.firstOrNull { candidate ->
                        name.startsWith("Nyanime-TV-$candidate-") &&
                            name.endsWith(".apk") &&
                            name.removePrefix("Nyanime-TV-$candidate-").removeSuffix(".apk").matches(tvTag)
                    } ?: return@mapNotNull null
                }
            } else {
                when {
                    name == "app-universal-preview.apk" -> "universal"
                    name.removePrefix("Nyanime-").removeSuffix(".apk").matches(stableTag) &&
                        name.startsWith("Nyanime-") &&
                        name.endsWith(".apk") -> "universal"
                    else -> abis.firstOrNull { abi ->
                        name == "app-" + abi + "-preview.apk" ||
                            (
                                name.startsWith("Nyanime-" + abi + "-") &&
                                    name.endsWith(".apk") &&
                                    name.removePrefix("Nyanime-" + abi + "-").removeSuffix(".apk").matches(stableTag)
                                )
                    } ?: return@mapNotNull null
                }
            }
            abi to asset.downloadLink
        }.toMap()
        return supportedAbis.firstNotNullOfOrNull { packages[it] } ?: packages["universal"]
    }
}
