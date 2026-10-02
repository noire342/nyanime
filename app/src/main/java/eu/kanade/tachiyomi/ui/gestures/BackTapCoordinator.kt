package eu.kanade.tachiyomi.ui.gestures

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import eu.kanade.tachiyomi.ui.base.delegate.SecureActivityDelegate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

data class BackTapTestState(
    val active: Boolean = false,
    val calibrating: Boolean = false,
    val warmingUp: Boolean = false,
    val completed: Int = 0,
    val detections: Int = 0,
)

/** One foreground sensor owner for the process. Android and native playback are never touched by the worker. */
class BackTapCoordinator(context: Context, private val preferences: BackTapPreferences) {
    private val sensors = context.getSystemService(SensorManager::class.java)
    private val power = context.getSystemService(PowerManager::class.java)
    private val accelerometer = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope = sensors.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    val supported = accelerometer != null && gyroscope != null
    private val main = Handler(Looper.getMainLooper())
    private val session = BackTapSession()
    private var owner: Binding? = null
    private var worker: HandlerThread? = null
    private var sensorHandler: Handler? = null
    private var listener: SensorEventListener? = null
    private var detector: BackTapDetector? = null
    private var resetWorker: (() -> Unit)? = null
    private var warmup: Runnable? = null
    private val strengths = mutableListOf<Double>()
    private val mutableTest = MutableStateFlow(BackTapTestState())
    val test = mutableTest.asStateFlow()
    private val mutableListening = MutableStateFlow(false)
    val listening = mutableListening.asStateFlow()

    fun bind(
        activity: ComponentActivity,
        context: () -> BackTapContext,
        available: () -> Boolean,
        perform: (BackTapAction) -> Boolean,
    ): Binding = Binding(activity, context, available, perform).also { binding ->
        activity.lifecycle.addObserver(binding)
        activity.lifecycleScope.launch {
            combine(
                preferences.enabled().changes(),
                preferences.sensitivity().changes(),
                preferences.calibration().changes(),
            ) {
                    _,
                    _,
                    _,
                ->
                Unit
            }.collect { if (owner === binding) refresh() }
        }
    }

    fun startTest(activity: ComponentActivity, calibrate: Boolean) {
        if (!supported || owner?.activity !== activity) return
        strengths.clear()
        mutableTest.value = BackTapTestState(active = true, calibrating = calibrate)
        refresh()
    }

    fun endTest(activity: ComponentActivity) {
        if (owner?.activity !== activity) return
        mutableTest.value = BackTapTestState()
        strengths.clear()
        refresh()
    }

    private fun stop() {
        warmup?.let(main::removeCallbacks)
        warmup = null
        session.renew()
        listener?.let(sensors::unregisterListener)
        listener = null
        detector = null
        resetWorker = null
        sensorHandler = null
        worker?.quitSafely()
        worker = null
        mutableListening.value = false
    }

    private fun unlocked(): Boolean = !Injekt.get<SecurityPreferences>().useAuthenticator().get() ||
        !SecureActivityDelegate.requireUnlock

    private fun prepareCalibration(binding: Binding): Long {
        warmup?.let(main::removeCallbacks)
        warmup = null
        if (!test.value.calibrating) return 350
        mutableTest.value = test.value.copy(warmingUp = true)
        warmup = Runnable {
            if (owner === binding && test.value.calibrating) {
                mutableTest.value = test.value.copy(warmingUp = false)
            }
            warmup = null
        }.also { main.postDelayed(it, 1000) }
        return 1000
    }

    private fun refresh() {
        stop()
        val binding = owner ?: return
        if (!supported ||
            !binding.foreground() ||
            binding.suspended ||
            !unlocked() ||
            (!preferences.enabled().get() && !test.value.active)
        ) {
            return
        }
        var token = session.renew()
        val thread = HandlerThread("NyanimeBackTap").apply { start() }
        val handler = Handler(thread.looper)
        val engine = BackTapDetector(
            if (test.value.calibrating) BackTapDetector.CALIBRATION_THRESHOLD else preferences.threshold(),
        )
        engine.reset(SystemClock.elapsedRealtimeNanos(), prepareCalibration(binding))
        val events = object : SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            override fun onSensorChanged(event: SensorEvent) {
                // Sensors and filtering share this worker: no lock, UI work or logging per sample.
                if (binding.touching) return
                val v = event.values
                if (v.size < 3) return
                if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
                    engine.gyroscope(event.timestamp, v[0].toDouble(), v[1].toDouble(), v[2].toDouble())
                } else {
                    engine.accelerometer(
                        event.timestamp,
                        v[0].toDouble(),
                        v[1].toDouble(),
                        v[2].toDouble(),
                    )?.let { gesture ->
                        val ticket = token
                        main.post { deliver(binding, ticket, gesture) }
                    }
                }
            }
        }
        worker = thread
        sensorHandler = handler
        detector = engine
        listener = events
        resetWorker = {
            val renewed = session.renew()
            val quietMillis = prepareCalibration(binding)
            handler.post {
                engine.reset(SystemClock.elapsedRealtimeNanos(), quietMillis)
                token = renewed
            }
        }
        val accelDelay = checkNotNull(accelerometer).minDelay
        val gyroDelay = checkNotNull(gyroscope).minDelay
        val period = BackTapSampling.periodMicros(accelDelay, gyroDelay)
        fun register(periodMicros: Int): Boolean = try {
            val accelRegistered = sensors.registerListener(events, accelerometer, periodMicros, handler)
            val gyroRegistered = sensors.registerListener(events, gyroscope, periodMicros, handler)
            accelRegistered && gyroRegistered
        } catch (_: RuntimeException) {
            // Vendor policy or disabled sensors must never crash playback or settings.
            false
        }
        var registered = register(period)
        if (!registered) {
            // Remove either half of a failed registration before trying the unrestricted rate.
            sensors.unregisterListener(events)
            val fallback = BackTapSampling.periodMicros(accelDelay, gyroDelay, highRate = false)
            if (fallback > period) registered = register(fallback)
        }
        if (!registered) stop() else mutableListening.value = true
    }

    private fun deliver(binding: Binding, token: Long, gesture: BackTapDetector.Gesture) {
        if (owner !== binding ||
            !binding.foreground() ||
            binding.touching ||
            binding.suspended ||
            test.value.warmingUp ||
            !unlocked() ||
            (!preferences.enabled().get() && !test.value.active) ||
            (!test.value.active && !binding.available()) ||
            !session.accept(token, gesture.timestampNanos, SystemClock.elapsedRealtimeNanos())
        ) {
            return
        }
        val accepted = if (test.value.active) {
            val state = test.value
            if (state.calibrating) {
                strengths.add(gesture.strength)
                mutableTest.value = state.copy(completed = strengths.size, detections = state.detections + 1)
                BackTapDetector.calibratedThreshold(strengths)?.let {
                    mutableTest.value = mutableTest.value.copy(calibrating = false, warmingUp = false)
                    preferences.calibration().set(it.toFloat())
                }
            } else {
                mutableTest.value = state.copy(detections = state.detections + 1)
            }
            true
        } else {
            val context = binding.context()
            val action = preferences.action(context).get().takeIf { it in context.actions }
                ?: context.default
            if (action == BackTapAction.QuickMenu) {
                showBackTapQuickMenu(binding.activity, binding.perform)
                true
            } else {
                binding.perform(action)
            }
        }
        if (accepted && preferences.haptic().get()) {
            // Block the vibration before asking the OS to emit it, including during calibration.
            val engine = detector
            sensorHandler?.post { engine?.suppress(SystemClock.elapsedRealtimeNanos(), 1000) }
            binding.activity.window.decorView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    inner class Binding internal constructor(
        internal val activity: ComponentActivity,
        internal val context: () -> BackTapContext,
        internal val available: () -> Boolean,
        internal val perform: (BackTapAction) -> Boolean,
    ) : DefaultLifecycleObserver {
        @Volatile internal var touching = false
        internal var suspended = false
        private var focused = false

        fun suspend(value: Boolean) {
            if (suspended == value) return
            suspended = value
            if (this@BackTapCoordinator.owner === this) refresh()
        }

        fun rearm() {
            if (this@BackTapCoordinator.owner === this) resetWorker?.invoke()
        }

        internal fun foreground() = focused &&
            activity.hasWindowFocus() &&
            power.isInteractive &&
            activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
            !activity.isInPictureInPictureMode &&
            !activity.isFinishing &&
            !activity.isDestroyed

        override fun onResume(owner: LifecycleOwner) {
            if (this@BackTapCoordinator.owner !== this) mutableTest.value = BackTapTestState()
            this@BackTapCoordinator.owner = this
            focused = activity.hasWindowFocus()
            refresh()
        }

        override fun onPause(owner: LifecycleOwner) {
            if (this@BackTapCoordinator.owner !== this) return
            stop()
            mutableTest.value = BackTapTestState()
            this@BackTapCoordinator.owner = null
            touching = false
        }

        override fun onDestroy(owner: LifecycleOwner) {
            onPause(owner)
            activity.lifecycle.removeObserver(this)
        }

        fun focusChanged(hasFocus: Boolean) {
            focused = hasFocus
            // A new window can consume the finger-up event after a long press opened it.
            touching = false
            if (this@BackTapCoordinator.owner === this) refresh()
        }

        fun touch(event: MotionEvent) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> touching = true
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> touching = false
                else -> return
            }
            if (this@BackTapCoordinator.owner !== this) return
            resetWorker?.invoke()
        }
    }
}
