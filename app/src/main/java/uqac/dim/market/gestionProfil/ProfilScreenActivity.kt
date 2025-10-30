package uqac.dim.market.gestionProfil

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import uqac.dim.market.theme.Footer
import uqac.dim.market.MainActivity
import uqac.dim.market.gestionAnnonces.MesAnnoncesActivity
import uqac.dim.market.gestionAnnonces.MesEnregistrementsActivity
import uqac.dim.market.conversations.MesMessagesActivity
import uqac.dim.market.authentification.ModifierMDPActivityScreen
import uqac.dim.market.R
import uqac.dim.market.ui.theme.MarketTheme
import uqac.dim.market.utils.UserProfileUtils
import uqac.dim.market.utils.ThemeManager
import androidx.compose.ui.tooling.preview.Preview as preview
import kotlinx.coroutines.delay

// Activité principale pour l'écran de profil
class ProfilActivity : ComponentActivity() {

    // Variable pour notifier le composable du changement d'avatar
    private var avatarAChange by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ProfilActivityContent(
                // Passe le déclencheur de mise à jour de l'avatar au composable
                declencheurMiseAJourAvatar = avatarAChange
            )
        }
    }

    override fun onResume() {
        super.onResume()
        // À chaque retour sur cet écran, on inverse la valeur pour déclencher la mise à jour
        avatarAChange = !avatarAChange
    }
}

// Composable principal qui gère le thème dynamiquement
@Composable
fun ProfilActivityContent(
    declencheurMiseAJourAvatar: Boolean = false // Déclencheur pour la mise à jour de l'avatar
) {
    val context = LocalContext.current

    // État local pour le thème qui se met à jour
    var isDarkMode by remember { mutableStateOf(ThemeManager.isDarkMode(context)) }

    // Application du thème avec l'état local
    MarketTheme(
        darkTheme = isDarkMode
    ) {
        ProfilScreenActivity(
            isDarkMode = isDarkMode,
            declencheurMiseAJourAvatar = declencheurMiseAJourAvatar,
            // Callback pour gérer les changements de thème
            onThemeChange = { newMode ->
                // Mise à jour du thème
                isDarkMode = newMode
                ThemeManager.setDarkMode(context, newMode) // Sauvegarde dans les préférences
            }
        )
    }
}

// Composable principal pour l'écran de profil utilisateur
// Affiche les informations personnelles récuperées depuis Firebase et les options de navigation
@Composable
fun ProfilScreenActivity(
    isDarkMode: Boolean = ThemeManager.isDarkMode(LocalContext.current),
    declencheurMiseAJourAvatar: Boolean = false,
    onThemeChange: (Boolean) -> Unit = {} // Callback pour les changements de thème
) {
    val context = LocalContext.current

    // Variables d'état pour stocker les informations utilisateur
    var nomUtilisateur by remember { mutableStateOf("Nom de l'utilisateur") }
    var courrielUtilisateur by remember { mutableStateOf("email") }
    var photoUrl by remember { mutableStateOf<String?>(null) }
    var idAvatarPersonnalise by remember { mutableStateOf<Int?>(null) }
    var chargementTheme by remember { mutableStateOf(false) }

    // Utilisateur actuellement connecté
    val user = FirebaseAuth.getInstance().currentUser

    // Quand je choisisais plusieurs avatars je voyais toujours toute la pile avant qu'elle se vide
    // donc j'ai fais une fonction pour naviguer vers la modification de profil et je nettoies
    fun naviguerVersModificationProfil() {
        val intent = Intent(context, ModifierMonProfilActivity::class.java)
        // Ces flags évitent l'accumulation d'activités dans la stack
        // FLAG_ACTIVITY_CLEAR_TOP Supprime toutes les activités au dessus de ProfilActivity
        // FLAG_ACTIVITY_SINGLE_TOP Évite la création d'une nouvelle instance si elle existe déjà
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        context.startActivity(intent)
    }

    // Chargement des données utilisateur depuis Firebase
    LaunchedEffect(Unit) {
        user?.let {
            // Récupération des données depuis Firebase Auth
            courrielUtilisateur = it.email ?: "email"
            photoUrl = it.photoUrl?.toString()

            val uid = it.uid
            val snapshot = FirebaseFirestore.getInstance().collection("utilisateurs").document(uid).get().await()

            // Récupération du nom d'utilisateur avec fallback sur le nom d'affichage ou l'email
            nomUtilisateur = snapshot.getString("nom")?.takeIf { it.isNotBlank() }
                ?: it.displayName?.substringBefore("@")?.takeIf { it.isNotBlank() }
                        ?: it.email?.substringBefore("@")
                        ?: "Nom inconnu"

            // On récupère l'avatar personnalisé stocké dans ma base de données
            idAvatarPersonnalise = UserProfileUtils.recupererAvatarUtilisateurActuel()
        }
    }

    // Mise à jour de l'avatar quand le déclencheur change
    LaunchedEffect(declencheurMiseAJourAvatar) {
        val nouveauAvatarId = UserProfileUtils.recupererAvatarUtilisateurActuel()
        idAvatarPersonnalise = nouveauAvatarId
    }

    // Gestion du délai du spinner
    LaunchedEffect(chargementTheme) {
        if (chargementTheme) {
            delay(500) // Délai court pour éviter les clics multiples
            chargementTheme = false
        }
    }

    // Structure principale
    Scaffold(
        bottomBar = {
            Footer() // mon footer en bas
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // Section d'affichage de la photo de profil
            // On priorise l'avatar personnalisé si il existe, sinon j'utilise celui de Google
            if (idAvatarPersonnalise != null) {
                // Affichage de l'avatar personnalisé depuis nos ressources locales
                Image(
                    painter = painterResource(id = idAvatarPersonnalise!!),
                    contentDescription = "Avatar",
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .clickable {
                            // Navigation vers l'écran de modification du profil et nettoyage de pile
                            naviguerVersModificationProfil()
                        }
                )
            } else {
                // Avatar par défaut ou celui fourni par Google OAuth
                Image(
                    painter = if (photoUrl != null) {
                        rememberAsyncImagePainter(model = photoUrl) // Chargement asynchrone de l'image depuis l'URL Google
                    } else {
                        painterResource(id = R.drawable.ic_profile_default) // Image par défaut pour les nouveaux comptes email
                    },
                    contentDescription = "Photo de profil",
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .clickable {
                            // Navigation vers l'écran de modification du profil et nettoyage de pile
                            naviguerVersModificationProfil()
                        }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Affichage des informations de l'utilisateur (nom et email)
            Text(
                text = nomUtilisateur,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            // Email de l'utilisateur connecté
            Text(
                text = courrielUtilisateur,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Menu des options du profil chaque élément utilise notre composable
            ProfilItem("Modifier mon profil", Icons.Default.Edit) {
                // Redirection vers l'écran de modification du profil et nettoyage de pile
                naviguerVersModificationProfil()
            }

            // Modification du mot de passe
            ProfilItem("Modifier mot de passe", Icons.Default.Lock) {
                context.startActivity(Intent(context, ModifierMDPActivityScreen::class.java))
            }

            // Mes annonces
            ProfilItem("Mes annonces", Icons.Default.List) {
                context.startActivity(Intent(context, MesAnnoncesActivity::class.java))
            }

            // Mes messages
            ProfilItem("Mes messages", Icons.Default.Message) {
                context.startActivity(Intent(context, MesMessagesActivity::class.java))
            }

            // Mes enregistrements
            ProfilItem("Mes enregistrements", Icons.Default.Bookmark) {
                val intent = Intent(context, MesEnregistrementsActivity::class.java)
                context.startActivity(intent)
            }

            // Basculement entre mode sombre et mode clair
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        // Prévention des clics multiples
                        if (!chargementTheme) {
                            chargementTheme = true
                            // Déclenche le changement de thème via le callback
                            onThemeChange(!isDarkMode)
                        }
                    }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icône qui change selon le mode actuel
                Icon(
                    imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                    contentDescription = if (isDarkMode) "Mode clair" else "Mode sombre",
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.width(16.dp))

                // Texte qui s'adapte au mode actuel
                Text(
                    text = if (isDarkMode) "Mode clair" else "Mode sombre",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            // Option de contact par email
            ProfilItem("Nous Contacter", Icons.Default.ContactMail) {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:") // mailto pour les applications de messagerie
                    putExtra(Intent.EXTRA_EMAIL, arrayOf("marketmaakiti@gmail.com"))
                }
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "Aucune application de messagerie trouvée sur votre téléphone", Toast.LENGTH_SHORT).show()
                }
            }

            // Déconnexion je redirige vers l'écran de connexion
            ProfilItem("Déconnexion", Icons.Default.ExitToApp) {
                FirebaseAuth.getInstance().signOut()
                // Retour à l'écran de connexion et nettoyage de la pile d'activités
                val intent = Intent(context, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                context.startActivity(intent)
            }
        }
    }
}

// Composable pour créer un élément de menu du profil
@Composable
fun ProfilItem(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icône de l'élément
        Icon(
            icon,
            contentDescription = label,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.width(16.dp))

        // Texte de l'élément
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

// Prévisualisation
@preview(showBackground = true)
@Composable
fun ProfilScreenPreview() {
    MarketTheme {
        ProfilScreenActivity()
    }
}