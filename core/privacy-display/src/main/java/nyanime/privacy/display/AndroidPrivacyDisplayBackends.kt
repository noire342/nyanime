package nyanime.privacy.display

import android.os.Build
import android.view.View

/** Adapter composition point. Adding a manufacturer does not change application screens or policy. */
object AndroidPrivacyDisplayBackends {
    fun create(
        device: PrivacyDisplayDevice = PrivacyDisplayDevice(Build.MANUFACTURER, Build.MODEL, Build.VERSION.SDK_INT),
    ): PrivacyDisplayBackend<View> = SamsungPrivacyDisplayBackend.create(device)
}
