package uqac.dim.market.annonces.vehicules

import android.Manifest
import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.google.firebase.firestore.FirebaseFirestore
import uqac.dim.market.ui.theme.MarketTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import kotlinx.coroutines.tasks.await
import uqac.dim.market.utils.ThemeManager
import uqac.dim.market.utils.ImageUploadUtils

// Activité pour modifier une annonce de véhicule existante
// Cette activité est spécialisée pour les véhicules
// J'ai  refactorisé en intégrant ImageUploadUtils pour unifier la logique d'upload
// avec le reste de l'application et assurer la cohérence
class ModifierAnnonceVehicule : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Récupération de l'ID de l'annonce depuis l'intent précédent
        val annonceId = intent.getStringExtra("annonceId") ?: return

        setContent {
            MarketTheme(
                darkTheme = ThemeManager.isDarkMode(this@ModifierAnnonceVehicule)
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ModifierAnnonceScreen(annonceId = annonceId)
                }
            }
        }
    }
}

// Composable principal de modification d'annonce véhicule
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModifierAnnonceScreen(annonceId: String) {
    val context = LocalContext.current
    var chargementEnCours by remember { mutableStateOf(true) }
    var enSauvegarde by remember { mutableStateOf(false) }
    var messageErreur by remember { mutableStateOf<String?>(null) }
    var progressionChargement by remember { mutableStateOf(0 to 0) } // Pour la barre de progression

    // Données de l'annonce deja remplies avec les données existantes
    var marque by remember { mutableStateOf("") }
    var modele by remember { mutableStateOf("") }
    var annee by remember { mutableStateOf("") }
    var proprios by remember { mutableStateOf("") }
    var kilometrage by remember { mutableStateOf("") }
    var boite by remember { mutableStateOf("") }
    var prix by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var imageUris by remember { mutableStateOf<List<Uri>>(emptyList()) } // Nouvelles images ajoutées
    var urlsImagesExistantes by remember { mutableStateOf<List<String>>(emptyList()) } // Images deja sur Firebase

    // États pour les dialogues
    var afficherDialogueBoite by remember { mutableStateOf(false) } // Transmission
    var afficherDialogueAnnee by remember { mutableStateOf(false) }
    var afficherDialoguePhoto by remember { mutableStateOf(false) }
    var uriImageTemporaire by remember { mutableStateOf<Uri?>(null) }

    // Options de choix disponibles
    val boiteOptions = listOf("Transmission manuelle", "Transmission automatique")
    val anneesOptions = (2027 downTo 1990).map { it.toString() }

    // Launchers pour les images
    // J'utilise GetMultipleContents pour permettre la sélection de plusieurs photos en une fois
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        // Ajout des nouvelles images à la liste existante
        imageUris = imageUris + uris
    }

    // Lancher pour prendre une photo avec l'appareil photo
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

    // Permissions pour utiliser l'appareil photo
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
                .collection("annonces") // Nom de ma collection dans Firestore
                .document(annonceId)
                .get()
                .await()

            if (snapshot.exists()) {
                // Remplissage des champs avec les données existantes
                marque = snapshot.getString("marque") ?: ""
                modele = snapshot.getString("modele") ?: ""
                annee = snapshot.getString("annee") ?: ""
                proprios = snapshot.getString("proprietaires") ?: ""
                kilometrage = snapshot.getString("kilometrage") ?: ""
                boite = snapshot.getString("boite") ?: ""
                prix = snapshot.getString("prix") ?: ""
                description = snapshot.getString("description") ?: ""

                // Je recupere les URLs d'images existantes
                // Je gère deux formats: une liste d'URLs ou une seule URL
                val imageUrlsStr = snapshot.getString("imageUrls")
                if (!imageUrlsStr.isNullOrBlank()) {
                    urlsImagesExistantes = imageUrlsStr.split(",").filter { it.isNotBlank() }
                }
                val singleImageUrl = snapshot.getString("imageUrl") // Image unique
                if (!singleImageUrl.isNullOrBlank() && urlsImagesExistantes.isEmpty()) {
                    urlsImagesExistantes = listOf(singleImageUrl)
                }
            } else {
                messageErreur = "Annonce introuvable"
            }
        } catch (e: Exception) {
            messageErreur = "Erreur lors du chargement: ${e.message}"
        } finally {
            chargementEnCours = false // Fin du chargement
        }
    }

    // Affichage de l'écran selon l'état de chargement
    when {
        // Affichage du loader pendant le chargement
        chargementEnCours -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        // Message d'erreur si le chargement échoue
        messageErreur != null -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Erreur: $messageErreur",
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
        // Affichage de l'écran principal de modification
        else -> {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // TopAppBar avec bouton retour
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
                                imageVector = Icons.Default.ArrowBack,
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

                // Contenu scrollable pour les formulaires longs
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Marque
                    OutlinedTextField(
                        value = marque,
                        onValueChange = { marque = it },
                        label = { Text("Marque") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !enSauvegarde, // Désactivé pendant la sauvegarde
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            cursorColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Modèle
                    OutlinedTextField(
                        value = modele,
                        onValueChange = { modele = it },
                        label = { Text("Modèle") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !enSauvegarde, // Désactivé pendant la sauvegarde
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            cursorColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Champ année clickable
                    // J'utilise un TextField désactivé mais cliquable
                    OutlinedTextField(
                        value = annee,
                        onValueChange = {},
                        readOnly = true,
                        enabled = false,
                        label = { Text("Année") },
                        trailingIcon = {
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                Log.d("DEBUG", "Année clické!") // je faisais un debogage pour voir si ça marche
                                afficherDialogueAnnee = true
                            },
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Nombre de propriétaires
                    OutlinedTextField(
                        value = proprios,
                        onValueChange = {
                            // Number seulement
                            if (it.isEmpty() || it.all { char -> char.isDigit() }) proprios = it
                        },
                        label = { Text("Nombre de propriétaires") },
                        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !enSauvegarde, // Désactivé pendant la sauvegarde
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            cursorColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Kilométrage
                    OutlinedTextField(
                        value = kilometrage,
                        onValueChange = {
                            // Number seulement
                            if (it.isEmpty() || it.all { char -> char.isDigit() }) kilometrage = it
                        },
                        label = { Text("Kilométrage") },
                        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !enSauvegarde, // Désactivé pendant la sauvegarde
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            cursorColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Champ boîte de vitesse clickable
                    // J'utilise un TextField désactivé mais cliquable(la meme pour l'année)
                    OutlinedTextField(
                        value = boite,
                        onValueChange = {},
                        readOnly = true,
                        enabled = false,
                        label = { Text("Boîte de vitesse") },
                        trailingIcon = {
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                Log.d("DEBUG", "Boite clické!")
                                afficherDialogueBoite = true
                            },
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Prix
                    OutlinedTextField(
                        value = prix,
                        onValueChange = {
                            // Number seulement
                            if (it.isEmpty() || it.all { char -> char.isDigit() }) prix = it
                        },
                        label = { Text("Prix") },
                        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !enSauvegarde, // Désactivé pendant la sauvegarde
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            cursorColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Description
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        enabled = !enSauvegarde, // Désactivé pendant la sauvegarde
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            cursorColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Section des images existantes
                    if (urlsImagesExistantes.isNotEmpty()) {
                        Text(
                            "Images actuelles:",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
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
                                    // Bouton de suppression
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
                            "Nouvelles images:",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
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
                                    // // Bouton pour supprimer une nouvelle image
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
                                // J'ai eu des crashs du a progressionChargement.second qui pouvait prendre 0 comme valeur donc la division par 0 donnait un NaN
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

                    // Boutons annuler et mettre à jour
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Boutons Annuler
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

                        // Boutons Mettre à jour
                        Button(
                            onClick = {
                                // Validation des champs obligatoires
                                if (marque.isNotEmpty() && modele.isNotEmpty() && annee.isNotEmpty() &&
                                    boite.isNotEmpty() && prix.isNotEmpty() &&
                                    (urlsImagesExistantes.isNotEmpty() || imageUris.isNotEmpty())) {
                                    enSauvegarde = true
                                    progressionChargement = 0 to imageUris.size

                                    if (imageUris.isEmpty()) {
                                        // Pas de nouvelles images, mise à jour directe
                                        updateFirestoreDocument(annonceId, mapOf(
                                            "marque" to marque,
                                            "modele" to modele,
                                            "annee" to annee,
                                            "proprietaires" to proprios,
                                            "kilometrage" to kilometrage,
                                            "boite" to boite,
                                            "prix" to prix,
                                            "description" to description
                                        ), urlsImagesExistantes, context) {
                                            enSauvegarde = false
                                        }

                                    } else {
                                        // Upload des nouvelles images avec ImageUploadUtils
                                        ImageUploadUtils.uploadImages(
                                            imageUris = imageUris,
                                            contentType = ImageUploadUtils.ContentType.VEHICULE,
                                            context = context,
                                            onProgress = { current, total ->
                                                progressionChargement = current to total // Mettre à jour la progression
                                            }
                                        ) { newImageUrls ->
                                            if (newImageUrls != null) {
                                                // Combiner les anciennes et nouvelles URLs
                                                val allImageUrls = urlsImagesExistantes + newImageUrls

                                                updateFirestoreDocument(annonceId, mapOf(
                                                    "marque" to marque,
                                                    "modele" to modele,
                                                    "annee" to annee,
                                                    "proprietaires" to proprios,
                                                    "kilometrage" to kilometrage,
                                                    "boite" to boite,
                                                    "prix" to prix,
                                                    "description" to description
                                                ), allImageUrls, context) {
                                                    enSauvegarde = false
                                                    progressionChargement = 0 to 0
                                                }
                                            } else {
                                                Toast.makeText(context, "Erreur lors de l'upload des images", Toast.LENGTH_LONG).show()
                                                enSauvegarde = false
                                                progressionChargement = 0 to 0 // Réinitialiser la progression
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
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
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

    // Dialogue pour la transmission
    if (afficherDialogueBoite) {
        AlertDialog(
            onDismissRequest = { afficherDialogueBoite = false }, // Fermeture du dialogue
            title = {
                Text(
                    "Boîte de vitesse",
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(modifier = Modifier.selectableGroup()) {
                    boiteOptions.forEach { text ->
                        RadioButtonWithTextLocal(
                            text = text,
                            selected = (text == boite)
                        ) {
                            boite = text
                            afficherDialogueBoite = false
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { afficherDialogueBoite = false }) {
                    Text(
                        "Annuler",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            textContentColor = MaterialTheme.colorScheme.onSurface
        )
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
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
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
                TextButton(onClick = { afficherDialoguePhoto = false }) {
                    Text(
                        "Annuler",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            textContentColor = MaterialTheme.colorScheme.onSurface
        )
    }

    // Dialogue pour l'année
    if (afficherDialogueAnnee) {
        AlertDialog(
            onDismissRequest = { afficherDialogueAnnee = false },
            title = {
                Text(
                    "Année",
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                // LazyColumn pour gérer la longue liste d'années (1990-2027)
                LazyColumn(modifier = Modifier.height(300.dp).selectableGroup()) {
                    items(anneesOptions) { anneeOption ->
                        RadioButtonWithTextLocal(
                            text = anneeOption,
                            selected = (anneeOption == annee)
                        ) {
                            annee = anneeOption
                            afficherDialogueAnnee = false // Fermeture du dialogue
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { afficherDialogueAnnee = false }) {
                    Text(
                        "Annuler",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            textContentColor = MaterialTheme.colorScheme.onSurface
        )
    }
}

// Composable réutilisable pour les RadioButtons avec texte
// J'utilise ce composant pour les dialogues de sélection (année, boîte de vitesse)
@Composable
fun RadioButtonWithTextLocal(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton // Pour l'accessibilité
            )
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null, // Pas d'action par défaut. Le clic est géré par le Row parent
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colorScheme.primary,
                unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// Cette fonction mets à jour le document Firestore pour la categorie véhicule
// Elle s'occupe de la sauvegarde dans Firebase après l'upload des images
private fun updateFirestoreDocument(
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
        .collection("annonces")
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
