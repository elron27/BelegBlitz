---
id: 6d3a0326-95a9-4557-bc88-a7d7afd41c64
type: system-requirement
status: approved
title: Rechnungsdaten per lokaler OCR erkennen
requirement-type: functional
acceptance-criteria:
  - Aus einer deutschen Rechnung werden Praxisname, Fachrichtung, Rechnungsdatum und Gesamtbetrag erkannt.
  - Die Erkennung läuft vollständig auf dem Gerät.
  - Die Erkennung bietet bei jedem Wert einen Vorschlag an, der sich ändern lässt.
verification-method: test
derives-from:
  - { id: e9682257-63c5-4a95-9dbd-f25ca07f8ad1, version: n2:b61636e3f9c3ebd6, title: Texterkennung lokal auf dem Gerät (OCR) }
  - { id: 41a04aa8-b076-4ad6-a0e7-f6a8ccad082c, version: n2:94154883f3a50edb, title: Praxis und Fachrichtung statt Kategorie erfassen }
---

Die OCR liest die Rechnungsdaten aus Foto oder PDF.
