package uqac.dim.market.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import uqac.dim.market.conversations.ChatActivity

// Le service de gestion des notifications Firebase Cloud Messaging FCM
// Ce service s'occupe de recevoir et d'afficher les notifications push
// pour les nouveaux messages de chat dans mon application
class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCMService" // Tag pour les logs
        private const val ID_Canal = "chat_notifications" // ID du canal de notification
    }

    // La méthode est appelée lors de la création et le démarrage du service
    // On crée le canal de notification ce qui est obligatoire
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    // Appelée automatiquement quand Firebase génère un nouveau token FCM
    // Firebase l'utilise pour savoir où envoyer les notifications
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Nouveau token FCM reçu: $token")

        // Sauvegarde du token dans Firestore pour que le backend puisse envoyer
        // des notifications au bon destinataire
        sauvegarderTokenDansFirestore(token)
    }

    // Méthode principale qui traite les messages recus de Firebase
    // Elle extrait les données du message et affiche une notification
    // Le RemoteMessage peut contenir deux types de données
    // Notification payload: titre et corps gérés automatiquement par Firebase (si l'app est en arrière plan)
    // Data payload: données personnalisées (toujours traitées, même en arrière-plan)
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        Log.d(TAG, "Message reçu de: ${remoteMessage.from}")

        // 2 types de données possibles du message
        val notification = remoteMessage.notification // Contient title et body si envoyés comme notification payload
        val data = remoteMessage.data // Contient les paires clé valeur personnalisées envoyées comme data payload

        // Extraction du titre et du corps du message avec des valeurs par défaut
        // L'operateur (?:) retourne la première valeur non null
        val title = notification?.title ?: data["title"] ?: "Nouveau message"
        val body = notification?.body ?: data["body"] ?: "Vous avez un nouveau message"

        // Récupération des données nécessaires pour la navigation vers le bon chat
        // Ces données sont envoyées depuis le backend avec le message FCM
        val conversationId = data["conversationId"] ?: ""
        val annonceId = data["annonceId"] ?: ""
        val idReceveur = data["idReceveur"] ?: ""
        val annonceTitle = data["annonceTitle"] ?: ""
        val idExpediteur = data["idExpediteur"] ?: ""

        // Vérifier si l'utilisateur courant n'est pas l'expéditeur
        // Cela évite que l'expéditeur recoive une notification de son propre message
        // peut arriver si le token FCM est enregistré sur plusieurs appareils du même utilisateur
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser?.uid == idExpediteur) {
            Log.d(TAG, "L'utilisateur courant est l'expéditeur, notification ignorée")
            return // Ne pas afficher la notification
        }

        Log.d(TAG, "Données notification -ConversationId: $conversationId, AnnonceId: $annonceId")

        // Affichage de la notification à l'utilisateur
        afficherNotification(title, body, conversationId, annonceId, idReceveur, annonceTitle, idExpediteur)
    }

    // Le token est stocké dans la collection "users" sous le document de l'utilisateur actuel
    // Cela permet au backend de récupérer le token
    // pour envoyer des notifications push à cet utilisateur spécifique
    private fun sauvegarderTokenDansFirestore(token: String) {
        // Vérification de sécurité: s'assurer qu'il est connecté
        val currentUser = FirebaseAuth.getInstance().currentUser
        currentUser?.let { user ->
            val db = FirebaseFirestore.getInstance()

            // Sauvegarder dans "users"
            db.collection("users").document(user.uid)
                .update("fcmToken", token)
                .addOnSuccessListener {
                    Log.d(TAG, "Token sauvegardé dans 'users'")
                }
                .addOnFailureListener { e ->
                    // Si le document n'existe pas, le créer
                    db.collection("users").document(user.uid)
                        .set(mapOf("fcmToken" to token))
                        .addOnSuccessListener { Log.d(TAG, "Document 'users' créé") }
                }

            // Sauvegarder aussi dans "utilisateurs"
            db.collection("utilisateurs").document(user.uid)
                .update("fcmToken", token)
                .addOnFailureListener { e ->
                    Log.e(TAG, "Erreur 'utilisateurs'", e)
                }
        }
    }

    // Je crée et affiche une notification locale avec toutes les métadonnées du message
    private fun afficherNotification(
        title: String,
        body: String,
        conversationId: String,
        annonceId: String,
        idReceveur: String,
        annonceTitle: String,
        idExpediteur: String
    ) {
        // Création de l'intent qui va ouvrir directement l'activité de chat
        val intent = Intent(this, ChatActivity::class.java).apply {
            // Transfert de toutes les données nécessaires à ChatActivity via des extras
            putExtra("conversationId", conversationId)
            putExtra("annonceId", annonceId)
            putExtra("idReceveur", idExpediteur) // on inverse expéditeur/receveur pour la réponse
            putExtra("annonceTitle", annonceTitle)

            // Configuration des flags de l'Intent pour contrôler le comportement de la pile d'activités
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or // Créer une nouvelle tâche si nécessaire
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or // Nettoyer la pile et réutiliser l'activité existante si présente
                    Intent.FLAG_ACTIVITY_SINGLE_TOP // Ne pas créer de doublon si l'activité est déjà en haut
        }

        // Configuration des flags du PendingIntent selon la version d'Android
        // FLAG_IMMUTABLE est requis
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Mettre à jour le PendingIntent existant avec les nouvelles données ou
            // Empêcher les modifications du contenu (sécurité)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        // Création du PendingIntent: un wrapper qui permet à Android
        // d'exécuter notre Intent même si notre app n'est plus en cours d'exécution
        val pendingIntent = PendingIntent.getActivity(
            this,
            conversationId.hashCode(),
            intent,
            pendingIntentFlags
        )

        // Construction de la notification avec NotificationCompat pour compatibilité multiversion
        val notificationBuilder = NotificationCompat.Builder(this, ID_Canal)

            .setSmallIcon(android.R.drawable.ic_popup_reminder) // Icone de chat système
            .setContentTitle(title) // Titre
            .setContentText(body) // Corps du message
            // Fermer automatiquement la notification quand l'utilisateur clique dessus
            .setAutoCancel(true)
            // Priorité HIGH : notification visible même en mode ne pas déranger (selon paramètres biensur)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            // Attacher le PendingIntent pour l'action au clic
            .setContentIntent(pendingIntent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))


            // Configuration pour garantir la visibilité même hors de l'app
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Visible meme sur écran de verrouillage

            .setWhen(System.currentTimeMillis()) // Timestamp actuel

            .setColor(resources.getColor(android.R.color.holo_blue_bright, null)) // Couleur d'accentuation

        // Affichage de la notification
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(conversationId.hashCode(), notificationBuilder.build())
    }

    // Canal de notification
    // Crée le canal de notification requis pour Android 8.0 API 26 et supérieur
    // Un canal doit être créé avant d'afficher des notifications, sinon elles seront bloquées
    private fun createNotificationChannel() {
        // Vérification de version: les canaux n'existent pas avant Android 8.0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                ID_Canal, // Identifiant unique du canal
                "Notifications de Chat",
                NotificationManager.IMPORTANCE_HIGH // Garantit l'affichage et le son(son + heads up + vibration)
            ).apply {
                // Description
                description = "Notifications pour les nouveaux messages de chat"
                // Activer le LED de notification (si l'appareil en a un)
                enableLights(true)
                // Activer la vibration lors de la réception
                enableVibration(true)
                setShowBadge(true)
                // Visibilité sur écran verrouillé: PUBLIC = contenu complet visible
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                // Le son par défaut du système sera utilisé
            }

            // Enregistrement du canal auprès du système Android
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)

            Log.d(TAG, "Canal de notification créé pour affichage: $ID_Canal")
        }
    }
}