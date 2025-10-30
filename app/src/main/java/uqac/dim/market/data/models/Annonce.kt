package uqac.dim.market.data.models

// Cette data class représente une annonce de type vehicule
// Ici, on regroupe toutes les informations nécessaires pour la décrire
data class AnnonceVehicule(
    val id: String = "", // Identifiant unique de l'annonce (généré par Firestore)
    val titre: String = "",
    val description: String = "",
    val prix: String = "",
    val imageUrl: String = "",
    val userId: String = "",
    val marque: String = "",
    val modele: String = "",
    val annee: String = "",
    val kilometrage: String = "",
    val proprietaires: String = "",
    val boite: String = "",
    val statut: String = "disponible", // Statut de l'annonce (disponible, en attente, vendu)
    val datePublication: Long = System.currentTimeMillis(), // Date de publication (timestamp)
    val imageUrls: List<String> = emptyList() // Liste des URLs d'images associées à l'annonce
)