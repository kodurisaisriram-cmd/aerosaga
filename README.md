# AeroSaga — Drone Mission Control System

AeroSaga is a drone mission orchestration project built with Java, Spring Boot, and Temporal. It combines mission management, drone activity orchestration, and real-time GPS telemetry integration.

## Documentation

- **Frontend Integration & Telemetry Guide:** [docs/FRONTEND_INTEGRATION_GUIDE.md](docs/FRONTEND_INTEGRATION_GUIDE.md) — REST endpoints, STOMP WebSocket topics, message schemas, and frontend integration details.
- **Temporal Workflow and Drone Mission Orchestration:** This README documents the workflow structure and supporting components.

## Temporal Workflow Overview

The Temporal workflow coordinates drone mission execution through workflow definitions, activities, and worker configuration.

### Main Components

- `DroneMissionWorkflow.java` — Defines the workflow contract and mission execution entry point.
- `DroneMissionWorkflowImpl.java` — Implements the workflow execution logic.
- `DroneActivity.java` — Declares drone operations invoked by the workflow.
- `DroneActivityImpl.java` — Implements the drone activity operations.
- `TemporalWorkerConfig.java` — Configures the Temporal worker and registers workflow and activity implementations.
- `WorkerStarter.java` — Provides worker or workflow startup functionality as implemented in the project.
- `DroneMissionRequest.java` — Represents input data for a drone mission.
- `DroneStatus.java` — Defines possible drone execution states.
- `MissionStatus.java` — Defines mission lifecycle states.
- `DroneProgress.java` — Tracks the progress of an individual drone.
- `MissionProgress.java` — Represents overall mission progress and individual drone progress.

## Workflow Execution

The intended execution flow is:

1. A mission is initiated.
2. The Temporal workflow begins execution.
3. The workflow coordinates the required drone activities.
4. The Temporal worker processes workflow and activity tasks.
5. Mission progress is updated as execution proceeds.
6. The mission completes or reports a failure, depending on the implemented logic.

## Prerequisites

Install the following tools:

- Java Development Kit (JDK 21 for this project)
- Apache Maven
- Docker Desktop
- Git
- Node.js and npm for the frontend

## Setup and Build

Run Docker services from the project root:

```bash
docker compose up -d
docker compose ps
```

Build the backend from the `backend` directory:

```bash
mvn clean test
```

To run the Spring Boot backend:

```bash
mvn spring-boot:run
```

To run the frontend, open a separate terminal in the `frontend` directory:

```bash
npm install
npm run dev
```

Ensure the required services, including Temporal and PostgreSQL, are running and correctly configured before testing mission execution.

## Testing and Verification

- Verify that the Maven build and tests complete successfully.
- Confirm that the Docker services are running.
- Check that the Temporal server is accessible.
- Verify that the worker starts without configuration errors.
- Test the mission APIs and telemetry integration.
- Confirm that workflow progress and status are reported as expected.

## Team Collaboration

AeroSaga is a collaborative team project. The backend workflow, mission APIs, telemetry, and frontend are integrated through shared Git branches and reviewed changes.

## Team members responsibility

In our team, 4 members are there.

1. Sai Sriram - Github and Project integration.

Responsibility - Repository management, project coordination and integration.

2. Aakash Verma - Mission Orchestration

Responsibility - Implementing drone mission workflows using temporal.

3. Meemansa Sakhla -  REST APIs and Backend Integration

Responsibility - Developing backend endpoints and connecting application components.

4. Sachin Rathod - Frontend development

Responsibility - Creating the user interface and visual monitoring dashboard.

## Current Scope

The project combines Temporal-based workflow orchestration with mission management and telemetry integration. Actual drone communication and execution depend on the functionality implemented in the relevant modules.