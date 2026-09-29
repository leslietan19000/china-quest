package org.chinaquest.app

import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ParentGateAcceptanceTest {
    private lateinit var context: Context
    private var clock = 1_000_000L

    @Before fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("parent_gate", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun startsWithoutDefaultPinAndRequiresParentConfiguration() {
        val gate = ParentGate(context) { clock }
        assertFalse(gate.isConfigured)
        assertFalse(gate.verify("123456"))
        gate.setPin("458726")
        assertTrue(ParentGate(context) { clock }.isConfigured)
        assertFalse(gate.verify("123456"))
        assertTrue(gate.verify("458726"))
    }

    @Test fun repeatedFailuresLockAcrossInstancesUntilTheClockAdvances() {
        val gate = ParentGate(context) { clock }
        gate.setPin("458726")
        repeat(5) { assertFalse(gate.verify("000000")) }
        val reopened = ParentGate(context) { clock }
        assertTrue(reopened.remainingLockSeconds() > 0)
        assertFalse(reopened.verify("458726"))
        clock += 60_001
        assertEquals(0, reopened.remainingLockSeconds())
        assertTrue(reopened.verify("458726"))
    }
}
