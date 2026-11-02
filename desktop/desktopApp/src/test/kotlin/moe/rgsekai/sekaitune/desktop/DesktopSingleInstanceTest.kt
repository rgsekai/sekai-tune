package moe.rgsekai.sekaitune.desktop

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DesktopSingleInstanceTest {

    @Before
    @After
    fun reset() {
        DesktopSingleInstance.cleanup()
    }

    @Test
    fun testPrimaryInstanceAcquisition() {
        val first = DesktopSingleInstance.acquireOrActivate()
        assertTrue("Primary instance should acquire the single instance lock", first)

        val second = DesktopSingleInstance.acquireOrActivate()
        assertFalse("Second instance should be rejected and activate the primary", second)

        DesktopSingleInstance.cleanup()

        val reacquired = DesktopSingleInstance.acquireOrActivate()
        assertTrue("After cleanup, a new instance should be able to acquire the lock", reacquired)
    }
}

