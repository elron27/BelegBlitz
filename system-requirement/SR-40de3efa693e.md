---
id: edb010d0-5fb8-4c1d-836b-40de3efa693e
type: system-requirement
status: approved
title: Status aus erstattetem Betrag ableiten
requirement-type: functional
acceptance-criteria:
  - Erstattung null ergibt den Status Abgelehnt.
  - Erstattung kleiner als der eingereichte Betrag ergibt Teilweise erstattet.
  - Erstattung gleich dem eingereichten Betrag ergibt Erstattet.
verification-method: test
derives-from:
  - { id: 3b6d543f-1c67-49e1-a06e-235be9388ad5, version: n2:dc119e85dd106f0e, title: Erstattungen und Bescheide erfassen }
  - { id: e5203d43-3034-41de-afe3-996d8d6faf29, version: n2:cce29236715e306b, title: Einreichungen nachverfolgen }
---

Der Status folgt automatisch aus dem erfassten Erstattungsbetrag.
