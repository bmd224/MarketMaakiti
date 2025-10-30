package uqac.dim.market.utils

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration

// Je gere ici les thèmes de l'application
// Cette classe singleton permet de gérer les préférences utilisateur concernant
// le mode sombre et la synchronisation avec les paramètres système
object ThemeManager {
    // Nom du fichier de préférences pour stocker les paramètres de thème
    private const val PREFS_NAME = "theme_prefs"
    // Clé pour sauvegarder l'état du mode sombre
    private const val KEY_DARK_MODE = "dark_mode"
    // Clé pour indiquer si l'utilisateur a défini manuellement un thème
    private const val KEY_USER_SET = "user_set_theme"

    // Je Récupère l'instance des SharedPreferences pour le thème
    // a travers le  contexte de l'application et je la retourne configurée
    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // Détermine si le mode sombre doit être activé
    // Vérifie d'abord si l'utilisateur a défini une préférence manuelle,
    // sinon on suit les paramètres par défaut
    fun isDarkMode(context: Context): Boolean {
        val prefs = getPreferences(context)
        return if (prefs.getBoolean(KEY_USER_SET, false)) {
            // L'utilisateur a défini une préférence, on l'utilise
            prefs.getBoolean(KEY_DARK_MODE, false)
        } else {
            // Aucune préférence définie, on suit le système
            val nightModeFlags = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            nightModeFlags == Configuration.UI_MODE_NIGHT_YES
        }
    }

    // Définit le mode sombre selon la préférence utilisateur
    // Cette fonction sauvegarde également le fait que l'utilisateur a fait un choix manuel
    fun setDarkMode(context: Context, isDarkMode: Boolean) {
        getPreferences(context).edit()
            .putBoolean(KEY_DARK_MODE, isDarkMode)
            .putBoolean(KEY_USER_SET, true)
            .apply()
    }
}