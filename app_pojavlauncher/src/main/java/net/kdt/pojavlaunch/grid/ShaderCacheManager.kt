package net.kdt.pojavlaunch.grid

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.Socket
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * MineX Grid — Fase 2
 * Gerencia o cache de shaders compilados:
 *  - Busca em peers antes de compilar localmente
 *  - Transfere arquivos com verificação SHA-256
 *  - Comprime com GZIP para economizar banda
 *  - Expõe o cache para outros peers via TCP
 *
 * Pacote: net.kdt.pojavlaunch.grid
 */
object ShaderCacheManager {

    private const val TAG             = "MineXShaderCache"
    private const val PROTOCOL_VER    = "1"
    private const val MAX_CACHE_SIZE  = 500 * 1024 * 1024L  // 500 MB
    private const val CONNECT_TIMEOUT = 5_000               // 5s
    private const val READ_TIMEOUT    = 30_000              // 30s

    // ─── Diretórios ───────────────────────────────────────────────────────────

    /**
     * Diretório gerenciado pelo MineX Grid.
     * Mesa/Zink é direcionado aqui via MESA_SHADER_CACHE_DIR.
     */
    fun getCacheDir(context: Context): File =
        File(context.cacheDir, "minexgrid_shader_cache").also { it.mkdirs() }

    fun getTmpDir(context: Context): File =
        File(context.cacheDir, "minexgrid_tmp").also { it.mkdirs() }

    // ─── Pre-launch Check ─────────────────────────────────────────────────────

    /**
     * Chamado ANTES de iniciar o Minecraft.
     * Para cada shader que ainda não existe no cache local,
     * tenta baixar de um peer com a mesma GPU.
     *
     * @param discovery instância ativa do MineXGridDiscovery
     * @param gpuModel  resultado de GLES20.glGetString(GL_RENDERER)
     * @param onProgress callback de progresso para a UI (0..100)
     */
    suspend fun preLaunchCheck(
        context:   Context,
        discovery: MineXGridDiscovery,
        gpuModel:  String,
        onProgress: ((Int) -> Unit)? = null
    ) = withContext(Dispatchers.IO) {

        val peers = discovery.getPeersWithGpu(gpuModel)
        if (peers.isEmpty()) {
            Log.i(TAG, "Nenhum peer com GPU=$gpuModel encontrado. Pulando pre-launch check.")
            return@withContext
        }

        Log.i(TAG, "Pre-launch check: ${peers.size} peer(s) com GPU=$gpuModel")

        peers.forEachIndexed { idx, peer ->
            try {
                val remoteIndex = fetchPeerShaderIndex(peer, gpuModel)
                val localCacheDir = getCacheDir(context)

                val missing = remoteIndex.filter { hash ->
                    !File(localCacheDir, hash).exists()
                }

                Log.i(TAG, "Peer ${peer.nodeId}: ${remoteIndex.size} shaders, ${ missing.size} faltando localmente")

                missing.forEachIndexed { shaderIdx, hash ->
                    val success = downloadShader(context, peer, hash, gpuModel)
                    if (success) Log.d(TAG, "✓ Shader $hash baixado de ${peer.nodeId}")
                    val progress = ((idx * missing.size + shaderIdx + 1) * 100) /
                                   (peers.size * missing.size.coerceAtLeast(1))
                    onProgress?.invoke(progress.coerceIn(0, 100))
                }
            } catch (e: Exception) {
                Log.w(TAG, "Erro ao verificar peer ${peer.nodeId}: ${e.message}")
            }
        }
    }

    // ─── Download de Shader ───────────────────────────────────────────────────

    /**
     * Baixa um shader de um peer, verifica integridade com SHA-256
     * e move para o cache oficial somente se válido.
     */
    suspend fun downloadShader(
        context:    Context,
        peer:       MineXGridDiscovery.PeerInfo,
        shaderHash: String,
        gpuModel:   String
    ): Boolean = withContext(Dispatchers.IO) {
        val tmpFile = File(getTmpDir(context), "dl_$shaderHash")
        try {
            val socket = Socket(peer.host, peer.port).apply {
                soTimeout = READ_TIMEOUT
                connect(java.net.InetSocketAddress(peer.host, peer.port), CONNECT_TIMEOUT)
            }

            val request = JSONObject().apply {
                put("action",     "get_shader")
                put("shaderHash", shaderHash)
                put("gpu",        gpuModel)
                put("proto",      PROTOCOL_VER)
            }

            val writer = socket.getOutputStream().bufferedWriter()
            writer.write(request.toString())
            writer.newLine()
            writer.flush()

            // Receber metadados
            val metaLine = socket.getInputStream().bufferedReader().readLine()
                ?: return@withContext false
            val meta = JSONObject(metaLine)

            if (!meta.getBoolean("found")) {
                socket.close()
                return@withContext false
            }

            val expectedSha256  = meta.getString("sha256")
            val expectedSize    = meta.getLong("size")
            val isCompressed    = meta.optBoolean("gzip", false)

            // Receber binário (com ou sem GZIP)
            val rawStream = socket.getInputStream()
            val inputStream = if (isCompressed) GZIPInputStream(rawStream) else rawStream

            tmpFile.outputStream().use { fos ->
                val buf = ByteArray(8192)
                var received = 0L
                while (received < expectedSize || isCompressed) {
                    val read = inputStream.read(buf)
                    if (read == -1) break
                    fos.write(buf, 0, read)
                    received += read
                }
            }
            socket.close()

            // ── Verificação de integridade SHA-256 ──────────────────────────
            val actualSha256 = computeSHA256(tmpFile)
            if (actualSha256 != expectedSha256) {
                Log.e(TAG, "SHA-256 inválido! esperado=$expectedSha256 recebido=$actualSha256")
                tmpFile.delete()
                return@withContext false
            }

            // Move para o cache oficial
            val dest = File(getCacheDir(context), shaderHash)
            tmpFile.copyTo(dest, overwrite = true)
            tmpFile.delete()

            evictCacheIfNeeded(context)
            true

        } catch (e: Exception) {
            Log.e(TAG, "Erro ao baixar shader $shaderHash de ${peer.nodeId}: ${e.message}")
            tmpFile.delete()
            false
        }
    }

    // ─── Servidor — Responder requisições ─────────────────────────────────────

    /**
     * Chamado pelo MineXGridDiscovery para cada conexão de cliente.
     * Processa uma requisição e envia o shader se disponível.
     */
    fun handleIncomingRequest(context: Context, client: Socket) {
        try {
            client.soTimeout = READ_TIMEOUT
            val reader = client.getInputStream().bufferedReader()
            val writer = client.getOutputStream().bufferedWriter()

            val requestLine = reader.readLine() ?: return
            val req = JSONObject(requestLine)

            when (req.optString("action")) {
                "get_shader"   -> handleGetShader(context, req, client, writer)
                "list_shaders" -> handleListShaders(context, req, writer)
                else           -> {
                    writer.write(JSONObject().put("error", "unknown_action").toString())
                    writer.newLine()
                    writer.flush()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao processar requisição: ${e.message}")
        } finally {
            try { client.close() } catch (_: Exception) {}
        }
    }

    private fun handleGetShader(
        context: Context,
        req:     JSONObject,
        client:  Socket,
        writer:  java.io.BufferedWriter
    ) {
        val shaderHash = req.optString("shaderHash") ?: return
        val reqGpu     = req.optString("gpu")
        val cacheFile  = File(getCacheDir(context), shaderHash)

        // Só envia se o shader existe e é para a mesma GPU
        // (gpu vazia = aceita qualquer)
        if (!cacheFile.exists()) {
            writer.write(JSONObject().put("found", false).toString())
            writer.newLine()
            writer.flush()
            return
        }

        val sha256   = computeSHA256(cacheFile)
        val gzip     = cacheFile.length() > 50_000  // comprimir arquivos > 50KB

        val meta = JSONObject().apply {
            put("found", true)
            put("sha256", sha256)
            put("size",  cacheFile.length())
            put("gzip",  gzip)
        }
        writer.write(meta.toString())
        writer.newLine()
        writer.flush()

        // Enviar binário
        val outStream = client.getOutputStream()
        if (gzip) {
            GZIPOutputStream(outStream).use { gz ->
                cacheFile.inputStream().use { it.copyTo(gz) }
            }
        } else {
            cacheFile.inputStream().use { it.copyTo(outStream) }
        }
        outStream.flush()
        Log.d(TAG, "Shader $shaderHash enviado para ${client.inetAddress.hostAddress}")
    }

    private fun handleListShaders(
        context: Context,
        req:     JSONObject,
        writer:  java.io.BufferedWriter
    ) {
        val gpu     = req.optString("gpu")
        val cacheDir = getCacheDir(context)

        // Listar todos os hashes disponíveis
        // (o filtro de GPU é feito pelo cliente na hora de baixar)
        val hashes = cacheDir.listFiles()?.map { it.name } ?: emptyList()
        val response = JSONObject().apply {
            put("shaders", org.json.JSONArray(hashes))
            put("count",   hashes.size)
            put("gpu",     MineXGridDiscovery("").gpuModel)  // informar GPU do servidor
        }
        writer.write(response.toString())
        writer.newLine()
        writer.flush()
    }

    // ─── Utilitários ─────────────────────────────────────────────────────────

    private fun fetchPeerShaderIndex(
        peer:    MineXGridDiscovery.PeerInfo,
        gpuModel: String
    ): List<String> {
        val socket = Socket(peer.host, peer.port).apply {
            soTimeout = READ_TIMEOUT
        }
        val request = JSONObject().apply {
            put("action", "list_shaders")
            put("gpu",    gpuModel)
            put("proto",  PROTOCOL_VER)
        }
        val writer = socket.getOutputStream().bufferedWriter()
        writer.write(request.toString())
        writer.newLine()
        writer.flush()

        val response = JSONObject(socket.getInputStream().bufferedReader().readLine() ?: "{}")
        socket.close()

        val arr = response.optJSONArray("shaders") ?: return emptyList()
        return (0 until arr.length()).map { arr.getString(it) }
    }

    /**
     * Remove os shaders mais antigos se o cache ultrapassar MAX_CACHE_SIZE.
     */
    private fun evictCacheIfNeeded(context: Context) {
        val cacheDir = getCacheDir(context)
        val files = cacheDir.listFiles() ?: return
        var totalSize = files.sumOf { it.length() }

        if (totalSize <= MAX_CACHE_SIZE) return

        // LRU: remover mais antigos primeiro
        files.sortedBy { it.lastModified() }.forEach { file ->
            if (totalSize <= MAX_CACHE_SIZE) return
            totalSize -= file.length()
            file.delete()
            Log.d(TAG, "Cache eviction: ${file.name} removido")
        }
    }

    fun computeSHA256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(8192)
            var read: Int
            while (input.read(buf).also { read = it } != -1) {
                digest.update(buf, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
