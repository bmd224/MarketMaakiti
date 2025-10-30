package uqac.dim.market

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import uqac.dim.market.secrets.FCMUtils

// Cette classe initialise Firebase et FCM dès le démarrage de l'application
class MarketApplication : Application() {
    companion object {
        private const val TAG = "MarketApplication"
    }
    override fun onCreate() {
        super.onCreate()

        // J'initialise Firebase pour permettre l'utilisation des services cloud de google
        FirebaseApp.initializeApp(this)

        // On initialise la gestion des notifications (FCM)
        FCMUtils.initializeFCM(this)

        // Petit log pour vérifier que tout est bien lancé des le debut dans mes logcats
        Log.d(TAG, "Application Maakiti initialisée")
    }
}