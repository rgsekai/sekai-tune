/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.morideobfuscator

import org.mozilla.javascript.ClassShutter
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextAction
import org.mozilla.javascript.ContextFactory
import org.mozilla.javascript.EvaluatorException
import org.mozilla.javascript.Function

import org.mozilla.javascript.Script
import java.util.concurrent.ConcurrentHashMap

internal class RhinoTransformExecutor {
    fun preWarm(plan: TransformPlan? = null) {
        runCatching {
            factory.call(
                ContextAction { context ->
                    context.optimizationLevel = -1
                    context.languageVersion = Context.VERSION_ES6
                    context.setClassShutter(ClassShutter { false })
                    context.initSafeStandardObjects(null, true)
                    plan?.let { nonNullPlan ->
                        nonNullPlan.signatureProgram?.let { program ->
                            val key = "${nonNullPlan.sourceSha256}:sig"
                            scriptCache.computeIfAbsent(key) {
                                context.compileString(program, "mori-player-sig", 1, null)
                            }
                        }
                        nonNullPlan.nProgram?.let { program ->
                            val key = "${nonNullPlan.sourceSha256}:n"
                            scriptCache.computeIfAbsent(key) {
                                context.compileString(program, "mori-player-n", 1, null)
                            }
                        }
                    }
                },
            )
        }
    }

    fun executeSignature(
        plan: TransformPlan,
        input: String,
    ): String =
        execute(
            cacheKey = "${plan.sourceSha256}:sig",
            program = plan.signatureProgram ?: throw MoriCipherCapabilityException("Signature transform is unavailable"),
            functionName = plan.signatureFunction ?: throw MoriCipherCapabilityException("Signature transform is unavailable"),
            input = input,
        )

    fun executeN(
        plan: TransformPlan,
        input: String,
    ): String =
        execute(
            cacheKey = "${plan.sourceSha256}:n",
            program = plan.nProgram ?: throw MoriCipherCapabilityException("Throttle transform is unavailable"),
            functionName = plan.nFunction ?: throw MoriCipherCapabilityException("Throttle transform is unavailable"),
            input = input,
        )

    private fun execute(
        cacheKey: String,
        program: String,
        functionName: String,
        input: String,
    ): String {
        if (input.length !in 1..MAX_INPUT_LENGTH) {
            throw MoriCipherException("Cipher input length was invalid")
        }
        val isHit = scriptCache.containsKey(cacheKey)
        val hits = if (isHit) cacheHits.incrementAndGet() else cacheHits.get()
        val misses = if (!isHit) cacheMisses.incrementAndGet() else cacheMisses.get()
        val total = hits + misses
        val rate = if (total > 0) (hits * 100.0) / total else 0.0
        println("[RhinoScriptCache] key=$cacheKey isHit=$isHit -> Stats: $hits hits / $misses misses (Hit rate: ${String.format(java.util.Locale.US, "%.1f", rate)}%) | CachedKeys=${scriptCache.keys().toList()}")
        return try {
            factory.call(
                ContextAction { context ->
                    context.optimizationLevel = -1
                    context.languageVersion = Context.VERSION_ES6
                    context.setClassShutter(ClassShutter { false })
                    val scope = context.initSafeStandardObjects(null, true)
                    var script = scriptCache[cacheKey]
                    if (script == null) {
                        val compiled = context.compileString(program, "mori-player", 1, null)
                        scriptCache[cacheKey] = compiled
                        script = compiled
                    }
                    script.exec(context, scope)
                    val function =
                        scope.get(functionName, scope) as? Function
                            ?: throw MoriCipherException("Compiled transform was not callable")
                    val value = function.call(context, scope, scope, arrayOf(input))
                    Context
                        .toString(value)
                        .takeIf { it.length in 1..MAX_OUTPUT_LENGTH && SAFE_OUTPUT.matches(it) }
                        ?: throw MoriCipherException("Transform produced an invalid value")
                },
            )
        } catch (error: MoriCipherException) {
            throw error
        } catch (error: Exception) {
            throw MoriCipherException("JavaScript transform execution failed", error)
        }
    }


    private class BoundedContextFactory : ContextFactory() {
        override fun makeContext(): Context =
            super.makeContext().apply {
                instructionObserverThreshold = INSTRUCTION_CHUNK
                putThreadLocal(DEADLINE_KEY, System.nanoTime() + MAX_EXECUTION_NANOS)
            }

        override fun observeInstructionCount(
            context: Context,
            instructionCount: Int,
        ) {
            val deadline = context.getThreadLocal(DEADLINE_KEY) as? Long ?: return
            if (System.nanoTime() > deadline) {
                throw EvaluatorException("JavaScript transform exceeded its execution budget")
            }
        }
    }

    private companion object {
        const val MAX_INPUT_LENGTH = 16_384
        const val MAX_OUTPUT_LENGTH = 32_768
        const val INSTRUCTION_CHUNK = 10_000
        const val MAX_EXECUTION_NANOS = 2_000_000_000L
        val DEADLINE_KEY = Any()
        val SAFE_OUTPUT = Regex("""^[A-Za-z0-9._~!$&'()*+,;=:@/?%-]+$""")
        val factory = BoundedContextFactory()
        val scriptCache = ConcurrentHashMap<String, Script>()
        val cacheHits = java.util.concurrent.atomic.AtomicLong(0)
        val cacheMisses = java.util.concurrent.atomic.AtomicLong(0)
    }
}




