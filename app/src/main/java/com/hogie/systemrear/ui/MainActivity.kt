package com.hogie.systemrear.ui

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.hogie.systemrear.coaching.Coaching
import com.hogie.systemrear.data.ACCENTS
import com.hogie.systemrear.data.ACCENT_NAMES
import com.hogie.systemrear.data.Ally
import com.hogie.systemrear.data.DEFAULT_QUESTS
import com.hogie.systemrear.data.Leveling
import com.hogie.systemrear.data.Quest
import com.hogie.systemrear.data.Store
import com.hogie.systemrear.rear.AllyView
import com.hogie.systemrear.rear.RankBadge
import com.hogie.systemrear.rear.RearLauncher
import com.hogie.systemrear.rear.Shell
import com.hogie.systemrear.service.KeeperService
import com.hogie.systemrear.service.NotifService
import com.hogie.systemrear.service.Notifs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.File
import java.util.UUID

private val RUNTIME_PERMS = arrayOf(
    Manifest.permission.POST_NOTIFICATIONS,
    Manifest.permission.RECORD_AUDIO,
    Manifest.permission.READ_CONTACTS,
    Manifest.permission.CALL_PHONE,
    Manifest.permission.ACTIVITY_RECOGNITION,
)

class MainActivity : ComponentActivity() {
    private val shizukuListener = Shizuku.OnRequestPermissionResultListener { _, _ -> refreshTick.value++ }
    val refreshTick = androidx.compose.runtime.mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { Shizuku.addRequestPermissionResultListener(shizukuListener) }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF3DA9FF), background = Color.Black, surface = Color(0xFF0B1222))) {
                Box(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) { Home(this@MainActivity) }
            }
        }
    }

    override fun onDestroy() {
        runCatching { Shizuku.removeRequestPermissionResultListener(shizukuListener) }
        super.onDestroy()
    }

    fun granted(p: String) = checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    fun notifAccess(): Boolean {
        val flat = AndroidSettings.Secure.getString(contentResolver, "enabled_notification_listeners").orEmpty()
        return flat.contains(ComponentName(this, NotifService::class.java).flattenToString())
    }

    fun canOverlay() = AndroidSettings.canDrawOverlays(this)
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0B1222)),
        shape = RoundedCornerShape(10.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = Color(0xFF3DA9FF), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp)
            content()
        }
    }
}

@Composable
private fun CheckRow(label: String, ok: Boolean, action: String?, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(if (ok) "✓" else "✗", color = if (ok) Color(0xFF2EF2C4) else Color(0xFFFF4D5E), fontWeight = FontWeight.Bold, modifier = Modifier.width(22.dp))
        Text(label, color = Color.White, modifier = Modifier.weight(1f), fontSize = 14.sp)
        if (!ok && action != null) TextButton(onClick = onClick) { Text(action) }
    }
}

@Composable
private fun Home(act: MainActivity) {
    val settings by Store.settings.collectAsState()
    val state by Store.state.collectAsState()
    val tick by act.refreshTick
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf("") }

    // Re-check permissions whenever we come back to the app.
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        owner.lifecycle.addObserver(LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) act.refreshTick.intValue++ })
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { act.refreshTick.intValue++ }
    LaunchedEffect(Unit) {
        if (Store.settings.value.showOnWake && !KeeperService.running) runCatching { KeeperService.start(act) }
    }

    val shizukuUp = remember(tick) { Shell.available() }
    val shizukuOk = remember(tick) { Shell.ready() }
    val notifOk = remember(tick) { act.notifAccess() }
    val permsOk = remember(tick) { RUNTIME_PERMS.all { act.granted(it) } }
    val overlayOk = remember(tick) { act.canOverlay() }
    val keeperOn = remember(tick) { KeeperService.running }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            val info = Leveling.levelOf(state.xp)
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                RankBadge(Leveling.rank(info.level), Color(ACCENTS[settings.accent]), 52.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("System Rear", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    Text("${settings.hunterName} · Lv ${info.level} · ${state.xp} XP total", color = Color(0xFF8A9BB8), fontSize = 13.sp)
                }
            }
        }
        item {
            Button(
                onClick = { RearLauncher.launch(act) { r -> act.runOnUiThread { message = if (r.startsWith("ok")) "Opened on the back screen" else r } } },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Show on back screen", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            if (message.isNotEmpty()) Text(message, color = Color(0xFF8A9BB8), fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        }

        item {
            Section("1 · SETUP") {
                CheckRow("Shizuku running", shizukuUp, null) {}
                if (shizukuUp) CheckRow("Shizuku allowed for this app", shizukuOk, "Allow") { runCatching { Shizuku.requestPermission(7) } }
                else Text("Open Shizuku and start it (Wireless debugging → Start). Needed after every reboot.", color = Color(0xFF8A9BB8), fontSize = 12.sp)
                CheckRow("Permissions (mic, contacts, calls, steps)", permsOk, "Allow") { permLauncher.launch(RUNTIME_PERMS) }
                CheckRow("Notification access (messages on the back)", notifOk, "Open") {
                    act.startActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
                CheckRow("Display over other apps (reopens panel without Shizuku)", overlayOk, "Open") {
                    act.startActivity(Intent(AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${act.packageName}")))
                }
                if (shizukuOk && (!notifOk || !permsOk || !overlayOk)) {
                    OutlinedButton(onClick = {
                        scope.launch {
                            val out = withContext(Dispatchers.IO) { grantAllViaShizuku(act) }
                            message = out
                            act.refreshTick.intValue++
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Grant everything with Shizuku") }
                    Text("Use this if Android says “Restricted setting” for notification access.", color = Color(0xFF8A9BB8), fontSize = 12.sp)
                }
                HorizontalDivider(color = Color(0xFF1C2A48))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Show System whenever the back screen wakes", color = Color.White, fontSize = 14.sp)
                        Text(if (keeperOn) "Running" else "Off", color = Color(0xFF8A9BB8), fontSize = 12.sp)
                    }
                    Switch(checked = settings.showOnWake && keeperOn, onCheckedChange = { on ->
                        Store.updateSettings { it.copy(showOnWake = on) }
                        if (on) KeeperService.start(act) else KeeperService.stop(act)
                        act.refreshTick.intValue++
                    })
                }
                SwitchRow("Keep the back screen on while System is open (uses battery)", settings.keepAwake) { v -> Store.updateSettings { it.copy(keepAwake = v) } }
                SwitchRow("Privacy while locked (hide message text; unlock to reply or call)", settings.lockPrivacy) { v -> Store.updateSettings { it.copy(lockPrivacy = v) } }
                Text("Camera area on the left: ${(settings.cameraInset * 100).toInt()}%", color = Color.White, fontSize = 14.sp)
                Slider(value = settings.cameraInset, onValueChange = { v -> Store.updateSettings { it.copy(cameraInset = v) } }, valueRange = 0f..0.45f)
            }
        }

        item {
            Section("2 · HUNTER PROFILE") {
                Field("Name", settings.hunterName) { v -> Store.updateSettings { it.copy(hunterName = v) } }
                Field("Title", settings.title) { v -> Store.updateSettings { it.copy(title = v) } }
                Text("Colour", color = Color.White)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ACCENTS.forEachIndexed { i, c ->
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).background(Color(c))
                                .border(3.dp, if (settings.accent == i) Color.White else Color.Transparent, CircleShape)
                                .clickable { Store.updateSettings { it.copy(accent = i) } }
                        )
                    }
                }
                Text(ACCENT_NAMES[settings.accent], color = Color(0xFF8A9BB8), fontSize = 12.sp)
            }
        }

        item { QuestEditor(state.quests) }
        item { AllyEditor(act, state.allies, state.activeAllyId, Color(ACCENTS[settings.accent])) }

        item {
            Section("5 · QUICK REPLIES") {
                Text("One per line. Tap one on the back to send it.", color = Color(0xFF8A9BB8), fontSize = 12.sp)
                var text by remember { mutableStateOf(settings.quickReplies.joinToString("\n")) }
                OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), minLines = 3)
                Button(onClick = { Store.updateSettings { s -> s.copy(quickReplies = text.lines().map { it.trim() }.filter { it.isNotEmpty() }) } }) { Text("Save replies") }
            }
        }

        item { CoachingSection() }

        item {
            Section("7 · REWARDS") {
                Field("XP for a class workout", settings.classXp.toString()) { v -> v.toIntOrNull()?.let { n -> Store.updateSettings { it.copy(classXp = n) } } }
                Field("Bonus XP for clearing the daily quest", settings.dailyBonus.toString()) { v -> v.toIntOrNull()?.let { n -> Store.updateSettings { it.copy(dailyBonus = n) } } }
                Text("System log", color = Color.White, fontWeight = FontWeight.Bold)
                state.log.take(8).forEach { Text(it, color = Color(0xFF8A9BB8), fontSize = 12.sp) }
                var confirm by remember { mutableStateOf(false) }
                TextButton(onClick = { if (confirm) { Store.resetProgress(); confirm = false } else confirm = true }) {
                    Text(if (confirm) "Tap again to reset level, XP and stats" else "Reset progress", color = Color(0xFFFF4D5E))
                }
            }
        }
        item { Spacer(Modifier.height(40.dp)) }
    }
}

@Composable
private fun SwitchRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onChange)
    }
}

@Composable
private fun Field(label: String, value: String, onDone: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    OutlinedTextField(
        text, { text = it; onDone(it) }, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true,
    )
}

@Composable
private fun QuestEditor(quests: List<Quest>) {
    Section("3 · DAILY QUESTS") {
        Text("Reps quests are logged with + on the back. “Steps” fills itself from the step counter.", color = Color(0xFF8A9BB8), fontSize = 12.sp)
        quests.forEach { q ->
            Column(Modifier.fillMaxWidth().border(1.dp, Color(0xFF1C2A48), RoundedCornerShape(8.dp)).padding(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    QField("Name", q.name, Modifier.weight(2f)) { v -> Store.setQuests(quests.map { if (it.id == q.id) it.copy(name = v) else it }) }
                    TextButton(onClick = { Store.setQuests(quests.filter { it.id != q.id }) }) { Text("Remove", color = Color(0xFFFF4D5E)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    QField("Target", q.target.toString(), Modifier.weight(1f)) { v -> v.toIntOrNull()?.let { n -> Store.setQuests(quests.map { if (it.id == q.id) it.copy(target = n.coerceAtLeast(1)) else it }) } }
                    if (q.auto.isEmpty()) QField("Per tap", q.step.toString(), Modifier.weight(1f)) { v -> v.toIntOrNull()?.let { n -> Store.setQuests(quests.map { if (it.id == q.id) it.copy(step = n.coerceAtLeast(1)) else it }) } }
                    QField("Unit", q.unit, Modifier.weight(1f)) { v -> Store.setQuests(quests.map { if (it.id == q.id) it.copy(unit = v) else it }) }
                    QField("XP", q.xp.toString(), Modifier.weight(1f)) { v -> v.toIntOrNull()?.let { n -> Store.setQuests(quests.map { if (it.id == q.id) it.copy(xp = n) else it }) } }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { Store.setQuests(quests + Quest(UUID.randomUUID().toString().take(8), "New quest", 50, 10, "reps", 50)) }) { Text("Add quest") }
            TextButton(onClick = { Store.setQuests(DEFAULT_QUESTS) }) { Text("Defaults") }
        }
    }
}

@Composable
private fun QField(label: String, value: String, modifier: Modifier, onDone: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    OutlinedTextField(text, { text = it; onDone(it) }, modifier, label = { Text(label, fontSize = 11.sp) }, singleLine = true)
}

@Composable
private fun AllyEditor(act: MainActivity, allies: List<Ally>, activeId: String, accent: Color) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                val ally = withContext(Dispatchers.IO) { importAlly(act, uri, allies.size) }
                if (ally != null) Store.setAllies(allies + ally, ally.id)
                busy = false
            }
        }
    }
    Section("4 · CHARACTERS") {
        Text(
            "Add your own character images (transparent PNGs look best). Set the level each one unlocks at to collect allies as you level up. Without any, the original “Shade” companion is shown.",
            color = Color(0xFF8A9BB8), fontSize = 12.sp,
        )
        allies.forEach { a ->
            Row(
                Modifier.fillMaxWidth().border(1.dp, if (a.id == activeId) accent else Color(0xFF1C2A48), RoundedCornerShape(8.dp)).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AllyView(a.file, accent, Modifier.size(64.dp).clickable { Store.setActiveAlly(a.id) })
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    QField("Name", a.name, Modifier.fillMaxWidth()) { v -> Store.setAllies(allies.map { if (it.id == a.id) it.copy(name = v) else it }) }
                    QField("Unlocks at level", a.unlockLevel.toString(), Modifier.fillMaxWidth()) { v ->
                        v.toIntOrNull()?.let { n -> Store.setAllies(allies.map { if (it.id == a.id) it.copy(unlockLevel = n.coerceAtLeast(1)) else it }) }
                    }
                }
                TextButton(onClick = {
                    File(a.file).delete()
                    Store.setAllies(allies.filter { it.id != a.id })
                }) { Text("✕", color = Color(0xFFFF4D5E)) }
            }
        }
        Button(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !busy) {
            Text(if (busy) "Importing…" else "Add character image")
        }
        Text("Tap a character on the back to switch between unlocked ones.", color = Color(0xFF8A9BB8), fontSize = 12.sp)
    }
}

private fun importAlly(act: MainActivity, uri: Uri, count: Int): Ally? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    act.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 900) sample *= 2
    val bmp = act.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: return null
    val dir = File(act.filesDir, "allies").apply { mkdirs() }
    val id = UUID.randomUUID().toString().take(8)
    val file = File(dir, "$id.png")
    file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    Ally(id, "Ally ${count + 1}", file.absolutePath, 1)
}.getOrNull()

@Composable
private fun CoachingSection() {
    val settings by Store.settings.collectAsState()
    val today by Coaching.today.collectAsState()
    val status by Coaching.status.collectAsState()
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf(settings.coachEmail) }
    var password by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    Section("6 · COACHING APP (S&C)") {
        if (settings.coachToken.isEmpty()) {
            Text("Sign in with your sandccoaching.com account to see today's class workout on the back.", color = Color(0xFF8A9BB8), fontSize = 12.sp)
            OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("Email") }, singleLine = true)
            OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
            Button(onClick = {
                scope.launch {
                    result = "Signing in…"
                    result = Coaching.login(email.trim(), password)
                    password = ""
                    if (Coaching.signedIn) Coaching.refresh()
                }
            }, enabled = email.isNotBlank() && password.isNotBlank()) { Text("Sign in") }
            if (result.isNotEmpty()) Text(result, color = Color(0xFF8A9BB8), fontSize = 12.sp)
        } else {
            Text("Signed in as ${settings.coachEmail}", color = Color.White)
            today?.let { w ->
                Text("${w.title} · ${w.time}", color = Color.White, fontWeight = FontWeight.Bold)
                w.lines.take(6).forEach { Text("▸ $it", color = Color(0xFF8A9BB8), fontSize = 12.sp) }
            }
            if (status.isNotEmpty()) Text(status, color = Color(0xFF8A9BB8), fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { scope.launch { Coaching.refresh() } }) { Text("Refresh") }
                TextButton(onClick = { Coaching.logout() }) { Text("Sign out") }
            }
            Text("The workout itself comes from the staff class API, so it shows for coach/admin accounts.", color = Color(0xFF8A9BB8), fontSize = 11.sp)
        }
    }
}

/** Grants runtime permissions, notification access and overlay through Shizuku's shell. */
private fun grantAllViaShizuku(act: MainActivity): String {
    val pkg = act.packageName
    val results = mutableListOf<String>()
    RUNTIME_PERMS.forEach { results += Shell.run("/system/bin/pm", "grant", pkg, it) }
    results += Shell.run("/system/bin/cmd", "notification", "allow_listener", ComponentName(act, NotifService::class.java).flattenToString())
    results += Shell.run("/system/bin/appops", "set", pkg, "SYSTEM_ALERT_WINDOW", "allow")
    results += Shell.run("/system/bin/appops", "set", pkg, "ACCESS_RESTRICTED_SETTINGS", "allow")
    val failed = results.count { it != "ok" }
    return if (failed == 0) "Everything granted" else "Granted with $failed issue(s): ${results.filter { it != "ok" }.joinToString("; ")}"
}
