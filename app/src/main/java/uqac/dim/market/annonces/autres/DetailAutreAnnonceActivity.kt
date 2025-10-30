package uqac.dim.market.annonces.autres

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import uqac.dim.market.ui.theme.MarketTheme
import uqac.dim.market.utils.ThemeManager
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch

import android.util.Log
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.ui.window.DialogProperties
import uqac.dim.market.data.models.AutreAnnonce
import uqac.dim.market.conversations.ChatActivity
import uqac.dim.market.utils.StatusUtils

// Fonction pour récupérer l'Activity depuis le Context
fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

// Activity principale pour afficher les détails d'une annonce autre
// Cette classe gère l'affichage complet d'une annonce avec toutes ses informations
class DetailAutreAnnonceActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // On récupère l'ID de l'annonce depuis l'intent
        val annonceId = intent.getStringExtra("annonceId") ?: return

        setContent {
            // Application du thème
            MarketTheme(
                darkTheme = ThemeManager.isDarkMode(this@DetailAutreAnnonceActivity)
            ) {
                DetailAutreAnnonceScreen(annonceId = annonceId)
            }
        }
    }
}

// Composable principal qui gère l'affichage des détails de l'annonce
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailAutreAnnonceScreen(annonceId: String) {
    // États pour gérer les données et l'UI
    var annonce by remember { mutableStateOf<AutreAnnonce?>(null) }
    var chargementEnCours by remember { mutableStateOf(true) }

    //États pour gérer les favoris
    var estFavori by remember { mutableStateOf(false) }
    var estFavoriLoading by remember { mutableStateOf(false) }

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
                Log.e("DetailAutreAnnonce", "Erreur lors de la vérification des favoris", e)
            }
        }
    }

    // Fonction pour ajouter/retirer des favoris
    fun ajouterRetirerFavori() {
        if (idUtilisateurActuel == null) return

        scope.launch {
            estFavoriLoading = true
            try {
                val db = FirebaseFirestore.getInstance()
                val favDocId = "${idUtilisateurActuel}_$annonceId"
                val favRef = db.collection("favoris").document(favDocId) // reference vers le document dans Firestore

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
                        "annonceType" to "autre", // Type pour cette catégorie
                        "dateAjout" to System.currentTimeMillis()
                    )
                    favRef.set(favData).await() // Enregistrer dans Firestore
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
    fun loadAnnonceData() {
        if (annonceId.isEmpty()) {
            Log.e("DetailAutreAnnonce", "ID d'annonce invalide: $annonceId")
            Toast.makeText(context, "ID d'annonce invalide", Toast.LENGTH_SHORT).show()

            chargementEnCours = false
            return
        }

        try {
            val db = FirebaseFirestore.getInstance()

            // Listener sur le document de l'annonce pour synchronisation automatique
            // des que l'annonce change dans Firebase, l'interface se met à jour
            db.collection("autre_annonces")
                .document(annonceId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e("DetailAutreAnnonce", "Erreur lors du chargement de l'annonce $annonceId", error)
                        Toast.makeText(context, "Erreur lors du chargement: ${error.message}", Toast.LENGTH_SHORT).show()
                        chargementEnCours = false
                        return@addSnapshotListener
                    }

                    // Extraction de toutes les données depuis Firebase
                    if (snapshot?.exists() == true) {
                        try {
                            val id = snapshot.id
                            val titre = snapshot.getString("titre") ?: ""
                            val description = snapshot.getString("description") ?: ""
                            val prix = snapshot.getString("prix") ?: ""
                            val userId = snapshot.getString("userId") ?: ""
                            val statut = snapshot.getString("statut") ?: StatusUtils.STATUS_DISPONIBLE
                            val datePublication = snapshot.getLong("datePublication") ?: System.currentTimeMillis()

                            // Parfois c'est une liste séparée par virgules, parfois une seule URL
                            val imageUrls = mutableListOf<String>()
                            val imageUrlsStr = snapshot.getString("imageUrls")
                            if (!imageUrlsStr.isNullOrBlank()) {
                                imageUrls.addAll(imageUrlsStr.split(",").filter { it.isNotBlank() })
                            }
                            val imageUrlUnique = snapshot.getString("imageUrl")
                            if (!imageUrlUnique.isNullOrBlank() && imageUrls.isEmpty()) {
                                imageUrls.add(imageUrlUnique)
                            }

                            // Construction de l'objet AutreAnnonce avec toutes les données
                            annonce = AutreAnnonce(
                                id = id,
                                titre = titre,
                                description = description,
                                prix = prix,
                                imageUrls = imageUrls,
                                userId = userId,
                                statut = statut,
                                datePublication = datePublication
                            )

                            // Vérifier si c'est en favori après avoir chargé l'annonce
                            scope.launch {
                                verifierSiFavori()
                            }

                        } catch (e: Exception) {

                            Log.e("DetailAutreAnnonce", "Erreur lors du traitement des données", e)
                            Toast.makeText(context, "Erreur lors du traitement des données: ${e.message}", Toast.LENGTH_SHORT).show()

                        }
                    } else {

                        Log.e("DetailAutreAnnonce", "Annonce introuvable avec ID: $annonceId")
                        Toast.makeText(context, "Annonce introuvable", Toast.LENGTH_SHORT).show()
                    }
                    chargementEnCours = false
                }

        } catch (e: Exception) {
            Log.e("DetailAutreAnnonce", "Erreur lors de l'initialisation", e)
            Toast.makeText(context, "Erreur lors de l'initialisation: ${e.message}", Toast.LENGTH_SHORT).show()
            chargementEnCours = false
        }
    }

    LaunchedEffect(annonceId) {
        loadAnnonceData()
    }

    // Structure de l'interface
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Détails de l'annonce") },
                navigationIcon = {
                    IconButton(onClick = {
                        context.findActivity()?.finish() // Retour à l'ecran preceedent
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
                // Ecran de chargement
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
                // Affichage de l'annonce synchronisation automatique
                DetailAutreAnnonceContent(
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

// Composable qui affiche le contenu détaillé
@Composable
fun DetailAutreAnnonceContent(
    annonce: AutreAnnonce,
    idUtilisateurActuel: String?,
    modifier: Modifier = Modifier,
    estFavori: Boolean = false,
    estFavoriLoading: Boolean = false,
    onajouterRetirerFavori: () -> Unit = {}
) {
    // Etats pour gérer les dialogues et interactions
    var dialogueOuvert by remember { mutableStateOf(false) }
    var indexImageActuelle by remember { mutableStateOf(0) }
    var afficherDialogueSupprimer by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val estVendeur = idUtilisateurActuel == annonce.userId // Droits differents pour le proprietaire

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Section galerie d'images avec pagination horizontale
            // J'utilise HorizontalPager pour permettre de swiper entre les images
            if (annonce.imageUrls.isNotEmpty()) {
                val etatPagination = rememberPagerState(pageCount = { annonce.imageUrls.size })

                Box {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
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
                                        // Clic sur l'image pour l'ouvrir en plein écran
                                        indexImageActuelle = page
                                        dialogueOuvert = true
                                    },
                                contentScale = ContentScale.Crop
                            )
                        }
                    }

                    // Boutons d'action pour le propriétaire de l'annonce
                    // Je reutilise StatusUtils pour déterminer quelles actions sont autorisées
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
                                // Bouton modifier (utilise StatusUtils pour déterminer si modifiable)
                                if (annonce.statut != StatusUtils.STATUS_VENDU) {
                                    IconButton(onClick = {
                                        val intent = Intent(context, ModifierAutreAnnonce::class.java)
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
                                // Bouton supprimer seulement disponible pour le propriétaire
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

            // Section titre avec badge de statut et icone favoris
            // Je reutilise encore StatusUtils pour déterminer l'affichage
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Titre avec style adaptatif selon le statut (barré si VENDU/gris si EN ATTENTE)
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

                // Section droite avec icône favoris et badge de statut
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Icone favoris seulement pour les acheteurs potentiels car le vendeur ne peut pas mettre en favori
                    if (!estVendeur && idUtilisateurActuel != null) {
                        IconButton(
                            onClick = onajouterRetirerFavori,
                            enabled = !estFavoriLoading
                        ) {
                            if (estFavoriLoading) {
                                // Spinner pendant l'ajout/suppression des favoris
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    imageVector = if (estFavori) Icons.Default.ThumbUp else Icons.Outlined.ThumbUp,
                                    contentDescription = if (estFavori) "Retirer des favoris" else "Ajouter aux favoris",
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

                    // Badge de statut
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

            // Prix formaté style selon le statut
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

            // Date de publication format
            val dateFormatee = SimpleDateFormat("EEEE dd MMMM yyyy", Locale.FRENCH).format(Date(annonce.datePublication))
            Text(
                text = "Publiée le: $dateFormatee",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )

            // Section description avec style adaptatif selon le statut
            if (annonce.description.isNotEmpty()) {
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

            // Bouton contacter affiché seulement si disponible et si je suis pas le propriétaire
            // Je reutilise StatusUtils pour déterminer l'éligibilité
            if (!estVendeur && idUtilisateurActuel != null && annonce.statut == StatusUtils.STATUS_DISPONIBLE) {
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        // Navigation vers le chat avec tous les paramètres nécessaires
                        val conversationId = "${idUtilisateurActuel}_${annonce.userId}_${annonce.id}"
                        val intent = Intent(context, ChatActivity::class.java)
                        intent.putExtra("conversationId", conversationId)
                        intent.putExtra("sellerId", annonce.userId)
                        intent.putExtra("idReceveur", annonce.userId)
                        intent.putExtra("annonceId", annonce.id)
                        intent.putExtra("annonceTitle", annonce.titre)
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Contacter le vendeur")
                }
            }
        }
    }

    // Dialogue de confirmation de suppression definitive
    if (afficherDialogueSupprimer) {
        AlertDialog(
            onDismissRequest = { afficherDialogueSupprimer = false },
            title = { Text("Confirmer la suppression") },
            text = { Text("Êtes-vous sûr de vouloir supprimer cette annonce ? Cette action est irréversible.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        afficherDialogueSupprimer = false
                        // Suppression dans Firebase -la synchronisation temps réel s'occupera du reste
                        FirebaseFirestore.getInstance()
                            .collection("autre_annonces")
                            .document(annonce.id)
                            .delete()
                            .addOnSuccessListener {
                                Toast.makeText(context, "Annonce supprimée avec succès", Toast.LENGTH_SHORT).show()
                                context.findActivity()?.finish()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(context, "Erreur lors de la suppression: ${e.message}", Toast.LENGTH_LONG).show()
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
                TextButton(onClick = { afficherDialogueSupprimer = false }) {
                    Text("Annuler")
                }
            }
        )
    }

    // Ce dialogue est pour affichager des images en plein écran
    if (dialogueOuvert) {
        Dialog(
            onDismissRequest = { dialogueOuvert = false },
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false // Utiliser tout l'écran
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .systemBarsPadding()
            ) {
                // Pager pour naviguer entre les images en plein écran
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

                // Bouton fermer le dialogue
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

                // Indicateurs de pagination en bas si plusieurs images en mode plein ecran
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
