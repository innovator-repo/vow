package com.vow.app.service.vpn

import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateDnsTest {

    @Test fun providersAreFilteringHostnames() {
        assertTrue(PrivateDns.providers.size >= 3)
        PrivateDns.providers.forEach { provider ->
            assertTrue(provider.hostname, provider.hostname.contains('.'))
            assertTrue(provider.hostname, provider.hostname.startsWith("family"))
            assertTrue(provider.name.isNotBlank() && provider.detail.isNotBlank())
        }
    }
}
