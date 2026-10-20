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

    // --- Onglet Checklists ---
    Section("Onglet Checklists")
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
    Bullet("Terrains proches (ANLFLD / NUMFLD) : aérodromes les plus proches et leur relèvement, issus de la base aérodromes complète (hors-ligne). En voyage, ils s'adaptent à votre position — pas seulement votre liste. Un aérodrome proche non listé : ouvrez sa VAC à la demande ou ajoutez-le à vos terrains.")
    Bullet("Enregistreur de vol (ANLFDR / NUMFDR) : conserve les dernières minutes de vol (GPS, accélération, inclinaison, altitude, baromètre). Double-tap = pause / reprise. Appui long = exporter dans Téléchargements : trace KML (Google Earth) ou GPX couvrant tout le vol (positions + altitude), ou log brut CSV des X dernières minutes (toutes les mesures). Récupérable en branchant l'appareil au PC. Il tourne en continu tant que l'application est active (service de vol), y compris quand vous passez sur une autre application ; il s'arrête au « Quitter ». Durée conservée et fréquence de sauvegarde réglables dans Réglages ▸ Instruments. Pastille verte = paramètre disponible et enregistré, orange = capteur absent. Anneau / barre orange = temps avant la prochaine sauvegarde disque.")
    Bullet("Trammer (NUMTRM) : pense-bête du point de report reprenant le mnémonique TRAMMER — Top, Route, Altitude, Moteur, Météo, Essence, Radio/Radionav. Les 7 lettres s'affichent au centre en grandes pills ; un unique interrupteur à droite indique la vérification courante. Double-tapez l'interrupteur : la lettre courante passe au vert et l'interrupteur avance automatiquement à la suivante. Au dernier double-tap, l'heure courante (HHhMM) est renseignée à gauche en bleu comme heure de passage. 5 secondes plus tard, l'instrument se réinitialise automatiquement (lettres OFF, retour sur Top) mais l'heure du dernier TRAMMER reste affichée jusqu'au prochain cycle complet.")
    Bullet("Approche finale (NUMAPP, 3 lignes mini) : vue « tunnel » 3D vers la piste (highway-in-the-sky). Trois cadres emboîtés (vert 500 / jaune 1000 / orange 1500 ft au-dessus du tour de piste = altitude terrain + 1000 ft) fuient vers la piste. La silhouette d'ULM se décale selon l'écart d'axe (droite/gauche) et de plan (au-dessus/en dessous du plan à 3°) : verte dans les tolérances (±15 m d'axe, ±60 ft de plan), orange en dehors. Bandeau : IAS, altitude, vario, écart d'axe (m G/D) et écart au plan (ft / 3°). Cible = aérodrome le plus proche (AUTO) ; appui long = verrouiller un terrain et un QFU (ou revenir en AUTO). En v1 la cible est le point de référence de l'aérodrome (proxy du seuil) et le QFU vient de la fiche au pas de 10° : aide INDICATIVE, non certifiée, sans conscience du relief.")
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
    SubSection("Carte météo (radar + METAR/SIGMET)")
    Bullet("Radar de précipitations (RainViewer) superposé à la carte, centré sur votre position.")
    Bullet("METAR : pastilles colorées par catégorie de vol (VFR/MVFR/IFR/LIFR) ; tapez-en une pour le METAR brut + le TAF.")
    Bullet("SIGMET : zones de phénomènes dangereux (orages, turbulence, givrage…) dessinées et cliquables ; tapez une zone pour son détail (nature, sommet, texte brut). Source : AviationWeather.gov.")
    Bullet("Calques activables (bouton en haut à droite) : Radar, METAR/TAF, SIGMET — le choix est mémorisé.")
    Bullet("Trajectoire future matérialisée par un trait en pointillés sur le cap actuel.")
    Bullet("Votre position est indiquée par une icône d'avion orientée selon le cap.")
    Bullet("Encart « vent au FL20 » : direction et force du vent en altitude.")
    SubSection("Moving Map")
    Bullet("Tapez la tuile « Moving Map » d'un tableau de bord pour ouvrir la carte en plein écran.")
    Bullet("Fond de carte type OACI (données OpenStreetMap) + espaces aériens, aérodromes, obstacles et points VFR (données OpenAIP), le tout hors-ligne.")
    Bullet("Votre position est au centre (icône avion jaune) ; la carte suit l'avion. Pincez pour zoomer / dézoomer.")
    Bullet("Orientation réglable : Nord en haut ou Cap en haut (Réglages ▸ Instruments ▸ Moving Map).")
    Bullet("En-tête EFIS (cap, altitude, vario, vitesse, bille) affiché au-dessus de la carte.")
    Bullet("La carte se télécharge dans Réglages ▸ Données ▸ Fond de Carte (à faire en Wi-Fi). Une alerte au démarrage signale une version plus récente.")
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
    Bullet("Carte VAC : téléchargement du PDF officiel (SIA) selon le cycle AIRAC configuré dans Réglages ▸ Terrains (le téléchargement se fait depuis Réglages ▸ Données ▸ Cartes VAC).")
    Spacer()

    // --- Réglages ---
    Section("Réglages")
    Para(
        "Les réglages sont répartis en onglets, accessibles par la barre défilante en haut " +
            "de l'écran (le premier bouton « Retour » revient à l'accueil) : Recherche, Application, " +
            "Cockpits, Instruments, Appareils, Checklists, Terrains, Données, Aide et Avertissement.",
    )
    SubSection("Recherche")
    Bullet("Recherche globale dans tous les réglages : tapez un mot-clé pour retrouver directement le paramètre voulu.")
    SubSection("Application")
    Bullet("Durée de l'écran de démarrage.")
    Bullet("Empêcher la mise en veille de l'écran (activé par défaut).")
    Bullet("Thème sombre / automatique.")
    Bullet("« Quitter l'application » : arrête le service de vol et ferme complètement l'application (voir ci-dessous).")
    SubSection("Cockpits")
    Bullet("Marqueurs des cockpits : style des marqueurs (points, barres ou numéros) et position (en haut ou en bas).")
    Bullet("Tableaux de bord : liste (ajouter / modifier / supprimer via le menu ⋮). Pastille numérotée à gauche : tapez-la pour réordonner. Case « Afficher dans le Cockpit » : détermine la présence dans l'onglet Cockpits.")
    Bullet("Import d'un tableau de bord au format JSON ou par QR code.")
    Bullet("L'édition d'un tableau (écran dédié) permet de choisir son nom, ses colonnes / lignes et l'instrument de chaque case.")
    Bullet(
        "Bouton « Options » d'une case : ouvre une dialogue regroupant la couleur / le style du contour " +
            "(Couleur par défaut, Carbone, Métal, ou une couleur sombre spécifique) ET la gestion de la case " +
            "(fusionner en large / en haut, ou séparer). Utile pour repérer un instrument d'un coup d'œil.",
    )
    SubSection("Instruments")
    Bullet(
        "Contour des cadrans : style du contour des instruments analogiques et du fond des titres " +
            "des instruments numériques — Couleur unie, Carbone ou Métal. En mode Couleur, choisissez " +
            "la teinte par défaut dans une palette sombre.",
    )
    Bullet(
        "Cette couleur par défaut s'applique à tous les instruments, mais peut être surchargée " +
            "instrument par instrument lors de l'édition d'un tableau de bord (voir Cockpits).",
    )
    Bullet(
        "« Afficher les valeurs numériques » : ne concerne QUE les instruments analogiques. " +
            "Les instruments numériques affichent toujours leurs valeurs.",
    )
    Bullet("Indicateurs de gestes, réactivité des instruments et fermeture automatique du mode Focus.")
    Bullet("Sources de données : source du cap (magnétique + calibration, ou track GPS) et source du variomètre (GPS ou baromètre).")
    Bullet("Unités : vitesse (km/h ou nœuds), altitude (ft ou m) et correction géoïde.")
    Bullet("Enregistreur de vol : durée conservée et fréquence de sauvegarde sur disque.")
    Bullet("Moving Map : orientation Nord en haut / Cap en haut et boutons de zoom.")
    Bullet("Trafic Safesky : affichage du trafic environnant.")
    SubSection("Appareils")
    Para("Créez, modifiez ou supprimez vos appareils. Pour chacun :")
    Bullet("Nom, sous-titre, icône.")
    Bullet("Caractéristiques techniques (masse à vide, moteur, etc.) : liste éditable — ajoutez une ligne libellé / valeur / unité, ou supprimez-la.")
    Bullet(
        "Vitesses (km/h) pour les arcs EFIS : Vs0/Vs1 (décrochage), arc vert (Vmin/Vmax), " +
            "arc blanc volets (Vmin/Vmoy/Vmax), Vno, Vne et Vpl (plané).",
    )
    Bullet("Import / export d'un appareil au format JSON (sauvegarde ou partage).")
    SubSection("Checklists")
    Bullet("Taille du texte des checklists.")
    Bullet("Ajout / édition des checklists (et de leurs sections) de l'appareil courant.")
    SubSection("Terrains")
    Bullet(
        "Cycle AIRAC utilisé pour les cartes VAC. Le cycle en cours est désormais " +
            "calculé automatiquement d'après la date du système et pré-rempli quand le " +
            "champ est vide ; il reste entièrement modifiable pour une correction manuelle. " +
            "Le texte d'aide sous le champ rappelle le cycle calculé (ex. « Cycle en cours " +
            "calculé : 01_OCT_2026 ») et un bouton « Aujourd'hui » apparaît pour revenir au " +
            "cycle courant en un geste dès que la valeur saisie en diffère.",
    )
    Bullet(
        "La valeur n'est plus que le segment variable DD_MON_YYYY (ex. 01_OCT_2026), avec " +
            "le mois en abréviation anglaise de 3 lettres en majuscules (JAN, FEB, … DEC) ; " +
            "le préfixe eAIP_ est désormais intégré à l'URL de téléchargement. Les anciennes " +
            "valeurs enregistrées avec le préfixe sont migrées automatiquement.",
    )
    Bullet("Liste des terrains (ajout / édition), avec accès à leur carte VAC.")
    SubSection("Données")
    Bullet("Fond de Carte : téléchargement / mise à jour de la carte VFR hors-ligne (à faire en Wi-Fi, ~400 Mo) et « Vérifier les mises à jour ».")
    Bullet("Cartes VAC : téléchargement et vérification des PDF officiels (SIA), selon le cycle AIRAC configuré dans l'onglet Terrains.")
    Bullet("Sauvegarde / Restauration : export et import de vos données.")
    Bullet("Attribution : fond © contributeurs OpenStreetMap (ODbL) ; données aéronautiques © OpenAIP (CC BY-NC — usage non commercial).")
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
    Bullet("Pour fermer réellement l'application : Réglages ▸ Application ▸ « Quitter l'application », ou l'action « Quitter » de la notification.")
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
