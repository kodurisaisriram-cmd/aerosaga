
import { useEffect, useRef } from "react";
import {
  Cartesian2,
  Cartesian3,
  Color,
  ConstantPositionProperty,
  ConstantProperty,
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

export default function CesiumMap({
  drones,
  selectedId,
  onSelect,
}: Props) {
  const containerRef = useRef<HTMLDivElement>(null);
  const viewerRef = useRef<Viewer | null>(null);
  const onSelectRef = useRef(onSelect);

  useEffect(() => {
    onSelectRef.current = onSelect;
  }, [onSelect]);

  // Initialize the globe once.
  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;

    const viewer = new Viewer(container, {
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

    const handler = new ScreenSpaceEventHandler(
      viewer.scene.canvas
    );

    handler.setInputAction(
      (movement: { position: Cartesian2 }) => {
        const picked = viewer.scene.pick(movement.position);
        const pickedId = picked?.id?.id;

        if (typeof pickedId === "string") {
          onSelectRef.current(pickedId);
        }
      },
      ScreenSpaceEventType.LEFT_CLICK
    );

    viewer.camera.flyTo({
      destination: Cartesian3.fromDegrees(
        77.5946,
        12.9716,
        150000
      ),
      duration: 1.5,
    });

    return () => {
      handler.destroy();
      viewer.destroy();
      viewerRef.current = null;
    };
  }, []);

  // Create or update drone markers without clearing all entities.
  useEffect(() => {
    const viewer = viewerRef.current;
    if (!viewer || viewer.isDestroyed()) return;

    const activeIds = new Set(drones.map((drone) => drone.id));

    // Remove markers for drones no longer present.
    for (const entity of [...viewer.entities.values]) {
      if (!activeIds.has(entity.id)) {
        viewer.entities.remove(entity);
      }
    }

    drones.forEach((drone) => {
      const isSelected = drone.id === selectedId;

      const color =
        drone.status === "OFFLINE"
          ? Color.RED
          : drone.status === "RETURNING"
          ? Color.ORANGE
          : Color.CYAN;

      const position = Cartesian3.fromDegrees(
        drone.longitude,
        drone.latitude,
        drone.altitude
      );

      const existing = viewer.entities.getById(drone.id);

      if (existing) {
        existing.position = new ConstantPositionProperty(position);

        if (existing.point) {
          existing.point.pixelSize = new ConstantProperty(
            isSelected ? 16 : 10
          );
          existing.point.color = new ConstantProperty(color);
        }

        if (existing.label) {
          existing.label.text = new ConstantProperty(drone.id);
          existing.label.font = new ConstantProperty(
            isSelected
              ? "bold 14px sans-serif"
              : "12px sans-serif"
          );
          existing.label.show = new ConstantProperty(isSelected);
        }
      } else {
        viewer.entities.add({
          id: drone.id,
          position,
          point: {
            pixelSize: isSelected ? 16 : 10,
            color,
            outlineColor: Color.WHITE,
            outlineWidth: 2,
            heightReference: HeightReference.NONE,
          },
          label: {
            text: drone.id,
            font: isSelected
              ? "bold 14px sans-serif"
              : "12px sans-serif",
            fillColor: Color.WHITE,
            style: LabelStyle.FILL_AND_OUTLINE,
            outlineColor: Color.BLACK,
            outlineWidth: 3,
            verticalOrigin: VerticalOrigin.BOTTOM,
            pixelOffset: new Cartesian2(0, -14),
            show: isSelected,
          },
        });
      }
    });

    viewer.scene.requestRender();
  }, [drones, selectedId]);

  return <div ref={containerRef} className="cesium-map" />;
}
