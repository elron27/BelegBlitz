package de.belegblitz.domain.ocr

/**
 * SR-a7d7afd41c64: Fachrichtung aus Schlüsselwörtern. Erweiterbar: einfach einen Eintrag in [LISTE] ergänzen.
 * [arzt] nennt die Praxis/den Arzt selbst, [leistung] typische Behandlungen (nur in Rechnungen ausgewertet,
 * denn ein DAK-Brief über eine Zahnreinigung nennt keinen Arzt).
 */
internal class Fachrichtung(val name: String, val arzt: Regex, val leistung: Regex? = null)

object Fachrichtungen {
    private fun r(s: String) = Regex(s, RegexOption.IGNORE_CASE)

    internal val LISTE = listOf(
        Fachrichtung("Zahnarzt", r("""zahn(arzt|ärzt|aerzt|medizin|klinik)|\bdent\.|dental|kieferorthop"""),
            r("""zahnreinigung|prophylaxe|zahnstein|\bZ[äa]hne?\b|füllung|krone""")),
        Fachrichtung("Hausarzt", r("""hausarzt|hausärzt|allgemeinmedizin|praktische[rn]? arzt|innere medizin|internist""")),
        Fachrichtung("Augenarzt", r("""augenarzt|augenärzt|augenheilkunde|ophthalm|optometr"""), r("""sehstärke|brille|netzhaut""")),
        Fachrichtung("Orthopäde", r("""orthop[äa]d|unfallchirurg"""), r("""wirbelsäule|gelenk""")),
        Fachrichtung("Hautarzt", r("""hautarzt|hautärzt|dermatolog"""), r("""hautkrebs|muttermal""")),
        Fachrichtung("Frauenarzt", r("""frauenarzt|frauenärzt|gyn[äa]kolog"""), r("""schwangerschaft|krebsvorsorge""")),
        Fachrichtung("HNO", r("""\bHNO\b|hals-?,? ?nasen|ohrenarzt"""), r("""audiometrie|tonaudio""")),
        Fachrichtung("Physiotherapie", r("""physiotherap|krankengymnastik"""), r("""massage|manuelle therapie""")),
        Fachrichtung("Radiologie", r("""radiolog|röntgen|\bMRT\b|computertomograph"""), r("""kernspin""")),
        Fachrichtung("Labor", r("""\blabor(arzt|ärzt|medizin)?\b|laborgemeinschaft"""), r("""blutbild|blutuntersuchung""")),
        Fachrichtung("Kinderarzt", r("""kinderarzt|kinderärzt|pädiat"""), null),
        Fachrichtung("Urologe", r("""urolog"""), null),
        Fachrichtung("Neurologe", r("""neurolog"""), null),
        Fachrichtung("Kardiologe", r("""kardiolog"""), r("""\bEKG\b""")),
    )

    /**
     * Wählt die Fachrichtung mit den meisten Treffern. Sicher nur, wenn sie einen eindeutigen Vorsprung hat
     * (SR-e268ffbc5098); kein Treffer -> null.
     */
    fun erkennen(text: String, mitLeistungen: Boolean = true): Wert<String>? {
        val treffer = LISTE.map { f ->
            val n = f.arzt.findAll(text).count() * 2 + (if (mitLeistungen) f.leistung?.findAll(text)?.count() ?: 0 else 0)
            f to n
        }.filter { it.second > 0 }.sortedByDescending { it.second }
        val erster = treffer.firstOrNull() ?: return null
        val sicher = treffer.size == 1 || erster.second >= treffer[1].second * 2
        return Wert(erster.first.name, sicher)
    }

    /** Vergleichsform für die Zuordnung ("Zahnärztin" und "Zahnarzt" sind dieselbe Fachrichtung). */
    fun normalisieren(text: String): String =
        erkennen(text, mitLeistungen = false)?.wert?.lowercase() ?: text.trim().lowercase()
}
