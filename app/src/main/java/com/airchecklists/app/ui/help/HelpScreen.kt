package com.airchecklists.app.ui.help

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.airchecklists.app.R

/**
 * In-app documentation. Static, offline content describing every feature of the
 * app. Structured as titled sections with paragraphs and bullet lists.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.help_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            HelpBody()
        }
    }
}

/**
 * The full help content (intro + sections), without any scaffold or scroll
 * container. Reused both by [HelpScreen] and by the "Aide" tab in Réglages.
 * Caller provides the scroll/padding.
 */
@Composable
fun HelpBody() {
    Intro()
    HelpContent()
}

@Composable
private fun Intro() {
    Text(
        "AirDetente",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
    )
    Para(
        "Application de checklists et d'instruments de vol pour ULM / aviation légère. " +
            "Tout fonctionne hors-ligne : vos appareils, checklists et cartes sont stockés sur le téléphone.",
    )
    Note(
        "⚠️ Les instruments EFIS sont indicatifs et NON certifiés. Ne les utilisez jamais comme " +
            "instruments de vol primaires.",
    )
    Spacer()
}

@Composable
private fun HelpContent() {
    // --- Démarrage ---
    Section("Démarrage")
    Para(
        "Au lancement, après l'écran d'accueil, vous choisissez l'appareil courant. " +
            "S'il n'y en a qu'un, il est sélectionné automatiquement.",
    )
    Bullet("Le nom de l'appareil courant s'affiche dans le bandeau en haut de l'écran.")
    Bullet("Pour changer d'appareil, relancez l'application.")
    Bullet("Bouton ℹ️ du bandeau : fiche technique de l'appareil (caractéristiques + vitesses), en lecture seule.")
    Bullet(
        "Test de compatibilité : au premier lancement, un écran vérifie les capteurs de " +
            "l'appareil (gyroscope / orientation, baromètre, GPS) et vous prévient si certaines " +
            "fonctions seront indisponibles. Cochez « Ne plus afficher » pour ne plus le revoir.",
    )
    Spacer()

    // --- Onglet Checks ---
    Section("Onglet Checks")
    Para("Affiche les checklists de l'appareil courant, réparties en deux sous-onglets :")
    Bullet("Normales : prévol, mise en route, roulage, etc.")
    Bullet("Urgences : pannes, feux, atterrissage d'urgence, etc.")
    Para(
        "Ouvrez une checklist pour la dérouler item par item ; cochez au fur et à mesure. " +
            "Les sections (titres non cochables) structurent les longues listes.",
    )
    Spacer()

    // --- Onglet Cockpits ---
    Section("Onglet Cockpits")
    Para(
        "Affiche vos tableaux de bord d'instruments, reconstruits à partir des capteurs du téléphone " +
            "(orientation, accéléromètre, baromètre) et du GPS.",
    )
    SubSection("Tableaux de bord")
    Bullet("Un tableau de bord est une disposition d'instruments (une grille) que vous nommez.")
    Bullet("Les tableaux marqués « Afficher dans le Cockpit » apparaissent ici.")
    Bullet("Slidez gauche / droite pour passer d'un tableau à l'autre ; les points en haut indiquent la page.")
    Bullet("Pratique pour basculer rapidement d'une vue d'ensemble à un instrument en plein écran.")
    Bullet("La création et l'édition des tableaux se font dans Réglages ▸ Cockpits.")
    SubSection("Calage de l'horizon")
    Bullet("Icône de calage (cible) dans le bandeau : appui court = cale l'horizon sur la position actuelle.")
    Bullet("Appui long sur la même icône = réinitialise le calage.")
    SubSection("Instruments disponibles")
    Para(
        "Chaque case d'un tableau peut recevoir un instrument, en version analogique (cadran rond, préfixe ANL) " +
            "ou numérique (bandeau, préfixe NUM) :",
    )
    Bullet("Conservateur de cap (ANLCAP / NUMCAP).")
    Bullet("Anémomètre (ANLSPD / NUMSPD), avec arcs colorés de vitesse.")
    Bullet("Altimètre et Variomètre (ANLALT / ANLVAR), ou combinés « Altimètre + Vario » numériques (NUMALT).")
    Bullet("Horizon artificiel (ANLHRZ / NUMHRZ).")
    Bullet("Bille (ANLSLP / NUMSLP).")
    Bullet("EFIS complet (NUMEFS, 3 lignes mini) : cap + horizon + altitude + vario + vitesse + bille réunis.")
    Bullet("Moving Map (NUMMAP, 5 lignes mini) : tuile qui ouvre la carte plein écran (voir ci-dessous).")
    Bullet("Chronomètre (ANLCHR / NUMCHR) : départ / arrêt / remise à zéro.")
    Bullet("Compte à rebours (ANLCWN / NUMCWN) : réglez une durée ; il démarre automatiquement à la validation. Bouton « Effacer » pour revenir à --:--.")
    Bullet("Horamètre (ANLHRM / NUMHRM) : deux compteurs d'heures que vous saisissez.")
    Bullet("Montre (ANLWCH / NUMWCH) : heure courante.")
    Bullet("Météo (ANLMTO / NUMMTO) : radar de précipitations + vent au FL20 ; tapez la tuile pour la carte météo plein écran.")
    Bullet("Terrains proches (ANLFLD / NUMFLD) : aérodromes les plus proches et leur relèvement.")
    Note(
        "Gyroscope requis : l'horizon, le conservateur de cap, la bille et l'EFIS complet ont besoin " +
            "d'un capteur d'orientation. Sur un appareil qui en est dépourvu, ces instruments affichent " +
            "« Instrument indisponible » ; dans l'éditeur de tableau de bord ils portent la mention " +
            "« (gyroscope requis) ». Les autres instruments (altimètre, vario, vitesse, chrono, carte…) restent utilisables.",
    )
    Note(
        "Les valeurs saisies (chronomètres, comptes à rebours, horamètres, cap et altitude à suivre) sont " +
            "conservées quand vous changez de tableau de bord, ET après une fermeture / réouverture de l'application : " +
            "un compte à rebours lancé continue de décompter en arrière-plan.",
    )
    SubSection("Carte météo (radar)")
    Bullet("Radar de précipitations (RainViewer) superposé à la carte, centré sur votre position.")
    Bullet("Trajectoire future matérialisée par un trait en pointillés sur le cap actuel.")
    Bullet("Votre position est indiquée par une icône d'avion orientée selon le cap.")
    Bullet("Encart « vent au FL20 » : direction et force du vent en altitude.")
    SubSection("Moving Map")
    Bullet("Tapez la tuile « Moving Map » d'un tableau de bord pour ouvrir la carte en plein écran.")
    Bullet("Fond de carte type OACI (données OpenStreetMap) + espaces aériens, aérodromes, obstacles et points VFR (données OpenAIP), le tout hors-ligne.")
    Bullet("Votre position est au centre (icône avion jaune) ; la carte suit l'avion. Pincez pour zoomer / dézoomer.")
    Bullet("Orientation réglable : Nord en haut ou Cap en haut (Réglages ▸ Cockpits ▸ Cartes).")
    Bullet("En-tête EFIS (cap, altitude, vario, vitesse, bille) affiché au-dessus de la carte.")
    Bullet("La carte se télécharge dans Réglages ▸ Cockpits ▸ Cartes (à faire en Wi-Fi). Une alerte au démarrage signale une version plus récente.")
    Bullet("⚠️ Indicatif et non certifié — ne remplace pas une carte OACI officielle ni un GPS certifié.")
    SubSection("Mode démo")
    Para(
        "Appui long sur le nom de l'appareil (dans le bandeau) : lance une démonstration qui simule " +
            "un calage puis une montée, une descente, un virage et une accélération/freinage. " +
            "Le bandeau affiche « • DÉMO ». Nouvel appui long pour revenir aux capteurs réels. " +
            "En démo, l'avion se déplace aussi sur la Moving Map.",
    )
    Spacer()

    // --- Onglet Terrains ---
    Section("Onglet Terrains")
    Para("La liste de vos terrains (aérodromes / plateformes). Pour chaque terrain :")
    Bullet("Météo : METAR / TAF récupérés en ligne (NOAA) quand le terrain a un code OACI.")
    Bullet("Carte VAC : téléchargement du PDF officiel (SIA) selon le cycle AIRAC configuré.")
    Spacer()

    // --- Réglages ---
    Section("Réglages")
    Para(
        "Les réglages sont répartis en onglets, accessibles par la barre défilante en haut " +
            "de l'écran (le premier bouton « Retour » revient à l'accueil).",
    )
    SubSection("Affichage")
    Bullet("Thème sombre / automatique et taille de police du texte des checklists.")
    Bullet("Durée de l'écran d'accueil.")
    Bullet("Empêcher la mise en veille de l'écran (activé par défaut).")
    Bullet(
        "Contour des cadrans : style du contour des instruments analogiques et du fond des titres " +
            "des instruments numériques — Couleur unie, Carbone ou Métal. En mode Couleur, choisissez " +
            "la teinte par défaut dans une palette sombre.",
    )
    Bullet(
        "Cette couleur par défaut s'applique à tous les instruments, mais peut être surchargée " +
            "instrument par instrument lors de l'édition d'un tableau de bord (voir Cockpits).",
    )
    Bullet("« Quitter l'application » : arrête le service de vol et ferme complètement l'application (voir ci-dessous).")
    SubSection("Cockpits")
    Bullet("Source du cap (magnétique ou track GPS) et source du variomètre (GPS ou baromètre).")
    Bullet("Unité de vitesse (km/h ou nœuds) et réactivité des instruments.")
    Bullet(
        "« Afficher les valeurs numériques » : ne concerne QUE les instruments analogiques. " +
            "Les instruments numériques affichent toujours leurs valeurs.",
    )
    Bullet("Tableaux de bord : liste (ajouter / modifier / supprimer via le menu ⋮). Pastille numérotée à gauche : tapez-la pour réordonner. Case « Afficher dans le Cockpit » : détermine la présence dans l'onglet Cockpits.")
    Bullet("L'édition d'un tableau (écran dédié) permet de choisir son nom, ses colonnes / lignes et l'instrument de chaque case.")
    Bullet(
        "Bouton « Options » d'une case : ouvre une dialogue regroupant la couleur / le style du contour " +
            "(Couleur par défaut, Carbone, Métal, ou une couleur sombre spécifique) ET la gestion de la case " +
            "(fusionner en large / en haut, ou séparer). Utile pour repérer un instrument d'un coup d'œil.",
    )
    Bullet("Cartes : téléchargement / mise à jour de la carte hors-ligne (à faire en Wi-Fi, ~400 Mo), orientation Nord en haut / Cap en haut, et « Vérifier les mises à jour ».")
    Bullet("Attribution : fond © contributeurs OpenStreetMap (ODbL) ; données aéronautiques © OpenAIP (CC BY-NC — usage non commercial).")
    SubSection("Appareils")
    Para("Créez, modifiez ou supprimez vos appareils. Pour chacun :")
    Bullet("Nom, sous-titre, icône.")
    Bullet("Caractéristiques techniques (masse à vide, moteur, etc.) : liste éditable — ajoutez une ligne libellé / valeur / unité, ou supprimez-la.")
    Bullet(
        "Vitesses (km/h) pour les arcs EFIS : Vs0/Vs1 (décrochage), arc vert (Vmin/Vmax), " +
            "arc blanc volets (Vmin/Vmoy/Vmax), Vno, Vne et Vpl (plané).",
    )
    Bullet("Import / export d'un appareil au format JSON (sauvegarde ou partage).")
    SubSection("Checks & Terrains")
    Bullet("Ajout / édition des checklists (et de leurs sections) de l'appareil courant.")
    Bullet("Ajout / édition des terrains, avec le cycle AIRAC utilisé pour les cartes VAC.")
    SubSection("Aide")
    Bullet("Cet écran : la documentation complète et hors-ligne de l'application.")
    Spacer()

    // --- Fermeture de l'application ---
    Section("Rester actif en vol & fermeture")
    Para(
        "Pour éviter que le système n'interrompe l'application pendant un vol, AirDetente lance un " +
            "service de premier plan (une notification persistante « vol en cours » s'affiche). L'application " +
            "reste ainsi active en arrière-plan et vos instruments continuent de tourner.",
    )
    Bullet("Pour fermer réellement l'application : Réglages ▸ Affichage ▸ « Quitter l'application », ou l'action « Quitter » de la notification.")
    Bullet("Les valeurs saisies restent conservées pour la prochaine ouverture.")
    Spacer()

    // --- Arcs de vitesse ---
    Section("Comprendre les arcs de vitesse")
    Bullet("Arc vert : plage d'utilisation normale (Vmin → Vmax).")
    Bullet("Bloc rouge : Vne, vitesse à ne jamais dépasser.")
    Bullet("Arc blanc : plage d'utilisation des volets (pleins volets puis 1 cran).")
    Bullet("Curseur violet : vitesse optimale de plané (Vpl).")
    Bullet("Sur les cadrans analogiques, la valeur affichée se colore selon la plage atteinte.")
    Spacer()

    Note(
        "Ces informations sont fournies à titre indicatif. En vol, référez-vous toujours au manuel " +
            "de vol de votre appareil et aux instruments certifiés.",
    )
}

// ---- Small formatting helpers ----

@Composable
private fun Section(title: String) {
    HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))
    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Spacer(4.dp)
}

@Composable
private fun SubSection(title: String) {
    Spacer(6.dp)
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(2.dp)
}

@Composable
private fun Para(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 3.dp))
}

@Composable
private fun Bullet(text: String) {
    androidx.compose.foundation.layout.Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text("•  ", style = MaterialTheme.typography.bodyMedium)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Note(text: String) {
    androidx.compose.material3.Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun Spacer(size: androidx.compose.ui.unit.Dp = 12.dp) {
    androidx.compose.foundation.layout.Spacer(Modifier.padding(top = size / 2))
}
