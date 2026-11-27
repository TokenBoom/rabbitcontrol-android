package io.rabbitcontrol.android.di

import io.rabbitcontrol.android.core.data.AccountRepository
import io.rabbitcontrol.android.core.data.InteractionPrefsStore
import io.rabbitcontrol.android.core.data.ProxyConfigStore
import io.rabbitcontrol.android.core.data.SessionLayoutStore
import io.rabbitcontrol.android.core.data.TargetHistoryStore
import io.rabbitcontrol.android.core.data.ToolOrderStore
import io.rabbitcontrol.android.core.data.WallpaperCache
import io.rabbitcontrol.android.core.host.HostManager
import io.rabbitcontrol.android.core.session.ClipboardSyncBridge
import io.rabbitcontrol.android.core.session.SessionManager
import io.rabbitcontrol.android.core.update.AppUpdateManager
import io.rabbitcontrol.android.core.update.CoreUpdateManager
import io.rabbitcontrol.android.ui.screens.auth.AuthViewModel
import io.rabbitcontrol.android.ui.screens.detail.DeviceDetailViewModel
import io.rabbitcontrol.android.ui.screens.home.HomeViewModel
import io.rabbitcontrol.android.ui.screens.tools.FileTransferViewModel
import io.rabbitcontrol.android.ui.screens.tools.PortMapViewModel
import io.rabbitcontrol.android.ui.screens.tools.TerminalViewModel
import io.rabbitcontrol.android.ui.session.SessionViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val dataModule = module {
    single { AccountRepository(androidContext()) }
    single { TargetHistoryStore(androidContext()) }
    single { ToolOrderStore(androidContext()) }
    single { WallpaperCache(androidContext()) }
    single { InteractionPrefsStore(androidContext()) }
    single { ProxyConfigStore(androidContext()) }
    single { SessionLayoutStore(androidContext()) }
    single { AppUpdateManager(androidContext()) }
    single { CoreUpdateManager(androidContext()) }
}

val sessionModule = module {
    single { ClipboardSyncBridge(androidContext()) }
    single { SessionManager(androidContext(), get(), get(), get(), get()) }
    single { HostManager(androidContext(), get(), get()) }
}

val viewModelModule = module {
    viewModel { AuthViewModel(get()) }
    viewModel { HomeViewModel(get(), get(), get(), get(), get(), get(), get()) }
    viewModel { DeviceDetailViewModel(get(), get(), get(), get(), get()) }
    viewModel { SessionViewModel(get(), get(), get(), get()) }
    viewModel { FileTransferViewModel(androidContext(), get()) }
    viewModel { TerminalViewModel(get()) }
    viewModel { PortMapViewModel(get()) }
}
