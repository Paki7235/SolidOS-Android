package it.solidos.home

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

/** Un'app che si può aprire dalla Home. */
data class AppTelefono(
    val pacchetto: String,
    val attivita: String,
    val nome: String,
    val icona: ImageBitmap,
    val categoria: Int,
)

/** Le app installate e le preferenze della Home (quali app, in che ordine). */
class Archivio(private val ctx: Context) {
    private val pref = ctx.getSharedPreferences("home", Context.MODE_PRIVATE)

    fun tutte(): List<AppTelefono> {
        val pm = ctx.packageManager
        val intento = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intento, 0)
            .filter { it.activityInfo.packageName != ctx.packageName }
            .map {
                AppTelefono(
                    pacchetto = it.activityInfo.packageName,
                    attivita = it.activityInfo.name,
                    nome = it.loadLabel(pm).toString(),
                    icona = it.loadIcon(pm).toBitmap(144, 144).asImageBitmap(),
                    categoria = it.activityInfo.applicationInfo.category,
                )
            }
            .sortedBy { it.nome.lowercase() }
    }

    /** L'app predefinita per un'azione (Telefono, Messaggi, Fotocamera, Browser). */
    private fun predefinita(intento: Intent): String? =
        ctx.packageManager.resolveActivity(intento, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
            ?.takeIf { it != "android" }

    fun dock(tutte: List<AppTelefono>): List<AppTelefono> {
        val pacchetti = listOfNotNull(
            predefinita(Intent(Intent.ACTION_DIAL)),
            predefinita(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))),
            predefinita(Intent(MediaStore.ACTION_IMAGE_CAPTURE)),
            predefinita(Intent(Intent.ACTION_VIEW, Uri.parse("https://solidos.it"))),
        )
        return pacchetti.mapNotNull { p -> tutte.firstOrNull { it.pacchetto == p } }.distinctBy { it.pacchetto }
    }

    /** Le app sulla Home: quelle scelte (tenendo premuto), altrimenti le prime utili. */
    fun home(tutte: List<AppTelefono>, dock: List<AppTelefono>): List<AppTelefono> {
        val scelte = pref.getString("home", null)?.split(",")?.filter { it.isNotBlank() }
        if (scelte != null) return scelte.mapNotNull { p -> tutte.firstOrNull { it.pacchetto == p } }
        val nelDock = dock.map { it.pacchetto }.toSet()
        return tutte.filter { it.pacchetto !in nelDock }.take(12)
    }

    fun salvaHome(app: List<AppTelefono>) {
        pref.edit().putString("home", app.joinToString(",") { it.pacchetto }).apply()
    }

    fun apri(app: AppTelefono) {
        val intento = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .setClassName(app.pacchetto, app.attivita)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        ctx.startActivity(intento)
    }

    fun info(app: AppTelefono) {
        ctx.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pacchetto}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    companion object {
        /** Cartelle della Libreria app, come sul telefono Linux e sul PC. */
        fun cartella(categoria: Int): String = when (categoria) {
            ApplicationInfo.CATEGORY_SOCIAL -> "Social"
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Produttività"
            ApplicationInfo.CATEGORY_IMAGE, ApplicationInfo.CATEGORY_VIDEO, ApplicationInfo.CATEGORY_AUDIO -> "Foto e video"
            ApplicationInfo.CATEGORY_GAME -> "Giochi"
            ApplicationInfo.CATEGORY_NEWS, ApplicationInfo.CATEGORY_MAPS -> "Informazioni"
            else -> "Utility"
        }
    }
}
