# Anime4K Ultra offline

Ultra is an optional second step after a successful internal episode download.
Enable **Settings → Downloads → Anime4K Ultra → Ultra dopo il download**.
It is disabled by default; charging-only and screen-off-only processing are enabled
by default. Existing enabled installations inherit the new screen-off default.

Downloaded episode rows expose their local file and Ultra state directly in the
title screen, in both ModernUI and legacy UI. Tap the row for playback, preparation,
pause/resume, retry, cancellation and **Export a copy** through Android's document
picker. The original stays in the episode's download folder; a completed Ultra
copy stays beside it. Normal episode playback selects that verified copy.
**Downloads → Ultra** and the settings queue show the same durable state, including
the reason for an automatic pause. Foreground notifications open the title.

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

Only one GPU conversion runs at a time. Transformer control and cancellation run
on a background HandlerThread, with background-priority GL work and an encoder
operating-rate hint of 12 / priority 1. This hint does not alter output frame rate.
Only hardware video encoders are selected.

Each shader pass uses scissored strips of at most 262,144 pixels and 128 rows.
The viewport, sampling coordinates, shaders and half-float intermediate images
remain unchanged. Each strip drains with glFinish before resting for four times
its measured work duration (nine times while interactive/warm). These are software
duty budgets, **not measured CPU/GPU utilization or a guaranteed device temperature**.

Admission is rechecked during encoding. Android MODERATE thermal status, a battery
sensor temperature of 39 C, or valid forecast thermal headroom >= 0.8 pauses work.
Resume requires status <= LIGHT, battery <= 36.5 C, headroom < 0.65 when available,
and a minimum cooling interval. Headroom is sampled at most once every 10 seconds.
Battery temperature is a fallback sensor, not a measurement of the GPU temperature.
Playback, screen-on by default, power saving, low battery and memory pressure
also defer work. WorkManager manages retries and charging/storage constraints;
Android may delay a restart.

Exports checkpoint short video-only clips, normally about two seconds of source
video, split at actual presentation timestamps. Finished clips and atomic receipts
live in app-private no-backup storage. A pause or process death discards only the
unfinished clip. Fixed source-timeline durations in the FFmpeg concat manifest
avoid accumulating rounding errors at joins. Audio is copied once from the original.
Sessions yield at a completed clip boundary after 20 minutes.

The durable task journal is separate from prunable WorkManager history. Worker
updates are scoped to their attempt ID, so an obsolete worker cannot overwrite a
new attempt or a manual pause. Cancellation discards checkpoints, never the original.
Deleting downloads cancels their work; successful publication removes scratch data.
Interrupted copies never receive a completion marker.

HDR exports and devices that cannot render/encode the target output fail safely.
This does not change the native MPV/FFmpeg playback libraries, streaming resolver,
download quality selection, manga pipeline or external downloader behavior.

## Validation boundaries

JVM tests cover thermal hysteresis, playback/resource admission, pacing arithmetic,
frame-aligned segment boundaries, interrupted checkpoints, queue persistence and
stale-worker exclusion, alongside the real bundled shader graph and existing
Smart/room preference tests. LayoutLib previews cover narrow screens, large text
and legacy styling without an emulator.

The new pacing and segmented export still require a physical-device endurance
check: temperature over time, responsiveness, pause/resume, audio synchronization
across joins, complete-episode output and vendor codec compatibility. Desktop
tests and successful APK assembly do not establish those measurements.
