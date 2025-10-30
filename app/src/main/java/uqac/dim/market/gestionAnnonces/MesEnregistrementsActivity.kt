package uqac.dim.market.gestionAnnonces

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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Delete
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
import kotlinx.coroutines.tasks.await
import uqac.dim.market.ui.theme.MarketTheme
import androidx.compose.ui.tooling.preview.Preview as preview
import java.util.Locale
import uqac.dim.market.utils.ThemeManager
import kotlinx.coroutines.launch
import uqac.dim.market.utils.StatusUtils
import android.util.Log
import uqac.dim.market.annonces.autres.DetailAutreAnnonceActivity
import uqac.dim.market.annonces.vehicules.DetailAnnonceActivity
import uqac.dim.market.data.models.AnnonceFavorite

// Activity pour afficher la liste des annonces enregistrées par l'utilisateur
// Cette page permet de voir tous les favoris et de naviguer vers leurs détails
// J'utilise une synchronisation temps réel avec Firebase pour mettre à jour automatiquement
// la liste et s'il ya des modifications je les detecte aussi
class MesEnregistrementsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MarketTheme(
                darkTheme = ThemeManager.isDarkMode(this@MesEnregistrementsActivity)
            ) {
                MesEnregistrementsScreen()
            }
        }
    }
}

// Composant de l'écran des favoris
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MesEnregistrementsScreen() {
    val context = LocalContext.current
    val idUtilisateurActuel = FirebaseAuth.getInstance().currentUser?.uid

    // États pour gérer la liste des favoris
    var favoris by remember { mutableStateOf<List<AnnonceFavorite>>(emptyList()) }
    var chargementEnCours by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    // Fonction pour charger les favoris depuis Firebase
    // J'utilise des listeners
    fun chargerFavoris() {
        if (idUtilisateurActuel == null) {
            chargementEnCours = false
            return
        }

        try {
            val db = FirebaseFirestore.getInstance()

            // Listener principal sur la collection favoris
            // Déclenche une mise à jour à chaque changement (ajout/suppression de favoris)
            db.collection("favoris")
                .whereEqualTo("userId", idUtilisateurActuel)
                .addSnapshotListener { favorisSnapshot, error ->
                    if (error != null) {
                        Log.e("MesEnregistrements", "Erreur de chargement des favoris", error)
                        Toast.makeText(context, "Erreur de chargement: ${error.message}", Toast.LENGTH_SHORT).show()
                        chargementEnCours = false
                        return@addSnapshotListener
                    }

                    // Si aucun favori
                    if (favorisSnapshot?.documents?.isEmpty() == true) {
                        favoris = emptyList()
                        chargementEnCours = false
                        return@addSnapshotListener
                    }

                    // Récupération des identifiants des favoris
                    val favorisDocuments = favorisSnapshot?.documents ?: emptyList()
                    if (favorisDocuments.isEmpty()) {
                        favoris = emptyList()
                        chargementEnCours = false
                        return@addSnapshotListener
                    }

                    // Extraire les IDs des annonces favorites sans oublier qu'on a 2 types d'annonces
                    val annoncesVehiculeIds = mutableListOf<String>()
                    val annoncesAutresIds = mutableListOf<String>()
                    val favorisMap = mutableMapOf<String, Long>() // Map annonceId -> dateAjout c'est pour conserver l'ordre d'ajout des favoris

                    // Récupération des dates d'ajout des favoris
                    for (favoriDoc in favorisDocuments) {
                        val annonceId = favoriDoc.getString("annonceId") ?: continue
                        val annonceType = favoriDoc.getString("annonceType") ?: "vehicule"
                        val dateAjout = favoriDoc.getLong("dateAjout") ?: System.currentTimeMillis()
                        // Ajout de la date d'ajout à la map
                        favorisMap[annonceId] = dateAjout

                        // Ajout de l'ID de l'annonce à la liste correspondante
                        if (annonceType == "vehicule") {
                            annoncesVehiculeIds.add(annonceId)
                        } else {
                            annoncesAutresIds.add(annonceId)
                        }
                    }

                    // Fonction pour traiter les annonces recues et finaliser la liste
                    fun traiterAnnonces(annoncesList: List<AnnonceFavorite>) {
                        favoris = annoncesList.sortedByDescending { it.dateAjoutFavori }
                        chargementEnCours = false
                    }

                    // Liste de toutes les annonces et synchronisation des 2 types d'annonces
                    val toutesLesAnnonces = mutableListOf<AnnonceFavorite>()
                    var listenersCompleted = 0
                    val totalListeners = if (annoncesVehiculeIds.isNotEmpty() && annoncesAutresIds.isNotEmpty()) 2
                    else if (annoncesVehiculeIds.isNotEmpty() || annoncesAutresIds.isNotEmpty()) 1
                    else 0

                    // Si aucun listener
                    if (totalListeners == 0) {
                        favoris = emptyList()
                        chargementEnCours = false
                        return@addSnapshotListener
                    }

                    // Listener temps réel pour les annonces véhicules
                    if (annoncesVehiculeIds.isNotEmpty()) {
                        db.collection("annonces")
                            .addSnapshotListener { annoncesSnapshot, annoncesError ->
                                if (annoncesError != null) {
                                    Log.e("MesEnregistrements", "Erreur lors du chargement des annonces véhicules", annoncesError)
                                    return@addSnapshotListener
                                }

                                // Filtrage et création de l'objet AnnonceFavorite pour véhicules
                                val annoncesVehiculeFavorites = annoncesSnapshot?.documents?.mapNotNull { doc ->
                                    val annonceId = doc.id
                                    if (annoncesVehiculeIds.contains(annonceId) && doc.exists()) {
                                        // Extraction des données véhicule
                                        val marque = doc.getString("marque") ?: ""
                                        val modele = doc.getString("modele") ?: ""
                                        val annee = doc.getString("annee") ?: ""
                                        val titre = if (marque.isNotEmpty() && modele.isNotEmpty() && annee.isNotEmpty()) {
                                            "$marque $modele $annee"
                                        } else if (marque.isNotEmpty()) {
                                            marque
                                        } else {
                                            "Véhicule"
                                        }

                                        // Images
                                        val imageUrls = mutableListOf<String>()
                                        val imageUrlsStr = doc.getString("imageUrls")
                                        if (!imageUrlsStr.isNullOrBlank()) {
                                            imageUrls.addAll(imageUrlsStr.split(",").filter { it.isNotBlank() })
                                        }
                                        val imageUrlUnique = doc.getString("imageUrl")
                                        if (!imageUrlUnique.isNullOrBlank() && imageUrls.isEmpty()) {
                                            imageUrls.add(imageUrlUnique)
                                        }

                                        // Création de l'objet AnnonceFavorite pour les véhicules
                                        AnnonceFavorite.creerDepuisVehicule(
                                            id = annonceId,
                                            userId = doc.getString("userId") ?: "",
                                            prix = doc.getString("prix") ?: "",
                                            statut = doc.getString("statut") ?: "disponible",
                                            datePublication = doc.getLong("datePublication") ?: System.currentTimeMillis(),
                                            imageUrls = imageUrls,
                                            imageUrl = imageUrls.firstOrNull() ?: "",
                                            marque = marque,
                                            modele = modele,
                                            annee = annee,
                                            dateAjoutFavori = favorisMap[annonceId] ?: System.currentTimeMillis()
                                        )
                                    } else {
                                        // Si l'annonce n'existe plus supprimer le favori
                                        if (annoncesVehiculeIds.contains(annonceId)) {
                                            db.collection("favoris").document("${idUtilisateurActuel}_${annonceId}").delete()
                                        }
                                        null
                                    }
                                } ?: emptyList()

                                // Synchroniser avec les autres types d'annonces
                                synchronized(toutesLesAnnonces) {
                                    toutesLesAnnonces.removeAll { it.type == "vehicule" }
                                    toutesLesAnnonces.addAll(annoncesVehiculeFavorites)
                                }

                                // J'incremente le compteur des listeners
                                listenersCompleted++
                                if (listenersCompleted >= totalListeners || annoncesAutresIds.isEmpty()) {
                                    traiterAnnonces(toutesLesAnnonces.toList())
                                }
                            }
                    }

                    // Listener temps réel pour les autres annonces
                    if (annoncesAutresIds.isNotEmpty()) {
                        db.collection("autre_annonces")
                            .addSnapshotListener { autresSnapshot, autresError ->
                                if (autresError != null) {
                                    Log.e("MesEnregistrements", "Erreur lors du chargement des autres annonces", autresError)
                                    return@addSnapshotListener
                                }

                                // Filtrage et création de l'objet AnnonceFavorite les annonces autres(j'utilise la meme logique que pour les véhicules)
                                val annoncesAutresFavorites = autresSnapshot?.documents?.mapNotNull { doc ->
                                    val annonceId = doc.id
                                    if (annoncesAutresIds.contains(annonceId) && doc.exists()) {
                                        val titre = doc.getString("titre") ?: "Annonce"

                                        // Images
                                        val imageUrls = mutableListOf<String>()
                                        val imageUrlsStr = doc.getString("imageUrls")
                                        if (!imageUrlsStr.isNullOrBlank()) {
                                            imageUrls.addAll(imageUrlsStr.split(",").filter { it.isNotBlank() })
                                        }
                                        // Image unique
                                        val imageUrlUnique = doc.getString("imageUrl")
                                        if (!imageUrlUnique.isNullOrBlank() && imageUrls.isEmpty()) {
                                            imageUrls.add(imageUrlUnique)
                                        }

                                        // Création de l'objet AnnonceFavorite pour les annonces autres
                                        AnnonceFavorite.creerDepuisAutreAnnonce(
                                            id = annonceId,
                                            userId = doc.getString("userId") ?: "",
                                            prix = doc.getString("prix") ?: "",
                                            statut = doc.getString("statut") ?: "disponible",
                                            datePublication = doc.getLong("datePublication") ?: System.currentTimeMillis(),
                                            imageUrls = imageUrls,
                                            imageUrl = imageUrls.firstOrNull() ?: "",
                                            titre = titre,
                                            dateAjoutFavori = favorisMap[annonceId] ?: System.currentTimeMillis()
                                        )
                                    } else {
                                        // Si l'annonce n'existe plus supprimer le favori
                                        if (annoncesAutresIds.contains(annonceId)) {
                                            db.collection("favoris").document("${idUtilisateurActuel}_${annonceId}").delete()
                                        }
                                        null
                                    }
                                } ?: emptyList()

                                // Synchroniser avec les annonces véhicules
                                synchronized(toutesLesAnnonces) {
                                    toutesLesAnnonces.removeAll { it.type == "autre" }
                                    toutesLesAnnonces.addAll(annoncesAutresFavorites)
                                }

                                // J'incremente le compteur des listeners
                                listenersCompleted++
                                if (listenersCompleted >= totalListeners || annoncesVehiculeIds.isEmpty()) {
                                    traiterAnnonces(toutesLesAnnonces.toList())
                                }
                            }
                    }
                }

        } catch (e: Exception) {
            Log.e("MesEnregistrements", "Erreur lors de l'initialisation", e)
            Toast.makeText(context, "Erreur: ${e.message}", Toast.LENGTH_SHORT).show()
            chargementEnCours = false
        }
    }

    // Chargement au démarrage
    LaunchedEffect(Unit) {
        chargerFavoris()
    }

    // Interface utilisateur j'utilise un Scaffold avec une top Bar
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Mes enregistrements")
                },
                navigationIcon = {
                    IconButton(onClick = {
                        (context as? ComponentActivity)?.finish()
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Affichage conditionnel selon l'état
            when {
                chargementEnCours -> {
                    // État de chargement
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Chargement de vos favoris...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                favoris.isEmpty() -> {
                    // État vide
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Favorite,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Aucune annonce enregistrée pour l'instant",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Parcourez les annonces et liker les pour qu'elles puissent apparaitre ici",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
                else -> {
                    // Compteur d'annonces favorites
                    Text(
                        text = "${favoris.size} annonce${if (favoris.size > 1) "s" else ""} enregistrée${if (favoris.size > 1) "s" else ""}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Liste des annonces favorites
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(favoris) { favori ->
                            AnnonceFavoriteCard(
                                favori = favori,
                                onClick = {
                                    // Navigation selon le type
                                    val intent = if (favori.type == "vehicule") {
                                        Intent(context, DetailAnnonceActivity::class.java)
                                    } else {
                                        Intent(context, DetailAutreAnnonceActivity::class.java)
                                    }
                                    intent.putExtra("annonceId", favori.id)
                                    context.startActivity(intent)
                                },
                                suppressionDesFavoris = {
                                    // Supprimer des favoris
                                    scope.launch {
                                        try {
                                            FirebaseFirestore.getInstance()
                                                .collection("favoris")
                                                .document("${idUtilisateurActuel}_${favori.id}")
                                                .delete()
                                                .await()

                                            Toast.makeText(context, "Vous avez retiré cette annonce des favoris", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            Log.e("MesEnregistrements", "Erreur lors de la suppression du favori", e)
                                            Toast.makeText(context, "Erreur: ${e.message}", Toast.LENGTH_SHORT).show()
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
}

// Composable pour afficher une carte de favori
// Gère l'affichage des informations et les actions de suppression
@Composable
fun AnnonceFavoriteCard(
    favori: AnnonceFavorite,
    onClick: () -> Unit,
    suppressionDesFavoris: () -> Unit
) {
    val context = LocalContext.current
    var afficherDialogueSupprimer by remember { mutableStateOf(false) }

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
                val imageUrl = if (favori.imageUrls.isNotEmpty()) {
                    favori.imageUrls.first()
                } else if (favori.imageUrl.isNotEmpty()) {
                    favori.imageUrl
                } else {
                    "Aucune Image"
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
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    // Titre avec badge type
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = favori.titre,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textDecoration = if (StatusUtils.barrerTexte(favori.statut))
                                TextDecoration.LineThrough else TextDecoration.None,
                            color = MaterialTheme.colorScheme.onSurface.copy(
                                alpha = StatusUtils.getTextOpacity(favori.statut)
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // Badge du type d'annonce
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (favori.type == "vehicule")
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Text(
                                text = if (favori.type == "vehicule") "Véhicule" else "Autres",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (favori.type == "vehicule")
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Prix formaté avec le style selon le statut
                    val prixFormate = if (favori.prix.isNotEmpty()) {
                        try {
                            val prixNumerique = favori.prix.toLong()
                            String.format(Locale.getDefault(), "%,d", prixNumerique)
                        } catch (e: NumberFormatException) {
                            favori.prix
                        }
                    } else {
                        "Prix non spécifié"
                    }

                    Text(
                        text = "$prixFormate $",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (StatusUtils.statutInactif(favori.statut))
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        else
                            MaterialTheme.colorScheme.primary,
                        textDecoration = if (StatusUtils.barrerTexte(favori.statut))
                            TextDecoration.LineThrough else TextDecoration.None
                    )
                }

                // Bouton supprimer des favoris(l'icone)
                IconButton(
                    onClick = { afficherDialogueSupprimer = true }
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Retirer des favoris",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            // Badge de statut, je reutilise StatusUtils
            if (StatusUtils.afficherBadgeStatut(favori.statut)) {
                val (badgeColor, textColor) = StatusUtils.getStatusBadgeColors(favori.statut)

                Card(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                    colors = CardDefaults.cardColors(containerColor = badgeColor)
                ) {
                    Text(
                        text = StatusUtils.getStatusDisplayText(favori.statut),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = textColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // Dialogue de confirmation de suppression
    if (afficherDialogueSupprimer) {
        AlertDialog(
            onDismissRequest = { afficherDialogueSupprimer = false },
            title = { Text("Retirer des favoris") },
            text = { Text("Voulez vous retirer cette annonce de vos favoris ?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        afficherDialogueSupprimer = false
                        suppressionDesFavoris()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Retirer")
                }
            },
            dismissButton = {
                TextButton(onClick = { afficherDialogueSupprimer = false }) {
                    Text("Annuler")
                }
            }
        )
    }
}

// Preview
@preview(showBackground = true)
@Composable
fun MesEnregistrementsScreenPreview() {
    MarketTheme {
        MesEnregistrementsScreen()
    }
}