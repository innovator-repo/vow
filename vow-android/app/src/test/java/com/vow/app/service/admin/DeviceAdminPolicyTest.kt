package com.vow.app.service.admin

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * An admin that declares no policy can fail to activate, and a wipe-data declaration gets the
 * app flagged as malware. Both are easy to reintroduce by editing the XML, so pin them here.
 */
class DeviceAdminPolicyTest {

    private val xml = File("src/main/res/xml/device_admin_policies.xml").readText()

    @Test fun declaresExactlyForceLock() {
        assertTrue("must declare force-lock", xml.contains("<force-lock"))
        assertTrue("must keep a uses-policies block", xml.contains("<uses-policies>"))
    }

    @Test fun declaresNothingAlarming() {
        listOf("wipe-data", "reset-password", "disable-camera", "watch-login", "expire-password")
            .forEach { assertFalse("must not declare $it", xml.contains("<$it")) }
    }
}
