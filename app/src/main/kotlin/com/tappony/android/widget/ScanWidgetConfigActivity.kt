package com.tappony.android.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tappony.android.R
import com.tappony.android.TapPonyApp
import com.tappony.android.ui.TapColors
import com.tappony.android.ui.TapPonyTheme

/**
 * Shown when the widget is placed (and on Android 12 and later, when it is
 * reconfigured): pick the profile it scans with, or let it follow the active one.
 * Backing out leaves RESULT_CANCELED, so the launcher drops the widget.
 */
class ScanWidgetConfigActivity : AppCompatActivity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        widgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Only configure widgets that are really ours.
        val manager = AppWidgetManager.getInstance(this)
        val ours = widgetId != AppWidgetManager.INVALID_APPWIDGET_ID &&
            manager?.getAppWidgetInfo(widgetId)?.provider == ComponentName(this, ScanWidget::class.java)
        if (!ours) {
            finish()
            return
        }
        enableEdgeToEdge()
        val app = application as TapPonyApp
        setContent {
            TapPonyTheme {
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                    val profiles by app.profiles.profiles.collectAsState()
                    LazyColumn(
                        Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            Text(
                                stringResource(R.string.widget_config_title),
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                        item {
                            Choice(stringResource(R.string.widget_follow_active), stringResource(R.string.widget_follow_active_detail)) { pick(null) }
                        }
                        items(profiles, key = { it.id }) { p ->
                            Choice(p.name.ifBlank { stringResource(R.string.editor_untitled) }, null) { pick(p.id) }
                        }
                        if (profiles.isEmpty()) {
                            item { Text(stringResource(R.string.widget_no_profiles), color = TapColors.Muted, modifier = Modifier.padding(top = 8.dp)) }
                        }
                    }
                }
            }
        }
    }

    private fun pick(profileId: String?) {
        WidgetPrefs.set(this, widgetId, profileId)
        AppWidgetManager.getInstance(this)?.let { ScanWidget.render(this, it, widgetId) }
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }
}

@Composable
private fun Choice(title: String, detail: String?, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            detail?.let { Text(it, color = TapColors.Muted, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}
