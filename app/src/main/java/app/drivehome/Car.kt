package app.drivehome

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale
import java.util.UUID

/** Ouvrants de la voiture. */
data class Doors(
    val fl: Boolean = false,
    val fr: Boolean = false,
    val rl: Boolean = false,
    val rr: Boolean = false,
    val trunk: Boolean = false,
    val hood: Boolean = false,
) {
    fun anyOpen() = fl || fr || rl || rr || trunk || hood

    fun openNames(): List<String> = listOfNotNull(
        "porte avant gauche".takeIf { fl },
        "porte avant droite".takeIf { fr },
        "porte arrière gauche".takeIf { rl },
        "porte arrière droite".takeIf { rr },
        "coffre".takeIf { trunk },
        "capot".takeIf { hood },
    )
}

data class CarColor(val name: String, val color: Color)

val CAR_COLORS = listOf(
    CarColor("Blanc pur", Color(0xFFF4F5F7)),
    CarColor("Blanc nacré", Color(0xFFEDEAE2)),
    CarColor("Argent", Color(0xFFB8BCC2)),
    CarColor("Gris", Color(0xFF6B7078)),
    CarColor("Noir", Color(0xFF1E2024)),
    CarColor("Bleu", Color(0xFF1F4E8C)),
    CarColor("Rouge", Color(0xFFA4161A)),
    CarColor("Vert", Color(0xFF2F5D46)),
)

/**
 * État de la voiture : ouvrants + données moteur.
 *
 * Sources possibles :
 *  - un boîtier OBD2 Bluetooth (ELM327) pour le moteur ;
 *  - une diffusion Android « app.drivehome.CAR_STATUS » envoyée par l'appli CAN
 *    de l'autoradio, Tasker, MacroDroid… pour les portes et le coffre ;
 *  - le mode test (boutons à l'écran).
 */
class CarManager(context: Context, private val prefs: Prefs) {
    private val appContext = context.applicationContext
    val obd = ObdClient(appContext)

    var doors by mutableStateOf(Doors())
        private set
    var doorsSource by mutableStateOf("aucune")
        private set
    var lastDoorUpdate by mutableLongStateOf(0L)
        private set

    // Données reçues par diffusion (souvent fournies par le boîtier CAN de l'autoradio)
    var extFuel by mutableStateOf<Int?>(null)
        private set
    var extRange by mutableStateOf<Int?>(null)
        private set
    var extOutTemp by mutableStateOf<Double?>(null)
        private set

    /** Message d'alerte à afficher (ouverture d'un ouvrant). */
    var alert by mutableStateOf<String?>(null)
        private set
    var alertAt by mutableLongStateOf(0L)
        private set

    /** Annonce vocale (branchée par AppState). */
    var onSpeak: ((String) -> Unit)? = null

    private var registered = false
    private var lastMovingWarn = 0L

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            if (i != null) applyIntent(i)
        }
    }

    fun start() {
        if (!registered) {
            try {
                ContextCompat.registerReceiver(
                    appContext,
                    receiver,
                    IntentFilter(ACTION_STATUS),
                    ContextCompat.RECEIVER_EXPORTED,
                )
                registered = true
            } catch (e: Exception) {
                // ignoré
            }
        }
        val addr = prefs.obdAddress
        if (prefs.obdAuto && addr.isNotEmpty() && obd.status == ObdClient.Status.OFF && obd.hasPermission()) {
            obd.connect(addr)
        }
    }

    fun stop() {
        if (registered) {
            try {
                appContext.unregisterReceiver(receiver)
            } catch (e: Exception) {
                // ignoré
            }
            registered = false
        }
        obd.disconnect()
    }

    /** Remplace l'état des ouvrants et déclenche les alertes pour ce qui vient de s'ouvrir. */
    fun updateDoors(next: Doors, source: String) {
        val prev = doors
        doors = next
        doorsSource = source
        lastDoorUpdate = System.currentTimeMillis()
        val opened = next.openNames() - prev.openNames().toSet()
        if (opened.isNotEmpty()) {
            val text = opened.joinToString(", ").replaceFirstChar { it.uppercase() } +
                if (opened.size > 1) " ouverts" else if (opened.first() == "coffre" || opened.first() == "capot") " ouvert" else " ouverte"
            raise(text)
        } else if (prev.anyOpen() && !next.anyOpen()) {
            raise("Tout est fermé", speak = false)
        }
    }

    /** Appelé chaque seconde : prévient si un ouvrant est ouvert en roulant. */
    fun tick(speedKmh: Float) {
        val now = System.currentTimeMillis()
        if (doors.anyOpen() && speedKmh > 8f && now - lastMovingWarn > 20_000) {
            lastMovingWarn = now
            raise("Attention : " + doors.openNames().joinToString(", ") + " en roulant !")
        }
    }

    private fun raise(text: String, speak: Boolean = true) {
        alert = text
        alertAt = System.currentTimeMillis()
        if (speak && prefs.doorVoice) onSpeak?.invoke(text)
    }

    fun dismissAlert() {
        alert = null
    }

    @Suppress("DEPRECATION")
    fun applyIntent(i: Intent) {
        val ex = i.extras ?: return
        fun bool(key: String, cur: Boolean): Boolean {
            if (!ex.containsKey(key)) return cur
            return when (val v = ex.get(key)) {
                is Boolean -> v
                is Number -> v.toInt() != 0
                is String -> v.trim().lowercase(Locale.ROOT) in setOf("1", "true", "open", "ouvert", "yes", "on")
                else -> cur
            }
        }
        fun num(key: String): Double? = when (val v = ex.get(key)) {
            is Number -> v.toDouble()
            is String -> v.trim().replace(',', '.').toDoubleOrNull()
            else -> null
        }

        val d = doors
        val next = Doors(
            fl = bool("door_fl", d.fl),
            fr = bool("door_fr", d.fr),
            rl = bool("door_rl", d.rl),
            rr = bool("door_rr", d.rr),
            trunk = bool("trunk", d.trunk),
            hood = bool("hood", d.hood),
        )
        if (next != d || lastDoorUpdate == 0L) updateDoors(next, "boîtier CAN / automatisation")
        num("fuel")?.let { extFuel = it.toInt() }
        num("range_km")?.let { extRange = it.toInt() }
        num("temp_out")?.let { extOutTemp = it }
    }

    companion object {
        const val ACTION_STATUS = "app.drivehome.CAR_STATUS"
    }
}

// =============================================================================
// OBD2 Bluetooth (ELM327)
// =============================================================================

class ObdClient(context: Context) {
    enum class Status { OFF, CONNECTING, CONNECTED, ERROR }

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    var status by mutableStateOf(Status.OFF)
        private set
    var message by mutableStateOf("")
        private set
    var deviceName by mutableStateOf("")
        private set

    var speed by mutableStateOf<Int?>(null)
        private set
    var rpm by mutableStateOf<Int?>(null)
        private set
    var coolant by mutableStateOf<Int?>(null)
        private set
    var intake by mutableStateOf<Int?>(null)
        private set
    var oil by mutableStateOf<Int?>(null)
        private set
    var load by mutableStateOf<Int?>(null)
        private set
    var throttle by mutableStateOf<Int?>(null)
        private set
    var fuel by mutableStateOf<Int?>(null)
        private set
    var ambient by mutableStateOf<Int?>(null)
        private set
    var voltage by mutableStateOf<Double?>(null)
        private set
    var dtcs by mutableStateOf<List<String>?>(null)
        private set

    private var socket: BluetoothSocket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null
    private var job: Job? = null
    private val unsupported = mutableSetOf<String>()

    private fun adapter(): BluetoothAdapter? =
        (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < 31 ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    /** Appareils Bluetooth déjà appairés : (nom, adresse). */
    @SuppressLint("MissingPermission")
    fun bondedDevices(): List<Pair<String, String>> = try {
        adapter()?.bondedDevices?.map { (it.name ?: it.address) to it.address }?.sortedBy { it.first } ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String) {
        disconnect()
        status = Status.CONNECTING
        message = "Connexion…"
        job = scope.launch {
            try {
                val ad = adapter() ?: throw IOException("Pas de Bluetooth sur cet appareil")
                val dev = ad.getRemoteDevice(address)
                val name = dev.name ?: address
                ui { deviceName = name }
                try {
                    ad.cancelDiscovery()
                } catch (e: Exception) {
                    // ignoré
                }
                val s = try {
                    dev.createRfcommSocketToServiceRecord(SPP).also { it.connect() }
                } catch (e: IOException) {
                    dev.createInsecureRfcommSocketToServiceRecord(SPP).also { it.connect() }
                }
                socket = s
                input = s.inputStream
                output = s.outputStream
                mutex.withLock {
                    cmd("ATZ", 4000)
                    for (c in listOf("ATE0", "ATL0", "ATS0", "ATH0", "ATSP0")) cmd(c)
                    cmd("0100", 10_000) // laisse l'ELM trouver le protocole
                }
                ui {
                    status = Status.CONNECTED
                    message = "Connecté à $name"
                }
                poll()
            } catch (e: Exception) {
                closeSocket()
                ui {
                    status = Status.ERROR
                    message = "Échec : " + (e.message ?: "connexion impossible")
                }
            }
        }
    }

    fun disconnect() {
        job?.cancel()
        job = null
        closeSocket()
        if (status != Status.OFF) {
            status = Status.OFF
            message = "Déconnecté"
        }
    }

    private fun closeSocket() {
        try {
            socket?.close()
        } catch (e: Exception) {
            // ignoré
        }
        socket = null
        input = null
        output = null
    }

    private suspend fun poll() {
        var n = 0
        while (scope.isActive && socket != null) {
            mutex.withLock {
                val sp = pid("0D")?.let { it[0] }
                val r = pid("0C")?.let { (it[0] * 256 + it[1]) / 4 }
                ui {
                    speed = sp
                    rpm = r
                }
                if (n % 4 == 0) {
                    val cool = pid("05")?.let { it[0] - 40 }
                    val ld = pid("04")?.let { it[0] * 100 / 255 }
                    val th = pid("11")?.let { it[0] * 100 / 255 }
                    val v = cmd("ATRV").replace("V", "").trim().replace(',', '.').toDoubleOrNull()
                    ui {
                        coolant = cool
                        load = ld
                        throttle = th
                        voltage = v
                    }
                }
                if (n % 15 == 0) {
                    val f = pid("2F")?.let { it[0] * 100 / 255 }
                    val amb = pid("46")?.let { it[0] - 40 }
                    val inT = pid("0F")?.let { it[0] - 40 }
                    val o = pid("5C")?.let { it[0] - 40 }
                    ui {
                        fuel = f
                        ambient = amb
                        intake = inT
                        oil = o
                    }
                }
            }
            n++
            delay(250)
        }
    }

    /** Lit les codes défaut (mode 03). */
    fun readDtcs() {
        if (status != Status.CONNECTED) return
        scope.launch {
            val codes = mutex.withLock { parseDtcs(cmd("03", 6000)) }
            ui { dtcs = codes }
        }
    }

    /** Efface les codes défaut et le voyant moteur (mode 04). */
    fun clearDtcs() {
        if (status != Status.CONNECTED) return
        scope.launch {
            mutex.withLock { cmd("04", 6000) }
            ui { dtcs = emptyList() }
        }
    }

    private fun pid(p: String): IntArray? {
        if (p in unsupported) return null
        val raw = try {
            cmd("01$p")
        } catch (e: IOException) {
            throw e
        }
        val clean = raw.uppercase(Locale.ROOT).replace(" ", "").replace("\r", "\n")
        val tag = "41$p"
        val line = clean.split("\n").firstOrNull { it.contains(tag) }
        if (line == null) {
            if (clean.contains("NODATA") || clean.contains("?")) unsupported.add(p)
            return null
        }
        val hex = line.substring(line.indexOf(tag) + tag.length)
        val bytes = hex.chunked(2).mapNotNull { it.toIntOrNull(16) }
        return if (bytes.isEmpty()) null else IntArray(4) { bytes.getOrElse(it) { 0 } }
    }

    private fun cmd(c: String, timeoutMs: Long = 2500): String {
        val out = output ?: throw IOException("Déconnecté")
        val inp = input ?: throw IOException("Déconnecté")
        out.write((c + "\r").toByteArray())
        out.flush()
        val sb = StringBuilder()
        val start = System.currentTimeMillis()
        while (true) {
            if (inp.available() > 0) {
                val b = inp.read()
                if (b < 0) throw IOException("Connexion perdue")
                val ch = b.toChar()
                if (ch == '>') break
                sb.append(ch)
            } else {
                if (System.currentTimeMillis() - start > timeoutMs) break
                Thread.sleep(10)
            }
        }
        return sb.toString().replace("SEARCHING...", "").trim()
    }

    private suspend fun ui(block: () -> Unit) = withContext(Dispatchers.Main) { block() }

    companion object {
        private val SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        /** Décode une réponse « 43 … » en codes P0123, U0100, etc. */
        fun parseDtcs(raw: String): List<String> {
            val codes = mutableListOf<String>()
            raw.uppercase(Locale.ROOT).replace("\r", "\n").split("\n").forEach { l ->
                val line = l.replace(" ", "").trim()
                val i = line.indexOf("43")
                if (i < 0) return@forEach
                var data = line.substring(i + 2).chunked(2).mapNotNull { it.toIntOrNull(16) }
                if (data.size % 2 == 1) data = data.drop(1) // CAN : 1er octet = nombre de codes
                data.chunked(2).forEach { pair ->
                    if (pair.size == 2 && (pair[0] != 0 || pair[1] != 0)) {
                        val a = pair[0]
                        val letter = "PCBU"[(a shr 6) and 3]
                        val d1 = (a shr 4) and 3
                        val rest = String.format(Locale.ROOT, "%01X%02X", a and 0xF, pair[1])
                        codes.add("$letter$d1$rest")
                    }
                }
            }
            return codes.distinct()
        }
    }
}
