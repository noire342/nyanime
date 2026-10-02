package eu.kanade.tachiyomi.ui.gestures

/** Bounded fast sampling for brief impulses, respecting the slower of the two physical sensors. */
internal object BackTapSampling {
    fun periodMicros(accelerometerMinDelay: Int, gyroscopeMinDelay: Int, highRate: Boolean = true): Int =
        maxOf(if (highRate) 2_500 else 5_000, accelerometerMinDelay, gyroscopeMinDelay)
}
