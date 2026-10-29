package io.github.qbh21.vitrine

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.net.Uri
import android.provider.Settings
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import android.graphics.Canvas as AndroidCanvas

// Vitrine: live tiles in frosted glass over a bloom wallpaper.
// Built from concepts/windows-rt-2026.html; sizes are that mockup's pixels / 2 (the TV is 1080p at 2x).

private val Text = Color(0xFFF4F6FB)
private val Muted = Color(0xADE8EEFA)
private val Glass = Color(0x16FFFFFF)
private val GlassEdge = Color(0x29FFFFFF)
private val Sheen = Color(0x1AFFFFFF)
private val Halo = Color(0x24FFFFFF)
private val Band = Color(0xEB0E1430)

/** Brand colour that glows softly behind each app's icon. */
private val GlowColors = mapOf(
    "com.google.android.youtube.tv" to Color(0x8CFF2828),
    "org.moonfin.androidtv" to Color(0x8C7C5CFF),
    "com.android.vending" to Color(0x7328C88C),
    "com.mediatek.wwtv.tvcenter" to Color(0x66FF5AAA),
    "com.mediatek.dmp.mediaplayer" to Color(0x6BFF8C3C),
    "com.android.documentsui" to Color(0x5C4FA8FF),
    "com.netflix.ninja" to Color(0x80E50914),
)
private val SpareGlows = listOf(Color(0x664F8CFF), Color(0x668B5CF6), Color(0x5C19B8FF), Color(0x5CFF5AAA), Color(0x5C28C88C))
private fun glowFor(pkg: String) = GlowColors[pkg] ?: SpareGlows[Math.floorMod(pkg.hashCode(), SpareGlows.size)]

// Selawik: Microsoft's open-source stand-in for Segoe UI.
private val Selawik = FontFamily(
    Font(R.font.selawik_light, FontWeight.Light),
    Font(R.font.selawik, FontWeight.Normal),
    Font(R.font.selawik_semibold, FontWeight.SemiBold),
)

private fun text(size: Float, weight: FontWeight = FontWeight.Normal, color: Color = Text) =
    TextStyle(fontFamily = Selawik, fontWeight = weight, fontSize = size.sp, color = color)

// ---- Wallpaper -------------------------------------------------------------------------------------

/** Wallpaper choices (the Personalise tile): the same bloom in four colourways. */
private enum class Backdrop(val label: String, val base: IntArray, val petals: IntArray, val swatch: Color) {
    Bloom("Bloom", intArrayOf(0xFF0B1636.toInt(), 0xFF081028.toInt(), 0xFF0D0C2E.toInt()),
        intArrayOf(0x3D7DFF, 0x8A5CFF, 0x19B8FF, 0x2846B4), Color(0xFF4F7BFF)),
    Cyan("Cyan", intArrayOf(0xFF062A33.toInt(), 0xFF04202A.toInt(), 0xFF062634.toInt()),
        intArrayOf(0x19D3E6, 0x1A8CFF, 0x35F0D0, 0x0E6E8C), Color(0xFF3CCDE6)),
    Red("Red", intArrayOf(0xFF2A0A12.toInt(), 0xFF1E070D.toInt(), 0xFF2A0B1C.toInt()),
        intArrayOf(0xFF4D5E, 0xFF7A45, 0xE5486D, 0x8C1E3C), Color(0xFFE5484D)),
    Grey("Grey", intArrayOf(0xFF1A1C22.toInt(), 0xFF121418.toInt(), 0xFF1B1C24.toInt()),
        intArrayOf(0x8A93A6, 0x5E6A82, 0xB4BCCB, 0x3A4252), Color(0xFF9AA3B2)),
}

/** One soft light in the bloom, in 1920x1080 mockup coordinates. [fade] is where it reaches transparent. */
private class Glow(val x: Float, val y: Float, val rx: Float, val ry: Float, val rgb: Int, val alpha: Float, val rotation: Float = 0f, val fade: Float = 0.7f)

private fun Backdrop.glows() = listOf(
    Glow(1382f, 497f, 900f, 520f, petals[0], .55f),
    Glow(1651f, 842f, 700f, 600f, petals[1], .42f),
    Glow(1114f, 194f, 620f, 420f, petals[2], .30f),
    Glow(230f, 1080f, 1100f, 700f, petals[3], .35f),
    Glow(1490f, 460f, 370f, 190f, petals[0], .50f, -28f, 1f),   // the three "petals"
    Glow(1590f, 670f, 320f, 170f, petals[1], .50f, 22f, 1f),
    Glow(1230f, 610f, 270f, 150f, petals[2], .32f, 8f, 1f),
)

/** Paints the bloom once into a half-resolution bitmap, so each frame only has to draw one image. */
private fun renderBloom(b: Backdrop, width: Int, height: Int): ImageBitmap {
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
    paint.shader = LinearGradient(width * 0.4f, 0f, width * 0.6f, height.toFloat(), b.base, floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    val s = width / 1920f
    for (g in b.glows()) {
        val color = ((g.alpha * 255).toInt() shl 24) or g.rgb
        canvas.save()
        canvas.translate(g.x * s, g.y * s)
        canvas.rotate(g.rotation)
        canvas.scale(g.rx / g.ry, 1f)   // a circle stretched into the ellipse
        val r = g.ry * s
        paint.shader = RadialGradient(0f, 0f, r, intArrayOf(color, color and 0x00FFFFFF), floatArrayOf(0f, g.fade), Shader.TileMode.CLAMP)
        canvas.drawCircle(0f, 0f, r, paint)
        canvas.restore()
    }
    return bmp.asImageBitmap()
}

// ---- Screens ---------------------------------------------------------------------------------------

private enum class Screen { Start, Apps, Search, Notes }

@Composable
fun LauncherScreen(refresh: Int, reset: Int, asHome: Boolean, resumed: Boolean, onExit: () -> Unit) {
    val context = LocalContext.current
    val apps = remember(refresh) { Apps.load(context) }
    var pinned by remember { mutableStateOf(Apps.pinned(context)) }
    var note by remember { mutableStateOf(Apps.note(context)) }
    var screen by remember { mutableStateOf(Screen.Start) }
    var menuFor by remember { mutableStateOf<AppEntry?>(null) }
    var backdrop by remember { mutableStateOf(Backdrop.entries.firstOrNull { it.name == Apps.backdrop(context) } ?: Backdrop.Bloom) }
    var pickBackdrop by remember { mutableStateOf(false) }
    var wallpaper by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(backdrop) { wallpaper = withContext(Dispatchers.Default) { renderBloom(backdrop, 960, 540) } }
    LaunchedEffect(reset) { screen = Screen.Start; menuFor = null; pickBackdrop = false }
    // Back: any other screen -> Start. On Start it closes the app when opened as a normal app; as the home
    // screen there's nowhere further back to go (Home and the stock launcher still work).
    BackHandler { if (screen != Screen.Start) screen = Screen.Start else if (!asHome) onExit() }

    val openApp: (AppEntry) -> Unit = { app -> Apps.recordLaunch(context, app.pkg); open(context, app.launch) }

    Box(Modifier.fillMaxSize().background(Color(0xFF0B1636))) {
        Crossfade(wallpaper, animationSpec = tween(500), label = "wallpaper") { w ->
            if (w != null) Image(w, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        }
        when (screen) {
            Screen.Start -> Start(
                apps, pinned, note, backdrop, refresh, resumed,
                onOpen = openApp, onMenu = { menuFor = it },
                onNotes = { screen = Screen.Notes }, onPersonalise = { pickBackdrop = true },
                onAll = { screen = Screen.Apps }, onSearch = { screen = Screen.Search },
            )
            Screen.Apps -> AppList("All apps", "by name", apps, onOpen = openApp, onMenu = { menuFor = it })
            Screen.Search -> SearchScreen(apps, onOpen = openApp, onMenu = { menuFor = it })
            Screen.Notes -> NotesEditor(note) { note = it; Apps.saveNote(context, it) }
        }
    }

    menuFor?.let { app ->
        fun setPinned(list: List<String>) { pinned = list; Apps.savePinned(context, list) }
        val options = buildList<Pair<String, () -> Unit>> {
            add("Open" to { openApp(app) })
            if (app.pkg in pinned) add("Unpin from Start" to { setPinned(pinned - app.pkg) })
            else add("Pin to Start" to { setPinned(pinned + app.pkg) })
            add("App info" to { open(context, packageIntent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, app.pkg)) })
            if (!app.isSystem) add("Uninstall" to { open(context, packageIntent(Intent.ACTION_DELETE, app.pkg)) })
        }
        OptionsBand(app.label, options, onDismiss = { menuFor = null })
    }

    if (pickBackdrop) {
        val choices = Backdrop.entries.map { b ->
            (if (b == backdrop) "✓  ${b.label}" else b.label) to { backdrop = b; Apps.saveBackdrop(context, b.name) }
        }
        OptionsBand("Personalise", choices, onDismiss = { pickBackdrop = false })
    }
}

private fun open(context: Context, intent: Intent): Boolean = try {
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
} catch (e: Exception) {
    false   // app removed or can't be opened; the list refreshes next time we're shown
}

private fun packageIntent(action: String, pkg: String) = Intent(action, Uri.fromParts("package", pkg, null))

/** Ticks on the minute; re-syncs whenever the screen comes back. */
@Composable
private fun rememberNow(refresh: Int): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(refresh) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000 - now % 60_000)
        }
    }
    return now
}

private fun format(pattern: String, now: Long) = SimpleDateFormat(pattern, Locale.getDefault()).format(Date(now))

private fun greeting(now: Long): String {
    val hour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
    return when { hour < 12 -> "Good morning"; hour < 17 -> "Good afternoon"; else -> "Good evening" }
}

// ---- Start -----------------------------------------------------------------------------------------

private val U = 84.dp                    // a medium tile
private val G = 7.dp                     // gap between tiles
private val Wide = U * 2 + G
private val Full = U * 4 + G * 3
private val GroupH = U * 3 + G * 2

private fun Modifier.cell(col: Int, row: Int) = offset(x = (U + G) * col, y = (U + G) * row)

@Composable
private fun Start(
    apps: List<AppEntry>, pinned: List<String>, note: String, backdrop: Backdrop, refresh: Int, resumed: Boolean,
    onOpen: (AppEntry) -> Unit, onMenu: (AppEntry) -> Unit, onNotes: () -> Unit, onPersonalise: () -> Unit,
    onAll: () -> Unit, onSearch: () -> Unit,
) {
    val context = LocalContext.current
    val now = rememberNow(refresh)
    val pins = remember(apps, pinned) { pinned.mapNotNull { pkg -> apps.firstOrNull { it.pkg == pkg } } }
    val firstFocus = remember { FocusRequester() }
    val focusFirst = Modifier.focusRequester(firstFocus)
    val dateSettings = Intent(Settings.ACTION_DATE_SETTINGS)

    @Composable
    fun pin(i: Int, w: Dp, h: Dp, at: Modifier) {
        pins.getOrNull(i)?.let { AppTile(it, w, h, if (i == 0) at.then(focusFirst) else at, onOpen, onMenu) }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(top = 32.dp)) {
            Header(now, onSearch)
            Row(
                Modifier.width(742.dp).align(Alignment.CenterHorizontally).padding(top = 22.dp, bottom = 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText("Pinned", style = text(13f, FontWeight.SemiBold))
                GlassBox(74.dp, 23.dp, Modifier, onAll, corner = 11.5.dp, focusScale = 1.06f) {
                    Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                        BasicText("All apps", style = text(10f))
                        Image(painterResource(R.drawable.pk_chevron), null, Modifier.padding(start = 4.dp).size(9.dp), colorFilter = ColorFilter.tint(Text))
                    }
                }
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp, horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    // Watch: the big app, two more apps, photos, calendar, storage
                    Box(Modifier.size(Full, GroupH)) {
                        pin(0, Wide, Wide, Modifier.cell(0, 0))
                        pin(1, U, U, Modifier.cell(2, 0))
                        pin(2, U, U, Modifier.cell(3, 0))
                        PhotosTile(Modifier.cell(2, 1), resumed, if (pins.isEmpty()) focusFirst else Modifier)
                        GlassBox(U, U, Modifier.cell(0, 2), { open(context, dateSettings) }) { CalendarFace(now) }
                        GlassBox(U, U, Modifier.cell(1, 2), { open(context, Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)) }) { StorageFace(refresh) }
                    }
                    // Everyday: notes, more apps, casting, personalise, recents
                    Box(Modifier.size(Full, GroupH)) {
                        GlassBox(Wide, U, Modifier.cell(0, 0), onNotes, background = NotesGlass) { NotesFace(note) }
                        pin(3, U, U, Modifier.cell(2, 0))
                        pin(4, U, U, Modifier.cell(3, 0))
                        GlassBox(Wide, U, Modifier.cell(0, 1), {}, focusable = false) { CastFace(refresh) }   // status only
                        pin(5, U, U, Modifier.cell(2, 1))
                        GlassBox(U, U, Modifier.cell(3, 1), onPersonalise) { PersonaliseFace(backdrop) }
                        JumpBackIn(Modifier.cell(0, 2), apps, refresh, now, onOpen, onMenu)
                    }
                    // Anything pinned beyond the six app slots: an extra column (or more) of medium tiles
                    val extra = pins.drop(6)
                    if (extra.isNotEmpty()) {
                        Box(Modifier.size((U + G) * ((extra.size + 2) / 3) - G, GroupH)) {
                            extra.forEachIndexed { i, app -> AppTile(app, U, U, Modifier.cell(i / 3, i % 3), onOpen, onMenu) }
                        }
                    }
                }
            }
        }
        Hints(Modifier.align(Alignment.BottomStart).padding(start = 48.dp, bottom = 22.dp))
    }
    LaunchedEffect(Unit) { withFrameNanos { }; runCatching { firstFocus.requestFocus() } }   // once the tiles are laid out
}

private val NotesGlass = Brush.linearGradient(listOf(Color(0x4DFFD65A), Color(0x24FFAA3C)), Offset.Zero, Offset.Infinite)

@Composable
private fun Header(now: Long, onSearch: () -> Unit) {
    val context = LocalContext.current
    val name = remember { Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME) ?: "Android TV" }
    Row(Modifier.fillMaxWidth().padding(horizontal = 48.dp), verticalAlignment = Alignment.Top) {
        Column {
            BasicText(greeting(now), style = text(13f, color = Muted))
            BasicText(DateFormat.getTimeFormat(context).format(Date(now)), style = text(56f, FontWeight.Light))
            BasicText(format("EEEE, d MMMM", now), style = text(14f, color = Muted))
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.padding(top = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            GlassBox(165.dp, 28.dp, Modifier, onSearch, corner = 14.dp, focusScale = 1.04f) {
                Row(Modifier.align(Alignment.CenterStart).padding(start = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(R.drawable.pk_search), null, Modifier.size(12.dp), colorFilter = ColorFilter.tint(Muted))
                    BasicText("Search apps", Modifier.padding(start = 6.dp), style = text(11f, color = Muted))
                }
            }
            PillIcon(R.drawable.pk_wifi, "Wi-Fi") { open(context, Intent(Settings.ACTION_WIFI_SETTINGS)) }
            PillIcon(R.drawable.pk_gear, "Settings") { open(context, Intent(Settings.ACTION_SETTINGS)) }
            Column(Modifier.padding(start = 5.dp), horizontalAlignment = Alignment.End) {
                BasicText(name, style = text(11f))
                BasicText("Android TV", style = text(8.5f, color = Muted))
            }
            Box(
                Modifier.size(28.dp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFF4F8CFF), Color(0xFF8B5CF6))))
                    .border(1.dp, Color(0x80FFFFFF), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Image(painterResource(R.drawable.pk_person), null, Modifier.size(15.dp), colorFilter = ColorFilter.tint(Color.White)) }
        }
    }
}

@Composable
private fun PillIcon(res: Int, description: String, onClick: () -> Unit) {
    GlassBox(28.dp, 28.dp, Modifier, onClick, corner = 14.dp, focusScale = 1.08f) {
        Image(painterResource(res), description, Modifier.align(Alignment.Center).size(13.dp), colorFilter = ColorFilter.tint(Text))
    }
}

@Composable
private fun Hints(modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(17.dp), verticalAlignment = Alignment.CenterVertically) {
        for ((key, action) in listOf("OK" to "Select", "Hold OK" to "Options", "Back" to "Close")) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    key,
                    Modifier.background(Color(0x1FFFFFFF), RoundedCornerShape(3.5.dp)).border(0.5.dp, GlassEdge, RoundedCornerShape(3.5.dp))
                        .padding(horizontal = 5.dp, vertical = 1.5.dp),
                    style = text(7.5f),
                )
                BasicText(action, Modifier.padding(start = 5.dp), style = text(9.5f, color = Muted))
            }
        }
    }
}

// ---- Tiles -----------------------------------------------------------------------------------------

/** An app tile: neutral glass, the app's brand colour glowing behind its own icon, its name bottom-left. */
@Composable
private fun AppTile(app: AppEntry, w: Dp, h: Dp, modifier: Modifier, onOpen: (AppEntry) -> Unit, onMenu: (AppEntry) -> Unit) {
    val glow = glowFor(app.pkg)
    GlassBox(w, h, modifier, { onOpen(app) }, { onMenu(app) }) {
        Box(Modifier.fillMaxSize().drawBehind {
            drawRect(Brush.radialGradient(0f to glow, 0.7f to Color.Transparent, center = Offset(size.width / 2, size.height * 0.42f), radius = size.width * 0.77f))
        })
        AppIcon(app, if (w > U) 75.dp else 38.dp, Modifier.align(Alignment.Center).offset(y = (-5).dp))
        Label(app.label)
    }
}

/** The app's own icon in a rounded square, or the cleaner glyph from [Overrides]. */
@Composable
private fun AppIcon(app: AppEntry, size: Dp, modifier: Modifier) {
    val glyph = Overrides.glyphs[app.pkg]
    if (glyph != null) {
        Image(painterResource(glyph), app.label, modifier.size(if (size > 40.dp) 60.dp else 30.dp), colorFilter = ColorFilter.tint(Text))
        return
    }
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val icon = remember(app.pkg, px) { app.icon(context, px) }
    val shape = RoundedCornerShape(size * 0.23f)
    Box(modifier.size(size).shadow(4.dp, shape).clip(shape).background(icon.background ?: Color.White), contentAlignment = Alignment.Center) {
        Image(icon.bitmap, app.label, if (icon.fullBleed) Modifier.fillMaxSize() else Modifier.size(size * 0.86f))
    }
}

@Composable
private fun BoxScope.Label(label: String) {
    BasicText(label, Modifier.align(Alignment.BottomStart).padding(start = 9.dp, end = 9.dp, bottom = 7.dp), style = text(10.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun BoxScope.CalendarFace(now: Long) {
    BasicText(format("d", now), Modifier.align(Alignment.TopStart).padding(start = 9.dp, top = 2.dp), style = text(38f, FontWeight.Light))
    Column(Modifier.align(Alignment.TopStart).padding(start = 10.dp, top = 46.dp)) {
        BasicText(format("EEEE", now), style = text(9.5f, color = Muted))
        BasicText(format("MMMM", now), style = text(9.5f, color = Muted))
    }
}

@Composable
private fun BoxScope.StorageFace(refresh: Int) {
    val (free, total) = remember(refresh) { Storage.gb() }
    BasicText("%.1f GB".format(free), Modifier.align(Alignment.TopStart).padding(start = 9.dp, top = 8.dp), style = text(20f, FontWeight.Light))
    BasicText("free of %.1f GB".format(total), Modifier.align(Alignment.TopStart).padding(start = 10.dp, top = 34.dp), style = text(8.5f, color = Muted))
    val used = (1 - free / total).toFloat().coerceIn(0f, 1f)
    Box(Modifier.align(Alignment.TopStart).padding(start = 10.dp, end = 10.dp, top = 49.dp).fillMaxWidth().height(3.dp).background(Color(0x29FFFFFF), RoundedCornerShape(1.5.dp))) {
        Box(Modifier.fillMaxWidth(used).fillMaxHeight().background(Brush.horizontalGradient(listOf(Color(0xFF5AB4FF), Color(0xFF8B8BFF))), RoundedCornerShape(1.5.dp)))
    }
    Label("Storage")
}

@Composable
private fun BoxScope.NotesFace(note: String) {
    val lines = note.lines().map { it.trim() }.filter { it.isNotEmpty() }.take(2)
    Column(Modifier.align(Alignment.TopStart).padding(start = 10.dp, end = 10.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        if (lines.isEmpty()) BasicText("Press OK to write a note", style = text(11f, color = Muted))
        for (line in lines) Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).border(1.dp, Color(0xCCFFFFFF), RoundedCornerShape(1.5.dp)))
            BasicText(line, Modifier.padding(start = 7.dp), style = text(11f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    Label("Notes")
}

private const val CHROMECAST = "com.google.android.apps.mediashell"
private const val AIRPLAY = "io.github.jqssun.airplay"   // the receiver in tv-airplay/, runs at boot

private fun installedAndEnabled(context: Context, pkg: String) = try {
    context.packageManager.getApplicationInfo(pkg, 0).enabled
} catch (e: Exception) {
    false
}

/** Status only (the tile isn't focusable): which casting receivers this TV is running. */
@Composable
private fun BoxScope.CastFace(refresh: Int) {
    val context = LocalContext.current
    val chromecast = remember(refresh) { installedAndEnabled(context, CHROMECAST) }
    val airplay = remember(refresh) { installedAndEnabled(context, AIRPLAY) }
    val name = remember { Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME) ?: "This TV" }
    val ways = listOfNotNull("AirPlay".takeIf { airplay }, "Chromecast".takeIf { chromecast })
    BasicText(if (ways.isEmpty()) "Casting is off" else "Ready to cast", Modifier.align(Alignment.TopStart).padding(start = 10.dp, top = 9.dp), style = text(15f, FontWeight.Light))
    BasicText(if (ways.isEmpty()) name else "${ways.joinToString(" & ")} · $name", Modifier.align(Alignment.TopStart).padding(start = 10.dp, top = 31.dp), style = text(9f, color = Muted))
    Image(painterResource(R.drawable.pk_cast), null, Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 11.dp).size(22.dp), colorFilter = ColorFilter.tint(Text))
    Label("Cast")
}

@Composable
private fun BoxScope.PersonaliseFace(current: Backdrop) {
    Row(Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 8.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (b in Backdrop.entries) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(b.swatch).border(if (b == current) 1.5.dp else 0.75.dp, if (b == current) Color.White else Color(0xB3FFFFFF), CircleShape))
        }
    }
    Image(painterResource(R.drawable.pk_palette), null, Modifier.align(Alignment.Center).offset(y = (-5).dp).size(30.dp), colorFilter = ColorFilter.tint(Text))
    Label("Personalise")
}

/** The TV's built-in photos, cross-fading every 8 seconds, only while the launcher is on screen. */
@Composable
private fun PhotosTile(modifier: Modifier, resumed: Boolean, focus: Modifier) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { Wide.roundToPx() }
    val files = remember { Photos.files() }
    var index by remember { mutableIntStateOf(0) }
    var photo by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(resumed, files) {
        while (resumed && files.isNotEmpty()) {
            photo = withContext(Dispatchers.IO) { Photos.decode(files[index % files.size], px) } ?: photo
            index++
            delay(8_000)
        }
    }
    GlassBox(Wide, Wide, modifier.then(focus), { open(context, Intent(Settings.ACTION_DREAM_SETTINGS)) }, sheen = false) {
        Crossfade(photo, Modifier.fillMaxSize(), animationSpec = tween(900), label = "photo") { bmp ->
            if (bmp != null) Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color(0x8C000000))))
        Label("Photos")
        if (files.isEmpty()) {
            BasicText("This TV has no built-in showroom photos", Modifier.align(Alignment.Center).padding(horizontal = 14.dp), style = text(9f, color = Muted))
        }
        if (files.size > 1) {
            val on = Math.floorMod(index - 1, files.size)
            Row(Modifier.align(Alignment.BottomEnd).padding(end = 9.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(3.5.dp)) {
                for (i in files.indices) Box(Modifier.height(4.dp).width(if (i == on) 11.dp else 4.dp).background(if (i == on) Color.White else Color(0x73FFFFFF), CircleShape))
            }
        }
    }
}

/** "Jump back in": the last three apps opened from the launcher. The chips themselves are focusable. */
@Composable
private fun JumpBackIn(modifier: Modifier, apps: List<AppEntry>, refresh: Int, now: Long, onOpen: (AppEntry) -> Unit, onMenu: (AppEntry) -> Unit) {
    val context = LocalContext.current
    val recent = remember(apps, refresh) {
        Apps.recents(context).mapNotNull { (pkg, time) -> apps.firstOrNull { it.pkg == pkg }?.let { it to time } }.take(3)
    }
    Box(modifier.size(Full, U).clip(RoundedCornerShape(8.dp)).background(Glass).border(0.5.dp, GlassEdge, RoundedCornerShape(8.dp)).drawBehind {
        drawRect(Brush.verticalGradient(0f to Sheen, 0.45f to Color.Transparent))
    }) {
        Row(Modifier.fillMaxSize().padding(horizontal = 11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            BasicText("Jump\nback in", Modifier.width(60.dp), style = text(10.5f, FontWeight.SemiBold))
            if (recent.isEmpty()) BasicText("Apps you open will show up here", style = text(9.5f, color = Muted))
            for ((app, time) in recent) {
                GlassBox(82.dp, 52.dp, Modifier, { onOpen(app) }, { onMenu(app) }, corner = 6.dp, background = SolidColor(Color(0x12FFFFFF)), sheen = false, focusScale = 1.05f) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(app, 26.dp, Modifier)
                        Column(Modifier.padding(start = 6.dp)) {
                            BasicText(app.label, style = text(9.5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            BasicText(ago(time, now), style = text(7.5f, color = Muted), maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

private fun ago(time: Long, now: Long): String =
    if (now - time < 60_000) "Just now"
    else DateUtils.getRelativeTimeSpanString(time, maxOf(now, time), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString()

// ---- Other screens ---------------------------------------------------------------------------------

/** All apps by name: icon squares with names, scrolling sideways. */
@Composable
private fun AppList(
    title: String, subtitle: String?, apps: List<AppEntry>, onOpen: (AppEntry) -> Unit, onMenu: (AppEntry) -> Unit,
    focusList: Boolean = true, header: (@Composable () -> Unit)? = null,
) {
    val firstFocus = remember { FocusRequester() }
    Column(Modifier.fillMaxSize().padding(top = 32.dp)) {
        Row(Modifier.padding(start = 48.dp), verticalAlignment = Alignment.Bottom) {
            BasicText(title, style = text(28f, FontWeight.Light))
            if (subtitle != null) BasicText(subtitle, Modifier.padding(start = 10.dp, bottom = 4.dp), style = text(11f, color = Muted))
        }
        header?.invoke()
        Spacer(Modifier.height(14.dp))
        LazyHorizontalGrid(
            rows = GridCells.Fixed(6),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 6.dp, bottom = 44.dp),
        ) {
            itemsIndexed(apps, key = { _, app -> app.pkg }) { i, app ->
                Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.CenterStart) {   // keep rows their own height
                    GlassBox(
                        150.dp, 36.dp, if (i == 0) Modifier.focusRequester(firstFocus) else Modifier, { onOpen(app) }, { onMenu(app) },
                        corner = 8.dp, background = SolidColor(Color.Transparent), focusBackground = SolidColor(Glass), sheen = false, focusScale = 1.03f,
                    ) {
                        Row(Modifier.fillMaxSize().padding(horizontal = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(app, 26.dp, Modifier)
                            BasicText(app.label, Modifier.padding(start = 9.dp), style = text(11f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { if (focusList && apps.isNotEmpty()) { withFrameNanos { }; runCatching { firstFocus.requestFocus() } } }
}

/** Search apps: type with the on-screen keyboard, results narrow as you go. */
@Composable
private fun SearchScreen(apps: List<AppEntry>, onOpen: (AppEntry) -> Unit, onMenu: (AppEntry) -> Unit) {
    var query by remember { mutableStateOf("") }
    val results = remember(apps, query) { if (query.isBlank()) apps else apps.filter { it.label.contains(query.trim(), ignoreCase = true) } }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var focused by remember { mutableStateOf(false) }
    AppList("Search", if (query.isBlank()) "all apps" else "${results.size} found", results, onOpen, onMenu, focusList = false) {
        Box(
            Modifier.padding(start = 48.dp, top = 14.dp).width(330.dp).height(30.dp).clip(RoundedCornerShape(15.dp)).background(Glass)
                .border(if (focused) 1.5.dp else 0.5.dp, if (focused) Color.White else GlassEdge, RoundedCornerShape(15.dp)),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(Modifier.padding(start = 12.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.pk_search), null, Modifier.size(12.dp), colorFilter = ColorFilter.tint(Muted))
                Box(Modifier.padding(start = 7.dp)) {
                    if (query.isEmpty()) BasicText("Type an app name", style = text(11f, color = Muted))
                    BasicTextField(
                        query, { query = it },
                        Modifier.fillMaxWidth().focusRequester(focus).onFocusChanged { focused = it.isFocused },
                        textStyle = text(11f), singleLine = true, cursorBrush = SolidColor(Text),
                    )
                }
            }
        }
    }
    LaunchedEffect(Unit) { withFrameNanos { }; runCatching { focus.requestFocus() }; keyboard?.show() }
}

/** Full-screen note editor; the on-screen keyboard opens automatically. Saved as you type. */
@Composable
private fun NotesEditor(note: String, onChange: (String) -> Unit) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    Column(Modifier.fillMaxSize().padding(start = 48.dp, end = 48.dp, top = 32.dp, bottom = 32.dp)) {
        BasicText("Notes", style = text(28f, FontWeight.Light))
        BasicText("One note per line. Saved as you type; press Back when you're done.", style = text(11f, color = Muted))
        Spacer(Modifier.height(14.dp))
        BasicTextField(
            note, onChange,
            Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)).background(NotesGlass).border(0.5.dp, GlassEdge, RoundedCornerShape(8.dp))
                .padding(16.dp).focusRequester(focus),
            textStyle = text(16f), cursorBrush = SolidColor(Text),
        )
    }
    LaunchedEffect(Unit) { withFrameNanos { }; runCatching { focus.requestFocus() }; keyboard?.show() }
}

// ---- Building blocks -------------------------------------------------------------------------------

/**
 * A focusable frosted-glass surface: translucent fill, a faint top sheen and edge. Focused, it lifts a
 * little with a white ring and a soft halo; pressing OK pushes it in. [content] is told
 * whether it's focused.
 */
@Composable
private fun GlassBox(
    width: Dp, height: Dp, modifier: Modifier, onClick: () -> Unit, onLongClick: (() -> Unit)? = null,
    corner: Dp = 8.dp, background: Brush = SolidColor(Glass), focusBackground: Brush? = null,
    sheen: Boolean = true, ring: Boolean = true, focusScale: Float = 1.045f,
    focusable: Boolean = true,   // false: purely informational, the remote skips over it
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(when { pressed -> 0.97f; focused -> focusScale; else -> 1f }, tween(150), label = "scale")
    val press = remember { Press() }
    val shape = RoundedCornerShape(corner)
    Box(
        modifier
            .size(width, height)
            .graphicsLayer { scaleX = scale; scaleY = scale }   // no shadow: it would show through the glass
            .drawBehind {   // soft halo just outside the ring
                if (focused && ring) {
                    val h = 4.dp.toPx()
                    drawRoundRect(Halo, Offset(-h / 2, -h / 2), Size(size.width + h, size.height + h), CornerRadius(corner.toPx() + h / 2), style = Stroke(h))
                }
            }
            .drawWithContent {
                drawContent()
                val w = if (focused && ring) 1.5.dp.toPx() else 0.5.dp.toPx()
                drawRoundRect(if (focused && ring) Color.White else GlassEdge, Offset(w / 2, w / 2), Size(size.width - w, size.height - w), CornerRadius(corner.toPx() - w / 2), style = Stroke(w))
            }
            .clip(shape)
            .background(if (focused && focusBackground != null) focusBackground else background)
            .drawBehind { if (sheen) drawRect(Brush.verticalGradient(0f to Sheen, 0.45f to Color.Transparent)) }
            .then(
                if (focusable) Modifier
                    .onFocusChanged { focused = it.isFocused }
                    .onKeyEvent { press.handle(it.nativeKeyEvent, { p -> pressed = p }, onClick, onLongClick) }
                    .focusable()
                else Modifier
            ),
    ) { content(focused) }
}

/** OK/Enter on a tile: click on release; holding it (or the Menu key) opens the options instead. */
private class Press {
    private var down = false
    private var longFired = false

    fun handle(k: KeyEvent, setPressed: (Boolean) -> Unit, onClick: () -> Unit, onLongClick: (() -> Unit)?): Boolean {
        if (k.keyCode == KeyEvent.KEYCODE_MENU && onLongClick != null) {
            if (k.action == KeyEvent.ACTION_UP) onLongClick()
            return true
        }
        if (k.keyCode != KeyEvent.KEYCODE_DPAD_CENTER && k.keyCode != KeyEvent.KEYCODE_ENTER && k.keyCode != KeyEvent.KEYCODE_NUMPAD_ENTER) return false
        if (k.action == KeyEvent.ACTION_DOWN) {
            if (k.repeatCount == 0) {
                down = true; longFired = false; setPressed(true)
            } else if (down && !longFired && onLongClick != null && (k.isLongPress || k.repeatCount >= 8)) {
                longFired = true; setPressed(false); onLongClick()
            }
        } else if (k.action == KeyEvent.ACTION_UP) {
            setPressed(false)
            if (down && !longFired) onClick()   // only if the press started here, not in the previous screen
            down = false
        }
        return true
    }
}

/** Options as a full-width glass band with pill buttons; the focused one turns white. Back closes it. */
@Composable
private fun OptionsBand(title: String, options: List<Pair<String, () -> Unit>>, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val firstFocus = remember { FocusRequester() }
        Column(Modifier.fillMaxWidth().background(Band).padding(horizontal = 48.dp, vertical = 20.dp)) {
            BasicText(title, style = text(20f, FontWeight.Light), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEachIndexed { i, (label, action) ->
                    GlassBox(
                        150.dp, 32.dp, if (i == 0) Modifier.focusRequester(firstFocus) else Modifier, { onDismiss(); action() },
                        corner = 16.dp, focusBackground = SolidColor(Color.White), sheen = false, ring = false, focusScale = 1.04f,
                    ) { focused ->
                        BasicText(label, Modifier.align(Alignment.Center), style = text(11.5f, color = if (focused) Color(0xFF0B1636) else Text), maxLines = 1)
                    }
                }
            }
        }
        LaunchedEffect(Unit) { withFrameNanos { }; runCatching { firstFocus.requestFocus() } }
    }
}
