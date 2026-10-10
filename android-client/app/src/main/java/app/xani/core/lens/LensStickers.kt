package app.xani.core.lens

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader

/**
 * Mask art drawn with the platform Canvas at first use: vector descriptions
 * instead of shipped PNGs, so the catalogue costs nothing in APK size and a
 * server pack can add a mask by naming shapes. Each bitmap is uploaded once
 * per GL context by the pipeline.
 *
 * The art aims past "flat vector sticker": every piece carries a light model
 * (soft drop shadow, gradient volume, specular highlights) and material
 * detail — individual fur strokes on ears, satin folds on the bow, feathers
 * on the wings, bevelled metal on the crown — because a face overlay reads
 * as cheap the moment it looks like a solid-colour print.
 */
internal object LensStickers {
    const val Sunglasses = "art.sunglasses"
    const val HeartGlasses = "art.heart_glasses"
    const val NeonGlasses = "art.neon_glasses"
    const val CatEars = "art.cat_ears"
    const val CatNose = "art.cat_nose"
    const val DogEars = "art.dog_ears"
    const val DogNose = "art.dog_nose"
    const val BunnyEars = "art.bunny_ears"
    const val Crown = "art.crown"
    const val FlowerCrown = "art.flower_crown"
    const val DevilHorns = "art.devil_horns"
    const val Moustache = "art.moustache"
    const val Blush = "art.blush"
    const val Freckles = "art.freckles"
    const val StarGlasses = "art.star_glasses"
    const val Halo = "art.halo"
    const val Sparkle = "art.sparkle"
    const val Wings = "art.wings"
    const val Bow = "art.bow"
    const val Tears = "art.tears"

    private const val W = 1024

    fun draw(id: String, aspect: Float): Bitmap {
        val h = (W * aspect).toInt().coerceAtLeast(8)
        val bmp = Bitmap.createBitmap(W, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val w = W.toFloat()
        val hf = h.toFloat()
        when (id) {
            Sunglasses -> sunglasses(c, w, hf)
            HeartGlasses -> heartGlasses(c, w, hf)
            NeonGlasses -> neonGlasses(c, w, hf)
            CatEars -> catEars(c, w, hf)
            CatNose -> catNose(c, w, hf)
            DogEars -> dogEars(c, w, hf)
            DogNose -> dogNose(c, w, hf)
            BunnyEars -> bunnyEars(c, w, hf)
            Crown -> crown(c, w, hf)
            FlowerCrown -> flowerCrown(c, w, hf)
            DevilHorns -> devilHorns(c, w, hf)
            Moustache -> moustache(c, w, hf)
            Blush -> blush(c, w, hf)
            Freckles -> freckles(c, w, hf)
            StarGlasses -> starGlasses(c, w, hf)
            Halo -> halo(c, w, hf)
            Sparkle -> sparkle(c, w, hf)
            Wings -> wings(c, w, hf)
            Bow -> bow(c, w, hf)
            Tears -> tears(c, w, hf)
        }
        return bmp
    }

    // ─── toolkit ─────────────────────────────────────────────────────────────

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }

    private fun stroke(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color; style = Paint.Style.STROKE; strokeWidth = width; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }

    /** Deterministic noise so the art is identical on every draw. */
    private class Rng(private var seed: Int) {
        fun next(): Float {
            seed = (seed * 1103515245 + 12345) and 0x7fffffff
            return (seed % 10000) / 10000f
        }

        fun range(a: Float, b: Float): Float = a + next() * (b - a)
    }

    /** Point on a quadratic bezier — used to walk ear silhouettes for fur. */
    private fun quadAt(x0: Float, y0: Float, cx: Float, cy: Float, x1: Float, y1: Float, t: Float): PointF {
        val u = 1f - t
        return PointF(u * u * x0 + 2f * u * t * cx + t * t * x1, u * u * y0 + 2f * u * t * cy + t * t * y1)
    }

    /** One tapered hair: a thin triangle from a wide root to a point. */
    private fun hair(c: Canvas, x: Float, y: Float, dx: Float, dy: Float, len: Float, root: Float, color: Int) {
        val m = kotlin.math.hypot(dx, dy).coerceAtLeast(1e-4f)
        val ux = dx / m
        val uy = dy / m
        val nx = -uy * root / 2f
        val ny = ux * root / 2f
        val p = Path()
        p.moveTo(x + nx, y + ny)
        p.lineTo(x - nx, y - ny)
        p.lineTo(x + ux * len, y + uy * len)
        p.close()
        c.drawPath(p, fill(color))
    }

    /** A classic concave four-point sparkle with a soft glow beneath it. */
    private fun fourPoint(c: Canvas, cx: Float, cy: Float, r: Float, rot: Float, core: Int, glow: Int) {
        c.save()
        c.rotate(rot, cx, cy)
        val p = Path()
        val k = r * 0.14f
        p.moveTo(cx, cy - r)
        p.quadTo(cx + k, cy - k, cx + r, cy)
        p.quadTo(cx + k, cy + k, cx, cy + r)
        p.quadTo(cx - k, cy + k, cx - r, cy)
        p.quadTo(cx - k, cy - k, cx, cy - r)
        p.close()
        c.drawPath(p, fill(glow).apply { maskFilter = BlurMaskFilter(r * 0.5f, BlurMaskFilter.Blur.NORMAL) })
        c.drawPath(p, fill(core))
        c.drawCircle(cx, cy, r * 0.13f, fill(0xFFFFFFFF.toInt()))
        c.restore()
    }

    // ─── glasses ─────────────────────────────────────────────────────────────

    private fun sunglasses(c: Canvas, w: Float, h: Float) {
        val lensW = w * 0.40f
        val lensH = h * 0.72f
        val top = h * 0.14f
        val r = lensH * 0.42f
        val clip = Path()
        for (side in 0..1) {
            val left = if (side == 0) w * 0.07f else w * 0.53f
            val rect = RectF(left, top, left + lensW, top + lensH)
            // Glass volume: graphite at the brow, near-black core, a faint
            // bounce at the cheek — a flat black fill is what reads "print".
            val glass = fill(0xFF05070C.toInt()).apply {
                shader = LinearGradient(
                    rect.left, rect.top, rect.left, rect.bottom,
                    intArrayOf(0xFF2B3647.toInt(), 0xFF0B0F17.toInt(), 0xFF04060A.toInt(), 0xFF18212F.toInt()),
                    floatArrayOf(0f, 0.40f, 0.78f, 1f), Shader.TileMode.CLAMP,
                )
                setShadowLayer(h * 0.06f, 0f, h * 0.04f, 0x59000000)
            }
            c.drawRoundRect(rect, r, r, glass)
            clip.reset()
            clip.addRoundRect(rect, r, r, Path.Direction.CW)
            c.save()
            c.clipPath(clip)
            // Sky reflection across the upper third plus two diagonal glints.
            val band = fill(0).apply {
                shader = LinearGradient(
                    rect.left, rect.top, rect.left, rect.top + lensH * 0.52f,
                    intArrayOf(0x5CE8F1FF, 0x17E8F1FF, 0x00E8F1FF), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP,
                )
            }
            c.drawRect(RectF(rect.left, rect.top, rect.right, rect.top + lensH * 0.52f), band)
            c.save()
            c.rotate(-24f, rect.centerX(), rect.centerY())
            c.drawRoundRect(
                RectF(rect.left + lensW * 0.10f, rect.top - lensH * 0.2f, rect.left + lensW * 0.30f, rect.bottom + lensH * 0.2f),
                lensW * 0.1f, lensW * 0.1f, fill(0x26FFFFFF),
            )
            c.drawRoundRect(
                RectF(rect.left + lensW * 0.38f, rect.top - lensH * 0.2f, rect.left + lensW * 0.46f, rect.bottom + lensH * 0.2f),
                lensW * 0.04f, lensW * 0.04f, fill(0x17FFFFFF),
            )
            c.restore()
            c.restore()
            // Frame bevel: gradient stroke, then a hairline top highlight.
            val frame = stroke(0xFF14181F.toInt(), h * 0.075f).apply {
                shader = LinearGradient(0f, rect.top, 0f, rect.bottom, intArrayOf(0xFF3C4654.toInt(), 0xFF0F1218.toInt()), null, Shader.TileMode.CLAMP)
            }
            c.drawRoundRect(rect, r, r, frame)
            val inset = RectF(rect.left + h * 0.045f, rect.top + h * 0.045f, rect.right - h * 0.045f, rect.bottom - h * 0.045f)
            val hi = stroke(0x66FFFFFF, h * 0.018f).apply {
                shader = LinearGradient(0f, rect.top, 0f, rect.centerY(), intArrayOf(0x73FFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP)
            }
            c.drawRoundRect(inset, r * 0.85f, r * 0.85f, hi)
        }
        // Keyhole bridge and hinge stubs.
        val bridgeY = top + lensH * 0.24f
        val p = Path()
        p.moveTo(w * 0.465f, bridgeY + h * 0.05f)
        p.quadTo(w * 0.5f, bridgeY - h * 0.06f, w * 0.535f, bridgeY + h * 0.05f)
        c.drawPath(p, stroke(0xFF1A1F28.toInt(), h * 0.10f))
        c.drawPath(p, stroke(0x40FFFFFF, h * 0.025f))
        c.drawLine(w * 0.015f, bridgeY, w * 0.07f, bridgeY + h * 0.02f, stroke(0xFF232A36.toInt(), h * 0.07f))
        c.drawLine(w * 0.93f, bridgeY + h * 0.02f, w * 0.985f, bridgeY, stroke(0xFF232A36.toInt(), h * 0.07f))
    }

    private fun heart(path: Path, cx: Float, cy: Float, s: Float) {
        path.reset()
        path.moveTo(cx, cy + s * 0.75f)
        path.cubicTo(cx - s * 1.5f, cy - s * 0.3f, cx - s * 0.8f, cy - s * 1.3f, cx, cy - s * 0.45f)
        path.cubicTo(cx + s * 0.8f, cy - s * 1.3f, cx + s * 1.5f, cy - s * 0.3f, cx, cy + s * 0.75f)
        path.close()
    }

    private fun heartGlasses(c: Canvas, w: Float, h: Float) {
        val p = Path()
        val s = h * 0.44f
        for (side in 0..1) {
            val cx = if (side == 0) w * 0.27f else w * 0.73f
            val cy = h * 0.52f
            heart(p, cx, cy, s)
            // Candy-gloss heart: lit from the top-left, deep rose at the lobe.
            val body = fill(0xFFE1337B.toInt()).apply {
                shader = RadialGradient(
                    cx - s * 0.45f, cy - s * 0.55f, s * 2.1f,
                    intArrayOf(0xFFFF9EC6.toInt(), 0xFFEE4D8B.toInt(), 0xFFB81D5F.toInt(), 0xFF8E1247.toInt()),
                    floatArrayOf(0f, 0.42f, 0.78f, 1f), Shader.TileMode.CLAMP,
                )
                setShadowLayer(h * 0.05f, 0f, h * 0.035f, 0x4D000000)
            }
            c.drawPath(p, body)
            // Inner bottom shadow keeps the lobe round instead of flat.
            c.save()
            c.clipPath(p)
            c.drawCircle(cx, cy + s * 1.15f, s * 1.05f, fill(0x33470B2B))
            // Big soft specular plus a pin highlight.
            c.save()
            c.rotate(-22f, cx - s * 0.42f, cy - s * 0.5f)
            c.drawOval(
                RectF(cx - s * 0.78f, cy - s * 0.72f, cx - s * 0.12f, cy - s * 0.34f),
                fill(0x8CFFFFFF.toInt()).apply { maskFilter = BlurMaskFilter(s * 0.09f, BlurMaskFilter.Blur.NORMAL) },
            )
            c.restore()
            c.drawCircle(cx + s * 0.38f, cy - s * 0.52f, s * 0.09f, fill(0x9DFFFFFF.toInt()))
            c.restore()
            c.drawPath(p, stroke(0xFFFFFFFF.toInt(), h * 0.035f))
        }
        val bridge = Path()
        bridge.moveTo(w * 0.435f, h * 0.45f)
        bridge.quadTo(w * 0.5f, h * 0.36f, w * 0.565f, h * 0.45f)
        c.drawPath(bridge, stroke(0xFFC22960.toInt(), h * 0.06f))
    }

    private fun neonGlasses(c: Canvas, w: Float, h: Float) {
        val lensW = w * 0.40f
        val lensH = h * 0.72f
        val top = h * 0.14f
        val r = lensH * 0.3f
        // Three glow shells, wide → tight, then a near-white core: a single
        // blurred stroke never looks like light, layered falloff does.
        val glowWide = stroke(0x3522D3EE, h * 0.30f).apply { maskFilter = BlurMaskFilter(h * 0.26f, BlurMaskFilter.Blur.NORMAL) }
        val glowMid = stroke(0x7322D3EE, h * 0.14f).apply { maskFilter = BlurMaskFilter(h * 0.12f, BlurMaskFilter.Blur.NORMAL) }
        val glowTight = stroke(0xBB67E8F9.toInt(), h * 0.07f).apply { maskFilter = BlurMaskFilter(h * 0.05f, BlurMaskFilter.Blur.NORMAL) }
        val core = stroke(0xFFF0FDFF.toInt(), h * 0.032f)
        for (side in 0..1) {
            val left = if (side == 0) w * 0.07f else w * 0.53f
            val rect = RectF(left, top, left + lensW, top + lensH)
            // Faint magenta cast inside the lens sells "lit sign", not "shape".
            val lens = fill(0).apply {
                shader = LinearGradient(
                    rect.left, rect.top, rect.right, rect.bottom,
                    intArrayOf(0x3022D3EE, 0x14701A75, 0x26D946EF.toInt()), null, Shader.TileMode.CLAMP,
                )
            }
            c.drawRoundRect(rect, r, r, lens)
            c.drawRoundRect(rect, r, r, glowWide)
            c.drawRoundRect(rect, r, r, glowMid)
            c.drawRoundRect(rect, r, r, glowTight)
            c.drawRoundRect(rect, r, r, core)
            c.drawArc(RectF(rect.left + lensW * 0.1f, rect.top + lensH * 0.06f, rect.right - lensW * 0.1f, rect.bottom), 205f, 60f, false, stroke(0xB3FFFFFF.toInt(), h * 0.02f))
        }
        val by = h * 0.42f
        c.drawLine(w * 0.47f, by, w * 0.53f, by, glowMid)
        c.drawLine(w * 0.47f, by, w * 0.53f, by, core)
    }

    private fun starGlasses(c: Canvas, w: Float, h: Float) {
        for (side in 0..1) {
            val cx = if (side == 0) w * 0.28f else w * 0.72f
            val cy = h * 0.52f
            val r = h * 0.38f
            val p = starPath(cx, cy, r)
            // Gilded star: radial gold, bevelled light upper half, glow rim.
            c.drawPath(p, fill(0x59FBBF24).apply { maskFilter = BlurMaskFilter(r * 0.25f, BlurMaskFilter.Blur.NORMAL) })
            val gold = fill(0xFFF7C948.toInt()).apply {
                shader = RadialGradient(
                    cx - r * 0.25f, cy - r * 0.3f, r * 1.5f,
                    intArrayOf(0xFFFFF3C4.toInt(), 0xFFF7C948.toInt(), 0xFFD98E1B.toInt()),
                    floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
                )
                setShadowLayer(h * 0.04f, 0f, h * 0.03f, 0x40000000)
            }
            c.drawPath(p, gold)
            c.save()
            c.clipPath(p)
            c.drawRect(
                RectF(cx - r, cy - r, cx + r, cy),
                fill(0).apply {
                    shader = LinearGradient(0f, cy - r, 0f, cy, intArrayOf(0x66FFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP)
                },
            )
            c.restore()
            c.drawPath(p, stroke(0xFFB36A0F.toInt(), h * 0.018f))
            c.drawCircle(cx - r * 0.2f, cy - r * 0.28f, r * 0.08f, fill(0xC9FFFFFF.toInt()))
        }
        val bridge = Path()
        bridge.moveTo(w * 0.42f, h * 0.48f)
        bridge.quadTo(w * 0.5f, h * 0.40f, w * 0.58f, h * 0.48f)
        c.drawPath(bridge, stroke(0xFFE3A82E.toInt(), h * 0.05f))
    }

    private fun starPath(cx: Float, cy: Float, r: Float): Path {
        val p = Path()
        for (i in 0 until 10) {
            val a = Math.toRadians((-90 + i * 36).toDouble())
            val rad = if (i % 2 == 0) r else r * 0.45f
            val x = cx + (rad * Math.cos(a)).toFloat()
            val y = cy + (rad * Math.sin(a)).toFloat()
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        return p
    }

    // ─── animal ears & noses ─────────────────────────────────────────────────

    private fun catEars(c: Canvas, w: Float, h: Float) {
        val rng = Rng(11)
        for (side in 0..1) {
            val x0 = if (side == 0) w * 0.08f else w * 0.60f
            val x1 = x0 + w * 0.32f
            val tipX = if (side == 0) x0 + w * 0.07f else x1 - w * 0.07f
            val tipY = h * 0.06f
            val cOutX = x0 + (tipX - x0) * 0.3f
            val cOutY = h * 0.42f
            val cInX = x1 - (x1 - tipX) * 0.25f
            val cInY = h * 0.55f
            val base = Path()
            base.moveTo(x0, h)
            base.quadTo(cOutX, cOutY, tipX, tipY)
            base.quadTo(cInX, cInY, x1, h)
            base.close()
            val coat = fill(0xFF221C29.toInt()).apply {
                shader = LinearGradient(
                    x0, tipY, x0, h,
                    intArrayOf(0xFF473C52.toInt(), 0xFF2A2333.toInt(), 0xFF171220.toInt()),
                    floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP,
                )
                setShadowLayer(h * 0.045f, 0f, h * 0.02f, 0x40000000)
            }
            c.drawPath(base, coat)
            // Inner ear: soft pink shell, darker toward the canal.
            val ix0 = x0 + w * 0.085f
            val ix1 = x1 - w * 0.085f
            val itX = tipX + (if (side == 0) w * 0.02f else -w * 0.02f)
            val inner = Path()
            inner.moveTo(ix0, h * 0.99f)
            inner.quadTo(ix0 + (itX - ix0) * 0.4f, h * 0.5f, itX, h * 0.3f)
            inner.quadTo(ix1 - (ix1 - itX) * 0.3f, h * 0.62f, ix1, h * 0.99f)
            inner.close()
            val shell = fill(0xFFD86F9F.toInt()).apply {
                shader = RadialGradient(
                    (ix0 + ix1) / 2f, h * 0.95f, h * 0.8f,
                    intArrayOf(0xFF8E4169.toInt(), 0xFFD17AA3.toInt(), 0xFFF2B3CE.toInt()),
                    floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
                )
                maskFilter = BlurMaskFilter(h * 0.015f, BlurMaskFilter.Blur.NORMAL)
            }
            c.drawPath(inner, shell)
            // Fur: individual tapered hairs along both silhouettes, longer and
            // lighter toward the base, a tuft past the tip. This is the single
            // biggest difference between "triangle with gradient" and "ear".
            for (i in 0 until 30) {
                val t = i / 29f
                val pt = quadAt(x0, h, cOutX, cOutY, tipX, tipY, t)
                val outDir = if (side == 0) -1f else 1f
                val len = h * rng.range(0.05f, 0.12f) * (1.2f - t * 0.6f)
                hair(c, pt.x, pt.y, outDir * rng.range(0.5f, 1f), -rng.range(0.3f, 0.9f), len, h * 0.012f, if (rng.next() < 0.3f) 0xFF4A4056.toInt() else 0xFF2E2738.toInt())
            }
            for (i in 0 until 24) {
                val t = i / 23f
                val pt = quadAt(x1, h, cInX, cInY, tipX, tipY, t)
                val inDir = if (side == 0) 1f else -1f
                val len = h * rng.range(0.04f, 0.09f) * (1.1f - t * 0.5f)
                hair(c, pt.x, pt.y, inDir * rng.range(0.4f, 0.9f), -rng.range(0.2f, 0.7f), len, h * 0.010f, 0xFF3A3147.toInt())
            }
            repeat(5) {
                hair(c, tipX + rng.range(-w * 0.01f, w * 0.01f), tipY + h * 0.01f, rng.range(-0.3f, 0.3f), -1f, h * rng.range(0.04f, 0.07f), h * 0.010f, 0xFF4A4056.toInt())
            }
            // Pale wisps rising inside the shell.
            repeat(9) {
                val bx = ix0 + (ix1 - ix0) * rng.range(0.2f, 0.8f)
                hair(c, bx, h * rng.range(0.82f, 0.98f), rng.range(-0.25f, 0.25f), -1f, h * rng.range(0.14f, 0.3f), h * 0.012f, 0xB3F6D9E6.toInt())
            }
        }
    }

    private fun catNose(c: Canvas, w: Float, h: Float) {
        // Glossy leather nose with a specular, not a flat triangle.
        val nose = Path()
        nose.moveTo(w * 0.42f, h * 0.28f)
        nose.quadTo(w * 0.5f, h * 0.22f, w * 0.58f, h * 0.28f)
        nose.quadTo(w * 0.585f, h * 0.38f, w * 0.5f, h * 0.50f)
        nose.quadTo(w * 0.415f, h * 0.38f, w * 0.42f, h * 0.28f)
        nose.close()
        val leather = fill(0xFFE26B9E.toInt()).apply {
            shader = RadialGradient(
                w * 0.48f, h * 0.29f, h * 0.26f,
                intArrayOf(0xFFF8A7C8.toInt(), 0xFFE26B9E.toInt(), 0xFFB04573.toInt()), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP,
            )
            setShadowLayer(h * 0.03f, 0f, h * 0.02f, 0x33000000)
        }
        c.drawPath(nose, leather)
        c.drawPath(nose, stroke(0xFF8E3A60.toInt(), h * 0.012f))
        c.drawOval(RectF(w * 0.455f, h * 0.255f, w * 0.505f, h * 0.29f), fill(0xA8FFFFFF.toInt()))
        // Philtrum.
        c.drawLine(w * 0.5f, h * 0.5f, w * 0.5f, h * 0.66f, stroke(0xFF2A2230.toInt(), h * 0.028f))
        // Whiskers: curved, fading to nothing at the tip — straight opaque
        // lines read as wires. Root dots anchor them to the cheek.
        val rng = Rng(23)
        for (side in 0..1) {
            val dir = if (side == 0) -1f else 1f
            val rootX = w * (0.5f + dir * 0.12f)
            for (i in 0..2) {
                val y0 = h * (0.34f + i * 0.13f)
                val sag = h * (0.04f + i * 0.09f) + rng.range(0f, h * 0.03f)
                val endX = w * (0.5f + dir * (0.46f + rng.range(0f, 0.025f)))
                val endY = y0 + (i - 1) * h * 0.1f + sag * 0.4f
                val wk = stroke(0xFFFFFFFF.toInt(), h * 0.022f).apply {
                    shader = LinearGradient(rootX, y0, endX, endY, intArrayOf(0xE6FFFFFF.toInt(), 0x8CFFFFFF.toInt(), 0x00FFFFFF), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
                }
                val p = Path()
                p.moveTo(rootX, y0)
                p.quadTo(rootX + dir * w * 0.17f, y0 + sag, endX, endY)
                c.drawPath(p, wk)
                c.drawCircle(rootX, y0, h * 0.014f, fill(0x663A2E42))
            }
        }
    }

    private fun dogEars(c: Canvas, w: Float, h: Float) {
        val rng = Rng(31)
        for (side in 0..1) {
            val dir = if (side == 0) 1f else -1f
            val cx = if (side == 0) w * 0.17f else w * 0.83f
            // Floppy spaniel ear: wider at the bottom, a slight outward swing.
            val ear = Path()
            ear.moveTo(cx - dir * w * 0.13f, h * 0.04f)
            ear.quadTo(cx + dir * w * 0.17f, h * 0.02f, cx + dir * w * 0.16f, h * 0.38f)
            ear.quadTo(cx + dir * w * 0.155f, h * 0.8f, cx + dir * w * 0.02f, h * 0.97f)
            ear.quadTo(cx - dir * w * 0.14f, h * 1.02f, cx - dir * w * 0.155f, h * 0.6f)
            ear.quadTo(cx - dir * w * 0.165f, h * 0.25f, cx - dir * w * 0.13f, h * 0.04f)
            ear.close()
            val coat = fill(0xFF7A4A1F.toInt()).apply {
                shader = LinearGradient(
                    cx, 0f, cx + dir * w * 0.08f, h,
                    intArrayOf(0xFFA96B2F.toInt(), 0xFF7A4A1F.toInt(), 0xFF4E2D10.toInt()),
                    floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
                )
                setShadowLayer(h * 0.04f, 0f, h * 0.025f, 0x4D000000)
            }
            c.drawPath(ear, coat)
            // Top fold catches the light.
            val foldClip = Path()
            foldClip.addPath(ear)
            c.save()
            c.clipPath(foldClip)
            c.drawOval(
                RectF(cx - w * 0.2f, -h * 0.06f, cx + w * 0.2f, h * 0.22f),
                fill(0).apply {
                    shader = LinearGradient(0f, 0f, 0f, h * 0.22f, intArrayOf(0x59FFE0B8, 0x00FFE0B8), null, Shader.TileMode.CLAMP)
                },
            )
            // Long wavy fur strands following the hang of the ear.
            repeat(22) {
                val sx = cx + dir * w * rng.range(-0.13f, 0.13f)
                val sy = h * rng.range(0.12f, 0.6f)
                val len = h * rng.range(0.18f, 0.38f)
                val tone = if (rng.next() < 0.5f) 0x338A5A26 else 0x33301A08
                val strand = Path()
                strand.moveTo(sx, sy)
                strand.quadTo(sx + dir * w * rng.range(0.01f, 0.04f), sy + len * 0.5f, sx + dir * w * rng.range(-0.01f, 0.02f), sy + len)
                c.drawPath(strand, stroke(tone, h * rng.range(0.012f, 0.03f)))
            }
            c.restore()
            // Edge fur on the outer silhouette.
            repeat(18) {
                val t = rng.next()
                val ex = cx + dir * w * (0.16f - 0.02f * t)
                val ey = h * (0.1f + t * 0.75f)
                hair(c, ex, ey, dir, rng.range(0.1f, 0.5f), h * rng.range(0.03f, 0.07f), h * 0.012f, 0xFF5E3715.toInt())
            }
        }
    }

    private fun dogNose(c: Canvas, w: Float, h: Float) {
        // Cream muzzle patch, soft-edged so it melts into the real face.
        val muzzle = fill(0xCCF8E8D0.toInt()).apply {
            shader = RadialGradient(
                w * 0.5f, h * 0.55f, w * 0.34f,
                intArrayOf(0xE8FBEEDC.toInt(), 0xB8F5E2C8.toInt(), 0x00F5E2C8), floatArrayOf(0f, 0.7f, 1f), Shader.TileMode.CLAMP,
            )
        }
        c.drawOval(RectF(w * 0.14f, h * 0.22f, w * 0.86f, h * 0.98f), muzzle)
        // The nose itself: wide rounded triangle, wet gloss, carved nostrils.
        val nose = Path()
        nose.moveTo(w * 0.33f, h * 0.26f)
        nose.quadTo(w * 0.5f, h * 0.12f, w * 0.67f, h * 0.26f)
        nose.quadTo(w * 0.71f, h * 0.38f, w * 0.56f, h * 0.52f)
        nose.quadTo(w * 0.5f, h * 0.56f, w * 0.44f, h * 0.52f)
        nose.quadTo(w * 0.29f, h * 0.38f, w * 0.33f, h * 0.26f)
        nose.close()
        val leather = fill(0xFF17181C.toInt()).apply {
            shader = RadialGradient(
                w * 0.46f, h * 0.24f, h * 0.4f,
                intArrayOf(0xFF4A4C55.toInt(), 0xFF222329.toInt(), 0xFF0D0E12.toInt()), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
            )
            setShadowLayer(h * 0.03f, 0f, h * 0.02f, 0x40000000)
        }
        c.drawPath(nose, leather)
        // Nostrils: comma shapes with a lighter ridge above each.
        for (sideN in 0..1) {
            val d = if (sideN == 0) -1f else 1f
            val nx = w * 0.5f + d * w * 0.085f
            val ny = h * 0.30f
            val nostril = Path()
            nostril.moveTo(nx, ny - h * 0.045f)
            nostril.quadTo(nx + d * w * 0.045f, ny - h * 0.02f, nx + d * w * 0.03f, ny + h * 0.05f)
            nostril.quadTo(nx - d * w * 0.01f, ny + h * 0.03f, nx, ny - h * 0.045f)
            nostril.close()
            c.drawPath(nostril, fill(0xFF000000.toInt()))
            c.drawArc(RectF(nx - w * 0.05f, ny - h * 0.085f, nx + w * 0.05f, ny + h * 0.01f), if (d < 0) 160f else 310f, 70f, false, stroke(0x4DFFFFFF, h * 0.012f))
        }
        // Wet shine.
        c.save()
        c.rotate(-18f, w * 0.42f, h * 0.2f)
        c.drawOval(RectF(w * 0.36f, h * 0.16f, w * 0.5f, h * 0.23f), fill(0x96FFFFFF.toInt()).apply { maskFilter = BlurMaskFilter(h * 0.015f, BlurMaskFilter.Blur.NORMAL) })
        c.restore()
        c.drawCircle(w * 0.6f, h * 0.2f, h * 0.018f, fill(0x80FFFFFF.toInt()))
        // Philtrum and mouth.
        c.drawLine(w * 0.5f, h * 0.55f, w * 0.5f, h * 0.72f, stroke(0xE61A1410.toInt(), h * 0.022f))
        val mouth = Path()
        mouth.moveTo(w * 0.3f, h * 0.7f)
        mouth.quadTo(w * 0.5f, h * 0.88f, w * 0.7f, h * 0.7f)
        c.drawPath(mouth, stroke(0xE61A1410.toInt(), h * 0.022f))
        // Whisker dots.
        val rng = Rng(5)
        repeat(8) {
            val d = if (it % 2 == 0) -1f else 1f
            c.drawCircle(w * 0.5f + d * w * rng.range(0.16f, 0.3f), h * rng.range(0.6f, 0.82f), h * 0.011f, fill(0x4D6B4A2A))
        }
    }

    private fun bunnyEars(c: Canvas, w: Float, h: Float) {
        val rng = Rng(47)
        for (side in 0..1) {
            val cx = if (side == 0) w * 0.3f else w * 0.7f
            val lean = if (side == 0) -7f else 7f
            c.save()
            c.rotate(lean, cx, h * 0.98f)
            // Outer ear: white fur with a cool grey core for roundness.
            val outer = RectF(cx - w * 0.125f, h * 0.02f, cx + w * 0.125f, h * 0.99f)
            val coat = fill(0xFFF7F5F2.toInt()).apply {
                shader = LinearGradient(
                    outer.left, 0f, outer.right, 0f,
                    intArrayOf(0xFFD9D4CE.toInt(), 0xFFFDFCFA.toInt(), 0xFFCFC9C2.toInt()),
                    floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP,
                )
                setShadowLayer(h * 0.03f, 0f, h * 0.015f, 0x30000000)
            }
            c.drawOval(outer, coat)
            // Inner shell: blush pink deepening toward the canal.
            val inner = RectF(cx - w * 0.065f, h * 0.1f, cx + w * 0.065f, h * 0.88f)
            val shell = fill(0xFFF2B3CE.toInt()).apply {
                shader = LinearGradient(
                    0f, inner.top, 0f, inner.bottom,
                    intArrayOf(0xFFF7CBDD.toInt(), 0xFFE896BB.toInt(), 0xFFC9699A.toInt()),
                    floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
                )
                maskFilter = BlurMaskFilter(h * 0.012f, BlurMaskFilter.Blur.NORMAL)
            }
            c.drawOval(inner, shell)
            // Rim light along the lit edge.
            c.drawArc(RectF(outer.left + w * 0.012f, outer.top + h * 0.01f, outer.right - w * 0.012f, outer.bottom - h * 0.01f), 200f, 90f, false, stroke(0xB3FFFFFF.toInt(), h * 0.014f))
            // Fur: fine white hairs off both edges and a soft crown at the tip.
            repeat(26) {
                val t = rng.next()
                val a = Math.PI * (0.9f + t * 1.2f)
                val ex = cx + (Math.cos(a) * w * 0.125f).toFloat()
                val ey = h * 0.5f + (Math.sin(a) * h * 0.48f).toFloat()
                val d = if (Math.cos(a) < 0) -1f else 1f
                hair(c, ex, ey, d, rng.range(-0.6f, 0.1f), h * rng.range(0.025f, 0.055f), h * 0.009f, 0xE6FFFFFF.toInt())
            }
            repeat(7) {
                hair(c, cx + rng.range(-w * 0.06f, w * 0.06f), h * 0.035f, rng.range(-0.3f, 0.3f), -1f, h * rng.range(0.025f, 0.05f), h * 0.009f, 0xF2FFFFFF.toInt())
            }
            c.restore()
        }
    }

    // ─── headpieces ──────────────────────────────────────────────────────────

    private fun crown(c: Canvas, w: Float, h: Float) {
        val p = Path()
        p.moveTo(w * 0.08f, h * 0.95f)
        p.lineTo(w * 0.05f, h * 0.30f)
        p.quadTo(w * 0.17f, h * 0.44f, w * 0.27f, h * 0.55f)
        p.quadTo(w * 0.4f, h * 0.3f, w * 0.5f, h * 0.08f)
        p.quadTo(w * 0.6f, h * 0.3f, w * 0.73f, h * 0.55f)
        p.quadTo(w * 0.83f, h * 0.44f, w * 0.95f, h * 0.30f)
        p.lineTo(w * 0.92f, h * 0.95f)
        p.close()
        // Metal: vertical gold gradient for volume, then a rotated sheen band
        // clipped inside — the moving-light impression flat fills never give.
        val gold = fill(0xFFF5C64B.toInt()).apply {
            shader = LinearGradient(
                0f, 0f, 0f, h,
                intArrayOf(0xFFFFF3C4.toInt(), 0xFFF5C64B.toInt(), 0xFFC8872B.toInt(), 0xFF8C5A17.toInt()),
                floatArrayOf(0f, 0.38f, 0.75f, 1f), Shader.TileMode.CLAMP,
            )
            setShadowLayer(h * 0.05f, 0f, h * 0.035f, 0x4D000000)
        }
        c.drawPath(p, gold)
        c.save()
        c.clipPath(p)
        c.save()
        c.rotate(-18f, w * 0.35f, h * 0.5f)
        c.drawRect(
            RectF(w * 0.22f, -h, w * 0.46f, h * 2f),
            fill(0).apply {
                shader = LinearGradient(w * 0.22f, 0f, w * 0.46f, 0f, intArrayOf(0x00FFFFFF, 0x59FFF6D8, 0x00FFFFFF), null, Shader.TileMode.CLAMP)
            },
        )
        c.restore()
        // Shadow where the band meets the points.
        c.drawRect(RectF(w * 0.05f, h * 0.72f, w * 0.95f, h * 0.78f), fill(0x269B6A1C))
        c.restore()
        c.drawPath(p, stroke(0xFF8C5A17.toInt(), h * 0.022f))
        // Base band: darker gold with engraved lines and bevel highlights.
        val band = RectF(w * 0.08f, h * 0.76f, w * 0.92f, h * 0.95f)
        val bandPaint = fill(0xFFC8872B.toInt()).apply {
            shader = LinearGradient(0f, band.top, 0f, band.bottom, intArrayOf(0xFFE8B23F.toInt(), 0xFFB06F1F.toInt(), 0xFF7E5013.toInt()), null, Shader.TileMode.CLAMP)
        }
        c.drawRect(band, bandPaint)
        c.drawLine(band.left, band.top + h * 0.012f, band.right, band.top + h * 0.012f, stroke(0x8CFFF1C9.toInt(), h * 0.012f))
        c.drawLine(band.left, band.bottom - h * 0.012f, band.right, band.bottom - h * 0.012f, stroke(0x66402A08, h * 0.012f))
        // Gems: glow halo, radial body, pin specular, dark setting ring.
        val gemCols = arrayOf(
            intArrayOf(0xFFFF8A8A.toInt(), 0xFFE03131.toInt(), 0xFF7A0F0F.toInt()),
            intArrayOf(0xFF8AB8FF.toInt(), 0xFF2563EB.toInt(), 0xFF102A6B.toInt()),
            intArrayOf(0xFF7FE8C1.toInt(), 0xFF0F9D6C.toInt(), 0xFF065238.toInt()),
        )
        for (i in 0..2) {
            val x = w * (0.27f + i * 0.23f)
            val y = h * 0.855f
            val r = h * 0.062f
            c.drawCircle(x, y, r * 1.5f, fill((gemCols[i][1] and 0x00FFFFFF) or 0x40000000).apply { maskFilter = BlurMaskFilter(r * 0.8f, BlurMaskFilter.Blur.NORMAL) })
            c.drawCircle(x, y, r * 1.12f, fill(0xFF6B4A12.toInt()))
            val gem = fill(gemCols[i][1]).apply {
                shader = RadialGradient(x - r * 0.3f, y - r * 0.35f, r * 1.8f, gemCols[i], floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
            }
            c.drawCircle(x, y, r, gem)
            c.drawCircle(x - r * 0.3f, y - r * 0.35f, r * 0.22f, fill(0xCCFFFFFF.toInt()))
        }
        // Pearls on the points.
        val tips = floatArrayOf(0.05f, 0.30f, 0.5f, 0.08f, 0.95f, 0.30f)
        var i = 0
        while (i < tips.size) {
            val x = w * tips[i]
            val y = h * tips[i + 1]
            val r = h * 0.045f
            val pearl = fill(0xFFF3EFE8.toInt()).apply {
                shader = RadialGradient(x - r * 0.3f, y - r * 0.35f, r * 1.7f, intArrayOf(0xFFFFFFFF.toInt(), 0xFFE9E2D4.toInt(), 0xFFB9AE9A.toInt()), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
            }
            c.drawCircle(x, y, r, pearl)
            c.drawCircle(x - r * 0.3f, y - r * 0.35f, r * 0.2f, fill(0xD9FFFFFF.toInt()))
            i += 2
        }
    }

    private fun flowerCrown(c: Canvas, w: Float, h: Float) {
        val rng = Rng(77)
        // Vine: two green tones woven, not a flat bar.
        val vineY = h * 0.62f
        c.drawRoundRect(RectF(w * 0.02f, vineY - h * 0.06f, w * 0.98f, vineY + h * 0.08f), h * 0.07f, h * 0.07f, fill(0xFF2F7D4F.toInt()).apply { setShadowLayer(h * 0.04f, 0f, h * 0.025f, 0x40000000) })
        repeat(9) {
            val x = w * (0.06f + it * 0.11f)
            val vp = Path()
            vp.moveTo(x, vineY + h * 0.06f)
            vp.quadTo(x + w * 0.05f, vineY - h * 0.08f, x + w * 0.11f, vineY + h * 0.04f)
            c.drawPath(vp, stroke(0xFF49A06B.toInt(), h * 0.025f))
        }
        // Leaves: pointed ellipses with a vein, light side and dark side.
        repeat(10) {
            val x = w * (0.05f + it * 0.1f)
            val up = it % 2 == 0
            val ly = if (up) vineY - h * 0.16f else vineY + h * 0.14f
            c.save()
            c.rotate(rng.range(-30f, 30f) + if (up) -20f else 160f, x, ly)
            val leaf = Path()
            leaf.moveTo(x, ly - h * 0.14f)
            leaf.quadTo(x + w * 0.035f, ly - h * 0.04f, x, ly + h * 0.05f)
            leaf.quadTo(x - w * 0.035f, ly - h * 0.04f, x, ly - h * 0.14f)
            leaf.close()
            c.drawPath(leaf, fill(0xFF3C9460.toInt()).apply {
                shader = LinearGradient(x - w * 0.035f, ly, x + w * 0.035f, ly, intArrayOf(0xFF57B77E.toInt(), 0xFF2A7046.toInt()), null, Shader.TileMode.CLAMP)
            })
            c.drawLine(x, ly - h * 0.12f, x, ly + h * 0.03f, stroke(0x66175434, h * 0.008f))
            c.restore()
        }
        // Flowers: petals as rotated drop shapes, each with its own radial
        // shading, over a soft contact shadow — a bed of circles is exactly
        // the "cheap clipart" the art must not be.
        val species = arrayOf(
            intArrayOf(0xFFF8BBD5.toInt(), 0xFFE56FA4.toInt(), 0xFFB6356F.toInt()),
            intArrayOf(0xFFFBE29B.toInt(), 0xFFF3B53C.toInt(), 0xFFB77514.toInt()),
            intArrayOf(0xFFCBB7F6.toInt(), 0xFF9168E0.toInt(), 0xFF5B34A8.toInt()),
            intArrayOf(0xFFFFC1B3.toInt(), 0xFFF3744F.toInt(), 0xFFB03A1C.toInt()),
            intArrayOf(0xFFB7D9F8.toInt(), 0xFF6AA5E8.toInt(), 0xFF2E5FA8.toInt()),
        )
        val n = 7
        for (f in 0 until n) {
            val cx = w * (0.09f + f * (0.82f / (n - 1)))
            val cy = h * (if (f % 2 == 0) 0.48f else 0.38f) + rng.range(-h * 0.02f, h * 0.02f)
            val r = h * (if (f % 3 == 1) 0.20f else 0.16f)
            val cols = species[f % species.size]
            c.drawCircle(cx, cy + r * 0.3f, r * 0.9f, fill(0x26000000).apply { maskFilter = BlurMaskFilter(r * 0.4f, BlurMaskFilter.Blur.NORMAL) })
            val baseRot = rng.range(0f, 72f)
            for (k in 0 until 5) {
                c.save()
                c.rotate(baseRot + k * 72f, cx, cy)
                val petal = Path()
                petal.moveTo(cx, cy)
                petal.cubicTo(cx - r * 0.5f, cy - r * 0.45f, cx - r * 0.38f, cy - r * 1.05f, cx, cy - r * 1.12f)
                petal.cubicTo(cx + r * 0.38f, cy - r * 1.05f, cx + r * 0.5f, cy - r * 0.45f, cx, cy)
                petal.close()
                c.drawPath(petal, fill(cols[1]).apply {
                    shader = RadialGradient(cx, cy, r * 1.15f, intArrayOf(cols[0], cols[1], cols[2]), floatArrayOf(0.1f, 0.6f, 1f), Shader.TileMode.CLAMP)
                })
                c.drawPath(petal, stroke((cols[2] and 0x00FFFFFF) or 0x59000000, r * 0.03f))
                c.restore()
            }
            // Centre: gold boss with a stamen dot ring.
            c.drawCircle(cx, cy, r * 0.34f, fill(0xFFF6D56A.toInt()).apply {
                shader = RadialGradient(cx - r * 0.1f, cy - r * 0.1f, r * 0.5f, intArrayOf(0xFFFDEDB5.toInt(), 0xFFEFB93A.toInt(), 0xFFA8761A.toInt()), floatArrayOf(0f, 0.65f, 1f), Shader.TileMode.CLAMP)
            })
            for (k in 0 until 8) {
                val a = Math.toRadians((k * 45).toDouble())
                c.drawCircle(cx + (Math.cos(a) * r * 0.26f).toFloat(), cy + (Math.sin(a) * r * 0.26f).toFloat(), r * 0.035f, fill(0x8C7A4D0D.toInt()))
            }
        }
    }

    private fun devilHorns(c: Canvas, w: Float, h: Float) {
        for (side in 0..1) {
            val base = if (side == 0) w * 0.22f else w * 0.78f
            val dir = if (side == 0) -1f else 1f
            val p = Path()
            p.moveTo(base - w * 0.085f, h)
            p.quadTo(base + dir * w * 0.14f, h * 0.55f, base + dir * w * 0.16f, h * 0.05f)
            p.quadTo(base + dir * w * 0.02f, h * 0.45f, base + w * 0.085f, h)
            p.close()
            // Lacquered horn: bright blood red mid-tone, near-black base and
            // a hot edge on the outer curve.
            val horn = fill(0xFFC01F1F.toInt()).apply {
                shader = LinearGradient(
                    base, h, base + dir * w * 0.16f, 0f,
                    intArrayOf(0xFF5C0E10.toInt(), 0xFFB81E22.toInt(), 0xFFE8483F.toInt(), 0xFF8F1416.toInt()),
                    floatArrayOf(0f, 0.4f, 0.75f, 1f), Shader.TileMode.CLAMP,
                )
                setShadowLayer(h * 0.04f, 0f, h * 0.025f, 0x4D000000)
            }
            c.drawPath(p, horn)
            c.save()
            c.clipPath(p)
            // Specular stripe hugging the outer curve.
            val shine = Path()
            shine.moveTo(base + dir * w * 0.015f, h * 0.9f)
            shine.quadTo(base + dir * w * 0.125f, h * 0.5f, base + dir * w * 0.145f, h * 0.1f)
            c.drawPath(shine, stroke(0x73FFD9C9, h * 0.035f).apply { maskFilter = BlurMaskFilter(h * 0.025f, BlurMaskFilter.Blur.NORMAL) })
            // Growth ridges near the base.
            for (k in 0..2) {
                val ry = h * (0.78f + k * 0.07f)
                c.drawArc(RectF(base - w * 0.1f, ry - h * 0.03f, base + w * 0.1f, ry + h * 0.03f), 200f, 140f, false, stroke(0x40330607, h * 0.016f))
            }
            c.restore()
            c.drawPath(p, stroke(0xFF4A0A0C.toInt(), h * 0.018f))
        }
    }

    private fun halo(c: Canvas, w: Float, h: Float) {
        val oval = RectF(w * 0.12f, h * 0.26f, w * 0.88f, h * 0.74f)
        // Light first: a wide warm bloom, then a tighter one.
        c.drawOval(oval, stroke(0x40FFE9A8, h * 0.3f).apply { maskFilter = BlurMaskFilter(h * 0.22f, BlurMaskFilter.Blur.NORMAL) })
        c.drawOval(oval, stroke(0x73FFE09A, h * 0.14f).apply { maskFilter = BlurMaskFilter(h * 0.1f, BlurMaskFilter.Blur.NORMAL) })
        // The ring itself: gold tube — light on top, deep amber underneath.
        val ring = stroke(0xFFEFBE4A.toInt(), h * 0.085f).apply {
            shader = LinearGradient(0f, oval.top, 0f, oval.bottom, intArrayOf(0xFFFFF4D1.toInt(), 0xFFF2C24E.toInt(), 0xFFB97F1D.toInt()), null, Shader.TileMode.CLAMP)
        }
        c.drawOval(oval, ring)
        // Inner highlight arc on the upper rim.
        c.drawArc(RectF(oval.left + w * 0.02f, oval.top + h * 0.03f, oval.right - w * 0.02f, oval.bottom - h * 0.03f), 190f, 120f, false, stroke(0xBFFFFCEF.toInt(), h * 0.022f))
        fourPoint(c, w * 0.2f, h * 0.3f, h * 0.09f, 12f, 0xFFFFF6DC.toInt(), 0x59FFE09A)
        fourPoint(c, w * 0.82f, h * 0.62f, h * 0.065f, -8f, 0xFFFFF6DC.toInt(), 0x59FFE09A)
    }

    // ─── face detail ─────────────────────────────────────────────────────────

    private fun moustache(c: Canvas, w: Float, h: Float) {
        val p = Path()
        p.moveTo(w * 0.5f, h * 0.45f)
        p.cubicTo(w * 0.40f, h * 0.15f, w * 0.18f, h * 0.20f, w * 0.04f, h * 0.55f)
        p.cubicTo(w * 0.12f, h * 0.72f, w * 0.26f, h * 0.70f, w * 0.34f, h * 0.60f)
        p.cubicTo(w * 0.40f, h * 0.80f, w * 0.46f, h * 0.84f, w * 0.5f, h * 0.84f)
        p.cubicTo(w * 0.54f, h * 0.84f, w * 0.60f, h * 0.80f, w * 0.66f, h * 0.60f)
        p.cubicTo(w * 0.74f, h * 0.70f, w * 0.88f, h * 0.72f, w * 0.96f, h * 0.55f)
        p.cubicTo(w * 0.82f, h * 0.20f, w * 0.60f, h * 0.15f, w * 0.5f, h * 0.45f)
        p.close()
        val base = fill(0xFF1F1813.toInt()).apply {
            shader = LinearGradient(0f, h * 0.1f, 0f, h * 0.85f, intArrayOf(0xFF3A2E22.toInt(), 0xFF191310.toInt(), 0xFF0C0908.toInt()), null, Shader.TileMode.CLAMP)
            setShadowLayer(h * 0.05f, 0f, h * 0.03f, 0x40000000)
        }
        c.drawPath(p, base)
        // Combed hair: strokes sweeping from the centre part out to the tips,
        // alternating warm and dark so it reads as strands, not plastic.
        c.save()
        c.clipPath(p)
        val rng = Rng(53)
        repeat(42) {
            val t = rng.next()
            val sideDir = if (it % 2 == 0) -1f else 1f
            val sx = w * 0.5f + sideDir * w * t * 0.06f
            val sy = h * (0.3f + t * 0.4f)
            val ex = w * 0.5f + sideDir * w * (0.1f + t * 0.4f)
            val ey = h * (0.25f + t * 0.42f) + rng.range(-h * 0.04f, h * 0.08f)
            val mx = (sx + ex) / 2f
            val my = sy - h * rng.range(0.05f, 0.18f)
            val tone = when {
                rng.next() < 0.3f -> 0x59684B32
                rng.next() < 0.5f -> 0x4D423122
                else -> 0x59070504
            }
            val hairP = Path()
            hairP.moveTo(sx, sy)
            hairP.quadTo(mx, my, ex, ey)
            c.drawPath(hairP, stroke(tone, h * rng.range(0.012f, 0.028f)))
        }
        // Gloss along the top of each wing.
        for (sideDir in intArrayOf(-1, 1)) {
            val gp = Path()
            gp.moveTo(w * 0.5f, h * 0.34f)
            gp.quadTo(w * (0.5f + sideDir * 0.17f), h * 0.2f, w * (0.5f + sideDir * 0.33f), h * 0.38f)
            c.drawPath(gp, stroke(0x33D9B38C, h * 0.035f).apply { maskFilter = BlurMaskFilter(h * 0.03f, BlurMaskFilter.Blur.NORMAL) })
        }
        c.restore()
    }

    private fun blush(c: Canvas, w: Float, h: Float) {
        for (side in 0..1) {
            val cx = if (side == 0) w * 0.2f else w * 0.8f
            val cy = h * 0.5f
            // Two stacked washes: a broad cool-pink veil and a warmer core a
            // touch higher, like real cream blush on the cheekbone.
            val veil = fill(0x52F27DA0).apply {
                shader = RadialGradient(cx, cy, h * 0.52f, intArrayOf(0x66F27DA0, 0x2BF27DA0, 0x00F27DA0), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
            }
            c.drawOval(RectF(cx - h * 0.62f, cy - h * 0.45f, cx + h * 0.62f, cy + h * 0.5f), veil)
            val core = fill(0x47FA9A6E).apply {
                shader = RadialGradient(cx, cy - h * 0.08f, h * 0.3f, intArrayOf(0x59FA9A6E, 0x24FA9A6E, 0x00FA9A6E), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
            }
            c.drawOval(RectF(cx - h * 0.36f, cy - h * 0.36f, cx + h * 0.36f, cy + h * 0.22f), core)
            // A whisper of highlight above, where blush meets cheekbone shine.
            val sheen = fill(0x24FFFFFF).apply {
                shader = RadialGradient(cx, cy - h * 0.22f, h * 0.22f, intArrayOf(0x33FFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP)
            }
            c.drawOval(RectF(cx - h * 0.26f, cy - h * 0.4f, cx + h * 0.26f, cy - h * 0.02f), sheen)
        }
    }

    private fun freckles(c: Canvas, w: Float, h: Float) {
        val rng = Rng(7)
        // Two melanin tones, elliptical spots, denser toward the nose bridge,
        // every spot slightly blurred — crisp circles read as marker dots.
        val tones = intArrayOf(0x7D8A5A33, 0x66704322, 0x8C9A6B3D.toInt())
        fun spot(x: Float, y: Float, r: Float) {
            val p = fill(tones[(rng.next() * tones.size).toInt().coerceAtMost(tones.size - 1)]).apply {
                maskFilter = BlurMaskFilter(r * 0.5f, BlurMaskFilter.Blur.NORMAL)
            }
            c.save()
            c.rotate(rng.range(0f, 180f), x, y)
            c.drawOval(RectF(x - r, y - r * rng.range(0.7f, 0.95f), x + r, y + r * rng.range(0.7f, 0.95f)), p)
            c.restore()
        }
        for (side in 0..1) {
            val x0 = if (side == 0) w * 0.05f else w * 0.57f
            repeat(16) {
                val t = rng.next()
                val x = x0 + t * w * 0.38f
                val centerPull = 1f - kotlin.math.abs((x / w) - 0.5f) * 1.4f
                val y = h * 0.22f + rng.next() * h * 0.56f * (0.7f + centerPull * 0.3f)
                spot(x, y, h * rng.range(0.018f, 0.042f))
            }
        }
        repeat(7) {
            spot(w * rng.range(0.44f, 0.56f), h * rng.range(0.3f, 0.6f), h * rng.range(0.016f, 0.034f))
        }
    }

    private fun tears(c: Canvas, w: Float, h: Float) {
        for (side in 0..1) {
            val cx = if (side == 0) w * 0.28f else w * 0.72f
            // Trailing droplet above the main drop.
            val mini = Path()
            mini.moveTo(cx + w * 0.012f, h * 0.02f)
            mini.cubicTo(cx - w * 0.015f, h * 0.08f, cx - w * 0.014f, h * 0.13f, cx + w * 0.012f, h * 0.16f)
            mini.cubicTo(cx + w * 0.032f, h * 0.12f, cx + w * 0.030f, h * 0.07f, cx + w * 0.012f, h * 0.02f)
            mini.close()
            c.drawPath(mini, fill(0x8C9BD7F4.toInt()))
            // Main drop: water needs depth (dark core low), a bright refracted
            // floor, a hard specular and a soft one.
            val p = Path()
            p.moveTo(cx, h * 0.12f)
            p.cubicTo(cx - w * 0.075f, h * 0.44f, cx - w * 0.068f, h * 0.76f, cx, h * 0.93f)
            p.cubicTo(cx + w * 0.068f, h * 0.76f, cx + w * 0.075f, h * 0.44f, cx, h * 0.12f)
            p.close()
            val water = fill(0xCC57B7E8.toInt()).apply {
                shader = LinearGradient(
                    cx, h * 0.12f, cx, h * 0.93f,
                    intArrayOf(0x99CFF0FF.toInt(), 0xB36FC7F2.toInt(), 0xD92E86C4.toInt(), 0xBF6FD0F5.toInt()),
                    floatArrayOf(0f, 0.4f, 0.78f, 1f), Shader.TileMode.CLAMP,
                )
                setShadowLayer(h * 0.025f, 0f, h * 0.02f, 0x26104A73)
            }
            c.drawPath(p, water)
            c.drawPath(p, stroke(0x734AA8DC, h * 0.016f))
            c.save()
            c.clipPath(p)
            // Refracted light pooling at the bottom of the drop.
            c.drawOval(
                RectF(cx - w * 0.05f, h * 0.68f, cx + w * 0.05f, h * 0.9f),
                fill(0x8CE4F6FF.toInt()).apply { maskFilter = BlurMaskFilter(h * 0.04f, BlurMaskFilter.Blur.NORMAL) },
            )
            c.restore()
            // Speculars.
            c.save()
            c.rotate(-14f, cx - w * 0.026f, h * 0.34f)
            c.drawOval(RectF(cx - w * 0.042f, h * 0.26f, cx - w * 0.01f, h * 0.46f), fill(0xBFFFFFFF.toInt()))
            c.restore()
            c.drawCircle(cx + w * 0.02f, h * 0.58f, h * 0.022f, fill(0x8CFFFFFF.toInt()))
        }
    }

    // ─── ornaments ───────────────────────────────────────────────────────────

    private fun sparkle(c: Canvas, w: Float, h: Float) {
        val rng = Rng(91)
        // Real four-point glints with glow and varied warmth, plus a dust of
        // pin-lights — not a scatter of plain dots.
        data class Spot(val x: Float, val y: Float, val r: Float, val warm: Boolean)
        val spots = listOf(
            Spot(0.12f, 0.24f, 0.11f, true), Spot(0.30f, 0.14f, 0.07f, false),
            Spot(0.72f, 0.18f, 0.10f, true), Spot(0.89f, 0.3f, 0.065f, false),
            Spot(0.17f, 0.6f, 0.075f, false), Spot(0.83f, 0.56f, 0.09f, true),
            Spot(0.07f, 0.82f, 0.06f, true), Spot(0.93f, 0.78f, 0.07f, false),
            Spot(0.48f, 0.1f, 0.055f, false), Spot(0.52f, 0.88f, 0.08f, true),
            Spot(0.34f, 0.44f, 0.05f, true), Spot(0.66f, 0.4f, 0.06f, false),
        )
        for (s in spots) {
            val core = if (s.warm) 0xFFFFF3CF.toInt() else 0xFFF2FAFF.toInt()
            val glow = if (s.warm) 0x59FFD98C else 0x598CD2FF
            fourPoint(c, w * s.x, h * s.y, h * s.r, rng.range(-20f, 20f), core, glow)
        }
        repeat(14) {
            val x = w * rng.next()
            val y = h * rng.next()
            val r = h * rng.range(0.008f, 0.018f)
            c.drawCircle(x, y, r * 2.2f, fill(0x26FFFFFF).apply { maskFilter = BlurMaskFilter(r * 2f, BlurMaskFilter.Blur.NORMAL) })
            c.drawCircle(x, y, r, fill(0xBFFFFFFF.toInt()))
        }
    }

    private fun wings(c: Canvas, w: Float, h: Float) {
        // Three ranks of individual feathers fanned from the shoulder: long
        // cool primaries at the back, secondaries, then small bright coverts
        // in front. Layered tone separation is what makes it plumage instead
        // of a white blob.
        for (side in 0..1) {
            val dir = if (side == 0) -1f else 1f
            val rootX = w * 0.5f
            val rootY = h * 0.62f
            featherRank(c, rootX, rootY, dir, count = 6, startDeg = 24f, stepDeg = 13f, len = h * 0.56f, wid = w * 0.046f, light = 0xFFCBD5E0.toInt(), dark = 0xFF8495A8.toInt(), shadowed = true)
            featherRank(c, rootX, rootY - h * 0.02f, dir, count = 5, startDeg = 28f, stepDeg = 14f, len = h * 0.42f, wid = w * 0.05f, light = 0xFFEDF2F7.toInt(), dark = 0xFFB4C1CF.toInt(), shadowed = false)
            featherRank(c, rootX, rootY - h * 0.045f, dir, count = 4, startDeg = 32f, stepDeg = 15f, len = h * 0.28f, wid = w * 0.052f, light = 0xFFFEFEFF.toInt(), dark = 0xFFD9E0E9.toInt(), shadowed = false)
        }
    }

    private fun featherRank(
        c: Canvas,
        rootX: Float,
        rootY: Float,
        dir: Float,
        count: Int,
        startDeg: Float,
        stepDeg: Float,
        len: Float,
        wid: Float,
        light: Int,
        dark: Int,
        shadowed: Boolean,
    ) {
        for (i in 0 until count) {
            c.save()
            c.rotate(dir * (startDeg + i * stepDeg), rootX, rootY)
            val fl = len * (1f - i * 0.05f)
            val fw = wid * (1f - i * 0.04f)
            val tipY = rootY - fl
            // Vane: a slim leaf, slightly asymmetric like a real flight feather.
            val p = Path()
            p.moveTo(rootX, rootY)
            p.cubicTo(rootX - fw, rootY - fl * 0.3f, rootX - fw * 0.8f, tipY + fl * 0.16f, rootX, tipY)
            p.cubicTo(rootX + fw * 0.9f, tipY + fl * 0.14f, rootX + fw * 1.1f, rootY - fl * 0.32f, rootX, rootY)
            p.close()
            val vane = fill(dark).apply {
                shader = LinearGradient(rootX, tipY, rootX, rootY, intArrayOf(light, dark), null, Shader.TileMode.CLAMP)
                if (shadowed) setShadowLayer(fw * 0.8f, 0f, fw * 0.4f, 0x33425469)
            }
            c.drawPath(p, vane)
            // Shaft and a few barb hints.
            c.drawLine(rootX, rootY - fl * 0.06f, rootX, tipY + fl * 0.1f, stroke(0x40607184, fw * 0.1f))
            for (b in 1..3) {
                val by = rootY - fl * (0.25f + b * 0.18f)
                c.drawLine(rootX, by, rootX - fw * 0.55f, by - fl * 0.05f, stroke(0x26607184, fw * 0.06f))
                c.drawLine(rootX, by, rootX + fw * 0.6f, by - fl * 0.05f, stroke(0x26607184, fw * 0.06f))
            }
            c.restore()
        }
    }

    private fun bow(c: Canvas, w: Float, h: Float) {
        val cy = h * 0.4f
        // Tails behind the loops: ribbons falling with a notch cut, one fold
        // line each so they hang rather than hover.
        for (side in 0..1) {
            val dir = if (side == 0) -1f else 1f
            val endX = w * (0.5f + dir * 0.2f)
            val tail = Path()
            tail.moveTo(w * 0.5f - dir * w * 0.02f, cy + h * 0.04f)
            tail.quadTo(w * (0.5f + dir * 0.05f), h * 0.7f, endX - dir * w * 0.055f, h * 0.97f)
            tail.lineTo(endX, h * 0.87f)
            tail.lineTo(endX + dir * w * 0.055f, h * 0.94f)
            tail.quadTo(w * (0.5f + dir * 0.14f), h * 0.58f, w * 0.5f + dir * w * 0.06f, cy + h * 0.02f)
            tail.close()
            val ribbon = fill(0xFFC22955.toInt()).apply {
                shader = LinearGradient(w * 0.5f, cy, endX, h * 0.95f, intArrayOf(0xFFD63866.toInt(), 0xFFA31B45.toInt(), 0xFF7E1234.toInt()), null, Shader.TileMode.CLAMP)
                setShadowLayer(h * 0.03f, 0f, h * 0.02f, 0x40000000)
            }
            c.drawPath(tail, ribbon)
            val fold = Path()
            fold.moveTo(w * (0.5f + dir * 0.015f), cy + h * 0.06f)
            fold.quadTo(w * (0.5f + dir * 0.085f), h * 0.64f, endX - dir * w * 0.02f, h * 0.9f)
            c.drawPath(fold, stroke(0x40590A22, h * 0.014f))
        }
        // Loops: satin — the sheen is a gradient that runs along the loop with
        // a bright band mid-way, plus fold shadows radiating from the knot.
        for (side in 0..1) {
            val dir = if (side == 0) -1f else 1f
            val loop = Path()
            loop.moveTo(w * 0.5f, cy)
            loop.cubicTo(w * (0.5f + dir * 0.07f), cy - h * 0.38f, w * (0.5f + dir * 0.42f), cy - h * 0.34f, w * (0.5f + dir * 0.44f), cy - h * 0.02f)
            loop.cubicTo(w * (0.5f + dir * 0.42f), cy + h * 0.32f, w * (0.5f + dir * 0.1f), cy + h * 0.34f, w * 0.5f, cy)
            loop.close()
            val satin = fill(0xFFE84A74.toInt()).apply {
                shader = LinearGradient(
                    w * 0.5f, cy, w * (0.5f + dir * 0.44f), cy - h * 0.1f,
                    intArrayOf(0xFFC22955.toInt(), 0xFFFF9DB4.toInt(), 0xFFE84A74.toInt(), 0xFFA31B45.toInt()),
                    floatArrayOf(0f, 0.35f, 0.7f, 1f), Shader.TileMode.CLAMP,
                )
                setShadowLayer(h * 0.035f, 0f, h * 0.02f, 0x40000000)
            }
            c.drawPath(loop, satin)
            c.save()
            c.clipPath(loop)
            // Fold shadows from the knot into the loop.
            for (k in 0..1) {
                val fp = Path()
                val spread = 0.1f + k * 0.12f
                fp.moveTo(w * 0.5f, cy)
                fp.quadTo(w * (0.5f + dir * 0.18f), cy - h * (spread - 0.04f), w * (0.5f + dir * 0.4f), cy - h * (spread - 0.12f))
                c.drawPath(fp, stroke(0x33690E28, h * 0.02f).apply { maskFilter = BlurMaskFilter(h * 0.018f, BlurMaskFilter.Blur.NORMAL) })
            }
            // Top-edge highlight where satin catches light.
            val hl = Path()
            hl.moveTo(w * (0.5f + dir * 0.06f), cy - h * 0.27f)
            hl.quadTo(w * (0.5f + dir * 0.3f), cy - h * 0.3f, w * (0.5f + dir * 0.41f), cy - h * 0.08f)
            c.drawPath(hl, stroke(0x66FFD3DE, h * 0.025f).apply { maskFilter = BlurMaskFilter(h * 0.02f, BlurMaskFilter.Blur.NORMAL) })
            c.restore()
            c.drawPath(loop, stroke(0xFF8E1239.toInt(), h * 0.014f))
        }
        // Knot: wrapped band with creases and a soft top shine.
        val knot = RectF(w * 0.44f, cy - h * 0.11f, w * 0.56f, cy + h * 0.11f)
        val knotPaint = fill(0xFFD63866.toInt()).apply {
            shader = LinearGradient(0f, knot.top, 0f, knot.bottom, intArrayOf(0xFFFF8BA7.toInt(), 0xFFD63866.toInt(), 0xFF8E1239.toInt()), null, Shader.TileMode.CLAMP)
        }
        c.drawRoundRect(knot, w * 0.025f, w * 0.025f, knotPaint)
        c.drawLine(w * 0.475f, knot.top + h * 0.01f, w * 0.465f, knot.bottom - h * 0.01f, stroke(0x40590A22, h * 0.012f))
        c.drawLine(w * 0.525f, knot.top + h * 0.01f, w * 0.535f, knot.bottom - h * 0.01f, stroke(0x40590A22, h * 0.012f))
        c.drawRoundRect(knot, w * 0.025f, w * 0.025f, stroke(0xFF8E1239.toInt(), h * 0.012f))
        c.drawOval(RectF(knot.left + w * 0.015f, knot.top + h * 0.012f, knot.right - w * 0.015f, knot.top + h * 0.05f), fill(0x59FFFFFF))
    }
}
