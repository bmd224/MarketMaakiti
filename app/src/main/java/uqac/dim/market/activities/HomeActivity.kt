package uqac.dim.market.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import uqac.dim.market.ui.theme.MarketTheme
import uqac.dim.market.secrets.FCMUtils
import uqac.dim.market.utils.ThemeManager

class HomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // J'occupe tout l'espace bord a bord

        setContent {
            // Appliquer le changement de thème
            MarketTheme(darkTheme = ThemeManager.isDarkMode(this@HomeActivity)) {
                HomeScreen()
            }
        }

        // Initialiser FCM
        FCMUtils.initializeFCM(this)
    }

    // Cette méthode sera appelée quand l'activité reprend
    override fun onResume() {
        super.onResume()
        // Theme
        setContent {
            MarketTheme(darkTheme = ThemeManager.isDarkMode(this@HomeActivity)) {
                HomeScreen()
            }
        }
    }
}