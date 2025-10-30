package uqac.dim.market.gestionAnnonces

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import uqac.dim.market.annonces.autres.PostAutreAnnonceActivity
import uqac.dim.market.annonces.vehicules.PostVehiculeActivity

// Boite de dialogue permettant à l'utilisateur de choisir le type d'annonce à ajouter (vehicule ou autre type)
@Composable
fun AddAnnonceDialog(
    afficherDialogue: Boolean, // Indique si la boîte de dialogue doit être affichée
    onDismiss: () -> Unit // Fonction appelée lors de la fermeture de la boîte de dialogue
) {
    val context = LocalContext.current // Récupère le contexte actuel

    if (afficherDialogue) {
        // Affiche le dialogue avec deux options: Véhicule ou Autres
        AlertDialog(
            onDismissRequest = onDismiss, // Appelé si l'utilisateur ferme le dialogue
            title = {
                Text(
                    text = "Que voulez-vous vendre ?",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column {
                    // Option pour publier une annonce de véhicule
                    AnnonceOption(
                        icon = Icons.Default.DirectionsCar,
                        title = "Véhicule",
                        onClick = {
                            onDismiss()
                            val intent = Intent(context, PostVehiculeActivity::class.java) // Prépare l'intent pour l'activité de publication de véhicule
                            context.startActivity(intent) // Lance l'activité
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp)) // Espace entre les options

                    // Option pour publier une autre annonce
                    AnnonceOption(
                        icon = Icons.Default.ShoppingCart,
                        title = "Autres",
                        onClick = {
                            onDismiss() // Ferme la boîte de dialogue
                            val intent = Intent(context, PostAutreAnnonceActivity::class.java)
                            context.startActivity(intent) // Lance l'activité
                        }
                    )
                }
            },
            confirmButton = {}, // Pas de bouton de confirmation principal
            dismissButton = {
                // Bouton pour annuler et fermer la boîte de dialogue
                TextButton(onClick = onDismiss) {
                    Text(
                        "Annuler",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurface
        )
    }
}

// Éléments d'options affichés dans la boîte de dialogue pour choisir le type d'annonce
@Composable
fun AnnonceOption(icon: ImageVector, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick) // Rends l'option cliquable
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon, // Icône représentant l'option
            contentDescription = title,
            modifier = Modifier.size(30.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(16.dp)) // Espace entre l'icône et le texte
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
