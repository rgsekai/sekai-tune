/*
 * Vendored from Kyant0/backdrop v2.0.0 (io.github.kyant0:backdrop)
 * https://github.com/Kyant0/backdrop — Copyright 2025 Kyant0, Apache License 2.0
 *
 * Vendored so the library ships as source with this app (binary AARs compiled
 * against older Compose broke at runtime) and to add a backdrop resolution
 * scale for cheaper effect rendering. KMP expect/actual declarations were
 * merged into this single Android source set. Package renamed accordingly.
 */
package moe.rgsekai.sekaitune.ui.component.backdrop.effects

import androidx.compose.ui.graphics.RenderEffect
import moe.rgsekai.sekaitune.ui.component.backdrop.BackdropEffectScope
import moe.rgsekai.sekaitune.ui.component.backdrop.RuntimeShader
import moe.rgsekai.sekaitune.ui.component.backdrop.internal.RuntimeShaderEffect
import moe.rgsekai.sekaitune.ui.component.backdrop.internal.chain
import moe.rgsekai.sekaitune.ui.component.backdrop.isRenderEffectSupported
import moe.rgsekai.sekaitune.ui.component.backdrop.isRuntimeShaderSupported
import kotlin.contracts.ExperimentalContracts
import org.intellij.lang.annotations.Language

fun BackdropEffectScope.effect(effect: RenderEffect) {
  if (!isRenderEffectSupported()) return

  renderEffect = renderEffect.chain(effect)
}

@OptIn(ExperimentalContracts::class)
fun BackdropEffectScope.runtimeShaderEffect(
  key: String,
  @Language("AGSL") shaderString: String,
  uniformShaderName: String,
  block: RuntimeShader.() -> Unit
) {
  if (!isRuntimeShaderSupported()) return

  val effect =
    RuntimeShaderEffect(
      runtimeShader = obtainRuntimeShader(key, shaderString).apply(block),
      uniformShaderName = uniformShaderName
    )
  renderEffect = renderEffect.chain(effect)
}
