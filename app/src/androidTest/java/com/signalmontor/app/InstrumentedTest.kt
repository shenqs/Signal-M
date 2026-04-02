package com.signalmontor.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InstrumentedTest {

    @Test
    fun useAppContext() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.signalmontor.app", appContext.packageName)
    }

    @Test
    fun testRadiationCalculator_Instrumented() {
        val result = RadiationCalculator.calculateWiFiRadiation(-50)
        assertNotNull(result)
        assertTrue(result.estimatedSAR >= 0)
        assertTrue(result.percentageOfLimit >= 0f && result.percentageOfLimit <= 100f)
    }

    @Test
    fun testRadiationStandard_Instrumented() {
        val standards = RadiationStandard.standards
        assertTrue(standards.isNotEmpty())
        assertTrue(standards.any { it.source.contains("ICNIRP") || it.source.contains("FCC") })
    }
}
