package uqac.dim.market.utils

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import uqac.dim.market.R

// Utilitaire centralisé pour gérer les avatars de l'utilisateur
// J'ai encore utilisé le principe DRY(Don't Repeat Yourself) pour éviter de dupliquer le code pour chaque avatar
object UserProfileUtils {

    // Référence à la base de données Firestore
    private val db = FirebaseFirestore.getInstance()

    // Liste des avatars disponibles pour l'instant
    val avatarsDisponibles = listOf(
        R.drawable.avatar_1,
        R.drawable.avatar_2,
        R.drawable.avatar_3,
        R.drawable.avatar_4,
        R.drawable.avatar_5,
        R.drawable.avatar_6
    )

    // J'enregistre l'avatar de l'utilisateur dans Firestore
    suspend fun sauvegarderAvatarUtilisateur(utilisateurId: String, avatarId: Int): Boolean {
        return try {
            val profilData = mapOf(
                "userId" to utilisateurId,
                "avatarId" to avatarId,
                "lastUpdated" to System.currentTimeMillis()
            )

            // Sauvegarde dans Firestore
            db.collection("utilisateurs").document(utilisateurId)
                .set(profilData, com.google.firebase.firestore.SetOptions.merge())
                .await()

            Log.d("UserProfileUtils", "Avatar enregistré pour userId: $utilisateurId, avatarId: $avatarId")
            true
        } catch (e: Exception) {
            Log.e("UserProfileUtils", "Erreur enregistrement avatar: ${e.message}")
            false
        }
    }

    // Récupérer l'avatar de l'utilisateur depuis Firestore
    suspend fun recupererAvatarUtilisateur(utilisateurId: String): Int? {
        return try {
            val document = db.collection("utilisateurs").document(utilisateurId).get().await()
            if (document.exists()) {
                val avatarId = document.getLong("avatarId")?.toInt()
                Log.d("UserProfileUtils", "Avatar récupéré pour userId: $utilisateurId, avatarId: $avatarId")
                avatarId
            } else {
                Log.d("UserProfileUtils", "Aucun avatar trouvé pour userId: $utilisateurId")
                null
            }
        } catch (e: Exception) {
            Log.e("UserProfileUtils", "Erreur récupération avatar: ${e.message}")
            null
        }
    }

    // Récupérer l'avatar actuel de l'utilisateur connecté
    suspend fun recupererAvatarUtilisateurActuel(): Int? {
        val utilisateurId = FirebaseAuth.getInstance().currentUser?.uid ?: return null
        return recupererAvatarUtilisateur(utilisateurId)
    }

    // Sauvegarder l'avatar de l'utilisateur connecté
    suspend fun sauvegarderAvatarUtilisateurActuel(avatarId: Int): Boolean {
        val utilisateurId = FirebaseAuth.getInstance().currentUser?.uid ?: return false
        return sauvegarderAvatarUtilisateur(utilisateurId, avatarId)
    }
}