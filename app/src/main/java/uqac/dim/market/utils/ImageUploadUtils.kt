package uqac.dim.market.utils

import android.content.Context
import android.net.Uri
import android.widget.Toast
import com.google.firebase.storage.FirebaseStorage
import java.util.UUID

// La classe utilitaire centralisée pour la gestion des uploads d'images vers Firebase Storage
// J'utilise dans cette classe le pattern Singleton (object) ce qui garantit une instance unique
// partagée dans l'appli
// Je standardise la logique d'upload et je reutilise dans les fichiers dont j'ai besoin
object ImageUploadUtils {

    // Configuration du bucket Firebase Storage
    private val storage = FirebaseStorage.getInstance("gs://maketapp-25fa0.firebasestorage.app") // Le bucket de mon app dans Firebase

    // Référence racine du stockage Firebase
    // Cette référence pointe vers la racine du bucket. Toutes les opérations
    // (upload, suppression, ...) se font à partir de cette référence
    private val storageRef = storage.reference

    // Types de contenu pour organiser le stockage
    enum class ContentType(val nomDossier: String) {
        VEHICULE("vehicules"),
        AUTRE("autres"),
        CHAT("chat_images"),
        PROFILE("profiles")
    }

    // Cette méthode gère l'upload de plusieurs images en parallèle, ce qui améliore
    // les performances par rapport à un upload séquentiel. Elle utilise des callbacks
    // pour notifier de l'avancement et du résultat final
    fun uploadImages(
        imageUris: List<Uri>,
        contentType: ContentType,
        context: Context,
        onProgress: ((current: Int, total: Int) -> Unit)? = null,
        onComplete: (imageUrls: List<String>?) -> Unit
    ) {
        // Vérification des images que la liste n'est pas vide
        if (imageUris.isEmpty()) {
            Toast.makeText(context, "Aucune image à Uploader", Toast.LENGTH_SHORT).show()
            onComplete(emptyList())
            return
        }

        // Liste pour stocker les URLs des images
        val imageUrls = mutableListOf<String>()
        var completedUploads = 0 // Compteur pour suivre le nombre d'uploads terminés (succès ou échec)

        // Lancement de tous les uploads en parallèle (non bloquant)
        imageUris.forEachIndexed { index, uri ->
            uploadImage(uri, contentType) { downloadUrl ->
                // Callback appelé quand l'upload est terminé
                if (downloadUrl != null) {
                    // Succès: ajouter l'URL à la liste des résultats
                    imageUrls.add(downloadUrl)
                } else {
                    // Échec: notifier l'utilisateur mais continuer les autres uploads
                    Toast.makeText(context, "Erreur upload image ${index + 1}", Toast.LENGTH_SHORT).show()
                }

                // Incrémenter le compteur d'uploads terminés
                completedUploads++
                onProgress?.invoke(completedUploads, imageUris.size)

                // Si tous les uploads terminés
                if (completedUploads == imageUris.size) {
                    // Comparer le nombre de succès avec le nombre total d'images
                    if (imageUrls.size == imageUris.size) {
                        // Tous les uploads ont réussi, retourner toutes les URLs
                        onComplete(imageUrls)
                    } else {
                        // Au moins un upload a échoué alors afficher un message et retourner null
                        Toast.makeText(context, "Certaines images n'ont pas pu être uploadées", Toast.LENGTH_LONG).show()
                        onComplete(null)
                    }
                }
            }
        }
    }

    // Upload d'une image. Le processus en deux étapes (upload puis récupération de l'URL) est nécessaire car:
    // -putFile() upload le fichier mais ne retourne pas directement l'URL
    // -downloadUrl doit être récupérée séparément après l'upload
    private fun uploadImage(
        imageUri: Uri,
        contentType: ContentType,
        onComplete: (String?) -> Unit
    ) {
        // Génération d'un nom unique avec extension .jpg standardisée
        val fileName = "${UUID.randomUUID()}.jpg" // UUID (Universally Unique Identifier) garantit que chaque fichier a un nom unique
        val imageRef = storageRef.child("${contentType.nomDossier}/$fileName")

        // Démarrage de l'upload du fichier vers Firebase Storage
        // putFile() lit le contenu de l'URI locale ensuite on l'envoie au cloud
        imageRef.putFile(imageUri)
            .addOnSuccessListener {
                // Récupération de l'URL de téléchargement
                imageRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                    onComplete(downloadUrl.toString())
                }.addOnFailureListener {
                    // En cas d'erreur lors de la récupération de l'URL
                    onComplete(null)
                }
            }
            .addOnFailureListener {
                // En cas d'erreur lors de l'upload
                onComplete(null)
            }
    }
}