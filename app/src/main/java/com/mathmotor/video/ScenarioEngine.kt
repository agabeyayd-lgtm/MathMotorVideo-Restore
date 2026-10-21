package com.mathmotor.video

/** The director adds a visual situation; it does not rewrite MathWorld.frames. */
object ScenarioEngine {
    private data class Template(
        val title: String,
        val setup: String,
        val payoff: String,
        val kind: SceneKind
    )

    private val templates = listOf(
        Template("Aşırı Dikkatli Çay Tepsisi", "Bir garson dolu çay tepsisini dümdüz tutmaya çalışır.", "Diğer her şey yerleşirken son bardak dik kalır.", SceneKind.TEA_TRAY),
        Template("Bir Kutu Daha", "Bir işçi yüksek istife son bir kutu daha ekler.", "İstif, gülünç derecede düzgün bir şekle oturur.", SceneKind.BOX_STACK),
        Template("Kapanmayan Alet Çantası", "Bir tamirci ağzına kadar dolu alet çantasını dikkatle kapatır.", "Hareket etmeye devam eden tek şey küçücük bir alettir.", SceneKind.TOOLBOX),
        Template("Süpürge, Kova, Denge", "Bir temizlikçi kovanın yanında süpürgeyi dengede tutmaya çalışır.", "Süpürge son anda dengeli bir noktaya oturur.", SceneKind.BROOM_BUCKET),
        Template("Dükkân Arabası", "Bir dükkân sahibi hafifçe kayan arabayı durdurmaya çalışır.", "Araba tam da başladığı yerde durup yine sorun çıkarır.", SceneKind.SHOP_CART)
    )

    fun generate(seed: Long): HumanScenario {
        val world = MathEngine.generate(seed)
        val template = templates[Math.floorMod(seed, templates.size.toLong()).toInt()]
        return HumanScenario(seed, template.title, template.setup, template.payoff, template.kind, world)
    }
}
