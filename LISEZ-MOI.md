# DriveHome — launcher façon CarPlay pour autoradio Android

## Compiler l'APK (sans Mac, sans Android Studio)

1. Crée un dépôt GitHub (privé) et envoie-y **tout le contenu de ce dossier**,
   y compris le dossier caché `.github`.
2. Onglet **Actions** → workflow **Build APK** → il se lance tout seul au premier envoi
   (ou bouton **Run workflow**).
3. Quand c'est vert, ouvre l'exécution et télécharge l'artefact **DriveHome-apk**
   (un zip contenant `app-debug.apk`).
4. Copie l'APK sur l'autoradio (clé USB ou téléchargement) et installe-le
   (autorise les sources inconnues si demandé).

## Premier lancement sur l'autoradio

- Appuie sur Home → choisis **DriveHome** → **Toujours** (ou Réglages → Launcher par défaut).
- Réglages → **Accès aux notifications** → active DriveHome : c'est ce qui permet
  d'afficher et de piloter la musique en cours (Spotify, YouTube Music, etc.).
- Réglages → renseigne **Domicile** et **Travail** pour les boutons de navigation en un tap.

## Options disponibles

- Thème auto (sombre de 19h à 7h) / sombre / clair, 8 couleurs d'accent
- 7 fonds d'écran + image personnalisée
- Dock à gauche, à droite ou en bas, taille des icônes réglable
- Cartes Navigation, Musique et Horloge activables séparément, horloge 12/24 h
- Choix de l'appli de navigation et de musique, dock personnalisable (5 apps)
- Tiroir d'applications avec recherche ; appui long : ajouter au dock, masquer, infos
- Bouton assistant vocal

## Si la compilation échoue

Copie le message d'erreur du job GitHub Actions et envoie-le à Claude pour correction.
