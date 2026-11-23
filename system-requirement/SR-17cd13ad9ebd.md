---
id: 1dd4f29f-9d46-40a8-8586-17cd13ad9ebd
type: system-requirement
status: approved
title: Status-Etiketten in der Rechnungskarte und Fortschritt in der Übersicht
requirement-type: functional
acceptance-criteria:
  - Jeder Einreichungsstatus (erfasst, eingereicht, erstattet, teilweise erstattet, abgelehnt) wird auf genau eine Etikett-Art abgebildet; die Abbildung ist je Status verschieden.
  - Der Fortschrittsanteil ist erstattet geteilt durch eingereicht, bei eingereicht gleich 0 ist er 0.
  - Der Fortschrittsanteil wird auf den Bereich 0 bis 1 begrenzt, auch bei erstattet größer als eingereicht oder negativen Werten.
verification-method: test
derives-from:
  - { id: a888c606-204d-496b-8fae-fae85a7ce5ec, version: n2:ba6e9fac7c1b5243, title: "Version 2: Bedienung und Auswertung verbessern" }
---

Die Rechnungskarte zeigt je Einreichung ein farbiges Etikett mit Kasse und Status; die Übersicht zeigt je Kasse den Anteil erstattet von eingereicht als Fortschrittsbalken. Die Logik dafür ist rein und ohne Android testbar.
