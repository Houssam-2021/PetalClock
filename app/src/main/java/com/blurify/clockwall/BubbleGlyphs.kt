package com.blurify.clockwall

import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path

/**
 * Original hand-designed bubble numerals, NOT an emboldened system font.
 * Each glyph lives in a 100×140 unit box. The rounded curved inner counters are
 * cut out of the filled silhouette; tracing from a compressed video is approximate.
 * 0, 4 and 6 were calibrated against the visible reference silhouettes; glyphs
 * not fully visible in that reference are stylistically consistent extrapolations.
 */
internal class BubbleGlyphs {
    private val forms = arrayOfNulls<Path>(10)
    private val cutter = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    fun path(d: Int): Path {
        require(d in 0..9)
        return forms[d] ?: build(d).also { forms[d] = it }
    }

    private fun build(d: Int): Path {
        val outer = Path().apply { fillType = Path.FillType.WINDING }
        // The use of broad Bezier segments (instead of text glyphs) preserves the
        // characteristic soft, inflated contour when a blurred mask is drawn.
        when (d) {
            0 -> with(outer) {
                moveTo(51f, 1f)
                cubicTo(79f, -2f, 98f, 25f, 98f, 65f)
                cubicTo(98f, 110f, 77f, 142f, 47f, 141f)
                cubicTo(20f, 141f, 2f, 115f, 3f, 74f)
                cubicTo(2f, 33f, 24f, 3f, 51f, 1f)
                close()
            }
            1 -> with(outer) {
                moveTo(59f, 1f)
                cubicTo(72f, -3f, 88f, 6f, 89f, 23f)
                lineTo(89f, 117f)
                cubicTo(89f, 135f, 77f, 142f, 59f, 140f)
                cubicTo(40f, 140f, 32f, 128f, 32f, 110f)
                lineTo(32f, 54f)
                cubicTo(24f, 60f, 12f, 60f, 5f, 51f)
                cubicTo(-3f, 39f, 0f, 27f, 12f, 18f)
                lineTo(40f, 4f)
                cubicTo(48f, 1f, 53f, 0f, 59f, 1f)
                close()
            }
            2 -> with(outer) {
                moveTo(8f, 28f)
                cubicTo(13f, 6f, 34f, -3f, 58f, 1f)
                cubicTo(84f, 2f, 101f, 23f, 97f, 52f)
                cubicTo(96f, 69f, 78f, 85f, 56f, 103f)
                lineTo(86f, 103f)
                cubicTo(106f, 103f, 106f, 140f, 85f, 140f)
                lineTo(17f, 140f)
                cubicTo(-1f, 140f, -4f, 123f, 7f, 109f)
                lineTo(51f, 61f)
                cubicTo(62f, 49f, 59f, 38f, 51f, 39f)
                cubicTo(44f, 39f, 44f, 50f, 30f, 53f)
                cubicTo(12f, 56f, 1f, 44f, 8f, 28f)
                close()
            }
            3 -> with(outer) {
                moveTo(14f, 13f)
                cubicTo(31f, -3f, 62f, -4f, 81f, 10f)
                cubicTo(103f, 24f, 103f, 46f, 88f, 65f)
                cubicTo(106f, 84f, 104f, 114f, 85f, 130f)
                cubicTo(62f, 149f, 28f, 143f, 10f, 128f)
                cubicTo(-4f, 115f, 7f, 92f, 25f, 91f)
                cubicTo(35f, 90f, 38f, 99f, 51f, 99f)
                cubicTo(66f, 98f, 68f, 87f, 54f, 85f)
                cubicTo(41f, 84f, 35f, 76f, 36f, 67f)
                cubicTo(37f, 57f, 44f, 51f, 56f, 48f)
                cubicTo(65f, 45f, 63f, 35f, 52f, 36f)
                cubicTo(40f, 37f, 38f, 45f, 28f, 44f)
                cubicTo(10f, 44f, 2f, 28f, 14f, 13f)
                close()
            }
            4 -> with(outer) {
                moveTo(59f, 1f)
                cubicTo(78f, -2f, 91f, 9f, 91f, 28f)
                lineTo(91f, 73f)
                cubicTo(107f, 76f, 107f, 106f, 91f, 110f)
                lineTo(91f, 119f)
                cubicTo(91f, 136f, 79f, 142f, 63f, 140f)
                cubicTo(48f, 140f, 39f, 131f, 39f, 115f)
                lineTo(15f, 115f)
                cubicTo(-1f, 115f, -5f, 102f, 1f, 87f)
                lineTo(38f, 15f)
                cubicTo(43f, 5f, 49f, 2f, 59f, 1f)
                close()
            }
            5 -> with(outer) {
                moveTo(24f, 2f)
                lineTo(80f, 2f)
                cubicTo(103f, 2f, 103f, 36f, 82f, 37f)
                lineTo(49f, 37f)
                lineTo(45f, 55f)
                cubicTo(80f, 43f, 103f, 73f, 98f, 101f)
                cubicTo(91f, 145f, 35f, 154f, 10f, 128f)
                cubicTo(-2f, 112f, 6f, 95f, 24f, 94f)
                cubicTo(38f, 94f, 39f, 106f, 54f, 106f)
                cubicTo(74f, 106f, 72f, 82f, 52f, 83f)
                cubicTo(45f, 83f, 37f, 88f, 25f, 86f)
                cubicTo(9f, 83f, 7f, 68f, 10f, 55f)
                lineTo(12f, 18f)
                cubicTo(12f, 8f, 17f, 3f, 24f, 2f)
                close()
            }
            6 -> with(outer) {
                moveTo(66f, 1f)
                cubicTo(88f, -2f, 97f, 22f, 83f, 35f)
                cubicTo(75f, 43f, 64f, 40f, 56f, 46f)
                cubicTo(88f, 40f, 101f, 61f, 98f, 91f)
                cubicTo(98f, 123f, 77f, 143f, 49f, 141f)
                cubicTo(17f, 141f, 1f, 118f, 3f, 81f)
                cubicTo(4f, 47f, 16f, 22f, 38f, 8f)
                cubicTo(47f, 3f, 57f, 1f, 66f, 1f)
                close()
            }
            7 -> with(outer) {
                moveTo(13f, 1f)
                lineTo(88f, 1f)
                cubicTo(105f, 1f, 108f, 14f, 101f, 30f)
                lineTo(59f, 128f)
                cubicTo(53f, 143f, 38f, 145f, 22f, 136f)
                cubicTo(8f, 128f, 9f, 114f, 16f, 99f)
                lineTo(41f, 39f)
                lineTo(12f, 39f)
                cubicTo(-6f, 39f, -7f, 2f, 13f, 1f)
                close()
            }
            8 -> with(outer) {
                moveTo(51f, 0f)
                cubicTo(80f, -1f, 100f, 16f, 99f, 43f)
                cubicTo(99f, 57f, 93f, 66f, 83f, 72f)
                cubicTo(96f, 80f, 100f, 93f, 97f, 111f)
                cubicTo(91f, 139f, 72f, 144f, 50f, 143f)
                cubicTo(19f, 143f, 2f, 125f, 2f, 107f)
                cubicTo(0f, 90f, 6f, 79f, 18f, 72f)
                cubicTo(6f, 63f, 3f, 53f, 4f, 40f)
                cubicTo(6f, 16f, 26f, 0f, 51f, 0f)
                close()
            }
            9 -> with(outer) {
                moveTo(52f, 0f)
                cubicTo(84f, 0f, 101f, 23f, 99f, 60f)
                cubicTo(98f, 91f, 84f, 117f, 60f, 134f)
                cubicTo(41f, 149f, 13f, 136f, 18f, 116f)
                cubicTo(21f, 103f, 37f, 106f, 44f, 97f)
                cubicTo(10f, 100f, 0f, 74f, 4f, 43f)
                cubicTo(7f, 17f, 23f, 1f, 52f, 0f)
                close()
            }
        }
        // A small curved arc (not a conventional oval hole) is essential to the reference.
        // Cut the counter through the fill so that underlying wallpaper remains visible.
        fun slit(points: FloatArray, width: Float) {
            val stroke = Path().apply {
                moveTo(points[0], points[1])
                cubicTo(points[2], points[3], points[4], points[5], points[6], points[7])
            }
            val cut = Path()
            cutter.strokeWidth = width
            cutter.getFillPath(stroke, cut)
            outer.op(cut, Path.Op.DIFFERENCE)
        }
        when (d) {
            0 -> slit(floatArrayOf(44f, 79f, 43f, 65f, 50f, 51f, 61f, 44f), 7.4f)
            2 -> slit(floatArrayOf(42f, 31f, 48f, 24f, 58f, 26f, 61f, 39f), 5f)
            4 -> slit(floatArrayOf(46f, 81f, 51f, 68f, 57f, 56f, 61f, 45f), 6.0f)
            6 -> slit(floatArrayOf(40f, 91f, 42f, 77f, 54f, 70f, 63f, 72f), 7f)
            8 -> {
                slit(floatArrayOf(43f, 48f, 46f, 39f, 56f, 36f, 60f, 46f), 5.5f)
                slit(floatArrayOf(40f, 106f, 43f, 95f, 57f, 93f, 61f, 105f), 6f)
            }
            9 -> slit(floatArrayOf(39f, 52f, 42f, 39f, 54f, 35f, 63f, 45f), 7f)
        }
        // Global reference units: the actual path is centered near (0, 0),
        // and has the same footprint as the original renderer's 94×141 glyphs.
        val transform = Matrix().apply {
            setTranslate(-50f, -70f)
            postScale(.94f, 1.0f)
        }
        outer.transform(transform)
        return outer
    }
}
