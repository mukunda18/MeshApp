# Transport Module

The transport module provides the low-level network connections used to exchange mesh packets. It supports TCP connections and UDP communication over the device's network interfaces.

## Responsibilities

- Send packets to a peer over TCP.
- Receive incoming TCP packets.
- Send UDP packets to a peer or broadcast them on available interfaces.
- Find network interface details, including broadcast addresses.

The transport module moves packet data between devices. It does not decide the packet's destination route or interpret its contents.
