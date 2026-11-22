---
id: 6a3ff7c3-4854-4c62-af06-dbb8a31cd03b
type: system-requirement
status: approved
title: Bescheid automatisch der Rechnung zuordnen
requirement-type: functional
acceptance-criteria:
  - Die App ordnet einen Bescheid der Rechnung zu, in der Reihenfolge Rechnungsdatum, Fachrichtung, Rechnungsbetrag; fehlt ein Merkmal im Bescheid, wird es übersprungen.
  - Ein Bescheid, der nur Rechnungsdatum und Erstattungsbetrag nennt (DAK-Brief), wird über das Rechnungsdatum zugeordnet; sein Betrag wird nicht mit dem Rechnungsbetrag verglichen.
  - Bei mehreren möglichen Rechnungen schlägt die App Kandidaten vor und lässt den Nutzer wählen.
  - Ohne Treffer kann der Nutzer die Rechnung von Hand zuordnen.
verification-method: test
derives-from:
  - { id: 3b6d543f-1c67-49e1-a06e-235be9388ad5, version: n2:dc119e85dd106f0e, title: Erstattungen und Bescheide erfassen }
  - { id: e9682257-63c5-4a95-9dbd-f25ca07f8ad1, version: n2:b61636e3f9c3ebd6, title: Texterkennung lokal auf dem Gerät (OCR) }
---

Die Zuordnung läuft über Rechnungsdatum, Fachrichtung und Betrag, in dieser Reihenfolge. Manche Bescheide nennen weder Arzt noch Rechnungsbetrag (z. B. DAK).
