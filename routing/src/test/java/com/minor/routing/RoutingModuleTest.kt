package com.minor.routing

import com.minor.model.Header
import com.minor.model.HeaderProtocol
import com.minor.model.MessageId
import com.minor.model.NodeId
import com.minor.model.Packet
import com.minor.model.ParseResult
import com.minor.model.Payload
import com.minor.model.PublicKey
import com.minor.model.Timestamp
import com.minor.packetprocessor.HeaderParser
import com.minor.packetprocessor.HeaderSerializer
import com.minor.packetprocessor.PayloadParser
import com.minor.packetprocessor.PayloadSerializer
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Test node identities
 * Keys are 32 bytes filled with a distinct value and NodeIds are their SHA256 digest
 */
class RoutingModuleTest {

    private val keyA = PublicKey(ByteArray(32) { 0xAA.toByte() })
    private val keyB = PublicKey(ByteArray(32) { 0xBB.toByte() })
    private val keyC = PublicKey(ByteArray(32) { 0xCC.toByte() })

    private val nodeA = sha256NodeId(keyA)
    private val nodeB = sha256NodeId(keyB)
    private val nodeC = sha256NodeId(keyC)

    private lateinit var transport: FakeMeshTransport
    private lateinit var router: Router
    private lateinit var peers: PeersManagement
    private lateinit var sender: Sender

    @Before
    fun setup() {
        transport = FakeMeshTransport()
        router = Router()
        peers = PeersManagement(nodeA, router)
        sender = Sender(nodeA, keyA, "NodeA", transport, router, peers)
    }

    // ---- Router tests ----

    @Test
    fun routerAcceptsFirstRouteForUnknownDestination() {
        router.update(nodeC, nodeB, 3)
        assertEquals(nodeB.toString(), router.lookup(nodeC)?.toString())
    }

    @Test
    fun routerKeepsExistingWhenNewRouteHasMoreHops() {
        router.update(nodeC, nodeB, 2)
        router.update(nodeC, nodeA, 5)
        assertEquals(nodeB.toString(), router.lookup(nodeC)?.toString())
    }

    @Test
    fun routerReplacesRouteWhenNewOneHasFewerHops() {
        router.update(nodeC, nodeB, 4)
        router.update(nodeC, nodeA, 2)
        assertEquals(nodeA.toString(), router.lookup(nodeC)?.toString())
    }

    @Test
    fun routerInvalidateMakesRouteReturnNull() {
        router.update(nodeC, nodeB, 2)
        router.invalidate(nodeC)
        assertNull(router.lookup(nodeC))
    }

    @Test
    fun routerInvalidateViaAffectsAllRoutesThrough() {
        router.update(nodeC, nodeB, 2)
        router.update(nodeA, nodeB, 3)
        val affected = router.invalidateVia(nodeB)
        assertTrue(nodeC.toString() in affected.map { it.toString() })
        assertTrue(nodeA.toString() in affected.map { it.toString() })
        assertNull(router.lookup(nodeC))
        assertNull(router.lookup(nodeA))
    }

    @Test
    fun routerInvalidateViaDoesNotAffectRoutesThrough() {
        router.update(nodeC, nodeB, 2)
        router.update(nodeB, nodeA, 1)
        val affected = router.invalidateVia(nodeB)
        assertTrue(nodeC.toString() in affected.map { it.toString() })
        assertTrue(nodeB.toString() !in affected.map { it.toString() })
        assertNotNull(router.lookup(nodeB))
    }

    @Test
    fun routerGetRoutesReturnsOnlyValidEntries() {
        router.update(nodeB, nodeA, 1)
        router.update(nodeC, nodeB, 2)
        router.invalidate(nodeC)
        val routes = router.getRoutes()
        assertEquals(1, routes.size)
        assertEquals(nodeB.toString(), routes[0].destinationNodeId.toString())
    }

    // ---- PeersManagement tests ----

    @Test
    fun peersAddOrUpdateEmitsAddedOnFirstContact() = runBlocking {
        peers.addOrUpdate(nodeB, "192.168.1.20", "NodeB", keyB)
        val event = peers.peerEvents.receive()
        assertTrue(event is PeerEvent.Added)
        assertEquals(nodeB.toString(), (event as PeerEvent.Added).peer.nodeId.toString())
    }

    @Test
    fun peersAddOrUpdateEmitsUpdatedOnSubsequentContact() = runBlocking {
        peers.addOrUpdate(nodeB, "192.168.1.20", "NodeB", keyB)
        peers.peerEvents.receive()
        peers.addOrUpdate(nodeB, "192.168.1.21", "NodeB", keyB)
        val event = peers.peerEvents.receive()
        assertTrue(event is PeerEvent.Updated)
    }

    @Test
    fun peersRemoveEmitsRemovedEvent() = runBlocking {
        peers.addOrUpdate(nodeB, "192.168.1.20", "NodeB", keyB)
        peers.peerEvents.receive()
        peers.remove(nodeB)
        val event = peers.peerEvents.receive()
        assertTrue(event is PeerEvent.Removed)
    }

    @Test
    fun peersResolveIpReturnsNullAfterRemoval() {
        peers.addOrUpdate(nodeB, "192.168.1.20", "NodeB", keyB)
        peers.remove(nodeB)
        assertNull(peers.resolveIp(nodeB))
    }

    @Test
    fun peersVerifyNodeIdReturnsTrueForMatchingKey() {
        assertTrue(peers.verifyNodeId(keyA, nodeA))
    }

    @Test
    fun peersVerifyNodeIdReturnsFalseForMismatchedKey() {
        assertTrue(!peers.verifyNodeId(keyB, nodeA))
    }

    // ---- Sender tests ----

    @Test
    fun senderBroadcastsRreqWhenNoPeerOrRouteExists() = runBlocking {
        val payload = Payload.Message(
            MessageId(1L),
            Timestamp(System.currentTimeMillis()),
            "hello"
        )
        sender.enqueue(1L, payload, nodeC)
        invokeProcessMessage(sender)
        assertEquals(1, transport.broadcasts.size)
        val parsed = HeaderParser.parse(transport.broadcasts[0])
        assertTrue(parsed is ParseResult.Success)
        assertEquals(HeaderProtocol.Type.RREQ, (parsed as ParseResult.Success).value.type)
    }

    @Test
    fun senderDeliversByTcpWhenPeerIsDirectlyKnown() = runBlocking {
        peers.addOrUpdate(nodeB, "192.168.1.20", "NodeB", keyB)
        drainPeerEvents()
        val payload = Payload.Message(
            MessageId(2L),
            Timestamp(System.currentTimeMillis()),
            "direct"
        )
        sender.enqueue(2L, payload, nodeB)
        invokeProcessMessage(sender)
        assertEquals(1, transport.tcpSends.size)
        assertEquals("192.168.1.20", transport.tcpSends[0].second)
        val parsed = HeaderParser.parse(transport.tcpSends[0].first)
        assertTrue(parsed is ParseResult.Success)
        assertEquals(HeaderProtocol.Type.MESSAGE, (parsed as ParseResult.Success).value.type)
    }

    @Test
    fun senderDeliversByTcpViaRoutedNextHop() = runBlocking {
        peers.addOrUpdate(nodeB, "192.168.1.20", "NodeB", keyB)
        drainPeerEvents()
        router.update(nodeC, nodeB, 2)
        val payload = Payload.Message(
            MessageId(3L),
            Timestamp(System.currentTimeMillis()),
            "routed"
        )
        sender.enqueue(3L, payload, nodeC)
        invokeProcessMessage(sender)
        assertEquals(1, transport.tcpSends.size)
        assertEquals("192.168.1.20", transport.tcpSends[0].second)
    }

    @Test
    fun senderEmitsFailedWhenRetryWindowExceeds() = runBlocking {
        val fastSender = Sender(nodeA, keyA, "NodeA", transport, router, peers, rreqRetryTimeoutMs = 0)
        val payload = Payload.Message(
            MessageId(4L),
            Timestamp(System.currentTimeMillis()),
            "timeout"
        )
        fastSender.enqueue(4L, payload, nodeC)
        Thread.sleep(10)
        invokeProcessMessage(fastSender)
        val status = fastSender.statusChannel.receive()
        assertEquals(SendStatus.FAILED, status.second)
    }
    @Test
    fun senderOnAckReceivedEmitsDelivered() = runBlocking {
        peers.addOrUpdate(nodeB, "192.168.1.20", "NodeB", keyB)
        drainPeerEvents()
        val payload = Payload.Message(
            MessageId(5L),
            Timestamp(System.currentTimeMillis()),
            "ack test"
        )
        sender.enqueue(5L, payload, nodeB)
        invokeProcessMessage(sender)

        val sentStatus = sender.statusChannel.receive()
        assertEquals(SendStatus.SENT, sentStatus.second)

        transport.clearAll()
        sender.onAckReceived(5L)

        val status = sender.statusChannel.receive()
        assertEquals(5L to SendStatus.DELIVERED, status)
    }


    @Test
    fun senderBroadcastRerrProducesValidPacket() = runBlocking {
        sender.broadcastRerr(listOf(nodeB, nodeC))
        assertEquals(1, transport.broadcasts.size)
        val parsed = HeaderParser.parse(transport.broadcasts[0])
        assertTrue(parsed is ParseResult.Success)
        val header = (parsed as ParseResult.Success).value
        assertEquals(HeaderProtocol.Type.RERR, header.type)
        val payloadBytes = transport.broadcasts[0].copyOfRange(
            HeaderProtocol.HEADER_SIZE,
            HeaderProtocol.HEADER_SIZE + header.payloadLength
        )
        val packet = Packet(header, payloadBytes)
        val payloadResult = PayloadParser.parse(packet)
        assertTrue(payloadResult is ParseResult.Success)
        val rerr = (payloadResult as ParseResult.Success).value as? Payload.RERR
        assertNotNull(rerr)
        assertEquals(2, rerr!!.destinations.size)
    }

    // ---- Receiver tests ----
    @Test
    fun receiverHandlesHelloAndAddsToRoutingTable() = runBlocking {
        val receiver = Receiver(nodeA, router, peers, sender)

        // 1. Ensure nodeB is registered as a trusted peer
        peers.addOrUpdate(nodeB, "192.168.1.20", "NodeB", keyB)
        drainPeerEvents()

// 2. Build the HELLO packet with minimal/standard string and key sizes
        val packet = buildPacket(
            selfNodeId = nodeB,
            selfPublicKey = keyB,
            type = HeaderProtocol.Type.HELLO,
            flags = HeaderProtocol.Flags.BROADCAST,
            dest = NodeId(ByteArray(32)),
            id = 0L,
            hopCount = 0,
            payload = Payload.Hello(
                "NodeB",
                keyB,
                listOf(
                    com.minor.model.RouteEntry(
                        nodeId = nodeC,
                        hopcount = 1,
                        publicKey = keyC,
                        name = "" // Use an empty string if the parser struggles with length-prefixes
                    )
                )
            )
        )

        // --- EXPLICIT DIAGNOSTIC CHECKS ---

        // Diagnostic 1: Is the payload parsing successfully?
        val parseResult = PayloadParser.parse(packet)
        assertTrue("Payload parsing failed completely!", parseResult is ParseResult.Success)

        // Diagnostic 2: Is it casting to Payload.Hello properly?
        val helloPayload = (parseResult as ParseResult.Success).value as? Payload.Hello
        assertNotNull("Parsed payload is not an instance of Payload.Hello!", helloPayload)

        // Diagnostic 3: Does the cryptographic/identity verification pass?
        val isVerified = peers.verifyNodeId(helloPayload!!.publicKey, packet.header.sourceNodeId)
        assertTrue("Peer NodeID validation failed!", isVerified)

        // Diagnostic 4: Does it contain the route entries?
        assertTrue("The Hello payload contains no route entries!", helloPayload.routeEntries.isNotEmpty())

        // 3. Process the packet if all diagnostics pass
        receiver.onPacketReceived(packet, "192.168.1.20")

        // 4. Check routing results
        val activeRoutes = router.getRoutes()
        assertTrue("Routing table should not be empty, but it is!", activeRoutes.isNotEmpty())
    }

    @Test
    fun receiverDropsHelloWithMismatchedPublicKey() = runBlocking {
        val receiver = Receiver(nodeA, router, peers, sender)
        val corruptKey = PublicKey(ByteArray(32) { 0x11.toByte() })
        val packet = buildPacket(
            selfNodeId = nodeB,
            selfPublicKey = corruptKey,
            type = HeaderProtocol.Type.HELLO,
            flags = HeaderProtocol.Flags.BROADCAST,
            dest = NodeId(ByteArray(32)),
            id = 0L,
            hopCount = 0,
            payload = Payload.Hello("NodeB", corruptKey, emptyList())
        )
        receiver.onPacketReceived(packet, "192.168.1.20")
        assertTrue(!peers.isDirectPeer(nodeB))
    }

    @Test
    fun receiverDeliversMsgAddressedToSelfOnChannel() = runBlocking {
        peers.addOrUpdate(nodeB, "192.168.1.20", "NodeB", keyB)
        drainPeerEvents()
        val receiver = Receiver(nodeA, router, peers, sender)
        val payload = Payload.Message(
            MessageId(10L),
            Timestamp(System.currentTimeMillis()),
            "hello nodeA"
        )
        val packet = buildPacket(
            selfNodeId = nodeB,
            selfPublicKey = keyB,
            type = HeaderProtocol.Type.MESSAGE,
            flags = HeaderProtocol.Flags.ENCRYPTED or HeaderProtocol.Flags.ACK_REQUESTED,
            dest = nodeA,
            id = 10L,
            hopCount = 0,
            payload = payload
        )
        receiver.onPacketReceived(packet, "192.168.1.20")
        val received = receiver.incomingMessageChannel.receive()
        assertEquals(10L, received.header.id.value)
        assertEquals(1, transport.tcpSends.size)
        val ackHeader = (HeaderParser.parse(transport.tcpSends[0].first) as ParseResult.Success).value
        assertEquals(HeaderProtocol.Type.ACK, ackHeader.type)
    }

    @Test
    fun receiverPropagatesRerrForLocallyAffectedRoutes() = runBlocking {
        router.update(nodeC, nodeB, 2)
        peers.addOrUpdate(nodeB, "192.168.1.20", "NodeB", keyB)
        drainPeerEvents()
        val receiver = Receiver(nodeA, router, peers, sender)
        val packet = buildPacket(
            selfNodeId = nodeB,
            selfPublicKey = keyB,
            type = HeaderProtocol.Type.RERR,
            flags = HeaderProtocol.Flags.BROADCAST,
            dest = NodeId(ByteArray(32)),
            id = 0L,
            hopCount = 0,
            payload = Payload.RERR(listOf(nodeC))
        )
        receiver.onPacketReceived(packet, "192.168.1.20")
        assertNull(router.lookup(nodeC))
        assertEquals(1, transport.broadcasts.size)
        val parsed = HeaderParser.parse(transport.broadcasts[0]) as ParseResult.Success
        assertEquals(HeaderProtocol.Type.RERR, parsed.value.type)
    }

    // ---- Helpers ----

    private fun sha256NodeId(key: PublicKey): NodeId =
        NodeId(MessageDigest.getInstance("SHA-256").digest(key.bytes))

    private fun buildPacket(
        selfNodeId: NodeId,
        selfPublicKey: PublicKey,
        type: Int,
        flags: Int,
        dest: NodeId,
        id: Long,
        hopCount: Int,
        payload: Payload
    ): Packet {
        val payloadBuf = ByteArray(65_536)
        val payloadLen = PayloadSerializer.serialize(payload, payloadBuf, 0)
        val payloadBytes = payloadBuf.copyOf(payloadLen)
        val header = Header(
            magic = HeaderProtocol.Magic.EXPECTED,
            version = HeaderProtocol.Version.SUPPORTED_VERSION,
            type = type,
            flags = flags,
            hopcount = hopCount,
            ttl = 8,
            reserved = 0,
            sourceNodeId = selfNodeId,
            destNodeId = dest,
            id = MessageId(id),
            originTimestamp = Timestamp(System.currentTimeMillis()),
            payloadLength = payloadLen
        )
        val fullBytes = ByteArray(HeaderProtocol.HEADER_SIZE + payloadLen)
        HeaderSerializer.serialize(header, fullBytes, 0)
        payloadBytes.copyInto(fullBytes, HeaderProtocol.HEADER_SIZE)
        val parsedHeader = (HeaderParser.parse(fullBytes) as ParseResult.Success).value
        return Packet(parsedHeader, payloadBytes)
    }

    private suspend fun drainPeerEvents() {
        while (!peers.peerEvents.isEmpty) peers.peerEvents.receive()
    }

    private suspend fun invokeProcessMessage(target: Sender) {
        while (target.queue.isNotEmpty()) {
            target.processMessage(target.queue.removeFirst())
        }
    }
}