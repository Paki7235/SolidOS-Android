package it.solidos.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Lo stile «Vetro» di SolidOS: gli stessi colori, lo stesso carattere (Inter) e lo sfondo del PC. */
object Tema {
    val sfondo = Color(0xFF1E1F24)
    val superficie = Color(0xFF26272D)
    val superficie2 = Color(0xFF2F3037)
    val bordo = Color(0xFF3A3B42)
    val testo = Color(0xFFF5F5F7)
    val testo2 = Color(0xFFA1A1AA)
    val accento = Color(0xFF4C7DFF)
    val rosso = Color(0xFFE5484D)
    val verde = Color(0xFF30A46C)

    // Vetro sopra lo sfondo: grafite trasparente con un bordo chiaro sottile
    val vetro = Color(0x94141519)
    val vetroBordo = Color(0x1FFFFFFF)

    @OptIn(ExperimentalTextApi::class)
    private fun inter(peso: Int) = Font(
        R.font.inter, FontWeight(peso),
        variationSettings = FontVariation.Settings(FontVariation.weight(peso)),
    )
    val font = FontFamily(inter(300), inter(400), inter(500), inter(600), inter(700))
}

/** Pannello di vetro (widget, Dock, cartelle). */
fun Modifier.vetro(raggio: Dp = 24.dp): Modifier = this
    .background(Tema.vetro, RoundedCornerShape(raggio))
    .border(BorderStroke(1.dp, Tema.vetroBordo), RoundedCornerShape(raggio))

/** Tessera di vetro chiaro delle icone (stile «Vetro liquido»). */
fun Modifier.tesseraVetro(raggio: Dp): Modifier = this
    .background(
        Brush.verticalGradient(listOf(Color(0x57FFFFFF), Color(0x21FFFFFF), Color(0x14FFFFFF))),
        RoundedCornerShape(raggio),
    )
    .border(BorderStroke(1.dp, Color(0x73FFFFFF)), RoundedCornerShape(raggio))
