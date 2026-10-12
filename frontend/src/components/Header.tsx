interface Props {
  online: number;
  total: number;
}

export default function Header({ online, total }: Props) {
  return (
    <header className="topbar">
      <div>
        <div className="eyebrow">AUTONOMOUS DRONE MISSION CONTROL</div>
        <h1>AeroSaga.io</h1>
      </div>
      <div className="fleet-summary">
        <span className="live-dot" />
        <strong>{online}</strong> / {total} online
      </div>
    </header>
  );
}