package com.mathmotor.video

import kotlin.math.abs

data class CheckResult(val name: String, val passed: Boolean, val detail: String)
data class PhysicsReport(val checks: List<CheckResult>) {
    val passedCount: Int get() = checks.count { it.passed }
    val totalCount: Int get() = checks.size
    val passed: Boolean get() = checks.all { it.passed }
    fun toDisplayText(): String = buildString {
        appendLine(if (passed) "FİZİK / KARE TESTİ: BAŞARILI" else "FİZİK / KARE TESTİ: BAŞARISIZ")
        appendLine("$passedCount/$totalCount checks passed")
        checks.forEach { appendLine("${if (it.passed) "PASS" else "FAIL"}  ${it.name}: ${it.detail}") }
    }
}

object PhysXFrameTest {
    fun run(): PhysicsReport {
        val checks = mutableListOf<CheckResult>()
        val world = MathEngine.generate(seed = 365L, frameCount = 300)
        val errors = MathEngine.validate(world)
        checks += CheckResult("300 karelik zaman çizelgesi", world.frameCount == 300, "kare=${world.frameCount}")
        checks += CheckResult("fiziksel sınırlar", errors.none { "outside canvas" in it }, if (errors.none { "outside canvas" in it }) "tüm noktalar görüntü alanında" else errors.first { "outside canvas" in it })
        checks += CheckResult("sonlu değerler", errors.none { "Non-finite" in it }, if (errors.none { "Non-finite" in it }) "NaN veya sonsuz değer yok" else errors.first { "Non-finite" in it })
        checks += CheckResult("hareket sürekliliği", errors.none { "Discontinuous" in it }, if (errors.none { "Discontinuous" in it }) "kareler arasında ani sıçrama yok" else errors.first { "Discontinuous" in it })
        checks += CheckResult("zaman çizelgesi uçları", errors.none { "Timeline" in it }, if (errors.none { "Timeline" in it }) "başlangıç 0, bitiş 1" else errors.first { "Timeline" in it })
        val repeat = MathEngine.generate(seed = 365L, frameCount = 300)
        val deterministic = world.frames.zip(repeat.frames).all { (a, b) ->
            a.t == b.t && a.objectX == b.objectX && a.objectY == b.objectY && a.objectAngle == b.objectAngle
        }
        checks += CheckResult("tekrarlanabilir tohum", deterministic, if (deterministic) "aynı tohum aynı dünyayı üretir" else "durumlar eşleşmiyor")
        val different = MathEngine.generate(seed = 366L, frameCount = 300)
        checks += CheckResult("tohum çeşitliliği", world.frames != different.frames, if (world.frames != different.frames) "farklı tohumlar farklı hareket üretir" else "tohumun etkisi olmadı")
        val scenario = ScenarioEngine.generate(365L)
        checks += CheckResult("senaryo aynı dünyayı kullanıyor", scenario.world.seed == scenario.seed && scenario.world.frames == world.frames, if (scenario.world.frames == world.frames) "yönetmen fiziksel durumları değiştirmedi" else "senaryo ve dünya uyuşmuyor")
        checks += CheckResult("senaryo ve sonuç bilgisi", scenario.payoff.isNotBlank() && scenario.setup.isNotBlank(), "kurulum ve sonuç mevcut")
        checks += CheckResult("son kare zamanlaması", world.frames.last().t == 1f && abs(world.frames[world.frames.lastIndex].t - 1f) < 0.0001f, "son kare zaman çizelgesinin sonunda")
        return PhysicsReport(checks)
    }
}
