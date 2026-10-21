package com.mathmotor.video

import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

data class MotionFrame(
    val t: Float,
    val objectX: Float,
    val objectY: Float,
    val objectAngle: Float,
    val handX: Float,
    val velocity: Float
)

data class MathWorld(val seed: Long, val frames: List<MotionFrame>) {
    val frameCount: Int get() = frames.size
}

/** Deterministic state generator. Physical state is generated first and never patched by the director. */
object MathEngine {
    const val OUTPUT_FRAMES = 300

    fun generate(seed: Long, frameCount: Int = OUTPUT_FRAMES): MathWorld {
        require(frameCount >= 2) { "A world needs at least two frames." }
        val random = Random(seed)
        var x = 0.72f
        var y = 0.42f + random.nextFloat() * 0.12f
        var vx = -0.0015f - random.nextFloat() * 0.0008f
        var vy = -0.001f + random.nextFloat() * 0.0015f
        val gravity = 0.000035f
        val frames = ArrayList<MotionFrame>(frameCount)

        repeat(frameCount) { index ->
            val t = index.toFloat() / (frameCount - 1).toFloat()
            frames += MotionFrame(
                t = t,
                objectX = x,
                objectY = y,
                objectAngle = (sin(t * 6.2831853f * 1.35f) * 24f),
                handX = 0.22f + 0.32f * t + 0.015f * sin(t * 6.2831853f),
                velocity = kotlin.math.sqrt(vx * vx + vy * vy)
            )
            x += vx
            y += vy
            vy += gravity
            if (x < 0.14f) { x = 0.14f; vx = abs(vx) * 0.78f }
            if (x > 0.86f) { x = 0.86f; vx = -abs(vx) * 0.78f }
            if (y < 0.20f) { y = 0.20f; vy = abs(vy) * 0.62f }
            if (y > 0.78f) { y = 0.78f; vy = -abs(vy) * 0.58f }
            vx += (random.nextFloat() - 0.5f) * 0.000018f
            vx = vx.coerceIn(-0.004f, 0.004f)
        }
        return MathWorld(seed, frames.toList())
    }

    fun validate(world: MathWorld): List<String> {
        val errors = mutableListOf<String>()
        if (world.frames.size != OUTPUT_FRAMES) errors += "Expected $OUTPUT_FRAMES frames; got ${world.frames.size}."
        if (world.frames.isEmpty()) return errors + "No frames generated."
        world.frames.forEachIndexed { i, f ->
            val values = listOf(f.t, f.objectX, f.objectY, f.objectAngle, f.handX, f.velocity)
            if (values.any { !it.isFinite() }) errors += "Non-finite value at frame $i."
            if (f.objectX !in 0.0f..1.0f || f.objectY !in 0.0f..1.0f) errors += "Object outside canvas at frame $i."
            if (f.handX !in 0.0f..1.0f) errors += "Hand outside canvas at frame $i."
            if (i > 0) {
                val p = world.frames[i - 1]
                if (kotlin.math.abs(f.objectX - p.objectX) > 0.035f || kotlin.math.abs(f.objectY - p.objectY) > 0.035f) {
                    errors += "Discontinuous movement between frames ${i - 1} and $i."
                }
            }
        }
        if (world.frames.first().t != 0f || world.frames.last().t != 1f) errors += "Timeline must begin at 0 and end at 1."
        return errors.distinct()
    }
}

enum class SceneKind(val label: String) {
    TEA_TRAY("Çay tepsisi"),
    BOX_STACK("Kutu istifi"),
    TOOLBOX("Alet çantası"),
    BROOM_BUCKET("Süpürge ve kova"),
    SHOP_CART("Dükkân arabası")
}

data class HumanScenario(
    val seed: Long,
    val title: String,
    val setup: String,
    val payoff: String,
    val kind: SceneKind,
    val world: MathWorld
)
