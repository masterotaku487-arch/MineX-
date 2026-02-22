package net.kdt.pojavlaunch.grid

/**
 * MineX Grid — Patch de integração para JvmLauncher
 *
 * Este arquivo mostra EXATAMENTE onde e como modificar o JvmLauncher
 * (ou a classe que faz o exec() do Minecraft) para integrar o MineX Grid.
 *
 * ════════════════════════════════════════════════════════════════
 *  PASSO 1 — Encontre a classe que prepara o ambiente da JRE.
 *  Normalmente: net.kdt.pojavlaunch.JvmLauncher  ou
 *               net.kdt.pojavlaunch.LaunchActivity
 *  Procure pelo método que chama ProcessBuilder() ou Runtime.exec()
 * ════════════════════════════════════════════════════════════════
 *
 * CÓDIGO ORIGINAL (exemplo simplificado do PojavLauncher):
 *
 *   fun launchMinecraft(context: Context, args: Array<String>) {
 *       val pb = ProcessBuilder(args.toList())
 *       pb.environment()["JAVA_HOME"] = jreDir
 *       pb.environment()["HOME"]      = gameDir
 *       // ... outras vars ...
 *
 *       val process = pb.start()
 *       // ...
 *   }
 *
 * ════════════════════════════════════════════════════════════════
 *  PASSO 2 — Adicione as chamadas do MineX Grid
 * ════════════════════════════════════════════════════════════════
 *
 * CÓDIGO MODIFICADO — adicione as linhas marcadas com "// ← MineX Grid":
 *
 *   fun launchMinecraft(context: Context, args: Array<String>) {
 *
 *       // ← MineX Grid: inicializar (se ainda não inicializado)
 *       MineXGridManager.initialize(context)
 *       MineXGridManager.start()
 *
 *       // ← MineX Grid: pre-launch check (buscar shaders nos peers)
 *       val gpuModel = GLES20.glGetString(GLES20.GL_RENDERER) ?: ""
 *       MineXGridManager.preLaunchCheck(gpuModel) { progress ->
 *           // Opcional: atualizar uma ProgressBar na UI
 *           // runOnUiThread { binding.gridProgress.progress = progress }
 *       }
 *
 *       val pb = ProcessBuilder(args.toList())
 *       pb.environment()["JAVA_HOME"] = jreDir
 *       pb.environment()["HOME"]      = gameDir
 *
 *       // ← MineX Grid: injetar variáveis de ambiente Mesa/Zink
 *       MineXGridManager.injectIntoProcess(context, pb)
 *
 *       val process = pb.start()
 *       // ...
 *   }
 *
 * ════════════════════════════════════════════════════════════════
 *  PASSO 3 — Inicializar no Application ou MainActivity
 * ════════════════════════════════════════════════════════════════
 *
 * Em PojavApplication.kt (ou onCreate() da MainActivity):
 *
 *   override fun onCreate() {
 *       super.onCreate()
 *       // ... código existente ...
 *
 *       // ← MineX Grid: inicializar cedo para descoberta ter tempo de rodar
 *       MineXGridManager.initialize(applicationContext)
 *       if (MineXGridManager.isEnabled()) {
 *           MineXGridManager.start()
 *       }
 *   }
 *
 * ════════════════════════════════════════════════════════════════
 *  PASSO 4 — Parar o Grid quando o app fechar
 * ════════════════════════════════════════════════════════════════
 *
 * Em onDestroy() da MainActivity ou no Application.onTerminate():
 *
 *   override fun onDestroy() {
 *       super.onDestroy()
 *       MineXGridManager.stop()  // ← MineX Grid
 *   }
 */

// Este arquivo é apenas documentação de integração.
// Não há código executável aqui — as modificações devem ser feitas
// diretamente nas classes existentes do PojavLauncher conforme acima.
