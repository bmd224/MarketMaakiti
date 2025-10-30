package uqac.dim.market.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Définit les couleurs principales utilisées quand le mode sombre est activé
private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

// Définit les couleurs principales utilisées quand le mode clair est activé
private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

// Composable principal qui applique le thème à toute l'application
// C'est ici que j'initialise le mode sombre/clair
@Composable
fun MarketTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    // Détermine la couleur du thème en fonction des préférences de l'utilisateur et de la version du système
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        // Si le mode sombre est activé, on utilise DarkColorScheme, sinon LightColorScheme
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // Mise à jour de la couleur de la status bar
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    // Application du thème à tout le contenu
    // Tous les composables enfants hériteront de ce thème
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}