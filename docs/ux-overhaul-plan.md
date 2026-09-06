# PlacesTracker UX/Functional Overhaul — Plan

## User-Punkte (vollständige Liste aus dem Auftrag)
1. NavGraph falsch: Zurückgehen landet ständig in der falschen Ansicht
2. Trip: kein Startdatum eingebbar
3. Trip: kein Enddatum eingebbar
4. Enddatum darf optional sein
5. Auswahl des Travel-Trackers ist unauffällig und hässlich
6. Automationen bei Übergängen fehlen (Enddatum erreicht → Tracking stoppen; Live-Trip: Fotos als Stops statt Erlebnis-Vorschläge)
7. Scrollen klappt manchmal nicht, weil sich was überlappt (BottomSheet-Touch-Hacks)
8. Einstellungsseite (Trip-Editor) ultra unübersichtlich
9. Einstellungsseite (Erlebnis-Editor) unübersichtlich
10. Bestätigungen fehlen: „Wirklich löschen?" / „Änderungen gehen verloren" (Back ohne Speichern) / Draft entfernen
11. Während Live-Reise werden fälschlich Erlebnisse vorgeschlagen → stattdessen Orte+Fotos als Stops speichern
12. Dashboard: gutes Grundgerüst, aber moderner Style fehlt
13. Map im Dashboard: keine Animationen beim Ein-/Ausblenden der Vorschaubilder (Popups)
14. Kompaktansicht stellt Vorschläge als normale Erlebnisse dar (keine Draft-Badge)
15. Dashboard: Live-Aufzeichnung muss offensichtlich sein
16. Detailansicht Erlebnis: Karte lädt nicht (inline GONE)
17. Uhrzeit aus Vorschlag wird nicht übernommen (Datum schon)
18. Insgesamt: Polarstep-Niveau Funktionalität, aber eigenständiges Design

## Architekturentscheidungen
- A1: Trip erhält `endDate: Long?` (nullable = offen/laufend). `date` bleibt Startdatum. Migration 50→51 via ALTER TABLE.
- A2: Feed-Sortierung: Live zuerst (besteht), sonst max(stops.date, endDate ?: 0).
- A3: Tracking-Automation: TrackingService stoppt bei erreichtem endDate; MainActivity resumed nur bei offenem endDate.
- A4: GalleryScanWorker: aktiver Trip → Fotos als Draft-Stops (Gruppierung 500m/Tag) dem Live-Trip zuordnen.
- A5: NavGraph: Save aus NewTrip → tripDetailFragment mit popUpTo(newTripFragment, inclusive).
- A6: Dirty-Check + Back-Interceptor mit „Änderungen verwerfen?"-Dialog in Editoren.
- A7: Popup-Animationen im Globe via CSS keyframes (scale+fade).
- A8: Live-Banner im Feed-Sheet-Header (Puls-Dot), Klick → TripDetail.
- A9: Editor-Layouts: klare Sektionen statt endloser Liste.

## Nicht Bestandteil
- Kalender, Statistik, Bucket List, Freunde, Settings-Unterseiten, PC-Edit, Supabase-Sync, Globus-Renderer selbst

## Umsetzungsreihenfolge
1. Datenmodell + Migration
2. NavGraph-Fixes
3. Zeit-Bug + TimePicker-Init
4. Confirm-Dialoge + Unsaved-Changes + Delete-Button-Text
5. GalleryScanWorker-Live-Trip-Logik + Tracking-Automationen (endDate)
6. Entry-Detail-Karte inline
7. Kompaktansicht-Drafts
8. Live-Banner Dashboard
9. Trip-Editor-Redesign (Zeitraum-UI)
10. Erlebnis-Editor-Redesign
11. Trip-Selection-BottomSheet
12. Dashboard-Card-Redesign
13. Globe-Popup-Animationen
14. Scroll-Fixes
15. Full Build + Verifikation + Push + Abschlussbericht