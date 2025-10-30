package uqac.dim.market.data.models

// Cette data class représente une annonce d'un type autre
// On y met toutes les infos nécessaires pour décrire ce type d'annonce
data class AutreAnnonce(
    val id: String = "", // Id unique de l'annonce
    val titre: String = "",
    val description: String = "",
    val prix: String = "",
    val imageUrls: List<String> = emptyList(), // Liste des URLs d'images associées
    val userId: String = "", // Utilisateur ayant post l'annonce
    val statut: String = "disponible", // Statut de l'annonce (disponible, en attente, vendu)
    val datePublication: Long = System.currentTimeMillis() // Date de publication (timestamp)
)