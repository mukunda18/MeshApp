# MeshControl Module

`meshControl` owns the high-level mesh runtime lifecycle. It sits above the
transport and routing modules and exposes a clean service API for the app,
messaging layer, UI, and Android foreground service.

## Main Classes

### MeshConfig

Central runtime configuration passed into `MeshService`.

It contains:

- UDP broadcast port and TCP port
- hello interval
- peer timeout and peer reaper interval
- route expiry and route expiry check interval
- RREQ retry timeout
- delivery ACK timeout
- max hop count / TTL
- origin timestamp freshness window
- own node ID, public key, and display name loaded from Identity
- route state publish interval

`MeshApplication` or `AppContainer` should populate identity values before
constructing `MeshService`.

### MeshService

Foreground-owned lifecycle coordinator with no direct Android dependency.

On `start()`, it:

- creates sockets through `MeshSocketFactory`
- creates `Router`, `PeersManagement`, `Sender`, and `Receiver`
- wires shared references between routing components
- starts TCP/UDP receive collection
- starts sender queue, hello broadcast, peer reaper, and route expiry loops
- aggregates internal routing events into public streams

On `stop()`, it:

- cancels the lifecycle scope
- closes TCP receiver, UDP socket, and TCP sender
- clears only public MeshService state
- drops internal component references

## Public API

### Commands

- `start()`
- `sendMessage(destinationNodeID, payload)`
- `stop()`

### Streams

- `meshStateStream`: `STARTING`, `RUNNING`, `STOPPING`, `STOPPED`, `ERROR`
- `incomingMessageStream`: sanitized inbound packets for Messaging
- `deliveryStatusStream`: message delivery status by message ID
- `peersStream`: active/removed peer snapshots for UI
- `routeStateStream`: route snapshots for diagnostics

## Integration Notes

`MeshService` does not create Android `Context` objects. Android code should
provide sockets through `MeshSocketFactory`, usually from the foreground service
or app container.

Example socket factory shape:

```kotlin
MeshSocketFactory { scope, config ->
    MeshSockets(
        tcpReceiver = TCPReceiver(config.tcpPort, scope),
        tcpSender = TCPSender(config.tcpPort, scope),
        udpSocket = UdpSocket(context, config.udpBroadcastPort, scope)
    )
}
```

Lower-level modules keep their internal channels private to their own layer.
`MeshService` is responsible for aggregating and re-publishing only the public,
sanitized streams.
