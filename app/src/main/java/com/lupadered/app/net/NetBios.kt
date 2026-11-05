package com.lupadered.app.net

/**
 * Consulta "NBSTAT" de NetBIOS (puerto UDP 137). Las PCs con Windows y los
 * equipos con Samba (NAS, algunos Linux) responden con su nombre de equipo,
 * por ejemplo "LAPTOP-JUAN". También sirve para saber que un equipo está vivo
 * aunque tenga el ping bloqueado.
 */
object NetBios {

    const val PORT = 137

    fun buildStatusQuery(id: Int): ByteArray {
        val q = ByteArray(50)
        q[0] = ((id ushr 8) and 255).toByte()
        q[1] = (id and 255).toByte()
        // flags = 0, qdcount = 1
        q[5] = 1
        // Nombre "*" codificado: 0x20 + "CK" + 30 x "A" + 0x00
        q[12] = 0x20
        q[13] = 'C'.code.toByte()
        q[14] = 'K'.code.toByte()
        for (i in 15 until 45) q[i] = 'A'.code.toByte()
        q[45] = 0
        q[46] = 0x00; q[47] = 0x21 // tipo NBSTAT
        q[48] = 0x00; q[49] = 0x01 // clase IN
        return q
    }

    /** Devuelve el nombre del equipo (sufijo 0x00, nombre único) o null. */
    fun parseStatusName(buf: ByteArray, length: Int): String? {
        try {
            if (length < 57) return null
            var pos = 12
            // Saltar el nombre del registro
            while (pos < length) {
                val l = buf[pos].toInt() and 255
                if (l == 0) { pos++; break }
                if ((l and 0xC0) == 0xC0) { pos += 2; break }
                pos += 1 + l
            }
            pos += 10 // tipo, clase, ttl, rdlength
            if (pos >= length) return null
            val count = buf[pos].toInt() and 255
            pos++
            var fallback: String? = null
            for (i in 0 until count) {
                if (pos + 18 > length) break
                val name = String(buf, pos, 15, Charsets.ISO_8859_1).trimEnd(' ', '\u0000')
                val suffix = buf[pos + 15].toInt() and 255
                val flags = ((buf[pos + 16].toInt() and 255) shl 8) or (buf[pos + 17].toInt() and 255)
                val isGroup = (flags and 0x8000) != 0
                if (suffix == 0x00 && !isGroup && name.isNotBlank()) return name
                if (fallback == null && !isGroup && name.isNotBlank()) fallback = name
                pos += 18
            }
            return fallback
        } catch (_: IndexOutOfBoundsException) {
            return null
        }
    }
}
