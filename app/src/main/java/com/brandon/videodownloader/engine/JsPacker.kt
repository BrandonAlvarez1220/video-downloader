package com.brandon.videodownloader.engine

/**
 * Desempaquetador del "Packer" de Dean Edwards (`eval(function(p,a,c,k,e,d){…}('…',36,123,'a|b|c'.split('|'),0,{}))`).
 *
 * Muchos sitios de streaming ofuscan el JavaScript del reproductor con este truco: el HTML que
 * recibimos no contiene la URL del video en texto plano, sino dentro de una cadena comprimida.
 * Con esto recuperamos el JavaScript "legible" para poder sacar los enlaces.
 *
 * Es una traducción fiel del algoritmo original (el mismo que usan webs como "unpacker").
 */
internal object JsPacker {

    // Captura el bloque `eval(function(p,a,c,k,e,d){…}('PAYLOAD', RADIX, COUNT, 'SIMBOLOS'.split('|'),0,{}))`
    private val packed = Regex(
        """eval\(function\(p,a,c,k,e,[rd]\)\{.*?\}\s*\('(.*?)',\s*(\d+),\s*(\d+),\s*'(.*?)'\s*\.split\('\|'\)""",
        RegexOption.DOT_MATCHES_ALL,
    )

    /** Desempaqueta cuantas capas encuentre (a veces hay packer dentro de packer). */
    fun unpack(source: String): String {
        var out = source
        var guard = 0
        while (guard++ < 6) {
            val m = packed.find(out) ?: break
            val payload = m.groupValues[1]
            val radix = m.groupValues[2].toIntOrNull() ?: break
            val count = m.groupValues[3].toIntOrNull() ?: break
            val symbols = m.groupValues[4].split('|')
            if (radix !in 2..36) break
            val decoded = decode(payload, radix, count, symbols)
            out = out.replaceRange(m.range, decoded)
        }
        return out
    }

    /** Sustituye cada token base-N por su palabra en la tabla (de la última a la primera). */
    private fun decode(payload: String, radix: Int, count: Int, symbols: List<String>): String {
        var p = payload
        for (i in count - 1 downTo 0) {
            val word = symbols.getOrNull(i)?.takeIf { it.isNotEmpty() } ?: continue
            val token = i.toString(radix)
            p = Regex("""\b${Regex.escape(token)}\b""").replace(p, Regex.escapeReplacement(word))
        }
        return p
    }

    /** Decodifica las cadenas base64 típicas (`atob('…')`) que a veces envuelven la URL del video. */
    fun decodeAtobBlocks(source: String): String {
        val atob = Regex("""atob\(\s*['"]([A-Za-z0-9+/=]+)['"]\s*\)""")
        return atob.replace(source) { m ->
            val decoded = runCatching {
                String(android.util.Base64.decode(m.groupValues[1], android.util.Base64.DEFAULT), Charsets.UTF_8)
            }.getOrNull() ?: return@replace m.value
            "'" + decoded.replace("'", "\\'") + "'"
        }
    }
}
