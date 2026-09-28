package nyanime.privacy.display

import android.view.View
import java.lang.reflect.Method

data class PrivacyDisplayDevice(val manufacturer: String, val model: String, val sdk: Int) {
    val samsungPrivacyHardware: Boolean
        get() = sdk >= 36 && manufacturer.equals("samsung", ignoreCase = true) && model.startsWith("SM-S948")
}

/** Only API names/signatures are external technical facts. All coordination is our own. */
class SamsungPrivacyDisplayBackend private constructor(
    override val capability: PrivacyDisplayCapability,
    private val methods: SamsungPrivacyMethods?,
) : PrivacyDisplayBackend<View> {
    override fun apply(target: View, region: PrivacyRegion): Result<Unit> = samsungApiCall {
        val api = checkNotNull(methods)
        api.apply(target, region)
    }

    override fun clear(target: View): Result<Unit> = samsungApiCall { checkNotNull(methods).clear(target) }

    companion object {
        fun create(device: PrivacyDisplayDevice): SamsungPrivacyDisplayBackend {
            if (!device.samsungPrivacyHardware) {
                return SamsungPrivacyDisplayBackend(
                    PrivacyDisplayCapability.Unavailable(PrivacyUnavailableReason.HARDWARE),
                    null,
                )
            }
            val resolved = samsungApiCall { SamsungPrivacyMethods.resolve(View::class.java) }
            return SamsungPrivacyDisplayBackend(
                if (resolved.isSuccess) {
                    PrivacyDisplayCapability.Available
                } else {
                    PrivacyDisplayCapability.Unavailable(PrivacyUnavailableReason.FIRMWARE)
                },
                resolved.getOrNull(),
            )
        }
    }
}

/** Resolved once by the backend. No target or accessibility override is retained. */
internal class SamsungPrivacyMethods private constructor(
    private val enable: Method,
    private val position: Method,
    private val disable: Method,
) {
    fun apply(target: Any, region: PrivacyRegion) {
        enable.invoke(target, region.cornerRadiusPx)
        with(region.bounds) { position.invoke(target, left, top, right, bottom) }
    }

    fun clear(target: Any) {
        disable.invoke(target)
    }

    companion object {
        fun resolve(owner: Class<*>): SamsungPrivacyMethods = SamsungPrivacyMethods(
            owner.getMethod("semSetPrivacyDisplayView", Float::class.javaPrimitiveType),
            owner.getMethod(
                "semSetPrivacyDisplayViewPosition",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
            ),
            owner.getMethod("semDisablePrivacyDisplayView"),
        )
    }
}

internal inline fun <T> samsungApiCall(operation: () -> T): Result<T> = try {
    Result.success(operation())
} catch (error: Exception) {
    Result.failure(error)
} catch (error: LinkageError) {
    Result.failure(error)
}
