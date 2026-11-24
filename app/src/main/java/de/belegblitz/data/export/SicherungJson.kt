package de.belegblitz.data.export

import de.belegblitz.data.Bescheid
import de.belegblitz.data.BescheidPosition
import de.belegblitz.data.Einreichung
import de.belegblitz.data.Person
import de.belegblitz.data.Rechnung
import de.belegblitz.data.VkbBeitrag
import de.belegblitz.domain.EinreichungStatus
import de.belegblitz.domain.Kasse
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.LocalDate

/** SR-e334c167571f, SR-176f6f1f60df: versionierte JSON-Datendatei (reine Serialisierung, ohne Android-Klassen außer org.json). */
object SicherungJson {

    fun zuJson(s: Sicherung): String = JSONObject().apply {
        put("formatVersion", Sicherung.FORMAT_VERSION)
        put("personen", arr(s.personen) { JSONObject().put("id", it.id).put("name", it.name) })
        put("rechnungen", arr(s.rechnungen) {
            JSONObject().put("id", it.id).put("personId", it.personId).put("praxis", it.praxis)
                .put("fachrichtung", it.fachrichtung).put("rechnungsdatum", it.rechnungsdatum.toString())
                .put("betragCent", it.betragCent).put("titel", it.titel).put("pdfPfad", it.pdfPfad ?: JSONObject.NULL)
                .put("ocrText", it.ocrText ?: JSONObject.NULL).put("erstellt", it.erstellt)
                .put("hinweis", it.hinweis ?: JSONObject.NULL)
        })
        put("einreichungen", arr(s.einreichungen) {
            JSONObject().put("id", it.id).put("rechnungId", it.rechnungId).put("kasse", it.kasse.name)
                .put("einreichungsdatum", it.einreichungsdatum?.toString() ?: JSONObject.NULL)
                .put("betragCent", it.betragCent).put("status", it.status.name)
                .put("erstatteterBetragCent", it.erstatteterBetragCent ?: JSONObject.NULL)
                .put("hinweis", it.hinweis ?: JSONObject.NULL).put("dokumentPfad", it.dokumentPfad ?: JSONObject.NULL)
        })
        put("bescheide", arr(s.bescheide) {
            JSONObject().put("id", it.id).put("kasse", it.kasse.name).put("datum", it.datum.toString())
                .put("dokumentPfad", it.dokumentPfad ?: JSONObject.NULL).put("ocrText", it.ocrText ?: JSONObject.NULL)
                .put("hinweis", it.hinweis ?: JSONObject.NULL)
        })
        put("positionen", arr(s.positionen) {
            JSONObject().put("id", it.id).put("bescheidId", it.bescheidId).put("rechnungId", it.rechnungId)
                .put("einreichungId", it.einreichungId ?: JSONObject.NULL).put("betragCent", it.betragCent)
        })
        put("vkbBeitraege", arr(s.beitraege) {
            JSONObject().put("id", it.id).put("gueltigAb", it.gueltigAb.toString()).put("monatsbetragCent", it.monatsbetragCent)
        })
        put("einstellungen", JSONObject(s.einstellungen))
    }.toString(2)

    /** @throws SicherungsFehler bei ungültigem Inhalt oder einer neueren Formatversion */
    fun ausJson(text: String): Sicherung = try {
        val o = JSONObject(text)
        val v = o.optInt("formatVersion", -1)
        if (v < 1) throw SicherungsFehler("Keine gültige Datendatei (formatVersion fehlt)")
        if (v > Sicherung.FORMAT_VERSION) throw SicherungsFehler("Datendatei stammt aus einer neueren App-Version ($v)")
        Sicherung(
            personen = list(o, "personen") { Person(it.getLong("id"), it.getString("name")) },
            rechnungen = list(o, "rechnungen") {
                Rechnung(
                    it.getLong("id"), it.getLong("personId"), it.getString("praxis"), it.getString("fachrichtung"),
                    LocalDate.parse(it.getString("rechnungsdatum")), it.getLong("betragCent"), it.getString("titel"),
                    str(it, "pdfPfad"), str(it, "ocrText"), it.getLong("erstellt"),
                    str(it, "hinweis"), // fehlt in älteren Sicherungen -> null
                )
            },
            einreichungen = list(o, "einreichungen") {
                Einreichung(
                    it.getLong("id"), it.getLong("rechnungId"), Kasse.valueOf(it.getString("kasse")),
                    str(it, "einreichungsdatum")?.let(LocalDate::parse), it.getLong("betragCent"),
                    EinreichungStatus.valueOf(it.getString("status")), lng(it, "erstatteterBetragCent"),
                    str(it, "hinweis"), str(it, "dokumentPfad"),
                )
            },
            bescheide = list(o, "bescheide") {
                Bescheid(
                    it.getLong("id"), Kasse.valueOf(it.getString("kasse")), LocalDate.parse(it.getString("datum")),
                    str(it, "dokumentPfad"), str(it, "ocrText"), str(it, "hinweis"),
                )
            },
            positionen = list(o, "positionen") {
                BescheidPosition(
                    it.getLong("id"), it.getLong("bescheidId"), it.getLong("rechnungId"), lng(it, "einreichungId"),
                    it.getLong("betragCent"),
                )
            },
            beitraege = list(o, "vkbBeitraege") {
                VkbBeitrag(it.getLong("id"), LocalDate.parse(it.getString("gueltigAb")), it.getLong("monatsbetragCent"))
            },
            einstellungen = o.optJSONObject("einstellungen")?.let { e -> e.keys().asSequence().associateWith { k -> e.getString(k) } }
                ?: emptyMap(),
        )
    } catch (e: SicherungsFehler) {
        throw e
    } catch (e: Exception) { // JSONException, DateTimeParseException, IllegalArgumentException (unbekannte Kasse/Status)
        throw SicherungsFehler("Datendatei ist beschädigt: ${e.message}", e)
    }

    private fun <T> arr(liste: List<T>, f: (T) -> JSONObject) = JSONArray().also { a -> liste.forEach { a.put(f(it)) } }

    private fun <T> list(o: JSONObject, key: String, f: (JSONObject) -> T): List<T> {
        val a = o.optJSONArray(key) ?: throw JSONException("Feld $key fehlt")
        return (0 until a.length()).map { f(a.getJSONObject(it)) }
    }

    private fun str(o: JSONObject, k: String): String? = if (o.isNull(k)) null else o.getString(k)
    private fun lng(o: JSONObject, k: String): Long? = if (o.isNull(k)) null else o.getLong(k)
}
