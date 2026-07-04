# Home Integration Report (Phase 3A)

Date: 2026-07-04
Scope: Home screen + mesh UI integration only
Build execution: Not run by instruction

## 1. Files modified

- ui/src/main/java/com/minor/ui/state/HomeUiState.kt
- ui/src/main/java/com/minor/ui/viewmodel/HomeViewModel.kt
- ui/src/main/java/com/minor/ui/viewmodel/HomeViewModelFactory.kt
- ui/src/main/java/com/minor/ui/screens/home/HomeScreen.kt
- ui/src/main/java/com/minor/ui/navigation/MeshAppNavHost.kt
- ui/build.gradle.kts
- app/src/main/java/com/minor/meshapp/MainActivity.kt
- HOME_INTEGRATION_REPORT.md

## 2. Existing backend APIs reused

- meshControl MeshService APIs:
  - meshStateStream
  - peersStream
  - routeStateStream
  - start()
  - stop()
- meshControl model types:
  - MeshState
  - PeerStatus
- Existing app bootstrap:
  - MeshApplication.container.meshService
  - IdentityStore.displayName
- Existing transport utilities (already present and reused):
  - NetworkInfo.isStaApSupported()
  - NetworkInfo.isLikelySupported()
  - NetworkScanner.getNetworkInterfaceInfo()

## 3. Fake implementations removed

- Removed HomeViewModel dependency on FakeDataProvider for:
  - mesh on/off state
  - profile identity data
- Removed local fake-only Home toggle behavior that flipped a boolean.
- Home now derives node list from real peersStream and hop count from routeStateStream.
- Home profile dropdown now shows live mesh status and real device/network capability values.

## 4. Home Screen integration status

Status: Complete for Phase 3A scope.

Implemented:
- Mesh ON/OFF button wired to MeshService start/stop.
- Mesh state text wired to meshStateStream.
- Connection status summary derived from live mesh state + active peers.
- Current device name shown from existing identity bootstrap.
- Connected node list driven by real peersStream and routeStateStream data.
- Lifecycle-aware UI collection retained using collectAsStateWithLifecycle.
- No polling used for mesh state/peer state updates.

## 5. Mesh integration status

Status: Integrated into Home UI.

- Home ViewModel now observes MeshService StateFlows via combine.
- Home UI auto-updates when mesh state/peer state/route state changes.
- No backend module logic changes were made.

## 6. Node list integration status

Status: Complete for Home scope.

- Fake node list replaced with real peer data from MeshService.peersStream.
- Node cards display only available real fields:
  - name
  - node ID
  - online/offline status
  - peer status enum value
  - IP (when available)
  - hop count (when route is available)

## 7. Profile dropdown integration status

Status: Complete for Home scope.

Dropdown now shows:
- Application name
- Current mesh status
- Current device name
- STA + AP capability
- Network interfaces count
- Navigation entry to existing Network Interfaces page
- About item

Reactive behavior:
- Mesh status updates from backend flow.
- Network interfaces count refreshes from existing scanner when menu opens.

## 8. Network Interfaces integration status

Status: Reused existing implementation; no rewrite.

- Existing NetworkInterfacesScreen and NetworkInterfacesViewModel were left unchanged.
- Existing Home navigation entry to Network Interfaces was preserved and reused.

## 9. Remaining work before Phase 3B

- Integrate Chats and Conversation screens with real messaging data (out of Phase 3A scope).
- Replace fake data paths still used by ChatsViewModel and ConversationViewModel.
- Wire messaging send/receive and delivery state into chat UI in the next phase.
