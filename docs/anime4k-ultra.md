# Compatibility with existing Ultra downloads

Background Ultra conversion is no longer available. The download preferences,
conversion queue, GPU exporter and Media3 Transformer/effect dependencies have
been removed, along with the media-processing foreground-service permission.
Normal downloads and real-time Anime4K **SM** remain available. The separate
**4K** quick button remains removed.

Previously completed `Nyanime-Ultra.mkv` files remain playable. A valid
`nyanime-ultra.json` completion marker is required before selecting the processed
copy and showing the magenta **ULTRA** badge. Existing originals and processed
files are not deleted. Incomplete exports are never selected as Ultra.

For a completed Ultra file, real-time shaders stay off without overwriting the
episode's saved Smart/custom preferences. Opening a normal video restores its
own settings. The Watch Together restriction remains independent.

The old worker class is retained only to finish persisted jobs with a terminal
failure; it cannot start a foreground service, encode a video, schedule retries,
or modify any downloaded file. No new conversion jobs can be enqueued.

The feature was removed during investigation of an Android installation block.
Timing alone does not establish that Ultra caused the Play Protect warning.
