package com.lupadered.app.ui

import com.lupadered.app.i18n.tr
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import com.lupadered.app.ui.components.LocalOpenPremium
import com.lupadered.app.ui.screens.ExportScreen
import com.lupadered.app.ui.screens.LatencyScreen
import com.lupadered.app.ui.screens.MonitorScreen
import com.lupadered.app.ui.screens.NetworkDetailScreen
import com.lupadered.app.ui.screens.NetworksScreen
import com.lupadered.app.ui.screens.PremiumScreen
import com.lupadered.app.ui.screens.SecurityScreen
import com.lupadered.app.ui.screens.SpeedTestScreen
import com.lupadered.app.ui.screens.ToolsActions
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lupadered.app.LupaApp
import com.lupadered.app.ui.screens.DeviceDetailScreen
import com.lupadered.app.ui.screens.DiagnosticsScreen
import com.lupadered.app.ui.screens.HistoryScreen
import com.lupadered.app.ui.screens.HomeScreen
import com.lupadered.app.ui.screens.IpManagerScreen
import com.lupadered.app.ui.screens.LearnScreen
import com.lupadered.app.ui.screens.ManualScanScreen
import com.lupadered.app.ui.screens.SettingsScreen
import com.lupadered.app.ui.screens.ToolsScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("red", tr("Red"), Icons.Outlined.Wifi),
    Tab("ips", "IPs", Icons.Outlined.Lan),
    Tab("historial", tr("Historial"), Icons.Outlined.History),
    Tab("herramientas", tr("Herramientas"), Icons.Outlined.Handyman),
)

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

@Composable
fun LupaRoot(openRoute: String? = null, onRouteOpened: () -> Unit = {}) {
    val nav = rememberNavController()
    val mainVm: MainViewModel = viewModel()
    val toolsVm: ToolsViewModel = viewModel()
    val context = LocalContext.current
    val app = context.applicationContext as LupaApp
    val activity = context.findActivity()

    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBar = tabs.any { it.route == route }

    // Antes de cualquier escaneo: anuncio solo si toca (y el escaneo corre detrás)
    val startNetworkScan = {
        activity?.let { app.ads.maybeShowOnScanStart(it) }
        mainVm.startScan()
    }
    val openPremium: () -> Unit = { nav.navigate("premium") { launchSingleTop = true } }

    // Abrir una pantalla pedida desde una notificación o el widget
    LaunchedEffect(openRoute) {
        if (openRoute != null) {
            if (openRoute == "red") {
                nav.popBackStack(nav.graph.findStartDestination().id, inclusive = false)
                mainVm.refreshNetwork()
            } else {
                nav.navigate(openRoute) { launchSingleTop = true }
            }
            onRouteOpened()
        }
    }

    CompositionLocalProvider(LocalOpenPremium provides openPremium) {
    Scaffold(
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    tabs.forEach { t ->
                        NavigationBarItem(
                            selected = route == t.route,
                            onClick = {
                                nav.navigate(t.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = { Text(t.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = "red",
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
        ) {
            composable("red") {
                HomeScreen(
                    vm = mainVm,
                    onScan = startNetworkScan,
                    onOpenDevice = { id -> nav.navigate("dispositivo/$id") },
                    onLearn = { nav.navigate("aprende") },
                )
            }
            composable("ips") {
                IpManagerScreen(
                    vm = mainVm,
                    onScan = startNetworkScan,
                    onOpenDevice = { id -> nav.navigate("dispositivo/$id") },
                    onInspectIp = { ip -> nav.navigate("manual?ip=$ip") },
                )
            }
            composable("historial") {
                HistoryScreen(vm = mainVm, onOpenDevice = { id -> nav.navigate("dispositivo/$id") })
            }
            composable("herramientas") {
                ToolsScreen(
                    ToolsActions(
                        onManual = { nav.navigate("manual?ip=") },
                        onDiagnostics = { nav.navigate("diagnostico") },
                        onLearn = { nav.navigate("aprende") },
                        onSettings = { nav.navigate("ajustes") },
                        onPremium = openPremium,
                        onMonitor = { nav.navigate("vigilancia") },
                        onSecurity = { nav.navigate("seguridad") },
                        onLatency = { nav.navigate("latencia") },
                        onNetworks = { nav.navigate("redes") },
                        onExport = { nav.navigate("exportar") },
                        onSpeed = { nav.navigate("velocidad") },
                    )
                )
            }
            composable("premium") {
                PremiumScreen(premium = app.premium, activity = activity, onBack = { nav.popBackStack() })
            }
            composable("vigilancia") {
                MonitorScreen(onBack = { nav.popBackStack() })
            }
            composable("seguridad") {
                SecurityScreen(
                    vm = viewModel(),
                    onOpenDevice = { id -> nav.navigate("dispositivo/$id") },
                    onBack = { nav.popBackStack() },
                )
            }
            composable("latencia") {
                LatencyScreen(vm = viewModel(), onBack = { nav.popBackStack() })
            }
            composable("redes") { entry ->
                val vm: NetworksViewModel = viewModel(entry)
                NetworksScreen(
                    vm = vm,
                    onOpenNetwork = { key -> nav.navigate("red-guardada/" + Uri.encode(key)) },
                    onScanCurrent = {
                        nav.popBackStack(nav.graph.findStartDestination().id, inclusive = false)
                        startNetworkScan()
                    },
                    onBack = { nav.popBackStack() },
                )
            }
            composable(
                "red-guardada/{key}",
                arguments = listOf(navArgument("key") { type = NavType.StringType }),
            ) { entry ->
                val key = entry.arguments?.getString("key").orEmpty()
                NetworkDetailScreen(
                    vm = viewModel(),
                    key = key,
                    onOpenDevice = { id -> nav.navigate("dispositivo/$id") },
                    onBack = { nav.popBackStack() },
                )
            }
            composable("velocidad") {
                SpeedTestScreen(vm = viewModel(), onBack = { nav.popBackStack() })
            }
            composable("exportar") {
                ExportScreen(vm = viewModel(), onBack = { nav.popBackStack() })
            }
            composable(
                "dispositivo/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) {
                DeviceDetailScreen(
                    vm = viewModel(),
                    onBack = {
                        nav.popBackStack()
                    },
                    onChanged = { mainVm.refreshNetwork() },
                )
            }
            composable(
                "manual?ip={ip}",
                arguments = listOf(navArgument("ip") { type = NavType.StringType; defaultValue = "" }),
            ) { entry ->
                ManualScanScreen(
                    vm = toolsVm,
                    initialIp = entry.arguments?.getString("ip").orEmpty(),
                    onBeforeScan = { activity?.let { app.ads.maybeShowOnScanStart(it) } },
                    onBack = { nav.popBackStack() },
                )
            }
            composable("diagnostico") {
                DiagnosticsScreen(vm = toolsVm, onBack = { nav.popBackStack() })
            }
            composable("aprende") {
                LearnScreen(onBack = { nav.popBackStack() })
            }
            composable("ajustes") {
                SettingsScreen(
                    vm = mainVm,
                    ads = app.ads,
                    premium = app.premium,
                    settings = app.settings,
                    activity = activity,
                    onPremium = openPremium,
                    onMonitor = { nav.navigate("vigilancia") },
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }
    }
}

/** Estructura común de cada pantalla: barra superior con título y "atrás" opcional. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Atrás"))
                        }
                    }
                },
                actions = { actions() },
            )
        },
        content = content,
    )
}
