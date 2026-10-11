 # AeroSaga – Drone Mission Workflow

## Overview

AeroSaga is a drone mission orchestration project designed to coordinate drone operations using **Java, Spring Boot, and Temporal**. Temporal provides workflow orchestration to manage mission execution and coordinate activities in a structured manner.

This documentation describes my contribution to the Temporal workflow component of the AeroSaga project.

## My Contribution

**Module:** Temporal Workflow and Drone Mission Orchestration

My responsibilities include:

- Creating the drone mission workflow interface.
- Implementing the workflow execution logic.
- Defining the mission execution method.
- Integrating the workflow with Temporal.
- Supporting mission identification using a mission ID.
- Preparing the workflow structure for coordination with drone activities.

## Files Worked On

### 1. `DroneMissionWorkflow.java`

This file defines the workflow interface for drone mission execution.

**Responsibilities:**
- Declares the workflow contract.
- Defines the `executeMission()` method.
- Provides the entry point for starting a drone mission workflow.
- Uses Temporal workflow annotations to identify the workflow.

### 2. `DroneMissionWorkflowImpl.java`

This file contains the implementation of the drone mission workflow.

**Responsibilities:**
- Implements the `DroneMissionWorkflow` interface.
- Defines the mission execution logic.
- Declares and uses a mission ID to identify the mission.
- Coordinates the execution flow and provides a structure for invoking drone activities.

### 3. `DroneActivityImpl.java` — Supporting Integration

This file implements the drone activity operations used by the workflow.

**Responsibilities:**
- Contains the implementation of drone-related activity methods.
- Provides operations that can be invoked by the workflow.
- Separates individual drone operations from the overall workflow orchestration.

### 4. `TemporalWorkerConfig.java` — Supporting Integration

This file configures the Temporal worker in the application.

**Responsibilities:**
- Configures the Temporal worker infrastructure.
- Registers the workflow implementation and activity implementation as required.
- Enables the worker to process workflow and activity tasks from the configured Temporal task queue.

## Workflow Execution

The intended workflow execution sequence is:

1. A drone mission is initiated.
2. The Temporal workflow starts executing.
3. A mission ID is assigned or received to identify the mission.
4. The workflow coordinates the required drone activities.
5. The Temporal worker processes workflow and activity tasks.
6. The mission execution flow completes or reports an error, depending on the implemented logic.

## Setup and Build

### Prerequisites

Install the following tools:

- Java Development Kit (JDK) compatible with the project configuration
- Apache Maven 3.9.x
- Docker Desktop
- Git
- An IDE such as IntelliJ IDEA or Eclipse

### Build the Project

Open a terminal in the project root directory and run:

```bash
mvn clean compile
```

To package the application, run:

```bash
mvn clean package
```

### Start Docker Services

If the project uses Docker Compose to run Temporal and its supporting services, start them with:

```bash
docker compose up -d
```

Check the service status:

```bash
docker compose ps
```

The required services should be running before testing the application.

### Run the Spring Boot Application

Run the application using Maven:

```bash
mvn spring-boot:run
```

Alternatively, run the main Spring Boot application class from your IDE.

## Testing and Verification

Verify the implementation by checking:

- The Maven build completes successfully.
- Docker Compose services are running.
- The Temporal server is accessible.
- The worker starts without configuration errors.
- The workflow is registered with the worker.
- A mission workflow can be started and its execution observed in Temporal.

## Team Collaboration

This work is part of the AeroSaga team project. The workflow module is designed to work with the activity and configuration modules developed within the shared repository.

Changes are tracked using Git and committed to GitHub so that other team members can review and integrate them.

## Current Scope

The contribution documented here focuses on the Temporal workflow structure and its supporting integration with drone activities.

Actual drone communication, mission execution, monitoring, retry policies, and failure recovery depend on the functionality implemented in the corresponding project modules.

## Conclusion

The Temporal workflow component establishes the orchestration structure for AeroSaga's drone missions. By separating workflow definitions, workflow implementation, activities, and worker configuration, the project can maintain a modular structure that is easier to test, extend, and integrate with other components.

