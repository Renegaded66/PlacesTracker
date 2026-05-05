# PlacesTracker 🌍

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.0-blue.svg)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Android-Min%20SDK%2024-green.svg)](https://developer.android.com)
[![Supabase](https://img.shields.io/badge/Backend-Supabase-black.svg)](https://supabase.com)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**PlacesTracker** ist eine moderne Android-Anwendung, die entwickelt wurde, um deine Reisen und Erlebnisse auf eine innovative Weise festzuhalten. Statt einfacher Listen bietet PlacesTracker eine interaktive 3D-Visualisierung und eine nahtlose Integration von Medien und Standorten.

---

## ✨ Hauptfunktionen

### 🗺️ Interaktive Visualisierung
- **3D-Globus**: Betrachte deine Reisen auf einem individuell gerenderten OpenGL-Globus.
- **Detaillierte Karten**: Integration von Google Maps und OpenStreetMap (OSM) zur genauen Standorterfassung.
- **Timeline-Galerie**: Eine chronologische Ansicht deiner Fotos und Videos, verknüpft mit deinen Trips.

### 🚗 Trip- & Activity-Tracking
- **Automatisches Tracking**: Erfasse deine Wege im Hintergrund mit dem integrierten Tracking-Service.
- **Trip-Management**: Organisiere deine Stopps, füge Notizen hinzu und wähle Cover-Bilder für deine Reisen aus.
- **Bucket List**: Plane zukünftige Abenteuer und lass sie automatisch in deinen Feed übergehen, sobald das Datum erreicht ist.

### 💻 PC-Edit Modus (Einzigartig!)
- Bearbeite deine Einträge bequem am Computer. PlacesTracker startet einen lokalen **Ktor-Server**, der es dir ermöglicht, Daten über deinen Webbrowser im lokalen Netzwerk zu editieren.

### 👥 Social & Cloud
- **Friend-System**: Teile deine Reisen mit Freunden und verfolge deren Erlebnisse in einem dedizierten Friend-Feed.
- **Cloud-Sync**: Echtzeit-Synchronisation mit **Supabase** (PostgreSQL, Auth & Realtime).
- **Backup & Restore**: Erstelle lokale Backups deiner Datenbank und Medien.

---

## 🛠️ Tech-Stack

- **Programmiersprache**: [Kotlin](https://kotlinlang.org/)
- **UI-Framework**: Hybrid aus [Jetpack Compose](https://developer.android.com/jetpack/compose) und klassischen XML Layouts.
- **Datenbank**: [Room Persistence Library](https://developer.android.com/training/data-storage/room) für lokales Caching.
- **Backend**: [Supabase](https://supabase.com/) für Benutzerverwaltung und Cloud-Speicherung.
- **Networking**: [Ktor](https://ktor.io/) (Client & Server) und [OkHttp](https://square.github.io/okhttp/).
- **Medien**: [Coil](https://coil-kt.github.io/coil/) & [Glide](https://github.com/bumptech/glide) für Bilder, [Media3/ExoPlayer](https://developer.android.com/guide/topics/media/exoplayer) für Videos.
- **Dependency Injection & Workers**: [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager) für Hintergrundaufgaben wie Galerie-Scans und Synchronisation.

---

## 🚀 Installation & Build

1. **Repository klonen**:
   ```bash
   git clone https://github.com/dein-username/placestracker.git
   ```

2. **Supabase Setup**:
   - Erstelle ein Projekt auf [Supabase](https://app.supabase.com/).
   - Trage deine API-Keys in die entsprechende Konfigurationsdatei (z.B. `SupabaseManager.kt` oder `local.properties`) ein.

3. **Projekt bauen**:
   - Öffne das Projekt in **Android Studio (Ladybug oder neuer)**.
   - Synchronisiere die Gradle-Dateien.
   - Starte die App auf einem Emulator oder einem physischen Gerät (Min SDK 24).

---

## 📂 Projektstruktur

- `ui/`: Enthält alle UI-Komponenten (Compose Screens, Fragments, Adapters).
- `data/`: Room-Entitäten, DAOs und Repositories.
- `globe/`: OpenGL-Implementierung für den 3D-Globus.
- `service/`: Hintergrund-Services für Tracking und den PC-Edit Server.
- `worker/`: Hintergrund-Jobs für Synchronisation und Medien-Scans.

---

## 📝 Lizenz

Dieses Projekt ist unter der MIT-Lizenz lizenziert. Siehe die [LICENSE](LICENSE) Datei für Details.

---

*Entwickelt mit ❤️ für Reisende.*
