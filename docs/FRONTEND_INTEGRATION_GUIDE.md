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

### 2.0 Mission Workflow States & Lifecycle

| Status | Description | Recovery Status | Progress Typical % |
| :--- | :--- | :--- | :--- |
| `PENDING` | Created and ready for launch | `NOT_STARTED` | 0.0% |
| `ACTIVE` | Drone has launched and is en route / performing task | `NOT_STARTED` | 10.0% – 75.0% |
| `RETURNING_TO_BASE` | Delivery complete, drone returning to base (recovery phase) | `RETURNING_TO_BASE` | 85.0% – 95.0% |
| `COMPLETED` | Drone landed safely at base, mission finished | `COMPLETED` | 100.0% |
| `FAILED` | Mission failed or emergency abort triggered | `FAILED` | N/A |
| `ABORTED` | Mission manually aborted | `ABORTED` | N/A |

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
    "progress": 0.0,
    "currentStep": "Pending",
    "recoveryStatus": "NOT_STARTED",
    "createdAt": "2026-10-31T12:00:00",
    "updatedAt": "2026-10-31T12:00:00"
  }
  ```

### 2.2 Start Mission
- **Endpoint**: `POST /api/missions/{missionId}/start`
- **Description**: Validates that the mission is in `PENDING` status, triggers the Temporal workflow execution, and marks the mission as `ACTIVE`.
- **Response**: `200 OK`
  ```json
  {
    "missionId": "ad3e4b49-caa7-4081-8da9-49da09c09b7b",
    "droneId": "DR-02",
    "source": "Warehouse-Alpha",
    "destination": "Hospital-North",
    "status": "ACTIVE",
    "progress": 10.0,
    "currentStep": "Taking off",
    "recoveryStatus": "NOT_STARTED",
    "createdAt": "2026-10-31T12:00:00",
    "updatedAt": "2026-10-31T12:00:05"
  }
  ```
- **Error Codes**:
  - `404 Not Found`: If `missionId` does not exist.
  - `409 Conflict`: If the mission is not in `PENDING` state (e.g. already running or completed).

### 2.3 Retrieve Mission Status & Recovery Status
- **Endpoint**: `GET /api/missions/{missionId}/status`
- **Description**: Lightweight endpoint returning current mission status, recovery phase, progress percentage, and current step.
- **Response**: `200 OK`
  ```json
  {
    "missionId": "ad3e4b49-caa7-4081-8da9-49da09c09b7b",
    "status": "RETURNING_TO_BASE",
    "recoveryStatus": "RETURNING_TO_BASE",
    "progress": 90.0,
    "currentStep": "Returning to base",
    "updatedAt": "2026-10-31T12:08:30"
  }
  ```

### 2.4 Dedicated Mission Progress & Recovery Query
- **Endpoint**: `GET /api/missions/{missionId}/progress`
- **Description**: Returns live progress synced with Member 2's Temporal workflow queries.
- **Response**: `200 OK`
  ```json
  {
    "missionId": "ad3e4b49-caa7-4081-8da9-49da09c09b7b",
    "droneId": "DR-02",
    "status": "RETURNING_TO_BASE",
    "progress": 90.0,
    "currentStep": "Returning to base",
    "recoveryStatus": "RETURNING_TO_BASE",
    "updatedAt": "2026-10-31T12:08:30"
  }
  ```

### 2.5 Update Mission Status
- **Endpoint**: `PATCH /api/missions/{missionId}/status?status=RETURNING_TO_BASE`
- **Description**: Update lifecycle status (also accepts JSON body `{"status": "RETURNING_TO_BASE"}`). Automatically adjusts `recoveryStatus` and `progress`.
- **Response**: `200 OK` (Full `Mission` entity)

### 2.6 Update Mission Progress
- **Endpoint**: `PATCH /api/missions/{missionId}/progress`
- **Request Body**:
  ```json
  {
    "progress": 95.0,
    "currentStep": "Final approach to base",
    "recoveryStatus": "RETURNING_TO_BASE"
  }
  ```
- **Response**: `200 OK` (Full `Mission` entity)

### 2.7 Get Full Mission Details
- **Endpoint**: `GET /api/missions/{missionId}`
- **Response**: `200 OK` (Full `Mission` entity)

### 2.8 Get All Missions
- **Endpoint**: `GET /api/missions`
- **Response**: `200 OK` (List of `Mission` entities)

### 2.9 Emergency Abort Mission (Week 4 Feature)
- **Endpoint**: `POST /api/missions/{missionId}/abort`
- **Description**: Triggers an emergency stop/abort on an active drone mission. Sends an asynchronous signal to the running Temporal workflow (`mission-{missionId}`), halting forward flight operations and triggering the drone's safe return-to-base activity. Updates the mission status in PostgreSQL to `ABORTED`.
- **Request Headers**: `Content-Type: application/json`
- **Request Body (Optional)**:
  ```json
  {
    "reason": "Severe storm approaching flight corridor"
  }
  ```
- **Response**: `200 OK` (Full `Mission` entity)
  ```json
  {
    "missionId": "ad3e4b49-caa7-4081-8da9-49da09c09b7b",
    "droneId": "DR-02",
    "source": "Warehouse-Alpha",
    "destination": "Hospital-North",
    "status": "ABORTED",
    "progress": 50.0,
    "currentStep": "Aborted: Severe storm approaching flight corridor",
    "recoveryStatus": "ABORTED",
    "createdAt": "2026-10-31T12:00:00",
    "updatedAt": "2026-11-03T15:30:00"
  }
  ```
- **Error Codes**:
  - `400 Bad Request`: If `missionId` is blank or invalid.
  - `404 Not Found`: If `missionId` does not exist in the database.
  - `409 Conflict`: If the mission is not active (e.g. already `COMPLETED`, already `ABORTED`, `FAILED`, or still `PENDING`).

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
