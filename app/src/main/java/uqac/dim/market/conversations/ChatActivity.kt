package uqac.dim.market.conversations

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import androidx.core.content.FileProvider
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import uqac.dim.market.data.models.Conversation
import uqac.dim.market.data.models.Message
import uqac.dim.market.secrets.FCMUtils
import uqac.dim.market.ui.theme.MarketTheme
import uqac.dim.market.utils.ImageUploadUtils
import uqac.dim.market.utils.ThemeManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

// Classe de donnees où j’associe le Message à
// son ID Firestore pour pouvoir le modifier/supprimer
data class MessageAvecId(
    val message: Message,
    val documentId: String
)

// Activity principale pour l'interface de chat entre deux utilisateurs
// Cette activité gère l'affichage des messages, l'envoi de nouveaux messages,
// la modification et la suppression de messages ainsi que l'envoie d'images
class ChatActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Gestion du clavier
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        // Données passées par l’intent
        val conversationId = intent.getStringExtra("conversationId") ?: return
        val annonceId = intent.getStringExtra("annonceId") ?: return
        val idReceveur = intent.getStringExtra("idReceveur") ?: return
        val annonceTitle = intent.getStringExtra("annonceTitle") ?: ""

        setContent {
            MarketTheme(darkTheme = ThemeManager.isDarkMode(this@ChatActivity)) {
                ChatScreen(
                    conversationId = conversationId,
                    annonceId = annonceId,
                    idReceveur = idReceveur,
                    annonceTitle = annonceTitle
                )
            }
        }
    }
}

// Composable principal qui gère l'interface utilisateur du chat
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    conversationId: String,
    annonceId: String,
    idReceveur: String,
    annonceTitle: String
) {
    // États et contextes
    var messages by remember { mutableStateOf<List<Message>>(emptyList()) }
    var messagesAvecIds by remember { mutableStateOf<List<MessageAvecId>>(emptyList()) } // ajouté pour editer/suppimer
    var messageText by remember { mutableStateOf("") }
    var chargementEnCours by remember { mutableStateOf(true) }
    val context = LocalContext.current
    val idUtilisateurActuel = FirebaseAuth.getInstance().currentUser?.uid
    val listState = rememberLazyListState() // État de scroll de la liste des messages
    val scope = rememberCoroutineScope() // Scope de coroutine pour lancer des opérations asynchrones

    var titreDynamiqueAnnonce by remember { mutableStateOf(annonceTitle) }
    var chargementAnnonceEnCours by remember { mutableStateOf(true) }

    var imageUris by remember { mutableStateOf<List<Uri>>(emptyList()) } // Liste des URIs des images sélectionnées par l'utilisateur (avant envoi)
    var afficherDialoguePhoto by remember { mutableStateOf(false) }
    var uriImageTemporaire by remember { mutableStateOf<Uri?>(null) } // URI temporaire pour la photo prise avec la caméra

    var pointSuppressionUtilisateur by remember { mutableStateOf<Timestamp?>(null) }
    var enCoursEnvoi by remember { mutableStateOf(false) } // Indique si un envoi de message est en cours (j'évite les envois multiples)

    // États pour le menu contextuel apres un long clic sur un message
    var afficherMenuContextuel by remember { mutableStateOf(false) }
    var messageSelectionne by remember { mutableStateOf<MessageAvecId?>(null) }
    var modeEdition by remember { mutableStateOf(false) }
    var messageEditionId by remember { mutableStateOf("") }

    // Launchers ou lancers images
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris -> imageUris = imageUris + uris } // Ajoute les nouvelles images à la liste existante

    // Launcher pour prendre une photo avec la caméra
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && uriImageTemporaire != null) {
            imageUris = imageUris + uriImageTemporaire!! // Si la photo a été prise avec succès, j'ajoute son URI à la liste
        }
    }

    // Launcher pour demander la permission d'accès à la galerie
    val galleryPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) galleryLauncher.launch("image/*") // "image/*" me permet de filtrer uniquement les images
        else Toast.makeText(context, "Permission refusée", Toast.LENGTH_SHORT).show()
    }

    // Launcher pour demander la permission d'accès à la caméra
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission accordée: je crée un fichier temporaire pour la photo
            val photoFile = File(context.filesDir, "temp_photo_${System.currentTimeMillis()}.jpg")
            // Je génère un URI sécurisé via FileProvider pour partager le fichier avec la caméra
            uriImageTemporaire = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", photoFile
            )
            cameraLauncher.launch(uriImageTemporaire!!) // Lance la prise de photo avec la caméra
        } else {
            // Permission refusée on informe l'utilisateur
            Toast.makeText(context, "Permission de la caméra refusée", Toast.LENGTH_SHORT).show()
        }
    }

    // Fonction envoi/modification d'un message
    fun envoyerEtReinitialiser() {
        if ((messageText.isNotBlank() || imageUris.isNotEmpty()) && !enCoursEnvoi) {
            if (modeEdition) {
                // On est en mode édition: modification du contenu d'un message existant
                modifierMessage(
                    conversationId = conversationId,
                    messageId = messageEditionId,
                    nouveauContenu = messageText,
                    context = context
                ) {
                    // Callback après modification réussie: je réinitialise le mode édition
                    modeEdition = false
                    messageEditionId = ""
                    messageText = ""
                }
            } else {
                // On est en mode création: envoi d'un nouveau message
                envoyerMessage(
                    content = messageText,
                    imageUris = imageUris,
                    conversationId = conversationId,
                    annonceId = annonceId,
                    idReceveur = idReceveur,
                    annonceTitle = annonceTitle,
                    context = context,
                    onStart = { enCoursEnvoi = true },
                    onComplete = {
                        enCoursEnvoi = false
                        imageUris = emptyList() // Vide la liste des images après envoi
                    }
                )
                messageText = "" // Vide le champ texte après envoi
            }

            // Scroll automatique vers le dernier message après un court délai
            // Le délai permet à Firestore de synchroniser le nouveau message
            scope.launch {
                kotlinx.coroutines.delay(120)
                if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size -1)
            }
        }
    }

    // Vérifie si un message peut encore être modifié
    // J'ai deux conditions:
    // 1.Le message n'a pas encore été lu par le destinataire. Dans ce cas, la modification est toujours autorisée
    // 2.Le message a été lu mais moins de 10 minutes se sont écoulées depuis l'envoi initial
    // Ca permet une fenêtre de correction même après estLu devient true
    fun peutModifierMessage(message: Message): Boolean {
        val maintenant = System.currentTimeMillis()
        val messageEnvoye = message.firestoreTimestamp.seconds * 1000
        val delaiModification = 2 * 60 * 1000 // 10 minutes en millisecondes

        // Condition 1
        if (!message.estLu) {
            return true
        }

        // Condition 2
        return (maintenant-messageEnvoye) < delaiModification
    }


    // Titre dynamique d’une annonce
    LaunchedEffect(annonceId) {
        chargerDetailsAnnonce(
            annonceId = annonceId,
            annonceTitle = annonceTitle
        ) { titre ->
            titreDynamiqueAnnonce = titre
            chargementAnnonceEnCours = false // Chargement terminé
        }
    }

    // Marquer lus les messages
    LaunchedEffect(conversationId, idUtilisateurActuel) {
        if (idUtilisateurActuel != null) {
            kotlinx.coroutines.delay(500)
            marquerMessagesCommeLus(conversationId, idUtilisateurActuel)
        }
    }

    // Point de suppression
    // Chargement du point de suppression pour l'utilisateur actuel
    // Quand un utilisateur supprime sa conversation et qu'il recoit un nouveau message,
    // il ne peut plus voir tout l'historique de cette conversation donc il faut filtrer
    LaunchedEffect(conversationId, idUtilisateurActuel) {
        if (idUtilisateurActuel != null) {
            try {
                val db = FirebaseFirestore.getInstance()
                val conversationRef = db.collection("conversations").document(conversationId)
                // Écouter les changements dans le document de conversation pour le point de suppression
                conversationRef.addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e("ChatActivity", "Erreur écoute point suppression", error)
                        return@addSnapshotListener
                    }
                    if (snapshot?.exists() == true) {
                        val pointsSuppressionMap = snapshot.get("pointsSuppression") as? Map<String, Any>
                        // ts = timestampSuppression contient la date et l'heure de suppression
                        val ts = pointsSuppressionMap?.get(idUtilisateurActuel) as? Timestamp

                        // Avant que le filtre des points de suppression fonctionne
                        // je loguais ces valeurs pour que je puisse debogger
                        Log.d("ChatActivity", "=== DEBUG DU POINT DE SUPPRESSION ====")
                        Log.d("ChatActivity", "Le document de cette conversation existe: ${snapshot.exists()}")
                        Log.d("ChatActivity", "pointsSuppressionMap: $pointsSuppressionMap") // Contenu de pointsSuppressionMap
                        Log.d("ChatActivity", "idUtilisateurActuel: $idUtilisateurActuel")
                        Log.d("ChatActivity", "timestampSuppression pour cet utilisateur: $ts") // Contenu de timestampSuppression
                        Log.d("ChatActivity", "================================")

                        pointSuppressionUtilisateur = ts

                        Log.d("ChatActivity", "Point de suppression pour l'utilisateur: $ts")
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatActivity", "Erreur chargement point suppression", e)
            }
        }
    }

    // Ici j'ecoute en temps réel les messages depuis firestore avec le filtrage par point de suppression
    LaunchedEffect(conversationId, pointSuppressionUtilisateur) {
        try {
            val db = FirebaseFirestore.getInstance()
            val messagesRef = db.collection("conversations")
                .document(conversationId)
                .collection("messages")
                .orderBy("firestoreTimestamp", Query.Direction.ASCENDING)

            // Le snapshot listener me permet d'écouter les changements en temps réel
            messagesRef.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("ChatActivity", "Erreur écoute messages", error)
                    chargementEnCours = false
                    return@addSnapshotListener
                }

                // Je recupere tous les messages
                snapshot?.let { qs -> // qs = QuerySnapshot
                    val tousAvecIds = qs.documents.mapNotNull { d -> // d = DocumentSnapshot
                        d.toObject(Message::class.java)?.let { m -> // m = Message
                            MessageAvecId(m, d.id)
                        }
                    }

                    // Filtrage selon le point de suppression de l'utilisateur
                    val filtres = if (pointSuppressionUtilisateur != null) {
                        // Je ne montre que les messages postérieurs au point de suppression
                        // Ceci garantit que l'utilisateur ne voit que les nouveaux messages après
                        // qu'il ai supprimer sa conversation
                        tousAvecIds.filter { mai -> // mai = MessageAvecId
                            val t = mai.message.firestoreTimestamp // t = Timestamp
                            // Comparaison pour savoir si le message est postérieur au point de suppression
                            t.seconds > pointSuppressionUtilisateur!!.seconds ||
                                    (t.seconds == pointSuppressionUtilisateur!!.seconds &&
                                            t.nanoseconds > pointSuppressionUtilisateur!!.nanoseconds)
                        }
                    } else {
                        // Si il n ya pas de point de suppression, j'affiche tous les messages
                        tousAvecIds
                    }

                    // Auto scroll vers le dernier message qui vient d'etre ajouté
                    if (filtres.size > messages.size) {
                        scope.launch {
                            if (filtres.isNotEmpty()) listState.animateScrollToItem(filtres.size -1)
                        }
                    }
                    // Mise à jour des états avec les messages filtrés
                    messagesAvecIds = filtres
                    messages = filtres.map { it.message }
                }
                chargementEnCours = false // Chargement terminé
            }
        } catch (e: Exception) {
            // En cas d'erreur, on arrete juste de loader
            Log.e("ChatActivity", "Exception lors de la configuration de l'écoute des messages", e)
            chargementEnCours = false
        }
    }

    // Structure principale
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(), // Gestion du clavier pour qu'il ne masque pas la saisie
        topBar = {
            // TopAppBar avec titre dynamique basé sur les détails de l'annonce
            // pour les vehicules je recupere marque, modele, annee et pour les categories autres je recupere le titre
            // permet a l'utilisateur de savoir il discute a propos de telle annonce
            TopAppBar(
                title = {
                    Column {
                        Text("Chat")
                        // Affiche le titre de l'annonce comme sous titre si disponible
                        if (!chargementAnnonceEnCours && titreDynamiqueAnnonce.isNotBlank() && titreDynamiqueAnnonce != "Chat") {
                            Text(
                                text = titreDynamiqueAnnonce,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            // Gestion du bouton retour selon le mode actuel
                            if (modeEdition) {
                                // En mode édition on annule l'édition au lieu de quitter
                                modeEdition = false
                                messageEditionId = ""
                                messageText = ""
                            } else {
                                // Mode normal on ferme l'activité
                                (context as ChatActivity).finish()
                            }
                        }
                    ) { Icon(Icons.Default.ArrowBack, contentDescription = "Retour") }
                }
            )
        }
    ) { paddingValues ->
        // Box pour mieux gérer l'espace disponible
        Box(
            modifier = Modifier.fillMaxSize().padding(paddingValues)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (chargementEnCours) {
                    // Pendant le chargement on affiche en attendant un spinner
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    // Bannière "mode édition"
                    if (modeEdition) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            tonalElevation = 4.dp,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Modifier le message",
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = {
                                    // Annulation de l'édition
                                    modeEdition = false
                                    messageEditionId = ""
                                    messageText = ""
                                }) { Text("Annuler") }
                            }
                        }
                    }

                    // Liste des messages et séparateurs de dates
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(messagesAvecIds) { index, mai ->
                            val message = mai.message
                            // Affiche un séparateur de date si nécessaire par 24h
                            if (separateurDate(messages, index)) {
                                SeparateurDate(message)
                            }

                            // Bulle avec menu contextuel
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = if (message.idExpediteur == idUtilisateurActuel)
                                    Alignment.CenterEnd else Alignment.CenterStart
                            ) {
                                // Composant MessageItem avec gestion appui long
                                MessageItem(
                                    message = message,
                                    isCurrentUser = message.idExpediteur == idUtilisateurActuel,
                                    onLongClick = { m ->
                                        // on n'autorise le menu que pour le propriétaire du message et pas si déjà supprimé
                                        if (m.idExpediteur == idUtilisateurActuel && !m.estSupprime) {
                                            messageSelectionne = mai
                                            afficherMenuContextuel = true
                                        }
                                    }
                                )
                                // Calcul de la position dans la liste
                                messagesAvecIds.size -index -1

                                // Menu contextuel modifier et supprimer
                                DropdownMenu(
                                    expanded = afficherMenuContextuel && messageSelectionne?.documentId == mai.documentId,
                                    onDismissRequest = {
                                        afficherMenuContextuel = false
                                        messageSelectionne = null
                                    },
                                    offset = DpOffset(
                                        x = if (message.idExpediteur == idUtilisateurActuel) (-50).dp else 0.dp,
                                        y = (-20).dp // Position
                                    )
                                ) {
                                    // Vérification si le message contient du texte valide (pas juste des images)
                                    val aDuTexte = messageSelectionne?.message?.content?.isNotEmpty() == true &&
                                            messageSelectionne?.message?.content !in listOf("Image","Images","Ce message a été supprimé")

                                    // Vérifie si le message peut être modifié selon les 2 conditions
                                    val peutModifier by remember(messageSelectionne?.message?.estLu, messageSelectionne?.message?.firestoreTimestamp) {
                                        derivedStateOf {
                                            messageSelectionne?.message?.let { peutModifierMessage(it) } ?: false
                                        }
                                    }

                                    // Afficher "modifier" seulement si ces conditions respectées
                                    // 1.Le message contient du texte
                                    // 2.Le message peut encore être modifié(selon conditions en haut)
                                    if (aDuTexte && peutModifier) {
                                        DropdownMenuItem(
                                            text = { Text("Modifier") },
                                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                            onClick = {
                                                modeEdition = true
                                                messageEditionId = messageSelectionne!!.documentId
                                                messageText = messageSelectionne!!.message.content
                                                afficherMenuContextuel = false
                                            }
                                        )
                                    }

                                    // Afficher "supprimer"
                                    // Toujours disponible pour les messages de l'utilisateur
                                    DropdownMenuItem(
                                        text = { Text("Supprimer") },
                                        leadingIcon = {
                                            Icon(Icons.Default.Delete, contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error)
                                        },
                                        onClick = {
                                            supprimerMessage(
                                                conversationId = conversationId,
                                                messageId = messageSelectionne!!.documentId,
                                                context = context
                                            ) {
                                                afficherMenuContextuel = false
                                                messageSelectionne = null
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Aperçu images à envoyer
                    if (imageUris.isNotEmpty()) {
                        Surface(modifier = Modifier.fillMaxWidth(), tonalElevation = 1.dp) {
                            // Liste des images sélectionnées
                            LazyRow(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                                items(imageUris) { uri ->
                                    Box(modifier = Modifier.padding(end = 4.dp)) {
                                        // Carte pour afficher chaque image avant d'envoyer
                                        Card(
                                            modifier = Modifier.size(60.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                        ) {
                                            Image(
                                                painter = rememberAsyncImagePainter(uri),
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        }
                                        // Petite icone suppression d'une image
                                        Surface(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .size(20.dp)
                                                .offset(x = 4.dp, y = (-4).dp),
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.error,
                                            onClick = {
                                                // Retire l'image de la liste des images à envoyer
                                                imageUris = imageUris.filter { it != uri }
                                            }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    Icons.Default.Close, contentDescription = "Supprimer",
                                                    tint = MaterialTheme.colorScheme.onError,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Barre de saisie en bas de l'écran
                    // Bouton "+" pour ajouter des images (masqué en mode édition)
                    // -TextField pour saisir le texte
                    // -Bouton d'envoi
                    Surface(modifier = Modifier.fillMaxWidth(), tonalElevation = 2.dp) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Bouton d'ajout d'images (caché en mode édition car on ne peut pas modifier les images)
                            if (!modeEdition) {
                                IconButton(onClick = { afficherDialoguePhoto = true }) {
                                    Icon(Icons.Default.Add, contentDescription = "Ajouter des images",
                                        tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            // Champ de saisie du texte avec placeholder dynamique
                            TextField(
                                value = messageText,
                                onValueChange = { messageText = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text(if (modeEdition) "Modifier mon message..." else "Écrivez ici...") },
                                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(onSend = { envoyerEtReinitialiser() })
                            )
                            // Bouton d'envoi/modification avec icone et état dynamiques
                            IconButton(
                                onClick = { envoyerEtReinitialiser() },
                                enabled = if (modeEdition) messageText.isNotBlank()
                                else (messageText.isNotBlank() || imageUris.isNotEmpty()) && !enCoursEnvoi
                            ) {
                                Icon(
                                    imageVector = when {
                                        modeEdition -> Icons.Default.Edit // Icone édition
                                        messageText.isNotBlank() || imageUris.isNotEmpty() -> Icons.Default.Send
                                        else -> Icons.Outlined.Send
                                    },
                                    contentDescription = if (modeEdition) "Modifier" else "Envoyer",
                                    tint = if (modeEdition || ((messageText.isNotBlank() || imageUris.isNotEmpty()) && !enCoursEnvoi))
                                        MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialogue de selection des images caméra/galerie
    // 1.Prendre une photo avec la caméra
    // 2.Sélectionner depuis la galerie
    // et gestion des permissions
    if (afficherDialoguePhoto) {
        AlertDialog(
            onDismissRequest = { afficherDialoguePhoto = false },
            title = { Text("Ajouter des photos - images", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column {
                    // Option 1: Caméra
                    Button(
                        onClick = {
                            afficherDialoguePhoto = false
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text("Prendre une photo")
                    }
                    Spacer(Modifier.height(8.dp))

                    // Option 2: Galerie
                    Button(
                        onClick = {
                            afficherDialoguePhoto = false
                            // Permission selon version Android
                            val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                                Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
                            galleryPermissionLauncher.launch(permission)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        )
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text("Choisir depuis la galerie")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { afficherDialoguePhoto = false }) {
                    Text("Annuler", color = MaterialTheme.colorScheme.primary)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

// Fonctions utilitaires pour firestore
// Marque tous les messages non lus d'une conversation comme lus pour un utilisateur
// Fonctionnement:
// 1.Je récupère tous les messages où l'utilisateur est le destinataire et estLu = false
// 2.Ensuite j'utilise un batch pour mettre à jour tous les messages en une seule transaction
// Pourquoi j'ai choisi l'utilisation du batch:
// -C'est plus efficace que des mises à jour individuelles
// -Atomique: soit tout réussit soit rien
// -Réduit le nombre de callback
private fun marquerMessagesCommeLus(conversationId: String, idUtilisateur: String?) {
    if (idUtilisateur == null) return
    try {
        val db = FirebaseFirestore.getInstance()
        // Marquer comme lus tous les messages reçus par cet utilisateur
        db.collection("conversations")
            .document(conversationId)
            .collection("messages")
            .whereEqualTo("idReceveur", idUtilisateur)
            .whereEqualTo("estLu", false)
            .get()
            .addOnSuccessListener { documents ->
                val batch = db.batch()
                // Ajoute chaque mise à jour au batch
                documents.forEach { document -> batch.update(document.reference, "estLu", true) }
                // Exécute toutes les mises à jour en une seule transaction
                if (documents.size() > 0) {
                    batch.commit()
                        .addOnSuccessListener { Log.d("ChatActivity", "${documents.size()} messages marqués comme lus") }
                        .addOnFailureListener { e -> Log.e("ChatActivity", "Erreur marquage lus", e) }
                }
            }
            // En cas d'erreur
            .addOnFailureListener { e -> Log.e("ChatActivity", "Erreur récupération non lus", e) }
    } catch (e: Exception) {
        Log.e("ChatActivity", "Exception marquer lus", e)
    }
}

// Cette fonction charge les détails de l'annonce
// J'utilise addSnapshotListener pour ecouter les changements en temps réel depuis firestore
// car une annonce peut etre modifier par un vendeur
private fun chargerDetailsAnnonce(
    annonceId: String,
    annonceTitle: String,
    onTitleLoaded: (String) -> Unit
) {
    // Si l'ID de l'annonce est vide on ne fait rien
    if (annonceId.isEmpty()) {
        Log.e("ChatActivity", "ID d'annonce vide")
        onTitleLoaded(annonceTitle.ifBlank { "Chat" })
        return
    }

    // Une annonce peut etre modifiée. Donc si un vendeur modifie
    // son titre il faut le réafficher le nouveau bon titre dans la conversation
    // Cette fonction écoute les changements. J'utilise un listener
    // La fonction pour essayer la collection des autres annonces
    fun essayerAutresAnnonces(db: FirebaseFirestore) {
        db.collection("autre_annonces")
            .document(annonceId.trim())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onTitleLoaded(annonceTitle.ifBlank { "Chat" }); return@addSnapshotListener
                }
                if (snapshot?.exists() == true) {
                    // Construction du titre pour une annonce autre
                    val titre = snapshot.getString("titre") ?: ""
                    onTitleLoaded(titre.ifBlank { "Annonce" })
                } else {
                    // Aucune annonce trouvée nulle part
                    onTitleLoaded(annonceTitle.ifBlank { "Chat" })
                }
            }
    }

    try {
        val db = FirebaseFirestore.getInstance()
        // Tentative dans la collection véhicules
        db.collection("annonces")
            .document(annonceId.trim())
            .addSnapshotListener { snapshot, error ->
                // En cas d'erreur
                if (error != null) { essayerAutresAnnonces(db); return@addSnapshotListener }
                if (snapshot?.exists() == true) {
                    // Construction du titre pour un véhicule
                    val marque = snapshot.getString("marque") ?: ""
                    val modele = snapshot.getString("modele") ?: ""
                    val annee = snapshot.getString("annee") ?: ""
                    val titre = if (marque.isNotEmpty() && modele.isNotEmpty() && annee.isNotEmpty())
                        "$marque $modele $annee" else if (marque.isNotEmpty()) marque else "Véhicule"
                    onTitleLoaded(titre)
                } else {
                    // Si pas trouvé dans annonces, essayer autre_annonces
                    essayerAutresAnnonces(db)
                }
            }
    } catch (e: Exception) {
        Log.e("ChatActivity", "Erreur écoute annonce", e)
        onTitleLoaded(annonceTitle.ifBlank { "Chat" })
    }
}

// Séparateur de date au format "dd MMMM yyyy"
@Composable
fun SeparateurDate(message: Message) {
    val dateFormat = SimpleDateFormat("dd MMMM yyyy", Locale.FRENCH)
    val dateString = dateFormat.format(Date(message.firestoreTimestamp.seconds * 1000))
    // Affichage du séparateur dans une Card
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Text(
                text = dateString,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

// Détermine si un séparateur de date doit être affiché avant un message
// Tous les messages qui sont du meme jour ne sont pas separes par un separateur de date
// La logique que j'utilise:
// -Toujours true pour le premier message (index 0)
// -true si le jour/mois/année diffère du message précédent
// -false sinon (même jour)
// Comparaison:
// -J'utilise Calendar pour comparer année, mois et jour
// -J'ignore les heures/minutes pour grouper par jour complet
private fun separateurDate(messages: List<Message>, indexActuel: Int): Boolean {
    if (indexActuel == 0) return true
    val messageActuel = messages[indexActuel]
    val messagePrecedent = messages[indexActuel -1]
    // Convertir les timestamps en dates
    val dateActuelle = Calendar.getInstance().apply {
        timeInMillis = messageActuel.firestoreTimestamp.seconds * 1000
    }
    val datePrecedente = Calendar.getInstance().apply {
        timeInMillis = messagePrecedent.firestoreTimestamp.seconds * 1000
    }
    // Retourne true si l'année, le mois ou le jour diffère
    return dateActuelle.get(Calendar.YEAR) != datePrecedente.get(Calendar.YEAR) ||
            dateActuelle.get(Calendar.MONTH) != datePrecedente.get(Calendar.MONTH) ||
            dateActuelle.get(Calendar.DAY_OF_MONTH) != datePrecedente.get(Calendar.DAY_OF_MONTH)
}

// Composable pour afficher un message dans le chat
// Gère l'affichage de tout ce qui comcerne messages texte et images
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageItem(
    message: Message,
    isCurrentUser: Boolean,
    onLongClick: (Message) -> Unit = {}
) {
    // États locaux pour gérer le mode plein ecran
    var afficherDialogueImage by remember { mutableStateOf(false) }
    var indexImageSelectionnee by remember { mutableStateOf(0) }

    // Extraction des URLs d'images multiples
    // Je parse les URLs des images depuis le champ imageUrl
    // et je les stocke comme "url1,url2,url3"
    val imageUrls = remember(message.imageUrl) {
        if (message.imageUrl != null)
            message.imageUrl.split(",").filter { it.isNotBlank() }
        else emptyList()
    }

    // Affichage du message
    // Column avec modificateur combinedClickable pour gérer:
    // -Click simple: rien ne se passe
    // -Long click seulement sur un message: ouvre le menu contextuel
    // On ne peut modifier une ou des images seulement les supprimer
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .combinedClickable(onClick = {}, onLongClick = { onLongClick(message) }),
        horizontalAlignment = if (isCurrentUser) Alignment.End else Alignment.Start
    ) {
        // Message supprimé
        if (message.estSupprime) {
            Card(
                modifier = Modifier.padding(horizontal = 8.dp).widthIn(max = 300.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Ce message a été supprimé",
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Text(
                        text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(
                            Date(
                                message.firestoreTimestamp.seconds * 1000
                            )
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
            return
        }

        // Vérifier d'abord si on a du texte valide
        val aDuTexte = message.content.isNotEmpty() &&
                message.content !in listOf("Image", "Images", "Ce message a été supprimé")

        // Bloc d'images
        // Gère l'affichage des images selon le nombre:
        // -1 image: affichage simple avec clic pour plein écran
        // -Multiples images: HorizontalPager avec indicateurs de pagination (dots ...)
        if (imageUrls.isNotEmpty()) {
            if (imageUrls.size == 1) {
                // Une seule image
                AsyncImage(
                    model = imageUrls[0],
                    contentDescription = "Image",
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .widthIn(max = 300.dp) // Largeur maximale
                        .height(200.dp) // Hauteur fixe
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            // Clic pour agrandir l'image
                            indexImageSelectionnee = 0
                            afficherDialogueImage = true
                        },
                    contentScale = ContentScale.Crop
                )
            } else {
                // Images multiples(caroussel)
                val pagerState = rememberPagerState(pageCount = { imageUrls.size })
                // Affiche les images avec navigation
                Box {
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 8.dp) // Ajustement horizontal
                            .widthIn(max = 300.dp)
                            .height(200.dp),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        // HorizontalPager pour swiper entre les images
                        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                            AsyncImage(
                                model = imageUrls[page],
                                contentDescription = "Image ${page + 1}",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable {
                                        indexImageSelectionnee = page
                                        afficherDialogueImage = true
                                    },
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
                // Indicateurs de pagination (...) sous les images
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    repeat(imageUrls.size) { i ->
                        val estSelectionne = pagerState.currentPage == i
                        Canvas(
                            modifier = Modifier.size(6.dp).padding(horizontal = 2.dp)
                        ) { drawCircle(if (estSelectionne) Color.Blue else Color.Gray) }
                    }
                }
            }

            // Si pas de texte, j'affiche l'heure + les coches sous l'image
            if (!aDuTexte) {
                TexteHeureEtCoches(
                    millis = message.firestoreTimestamp.seconds * 1000L, // Afficher l'heure du message
                    isCurrentUser = isCurrentUser,
                    estLu = message.estLu,
                    dansBullePrimaire = false,
                    estModifie = message.estModifie
                )
            }
        }

        // Bulle de texte avec couleurs différentes selon l'expéditeur:
        // -Utilisateur actuel: couleur primaire du thème
        // -Autres: couleur secondaire du thème
        // en fonction aussi du theme actuel
        if (aDuTexte) {
            Card(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).widthIn(max = 300.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isCurrentUser) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(Modifier.padding(8.dp)) {
                    // Texte du message
                    Text(
                        text = message.content,
                        color = if (isCurrentUser) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSecondaryContainer
                    )

                    // Heure + 'modifié' + coches
                    TexteHeureEtCoches(
                        millis = message.firestoreTimestamp.seconds * 1000L, // Afficher l'heure
                        isCurrentUser = isCurrentUser,
                        estLu = message.estLu,
                        dansBullePrimaire = true,
                        estModifie = message.estModifie
                    )
                }
            }
        }
    }

    // Plein écran pour images
    if (afficherDialogueImage && imageUrls.isNotEmpty()) {
        DialogueImage(
            imageUrls = imageUrls,
            indexInitial = indexImageSelectionnee,
            onDismiss = { afficherDialogueImage = false }
        )
    }
}

// Composable pour l’heure + 'modifié'(optionnel) + coches
@Composable
private fun TexteHeureEtCoches(
    millis: Long,
    isCurrentUser: Boolean,
    estLu: Boolean,
    dansBullePrimaire: Boolean,
    estModifie: Boolean = false
) {
    // Formatage de l'heure
    val heure = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Je construit le texte avec buildString pour concaténer les chaines
        // Ensuite j'ajoute "  modifié" si le message a été édité
        Text(
            text = buildString {
                append(heure)
                if (estModifie) append("  modifié")
            },
            style = MaterialTheme.typography.bodySmall,
            // Adaptation de la couleur selon le contexte d'affichage
            color = if (dansBullePrimaire) {
                if (isCurrentUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                else MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            }
        )

        // Coches de lecture (seulement pour les messages envoyés) pour l'utilisateur actuel
        if (isCurrentUser) {
            if (estLu) {
                // Bleue = lu
                Icon(
                    Icons.Filled.DoneAll,
                    contentDescription = "Message lu",
                    modifier = Modifier.size(12.dp),
                    tint = Color(0xFF00c2ff))
                Icon(
                    Icons.Filled.DoneAll,
                    contentDescription = "Message lu",
                    modifier = Modifier
                        .size(12.dp)
                        .offset(x = (-4).dp), // Décalage pour superposition
                    tint = Color(0xFF00c2ff))
            } else {
                // Grise = pas encore lu
                Icon(
                    Icons.Default.DoneAll,
                    contentDescription = "Message envoyé",
                    modifier = Modifier.size(12.dp),
                    tint = if (dansBullePrimaire)
                        (if (isCurrentUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                        else MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                Icon(
                    Icons.Default.DoneAll,
                    contentDescription = "Message envoyé",
                    modifier = Modifier
                        .size(12.dp)
                        .offset(x = (-4).dp),
                    tint = if (dansBullePrimaire)
                        (if (isCurrentUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                        else MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            }
        }
    }
}

// Ce composable permet d'afficher une image en plein écran quand on clique dessu
@Composable
fun DialogueImage(
    imageUrls: List<String>,
    indexInitial: Int = 0,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
                .background(Color.Black)
                .systemBarsPadding()
        ) {
            // HorizontalPager permet de swiper entre les images
            // Démarre à l'index sélectionné par l'utilisateur
            val paginationComplete = rememberPagerState(
                initialPage = indexInitial,
                pageCount = { imageUrls.size }
            )
            // Chaque page affiche une image
            HorizontalPager(
                state = paginationComplete,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                AsyncImage(
                    model = imageUrls[page],
                    contentDescription = "Image agrandie",
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { onDismiss() }, // Clic n'importe où pour fermer
                    contentScale = ContentScale.Fit
                )
            }
            // Bouton pour fermer
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.5f)
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Default.Close,
                        contentDescription = "Fermer",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp))
                }
            }
            // Indicateurs de pagination en bas si plusieurs images (...)
            // dans le mode plein ecran
            if (imageUrls.size > 1) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    repeat(imageUrls.size) { index ->
                        val estSelectionne = paginationComplete.currentPage == index
                        Canvas(
                            modifier = Modifier.size(8.dp).padding(horizontal = 2.dp)
                        ) { drawCircle(if (estSelectionne) Color.White else Color.White.copy(alpha = 0.5f)) }
                    }
                }
            }
        }
    }
}

// Opérations Firestore pour modifier/supprimer
// Je modifier le contenu texte du message et marque modifié + heure mis à jour
// -Met à jour le champ "content" avec le nouveau texte dans firebase
// -Marque le message comme modifié (estModifie = true) dans firebase
// -Enregistre le timestamp de modification pour référence dans firebase
private fun modifierMessage(
    conversationId: String,
    messageId: String,
    nouveauContenu: String,
    context: Context,
    onComplete: () -> Unit
) {
    val db = FirebaseFirestore.getInstance()
    val conversationRef = db.collection("conversations").document(conversationId)
    val timestampModification = Timestamp.now()

    // 1.Mettre à jour le message
    conversationRef
        .collection("messages")
        .document(messageId)
        .update(
            mapOf(
                "content" to nouveauContenu,
                "estModifie" to true,
                "timestampModification" to timestampModification
            )
        )
        .addOnSuccessListener {
            // 2.Vérifier si c'est le dernier message et mettre à jour la conversation
            conversationRef.collection("messages")
                .orderBy("firestoreTimestamp", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .addOnSuccessListener { querySnapshot ->
                    if (!querySnapshot.isEmpty) {
                        val dernierMessage = querySnapshot.documents[0]

                        // Si le message modifié est le dernier message
                        if (dernierMessage.id == messageId) {
                            conversationRef.update("lastMessage", nouveauContenu)
                                .addOnSuccessListener {
                                    Toast.makeText(context, "Message modifié", Toast.LENGTH_SHORT).show()
                                    onComplete()
                                }
                                .addOnFailureListener { e ->
                                    Log.e("ChatActivity", "Erreur mise à jour lastMessage", e)
                                    Toast.makeText(context, "Message modifié", Toast.LENGTH_SHORT).show()
                                    onComplete()
                                }
                        } else {
                            Toast.makeText(context, "Message modifié", Toast.LENGTH_SHORT).show()
                            onComplete()
                        }
                    } else {
                        Toast.makeText(context, "Message modifié", Toast.LENGTH_SHORT).show()
                        onComplete()
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("ChatActivity", "Erreur vérification dernier message", e)
                    Toast.makeText(context, "Message modifié", Toast.LENGTH_SHORT).show()
                    onComplete()
                }
        }
        .addOnFailureListener { e ->
            Log.e("ChatActivity", "Erreur modification", e)
            Toast.makeText(context, "Erreur lors de la modification", Toast.LENGTH_SHORT).show()
            onComplete()
        }
}

// Effectue une suppression d'un message dans firebase
// -estSupprime = true: flag principal de suppression
// -timestampSuppression: enregistre quand la suppression a eu lieu
// -contenu = "Ce message a été supprimé": comme texte de remplacement
private fun supprimerMessage(
    conversationId: String,
    messageId: String,
    context: Context,
    onComplete: () -> Unit
) {
    val db = FirebaseFirestore.getInstance()
    val conversationRef = db.collection("conversations").document(conversationId)
    val timestampSuppression = Timestamp.now()

    // 1.Marquer le message comme supprimé
    conversationRef
        .collection("messages")
        .document(messageId)
        .update(
            mapOf(
                "estSupprime" to true,
                "timestampSuppression" to timestampSuppression,
                "content" to "Ce message a été supprimé"
            )
        )
        .addOnSuccessListener {
            // 2.Vérifier si c'est le dernier message et mettre à jour la conversation
            conversationRef.collection("messages")
                .orderBy("firestoreTimestamp", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .addOnSuccessListener { querySnapshot ->
                    if (!querySnapshot.isEmpty) {
                        val dernierMessage = querySnapshot.documents[0]

                        // Si le message supprimé est le dernier message
                        if (dernierMessage.id == messageId) {
                            conversationRef.update("lastMessage", "Dernier message supprimé")
                                .addOnSuccessListener {
                                    Toast.makeText(context, "Message supprimé", Toast.LENGTH_SHORT).show()
                                    onComplete()
                                }
                                .addOnFailureListener { e ->
                                    Log.e("ChatActivity", "Erreur mise à jour lastMessage", e)
                                    Toast.makeText(context, "Message supprimé", Toast.LENGTH_SHORT).show()
                                    onComplete()
                                }
                        } else {
                            Toast.makeText(context, "Message supprimé", Toast.LENGTH_SHORT).show()
                            onComplete()
                        }
                    } else {
                        Toast.makeText(context, "Message supprimé", Toast.LENGTH_SHORT).show()
                        onComplete()
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("ChatActivity", "Erreur vérification dernier message", e)
                    Toast.makeText(context, "Message supprimé", Toast.LENGTH_SHORT).show()
                    onComplete()
                }
        }
        .addOnFailureListener { e ->
            Log.e("ChatActivity", "Erreur suppression message", e)
            Toast.makeText(context, "Erreur lors de la suppression", Toast.LENGTH_SHORT).show()
            onComplete()
        }
}

// Fonction principale pour envoyer un message (texte et/ou images) firestore
// Gère l'upload des images, la création du document Firestore
// 1.Vérification de l'authentification de l'utilisateur
// 2.Upload des images vers Firebase Storage (si présentes)
// 3.Création du message dans Firestore
// 4.Mise à jour de la conversation (dernier message, timestamp)
// 5.Envoi de notification push FCM au destinataire
// 6.Gestion de la réactivation de conversation supprimée
// Pour les transactions Firestore j'utilise runTransaction
// pour garantir l'atomicité
// soit tout réussit (message + mise à jour conversation), soit rien
private fun envoyerMessage(
    content: String,
    imageUris: List<Uri>,
    conversationId: String,
    annonceId: String,
    idReceveur: String,
    annonceTitle: String,
    context: Context,
    onStart: () -> Unit,
    onComplete: () -> Unit
) {
    // Vérification de l'authentification
    val currentUser = FirebaseAuth.getInstance().currentUser ?: run {
        Log.e("ChatActivity", "Utilisateur non connecté"); onComplete(); return
    }

    onStart()

    // Quand j'envoyais des images j'avais des crash donc j'utilise ici un AtomicBoolean
    // Si onComplete() est appelé plusieurs fois ca évite les callback multiples
    val callbackExecute = AtomicBoolean(false)
    fun finaliserProprement() {
        if (callbackExecute.compareAndSet(false, true)) onComplete()
    }

    // Ici je gere la réactivation
    // -Fonction suspend pour gérer la réactivation d'une conversation supprimée
    // -L'utilisateur peut supprimer une conversation (point de suppression)
    // -Si l'autre participant envoie un message, la conversation est réactivée
    // -Un timestamp de réactivation est enregistré
    // Donc au final je filtre seulement les messages qui ont été supprimé mais tout est encore dans firebase
    suspend fun gererReactivationConversation(conversationRef: com.google.firebase.firestore.DocumentReference) {
        try {
            val db = FirebaseFirestore.getInstance()
            val conversationSnapshot = conversationRef.get().await()
            if (conversationSnapshot.exists()) {
                val pointsSuppressionMap = conversationSnapshot.get("pointsSuppression") as? Map<String, Any> ?: emptyMap()
                // Si le destinataire avait supprimé la conversation
                if (pointsSuppressionMap.containsKey(idReceveur)) {
                    Log.d("ChatActivity", "Le destinataire $idReceveur avait supprimé la conversation")

                    // Au lieu que je supprime le point, on ajoute un marqueur de réactivation
                    // Cela permet à MesMessagesActivity de détecter que la conversation doit réapparaître
                    // Mais le point de suppression reste pour filtrer les anciens messages dans ChatActivity
                    val ts = Timestamp.now()
                    conversationRef.update(mapOf("conversationReactivee.$idReceveur" to ts)).await()
                }
            }
        } catch (e: Exception) {
            Log.e("ChatActivity", "Erreur réactivation", e)
        }
    }
    try {
        // Envoi avec images
        // J'utilise encore ImageUploadUtils, ma classe utilitaire
        if (imageUris.isNotEmpty()) {
            ImageUploadUtils.uploadImages(
                imageUris = imageUris,
                contentType = ImageUploadUtils.ContentType.CHAT, // mon dossier dans Storage
                context = context,
                onProgress = { _, _ -> }, // vide car je ne veux pas que l'utilisateur voit la progression
                onComplete = { imageUrls ->
                    try {
                        if (imageUrls != null && imageUrls.isNotEmpty()) {
                            val db = FirebaseFirestore.getInstance()
                            val conversationRef = db.collection("conversations").document(conversationId)
                            val now = Timestamp.now()
                            // Détermine le contenu textuel du message
                            val messageContent = if (content.isNotEmpty()) content else "Images"
                            val lastMessage = if (content.isNotEmpty()) content else "Images"

                            // Je crre l'objet message avec:
                            // -Texte (ou images si vide)
                            // -URLs des images concaténées par virgule
                            // -Métadonnées (expéditeur, destinataire, annonce, timestamp,)
                            // -estLu = false par défaut
                            val message = Message(
                                idExpediteur = currentUser.uid,
                                idReceveur = idReceveur,
                                content = messageContent,
                                imageUrl = imageUrls.joinToString(","),
                                firestoreTimestamp = now,
                                annonceId = annonceId,
                                estLu = false
                            )

                            // Transaction firestore
                            // -Opérations atomiques:
                            // 1.Réactivation si nécessaire
                            // 2.Ajout du message à la sous-collection "messages"
                            // 3.Mise à jour du document conversation (lastMessage, timestamp)
                            // Ensuite si une opération échoue tout est annulé et je fais un rollback
                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    // Reactiver la conversation
                                    gererReactivationConversation(conversationRef)
                                    // Transaction Firestore pour garantir la cohérence
                                    db.runTransaction { tr ->
                                        // Sauvegarde du message
                                        // msgRef = messageReference
                                        val msgRef = conversationRef.collection("messages").document()
                                        tr.set(msgRef, message)
                                        // convData = conversationData
                                        val convData = mapOf(
                                            "participants" to listOf(currentUser.uid, idReceveur),
                                            "lastMessage" to lastMessage,
                                            "firestoreTimestamp" to now,
                                            "annonceId" to annonceId,
                                            "annonceTitle" to annonceTitle
                                        )
                                        // Mise à jour de la conversation
                                        // tr = transaction
                                        tr.set(conversationRef, convData, com.google.firebase.firestore.SetOptions.merge())
                                    }.addOnSuccessListener {
                                        // Envoie une notification push au destinataire
                                        // Condition: l'expéditeur différent du destinataire (je ne peux pas recevoir mes propres notifications)
                                        // La notification contient:
                                        // -Nom de l'expéditeur
                                        // -Contenu du message
                                        // -IDs pour navigation directe vers la conversation
                                        if (currentUser.uid != idReceveur) {
                                            CoroutineScope(Dispatchers.IO).launch {
                                                try {
                                                    FCMUtils.sendNotificationToUser(
                                                        idReceveur = idReceveur,
                                                        senderName = "",
                                                        messageContent = if (content.isNotEmpty()) content else "Vous avez reçu des images",
                                                        conversationId = conversationId,
                                                        annonceId = annonceId,
                                                        annonceTitle = annonceTitle
                                                    )
                                                } catch (_: Exception) {
                                                    // Échec de la notification non bloquant
                                                }
                                            }
                                        }
                                        finaliserProprement()
                                    }.addOnFailureListener { _ -> finaliserProprement() }
                                } catch (_: Exception) { finaliserProprement() }
                            }
                        } else {
                            // Upload d'images échoué je finalise
                            finaliserProprement()
                        }
                    } catch (_: Exception) { finaliserProprement() }
                }
            )
        } else {
            // Envoi texte seul plus rapide
            val db = FirebaseFirestore.getInstance()
            val conversationRef = db.collection("conversations").document(conversationId)
            val now = Timestamp.now()

            // Utiliser coroutines pour la gestion de réactivation
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    gererReactivationConversation(conversationRef)
                    db.runTransaction { tr ->
                        // Transaction firestore pour garantir la cohérence des données
                        // convSnap = conversationSnapshot
                        val convSnap = tr.get(conversationRef)
                        // Si la conversation n'existe pas encore, la créer d'abord
                        if (!convSnap.exists()) {
                            val conversation = Conversation(
                                id = conversationId,
                                participants = listOf(currentUser.uid, idReceveur),
                                lastMessage = content,
                                firestoreTimestamp = now,
                                annonceId = annonceId,
                                annonceTitle = annonceTitle
                            )
                            tr.set(conversationRef, conversation)
                        } else {
                            // On met juste à jour le dernier message et l'heure
                            tr.update(conversationRef, mapOf(
                                "lastMessage" to content,
                                "firestoreTimestamp" to now
                            ))
                        }
                        // Création du message dans la sous collection "messages"
                        val message = Message(
                            idExpediteur = currentUser.uid,
                            idReceveur = idReceveur,
                            content = content,
                            firestoreTimestamp = now,
                            annonceId = annonceId,
                            estLu = false
                        )
                        val messageRef = conversationRef.collection("messages").document()
                        tr.set(messageRef, message)
                    }.addOnSuccessListener {
                        // Notification push pour le destinataire
                        // meme logique que pour les messages avec images
                        if (currentUser.uid != idReceveur) {
                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    FCMUtils.sendNotificationToUser(
                                        idReceveur = idReceveur,
                                        senderName = "",
                                        messageContent = content,
                                        conversationId = conversationId,
                                        annonceId = annonceId,
                                        annonceTitle = annonceTitle
                                    )
                                } catch (_: Exception) {
                                    // Échec de la notification non bloquant
                                }
                            }
                        }
                        // Envoi réussi
                        finaliserProprement()
                    }.addOnFailureListener { _ -> finaliserProprement() } // En cas d'erreur
                } catch (_: Exception) { finaliserProprement() } // En cas d'erreur de réactivation
            }
        }
    } catch (_: Exception) { finaliserProprement() }
}
// Lorsque j'implementais les fonctionnalites du chat, j'ai rencontré plusieurs problemes donc raison
// pour laquelle je loguais toujours pour pouvoir m'aider a debogger et trouver les erreurs -bugs
// D'ailleurs un peu partout dans mes autres fichiers je le fais pour les meme raisons