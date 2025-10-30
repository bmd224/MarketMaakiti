package uqac.dim.market.theme

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview

// Footer est mon composable qui sert afficher un texte en bas de la page
// je le réutilise dans les autres activités. Celles que je veux
@Composable
fun Footer() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "© 2025 - Maakiti",
            fontSize = 14.sp,
            color = Color.DarkGray
        )
        Text(
            text = "Tous droits réservés",
            fontSize = 12.sp,
            color = Color.DarkGray
        )
    }
}

// preview de mon ecran
@Preview
@Composable
fun PreviewFooter() {
    Footer()
}
