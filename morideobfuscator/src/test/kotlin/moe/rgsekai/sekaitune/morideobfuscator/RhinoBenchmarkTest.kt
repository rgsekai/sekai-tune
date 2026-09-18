/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 */

package moe.rgsekai.sekaitune.morideobfuscator

import org.junit.Test

class RhinoBenchmarkTest {
    @Test
    fun benchmarkRhinoScriptEvaluation() {
        val executor = RhinoTransformExecutor()
        val signatureProgram = """
            var AK = {
                swap: function(a, b) { var c = a[0]; a[0] = a[b % a.length]; a[b % a.length] = c; },
                splice: function(a, b) { a.splice(0, b); },
                reverse: function(a) { a.reverse(); }
            };
            function decryptSig(a) {
                a = a.split("");
                AK.reverse(a);
                AK.splice(a, 2);
                AK.swap(a, 14);
                AK.reverse(a);
                AK.swap(a, 41);
                return a.join("");
            }
        """.trimIndent()

        val nProgram = """
            var bA = {
                d: function(a, b) { return a.slice(b); },
                e: function(a, b) { var c = a[0]; a[0] = a[b % a.length]; a[b % a.length] = c; },
                f: function(a) { a.reverse(); }
            };
            function transformN(a) {
                var b = a.split("");
                for (var i = 0; i < 100; i++) {
                    bA.f(b);
                    bA.e(b, (i * 7) % b.length);
                }
                return b.join("");
            }
        """.trimIndent()

        val plan = TransformPlan(
            playerId = "test-player",
            playerUrl = "https://www.youtube.com/s/player/test/player_ias.vflset/en_US/base.js",
            sourceSha256 = "test-hash",
            signatureTimestamp = 19999,
            signatureProgram = signatureProgram,
            signatureFunction = "decryptSig",
            nProgram = nProgram,
            nFunction = "transformN",
            createdAtMillis = System.currentTimeMillis(),
            nTransformState = NTransformState.REQUIRED,
        )

        val sampleInput = "AIzaSyD-abcdEFGHijklMNOPqrstUVWXYZ_0123456789"
        val timingsMs = mutableListOf<Double>()

        // 10 consecutive calls with pre-compilation cache
        for (i in 1..10) {
            val start = System.nanoTime()
            val result = executor.executeSignature(plan, sampleInput)
            val elapsedMs = (System.nanoTime() - start) / 1_000_000.0
            timingsMs.add(elapsedMs)
            println("Iteration $i: ${String.format("%.3f", elapsedMs)} ms -> $result")
        }

        // Test with prewarm
        val prewarmedExecutor = RhinoTransformExecutor()
        val prewarmStart = System.nanoTime()
        prewarmedExecutor.preWarm(plan)
        val prewarmElapsed = (System.nanoTime() - prewarmStart) / 1_000_000.0
        val postPrewarmStart = System.nanoTime()
        val prewarmedResult = prewarmedExecutor.executeSignature(plan, sampleInput)
        val postPrewarmElapsed = (System.nanoTime() - postPrewarmStart) / 1_000_000.0
        println("Prewarm time: ${String.format("%.3f", prewarmElapsed)} ms")
        println("First call after prewarm: ${String.format("%.3f", postPrewarmElapsed)} ms -> $prewarmedResult")

        timingsMs.sort()
        val median = timingsMs[timingsMs.size / 2]
        val p95 = timingsMs[(timingsMs.size * 0.95).toInt().coerceAtMost(timingsMs.size - 1)]
        val avg = timingsMs.average()

        println("=== RHINO BENCHMARK RESULTS (10 iterations) ===")
        println("Min:    ${String.format("%.3f", timingsMs.first())} ms")
        println("Median: ${String.format("%.3f", median)} ms")
        println("P95:    ${String.format("%.3f", p95)} ms")
        println("Max:    ${String.format("%.3f", timingsMs.last())} ms")
        println("Avg:    ${String.format("%.3f", avg)} ms")
        println("===============================================")
    }
}
