---
id: f3309776-67ab-48f0-ba39-d2f03abf6d13
type: system-requirement
status: approved
title: Restbetrag bei der VKB nacheinreichen
requirement-type: functional
acceptance-criteria:
  - Bei Status Abgelehnt oder Teilweise erstattet bei der DAK bietet die App eine Einreichung bei der VKB an.
  - Als Betrag der VKB-Einreichung wird der nicht erstattete Rest vorgeschlagen.
verification-method: test
derives-from:
  - { id: e5203d43-3034-41de-afe3-996d8d6faf29, version: n2:cce29236715e306b, title: Einreichungen nachverfolgen }
---

Nicht oder nur teilweise erstattete DAK-Beträge werden bei der VKB erneut versucht.
