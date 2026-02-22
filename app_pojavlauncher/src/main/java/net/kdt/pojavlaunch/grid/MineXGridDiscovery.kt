package net.kdt.pojavlaunch.grid

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.opengl.GLES20
import android.provider.Settings
import android.util.Log
import java.net.ServerSocket
import java.security.MessageDigest

/**
 * MineX Grid — Fase 1
 * Descoberta de peers via mDNS (rede local) usando o NsdManager nativo do Android.
 *
 * Pacote: net.kdt.pojavlaunch.grid
 */
class MineXGridDiscovery(private val context: Context) {

    companion object {
        private const val TAG            = "MineXGrid"
        private const val SERVICE_TYPE   = "_minexgrid._tcp."
        private const val SERVICE_PREFIX = "MineXNode"
        const val DEFAULT_PORT           = 47823
    }

    // ─── Modelos de dados ────────────────────────────────────────────────────

    data class PeerInfo(
        val nodeId:     String,
        val host:       String,
        val port:       Int,
        val gpuModel:   String,
        val appVersion: String,
        val lastSeen:   Long = System.currentTimeMillis()
    )

    // ─── Estado interno ───────────────────────────────────────────────────────

    private val nsdManager: NsdManager by lazy {
        context.getSystemService(Context.NSD_SERVICE) as NsdManager
    }

    private val _peers = mutableMapOf<String, PeerInfo>()
    val peers: Map<String, PeerInfo> get() = _peers.toMap()

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener:    NsdManager.DiscoveryListener?    = null
    private var serverSocket:         ServerSocket?                    = null
    private var isRunning = false

    // Callbacks externos
    var onPeerFound:  ((PeerInfo) -> Unit)? = null
    var onPeerLost:   ((String)   -> Unit)? = null

    // ─── Identidade do nó ─────────────────────────────────────────────────────

    val nodeId: String by lazy {
        val androidId = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ANDROID_ID
        ) ?: "unknown"
        sha256(androidId).take(16)
    }

    val gpuModel: String by lazy {
        // Nota: deve ser chamado em thread com contexto GL ativo.
        // Fallback para propriedade do sistema se GL não disponível.
        GLES20.glGetString(GLES20.GL_RENDERER)
            ?: android.os.Build.HARDWARE
    }

    val appVersion: String by lazy {
        try {
            context.packageManager
                .getPackageInfo(context.packageName, 0)
                .versionName ?: "unknown"
        } catch (e: Exception) { "unknown" }
    }

    // ─── API Pública ─────────────────────────────────────────────────────────

    /**
     * Inicia o servidor local + anúncio mDNS + descoberta de peers.
     */
    fun start() {
        if (isRunning) return
        isRunning = true
        Log.i(TAG, "Iniciando MineX Grid — nodeId=$nodeId gpu=$gpuModel")
        startServer()
        advertise()
        discover()
    }

    /**
     * Para tudo e libera recursos.
     */
    fun stop() {
        isRunning = false
        try { registrationListener?.let { nsdManager.unregisterService(it) } } catch (_: Exception) {}
        try { discoveryListener?.let    { nsdManager.stopServiceDiscovery(it) } } catch (_: Exception) {}
        try { serverSocket?.close() } catch (_: Exception) {}
        _peers.clear()
        Log.i(TAG, "MineX Grid parado.")
    }

    /**
     * Retorna todos os peers que possuem a mesma GPU.
     */
    fun getPeersWithGpu(gpu: String): List<PeerInfo> =
        _peers.values.filter { it.gpuModel == gpu }

    // ─── mDNS — Anúncio ──────────────────────────────────────────────────────

    private fun advertise() {
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "${SERVICE_PREFIX}_$nodeId"
            serviceType = SERVICE_TYPE
            port        = serverSocket?.localPort ?: DEFAULT_PORT
            setAttribute("nodeId",  nodeId)
            setAttribute("gpu",     gpuModel.take(63))   // limite NSD ~63 chars
            setAttribute("version", appVersion)
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                Log.i(TAG, "Serviço registrado: ${info.serviceName}")
            }
            override fun onRegistrationFailed(info: NsdServiceInfo, code: Int) {
                Log.e(TAG, "Falha ao registrar serviço: código $code")
            }
            override fun onServiceUnregistered(info: NsdServiceInfo) {
                Log.i(TAG, "Serviço removido: ${info.serviceName}")
            }
            override fun onUnregistrationFailed(info: NsdServiceInfo, code: Int) {
                Log.w(TAG, "Falha ao remover serviço: código $code")
            }
        }

        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
    }

    // ─── mDNS — Descoberta ────────────────────────────────────────────────────

    private fun discover() {
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "Descoberta iniciada: $regType")
            }
            override fun onServiceFound(service: NsdServiceInfo) {
                if (!service.serviceName.startsWith(SERVICE_PREFIX)) return
                // Não conectar em si mesmo
                if (service.serviceName.contains(nodeId)) return
                resolveService(service)
            }
            override fun onServiceLost(service: NsdServiceInfo) {
                val lostId = service.serviceName.removePrefix("${SERVICE_PREFIX}_")
                _peers.remove(lostId)
                onPeerLost?.invoke(lostId)
                Log.d(TAG, "Peer perdido: $lostId")
            }
            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "Descoberta parada")
            }
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Falha ao iniciar descoberta: $errorCode")
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Falha ao parar descoberta: $errorCode")
            }
        }

        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    private fun resolveService(service: NsdServiceInfo) {
        nsdManager.resolveService(service, object : NsdManager.ResolveListener {
            override fun onServiceResolved(info: NsdServiceInfo) {
                val host = info.host?.hostAddress ?: return
                val attrs = info.attributes

                val peer = PeerInfo(
                    nodeId     = attrs["nodeId"]?.let  { String(it) } ?: service.serviceName,
                    host       = host,
                    port       = info.port,
                    gpuModel   = attrs["gpu"]?.let     { String(it) } ?: "unknown",
                    appVersion = attrs["version"]?.let { String(it) } ?: "unknown"
                )

                _peers[peer.nodeId] = peer
                onPeerFound?.invoke(peer)
                Log.i(TAG, "Peer encontrado: ${peer.nodeId} @ ${peer.host}:${peer.port} GPU=${peer.gpuModel}")
            }
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "Falha ao resolver ${serviceInfo.serviceName}: $errorCode")
            }
        })
    }

    // ─── Servidor TCP local ───────────────────────────────────────────────────

    /**
     * Servidor que responde a requisições de shader cache de outros peers.
     * Roda em background thread dedicada.
     */
    private fun startServer() {
        serverSocket = ServerSocket(0) // porta aleatória disponível
        val port = serverSocket!!.localPort
        Log.i(TAG, "Servidor MineX Grid ouvindo na porta $port")

        Thread({
            while (isRunning) {
                try {
                    val client = serverSocket!!.accept()
                    Thread({ ShaderCacheManager.handleIncomingRequest(context, client) }).start()
                } catch (e: Exception) {
                    if (isRunning) Log.e(TAG, "Erro no servidor: ${e.message}")
                }
            }
        }, "MineXGrid-Server").apply {
            isDaemon = true
            start()
        }
    }

    // ─── Utilidades ──────────────────────────────────────────────────────────

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
