package app.personal.banglaai.image

/** Normalised (0..1) crop rectangle over the displayed image, and the pure maths the image screen needs. Unit-tested on the JVM. */
data class NRect(val l: Float, val t: Float, val r: Float, val b: Float) {
    val w get() = r - l
    val h get() = b - t
    companion object { val Full = NRect(0f, 0f, 1f, 1f) }
}

enum class Corner { TL, TR, BL, BR }

object CropMath {
    const val MIN = 0.08f   // a crop never gets smaller than 8% of a side

    /** Moves one corner by (dx, dy) in normalised units, staying inside the image and above the minimum size. */
    fun drag(r: NRect, c: Corner, dx: Float, dy: Float): NRect {
        var l = r.l; var t = r.t; var rr = r.r; var b = r.b
        if (c == Corner.TL || c == Corner.BL) l = (l + dx).coerceIn(0f, rr - MIN) else rr = (rr + dx).coerceIn(l + MIN, 1f)
        if (c == Corner.TL || c == Corner.TR) t = (t + dy).coerceIn(0f, b - MIN) else b = (b + dy).coerceIn(t + MIN, 1f)
        return NRect(l, t, rr, b)
    }

    /** Pixel rectangle (left, top, width, height) of [r] on an image of w×h, always at least 1 px and inside the image. */
    fun toPixels(r: NRect, w: Int, h: Int): IntArray {
        val l = (r.l * w).toInt().coerceIn(0, w - 1); val t = (r.t * h).toInt().coerceIn(0, h - 1)
        val rr = Math.round(r.r * w).coerceIn(l + 1, w); val bb = Math.round(r.b * h).coerceIn(t + 1, h)
        return intArrayOf(l, t, rr - l, bb - t)
    }

    /** Where an image of iw×ih sits when fitted into a bw×bh box: (left, top, width, height). */
    fun fit(iw: Int, ih: Int, bw: Float, bh: Float): FloatArray {
        val s = minOf(bw / iw, bh / ih); val w = iw * s; val h = ih * s
        return floatArrayOf((bw - w) / 2f, (bh - h) / 2f, w, h)
    }

    /** Zoom is limited to 1×..6×, and panning never leaves the picture's edges outside the box. */
    fun clampZoom(z: Float) = z.coerceIn(1f, 6f)
    fun clampPan(p: Float, zoom: Float, size: Float): Float { val m = size * (zoom - 1f) / 2f; return p.coerceIn(-m, m) }

    /** Rotation in 90° steps; keeps the value in 0,90,180,270. */
    fun rotate(deg: Int, by: Int) = ((deg + by) % 360 + 360) % 360
}
