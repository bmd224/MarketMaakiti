package uqac.dim.market.gestionAnnonces

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import uqac.dim.market.annonces.autres.DetailAutreAnnonceActivity
import uqac.dim.market.ui.theme.MarketTheme
import androidx.compose.ui.tooling.preview.Preview as preview
import java.util.Locale
import uqac.dim.market.utils.ThemeManager
import uqac.dim.market.annonces.vehicules.DetailAnnonceActivity
import uqac.dim.market.utils.StatusUtils

// Activité principale pour l'affichage des annonces de l'utilisateur
class MesAnnoncesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MarketTheme(
                darkTheme = ThemeManager.isDarkMode(this@MesAnnoncesActivity)
            ) {
                MesAnnoncesScreenActivity()
            }
        }
    }
}

// Classe scellée pour représenter tous les types d'annonces dans l'application
// J'ai choisi ici une classe scellée pour pouvoir gérer différents types d'annonces
// de manière type-safe(erreurs de type) et éviter les erreurs de cast
sealed class AnnonceUniverselle {

    // Propriétés communes à tous les types d'annonces
    abstract val id: String
    abstract val userId: String
    abstract val prix: String
    abstract val statut: String // Valeurs possibles: "disponible", "en_attente", "vendu"
    abstract val datePublication: Long
    abstract val imageUrls: List<String>
    abstract val imageUrl: String

    // Sous classe pour les annonces de véhicules
    data class Vehicule(
        override val id: String,
        override val userId: String,
        override val prix: String,
        override val statut: String,
        override val datePublication: Long,
        override val imageUrls: List<String>,
        override val imageUrl: String,
        val marque: String,
        val modele: String,
        val annee: String,
        val description: String,
        val kilometrage: String,
        val proprietaires: String,
        val boite: String
    ) : AnnonceUniverselle()

    // Sous classe pour les types d'annonces autres
    data class Autre(
        override val id: String,
        override val userId: String,
        override val prix: String,
        override val statut: String,
        override val datePublication: Long,
        override val imageUrls: List<String>,
        override val imageUrl: String,
        val titre: String,
        val description: String
    ) : AnnonceUniverselle()
}

// Activité principale pour l'affichage des annonces de l'utilisateur
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MesAnnoncesScreenActivity() {
    // Récupération des données
    val context = LocalContext.current // contexte
    // État pour stocker toutes les annonces (véhicules + autres)
    var annonces by remember { mutableStateOf<List<AnnonceUniverselle>>(emptyList()) }
    // État pour gérer l'affichage du spinner de chargement
    var chargementEnCours by remember { mutableStateOf(true) }
    // Récupération de l'utilisateur connecté
    val user = FirebaseAuth.getInstance().currentUser

    // Lancé au démarrage pour charger les annonces
    // J'ecoute en temps réel toutes les annonces en cas de modifications
    // Aussi, je combine les deux listes et les trie par date de publication
    LaunchedEffect(Unit) {
        user?.let { currentUser ->
            try {
                val db = FirebaseFirestore.getInstance()

                // 1er listener j'écoute les changements en temps réel pour les annonces de véhicules
                db.collection("annonces")
                    .whereEqualTo("userId", currentUser.uid)
                    .addSnapshotListener { snapshot, error ->
                        // Gestion des erreurs Firebase
                        if (error != null) {
                            Toast.makeText(context, "Erreur de chargement: ${error.message}", Toast.LENGTH_SHORT).show()
                            chargementEnCours = false
                            return@addSnapshotListener
                        }

                        snapshot?.let { querySnapshot ->
                            // Traitement de chaque document véhicule
                            val vehiculesAnnonces = querySnapshot.documents.mapNotNull { document ->
                                try {
                                    // Extraction de tous les champs
                                    val id = document.id
                                    val marque = document.getString("marque") ?: ""
                                    val modele = document.getString("modele") ?: ""
                                    val annee = document.getString("annee") ?: ""
                                    val prix = document.getString("prix") ?: ""
                                    val description = document.getString("description") ?: ""
                                    val userId = document.getString("userId") ?: ""
                                    val kilometrage = document.getString("kilometrage") ?: ""
                                    val proprietaires = document.getString("proprietaires") ?: ""
                                    val boite = document.getString("boite") ?: ""
                                    val datePublication = document.getLong("datePublication") ?: System.currentTimeMillis()
                                    val statut = document.getString("statut") ?: "disponible" // Valeur par défaut

                                    // Gestion des URLs d'images
                                    // D'abord, chercher le champ "imageUrls"
                                    // Si vide, utiliser "imageUrl" unique
                                    val imageUrls = mutableListOf<String>()
                                    val imageUrlsStr = document.getString("imageUrls")
                                    if (!imageUrlsStr.isNullOrBlank()) {
                                        // Parsing des URLs séparées par virgule
                                        imageUrls.addAll(imageUrlsStr.split(",").filter { it.isNotBlank() })
                                    }
                                    val imageUrlUnique = document.getString("imageUrl")
                                    if (!imageUrlUnique.isNullOrBlank() && imageUrls.isEmpty()) {
                                        // Fallback vers l'image unique
                                        imageUrls.add(imageUrlUnique)
                                    }

                                    // Création de l'objet AnnonceUniverselle.Vehicule
                                    AnnonceUniverselle.Vehicule(
                                        id = id,
                                        marque = marque,
                                        modele = modele,
                                        annee = annee,
                                        prix = prix,
                                        description = description,
                                        userId = userId,
                                        kilometrage = kilometrage,
                                        proprietaires = proprietaires,
                                        boite = boite,
                                        datePublication = datePublication,
                                        statut = statut,
                                        imageUrls = imageUrls,
                                        imageUrl = imageUrls.firstOrNull() ?: ""
                                    )
                                } catch (e: Exception) {
                                    // En cas d'erreur sur un document, on l'ignore simplement
                                    // Cela m'évite qu'une annonce corrompue fasse planter tout l'affichage
                                    null
                                }
                            }

                            // 2e listener maintenant j'écoute les autres types d'annonces
                            db.collection("autre_annonces")
                                .whereEqualTo("userId", currentUser.uid)
                                .addSnapshotListener { autresSnapshot, autresError ->
                                    // Gestion des erreurs pour les autres annonces
                                    if (autresError != null) {
                                        Toast.makeText(context, "Erreur de chargement: ${autresError.message}", Toast.LENGTH_SHORT).show()
                                        chargementEnCours = false
                                        return@addSnapshotListener
                                    }

                                    autresSnapshot?.let { autresQuerySnapshot ->
                                        // Traitement de chaque document autre
                                        val autresAnnonces = autresQuerySnapshot.documents.mapNotNull { document ->
                                            try {
                                                // Extraction des champs pour annonces autres
                                                val id = document.id
                                                val titre = document.getString("titre") ?: ""
                                                val description = document.getString("description") ?: ""
                                                val prix = document.getString("prix") ?: ""
                                                val userId = document.getString("userId") ?: ""
                                                val statut = document.getString("statut") ?: "disponible"
                                                val datePublication = document.getLong("datePublication") ?: System.currentTimeMillis()
                                                // Meme logique de gestion d'images que pour les véhicules
                                                val imageUrls = mutableListOf<String>()
                                                val imageUrlsStr = document.getString("imageUrls")
                                                if (!imageUrlsStr.isNullOrBlank()) {
                                                    imageUrls.addAll(imageUrlsStr.split(",").filter { it.isNotBlank() })
                                                }
                                                val imageUrlUnique = document.getString("imageUrl")
                                                if (!imageUrlUnique.isNullOrBlank() && imageUrls.isEmpty()) {
                                                    imageUrls.add(imageUrlUnique)
                                                }

                                                // Création de l'objet AnnonceUniverselle.Autre
                                                AnnonceUniverselle.Autre(
                                                    id = id,
                                                    titre = titre,
                                                    description = description,
                                                    prix = prix,
                                                    userId = userId,
                                                    statut = statut,
                                                    datePublication = datePublication,
                                                    imageUrls = imageUrls,
                                                    imageUrl = imageUrls.firstOrNull() ?: ""
                                                )
                                            } catch (e: Exception) {
                                                // En cas d'erreur sur un document, on l'ignore simplement. Cela m'évite qu'une annonce
                                                // fasse planter tout l'affichage (meme gestion que j'ai faite un peu plus haut)
                                                null
                                            }
                                        }

                                        // Je combine toutes les annonces et les trie par date de publication (plus récentes en premier)
                                        annonces = (vehiculesAnnonces + autresAnnonces).sortedByDescending { it.datePublication }
                                        chargementEnCours = false
                                    }
                                }
                        }
                    }
            } catch (e: Exception) {
                // Gestion d'erreur globale pour tout problème de configuration Firebase
                Toast.makeText(context, "Erreur: ${e.message}", Toast.LENGTH_SHORT).show()
                chargementEnCours = false
            }
        }
    }

    // Interface utilisateur avec la top bar
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mes annonces") },
                navigationIcon = {
                    IconButton(onClick = {
                        // Fermeture de l'activité et retour à l'écran précédent
                        (context as? ComponentActivity)?.finish()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            if (chargementEnCours) {
                // Chargement en cours
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (annonces.isEmpty()) {
                // Aucune annonce trouvée
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Aucune annonce trouvée",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Vous n'avez pas encore créé d'annonces",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // Affichage de la liste avec LazyColumn
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(annonces) { annonce ->
                        AnnonceUniverselleItem(
                            annonce = annonce,
                            onClick = {
                                // J'utilise when sur la classe scellée
                                // pour naviguer vers le bon écran de détail
                                when (annonce) {
                                    is AnnonceUniverselle.Vehicule -> {
                                        val intent = Intent(context, DetailAnnonceActivity::class.java)
                                        intent.putExtra("annonceId", annonce.id)
                                        context.startActivity(intent)
                                    }
                                    is AnnonceUniverselle.Autre -> {
                                        val intent = Intent(context, DetailAutreAnnonceActivity::class.java)
                                        intent.putExtra("annonceId", annonce.id)
                                        context.startActivity(intent)
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

// Composable pour afficher un item d'annonce dans la liste
// Le parametre annonce: L'annonce à afficher (peut être Vehicule ou Autre)
// Le parametre onClick: Callback exécuté lors du clic sur l'item
@Composable
fun AnnonceUniverselleItem(annonce: AnnonceUniverselle, onClick: () -> Unit) {
    val context = LocalContext.current // contexte
    var afficherMenu by remember { mutableStateOf(false) } // État pour afficher le menu contextuel
    var enMiseAJour by remember { mutableStateOf(false) } // Spinner lors des changements de statut

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Image de l'annonce
                val imageUrl = if (annonce.imageUrls.isNotEmpty()) {
                    annonce.imageUrls.first() // Première image de la liste
                } else if (annonce.imageUrl.isNotEmpty()) {
                    annonce.imageUrl // Image unique
                } else {
                   ""
                }

                Image(
                    painter = rememberAsyncImagePainter(model = imageUrl),
                    contentDescription = "Image de l'annonce",
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Informations de l'annonce
                // Véhicules: "Marque Modèle Année"
                // Autres: "Titre"
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    val titre = when (annonce) {
                        is AnnonceUniverselle.Vehicule -> "${annonce.marque} ${annonce.modele} ${annonce.annee}"
                        is AnnonceUniverselle.Autre -> annonce.titre
                    }

                    // Titre avec style selon le statut je reutilise StatusUtils ou j'ai deja defini les status
                    // Texte barré pour les articles vendus
                    // Opacité réduite pour les articles inactifs
                    // Style normal pour les articles disponibles
                    Text(
                        text = titre,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis, // Points de suspension si trop long
                        textDecoration = if (StatusUtils.barrerTexte(annonce.statut))
                            TextDecoration.LineThrough else TextDecoration.None,
                        color = MaterialTheme.colorScheme.onSurface.copy(
                            alpha = StatusUtils.getTextOpacity(annonce.statut)
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Je formatte le prix
                    // Pour un debut je convertis en nombre pour avec des espaces aux 3 chiffres
                    // Si échec, j'affiche le prix tel qu'enregistré
                    // ou si non message par défaut si prix vide
                    val prixFormate = if (annonce.prix.isNotEmpty()) {
                        try {
                            val prixNumerique = annonce.prix.toLong()
                            String.format(Locale.getDefault(), "%,d", prixNumerique)
                        } catch (e: NumberFormatException) {
                            annonce.prix
                        }
                    } else {
                        "Prix non spécifié"
                    }

                    // Affichage du prix avec styles adaptatifs selon le statut(comme j'avais fait un peu plus haut avec les annonces)
                    Text(
                        text = "$prixFormate $",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (StatusUtils.statutInactif(annonce.statut))
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        else
                            MaterialTheme.colorScheme.primary,
                        textDecoration = if (StatusUtils.barrerTexte(annonce.statut))
                            TextDecoration.LineThrough else TextDecoration.None
                    )

                    // Badge pour le type d'annonce
                    // Couleur différente selon le type (véhicule vs autres)
                    Card(
                        modifier = Modifier.padding(top = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = when (annonce) {
                                is AnnonceUniverselle.Vehicule -> MaterialTheme.colorScheme.primaryContainer
                                is AnnonceUniverselle.Autre -> MaterialTheme.colorScheme.secondaryContainer
                            }
                        )
                    ) {
                        Text(
                            text = when (annonce) {
                                is AnnonceUniverselle.Vehicule -> "Véhicule"
                                is AnnonceUniverselle.Autre -> "Autres"
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = when (annonce) {
                                is AnnonceUniverselle.Vehicule -> MaterialTheme.colorScheme.onPrimaryContainer
                                is AnnonceUniverselle.Autre -> MaterialTheme.colorScheme.onSecondaryContainer
                            }
                        )
                    }
                }

                // Menu contextuel avec options selon le statut
                // Le menu s'adapte selon l'état actuel de l'annonce:
                // Disponible: peut passer en attente ou vendu
                // En attente: peut revenir disponible ou passer vendu
                // Vendu: peut seulement revenir disponible
                Box {
                    IconButton(
                        onClick = { afficherMenu = !afficherMenu },
                        enabled = !enMiseAJour // Désactive le bouton si en cours de mise à jour
                    ) {
                        if (enMiseAJour) {
                            // Spinner pendant la mise à jour du statut
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "Plus d'options"
                            )
                        }
                    }

                    // Menu déroulant avec options selon le statut actuel
                    // Les options disponibles changent selon le statut actuel
                    // Cela évite les transitions invalides et guide l'utilisateur
                    DropdownMenu(
                        expanded = afficherMenu,
                        onDismissRequest = { afficherMenu = false }
                    ) {
                        when (annonce.statut) {
                            StatusUtils.STATUS_DISPONIBLE -> {
                                // Options depuis l'état "disponible"
                                DropdownMenuItem(
                                    text = { Text("Mettre en attente") },
                                    onClick = {
                                        afficherMenu = false
                                        mettreAJourStatutAnnonce(context, annonce, StatusUtils.STATUS_EN_ATTENTE) { enMiseAJour = it }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Marquer comme vendu") },
                                    onClick = {
                                        afficherMenu = false
                                        mettreAJourStatutAnnonce(context, annonce, StatusUtils.STATUS_VENDU) { enMiseAJour = it }
                                    }
                                )
                            }
                            StatusUtils.STATUS_EN_ATTENTE -> {
                                // Options depuis l'état "en attente"
                                DropdownMenuItem(
                                    text = { Text("Remettre en ligne") },
                                    onClick = {
                                        afficherMenu = false
                                        mettreAJourStatutAnnonce(context, annonce, StatusUtils.STATUS_DISPONIBLE) { enMiseAJour = it }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Marquer comme vendu") },
                                    onClick = {
                                        afficherMenu = false
                                        mettreAJourStatutAnnonce(context, annonce, StatusUtils.STATUS_VENDU) { enMiseAJour = it }
                                    }
                                )
                            }
                            StatusUtils.STATUS_VENDU -> {
                                // Options depuis l'état "vendu"
                                DropdownMenuItem(
                                    text = { Text("Remettre disponible") },
                                    onClick = {
                                        afficherMenu = false
                                        mettreAJourStatutAnnonce(context, annonce, StatusUtils.STATUS_DISPONIBLE) { enMiseAJour = it }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Badge de statut
            // Je le positionne en bas à droite de la carte avec positionnement absolu
            if (StatusUtils.afficherBadgeStatut(annonce.statut)) {
                val (badgeColor, textColor) = StatusUtils.getStatusBadgeColors(annonce.statut)

                Card(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
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

// Mettre à jour le statut d'une annonce dans firestore
// Active l'état de chargement (spinner)
// Détermine la collection Firebase selon le type d'annonce
// Effectue la mise à jour avec gestion d'erreur
// Affiche un message de confirmation ou d'erreur
// Désactive l'état de chargement
private fun mettreAJourStatutAnnonce(
    context: Context,
    annonce: AnnonceUniverselle,
    nouveauStatut: String,
    gererChargement: (Boolean) -> Unit // Callback pour gérer l'état de chargement de l'UI
) {
    gererChargement(true) // Activation du spinner de chargement

    // J'utilise le pattern matching when sur la classe scellée pour déterminer
    // dans quelle collection Firebase effectuer la mise à jour
    val collectionName = when (annonce) {
        is AnnonceUniverselle.Vehicule -> "annonces"
        is AnnonceUniverselle.Autre -> "autre_annonces"
    }

    // J'utilise une mise à jour simple car:
    // On ne modifie qu'un seul champ
    // Les listeners temps réel mettront à jour l'UI automatiquement
    FirebaseFirestore.getInstance()
        .collection(collectionName)
        .document(annonce.id)
        .update("statut", nouveauStatut)
        .addOnSuccessListener {
            // Mise à jour réussie
            Toast.makeText(context, StatusUtils.getSuccessMessage(nouveauStatut), Toast.LENGTH_SHORT).show()
            gererChargement(false) // Arrêt du spinner
        }
        .addOnFailureListener { exception ->
            // Erreurs
            Toast.makeText(context, "Erreur: ${exception.message}", Toast.LENGTH_SHORT).show()
            gererChargement(false)
        }
}

// preview de l'activité
@preview(showBackground = true)
@Composable
fun MesAnnoncesScreenPreview() {
    MarketTheme {
        MesAnnoncesScreenActivity()
    }
}