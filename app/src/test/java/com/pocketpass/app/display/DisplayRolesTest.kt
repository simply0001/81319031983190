package com.pocketpass.app.display

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayRolesTest {
    @Test
    fun anbernicRgDsIsBottomPrimaryHoweverTheNameIsSpelled() {
        assertTrue(DisplayRoles.isBottomPrimaryDevice("RGDS"))
        assertTrue(DisplayRoles.isBottomPrimaryDevice("RG DS", null, null))
        assertTrue(DisplayRoles.isBottomPrimaryDevice(null, "Anbernic_RG_DS", null))
    }

    @Test
    fun otherDualScreenDevicesKeepTheTopPanelAsDefault() {
        assertFalse(DisplayRoles.isBottomPrimaryDevice("AYN Thor", "kalama", "kalama"))
        assertFalse(DisplayRoles.isBottomPrimaryDevice(null, null, null))
    }
}
