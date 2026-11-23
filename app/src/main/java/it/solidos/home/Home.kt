package it.solidos.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** La Home di SolidOS: widget, app, ricerca e Dock di vetro; scorrendo, la Libreria app. */
class Home : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // barre di sistema trasparenti con le icone chiare (sfondo scuro)
        enableEdgeToEdge(SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        ControlloAggiornamenti.pianifica(this)
        setContent { SchermataHome() }
    }
}

private val stile = TextStyle(fontFamily = Tema.font, color = Tema.testo)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SchermataHome() {
    val ctx = LocalContext.current
    val archivio = remember { Archivio(ctx) }
    var versione by remember { mutableIntStateOf(0) }       // ricarica quando si installa o si toglie un'app
    val tutte = remember(versione) { archivio.tutte() }
    val dock = remember(tutte) { archivio.dock(tutte) }
    var home by remember(tutte) { mutableStateOf(archivio.home(tutte, dock)) }
    // Le app installate o tolte arrivano subito sulla Home
    DisposableEffect(Unit) {
        val ricevitore = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) { versione++ }
        }
        val filtro = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED); addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ctx.registerReceiver(ricevitore, filtro)
        onDispose { ctx.unregisterReceiver(ricevitore) }
    }
    val pagine = rememberPagerState { 2 }

    Box(Modifier.fillMaxSize()) {
        Image(painterResource(R.drawable.sfondo_notte), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        HorizontalPager(pagine, Modifier.fillMaxSize()) { pagina ->
            if (pagina == 0) {
                PaginaHome(
                    home = home, dock = dock, archivio = archivio,
                    togli = { a -> home = home - a; archivio.salvaHome(home) },
                    cerca = { },
                )
            } else {
                Libreria(tutte, archivio, aggiungi = { a -> if (a !in home) { home = home + a; archivio.salvaHome(home) } })
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PaginaHome(
    home: List<AppTelefono>, dock: List<AppTelefono>, archivio: Archivio,
    togli: (AppTelefono) -> Unit, cerca: () -> Unit,
) {
    val ctx = LocalContext.current
    var menuSfondo by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
            .combinedClickable(onClick = {}, onLongClick = { menuSfondo = true }, indication = null, interactionSource = null),
    ) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            WidgetOggi(Modifier.weight(1f))
            WidgetBatteria(Modifier.weight(1f))
        }
        LazyVerticalGrid(
            GridCells.Fixed(4), Modifier.weight(1f).padding(horizontal = 12.dp),
            contentPadding = PaddingValues(top = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(home, key = { it.pacchetto }) { app -> IconaApp(app, archivio, conNome = true, togli = { togli(app) }) }
        }
        Text("Scorri per la Libreria app", style = stile.copy(fontSize = 12.sp, color = Tema.testo2), modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.padding(horizontal = 12.dp).fillMaxWidth().height(96.dp).vetro(34.dp),
            horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically,
        ) {
            dock.forEach { IconaApp(it, archivio, conNome = false) }
        }
        Spacer(Modifier.height(8.dp))
        DropdownMenu(menuSfondo, { menuSfondo = false }) {
            DropdownMenuItem({ Text("Aggiornamenti di SolidOS") }, {
                menuSfondo = false
                ctx.startActivity(Intent(ctx, Aggiornamenti::class.java))
            })
            DropdownMenuItem({ Text("Impostazioni del telefono") }, {
                menuSfondo = false
                ctx.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IconaApp(app: AppTelefono, archivio: Archivio, conNome: Boolean, togli: (() -> Unit)? = null, aggiungi: (() -> Unit)? = null) {
    var menu by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(
            Modifier.size(60.dp).clip(RoundedCornerShape(16.dp))
                .combinedClickable(onClick = { archivio.apri(app) }, onLongClick = { menu = true }),
            contentAlignment = Alignment.Center,
        ) {
            Image(app.icona, app.nome, Modifier.size(60.dp))
        }
        if (conNome) {
            Spacer(Modifier.height(6.dp))
            Text(app.nome, style = stile.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
        DropdownMenu(menu, { menu = false }) {
            if (aggiungi != null) DropdownMenuItem({ Text("Aggiungi alla Home") }, { menu = false; aggiungi() })
            if (togli != null) DropdownMenuItem({ Text("Togli dalla Home") }, { menu = false; togli() })
            DropdownMenuItem({ Text("Informazioni sull'app") }, { menu = false; archivio.info(app) })
        }
    }
}

@Composable
private fun WidgetOggi(modifier: Modifier) {
    val adesso = Date()
    Column(modifier.height(150.dp).vetro().padding(16.dp)) {
        Text(SimpleDateFormat("EEEE", Locale.ITALIAN).format(adesso).uppercase(), style = stile.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF6B6B)))
        Text(SimpleDateFormat("d", Locale.ITALIAN).format(adesso), style = stile.copy(fontSize = 50.sp, fontWeight = FontWeight.Light))
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(3.dp).height(30.dp).background(Color(0xFF8B5CF6), RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(8.dp))
            Column {
                Text(SimpleDateFormat("MMMM yyyy", Locale.ITALIAN).format(adesso), style = stile.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
                Text("Nessun impegno", style = stile.copy(fontSize = 12.sp, color = Tema.testo2))
            }
        }
    }
}

@Composable
private fun WidgetBatteria(modifier: Modifier) {
    val ctx = LocalContext.current
    var livello by remember { mutableIntStateOf(100) }
    LaunchedEffect(Unit) {
        val stato = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        livello = stato?.getIntExtra(BatteryManager.EXTRA_LEVEL, 100) ?: 100
    }
    Column(modifier.height(150.dp).vetro().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Batterie", style = stile.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Tema.testo2), modifier = Modifier.align(Alignment.Start))
        Spacer(Modifier.height(10.dp))
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(58.dp)) {
                val s = 5.dp.toPx()
                drawArc(Color(0x1AFFFFFF), 0f, 360f, false, style = Stroke(s), topLeft = Offset(s / 2, s / 2), size = Size(size.width - s, size.height - s))
                drawArc(if (livello <= 20) Tema.rosso else Tema.verde, -90f, 360f * livello / 100, false, style = Stroke(s, cap = StrokeCap.Round), topLeft = Offset(s / 2, s / 2), size = Size(size.width - s, size.height - s))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("$livello%", style = stile.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
    }
}

/** Libreria app: ricerca e cartelle per tipo (Social, Produttività, Foto e video…). */
@Composable
private fun Libreria(tutte: List<AppTelefono>, archivio: Archivio, aggiungi: (AppTelefono) -> Unit) {
    var cerca by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(44.dp).vetro(22.dp).padding(horizontal = 18.dp), contentAlignment = Alignment.CenterStart) {
            if (cerca.isEmpty()) Text("Libreria app", style = stile.copy(fontSize = 15.sp, color = Tema.testo2))
            BasicTextField(cerca, { cerca = it }, singleLine = true, textStyle = stile.copy(fontSize = 15.sp), cursorBrush = SolidColor(Tema.accento), modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(16.dp))
        if (cerca.isNotBlank()) {
            LazyVerticalGrid(GridCells.Fixed(4), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                items(tutte.filter { it.nome.contains(cerca, ignoreCase = true) }, key = { it.pacchetto }) { a ->
                    IconaApp(a, archivio, conNome = true, aggiungi = { aggiungi(a) })
                }
            }
        } else {
            val cartelle = tutte.groupBy { Archivio.cartella(it.categoria) }.toList().sortedBy { it.first }
            LazyVerticalGrid(GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(cartelle, key = { it.first }) { (nome, app) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.fillMaxWidth().height(160.dp).vetro().padding(12.dp)) {
                            LazyVerticalGrid(GridCells.Fixed(2), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), userScrollEnabled = false) {
                                items(app.take(4), key = { it.pacchetto }) { a -> IconaApp(a, archivio, conNome = false, aggiungi = { aggiungi(a) }) }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(nome, style = stile.copy(fontSize = 12.sp, color = Tema.testo2))
                    }
                }
            }
        }
    }
}

@Composable
private fun PuntoRosso() = Box(Modifier.size(10.dp).background(Tema.rosso, CircleShape))
