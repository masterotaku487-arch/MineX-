package net.kdt.pojavlaunch.grid

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * MineX Grid — Ponto central de controle.
 * Integra MineXGridDiscovery + ShaderCacheManager.
 * Injetável em qualquer ponto do launcher.
 *
 * Uso:
 *   MineXGridManager.initialize(applicationContext)
 *   MineXGridManager.start()                          // no onResume ou antes do launch
 *   MineXGridManager.preLaunchCheck(gpu) { progress } // antes do Minecraft iniciar
 *   MineXGridManager.injectEnvironmentVars(envMap)     // injetar vars de ambiente
 *   MineXGridManager.stop()                            // no onDestroy
 */
object MineXGridManager {

    private const val TAG       = "MineXGridManager"
    private const val PREFS_KEY = "minexgrid_prefs"
    private const val KEY_ENABLED = "grid_enabled"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var discovery: MineXGridDiscovery? = null
    private var appContext: Context? = null
    private var prefs: SharedPreferences? = null

    // ─── Inicialização ────────────────────────────────────────────────────────

    fun initialize(context: Context) {
        appContext = context.applicationContext
        prefs      = context.getSharedPreferences(PREFS_KEY, Context.MODE_PRIVATE)
        discovery  = MineXGridDiscovery(context.applicationContext)

        discovery!!.onPeerFound = { peer ->
            Log.i(TAG, "🟢 Peer encontrado: ${peer.nodeId} GPU=${peer.gpuModel}")
        }
        discovery!!.onPeerLost = { nodeId ->
            Log.i(TAG, "🔴 Peer perdido: $nodeId")
        }
    }

    // ─── Liga / desliga ───────────────────────────────────────────────────────

    fun start() {
        if (!isEnabled()) {
            Log.i(TAG, "MineX Grid desativado pelo usuário.")
            return
        }
        checkInitialized()
        discovery!!.start()
        Log.i(TAG, "MineX Grid iniciado. NodeId=${discovery!!.nodeId}")
    }

    fun stop() {
        discovery?.stop()
        Log.i(TAG, "MineX Grid parado.")
    }

    // ─── Pre-launch Check ─────────────────────────────────────────────────────

    /**
     * Chame este método antes de iniciar o Minecraft.
     * Busca shaders nos peers e baixa os que faltam.
     *
     * Exemplo de uso em JvmLauncher ou LauncherActivity:
     *
     *   MineXGridManager.preLaunchCheck(gpuModel) { progress ->
     *       runOnUiThread { progressBar.progress = progress }
     *   }
     */
    fun preLaunchCheck(gpuModel: String, onProgress: ((Int) -> Unit)? = null) {
        if (!isEnabled()) return
        checkInitialized()

        scope.launch {
            ShaderCacheManager.preLaunchCheck(
                context    = appContext!!,
                discovery  = discovery!!,
                gpuModel   = gpuModel,
                onProgress = onProgress
            )
        }
    }

    // ─── Injeção de Variáveis de Ambiente ─────────────────────────────────────

    /**
     * Adiciona as variáveis de ambiente do MineX Grid ao mapa
     * que será passado para o processo do Minecraft.
     *
     * Chame ANTES do ProcessBuilder / exec() em JvmLauncher:
     *
     *   val envMap = mutableMapOf<String, String>()
     *   // ... suas variáveis existentes ...
     *   MineXGridManager.injectEnvironmentVars(appContext, envMap)
     *   processBuilder.environment().putAll(envMap)
     */
    fun injectEnvironmentVars(context: Context, envMap: MutableMap<String, String>) {
        if (!isEnabled()) return

        val cacheDir = ShaderCacheManager.getCacheDir(context).absolutePath

        envMap["MESA_SHADER_CACHE_DIR"]       = cacheDir
        envMap["MESA_SHADER_CACHE_MAX_SIZE"]  = "1G"
        envMap["MESA_GLSL_CACHE_DIR"]         = cacheDir      // fallback para versões antigas do Mesa
        envMap["__GL_SHADER_DISK_CACHE_PATH"] = cacheDir      // Nvidia compat

        Log.i(TAG, "Variáveis de ambiente MineX Grid injetadas → $cacheDir")
    }

    /**
     * Sobrecarga para Process.environment() (usado no PojavLauncher).
     * Exemplo:
     *   MineXGridManager.injectIntoProcess(appContext, processBuilder)
     */
    fun injectIntoProcess(context: Context, pb: ProcessBuilder) {
        val env = pb.environment()
        val tmp = mutableMapOf<String, String>()
        injectEnvironmentVars(context, tmp)
        env.putAll(tmp)
    }

    // ─── Preferências ─────────────────────────────────────────────────────────

    fun isEnabled(): Boolean =
        prefs?.getBoolean(KEY_ENABLED, false) ?: false

    fun setEnabled(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_ENABLED, enabled)?.apply()
        if (enabled) start() else stop()
    }

    // ─── Info de debug ────────────────────────────────────────────────────────

    fun getPeerCount(): Int = discovery?.peers?.size ?: 0
    fun getNodeId():    String = discovery?.nodeId ?: "N/A"

    // ─── Interno ─────────────────────────────────────────────────────────────

    private fun checkInitialized() {
        check(appContext != null && discovery != null) {
            "MineXGridManager não inicializado! Chame initialize(context) primeiro."
        }
    }
}
