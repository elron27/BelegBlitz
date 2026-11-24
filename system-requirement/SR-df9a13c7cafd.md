---
id: 9f004313-38d7-42ab-b7b3-df9a13c7cafd
type: system-requirement
status: approved
title: Hinweis zu Rechnung und Bescheid
requirement-type: functional
acceptance-criteria:
  - Der Nutzer kann zu jeder Rechnung und jedem Bescheid einen Hinweis speichern und ändern; der Hinweis wird ohne umgebende Leerzeichen gespeichert.
  - Ein leerer oder nur aus Leerzeichen bestehender Hinweis entfernt den Hinweis (gespeichert wird kein Text).
  - Export und Wiederherstellung enthalten den Hinweis von Rechnung und Bescheid unverändert (auch Anführungszeichen, Umlaute und Zeilenumbrüche); ein fehlender Hinweis bleibt leer.
  - Eine alte Sicherung ohne Hinweisfeld bleibt lesbar; die übrigen Felder werden unverändert wiederhergestellt.
  - Die Volltextsuche findet Rechnungen und Bescheide über ihren Hinweis, auch zusammen mit Begriffen aus dem OCR-Text.
verification-method: test
derives-from:
  - { id: a888c606-204d-496b-8fae-fae85a7ce5ec, version: n2:ba6e9fac7c1b5243, title: "Version 2: Bedienung und Auswertung verbessern" }
---

Zu jeder Rechnung und jedem Bescheid kann der Nutzer einen freien Hinweis festhalten (Issue #17). Der Hinweis ist Teil der Sicherung und wird bei der Volltextsuche mit durchsucht.
