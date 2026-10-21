package com.mathmotor.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicsModelTest {
    @Test fun generatedWorldHasThreeHundredFramesAndPassesValidation() {
        val world = MathEngine.generate(12345L)
        assertEquals(300, world.frames.size)
        assertTrue(MathEngine.validate(world).joinToString("\n"), MathEngine.validate(world).isEmpty())
    }

    @Test fun sameSeedIsDeterministic() {
        assertEquals(MathEngine.generate(44L), MathEngine.generate(44L))
    }

    @Test fun scenarioDoesNotReplaceTheGeneratedWorld() {
        val scenario = ScenarioEngine.generate(77L)
        assertEquals(scenario.seed, scenario.world.seed)
        assertEquals(300, scenario.world.frames.size)
    }

    @Test fun frameTestReportPasses() {
        assertTrue(PhysXFrameTest.run().toDisplayText(), PhysXFrameTest.run().passed)
    }
}
