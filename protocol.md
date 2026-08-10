# MeshApp Protocol Reference

All multi-byte integers are **big-endian**. All offsets are zero-based.

---

## Primitive Encoding Helpers

| Helper | Size | Description |
|--------|------|-------------|
| `readU8` / `writeU8` | 1 byte | Unsigned 8-bit integer |
| `readU16` / `writeU16` | 2 bytes | Unsigned 16-bit integer, big-endian |
| `readU32` / `writeU32` | 4 bytes | Unsigned 32-bit integer, big-endian (returned as `Long`) |
| `readI64` / `writeI64` | 8 bytes | Signed 64-bit integer, big-endian |
| `readBytes` / `writeBytes` | N bytes | Raw byte copy; write requires `value.size == length` |
| `readString` | N bytes | UTF-8 decode of N bytes |

---

## Core Data Types (`MessageClasses.kt`)

### `NodeId`
| Property | Type | Size | Description |
|----------|------|------|-------------|
| `bytes` | `ByteArray` | **32 bytes** | Public-key-derived node identifier |

- Constraint: exactly 32 bytes
- `toString()`: lowercase hex string (64 chars)

---

### `MessageId`
| Property | Type | Size | Description |
|----------|------|------|-------------|
| `bytes` | `ByteArray` | **8 bytes** | Unique message / call / packet identifier |

- Constraint: exactly 8 bytes
- `randomMessageId()`: generated from UUID MSB
- `toString()`: lowercase hex string (16 chars)

---

### `PublicKey`
| Property | Type | Size | Description |
|----------|------|------|-------------|
| `bytes` | `ByteArray` | **91 bytes** | P-256 DER-encoded public key |

- Constraint: exactly 91 bytes

---

### `Signature`
| Property | Type | Size | Description |
|----------|------|------|-------------|
| `bytes` | `ByteArray` | **64 bytes** | ECDSA signature (raw r||s) |

- Constraint: exactly 64 bytes

---

### `Timestamp`
| Property | Type | Size | Description |
|----------|------|------|-------------|
| `millis` | `Long` | **8 bytes** | Unix epoch in milliseconds, signed 64-bit |

---

### `ReadWithLength<T>`
| Property | Type | Description |
|----------|------|-------------|
| `value` | `T` | The decoded value |
| `bytesRead` | `Int` | Number of bytes consumed from the buffer |

---

### `RouteEntry`
| Field | Type | Size | Description |
|-------|------|------|-------------|
| `nodeId` | `NodeId` | 32 bytes | Peer node identifier |
| `hopcount` | `Int` | 1 byte | Distance in hops |
| `publicKey` | `PublicKey` | 91 bytes | Peer's P-256 public key |
| `timestamp` | `Timestamp` | 8 bytes | Route freshness timestamp (default 0) |
| `name` | `String` | 1 + N bytes | Length-prefixed UTF-8 name |

Per-entry wire size: **133 + name_len bytes**

---

### `Header`
| Field | Type | Size | Description |
|-------|------|------|-------------|
| `magic` | `Int` | 2 bytes | Magic bytes |
| `version` | `Int` | 1 byte | Protocol version |
| `type` | `Int` | 1 byte | Packet type |
| `flags` | `Int` | 1 byte | Control flags |
| `hopcount` | `Int` | 1 byte | Current hop count |
| `ttl` | `Int` | 1 byte | Time-to-live |
| `reserved` | `Int` | 1 byte | Reserved (unused) |
| `immediateSenderNodeId` | `NodeId` | 32 bytes | Last-hop sender node ID |
| `sourceNodeId` | `NodeId` | 32 bytes | Originating node ID |
| `destNodeId` | `NodeId` | 32 bytes | Destination node ID |
| `id` | `MessageId` | 8 bytes | Packet identifier |
| `originTimestamp` | `Timestamp` | 8 bytes | Origination time (ms) |
| `payloadLength` | `Int` | 2 bytes | Byte length of the payload |

**Total HEADER_SIZE = 122 bytes**

---

### `SecureEnvelope`
| Field | Type | Size | Description |
|-------|------|------|-------------|
| `envVersion` | `Int` | 1 byte | Envelope format version |
| `senderNodeId` | `NodeId` | 32 bytes | Sender's node ID |
| `encSymKey` | `ByteArray` | 91 bytes | Encrypted symmetric key (P-256 DER ephemeral public key) |
| `nonce` | `ByteArray` | 12 bytes | AES-GCM nonce |
| `ciphertext` | `ByteArray` | 4 + N bytes | 4-byte length prefix + N bytes encrypted plaintext |
| `signature` | `Signature` | 64 bytes | Over envelope fields |

Fixed overhead: **1 + 32 + 91 + 12 + 4 + 64 = 204 bytes** + ciphertext length

---

### `InnerPlaintextBlock`
| Field | Type | Size | Description |
|-------|------|------|-------------|
| `messageId` | `MessageId` | 8 bytes | Message unique ID |
| `timestamp` | `Timestamp` | 8 bytes | Send time (ms) |
| `contentType` | `Int` | 1 byte | Content type code |
| `content` | `ByteArray` | 4 + N bytes | 4-byte length prefix + N bytes payload |

Fixed overhead: **8 + 8 + 1 + 4 = 21 bytes** + content length

---

### `CallSignal`
| Field | Type | Size | Description |
|-------|------|------|-------------|
| `callId` | `MessageId` | 8 bytes | Identifies the call session |
| `type` | `Int` | 1 byte | Signal type code |
| `payload` | `ByteArray` | variable | Signal-specific payload bytes |

---

### `CallOffer`
| Field | Type | Size | Description |
|-------|------|------|-------------|
| `ephemeralPublicKey` | `ByteArray` | **91 bytes** | P-256 DER ephemeral public key for ECDH |

---

### `CallAccept`
| Field | Type | Size | Description |
|-------|------|------|-------------|
| `ephemeralPublicKey` | `ByteArray` | **91 bytes** | P-256 DER ephemeral public key for ECDH |

---

### `VoicePacket`
| Field | Type | Size | Description |
|-------|------|------|-------------|
| `callId` | `MessageId` | 8 bytes | Identifies the call session |
| `sequenceNumber` | `Int` | 4 bytes | Packet ordering sequence (U32) |
| `timestampMs` | `Long` | 8 bytes | Audio capture timestamp (ms) |
| `encodedAudio` | `ByteArray` | 2 + N bytes | 2-byte length prefix + encoded audio bytes (max 65535) |

Fixed overhead: **8 + 4 + 8 + 2 = 22 bytes** + audio length

---

### `FileSignal`
| Field | Type | Size | Description |
|-------|------|------|-------------|
| `transferId` | `MessageId` | 8 bytes | Identifies the file transfer session |
| `type` | `Int` | 1 byte | File signal type code |
| `payload` | `ByteArray` | variable | Signal-specific payload bytes |

---

### `FileChunkPacket`
| Field | Type | Size | Description |
|-------|------|------|-------------|
| `transferId` | `MessageId` | 8 bytes | Identifies the file transfer session |
| `chunkIndex` | `Int` | 4 bytes | Chunk index (U32) |
| `totalChunks` | `Int` | 4 bytes | Total chunk count (U32) |
| `data` | `ByteArray` | 2 + N bytes | 2-byte length prefix + chunk bytes (max 65535) |

Fixed overhead: **8 + 4 + 4 + 2 = 18 bytes** + chunk data

---

### `Packet`
| Field | Type | Description |
|-------|------|-------------|
| `header` | `Header` | 122-byte parsed header |
| `payload` | `ByteArray` | Raw payload bytes |

---

### `Envelope`
| Field | Type | Description |
|-------|------|-------------|
| `packet` | `Packet` | The decoded packet |
| `remoteAddress` | `InetSocketAddress` | Source network address |

---

## Sealed Interfaces

### `Payload` (sealed)
| Subtype | Fields | Description |
|---------|--------|-------------|
| `Payload.Hello` | `name: String`, `publicKey: PublicKey`, `routeEntries: List<RouteEntry>` | Neighbour announcement |
| `Payload.Message` | `envelope: SecureEnvelope` | Encrypted user message |
| `Payload.Ack` | `status: Int`, `signature: Signature` | Delivery acknowledgement |
| `Payload.RREQ` | `name: String`, `publicKey: PublicKey` | Route request |
| `Payload.RREP` | `name: String`, `publicKey: PublicKey` | Route reply |
| `Payload.RERR` | `destinations: List<NodeId>` | Route error |
| `Payload.Voice` | `packet: VoicePacket` | Encoded audio packet |
| `Payload.FileChunk` | `packet: FileChunkPacket` | File transfer chunk packet |

---

### `ParseResult<T>` (sealed)
| Subtype | Fields | Description |
|---------|--------|-------------|
| `ParseResult.Success<T>` | `value: T` | Successfully parsed value |
| `ParseResult.Failure` | `error: ParseError` | Parse failed with error |

---

### `ParseError` (sealed)
| Subtype | Fields | Meaning |
|---------|--------|---------|
| `TooShort` | `actual: Int`, `expected: Int` | Buffer smaller than required |
| `InvalidMagic` | `actual: Int` | Magic bytes did not equal `0x4D45` |
| `InvalidVersion` | `actual: Int` | Version not supported |
| `InvalidPayloadLength` | `actual: Int` | Payload length field invalid |
| `UnsupportedType` | `actual: Int` | Unknown packet type byte |
| `MalformedPayload` | `reason: String` | Payload content is invalid |

---

## Constant Tables

### `HeaderProtocol.Type` — Packet Types
| Constant | Value | Payload Type |
|----------|-------|-------------|
| `HELLO` | `0x01` | `Payload.Hello` |
| `MESSAGE` | `0x02` | `Payload.Message` |
| `RREQ` | `0x03` | `Payload.RREQ` |
| `RREP` | `0x04` | `Payload.RREP` |
| `ACK` | `0x05` | `Payload.Ack` |
| `RERR` | `0x06` | `Payload.RERR` |
| `VOICE` | `0x07` | `Payload.Voice` |
| `FILE_CHUNK` | `0x08` | `Payload.FileChunk` |

### `HeaderProtocol.Flags` — Bit Flags
| Constant | Value | Meaning |
|----------|-------|---------|
| `BROADCAST` | `0x01` | Send to all neighbours |
| `ENCRYPTED` | `0x02` | Payload is encrypted |
| `ACK_REQUESTED` | `0x04` | Recipient must send ACK |

### `HeaderProtocol` — Other Constants
| Constant | Value | Description |
|----------|-------|-------------|
| `Magic.EXPECTED` | `0x4D45` | ASCII "ME" |
| `Version.SUPPORTED_VERSION` | `1` | Current protocol version |
| `MAX_PAYLOAD` | `0xFFFF` | Maximum payload length (65535 bytes) |
| `HEADER_SIZE` | `122` | Fixed header size in bytes |

### `ContentType`
| Constant | Value | Description |
|----------|-------|-------------|
| `CHAT` | `0x01` | Plain text / chat message |
| `CALL_SIGNAL` | `0x02` | Call signalling payload |
| `FILE_SIGNAL` | `0x03` | File transfer signalling payload |

### `CallSignalType`
| Constant | Value | Description |
|----------|-------|-------------|
| `OFFER` | `0x01` | Initiator sends ephemeral public key |
| `RINGING` | `0x02` | Callee alerting user |
| `ACCEPT` | `0x03` | Callee sends ephemeral public key |
| `REJECT` | `0x04` | Callee declined |
| `BUSY` | `0x05` | Callee busy |
| `CANCEL` | `0x06` | Caller cancelled before answer |
| `HANGUP` | `0x07` | Either party ended call |

### `FileSignalType`
| Constant | Value | Description |
|----------|-------|-------------|
| `OFFER` | `0x01` | Sender offers file metadata |
| `ACCEPT` | `0x02` | Receiver accepted transfer |
| `REJECT` | `0x03` | Receiver rejected transfer |
| `CANCEL` | `0x04` | Either side cancelled transfer |
| `COMPLETE` | `0x05` | Receiver completed file assembly |

---

## Protocol Objects (Wire Layouts)

### `HeaderProtocol` — Wire Layout (122 bytes total)

| Offset | Field | Size | Type | Notes |
|--------|-------|------|------|-------|
| 0 | magic | 2 | U16 | Must equal `0x4D45` |
| 2 | version | 1 | U8 | Must equal `1` |
| 3 | type | 1 | U8 | See Type table |
| 4 | flags | 1 | U8 | Bitmask; see Flags table |
| 5 | hopcount | 1 | U8 | Incremented at each hop |
| 6 | ttl | 1 | U8 | Decremented at each hop |
| 7 | reserved | 1 | U8 | Set to `0` |
| 8 | immediateSenderNodeId | 32 | bytes | Last-hop sender |
| 40 | sourceNodeId | 32 | bytes | Originating node |
| 72 | destNodeId | 32 | bytes | Destination node |
| 104 | id | 8 | bytes | Packet ID |
| 112 | originTimestamp | 8 | I64 | ms since epoch |
| 120 | payloadLength | 2 | U16 | Byte count of payload |

---

### `HelloProtocol` — Payload Wire Layout (type `0x01`)

| Order | Field | Size | Type | Notes |
|-------|-------|------|------|-------|
| 1 | name | 1 + N | U8 len + UTF-8 | Node display name |
| 2 | publicKey | 91 | bytes | P-256 DER public key |
| 3 | routeCount | 2 | U16 | Number of route entries |
| 4..N | routeEntries | variable | `RouteEntry[]` | See RouteEntry layout |

**RouteEntry wire layout (per entry):**

| Order | Field | Size | Type |
|-------|-------|------|------|
| 1 | nodeId | 32 | bytes |
| 2 | hopcount | 1 | U8 |
| 3 | publicKey | 91 | bytes |
| 4 | timestamp | 8 | I64 (ms) |
| 5 | name | 1 + N | U8 len + UTF-8 |

---

### `MessageProtocol` — Payload Wire Layout (type `0x02`)

#### 5.2.1 Secure Envelope (outer, always present)

| Order | Field | Size | Type | Notes |
|-------|-------|------|------|-------|
| 1 | envVersion | 1 | U8 | Envelope format version |
| 2 | senderNodeId | 32 | bytes | Sender's node ID |
| 3 | encSymKey | 91 | bytes | P-256 DER ephemeral key for ECDH |
| 4 | nonce | 12 | bytes | AES-GCM nonce |
| 5 | ciphertextLen | 4 | U32 | Length of ciphertext |
| 6 | ciphertext | N | bytes | AES-GCM encrypted inner block |
| 7 | signature | 64 | bytes | Signature over envelope |

#### 5.2.2 Inner Plaintext Block (inside ciphertext, after decryption)

| Order | Field | Size | Type | Notes |
|-------|-------|------|------|-------|
| 1 | messageId | 8 | bytes | Message unique ID |
| 2 | timestamp | 8 | I64 | Send time (ms) |
| 3 | contentType | 1 | U8 | See ContentType table |
| 4 | contentLen | 4 | U32 | Length of content |
| 5 | content | N | bytes | Application content bytes |

---

### `AckProtocol` — Payload Wire Layout (type `0x05`)

| Order | Field | Size | Type | Notes |
|-------|-------|------|------|-------|
| 1 | status | 1 | U8 | Delivery status code |
| 2 | signature | 64 | bytes | Authenticates the ACK |

**Total: 65 bytes**

---

### `RREQProtocol` — Payload Wire Layout (type `0x03`)

| Order | Field | Size | Type | Notes |
|-------|-------|------|------|-------|
| 1 | name | 1 + N | U8 len + UTF-8 | Queried node's name |
| 2 | publicKey | 91 | bytes | Requester's P-256 public key |

---

### `RREPProtocol` — Payload Wire Layout (type `0x04`)

| Order | Field | Size | Type | Notes |
|-------|-------|------|------|-------|
| 1 | Name | 1 + N | U8 len + UTF-8 | Responding node's name |
| 2 | PublicKey | 91 | bytes | Responding node's P-256 public key |

---

### `RERRProtocol` — Payload Wire Layout (type `0x06`)

| Order | Field | Size | Type | Notes |
|-------|-------|------|------|-------|
| 1 | count | 1 | U8 | Number of broken-route destinations |
| 2..N | destinations | count × 32 | bytes | List of unreachable `NodeId`s |

**Total: 1 + (count × 32) bytes**

---

### `VoicePacketProtocol` — Payload Wire Layout (type `0x07`)

| Order | Field | Size | Type | Notes |
|-------|-------|------|------|-------|
| 1 | callId | 8 | bytes | `MessageId` identifying the call |
| 2 | sequenceNumber | 4 | U32 | Packet ordering counter |
| 3 | timestamp | 8 | I64 | Audio capture time (ms) |
| 4 | audioLen | 2 | U16 | Length of encoded audio (max 65535) |
| 5 | encodedAudio | N | bytes | Encoded audio bytes |

**Fixed overhead: 22 bytes + audio length**

---

### `FileChunkPacketProtocol` — Payload Wire Layout (type `0x08`)

| Order | Field | Size | Type | Notes |
|-------|-------|------|------|-------|
| 1 | transferId | 8 | bytes | `MessageId` identifying transfer |
| 2 | chunkIndex | 4 | U32 | Zero-based chunk index |
| 3 | totalChunks | 4 | U32 | Total chunk count |
| 4 | chunkDataLen | 2 | U16 | Chunk byte length (max 65535) |
| 5 | chunkData | N | bytes | Raw file chunk bytes |

**Fixed overhead: 18 bytes + chunk data**

---

### `CallSignalProtocol` — Signal Wire Layout (inside `InnerPlaintextBlock.content` when `contentType = CALL_SIGNAL`)

| Order | Field | Size | Type | Notes |
|-------|-------|------|------|-------|
| 1 | callId | 8 | bytes | `MessageId` for the call session |
| 2 | signalType | 1 | U8 | See CallSignalType table |
| 3 | payloadLen | 4 | U32 | Length of signal payload |
| 4 | payload | N | bytes | Signal-specific bytes |

**Signal payload per type:**

| Type | Payload | Size |
|------|---------|------|
| `OFFER (0x01)` | `CallOffer.ephemeralPublicKey` | 91 bytes |
| `ACCEPT (0x03)` | `CallAccept.ephemeralPublicKey` | 91 bytes |
| `RINGING / REJECT / BUSY / CANCEL / HANGUP` | *(empty)* | 0 bytes |

---

### `FileSignalProtocol` — Signal Wire Layout (inside `InnerPlaintextBlock.content` when `contentType = FILE_SIGNAL`)

| Order | Field | Size | Type | Notes |
|-------|-------|------|------|-------|
| 1 | transferId | 8 | bytes | `MessageId` for transfer session |
| 2 | signalType | 1 | U8 | See FileSignalType table |
| 3 | payloadLen | 4 | U32 | Length of signal payload |
| 4 | payload | N | bytes | Signal-specific bytes |

**Signal payload per type:**

| Type | Payload | Size |
|------|---------|------|
| `OFFER (0x01)` | `FileTransferMetadata` | variable |
| `ACCEPT / REJECT / CANCEL / COMPLETE` | *(empty)* | 0 bytes |

---

## Packet Full Wire Structure

```
[ HEADER 122 bytes ][ PAYLOAD up to 65535 bytes ]

Total packet size = 122 + payloadLength
```

### Per-type total minimum sizes

| Type | Header | Payload (min) | Total (min) |
|------|--------|---------------|-------------|
| HELLO | 122 | 1+name + 91 + 2 = 94+ | 216+ bytes |
| MESSAGE | 122 | 1+32+91+12+4+64 = 204+ | 326+ bytes |
| RREQ | 122 | 1+name + 91 = 92+ | 214+ bytes |
| RREP | 122 | 1+name + 91 = 92+ | 214+ bytes |
| ACK | 122 | 65 | 187 bytes |
| RERR | 122 | 1 + 32×count | 123+ bytes |
| VOICE | 122 | 22 + audio | 144+ bytes |
| FILE_CHUNK | 122 | 18 + chunk | 140+ bytes |

---

## File Transfer Transport Notes (Current Implementation)

1. **File signals** (`FILE_SIGNAL`) are carried inside encrypted `MESSAGE (0x02)` packets via `InnerPlaintextBlock.contentType = FILE_SIGNAL`.
2. **File chunks** are carried as native `FILE_CHUNK (0x08)` packets.
3. `FILE_CHUNK` packets are sent and forwarded over **TCP** in the routing layer.
4. `FILE_CHUNK` packets currently use `flags = 0` (no `ENCRYPTED`, no `ACK_REQUESTED`).

---

## `RouteProtocol` — Shared Sub-Field Sizes

| Field | Size | Encoding |
|-------|------|----------|
| `nodeId` | 32 bytes | raw bytes |
| `hopcount` | 1 byte | U8 |
| `publicKey` | 91 bytes | P-256 DER bytes |
| `timestamp` | 8 bytes | I64 ms |
| `name` | 1 + N bytes | U8 length + UTF-8 |

> Used by `HelloProtocol.routeEntries` to encode/decode each `RouteEntry`.

---

## `Field<T>` Interface

Every protocol field implements:

```kotlin
interface Field<T> {
    fun read(data: ByteArray, baseOffset: Int = 0): ReadWithLength<T>
    fun write(data: ByteArray, value: T, baseOffset: Int = 0): Int  // returns bytes written
}
```
