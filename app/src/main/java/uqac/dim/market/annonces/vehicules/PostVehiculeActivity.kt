package uqac.dim.market.annonces.vehicules

import android.Manifest
import android.app.Activity
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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import uqac.dim.market.ui.theme.MarketTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import uqac.dim.market.utils.ThemeManager
import uqac.dim.market.utils.ImageUploadUtils

// Activité principale pour la création et publication d'annonces de véhicules
// Cette classe gère l'interface utilisateur permettant aux vendeurs de publier leurs annonces
// avec toutes les informations nécessaires
class PostVehiculeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MarketTheme(
                darkTheme = ThemeManager.isDarkMode(this@PostVehiculeActivity)
            ) {
                PostVehiculeScreen()
            }
        }
    }
}

// Interface utilisateur principale pour la création d'annonces véhicules
// Gère tous les champs de saisie, validation et upload des images
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostVehiculeScreen() {
    val context = LocalContext.current

    // Variables d'état pour stocker les informations du véhicule
    var marque by remember { mutableStateOf("") }
    var modele by remember { mutableStateOf("") }
    var annee by remember { mutableStateOf("") }
    var proprios by remember { mutableStateOf("") }
    var kilometrage by remember { mutableStateOf("") }
    var boite by remember { mutableStateOf("") }
    var prix by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var imageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

    // États pour contrôler l'affichage des dialogues
    var afficherDialogueBoite by remember { mutableStateOf(false) }
    var afficherDialogueAnnee by remember { mutableStateOf(false) }
    var afficherDialoguePhoto by remember { mutableStateOf(false) }
    var uriImageTemporaire by remember { mutableStateOf<Uri?>(null) }
    var enChargement by remember { mutableStateOf(false) }
    var progressionChargement by remember { mutableStateOf(0 to 0) }

    // Listes prédéfinies pour les champs de sélection
    val boiteOptions = listOf("Transmission manuelle", "Transmission automatique")
    val anneesOptions = (2027 downTo 1990).map { it.toString() }

    //  Gestionnaire pour la sélection multiple d'images depuis la galerie
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        // Pour chaque image sélectionnée, ajouter les nouvelles images à la liste existante
        imageUris = imageUris + uris
    }

    // Gestionnaire pour la prise de photo avec l'appareil photo
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && uriImageTemporaire != null) {
            imageUris = imageUris + uriImageTemporaire!!
        }
    }

    // Gestionnaire de permission pour accéder à la galerie
    val galleryPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            galleryLauncher.launch("image/*")
        } else {
            Toast.makeText(context, "Permission refusée", Toast.LENGTH_SHORT).show()
        }
    }

    // Gestion de permission pour utiliser l'appareil photo
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Création d'un fichier temporaire pour stocker la photo qui vient d'être prise
            val photoFile = File(context.filesDir, "temp_photo_${System.currentTimeMillis()}.jpg")
            uriImageTemporaire = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                photoFile
            )
            cameraLauncher.launch(uriImageTemporaire!!)
        } else {
            Toast.makeText(context, "Permission caméra refusée", Toast.LENGTH_SHORT).show()
        }
    }

    // Interface utilisateur principale pour scroller
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Espacement
        Spacer(modifier = Modifier.height(32.dp))

        // Titre de la page
        Text(
            text = "Nouvelle annonce - Véhicules",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Champ de saisie pour la marque du véhicule
        OutlinedTextField(
            value = marque,
            onValueChange = { marque = it },
            label = { Text("Marque") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !enChargement,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Champ de saisie pour le modèle du véhicule
        OutlinedTextField(
            value = modele,
            onValueChange = { modele = it },
            label = { Text("Modèle") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !enChargement, // Désactive le champ pendant le chargement
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Champ de sélection pour l'année (lecture seule avec dialogue)
        // pas possible de saisir ici
        Box(modifier = Modifier.fillMaxWidth().clickable {
            if (!enChargement) afficherDialogueAnnee = true
        }) {
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
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledBorderColor = MaterialTheme.colorScheme.outline
                )
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        // Champ numérique pour le nombre de propriétaires précédents du véhicule
        OutlinedTextField(
            value = proprios,
            onValueChange = {
                // on accepte que les chiffres
                if (it.isEmpty() || it.all { char -> char.isDigit() }) proprios = it
            },
            label = { Text("Nombre de propriétaires du véhicule") },
            keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            enabled = !enChargement, // Désactive le champ pendant le chargement
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Champ numérique pour le kilométrage
        OutlinedTextField(
            value = kilometrage,
            onValueChange = {
                // on accepte que les chiffres
                if (it.isEmpty() || it.all { char -> char.isDigit() }) kilometrage = it
            },
            label = { Text("Kilométrage") },
            keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            enabled = !enChargement,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Champ de selection pour le type de boite de vitesse
        // pas possible de saisir ici
        Box(modifier = Modifier.fillMaxWidth().clickable {
            if (!enChargement) afficherDialogueBoite = true
        }) {
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
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    disabledBorderColor = MaterialTheme.colorScheme.outline
                )
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        // Champ numérique pour le prix de vente
        OutlinedTextField(
            value = prix,
            onValueChange = {
                // on accepte que les chiffres
                if (it.isEmpty() || it.all { char -> char.isDigit() }) prix = it
            },
            label = { Text("Prix") },
            keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            enabled = !enChargement, // Désactive le champ pendant le chargement
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Champ de texte pour la description
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            enabled = !enChargement,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Bouton pour ajouter des photos - images
        Button(
            onClick = {
                afficherDialoguePhoto = true
            },
            enabled = !enChargement,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
        ) {
            Text("Ajouter des images - photos")
        }

        // Affichage des images sélectionnées avec possibilité de suppression individuelle
        if (imageUris.isNotEmpty()) {
            LazyRow(modifier = Modifier.padding(vertical = 8.dp)) {
                items(imageUris) { uri ->
                    Box {
                        Image(
                            painter = rememberAsyncImagePainter(uri),
                            contentDescription = null,
                            modifier = Modifier.size(100.dp).padding(end = 8.dp),
                            contentScale = ContentScale.Crop
                        )
                        // Bouton pour supprimer une image individuelle (petite croix)
                        IconButton(
                            onClick = {
                                imageUris = imageUris.filter { it != uri }
                            },
                            modifier = Modifier.align(Alignment.TopEnd)
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

            // Affichage du nombre d'images sélectionnées
            Text(
                text = "${imageUris.size} image(s) sélectionnée(s)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Affichage de la progression d'upload
        if (enChargement && progressionChargement.second > 0) {
            Column {
                LinearProgressIndicator(
                    progress = { progressionChargement.first.toFloat() / progressionChargement.second.toFloat() },
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

        // Bouton de publication avec validation des champs obligatoires
        Button(
            onClick = {
                if (marque.isNotEmpty() && modele.isNotEmpty() && annee.isNotEmpty() &&
                    boite.isNotEmpty() && prix.isNotEmpty() && imageUris.isNotEmpty()) {
                    enChargement = true
                    progressionChargement = 0 to imageUris.size

                    // Upload des images et création de l'annonce
                    // Utilisation de l'objet ImageUploadUtils pour gérer l'upload des images
                    ImageUploadUtils.uploadImages(
                        imageUris = imageUris,
                        contentType = ImageUploadUtils.ContentType.VEHICULE,
                        context = context,
                        onProgress = { current, total ->
                            progressionChargement = current to total
                        }
                    ) { imageUrls ->
                        if (imageUrls != null) {
                            // Préparation des données pour Firestore
                            val annonceData = mapOf(
                                "marque" to marque,
                                "modele" to modele,
                                "annee" to annee,
                                "proprietaires" to proprios,
                                "kilometrage" to kilometrage,
                                "boite" to boite,
                                "prix" to prix,
                                "description" to description,
                                "imageUrls" to imageUrls.joinToString(","),
                                "categorie" to "vehicule",
                                "userId" to (FirebaseAuth.getInstance().currentUser?.uid ?: "inconnu"),
                                "datePublication" to System.currentTimeMillis(),
                                "statut" to "disponible"
                            )

                            // Enregistrement dans Firestore dans la collection "annonces"
                            FirebaseFirestore.getInstance().collection("annonces")
                                .add(annonceData)
                                .addOnSuccessListener {
                                    Toast.makeText(context, "Annonce publiée", Toast.LENGTH_SHORT).show()
                                    (context as? Activity)?.finish()
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(context, "Erreur Firestore : ${e.message}", Toast.LENGTH_LONG).show()
                                }
                        }
                        enChargement = false // Réinitialisation de l'état d'upload
                        progressionChargement = 0 to 0 // Réinitialisation de la progression
                    }
                } else {
                    Toast.makeText(context, "Veuillez remplir tous les champs requis.", Toast.LENGTH_LONG).show()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !enChargement,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
        ) {
            if (enChargement) {
                // Affichage du loader pendant l'upload
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Publication...")
            } else {
                Text("Publier")
            }
        }
    }

    // Dialogue de sélection du type de boîte de vitesse
    if (afficherDialogueBoite) {
        AlertDialog(
            onDismissRequest = { afficherDialogueBoite = false },
            title = {
                Text(
                    "Boîte de vitesse",
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(modifier = Modifier.selectableGroup()) {
                    boiteOptions.forEach { text ->
                        RadioButtonWithText(text = text, selected = (text == boite)) {
                            boite = text
                            afficherDialogueBoite = false
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { afficherDialogueBoite = false },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Annuler")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            textContentColor = MaterialTheme.colorScheme.onSurface
        )
    }

    // Dialogue de choix entre caméra et galerie pour les photos
    if (afficherDialoguePhoto) {
        AlertDialog(
            onDismissRequest = { afficherDialoguePhoto = false },
            title = {
                Text(
                    "Ajouter des photos -images",
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
                            // permission selon la version Android
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

    // Dialogue de sélection de l'année avec liste déroulante
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
                LazyColumn(modifier = Modifier.height(300.dp).selectableGroup()) {
                    items(anneesOptions) { anneeOption ->
                        RadioButtonWithText(text = anneeOption, selected = (anneeOption == annee)) {
                            annee = anneeOption
                            afficherDialogueAnnee = false
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { afficherDialogueAnnee = false },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Annuler")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            textContentColor = MaterialTheme.colorScheme.onSurface
        )
    }
}

// Composant réutilisable pour les Radiobuttons
// Je le reutilise dans les dialogues de sélection annee et transmssion(boite de vitesse)
@Composable
fun RadioButtonWithText(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton
            )
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
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