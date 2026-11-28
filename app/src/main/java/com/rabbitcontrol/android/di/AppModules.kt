package com.rabbitcontrol.android.di

import com.rabbitcontrol.android.core.data.AccountRepository
import com.rabbitcontrol.android.core.data.AppLockStore
import com.rabbitcontrol.android.core.data.InteractionPrefsStore
import com.rabbitcontrol.android.core.data.PortMapRuleStore
import com.rabbitcontrol.android.core.data.ProxyConfigStore
import com.rabbitcontrol.android.core.data.SessionLayoutStore
import com.rabbitcontrol.android.core.data.TargetHistoryStore
import com.rabbitcontrol.android.core.data.TerminalPasswordStore
import com.rabbitcontrol.android.core.data.ToolOrderStore
import com.rabbitcontrol.android.core.data.WallpaperCache
import com.rabbitcontrol.android.core.host.HostManager
import com.rabbitcontrol.android.core.session.ClipboardSyncBridge
import com.rabbitcontrol.android.core.session.SessionManager
import com.rabbitcontrol.android.core.update.AppUpdateManager
import com.rabbitcontrol.android.core.update.CoreUpdateManager
import com.rabbitcontrol.android.ui.screens.auth.AuthViewModel
import com.rabbitcontrol.android.ui.screens.detail.DeviceDetailViewModel
import com.rabbitcontrol.android.ui.screens.home.HomeViewModel
import com.rabbitcontrol.android.ui.screens.tools.FileTransferViewModel
import com.rabbitcontrol.android.ui.screens.tools.PortMapViewModel
import com.rabbitcontrol.android.ui.screens.tools.TerminalViewModel
import com.rabbitcontrol.android.ui.session.SessionViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val dataModule = module {
    single { InteractionPrefsStore(androidContext()) }
    single { AccountRepository(androidContext(), get()) }
    single { TargetHistoryStore(androidContext()) }
    single { ToolOrderStore(androidContext()) }
    single { WallpaperCache(androidContext()) }
    single { ProxyConfigStore(androidContext()) }
    single { SessionLayoutStore(androidContext()) }
    single { AppLockStore(androidContext()) }
    single { TerminalPasswordStore(androidContext()) }
    single { PortMapRuleStore(androidContext()) }
    single { AppUpdateManager(androidContext()) }
    single { CoreUpdateManager(androidContext()) }
}

val sessionModule = module {
    single { ClipboardSyncBridge(androidContext()) }
    single { SessionManager(androidContext(), get(), get(), get(), get(), get(), get()) }
    single { HostManager(androidContext(), get(), get()) }
}

val viewModelModule = module {
    viewModel { AuthViewModel(get()) }
    viewModel { HomeViewModel(get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { DeviceDetailViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { SessionViewModel(get(), get(), get(), get()) }
    viewModel { FileTransferViewModel(androidContext(), get()) }
    viewModel { TerminalViewModel(get(), get(), get()) }
    viewModel { PortMapViewModel(get(), get()) }
}
