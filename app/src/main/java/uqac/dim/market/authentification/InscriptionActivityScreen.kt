package uqac.dim.market.authentification

import android.content.Context
import android.widget.Toast
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
import com.google.firebase.auth.FirebaseAuth
import uqac.dim.market.theme.Footer
import uqac.dim.market.ui.theme.MarketTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack

// L'écran d'inscription
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InscriptionActivityScreen(
    inscriptionReussie: () -> Unit,
    retourConnexion: () -> Unit
) {
    val contexte = LocalContext.current

    // Instance Firebase Auth
    val auth = remember { FirebaseAuth.getInstance() }

    // Variables d'état
    var email by remember { mutableStateOf("") }
    var motDePasse by remember { mutableStateOf("") }
    var chargementEnCours by remember { mutableStateOf(false) }

    // Interface utilisateur principale
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("") },
                navigationIcon = {
                    IconButton(onClick = retourConnexion) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour à la connexion"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(paddingValues)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
            Spacer(modifier = Modifier.height(40.dp))

            // Titre
            Text(
                text = "Inscription",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Champ email
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Champ mot de passe
            OutlinedTextField(
                value = motDePasse,
                onValueChange = { motDePasse = it },
                label = { Text("Mot de passe") },
                visualTransformation = PasswordVisualTransformation(), // Masque le texte
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Bouton principal pour créer le compte
            Button(
                onClick = {
                    // Validation des champs avant de procéder à l'inscription
                    if (email.isNotEmpty() && motDePasse.isNotEmpty()) {
                        chargementEnCours = true
                        creerCompte(email, motDePasse, auth, inscriptionReussie, contexte)
                    } else {
                        // Message d'erreur si les champs sont vides
                        Toast.makeText(
                            contexte,
                            "Veuillez remplir tous les champs",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Créer un compte")
            }

            if (chargementEnCours) {
                CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
            }

            // Espacement pour le footer
            Spacer(modifier = Modifier.weight(1f))

            // footer
            Footer()
        }
    }
}

// Gere l'inscription avec Email/Password
private fun creerCompte(
    email: String,
    motDePasse: String,
    auth: FirebaseAuth,
    inscriptionReussie: () -> Unit,
    contexte: Context
) {
    // Tentative de création du compte avec Firebase
    auth.createUserWithEmailAndPassword(email, motDePasse)
        .addOnCompleteListener { tache ->
            if (tache.isSuccessful) {
                // Succès: on informe l'utilisateur et on déclenche le callback
                Toast.makeText(contexte, "Compte créé avec succès", Toast.LENGTH_SHORT).show()
                inscriptionReussie()
            } else {
                // Échec: on affiche le message d'erreur
                Toast.makeText(contexte, "Échec : ${tache.exception?.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
}

// preview de mon ecran
@Preview(showBackground = true)
@Composable
fun PreviewInscriptionActivityScreen() {
    MarketTheme {
        InscriptionActivityScreen(
            inscriptionReussie = {},
            retourConnexion = {}
        )
    }
}