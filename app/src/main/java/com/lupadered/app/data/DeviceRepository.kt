package com.lupadered.app.data

import com.lupadered.app.net.sanitized

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

private class DbHelper(context: Context) : SQLiteOpenHelper(context, "lupa.db", null, 8) {
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
            """CREATE TABLE scan_devices (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                scan_id INTEGER NOT NULL,
                network_key TEXT NOT NULL,
                device_id INTEGER NOT NULL,
                ip TEXT NOT NULL
            )"""
        )
        db.execSQL("CREATE INDEX idx_scandev_scan ON scan_devices(scan_id)")
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
        migrateToV2(db)
        migrateToV3(db)
        migrateToV4(db)
        migrateToV5(db)
        migrateToV6(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Nunca borramos datos del usuario: solo agregamos columnas y tablas.
        if (oldVersion < 2) migrateToV2(db)
        if (oldVersion < 3) migrateToV3(db)
        if (oldVersion < 4) migrateToV4(db)
        if (oldVersion < 5) migrateToV5(db)
        if (oldVersion < 6) migrateToV6(db)
        if (oldVersion < 7) migrateToV7(db)
        if (oldVersion < 8) migrateToV8(db)
    }

    /** Versión 7: velocidad del WiFi (Mbps) en cada punto medido. */
    private fun migrateToV7(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE heat_points ADD COLUMN mbps INTEGER")
    }

    /** Versión 8: qué equipos estaban conectados en cada escaneo (historial). */
    private fun migrateToV8(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE scan_devices (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                scan_id INTEGER NOT NULL,
                network_key TEXT NOT NULL,
                device_id INTEGER NOT NULL,
                ip TEXT NOT NULL
            )"""
        )
        db.execSQL("CREATE INDEX idx_scandev_scan ON scan_devices(scan_id)")
    }

    /** Versión 6: ancho aproximado de cada piso, para pasar pasos y metros al plano. */
    private fun migrateToV6(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE heat_floors ADD COLUMN width_m REAL NOT NULL DEFAULT 12")
    }

    /** Versión 5: mapa de calor del WiFi (pisos y puntos medidos). */
    private fun migrateToV5(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE heat_floors (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                network_key TEXT NOT NULL,
                name TEXT NOT NULL,
                position INTEGER NOT NULL DEFAULT 0,
                router_x REAL,
                router_y REAL,
                created_at INTEGER NOT NULL
            )"""
        )
        db.execSQL("CREATE INDEX idx_heat_floors_net ON heat_floors(network_key)")
        db.execSQL(
            """CREATE TABLE heat_points (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                floor_id INTEGER NOT NULL,
                x REAL NOT NULL,
                y REAL NOT NULL,
                rssi INTEGER NOT NULL,
                ping_ms INTEGER,
                mbps INTEGER,
                at INTEGER NOT NULL
            )"""
        )
        db.execSQL("CREATE INDEX idx_heat_points_floor ON heat_points(floor_id)")
    }

    /** Versión 4 (Premium): alertas personalizadas por dispositivo. */
    private fun migrateToV4(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE alert_rules (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                device_id INTEGER NOT NULL,
                type TEXT NOT NULL,
                threshold_min INTEGER NOT NULL DEFAULT 15,
                enabled INTEGER NOT NULL DEFAULT 1,
                created_at INTEGER NOT NULL
            )"""
        )
        db.execSQL("CREATE INDEX idx_alert_rules_dev ON alert_rules(device_id)")
        db.execSQL(
            """CREATE TABLE alert_state (
                rule_id INTEGER PRIMARY KEY,
                last_fired_at INTEGER,
                last_state TEXT
            )"""
        )
    }

    /** Versión 3: historial de pruebas de velocidad. */
    private fun migrateToV3(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE speed_tests (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                network_key TEXT,
                at INTEGER NOT NULL,
                download_mbps REAL NOT NULL,
                upload_mbps REAL NOT NULL,
                ping_ms REAL,
                server TEXT
            )"""
        )
        db.execSQL("CREATE INDEX idx_speed_at ON speed_tests(at)")
    }

    /** Versión 2 (Premium): grupos e íconos, origen del escaneo y redes guardadas. */
    private fun migrateToV2(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE devices ADD COLUMN group_name TEXT")
        db.execSQL("ALTER TABLE devices ADD COLUMN custom_icon TEXT")
        db.execSQL("ALTER TABLE scans ADD COLUMN source TEXT NOT NULL DEFAULT 'MANUAL'")
        db.execSQL(
            """CREATE TABLE networks (
                network_key TEXT PRIMARY KEY,
                name TEXT,
                subnet TEXT NOT NULL,
                gateway TEXT,
                first_seen INTEGER NOT NULL,
                last_seen INTEGER NOT NULL
            )"""
        )
        // Las redes que ya se habían escaneado pasan a la lista de "Mis redes".
        db.execSQL(
            """INSERT OR IGNORE INTO networks (network_key, name, subnet, gateway, first_seen, last_seen)
               SELECT network_key, NULL, subnet,
                      NULLIF(substr(network_key, instr(network_key, '|') + 1), '-'),
                      MIN(started_at), MAX(started_at)
               FROM scans GROUP BY network_key"""
        )
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

    /** Equipos que estaban conectados en un escaneo concreto (con la IP que tenían entonces). */
    fun devicesInScan(scanId: Long): List<Pair<Device, String>> {
        val out = ArrayList<Pair<Device, String>>()
        db.rawQuery("SELECT device_id, ip FROM scan_devices WHERE scan_id = ? ORDER BY id", arrayOf(scanId.toString()))
            .use { c ->
                while (c.moveToNext()) {
                    val d = device(c.getLong(0)) ?: continue
                    out += d to (c.getString(1) ?: d.ip)
                }
            }
        return out
    }

    fun latestScan(networkKey: String): ScanRecord? = scans(networkKey, 1).firstOrNull()

    fun scans(networkKey: String, limit: Int = 50, since: Long = 0L): List<ScanRecord> =
        db.query(
            "scans", null, "network_key = ? AND started_at >= ?", arrayOf(networkKey, since.toString()),
            null, null, "started_at DESC", limit.toString(),
        )
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
                        source = c.str("source")?.let { n -> ScanSource.entries.firstOrNull { it.name == n } }
                            ?: ScanSource.MANUAL,
                    )
                }.toList()
            }

    fun events(networkKey: String, limit: Int = 200, since: Long = 0L): List<DeviceEvent> =
        db.query(
            "events", null, "network_key = ? AND at >= ?", arrayOf(networkKey, since.toString()),
            null, null, "at DESC, id DESC", limit.toString(),
        )
            .use { c -> generateSequence { if (c.moveToNext()) c.toEvent() else null }.toList() }

    fun eventsForDevice(deviceId: Long, limit: Int = 50, since: Long = 0L): List<DeviceEvent> =
        db.query(
            "events", null, "device_id = ? AND at >= ?", arrayOf(deviceId.toString(), since.toString()),
            null, null, "at DESC, id DESC", limit.toString(),
        )
            .use { c -> generateSequence { if (c.moveToNext()) c.toEvent() else null }.toList() }

    /** Cuántos eventos de la red son anteriores a [before] (para el aviso del historial gratis). */
    fun countEventsBefore(networkKey: String, before: Long): Int =
        count("SELECT COUNT(*) FROM events WHERE network_key = ? AND at < ?", networkKey, before.toString())

    fun countScansBefore(networkKey: String, before: Long): Int =
        count("SELECT COUNT(*) FROM scans WHERE network_key = ? AND started_at < ?", networkKey, before.toString())

    fun countDeviceEventsBefore(deviceId: Long, before: Long): Int =
        count("SELECT COUNT(*) FROM events WHERE device_id = ? AND at < ?", deviceId.toString(), before.toString())

    /** Todos los eventos de la red entre dos fechas, del más antiguo al más nuevo (para reportes). */
    fun eventsBetween(networkKey: String, from: Long, to: Long): List<DeviceEvent> =
        db.query(
            "events", null, "network_key = ? AND at >= ? AND at <= ?",
            arrayOf(networkKey, from.toString(), to.toString()), null, null, "at ASC, id ASC",
        ).use { c -> generateSequence { if (c.moveToNext()) c.toEvent() else null }.toList() }

    /** Nombres de grupo que el usuario ya usó en esta red. */
    fun groups(networkKey: String): List<String> =
        db.rawQuery(
            "SELECT DISTINCT group_name FROM devices WHERE network_key = ? AND group_name IS NOT NULL ORDER BY group_name",
            arrayOf(networkKey),
        ).use { c -> generateSequence { if (c.moveToNext()) c.getString(0) else null }.toList() }

    // -------------------------------------------------------------------- redes

    fun networks(): List<NetworkProfile> =
        db.rawQuery(
            """SELECT n.*,
                      (SELECT COUNT(*) FROM devices d WHERE d.network_key = n.network_key) AS device_count,
                      (SELECT s.found FROM scans s WHERE s.network_key = n.network_key
                       ORDER BY s.started_at DESC LIMIT 1) AS last_found
               FROM networks n ORDER BY n.last_seen DESC""",
            null,
        ).use { c -> generateSequence { if (c.moveToNext()) c.toNetwork() else null }.toList() }

    fun network(key: String): NetworkProfile? = networks().firstOrNull { it.key == key }

    fun renameNetwork(key: String, name: String?) {
        db.update("networks", ContentValues().apply { put("name", name?.trim()?.take(40)?.takeIf { it.isNotEmpty() }) },
            "network_key = ?", arrayOf(key))
    }

    /** Olvida una red guardada con todos sus dispositivos e historial. */
    fun deleteNetwork(key: String) {
        val database = db
        database.beginTransaction()
        try {
            database.delete("events", "network_key = ?", arrayOf(key))
            database.delete("scans", "network_key = ?", arrayOf(key))
            database.delete("scan_devices", "network_key = ?", arrayOf(key))
            database.delete("devices", "network_key = ?", arrayOf(key))
            database.delete("networks", "network_key = ?", arrayOf(key))
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    // ---------------------------------------------------------------- escritura

    fun updateAlias(id: Long, alias: String?) {
        db.update("devices", ContentValues().apply { put("alias", alias?.trim()?.take(40)?.takeIf { it.isNotEmpty() }) },
            "id = ?", arrayOf(id.toString()))
    }

    fun updateUserType(id: Long, type: DeviceType?) {
        db.update("devices", ContentValues().apply { put("user_type", type?.name) }, "id = ?", arrayOf(id.toString()))
    }

    fun updateGroup(id: Long, group: String?) {
        db.update("devices", ContentValues().apply { put("group_name", group?.trim()?.takeIf { it.isNotEmpty() }?.take(30)) },
            "id = ?", arrayOf(id.toString()))
    }

    fun updateIcon(id: Long, icon: String?) {
        db.update("devices", ContentValues().apply { put("custom_icon", icon) }, "id = ?", arrayOf(id.toString()))
    }

    fun setTrusted(id: Long, trusted: Boolean) {
        db.update("devices", ContentValues().apply { put("trusted", if (trusted) 1 else 0) }, "id = ?", arrayOf(id.toString()))
    }

    fun trustAll(networkKey: String) {
        db.update("devices", ContentValues().apply { put("trusted", 1) }, "network_key = ?", arrayOf(networkKey))
    }

    /**
     * Guarda los puertos abiertos de un análisis. Si se indica [scanned], se
     * conservan los puertos abiertos encontrados antes que no se revisaron esta vez
     * (p. ej. los de un análisis extendido cuando ahora se hizo uno común).
     */
    fun updatePorts(id: Long, ports: List<Int>, at: Long, scanned: Collection<Int>? = null) {
        val d = device(id) ?: return
        val checked = scanned?.toHashSet()
        val kept = if (checked == null) emptyList() else d.openPorts.filter { it !in checked }
        val all = (kept + ports).toSortedSet()
        val type = DeviceClassifier.classify(d.hints(all))
        db.update("devices", ContentValues().apply {
            put("open_ports", all.joinToString(","))
            put("ports_scanned_at", at)
            put("auto_type", type.name)
        }, "id = ?", arrayOf(id.toString()))
    }

    fun deleteDevice(id: Long) {
        db.delete("events", "device_id = ?", arrayOf(id.toString()))
        db.delete("scan_devices", "device_id = ?", arrayOf(id.toString()))
        db.delete("devices", "id = ?", arrayOf(id.toString()))
        db.delete("alert_rules", "device_id = ?", arrayOf(id.toString()))
    }

    // ------------------------------------------------------------- alertas (RF-49)

    fun alertRules(deviceId: Long): List<AlertRule> =
        db.query("alert_rules", null, "device_id = ?", arrayOf(deviceId.toString()), null, null, "id ASC")
            .use { c -> generateSequence { if (c.moveToNext()) c.toAlertRule() else null }.toList() }

    /** Todas las reglas habilitadas de la red (para evaluarlas en el escaneo en segundo plano). */
    fun enabledAlertRules(networkKey: String): List<AlertRule> =
        db.rawQuery(
            """SELECT r.* FROM alert_rules r JOIN devices d ON d.id = r.device_id
               WHERE d.network_key = ? AND r.enabled = 1""",
            arrayOf(networkKey),
        ).use { c -> generateSequence { if (c.moveToNext()) c.toAlertRule() else null }.toList() }

    fun saveAlertRule(rule: AlertRule): Long {
        val values = ContentValues().apply {
            put("device_id", rule.deviceId)
            put("type", rule.type.name)
            put("threshold_min", rule.thresholdMin)
            put("enabled", if (rule.enabled) 1 else 0)
            put("created_at", if (rule.createdAt > 0) rule.createdAt else System.currentTimeMillis())
        }
        return if (rule.id > 0) {
            db.update("alert_rules", values, "id = ?", arrayOf(rule.id.toString()))
            rule.id
        } else {
            db.insert("alert_rules", null, values)
        }
    }

    fun deleteAlertRule(id: Long) {
        db.delete("alert_rules", "id = ?", arrayOf(id.toString()))
        db.delete("alert_state", "rule_id = ?", arrayOf(id.toString()))
    }

    /** Último estado conocido de una regla, para no repetir la misma alerta en cada escaneo. */
    fun alertState(ruleId: Long): Pair<Long?, String?> =
        db.query("alert_state", null, "rule_id = ?", arrayOf(ruleId.toString()), null, null, null)
            .use { c -> if (c.moveToFirst()) c.longOrNull("last_fired_at") to c.str("last_state") else null to null }

    fun setAlertState(ruleId: Long, lastFiredAt: Long?, lastState: String?) {
        val values = ContentValues().apply {
            put("rule_id", ruleId)
            lastFiredAt?.let { put("last_fired_at", it) }
            put("last_state", lastState)
        }
        if (db.update("alert_state", values, "rule_id = ?", arrayOf(ruleId.toString())) == 0) {
            db.insert("alert_state", null, values)
        }
    }

    // -------------------------------------------------------------- mapa de calor

    fun heatFloors(networkKey: String): List<HeatFloor> =
        db.rawQuery(
            "SELECT id, name, position, router_x, router_y, width_m FROM heat_floors WHERE network_key = ? ORDER BY position, id",
            arrayOf(networkKey),
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(
                    HeatFloor(
                        id = c.getLong(0), name = c.getString(1), position = c.getInt(2),
                        routerX = if (c.isNull(3)) null else c.getFloat(3),
                        routerY = if (c.isNull(4)) null else c.getFloat(4),
                        widthM = c.getFloat(5),
                    )
                )
            }
        }

    fun addHeatFloor(networkKey: String, name: String): Long {
        val next = db.rawQuery(
            "SELECT COALESCE(MAX(position), -1) + 1 FROM heat_floors WHERE network_key = ?", arrayOf(networkKey),
        ).use { if (it.moveToFirst()) it.getInt(0) else 0 }
        return db.insert("heat_floors", null, ContentValues().apply {
            put("network_key", networkKey)
            put("name", name)
            put("position", next)
            put("created_at", System.currentTimeMillis())
        })
    }

    fun heatFloor(id: Long): HeatFloor? =
        db.rawQuery(
            "SELECT id, name, position, router_x, router_y, width_m FROM heat_floors WHERE id = ?", arrayOf(id.toString()),
        ).use { c ->
            if (!c.moveToFirst()) null else HeatFloor(
                id = c.getLong(0), name = c.getString(1), position = c.getInt(2),
                routerX = if (c.isNull(3)) null else c.getFloat(3),
                routerY = if (c.isNull(4)) null else c.getFloat(4),
                widthM = c.getFloat(5),
            )
        }

    fun setHeatFloorWidth(id: Long, widthM: Float) {
        db.update("heat_floors", ContentValues().apply { put("width_m", widthM) }, "id = ?", arrayOf(id.toString()))
    }

    fun renameHeatFloor(id: Long, name: String) {
        db.update("heat_floors", ContentValues().apply { put("name", name.trim().take(30)) }, "id = ?", arrayOf(id.toString()))
    }

    fun setHeatRouter(id: Long, x: Float?, y: Float?) {
        db.update("heat_floors", ContentValues().apply {
            if (x == null || y == null) { putNull("router_x"); putNull("router_y") } else { put("router_x", x); put("router_y", y) }
        }, "id = ?", arrayOf(id.toString()))
    }

    fun deleteHeatFloor(id: Long) {
        db.delete("heat_points", "floor_id = ?", arrayOf(id.toString()))
        db.delete("heat_floors", "id = ?", arrayOf(id.toString()))
    }

    fun heatPoints(floorId: Long): List<HeatPoint> =
        db.rawQuery(
            "SELECT id, x, y, rssi, ping_ms, mbps, at FROM heat_points WHERE floor_id = ? ORDER BY at",
            arrayOf(floorId.toString()),
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(
                    HeatPoint(
                        id = c.getLong(0), x = c.getFloat(1), y = c.getFloat(2), rssi = c.getInt(3),
                        pingMs = if (c.isNull(4)) null else c.getInt(4),
                        mbps = if (c.isNull(5)) null else c.getInt(5),
                        at = c.getLong(6),
                    )
                )
            }
        }

    fun addHeatPoint(floorId: Long, x: Float, y: Float, rssi: Int, pingMs: Int?, mbps: Int? = null): Long =
        db.insert("heat_points", null, ContentValues().apply {
            put("floor_id", floorId)
            put("x", x)
            put("y", y)
            put("rssi", rssi)
            pingMs?.let { put("ping_ms", it) }
            mbps?.let { put("mbps", it) }
            put("at", System.currentTimeMillis())
        })

    /** Corrige dónde quedó un punto (la realidad aumentada ajusta el recorrido al terminar). */
    fun moveHeatPoint(id: Long, x: Float, y: Float) {
        db.update("heat_points", ContentValues().apply {
            put("x", x)
            put("y", y)
        }, "id = ?", arrayOf(id.toString()))
    }

    fun deleteHeatPoint(id: Long) {
        db.delete("heat_points", "id = ?", arrayOf(id.toString()))
    }

    fun clearHeatPoints(floorId: Long) {
        db.delete("heat_points", "floor_id = ?", arrayOf(floorId.toString()))
    }

    // -------------------------------------------------------------- velocidad

    fun insertSpeedTest(t: SpeedTestRecord): Long =
        db.insert("speed_tests", null, ContentValues().apply {
            put("network_key", t.networkKey)
            put("at", t.at)
            put("download_mbps", t.downloadMbps)
            put("upload_mbps", t.uploadMbps)
            t.pingMs?.let { put("ping_ms", it) }
            put("server", t.server)
        })

    fun speedTests(limit: Int = 100): List<SpeedTestRecord> =
        db.query("speed_tests", null, null, null, null, null, "at DESC", limit.toString()).use { c ->
            generateSequence {
                if (!c.moveToNext()) null else SpeedTestRecord(
                    id = c.long("id"),
                    networkKey = c.str("network_key"),
                    at = c.long("at"),
                    downloadMbps = c.getDouble(c.getColumnIndexOrThrow("download_mbps")),
                    uploadMbps = c.getDouble(c.getColumnIndexOrThrow("upload_mbps")),
                    pingMs = c.getColumnIndexOrThrow("ping_ms").let { i -> if (c.isNull(i)) null else c.getDouble(i) },
                    server = c.str("server"),
                )
            }.toList()
        }

    /** Cuántas pruebas se hicieron desde [since] (para el límite diario de la versión gratis). */
    fun countSpeedTestsSince(since: Long): Int =
        count("SELECT COUNT(*) FROM speed_tests WHERE at >= ?", since.toString())

    fun clearAll() {
        db.delete("speed_tests", null, null)
        db.delete("networks", null, null)
        db.delete("events", null, null)
        db.delete("scans", null, null)
        db.delete("scan_devices", null, null)
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
        source: ScanSource = ScanSource.MANUAL,
        gateway: String? = null,
        ssid: String? = null,
    ): ScanSummary {
        // Los textos vienen de otros equipos: se limpian antes de guardarlos
        @Suppress("NAME_SHADOWING")
        val hosts = hosts.map { it.sanitized() }
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
                put("source", source.name)
            })

            // Registra la red en "Mis redes" (o actualiza cuándo se vio por última vez)
            val updated = database.update("networks", ContentValues().apply {
                put("last_seen", now)
                put("subnet", subnet)
            }, "network_key = ?", arrayOf(networkKey))
            // Si conocemos el nombre de la red (SSID) y el usuario no le ha puesto uno,
            // lo guardamos como nombre automático. No pisamos un nombre que puso el usuario.
            if (!ssid.isNullOrBlank()) {
                database.execSQL(
                    "UPDATE networks SET name = ? WHERE network_key = ? AND (name IS NULL OR name = '')",
                    arrayOf(ssid.take(40), networkKey),
                )
            }
            if (updated == 0) {
                database.insert("networks", null, ContentValues().apply {
                    put("network_key", networkKey)
                    put("name", ssid?.takeIf { it.isNotBlank() }?.take(40))
                    put("subnet", subnet)
                    put("gateway", gateway)
                    put("first_seen", now)
                    put("last_seen", now)
                })
            }

            val matched = HashSet<Long>()
            // Qué equipos estaban conectados en este escaneo (para el historial)
            val presence = ArrayList<Pair<Long, String>>()
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
                    presence += candidate.id to h.ip
                    // El escaneo de red es rápido y puede perder un puerto lento: aquí solo sumamos.
                    // Los puertos se quitan únicamente con "Analizar puertos", que los revisa con calma.
                    val ports = (candidate.openPorts + h.openPorts).toSortedSet()
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
                    presence += id to h.ip
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

            for ((deviceId, ip) in presence) {
                database.insert("scan_devices", null, ContentValues().apply {
                    put("scan_id", scanId)
                    put("network_key", networkKey)
                    put("device_id", deviceId)
                    put("ip", ip)
                })
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

    private fun count(sql: String, vararg args: String): Int =
        db.rawQuery(sql, arrayOf(*args)).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

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
        groupName = str("group_name"),
        customIcon = str("custom_icon"),
    )

    private fun Cursor.toNetwork() = NetworkProfile(
        key = str("network_key")!!,
        name = str("name"),
        subnet = str("subnet")!!,
        gateway = str("gateway"),
        firstSeen = long("first_seen"),
        lastSeen = long("last_seen"),
        deviceCount = long("device_count").toInt(),
        lastFound = longOrNull("last_found")?.toInt(),
    )

    private fun Cursor.toAlertRule() = AlertRule(
        id = long("id"),
        deviceId = long("device_id"),
        type = AlertType.entries.firstOrNull { it.name == str("type") } ?: AlertType.OFFLINE_FOR,
        thresholdMin = long("threshold_min").toInt(),
        enabled = long("enabled") != 0L,
        createdAt = long("created_at"),
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
