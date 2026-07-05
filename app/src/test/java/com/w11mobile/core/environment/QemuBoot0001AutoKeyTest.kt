package com.w11mobile.core.environment

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QemuBoot0001AutoKeyTest {
    @Test
    fun isStartingBoot0001Line_detectsUefiStart() {
        assertTrue(
            QemuBoot0001AutoKey.isStartingBoot0001Line(
                "BdsDxe: starting Boot0001 \"UEFI Misc Device\" from PciRoot(0x0)/Pci(0x3,0x0)",
            ),
        )
    }

    @Test
    fun isStartingBoot0001Line_ignoresLoadOnly() {
        assertFalse(
            QemuBoot0001AutoKey.isStartingBoot0001Line(
                "BdsDxe: loading Boot0001 \"UEFI Misc Device\"",
            ),
        )
    }
}
