package uqac.dim.market.data.models

// Cette data classe représente une annonce favorite
// La classe combine les informations des favoris que l'utilisateur a liker avec les détails
// de l'annonce pour l'afficher dans la liste des enregistrements
data class AnnonceFavorite(
    val id: String,
    val userId: String,
    val prix: String,
    val statut: String,
    val datePublication: Long,
    val imageUrls: List<String>, // Collection d'URLs -peut être vide
    val imageUrl: String, // URL principale -fallback si imageUrls est vide
    val titre: String,
    val type: String, // "vehicule" ou "autre"
    val dateAjoutFavori: Long // Timestamp d'ajout aux favoris
) {
    companion object {
        // Constantes pour les types d'annonces
        const val TYPE_VEHICULE = "vehicule"
        const val TYPE_AUTRE = "autre"

        // Je crée une AnnonceFavorite à partir d'une annonce véhicule
        fun creerDepuisVehicule(
            id: String,
            userId: String,
            prix: String,
            statut: String,
            datePublication: Long,
            imageUrls: List<String>,
            imageUrl: String,
            marque: String,
            modele: String,
            annee: String,
            dateAjoutFavori: Long
        ): AnnonceFavorite {
            // Combinaison du titre avec la marque + modèle + année
            val titre = if (marque.isNotEmpty() && modele.isNotEmpty() && annee.isNotEmpty()) {
                "$marque $modele $annee"
            } else if (marque.isNotEmpty()) {
                marque
            } else {
                "Véhicule"
            }
            // Construction de l'objet
            return AnnonceFavorite(
                id = id,
                userId = userId,
                prix = prix,
                statut = statut,
                datePublication = datePublication,
                imageUrls = imageUrls,
                imageUrl = imageUrl,
                titre = titre,
                type = TYPE_VEHICULE,
                dateAjoutFavori = dateAjoutFavori
            )
        }

        // Je crée une AnnonceFavorite à partir d'une autre annonce
        fun creerDepuisAutreAnnonce(
            id: String,
            userId: String,
            prix: String,
            statut: String,
            datePublication: Long,
            imageUrls: List<String>,
            imageUrl: String,
            titre: String,
            dateAjoutFavori: Long
        ): AnnonceFavorite {
            // Construction de l'objet
            return AnnonceFavorite(
                id = id,
                userId = userId,
                prix = prix,
                statut = statut,
                datePublication = datePublication,
                imageUrls = imageUrls,
                imageUrl = imageUrl,
                titre = titre.ifEmpty { "Annonce" },
                type = TYPE_AUTRE,
                dateAjoutFavori = dateAjoutFavori
            )
        }
    }
}