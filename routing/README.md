# Routing module

The routing module implements the mesh-facing packet routing layer for the application. It is responsible for discovering neighbours, maintaining routing state, forwarding packets, and delivering inbound messages to the higher layers.

## Purpose

This module provides the core routing behaviour used by the mesh application:

- Track direct peers through HELLO advertisements and peer timeout handling
- Maintain a routing table for multihop delivery
- Handle route discovery with RREQ/RREP messages
- Forward application messages and control packets
- Report delivery state and routing errors back to the caller

## Main components

### RoutingModule
The entry point for the module. It wires together the router, peer manager, sender, and receiver into a single lifecycle unit.

Responsibilities:
- Create the routing subcomponents
- Start background loops for expiry, queue processing, peer reaping, and HELLO broadcasts
- Dispatch inbound packets from the transport layer through the receiver

Key objects:
- router: the routing table manager
- peers: the direct-neighbour registry
- sender: the outbound packet builder and sender
- receiver: the inbound packet dispatcher
- inboundPackets: a channel that receives packets paired with their source IP

Key functions:
- start(scope, displayName): begins all background loops and starts packet dispatching
- stop(): closes the inbound channel and stops the dispatch loop

### Router
Maintains the internal routing table.

Responsibilities:
- Store routes as destination -> next hop mappings
- Update routes when better paths are discovered
- Invalidate routes when links fail or peers disappear

Key functions:
- lookup(destinationNodeId): returns the next hop for a destination if a valid route exists
- update(destinationNodeId, nextHopNodeId, hopCount): installs or improves a route
- invalidate(destinationNodeId): marks a route invalid without removing it immediately
- invalidateVia(nextHopNodeId): invalidates all routes that depend on a failed next hop
- getRoutes(): returns the currently valid routes

Routes are keyed by `routeTimestamp`, which is refreshed from HELLO message route
timestamps and via `updateRouteFromHeader`. There is no time-based expiry loop.

### PeersManagement
Tracks direct neighbours and their IP addresses.

Responsibilities:
- Store known peers and their metadata
- Verify claimed node identities using public-key hashing
- Time out stale peers
- Broadcast HELLO messages periodically

Key functions:
- addOrUpdate(nodeId, ip, name, publicKey): inserts or refreshes a peer entry
- remove(nodeId): removes a peer explicitly
- resolveIp(nodeId): returns the stored IP for a known peer
- isDirectPeer(nodeId): checks whether the node is a direct neighbour
- lookupPublicKey(nodeId): returns the public key for a peer
- getPeers(): returns a snapshot of all peers
- verifyNodeId(publicKey, claimedNodeId): validates that a public key matches a node ID
- startReaperLoop(scope, sender): removes stale peers and triggers route invalidation
- startHelloBroadcastLoop(scope, sender, displayName): periodically sends HELLO packets

### Sender
Owns outbound packet construction and delivery.

Responsibilities:
- Queue outgoing messages
- Build serialized packets for HELLO, RREQ, RREP, ACK, and RERR
- Deliver directly to known peers or through the routing table
- Trigger route discovery when no route exists
- Report send outcomes through status updates

Key functions:
- enqueue(messageId, payload, destinationNodeId): adds an outbound message to the queue
- broadcastHello(displayName): sends a HELLO packet advertising current routes
- broadcastRerr(unreachable): sends a route error packet for unreachable destinations
- sendRrep(rreqId, rreqOriginatorNodeId, upstreamIp): replies to an RREQ toward the requester
- sendAck(messageId, destNodeId, status): sends an ACK for a message
- forwardTcp(bytes, ip): forwards a prepared TCP packet to a peer IP
- forwardUdpBroadcast(bytes): forwards a prepared UDP broadcast packet
- onAckReceived(messageId): marks a pending message as delivered when an ACK arrives
- onTcpError(failedIp): marks pending messages as failed if their path used a failed IP
- startQueueLoop(scope): begins processing the outbound queue continuously
- buildPacket(...): assembles the final packet bytes from header and payload
- processMessage(msg): handles queue processing for a single outbound message

### Receiver
Processes inbound packets and dispatches them to the appropriate handler.

Responsibilities:
- Validate packet freshness
- Handle HELLO, MESSAGE, RREQ, RREP, ACK, and RERR packets
- Update peer and routing state based on incoming control packets
- Forward messages toward their destination
- Deliver messages addressed to this node to the upper layer

Key functions:
- onPacketReceived(packet, senderIp): entry point for all inbound packets
- incomingMessageChannel: channel used to deliver messages intended for this node
- handleHello(packet, senderIp): learns peers and routes from HELLO packets
- handleMessage(packet, senderIp): forwards or delivers application messages
- handleRreq(packet, senderIp): processes route request packets and may trigger RREP
- handleRrep(packet, senderIp): processes route reply packets and installs reverse routes
- handleAck(packet): handles ACK packets and notifies sender state
- handleRerr(packet): invalidates broken routes and propagates error information
- rebuildWithHop(packet, newHopCount): rebuilds a packet with an updated hop count

### RoutingModels
Defines the shared data structures used across the module:
- Peer: a direct neighbour with node ID, IP, name, public key, and last-seen time
- RouteInfo: one route entry mapping a destination to a next hop and hop count
- PeerEvent: events emitted when peers are added, updated, or removed
- QueuedMessage: an outbound message waiting in the sender queue
- SendStatus: delivery lifecycle states such as SENT, DELIVERED, and FAILED

## Runtime flow

1. The application creates a RoutingModule with the local node identity and a transport implementation.
2. The module starts background loops for:
   - route expiry
   - outbound queue processing
   - peer reaping
   - HELLO broadcasting
3. Packets arriving from the transport layer are passed into the inbound packet channel.
4. The receiver inspects each packet type and updates routing state or forwards it as needed.
5. Outbound application messages are queued in Sender and either delivered directly or discovered through RREQ.

## Notes and assumptions

- The current transport layer is expected to provide the sender IP address together with inbound packets.
- Route discovery relies on HELLO and RREQ/RREP control packets.
- Packet freshness is checked before processing to avoid acting on stale traffic.
- The module depends on the model, packetProcessor, and transport modules.

## Typical usage

A normal startup sequence looks like this:

```kotlin
val routingModule = RoutingModule(selfNodeId, selfPublicKey, selfName, transport)
routingModule.start(scope, displayName)
```

When the module is no longer needed, it can be stopped with:

```kotlin
routingModule.stop()
```
