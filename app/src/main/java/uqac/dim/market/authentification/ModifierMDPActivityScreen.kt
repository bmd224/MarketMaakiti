package uqac.dim.market.authentification

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import uqac.dim.market.theme.Footer
import uqac.dim.market.activities.HomeActivity
import uqac.dim.market.ui.theme.MarketTheme
import uqac.dim.market.utils.ThemeManager
import android.app.Activity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import uqac.dim.market.gestionProfil.ProfilActivity

// Activité principale pour la modification du mot de passe utilisateur
// Cette classe gère l'interface permettant aux utilisateurs de changer leur mot de passe
// en vérifiant d'abord leur ancien mot de passe pour des raisons de sécurité
class ModifierMDPActivityScreen : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MarketTheme(
                darkTheme = ThemeManager.isDarkMode(this@ModifierMDPActivityScreen)
            ) {
                ModifierMDPScreen()
            }
        }
    }
}

// Interface principale pour la modification du mot de passe
// Cette fonction composable gère tous les éléments visuels et la logique
// de validation pour le changement de mot de passe
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModifierMDPScreen() {
    val contexte = LocalContext.current

    // États pour gérer les valeurs des champs de saisie
    var email by remember { mutableStateOf(TextFieldValue("")) }
    var ancienMotDePasse by remember { mutableStateOf(TextFieldValue("")) }
    var nouveauMotDePasse by remember { mutableStateOf(TextFieldValue("")) }
    var confirmerMotDePasse by remember { mutableStateOf(TextFieldValue("")) }

    // Récupération de l'instance Firebase Auth et de l'utilisateur connecté
    val auth = FirebaseAuth.getInstance()
    val utilisateurActuel = auth.currentUser

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Modifier mon mot de passe",
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        (contexte as? Activity)?.finish()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Champ email
            // On demande l'email pour des raisons de sécurité lors de la réauthentification
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Champ pour l'ancien mot de passe
            // nécessaire pour vérifier l'identité avant de permettre un changement
            OutlinedTextField(
                value = ancienMotDePasse,
                onValueChange = { ancienMotDePasse = it },
                label = { Text("Ancien Mot de Passe") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Champ pour le nouveau mot de passe
            OutlinedTextField(
                value = nouveauMotDePasse,
                onValueChange = { nouveauMotDePasse = it },
                label = { Text("Nouveau Mot de Passe") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Champ de confirmation du nouveau mot de passe
            // Permet d'éviter les erreurs de frappe lors de la saisie du nouveau mot de passe
            OutlinedTextField(
                value = confirmerMotDePasse,
                onValueChange = { confirmerMotDePasse = it },
                label = { Text("Confirmer votre nouveau mot de passe") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Bouton principal pour déclencher la modification du mot de passe
            Button(
                onClick = {
                    // Vérification que les nouveaux mots de passe correspondent
                    if (nouveauMotDePasse.text != confirmerMotDePasse.text) {
                        Toast.makeText(
                            contexte,
                            "Les nouveaux mots de passe ne correspondent pas",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        // Appel de la fonction de modification si tout est correct
                        modifierMotDePasse(
                            contexte,
                            auth,
                            utilisateurActuel,
                            email.text,
                            ancienMotDePasse.text,
                            nouveauMotDePasse.text
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            ) {
                Text(
                    text = "Mettre à jour",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Espacement pour le footer
            Spacer(modifier = Modifier.weight(1f))

            // Footer
            Footer()
        }
    }
}

// Fonction principale pour gérer la modification du mot de passe dans Firebase
private fun modifierMotDePasse(
    contexte: Context,
    auth: FirebaseAuth,
    utilisateurActuel: FirebaseUser?,
    emailSaisi: String,
    ancienMotDePasse: String,
    nouveauMotDePasse: String
) {
    // Vérification que tous les champs sont remplis
    if (emailSaisi.isBlank() || ancienMotDePasse.isBlank() || nouveauMotDePasse.isBlank()) {
        Toast.makeText(contexte, "Veuillez remplir les champs", Toast.LENGTH_SHORT).show()
        return
    }

    // Vérification qu'un utilisateur est bien connecté
    if (utilisateurActuel == null) {
        Toast.makeText(contexte, "Vous n'êtes pas connecté", Toast.LENGTH_SHORT).show()
        return
    }

    val emailUtilisateur = utilisateurActuel.email ?: ""

    // On vérifie que l'email saisi correspond à celui du compte connecté
    if (emailSaisi != emailUtilisateur) {
        Toast.makeText(contexte, "Email incorrect. Utilisez l'email du compte actuel.", Toast.LENGTH_LONG).show()
        return
    }

    // Les utilisateurs connectés via Google doivent modifier leur mot de passe directement chez Google
    val estUtilisateurGoogle = utilisateurActuel.providerData.any { it.providerId == "google.com" }
    if (estUtilisateurGoogle) {
        Toast.makeText(contexte, "Vous utilisez un compte Google. Modifiez votre mot de passe via Google.", Toast.LENGTH_LONG).show()
        // Redirection vers la page Google
        val intent = Intent(Intent.ACTION_VIEW,
            "https://myaccount.google.com/security-checkup".toUri())
        contexte.startActivity(intent)
        return
    }

    // Processus de réauthentification obligatoire pour les modifications
    // On revérifie l'identité avant de changer le mot de passe
    val credential = EmailAuthProvider.getCredential(emailSaisi, ancienMotDePasse)

    utilisateurActuel.reauthenticate(credential).addOnCompleteListener { tacheReauth ->
        if (tacheReauth.isSuccessful) {
            // Si la réauthentification réussit, on peut procéder au changement
            utilisateurActuel.updatePassword(nouveauMotDePasse).addOnCompleteListener { tacheMiseAJour ->
                if (tacheMiseAJour.isSuccessful) {
                    // Succès: on informe l'utilisateur et on retourne à l'accueil
                    Toast.makeText(contexte, "Votre mot de passe a été mis à jour avec succès", Toast.LENGTH_LONG).show()
                    val intent = Intent(contexte, ProfilActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    contexte.startActivity(intent)
                } else {
                    // Échec lors de la mise à jour
                    Toast.makeText(contexte, "Échec de la mise à jour du mot de passe", Toast.LENGTH_LONG).show()
                }
            }
        } else {
            // Échec de la réauthentification ancien mot de passe incorrect
            Toast.makeText(contexte, "Votre mot de passe est incorrect, Réessayez", Toast.LENGTH_LONG).show()
        }
    }
}

// preview de l'écran
@Preview(showBackground = true)
@Composable
fun PreviewModifierMDPScreen() {
    MarketTheme {
        ModifierMDPScreen()
    }
}