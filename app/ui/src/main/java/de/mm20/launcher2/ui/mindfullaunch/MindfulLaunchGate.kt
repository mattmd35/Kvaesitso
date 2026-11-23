package de.mm20.launcher2.ui.mindfullaunch

import android.app.Activity
import android.content.Context
import android.content.Intent
import de.mm20.launcher2.mindfullaunch.MindfulLaunch
import de.mm20.launcher2.search.AppShortcut
import de.mm20.launcher2.search.Application
import de.mm20.launcher2.search.SavableSearchable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Called by every launch path. If the app is watched, the launch is replaced by the pause screen
 * ([MindfulLaunchActivity]), which opens the app afterwards if the user still wants to.
 */
object MindfulLaunchGate : KoinComponent {
    private val mindfulLaunch: MindfulLaunch by inject()

    /**
     * The searchable waiting behind the pause screen. Same process, so it is simply held here.
     */
    internal var pending: SavableSearchable? = null

    fun packageOf(searchable: SavableSearchable): String? = when (searchable) {
        is Application -> searchable.componentName.packageName
        is AppShortcut -> searchable.componentName?.packageName
        else -> null
    }

    /**
     * @return true if the launch was intercepted; the caller must not launch [searchable] itself.
     */
    fun intercept(context: Context, searchable: SavableSearchable): Boolean {
        val packageName = packageOf(searchable) ?: return false
        if (!mindfulLaunch.shouldPause(packageName)) return false
        pending = searchable
        val intent = Intent(context, MindfulLaunchActivity::class.java)
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }
}
