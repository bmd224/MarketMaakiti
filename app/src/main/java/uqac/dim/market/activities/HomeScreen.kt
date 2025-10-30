package uqac.dim.market.activities

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Category
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import uqac.dim.market.ui.theme.MarketTheme
import uqac.dim.market.utils.ThemeManager
import java.util.Locale
import com.google.firebase.firestore.FirebaseFirestore
import android.widget.Toast
import androidx.compose.foundation.background
import com.google.firebase.firestore.Query
import kotlinx.coroutines.delay
import uqac.dim.market.gestionAnnonces.AddAnnonceDialog
import uqac.dim.market.annonces.vehicules.DetailAnnonceActivity
import uqac.dim.market.annonces.autres.DetailAutreAnnonceActivity
import uqac.dim.market.gestionProfil.ProfilActivity
import uqac.dim.market.utils.StatusUtils

// J'ai créé cette classe pour simplifier l'affichage des types d'annonces
// (véhicules et autres produits) dans une seule interface
data class AnnonceUnifiee(
    val id: String,
    val titre: String,
    val prix: String,
    val description: String,
    val imageUrls: List<String>,
    val userId: String,
    val statut: String,
    val datePublication: Long,
    val type: String // on distingue "vehicule" ou "autre" pour filtrer plus tard
)

// Structure pour organiser nos catégories avec leur icones (DirectionsCar, ShoppingCart)
data class ElementCategorie(
    val name: String,
    val icon: ImageVector,
    val type: String // permet de savoir quel type d'annonce afficher
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current

    // Je surveille les changements de thème avec une approche plus fluide
    var isDarkMode by remember { mutableStateOf(ThemeManager.isDarkMode(context)) }
    var verificationThemeEnCours by remember { mutableStateOf(false) }

    // Je vérifie le thème de façon plus intelligente pour éviter le rechargement constant
    LaunchedEffect(Unit) {
        while (true) {
            delay(500) // J'augmente l'intervalle pour réduire la charge
            val currentTheme = ThemeManager.isDarkMode(context)
            if (currentTheme != isDarkMode) {
                verificationThemeEnCours = true
                delay(50) // Court délai
                isDarkMode = currentTheme
                verificationThemeEnCours = false
            }
        }
    }

    // etats pour gérer l'interface utilisateur
    var ongletSelectionne by remember { mutableStateOf("Pour vous") }
    val tabs = listOf("vendre", "Pour vous", "Catégories")
    var annoncesUnifiees by remember { mutableStateOf<List<AnnonceUnifiee>>(emptyList()) }
    var afficherDialogue by remember { mutableStateOf(false) }

    // barre de recherche
    var afficherBarreRecherche by remember { mutableStateOf(false) }
    var requeteRecherche by remember { mutableStateOf("") }

    // Pour les filtres par catégorie
    var categorieSelectionnee by remember { mutableStateOf("all") }

    var chargementEnCours by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    // Mes catégories principales avec leurs icones
    val categories = listOf(
        ElementCategorie("Véhicules", Icons.Default.DirectionsCar, "vehicule"),
        ElementCategorie("Autres", Icons.Default.ShoppingCart, "autre"),
        ElementCategorie("Toutes les catégories", Icons.Default.Category, "all") // Toutes les catégories
    )

    // Cette fonction charge toutes les annonces depuis Firebase
    // J'utilise des listeners pour ecouter en temps réel et que l'affichage se mette à jour
    fun chargerToutesLesAnnonces() {
        try {
            val db = FirebaseFirestore.getInstance()

            // 1er listener pour les annonces de vehicules
            db.collection("annonces")
                .orderBy("datePublication", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Toast.makeText(context, "Erreur de chargement: ${error.message}", Toast.LENGTH_SHORT).show()
                        chargementEnCours = false
                        return@addSnapshotListener
                    }

                    // Je transforme chaque document Firebase en AnnonceUnifiee
                    val vehiculesAnnonces = snapshot?.documents?.mapNotNull { document ->
                        try {
                            val id = document.id
                            val marque = document.getString("marque") ?: ""
                            val modele = document.getString("modele") ?: ""
                            val annee = document.getString("annee") ?: ""
                            val prix = document.getString("prix") ?: ""
                            val description = document.getString("description") ?: ""
                            val userId = document.getString("userId") ?: ""
                            val statut = document.getString("statut") ?: "disponible"
                            val datePublication = document.getLong("datePublication") ?: System.currentTimeMillis()

                            // Pour les images parfois on a plusieurs URLs, parfois une seule
                            val imageUrls = mutableListOf<String>()
                            val imageUrlsStr = document.getString("imageUrls")
                            if (!imageUrlsStr.isNullOrBlank()) {
                                imageUrls.addAll(imageUrlsStr.split(",").filter { it.isNotBlank() })
                            }
                            val imageUrlUnique = document.getString("imageUrl")
                            if (!imageUrlUnique.isNullOrBlank() && imageUrls.isEmpty()) {
                                imageUrls.add(imageUrlUnique)
                            }

                            // Je construis un titre lisible à partir des infos du véhicule
                            val titre = when {
                                marque.isNotEmpty() && modele.isNotEmpty() && annee.isNotEmpty() -> "$marque $modele $annee"
                                marque.isNotEmpty() -> marque
                                else -> "Véhicule"
                            }

                            AnnonceUnifiee(
                                id = id,
                                titre = titre,
                                prix = prix,
                                description = description,
                                imageUrls = imageUrls,
                                userId = userId,
                                statut = statut,
                                datePublication = datePublication,
                                type = "vehicule"
                            )
                        } catch (e: Exception) {
                            // Si une annonce pose problème, on l'ignore plutot que de crasher
                            null
                        }
                    } ?: emptyList()

                    // 2e listener pour les autres types d'annonces
                    db.collection("autre_annonces")
                        .orderBy("datePublication", Query.Direction.DESCENDING)
                        .addSnapshotListener { autresSnapshot, autresError ->
                            if (autresError != null) {
                                Toast.makeText(context, "Erreur de chargement: ${autresError.message}", Toast.LENGTH_SHORT).show()
                                chargementEnCours = false
                                return@addSnapshotListener
                            }

                            // meme traitement pour les autres annonces
                            val autresAnnonces = autresSnapshot?.documents?.mapNotNull { document ->
                                try {
                                    val id = document.id
                                    val titre = document.getString("titre") ?: ""
                                    val description = document.getString("description") ?: ""
                                    val prix = document.getString("prix") ?: ""
                                    val userId = document.getString("userId") ?: ""
                                    val statut = document.getString("statut") ?: "disponible"
                                    val datePublication = document.getLong("datePublication") ?: System.currentTimeMillis()

                                    val imageUrls = mutableListOf<String>()
                                    val imageUrlsStr = document.getString("imageUrls")
                                    if (!imageUrlsStr.isNullOrBlank()) {
                                        imageUrls.addAll(imageUrlsStr.split(",").filter { it.isNotBlank() })
                                    }
                                    val imageUrlUnique = document.getString("imageUrl")
                                    if (!imageUrlUnique.isNullOrBlank() && imageUrls.isEmpty()) {
                                        imageUrls.add(imageUrlUnique)
                                    }

                                    AnnonceUnifiee(
                                        id = id,
                                        titre = titre,
                                        prix = prix,
                                        description = description,
                                        imageUrls = imageUrls,
                                        userId = userId,
                                        statut = statut,
                                        datePublication = datePublication,
                                        type = "autre"
                                    )
                                } catch (e: Exception) {
                                    null
                                }
                            } ?: emptyList()

                            // On combine tout et on trie par date (les plus recentes d'abord)
                            val toutesAnnonces = (vehiculesAnnonces + autresAnnonces)
                                .sortedByDescending { it.datePublication }

                            // Et on met à jour l'affichage
                            annoncesUnifiees = toutesAnnonces
                            chargementEnCours = false // chargement terminé
                        }
                }
        } catch (e: Exception) {
            Toast.makeText(context, "Erreur: ${e.message}", Toast.LENGTH_SHORT).show()
            chargementEnCours = false // chargement termine
        }
    }

    // On lance le chargement des que l'écran s'affiche
    LaunchedEffect(Unit) {
        chargerToutesLesAnnonces()
    }

    // Interface principale avec gestion fluide du changement de thème
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            Text(
                                "Maakiti",
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        actions = {
                            // Bouton profil
                            IconButton(onClick = {
                                val intent = Intent(context, ProfilActivity::class.java)
                                context.startActivity(intent)
                            }) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = "Profil",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            // Bouton recherche j'alterne entre search et close selon l'état
                            IconButton(onClick = {
                                afficherBarreRecherche = !afficherBarreRecherche
                                if (!afficherBarreRecherche) {
                                    requeteRecherche = "" // on vide la recherche quand on ferme
                                }
                            }) {
                                Icon(
                                    if (afficherBarreRecherche) Icons.Default.Close else Icons.Default.Search,
                                    contentDescription = if (afficherBarreRecherche) "Fermer recherche" else "Rechercher",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    )

                    // Barre de recherche qui apparait/disparait
                    if (afficherBarreRecherche) {
                        OutlinedTextField(
                            value = requeteRecherche,
                            onValueChange = { requeteRecherche = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            placeholder = {
                                Text(
                                    "Rechercher une annonce...",
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }
            },
            floatingActionButton = {
                // Bouton + pour ajouter une annonce
                FloatingActionButton(
                    onClick = { afficherDialogue = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Text("+")
                }
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .padding(horizontal = 8.dp)
            ) {
                // Mes trois onglets principaux
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    tabs.forEach { tab ->
                        val selected = tab == ongletSelectionne
                        TextButton(onClick = {
                            ongletSelectionne = tab
                            if (tab == "vendre") afficherDialogue = true // raccourci pour ajouter une annonce
                            if (tab != "Categories") {
                                categorieSelectionnee = "all"
                            }
                        }) {
                            Text(
                                text = tab,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (selected)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Affichage des catégories quand on est sur l'onglet Categories
                if (ongletSelectionne == "Catégories") {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Catégories principales",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                        )

                        // Je liste toutes mes catégories
                        categories.forEach { category ->
                            CarteCategorie(
                                category = category,
                                estSelectionne = categorieSelectionnee == category.type,
                                onClick = {
                                    categorieSelectionnee = category.type
                                    ongletSelectionne = "Pour vous" // retour automatique à la liste
                                }
                            )
                        }
                    }
                } else {
                    // Le contenu principal avec les annonces
                    if (chargementEnCours) {
                        // Petit spinner pendant le chargement
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        // Filtres
                        val filteredAnnonces = annoncesUnifiees.filter { annonce ->
                            // Filtre par catégorie d'abord
                            val categoryMatch = when (categorieSelectionnee) {
                                "vehicule" -> annonce.type == "vehicule"
                                "autre" -> annonce.type == "autre"
                                "all" -> true
                                else -> true
                            }

                            // Puis le filtre par texte de recherche
                            val searchMatch = if (requeteRecherche.isBlank()) {
                                true
                            } else {
                                val query = requeteRecherche.trim().lowercase(Locale.getDefault())
                                annonce.titre.lowercase(Locale.getDefault()).contains(query) ||
                                        annonce.description.lowercase(Locale.getDefault()).contains(query)
                            }
                            categoryMatch && searchMatch
                        }

                        // Feedback utilisateur: résultats trouvés
                        if (afficherBarreRecherche && requeteRecherche.isNotBlank()) {
                            Text(
                                text = "${filteredAnnonces.size} résultat(s) trouvé(s)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        // Indicateur de catégorie avec possibilité de l'effacer
                        if (categorieSelectionnee != "all") {
                            val categoryName = categories.find { it.type == categorieSelectionnee }?.name ?: ""
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Catégorie: $categoryName",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    TextButton(
                                        onClick = { categorieSelectionnee = "all" }
                                    ) {
                                        Text(
                                            "Effacer",
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }

                        // Grille des annonces -j'ai fait 2 colonnes
                        if (filteredAnnonces.isNotEmpty()) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(8.dp)
                            ) {
                                items(filteredAnnonces) { annonce ->
                                    CarteAnnonce(annonce = annonce)
                                }
                            }
                        } else if (afficherBarreRecherche && requeteRecherche.isNotBlank()) {
                            // Quand la recherche ne donne rien
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "Aucune annonce trouvée",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                    Text(
                                        text = "Essayez avec d'autres mots clés",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        } else if (categorieSelectionnee != "all" && filteredAnnonces.isEmpty()) {
                            // Quand une catégorie est vide
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "Aucune annonce dans cette catégorie",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                    TextButton(
                                        onClick = { categorieSelectionnee = "all" }
                                    ) {
                                        Text("Voir toutes les annonces")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Vérification du thème
        if (verificationThemeEnCours) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.3f))
            )
        }
    }

    // Dialogue pour ajouter une nouvelle annonce
    if (afficherDialogue) {
        AddAnnonceDialog(
            afficherDialogue = afficherDialogue,
            onDismiss = { afficherDialogue = false }
        )
    }
}

// Composable pour afficher chaque catégorie dans l'onglet "Catégories"
// J'ai crée cette carte pour permettre à l'utilisateur de filtrer facilement les annonces
// par type (véhicules, autres, ou toutes). Le design s'adapte selon si c'est sélectionné ou non
@Composable
fun CarteCategorie(
    category: ElementCategorie,
    estSelectionne: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (estSelectionne)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icones des catégories
            Icon(
                imageVector = category.icon,
                contentDescription = category.name,
                modifier = Modifier.size(32.dp),
                tint = if (estSelectionne)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))

            // Nom de la catégorie
            Text(
                text = category.name,
                style = MaterialTheme.typography.titleMedium,
                color = if (estSelectionne)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// Composable pour afficher chaque annonce dans la grille
@Composable
fun CarteAnnonce(annonce: AnnonceUnifiee) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth()
            .clickable {
                // Navigation différente selon le type d'annonce
                // Chaque type a sa propre activité de détail avec ses spécificités
                val intent = if (annonce.type == "vehicule") {
                    Intent(context, DetailAnnonceActivity::class.java).apply {
                        putExtra("annonceId", annonce.id)
                    }
                } else {
                    Intent(context, DetailAutreAnnonceActivity::class.java).apply {
                        putExtra("annonceId", annonce.id)
                    }
                }
                context.startActivity(intent) // Lance l'activité
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        // Box me permet de superposer le badge de statut sur l'image
        Box {
            Column(modifier = Modifier.padding(8.dp)) {
                // Image principale de l'annonce
                if (annonce.imageUrls.isNotEmpty()) {
                    AsyncImage(
                        model = annonce.imageUrls.firstOrNull(), // On prend la première image de la liste
                        contentDescription = annonce.titre,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    // Placeholder si pas d'image disponible
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Aucune image disponible",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Titre avec gestion du statut (barré si VENDU/gris si EN ATTENTE)
                Text(
                    text = annonce.titre,
                    style = MaterialTheme.typography.bodyMedium,
                    textDecoration = if (StatusUtils.barrerTexte(annonce.statut))
                        TextDecoration.LineThrough else TextDecoration.None,
                    color = MaterialTheme.colorScheme.onSurface.copy(
                        alpha = StatusUtils.getTextOpacity(annonce.statut)
                    ),
                    maxLines = 2 // Limite à 2 lignes dans la grille
                )

                // Prix formaté
                val prixFormate = if (annonce.prix.isNotEmpty()) {
                    try {
                        val prixNumerique = annonce.prix.toLong()
                        String.format(Locale.getDefault(), "%,d", prixNumerique)
                    } catch (e: NumberFormatException) {
                        annonce.prix
                    }
                } else {
                    annonce.prix
                }

                Text(
                    text = "$prixFormate $",
                    style = MaterialTheme.typography.bodySmall,
                    textDecoration = if (StatusUtils.barrerTexte(annonce.statut))
                        TextDecoration.LineThrough else TextDecoration.None,
                    color = MaterialTheme.colorScheme.onSurface.copy(
                        alpha = StatusUtils.getTextOpacity(annonce.statut)
                    )
                )

                // Badge pour identifier le type d'annonce (véhicule,autres)
                // Les couleurs sont un peu différentes pour chaque type
                Card(
                    modifier = Modifier.padding(top = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (annonce.type == "vehicule")
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Text(
                        text = if (annonce.type == "vehicule") "Véhicule" else "Autres",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (annonce.type == "vehicule")
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            // Badge de statut
            if (StatusUtils.afficherBadgeStatut(annonce.statut)) {
                val (badgeColor, textColor) = StatusUtils.getStatusBadgeColors(annonce.statut)

                Card(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    colors = CardDefaults.cardColors(containerColor = badgeColor)
                ) {
                    Text(
                        text = StatusUtils.getStatusDisplayText(annonce.statut),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = textColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// preview de ma page
@Preview(showBackground = true)
@Composable
fun PreviewHomeScreen() {
    MarketTheme {
        HomeScreen()
    }
}