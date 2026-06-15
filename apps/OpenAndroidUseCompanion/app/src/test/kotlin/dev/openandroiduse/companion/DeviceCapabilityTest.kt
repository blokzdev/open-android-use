package dev.openandroiduse.companion

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceCapabilityTest {

    private fun gib(n: Long): Long = n * 1024L * 1024L * 1024L

    private fun tier(
        ramGib: Long,
        cores: Int = 8,
        sdk: Int = 34,
        is64Bit: Boolean = true,
        lowRam: Boolean = false,
    ): DeviceTier = classifyTier(gib(ramGib), cores, sdk, is64Bit, lowRam)

    @Test
    fun roomyModernMultiCore64BitIsHigh() {
        assertEquals(DeviceTier.HIGH, tier(ramGib = 8, cores = 6, sdk = 31))
        assertEquals(DeviceTier.HIGH, tier(ramGib = 12, cores = 8, sdk = 34))
    }

    @Test
    fun lowRamFlagIsAlwaysLow() {
        // Even a roomy device flagged low-RAM (Android Go) degrades to LOW.
        assertEquals(DeviceTier.LOW, tier(ramGib = 8, cores = 8, sdk = 34, lowRam = true))
    }

    @Test
    fun thirtyTwoBitIsLow() {
        assertEquals(DeviceTier.LOW, tier(ramGib = 8, cores = 8, is64Bit = false))
    }

    @Test
    fun underFourGibIsLow() {
        assertEquals(DeviceTier.LOW, tier(ramGib = 3))
    }

    @Test
    fun midRangeIsMedium() {
        // 6 GiB, plenty of cores, modern — capable but not HIGH (needs >= 8 GiB).
        assertEquals(DeviceTier.MEDIUM, tier(ramGib = 6))
    }

    @Test
    fun roomyButOldOrFewCoresIsMedium() {
        assertEquals(DeviceTier.MEDIUM, tier(ramGib = 8, cores = 8, sdk = 28)) // pre-Android 12
        assertEquals(DeviceTier.MEDIUM, tier(ramGib = 8, cores = 4, sdk = 34)) // too few cores
    }

    @Test
    fun tierIsDerivedFromTheDataClass() {
        val cap = DeviceCapability(
            totalRamBytes = gib(8),
            cpuCores = 8,
            sdkInt = 34,
            is64Bit = true,
            isLowRamDevice = false,
        )
        assertEquals(DeviceTier.HIGH, cap.tier)
    }
}
