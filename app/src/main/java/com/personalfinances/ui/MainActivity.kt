package com.personalfinances.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.personalfinances.container
import com.personalfinances.data.FinanceSnapshot
import com.personalfinances.data.db.AccountType
import com.personalfinances.data.db.TxType
import com.personalfinances.notifications.DetectionNotifier
import com.personalfinances.ui.screens.AccountDetailScreen
import com.personalfinances.ui.screens.AccountEditScreen
import com.personalfinances.ui.screens.AddRequest
import com.personalfinances.ui.screens.AddTransactionContent
import com.personalfinances.ui.screens.CardDetailScreen
import com.personalfinances.ui.screens.CardsScreen
import com.personalfinances.ui.screens.CategoriesScreen
import com.personalfinances.ui.screens.HomeScreen
import com.personalfinances.ui.screens.NotificationSetupScreen
import com.personalfinances.ui.screens.PendingScreen
import com.personalfinances.ui.screens.SettingsScreen
import com.personalfinances.ui.screens.TransactionsScreen
import com.personalfinances.ui.theme.FinanceTheme
import kotlinx.coroutines.launch

private data class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

@Suppress("DEPRECATION")
private val tabs = listOf(
    Tab("home", "Inicio", Icons.Outlined.Home, Icons.Filled.Home),
    Tab("transactions", "Movimientos", Icons.Outlined.ReceiptLong, Icons.Filled.ReceiptLong),
    Tab("cards", "Tarjetas", Icons.Outlined.CreditCard, Icons.Filled.CreditCard),
    Tab("settings", "Ajustes", Icons.Outlined.Settings, Icons.Filled.Settings),
)

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels { MainViewModel.factory(container) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val snapshot by vm.snapshot.collectAsStateWithLifecycle()
            FinanceTheme(snapshot.settings.themeMode, snapshot.settings.dynamicColor) {
                AppRoot(snapshot, vm)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot(snapshot: FinanceSnapshot, vm: MainViewModel) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val isTopLevel = tabs.any { it.route == route } || route == null
    var addRequest by remember { mutableStateOf<AddRequest?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    val openAdd: (AddRequest) -> Unit = { addRequest = it }
    val openTx: (Long) -> Unit = { addRequest = AddRequest(transactionId = it) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            AnimatedVisibility(isTopLevel, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                    tabs.forEach { tab ->
                        val selected = route == tab.route || (route == null && tab.route == "home")
                        NavigationBarItem(
                            selected = selected,
                            onClick = { nav.navigateTab(tab.route) },
                            icon = { Icon(if (selected) tab.selectedIcon else tab.icon, tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            AnimatedVisibility(isTopLevel && route != "settings", enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                FloatingActionButton(onClick = { openAdd(AddRequest(type = TxType.EXPENSE)) }, containerColor = MaterialTheme.colorScheme.primary) {
                    Icon(Icons.Filled.Add, "Agregar movimiento")
                }
            }
        },
    ) { padding ->
        val pad = PaddingValues(bottom = padding.calculateBottomPadding())
        NavHost(
            nav, startDestination = "home",
            enterTransition = { fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 12 } },
            exitTransition = { fadeOut(tween(180)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = { fadeOut(tween(180)) + slideOutHorizontally(tween(240)) { it / 12 } },
        ) {
            composable("home") {
                HomeScreen(
                    snapshot = snapshot,
                    onToggleHide = { vm.setHideAmounts(!snapshot.settings.hideAmounts) },
                    onOpenAccount = { st -> nav.navigate(if (st.account.type.isCard) "card/${st.account.id}" else "account/${st.account.id}") },
                    onNewAccount = { nav.navigate("accountEdit") },
                    onOpenPending = { nav.navigate("pending") },
                    onOpenTransactions = { nav.navigateTab("transactions") },
                    onOpenTransaction = openTx,
                    onPayCard = { st -> openAdd(AddRequest(type = TxType.TRANSFER, toAccountId = st.account.id, amount = st.card?.billedDue)) },
                    contentPadding = pad,
                )
            }
            composable("transactions") { TransactionsScreen(snapshot, openTx, pad) }
            composable("cards") {
                CardsScreen(snapshot, onOpenCard = { nav.navigate("card/${it.account.id}") }, onNewCard = { nav.navigate("accountEdit?type=CREDIT_CARD") }, contentPadding = pad)
            }
            composable("settings") {
                SettingsScreen(snapshot, vm, onOpenNotifications = { nav.navigate("notifications") }, onOpenCategories = { nav.navigate("categories") }, contentPadding = pad)
            }
            composable("card/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                val id = e.arguments!!.getLong("id")
                CardDetailScreen(
                    snapshot, id,
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate("accountEdit?id=$id") },
                    onPay = { st -> openAdd(AddRequest(type = TxType.TRANSFER, toAccountId = st.account.id, amount = st.card?.billedDue?.takeIf { it > 0 })) },
                    onAddExpense = { st -> openAdd(AddRequest(type = TxType.EXPENSE, accountId = st.account.id)) },
                    onOpenTransaction = openTx,
                )
            }
            composable("account/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                val id = e.arguments!!.getLong("id")
                AccountDetailScreen(
                    snapshot, id,
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate("accountEdit?id=$id") },
                    onTransfer = { st -> openAdd(AddRequest(type = TxType.TRANSFER, accountId = st.account.id)) },
                    onAdjust = { vm.saveTransaction(it) },
                    onOpenTransaction = openTx,
                )
            }
            composable(
                "accountEdit?id={id}&type={type}",
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("type") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) { e ->
                val id = e.arguments!!.getLong("id").takeIf { it > 0 }
                val preset = e.arguments!!.getString("type")?.let { runCatching { AccountType.valueOf(it) }.getOrNull() }
                AccountEditScreen(
                    snapshot, id, preset,
                    onBack = { nav.popBackStack() },
                    onSave = { vm.saveAccount(it); nav.popBackStack() },
                    onDelete = { vm.deleteAccount(it); nav.popBackStack("home", inclusive = false) },
                )
            }
            composable("pending") {
                PendingScreen(snapshot, onBack = { nav.popBackStack() },
                    onConfirm = { openAdd(AddRequest(detectionId = it)) },
                    onDismiss = { vm.dismissDetection(it); DetectionNotifier.cancel(context, it) })
            }
            composable("notifications") { NotificationSetupScreen(snapshot, vm, onBack = { nav.popBackStack() }) }
            composable("categories") { CategoriesScreen(snapshot, vm, onBack = { nav.popBackStack() }) }
        }
    }

    addRequest?.let { req ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val scope = rememberCoroutineScope()
        fun close() { scope.launch { sheetState.hide() }.invokeOnCompletion { addRequest = null } }
        ModalBottomSheet(
            onDismissRequest = { addRequest = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentWindowInsets = { androidx.compose.foundation.layout.WindowInsets(0) },
        ) {
            AddTransactionContent(
                request = req,
                snapshot = snapshot,
                onSave = { draft ->
                    vm.saveTransaction(draft)
                    draft.detectionId?.let { DetectionNotifier.cancel(context, it) }
                    close()
                },
                onDelete = { id -> vm.deleteTransaction(id); close(); Toast.makeText(context, "Movimiento eliminado", Toast.LENGTH_SHORT).show() },
                onDismissDetection = { id -> vm.dismissDetection(id); DetectionNotifier.cancel(context, id); close() },
                onClose = ::close,
            )
        }
    }
}

private fun NavHostController.navigateTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

