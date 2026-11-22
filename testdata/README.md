# Testdaten (Erwartungswerte)

Die echten, geschwärzten Dokumente liegen in `testdata/echt/` und sind per `.gitignore` nicht im Repo
(enthalten noch sichtbare Versicherungsnummern). Erwartete OCR-Ergebnisse:

| Datei | Erwartung |
|---|---|
| rechnung-zahnarzt-20260728.jpg | Fachrichtung Zahnarzt, Praxis MVZ Chiemgau lacht GmbH (Titel Zahnarzt_MVZ-Chiemgau-lacht_20260728), Rechnungsdatum 2026-07-28, Betrag 140,00 |
| dak-bescheid-20260824.jpg | Schreiben 2026-08-24, Bezug Rechnung vom 2026-07-28, Erstattung 60,00. Kein Arzt, kein Rechnungsbetrag |
| vkb-bescheid-20260907-seite1..3.png | Abrechnung 2026-09-07, Beleg vom 2026-07-28 (Zahnarztrechnung), Rechnungsbetrag 140,00, Auszahlung 7,93, Hinweise 0240 und 0899 (erklärt auf Seite 3); nur Seite 1 hat Daten |

Zusammenhang: eine Rechnung (140,00), DAK erstattet 60,00, VKB erstattet 7,93 (Teilerstattung bei beiden Kassen).
