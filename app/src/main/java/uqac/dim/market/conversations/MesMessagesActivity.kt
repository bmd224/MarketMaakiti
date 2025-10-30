package uqac.dim.market.conversations

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.launch
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*
import uqac.dim.market.utils.ThemeManager
import uqac.dim.market.ui.theme.MarketTheme
import coil.compose.AsyncImage
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import uqac.dim.market.data.models.Conversation
import uqac.dim.market.utils.StatusUtils
import com.google.firebase.firestore.FieldValue
import com.google.firebase.Timestamp

// Activité principale pour gérer l'affichage des coversations d'un utilisateur
// Cette activité permet de:
// -Voir toutes les conversations actives de l'utilisateur
// -Sélectionner et supprimer des conversations
// -Naviguer vers les conversations individuelles pour chatter
// -Afficher les détails des annonces associées aux conversations
// Aussi j'implémente une suppression locale avec point de suppression
// Quand un utilisateur supprime une conversation, je stocke le timestamp de suppression
class MesMessagesActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Thème
        setContent {
            MarketTheme(
                darkTheme = ThemeManager.isDarkMode(this@MesMessagesActivity)
            ) {
                MesMessagesScreen()
            }
        }
    }
}

// Mon écran principal des messages avec gestion du mode sélection multiple
// Fonctionnalités implémentées:
// -Affichage de la liste des conversations
// -Cache des données d'annonces
// -Suppression des conversations locale avec point de suppression
// -Navigation vers les conversations individuels
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MesMessagesScreen() {
    
    // État principal pour stocker la liste des conversations
    var conversations by remember { mutableStateOf<List<Conversation>>(emptyList()) }

    // État pour indiquer si le chargement initial est en cours
    var chargementEnCours by remember { mutableStateOf(true) }

    // État pour les conversations avec leur type (véhicule/autre)
    // J'utilise la même logique que MesEnregistrementsActivity mais sans filtrage
    var conversationsAvecType by remember { mutableStateOf<List<Pair<Conversation, String>>>(emptyList()) }
    
    // Cache des données des annonces
    // C'est pour éviter les appels répétés à Firebase pour les mêmes annonces
    var annoncesVehiculesCache by remember { mutableStateOf<Map<String, Map<String, Any>>>(emptyMap()) }
    var annoncesAutresCache by remember { mutableStateOf<Map<String, Map<String, Any>>>(emptyMap()) }

    // Gestion du mode sélection multiple
    // Permet de sélectionner et supprimer plusieurs conversations
    var conversationsSelectionnees by remember { mutableStateOf<Set<String>>(emptySet()) }
    var modeSelection by remember { mutableStateOf(false) }
    var afficherDialogueSupprimer by remember { mutableStateOf(false) }
    
    // Ces conversations restent supprimees jusqu'à confirmation de Firebase
    var conversationsSupprimes by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Récupération du contexte de l'ID de l'utilisateur actuel
    val context = LocalContext.current
    val idUtilisateurActuel = FirebaseAuth.getInstance().currentUser?.uid


    // Ma fonction pour déterminer le type d'une conversation (véhicule/autre)
    // Recherche dans les véhicules puis dans les autres annonces
    // Utilise un fallback par titre si l'ID exact n'est pas trouvé
    suspend fun determineConversationType(conversation: Conversation): String {
        // Si pas d'ID d'annonce, je considere comme "autre" par défaut
        if (conversation.annonceId.isBlank()) return "autre"

        return try {
            val db = FirebaseFirestore.getInstance()

            // Je recherche d'abord dans les annonces de véhicules
            val vehiculeDoc = db.collection("annonces")
                .document(conversation.annonceId.trim())
                .get()
                .await()

            if (vehiculeDoc.exists()) {
                "vehicule"
            } else {
                // Je recherche dans les autres annonces
                val autreDoc = db.collection("autre_annonces")
                    .document(conversation.annonceId.trim())
                    .get()
                    .await()

                if (autreDoc.exists()) {
                    "autre"
                } else {
                    // Je recherche par titre comme fallback (au cas où l'ID exact n'est pas trouvé)
                    val vehiculeQuery = db.collection("annonces")
                        .whereEqualTo("titre", conversation.annonceTitle)
                        .limit(1)
                        .get()
                        .await()

                    if (!vehiculeQuery.isEmpty) {
                        "vehicule"
                    } else {
                        "autre" // Par défaut si rien n'est trouvé
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MesMessages", "Erreur lors de la détermination du type", e)
            "autre" // Valeur par défaut en cas d'erreur aussi
        }
    }

    // La fonction pour traiter les conversations avec les données d'annonces en cache
    // Cette fonction optimise les performances en:
    // Déterminant le type de chaque conversation
    // Utilisant les coroutines pour éviter de bloquer l'UI
    // Mettant à jour l'état une fois tout le traitement terminé
    fun traiterConversationsAvecCache() {
        val scope = CoroutineScope(Dispatchers.Main)
        scope.launch {
            val conversationsWithTypes = mutableListOf<Pair<Conversation, String>>()

            // Traitement de chaque conversation pour déterminer son type
            conversations.forEach { conversation ->
                val type = determineConversationType(conversation)
                conversationsWithTypes.add(Pair(conversation, type))
            }

            // Mise à jour de l'état avec les résultats
            conversationsAvecType = conversationsWithTypes
            chargementEnCours = false
        }
    }

    // J'ai implementé cette fonction pour vérifier s'il y a des messages après suppression
    // La fonction vérifie correctement s'il y a des nouveaux messages depuis la suppression
    suspend fun verifierNouveauxMessagesDepuisSuppression(conversationId: String, timestampSuppression: Timestamp): Boolean {
        return try {
            val db = FirebaseFirestore.getInstance()
            val nouveauxMessagesQuery = db.collection("conversations")
                .document(conversationId)
                .collection("messages")
                .whereGreaterThan("firestoreTimestamp", timestampSuppression)
                .limit(1) // Je limite la recherche à une conversation par limite à 1 pour éviter de charger trop de données
                .get()
                .await()

            val aDeNouveauxMessages = !nouveauxMessagesQuery.isEmpty
            Log.d("MesMessages", "Conversation $conversationId -Nouveaux messages depuis suppression: $aDeNouveauxMessages")
            return aDeNouveauxMessages
        } catch (e: Exception) {
            Log.e("MesMessages", "Erreur lors de la vérification des nouveaux messages", e)
            false
        }
    }

    // Je veux vérifier si une conversation a été réactivée
    // Cette fonction détecte si l'autre utilisateur a envoyé un message après que l'utilisateur actuel ait supprimé
    fun verifierConversationReactivee(conversationSnapshot: com.google.firebase.firestore.DocumentSnapshot, idUtilisateur: String): Boolean {
        try {
            // Vérifier s'il y a un marqueur de réactivation pour cet utilisateur
            val marqueurReactivationMap = conversationSnapshot.get("conversationReactivee") as? Map<String, Any>
            val timestampReactivation = marqueurReactivationMap?.get(idUtilisateur) as? Timestamp

            // Si oui, la conversation a été réactivée
            if (timestampReactivation != null) {
                Log.d("MesMessages", "Conversation ${conversationSnapshot.id} -Marqueur de réactivation trouvé: $timestampReactivation")
                return true
            }
            return false
        } catch (e: Exception) {
            Log.e("MesMessages", "Erreur lors de la vérification de réactivation", e)
            return false
        }
    }

    // Chargement des conversations
    LaunchedEffect(idUtilisateurActuel) {
        if (idUtilisateurActuel != null) {
            try {
                val db = FirebaseFirestore.getInstance()

                // Requête pour récupérer toutes les conversations ou l'utilisateur participe
                val conversationsRef = db.collection("conversations")
                    .whereArrayContains("participants", idUtilisateurActuel)
                    .orderBy("firestoreTimestamp", Query.Direction.DESCENDING)

                // Écoute en temps réel des changements sur les conversations
                conversationsRef.addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e("MesMessages", "Erreur lors de l'écoute des conversations", error)
                        chargementEnCours = false
                        return@addSnapshotListener
                    }

                    // Je traite les conversations mais je sais qu'une conversation peut etre supprimée après
                    snapshot?.let { querySnapshot ->
                        val scope = CoroutineScope(Dispatchers.Main)
                        scope.launch {
                            val conversationsList = mutableListOf<Conversation>()

                            // Parcourir les documents de la requête
                            for (doc in querySnapshot.documents) {
                                val conversation = doc.toObject(Conversation::class.java)?.copy(id = doc.id)
                                if (conversation != null) {

                                    // Vérifier d'abord la suppression
                                    if (conversationsSupprimes.contains(conversation.id)) {
                                        Log.d("MesMessages", "Conversation ${conversation.id} -Supprimée, ignorée par le listener")
                                        continue // Ignorer cette conversation car elle a été supprimée
                                    }

                                    // Vérifier le point de suppression pour cet utilisateur
                                    val pointsSuppressionMap = doc.get("pointsSuppression") as? Map<String, Any>
                                    val timestampSuppressionUtilisateur = pointsSuppressionMap?.get(idUtilisateurActuel) as? Timestamp

                                    if (timestampSuppressionUtilisateur == null) {
                                        // Pas de suppression afficher la conversation
                                        Log.d("MesMessages", "Conversation ${conversation.id} -Pas de suppression, ajout à la liste")
                                        conversationsList.add(conversation)
                                    } else {
                                        // Vérifier si la conversation a été réactivée au cas ou il y a de nouveaux messages
                                        val conversationReactivee = verifierConversationReactivee(doc, idUtilisateurActuel)
                                        val aDesNouveauxMessages = verifierNouveauxMessagesDepuisSuppression(
                                            conversation.id,
                                            timestampSuppressionUtilisateur
                                        )
                                        // Si la conversation a été réactivée ou si il y a de nouveaux messages
                                        if (conversationReactivee || aDesNouveauxMessages) {
                                            // La conversation doit réapparaître
                                            Log.d("MesMessages", "Conversation ${conversation.id} -Réapparition (réactivée: $conversationReactivee, nouveaux messages: $aDesNouveauxMessages)")
                                            conversationsList.add(conversation)

                                            // Nettoyer le marqueur de réactivation après ce traitement
                                            if (conversationReactivee) {
                                                try {
                                                    val updatesNettoyage = mapOf(
                                                        "conversationReactivee.$idUtilisateurActuel" to FieldValue.delete()
                                                    )
                                                    db.collection("conversations").document(conversation.id)
                                                        .update(updatesNettoyage)
                                                    Log.d("MesMessages", "Marqueur de réactivation nettoyé pour ${conversation.id}")
                                                } catch (e: Exception) {
                                                    Log.e("MesMessages", "Erreur lors du nettoyage du marqueur", e)
                                                    // Je prefere ne pas tout bloquer donc on continue malgré l'erreur de nettoyage mais je logue pour savoir
                                                }
                                            }
                                        } else {
                                            // Pas de nouveaux messages, conversation reste supprimée
                                            Log.d("MesMessages", "Conversation ${conversation.id} -Reste supprimée")
                                        }
                                    }
                                }
                            }

                            Log.d("MesMessages", "Total conversations filtrées: ${conversationsList.size}") // Affichage du nombre de conversations
                            conversations = conversationsList
                            traiterConversationsAvecCache() // Traitement des conversations avec cache
                        }
                    }
                }

                // Listener pour les annonces de véhicules pour une synchronisation au cas ou il ya changemeent
                db.collection("annonces")
                    .addSnapshotListener { annoncesSnapshot, annoncesError ->
                        if (annoncesError != null) {
                            Log.e("MesMessages", "Erreur lors de l'écoute des annonces véhicules", annoncesError)
                            return@addSnapshotListener
                        }

                        // Mise à jour du cache des annonces véhicules
                        val nouveauCache = mutableMapOf<String, Map<String, Any>>()
                        annoncesSnapshot?.documents?.forEach { doc ->
                            if (doc.exists()) {
                                val data = doc.data?.toMutableMap() ?: mutableMapOf()
                                data["id"] = doc.id
                                nouveauCache[doc.id] = data
                            }
                        }
                        annoncesVehiculesCache = nouveauCache

                        // Encore traiter les conversations si elles sont déjà chargées
                        if (conversations.isNotEmpty()) {
                            traiterConversationsAvecCache() // Traitement des conversations avec cache
                        }
                    }

                // Listener pour les autres annonces pour une synchronisation au cas ou il ya changement
                db.collection("autre_annonces")
                    .addSnapshotListener { autresSnapshot, autresError ->
                        if (autresError != null) {
                            Log.e("MesMessages", "Erreur lors de l'écoute des autres annonces", autresError)
                            return@addSnapshotListener
                        }

                        // Mise à jour du cache des autres annonces
                        val nouveauCache = mutableMapOf<String, Map<String, Any>>()
                        autresSnapshot?.documents?.forEach { doc ->
                            if (doc.exists()) {
                                val data = doc.data?.toMutableMap() ?: mutableMapOf()
                                data["id"] = doc.id
                                nouveauCache[doc.id] = data
                            }
                        }
                        annoncesAutresCache = nouveauCache

                        // Encore traiter les conversations si elles sont déjà chargées
                        if (conversations.isNotEmpty()) {
                            traiterConversationsAvecCache() // Traitement des conversations avec cache
                        }
                    }

            } catch (e: Exception) {
                Log.e("MesMessages", "Exception lors de la configuration du listener", e)
                chargementEnCours = false
            }
        } else {
            // Si l'utilisateur n'est pas connecté
            chargementEnCours = false
        }
    }

    // J'avais un probleme d'optimisation lorsque je supprimais une conversation dans firebase
    // donc j'ai fais cette fonction pour supprimer les conversations avec une gestion propre des états
    // En suite, je fais un nettoyage automatique
    suspend fun executerSuppressionFirebase(conversationsIds: Set<String>) {
        if (idUtilisateurActuel == null) return

        // Suppression dans Firebase
        try {
            val db = FirebaseFirestore.getInstance()
            val timestampSuppression = Timestamp.now()

            Log.d("MesMessages", "DÉBUT SUPPRESSION FIREBASE -Timestamp: $timestampSuppression")
            Log.d("MesMessages", "ID Utilisateur actuel pour suppression: $idUtilisateurActuel")

            // Traitement des conversations pour optimiser les performances
            conversationsIds.forEach { conversationId ->
                // Stocker le timestamp de suppression pour cet utilisateur spécifique
                val updates = mapOf(
                    "pointsSuppression.$idUtilisateurActuel" to timestampSuppression
                )

                Log.d("MesMessages", "Suppression conversation $conversationId pour utilisateur $idUtilisateurActuel")
                Log.d("MesMessages", "Update: $updates")

                // J'utilise une transaction pour garantir la cohérence
                db.runTransaction { transaction ->
                    val conversationRef = db.collection("conversations").document(conversationId)
                    transaction.update(conversationRef, updates)
                }.await()

                Log.d("MesMessages", "Point de suppression défini pour conversation $conversationId")
            }

            Log.d("MesMessages", " SUPPRESSION FIREBASE TERMINÉE -Toutes les conversations sont traitées")

            // Nettoyage de la suppression apres succes
            conversationsSupprimes = conversationsSupprimes -conversationsIds

        } catch (e: Exception) {
            Log.e("MesMessages", "ERREUR lors de la suppression avec points de suppression", e)

            // En cas d'erreur, restaurer les conversations
            conversationsSupprimes = conversationsSupprimes -conversationsIds
        }
    }

    // Le filtre pour l'affichage des conversations
    // Je gere la suppression directement dans le listener Firebase
    val conversationsFiltrees = conversationsAvecType

    // Scaffold avec la TopAppBar
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        // Titre
                        if (modeSelection)
                            "${conversationsSelectionnees.size} sélectionnée(s)"
                        else
                            "Mes Messages"
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (modeSelection) {
                            // Sortie du mode sélection
                            modeSelection = false
                            conversationsSelectionnees = emptySet()
                        } else {
                            // Retour à l'écran précédent
                            (context as MesMessagesActivity).finish()
                        }
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    // Bouton de suppression visible seulement en mode sélection
                    if (modeSelection && conversationsSelectionnees.isNotEmpty()) {
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
            )
        }
    ) { paddingValues ->
        if (chargementEnCours) {
            // État de chargement
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (conversationsFiltrees.isEmpty()) {
            // État vide
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Vous n'avez aucun message pour l'instant.")
            }
        } else {
            // Affichage de la liste
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                items(conversationsFiltrees) { (conversation, type) ->
                    ConversationItem(
                        conversation = conversation,
                        idUtilisateurActuel = idUtilisateurActuel,
                        estSelectionne = conversationsSelectionnees.contains(conversation.id),
                        modeSelection = modeSelection,
                        annonceType = type,
                        annoncesVehiculesCache = annoncesVehiculesCache,
                        annoncesAutresCache = annoncesAutresCache,
                        // Fonction de sélection
                        onSelectionToggle = { conversationId ->
                            conversationsSelectionnees = if (conversationsSelectionnees.contains(conversationId)) {
                                conversationsSelectionnees -conversationId
                            } else {
                                conversationsSelectionnees + conversationId
                            }
                        },
                        // Long clic
                        onLongPress = {
                            if (!modeSelection) {
                                modeSelection = true
                                conversationsSelectionnees = setOf(conversation.id)
                            }
                        },
                        onClick = { autreParticipantId ->
                            if (modeSelection) {
                                val conversationId = conversation.id
                                conversationsSelectionnees = if (conversationsSelectionnees.contains(conversationId)) {
                                    conversationsSelectionnees -conversationId
                                } else {
                                    conversationsSelectionnees + conversationId
                                }
                            } else {
                                // Navigation vers l'écran de chat
                                val intent = Intent(context, ChatActivity::class.java)
                                intent.putExtra("conversationId", conversation.id)
                                intent.putExtra("annonceId", conversation.annonceId)
                                intent.putExtra("idReceveur", autreParticipantId)
                                intent.putExtra("annonceTitle", conversation.annonceTitle)
                                context.startActivity(intent)
                            }
                        }
                    )
                }
            }
        }
    }

    // Dialogue de confirmation pour la suppression
    if (afficherDialogueSupprimer) {
        AlertDialog(
            onDismissRequest = {
                afficherDialogueSupprimer = false
            },
            title = {
                Text("Confirmer la suppression")
            },
            text = {
                val count = conversationsSelectionnees.size
                val message = if (count == 1) {
                    "Êtes-vous sûr de vouloir supprimer cette conversation de votre liste ?"
                } else {
                    "Êtes-vous sûr de vouloir supprimer ces $count conversations de votre liste ?"
                }
                Text("$message Cette action est irréversible.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // Suppression
                        Log.d("MesMessages", "SUPPRESSION -Début")
                        // Avant que je fasse la suppression, la conversation disparaissait 
                        // puis elle reapparait avant de disparaitre je regle le probleme en faisant
                        // Mémoriser les conversations à supprimer
                        val conversationsATraiter = conversationsSelectionnees.toSet()

                        // Masquer immédiatement les conversations
                        conversationsSupprimes = conversationsSupprimes + conversationsATraiter

                        // Réinitialiser l'interface immédiatement
                        conversationsSelectionnees = emptySet()
                        modeSelection = false
                        afficherDialogueSupprimer = false

                        Log.d("MesMessages", " SUPPRESSION -${conversationsATraiter.size} conversations masquées définitivement")

                        // Lancer la supression dans une couroutine Firebase en arriere plan
                        val scope = CoroutineScope(Dispatchers.Main)
                        scope.launch {
                            Log.d("MesMessages", " TRAITEMENT FIREBASE EN ARRIÈRE-PLAN")
                            executerSuppressionFirebase(conversationsATraiter)
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
                    onClick = {
                        afficherDialogueSupprimer = false
                    }
                ) {
                    Text("Annuler")
                }
            }
        )
    }
}

// Composant pour afficher un élément de conversation dans la liste
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConversationItem(
    conversation: Conversation,
    idUtilisateurActuel: String?,
    estSelectionne: Boolean = false,
    modeSelection: Boolean = false,
    annonceType: String,
    annoncesVehiculesCache: Map<String, Map<String, Any>> = emptyMap(),
    annoncesAutresCache: Map<String, Map<String, Any>> = emptyMap(),
    onSelectionToggle: (String) -> Unit = {},
    onLongPress: () -> Unit = {},
    onClick: (String) -> Unit
) {
    val autreParticipantId = conversation.participants.firstOrNull { it != idUtilisateurActuel } ?: ""

    // Etat pour l'affichage de la conversation
    var annonceDetails by remember { mutableStateOf("") }
    var annonceStatut by remember { mutableStateOf(StatusUtils.STATUS_DISPONIBLE) }
    var premiereUrlImage by remember { mutableStateOf<String?>(null) }

    // Chargement des données de l'annonce
    LaunchedEffect(conversation.annonceId, annoncesVehiculesCache, annoncesAutresCache) {
        Log.d("DEBUG", "ID de conversation: ${conversation.id}")
        Log.d("DEBUG", "ID d'annonce depuis la conversation: '${conversation.annonceId}'")
        Log.d("DEBUG", "Titre d'annonce depuis la conversation: '${conversation.annonceTitle}'")

        // Vérifier si l'ID de l'annonce est présent
        if (conversation.annonceId.isNotBlank()) {
            try {
                val annonceIdTrimmed = conversation.annonceId.trim()
                var annonceData: Map<String, Any>? = null
                var vientDuCacheVehicule = false

                // Recherche dans le cache des véhicules
                if (annoncesVehiculesCache.containsKey(annonceIdTrimmed)) {
                    annonceData = annoncesVehiculesCache[annonceIdTrimmed]
                    vientDuCacheVehicule = true
                    Log.d("DEBUG", "Trouvé dans le cache véhicules")
                }
                // Recherche dans le cache des autres annonces
                else if (annoncesAutresCache.containsKey(annonceIdTrimmed)) {
                    annonceData = annoncesAutresCache[annonceIdTrimmed]
                    vientDuCacheVehicule = false
                    Log.d("DEBUG", "Trouvé dans le cache autres annonces")
                }

                if (annonceData != null) {
                    // Traitement des données depuis le cache
                    val statut = annonceData["statut"] as? String ?: StatusUtils.STATUS_DISPONIBLE
                    annonceStatut = statut

                    // Récupération de l'image
                    val imageUrls = mutableListOf<String>()
                    val imageUrlsStr = annonceData["imageUrls"] as? String
                    if (!imageUrlsStr.isNullOrBlank()) {
                        imageUrls.addAll(imageUrlsStr.split(",").filter { it.isNotBlank() })
                    }
                    val imageUrlUnique = annonceData["imageUrl"] as? String
                    if (!imageUrlUnique.isNullOrBlank() && imageUrls.isEmpty()) {
                        imageUrls.add(imageUrlUnique)
                    }
                    premiereUrlImage = imageUrls.firstOrNull()

                    if (vientDuCacheVehicule) {
                        // Traitement pour véhicule
                        val marque = annonceData["marque"] as? String ?: ""
                        val modele = annonceData["modele"] as? String ?: ""
                        val annee = annonceData["annee"] as? String ?: (annonceData["annee"] as? Long)?.toString() ?: ""
                        val titre = annonceData["titre"] as? String ?: ""

                        Log.d("DEBUG", "Marque: '$marque', Modele: '$modele', Annee: '$annee', Titre: '$titre'")

                        annonceDetails = if (marque.isNotBlank() || modele.isNotBlank() || annee.isNotBlank()) {
                            listOf(marque, modele, annee)
                                .filter { it.isNotBlank() }
                                .joinToString(" ")
                                .ifBlank { "Informations manquantes" }
                        } else {
                            titre.ifBlank { "Informations manquantes" }
                        }
                    } else {
                        // Traitement pour autre annonce
                        val titre = annonceData["titre"] as? String ?: ""
                        annonceDetails = titre.ifBlank { "Informations manquantes" }
                    }
                } else {
                    // Fallback je recherche dans Firebase
                    val db = FirebaseFirestore.getInstance()
                    Log.d("DEBUG", "Recherche du document avec ID: '${conversation.annonceId}'")

                    var annonceDoc = db.collection("annonces")
                        .document(conversation.annonceId.trim())
                        .get()
                        .await()

                    Log.d("DEBUG", "Document véhicule existe: ${annonceDoc.exists()}")

                    if (annonceDoc.exists()) {
                        // Traitement véhicule depuis Firebase
                        val statut = annonceDoc.getString("statut") ?: StatusUtils.STATUS_DISPONIBLE
                        annonceStatut = statut

                        // Récupération de l'image
                        val imageUrls = mutableListOf<String>()
                        val imageUrlsStr = annonceDoc.getString("imageUrls")
                        if (!imageUrlsStr.isNullOrBlank()) {
                            imageUrls.addAll(imageUrlsStr.split(",").filter { it.isNotBlank() })
                        }
                        val imageUrlUnique = annonceDoc.getString("imageUrl")
                        if (!imageUrlUnique.isNullOrBlank() && imageUrls.isEmpty()) {
                            imageUrls.add(imageUrlUnique)
                        }
                        premiereUrlImage = imageUrls.firstOrNull()

                        val marque = annonceDoc.getString("marque") ?: ""
                        val modele = annonceDoc.getString("modele") ?: ""
                        val annee = annonceDoc.getString("annee") ?: annonceDoc.getLong("annee")?.toString() ?: ""
                        val titre = annonceDoc.getString("titre") ?: ""

                        Log.d("DEBUG", "Marque: '$marque', Modele: '$modele', Annee: '$annee', Titre: '$titre'")

                        annonceDetails = if (marque.isNotBlank() || modele.isNotBlank() || annee.isNotBlank()) {
                            listOf(marque, modele, annee)
                                .filter { it.isNotBlank() }
                                .joinToString(" ")
                                .ifBlank { "Informations manquantes" }
                        } else {
                            titre.ifBlank { "Informations manquantes" }
                        }
                    } else {
                        Log.d("DEBUG", "Document véhicule non trouvé, recherche dans autre_annonces")

                        annonceDoc = db.collection("autre_annonces")
                            .document(conversation.annonceId.trim())
                            .get()
                            .await()

                        Log.d("DEBUG", "Document autre_annonce existe: ${annonceDoc.exists()}")

                        if (annonceDoc.exists()) {
                            // Traitement autre annonce depuis Firebase
                            val statut = annonceDoc.getString("statut") ?: StatusUtils.STATUS_DISPONIBLE
                            annonceStatut = statut

                            val imageUrls = mutableListOf<String>()
                            val imageUrlsStr = annonceDoc.getString("imageUrls")
                            if (!imageUrlsStr.isNullOrBlank()) {
                                imageUrls.addAll(imageUrlsStr.split(",").filter { it.isNotBlank() })
                            }
                            val imageUrlUnique = annonceDoc.getString("imageUrl")
                            if (!imageUrlUnique.isNullOrBlank() && imageUrls.isEmpty()) {
                                imageUrls.add(imageUrlUnique)
                            }
                            premiereUrlImage = imageUrls.firstOrNull()

                            val titre = annonceDoc.getString("titre") ?: ""
                            annonceDetails = titre.ifBlank { "Informations manquantes" }
                        } else {
                            Log.d("DEBUG", "Document non trouvé dans les deux collections, recherche par titre")

                            // Recherche par titre dans les véhicules
                            var querySnapshot = db.collection("annonces")
                                .whereEqualTo("titre", conversation.annonceTitle)
                                .limit(1)
                                .get()
                                .await()

                            if (!querySnapshot.isEmpty) {
                                // Trouvé un véhicule par titre
                                val foundDoc = querySnapshot.documents.first()
                                Log.d("DEBUG", "Document véhicule trouvé par titre: ${foundDoc.id}")

                                val statut = foundDoc.getString("statut") ?: StatusUtils.STATUS_DISPONIBLE // Statut par défaut
                                annonceStatut = statut

                                val imageUrls = mutableListOf<String>()
                                val imageUrlsStr = foundDoc.getString("imageUrls")
                                if (!imageUrlsStr.isNullOrBlank()) {
                                    imageUrls.addAll(imageUrlsStr.split(",").filter { it.isNotBlank() })
                                }
                                val imageUrlUnique = foundDoc.getString("imageUrl")
                                if (!imageUrlUnique.isNullOrBlank() && imageUrls.isEmpty()) {
                                    imageUrls.add(imageUrlUnique)
                                }
                                premiereUrlImage = imageUrls.firstOrNull()

                                val marque = foundDoc.getString("marque") ?: ""
                                val modele = foundDoc.getString("modele") ?: ""
                                val annee = foundDoc.getString("annee") ?: foundDoc.getLong("annee")?.toString() ?: ""
                                val titre = foundDoc.getString("titre") ?: ""

                                annonceDetails = if (marque.isNotBlank() || modele.isNotBlank() || annee.isNotBlank()) {
                                    listOf(marque, modele, annee)
                                        .filter { it.isNotBlank() }
                                        .joinToString(" ")
                                        .ifBlank { conversation.annonceTitle }
                                } else {
                                    titre.ifBlank { conversation.annonceTitle }
                                }
                            } else {
                                // Recherche par titre dans les autres annonces
                                querySnapshot = db.collection("autre_annonces")
                                    .whereEqualTo("titre", conversation.annonceTitle)
                                    .limit(1)
                                    .get()
                                    .await()

                                if (!querySnapshot.isEmpty) {
                                    // Trouvé une autre annonce par titre
                                    val foundDoc = querySnapshot.documents.first()
                                    Log.d("DEBUG", "Document autre_annonce trouvé par titre: ${foundDoc.id}")

                                    val statut = foundDoc.getString("statut") ?: StatusUtils.STATUS_DISPONIBLE
                                    annonceStatut = statut

                                    val imageUrls = mutableListOf<String>()
                                    val imageUrlsStr = foundDoc.getString("imageUrls")
                                    if (!imageUrlsStr.isNullOrBlank()) {
                                        imageUrls.addAll(imageUrlsStr.split(",").filter { it.isNotBlank() })
                                    }
                                    val imageUrlUnique = foundDoc.getString("imageUrl")
                                    if (!imageUrlUnique.isNullOrBlank() && imageUrls.isEmpty()) {
                                        imageUrls.add(imageUrlUnique)
                                    }
                                    premiereUrlImage = imageUrls.firstOrNull()

                                    val titre = foundDoc.getString("titre") ?: ""
                                    annonceDetails = titre.ifBlank { conversation.annonceTitle }
                                } else {
                                    // Aucune annonce trouvée
                                    Log.d("DEBUG", "Aucun document trouvé par titre non plus")
                                    annonceDetails = conversation.annonceTitle.ifBlank { "Annonce introuvable" }
                                    annonceStatut = StatusUtils.STATUS_DISPONIBLE
                                    premiereUrlImage = null
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("DEBUG", "Erreur lors du chargement de l'annonce: ${e.message}", e)
                annonceDetails = conversation.annonceTitle.ifBlank { "Erreur de chargement" }
                annonceStatut = StatusUtils.STATUS_DISPONIBLE
                premiereUrlImage = null
            }
        } else {
            Log.d("DEBUG", "ID d'annonce vide, utilisation du titre")
            annonceDetails = conversation.annonceTitle.ifBlank { "Pas d'annonce associée" }
            annonceStatut = StatusUtils.STATUS_DISPONIBLE
            premiereUrlImage = null
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .combinedClickable(
                onClick = {
                    if (modeSelection) {
                        onSelectionToggle(conversation.id)
                    } else {
                        onClick(autreParticipantId)
                    }
                },
                onLongClick = {
                    onLongPress()
                }
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box {
            // Sélectionné ou non
            if (estSelectionne) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            RoundedCornerShape(12.dp)
                        )
                )
            }

            // Contenu principal de la Card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Checkbox comme mode sélection
                if (modeSelection) {
                    Checkbox(
                        checked = estSelectionne,
                        onCheckedChange = { onSelectionToggle(conversation.id) },
                        modifier = Modifier.padding(end = 16.dp)
                    )
                }

                // Image de l'annonce
                if (premiereUrlImage != null) {
                    AsyncImage(
                        model = premiereUrlImage,
                        contentDescription = annonceDetails,
                        modifier = Modifier
                            .size(60.dp)
                            .padding(end = 12.dp),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    // Placeholder sans image
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .padding(end = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Aucune image", // Lorsque l'annonce est deja supprimee par le vendeur
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                // Informations de la conversation
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    // Titre et badge type
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Titre et gestion du statut
                        Text(
                            text = annonceDetails.ifBlank { "Annonce non spécifiée" },
                            style = MaterialTheme.typography.titleMedium,
                            textDecoration = if (StatusUtils.barrerTexte(annonceStatut))
                                TextDecoration.LineThrough else TextDecoration.None,
                            color = MaterialTheme.colorScheme.onSurface.copy(
                                alpha = StatusUtils.getTextOpacity(annonceStatut)
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // Badge type
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (annonceType == "vehicule")
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Text(
                                text = if (annonceType == "vehicule") "Véhicule" else "Autres",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (annonceType == "vehicule")
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Aperu du dernier message
                    Text(
                        text = conversation.lastMessage.ifBlank { "Aucun message" }, // par defaut
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        color = MaterialTheme.colorScheme.onSurface.copy(
                            alpha = StatusUtils.getTextOpacity(annonceStatut)
                        )
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Date
                    Text(
                        text = SimpleDateFormat("dd MMMM yyyy 'à' HH:mm", Locale.FRENCH)
                            .format(Date(conversation.firestoreTimestamp.seconds * 1000)),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (StatusUtils.statutInactif(annonceStatut))
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Badge statut
            if (StatusUtils.afficherBadgeStatut(annonceStatut)) {
                val (badgeColor, textColor) = StatusUtils.getStatusBadgeColors(annonceStatut)

                Card(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                    colors = CardDefaults.cardColors(containerColor = badgeColor),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = StatusUtils.getStatusDisplayText(annonceStatut),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = textColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}