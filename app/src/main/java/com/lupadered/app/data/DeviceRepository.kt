package com.lupadered.app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.lupadered.app.knowledge.DeviceClassifier
import com.lupadered.app.knowledge.DeviceHints
import com.lupadered.app.knowledge.DeviceType
import com.lupadered.app.net.DiscoveryMethod
import com.lupadered.app.net.HostSnapshot
import com.lupadered.app.net.NetworkScanner

private class DbHelper(context: Context) : SQLiteOpenHelper(context, "lupa.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE devices (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                network_key TEXT NOT NULL,
                ip TEXT NOT NULL,
                name_key TEXT,
                alias TEXT,
                user_type TEXT,
                auto_type TEXT NOT NULL,
                name TEXT,
                vendor TEXT,
                model TEXT,
                server TEXT,
                services TEXT NOT NULL DEFAULT '',
                open_ports TEXT NOT NULL DEFAULT '',
                methods TEXT NOT NULL DEFAULT '',
                ports_scanned_at INTEGER,
                first_seen INTEGER NOT NULL,
                last_seen INTEGER NOT NULL,
                last_scan_id INTEGER NOT NULL,
                latency_ms INTEGER,
                trusted INTEGER NOT NULL DEFAULT 0,
                is_gateway INTEGER NOT NULL DEFAULT 0,
                is_self INTEGER NOT NULL DEFAULT 0
            )"""
        )
        db.execSQL("CREATE INDEX idx_devices_net ON devices(network_key)")
        db.execSQL(
            """CREATE TABLE scans (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                network_key TEXT NOT NULL,
                subnet TEXT NOT NULL,
                started_at INTEGER NOT NULL,
                duration_ms INTEGER NOT NULL,
                found INTEGER NOT NULL,
                new_count INTEGER NOT NULL
            )"""
        )
        db.execSQL("CREATE INDEX idx_scans_net ON scans(network_key, started_at)")
        db.execSQL(
            """CREATE TABLE events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                network_key TEXT NOT NULL,
                device_id INTEGER NOT NULL,
                type TEXT NOT NULL,
                ip TEXT NOT NULL,
                detail TEXT,
                at INTEGER NOT NULL
            )"""
        )
        db.execSQL("CREATE INDEX idx_events_net ON events(network_key, at)")
        db.execSQL("CREATE INDEX idx_events_dev ON events(device_id, at)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Versión 1: aún no hay migraciones. Cuando cambies el esquema, súbelo
        // a la versión 2 y agrega aquí los ALTER TABLE (nunca borres datos del usuario).
    }
}

/**
 * Guarda el inventario de dispositivos, el historial de escaneos y los eventos
 * (aparecer, desaparecer, cambiar de IP). Todas las funciones son bloqueantes:
 * llámalas desde Dispatchers.IO.
 */
class DeviceRepository(context: Context) {

    private val helper = DbHelper(context.applicationContext)
    private val db: SQLiteDatabase get() = helper.writableDatabase

    // ------------------------------------------------------------------ lectura

    fun devices(networkKey: String): List<Device> =
        db.query("devices", null, "network_key = ?", arrayOf(networkKey), null, null, null)
            .use { c -> generateSequence { if (c.moveToNext()) c.toDevice() else null }.toList() }

    fun device(id: Long): Device? =
        db.query("devices", null, "id = ?", arrayOf(id.toString()), null, null, null)
            .use { c -> if (c.moveToFirst()) c.toDevice() else null }

    fun latestScan(networkKey: String): ScanRecord? = scans(networkKey, 1).firstOrNull()

    fun scans(networkKey: String, limit: Int = 50): List<ScanRecord> =
        db.query("scans", null, "network_key = ?", arrayOf(networkKey), null, null, "started_at DESC", limit.toString())
            .use { c ->
                generateSequence {
                    if (!c.moveToNext()) null else ScanRecord(
                        id = c.long("id"),
                        networkKey = c.str("network_key")!!,
                        subnet = c.str("subnet")!!,
                        startedAt = c.long("started_at"),
                        durationMs = c.long("duration_ms"),
                        found = c.long("found").toInt(),
                        newCount = c.long("new_count").toInt(),
                    )
                }.toList()
            }

    fun events(networkKey: String, limit: Int = 200): List<DeviceEvent> =
        db.query("events", null, "network_key = ?", arrayOf(networkKey), null, null, "at DESC, id DESC", limit.toString())
            .use { c -> generateSequence { if (c.moveToNext()) c.toEvent() else null }.toList() }

    fun eventsForDevice(deviceId: Long, limit: Int = 50): List<DeviceEvent> =
        db.query("events", null, "device_id = ?", arrayOf(deviceId.toString()), null, null, "at DESC, id DESC", limit.toString())
            .use { c -> generateSequence { if (c.moveToNext()) c.toEvent() else null }.toList() }

    // ---------------------------------------------------------------- escritura

    fun updateAlias(id: Long, alias: String?) {
        db.update("devices", ContentValues().apply { put("alias", alias?.trim()?.takeIf { it.isNotEmpty() }) },
            "id = ?", arrayOf(id.toString()))
    }

    fun updateUserType(id: Long, type: DeviceType?) {
        db.update("devices", ContentValues().apply { put("user_type", type?.name) }, "id = ?", arrayOf(id.toString()))
    }

    fun setTrusted(id: Long, trusted: Boolean) {
        db.update("devices", ContentValues().apply { put("trusted", if (trusted) 1 else 0) }, "id = ?", arrayOf(id.toString()))
    }

    fun trustAll(networkKey: String) {
        db.update("devices", ContentValues().apply { put("trusted", 1) }, "network_key = ?", arrayOf(networkKey))
    }

    fun updatePorts(id: Long, ports: List<Int>, at: Long) {
        val d = device(id) ?: return
        val type = DeviceClassifier.classify(d.hints(ports.toSet()))
        db.update("devices", ContentValues().apply {
            put("open_ports", ports.sorted().joinToString(","))
            put("ports_scanned_at", at)
            put("auto_type", type.name)
        }, "id = ?", arrayOf(id.toString()))
    }

    fun deleteDevice(id: Long) {
        db.delete("events", "device_id = ?", arrayOf(id.toString()))
        db.delete("devices", "id = ?", arrayOf(id.toString()))
    }

    fun clearAll() {
        db.delete("events", null, null)
        db.delete("scans", null, null)
        db.delete("devices", null, null)
    }

    /**
     * Guarda el resultado de un escaneo y calcula los eventos. Reconoce a un
     * equipo aunque haya cambiado de IP si conserva su nombre en la red.
     */
    fun applyScan(
        networkKey: String,
        subnet: String,
        hosts: List<HostSnapshot>,
        startedAt: Long,
        durationMs: Long,
        selfModel: String,
    ): ScanSummary {
        val database = db
        database.beginTransaction()
        try {
            val existing = devices(networkKey)
            val prevScanId = latestScan(networkKey)?.id
            val firstScan = prevScanId == null
            val now = System.currentTimeMillis()

            val scanId = database.insert("scans", null, ContentValues().apply {
                put("network_key", networkKey)
                put("subnet", subnet)
                put("started_at", startedAt)
                put("duration_ms", durationMs)
                put("found", hosts.size)
                put("new_count", 0)
            })

            val matched = HashSet<Long>()
            val newDevices = ArrayList<Device>()

            for (h in hosts) {
                val key = when {
                    h.isSelf -> SELF_KEY
                    else -> h.nameKey()
                }
                val candidate = key?.let { k -> existing.firstOrNull { it.nameKey == k && it.id !in matched } }
                    ?: existing.firstOrNull {
                        it.ip == h.ip && it.id !in matched &&
                            (it.nameKey == null || key == null || it.nameKey == key)
                    }

                val name = if (h.isSelf) selfModel else h.bestName()
                val values = ContentValues().apply {
                    put("network_key", networkKey)
                    put("ip", h.ip)
                    put("last_seen", now)
                    put("last_scan_id", scanId)
                    put("latency_ms", h.latencyMs)
                    put("methods", h.methods.joinToString(",") { it.name })
                    put("is_gateway", if (h.isGateway) 1 else 0)
                    put("is_self", if (h.isSelf) 1 else 0)
                }

                if (candidate != null) {
                    matched += candidate.id
                    // Conservamos los puertos encontrados en un análisis a fondo
                    val keptPorts = candidate.openPorts.filter { it !in NetworkScanner.DISCOVERY_PORTS }
                    val ports = (keptPorts + h.openPorts).toSortedSet()
                    val services = candidate.services + h.mdnsServices
                    val merged = HostMerge(
                        name = name ?: candidate.name,
                        vendor = h.manufacturer ?: candidate.vendor,
                        model = h.model ?: candidate.model,
                        server = h.ssdpServer ?: candidate.server,
                    )
                    values.apply {
                        put("name_key", key ?: candidate.nameKey)
                        put("name", merged.name)
                        put("vendor", merged.vendor)
                        put("model", merged.model)
                        put("server", merged.server)
                        put("services", services.joinToString(","))
                        put("open_ports", ports.joinToString(","))
                        put("auto_type", classify(h, merged, services, ports).name)
                    }
                    database.update("devices", values, "id = ?", arrayOf(candidate.id.toString()))

                    if (candidate.ip != h.ip) {
                        addEvent(database, networkKey, candidate.id, EventType.IP_CHANGED, h.ip, candidate.ip, now)
                    }
                    if (prevScanId != null && candidate.lastScanId != prevScanId) {
                        addEvent(database, networkKey, candidate.id, EventType.BACK, h.ip, null, now)
                    }
                } else {
                    val merged = HostMerge(name, h.manufacturer, h.model, h.ssdpServer)
                    values.apply {
                        put("name_key", key)
                        put("name", merged.name)
                        put("vendor", merged.vendor)
                        put("model", merged.model)
                        put("server", merged.server)
                        put("services", h.mdnsServices.joinToString(","))
                        put("open_ports", h.openPorts.sorted().joinToString(","))
                        put("auto_type", classify(h, merged, h.mdnsServices, h.openPorts).name)
                        put("first_seen", now)
                        // Tu teléfono y, en el primer escaneo, todo lo encontrado se consideran "conocidos"
                        put("trusted", if (h.isSelf || firstScan) 1 else 0)
                    }
                    val id = database.insert("devices", null, values)
                    matched += id
                    if (!firstScan) {
                        addEvent(database, networkKey, id, EventType.NEW, h.ip, null, now)
                        device(id)?.let { newDevices += it }
                    }
                }
            }

            if (prevScanId != null) {
                for (d in existing) {
                    if (d.id !in matched && d.lastScanId == prevScanId) {
                        addEvent(database, networkKey, d.id, EventType.LEFT, d.ip, null, now)
                    }
                }
            }

            database.update("scans", ContentValues().apply { put("new_count", newDevices.size) },
                "id = ?", arrayOf(scanId.toString()))
            database.setTransactionSuccessful()
            return ScanSummary(scanId, hosts.size, newDevices, firstScan)
        } finally {
            database.endTransaction()
        }
    }

    // ---------------------------------------------------------------- utilidades

    private class HostMerge(val name: String?, val vendor: String?, val model: String?, val server: String?)

    private fun classify(h: HostSnapshot, m: HostMerge, services: Set<String>, ports: Set<Int>): DeviceType =
        DeviceClassifier.classify(
            DeviceHints(
                names = listOfNotNull(m.name, h.mdnsHostname, h.netbiosName, h.dnsName) + h.mdnsInstances,
                services = services,
                openPorts = ports,
                server = m.server,
                manufacturer = m.vendor,
                model = m.model,
                isGateway = h.isGateway,
                isSelf = h.isSelf,
            )
        )

    private fun Device.hints(ports: Set<Int>) = DeviceHints(
        names = listOfNotNull(name, nameKey),
        services = services,
        openPorts = ports,
        server = server,
        manufacturer = vendor,
        model = model,
        isGateway = isGateway,
        isSelf = isSelf,
    )

    private fun addEvent(
        database: SQLiteDatabase, networkKey: String, deviceId: Long,
        type: EventType, ip: String, detail: String?, at: Long,
    ) {
        database.insert("events", null, ContentValues().apply {
            put("network_key", networkKey)
            put("device_id", deviceId)
            put("type", type.name)
            put("ip", ip)
            put("detail", detail)
            put("at", at)
        })
    }

    private fun Cursor.str(col: String): String? = getColumnIndexOrThrow(col).let { if (isNull(it)) null else getString(it) }
    private fun Cursor.long(col: String): Long = getLong(getColumnIndexOrThrow(col))
    private fun Cursor.longOrNull(col: String): Long? = getColumnIndexOrThrow(col).let { if (isNull(it)) null else getLong(it) }

    private fun Cursor.toDevice() = Device(
        id = long("id"),
        networkKey = str("network_key")!!,
        ip = str("ip")!!,
        nameKey = str("name_key"),
        alias = str("alias"),
        userType = str("user_type")?.let { n -> DeviceType.entries.firstOrNull { it.name == n } },
        autoType = str("auto_type")?.let { n -> DeviceType.entries.firstOrNull { it.name == n } } ?: DeviceType.UNKNOWN,
        name = str("name"),
        vendor = str("vendor"),
        model = str("model"),
        server = str("server"),
        services = str("services").orEmpty().split(',').filter { it.isNotBlank() }.toSet(),
        openPorts = str("open_ports").orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() }.toSet(),
        methods = str("methods").orEmpty().split(',')
            .mapNotNull { n -> DiscoveryMethod.entries.firstOrNull { it.name == n } }.toSet(),
        portsScannedAt = longOrNull("ports_scanned_at"),
        firstSeen = long("first_seen"),
        lastSeen = long("last_seen"),
        lastScanId = long("last_scan_id"),
        latencyMs = longOrNull("latency_ms"),
        trusted = long("trusted") != 0L,
        isGateway = long("is_gateway") != 0L,
        isSelf = long("is_self") != 0L,
    )

    private fun Cursor.toEvent() = DeviceEvent(
        id = long("id"),
        networkKey = str("network_key")!!,
        deviceId = long("device_id"),
        type = EventType.entries.firstOrNull { it.name == str("type") } ?: EventType.NEW,
        ip = str("ip")!!,
        detail = str("detail"),
        at = long("at"),
    )

    companion object {
        private const val SELF_KEY = "__este_telefono__"
    }
}
