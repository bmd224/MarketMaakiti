package uqac.dim.market.annonces.autres

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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import uqac.dim.market.ui.theme.MarketTheme
import uqac.dim.market.utils.ThemeManager
import uqac.dim.market.utils.ImageUploadUtils

// Activité principale pour publier une annonce de la catégorie "Autres"
// Permet aux utilisateurs de créer des annonces avec titre, prix, description et image
class PostAutreAnnonceActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Application du theme
        setContent {
            MarketTheme(
                darkTheme = ThemeManager.isDarkMode(this@PostAutreAnnonceActivity)
            ) {
                PostAutreAnnonceScreen()
            }
        }
    }
}

// Interface utilisateur pour la création d'annonces "Autres"
// Gère la saisie des informations et l'upload des images
@Composable
fun PostAutreAnnonceScreen() {
    val context = LocalContext.current

    // Variables d'état pour les champs du formulaire
    var titre by remember { mutableStateOf("") }
    var prix by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var imageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

    // États pour la gestion des dialogues et processus
    var afficherDialoguePhoto by remember { mutableStateOf(false) }
    var uriImageTemporaire by remember { mutableStateOf<Uri?>(null) }
    var enChargement by remember { mutableStateOf(false) }
    var progressionChargement by remember { mutableStateOf(0 to 0) }

    // Lanceur pour sélectionner plusieurs images depuis la galerie
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        imageUris = imageUris + uris
    }

    // Lanceur pour prendre des photos avec l'appareil photo
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && uriImageTemporaire != null) {
            // On ajoute la nouvelle image à notre liste existante
            imageUris = imageUris + uriImageTemporaire!!
        }
    }

    // Gestion des permissions pour accéder à la galerie
    val galleryPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            galleryLauncher.launch("image/*")
        } else {
            Toast.makeText(context, "Permission refusée", Toast.LENGTH_SHORT).show()
        }
    }

    // Gestion des permissions pour utiliser l'appareil photo
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Création d'un fichier temporaire pour stocker la photo prise
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

    // Interface utilisateur principale
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Espacement
            Spacer(modifier = Modifier.height(32.dp))

            // Titre de la page
            Text(
                "Nouvelle annonce - Autres",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Champ de saisie pour le titre de l'annonce
            OutlinedTextField(
                value = titre,
                onValueChange = { titre = it },
                label = { Text("Titre") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !enChargement,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    cursorColor = MaterialTheme.colorScheme.primary
                )
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Champ de saisie pour le prix (uniquement des chiffres)
            OutlinedTextField(
                value = prix,
                onValueChange = {
                    // on n'accepte que les chiffres
                    if (it.isEmpty() || it.all { char -> char.isDigit() }) prix = it
                },
                label = { Text("Prix") },
                keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                enabled = !enChargement, // Désactive le champ pendant le chargement
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    cursorColor = MaterialTheme.colorScheme.primary
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
                enabled = !enChargement, // Désactive le champ pendant le chargement
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    cursorColor = MaterialTheme.colorScheme.primary
                )
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Bouton pour ouvrir le dialogue de sélection de photos
            Button(
                onClick = {
                    afficherDialoguePhoto = true
                },
                enabled = !enChargement, // Désactive le bouton pendant le chargement
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
                            Card(
                                modifier = Modifier
                                    .size(100.dp)
                                    .padding(end = 8.dp),
                                shape = RoundedCornerShape(8.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Image(
                                    painter = rememberAsyncImagePainter(uri),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            // Bouton pour supprimer une image individuelle
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

            // Bouton pour publier l'annonce
            Button(
                onClick = {
                    // Validation des champs requis avant publication
                    if (titre.isNotEmpty() && prix.isNotEmpty() && imageUris.isNotEmpty()) {
                        enChargement = true
                        progressionChargement = 0 to imageUris.size

                        // Upload des images et création de l'annonce
                        // J'utilise ImageUploadUtils pour gérer l'upload des images
                        ImageUploadUtils.uploadImages(
                            imageUris = imageUris,
                            contentType = ImageUploadUtils.ContentType.AUTRE,
                            context = context,
                            onProgress = { current, total ->
                                progressionChargement = current to total
                            }
                        ) { imageUrls ->
                            if (imageUrls != null) {
                                // Préparation des données pour pouvoir stocker dans ma BD dans Firestore
                                val annonceData = mapOf(
                                    "titre" to titre,
                                    "prix" to prix,
                                    "description" to description,
                                    "imageUrls" to imageUrls.joinToString(","), // Concaténation des URLs
                                    "userId" to (FirebaseAuth.getInstance().currentUser?.uid ?: "inconnu"),
                                    "datePublication" to System.currentTimeMillis(),
                                    "statut" to "disponible"
                                )

                                // Sauvegarde dans la bonne collection Firestore
                                FirebaseFirestore.getInstance()
                                    .collection("autre_annonces")
                                    .add(annonceData)
                                    .addOnSuccessListener {
                                        Toast.makeText(context, "Annonce publiée avec succès", Toast.LENGTH_SHORT).show()
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
                        Toast.makeText(context, "Veuillez remplir tous les champs et ajouter au moins une image.", Toast.LENGTH_LONG).show()
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
    }

    // Dialogue pour choisir entre appareil photo et galerie
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
                            afficherDialoguePhoto = false // Fermeture du dialogue
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA) // Demande de permission
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
                            // permissions selon la version Android
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