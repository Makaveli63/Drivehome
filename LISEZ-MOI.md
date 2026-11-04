# DriveHome 2.0 — le launcher de conduite pour autoradio Android

Une interface façon CarPlay, mais avec beaucoup plus d'options : tableau de bord en
cartes, compteur GPS, météo, ordinateur de bord, lecture vocale des messages,
centre de contrôle, écran de veille…

## Récupérer l'APK

Chaque envoi sur GitHub compile l'APK automatiquement (onglet **Actions**, workflow **Build APK**).

- **Le plus simple :** onglet **Releases** du dépôt → dernière version → télécharge **DriveHome.apk**
  (tu peux ouvrir ce lien directement depuis le navigateur de l'autoradio).
- Ou bien : **Actions** → la dernière exécution verte → artefact **DriveHome-apk** (un zip).

Copie l'APK sur l'autoradio (clé USB ou téléchargement) et installe-le
(autorise les sources inconnues si demandé).

### Mises à jour sans désinstaller (optionnel, à faire une fois)

Sans configuration, chaque build est signé avec une clé de débogage différente : pour
installer une nouvelle version, il faut d'abord désinstaller l'ancienne. Pour l'éviter,
crée une clé fixe sur un ordinateur (Java requis) :

    keytool -genkeypair -keystore release.keystore -alias drivehome -keyalg RSA -keysize 2048 -validity 20000 -dname "CN=DriveHome"
    base64 -w0 release.keystore

puis dans GitHub → **Settings → Secrets and variables → Actions**, ajoute :
`SIGNING_KEYSTORE_B64` (le texte base64), `SIGNING_PASSWORD` (le mot de passe choisi).

## Premier lancement

1. Appuie sur Home → choisis **DriveHome** → **Toujours** (ou Réglages → Launcher par défaut).
2. Accepte la **localisation** (vitesse, météo, thème jour/nuit selon le soleil).
3. Réglages → **Autorisations & système** → **Accès aux notifications** : active DriveHome.
   C'est ce qui permet d'afficher/piloter la musique et de lire tes messages.
4. Renseigne **Domicile**, **Travail**, tes lieux et tes contacts favoris.

## Ce que DriveHome fait de plus que CarPlay

**Accueil**
- Tableau de bord en cartes, avec plusieurs pages (glisse vers la gauche)
- 9 cartes à activer et réordonner : Navigation, Musique, Compteur de vitesse, Horloge,
  Météo, Ordinateur de bord, Messages, Contacts favoris, Raccourcis d'apps
- Barre d'état : heure, date, vitesse, météo, messages non lus, GPS, batterie

**Navigation**
- Domicile / Travail / lieux favoris illimités en un tap
- Recherche d'adresse et « autour de moi » : essence, recharge, parking, resto, café…
- Fonctionne avec Google Maps, Waze, OsmAnd, HERE, Sygic, TomTom…

**Musique**
- Pochette, titre, artiste, album, barre de progression avec déplacement
- Boutons -15 s / +30 s (podcasts), changement de source audio en un tap
- Reprise automatique de la musique au démarrage de la voiture

**Conduite**
- Compteur de vitesse GPS avec jauge, cap (boussole) et altitude
- Alerte de vitesse réglable avec bip (km/h ou mph)
- Ordinateur de bord : distance, temps de conduite, vitesse moyenne et max

**Météo** — température, ciel, min/max, vent, risque de pluie (Open-Meteo, sans compte),
par GPS ou ville choisie

**Messages** — WhatsApp, SMS, Messenger, Telegram… affichés en grand, lus à voix haute
(automatiquement si tu veux), mode discret qui annonce seulement l'expéditeur

**Téléphone** — clavier de numérotation, contacts favoris en appel direct, import depuis le carnet

**Centre de contrôle** — volume, luminosité, thème, Wi-Fi, Bluetooth, égaliseur, plein écran…

**Ma voiture** — break dessiné en 3D (blanc par défaut, couleur au choix), qu'on fait tourner
du doigt. Les portes, le coffre et le capot s'ouvrent sur le dessin, avec bandeau d'alerte et annonce
vocale, et un avertissement si un ouvrant est ouvert en roulant. Plaque d'immatriculation, motorisation.
- Tableau de bord moteur via boîtier **OBD2 Bluetooth (ELM327)** : régime, vitesse, liquide de
  refroidissement, huile, tension batterie, carburant, charge, accélérateur, temp. extérieure
- Diagnostic : lecture et effacement des codes défaut
- Portes / coffre depuis le **CANbus** de l'autoradio : DriveHome écoute la diffusion
  `app.drivehome.CAR_STATUS` (extras `door_fl`, `door_fr`, `door_rl`, `door_rr`, `trunk`, `hood`,
  `fuel`, `range_km`, `temp_out`). Test : `adb shell am broadcast -a app.drivehome.CAR_STATUS --ez door_fl true`

**Vrai modèle 3D** — Ma voiture → « Modèle 3D de la voiture » → **Importer un .glb**.
Rendu 3D réaliste (moteur Filament), rotation et zoom au doigt. Si le modèle a des portes
séparées, elles pivotent vraiment à l'ouverture (association automatique ou manuelle des pièces).
Où trouver une Passat B8 Variant : Sketchfab (filtre « Downloadable »), CGTrader, RenderHub —
vérifie la licence et choisis le format **glTF binaire (.glb)**.

**Tuiles** — sur l'accueil, « ✎ Modifier » : ajouter, retirer, déplacer et redimensionner chaque
tuile (largeur × hauteur), grille réglable (colonnes × lignes).

**Dock** — bouton « + » pour ajouter autant d'applis que tu veux ; appui long sur une appli du
dock pour la déplacer ou la retirer ; boutons fixes masquables.

**Personnalisation**
- Thème auto (horaires réglables ou lever/coucher du soleil), sombre, clair
- 12 couleurs d'accent, 9 fonds d'écran + image perso (avec voile réglable)
- Taille de l'interface, des icônes, opacité des cartes
- Dock à gauche / droite / en bas, jusqu'à 8 apps
- Tiroir d'apps avec recherche, tri par usage, noms affichés ou non ; appui long :
  ajouter au dock, définir comme appli de navigation/musique, masquer, infos
- Écran de veille horloge, écran toujours allumé, plein écran
- Ouverture automatique de la navigation au démarrage

## Si la compilation échoue

Copie le message d'erreur du job GitHub Actions et envoie-le à Claude pour correction.

## Crédits

- Modèle 3D « Volkswagen Passat B8 Variant R-Line 2015 » par **Nieve5677** —
  https://sketchfab.com/3d-models/volkswagen-passat-b8-variant-r-line-2015-41af8da6d8054229ba1f462cd25ba0b4 —
  licence [CC BY 4.0](http://creativecommons.org/licenses/by/4.0/).
  Modifications : matières recréées (carrosserie blanche, vitres, jantes, pneus, chromes, feux),
  maillage allégé (≈394 k → 177 k triangles), plaques DS-241-FV ajoutées.
