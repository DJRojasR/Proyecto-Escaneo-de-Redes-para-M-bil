package com.lupadered.app.net

import java.io.ByteArrayOutputStream

/**
 * Lector y escritor mínimo de mensajes DNS. Sirve tanto para DNS normal
 * (preguntar al router el nombre de una IP) como para mDNS/Bonjour, que usa
 * exactamente el mismo formato.
 */
object DnsMessage {

    const val TYPE_A = 1
    const val TYPE_PTR = 12
    const val TYPE_TXT = 16
    const val TYPE_AAAA = 28
    const val TYPE_SRV = 33
    const val TYPE_ANY = 255
    private const val CLASS_IN = 1
    private const val QU_BIT = 0x8000

    data class Question(val name: String, val type: Int)

    data class Record(
        val name: String,
        val type: Int,
        /** PTR/SRV: nombre destino. A: IP. TXT: textos unidos por ';'. */
        val data: String,
        val port: Int = 0,
    )

    fun buildQuery(id: Int, questions: List<Question>, unicastResponse: Boolean = false): ByteArray {
        val out = ByteArrayOutputStream()
        writeShort(out, id and 0xFFFF)
        writeShort(out, 0x0100) // recursión deseada (ignorada por mDNS)
        writeShort(out, questions.size)
        writeShort(out, 0)
        writeShort(out, 0)
        writeShort(out, 0)
        for (q in questions) {
            writeName(out, q.name)
            writeShort(out, q.type)
            writeShort(out, if (unicastResponse) CLASS_IN or QU_BIT else CLASS_IN)
        }
        return out.toByteArray()
    }

    /** Devuelve todos los registros (respuestas, autoridad y adicionales). */
    fun parse(buf: ByteArray, length: Int = buf.size): List<Record> {
        if (length < 12) return emptyList()
        val qd = u16(buf, 4)
        val total = u16(buf, 6) + u16(buf, 8) + u16(buf, 10)
        var pos = 12
        val records = ArrayList<Record>()
        try {
            repeat(qd) {
                pos = readName(buf, length, pos).second
                pos += 4
            }
            repeat(total) {
                if (pos >= length) return records
                val (name, afterName) = readName(buf, length, pos)
                pos = afterName
                if (pos + 10 > length) return records
                val type = u16(buf, pos)
                val rdLen = u16(buf, pos + 8)
                val rdStart = pos + 10
                val rdEnd = rdStart + rdLen
                if (rdEnd > length) return records
                when (type) {
                    TYPE_A -> if (rdLen == 4) {
                        val ip = "${buf[rdStart].toInt() and 255}.${buf[rdStart + 1].toInt() and 255}." +
                            "${buf[rdStart + 2].toInt() and 255}.${buf[rdStart + 3].toInt() and 255}"
                        records += Record(name, type, ip)
                    }
                    TYPE_PTR -> records += Record(name, type, readName(buf, length, rdStart).first)
                    TYPE_SRV -> if (rdLen >= 7) {
                        val port = u16(buf, rdStart + 4)
                        records += Record(name, type, readName(buf, length, rdStart + 6).first, port)
                    }
                    TYPE_TXT -> {
                        val parts = ArrayList<String>()
                        var p = rdStart
                        while (p < rdEnd) {
                            val l = buf[p].toInt() and 255
                            p++
                            if (l == 0 || p + l > rdEnd) break
                            parts += String(buf, p, l, Charsets.UTF_8)
                            p += l
                        }
                        records += Record(name, type, parts.joinToString(";"))
                    }
                    else -> Unit
                }
                pos = rdEnd
            }
        } catch (_: IndexOutOfBoundsException) {
            // Paquete mal formado: devolvemos lo que se pudo leer.
        } catch (_: IllegalStateException) {
        }
        return records
    }

    /** Lee un nombre DNS (con compresión). Devuelve el nombre y la posición siguiente. */
    fun readName(buf: ByteArray, length: Int, start: Int): Pair<String, Int> {
        val labels = ArrayList<String>()
        var pos = start
        var next = -1
        var jumps = 0
        while (true) {
            check(pos < length) { "nombre fuera de rango" }
            val len = buf[pos].toInt() and 255
            when {
                len == 0 -> {
                    pos++
                    break
                }
                (len and 0xC0) == 0xC0 -> {
                    check(pos + 1 < length) { "puntero cortado" }
                    val ptr = ((len and 0x3F) shl 8) or (buf[pos + 1].toInt() and 255)
                    if (next < 0) next = pos + 2
                    check(++jumps <= 32) { "demasiados saltos" }
                    pos = ptr
                }
                else -> {
                    check(pos + 1 + len <= length) { "etiqueta cortada" }
                    labels += String(buf, pos + 1, len, Charsets.UTF_8)
                    pos += 1 + len
                }
            }
        }
        return Pair(labels.joinToString("."), if (next >= 0) next else pos)
    }

    private fun writeName(out: ByteArrayOutputStream, name: String) {
        for (label in name.trimEnd('.').split('.')) {
            if (label.isEmpty()) continue
            val bytes = label.toByteArray(Charsets.UTF_8)
            out.write(bytes.size.coerceAtMost(63))
            out.write(bytes, 0, bytes.size.coerceAtMost(63))
        }
        out.write(0)
    }

    private fun writeShort(out: ByteArrayOutputStream, v: Int) {
        out.write((v ushr 8) and 255)
        out.write(v and 255)
    }

    private fun u16(buf: ByteArray, pos: Int): Int =
        ((buf[pos].toInt() and 255) shl 8) or (buf[pos + 1].toInt() and 255)
}
