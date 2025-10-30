package uqac.dim.market

import uqac.dim.market.ui.theme.MarketTheme
import uqac.dim.market.utils.ThemeManager
import uqac.dim.market.secrets.FCMUtils

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import uqac.dim.market.activities.HomeActivity
import uqac.dim.market.authentification.ConnexionActivityScreen
import uqac.dim.market.authentification.InscriptionActivityScreen
import uqac.dim.market.conversations.ChatActivity

// Cette classe gère l'authentification, les notifications push et la navigation initiale
// Elle sert de point d'entrée pour diriger les utilisateurs vers les bons écrans
class MainActivity : ComponentActivity() {

    companion object {
        // Tag pour mes logs de débogage
        private const val TAG = "MainActivity"
    }

    // Méthode appelée à la création de l'activité
    // On initialise ici tous les composants nécessaires au fonctionnement de l'app
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "MainActivity -Démarrage de l'application")

        // Initialisation des notifications push des le lancement
        // Je le fais tôt pour ne pas rater les messages
        FCMUtils.initializeFCM(this)

        // Si on arrive via une notification
        gererIntentNotification()

        setContent {
            // Application du thème
            MarketTheme(
                darkTheme = ThemeManager.isDarkMode(this@MainActivity)
            ) {
                EcranPrincipalAvecChargement()
            }
        }
    }

    // Composable principal qui gère l'état de chargement et la navigation
    // J'ai ajouté un écran de chargement pour éviter le flash de l'écran de connexion
    // car avant j'avais un temps de latence
    @Composable
    private fun EcranPrincipalAvecChargement() {
        val contexte = LocalContext.current
        val auth = remember { FirebaseAuth.getInstance() }

        // États pour gérer la navigation entre écrans
        var afficherEcranInscription by remember { mutableStateOf(false) }
        var utilisateurConnecte by remember { mutableStateOf<Boolean?>(null) }
        var verificationTerminee by remember { mutableStateOf(false) }

        // J'utilise LaunchedEffect pour écouter les changements Firebase
        LaunchedEffect(Unit) {
            // Vérification initiale immédiate de l'état d'authentification
            val utilisateurActuel = auth.currentUser
            utilisateurConnecte = utilisateurActuel != null
            verificationTerminee = true

            Log.d(TAG, "Vérification initiale: utilisateur connecté = ${utilisateurConnecte}")

            // Listener pour les changements d'état d'authentification
            auth.addAuthStateListener { firebaseAuth ->
                val newUserState = firebaseAuth.currentUser != null
                Log.d(TAG, "État utilisateur changé: $newUserState")
                utilisateurConnecte = newUserState
                verificationTerminee = true
            }
        }

        // Redirection automatique vers l'accueil si l'utilisateur est connecté
        LaunchedEffect(utilisateurConnecte, verificationTerminee) {
            if (verificationTerminee && utilisateurConnecte == true) {
                Log.d(TAG, "Utilisateur connecté, vérification de redirection...")

                // Une petite exception: pas de redirection si on vient d'une notification
                // Dans ce cas, on veut ouvrir directement le chat
                if (!ouvrirChatDirectement()) {
                    Log.d(TAG, "Redirection vers HomeActivity")
                    val intent = Intent(contexte, HomeActivity::class.java)
                    // Flags Intent pour nettoyer la pile d'activités:
                    // -FLAG_ACTIVITY_NEW_TASK: Lance l'activite dans une nouvelle tache
                    // -FLAG_ACTIVITY_CLEAR_TASK: Vider toute la pile existante
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    contexte.startActivity(intent)
                    finish() // Ferme MainActivity pour libérer la mémoire
                }
            }
        }

        // Affichage conditionnel selon l'état de vérification
        when {
            // Phase de vérification, on affiche un écran de chargement
            !verificationTerminee -> {
                EcranChargementInitial()
            }

            // Utilisateur non connecté, on affiche les écrans d'authentification
            utilisateurConnecte == false -> {
                if (afficherEcranInscription) {
                    InscriptionActivityScreen(
                        inscriptionReussie = {
                            // Retour à l'écran de connexion après inscription réussie
                            afficherEcranInscription = false
                            utilisateurConnecte = true
                        },
                        retourConnexion = {
                            // Simple retour à l'écran de connexion
                            afficherEcranInscription = false
                        }
                    )
                } else {
                    ConnexionActivityScreen(
                        connexionReussie = {
                            // L'utilisateur s'est connecté avec succès
                            utilisateurConnecte = true
                        },
                        inscriptionClick = {
                            // Basculer vers l'écran d'inscription
                            afficherEcranInscription = true
                        }
                    )
                }
            }
        }
    }

    // Écran de chargement initial
    // Affiché pendant la vérification de l'état d'authentification
    @Composable
    private fun EcranChargementInitial() {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Indicateur de chargement
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )

                // Nom de l'application
                Text(
                    text = "Maakiti",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )

                // Message de chargement
                Text(
                    text = "Chargement en cours...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
            }
        }
    }

    // Gestion des notifications push reçues
    // Cette fonction analyse l'intent pour déterminer si on doit ouvrir le chat
    // En gros, le point d'entrée qui décide si on traite une notification ou non
    private fun gererIntentNotification() {
        val currentUser = FirebaseAuth.getInstance().currentUser

        // Double vérification: notification présente et utilisateur connecté
        if (ouvrirChatDirectement() && currentUser != null) {
            Log.d(TAG, "Notification détectee")
            ouvrirChatDepuisNotification()
            return
        }

        Log.d(TAG, "Pas de notification ou utilisateur non connecte")
    }

    // J'ai séparé cette logique pour la rendre plus lisible et testable car j'avais des erreurs au debut
    // retourne true si on doit ouvrir le chat, false sinon
    // En gros, vérifie si l'intent contient aussi les bons flags
    private fun ouvrirChatDirectement(): Boolean {
        val ouvrirChat = intent.getBooleanExtra("openChat", false)
        val depuisNotification = intent.getBooleanExtra("depuisNotification", false)

        Log.d(TAG, "Vérification chat direct: openChat=$ouvrirChat, depuisNotification=$depuisNotification")

        return ouvrirChat && depuisNotification
    }

    // Ouverture du chat à partir d'une notification
    // Cette fonction extrait toutes les données nécessaires de l'intent
    // et lance ChatActivity avec les bons paramètres
    // En gros, ouvre le chat depuis une notification
    private fun ouvrirChatDepuisNotification() {
        // Extraction des paramètres de la notification
        val conversationId = intent.getStringExtra("conversationId")
        val annonceId = intent.getStringExtra("annonceId")
        val idReceveur = intent.getStringExtra("idReceveur")
        val annonceTitle = intent.getStringExtra("annonceTitle")

        Log.d(TAG, "Données chat extraites: conversationId=$conversationId, annonceId=$annonceId, idReceveur=$idReceveur")

        // Vérification que nous avons toutes les données requises
        if (conversationId != null && annonceId != null && idReceveur != null) {
            val chatIntent = Intent(this, ChatActivity::class.java).apply {
                putExtra("conversationId", conversationId)
                putExtra("annonceId", annonceId)
                putExtra("idReceveur", idReceveur)
                putExtra("annonceTitle", annonceTitle ?: "")
                // Flags importants pour un comportement correct de la navigation
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            Log.d(TAG, "Lancement ChatActivity depuis notification")
            startActivity(chatIntent)
            finish() // On ferme MainActivity après ouverture du chat
        } else {
            // Fallback si les données sont corrompues ou manquantes
            // Logger l'erreur, redirection
            // vers l'accueil si ça arrive et ne pas crasher l'app
            Log.e(TAG, "Données de chat manquantes -redirection vers l'accueil")
            val homeIntent = Intent(this, HomeActivity::class.java)
            homeIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(homeIntent)
            finish()
        }
    }

    // Gestion des nouveaux intents quand l'app est déjà ouverte
    // Cela arrive quand l'utilisateur clique sur une notification alors que l'app tourne
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d(TAG, "onNewIntent appelé -nouvelle notification reçue")

        setIntent(intent)

        // Traitement immédiat des nouvelles notifications
        gererIntentNotification()
    }

    // Méthodes du cycle de vie Android
    // Je les ai gardé pour le débogage et le suivi du comportement de l'app
    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume -Activité reprend")
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart -Activité démarre")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy -Activité détruite")
    }
}