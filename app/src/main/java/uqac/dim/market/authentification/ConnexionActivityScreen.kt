package uqac.dim.market.authentification

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.*
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import uqac.dim.market.theme.Footer
import uqac.dim.market.R
import uqac.dim.market.activities.HomeActivity
import uqac.dim.market.ui.theme.MarketTheme

// L'écran de connexion
// J'ai implémenté deux méthodes: email/mot de passe classique et les services de Google avec Google Sign-In
@Composable
fun ConnexionActivityScreen(
    connexionReussie: () -> Unit,
    inscriptionClick: () -> Unit
) {
    val contexte = LocalContext.current
    val auth = remember { Firebase.auth }

    // États pour gérer les champs de saisie et le loading
    var email by remember { mutableStateOf("") }
    var motDePasse by remember { mutableStateOf("") }
    var chargementEnCours by remember { mutableStateOf(false) }

    // Configuration pour Google Sign-In j'ai utilisé le web client ID de mon projet Firebase
    val optionsGoogle = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken(contexte.getString(R.string.default_web_client_id))
        .requestEmail()
        .build()

    // Client Google Sign-In
    val clientGoogle = remember { GoogleSignIn.getClient(contexte, optionsGoogle) }

    // Lanceur pour gérer le résultat de Google Sign-In
    // J'ai utilisé l'API ActivityResultContracts
    val lanceurGoogle = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val tache = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val compte = tache.getResult(ApiException::class.java)
                if (compte != null) {
                    authentificationAvecGoogle(compte.idToken!!, auth, connexionReussie, contexte)
                }
            } catch (e: ApiException) {
                // Si Google Sign-In échoue, on affiche un message d'erreur
                Toast.makeText(contexte, "Échec Google Sign-In: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                Log.w("LoginActivity", "Google sign en panne pour l'instant", e)
            }
        }
    }

    // Ecran de connexion
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(80.dp))

        // Titre principal
        Text(
            text = "Connexion",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Champ email
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Entrez votre Email...") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Champ mot de passe avec masquage automatique
        OutlinedTextField(
            value = motDePasse,
            onValueChange = { motDePasse = it },
            label = { Text("Votre mot de passe...") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Bouton de connexion
        Button(
            onClick = {
                // Vérification des champs avant d'envoyer la requête
                if (email.isNotEmpty() && motDePasse.isNotEmpty()) {
                    chargementEnCours = true
                    connexionAvecEmailEtMotDePasse(email, motDePasse, auth, contexte)
                } else {
                    Toast.makeText(contexte, "Veuillez remplir tous les champs", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Se Connecter")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Bouton Google Sign-In
        // Déconnexion pour éviter les problèmes de compte en cache
        Button(
            onClick = {
                clientGoogle.signOut().addOnCompleteListener {
                    lanceurGoogle.launch(clientGoogle.signInIntent)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Se connecter avec Google")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Lien vers l'inscription
        TextButton(onClick = inscriptionClick) {
            Text("Vous n'avez encore pas de compte ? Inscrivez vous")
        }

        // Indicateur de chargement qui s'affiche pendant la connexion
        if (chargementEnCours) {
            CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
        }

        // Espacement pour le footer
        Spacer(modifier = Modifier.weight(1f))

        // Footer
        Footer()
    }
}


// Fonction qui gère la connexion avec email et mot de passe
// J'utilise Firebase Auth qui gère tout le côté sécurité
fun connexionAvecEmailEtMotDePasse(
    email: String,
    motDePasse: String,
    auth: FirebaseAuth,
    contexte: Context
) {
    auth.signInWithEmailAndPassword(email, motDePasse)
        .addOnCompleteListener { tache ->
            if (tache.isSuccessful) {
                // Connexion réussie, on redirige vers l'accueil
                Toast.makeText(contexte, "Vous etes maintenant connecté !", Toast.LENGTH_SHORT).show()
                val intent = Intent(contexte, HomeActivity::class.java)
                // Je nettoie toute la pile d'activités pour éviter le retour en arrière
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                contexte.startActivity(intent)
            } else {
                // Erreur de connexion, mot de passe incorrect, compte inexistant, etc..
                Toast.makeText(contexte, "Échec de la connexion: ${tache.exception?.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
}


// Fonction pour l'authentification avec Google
// Ici, je récupère le token Google contre un token Firebase
fun authentificationAvecGoogle(
    idToken: String,
    auth: FirebaseAuth,
    connexionReussie: () -> Unit,
    contexte: Context
) {
    // Création des credentials Firebase à partir du token Google
    val credential = GoogleAuthProvider.getCredential(idToken, null)
    auth.signInWithCredential(credential)
        .addOnCompleteListener { tache ->
            if (tache.isSuccessful) {
                // Connexion Google réussie
                Toast.makeText(contexte, "Vous vous etes connecté avec Google", Toast.LENGTH_SHORT).show()
                val intent = Intent(contexte, HomeActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                contexte.startActivity(intent)
                connexionReussie()
            } else {
                // Erreur lors de l'authentification avec Google
                Toast.makeText(contexte, "Échec de connexion avec Google: ${tache.exception?.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
}

// preview
@Preview(showBackground = true)
@Composable
fun PreviewConnexionActivityScreen() {
    MarketTheme {
        ConnexionActivityScreen(
            connexionReussie = {},
            inscriptionClick = {}
        )
    }
}
