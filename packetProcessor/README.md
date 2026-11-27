# Packet Processor Module

The packet processor module converts packet headers and payloads between structured data and byte arrays. It is used when packets are prepared for sending and when received packet data is read.

## Responsibilities

- Serialize packet headers and payloads into their network byte format.
- Parse received header and payload data into the types defined by the model module.
- Report malformed or incomplete packet data as parse errors.

This module handles packet encoding and decoding; transport and routing are handled by other modules.
