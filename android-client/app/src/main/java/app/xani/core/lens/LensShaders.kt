package app.xani.core.lens

/**
 * GLSL ES 3.00 sources. Texture coordinates are bottom-left origin; every
 * pass that samples the camera frame goes through `uTexMatrix`, which carries
 * the SurfaceTexture transform plus our crop / rotate / mirror.
 */
internal object LensShaders {
    const val VERT = """#version 300 es
in vec2 aPos;
in vec2 aTex;
uniform mat4 uTexMatrix;
out vec2 vTex;
void main() {
    gl_Position = vec4(aPos, 0.0, 1.0);
    vTex = (uTexMatrix * vec4(aTex, 0.0, 1.0)).xy;
}
"""

    const val FRAG_OES = """#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require
precision mediump float;
uniform samplerExternalOES uTex;
in vec2 vTex;
out vec4 o;
void main() { o = texture(uTex, vTex); }
"""

    const val FRAG_2D = """#version 300 es
precision mediump float;
uniform sampler2D uTex;
in vec2 vTex;
out vec4 o;
void main() { o = texture(uTex, vTex); }
"""

    /** 7×7 bilateral on the half-resolution frame: the skin "smooth" layer. */
    const val FRAG_BILATERAL = """#version 300 es
precision highp float;
uniform sampler2D uTex;
uniform vec2 uTexel;
uniform float uRange;
in vec2 vTex;
out vec4 o;
const vec3 LUMA = vec3(0.299, 0.587, 0.114);
void main() {
    vec3 c = texture(uTex, vTex).rgb;
    float lc = dot(c, LUMA);
    vec3 sum = vec3(0.0);
    float wsum = 0.0;
    float inv2r = 1.0 / (2.0 * uRange * uRange);
    for (int y = -3; y <= 3; y++) {
        for (int x = -3; x <= 3; x++) {
            vec2 off = vec2(float(x), float(y)) * uTexel * 1.5;
            vec3 s = texture(uTex, vTex + off).rgb;
            float d = dot(s, LUMA) - lc;
            float w = exp(-float(x * x + y * y) / 18.0) * exp(-(d * d) * inv2r);
            sum += s * w;
            wsum += w;
        }
    }
    o = vec4(sum / max(wsum, 1e-4), 1.0);
}
"""

    /**
     * 9-tap gaussian, one direction per pass (`uDir` = texel × axis). Blurs
     * all four channels: the background path stores colour premultiplied by
     * the background weight in rgb and the weight itself in alpha, so the
     * composite can renormalise and get a blur with no person bleed.
     */
    const val FRAG_BLUR = """#version 300 es
precision mediump float;
uniform sampler2D uTex;
uniform vec2 uDir;
in vec2 vTex;
out vec4 o;
void main() {
    vec4 c = texture(uTex, vTex) * 0.2270270270;
    c += texture(uTex, vTex + uDir * 1.3846153846) * 0.3162162162;
    c += texture(uTex, vTex - uDir * 1.3846153846) * 0.3162162162;
    c += texture(uTex, vTex + uDir * 3.2307692308) * 0.0702702703;
    c += texture(uTex, vTex - uDir * 3.2307692308) * 0.0702702703;
    o = c;
}
"""

    /**
     * Prepares the frame for the portrait blur: person knocked out, colour
     * premultiplied by the background weight (weight kept in alpha). Blurring
     * this instead of the raw frame is what removes the colour halo the
     * person would otherwise smear around their own silhouette.
     */
    const val FRAG_BGPREP = """#version 300 es
precision mediump float;
uniform sampler2D uTex;
uniform sampler2D uSeg;
in vec2 vTex;
out vec4 o;
void main() {
    float person = smoothstep(0.30, 0.62, texture(uSeg, vTex).r);
    float w = 1.0 - person;
    o = vec4(texture(uTex, vTex).rgb * w, w);
}
"""

    /** Temporal blend of the segmentation mask: prev × (1-k) + new × k. */
    const val FRAG_MIX = """#version 300 es
precision mediump float;
uniform sampler2D uPrev;
uniform sampler2D uNext;
uniform float uMix;
uniform int uFlipNext;
in vec2 vTex;
out vec4 o;
void main() {
    vec2 n = uFlipNext == 1 ? vec2(vTex.x, 1.0 - vTex.y) : vTex;
    float a = texture(uPrev, vTex).r;
    float b = texture(uNext, n).r;
    o = vec4(vec3(mix(a, b, uMix)), 1.0);
}
"""

    /** Face-anchored art, premultiplied alpha. */
    const val VERT_STICKER = """#version 300 es
in vec2 aPos;
in vec2 aTex;
out vec2 vTex;
void main() {
    gl_Position = vec4(aPos, 0.0, 1.0);
    vTex = aTex;
}
"""

    const val FRAG_STICKER = """#version 300 es
precision mediump float;
uniform sampler2D uTex;
uniform float uAlpha;
in vec2 vTex;
out vec4 o;
void main() { o = texture(uTex, vTex) * uAlpha; }
"""

    /**
     * The main pass: reshape → skin → background → colour grade, in one
     * fragment program so the frame is read once at full resolution.
     */
    const val FRAG_BEAUTY = """#version 300 es
precision highp float;
in vec2 vTex;
out vec4 o;

uniform sampler2D uSrc;
uniform sampler2D uSmooth;
uniform sampler2D uSeg;
uniform sampler2D uBg;
uniform vec2 uSize;

uniform float uSmoothAmt;
uniform float uWhiten;
uniform float uSharpen;
uniform float uEyes;
uniform float uSlim;
uniform float uGlow;

uniform int uFaceCount;
uniform vec4 uFace[3];
uniform vec2 uEyeL[3];
uniform vec2 uEyeR[3];
uniform vec2 uChin[3];

uniform int uBgMode;
uniform vec3 uBgTop;
uniform vec3 uBgBottom;
uniform vec4 uBgRect;
uniform float uBgEdge;

uniform float uExposure;
uniform float uContrast;
uniform float uSaturation;
uniform float uTemp;
uniform float uTint;
uniform float uFade;
uniform float uVignette;
uniform float uHue;
uniform float uSplit;
uniform float uMono;
uniform float uGrain;
uniform float uTime;
uniform vec3 uShadow;
uniform vec3 uHigh;

const vec3 LUMA = vec3(0.299, 0.587, 0.114);

vec2 asp() { return vec2(uSize.x / uSize.y, 1.0); }

vec2 bulge(vec2 p, vec2 c, float radius, float k) {
    vec2 a = asp();
    vec2 d = (p - c) * a;
    float r = length(d) / max(radius, 1e-4);
    if (r >= 1.0) return p;
    float f = 1.0 - k * (1.0 - r) * (1.0 - r);
    return c + d * f / a;
}

vec2 push(vec2 p, vec2 from, vec2 toward, float radius, float k) {
    vec2 a = asp();
    vec2 d = (p - from) * a;
    float w = exp(-dot(d, d) / max(radius * radius, 1e-5));
    return p + (from - toward) * w * k;
}

vec2 warp(vec2 uv) {
    vec2 p = uv;
    for (int i = 0; i < 3; i++) {
        if (i >= uFaceCount) break;
        vec2 el = uEyeL[i];
        vec2 er = uEyeR[i];
        float ie = distance(el * asp(), er * asp());
        if (uEyes > 0.0) {
            p = bulge(p, el, ie * 0.46, uEyes);
            p = bulge(p, er, ie * 0.46, uEyes);
        }
        if (uSlim > 0.0) {
            vec2 c = uFace[i].xy;
            vec2 chin = uChin[i];
            vec2 across = (el - er) * 0.62;
            vec2 lift = (c - chin) * 0.38;
            vec2 jawL = chin + across + lift;
            vec2 jawR = chin - across + lift;
            p = push(p, jawL, c, ie * 0.95, uSlim * 0.55);
            p = push(p, jawR, c, ie * 0.95, uSlim * 0.55);
        }
    }
    return p;
}

float faceMask(vec2 uv) {
    float m = 0.0;
    for (int i = 0; i < 3; i++) {
        if (i >= uFaceCount) break;
        vec2 d = (uv - uFace[i].xy) * asp();
        vec2 r = uFace[i].zw * asp();
        float e = length(d / max(r, vec2(1e-4)));
        m = max(m, 1.0 - smoothstep(0.80, 1.12, e));
    }
    return m;
}

float skinProb(vec3 c) {
    float y = dot(c, LUMA);
    float cb = 0.564 * (c.b - y) + 0.5;
    float cr = 0.713 * (c.r - y) + 0.5;
    float m = smoothstep(0.30, 0.38, cb) * (1.0 - smoothstep(0.50, 0.57, cb))
            * smoothstep(0.51, 0.56, cr) * (1.0 - smoothstep(0.67, 0.74, cr));
    return clamp(m * 1.35, 0.0, 1.0);
}

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

// Joint-bilateral upsample of the low-res person mask, guided by the full-res
// image: each mask tap only counts where the image luma matches the centre
// pixel, so the mask edge snaps to the real hair/shoulder edge instead of
// being a blurry 256-px stair step scaled up.
float segGuided(vec2 uv, vec3 ref) {
    vec2 texel = 1.0 / vec2(textureSize(uSeg, 0));
    float lr = dot(ref, LUMA);
    float sum = 0.0;
    float wsum = 0.0;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 off = vec2(float(x), float(y)) * texel;
            float s = texture(uSeg, uv + off).r;
            float l = dot(texture(uSrc, uv + off).rgb, LUMA);
            float w = exp(-float(x * x + y * y) * 0.4 - abs(l - lr) * 12.0);
            sum += s * w;
            wsum += w;
        }
    }
    return sum / max(wsum, 1e-4);
}

vec3 hueRotate(vec3 c, float h) {
    float a = h * 6.2831853;
    float cs = cos(a), sn = sin(a);
    mat3 toYIQ = mat3(0.299, 0.596, 0.211, 0.587, -0.274, -0.523, 0.114, -0.322, 0.312);
    mat3 toRGB = mat3(1.0, 1.0, 1.0, 0.956, -0.272, -1.106, 0.621, -0.647, 1.703);
    vec3 yiq = toYIQ * c;
    yiq.yz = mat2(cs, -sn, sn, cs) * yiq.yz;
    return toRGB * yiq;
}

void main() {
    vec2 uv = warp(vTex);
    vec3 src = texture(uSrc, uv).rgb;
    vec3 col = src;

    if (uSmoothAmt > 0.0 || uSharpen > 0.0 || uWhiten > 0.0 || uGlow > 0.0) {
        vec3 sm = texture(uSmooth, uv).rgb;
        float region = uFaceCount > 0 ? faceMask(uv) : 0.7;
        float skin = region * skinProb(src);
        vec3 hp = src - sm;
        // Smooth the blotch, keep the pore: the bilateral kills mid-scale
        // unevenness, and adding a slice of the fine high-pass back on top is
        // what separates "clear skin" from the waxy all-blur look.
        col = mix(src, sm, min(1.0, uSmoothAmt * 1.18) * skin);
        col += hp * (0.26 * uSmoothAmt * skin);
        // Clarity: full strength off-skin (hair, eyes, lashes), half on skin —
        // features stay crisp without resurrecting what was just smoothed.
        col += hp * uSharpen * (1.5 * (1.0 - skin) + 0.55 * skin);
        col = mix(col, pow(max(col, vec3(0.0)), vec3(1.0 - 0.40 * uWhiten)), skin);
        float lum = dot(col, LUMA);
        col += skin * uGlow * 0.14 * smoothstep(0.42, 0.92, lum) * vec3(1.0, 0.93, 0.86);
    }

    if (uBgMode > 0) {
        // Threshold centred below 0.5: a borderline pixel stays with the
        // person, so the replacement sits behind the body, never on it.
        float a = segGuided(uv, src);
        a = smoothstep(0.42 - uBgEdge, 0.42 + uBgEdge, a);
        vec3 bg;
        if (uBgMode == 1) {
            vec4 bb = texture(uBg, uv);
            bg = bb.rgb / max(bb.a, 0.04);
        } else if (uBgMode == 2) {
            bg = mix(uBgBottom, uBgTop, uv.y);
            bg += (hash(uv * uSize) - 0.5) / 160.0;
        } else {
            vec2 b = uv * uBgRect.xy + uBgRect.zw;
            bg = texture(uBg, vec2(b.x, 1.0 - b.y)).rgb;
        }
        if (uBgMode != 1) {
            // Soft contact shadow cast down-left of the person grounds the
            // cutout in the replaced scene instead of floating on a print.
            float sh = texture(uSeg, uv + vec2(0.030, 0.070)).r;
            bg *= 1.0 - 0.22 * sh;
        }
        col = mix(bg, col, a);
    }

    // ── grade ───────────────────────────────────────────────────────────────
    // Exposure first, with headroom kept above 1.0 so a bright window is
    // rolled off at the end instead of flat-clipping to white here.
    col = max(col, vec3(0.0)) * exp2(uExposure * 1.6);

    // White balance as a channel gain, not an offset: an offset tints the
    // blacks and milks the shadows, a gain leaves neutrals neutral.
    col *= vec3(1.0 + uTemp * 0.16 + uTint * 0.05,
                1.0 - uTint * 0.09,
                1.0 - uTemp * 0.16 + uTint * 0.05);

    // Contrast on an S-curve. A straight (x-0.5)*k slams highlights into
    // white and crushes shadows; the cubic keeps the shoulder and the toe,
    // which is most of the difference between "filter" and "grade".
    if (uContrast > 0.0) {
        vec3 x = clamp(col, 0.0, 1.0);
        col = mix(col, x * x * (3.0 - 2.0 * x), min(uContrast * 1.6, 1.0));
    } else if (uContrast < 0.0) {
        col = mix(col, col * 0.62 + 0.19, min(-uContrast * 1.6, 1.0));
    }

    if (uHue != 0.0) col = hueRotate(col, uHue);

    // Vibrance, not saturation: pixels that are already vivid get pushed
    // less, so colour comes up without turning lips and sky into neon.
    if (uSaturation != 0.0) {
        float mx = max(max(col.r, col.g), col.b);
        float mn = min(min(col.r, col.g), col.b);
        float chroma = clamp(mx - mn, 0.0, 1.0);
        float amt = uSaturation * (uSaturation > 0.0 ? 1.0 - chroma * 0.55 : 1.0);
        col = mix(vec3(dot(col, LUMA)), col, 1.0 + amt);
    }

    col = mix(col, vec3(dot(col, LUMA)), uMono);

    // Split toning weighted to the ends of the range: squaring the weights
    // keeps the tone out of the midtones, which is where skin lives.
    if (uSplit > 0.0) {
        float l = clamp(dot(col, LUMA), 0.0, 1.0);
        float lo = (1.0 - l) * (1.0 - l);
        float hi = l * l;
        col += uSplit * (uShadow - 0.5) * 0.95 * lo;
        col += uSplit * (uHigh - 0.5) * 0.95 * hi;
    }

    // Matte film: lift the toe and pull the shoulder in together.
    if (uFade > 0.0) col = mix(col, col * 0.80 + 0.105, uFade);

    if (uVignette > 0.0) {
        vec2 d = (uv - 0.5) * vec2(max(uSize.x / uSize.y, 1.0), max(uSize.y / uSize.x, 1.0));
        float v = length(d) * 1.41421356;
        col *= 1.0 - uVignette * smoothstep(0.30, 1.05, v) * 0.8;
    }

    // Grain lives in the midtones on film; spraying it into blacks reads as
    // sensor noise, which is the opposite of the intended look.
    if (uGrain > 0.0) {
        float lg = clamp(dot(col, LUMA), 0.0, 1.0);
        float shape = 1.0 - abs(lg * 2.0 - 1.0);
        col += (hash(uv * uSize + uTime) - 0.5) * uGrain * 0.16 * shape;
    }

    // Highlight rolloff: whatever exposure and grading pushed above 1.0 is
    // compressed back in, so bright skin keeps its shading.
    col = max(col, vec3(0.0));
    col = col / (1.0 + max(vec3(0.0), col - 1.0));
    o = vec4(clamp(col, 0.0, 1.0), 1.0);
}
"""
}
