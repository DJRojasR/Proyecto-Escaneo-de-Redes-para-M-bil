package com.xyntra.wifiscanner.ui

import com.xyntra.wifiscanner.i18n.tr
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import com.xyntra.wifiscanner.ui.screens.DevicesScreen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import com.xyntra.wifiscanner.ui.components.LocalOpenPremium
import com.xyntra.wifiscanner.ui.screens.ExportScreen
import com.xyntra.wifiscanner.ar.ArHeatScreen
import com.xyntra.wifiscanner.ui.screens.HeatMapScreen
import com.xyntra.wifiscanner.ui.screens.LatencyScreen
import com.xyntra.wifiscanner.ui.screens.MonitorScreen
import com.xyntra.wifiscanner.ui.screens.NetworkDetailScreen
import com.xyntra.wifiscanner.ui.screens.NetworksScreen
import com.xyntra.wifiscanner.ui.screens.PremiumScreen
import com.xyntra.wifiscanner.ui.screens.SecurityScreen
import com.xyntra.wifiscanner.ui.screens.SpeedTestScreen
import com.xyntra.wifiscanner.ui.screens.ToolsActions
import androidx.compose.runtime.getValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xyntra.wifiscanner.ads.AdBanner
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.unit.dp
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
import com.xyntra.wifiscanner.XyntraApp
import com.xyntra.wifiscanner.ui.screens.DeviceDetailScreen
import com.xyntra.wifiscanner.ui.screens.DiagnosticsScreen
import com.xyntra.wifiscanner.ui.screens.WifiSecurityScreen
import com.xyntra.wifiscanner.ui.screens.WifiQrScreen
import com.xyntra.wifiscanner.ui.screens.HistoryScreen
import com.xyntra.wifiscanner.ui.screens.HomeScreen
import com.xyntra.wifiscanner.ui.screens.IpManagerScreen
import com.xyntra.wifiscanner.ui.screens.LearnScreen
import com.xyntra.wifiscanner.ui.screens.ManualScanScreen
import com.xyntra.wifiscanner.ui.screens.SettingsScreen
import com.xyntra.wifiscanner.ui.screens.ToolsScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

/**
 * Navegación principal: cuatro secciones con un propósito claro cada una.
 * - Inicio: el estado de la red y el escaneo.
 * - Dispositivos: la lista completa (con búsqueda, filtros y el IP Manager).
 * - Historial: qué pasó en la red.
 * - Herramientas: diagnóstico, velocidad, seguridad… y Ajustes.
 */
private val tabs = listOf(
    Tab("red", tr("Inicio"), Icons.Outlined.Home, Icons.Filled.Home),
    Tab("dispositivos", tr("Dispositivos"), Icons.Outlined.Devices, Icons.Filled.Devices),
    Tab("historial", tr("Historial"), Icons.Outlined.History, Icons.Filled.History),
    Tab("herramientas", tr("Herramientas"), Icons.Outlined.Handyman, Icons.Filled.Handyman),
)

/**
 * Rutas que `MainActivity` acepta de un Intent externo (notificación, widget, u otra
 * app). `MainActivity` es `exported` porque es el LAUNCHER, así que esta whitelist es
 * la única defensa contra que otra app navegue a una pantalla no prevista.
 */
object RouteWhitelist {
    private val devicePattern = Regex("dispositivo/\\d{1,12}")
    private const val MAX_LENGTH = 32

    fun isAllowed(route: String?): Boolean {
        if (route == null || route.length > MAX_LENGTH) return false
        return route == "red" || route == "premium" || devicePattern.matches(route)
    }
}

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

@Composable
fun XyntraRoot(openRoute: String? = null, onRouteOpened: () -> Unit = {}) {
    val nav = rememberNavController()
    val mainVm: MainViewModel = viewModel()
    val toolsVm: ToolsViewModel = viewModel()
    val context = LocalContext.current
    val app = context.applicationContext as XyntraApp
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
    // Cambiar de pestaña conservando el estado de cada una
    val openTab: (String) -> Unit = { target ->
        nav.navigate(target) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // Abrir una pantalla pedida desde una notificación o el widget
    LaunchedEffect(openRoute) {
        // Cualquier app puede abrir la pantalla principal con datos extra: solo aceptamos
        // las rutas que usan nuestras notificaciones y el widget, y nunca dejamos que falle.
        val allowed = RouteWhitelist.isAllowed(openRoute)
        if (openRoute != null && !allowed) onRouteOpened()
        if (openRoute != null && allowed) {
            if (openRoute == "red") {
                nav.popBackStack(nav.graph.findStartDestination().id, inclusive = false)
                mainVm.refreshNetwork()
            } else {
                runCatching { nav.navigate(openRoute) { launchSingleTop = true } }
            }
            onRouteOpened()
        }
    }

    // ---------- Anuncios: mini banner (solo en Inicio, Dispositivos e Historial) y aviso discreto de Premium
    val adsReady by app.ads.ready.collectAsStateWithLifecycle()
    val premiumState by app.premium.state.collectAsStateWithLifecycle()
    val showBanner = showBar && adsReady && !premiumState.isPremium && app.ads.bannerAllowed(route)
    val rootSnackbar = remember { SnackbarHostState() }
    val premiumHint by app.ads.premiumHint.collectAsStateWithLifecycle()
    LaunchedEffect(premiumHint) {
        if (premiumHint) {
            app.ads.dismissPremiumHint()
            val result = rootSnackbar.showSnackbar(
                message = tr("¿Prefieres sin anuncios? Prueba XYNTRA Premium."),
                actionLabel = tr("Ver Premium"),
                withDismissAction = true,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) openPremium()
        }
    }

    var showSplash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1400)
        showSplash = false
    }
    Box(Modifier.fillMaxSize()) {
    CompositionLocalProvider(LocalOpenPremium provides openPremium) {
    Scaffold(
        snackbarHost = { SnackbarHost(rootSnackbar) },
        bottomBar = {
            if (showBar) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    if (showBanner) {
                        // Mini banner sobre la barra, con un pequeño margen para que nadie
                        // lo toque sin querer al cambiar de pestaña.
                        Column(Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)) {
                            AdBanner(Modifier.padding(top = 4.dp))
                            Spacer(Modifier.height(6.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        }
                    }
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        tonalElevation = 0.dp,
                    ) {
                        tabs.forEach { t ->
                            val selected = route == t.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { openTab(t.route) },
                                icon = { Icon(if (selected) t.selectedIcon else t.icon, contentDescription = null) },
                                label = { Text(t.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = "red",
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
            // Transiciones suaves y cortas: fundido entre pestañas y un leve desplazamiento
            // al entrar a una pantalla de detalle.
            enterTransition = { fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 14 } },
            exitTransition = { fadeOut(tween(140)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = { fadeOut(tween(140)) + slideOutHorizontally(tween(220)) { it / 14 } },
        ) {
            composable("red") {
                HomeScreen(
                    vm = mainVm,
                    onScan = startNetworkScan,
                    onOpenDevice = { id -> nav.navigate("dispositivo/$id") },
                    onLearn = { nav.navigate("aprende") },
                    onInspectIp = { nav.navigate("manual?ip=") },
                    onWifiSecurity = { nav.navigate("seguridad_wifi") },
                    onOpenDevices = { openTab("dispositivos") },
                    onSettings = { nav.navigate("ajustes") },
                )
            }
            composable("dispositivos") {
                DevicesScreen(
                    vm = mainVm,
                    onScan = startNetworkScan,
                    onOpenDevice = { id -> nav.navigate("dispositivo/$id") },
                    onInspectIp = { nav.navigate("manual?ip=") },
                    onOpenIps = { nav.navigate("ips") { launchSingleTop = true } },
                )
            }
            composable("ips") {
                IpManagerScreen(
                    vm = mainVm,
                    onScan = startNetworkScan,
                    onOpenDevice = { id -> nav.navigate("dispositivo/$id") },
                    onInspectIp = { ip -> nav.navigate("manual?ip=$ip") },
                    onBack = { nav.popBackStack() },
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
                        onWifiSecurity = { nav.navigate("seguridad_wifi") },
                        onWifiQr = { nav.navigate("wifi_qr") },
                        onLearn = { nav.navigate("aprende") },
                        onSettings = { nav.navigate("ajustes") },
                        onPremium = openPremium,
                        onMonitor = { nav.navigate("vigilancia") },
                        onSecurity = { nav.navigate("seguridad") },
                        onLatency = { nav.navigate("latencia") },
                        onNetworks = { nav.navigate("redes") },
                        onExport = { nav.navigate("exportar") },
                        onSpeed = { nav.navigate("velocidad") },
                        onHeatMap = { nav.navigate("mapa_calor") },
                        onIpManager = { nav.navigate("ips") { launchSingleTop = true } },
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
            composable("mapa_calor") {
                HeatMapScreen(
                    vm = viewModel(),
                    onOpenAr = { floorId, x, y -> nav.navigate("mapa_calor_ar/$floorId/$x/$y") },
                    onBack = { nav.popBackStack() },
                )
            }
            composable("mapa_calor_ar/{floor}/{x}/{y}") { entry ->
                val args = entry.arguments
                ArHeatScreen(
                    vm = viewModel(),
                    floorId = args?.getString("floor")?.toLongOrNull() ?: -1L,
                    startX = args?.getString("x")?.toFloatOrNull() ?: 0.5f,
                    startY = args?.getString("y")?.toFloatOrNull() ?: 0.5f,
                    onDone = { nav.popBackStack() },
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
            composable("wifi_qr") {
                WifiQrScreen(onBack = { nav.popBackStack() })
            }
            composable("seguridad_wifi") {
                WifiSecurityScreen(vm = toolsVm, onBack = { nav.popBackStack() })
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
                    onLearn = { nav.navigate("aprende") },
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }
    }
    AppSplash(visible = showSplash)

    // "Pago confirmado": solo tras una compra nueva confirmada por Google Play
    val celebrate by app.premium.celebrate.collectAsStateWithLifecycle()
    androidx.compose.animation.AnimatedVisibility(
        visible = celebrate != null,
        enter = fadeIn(tween(300)),
        exit = fadeOut(tween(400)),
    ) {
        // Recordamos el plan para que no desaparezca durante el fundido de salida
        val plan = remember { celebrate } ?: return@AnimatedVisibility
        com.xyntra.wifiscanner.ui.components.PurchaseCelebration(plan) {
            app.premium.celebrationShown()
            openTab("herramientas")
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
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = snackbarHost,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = if (onBack == null) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Atrás"))
                        }
                    }
                },
                actions = { actions() },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        },
        content = content,
    )
}

/** Pantalla de bienvenida breve: el logo con ondas de "señal" que se expanden. */
@Composable
private fun AppSplash(visible: Boolean) {
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(tween(450)),
    ) {
        val cs = MaterialTheme.colorScheme
        Box(
            Modifier.fillMaxSize().background(cs.background),
            contentAlignment = Alignment.Center,
        ) {
            val scale = remember { androidx.compose.animation.core.Animatable(0.82f) }
            LaunchedEffect(Unit) {
                scale.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = 260f))
            }
            // Ondas que salen del logo, como una señal que se propaga
            val ringColor = cs.primary
            val pulse = androidx.compose.animation.core.rememberInfiniteTransition(label = "radar")
            val wave by pulse.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    animation = tween(2200, easing = androidx.compose.animation.core.LinearEasing),
                    repeatMode = androidx.compose.animation.core.RepeatMode.Restart,
                ),
                label = "wave",
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                        val maxR = size.minDimension / 2f
                        for (i in 0..2) {
                            val prog = ((wave + i / 3f) % 1f)
                            drawCircle(
                                color = ringColor.copy(alpha = (1f - prog) * 0.30f),
                                radius = maxR * (0.35f + 0.65f * prog),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()),
                            )
                        }
                    }
                    // El ícono de la app: la "X" con su órbita sobre azul noche
                    Box(
                        Modifier
                            .size(96.dp)
                            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
                            .background(androidx.compose.ui.graphics.Color(0xFF000518), androidx.compose.foundation.shape.RoundedCornerShape(28.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(com.xyntra.wifiscanner.R.drawable.brand_mark),
                            contentDescription = null,
                            modifier = Modifier.size(width = 64.dp, height = 49.dp),
                        )
                    }
                }
                Text(
                    "XYNTRA",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        letterSpacing = androidx.compose.ui.unit.TextUnit(6f, androidx.compose.ui.unit.TextUnitType.Sp),
                    ),
                    color = cs.onBackground,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    tr("Mira quién está en tu WiFi"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                )
            }
        }
    }
}
