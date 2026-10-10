package com.aerosaga.backend.worker;

import com.aerosaga.backend.activity.DroneActivityImpl;
import com.aerosaga.backend.workflow.DroneMissionWorkflowImpl;

import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration 
public class TemporalWorkerConfig {
    public static final String TASK_QUEUE = "DRONE_MISSION_TASK_QUEUE";

    @Bean(destroyMethod = "shutdown")
    public WorkerFactory workerFactory() {
        WorkflowServiceStubs service = WorkflowServiceStubs.newLocalServiceStubs();
        WorkflowClient client = WorkflowClient.newInstance(service);
        WorkerFactory factory = WorkerFactory.newInstance(client);
        Worker worker = factory.newWorker(TASK_QUEUE);

        // Register the workflow implementation
        worker.registerWorkflowImplementationTypes(DroneMissionWorkflowImpl.class);

        // Register the activity implementation
        worker.registerActivitiesImplementations(new DroneActivityImpl());

        factory.start();
        return factory;
    }
}
