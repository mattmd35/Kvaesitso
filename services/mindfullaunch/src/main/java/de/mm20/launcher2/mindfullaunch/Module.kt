package de.mm20.launcher2.mindfullaunch

import de.mm20.launcher2.backup.Backupable
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

val mindfulLaunchModule = module {
    single { MindfulLaunchStore(androidContext()) }
    factory<Backupable>(named<MindfulLaunchStore>()) { get<MindfulLaunchStore>() }
    single { MindfulLaunch(get()) }
}
