package it.solidos.home

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Aggiornamenti di SolidOS (come sul PC): ogni build pubblicata su GitHub è una nuova versione.
 * Qui si vede «SolidOS è aggiornato» oppure la nuova versione con le novità e il peso;
 * «Aggiorna ora» scarica, controlla (impronta SHA-256) e installa. Android accetta
 * l'aggiornamento solo se è firmato con la stessa chiave di SolidOS.
 */
data class Versione(val codice: Int, val nome: String, val data: String, val novita: List<String>, val apk: List<Pacchetto>) {
    val byte get() = apk.sumOf { it.byte }
}
data class Pacchetto(val nome: String, val url: String, val sha256: String, val byte: Long)

object Server {
    fun ultima(): Versione {
        val testo = scarica(BuildConfig.AGGIORNAMENTI)
        val j = JSONObject(testo)
        val apk = j.getJSONArray("apk").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
            .map { Pacchetto(it.getString("nome"), it.getString("url"), it.getString("sha256"), it.optLong("byte")) }
        val novita = j.optJSONArray("novita")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
        return Versione(j.getInt("versionCode"), j.optString("versione"), j.optString("data"), novita, apk)
    }

    private fun connessione(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000; readTimeout = 30000; instanceFollowRedirects = true
            setRequestProperty("User-Agent", "SolidOS-Aggiornamenti")
        }

    private fun scarica(url: String): String = connessione(url).inputStream.bufferedReader().use { it.readText() }

    /** Scarica un file mostrando l'avanzamento (0…1) e ne controlla l'impronta. */
    fun scaricaFile(p: Pacchetto, dove: File, avanzamento: (Float) -> Unit) {
        val c = connessione(p.url)
        val totale = c.contentLengthLong.takeIf { it > 0 } ?: p.byte
        val impronta = MessageDigest.getInstance("SHA-256")
        c.inputStream.use { ingresso ->
            dove.outputStream().use { uscita ->
                val buffer = ByteArray(64 * 1024)
                var letti = 0L
                while (true) {
                    val n = ingresso.read(buffer)
                    if (n < 0) break
                    uscita.write(buffer, 0, n); impronta.update(buffer, 0, n)
                    letti += n
                    if (totale > 0) avanzamento(letti.toFloat() / totale)
                }
            }
        }
        val calcolata = impronta.digest().joinToString("") { "%02x".format(it) }
        if (!calcolata.equals(p.sha256, ignoreCase = true)) {
            dove.delete()
            throw SecurityException("Il file scaricato non corrisponde: aggiornamento rifiutato")
        }
    }
}

/** Installazione con PackageInstaller: Android controlla la firma e chiede conferma. */
object Installatore {
    fun installa(ctx: Context, file: List<File>) {
        val pi = ctx.packageManager.packageInstaller
        val parametri = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        if (Build.VERSION.SDK_INT >= 31) parametri.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        val id = pi.createSession(parametri)
        pi.openSession(id).use { sessione ->
            file.forEach { f ->
                sessione.openWrite(f.name, 0, f.length()).use { out -> f.inputStream().use { it.copyTo(out) }; sessione.fsync(out) }
            }
            val esito = PendingIntent.getBroadcast(
                ctx, id, Intent(ctx, EsitoInstallazione::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            sessione.commit(esito.intentSender)
        }
    }
}

class EsitoInstallazione : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val conferma = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                conferma?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let(ctx::startActivity)
            }
            PackageInstaller.STATUS_SUCCESS -> Notifiche.mostra(ctx, "SolidOS aggiornato", "Il nuovo SolidOS è installato.")
            else -> Notifiche.mostra(ctx, "Aggiornamento non riuscito",
                intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Riprova più tardi")
        }
    }
}

object Notifiche {
    private const val CANALE = "aggiornamenti"
    fun mostra(ctx: Context, titolo: String, testo: String) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CANALE, "Aggiornamenti di SolidOS", NotificationManager.IMPORTANCE_DEFAULT))
        val apri = PendingIntent.getActivity(ctx, 0, Intent(ctx, Aggiornamenti::class.java), PendingIntent.FLAG_IMMUTABLE)
        nm.notify(1, NotificationCompat.Builder(ctx, CANALE).setSmallIcon(R.drawable.ic_logo)
            .setContentTitle(titolo).setContentText(testo).setContentIntent(apri).setAutoCancel(true).build())
    }
}

/** Una volta al giorno: c'è una nuova versione? Avviso con una notifica. */
class ControlloAggiornamenti(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result = runCatching {
        val v = Server.ultima()
        if (v.codice > BuildConfig.VERSION_CODE) {
            Notifiche.mostra(applicationContext, "Aggiornamento disponibile", "SolidOS ${v.nome}: tocca per aggiornare")
        }
        Result.success()
    }.getOrElse { Result.retry() }

    companion object {
        fun pianifica(ctx: Context) {
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                "aggiornamenti", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ControlloAggiornamenti>(1, TimeUnit.DAYS).build(),
            )
        }
    }
}

class Aggiornamenti : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // barre di sistema trasparenti con le icone chiare (sfondo scuro)
        enableEdgeToEdge(SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setContent { SchermataAggiornamenti() }
    }
}

private val testo = TextStyle(fontFamily = Tema.font, color = Tema.testo)

@Composable
private fun SchermataAggiornamenti() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var stato by remember { mutableStateOf("controllo") }      // controllo | aggiornato | nuova | scarico | installo | errore
    var versione by remember { mutableStateOf<Versione?>(null) }
    var avanzamento by remember { mutableStateOf(0f) }
    var errore by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        runCatching { withContext(Dispatchers.IO) { Server.ultima() } }
            .onSuccess { v -> versione = v; stato = if (v.codice > BuildConfig.VERSION_CODE) "nuova" else "aggiornato" }
            .onFailure { errore = "Non riesco a controllare: collegati a Internet e riprova"; stato = "errore" }
    }

    fun aggiorna() {
        val v = versione ?: return
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            // La prima volta Android chiede di permettere a SolidOS di installare gli aggiornamenti
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")))
            return
        }
        stato = "scarico"; avanzamento = 0f
        scope.launch {
            runCatching {
                val cartella = File(ctx.cacheDir, "aggiornamento").apply { deleteRecursively(); mkdirs() }
                val totale = v.byte.coerceAtLeast(1)
                var fatti = 0L
                val file = v.apk.map { p ->
                    val f = File(cartella, p.nome)
                    withContext(Dispatchers.IO) { Server.scaricaFile(p, f) { x -> avanzamento = (fatti + x * p.byte) / totale } }
                    fatti += p.byte
                    f
                }
                stato = "installo"; avanzamento = 1f
                withContext(Dispatchers.IO) { Installatore.installa(ctx, file) }
            }.onFailure { errore = it.message ?: "Aggiornamento non riuscito"; stato = "errore" }
        }
    }

    Column(
        Modifier.fillMaxSize().background(Tema.sfondo).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Aggiornamenti", style = testo.copy(fontSize = 30.sp, fontWeight = FontWeight.Bold), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(40.dp))
        Box(
            Modifier.size(84.dp).background(Brush.linearGradient(listOf(Color(0xFF4C7DFF), Color(0xFF8B5CF6))), RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center,
        ) { Text("S", style = testo.copy(fontSize = 40.sp, fontWeight = FontWeight.Bold)) }
        Spacer(Modifier.height(18.dp))
        val v = versione
        when (stato) {
            "controllo" -> Text("Controllo se c'è una nuova versione…", style = testo.copy(color = Tema.testo2))
            "aggiornato" -> {
                Text("SolidOS è aggiornato", style = testo.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold))
                Text("Versione ${BuildConfig.VERSION_NAME}", style = testo.copy(color = Tema.testo2))
            }
            "nuova", "scarico", "installo" -> if (v != null) {
                Text("SolidOS ${v.nome}", style = testo.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold))
                Text("%.1f MB · %s".format(v.byte / 1048576.0, v.data).replace(".", ","), style = testo.copy(color = Tema.testo2))
                Spacer(Modifier.height(20.dp))
                Column(Modifier.fillMaxWidth().vetro(18.dp).background(Tema.superficie, RoundedCornerShape(18.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Novità", style = testo.copy(fontWeight = FontWeight.Bold))
                    v.novita.forEach { Text("• $it", style = testo.copy(fontSize = 14.sp, color = Tema.testo2)) }
                }
                Spacer(Modifier.height(24.dp))
                if (stato == "nuova") {
                    Box(
                        Modifier.fillMaxWidth().height(50.dp).background(Tema.accento, RoundedCornerShape(25.dp)).clickable { aggiorna() },
                        contentAlignment = Alignment.Center,
                    ) { Text("Aggiorna ora", style = testo.copy(fontWeight = FontWeight.SemiBold, color = Color.White)) }
                } else {
                    LinearProgressIndicator(progress = { avanzamento }, modifier = Modifier.fillMaxWidth().height(8.dp), color = Tema.accento, trackColor = Tema.superficie2)
                    Spacer(Modifier.height(8.dp))
                    Text(if (stato == "scarico") "Scaricamento ${(avanzamento * 100).toInt()}%" else "Installazione: conferma sullo schermo",
                        style = testo.copy(fontSize = 13.sp, color = Tema.testo2))
                }
            }
            else -> Text(errore, style = testo.copy(color = Tema.rosso), textAlign = TextAlign.Center)
        }
    }
}
