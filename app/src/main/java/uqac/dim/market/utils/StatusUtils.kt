package uqac.dim.market.utils

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Utilitaire centralisé pour gérer tous les statuts des annonces
// J'ai refactorisé mon code en utilisant le principe DRY(Don't Repeat Yourself) pour éviter de dupliquer le code pour chaque statut
// Donc je reutilise ce code dans les fichiers dont j'ai besoin
object StatusUtils {

    // Constantes pour les statuts
    const val STATUS_DISPONIBLE = "disponible"
    const val STATUS_EN_ATTENTE = "en_attente"
    const val STATUS_VENDU = "vendu"

    // Obtient le texte d'affichage pour un statut donné
    // Exemple: "disponible" -> "EN LIGNE"
    fun getStatusDisplayText(statut: String): String {
        return when (statut) {
            STATUS_DISPONIBLE -> "EN LIGNE"
            STATUS_EN_ATTENTE -> "EN ATTENTE"
            STATUS_VENDU -> "VENDU"
            else -> "INCONNU"
        }
    }

    // Obtient les couleurs de fond et de texte pour un statut donné
    // Les couleurs s'adaptent aussi en fonction du thème actuel de l'application
    @Composable
    fun getStatusBadgeColors(statut: String): Pair<Color, Color> {
        return when (statut) {
            STATUS_DISPONIBLE -> Pair(
                MaterialTheme.colorScheme.primaryContainer,
                MaterialTheme.colorScheme.onPrimaryContainer
            )
            STATUS_EN_ATTENTE -> Pair(
                MaterialTheme.colorScheme.tertiaryContainer,
                MaterialTheme.colorScheme.onTertiaryContainer
            )
            STATUS_VENDU -> Pair(
                Color.Companion.Red,
                Color.Companion.White
            )
            // couleurs neutres pour statut inconnu
            else -> Pair(
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    // Petite fonction qui détermine si un badge doit être affiché pour le statut
    // On n'affiche pas de badge pour "disponible"
    fun afficherBadgeStatut(statut: String): Boolean {
        return statut != STATUS_DISPONIBLE
    }

    // Détermine si le texte doit être barré selon le statut
    // Seules les annonces vendues sont barrées
    fun barrerTexte(statut: String): Boolean {
        return statut == STATUS_VENDU
    }

    // Détermine si l'annonce est dans un état "inactif" (style gris)
    // Je considere les annonces en attente et vendues sont considérées comme inactives
    fun statutInactif(statut: String): Boolean {
        return statut == STATUS_VENDU || statut == STATUS_EN_ATTENTE
    }

    // Le message de succès après changement de statut
    fun getSuccessMessage(nouveauStatut: String): String {
        return when (nouveauStatut) {
            STATUS_DISPONIBLE -> "Annonce remise en ligne"
            STATUS_EN_ATTENTE -> "Annonce mise en attente"
            STATUS_VENDU -> "Annonce marquée comme vendue"
            else -> "Statut mis à jour"
        }
    }

    // L'opacité du texte selon le statut
    // Les annonces inactives ont une opacité réduite
    fun getTextOpacity(statut: String): Float {
        return if (statutInactif(statut)) 0.6f else 1f
    }
}