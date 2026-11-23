package de.mm20.launcher2.ui.mindfullaunch

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import de.mm20.launcher2.mindfullaunch.MindfulLaunchData
import de.mm20.launcher2.ui.R

internal data class InsteadApp(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
)

internal data class InsteadContent(
    val suggestions: List<String>,
    val apps: List<InsteadApp>,
) {
    companion object {
        /**
         * @param exclude the app being opened, so it isn't suggested as its own alternative
         */
        fun load(context: Context, data: MindfulLaunchData?, exclude: String?): InsteadContent? {
            if (data == null || !data.insteadEnabled) return null
            val builtIn = context.resources.getStringArray(R.array.mindful_instead_defaults).toList()
            val suggestions = (data.insteadTasks + builtIn).shuffled()
            val pm = context.packageManager
            val apps = data.insteadApps.filter { it != exclude }.mapNotNull { pkg ->
                try {
                    val info = pm.getApplicationInfo(pkg, 0)
                    InsteadApp(
                        packageName = pkg,
                        label = pm.getApplicationLabel(info).toString(),
                        icon = pm.getApplicationIcon(info).toBitmap(144, 144).asImageBitmap(),
                    )
                } catch (e: Exception) {
                    null // uninstalled
                }
            }.take(4)
            return InsteadContent(suggestions, apps)
        }

        fun launch(context: Context, packageName: String): Boolean {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
            return try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            } catch (e: Exception) {
                false
            }
        }
    }
}

/**
 * "Instead, you could…" — a suggestion (tap for another one) and alternative apps.
 */
@Composable
internal fun InsteadSection(
    content: InsteadContent,
    onOpenApp: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var index by remember { mutableStateOf(0) }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
        modifier = modifier.fillMaxWidth(0.85f),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.mindful_instead_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            if (content.suggestions.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = content.suggestions[index % content.suggestions.size],
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .clickable { index++ }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            if (content.apps.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                    for (app in content.apps) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(72.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .clickable { onOpenApp(app.packageName) }
                                .padding(4.dp),
                        ) {
                            if (app.icon != null) {
                                Image(app.icon, contentDescription = null, modifier = Modifier.size(44.dp))
                            }
                            Text(
                                text = app.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
