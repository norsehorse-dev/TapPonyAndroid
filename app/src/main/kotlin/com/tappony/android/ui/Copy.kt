package com.tappony.android.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tappony.android.R

/**
 * Puts text on the clipboard. Android 13 and later show their own confirmation,
 * so the toast is only for 8 to 12. Reply and request bodies are flagged
 * sensitive so the system keeps them out of the clipboard preview.
 */
fun copyToClipboard(context: Context, text: String, sensitive: Boolean = false) {
    val cm = context.getSystemService(ClipboardManager::class.java) ?: return
    val clip = ClipData.newPlainText("TapPony", text)
    if (sensitive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    }
    // A body too big for the binder would throw; better to copy nothing than to crash.
    if (runCatching { cm.setPrimaryClip(clip) }.isFailure) return
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, context.getString(R.string.copied), Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun CopyButton(text: String, description: String, sensitive: Boolean = false) {
    val context = LocalContext.current
    IconButton(onClick = { copyToClipboard(context, text, sensitive) }, modifier = Modifier.size(36.dp)) {
        Icon(Icons.Filled.ContentCopy, contentDescription = description, tint = TapColors.Muted, modifier = Modifier.size(18.dp))
    }
}

/** A value with a copy button at the end of its row. The value gets the remaining width. */
@Composable
fun CopyableRow(text: String, description: String, sensitive: Boolean = false, content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        content()
        CopyButton(text, description, sensitive)
    }
}
