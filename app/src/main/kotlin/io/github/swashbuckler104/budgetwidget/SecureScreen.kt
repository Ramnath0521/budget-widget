package io.github.swashbuckler104.budgetwidget

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import io.github.swashbuckler104.budgetwidget.core.datastore.SettingsStore
import kotlinx.coroutines.launch

/**
 * "Hide amounts in screenshots & recents" (audit F1): `FLAG_SECURE` is set before the first frame,
 * because the setting is on by default and loads asynchronously, then follows the setting. Call
 * from `onCreate` before `setContent`.
 */
internal fun ComponentActivity.followSecureScreenSetting(settings: SettingsStore) {
    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    lifecycleScope.launch {
        settings.secureScreen.collect { secure ->
            if (secure) {
                window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }
}
