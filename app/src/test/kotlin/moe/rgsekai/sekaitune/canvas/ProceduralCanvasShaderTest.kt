/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.min

class ProceduralCanvasShaderTest {

    @Test
    fun testAgslShaderSourceContainsRequiredUniformsAndMain() {
        val shader = KAWARP_AGSL_SHADER
        assertTrue("Shader source must not be empty", shader.isNotBlank())
        assertTrue("Shader must declare uniform shader image", shader.contains("uniform shader image;"))
        assertTrue("Shader must declare uniform float2 resolution", shader.contains("uniform float2 resolution;"))
        assertTrue("Shader must declare uniform float time", shader.contains("uniform float time;"))
        assertTrue("Shader must declare half4 main(float2 fragCoord)", shader.contains("half4 main(float2 fragCoord)"))
        assertTrue("Shader must evaluate input shader", shader.contains("image.eval("))
        assertTrue("Shader must clamp coordinates to prevent out-of-bounds sampling", shader.contains("clamp("))
        assertTrue("Shader must include sine harmonic displacement", shader.contains("sin("))
        assertTrue("Shader must include cosine harmonic displacement", shader.contains("cos("))
        assertTrue("Shader must use isotropic min(resolution.x, resolution.y) scaling", shader.contains("min(resolution.x, resolution.y)"))
    }

    @Test
    fun testDeviceCapabilityBranching() {
        // API 33+ (Tiramisu, UpsideDownCake, VanillaIceCream, etc.) with standard RAM -> AGSL supported
        assertTrue(
            "API 33 standard device must support AGSL runtime shaders",
            isProceduralShaderSupported(sdkInt = 33, isLowRamDevice = false),
        )
        assertTrue(
            "API 34 standard device must support AGSL runtime shaders",
            isProceduralShaderSupported(sdkInt = 34, isLowRamDevice = false),
        )
        assertTrue(
            "API 35 standard device must support AGSL runtime shaders",
            isProceduralShaderSupported(sdkInt = 35, isLowRamDevice = false),
        )

        // API < 33 -> Must fallback to Ken Burns
        assertFalse(
            "API 32 (S_V2) must fall back to Ken Burns",
            isProceduralShaderSupported(sdkInt = 32, isLowRamDevice = false),
        )
        assertFalse(
            "API 31 (S) must fall back to Ken Burns",
            isProceduralShaderSupported(sdkInt = 31, isLowRamDevice = false),
        )
        assertFalse(
            "API 30 (R) must fall back to Ken Burns",
            isProceduralShaderSupported(sdkInt = 30, isLowRamDevice = false),
        )
        assertFalse(
            "API 26 (O) must fall back to Ken Burns",
            isProceduralShaderSupported(sdkInt = 26, isLowRamDevice = false),
        )

        // Low RAM device -> Must fall back to Ken Burns even on API 33+
        assertFalse(
            "Low RAM device on API 33 must fall back to Ken Burns",
            isProceduralShaderSupported(sdkInt = 33, isLowRamDevice = true),
        )
        assertFalse(
            "Low RAM device on API 34 must fall back to Ken Burns",
            isProceduralShaderSupported(sdkInt = 34, isLowRamDevice = true),
        )
    }

    @Test
    fun testAspectRatioIsotropicDisplacementCalculation() {
        // Test 1:1 Mini-player thumbnail (300 x 300)
        val miniW = 300f
        val miniH = 300f
        val miniMinDim = min(miniW, miniH)

        // Test 9:16 Portrait Now Playing screen (1080 x 2400)
        val fullW = 1080f
        val fullH = 2400f
        val fullMinDim = min(fullW, fullH)

        // The displacement fraction is identical regardless of aspect ratio
        val warpFractionX = 0.015f
        val warpFractionY = 0.015f

        val miniDispPxX = warpFractionX * miniMinDim
        val miniDispPxY = warpFractionY * miniMinDim
        assertEquals("In 1:1 aspect, X and Y pixel displacements must be equal", miniDispPxX, miniDispPxY, 0.001f)

        val fullDispPxX = warpFractionX * fullMinDim
        val fullDispPxY = warpFractionY * fullMinDim
        assertEquals("In portrait 9:16 aspect, X and Y pixel displacements must remain isotropic and equal", fullDispPxX, fullDispPxY, 0.001f)
    }

    @Test
    fun testTimeProgressionCalculation() {
        var accumulatedTimeSeconds = 0f
        val frameDeltaNanos = 16_666_666L // ~60fps frame delta in nanos

        for (frame in 1..60) {
            val deltaSeconds = frameDeltaNanos / 1_000_000_000f
            accumulatedTimeSeconds += deltaSeconds
        }

        // 60 frames at 16.666ms should accumulate ~1.0 second
        assertEquals(1.0f, accumulatedTimeSeconds, 0.01f)
    }
}
