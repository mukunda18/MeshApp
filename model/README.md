# Model Module

The model module defines the shared data types and wire formats used across MeshApp. Other modules use these definitions to represent information consistently and exchange packets.

## Responsibilities

- Define common identifiers and values, such as node IDs, message IDs, and timestamps.
- Describe packet headers and payloads for messages, routing, acknowledgements, calls, and file transfers.
- Provide protocol field definitions for reading and writing packet data.

This module contains shared data and protocol definitions rather than app screens or network services.
