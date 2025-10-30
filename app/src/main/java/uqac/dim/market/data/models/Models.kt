package uqac.dim.market.data.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude

// Cette data class représente un message
// Elle gère les messages échangés entre utilisateurs concernant une annonce
data class Message(
    val idExpediteur: String = "",
    val idReceveur: String = "",
    val content: String = "",            // Contenu textuel du message
    val imageUrl: String? = null,        // URL de l'image attachée
    @get:Exclude val timestamp: Long = System.currentTimeMillis(),  // Timestamp local du message
    val firestoreTimestamp: Timestamp = Timestamp.now(),           // Timestamp de Firestore pour la synchronisation
    val annonceId: String = "",          // ID de l'annonce concernée par ce message
    val estLu: Boolean = false,          // Statut de lecture du message

    // Nouveaux champs pour la gestion de l'édition et de la suppression
    val estModifie: Boolean = false,     // Indique si le message a été modifié
    val timestampModification: Timestamp? = null,  // Horodatage de la dernière modification
    val estSupprime: Boolean = false,    // Indique si le message a été supprimé
    val timestampSuppression: Timestamp? = null    // Horodatage de la suppression
) {
    // Constructeur secondaire utilisé pour la conversion depuis Firestore
    // Je l'utilise car Firestore ne peut pas directement mapper le timestamp local
    // Je calcule le timestamp local à partir du timestamp Firestore
    constructor(
        idExpediteur: String = "",
        idReceveur: String = "",
        content: String = "",
        imageUrl: String? = null,
        firestoreTimestamp: Timestamp = Timestamp.now(), // Timestamp Firestore pour la synchronisation
        annonceId: String = "",
        estLu: Boolean = false,
        estModifie: Boolean = false,
        timestampModification: Timestamp? = null,
        estSupprime: Boolean = false,
        timestampSuppression: Timestamp? = null
    ) : this(
        idExpediteur = idExpediteur,
        idReceveur = idReceveur,
        content = content,
        imageUrl = imageUrl,
        // Je convertis le timestamp Firestore en millisecondes pour l'utilisation locale
        timestamp = firestoreTimestamp.seconds * 1000,
        firestoreTimestamp = firestoreTimestamp,
        annonceId = annonceId,
        estLu = estLu,
        estModifie = estModifie,
        timestampModification = timestampModification,
        estSupprime = estSupprime,
        timestampSuppression = timestampSuppression
    )
}

// Cette data class représente une conversation entre deux utilisateurs
// La conversation regroupe tous les messages concernant une annonce spécifique
data class Conversation(
    val id: String = "",
    val participants: List<String> = listOf(),    // Liste des IDs des participants (vendeur et acheteur)
    val lastMessage: String = "",                 // Dernier message de la conversation pour l'apercu
    @get:Exclude val lastMessageTimestamp: Long = System.currentTimeMillis(),
    val firestoreTimestamp: Timestamp = Timestamp.now(),
    val annonceId: String = "",
    val annonceTitle: String = "",
    val pointsSuppression: Map<String, Timestamp>? = null  // Points de suppression par utilisateur
) {
    // Constructeur secondaire pour la conversion Firestore
    // Même principe que pour Message on gère la conversion des timestamps
    constructor(
        id: String = "",
        participants: List<String> = listOf(),
        lastMessage: String = "",
        firestoreTimestamp: Timestamp = Timestamp.now(),
        annonceId: String = "",
        annonceTitle: String = "",
        pointsSuppression: Map<String, Timestamp>? = null
    ) : this(
        id = id,
        participants = participants,
        lastMessage = lastMessage,
        // Conversion du timestamp Firestore vers le format local pour nos calculs
        lastMessageTimestamp = firestoreTimestamp.seconds * 1000,
        firestoreTimestamp = firestoreTimestamp,
        annonceId = annonceId,
        annonceTitle = annonceTitle,
        pointsSuppression = pointsSuppression
    )
}