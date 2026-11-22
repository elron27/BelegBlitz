package de.belegblitz.domain

/** SR-c43e8d9ab0a8: Entscheidungslogik der App-Sperre (ohne Android-Abhängigkeit). */
object Sperre {
    /** Kurze Frist im Hintergrund, bevor die App beim Zurückkehren wieder sperrt. */
    const val FRIST_MS = 30_000L

    /** Beim Start der App (frischer Prozess) gilt sie als gesperrt, wenn die Sperre eingeschaltet ist. */
    fun beimStartGesperrt(aktiv: Boolean): Boolean = aktiv

    /**
     * Beim Zurückkehren aus dem Hintergrund: sperren, wenn die Sperre aktiv ist und die App länger als
     * [fristMs] im Hintergrund war. [imHintergrundSeitMs] ist null, wenn sie nicht im Hintergrund war.
     */
    fun beimZurueckkehrenGesperrt(
        aktiv: Boolean,
        imHintergrundSeitMs: Long?,
        jetztMs: Long,
        fristMs: Long = FRIST_MS,
    ): Boolean = aktiv && imHintergrundSeitMs != null && jetztMs - imHintergrundSeitMs >= fristMs

    /** Ohne eingerichtete Fingerabdruck- oder Gerätesperre kann man nicht entsperren; dann nicht aussperren. */
    fun einschaltenMoeglich(authentifizierungVerfuegbar: Boolean): Boolean = authentifizierungVerfuegbar
}
