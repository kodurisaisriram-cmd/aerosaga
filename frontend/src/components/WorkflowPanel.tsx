import type { Drone, MissionStep } from "../types/drone";

const steps: MissionStep[] = [
  "TAKEOFF",
  "NAVIGATE",
  "DROP PACKAGE",
  "RETURN TO BASE",
];

interface Props {
  drone: Drone | null;
}

export default function WorkflowPanel({ drone }: Props) {
  if (!drone) {
    return (
      <section className="workflow-panel">
        <div className="panel-title">Workflow Status</div>
        <p>Select a drone to view its mission workflow.</p>
      </section>
    );
  }

  const activeIndex = steps.indexOf(drone.mission);

  return (
    <section className="workflow-panel">
      <div className="workflow-header">
        <div>
          <div className="panel-title">Workflow Status</div>
          <h2>{drone.id}</h2>
        </div>
        <span className={`badge ${drone.status.toLowerCase()}`}>{drone.status}</span>
      </div>

      <div className="workflow-steps">
        {steps.map((step, index) => {
          const state = index < activeIndex ? "completed" : index === activeIndex ? "active" : "pending";
          return (
            <div className={`workflow-step ${state}`} key={step}>
              <span className="step-icon">
                {state === "completed" ? "✓" : state === "active" ? "●" : "○"}
              </span>
              <span>{step}</span>
              {index < steps.length - 1 && <span className="step-arrow">↓</span>}
            </div>
          );
        })}
      </div>

      {drone.status === "OFFLINE" && (
        <div className="warning">
          ⚠ Drone is offline. Temporal workflow can remain durable while the drone reconnects.
        </div>
      )}
    </section>
  );
}