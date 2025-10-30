package uqac.dim.market.annonces.vehicules

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*
import uqac.dim.market.ui.theme.MarketTheme
import uqac.dim.market.utils.ThemeManager
import uqac.dim.market.utils.StatusUtils
import kotlinx.coroutines.launch
import uqac.dim.market.data.models.AnnonceVehicule
import android.util.Log
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbUp
import uqac.dim.market.conversations.ChatActivity
import uqac.dim.market.annonces.autres.findActivity

// Activity principale pour afficher les détails d'une annonce de véhicule
// Cette classe gère l'affichage complet d'une annonce avec toutes ses infos
class DetailAnnonceActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Je récupère l'ID de l'annonce passé depuis l'activité précédente
        val annonceId = intent.getStringExtra("annonceId") ?: return

        setContent {
            // J'applique le thème ici
            MarketTheme(
                darkTheme = ThemeManager.isDarkMode(this@DetailAnnonceActivity)
            ) {
                DetailAnnonceScreen(annonceId = annonceId)
            }
        }
    }
}

// Écran composable qui affiche tous les détails d'une annonce
// Utilise addSnapshotListener pour synchronisation temps réel
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailAnnonceScreen(annonceId: String) {
    // États pour gérer les données et l'UI
    var annonce by remember { mutableStateOf<AnnonceVehicule?>(null) }
    var chargementEnCours by remember { mutableStateOf(true) }
    
    // États pour gérer les favoris
    var estFavori by remember { mutableStateOf(false) }
    var estFavoriLoading by remember { mutableStateOf(false) } // Chargement du bouton

    val idUtilisateurActuel = FirebaseAuth.getInstance().currentUser?.uid
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Fonction pour vérifier si l'annonce est en favori
    suspend fun verifierSiFavori() {
        if (idUtilisateurActuel != null) {
            try {
                val favSnapshot = FirebaseFirestore.getInstance()
                    .collection("favoris")
                    .document("${idUtilisateurActuel}_$annonceId")
                    .get()
                    .await()

                estFavori = favSnapshot.exists()
            } catch (e: Exception) {
                Log.e("DetailAnnonce", "Erreur lors de la vérification des favoris", e)
            }
        }
    }

    // Fonction pour ajouter/retirer des favoris
    fun ajouterRetirerFavori() {
        if (idUtilisateurActuel == null) return
        // Utilisation de la coroutine pour effectuer l'opération en arrière plan
        scope.launch {
            estFavoriLoading = true
            try {
                val db = FirebaseFirestore.getInstance()
                val favDocId = "${idUtilisateurActuel}_$annonceId"
                val favRef = db.collection("favoris").document(favDocId)

                if (estFavori) {
                    // Retirer des favoris
                    favRef.delete().await()
                    estFavori = false
                    Toast.makeText(context, "Vous avez retiré cette annonce des favoris", Toast.LENGTH_SHORT).show()
                    // Signaler le changement à l'activité parente
                    context.findActivity()?.setResult(Activity.RESULT_OK)
                } else {
                    // Ajouter aux favoris
                    val favData = hashMapOf(
                        "userId" to idUtilisateurActuel,
                        "annonceId" to annonceId,
                        "annonceType" to "vehicule", // Pour distinguer des autres types d'annonces
                        "dateAjout" to System.currentTimeMillis()
                    )
                    favRef.set(favData).await() // Enregistrement du document dans Firestore
                    estFavori = true
                    Toast.makeText(context, "Vous avez ajouté cette annonce aux favoris", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Erreur: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                estFavoriLoading = false
            }
        }
    }

    // Fonction avec addSnapshotListener pour synchronisation temps réel
    fun loadAnnonceVehiculeData() {
        if (annonceId.isEmpty()) {
            Log.e("DetailAnnonce", "ID d'annonce invalide: $annonceId")
            Toast.makeText(context, "ID d'annonce invalide", Toast.LENGTH_SHORT).show()

            chargementEnCours = false
            return
        }

        try {
            val db = FirebaseFirestore.getInstance() // Récupération de l'instance Firestore

            // Listener sur le document de l'annonce pour synchronisation automatique
            db.collection("annonces")
                .document(annonceId)
                .addSnapshotListener { snapshot, error ->

                    if (error != null) {
                        Log.e("DetailAnnonce", "Erreur lors du chargement de l'annonce $annonceId", error)
                        Toast.makeText(context, "Erreur lors du chargement: ${error.message}", Toast.LENGTH_SHORT).show()
                        chargementEnCours = false
                        return@addSnapshotListener
                    }

                    if (snapshot?.exists() == true) {
                        try {
                            // Extraction de toutes les données du document Firebase
                            val marque = snapshot.getString("marque") ?: ""
                            val modele = snapshot.getString("modele") ?: ""
                            val annee = snapshot.getString("annee") ?: ""
                            val proprietaires = snapshot.getString("proprietaires") ?: ""
                            val kilometrage = snapshot.getString("kilometrage") ?: ""
                            val boite = snapshot.getString("boite") ?: ""
                            val prix = snapshot.getString("prix") ?: ""
                            val description = snapshot.getString("description") ?: ""
                            val userId = snapshot.getString("userId") ?: ""
                            val timestamp = snapshot.get("datePublication") as? Long ?: System.currentTimeMillis()
                            val statut = snapshot.getString("statut") ?: StatusUtils.STATUS_DISPONIBLE

                            // On gère les images parfois c'est une liste, parfois une seule URL
                            val imageUrls = mutableListOf<String>()
                            val imageUrlsStr = snapshot.getString("imageUrls")
                            if (!imageUrlsStr.isNullOrBlank()) {
                                imageUrls.addAll(imageUrlsStr.split(",").filter { it.isNotBlank() })
                            }

                            // Une seule image
                            val imageUrlUnique = snapshot.getString("imageUrl")
                            if (!imageUrlUnique.isNullOrBlank() && imageUrls.isEmpty()) {
                                imageUrls.add(imageUrlUnique)
                            }

                            // Construction du titre combiné avec marque, modèle et année
                            val titre = if (marque.isNotEmpty() && modele.isNotEmpty() && annee.isNotEmpty()) {
                                "$marque $modele $annee"
                            } else if (marque.isNotEmpty()) {
                                marque
                            } else {
                                "Véhicule"
                            }

                            // Création de notre objet AnnonceVehicule avec toutes les données
                            annonce = AnnonceVehicule(
                                id = annonceId,
                                titre = titre,
                                marque = marque,
                                modele = modele,
                                annee = annee,
                                proprietaires = proprietaires,
                                kilometrage = kilometrage,
                                boite = boite,
                                prix = prix,
                                description = description,
                                imageUrls = imageUrls,
                                datePublication = timestamp,
                                userId = userId,
                                statut = statut
                            )

                            // Vérifier si c'est en favori après avoir chargé l'annonce
                            scope.launch {
                                verifierSiFavori()
                            }

                        } catch (e: Exception) {
                            Log.e("DetailAnnonce", "Erreur lors du traitement des données", e)
                            Toast.makeText(context, "Erreur lors du traitement des données: ${e.message}", Toast.LENGTH_SHORT).show()

                        }
                    } else {
                        Log.e("DetailAnnonce", "Annonce introuvable avec ID: $annonceId")
                        Toast.makeText(context, "Annonce introuvable", Toast.LENGTH_SHORT).show()
                    }

                    chargementEnCours = false
                }

        } catch (e: Exception) {
            Log.e("DetailAnnonce", "Erreur lors de l'initialisation", e)
            Toast.makeText(context, "Erreur lors de l'initialisation: ${e.message}", Toast.LENGTH_SHORT).show()
            chargementEnCours = false
        }
    }

    //  LaunchedEffect pour déclencher la fonction de chargement
    LaunchedEffect(annonceId) {
        loadAnnonceVehiculeData()
    }

    // Structure principale avec TopAppBar
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Détails de l'annonce") },
                navigationIcon = {
                    IconButton(onClick = {
                        // retour: je ferme cette activité
                        context.findActivity()?.finish()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { paddingValues ->
        // Gestion des différents états d'affichage
        when {
            chargementEnCours -> {
                // Écran de chargement avec indicateur circulaire
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Chargement...",
                            color = MaterialTheme.colorScheme.onBackground,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            annonce != null -> {
                // Affichage de l'annonce
                DetailContent(
                    annonce = annonce!!,
                    idUtilisateurActuel = idUtilisateurActuel,
                    modifier = Modifier.padding(paddingValues),
                    estFavori = estFavori,
                    estFavoriLoading = estFavoriLoading,
                    onajouterRetirerFavori = { ajouterRetirerFavori() }
                )
            }
        }
    }
}

// Composable principal qui affiche tout le contenu détaillé de l'annonce
@Composable
fun DetailContent(
    annonce: AnnonceVehicule,
    idUtilisateurActuel: String?,
    modifier: Modifier = Modifier,
    estFavori: Boolean = false,
    estFavoriLoading: Boolean = false,
    onajouterRetirerFavori: () -> Unit = {}
) {
    // États pour gérer les dialogues et l'affichage des images
    var dialogueOuvert by remember { mutableStateOf(false) }
    var indexImageActuelle by remember { mutableStateOf(0) }
    var afficherDialogueSupprimer by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val estVendeur = idUtilisateurActuel == annonce.userId

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Section galerie d'images avec pagination si plusieurs images
            if (annonce.imageUrls.isNotEmpty()) {
                val etatPagination = rememberPagerState(pageCount = { annonce.imageUrls.size })

                Box {
                    // Card contenant la galerie d'images
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        // HorizontalPager pour pouvoir swiper entre les images
                        HorizontalPager(
                            state = etatPagination,
                            modifier = Modifier.fillMaxSize()
                        ) { page ->
                            AsyncImage(
                                model = annonce.imageUrls[page],
                                contentDescription = "Image ${page + 1}",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable {
                                        // Clic sur l'image pour l'ouvrir en mode plein écran
                                        indexImageActuelle = page
                                        dialogueOuvert = true
                                    },
                                contentScale = ContentScale.Crop
                            )
                        }
                    }

                    // Boutons d'action pour le propriétaire de l'annonce
                    if (estVendeur) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                        ) {
                            Row(
                                modifier = Modifier.padding(4.dp)
                            ) {
                                // Bouton modifier avec vérification du statut
                                if (annonce.statut != StatusUtils.STATUS_VENDU) {
                                    IconButton(onClick = {
                                        val intent = Intent(context, ModifierAnnonceVehicule::class.java)
                                        intent.putExtra("annonceId", annonce.id)
                                        context.startActivity(intent)
                                    }) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "Modifier",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                // Bouton supprimer l'annonce
                                IconButton(onClick = {
                                    afficherDialogueSupprimer = true
                                }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Supprimer",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Indicateurs de pagination (...) si plusieurs images
                if (annonce.imageUrls.size > 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        repeat(annonce.imageUrls.size) { index ->
                            val estSelectionne = etatPagination.currentPage == index
                            Canvas(
                                modifier = Modifier
                                    .size(8.dp)
                                    .padding(horizontal = 2.dp)
                            ) {
                                drawCircle(
                                    color = if (estSelectionne) Color.Blue else Color.Gray
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // Section titre avec badge de statut et icône favoris utilisant StatusUtils
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {

                Text(
                    text = annonce.titre,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textDecoration = if (StatusUtils.barrerTexte(annonce.statut))
                        TextDecoration.LineThrough else TextDecoration.None,
                    color = MaterialTheme.colorScheme.onSurface.copy(
                        alpha = StatusUtils.getTextOpacity(annonce.statut)
                    ),
                    modifier = Modifier.weight(1f)
                )

                // Section icone favoris et badge de statut
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Icone favoris seulement pour les acheteurs (je pars du principe qu'un vendeur ne peut pas liker sa propre annonce)
                    if (!estVendeur && idUtilisateurActuel != null) {
                        IconButton(
                            onClick = onajouterRetirerFavori,
                            enabled = !estFavoriLoading
                        ) {
                            if (estFavoriLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    imageVector = if (estFavori) Icons.Default.ThumbUp else Icons.Outlined.ThumbUp,
                                    contentDescription = if (estFavori) "Vous avez retiré cette annonce des favoris" else "Vous avez ajouté cette annonce aux favoris",
                                    tint = if (estFavori) {
                                        // Couleur adaptée au thème
                                        if (ThemeManager.isDarkMode(context)) Color(0xFF64B5F6) else Color(0xFF1976D2)
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    // Badge de statut je reutilise StatusUtils
                    if (StatusUtils.afficherBadgeStatut(annonce.statut)) {
                        val (badgeColor, textColor) = StatusUtils.getStatusBadgeColors(annonce.statut)

                        Surface(
                            color = badgeColor,
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Text(
                                text = StatusUtils.getStatusDisplayText(annonce.statut),
                                color = textColor,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Prix avec formatage utilisant des espaces et style selon le statut
            val prixFormate = if (annonce.prix.isNotEmpty()) {
                try {
                    val prixNumerique = annonce.prix.toLong()
                    String.format(Locale.getDefault(), "%,d", prixNumerique).replace(",", " ")
                } catch (e: NumberFormatException) {
                    annonce.prix
                }
            } else {
                annonce.prix
            }

            Text(
                text = "$prixFormate $",
                style = MaterialTheme.typography.headlineMedium,
                color = if (StatusUtils.statutInactif(annonce.statut))
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                else
                    MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                textDecoration = if (StatusUtils.barrerTexte(annonce.statut))
                    TextDecoration.LineThrough else TextDecoration.None
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Date de publication formatée
            val dateFormatee = SimpleDateFormat("EEEE dd MMMM yyyy", Locale.FRENCH).format(Date(annonce.datePublication))
            Text(
                text = "Publiée le: $dateFormatee",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )

            // Section détails du véhicule on affiche seulement si on a des infos
            if (annonce.annee.isNotEmpty() || annonce.kilometrage.isNotEmpty() ||
                annonce.boite.isNotEmpty() || annonce.proprietaires.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Détails de l'annonce",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Affichage conditionnel de chaque détail (StatusUtils pour l'opacité)
                if (annonce.annee.isNotEmpty()) {
                    DetailRow(label = "Année", value = annonce.annee, statut = annonce.statut)
                }

                // kilometrage
                if (annonce.kilometrage.isNotEmpty()) {
                    val kmFormate = if (annonce.kilometrage.isNotEmpty()) {
                        try {
                            val kmNumerique = annonce.kilometrage.toLong()
                            "${String.format(Locale.getDefault(), "%,d", kmNumerique).replace(",", " ")} kilomètres au compteur"
                        } catch (e: NumberFormatException) {
                            "${annonce.kilometrage} kilomètres au compteur"
                        }
                    } else {
                        "${annonce.kilometrage} kilomètres au compteur"
                    }
                    DetailRow(label = "Kilométrage", value = kmFormate, statut = annonce.statut)
                }

                // Boite de vitesse, transmission
                if (annonce.boite.isNotEmpty()) {
                    val boiteFormat = when (annonce.boite) {
                        "Transmission automatique" -> "Automatique"
                        "Transmission manuelle" -> "Manuelle"
                        else -> annonce.boite
                    }
                    DetailRow(label = "Boîte de vitesse", value = boiteFormat, statut = annonce.statut)
                }

                // proprietaires
                if (annonce.proprietaires.isNotEmpty()) {
                    val proprioText = if (annonce.proprietaires == "1") {
                        "1 propriétaire"
                    } else {
                        "${annonce.proprietaires} propriétaires"
                    }
                    DetailRow(label = "Nombre de propriétaires", value = proprioText, statut = annonce.statut)
                }
            }

            // Section description avec style selon le statut
            if (annonce.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Description de l'annonce",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = annonce.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(
                        alpha = StatusUtils.getTextOpacity(annonce.statut)
                    )
                )
            }

            // Bouton contacter affiché seulement si disponible (je reutilise aussi StatusUtils)
            if (!estVendeur && idUtilisateurActuel != null && annonce.statut == StatusUtils.STATUS_DISPONIBLE) {
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        val currentUser = FirebaseAuth.getInstance().currentUser
                        if (currentUser != null) {
                            // Je crée un ID unique pour la conversation
                            val conversationId = "${currentUser.uid}_${annonce.userId}_${annonce.id}"
                            val intent = Intent(context, ChatActivity::class.java)
                            intent.putExtra("conversationId", conversationId)
                            intent.putExtra("sellerId", annonce.userId)
                            intent.putExtra("idReceveur", annonce.userId)
                            intent.putExtra("annonceId", annonce.id)
                            intent.putExtra("annonceTitle", annonce.titre)
                            context.startActivity(intent)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Contacter le vendeur")
                }
            }
        }
    }

    // Dialogue de confirmation pour supprimer l'annonce
    if (afficherDialogueSupprimer) {
        AlertDialog(
            onDismissRequest = { afficherDialogueSupprimer = false },
            title = {
                Text("Confirmer la suppression")
            },
            text = {
                Text("Êtes-vous sûr de vouloir supprimer cette annonce ? Cette action est irréversible.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        afficherDialogueSupprimer = false
                        // Suppression dans Firebase
                        // la synchronisation temps réel s'occupera du reste
                        FirebaseFirestore.getInstance()
                            .collection("annonces")
                            .document(annonce.id)
                            .delete()
                            .addOnSuccessListener {
                                Toast.makeText(context, "Annonce supprimée avec succès", Toast.LENGTH_SHORT).show()
                                (context as? Activity)?.finish()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(context, "Une erreur a été rencontrée lors de la suppression: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Supprimer")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { afficherDialogueSupprimer = false }
                ) {
                    Text("Annuler")
                }
            }
        )
    }

    // Dialogue pour afficher les images en plein écran
    if (dialogueOuvert) {
        Dialog(
            onDismissRequest = { dialogueOuvert = false },
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false // Pour utiliser tout l'écran
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .systemBarsPadding()
            ) {
                // Naviguer entre les images en mode plein écran -les Etats de pagination
                val paginationComplete = rememberPagerState(
                    initialPage = indexImageActuelle,
                    pageCount = { annonce.imageUrls.size }
                )

                HorizontalPager(
                    state = paginationComplete,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    AsyncImage(
                        model = annonce.imageUrls[page],
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { dialogueOuvert = false }, // Clic n'importe où pour fermer
                        contentScale = ContentScale.Fit
                    )
                }

                // Bouton fermer
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.5f)
                ) {
                    IconButton(
                        onClick = { dialogueOuvert = false },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Fermer",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Indicateurs de pagination en bas si plusieurs images (...) en mode plein ecran
                if (annonce.imageUrls.size > 1) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        repeat(annonce.imageUrls.size) { index ->
                            val estSelectionne = paginationComplete.currentPage == index
                            Canvas(
                                modifier = Modifier
                                    .size(8.dp)
                                    .padding(horizontal = 2.dp)
                            ) {
                                drawCircle(
                                    color = if (estSelectionne) Color.White else Color.White.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Fonction composable pour afficher les détails de chaque détail(kilometrage, annee, transmission, proprios)
// Je reutilise StatusUtils pour l'opacité des textes
@Composable
fun DetailRow(label: String, value: String, statut: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(
                alpha = if (StatusUtils.statutInactif(statut)) 0.4f else 0.7f
            ),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(
                alpha = StatusUtils.getTextOpacity(statut)
            ),
            modifier = Modifier.weight(1f)
        )
    }
}