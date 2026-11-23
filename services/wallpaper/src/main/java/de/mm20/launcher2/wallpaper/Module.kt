package de.mm20.launcher2.wallpaper

import de.mm20.launcher2.backup.Backupable
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

val wallpaperModule = module {
    single { WallpaperCarouselStore(androidContext()) }
    factory<Backupable>(named<WallpaperCarouselStore>()) { get<WallpaperCarouselStore>() }
    single { WallpaperCarousel(androidContext(), get()) }
}
