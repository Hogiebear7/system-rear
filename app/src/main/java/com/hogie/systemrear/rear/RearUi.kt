package com.hogie.systemrear.rear

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import android.provider.ContactsContract
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hogie.systemrear.coaching.Coaching
import com.hogie.systemrear.data.ACCENTS
import com.hogie.systemrear.data.GameEvent
import com.hogie.systemrear.data.GameState
import com.hogie.systemrear.data.Leveling
import com.hogie.systemrear.data.STAT_NAMES
import com.hogie.systemrear.data.Settings
import com.hogie.systemrear.data.Store
import com.hogie.systemrear.service.Media
import com.hogie.systemrear.service.Msg
import com.hogie.systemrear.service.Notifs
import com.hogie.systemrear.service.RecorderService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val PAGES = listOf("STATUS", "QUESTS", "MSGS", "MUSIC", "CALL", "REC", "CLASS")
private val Red = Color(0xFFFF4D5E)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RearRoot(act: RearActivity) {
    val settings by Store.settings.collectAsState()
    val state by Store.state.collectAsState()
    val accent = Color(ACCENTS[settings.accent.coerceIn(ACCENTS.indices)])
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(pageCount = { PAGES.size })

    var now by remember { mutableStateOf(LocalDateTime.now()) }
    var locked by remember { mutableStateOf(act.locked) }
    var battery by remember { mutableIntStateOf(0) }
    var charging by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val bm = act.getSystemService(BatteryManager::class.java)
        while (true) {
            now = LocalDateTime.now()
            locked = act.locked
            battery = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            charging = bm.isCharging
            if (now.second == 0) Store.tick()
            delay(1000)
        }
    }
    val hide = locked && settings.hideTextLocked

    var banner by remember { mutableStateOf<Msg?>(null) }
    var openKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        Notifs.incoming.collectLatest {
            banner = it
            act.buzz()
            delay(4500)
            banner = null
        }
    }
    var popup by remember { mutableStateOf<GameEvent?>(null) }
    LaunchedEffect(Unit) {
        Store.events.collectLatest {
            popup = it
            act.buzz(strong = it is GameEvent.LevelUp || it is GameEvent.RankUp || it is GameEvent.DailyClear)
            delay(3200)
            popup = null
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF02040A), Color(0xFF070F24))))
    ) {
        val landscape = maxWidth > maxHeight
        val startInset: Dp = if (landscape) maxWidth * settings.cameraInset else 0.dp
        val topInset: Dp = if (landscape) 0.dp else maxHeight * settings.cameraInset
        Box(Modifier.fillMaxSize().padding(start = startInset + 4.dp, top = topInset + 4.dp, end = 6.dp, bottom = 4.dp)) {
            Column(Modifier.fillMaxSize()) {
                // Top bar: clock, battery, page tabs
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(now.format(DateTimeFormatter.ofPattern("HH:mm")), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.width(6.dp))
                    Text(now.format(DateTimeFormatter.ofPattern("EEE d")) + "  ${if (charging) "⚡" else ""}$battery%", color = Dim, fontSize = 11.sp)
                    Spacer(Modifier.weight(1f))
                    PAGES.forEachIndexed { i, name ->
                        val sel = pager.currentPage == i
                        Text(
                            name, color = if (sel) accent else Dim.copy(alpha = 0.7f), fontSize = 9.sp,
                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal, fontFamily = FontFamily.Monospace,
                            modifier = Modifier.clickable { scope.launch { pager.animateScrollToPage(i) } }.padding(horizontal = 3.dp, vertical = 2.dp),
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth(), beyondViewportPageCount = 1) { page ->
                    when (page) {
                        0 -> StatusPage(state, settings, accent)
                        1 -> QuestPage(state, accent, now)
                        2 -> MessagesPage(act, accent, settings, hide, openKey) { openKey = it }
                        3 -> MusicPage(accent, now)
                        4 -> CallPage(act, accent)
                        5 -> RecordPage(act, accent, now, hide)
                        6 -> WorkoutPage(state, settings, accent)
                    }
                }
            }

            AnimatedVisibility(
                visible = banner != null,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                val m = banner
                if (m != null) {
                    SystemWindow("${m.app.uppercase()} · NEW MESSAGE", accent, Modifier.fillMaxWidth().clickable {
                        openKey = m.key
                        banner = null
                        scope.launch { pager.animateScrollToPage(2) }
                    }) {
                        Column {
                            Text(m.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (hide) "🔒 Unlock to read" else m.text, color = Dim, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = popup != null, enter = scaleIn() + fadeIn(), exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center),
            ) {
                popup?.let { EventPopup(it, accent) }
            }
        }
    }
}

@Composable
private fun EventPopup(e: GameEvent, accent: Color) {
    val (title, line, color) = when (e) {
        is GameEvent.LevelUp -> Triple("LEVEL UP!", "You are now level ${e.level}\n+3 stat points", accent)
        is GameEvent.RankUp -> Triple("RANK UP", "You have reached ${e.rank}-Rank", Color(0xFFFFC547))
        is GameEvent.QuestDone -> Triple("QUEST COMPLETE", "${e.name}\n+${e.xp} XP", accent)
        is GameEvent.DailyClear -> Triple("DAILY QUEST CLEARED", "Streak: ${e.streak} days\nBonus XP + 1 stat point", Color(0xFF2EF2C4))
        is GameEvent.AllyUnlocked -> Triple("NEW ALLY", "${e.name} has joined you", Color(0xFF9B5CFF))
        is GameEvent.Penalty -> Triple("PENALTY", e.text, Red)
    }
    SystemWindow("SYSTEM", color, Modifier.width(260.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = color, fontSize = 20.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text(line, color = Color.White, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
    }
}

// ── Small building blocks ───────────────────────────────────────────────────

@Composable
fun SysButton(text: String, accent: Color, modifier: Modifier = Modifier, size: TextUnit = 12.sp, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, if (enabled) accent else Dim.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .background(if (enabled) accent.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (enabled) Color.White else Dim, fontSize = size, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun Bar(fraction: Float, accent: Color, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(3.dp)).background(Color(0xFF16213A))) {
        Box(
            Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight()
                .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.6f), accent)))
        )
    }
}

// ── Pages ───────────────────────────────────────────────────────────────────

@Composable
private fun StatusPage(state: GameState, settings: Settings, accent: Color) {
    val info = Leveling.levelOf(state.xp)
    val unlocked = state.allies.filter { it.unlockLevel <= info.level }
    val active = unlocked.firstOrNull { it.id == state.activeAllyId } ?: unlocked.lastOrNull()
    Row(Modifier.fillMaxSize()) {
        Box(
            Modifier.weight(0.38f).fillMaxHeight().clickable(enabled = unlocked.size > 1) {
                val i = unlocked.indexOf(active)
                Store.setActiveAlly(unlocked[(i + 1) % unlocked.size].id)
            },
            contentAlignment = Alignment.BottomCenter,
        ) {
            AllyView(active?.file, accent, Modifier.fillMaxSize().padding(bottom = 14.dp))
            Text(active?.name ?: "Shade", color = Dim, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.width(6.dp))
        SystemWindow("STATUS", accent, Modifier.weight(0.62f).fillMaxHeight()) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RankBadge(Leveling.rank(info.level), accent, 40.dp)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(settings.hunterName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                        Text("Lv ${info.level} · ${settings.title}", color = Dim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Bar(info.into.toFloat() / info.need, accent)
                Text("XP ${info.into} / ${info.need}", color = Dim, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(4.dp))
                STAT_NAMES.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { stat ->
                            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                Text("$stat ${state.stats[stat] ?: 10}", color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                                if (state.statPoints > 0) {
                                    Text(
                                        " ＋", color = accent, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clickable { Store.spendPoint(stat) }.padding(2.dp),
                                    )
                                }
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Spacer(Modifier.height(4.dp))
                val points = if (state.statPoints > 0) "Points: ${state.statPoints}   " else ""
                Text("$points🔥 ${state.streak} day streak (best ${state.bestStreak})", color = Dim, fontSize = 11.sp)
                state.log.firstOrNull()?.let {
                    Text(it, color = accent.copy(alpha = 0.85f), fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun QuestPage(state: GameState, accent: Color, now: LocalDateTime) {
    val left = Duration.between(now, LocalDate.now().plusDays(1).atStartOfDay())
    SystemWindow("DAILY QUEST · PREPARE TO GROW STRONGER", accent, Modifier.fillMaxSize()) {
        Column {
            Text(
                if (state.dailyCleared) "✓ Cleared — streak ${state.streak}" else "Time left: ${left.toHours()}h ${left.toMinutesPart()}m · failing resets your streak",
                color = if (state.dailyCleared) Color(0xFF2EF2C4) else Dim, fontSize = 10.sp,
            )
            Spacer(Modifier.height(4.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(state.quests, key = { it.id }) { q ->
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                (if (q.done) "✓ " else "") + q.name, color = if (q.done) accent else Color.White,
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
                            )
                            Text("${q.progress}/${q.target} ${q.unit}", color = Dim, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            if (q.auto.isEmpty()) {
                                Spacer(Modifier.width(6.dp))
                                SysButton("−", accent, size = 13.sp) { Store.addProgress(q.id, -q.step) }
                                Spacer(Modifier.width(4.dp))
                                SysButton("+${q.step}", accent, size = 13.sp) { Store.addProgress(q.id, q.step) }
                            }
                        }
                        Spacer(Modifier.height(3.dp))
                        Bar(q.progress.toFloat() / q.target.coerceAtLeast(1), accent, height = 4.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun MessagesPage(
    act: RearActivity, accent: Color, settings: Settings, hide: Boolean,
    openKey: String?, setOpen: (String?) -> Unit,
) {
    val items by Notifs.items.collectAsState()
    val connected by Notifs.connected.collectAsState()
    val open = items.firstOrNull { it.key == openKey }
    if (open != null) {
        MessageDetail(act, accent, settings, hide, open) { setOpen(null) }
        return
    }
    SystemWindow("MESSAGES · ${items.size}", accent, Modifier.fillMaxSize()) {
        Column {
            if (!connected) Text("Give System Rear notification access in the front app", color = Red, fontSize = 11.sp)
            if (items.isEmpty()) {
                Text("No new messages", color = Dim, fontSize = 12.sp)
            } else {
                Row { Spacer(Modifier.weight(1f)); SysButton("Clear all", accent, size = 10.sp) { act.guarded { Notifs.dismissAll() } } }
                Spacer(Modifier.height(4.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    items(items, key = { it.key }) { m ->
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(Color(0x22FFFFFF))
                                .clickable { setOpen(m.key) }.padding(6.dp)
                        ) {
                            Row {
                                Text(m.app, color = accent, fontSize = 10.sp, modifier = Modifier.weight(1f))
                                Text(timeAgo(m.time), color = Dim, fontSize = 10.sp)
                            }
                            Text(m.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (hide) "🔒 Unlock to read" else m.text, color = Dim, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageDetail(act: RearActivity, accent: Color, settings: Settings, hide: Boolean, m: Msg, close: () -> Unit) {
    var draft by remember(m.key) { mutableStateOf("") }
    var typing by remember(m.key) { mutableStateOf(false) }
    var status by remember(m.key) { mutableStateOf("") }
    fun send(text: String) = act.guarded {
        if (Notifs.reply(act, m, text)) {
            status = "✓ Sent"
            draft = ""
            typing = false
            act.buzz()
        } else {
            status = "Couldn't send — open it on the phone"
        }
    }
    SystemWindow("${m.app.uppercase()} · ${m.title}", accent, Modifier.fillMaxSize()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SysButton("‹ Back", accent, size = 11.sp) { close() }
                Spacer(Modifier.width(6.dp))
                Text(status, color = accent, fontSize = 11.sp, modifier = Modifier.weight(1f), maxLines = 1)
                SysButton("Dismiss", accent, size = 11.sp) { act.guarded { Notifs.dismiss(m); close() } }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                if (hide) "🔒 Unlock to read" else m.text, color = Color.White, fontSize = 13.sp,
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            )
            if (m.reply == null) {
                Row {
                    Text("This app has no reply button", color = Dim, fontSize = 11.sp, modifier = Modifier.weight(1f))
                    SysButton("Open on phone", accent, size = 11.sp) { act.guarded { runCatching { m.open?.send() } } }
                }
                return@Column
            }
            if (typing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        draft, { draft = it },
                        textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                        cursorBrush = SolidColor(accent),
                        modifier = Modifier.weight(1f).border(1.dp, accent, RoundedCornerShape(4.dp)).padding(6.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    SysButton("Send", accent, enabled = draft.isNotBlank()) { send(draft) }
                }
            } else if (draft.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("“$draft”", color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 2)
                    SysButton("Send", accent) { send(draft) }
                    Spacer(Modifier.width(4.dp))
                    SysButton("✕", accent) { draft = "" }
                }
            }
            Spacer(Modifier.height(4.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                item {
                    SysButton("🎤 Speak", accent, size = 11.sp) {
                        typing = false
                        act.listen(onPartial = { status = it }, onText = { draft = it; status = "Tap Send" }, fail = { status = it })
                    }
                }
                item { SysButton("⌨ Type", accent, size = 11.sp) { typing = !typing } }
                items(settings.quickReplies) { r -> SysButton(r, accent, size = 11.sp) { send(r) } }
            }
        }
    }
}

@Composable
private fun MusicPage(accent: Color, now: LocalDateTime) {
    val np by Media.now.collectAsState()
    val m = np
    SystemWindow("NOW PLAYING", accent, Modifier.fillMaxSize()) {
        if (m == null) {
            Text("Nothing playing. Start music on the phone and it appears here.", color = Dim, fontSize = 12.sp)
            return@SystemWindow
        }
        now.second // tick the progress bar every second
        val pos = m.currentPosition()
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            val art = remember(m.art) { m.art?.asImageBitmap() }
            Box(Modifier.size(104.dp).clip(RoundedCornerShape(6.dp)).background(Color(0x22FFFFFF)), contentAlignment = Alignment.Center) {
                if (art != null) Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Text("♪", color = accent, fontSize = 40.sp)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(m.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(m.artist, color = Dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(m.app, color = accent, fontSize = 10.sp)
                Spacer(Modifier.height(6.dp))
                if (m.durationMs > 0) {
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val w = constraints.maxWidth
                        Box(Modifier.fillMaxWidth().pointerInput(m.durationMs) {
                            detectTapGestures { o -> Media.seekTo((o.x / w * m.durationMs).toLong()) }
                        }.padding(vertical = 6.dp)) {
                            Bar(pos.toFloat() / m.durationMs, accent, height = 5.dp)
                        }
                    }
                    Row {
                        Text(fmt(pos), color = Dim, fontSize = 10.sp, modifier = Modifier.weight(1f))
                        Text(fmt(m.durationMs), color = Dim, fontSize = 10.sp)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    SysButton("⏮", accent, size = 15.sp) { Media.previous() }
                    SysButton(if (m.playing) "⏸" else "▶", accent, size = 15.sp) { Media.playPause() }
                    SysButton("⏭", accent, size = 15.sp) { Media.next() }
                    Spacer(Modifier.width(6.dp))
                    SysButton("🔉", accent, size = 13.sp) { Media.volume(false) }
                    SysButton("🔊", accent, size = 13.sp) { Media.volume(true) }
                }
            }
        }
    }
}

private fun fmt(ms: Long) = "%d:%02d".format(ms / 60000, ms / 1000 % 60)

private data class Contact(val name: String, val number: String)

@Composable
private fun CallPage(act: RearActivity, accent: Color) {
    var contacts by remember { mutableStateOf<List<Contact>?>(null) }
    var pending by remember { mutableStateOf<Contact?>(null) }
    LaunchedEffect(Unit) {
        contacts = withContext(Dispatchers.IO) { loadFavourites(act) }
    }
    SystemWindow("CALL · FAVOURITES", accent, Modifier.fillMaxSize()) {
        Column {
            val p = pending
            if (p != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
                    Text("Call ${p.name}?", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    SysButton("📞 Call", accent) {
                        pending = null
                        act.guarded {
                            val action = if (act.hasPermission(Manifest.permission.CALL_PHONE)) Intent.ACTION_CALL else Intent.ACTION_DIAL
                            runCatching { act.startActivity(Intent(action, Uri.fromParts("tel", p.number, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                    SysButton("✕", accent) { pending = null }
                }
            }
            val list = contacts
            when {
                !act.hasPermission(Manifest.permission.READ_CONTACTS) -> Text("Allow contacts in the front app", color = Red, fontSize = 12.sp)
                list == null -> Text("Loading…", color = Dim, fontSize = 12.sp)
                list.isEmpty() -> Text("Star (favourite) contacts in your Contacts app and they appear here", color = Dim, fontSize = 12.sp)
                else -> LazyVerticalGrid(GridCells.Adaptive(76.dp), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(list) { c ->
                        Column(
                            Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0x22FFFFFF)).clickable { pending = c }.padding(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(Modifier.size(34.dp).clip(CircleShape).border(1.dp, accent, CircleShape), contentAlignment = Alignment.Center) {
                                Text(c.name.split(" ").mapNotNull { it.firstOrNull()?.uppercase() }.take(2).joinToString(""), color = accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Text(c.name, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

private fun loadFavourites(act: RearActivity): List<Contact> {
    if (!act.hasPermission(Manifest.permission.READ_CONTACTS)) return emptyList()
    val out = LinkedHashMap<String, Contact>()
    runCatching {
        act.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.STARRED} = 1", null,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(0) ?: continue
                val number = c.getString(1) ?: continue
                if (name !in out) out[name] = Contact(name, number)
            }
        }
    }
    return out.values.toList()
}

@Composable
private fun RecordPage(act: RearActivity, accent: Color, now: LocalDateTime, hide: Boolean) {
    val since by RecorderService.recordingSince.collectAsState()
    val recent by RecorderService.recent.collectAsState()
    val playing by RecorderService.playing.collectAsState()
    var msg by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { RecorderService.loadRecent(act) } }
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(0.45f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            val rec = since > 0
            Box(
                Modifier.size(96.dp).clip(CircleShape)
                    .border(3.dp, if (rec) Red else accent, CircleShape)
                    .background(if (rec) Red.copy(alpha = 0.25f) else accent.copy(alpha = 0.12f))
                    .clickable {
                        if (rec) {
                            RecorderService.stop(act)
                            msg = "Saved to Recordings/SystemRear"
                        } else if (!act.hasPermission(Manifest.permission.RECORD_AUDIO)) {
                            msg = "Allow the microphone in the front app"
                        } else {
                            RecorderService.start(act)
                            msg = ""
                        }
                        act.buzz(true)
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (rec) {
                    val secs = ((System.currentTimeMillis() - since) / 1000).coerceAtLeast(0)
                    Text("%d:%02d".format(secs / 60, secs % 60), color = Color.White, fontSize = 20.sp, fontFamily = FontFamily.Monospace)
                } else {
                    Text("● REC", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(if (since > 0) "Tap to stop" else msg.ifEmpty { "Tap to record" }, color = Dim, fontSize = 11.sp, textAlign = TextAlign.Center)
            // keep the timer ticking
            now.second
        }
        SystemWindow("VOICE NOTES", accent, Modifier.weight(0.55f).fillMaxHeight()) {
            if (recent.isEmpty()) Text("No recordings yet", color = Dim, fontSize = 12.sp)
            else LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(recent, key = { it.uri.toString() }) { r ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(Color(0x22FFFFFF))
                            .clickable { act.guarded { runCatching { RecorderService.play(act, r.uri) } } }.padding(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(if (playing == r.uri) "■ " else "▶ ", color = accent, fontSize = 13.sp)
                        Text(
                            if (hide) "Voice note" else r.name.removePrefix("Voice ").removeSuffix(".m4a"),
                            color = Color.White, fontSize = 11.sp, modifier = Modifier.weight(1f), maxLines = 1,
                        )
                        Text("%d:%02d".format(r.durationMs / 60000, r.durationMs / 1000 % 60), color = Dim, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkoutPage(state: GameState, settings: Settings, accent: Color) {
    val today by Coaching.today.collectAsState()
    val status by Coaching.status.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        val w = Coaching.today.value
        if (Coaching.signedIn && (w == null || System.currentTimeMillis() - w.fetchedAt > 30 * 60_000)) Coaching.refresh()
    }
    val done = state.classDoneDay == Store.today()
    SystemWindow("CLASS WORKOUT", accent, Modifier.fillMaxSize()) {
        Column {
            val w = today
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(w?.title ?: "No class loaded", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    if (w != null) Text("Today ${w.time}", color = Dim, fontSize = 11.sp)
                }
                SysButton("↻", accent, size = 13.sp) { scope.launch { Coaching.refresh() } }
                Spacer(Modifier.width(4.dp))
                SysButton(if (done) "✓ Logged" else "Done +${settings.classXp} XP", accent, size = 11.sp, enabled = !done) { Store.classDone() }
            }
            if (status.isNotEmpty()) Text(status, color = Dim, fontSize = 11.sp)
            Spacer(Modifier.height(4.dp))
            if (w != null) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    items(w.lines) { line -> Text("▸ $line", color = Color.White, fontSize = 12.sp) }
                    if (w.notes.isNotBlank()) item { Text(w.notes, color = Dim, fontSize = 11.sp) }
                }
            }
        }
    }
}

private fun timeAgo(t: Long): String {
    val m = (System.currentTimeMillis() - t) / 60000
    return when {
        m < 1 -> "now"
        m < 60 -> "${m}m"
        m < 1440 -> "${m / 60}h"
        else -> "${m / 1440}d"
    }
}
