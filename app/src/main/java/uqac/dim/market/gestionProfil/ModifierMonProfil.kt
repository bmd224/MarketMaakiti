package uqac.dim.market.gestionProfil

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import uqac.dim.market.theme.Footer
import uqac.dim.market.ui.theme.MarketTheme
import uqac.dim.market.utils.ThemeManager
import uqac.dim.market.utils.UserProfileUtils

// Activité principale qui gère l'affichage de l'écran de modification de profil
class ModifierMonProfilActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MarketTheme(
                // Application du thème
                darkTheme = ThemeManager.isDarkMode(this@ModifierMonProfilActivity)
            ) {
                ModifierMonProfilScreen()
            }
        }
    }
}

// Écran permettant à l'utilisateur de modifier son avatar de profil
// J'affiche une grille d'avatars disponibles que l'utilisateur peut choisir
// Une fois sélectionné, l'avatar est sauvegardé dans Firebase et l'utilisateur est
// redirigé vers son écran de profil principal
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModifierMonProfilScreen() {
    val context = LocalContext.current // contexte
    val coroutineScope = rememberCoroutineScope() // Scope pour les coroutines

    // État pour gérer l'affichage du loading pendant la sauvegarde
    var chargementEnCours by remember { mutableStateOf(false) }

    // État qui contient l'ID de l'avatar actuellement sélectionné par l'utilisateur
    // Il est null au début, puis chargé depuis Firebase
    var idAvatarActuel by remember { mutableStateOf<Int?>(null) }

    // Avant que je fasse cette modification, j'avais un temps fou avant
    // que l'avatar ne se mette a jour et la redirection vers MonProfilActivity
    // L'etat pour contrôler si on doit rediriger ou pas
    var doitRediriger by remember { mutableStateOf(false) }

    // Redirection
    fun retournerVersProfil() {
        (context as ComponentActivity).finish() // Ferme l'activité actuelle
    }

    // On récupère l'avatar actuel de l'utilisateur depuis Firebase pour l'afficher
    LaunchedEffect(Unit) {
        idAvatarActuel = UserProfileUtils.recupererAvatarUtilisateurActuel()
    }

    LaunchedEffect(doitRediriger) {
        if (doitRediriger) {
            retournerVersProfil()
        }
    }

    // Interface
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Choisir un avatar") },
                navigationIcon = {
                    IconButton(onClick = {
                        // Retour vers l'écran de profil principal
                        retournerVersProfil()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        },
        bottomBar = {
            // Je reutilise mon footer en bas
            Footer()
        }
    ) { paddingValues ->
        // Contenu de l'écran
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Titre de la page
            Text(
                text = "Sélectionner mon avatar",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Affichage conditionnel: soit le loading, soit la grille d'avatars
            if (chargementEnCours) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                // Grille des avatars disponibles organisée en 2 colonnes
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp), // Marge autour de la grille
                    horizontalArrangement = Arrangement.spacedBy(16.dp), // Espacement horizontal entre les éléments
                    verticalArrangement = Arrangement.spacedBy(16.dp) // Espacement vertical entre les éléments
                ) {
                    // Pour chaque avatar disponible, on crée une carte cliquable
                    items(UserProfileUtils.avatarsDisponibles) { avatarRessourceId ->
                        AvatarCard(
                            avatarRessourceId = avatarRessourceId, // ID de la ressource de l'avatar
                            estSelectionne = idAvatarActuel == avatarRessourceId,
                            onClick = {
                                // Si c'est déjà sélectionné, on ne fait rien
                                if (idAvatarActuel == avatarRessourceId) return@AvatarCard

                                // Mise à jour de l'interface
                                idAvatarActuel = avatarRessourceId

                                // Lancement d'une coroutine pour la sauvegarde asynchrone
                                coroutineScope.launch {
                                    // Appel à la fonction utilitaire pour sauvegarder dans Firebase
                                    val success = UserProfileUtils.sauvegarderAvatarUtilisateurActuel(avatarRessourceId)
                                    // Sauvegarde réussie
                                    if (success) {
                                        // Toast puis redirection immédiate
                                        Toast.makeText(context, "Votre avatar a été mis à jour", Toast.LENGTH_SHORT).show()
                                        doitRediriger = true
                                    } else {
                                        // En cas d'erreur
                                        Toast.makeText(context, "Erreur de sauvegarde", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

// Composant qui représente une carte d'avatar individuelle
// Affiche l'image de l'avatar avec un indicateur visuel(checkmark) si il est sélectionné
@Composable
fun AvatarCard(
    avatarRessourceId: Int,
    estSelectionne: Boolean,
    onClick: () -> Unit
) {
    // Conteneur principal
    Box(
        modifier = Modifier
            .size(120.dp)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        // Image de l'avatar
        Image(
            painter = painterResource(id = avatarRessourceId),
            contentDescription = "Avatar",
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )

        // Indicateur de sélection affiché seulement si l'avatar est sélectionné
        if (estSelectionne) {
            Card(
                shape = CircleShape,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(24.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    // Checkmark pour indiquer la sélection
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Sélectionné",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

// Preview
@Preview(showBackground = true)
@Composable
fun ModifierMonProfilPreview() {
    MarketTheme {
        ModifierMonProfilScreen()
    }
}