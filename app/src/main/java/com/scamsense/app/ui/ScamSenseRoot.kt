package com.scamsense.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.scamsense.app.MainViewModel
import com.scamsense.core.engine.CheckType
import com.scamsense.app.ui.components.glowBackdrop
import com.scamsense.app.ui.theme.Brand
import com.scamsense.app.ui.screens.CallGuardScreen
import com.scamsense.app.ui.screens.CallLogScreen
import com.scamsense.app.ui.screens.CheckScreen
import com.scamsense.app.ui.screens.HistoryScreen
import com.scamsense.app.ui.screens.HomeScreen
import com.scamsense.app.ui.screens.LoadingScreen
import com.scamsense.app.ui.screens.ResultScreen
import com.scamsense.app.ui.screens.SettingsScreen
import com.scamsense.app.ui.screens.TransactionScreen
import com.scamsense.app.util.Intents
import kotlinx.coroutines.launch

object Routes {
    const val HOME = "home"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val CHECK = "check/{type}?voice={voice}"
    const val TRANSACTION = "transaction"
    const val RESULT = "result"
    const val CALL_GUARD = "callguard"
    const val CALL_LOG = "calllog"
    fun check(type: CheckType, voice: Boolean = false) = "check/${type.name}?voice=$voice"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "หน้าหลัก", Icons.Rounded.Home),
    Tab(Routes.HISTORY, "ประวัติ", Icons.Rounded.History),
    Tab(Routes.SETTINGS, "ตั้งค่า", Icons.Rounded.Settings),
)

@Composable
fun ScamSenseRoot(vm: MainViewModel) {
    val startup by vm.startup.collectAsStateWithLifecycle()
    AnimatedContent(
        targetState = startup.ready,
        transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(300)) },
        label = "root",
    ) { ready ->
        if (ready) AppNavigation(vm) else LoadingScreen(startup)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppNavigation(vm: MainViewModel, nav: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val analyzing by vm.analyzing.collectAsStateWithLifecycle()
    val inputError by vm.inputError.collectAsStateWithLifecycle()
    val result by vm.currentResult.collectAsStateWithLifecycle()
    val shared by vm.sharedText.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var sharedForCheck by remember { mutableStateOf<String?>(null) }
    val assistantCalls by vm.assistantCalls.collectAsStateWithLifecycle()
    val assistantBusy by vm.assistantBusy.collectAsStateWithLifecycle()
    val assistantMessage by vm.assistantMessage.collectAsStateWithLifecycle()
    val modelDownload by vm.modelDownload.collectAsStateWithLifecycle()

    fun toast(msg: String) { scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(msg) } }

    LaunchedEffect(Unit) {
        vm.showResult.collect {
            nav.navigate(Routes.RESULT) {
                // Replace the input screen so "back" from a result goes home, not to a stale form.
                popUpTo(Routes.HOME)
                launchSingleTop = true
            }
        }
    }
    LaunchedEffect(Unit) {
        vm.voiceCheckRequests.collect {
            sharedForCheck = null
            nav.navigate(Routes.check(CheckType.MESSAGE, voice = true)) { popUpTo(Routes.HOME); launchSingleTop = true }
        }
    }
    LaunchedEffect(assistantMessage) {
        assistantMessage?.let { toast(it); vm.clearAssistantMessage() }
    }
    // Text shared from another app ("ตรวจด้วย ScamSense"): check it immediately.
    LaunchedEffect(shared) {
        val s = shared ?: return@LaunchedEffect
        sharedForCheck = s.text
        nav.navigate(Routes.check(CheckType.MESSAGE)) { popUpTo(Routes.HOME); launchSingleTop = true }
        vm.analyze(CheckType.MESSAGE, s.text)
        vm.consumeSharedText()
    }

    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val isTab = tabs.any { it.route == route }

    Scaffold(
        bottomBar = {
            if (isTab) {
                FloatingTabBar(
                    tabs = tabs.map { Triple(it.route, it.label, it.icon) },
                    selected = route,
                    onSelect = { r ->
                        nav.navigate(r) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(if (isTab) padding else androidx.compose.foundation.layout.PaddingValues()),
            // Shared-axis style: the new page slides in a little and fades, the old one recedes.
            enterTransition = { fadeIn(tween(300, delayMillis = 60)) + slideInHorizontally(tween(380, easing = com.scamsense.app.ui.components.EmphasizedEasing)) { it / 10 } },
            exitTransition = { fadeOut(tween(160)) + slideOutHorizontally(tween(380, easing = com.scamsense.app.ui.components.EmphasizedEasing)) { -it / 20 } },
            popEnterTransition = { fadeIn(tween(300, delayMillis = 60)) + slideInHorizontally(tween(380, easing = com.scamsense.app.ui.components.EmphasizedEasing)) { -it / 20 } },
            popExitTransition = { fadeOut(tween(160)) + slideOutHorizontally(tween(380, easing = com.scamsense.app.ui.components.EmphasizedEasing)) { it / 10 } },
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    settings = settings,
                    history = history,
                    onCheck = { type, voice ->
                        vm.clearInputError()
                        sharedForCheck = null
                        nav.navigate(Routes.check(type, voice))
                    },
                    onTransaction = { nav.navigate(Routes.TRANSACTION) },
                    onCallHotline = { if (!Intents.dial(context, Intents.AOC_HOTLINE)) toast("เครื่องนี้โทรออกไม่ได้ กรุณาโทร 1441 จากโทรศัพท์") },
                    onCallCaregiver = { if (!Intents.dial(context, settings.caregiverPhone)) toast("ไม่พบแอปโทรศัพท์") },
                    onOpenSettings = { nav.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                    onOpenCallGuard = { nav.navigate(Routes.CALL_GUARD) },
                    onOpenHistory = {
                        nav.navigate(Routes.HISTORY) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onOpenResult = { vm.openHistoryItem(it) },
                )
            }
            composable(Routes.HISTORY) {
                HistoryScreen(
                    items = history,
                    saveHistory = settings.saveHistory,
                    onOpen = { vm.openHistoryItem(it) },
                    onClearAll = { vm.clearHistory(); toast("ลบประวัติแล้ว") },
                    onStartCheck = {
                        nav.navigate(Routes.HOME) {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    settings = settings, onUpdate = vm::updateSettings, onSaved = ::toast,
                    onOpenCallGuard = { nav.navigate(Routes.CALL_GUARD) },
                )
            }
            composable(Routes.CALL_GUARD) {
                CallGuardScreen(
                    settings = settings,
                    assistantBusy = assistantBusy,
                    onUpdate = vm::updateSettings,
                    onPair = vm::pairAssistant,
                    onUnpair = { vm.unpairAssistant(); toast("ยกเลิกการจับคู่แล้ว อย่าลืมยกเลิกการโอนสายในซิมด้วย") },
                    onDialCode = { if (!Intents.dialCode(context, it)) toast("เปิดหน้าโทรศัพท์ไม่ได้") },
                    onOpenCallLog = { nav.navigate(Routes.CALL_LOG) },
                    onBack = { nav.popBackStack() },
                    modelDownload = modelDownload,
                    onDownloadModel = vm::downloadModel,
                )
            }
            composable(Routes.CALL_LOG) {
                CallLogScreen(
                    calls = assistantCalls,
                    busy = assistantBusy,
                    onRefresh = vm::refreshAssistantCalls,
                    onDeleteAll = vm::deleteAssistantCalls,
                    onBack = { nav.popBackStack() },
                )
            }
            composable(
                Routes.CHECK,
                arguments = listOf(
                    navArgument("type") { type = NavType.StringType },
                    navArgument("voice") { type = NavType.BoolType; defaultValue = false },
                ),
            ) { entry ->
                val type = CheckType.entries.firstOrNull { it.name == entry.arguments?.getString("type") } ?: CheckType.MESSAGE
                CheckScreen(
                    type = type,
                    autoVoice = entry.arguments?.getBoolean("voice") == true,
                    initialText = sharedForCheck,
                    analyzing = analyzing,
                    error = inputError,
                    onAnalyze = { vm.analyze(type, it) },
                    onClearError = vm::clearInputError,
                    onBack = { nav.popBackStack() },
                )
            }
            composable(Routes.TRANSACTION) {
                TransactionScreen(analyzing = analyzing, onAnalyze = vm::analyzeTransaction, onBack = { nav.popBackStack() })
            }
            composable(Routes.RESULT) {
                val r = result
                if (r == null) {
                    LaunchedEffect(Unit) { nav.popBackStack(Routes.HOME, inclusive = false) }
                    return@composable
                }
                ResultScreen(
                    result = r,
                    settings = settings,
                    onBack = { nav.popBackStack() },
                    onDone = { nav.popBackStack(Routes.HOME, inclusive = false) },
                    onNotifyCaregiver = {
                        val ok = Intents.smsCaregiver(context, settings.caregiverPhone, Intents.buildReport(r, settings.caregiverName))
                        if (!ok) toast("ไม่พบแอปส่งข้อความในเครื่องนี้")
                    },
                    onSetupCaregiver = { nav.navigate(Routes.SETTINGS) { popUpTo(Routes.HOME); launchSingleTop = true } },
                    onShare = { if (!Intents.shareText(context, Intents.buildReport(r))) toast("ไม่พบแอปสำหรับส่งต่อ") },
                    onCallHotline = { if (!Intents.dial(context, Intents.AOC_HOTLINE)) toast("เครื่องนี้โทรออกไม่ได้ กรุณาโทร 1441") },
                    onBlockNumber = { vm.blockNumber(it); toast("บันทึกเบอร์ $it ว่าเป็นมิจฉาชีพแล้ว") },
                )
            }
        }
    }
}

/**
 * Floating pill tab bar. Every tab keeps its text label (older users read words, not icons);
 * the selected tab is a filled navy pill.
 */
@Composable
private fun FloatingTabBar(
    tabs: List<Triple<String, String, androidx.compose.ui.graphics.vector.ImageVector>>,
    selected: String?,
    onSelect: (String) -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        androidx.compose.material3.Surface(
            shape = RoundedCornerShape(36.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 10.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                tabs.forEach { (r, label, icon) ->
                    val on = r == selected
                    val bg by androidx.compose.animation.animateColorAsState(
                        if (on) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, tween(240), label = "tabBg",
                    )
                    val fg by androidx.compose.animation.animateColorAsState(
                        if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, tween(240), label = "tabFg",
                    )
                    // Selected: blue pill with icon + label. Others: icon only (label read by TalkBack).
                    Row(
                        Modifier
                            .weight(if (on) 1.6f else 1f)
                            .clip(RoundedCornerShape(30.dp))
                            .background(bg)
                            .selectable(selected = on, role = androidx.compose.ui.semantics.Role.Tab, onClick = { onSelect(r) })
                            .semantics { contentDescription = label }
                            .heightIn(min = 56.dp)
                            .animateContentSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(24.dp))
                        if (on) {
                            Spacer(Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
