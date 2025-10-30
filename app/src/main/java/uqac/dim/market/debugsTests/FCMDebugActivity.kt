package uqac.dim.market.debugsTests

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import uqac.dim.market.ui.theme.MarketTheme
import uqac.dim.market.utils.ThemeManager

// j'utilise ce fichier pour deboguer car au debut les notifications ne s'affichaient pas soit sur l'emulateur soit
// sur le telephone physique(des fois les 2) j'ai donc fais ce fichier pour diagnostiquer les problèmes FCM
// j'ai observé mes logcats pour pouvoir trouver et régler le probleme
class FCMDebugActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MarketTheme(
                darkTheme = ThemeManager.isDarkMode(this@FCMDebugActivity)
            ) {
                FCMDebugScreen()
            }
        }
    }
}

@Composable
fun FCMDebugScreen() {
    var debugInfo by remember { mutableStateOf("Chargement des informations...") }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        collecterInfosDebug(context) { info ->
            debugInfo = info
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Debug FCM",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = debugInfo,
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                collecterInfosDebug(context) { info ->
                    debugInfo = info
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Actualiser les infos")
        }
    }
}

private fun collecterInfosDebug(context: Context, onResult: (String) -> Unit) {
    val info = StringBuilder()

    // Informations sur l'appareil
    info.appendLine("INFORMATIONS APPAREIL")
    info.appendLine("Modèle: ${Build.MODEL}")
    info.appendLine("Fabricant: ${Build.MANUFACTURER}")
    info.appendLine("Version Android: ${Build.VERSION.RELEASE}")
    info.appendLine("API Level: ${Build.VERSION.SDK_INT}")
    info.appendLine()

    // Vérifier Google Play Services
    info.appendLine("GOOGLE PLAY SERVICES")
    try {
        val gms = GoogleApiAvailability.getInstance()
        val result = gms.isGooglePlayServicesAvailable(context)
        if (result == ConnectionResult.SUCCESS) {
            info.appendLine("Disponible")
        } else {
            info.appendLine("Non disponible (Code: $result)")
        }
    } catch (e: Exception) {
        info.appendLine("Erreur: ${e.message}")
    }
    info.appendLine()

    // Vérifier les notifications
    info.appendLine("PARAMÈTRES DE NOTIFICATIONS")
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        val areNotificationsEnabled = notificationManager.areNotificationsEnabled()
        info.appendLine("Notifications activées: $areNotificationsEnabled")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = notificationManager.getNotificationChannel("chat_notifications")
            if (channel != null) {
                info.appendLine("Canal 'chat_notifications': ")
                info.appendLine("Importance: ${channel.importance}")
                info.appendLine("Activé: ${channel.importance != NotificationManager.IMPORTANCE_NONE}")
            } else {
                info.appendLine("Canal 'chat_notifications': ")
            }
        }
    }
    info.appendLine()

    // Utilisateur Firebase
    info.appendLine("UTILISATEUR FIREBASE")
    val currentUser = FirebaseAuth.getInstance().currentUser
    if (currentUser != null) {
        info.appendLine("Connecté")
        info.appendLine("UID: ${currentUser.uid}")
        info.appendLine("Email: ${currentUser.email}")
        info.appendLine("Nom: ${currentUser.displayName}")
    } else {
        info.appendLine("Non connecté")
    }
    info.appendLine()

    // Token FCM
    info.appendLine("TOKEN FCM")
    FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
        if (task.isSuccessful) {
            val token = task.result
            info.appendLine("Token récupéré")
            info.appendLine("Token: ${token.take(50)}...")
            onResult(info.toString())
        } else {
            info.appendLine("Erreur: ${task.exception?.message}")
            onResult(info.toString())
        }
    }
}