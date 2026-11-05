package com.lupadered.app.net

import java.io.IOException
import java.net.InetSocketAddress
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.SocketChannel

/**
 * Prueba conexiones TCP a muchos (IP, puerto) a la vez usando un único hilo
 * (NIO no bloqueante). Es la clave de la velocidad: en lugar de esperar una
 * conexión tras otra, lanzamos cientos en paralelo.
 *
 * Detalle importante para no "perder" dispositivos: si un puerto está CERRADO,
 * el equipo responde "conexión rechazada" casi al instante. Eso también prueba
 * que el equipo existe y está encendido, aunque no tenga ese servicio.
 */
class NioTcpProber(private val maxInFlight: Int = 384) {

    data class Target(val ip: String, val port: Int)

    enum class State {
        /** El puerto respondió: hay un servicio escuchando. */
        OPEN,
        /** El equipo respondió "rechazado": existe, pero ese puerto está cerrado. */
        REFUSED,
        /** Nadie respondió a tiempo. */
        TIMEOUT,
        /** La red dice que no hay nadie en esa IP. */
        UNREACHABLE,
    }

    data class Outcome(val target: Target, val state: State, val rttMs: Long) {
        val hostAlive: Boolean get() = state == State.OPEN || state == State.REFUSED
    }

    private class Pending(val target: Target, val startNs: Long, val channel: SocketChannel)

    /**
     * Ejecuta las pruebas. Es bloqueante: llámalo desde un hilo de fondo.
     * [onResult] se llama desde el mismo hilo por cada resultado.
     */
    fun run(
        targets: List<Target>,
        timeoutMs: Long,
        isCancelled: () -> Boolean,
        onResult: (Outcome) -> Unit,
    ) {
        if (targets.isEmpty()) return
        val timeoutNs = timeoutMs * 1_000_000L
        val queue = ArrayDeque(targets)
        val pending = HashMap<SelectionKey, Pending>()
        val selector = Selector.open()
        try {
            while ((queue.isNotEmpty() || pending.isNotEmpty()) && !isCancelled()) {
                // 1) Lanzar nuevas conexiones hasta el límite
                while (pending.size < maxInFlight && queue.isNotEmpty()) {
                    val t = queue.removeFirst()
                    var ch: SocketChannel? = null
                    val start = System.nanoTime()
                    try {
                        ch = SocketChannel.open()
                        ch.configureBlocking(false)
                        if (ch.connect(InetSocketAddress(t.ip, t.port))) {
                            closeQuietly(ch)
                            onResult(Outcome(t, State.OPEN, elapsedMs(start)))
                        } else {
                            val key = ch.register(selector, SelectionKey.OP_CONNECT)
                            pending[key] = Pending(t, start, ch)
                        }
                    } catch (e: IOException) {
                        closeQuietly(ch)
                        onResult(Outcome(t, classify(e), elapsedMs(start)))
                    }
                }

                // 2) Esperar eventos
                selector.select(25)
                val it = selector.selectedKeys().iterator()
                while (it.hasNext()) {
                    val key = it.next()
                    it.remove()
                    val p = pending[key] ?: continue
                    var state: State? = null
                    try {
                        if (p.channel.finishConnect()) state = State.OPEN
                    } catch (e: IOException) {
                        state = classify(e)
                    }
                    if (state != null) {
                        pending.remove(key)
                        key.cancel()
                        closeQuietly(p.channel)
                        onResult(Outcome(p.target, state, elapsedMs(p.startNs)))
                    }
                }

                // 3) Vencer los que tardan demasiado
                val now = System.nanoTime()
                val expired = pending.entries.filter { now - it.value.startNs > timeoutNs }
                for (e in expired) {
                    pending.remove(e.key)
                    e.key.cancel()
                    closeQuietly(e.value.channel)
                    onResult(Outcome(e.value.target, State.TIMEOUT, timeoutMs))
                }
            }
        } finally {
            for (p in pending.values) closeQuietly(p.channel)
            try {
                selector.close()
            } catch (_: IOException) {
            }
        }
    }

    private fun classify(e: IOException): State {
        val msg = (e.message ?: "").lowercase()
        return when {
            "refused" in msg -> State.REFUSED
            "unreachable" in msg || "no route" in msg -> State.UNREACHABLE
            else -> State.TIMEOUT
        }
    }

    private fun elapsedMs(startNs: Long) = (System.nanoTime() - startNs) / 1_000_000L

    private fun closeQuietly(ch: SocketChannel?) {
        try {
            ch?.close()
        } catch (_: IOException) {
        }
    }
}
