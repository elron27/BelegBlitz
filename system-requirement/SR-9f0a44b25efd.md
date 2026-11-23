---
id: d50cf991-ec29-405c-b450-9f0a44b25efd
type: system-requirement
status: approved
title: Zugeordnete Bescheide unter der Rechnung anzeigen
requirement-type: functional
acceptance-criteria:
  - Je zugeordnetem Bescheid steht eine Überschrift mit Kasse und Datum; mehrere Positionen desselben Bescheids erscheinen nur einmal.
  - Die Bescheide sind nach Datum aufsteigend sortiert, bei gleichem Datum nach Id.
  - Fehlt das Dokument eines Bescheids, erscheint stattdessen eine Textzeile statt eines Absturzes.
verification-method: test
derives-from:
  - { id: a888c606-204d-496b-8fae-fae85a7ce5ec, version: n2:ba6e9fac7c1b5243, title: "Version 2: Bedienung und Auswertung verbessern" }
---

Die Rechnungs-Detailansicht zeigt unter den Rechnungsseiten die der Rechnung zugeordneten Bescheide mit Kasse, Datum und Seitenvorschau.
