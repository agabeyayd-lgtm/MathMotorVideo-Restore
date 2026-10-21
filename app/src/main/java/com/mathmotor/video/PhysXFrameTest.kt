package com.mathmotor.video

import kotlin.math.abs

data class CheckResult(val name: String, val passed: Boolean, val detail: String)
data class PhysicsReport(val checks: List<CheckResult>) {
    val passedCount: Int get() = checks.count { it.passed }
    val totalCount: Int get() = checks.size
    val passed: Boolean get() = checks.all { it.passed }
    fun toDisplayText(): String = buildString {
        appendLine(if (passed) "PHYSX / FRAME TEST: PASS" else "PHYSX / FRAME TEST: FAIL")
        appendLine("$passedCount/$totalCount checks passed")
        checks.forEach { appendLine("${if (it.passed) "PASS" else "FAIL"}  ${it.name}: ${it.detail}") }
    }
}

object PhysXFrameTest {
    fun run(): PhysicsReport {
        val checks = mutableListOf<CheckResult>()
        val world = MathEngine.generate(seed = 365L, frameCount = 300)
        val errors = MathEngine.validate(world)
        checks += CheckResult("300-frame timeline", world.frameCount == 300, "frames=${world.frameCount}")
        checks += CheckResult("bounded physical state", errors.none { "outside canvas" in it }, if (errors.none { "outside canvas" in it }) "all points inside canvas" else errors.first { "outside canvas" in it })
        checks += CheckResult("finite values", errors.none { "Non-finite" in it }, if (errors.none { "Non-finite" in it }) "no NaN/Infinity" else errors.first { "Non-finite" in it })
        checks += CheckResult("continuity", errors.none { "Discontinuous" in it }, if (errors.none { "Discontinuous" in it }) "no frame-to-frame teleport" else errors.first { "Discontinuous" in it })
        checks += CheckResult("timeline endpoints", errors.none { "Timeline" in it }, if (errors.none { "Timeline" in it }) "t=0.0 to t=1.0" else errors.first { "Timeline" in it })
        val repeat = MathEngine.generate(seed = 365L, frameCount = 300)
        val deterministic = world.frames.zip(repeat.frames).all { (a, b) ->
            a.t == b.t && a.objectX == b.objectX && a.objectY == b.objectY && a.objectAngle == b.objectAngle
        }
        checks += CheckResult("deterministic seed", deterministic, if (deterministic) "same seed reproduces same world" else "state mismatch")
        val different = MathEngine.generate(seed = 366L, frameCount = 300)
        checks += CheckResult("seed diversity", world.frames != different.frames, if (world.frames != different.frames) "different seeds produce different motion" else "seed had no effect")
        val scenario = ScenarioEngine.generate(365L)
        checks += CheckResult("scenario owns the same world", scenario.world.seed == scenario.seed && scenario.world.frames == world.frames, if (scenario.world.frames == world.frames) "director did not rewrite physical states" else "scenario/world mismatch")
        checks += CheckResult("payoff metadata", scenario.payoff.isNotBlank() && scenario.setup.isNotBlank(), "setup and payoff present")
        checks += CheckResult("final-frame window", world.frames.last().t == 1f && abs(world.frames[world.frames.lastIndex].t - 1f) < 0.0001f, "last frame is on timeline end")
        return PhysicsReport(checks)
    }
}
