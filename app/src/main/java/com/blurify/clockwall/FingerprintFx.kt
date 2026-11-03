package com.blurify.clockwall

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi

/**
 * Experimental (CHROMATIC_FX builds only): a glass lens that sits under the system fingerprint icon on the lock
 * screen, and the chromatic shock wave that runs out of it when the phone is unlocked.
 *
 * The whole finished frame is drawn into one RenderNode and passed through this shader, so the existing wallpaper
 * is untouched underneath; with every strength at 0 the shader returns the frame as is.
 *
 * Timing and look were measured from the user's 30 fps reference recording (frames 151-161):
 *  - the wave is a circle in screen-uv space (so a tall ellipse on the phone) whose front moves out at about
 *    2.4 screen widths per second;
 *  - behind the front the picture is zoom blurred along the ray from the finger and swollen outward;
 *  - a dispersion band (leading edge yellow -> pale cyan -> azure -> deep blue at the trailing edge) shows as two
 *    lobes level with the fingerprint, on its left and right;
 *  - the lens magnifies about 1.2x and folds the picture over at the rim; inside it the picture is stirred by a
 *    liquid swirl whose centre keeps drifting around (from the user's close-up recording of the lens);
 *  - the rim is a dark blue band, a thin cold white edge and a faint warm halo just outside.
 */
@RequiresApi(33)
class FingerprintFx {

    private val shader = RuntimeShader(AGSL)

    /** Times in seconds since the wave started. */
    class Wave(val radius: Float, val distort: Float, val blur: Float, val color: Float)

    fun wave(t: Float): Wave? {
        if (t < 0f || t > WAVE_END) return null
        val r = 2.4f * t + 0.17f * (t / 0.06f).coerceAtMost(1f)
        return Wave(
            radius = r,
            distort = smooth(t / 0.03f) * (1f - smooth((t - 0.14f) / 0.20f)),
            blur = smooth(t / 0.04f) * (1f - smooth((t - 0.10f) / 0.22f)),
            color = smooth((t - 0.02f) / 0.05f) * (1f - smooth((t - 0.13f) / 0.09f)),
        )
    }

    fun effect(
        w: Int, h: Int, cx: Float, cy: Float, lensR: Float, rimPx: Float, lensA: Float, wave: Wave?, stripH: Float,
        time: Float,
    ): RenderEffect {
        shader.setFloatUniform("time", time)
        shader.setFloatUniform("res", w.toFloat(), h.toFloat())
        shader.setFloatUniform("fp", cx, cy)
        shader.setFloatUniform("lensR", lensR)
        shader.setFloatUniform("rimPx", rimPx)
        shader.setFloatUniform("lensA", lensA)
        shader.setFloatUniform("waveR", wave?.radius ?: 0f)
        shader.setFloatUniform("distA", wave?.distort ?: 0f)
        shader.setFloatUniform("blurA", wave?.blur ?: 0f)
        shader.setFloatUniform("colorA", wave?.color ?: 0f)
        shader.setFloatUniform("stripH", stripH)
        // the effect snapshots the uniforms, so it is rebuilt for every frame
        return RenderEffect.createRuntimeShaderEffect(shader, "content")
    }

    companion object {
        const val WAVE_END = 0.36f

        private fun smooth(x: Float): Float {
            val t = x.coerceIn(0f, 1f)
            return t * t * (3f - 2f * t)
        }

        private const val AGSL = """
uniform shader content;
uniform float2 res;     // surface size, px
uniform float2 fp;      // fingerprint centre, px
uniform float lensR;    // lens radius, px
uniform float rimPx;    // lens rim width, px
uniform float lensA;    // lens opacity
uniform float waveR;    // wave front radius, screen-uv
uniform float distA;    // swell strength
uniform float blurA;    // zoom blur strength
uniform float colorA;   // dispersion band strength
uniform float stripH;   // px, height of the two colour lobes
uniform float time;     // seconds, drives the liquid swirl inside the lens

const float BAND = 0.15;   // uv width of the dispersion band
const float SWELL = 0.26;  // uv width of the swell behind the front

float4 src(float2 p) {
    return content.eval(clamp(p, float2(0.5), res - float2(0.5)));
}

// s = 0 at the leading edge of the band, 1.25 at its trailing edge. rgb + alpha.
float4 tint(float s) {
    float4 k0 = float4(0.91, 0.74, 0.17, 0.00);
    float4 k1 = float4(0.91, 0.74, 0.17, 0.35);
    float4 k2 = float4(0.91, 0.83, 0.33, 0.50);
    float4 k3 = float4(0.84, 0.91, 0.55, 0.62);
    float4 k4 = float4(0.67, 0.91, 0.73, 0.75);
    float4 k5 = float4(0.31, 0.76, 0.78, 0.80);
    float4 k6 = float4(0.10, 0.57, 0.84, 0.86);
    float4 k7 = float4(0.05, 0.39, 0.92, 0.90);
    float4 k8 = float4(0.05, 0.27, 0.78, 0.80);
    float4 k9 = float4(0.12, 0.24, 0.47, 0.45);
    float4 k10 = float4(0.12, 0.24, 0.47, 0.00);
    if (s < 0.10) return mix(k0, k1, s / 0.10);
    if (s < 0.25) return mix(k1, k2, (s - 0.10) / 0.15);
    if (s < 0.38) return mix(k2, k3, (s - 0.25) / 0.13);
    if (s < 0.50) return mix(k3, k4, (s - 0.38) / 0.12);
    if (s < 0.65) return mix(k4, k5, (s - 0.50) / 0.15);
    if (s < 0.78) return mix(k5, k6, (s - 0.65) / 0.13);
    if (s < 0.88) return mix(k6, k7, (s - 0.78) / 0.10);
    if (s < 1.00) return mix(k7, k8, (s - 0.88) / 0.12);
    if (s < 1.12) return mix(k8, k9, (s - 1.00) / 0.12);
    return mix(k9, k10, clamp((s - 1.12) / 0.13, 0.0, 1.0));
}

// the wallpaper as seen through the wave at p
float4 wave(float2 p) {
    float2 duv = (p - fp) / res;
    float d = length(duv);
    float2 dir = d > 0.00001 ? duv / d : float2(0.0, -1.0);
    float2 dpx = dir * res;   // one uv step along the ray from the finger, in px

    // swell: the picture just behind the front is pushed outward
    float x = (waveR - d) / SWELL;
    float swell = (x > 0.0 && x < 1.0) ? sin(3.14159265 * x) * (1.0 - x) * 1.77 : 0.0;
    float2 q = p - dpx * (0.030 * distA * swell);

    // zoom blur inside the wave, streak length growing with the distance from the finger
    float inside = 1.0 - smoothstep(-0.05, 0.03, d - waveR);
    float len = blurA * inside * min(d, 0.5) * 0.30;
    float4 col;
    if (len * res.x > 0.75) {
        float4 acc = float4(0.0);
        for (int i = 0; i < 10; i++) {
            acc += src(q - dpx * (len * float(i) / 9.0));
        }
        col = acc * 0.1;
    } else {
        col = src(q);
    }

    // dispersion band: two lobes level with the fingerprint
    if (colorA > 0.001) {
        float s = (waveR - d) / BAND;
        if (s > 0.0 && s < 1.25) {
            float dy = (p.y - fp.y) / stripH;
            float w = colorA * exp(-dy * dy);
            float sh = 0.010 * w;
            col.r = mix(col.r, src(q + dpx * sh).r, w);
            col.b = mix(col.b, src(q - dpx * sh).b, w);
            float4 t = tint(s);
            col.rgb = mix(col.rgb, t.rgb, t.a * w);
        }
    }
    return col;
}

// rotate q around c, strongest in the middle, nothing at radius R
float2 twirl(float2 q, float2 c, float R, float a) {
    float2 u = q - c;
    float rho = length(u) / R;
    if (rho >= 1.0) return q;
    float f = (1.0 - rho);
    f = f * f * a;
    float cs = cos(f);
    float sn = sin(f);
    return c + float2(u.x * cs - u.y * sn, u.x * sn + u.y * cs);
}

half4 main(float2 p) {
    float4 base = wave(p);
    if (lensA < 0.001) return half4(base);
    float2 v = p - fp;
    float d = length(v);
    if (d > lensR * 1.08 + 1.5) return half4(base);

    float r = d / lensR;
    float rc = min(r, 1.0);
    float2 n = d > 0.0 ? v / d : float2(0.0, -1.0);
    float seg = 0.5 + 0.5 * sin(atan(n.y, n.x) * 7.0 + time * 0.6);   // the rim glints in segments

    // faint warm halo just outside the rim
    if (r > 1.0) {
        float halo = (1.0 - smoothstep(1.0, 1.08, r)) * lensA;
        base.rgb = clamp(base.rgb + float3(0.09, 0.07, -0.03) * halo, 0.0, 1.0);
        if (d > lensR + 1.5) return half4(base);
    }

    // magnify ~1.2x in the middle and fold the picture over in a thin band at the rim
    float m = 0.82 - 0.22 * rc * rc * rc * rc * rc;
    float2 pl = fp + v * m;

    // liquid swirl: two vortices whose centres drift inside the lens; the rim keeps a clean refraction
    float calm = 1.0 - smoothstep(0.62, 0.92, rc);
    float2 c1 = fp + lensR * 0.34 * float2(sin(time * 1.7 + 0.7) * cos(time * 0.6), sin(time * 1.3 + 2.1));
    float2 c2 = fp + lensR * 0.36 * float2(cos(time * 1.1 + 4.0), sin(time * 1.9 + 0.3) * cos(time * 0.5 + 1.0));
    float a1 = (1.35 + 0.5 * sin(time * 1.1)) * calm;
    float a2 = (-0.9 + 0.4 * sin(time * 0.9 + 1.3)) * calm;
    float4 lc = float4(0.0);
    for (int j = 0; j < 4; j++) {
        float k = 1.0 - 0.12 * float(j);   // a few taps along the swirl smear the picture into streaks
        lc += wave(twirl(twirl(pl, c1, lensR * 0.62, a1 * k), c2, lensR * 0.5, a2 * k));
    }
    lc *= 0.25;
    if (r > 0.78) {   // dispersion near the rim
        float e = smoothstep(0.78, 1.0, rc) * 0.05;
        lc.r = wave(twirl(twirl(fp + v * (m + e), c1, lensR * 0.62, a1), c2, lensR * 0.5, a2)).r;
        lc.b = wave(twirl(twirl(fp + v * (m - e), c1, lensR * 0.62, a1), c2, lensR * 0.5, a2)).b;
    }
    lc.rgb = lc.rgb + 0.03 * (1.0 - lc.rgb);           // faint milky lift of the glass
    lc.rgb *= 1.0 - 0.08 * smoothstep(0.86, 0.97, rc);  // a touch darker just inside the rim

    // dark blue band just inside the rim
    float blue = smoothstep(0.86, 0.95, rc) * (1.0 - smoothstep(0.975, 0.995, r));
    lc.rgb = mix(lc.rgb, float3(0.16, 0.27, 0.58), blue * (0.30 + 0.25 * seg));

    // thin cold specular edge, brightest towards the top
    float edge = abs(d - (lensR - rimPx * 0.5));
    float rim = 1.0 - smoothstep(rimPx * 0.5 - 0.5, rimPx * 0.5 + 0.8, edge);
    float lit = abs(dot(n, float2(-0.35, -0.94)));
    float light = 0.45 + 0.55 * lit * sqrt(lit);
    float3 rimCol = mix(float3(0.95, 0.97, 1.0), float3(0.60, 0.74, 1.0), 0.25 + 0.5 * (1.0 - seg));
    lc.rgb = mix(lc.rgb, rimCol, rim * light * (0.38 + 0.32 * seg));

    float cover = 1.0 - smoothstep(lensR - 0.8, lensR + 0.8, d);
    return half4(mix(base, lc, cover * lensA));
}
"""
    }
}
