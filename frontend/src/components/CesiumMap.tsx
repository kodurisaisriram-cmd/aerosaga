import { useEffect, useRef } from "react";
import {
  Cartesian2,
  Cartesian3,
  Color,
  HeightReference,
  ImageryLayer,
  LabelStyle,
  OpenStreetMapImageryProvider,
  ScreenSpaceEventHandler,
  ScreenSpaceEventType,
  VerticalOrigin,
  Viewer,
} from "cesium";
import type { Drone } from "../types/drone";

interface Props {
  drones: Drone[];
  selectedId: string | null;
  onSelect: (id: string) => void;
}

export default function CesiumMap({ drones, selectedId, onSelect }: Props) {
  const containerRef = useRef<HTMLDivElement>(null);
  const viewerRef = useRef<Viewer | null>(null);
  const handlersRef = useRef<any>(null);

  useEffect(() => {
    if (!containerRef.current) return;

    const viewer = new Viewer(containerRef.current, {
      baseLayer: new ImageryLayer(
        new OpenStreetMapImageryProvider({
          url: "https://tile.openstreetmap.org/",
        })
      ),
      baseLayerPicker: false,
      animation: false,
      timeline: false,
      geocoder: false,
      homeButton: true,
      sceneModePicker: false,
      navigationHelpButton: false,
      fullscreenButton: false,
      infoBox: false,
      selectionIndicator: false,
    });

    viewerRef.current = viewer;

    const handler = new ScreenSpaceEventHandler(viewer.scene.canvas);
    handler.setInputAction((movement: any) => {
      const picked = viewer.scene.pick(movement.position);
      if (picked?.id?.droneId) onSelect(picked.id.droneId);
    }, ScreenSpaceEventType.LEFT_CLICK);
    handlersRef.current = handler;

    viewer.camera.flyTo({
      destination: Cartesian3.fromDegrees(77.5946, 12.9716, 150000),
      duration: 1.5,
    });

    return () => {
      handler.destroy();
      viewer.destroy();
      viewerRef.current = null;
    };
  }, [onSelect]);

  useEffect(() => {
    const viewer = viewerRef.current;
    if (!viewer) return;

    viewer.entities.removeAll();

    drones.forEach((drone) => {
      const isSelected = drone.id === selectedId;
      const color =
        drone.status === "OFFLINE"
          ? Color.RED
          : drone.status === "RETURNING"
          ? Color.ORANGE
          : Color.CYAN;

      viewer.entities.add({
        id: drone.id,
        droneId: drone.id,
        position: Cartesian3.fromDegrees(drone.longitude, drone.latitude, drone.altitude),
        point: {
          pixelSize: isSelected ? 16 : 10,
          color,
          outlineColor: Color.WHITE,
          outlineWidth: 2,
          heightReference: HeightReference.NONE,
        },
        label: {
          text: drone.id,
          font: isSelected ? "bold 14px sans-serif" : "12px sans-serif",
          fillColor: Color.WHITE,
          style: LabelStyle.FILL_AND_OUTLINE,
          outlineColor: Color.BLACK,
          outlineWidth: 3,
          verticalOrigin: VerticalOrigin.BOTTOM,
          pixelOffset: new Cartesian2(0, -14),
          show: isSelected,
        },
      } as any);
    });
  }, [drones, selectedId]);

  return <div ref={containerRef} className="cesium-map" />;
}