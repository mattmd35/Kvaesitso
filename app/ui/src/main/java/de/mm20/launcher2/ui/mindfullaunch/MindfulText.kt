package de.mm20.launcher2.ui.mindfullaunch

import android.content.Context
import de.mm20.launcher2.ui.R

internal object MindfulText {
    fun phrase(context: Context, spicy: Boolean): String {
        val phrases = context.resources.getStringArray(
            if (spicy) R.array.mindful_phrases_spicy else R.array.mindful_phrases
        )
        return phrases.random()
    }

    /**
     * "45 s", "12 min", "1 h 5 min"
     */
    fun duration(millis: Long): String {
        val totalMinutes = millis / 60_000
        return when {
            millis < 60_000 -> "${millis / 1000} s"
            totalMinutes < 60 -> "$totalMinutes min"
            totalMinutes % 60 == 0L -> "${totalMinutes / 60} h"
            else -> "${totalMinutes / 60} h ${totalMinutes % 60} min"
        }
    }
}
