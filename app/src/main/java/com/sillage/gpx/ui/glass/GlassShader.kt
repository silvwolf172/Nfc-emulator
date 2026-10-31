package com.sillage.gpx.ui.glass

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * Shader AGSL de lentille : près du bord, la lumière est déviée comme à travers un dôme de verre,
 * avec une légère dispersion chromatique et un liseré de Fresnel.
 */
private const val LENS_SHADER = """
uniform shader content;
uniform float2 size;
uniform float2 origin;
uniform float radius;
uniform float band;
uniform float strength;
uniform float dispersion;
uniform float fresnel;

float sdRoundRect(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + float2(r, r);
    return min(max(q.x, q.y), 0.0) + length(max(q, float2(0.0, 0.0))) - r;
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 p = coord - origin - halfSize;
    float d = sdRoundRect(p, halfSize, radius);
    if (d > 0.5) {
        return content.eval(coord);
    }
    float depth = clamp(-d / band, 0.0, 1.0);
    float x = 1.0 - depth;
    // Profil circulaire : quasi nul au centre, très pentu sur le bord.
    float bend = 1.0 - sqrt(max(0.0, 1.0 - x * x));

    float2 e = float2(0.75, 0.0);
    float2 n = float2(
        sdRoundRect(p + e.xy, halfSize, radius) - sdRoundRect(p - e.xy, halfSize, radius),
        sdRoundRect(p + e.yx, halfSize, radius) - sdRoundRect(p - e.yx, halfSize, radius));
    float nl = length(n);
    n = nl > 0.0001 ? n / nl : float2(0.0, 0.0);

    float2 disp = n * bend * strength;
    half4 c = content.eval(coord + disp);
    if (dispersion > 0.0) {
        half r = content.eval(coord + disp * (1.0 + dispersion)).r;
        half b = content.eval(coord + disp * (1.0 - dispersion)).b;
        c = half4(r, c.g, b, c.a);
    }
    // Liseré lumineux (approximation de Fresnel) sur l'arête du verre.
    half f = half(pow(bend, 2.2) * fresnel);
    c.rgb = mix(c.rgb, half3(c.a), f);
    return c;
}
"""

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class LensShader {
    private val shader = RuntimeShader(LENS_SHADER)

    fun effect(
        width: Float,
        height: Float,
        margin: Float,
        radius: Float,
        band: Float,
        strength: Float,
        dispersion: Float,
        fresnel: Float,
        input: RenderEffect?,
    ): RenderEffect {
        shader.setFloatUniform("size", width, height)
        shader.setFloatUniform("origin", margin, margin)
        shader.setFloatUniform("radius", radius)
        shader.setFloatUniform("band", band.coerceAtLeast(1f))
        shader.setFloatUniform("strength", strength)
        shader.setFloatUniform("dispersion", dispersion)
        shader.setFloatUniform("fresnel", fresnel)
        val lens = RenderEffect.createRuntimeShaderEffect(shader, "content")
        return if (input != null) RenderEffect.createChainEffect(lens, input) else lens
    }
}

/** Flou + saturation (« vibrancy ») : disponible dès Android 12. */
@RequiresApi(Build.VERSION_CODES.S)
internal fun frostEffect(blurPx: Float, saturation: Float): RenderEffect? {
    val blur = if (blurPx > 0.5f) RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP) else null
    if (saturation == 1f) return blur
    val matrix = ColorMatrix().apply { setSaturation(saturation) }
    val filter = ColorMatrixColorFilter(matrix)
    return if (blur != null) RenderEffect.createColorFilterEffect(filter, blur) else RenderEffect.createColorFilterEffect(filter)
}
