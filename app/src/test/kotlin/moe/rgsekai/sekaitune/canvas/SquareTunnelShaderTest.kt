/*
 * Sekai Tune (2026)
 * © Sekai Tune - github.com/rgsekai/sekai-tune
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rgsekai.sekaitune.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow

class SquareTunnelShaderTest {

    @Test
    fun testSquareTunnelAgslShaderSourceContainsRequiredUniformsAndMath() {
        val shader = SQUARE_TUNNEL_AGSL_SHADER
        assertTrue("Shader source must not be empty", shader.isNotBlank())
        assertTrue("Shader must declare uniform shader image", shader.contains("uniform shader image;"))
        assertTrue("Shader must declare uniform float2 resolution", shader.contains("uniform float2 resolution;"))
        assertTrue("Shader must declare uniform float time", shader.contains("uniform float time;"))
        assertTrue("Shader must declare uniform float3 centerColor", shader.contains("uniform float3 centerColor;"))
        assertTrue("Shader must declare half4 main(float2 fragCoord)", shader.contains("half4 main(float2 fragCoord)"))
        assertTrue("Shader must evaluate input shader", shader.contains("image.eval("))
        assertTrue("Shader must calculate polar angle using atan", shader.contains("atan(p.y, p.x)"))
        assertTrue("Shader must calculate 8th-order Minkowski p-norm", shader.contains("0.125"))
        assertTrue("Shader must include mirrored repeat wrapping math", shader.contains("fract(uv * 0.5)"))
        assertTrue("Shader must include radial center vignette", shader.contains("smoothstep("))
        assertTrue("Shader must use isotropic min(resolution.x, resolution.y) scaling", shader.contains("min(resolution.x, resolution.y)"))
    }

    @Test
    fun testMinkowskiNormFlatWallGeometry() {
        fun minkowski8Norm(x: Double, y: Double): Double {
            return (abs(x).pow(8.0) + abs(y).pow(8.0)).pow(1.0 / 8.0)
        }

        // On axes, radius is 1.0
        assertEquals(1.0, minkowski8Norm(1.0, 0.0), 0.0001)
        assertEquals(1.0, minkowski8Norm(0.0, 1.0), 0.0001)
        assertEquals(1.0, minkowski8Norm(-1.0, 0.0), 0.0001)
        assertEquals(1.0, minkowski8Norm(0.0, -1.0), 0.0001)

        // On diagonal (1, 1), Minkowski 8-norm is 2^(1/8) ≈ 1.0905
        // whereas Euclidean 2-norm is sqrt(2) ≈ 1.4142.
        // This confirms flat square tunnel walls with soft corners.
        val diagonalR = minkowski8Norm(1.0, 1.0)
        assertEquals(2.0.pow(0.125), diagonalR, 0.0001)
        assertTrue("Minkowski 8-norm diagonal must be significantly flatter than Euclidean sqrt(2)", diagonalR < 1.10)
    }

    @Test
    fun testMirroredRepeatWrappingMath() {
        fun fract(x: Double): Double = x - floor(x)
        fun mirroredCoord(x: Double): Double = 1.0 - abs(fract(x * 0.5) * 2.0 - 1.0)

        assertEquals(0.0, mirroredCoord(0.0), 0.0001)
        assertEquals(0.5, mirroredCoord(0.5), 0.0001)
        assertEquals(1.0, mirroredCoord(1.0), 0.0001)
        assertEquals(0.5, mirroredCoord(1.5), 0.0001)
        assertEquals(0.0, mirroredCoord(2.0), 0.0001)
        assertEquals(0.5, mirroredCoord(2.5), 0.0001)

        // Negative values
        assertEquals(0.5, mirroredCoord(-0.5), 0.0001)
        assertEquals(1.0, mirroredCoord(-1.0), 0.0001)
        assertEquals(0.5, mirroredCoord(-1.5), 0.0001)
        assertEquals(0.0, mirroredCoord(-2.0), 0.0001)
    }

    @Test
    fun testProceduralCanvasStyleEnumParsingAndDefaults() {
        assertEquals(ProceduralCanvasStyle.KAWARP, ProceduralCanvasStyle.fromPreference("KAWARP"))
        assertEquals(ProceduralCanvasStyle.SQUARE_TUNNEL, ProceduralCanvasStyle.fromPreference("SQUARE_TUNNEL"))
        assertEquals(ProceduralCanvasStyle.SQUARE_TUNNEL, ProceduralCanvasStyle.fromPreference("square_tunnel"))
        assertEquals(ProceduralCanvasStyle.KAWARP, ProceduralCanvasStyle.fromPreference("UNKNOWN_STYLE"))
        assertEquals(ProceduralCanvasStyle.KAWARP, ProceduralCanvasStyle.fromPreference(null))
    }

    @Test
    fun testCanvasConfigurationStyleDefaults() {
        val config = CanvasConfiguration()
        assertEquals(ProceduralCanvasStyle.KAWARP, config.proceduralStyle)
        assertTrue(config.proceduralFallback)
    }
}
