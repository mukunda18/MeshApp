# Messaging Module

`messaging` owns the user-facing conversation layer. It sits above
`meshControl` and below the UI, converting opaque mesh packets into persisted
chat history and public streams.

## Purpose

This module provides:

- durable per-node conversations
- incoming and outgoing message persistence
- delivery status tracking for queued, sent, delivered, and failed messages
- UI-friendly history and thread-list commands
- public streams for message, status, and conversation updates
- a security boundary for encrypting/signing outbound messages and decoding
  inbound messages

## Main Classes

### ConversationStore

SQLite-backed local storage for conversations and messages.

It is constructed once by the application layer, usually in `MeshApplication`
or `AppContainer`, and injected into `MessagingService`.

Key functions:

- `getConversation(nodeID)`: returns the stored conversation for one remote node
- `listConversations()`: returns conversations ordered by most recently updated
- `appendMessage(nodeID, message)`: creates/updates a conversation and persists a message
- `updateDeliveryStatus(messageID, deliveryStatus)`: persists delivery status changes
- `deleteConversation(nodeID)`: deletes a conversation and its messages

Storage model:

- `conversations`: one row per remote node
- `messages`: ordered messages belonging to a remote node
- message order is preserved by SQLite insertion order
- foreign-key constraints are enabled for conversation/message cleanup

### MessagingService

Central service for the messaging module.

It depends on:

- local node ID
- `MeshService`
- `ConversationStore`
- `MessageSecurityCodec`
- a coroutine dispatcher

On `start()`, it:

- loads stored conversations into `conversationsStream`
- starts a scoped listener for `MeshService.incomingMessageStream`
- starts a scoped listener for `MeshService.deliveryStatusStream`

On `stop()`, it:

- cancels the messaging scope
- clears in-memory delivery tracking

## Public API

### Commands

- `start()`
- `stop()`
- `send(destinationNodeID, plaintext)`
- `listConversations()`
- `getHistory(nodeID)`

### Streams

- `messagesStream`: emits incoming messages, outgoing messages, and persisted message updates
- `deliveryStatusStream`: emits queued, sent, delivered, and failed status updates
- `conversationsStream`: emits thread summaries with last message and unread count

## Runtime Flow

### Sending a message

1. UI calls `MessagingService.send(destinationNodeID, plaintext)`.
2. `MessagingService` creates a local compose timestamp and local message ID.
3. `MessageSecurityCodec.encode(...)` produces an opaque `Payload`.
4. `MeshService.sendMessage(destinationNodeID, payload)` queues the packet for mesh delivery.
5. The returned mesh message ID is used for delivery tracking.
6. The outgoing message is persisted as `QUEUED`.
7. `messagesStream`, `deliveryStatusStream`, and `conversationsStream` are updated.
8. Later `MeshService.deliveryStatusStream` updates the stored message to `SENT`,
   `DELIVERED`, or `FAILED`.
9. If no terminal status arrives before the timeout, the message is marked `FAILED`.

### Receiving a message

1. `MeshService` emits a packet on `incomingMessageStream`.
2. `MessagingService` passes the packet to `MessageSecurityCodec.decode(...)`.
3. The codec is responsible for signature verification, decryption, and timestamp freshness.
4. The decoded plaintext is converted to a `Message`.
5. The message is persisted in the conversation matching the sender node ID.
6. `messagesStream` and `conversationsStream` are updated.

### Delivery status updates

1. `MeshService` emits delivery status by mesh message ID.
2. `MessagingService` maps mesh states to messaging states:
   - `SENT` -> `SENT`
   - `DELIVERED` -> `DELIVERED`
   - `FAILED` -> `FAILED`
3. `ConversationStore.updateDeliveryStatus(...)` persists the new status.
4. Public streams emit the updated message and status.

## Security Boundary

The messaging module defines `MessageSecurityCodec` instead of directly
depending on a concrete security implementation.

```kotlin
interface MessageSecurityCodec {
    fun encode(
        plaintext: String,
        recipientNodeID: NodeId,
        messageID: MessageId
    ): Payload

    fun decode(packet: Packet): DecodedMessage
}
```

The security module should provide an implementation that:

- signs and encrypts outbound plaintext
- verifies Ed25519 signatures on inbound packets
- decrypts inbound payloads
- checks inner message timestamp freshness
- returns `DecodedMessage` only after validation succeeds

This keeps `messaging` testable and lets `AppContainer` wire the final security
implementation without creating a compile-time dependency cycle.

## Integration Notes

Normal construction should happen once in the application container:

```kotlin
val conversationStore = ConversationStore(context)
val messagingService = MessagingService(
    ownNodeId = config.ownNodeId,
    meshService = meshService,
    conversationStore = conversationStore,
    securityCodec = securityCodec
)
```

Typical startup order:

1. create identity/config
2. create `MeshService`
3. create `ConversationStore`
4. create `MessagingService`
5. start `MeshService`
6. start `MessagingService`

The UI should read history through `getHistory(nodeID)` and observe changes
through the public streams.

## Current Limitations

- `MessageSecurityCodec` is an integration boundary; the concrete security
  implementation still needs to be provided by the `security` module.
- Unread counts currently count delivered incoming messages. A future read
  receipt/read-marker model should replace that with explicit read state.
- Full Gradle verification requires a local JDK. Without `JAVA_HOME` or `java`
  on `PATH`, the project cannot run `./gradlew build`.
