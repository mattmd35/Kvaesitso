package de.mm20.launcher2.nightscreen

import de.mm20.launcher2.backup.Backupable
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

val nightScreenModule = module {
    single { NightScreenStore(androidContext()) }
    factory<Backupable>(named<NightScreenStore>()) { get<NightScreenStore>() }
    single { NightScreen(androidContext(), get()) }
}
