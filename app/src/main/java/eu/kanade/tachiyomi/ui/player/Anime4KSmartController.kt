package eu.kanade.tachiyomi.ui.player

/**
 * Session-local Adaptive Headroom Governor. It probes one level at a time and uses complete
 * telemetry windows, so a single late frame or shader warm-up spike cannot cause oscillation.
 */
class Anime4KSmartController(
    initialMediaInfo: Anime4KMediaInfo,
    initialTimestampMillis: Long,
    initialCalibration: Anime4KCalibration? = null,
) {
    companion object {
        private const val MIN_SAMPLE_INTERVAL_MILLIS = 750L
        private const val WARMUP_MILLIS = 2_000L
        private const val WINDOW_MILLIS = 5_000L
        private const val UPGRADE_COOLDOWN_MILLIS = 10_000L
        private const val DOWNGRADE_COOLDOWN_MILLIS = 1_500L
        private const val HEALTHY_WINDOWS_FOR_UPGRADE = 2
        private const val OVERLOADED_WINDOWS_FOR_DOWNGRADE = 2

        private const val HEALTHY_HEADROOM = 1.20
        private const val SEVERE_HEADROOM = 0.85
        private const val OUTPUT_DROP_OVERLOAD = 0.01
        private const val DECODER_DROP_OVERLOAD = 0.0025
        private const val DELAYED_FRAME_OVERLOAD = 0.02
        private const val MISTIMED_FRAME_OVERLOAD = 0.03
        private const val OUTPUT_DROP_HEALTHY = 0.005
        private const val DECODER_DROP_HEALTHY = 0.001
        private const val DELAYED_FRAME_HEALTHY = 0.01
        private const val MISTIMED_FRAME_HEALTHY = 0.02
    }

    private data class PerformanceWindow(
        val elapsedSeconds: Double,
        val expectedFrames: Double,
        val outputDrops: Long?,
        val decoderDrops: Long?,
        val delayedFrames: Long?,
        val mistimedFrames: Long?,
        val filterFps: Double?,
    )

    private var mediaInfo = initialMediaInfo
    private var lastSample: Anime4KPerformanceSample? = null
    private var windowStartSample: Anime4KPerformanceSample? = null
    private val filterFpsSamples = mutableListOf<Double>()
    private var lastTransitionMillis = initialTimestampMillis
    private var healthyWindows = 0
    private var overloadedWindows = 0
    private var probeSafeMode = Anime4K.resolveSmartMode(initialMediaInfo)
    private var probingMode: Anime4KMode? = null
    private var lastReason: String? = "conservative start"
    private var successfulProbes = initialCalibration?.successfulProbes ?: 0
    private val calibratedCeiling = initialCalibration?.maxStableMode
    private val shaderFailedModes = mutableSetOf<Anime4KMode>()
    private val performanceBlockedModes = mutableSetOf<Anime4KMode>()

    var currentMode: Anime4KMode = Anime4K.resolveSmartMode(initialMediaInfo)
        private set

    var stableMode: Anime4KMode = currentMode
        private set

    var isSuspended: Boolean = false
        private set

    val calibration: Anime4KCalibration
        get() = Anime4KCalibration(stableMode, successfulProbes, lastTransitionMillis)

    private var currentDiagnostics = Anime4KSmartDiagnostics(
        profile = Anime4KProfile.Smart,
        mode = currentMode,
        sourceWidth = initialMediaInfo.sourceWidth,
        sourceHeight = initialMediaInfo.sourceHeight,
        targetWidth = initialMediaInfo.targetWidth,
        targetHeight = initialMediaInfo.targetHeight,
        targetFramesPerSecond = Anime4K.effectiveFrameRate(initialMediaInfo),
        reason = lastReason,
    )

    val diagnostics: Anime4KSmartDiagnostics
        get() = currentDiagnostics.copy(
            mode = currentMode,
            probing = probingMode != null,
            suspended = isSuspended,
            reason = lastReason,
        )

    fun updateMediaInfo(
        updatedMediaInfo: Anime4KMediaInfo,
        nowMillis: Long,
    ): Anime4KSmartAdjustment? {
        if (updatedMediaInfo != mediaInfo) {
            mediaInfo = updatedMediaInfo
            performanceBlockedModes.clear()
            resetTelemetry()
            lastReason = "media changed"
            updateDiagnostics(Anime4KHealth.Unknown)
        }
        if (isSuspended) return null

        if (
            Anime4K.isSecondaryMode(currentMode) &&
            !Anime4K.supportsSecondaryPass(updatedMediaInfo) &&
            nowMillis - lastTransitionMillis >= DOWNGRADE_COOLDOWN_MILLIS
        ) {
            val fallback = Anime4K.primaryVariant(currentMode)
            if (fallback != currentMode) {
                stableMode = fallback
                return transition(
                    mode = fallback,
                    nowMillis = nowMillis,
                    reason = "secondary pass no longer compatible",
                )
            }
        }
        return null
    }

    fun onSample(sample: Anime4KPerformanceSample): Anime4KSmartAdjustment? {
        if (isSuspended) return null
        val previous = lastSample
        if (
            previous != null &&
            sample.timestampMillis - previous.timestampMillis < MIN_SAMPLE_INTERVAL_MILLIS
        ) {
            return null
        }
        lastSample = sample

        val filterFps = sample.estimatedFilterFramesPerSecond
            ?.takeIf { it.isFinite() && it > 0.0 }
        if (filterFps != null) filterFpsSamples += filterFps

        if (sample.timestampMillis - lastTransitionMillis < WARMUP_MILLIS) {
            windowStartSample = null
            filterFpsSamples.clear()
            filterFps?.let { filterFpsSamples += it }
            updateDiagnostics(
                health = Anime4KHealth.Unknown,
                filterFps = filterFps,
            )
            return null
        }

        val windowStart = windowStartSample ?: run {
            windowStartSample = sample
            filterFpsSamples.clear()
            filterFps?.let { filterFpsSamples += it }
            updateDiagnostics(
                health = Anime4KHealth.Unknown,
                filterFps = filterFps,
            )
            return null
        }
        val elapsedMillis = sample.timestampMillis - windowStart.timestampMillis
        if (elapsedMillis < WINDOW_MILLIS) {
            updateDiagnostics(
                health = Anime4KHealth.Unknown,
                filterFps = filterFpsSamples.averageOrNull(),
            )
            return null
        }

        val elapsedSeconds = elapsedMillis.toDouble() / 1000.0
        val targetFps = Anime4K.effectiveFrameRate(mediaInfo)
        val window = PerformanceWindow(
            elapsedSeconds = elapsedSeconds,
            expectedFrames = (targetFps * elapsedSeconds).coerceAtLeast(1.0),
            outputDrops = counterDelta(sample.outputDroppedFrames, windowStart.outputDroppedFrames),
            decoderDrops = counterDelta(sample.decoderDroppedFrames, windowStart.decoderDroppedFrames),
            delayedFrames = counterDelta(sample.delayedFrames, windowStart.delayedFrames),
            mistimedFrames = counterDelta(sample.mistimedFrames, windowStart.mistimedFrames),
            filterFps = filterFpsSamples.averageOrNull(),
        )
        windowStartSample = sample
        filterFpsSamples.clear()
        filterFps?.let { filterFpsSamples += it }
        return evaluateWindow(window, sample.timestampMillis)
    }

    /** Discards measurements across a seek without changing the selected quality. */
    fun resetTelemetry() {
        lastSample = null
        windowStartSample = null
        filterFpsSamples.clear()
        healthyWindows = 0
        overloadedWindows = 0
        updateDiagnostics(Anime4KHealth.Unknown)
    }

    /** Explicit user reactivation after Smart has suspended itself at Off. */
    fun resume(nowMillis: Long) {
        isSuspended = false
        performanceBlockedModes.clear()
        currentMode = Anime4K.resolveSmartMode(mediaInfo)
        stableMode = currentMode
        probeSafeMode = currentMode
        probingMode = null
        lastTransitionMillis = nowMillis
        lastReason = "manually reactivated"
        resetTelemetry()
    }

    /** Handles a synchronous MPV failure while applying a candidate mode. */
    fun onModeApplyFailure(nowMillis: Long): Anime4KSmartAdjustment? {
        if (currentMode == Anime4KMode.Off) return null
        performanceBlockedModes += currentMode
        val fallback = if (probingMode != null) probeSafeMode else Anime4K.lessDemandingMode(currentMode)
        val safeFallback = if (fallback == currentMode) Anime4KMode.Off else fallback
        if (safeFallback != Anime4KMode.Off) stableMode = safeFallback
        return transition(
            mode = safeFallback,
            nowMillis = nowMillis,
            reason = "shader pipeline unavailable",
            suspended = safeFallback == Anime4KMode.Off,
        )
    }

    fun onShaderError(
        nowMillis: Long,
        shaderName: String? = null,
    ): Anime4KSmartAdjustment? {
        if (currentMode == Anime4KMode.Off) return null
        shaderName?.let { failedShader ->
            Anime4KMode.entries
                .filter { failedShader in it.shaderFileNames }
                .forEach(shaderFailedModes::add)
        }
        shaderFailedModes += currentMode
        val fallback = if (probingMode != null) probeSafeMode else Anime4K.lessDemandingMode(currentMode)
        val safeFallback = if (fallback == currentMode) Anime4KMode.Off else fallback
        if (safeFallback != Anime4KMode.Off) stableMode = safeFallback
        return transition(
            mode = safeFallback,
            nowMillis = nowMillis,
            reason = "shader compilation failure",
            suspended = safeFallback == Anime4KMode.Off,
        )
    }

    private fun evaluateWindow(
        window: PerformanceWindow,
        nowMillis: Long,
    ): Anime4KSmartAdjustment? {
        val targetFps = Anime4K.effectiveFrameRate(mediaInfo)
        val outputDropRate = window.rate(window.outputDrops)
        val decoderDropRate = window.rate(window.decoderDrops)
        val delayedFrameRate = window.rate(window.delayedFrames)
        val mistimedFrameRate = window.rate(window.mistimedFrames)
        val headroom = window.filterFps?.let { it / targetFps }
        val confidence = telemetryConfidence(
            window.filterFps,
            outputDropRate,
            decoderDropRate,
            delayedFrameRate,
            mistimedFrameRate,
        )

        val severe =
            (headroom != null && headroom < SEVERE_HEADROOM) ||
                (outputDropRate ?: 0.0) >= 0.03 ||
                (decoderDropRate ?: 0.0) >= 0.01 ||
                (delayedFrameRate ?: 0.0) >= 0.05
        val overloaded =
            severe ||
                (headroom != null && headroom < 0.98) ||
                (outputDropRate ?: 0.0) >= OUTPUT_DROP_OVERLOAD ||
                (decoderDropRate ?: 0.0) >= DECODER_DROP_OVERLOAD ||
                (delayedFrameRate ?: 0.0) >= DELAYED_FRAME_OVERLOAD ||
                (mistimedFrameRate ?: 0.0) >= MISTIMED_FRAME_OVERLOAD
        val healthy =
            window.filterFps != null &&
                confidence >= 0.75 &&
                (headroom ?: 0.0) >= HEALTHY_HEADROOM &&
                below(outputDropRate, OUTPUT_DROP_HEALTHY) &&
                below(decoderDropRate, DECODER_DROP_HEALTHY) &&
                below(delayedFrameRate, DELAYED_FRAME_HEALTHY) &&
                below(mistimedFrameRate, MISTIMED_FRAME_HEALTHY)
        val health = when {
            severe -> Anime4KHealth.Severe
            overloaded -> Anime4KHealth.Overloaded
            healthy -> Anime4KHealth.Healthy
            confidence > 0.0 -> Anime4KHealth.Borderline
            else -> Anime4KHealth.Unknown
        }
        updateDiagnostics(
            health = health,
            filterFps = window.filterFps,
            outputDropRate = outputDropRate,
            decoderDropRate = decoderDropRate,
            delayedFrameRate = delayedFrameRate,
            mistimedFrameRate = mistimedFrameRate,
            headroom = headroom,
            confidence = confidence,
        )

        if (overloaded) {
            overloadedWindows++
            healthyWindows = 0
            val shouldDowngrade = probingMode != null ||
                severe ||
                overloadedWindows >= OVERLOADED_WINDOWS_FOR_DOWNGRADE
            if (shouldDowngrade && nowMillis - lastTransitionMillis >= DOWNGRADE_COOLDOWN_MILLIS) {
                val fallback = if (probingMode != null) probeSafeMode else Anime4K.lessDemandingMode(currentMode)
                if (fallback != currentMode) {
                    performanceBlockedModes += currentMode
                    if (fallback != Anime4KMode.Off) stableMode = fallback
                    return transition(
                        mode = fallback,
                        nowMillis = nowMillis,
                        reason = if (probingMode !=
                            null
                        ) {
                            "probe exceeded playback budget"
                        } else {
                            "playback headroom exhausted"
                        },
                        suspended = fallback == Anime4KMode.Off,
                    )
                }
            }
            return null
        }

        overloadedWindows = 0
        if (!healthy) {
            healthyWindows = 0
            return null
        }

        if (probingMode != null) {
            stableMode = currentMode
            successfulProbes++
            probingMode = null
            lastReason = "probe stable"
            updateDiagnostics(Anime4KHealth.Healthy)
            healthyWindows = 0
            return null
        }

        healthyWindows++
        if (
            healthyWindows >= HEALTHY_WINDOWS_FOR_UPGRADE &&
            nowMillis - lastTransitionMillis >= UPGRADE_COOLDOWN_MILLIS
        ) {
            val upgrade = Anime4K.moreDemandingMode(currentMode, mediaInfo)
                ?.takeUnless(shaderFailedModes::contains)
                ?.takeUnless(performanceBlockedModes::contains)
                ?.takeIf(::isWithinCalibrationCeiling)
            if (upgrade != null) {
                probeSafeMode = currentMode
                return transition(
                    mode = upgrade,
                    nowMillis = nowMillis,
                    reason = "probing ${Anime4K.smartButtonLabel(upgrade)}",
                    probing = true,
                )
            }
        }
        return null
    }

    private fun isWithinCalibrationCeiling(mode: Anime4KMode): Boolean {
        return calibratedCeiling == null ||
            Anime4K.complexityRank(mode) <= Anime4K.complexityRank(calibratedCeiling)
    }

    private fun transition(
        mode: Anime4KMode,
        nowMillis: Long,
        reason: String,
        probing: Boolean = false,
        suspended: Boolean = false,
    ): Anime4KSmartAdjustment {
        currentMode = mode
        probingMode = if (probing) mode else null
        isSuspended = suspended
        lastTransitionMillis = nowMillis
        lastReason = reason
        resetTelemetry()
        return Anime4KSmartAdjustment(mode, reason)
    }

    private fun updateDiagnostics(
        health: Anime4KHealth,
        filterFps: Double? = currentDiagnostics.filterFramesPerSecond,
        outputDropRate: Double? = currentDiagnostics.outputDropRate,
        decoderDropRate: Double? = currentDiagnostics.decoderDropRate,
        delayedFrameRate: Double? = currentDiagnostics.delayedFrameRate,
        mistimedFrameRate: Double? = currentDiagnostics.mistimedFrameRate,
        headroom: Double? = currentDiagnostics.headroom,
        confidence: Double = currentDiagnostics.confidence,
    ) {
        currentDiagnostics = Anime4KSmartDiagnostics(
            profile = Anime4KProfile.Smart,
            mode = currentMode,
            sourceWidth = mediaInfo.sourceWidth,
            sourceHeight = mediaInfo.sourceHeight,
            targetWidth = mediaInfo.targetWidth,
            targetHeight = mediaInfo.targetHeight,
            targetFramesPerSecond = Anime4K.effectiveFrameRate(mediaInfo),
            filterFramesPerSecond = filterFps,
            outputDropRate = outputDropRate,
            decoderDropRate = decoderDropRate,
            delayedFrameRate = delayedFrameRate,
            mistimedFrameRate = mistimedFrameRate,
            headroom = headroom,
            confidence = confidence,
            health = health,
            probing = probingMode != null,
            suspended = isSuspended,
            reason = lastReason,
        )
    }

    private fun telemetryConfidence(
        filterFps: Double?,
        outputDropRate: Double?,
        decoderDropRate: Double?,
        delayedFrameRate: Double?,
        mistimedFrameRate: Double?,
    ): Double {
        return (if (filterFps != null) 0.40 else 0.0) +
            (if (outputDropRate != null) 0.25 else 0.0) +
            (if (decoderDropRate != null) 0.15 else 0.0) +
            (if (delayedFrameRate != null) 0.10 else 0.0) +
            (if (mistimedFrameRate != null) 0.10 else 0.0)
    }

    private fun below(value: Double?, limit: Double): Boolean = value == null || value < limit

    private fun PerformanceWindow.rate(delta: Long?): Double? = delta?.toDouble()?.div(expectedFrames)

    private fun counterDelta(current: Long?, previous: Long?): Long? {
        return if (current != null && previous != null) {
            (current - previous).coerceAtLeast(0L)
        } else {
            null
        }
    }

    private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()
}
