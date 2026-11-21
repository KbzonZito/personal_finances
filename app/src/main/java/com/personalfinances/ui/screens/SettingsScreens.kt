package com.personalfinances.ui.screens

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.NotificationImportant
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Screenshot
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.personalfinances.data.FinanceSnapshot
import com.personalfinances.data.db.CategoryEntity
import com.personalfinances.data.prefs.ThemeMode
import com.personalfinances.domain.Currency
import com.personalfinances.domain.ExchangeRates
import com.personalfinances.domain.Money
import com.personalfinances.notifications.NotificationParser
import com.personalfinances.notifications.PurchaseNotificationListener
import com.personalfinances.ui.MainViewModel
import com.personalfinances.ui.lock.AppLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@Composable
fun SettingsScreen(
    snapshot: FinanceSnapshot,
    vm: MainViewModel,
    onOpenNotifications: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenRecurring: () -> Unit,
    onOpenBudgets: () -> Unit,
    onOpenGoals: () -> Unit,
    contentPadding: PaddingValues,
) {
    val s = snapshot.settings
    val context = LocalContext.current
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let { vm.enableAutoBackup(context, it) } }
    var showRates by remember { mutableStateOf(false) }
    var showCurrency by remember { mutableStateOf(false) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(vm::exportBackup) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::importBackup) }
    var confirmImport by remember { mutableStateOf(false) }
    var noLockAvailable by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item { Text("Ajustes", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.statusBarsPadding().padding(start = 20.dp, top = 16.dp, bottom = 8.dp)) }

        item { Group("Detección automática") }
        item {
            SettingRow(Icons.Outlined.NotificationsActive, "Leer notificaciones de compras",
                if (s.detectionEnabled) "Activado · ${s.watchedPackages.size} apps" else "Desactivado", onClick = onOpenNotifications)
        }

        item { Group("Seguridad") }
        item {
            val activity = LocalContext.current as? androidx.fragment.app.FragmentActivity
            SwitchSetting(Icons.Outlined.Fingerprint, "Bloquear con huella o PIN", "Se pide al abrir la app", s.lockEnabled) { enable ->
                when {
                    !enable -> vm.setLockEnabled(false)
                    activity == null -> Unit
                    !AppLock.canAuthenticate(activity) -> noLockAvailable = true
                    // Confirmar identidad antes de activar, para no quedar bloqueado por error.
                    else -> AppLock.authenticate(activity, "Activar bloqueo") { ok -> if (ok) vm.setLockEnabled(true) }
                }
            }
        }
        if (s.lockEnabled) item {
            LazyRow(contentPadding = PaddingValues(start = 60.dp, end = 20.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf(0, 1, 5, 15)) { m ->
                    FilterChip(selected = s.lockTimeoutMinutes == m, onClick = { vm.setLockTimeout(m) },
                        label = { Text(if (m == 0) "Siempre" else "Tras $m min") })
                }
            }
        }
        item { SwitchSetting(Icons.Outlined.Screenshot, "Proteger pantalla", "Oculta la app en recientes y bloquea capturas", s.secureScreen, vm::setSecureScreen) }

        item { Group("General") }
        item { SettingRow(Icons.Outlined.Payments, "Moneda principal", "${s.mainCurrency.flag} ${s.mainCurrency.displayName}") { showCurrency = true } }
        item {
            SettingRow(Icons.Outlined.CurrencyExchange, "Tasas de cambio",
                "1 USD = ${Money.format(Currency.CLP.fromMajorDouble(s.rates.rate(Currency.USD)), Currency.CLP)} · 1 UF = ${Money.format(Currency.CLP.fromMajorDouble(s.rates.rate(Currency.UF)), Currency.CLP)}") { showRates = true }
        }
        item {
            val n = snapshot.recurring.count { it.active }
            SettingRow(Icons.Outlined.Repeat, "Movimientos recurrentes", if (n == 0) "Sueldo, arriendo, suscripciones…" else "$n activos", onClick = onOpenRecurring)
        }
        item { SettingRow(Icons.Outlined.Savings, "Presupuestos", if (snapshot.budgets.isEmpty()) "Límites mensuales por categoría" else "${snapshot.budgets.size} activos", onClick = onOpenBudgets) }
        item { SettingRow(Icons.Outlined.Flag, "Metas de ahorro", if (snapshot.goals.isEmpty()) "Viajes, fondo de emergencia…" else "${snapshot.goals.size} metas", onClick = onOpenGoals) }
        item { SwitchSetting(Icons.Outlined.NotificationImportant, "Alertas de presupuesto", "Al llegar al 80% y si te pasas", s.budgetAlerts, vm::setBudgetAlerts) }
        item { SwitchSetting(Icons.Outlined.Alarm, "Recordatorios de tarjetas", "3 días antes del vencimiento y el día de cierre", s.remindersEnabled, vm::setRemindersEnabled) }
        item { SettingRow(Icons.Outlined.Category, "Categorías", "${snapshot.categories.size} categorías", onClick = onOpenCategories) }
        item { SwitchSetting(Icons.Outlined.VisibilityOff, "Ocultar montos", "Útil en lugares públicos", s.hideAmounts, vm::setHideAmounts) }

        item { Group("Apariencia") }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.DarkMode, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(16.dp))
                Text("Tema", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            }
            LazyRow(contentPadding = PaddingValues(start = 60.dp, end = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ThemeMode.entries) { m ->
                    FilterChip(selected = s.themeMode == m, onClick = { vm.setTheme(m) },
                        label = { Text(when (m) { ThemeMode.SYSTEM -> "Sistema"; ThemeMode.LIGHT -> "Claro"; ThemeMode.DARK -> "Oscuro" }) })
                }
            }
        }
        if (Build.VERSION.SDK_INT >= 31) item { SwitchSetting(Icons.Outlined.Palette, "Colores del fondo de pantalla", "Material You", s.dynamicColor, vm::setDynamic) }

        item { Group("Respaldo") }
        item {
            val uri = s.autoBackupUri
            if (uri == null) {
                SettingRow(Icons.Outlined.Backup, "Respaldo automático semanal", "Elige una carpeta (por ej. Documentos)") { folderPicker.launch(null) }
            } else {
                val folder = remember(uri) { com.personalfinances.data.backup.AutoBackup.folderName(context, android.net.Uri.parse(uri)) }
                val last = if (s.lastAutoBackup > 0) DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(s.lastAutoBackup)) else "nunca"
                Column {
                    SettingRow(Icons.Outlined.Backup, "Respaldo automático semanal", "En \"$folder\" · último: $last") { vm.backupNow(context) }
                    Row(Modifier.padding(start = 60.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { vm.backupNow(context) }) { Text("Respaldar ahora") }
                        TextButton(onClick = { folderPicker.launch(null) }) { Text("Cambiar carpeta") }
                        TextButton(onClick = vm::disableAutoBackup) { Text("Desactivar") }
                    }
                }
            }
        }
        item { SettingRow(Icons.Outlined.CloudUpload, "Exportar respaldo", "Guarda un archivo .json (puedes elegir Google Drive)") { exporter.launch("finanzas-${java.time.LocalDate.now()}.json") } }
        item { SettingRow(Icons.Outlined.CloudDownload, "Restaurar respaldo", "Reemplaza todos los datos actuales") { confirmImport = true } }

        item { Group("Privacidad") }
        item {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Icon(Icons.Outlined.Shield, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(16.dp))
                Text("Tus datos se guardan solo en este teléfono. La app no tiene servidores ni envía tu información a terceros; la única conexión es opcional, para actualizar tasas de cambio desde mindicador.cl.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { Spacer(Modifier.height(96.dp)) }
    }

    if (showCurrency) {
        AlertDialog(
            onDismissRequest = { showCurrency = false },
            title = { Text("Moneda principal") },
            text = { Column { Text("Se usa para el patrimonio total y los resúmenes.", style = MaterialTheme.typography.bodySmall); Spacer(Modifier.height(12.dp)); CurrencyDropdown(s.mainCurrency, Modifier.fillMaxWidth()) { vm.setMainCurrency(it); showCurrency = false } } },
            confirmButton = { TextButton(onClick = { showCurrency = false }) { Text("Cerrar") } },
        )
    }
    if (noLockAvailable) {
        AlertDialog(
            onDismissRequest = { noLockAvailable = false },
            title = { Text("Sin bloqueo de pantalla") },
            text = { Text("Para usar esta opción configura primero una huella, rostro, PIN o patrón en los ajustes de tu teléfono.") },
            confirmButton = { TextButton(onClick = { noLockAvailable = false }) { Text("Entendido") } },
        )
    }
    if (showRates) RatesDialog(s.rates, s.ratesUpdatedAt, vm) { showRates = false }
    if (confirmImport) {
        AlertDialog(
            onDismissRequest = { confirmImport = false },
            title = { Text("¿Restaurar respaldo?") },
            text = { Text("Se reemplazarán todas tus cuentas y movimientos actuales por los del archivo.") },
            confirmButton = { TextButton(onClick = { confirmImport = false; importer.launch(arrayOf("application/json", "*/*")) }) { Text("Elegir archivo") } },
            dismissButton = { TextButton(onClick = { confirmImport = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun RatesDialog(rates: ExchangeRates, updatedAt: Long, vm: MainViewModel, onDismiss: () -> Unit) {
    val editable = listOf(Currency.USD, Currency.EUR, Currency.UF, Currency.BRL, Currency.ARS, Currency.GBP)
    val values = remember { editable.associateWith { mutableStateOf(rates.rate(it).toBigDecimal().stripTrailingZeros().toPlainString().replace('.', ',')) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tasas de cambio") },
        text = {
            Column {
                Text("Valor de 1 unidad en pesos chilenos" + if (updatedAt > 0) " · actualizado ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(updatedAt))}" else "",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                editable.forEach { c ->
                    val st = values.getValue(c)
                    OutlinedTextField(st.value, { st.value = it.filter { ch -> ch.isDigit() || ch == ',' || ch == '.' } },
                        label = { Text("${c.flag} 1 ${c.code}") }, prefix = { Text("$") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.padding(vertical = 2.dp))
                }
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(onClick = { vm.refreshRates(); onDismiss() }) {
                    Icon(Icons.Outlined.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Actualizar USD, EUR y UF")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val map = rates.clpPerUnit.toMutableMap()
                values.forEach { (c, st) -> st.value.replace(".", "").replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }?.let { map[c] = it } }
                vm.setRates(ExchangeRates(map)); onDismiss()
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun Group(title: String) = Text(
    title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 4.dp),
)

@Composable
private fun SettingRow(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SwitchSetting(icon: ImageVector, title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(checked, onChange)
    }
}

// ----------------------------------------------------------------------------------------------
// Configuración de detección por notificaciones
// ----------------------------------------------------------------------------------------------

private data class AppInfo(val packageName: String, val label: String, val icon: Drawable?, val suggested: Boolean)

private val bankHints = listOf(
    "banco", "bank", "bci", "santander", "scotiabank", "itaú", "itau", "estado", "chile", "falabella", "ripley", "cmr",
    "tenpo", "mach", "mercado pago", "mercadopago", "wallet", "billetera", "security", "bice", "consorcio", "coopeuch",
    "tapp", "fintual", "racional", "paypal", "revolut", "wise", "nubank", "líder", "lider", "prex", "global66", "copec",
)

private fun isListenerEnabled(context: Context): Boolean {
    val flat = AndroidSettings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
    val me = ComponentName(context, PurchaseNotificationListener::class.java).flattenToString()
    return flat.split(":").any { it == me }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSetupScreen(snapshot: FinanceSnapshot, vm: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val s = snapshot.settings
    var listenerOn by remember { mutableStateOf(isListenerEnabled(context)) }
    var postGranted by remember { mutableStateOf(Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) }
    var apps by remember { mutableStateOf<List<AppInfo>?>(null) }
    var showConsent by rememberSaveable { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current
    val postLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { postGranted = it }

    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) listenerOn = isListenerEnabled(context) }
        lifecycle.lifecycle.addObserver(obs)
        onDispose { lifecycle.lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(intent, 0).map { it.activityInfo.applicationInfo }.distinctBy { it.packageName }
                .filter { it.packageName != context.packageName }
                .map { ai ->
                    val label = pm.getApplicationLabel(ai).toString()
                    val l = label.lowercase()
                    AppInfo(ai.packageName, label, runCatching { pm.getApplicationIcon(ai) }.getOrNull(),
                        ai.packageName == NotificationParser.GOOGLE_WALLET || bankHints.any { l.contains(it) })
                }
                .sortedWith(compareByDescending<AppInfo> { it.packageName in s.watchedPackages }.thenByDescending { it.suggested }.thenBy { it.label.lowercase() })
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Detección de compras", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver") } },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            item {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Column(Modifier.padding(18.dp)) {
                        Text("¿Cómo funciona?", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Cuando tu banco o Google Wallet te notifica una compra, la app lee el monto y el comercio y te pregunta si quieres registrarlo. " +
                                "Nunca se registra nada sin tu confirmación, solo se leen las apps que elijas abajo, y todo queda en tu teléfono.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().clickable { if (s.detectionEnabled) vm.setDetectionEnabled(false) else showConsent = true }.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Activar detección", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Switch(s.detectionEnabled, { if (it) showConsent = true else vm.setDetectionEnabled(false) })
                }
            }
            if (s.detectionEnabled) {
                item {
                    StepRow(1, "Permitir acceso a notificaciones", listenerOn, if (listenerOn) "Listo" else "Abre ajustes del sistema y activa \"Finanzas\"") {
                        context.startActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }
                }
                item {
                    StepRow(2, "Permitir que la app te avise", postGranted, if (postGranted) "Listo" else "Para pedirte confirmar cada compra") {
                        if (Build.VERSION.SDK_INT >= 33) postLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                item {
                    Text("3 · Elige qué apps leer", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp))
                    Text("Sugerimos bancos y billeteras. Solo se leerán las marcadas.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
                }
                val list = apps
                if (list == null) item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { Text("Cargando apps…") } }
                else items(list, key = { it.packageName }) { app ->
                    val checked = app.packageName in s.watchedPackages
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.setWatchedPackages(if (checked) s.watchedPackages - app.packageName else s.watchedPackages + app.packageName) }
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val bmp = remember(app.packageName) { app.icon?.toBitmap(96, 96)?.asImageBitmap() }
                        if (bmp != null) Image(bmp, null, Modifier.size(36.dp)) else Spacer(Modifier.size(36.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(app.label, style = MaterialTheme.typography.bodyLarge, fontWeight = if (app.suggested) FontWeight.Medium else FontWeight.Normal)
                            if (app.suggested) Text("Sugerida", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        Checkbox(checked, null)
                    }
                }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    if (showConsent) {
        AlertDialog(
            onDismissRequest = { showConsent = false },
            title = { Text("¿Permitir lectura de notificaciones?") },
            text = {
                Text("La app leerá el texto de las notificaciones de las apps que elijas para detectar compras e ingresos. " +
                    "Cada movimiento detectado te pedirá confirmación antes de guardarse. Puedes desactivarlo cuando quieras.")
            },
            confirmButton = { Button(onClick = { vm.setDetectionEnabled(true); showConsent = false }) { Text("Acepto") } },
            dismissButton = { TextButton(onClick = { showConsent = false }) { Text("Ahora no") } },
        )
    }
}

@Composable
private fun StepRow(n: Int, title: String, done: Boolean, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = !done, onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = androidx.compose.foundation.shape.CircleShape, color = if (done) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.size(32.dp)) {
            Box(contentAlignment = Alignment.Center) { Text(if (done) "✓" else "$n", color = if (done) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurface) }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!done) Icon(Icons.Outlined.ChevronRight, null)
    }
}

// ----------------------------------------------------------------------------------------------
// Pendientes por confirmar
// ----------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingScreen(snapshot: FinanceSnapshot, onBack: () -> Unit, onConfirm: (Long) -> Unit, onDismiss: (Long) -> Unit) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Por confirmar", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver") } },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            if (snapshot.pending.isEmpty()) item {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { Text("Todo al día ✨", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(snapshot.pending, key = { it.id }) { d ->
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large,
                    modifier = Modifier.animateItem().fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(d.merchant ?: if (d.isIncome) "Ingreso" else "Compra", style = MaterialTheme.typography.titleMedium)
                                Text("${d.appLabel} · ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(d.postedAt))}",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text((if (d.isIncome) "+" else "") + Money.format(d.amount, d.currency), style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(d.text.ifBlank { d.title }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onConfirm(d.id) }, modifier = Modifier.weight(1f)) { Text("Registrar") }
                            FilledTonalButton(onClick = { onDismiss(d.id) }, modifier = Modifier.weight(1f)) { Text("Ignorar") }
                        }
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------------------------------------------------
// Categorías
// ----------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(snapshot: FinanceSnapshot, vm: MainViewModel, onBack: () -> Unit) {
    var editing by remember { mutableStateOf<CategoryEntity?>(null) }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Categorías", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver") } },
                actions = { TextButton(onClick = { editing = CategoryEntity(name = "", emoji = "🏷️", color = 0xFF6C5CE7) }) { Text("Nueva") } },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            listOf(false to "Gastos", true to "Ingresos").forEach { (income, title) ->
                item { Group(title) }
                items(snapshot.categories.filter { it.isIncome == income }, key = { it.id }) { c ->
                    Row(Modifier.fillMaxWidth().clickable { editing = c }.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(c.emoji, style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.width(16.dp))
                        Text(c.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    }
                    HorizontalDivider(Modifier.padding(start = 60.dp), color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
    editing?.let { cat ->
        var name by remember(cat) { mutableStateOf(cat.name) }
        var emoji by remember(cat) { mutableStateOf(cat.emoji) }
        var income by remember(cat) { mutableStateOf(cat.isIncome) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (cat.id == 0L) "Nueva categoría" else "Editar categoría") },
            text = {
                Column {
                    OutlinedTextField(emoji, { emoji = it.take(4) }, label = { Text("Emoji") }, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        Text("Es de ingreso", modifier = Modifier.weight(1f)); Switch(income, { income = it })
                    }
                    if (cat.id != 0L && cat.systemKey == null) TextButton(onClick = { vm.saveCategory(cat.copy(archived = true)); editing = null }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { vm.saveCategory(cat.copy(name = name.trim(), emoji = emoji.ifBlank { "🏷️" }, isIncome = income)); editing = null }) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancelar") } },
        )
    }
}

