package com.minor.ui.fake

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeDataProviderTest {
    @Test
    fun fakeDataProvider_containsExpectedMeshNodes() {
        val nodes = FakeDataProvider.nodes

        assertTrue(nodes.isNotEmpty())
        assertEquals("Node Alpha", nodes.first().name)
        assertTrue(nodes.first().isOnline)
    }
}
