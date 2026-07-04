# MeshApp — Complete Project Architecture Documentation

> Generated: 2026-07-04  
> Purpose: Full architectural analysis for UI integration planning  
> Status: Analysis only — no code was modified

---

## Table of Contents

1. [Module Overview](#1-module-overview)
2. [Folder Overview](#2-folder-overview)
3. [Dependency Graph](#3-dependency-graph)
4. [Public APIs](#4-public-apis)
5. [Data Flow Diagrams](#5-data-flow-diagrams)
6. [Existing Fake Data](#6-existing-fake-data)
7. [Real Backend Replacements](#7-real-backend-replacements)
8. [UI Integration Plan](#8-ui-integration-plan)
9. [Integration Checklist](#9-integration-checklist)
10. [Remaining TODOs](#10-remaining-todos)
11. [Missing Wiring](#11-missing-wiring)
12. [Suggested Order for Integration](#12-suggested-order-for-integration)
13. [Summary](#13-summary)

---

## 1. Module Overview

### 1.1 `model` — Shared Data Contracts

| Item | Detail |
|---|---|
| **Package** | `com.minor.model` |
| **Purpose** | Pure Kotlin data classes, value classes, sealed interfaces, and binary protocol objects. No Android dependencies. Shared by every other module. |
| **Status** | ✅ Complete |

**Public Classes / Types:**

| Type | Kind | Description |
|---|---|---|
| `NodeId` | `@JvmInline value class(ByteArray[32])` | 32-byte unique node identity |
| `MessageId` | `@JvmInline value class(Long)` | 64-bit message identifier |
| `PublicKey` | `@JvmInline value class(ByteArray[32])` | 32-byte Ed25519 public key |
| `Signature` | `@JvmInline value class(ByteArray[64])` | 64-byte signature |
| `Timestamp` | `@JvmInline value class(Long)` | Unix millis timestamp |
| `Header` | `data class` | Full parsed packet header (magic, version, type, flags, hopcount, ttl, srcNodeId, dstNodeId, id, originTimestamp, payloadLength) |
| `Packet` | `data class` | Pair of `Header` + raw `ByteArray` payload |
| `Envelope` | `data class` | `Packet` + `InetSocketAddress` remote address (transport origin) |
| `RouteEntry` | `data class` | One entry inside a HELLO payload (nodeId, hopcount, publicKey, timestamp, name) |
| `Payload` | `sealed interface` | Discriminated union of all payload types |
| `Payload.Hello` | `data class` | HELLO payload: name, publicKey, routeEntries |
| `Payload.Message` | `data class` | MSG payload: messageId, timestamp, content (plaintext) |
| `Payload.Ack` | `data class` | ACK payload: status Int, signature |
| `Payload.RREQ` | `data class` | Route Request: name, publicKey |
| `Payload.RREP` | `data class` | Route Reply: name, publicKey |
| `Payload.RERR` | `data class` | Route Error: list of unreachable `NodeId`s |
| `ParseError` | `sealed interface` | TooShort, InvalidMagic, InvalidVersion, InvalidPayloadLength, UnsupportedType, MalformedPayload |
| `ParseResult<T>` | `sealed interface` | `Success(value: T)` / `Failure(error: ParseError)` |
| `Field<T>` | `interface` | Binary field read/write contract used by all `*Protocol` objects |
| `ReadWithLength<T>` | `data class` | Result of a field read: value + bytesRead |
| `HeaderProtocol` | `object` | Field objects for every header field + constants (HEADER_SIZE, Magic, Version, Type, Flags, etc.) |
| `HelloProtocol` | `object` | Field objects for HELLO payload binary layout |
| `MessageProtocol` | `object` | Field objects for MSG payload binary layout |
| `AckProtocol` | `object` | Field objects for ACK payload binary layout |
| `RREQProtocol` | `object` | Field objects for RREQ payload binary layout |
| `RREPProtocol` | `object` | Field objects for RREP payload binary layout |
| `RERRProtocol` | `object` | Field objects for RERR payload binary layout |
| `RouteProtocol` | `object` | Field objects for `RouteEntry` binary layout inside HELLO |
| `PrimitiveHelpers` | `(file-level fns)` | `readU8`, `readI64`, `readString`, `writeBytes`, etc. |

**Dependencies:** None (pure Kotlin).

---

### 1.2 `transport` — Network I/O

| Item | Detail |
|---|---|
| **Package** | `com.minor.network` |
| **Purpose** | Low-level UDP broadcast and TCP unicast. Emits `Envelope` objects upstream. Provides hardware capability queries. |
| **Status** | ✅ Complete |

**Public Classes:**

| Class | Description |
|---|---|
| `UdpSocket(context, port, scope, useMulticastLock)` | Binds a broadcast UDP socket. `start()` launches a receive loop emitting `Envelope` via `incoming: ReceiveChannel<Envelope>`. Parses the header on arrival to construct the `Packet`. `send(bytes, address)` sends unicast/broadcast UDP. `close()` tears down. |
| `TCPReceiver(port, scope)` | Binds a `ServerSocket`. `start()` accepts connections, creates `Client` workers per connection, emits `Envelope` via `incoming: ReceiveChannel<Envelope>`. `close()` shuts down. |
| `TCPSender(port, scope)` | Maintains a pooled connection map. `send(bytes, address)` sends TCP with connection reuse and auto-cleanup after 60 s idle. `close()` tears down pool. |
| `MeshTransport(tcpSender, udpSocket)` | Facade. `sendTcp(bytes, ip)` — unicast TCP. `broadcastUdp(bytes)` — iterates all active interfaces and broadcasts. |
| `NetworkScanner` | `object`. `getNetworkInterfaceInfo(): List<NetworkInterfaceInfo>` — enumerates all up, non-loopback IPv4 interfaces with their broadcast addresses. |
| `NetworkInterfaceInfo` | `data class(interfaceName, localAddress, broadcastAddress)` |
| `NetworkInfo(context)` | `isStaApSupported(): Boolean`, `isLikelySupported(): Boolean` — checks WiFi STA+AP concurrency capability. |
| `Client` | Internal per-connection TCP reader. |

**Flows exposed:**
- `UdpSocket.incoming: ReceiveChannel<Envelope>`
- `TCPReceiver.incoming: ReceiveChannel<Envelope>`

**Dependencies:** `:model`, `:packetProcessor`

---

### 1.3 `packetProcessor` — Binary Serialization/Deserialization

| Item | Detail |
|---|---|
| **Package** | `com.minor.packetprocessor` |
| **Purpose** | Pure stateless serializers and parsers for the custom wire protocol. Converts `ByteArray ↔ Header/Payload`. |
| **Status** | ✅ Complete |

**Public Classes:**

| Class | Description |
|---|---|
| `HeaderParser` | `object`. `parse(data: ByteArray): ParseResult<Header>` — validates magic/version/payloadLength, returns typed `Header` or typed `ParseError`. |
| `HeaderSerializer` | `object`. `serialize(header: Header): ByteArray` — converts `Header` to wire bytes. |
| `PayloadParser` | `object`. `parse(packet: Packet): ParseResult<Payload>` and `parse(type: Int, data: ByteArray): ParseResult<Payload>` — dispatches by header type constant to HELLO/MSG/ACK/RREQ/RREP/RERR parsers. |
| `PayloadSerializer` | `object`. `serialize(payload: Payload): ByteArray` — converts any `Payload` to wire bytes. |

**Dependencies:** `:model`

---

### 1.4 `routing` — AODV-Style Routing Layer

| Item | Detail |
|---|---|
| **Package** | `com.minor.routing` |
| **Purpose** | Implements AODV-inspired on-demand routing: peer discovery via HELLO, route discovery via RREQ/RREP, route error via RERR, message forwarding with TTL, and outbound queueing with retry. |
| **Status** | ✅ Complete |

**Public Classes:**

| Class | Description |
|---|---|
| `Router()` | Thread-safe routing table. `lookup(destinationNodeId): NodeId?` — next hop. `update(dest, name, nextHop, hopCount, routeTimestamp)` — installs/improves routes using freshness + hop-count tie-break. `invalidate(dest)` — marks one route invalid. `invalidateVia(nextHop): List<NodeId>` — invalidates routes affected by a failed peer. `getRoutes(): List<RouteInfo>` — valid routes snapshot. |
| `PeersManagement(selfNodeId, router, peerTimeoutMs, reaperCheckMs, helloIntervalMs)` | Peer table. `addOrUpdate(nodeId, ip, name, publicKey)`. `remove(nodeId)`. `resolveIp(nodeId): String?`. `isDirectPeer(nodeId): Boolean`. `lookupPublicKey(nodeId): PublicKey?`. `getPeers(): List<Peer>`. `peerEvents: ReceiveChannel<PeerEvent>`. `startReaperLoop(scope, sender)`. `startHelloBroadcastLoop(scope, sender, name)`. |
| `Sender(selfNodeId, selfPublicKey, selfName, transport, router, peers, rreqRetryTimeoutMs, maxHopCount)` | Owns outbound queue. `enqueue(messageId, content, dest)`. `broadcastHello(displayName)`. `broadcastRerr(unreachable)`. `sendRrep(...)`. `sendAck(...)`. `forwardTcp(...)`. `forwardUdpBroadcast(...)`. `onAckReceived(messageId)`. `statusChannel: Channel<Pair<Long, SendStatus>>`. `startQueueLoop(scope)`. |
| `Receiver(selfNodeId, router, peers, sender, freshnessWindowMs)` | Packet dispatcher. `onPacketReceived(packet, senderIp)` — routes HELLO/MSG/RREQ/RREP/ACK/RERR. `incomingMessageChannel: Channel<Packet>` — messages addressed to this node. |
| `RoutingModule(selfNodeId, selfPublicKey, selfName, transport, tcpIncoming, udpIncoming)` | Wires Router + PeersManagement + Sender + Receiver together and consumes both inbound `ReceiveChannel<Envelope>` streams. `start(scope, displayName)`. Exposed: `router`, `peers`, `sender`, `receiver`. |

**Data Models (routing-specific):**

| Type | Description |
|---|---|
| `Peer` | `data class(nodeId, ip, name, publicKey, lastSeen)` |
| `RouteInfo` | `data class(destinationNodeId, name, nextHopNodeId, hopCount, routeTimestamp, valid)` |
| `PeerEvent` | `sealed class`: `Added(peer)`, `Updated(peer)`, `Removed(nodeId)` |
| `QueuedMessage` | `data class(messageId, content, destinationNodeId, rreqFlag, enqueueTime)` |
| `SendStatus` | `enum`: `SENT`, `DELIVERED`, `FAILED` |

**Dependencies:** `:model`, `:packetProcessor`, `:transport`

---

### 1.5 `meshControl` — Mesh Lifecycle Coordinator

| Item | Detail |
|---|---|
| **Package** | `com.minor.meshcontrol` |
| **Purpose** | Orchestrates full mesh lifecycle: creates sockets, wires routing, exposes `StateFlow`/`SharedFlow` streams for state, peers, routes, incoming messages, and delivery status. The single entry point for upper layers. |
| **Status** | ✅ Complete |

**Public Classes:**

| Class | Description |
|---|---|
| `MeshConfig` | `data class` with all runtime tunable parameters: ports, timeouts, intervals, `ownNodeId`, `ownPublicKey`, `ownName`. |
| `MeshService(config, socketFactory, scopeDispatcher)` | Core lifecycle class. `start()` — boots the mesh. `suspend stop()` — shuts down gracefully. `sendMessage(destinationNodeID, payload): Long` — enqueues outbound payloads (currently `Payload.Message` only), returns mesh message ID. |
| `MeshSocketFactory` | `fun interface`. Factory to create `MeshSockets`. Android context-bound impl should live in `:app`. |
| `MeshSockets` | `data class(tcpReceiver, tcpSender, udpSocket)` |
| `MeshMessagingGateway` | `interface` — `incomingMessageStream: SharedFlow<Packet>`, `deliveryStatusStream: SharedFlow<DeliveryStatus>`, `sendMessage(destinationNodeID, payload): Long`. `MeshService` implements this. |

**Flows exposed by `MeshService`:**

| Flow | Type | Description |
|---|---|---|
| `meshStateStream` | `StateFlow<MeshState>` | STARTING / RUNNING / STOPPING / STOPPED / ERROR |
| `incomingMessageStream` | `SharedFlow<Packet>` | Raw packets addressed to this node |
| `deliveryStatusStream` | `SharedFlow<DeliveryStatus>` | SENT / DELIVERED / FAILED per messageId |
| `peersStream` | `StateFlow<List<PeerState>>` | Live list of known peers, sorted by name |
| `routeStateStream` | `StateFlow<RouteState>` | Snapshot of valid routes, refreshed every `routeStateIntervalMs` |

**Supporting types:**

| Type | Description |
|---|---|
| `MeshState` | `enum`: STARTING, RUNNING, STOPPING, STOPPED, ERROR |
| `DeliveryState` | `enum`: SENT, DELIVERED, FAILED |
| `DeliveryStatus` | `data class(messageId: Long, state: DeliveryState)` |
| `PeerStatus` | `enum`: ACTIVE, REMOVED |
| `PeerState` | `data class(nodeId, ip?, name?, publicKey?, status, lastSeen?)` |
| `RouteState` | `data class(routes: List<RouteInfo>)` |

**Dependencies:** `:model`, `:routing`, `:transport`

---

### 1.6 `messaging` — Messaging Service

| Item | Detail |
|---|---|
| **Package** | `com.minor.messaging` |
| **Purpose** | High-level conversation management. Wraps `MeshMessagingGateway` with persistence (SQLite), encode/decode security codec, delivery tracking, timeouts, and conversation aggregation. |
| **Status** | ✅ Complete — but `MessageSecurityCodec` is an **interface with no implementation provided**. |

**Public Classes:**

| Class | Description |
|---|---|
| `MessagingService(ownNodeId, meshGateway, conversationStore, securityCodec, deliveryTimeoutMs, dispatcher)` | Lifecycle: `start()`, `stop()`. Send: `send(destinationNodeID, plaintext): Message`. Query: `listConversations(): List<Conversation>`, `getHistory(nodeID): List<Message>`. |
| `ConversationStore(context)` | SQLite-backed persistence. `getConversation(nodeID)`, `listConversations()`, `appendMessage(nodeID, message)`, `updateDeliveryStatus(messageID, deliveryStatus)`, `deleteConversation(nodeID)`. Database: `mesh_conversations.db`. |
| `Conversation(remoteNodeId, initialMessages)` | In-memory conversation with `messages: List<Message>`. `appendOutgoing(...)`, `appendIncoming(...)`. |
| `MessageSecurityCodec` | **Interface (not yet implemented)**. `encode(plaintext, recipientNodeID, messageID): Payload`. `decode(packet: Packet): DecodedMessage`. |

**Flows exposed by `MessagingService`:**

| Flow | Type | Description |
|---|---|---|
| `messagesStream` | `SharedFlow<MessageUpdate>` | Every new/updated message with direction |
| `deliveryStatusStream` | `SharedFlow<MessageStatusUpdate>` | Status change per message |
| `conversationsStream` | `StateFlow<List<ConversationSummary>>` | Live sorted conversation list for chats screen |

**Supporting types:**

| Type | Description |
|---|---|
| `Message` | `data class(senderNodeId, plaintextContent, composeTimestamp, messageId, deliveryStatus)` |
| `MessageDeliveryStatus` | `enum`: QUEUED, SENT, DELIVERED, FAILED |
| `Conversation` | `class(remoteNodeId, messages)` |
| `ConversationSummary` | `data class(nodeID, lastMessage?, unreadCount)` |
| `MessageUpdate` | `data class(nodeID, message, direction)` |
| `MessageStatusUpdate` | `data class(nodeID, messageID, deliveryStatus)` |
| `MessageDirection` | `enum`: INCOMING, OUTGOING |
| `DecodedMessage` | `data class(senderNodeId, plaintext, composeTimestamp, messageID)` |
| `StoredMessage` | `data class(remoteNodeId, message)` |

**Dependencies:** `:meshControl` (api), `:model` (api)

---

### 1.7 `security` — Cryptographic Security

| Item | Detail |
|---|---|
| **Package** | `com.minor.security` |
| **Purpose** | Intended to provide key management and message signing/verification. |
| **Status** | ❌ **Empty** — module exists in Gradle but contains **zero Kotlin source files**. |

**Missing:** Implementation of `MessageSecurityCodec` (the interface declared in `messaging`). Also missing: key generation, key storage, Ed25519 signing/verification.

**Dependencies:** None declared beyond androidx boilerplate.

---

### 1.8 `ui` — Presentation Layer

| Item | Detail |
|---|---|
| **Package** | `com.minor.ui` |
| **Purpose** | Jetpack Compose screens, ViewModels, state classes, navigation, theme, and reusable components. Currently wired entirely with fake data. |
| **Status** | ⚠️ Structurally complete — all screens exist and are navigable — but all business data comes from `FakeDataProvider`. Not connected to any backend. |

**Navigation Graph (`MeshAppNavHost`):**

```
HOME  (bottom nav)
  ├── → CHATS  (bottom nav)
  │     └── → CONVERSATION/{nodeId}  (push)
  └── → NETWORK_INTERFACES  (push via dropdown)
```

Routes defined in `MeshRoutes`:
- `"home"` → `HomeScreen`
- `"chats"` → `ChatsScreen`
- `"conversation/{nodeId}"` → `ConversationScreen` (arg: `nodeId: String`)
- `"NetworkInterfaces"` → `NetworkInterfacesScreen`

**Screens:**

| Screen | ViewModel | What it shows |
|---|---|---|
| `HomeScreen` | `HomeViewModel` | Mesh ON/OFF toggle button, user profile name in top bar |
| `ChatsScreen` | `ChatsViewModel` | List of `NodeCard` items (fake nodes) |
| `ConversationScreen` | `ConversationViewModel` | Chat bubble list + message input field |
| `NetworkInterfacesScreen` | `NetworkInterfacesViewModel` | Real WiFi interface data (already wired to transport) |

**ViewModels:**

| ViewModel | Data source | Status |
|---|---|---|
| `HomeViewModel` | `FakeDataProvider.meshStatus`, `FakeDataProvider.profile` | ❌ Fake |
| `ChatsViewModel` | `FakeDataProvider.nodes` | ❌ Fake |
| `ConversationViewModel` | `FakeDataProvider.messagesByNodeId`, `FakeDataProvider.nodes` | ❌ Fake |
| `NetworkInterfacesViewModel` | `NetworkScanner`, `NetworkInfo` (real) | ✅ Real |

**UI State Classes:**

| Class | Fields |
|---|---|
| `HomeUiState` | `isMeshOn: Boolean`, `profile: ProfileUiState` |
| `ProfileUiState` | `name: String`, `avatarInitials: String` |
| `NodeCardState` | `id: String`, `name: String`, `isOnline: Boolean`, `avatarInitials: String` |
| `ConversationUiState` | `node: NodeCardState`, `messages: List<ConversationMessageUiState>` |
| `ConversationMessageUiState` | `id: String`, `text: String`, `isOutgoing: Boolean`, `timestamp: String` |
| `NetworkInterfacesUiState` | `isStaApSupported: Boolean`, `isLikelySupported: Boolean`, `interfaces: List<NetworkInterfaceUiState>` |
| `NetworkInterfaceUiState` | `interfaceName: String`, `localIp: String`, `broadcastIp: String` |

**Reusable Components (`CommonComponents.kt`):**

| Component | Description |
|---|---|
| `ProfileAvatar(initials, size, containerColor)` | Circular colored avatar with initials |
| `OnlineIndicator(isOnline)` | Colored dot + "Online"/"Offline" label |
| `MeshTopBar(title, subtitle?, onBack?, trailing)` | `TopAppBar` wrapper with optional back button and trailing slot |
| `BottomNavigationBar(currentRoute, onNavigate)` | Home + Chats bottom nav |
| `NodeCard(node, onClick)` | Card with avatar, name, node id, online indicator |
| `ChatBubble(message)` | Right-aligned green outgoing / left-aligned surface incoming |
| `EmptyState(title, subtitle)` | Centered icon + two text labels |

**Current Architecture:**  
Unidirectional data flow: `ViewModel (StateFlow) → Screen (collectAsStateWithLifecycle) → Composable`.  
ViewModels instantiated via default `viewModel()` factory — **no dependency injection is currently present**.

**Dependencies:** `:transport` (for `NetworkInterfacesViewModel`)

---

### 1.9 `app` — Application Entry Point

| Item | Detail |
|---|---|
| **Package** | `com.minor.meshapp` |
| **Purpose** | Android application module. Hosts `MainActivity`, old prototype screen (`InterfacesScreen`), and `NetworkViewModel`. Entry point delegates directly to `MeshAppNavHost()`. |
| **Status** | ⚠️ Minimal — no `Application` subclass, no DI container, no `MeshService` or `MessagingService` instantiation. The old `InterfacesScreen` is commented out. |

**Files:**

| File | Description |
|---|---|
| `MainActivity` | `setContent { MeshAppNavHost() }`. No lifecycle wiring to mesh services. |
| `NetworkViewModel` | Prototype VM using `NetworkScanner` directly. Superseded by `NetworkInterfacesViewModel` in `:ui`. Now unused in the main flow. |
| `InterfacesScreen` | Prototype Compose screen. Commented out in `MainActivity`. Dead code. |
| `ui/theme/` | Prototype theme. Superseded by `ui` module theme. |

**Dependencies:** `:ui`, `:transport`

---

## 2. Folder Overview

```
MeshApp/
│
├── app/                        Android application module
│   └── src/main/
│       ├── MainActivity.kt     Entry point → MeshAppNavHost
│       ├── viewmodel/
│       │   └── NetworkViewModel.kt   (prototype, unused)
│       └── ui/
│           ├── InterfacesScreen.kt   (prototype, commented out)
│           └── theme/                (prototype theme, unused)
│
├── ui/                         Presentation layer (Compose)
│   └── src/main/
│       ├── navigation/         MeshRoutes.kt, MeshAppNavHost.kt
│       ├── screens/
│       │   ├── home/           HomeScreen.kt
│       │   ├── chats/          ChatsScreen.kt
│       │   ├── conversation/   ConversationScreen.kt
│       │   └── networkinterfaces/ NetworkInterfacesScreen.kt
│       ├── viewmodel/          HomeViewModel, ChatsViewModel,
│       │                       ConversationViewModel, NetworkInterfacesViewModel
│       ├── state/              HomeUiState, NetworkInterfacesUiState (all UI state data classes)
│       ├── fake/               FakeDataProvider.kt  ← REPLACE ALL
│       ├── components/         CommonComponents.kt (reusable Compose)
│       └── theme/              Color, Type, Theme
│
├── messaging/                  High-level messaging service
│   └── src/main/
│       ├── MessagingService.kt  ← MAIN API
│       ├── Message.kt
│       ├── Conversation.kt
│       └── ConversationStore.kt (SQLite persistence)
│
├── meshControl/                Mesh lifecycle coordinator
│   └── src/main/
│       ├── MeshService.kt       ← MAIN API
│       └── MeshConfig.kt
│
├── routing/                    AODV routing protocol implementation
│   └── src/main/
│       ├── Router.kt
│       ├── PeersManagement.kt
│       ├── Sender.kt
│       ├── Receiver.kt
│       ├── RoutingModule.kt
│       └── RoutingModels.kt
│
├── packetProcessor/            Binary wire protocol codec
│   └── src/main/
│       ├── HeaderParser.kt
│       ├── HeaderSerializer.kt
│       ├── PayloadParser.kt
│       └── PayloadSerializer.kt
│
├── transport/                  Network I/O (TCP + UDP)
│   └── src/main/
│       ├── MeshTransport.kt
│       ├── TCPSender.kt
│       ├── TCPReceiver.kt
│       ├── UDPSocket.kt
│       ├── NetworkScanner.kt
│       ├── NetworkInfo.kt
│       └── Client.kt
│
├── model/                      Shared data contracts (no Android deps)
│   └── src/main/
│       ├── MessageClasses.kt   (NodeId, MessageId, Header, Packet, Envelope, Payload, …)
│       ├── ParserInterfaces.kt (ParseResult, ParseError)
│       ├── HeaderProtocol.kt
│       ├── HelloProtocol.kt
│       ├── MessageProtocol.kt
│       ├── AckProtocol.kt
│       ├── RREQProtocol.kt
│       ├── RREPProtocol.kt
│       ├── RERRProtocol.kt
│       ├── RouteProtocol.kt
│       └── PrimitiveHelpers.kt
│
└── security/                   ❌ EMPTY — no source files
```

---

## 3. Dependency Graph

```
         app
        /   \
       ui   transport (direct)
       |
       ui depends on:
       ├── transport  (NetworkInterfacesViewModel uses NetworkScanner/NetworkInfo)
       └── (future) → messaging → meshControl → routing → transport
                                                        → packetProcessor → model
                                                 ↑
                                               model (shared by all)

Full layered graph:

┌─────────────────────────────────────────┐
│                  app                    │
│  (MainActivity, no DI, no services)     │
└──────────────────┬──────────────────────┘
                   │ depends on
        ┌──────────▼──────────┐
        │          ui          │
        │  (screens, VMs,      │
        │   navigation)        │
        └──────────┬───────────┘
                   │ (future dependency)
        ┌──────────▼───────────┐
        │      messaging        │
        │  (MessagingService,   │
        │   ConversationStore)  │
        └──────────┬────────────┘
                   │ api
        ┌──────────▼───────────┐
        │      meshControl      │
        │  (MeshService,        │
        │   MeshConfig,         │
        │   all StateFlows)     │
        └──────┬────────┬───────┘
               │ api    │ api
   ┌───────────▼──┐  ┌──▼────────────┐
   │   routing     │  │   transport   │
   │  (Router,     │  │  (TCPSender,  │
   │  PeersMgmt,   │  │  TCPReceiver, │
   │  Sender,      │  │  UdpSocket,   │
   │  Receiver)    │  │  MeshTransport│
   └───────┬───────┘  └───────────────┘
           │ depends on
  ┌────────▼────────┐
  │ packetProcessor  │
  │ (HeaderParser,   │
  │  PayloadParser,  │
  │  Serializers)    │
  └────────┬─────────┘
           │ depends on
  ┌────────▼─────────┐
  │      model        │
  │ (ALL data types,  │
  │  protocol consts) │
  └───────────────────┘

  security  ←  EMPTY, will need to implement MessageSecurityCodec
               and be wired into messaging
```

---

## 4. Public APIs

### 4.1 `MeshService` (in `:meshControl`)

```kotlin
class MeshService(
    config: MeshConfig,
    socketFactory: MeshSocketFactory,
    scopeDispatcher: CoroutineDispatcher = Dispatchers.Default
) : MeshMessagingGateway {

    // --- Lifecycle ---
    fun start()                          // boots mesh; idempotent if already running
    suspend fun stop()                   // graceful shutdown

    // --- Sending ---
    override fun sendMessage(
        destinationNodeID: NodeId,
        payload: Payload                 // must be Payload.Message currently
    ): Long                              // returns mesh-layer message ID

    // --- Observing (collect in ViewModel / coroutine) ---
    val meshStateStream:    StateFlow<MeshState>         // STARTING/RUNNING/STOPPING/STOPPED/ERROR
    val peersStream:        StateFlow<List<PeerState>>   // live peers list
    val routeStateStream:   StateFlow<RouteState>        // route table snapshot
    override val incomingMessageStream:  SharedFlow<Packet>          // raw incoming messages
    override val deliveryStatusStream:   SharedFlow<DeliveryStatus>  // SENT/DELIVERED/FAILED
}
```

### 4.2 `MeshConfig` (in `:meshControl`)

```kotlin
data class MeshConfig(
    val udpBroadcastPort: Int,
    val tcpPort: Int,
    val helloIntervalMs: Long = 5_000L,
    val peerTimeoutMs: Long = 15_000L,
    val peerReaperCheckMs: Long = 5_000L,
    val routeExpiryMs: Long = 60_000L,
    val routeExpiryCheckIntervalMs: Long = 10_000L,
    val rreqRetryTimeoutMs: Long = 8_000L,
    val deliveryAckTimeoutMs: Long = 8_000L,
    val maxHopCount: Int = 8,
    val originTimestampFreshnessWindowMs: Long = 30_000L,
    val ownNodeId: NodeId,
    val ownPublicKey: PublicKey,
    val ownName: String,
    val routeStateIntervalMs: Long = 5_000L,
)
```

### 4.3 `MessagingService` (in `:messaging`)

```kotlin
class MessagingService(
    ownNodeId: NodeId,
    meshGateway: MeshMessagingGateway,    // typically a MeshService instance
    conversationStore: ConversationStore,
    securityCodec: MessageSecurityCodec,  // ← MUST be provided (not yet implemented)
    deliveryTimeoutMs: Long = 8_000L,
    dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    // --- Lifecycle ---
    fun start()
    fun stop()

    // --- Sending ---
    fun send(destinationNodeID: NodeId, plaintext: String): Message

    // --- Querying ---
    fun listConversations(): List<Conversation>
    fun getHistory(nodeID: NodeId): List<Message>

    // --- Observing ---
    val messagesStream:      SharedFlow<MessageUpdate>          // new/updated messages
    val deliveryStatusStream: SharedFlow<MessageStatusUpdate>   // delivery status events
    val conversationsStream: StateFlow<List<ConversationSummary>> // for chats list
}
```

### 4.4 `MessageSecurityCodec` (in `:messaging` — interface only, impl missing)

```kotlin
interface MessageSecurityCodec {
    fun encode(
        plaintext: String,
        recipientNodeID: NodeId,
        messageID: MessageId
    ): Payload   // returns Payload.Message with encoded content

    fun decode(packet: Packet): DecodedMessage
}
```

### 4.5 `ConversationStore` (in `:messaging`)

```kotlin
class ConversationStore(context: Context) {
    fun getConversation(nodeID: NodeId): Conversation?
    fun listConversations(): List<Conversation>
    fun appendMessage(nodeID: NodeId, message: Message)
    fun updateDeliveryStatus(messageID: MessageId, deliveryStatus: MessageDeliveryStatus): StoredMessage?
    fun deleteConversation(nodeID: NodeId)
}
```

### 4.6 `NetworkScanner` (in `:transport`)

```kotlin
object NetworkScanner {
    fun getNetworkInterfaceInfo(): List<NetworkInterfaceInfo>
}
```

### 4.7 `NetworkInfo` (in `:transport`)

```kotlin
class NetworkInfo(context: Context) {
    fun isStaApSupported(): Boolean
    fun isLikelySupported(): Boolean
}
```

---

## 5. Data Flow Diagrams

### 5.1 Application Startup

```
User launches app
        ↓
MainActivity.onCreate()
        ↓
setContent { MeshAppNavHost() }          ← only UI is started right now
        ↓
NavHost renders HomeScreen
        ↓
HomeViewModel instantiated (viewModel())
        ↓
_uiState ← FakeDataProvider              ← ❌ still fake

--- MISSING wiring (must be added to app or Application subclass) ---

Application.onCreate()  (to be created)
        ↓
Instantiate MeshConfig (ownNodeId, ownPublicKey, ownName from identity store)
        ↓
Instantiate MeshSocketFactory (context-aware, creates UdpSocket + TCP*)
        ↓
Instantiate MeshService(config, socketFactory)
        ↓
Instantiate ConversationStore(context)
        ↓
Instantiate MessageSecurityCodec impl  (security module — not yet built)
        ↓
Instantiate MessagingService(ownNodeId, meshService, store, codec)
        ↓
meshService.start()
messagingService.start()
        ↓
Expose both as singletons (manual DI / Hilt)
```

### 5.2 Sending a Message

```
ConversationScreen
 └─ user types + taps "Send"
        ↓
ConversationViewModel.sendMessage(text)
        ↓
  [currently: appends fake local message to _uiState]

--- REAL FLOW (after wiring) ---

ConversationViewModel.sendMessage(text, destinationNodeId)
        ↓
MessagingService.send(destinationNodeId, plaintext)
        ↓
  MessageSecurityCodec.encode(plaintext, recipientNodeID, messageID)
  → Payload.Message(encodedContent)
        ↓
  MeshMessagingGateway.sendMessage(destinationNodeId, payload)  [= MeshService]
        ↓
  Sender.enqueue(MessageId, content, destinationNodeId)
        ↓
        Sender.startQueueLoop processes queue:
                ├── if direct peer or routed next hop exists → buildPacket() → MeshTransport.sendTcp(bytes, ip)
                └── if no route → sender issues RREQ broadcast internally → wait for RREP/route update → retry send
        ↓
  TCPSender.send(bytes, ip) → Socket.getOutputStream().write(bytes)
        ↓
  Remote node TCPReceiver.incoming → Envelope
        ↓
  Remote Receiver.onPacketReceived → handleMessage
        ↓
  Remote Receiver.incomingMessageChannel.trySend(packet)  → MessagingService collects
        ↓
  Remote sends ACK → Sender.statusChannel (DELIVERED)
        ↓
  MeshService._deliveryStatusStream.emit(DeliveryStatus(id, DELIVERED))
        ↓
  MessagingService.collectDeliveryStatuses() → ConversationStore.updateDeliveryStatus()
        ↓
  MessagingService.messagesStream.emit(MessageUpdate)
        ↓
  ConversationViewModel collects → _uiState updated → ChatBubble shows ✓✓
```

### 5.3 Receiving a Message

```
Remote node sends TCP packet
        ↓
TCPReceiver.accept() → Client reads bytes → Envelope(packet, remoteAddress)
        ↓
TCPReceiver.incoming  [ReceiveChannel<Envelope>]
        ↓
MeshService.startTransportCollectors() collects channel
        ↓
Receiver.onPacketReceived(packet, senderIp)
        ↓
handleMessage(packet, senderIp)
  ├── if destNodeId == selfNodeId:
  │     incomingMessageChannel.trySend(packet)
  │     sender.sendAck(msgId, sourceNodeId, 0x00)
  └── else: forward via router (not our node)
        ↓
MeshService.startInternalAggregators() collects incomingMessageChannel
        ↓
_incomingMessageStream.emit(packet)   [SharedFlow<Packet>]
        ↓
MessagingService.collectIncomingMessages() collects incomingMessageStream
        ↓
MessageSecurityCodec.decode(packet) → DecodedMessage
        ↓
ConversationStore.appendMessage(senderNodeId, message)
        ↓
_messagesStream.emit(MessageUpdate(nodeID, message, INCOMING))
_conversationsStream.value = refreshed list
        ↓
ConversationViewModel collects messagesStream → appends to _uiState.messages
ChatsViewModel collects conversationsStream → updates conversation list
```

### 5.4 Mesh State Updates

```
MeshService.start() called
        ↓
_meshStateStream.value = STARTING → RUNNING
        ↓
PeersManagement.startHelloBroadcastLoop → Sender.broadcastHello(name) every 5s
        ↓
Remote node receives HELLO → Receiver.handleHello() → PeersManagement.addOrUpdate()
        ↓
peerEventsChannel.trySend(PeerEvent.Added(peer))
        ↓
MeshService.startInternalAggregators() collects peerEvents
        ↓
_peersStream.update { applyPeerEvent(event) }   [StateFlow<List<PeerState>>]
        ↓
  [after wiring]
HomeViewModel / ChatsViewModel collects peersStream
        ↓
NodeCardState list updated → ChatsScreen recomposes → NodeCard appears
OnlineIndicator(isOnline = true) shown

Peer timeout:
PeersManagement.startReaperLoop → peer.lastSeen expired
        ↓
PeerEvent.Removed → _peersStream.update → PeerState(status = REMOVED)
        ↓
ChatsScreen shows node as offline / removed
```

### 5.5 Route Discovery (RREQ/RREP)

```
Sender.startQueueLoop processes QueuedMessage for unknown destination
        ↓
No route in Router.lookup(dest) → sender issues internal RREQ broadcast
        ↓
MeshTransport.broadcastUdp(rreqBytes)
        ↓
All peers receive RREQ → Receiver.handleRreq()
  ├── If dest == self → Sender.sendRrep(rreqId, originator, upstreamIp)
  └── else → forward RREQ (increment hop count)
        ↓
RREP received → Receiver.handleRrep()
        ↓
Router.update(source, name, nextHop, hopCount)
        ↓
Sender queue retries → message sent via TCP unicast
```

---

## 6. Existing Fake Data

All fake data lives in a single file:

**`ui/src/main/java/com/minor/ui/fake/FakeDataProvider.kt`**

| Fake field | Type | Used by |
|---|---|---|
| `FakeDataProvider.profile` | `ProfileUiState(name="Avery", avatarInitials="AV")` | `HomeViewModel` |
| `FakeDataProvider.meshStatus` | `Boolean = true` | `HomeViewModel` |
| `FakeDataProvider.networkInterfaces` | `NetworkInterfacesUiState` (2 hardcoded interfaces) | **NOT used** — `NetworkInterfacesViewModel` uses real data |
| `FakeDataProvider.nodes` | `List<NodeCardState>` (3 nodes: alpha, beta, gamma) | `ChatsViewModel`, `ConversationScreen.LaunchedEffect` |
| `FakeDataProvider.messagesByNodeId` | `Map<String, List<ConversationMessageUiState>>` | `ConversationViewModel.initialize()` |

---

## 7. Real Backend Replacements

| Fake | Replace with | Source |
|---|---|---|
| `FakeDataProvider.profile` (name, avatar) | User's own name from `MeshConfig.ownName` + derived initials | `:meshControl` / identity store |
| `FakeDataProvider.meshStatus` (Boolean) | `MeshService.meshStateStream.map { it == MeshState.RUNNING }` | `:meshControl` |
| `HomeViewModel.toggleMesh()` (just flips bool) | `meshService.start()` / `meshService.stop()` | `:meshControl` |
| `FakeDataProvider.nodes` (hardcoded list) | `MeshService.peersStream` mapped to `List<NodeCardState>` | `:meshControl` |
| `ConversationScreen LaunchedEffect` uses `FakeDataProvider.nodes` | Pass real `NodeCardState` (built from `PeerState`) via nav argument | `:meshControl` |
| `ConversationViewModel.sendMessage(text)` (appends fake message) | `MessagingService.send(destinationNodeId, text)` | `:messaging` |
| `FakeDataProvider.messagesByNodeId` (static message maps) | `MessagingService.getHistory(nodeId)` + collect `messagesStream` | `:messaging` |
| `ChatsViewModel` nodes list | `MessagingService.conversationsStream` mapped to `NodeCardState` list | `:messaging` |
| Node online status (`isOnline`) | Derived from `PeerState.status == PeerStatus.ACTIVE` | `:meshControl` |

---

## 8. UI Integration Plan

### 8.1 Dependency Injection Needed

No DI framework is currently present. The following singletons must be created and provided to ViewModels:

| Singleton | Where to create | Why |
|---|---|---|
| `MeshConfig` | `Application.onCreate()` | Requires identity (nodeId, publicKey, name) |
| `MeshService` | `Application.onCreate()` | Lifecycle matches app lifetime |
| `ConversationStore` | `Application.onCreate()` | SQLite, needs context |
| `MessageSecurityCodec` impl | `Application.onCreate()` | Injected into `MessagingService` |
| `MessagingService` | `Application.onCreate()` | Depends on all above |

**Recommended approach:** Create a custom `Application` subclass (`MeshApplication`) that acts as a manual DI container (or wire Hilt). ViewModels must be created via `ViewModelProvider.Factory` receiving these singletons.

### 8.2 ViewModels That Must Change

#### `HomeViewModel`
- **Inject:** `MeshService`
- **Replace fake with:**
  - `isMeshOn` ← `meshService.meshStateStream.map { it == MeshState.RUNNING }`
  - `profile.name` ← `meshConfig.ownName`
  - `profile.avatarInitials` ← derived from name
  - `toggleMesh()` ← call `meshService.start()` or `meshService.stop()`
- **Collect in:** `viewModelScope.launch { meshService.meshStateStream.collect { ... } }`

#### `ChatsViewModel`
- **Inject:** `MessagingService`, `MeshService`
- **Replace fake with:**
  - `nodes` ← `meshService.peersStream` or `messagingService.conversationsStream` mapped to `NodeCardState`
  - Combine peers (mesh nodes) with conversation summaries (unread count, last message)
- **Collect in:** `viewModelScope.launch { meshService.peersStream.collect { ... } }`

#### `ConversationViewModel`
- **Inject:** `MessagingService`, `MeshService`
- **Add parameter:** `destinationNodeId: NodeId` (passed from nav argument, not from FakeDataProvider)
- **Replace fake with:**
  - `messages` ← `messagingService.getHistory(nodeId)` on init + collect `messagingService.messagesStream.filter { it.nodeID == nodeId }`
  - `sendMessage(text)` ← `messagingService.send(destinationNodeId, text)`
  - `node` (NodeCardState) ← built from `MeshService.peersStream` lookup
- **Delivery status ticks** ← collect `messagingService.deliveryStatusStream`

#### `NetworkInterfacesViewModel`
- **Already real** — no changes needed.

### 8.3 Flows to Collect in ViewModels

| Flow | Collect in ViewModel | Update |
|---|---|---|
| `MeshService.meshStateStream` | `HomeViewModel` | `isMeshOn`, error states |
| `MeshService.peersStream` | `ChatsViewModel`, `ConversationViewModel`, `HomeViewModel` | Node list, online status, peer names |
| `MeshService.routeStateStream` | Optional debug screen | Route list |
| `MessagingService.conversationsStream` | `ChatsViewModel` | Conversation list, unread counts |
| `MessagingService.messagesStream` | `ConversationViewModel` | Chat history updates |
| `MessagingService.deliveryStatusStream` | `ConversationViewModel` | Delivery tick marks |

### 8.4 Navigation Arguments

Currently `ConversationScreen` receives `nodeId: String` from nav graph.

**What must change:**
- The `nodeId` string is currently matched against `FakeDataProvider.nodes`. It must be matched against a real `NodeId` (hex string of the 32-byte ID).
- `ConversationScreen / ConversationViewModel` must receive the **full hex string NodeId** as navigation argument.
- `NodeId` can be reconstructed from the hex string by reversing `NodeId.toString()`.
- The `NodeCardState.id` field in `ChatsScreen` must be set to `peerState.nodeId.toString()` (hex string) so the nav argument is consistent.

### 8.5 Where Fake Data Must Be Replaced (file-level)

| File | Line / Location | What to replace |
|---|---|---|
| `ui/fake/FakeDataProvider.kt` | Entire object | Remove entirely after wiring — or keep for Compose previews only |
| `ui/viewmodel/HomeViewModel.kt` | `_uiState` init block | Replace `FakeDataProvider.meshStatus` and `FakeDataProvider.profile` |
| `ui/viewmodel/ChatsViewModel.kt` | `_uiState` init block | Replace `FakeDataProvider.nodes` |
| `ui/viewmodel/ConversationViewModel.kt` | `initialize()` and `sendMessage()` | Remove `FakeDataProvider.messagesByNodeId` usage; remove fake local-append in `sendMessage` |
| `ui/screens/conversation/ConversationScreen.kt` | `LaunchedEffect(nodeId)` block | Replace `FakeDataProvider.nodes.firstOrNull { it.id == nodeId }` with real peer lookup |

### 8.6 `app` Module Changes Needed

| Task | Description |
|---|---|
| Create `MeshApplication` | Custom `Application` subclass; instantiate and hold `MeshConfig`, `MeshService`, `MessagingService`. |
| Register in `AndroidManifest.xml` | `android:name=".MeshApplication"` |
| Create `MeshSocketFactory` impl | Context-bound factory creating `UdpSocket`, `TCPReceiver`, `TCPSender` with real `Context`. |
| Add `:messaging` and `:meshControl` as app dependencies | Currently `:app` only depends on `:ui` and `:transport`. |
| Create `ViewModelFactory` | For each ViewModel that needs injected dependencies. |
| Identity bootstrapping | Generate or load `NodeId` + `PublicKey` + user name from `SharedPreferences` / Keystore. Pass to `MeshConfig`. |
| Android permissions | Ensure `CHANGE_WIFI_MULTICAST_STATE`, `ACCESS_WIFI_STATE`, `INTERNET`, `CHANGE_NETWORK_STATE` are declared. |

---

## 9. Integration Checklist

### App Module
- [ ] Create `MeshApplication` class with singleton lifecycle management
- [ ] Register `MeshApplication` in `AndroidManifest.xml`
- [ ] Implement `MeshSocketFactory` (context-aware socket creation)
- [ ] Implement identity bootstrapping (generate/persist NodeId, PublicKey, name)
- [ ] Instantiate and start `MeshService`
- [ ] Instantiate `ConversationStore`
- [ ] Instantiate `MessagingService`
- [ ] Add `:messaging` and `:meshControl` to `app/build.gradle.kts` dependencies
- [ ] Add required Android permissions to `AndroidManifest.xml`
- [ ] Create `ViewModelFactory` implementations for all injected ViewModels

### Security Module
- [ ] Implement `MessageSecurityCodec` interface (encode + decode)
- [ ] Implement key generation (Ed25519)
- [ ] Implement key persistence (Android Keystore)
- [ ] Add `:model` and `:messaging` (for the interface) as dependencies

### UI Module — HomeViewModel
- [ ] Accept `MeshService` via constructor / factory
- [ ] Replace `FakeDataProvider.meshStatus` with `meshStateStream` collection
- [ ] Replace `FakeDataProvider.profile` with `MeshConfig.ownName`
- [ ] Wire `toggleMesh()` to `meshService.start()` / `meshService.stop()`

### UI Module — ChatsViewModel
- [ ] Accept `MeshService` (or `MessagingService`) via constructor / factory
- [ ] Replace `FakeDataProvider.nodes` with `peersStream` or `conversationsStream`
- [ ] Map `PeerState` → `NodeCardState` (id = nodeId hex string, name, isOnline)

### UI Module — ConversationViewModel
- [ ] Accept `MessagingService` + `MeshService` via constructor / factory
- [ ] Accept `destinationNodeId: NodeId` as constructor parameter
- [ ] Replace `initialize()` fake loading with `messagingService.getHistory(nodeId)`
- [ ] Collect `messagingService.messagesStream` filtered by nodeId
- [ ] Collect `messagingService.deliveryStatusStream` for delivery ticks
- [ ] Wire `sendMessage(text)` to `messagingService.send(destinationNodeId, text)` (remove fake local append)

### UI Module — ConversationScreen
- [ ] Remove `LaunchedEffect` dependency on `FakeDataProvider.nodes`
- [ ] Build `NodeCardState` from real `PeerState` passed via ViewModel

### Navigation
- [ ] Ensure `NodeCardState.id` in `ChatsScreen` is set to real `NodeId` hex string
- [ ] Ensure `ConversationViewModel` reconstructs `NodeId` from the hex nav argument

---

## 10. Remaining TODOs

### Critical (blocks integration)

| # | Module | TODO |
|---|---|---|
| 1 | `security` | Implement `MessageSecurityCodec` — **entire module is empty** |
| 2 | `security` | Implement identity management (NodeId + PublicKey generation and persistence) |
| 3 | `app` | Create `MeshApplication` with manual DI and service lifecycle |
| 4 | `app` | Implement `MeshSocketFactory` |
| 5 | `ui` | Wire all three fake ViewModels to real services |

### Important (needed for production quality)

| # | Module | TODO |
|---|---|---|
| 6 | `app` | Request runtime permissions (WiFi, Internet) in `MainActivity` |
| 7 | `meshControl` | `MeshConfig.ownNodeId` / `ownPublicKey` must come from a real identity source (none exists yet) |
| 8 | `messaging` | No-op / passthrough `MessageSecurityCodec` is needed as a placeholder until `security` is done |
| 9 | `ui` | `ConversationViewModel` has no `destinationNodeId` parameter — must be refactored |
| 10 | `app` | `app/build.gradle.kts` currently only depends on `:ui` and `:transport` — must add `:messaging` and `:meshControl` |

### Minor / Polish

| # | Module | TODO |
|---|---|---|
| 11 | `app` | Remove dead code: `NetworkViewModel`, `InterfacesScreen`, prototype theme in `app` |
| 12 | `ui` | `FakeDataProvider.networkInterfaces` field is defined but never used (real data already wired) |
| 13 | `routing` | `RoutingModule` class exists but `MeshService` does NOT use it — `MeshService` wires routing manually. `RoutingModule` is unused dead code or an earlier design — clarify ownership. |
| 14 | `ui` | Add unread count badge to `NodeCard` / `ChatsScreen` (data exists in `ConversationSummary.unreadCount`) |
| 15 | `ui` | Add delivery status icons (✓ / ✓✓) to `ChatBubble` |
| 16 | `ui` | Timestamp formatting in `ConversationMessageUiState` is hardcoded as "Now" in fake send |

---

## 11. Missing Wiring

### 11.1 `app` ↔ Backend Services

```
app/MainActivity.kt
    setContent { MeshAppNavHost() }
           ↑
    ❌ No MeshService started
    ❌ No MessagingService started
    ❌ No MeshApplication class
    ❌ No ViewModelFactory provided to NavHost
```

### 11.2 `ui/viewmodel` ↔ `MessagingService`

```
ChatsViewModel      →  ❌ no MessagingService injected
HomeViewModel       →  ❌ no MeshService injected
ConversationViewModel → ❌ no MessagingService injected
                        ❌ no NodeId parameter
                        ❌ sendMessage() does not call any real API
```

### 11.3 `messaging` ↔ `security`

```
MessagingService constructor requires MessageSecurityCodec
        ↑
security module is empty — codec not implemented
        ↑
MessagingService cannot be instantiated without a real or stub codec
```

### 11.4 `MeshService` ↔ App lifecycle

```
MeshService.start() / stop() must be tied to:
    - app foreground service  OR  Application.onCreate/onTerminate
    ↑
Currently: nobody calls start() or stop()
```

### 11.5 `ConversationScreen` node resolution

```
ConversationScreen:
    LaunchedEffect(nodeId) {
        val node = FakeDataProvider.nodes.firstOrNull { it.id == nodeId }  ← ❌
        viewModel.initialize(node)
    }
    ↑
Must use real PeerState from MeshService.peersStream
```

---

## 12. Suggested Order for Integration

```
Step 1 — Identity & Security (security module)
  1a. Implement key generation (Ed25519) and persistence (SharedPreferences / Keystore)
  1b. Implement a passthrough MessageSecurityCodec (no real encryption yet, for initial wiring)
  1c. Implement full MessageSecurityCodec with real signing/verification

Step 2 — App Bootstrap (app module)
  2a. Create MeshApplication class
  2b. Implement MeshSocketFactory
  2c. Load/generate identity → build MeshConfig
  2d. Instantiate MeshService, ConversationStore, MessagingService
  2e. Call meshService.start(), messagingService.start()
  2f. Add required dependencies and permissions

Step 3 — ViewModel DI wiring (app module)
  3a. Create ViewModelProvider.Factory for HomeViewModel, ChatsViewModel, ConversationViewModel
  3b. Pass factories into MeshAppNavHost → each composable

Step 4 — HomeViewModel wiring (ui module)
  4a. Inject MeshService
  4b. Collect meshStateStream → isMeshOn
  4c. Load profile from MeshConfig.ownName
  4d. Wire toggleMesh() to start/stop

Step 5 — ChatsViewModel wiring (ui module)
  5a. Inject MeshService (or MessagingService)
  5b. Collect peersStream → map PeerState → NodeCardState
  5c. Optionally merge with conversationsStream for unread counts

Step 6 — ConversationViewModel wiring (ui module)
  6a. Add destinationNodeId constructor parameter
  6b. Inject MessagingService
  6c. Load history on init
  6d. Collect messagesStream filtered by nodeId
  6e. Wire sendMessage to messagingService.send
  6f. Collect deliveryStatusStream for delivery ticks

Step 7 — Navigation argument cleanup (ui module)
  7a. Verify NodeCardState.id = NodeId.toString() (hex string)
  7b. Remove FakeDataProvider.nodes lookup in ConversationScreen

Step 8 — Remove dead code (app module)
  8a. Delete NetworkViewModel, InterfacesScreen, prototype theme
```

---

## 13. Summary

### What is Already Complete ✅

| Module | Status |
|---|---|
| `model` | 100% — all data types, protocol field definitions, parse error types |
| `packetProcessor` | 100% — HeaderParser, HeaderSerializer, PayloadParser, PayloadSerializer all implemented |
| `transport` | 100% — TCPSender, TCPReceiver, UdpSocket, NetworkScanner, NetworkInfo, MeshTransport fully implemented |
| `routing` | 100% — Router, PeersManagement, Sender, Receiver, RoutingModule, all models implemented |
| `meshControl` | 100% — MeshService with full lifecycle, all StateFlows, all SharedFlows, MeshConfig |
| `messaging` | 95% — MessagingService, ConversationStore (SQLite), Conversation, Message all done. **Missing: `MessageSecurityCodec` implementation** |
| `ui` navigation | 100% — all routes defined, NavHost wired, bottom nav working |
| `ui` components | 100% — all reusable Compose components built |
| `ui` screens | 100% structurally — all 4 screens exist and render |
| `ui/NetworkInterfacesViewModel` | 100% — already uses real `NetworkScanner` + `NetworkInfo` |

### What is Partially Complete ⚠️

| Item | What's Done | What's Missing |
|---|---|---|
| `HomeViewModel` | UiState structure, `toggleMesh()` function | Real data from `MeshService` |
| `ChatsViewModel` | UiState structure, node list | Real peers from `MeshService.peersStream` |
| `ConversationViewModel` | UiState structure, `sendMessage()` | Real messaging via `MessagingService`; no `NodeId` param |
| `app` module | `MainActivity` delegates to `MeshAppNavHost` | No DI, no service startup, no `Application` subclass |
| `messaging` | Full service, persistence, all flows | `MessageSecurityCodec` not implemented |

### What Still Needs Wiring 🔌

1. **`security` module** — empty; needs `MessageSecurityCodec` + identity management
2. **`app` module** — needs `MeshApplication`, `MeshSocketFactory`, service instantiation, DI factories
3. **`HomeViewModel`** — wire to `MeshService.meshStateStream` + `meshStateStream` control
4. **`ChatsViewModel`** — wire to `MeshService.peersStream` or `MessagingService.conversationsStream`
5. **`ConversationViewModel`** — wire to `MessagingService.send()` + `messagesStream` + history
6. **`ConversationScreen`** — remove `FakeDataProvider.nodes` lookup

### What the UI Team Needs to Do

1. Refactor `HomeViewModel` constructor to accept `MeshService`; replace fake data with real flow collection.
2. Refactor `ChatsViewModel` constructor to accept `MeshService`; map `PeerState → NodeCardState`.
3. Refactor `ConversationViewModel` to accept `MessagingService` and a `NodeId`; replace all fake data; wire `sendMessage` to real API.
4. Remove `FakeDataProvider` dependency from `ConversationScreen.kt` (the `LaunchedEffect`).
5. Ensure `NodeCardState.id` always holds the `NodeId.toString()` hex string (32 bytes → 64 hex chars).
6. Add delivery status indicator to `ChatBubble` using `ConversationMessageUiState.deliveryStatus` (field not yet present — add it).
7. Add unread count badge to `NodeCard` (data already available in `ConversationSummary.unreadCount`).

### What the App Module Team Needs to Do

1. Create `MeshApplication : Application` as the DI root.
2. Implement `MeshSocketFactory` (context-aware, wraps `UdpSocket` + `TCPReceiver` + `TCPSender`).
3. Implement identity bootstrapping: generate Ed25519 keypair, persist to Keystore, derive `NodeId` from public key, set display name.
4. Wire `MeshService.start()` on app startup (or foreground service).
5. Create `ViewModelProvider.Factory` subclasses for each ViewModel that needs injection.
6. Add `:messaging`, `:meshControl`, and `:security` to `app/build.gradle.kts`.
7. Add required permissions to `AndroidManifest.xml`.

### What `messaging` and `meshControl` Expose for the UI

**`MeshService` (meshControl):**
- `meshStateStream: StateFlow<MeshState>` → controls "Mesh is ON/OFF" toggle
- `peersStream: StateFlow<List<PeerState>>` → drives the Chats node list and online indicators
- `routeStateStream: StateFlow<RouteState>` → optional debug/diagnostics screen
- `sendMessage(destinationNodeID, payload): Long` → (used internally by MessagingService, UI should NOT call this directly)

**`MessagingService` (messaging):**
- `conversationsStream: StateFlow<List<ConversationSummary>>` → drives `ChatsScreen` node/conversation list
- `messagesStream: SharedFlow<MessageUpdate>` → drives live `ConversationScreen` chat updates
- `deliveryStatusStream: SharedFlow<MessageStatusUpdate>` → drives delivery tick marks
- `send(destinationNodeID, plaintext): Message` → called by `ConversationViewModel.sendMessage()`
- `getHistory(nodeID): List<Message>` → initial load of conversation in `ConversationViewModel`
- `listConversations(): List<Conversation>` → optional initial load for `ChatsViewModel`
