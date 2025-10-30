package uqac.dim.market.annonces.autres

import android.Manifest
import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.google.firebase.firestore.FirebaseFirestore
import uqac.dim.market.ui.theme.MarketTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.tasks.await
import uqac.dim.market.utils.ThemeManager
import uqac.dim.market.utils.ImageUploadUtils
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.IconButton

// Activité principale pour permettre aux utilisateurs de modifier leurs annonces dans la catégorie "Autre"
// Cette classe gère l'interface utilisateur et récupère l'ID de l'annonce depuis l'intent
// J'ai intégré ImageUploadUtils pour unifier la logique d'upload
// avec le reste de l'application et assurer la cohérence
class ModifierAutreAnnonce : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // On récupère l'ID de l'annonce passé en paramètre depuis l'écran précédent
        // Si l'ID est manquant, on ferme directement l'activité
        val annonceId = intent.getStringExtra("annonceId") ?: return

        setContent {
            // Application du thème
            MarketTheme(
                darkTheme = ThemeManager.isDarkMode(this@ModifierAutreAnnonce)
            ) {
                ModifierAutreAnnonceScreen(annonceId = annonceId)
            }
        }
    }
}

// Composable principal pour l'écran de modification d'une annonce
// Gère toute la logique d'affichage et de modification des données
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModifierAutreAnnonceScreen(annonceId: String) {
    val context = LocalContext.current
    // États pour gérer l'interface utilisateur
    var chargementEnCours by remember { mutableStateOf(true) }
    var enSauvegarde by remember { mutableStateOf(false) }
    var messageErreur by remember { mutableStateOf<String?>(null) }
    var isVendu by remember { mutableStateOf(false) }
    var progressionChargement by remember { mutableStateOf(0 to 0) } // Pour la barre de progression

    // Variables d'état pour les champs du formulaire
    // deja remplies avec les données existantes
    var titre by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var prix by remember { mutableStateOf("") }
    var imageUris by remember { mutableStateOf<List<Uri>>(emptyList()) } // Nouvelles images ajoutées
    var urlsImagesExistantes by remember { mutableStateOf<List<String>>(emptyList()) } // Images deja sur Firebase

    // États pour controler l'affichage des dialogues
    var afficherDialoguePhoto by remember { mutableStateOf(false) }
    var uriImageTemporaire by remember { mutableStateOf<Uri?>(null) }

    // Launcher pour la sélection multiple d'images depuis la galerie
    // J'utilise GetMultipleContents pour permettre la sélection de plusieurs images en une fois
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        // Ajout des nouvelles images à la liste existante
        imageUris = imageUris + uris
    }

    // Launcher pour prendre une photo avec l'appareil photo
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && uriImageTemporaire != null) {
            // Si la photo a été prise avec succès on l'ajoute à nos images
            imageUris = imageUris + uriImageTemporaire!!
        }
    }

    // Gestion des permissions pour accéder à la galerie
    val galleryPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            galleryLauncher.launch("image/*") // Lance la sélection d'images
        } else {
            Toast.makeText(context, "Permission refusée", Toast.LENGTH_SHORT).show()
        }
    }

    // Gestion des permissions pour utiliser l'appareil photo
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Création d'un fichier temporaire pour stocker la photo
            val photoFile = File(context.filesDir, "temp_photo_${System.currentTimeMillis()}.jpg")
            uriImageTemporaire = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider", // Défini dans mon manifest
                photoFile
            )
            cameraLauncher.launch(uriImageTemporaire!!)
        } else {
            Toast.makeText(context, "Permission caméra refusée", Toast.LENGTH_SHORT).show()
        }
    }

    // LaunchedEffect pour récupérer les données de l'annonce
    LaunchedEffect(annonceId) {
        try {
            // Récupération des données depuis Firestore
            val snapshot = FirebaseFirestore.getInstance()
                .collection("autre_annonces") // Nom de ma collection dans Firestore
                .document(annonceId)
                .get()
                .await()

            if (snapshot.exists()) {
                // Remplissage des champs avec les données existantes
                titre = snapshot.getString("titre") ?: ""
                description = snapshot.getString("description") ?: ""
                prix = snapshot.getString("prix") ?: ""
                isVendu = snapshot.getString("statut") == "vendu" // Vérification du statut

                // Je recupere les URLs d'images existantes
                // Je gère deux formats: une liste d'URLs ou une seule URL
                val imageUrlsStr = snapshot.getString("imageUrls")
                if (!imageUrlsStr.isNullOrBlank()) {
                    urlsImagesExistantes = imageUrlsStr.split(",").filter { it.isNotBlank() }
                }
                val imageUrlUnique = snapshot.getString("imageUrl")
                if (!imageUrlUnique.isNullOrBlank() && urlsImagesExistantes.isEmpty()) {
                    urlsImagesExistantes = listOf(imageUrlUnique)
                }
            }
            chargementEnCours = false // Fin du chargement
        } catch (e: Exception) {
            // En cas d'erreur, on affiche un message
            messageErreur = e.message
            chargementEnCours = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Modifier mon annonce",
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        (context as? Activity)?.finish()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            color = MaterialTheme.colorScheme.background
        ) {
        // Si l'annonce est vendue, on ne peut plus la modifier
        if (isVendu) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Cette annonce est marquée comme vendue et ne peut pas être modifiée",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Button(
                        onClick = { (context as? Activity)?.finish() },
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        Text("Retour")
                    }
                }
            }
            return@Surface
        }

        when {
            // Affichage du loader pendant le chargement
            chargementEnCours -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            // Message d'erreur si le chargement échoue
            messageErreur != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Erreur: $messageErreur",
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            // Interface principale de modification
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Champ de saisie pour le titre
                    OutlinedTextField(
                        value = titre,
                        onValueChange = { titre = it },
                        label = { Text("Titre") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !enSauvegarde,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Champ de saisie pour le prix
                    OutlinedTextField(
                        value = prix,
                        onValueChange = {
                            // Number seulement
                            if (it.isEmpty() || it.all { char -> char.isDigit() }) prix = it
                        },
                        label = { Text("Prix") },
                        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !enSauvegarde,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Champ de saisie pour la description
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        enabled = !enSauvegarde,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Section pour afficher les images existantes
                    if (urlsImagesExistantes.isNotEmpty()) {
                        Text(
                            text = "Images actuelles:",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        LazyRow(modifier = Modifier.padding(vertical = 8.dp)) {
                            items(urlsImagesExistantes) { imageUrl ->
                                Box {
                                    // Affichage de chaque image
                                    Image(
                                        painter = rememberAsyncImagePainter(imageUrl),
                                        contentDescription = null,
                                        modifier = Modifier.size(100.dp).padding(end = 8.dp),
                                        contentScale = ContentScale.Crop
                                    )
                                    // Bouton pour supprimer une image
                                    IconButton(
                                        onClick = {
                                            urlsImagesExistantes = urlsImagesExistantes.filter { it != imageUrl }
                                        },
                                        modifier = Modifier.align(Alignment.TopEnd),
                                        enabled = !enSauvegarde
                                    ) {
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

                    // Bouton pour ajouter de nouvelles images
                    Button(
                        onClick = { if (!enSauvegarde) afficherDialoguePhoto = true },
                        enabled = !enSauvegarde,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text("Ajouter des images - photos")
                    }

                    // Nouvelles images ajoutées
                    if (imageUris.isNotEmpty()) {
                        Text(
                            text = "Nouvelles images:",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        LazyRow(modifier = Modifier.padding(vertical = 8.dp)) {
                            items(imageUris) { uri ->
                                Box {
                                    Image(
                                        painter = rememberAsyncImagePainter(uri),
                                        contentDescription = null,
                                        modifier = Modifier.size(100.dp).padding(end = 8.dp),
                                        contentScale = ContentScale.Crop
                                    )
                                    // Bouton pour supprimer une nouvelle image
                                    IconButton(
                                        onClick = {
                                            imageUris = imageUris.filter { it != uri }
                                        },
                                        modifier = Modifier.align(Alignment.TopEnd),
                                        enabled = !enSauvegarde
                                    ) {
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

                    // J'affiche la progression d'upload
                    if (enSauvegarde && progressionChargement.second > 0) {
                        Column {
                            LinearProgressIndicator(
                                progress = {
                                    if (progressionChargement.second > 0) {
                                        (progressionChargement.first.toFloat() / progressionChargement.second.toFloat()).coerceIn(0f, 1f)
                                    } else {
                                        0f
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                text = "Upload ${progressionChargement.first}/${progressionChargement.second} images...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    // Boutons Annuler et Mettre à jour
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Bouton Annuler
                        Button(
                            onClick = {
                                (context as? Activity)?.finish()
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !enSauvegarde,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            )
                        ) {
                            Text("Annuler")
                        }

                        // Bouton Mettre à jour
                        Button(
                            onClick = {
                                // Validation des champs obligatoires
                                if (titre.isNotEmpty() && prix.isNotEmpty() &&
                                    (urlsImagesExistantes.isNotEmpty() || imageUris.isNotEmpty())) {
                                    enSauvegarde = true
                                    progressionChargement = 0 to imageUris.size

                                    if (imageUris.isEmpty()) {
                                        // Pas de nouvelles images, mise à jour directe
                                        updateFirestoreAutreDocument(annonceId, mapOf(
                                            "titre" to titre,
                                            "description" to description,
                                            "prix" to prix
                                        ), urlsImagesExistantes, context) {
                                            enSauvegarde = false
                                        }
                                    } else {
                                        // Upload des nouvelles images avec ImageUploadUtils
                                        ImageUploadUtils.uploadImages(
                                            imageUris = imageUris,
                                            contentType = ImageUploadUtils.ContentType.AUTRE,
                                            context = context,
                                            onProgress = { current, total ->
                                                progressionChargement = current to total
                                            }
                                        ) { newImageUrls ->
                                            if (newImageUrls != null) {
                                                // Combiner les anciennes et nouvelles URLs
                                                val allImageUrls = urlsImagesExistantes + newImageUrls
                                                updateFirestoreAutreDocument(annonceId, mapOf(
                                                    "titre" to titre,
                                                    "description" to description,
                                                    "prix" to prix
                                                ), allImageUrls, context) {
                                                    enSauvegarde = false
                                                    progressionChargement = 0 to 0 // Réinitialiser la progression a la fin
                                                }
                                            } else {
                                                Toast.makeText(context, "Erreur lors de l'upload des images", Toast.LENGTH_LONG).show()
                                                enSauvegarde = false
                                                progressionChargement = 0 to 0
                                            }
                                        }
                                    }
                                } else {
                                    Toast.makeText(context, "Veuillez remplir tous les champs", Toast.LENGTH_LONG).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !enSauvegarde,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            if (enSauvegarde) {
                                // Affichage du loader pendant la sauvegarde
                                CircularProgressIndicator(modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Mise à jour en cours...")
                            } else {
                                Text("Mettre à jour")
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialogue pour l'ajout de photos
    if (afficherDialoguePhoto) {
        AlertDialog(
            onDismissRequest = { afficherDialoguePhoto = false },
            title = {
                Text(
                    "Ajouter des photos",
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column {
                    // Option pour prendre une photo
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
                        Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = "Appareil photo",
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text("Prendre une photo")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    // Option pour choisir depuis la galerie
                    Button(
                        onClick = {
                            afficherDialoguePhoto = false
                            // Gestion des différentes versions d'Android pour les permissions
                            val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                Manifest.permission.READ_MEDIA_IMAGES
                            } else {
                                Manifest.permission.READ_EXTERNAL_STORAGE
                            }
                            galleryPermissionLauncher.launch(permission)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        )
                    ) {
                        Icon(
                            Icons.Default.PhotoLibrary,
                            contentDescription = "Galerie",
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text("Choisir depuis la galerie")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { afficherDialoguePhoto = false },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Annuler")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurface
        )
    }
    }
}

// Cette fonction mets à jour le document Firestore pour la catégorie Autre
// Elle s'occupe de la sauvegarde dans Firebas
private fun updateFirestoreAutreDocument(
    annonceId: String,
    data: Map<String, String>,
    allImageUrls: List<String>,
    context: Context,
    onComplete: () -> Unit
) {
    // Préparation des données pour Firestore
    val annonceData = data.toMutableMap() as MutableMap<String, Any>
    annonceData["imageUrls"] = allImageUrls.joinToString(",")

    // Mise à jour du document dans Firestore
    FirebaseFirestore.getInstance()
        .collection("autre_annonces")
        .document(annonceId)
        .update(annonceData)
        .addOnSuccessListener {
            Toast.makeText(context, "Annonce modifiée avec succès", Toast.LENGTH_SHORT).show()
            // Fermeture de l'activité après la mise à jour
            (context as? Activity)?.finish()
        }
        .addOnFailureListener { e ->
            Toast.makeText(context, "Erreur lors de la modification : ${e.message}", Toast.LENGTH_LONG).show()
        }
        // On appelle onComplete après la mise à jour
        .addOnCompleteListener {
            onComplete()
        }
}