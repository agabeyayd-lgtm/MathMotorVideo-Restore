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
        Template("The Overcareful Tea Tray", "A server tries to keep a loaded tray perfectly level.", "The last glass stays upright while everything else settles.", SceneKind.TEA_TRAY),
        Template("One More Box", "A worker adds one last box to a tall stack.", "The stack settles into an absurdly neat final shape.", SceneKind.BOX_STACK),
        Template("The Toolbox That Wouldn't Stay Shut", "A mechanic carefully closes a crowded toolbox.", "One tiny tool is the only thing left moving.", SceneKind.TOOLBOX),
        Template("Broom, Bucket, Balance", "A cleaner balances a broom beside a bucket.", "The broom finds a stable resting point at the last moment.", SceneKind.BROOM_BUCKET),
        Template("The Shop Cart", "A shopkeeper tries to stop a lightly rolling cart.", "The cart stops exactly where it started to cause trouble.", SceneKind.SHOP_CART)
    )

    fun generate(seed: Long): HumanScenario {
        val world = MathEngine.generate(seed)
        val template = templates[Math.floorMod(seed, templates.size.toLong()).toInt()]
        return HumanScenario(seed, template.title, template.setup, template.payoff, template.kind, world)
    }
}
