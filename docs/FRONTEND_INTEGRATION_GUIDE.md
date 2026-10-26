# AeroSaga — Frontend Integration & Telemetry Guide

This document specifies the integration contract between the **AeroSaga Backend** and **Member 4's Frontend Dashboard** for mission control and real-time drone telemetry streaming.

---

## 1. Architecture Overview

```
+---------------------------+             HTTP REST (Port 8080)
|                           | ----------------------------------------------> [ Mission & Telemetry REST APIs ]
|   React + CesiumJS UI     |                                                  - POST /api/missions
|       (Member 4)          |                                                  - POST /api/missions/{id}/start
|                           |                                                  - GET  /api/missions/{id}/status
|                           |             STOMP over WebSocket
|                           | <============================================== [ WebSocket Broker ]
+---------------------------+       ws://localhost:8080/ws/telemetry           - /topic/telemetry
                                                                               - /topic/drones/{droneId}/telemetry
                                                                               - /topic/missions/{missionId}/telemetry
```

- **Separation of Concerns**: Mission control actions (create, start, status) and historical queries are handled through REST endpoints. Live GPS telemetry is pushed asynchronously over WebSocket using STOMP.
- **Protocol**: STOMP over WebSocket (`ws://localhost:8080/ws/telemetry`) with SockJS fallback enabled (`http://localhost:8080/ws/telemetry`).

---

## 2. Mission Control REST APIs

Base URL: `http://localhost:8080/api/missions`

### 2.1 Create Mission
- **Endpoint**: `POST /api/missions`
- **Request Headers**: `Content-Type: application/json`
- **Request Body**:
  ```json
  {
    "droneId": "DR-02",
    "source": "Warehouse-Alpha",
    "destination": "Hospital-North"
  }
  ```
- **Response**: `201 Created`
  ```json
  {
    "missionId": "ad3e4b49-caa7-4081-8da9-49da09c09b7b",
    "droneId": "DR-02",
    "source": "Warehouse-Alpha",
    "destination": "Hospital-North",
    "status": "PENDING",
    "createdAt": "2026-10-20T16:00:00"
  }
  ```

### 2.2 Start Mission
- **Endpoint**: `POST /api/missions/{missionId}/start`
- **Description**: Validates that the mission is in `PENDING` status, triggers the Temporal workflow execution, and marks the mission as `IN_PROGRESS`.
- **Response**: `200 OK`
  ```json
  {
    "missionId": "ad3e4b49-caa7-4081-8da9-49da09c09b7b",
    "droneId": "DR-02",
    "source": "Warehouse-Alpha",
    "destination": "Hospital-North",
    "status": "IN_PROGRESS",
    "createdAt": "2026-10-20T16:00:00"
  }
  ```
- **Error Codes**:
  - `404 Not Found`: If `missionId` does not exist.
  - `409 Conflict`: If the mission is not in `PENDING` state (e.g. already running or completed).

### 2.3 Retrieve Mission Status
- **Endpoint**: `GET /api/missions/{missionId}/status`
- **Description**: Dedicated endpoint to fetch lightweight mission status.
- **Response**: `200 OK`
  ```json
  {
    "missionId": "ad3e4b49-caa7-4081-8da9-49da09c09b7b",
    "status": "IN_PROGRESS"
  }
  ```

### 2.4 Get Full Mission Details
- **Endpoint**: `GET /api/missions/{missionId}`
- **Response**: `200 OK` (Full `Mission` entity)

---

## 3. Telemetry REST APIs

Base URL: `http://localhost:8080/api/telemetry`

### 3.1 Ingest Telemetry (Simulation / Drone Agent)
- **Endpoint**: `POST /api/telemetry`
- **Request Body**:
  ```json
  {
    "droneId": "DR-02",
    "missionId": "ad3e4b49-caa7-4081-8da9-49da09c09b7b",
    "latitude": 19.0760,
    "longitude": 72.8777,
    "altitude": 150.0,
    "batteryLevel": 72.0
  }
  ```
- **Behavior**: Persists telemetry to PostgreSQL and automatically broadcasts the GPS telemetry packet across the WebSocket broker.

### 3.2 Query Telemetry History
- `GET /api/telemetry`: All recorded telemetry.
- `GET /api/telemetry/drone/{droneId}`: Chronological telemetry history for a specific drone (newest first).
- `GET /api/telemetry/drone/{droneId}/latest`: Most recent telemetry packet for a drone.

---

## 4. WebSocket Live Telemetry Streaming

### 4.1 Connection Details
- **STOMP Endpoint**: `ws://localhost:8080/ws/telemetry`
- **SockJS Fallback Endpoint**: `http://localhost:8080/ws/telemetry`
- **CORS Allowed Origins**: `*` (Configured for development with Vite at `http://localhost:5173`)

### 4.2 Available Subscription Topics

| Topic Destination | Purpose | Example Use Case |
| :--- | :--- | :--- |
| `/topic/telemetry` | Global broadcast for all drones | Main 3D Cesium Map showing all active drones |
| `/topic/drones/{droneId}/telemetry` | Telemetry filtered for a single drone | Selected Drone Details panel (`DRONE-07`) |
| `/topic/missions/{missionId}/telemetry` | Telemetry filtered for a single mission | Active Mission Tracking panel |

### 4.3 Telemetry Message Schema (JSON)

Every frame published to the telemetry topics contains:

```json
{
  "droneId": "DR-02",
  "latitude": 19.076,
  "longitude": 72.8777,
  "altitude": 150.0,
  "batteryLevel": 72.0,
  "timestamp": "2026-10-20T10:37:35.302672600Z",
  "missionId": "ad3e4b49-caa7-4081-8da9-49da09c09b7b"
}
```

---

## 5. Frontend Implementation with Auto-Reconnection (React / TypeScript)

Member 4 can install `@stomp/stompjs` (standard lightweight STOMP client for modern browsers):

```bash
npm install @stomp/stompjs
```

### Complete Hook Implementation (`useDroneTelemetry.ts`):

```typescript
import { useEffect, useState } from "react";
import { Client } from "@stomp/stompjs";

export interface TelemetryMessage {
  droneId: string;
  latitude: number;
  longitude: number;
  altitude: number;
  batteryLevel: number;
  timestamp: string;
  missionId: string;
}

export function useDroneTelemetry(onTelemetryReceived: (telemetry: TelemetryMessage) => void) {
  const [isConnected, setIsConnected] = useState(false);

  useEffect(() => {
    // Configure STOMP client with built-in exponential auto-reconnection
    const client = new Client({
      brokerURL: "ws://localhost:8080/ws/telemetry",
      reconnectDelay: 5000, // Reconnect every 5 seconds if connection drops
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      onConnect: () => {
        setIsConnected(true);
        console.log("[STOMP] Connected to AeroSaga live telemetry stream");

        // Subscribe to all drone telemetry
        client.subscribe("/topic/telemetry", (message) => {
          if (message.body) {
            const data: TelemetryMessage = JSON.parse(message.body);
            onTelemetryReceived(data);
          }
        });
      },
      onDisconnect: () => {
        setIsConnected(false);
        console.warn("[STOMP] Disconnected from telemetry stream. Reconnecting...");
      },
      onStompError: (frame) => {
        console.error("[STOMP Error]", frame.headers["message"], frame.body);
      },
    });

    client.activate();

    // Clean up on component unmount
    return () => {
      client.deactivate();
    };
  }, [onTelemetryReceived]);

  return { isConnected };
}
```
