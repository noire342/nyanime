# Anime4K Ultra offline

Ultra is an optional second step after a successful internal episode download.
Enable **Settings → Downloads → Anime4K Ultra → Ultra dopo il download**.
It is disabled by default; charging-only processing is enabled by default.
The queue in the same settings group shows progress, failures and cancellation.

The regular **SM** player button remains. The separate **4K** quick button has
been removed; existing saved Maximum and Custom choices remain compatible.

## Output

- Uses the bundled Maximum **A+ HQ** shader sequence, including its CNN weights,
  signed half-float intermediate textures and highlight clamping. Shader assets
  are unchanged. Media3 Transformer performs GPU rendering and hardware encoding.
- SDR output is H.264, up to four times the source dimensions and bounded by
  3840×2160 (2160×3840 for portrait). Aspect ratio and frame timestamps are preserved.
  Hardware must support the requested output; there is no silent preset downgrade.
- FFmpeg remuxes the encoded video with the original audio tracks, subtitles,
  chapters and attachments into Matroska. Unsupported input/track combinations
  fail the conversion and retain the original.
- `Nyanime-Ultra.mkv` is selected only after dimensions, duration, copied length
  and the completion marker have been checked. An unfinished export is never Ultra.
- Completed downloads show a magenta **ULTRA** label in place of SM. Runtime
  Anime4K controls, diagnostics and automatic startup are suppressed for that file.
  Opening a regular video restores its own preferences. Room restrictions remain.

## Resource and lifecycle behavior

The original download is retained alongside the processed copy. Conversion needs
additional temporary storage and can take longer than the episode's duration.
It may substantially increase file size; the output is an enhancement, not a way
to recover information missing from the original recording.

Only one GPU conversion runs at a time. Jobs wait for battery/storage constraints
and optionally charging. Opening the player interrupts a running conversion;
WorkManager retries after playback. Interrupted encodes restart from the beginning.
Severe thermal pressure stops processing with a retryable error in the queue.
Cancelling one job does not cancel other episodes. Temporary files are cleaned
up and the original remains playable after cancellation, errors or process death.

HDR exports and devices that cannot render/encode the target output fail safely.
This does not change the native MPV/FFmpeg playback libraries, streaming resolver,
download quality selection, manga pipeline or external downloader behavior.

## Validation boundaries

JVM tests compile the real bundled shader graph at landscape, portrait and native
4K sizes; verify dependency ordering, dimensions and preservation of Smart/custom
episode preferences when Ultra and rooms overlap. Physical-device probes exercise
the export engine. Short-clip checks do not establish full-episode throughput,
thermal endurance or compatibility with every device and source container.
