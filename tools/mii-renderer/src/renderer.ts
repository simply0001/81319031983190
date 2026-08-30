import "../util/PrepareThree";
import CameraControls from "camera-controls";
import * as THREE from "three";
import Mii from "../class/MiiData";
import {
  CameraPosition,
  Mii3DScene,
  SetupType
} from "../class/3DScene";
import {
  createMiiRender,
  getAdditionalInfoFromMii,
  iconRenderer
} from "../util/IconRendering";
import { ViewType } from "../util/camera";
import {
  PocketPassBodyUpdate,
  PocketPassRenderPart
} from "./RendererTypes";
import {
  getPocketPassFFL,
  preparePocketPassFFL
} from "./RendererFFL";

type Command = {
  id?: string;
  type: string;
  canonicalBase64?: string;
  field?: string;
  fields?: Record<string, unknown>;
  value?: unknown;
  renderPart?: number;
  bodyUpdate?: number;
  camera?: "fullBody" | "head";
  transitionMillis?: number;
  size?: number;
  x?: number;
  y?: number;
};

type CameraName = "fullBody" | "head";

type CameraPreset = {
  name: CameraName;
  position: CameraPosition;
  target: THREE.Vector3;
  distance: number;
  panRadius: number;
  panY: number;
};

type CameraSnapshot = {
  target: THREE.Vector3;
  distance: number;
  azimuth: number;
  polar: number;
};

type VirtualViewport = {
  width: number;
  height: number;
  widescreen: boolean;
};

const DEFAULT_MII =
  "BAXGigDvV8wSNID/cJl869TJwxYAAAAAAAAAAAAAAAAAAAAAAAAAAE0AaQBpAAAAAAAAAAAAAAAAAAAACAAAAAAAQAMDAQYEBgIKCAQEAgIMAAAAAP8AAAAACAQACgEAIf///0AABAACFAMTBBcNBAAKBAEJ//8A/wAAAP//";
const root = document.getElementById("mii-render-root")!;
const initialCanonical =
  new URLSearchParams(location.search).get("mii") || DEFAULT_MII;

let mii = new Mii(initialCanonical);
let scene: Mii3DScene | null = null;
let ready = false;
let operation = Promise.resolve();
let cameraResetTimer: number | null = null;
let cameraAnimationFrame: number | null = null;
let cameraAnimationGeneration = 0;
let cameraListenersAttached = false;
let cameraInteractionLocked = false;
let activeCamera: CameraName = "head";
let activePreset: CameraPreset | null = null;
let activePanRadius = 0;
let activePanY = 0;
let orbitTargetX = 0;
let orbitTargetY = 0;
let orbitEngaged = false;
let orbitDragActive = false;
let orbitDragAzimuth = 0;
let orbitDragPolar = 0;
let orbitDragPointerId: number | null = null;
let orbitDragLastX = 0;
let orbitDragLastY = 0;
const orbitPointers = new Set<number>();

const FRONT_AZIMUTH = 0;
const LEVEL_POLAR = Math.PI / 2;
const POLAR_ORBIT_LIMIT = THREE.MathUtils.degToRad(25);
const MIN_POLAR = LEVEL_POLAR - POLAR_ORBIT_LIMIT;
const MAX_POLAR = LEVEL_POLAR + POLAR_ORBIT_LIMIT;
const CAMERA_RESET_DELAY_MS = 3000;
const CAMERA_RESET_DURATION_MS = 600;
const ORBIT_AZIMUTH_RANGE = THREE.MathUtils.degToRad(77);
const ORBIT_POLAR_UP_RANGE = THREE.MathUtils.degToRad(50);
const ORBIT_POLAR_DOWN_RANGE = THREE.MathUtils.degToRad(18);
const ORBIT_SMOOTHING_SECONDS = 0.15;
const ORBIT_SETTLE_EPSILON = 0.001;
const ORBIT_DRAG_PIXELS_PER_RADIAN = 360;
const TOP_DESIGN_WIDTH = 1920;
const LEGACY_RENDERER_LEFT = 513.85;
const LEGACY_RENDERER_WIDTH = 892.949;
const WHOLE_HEAD_DISTANCE = 54;
const FIGMA_RENDERER_HEIGHT = 892.949;
const PREVIOUS_HEAD_RENDERER_HEIGHT = 1080;
const EXTENDED_RENDERER_HEIGHT = 1267.469;
const LEGACY_RENDERER_CENTER_X =
  LEGACY_RENDERER_LEFT + LEGACY_RENDERER_WIDTH / 2;
const VIRTUAL_CANVAS_CENTER_OFFSET_X =
  LEGACY_RENDERER_CENTER_X - TOP_DESIGN_WIDTH / 2;
const LEGACY_PROJECTION_X_SCALE = 1 / 1.081;
const LEGACY_PROJECTION_UPWARD_PX = 92;
const HEAD_VERTICAL_OFFSET = 3.5;
const HEAD_PROJECTION_SCALE =
  PREVIOUS_HEAD_RENDERER_HEIGHT / FIGMA_RENDERER_HEIGHT;
const BODY_PROJECTION_SCALE =
  EXTENDED_RENDERER_HEIGHT / FIGMA_RENDERER_HEIGHT;
const FULL_BODY_DISTANCE_SCALE = 1.85;
const HEAD_ADDITIONAL_DOWNWARD_SCREEN_PX = 0;
// Ground line of mii_editor_ground_shadow on the editor top screen; keep in sync.
const GROUND_SCREEN_Y = 1027.252;
const RENDER_AXIS_SCREEN_Y =
  EXTENDED_RENDERER_HEIGHT / 2 - LEGACY_PROJECTION_UPWARD_PX;
const GROUND_BELOW_AXIS_PX = GROUND_SCREEN_Y - RENDER_AXIS_SCREEN_Y;
const PROJECTION_CALIBRATION = 1.0305;
const SOLE_WORLD_Y = 0.188;
const HEAD_BOX_OVERSHOOT_WORLD = 1.82;
const FIGURE_TOP_MARGIN_PX = 32;
const MAX_ZOOM_FACTOR = 2;
const MAX_ZOOM_OUT_FACTOR = 1.25;
const PAN_FRACTION_OF_HALF_VIEW = 0.55;

const APPEARANCE_FIELDS = new Set([
  "beardColor",
  "beardType",
  "build",
  "eyeAspect",
  "eyeColor",
  "eyeRotate",
  "eyeScale",
  "eyeType",
  "eyeX",
  "eyeY",
  "eyebrowAspect",
  "eyebrowColor",
  "eyebrowRotate",
  "eyebrowScale",
  "eyebrowType",
  "eyebrowX",
  "eyebrowY",
  "facelineColor",
  "facelineMake",
  "facelineType",
  "facelineWrinkle",
  "facePaintColor",
  "favoriteColor",
  "gender",
  "glassColor",
  "glassScale",
  "glassType",
  "glassY",
  "hairColor",
  "hairFlip",
  "hairType",
  "hatCommonColor",
  "hatFavoriteColor",
  "hatType",
  "height",
  "moleScale",
  "moleType",
  "moleX",
  "moleY",
  "mouthAspect",
  "mouthColor",
  "mouthScale",
  "mouthType",
  "mouthY",
  "mustacheScale",
  "mustacheType",
  "mustacheY",
  "noseScale",
  "noseType",
  "noseY"
]);
const BODY_SCALE_FIELDS = new Set(["height", "build"]);

function emit(value: Record<string, unknown>) {
  const message = JSON.stringify(value);
  const nativeBridge = (globalThis as any).PocketPassNative;
  if (nativeBridge && typeof nativeBridge.postMessage === "function") {
    nativeBridge.postMessage(message);
  }
}

function result(id: string | undefined, value?: unknown) {
  emit({ type: "result", id, ok: true, value });
}

function failure(id: string | undefined, error: unknown) {
  const message = error instanceof Error ? error.message : String(error);
  emit({ type: "result", id, ok: false, error: message });
}

function applyVirtualViewport(activeScene: Mii3DScene): VirtualViewport {
  const rootWidth =
    root.getBoundingClientRect().width || window.innerWidth || TOP_DESIGN_WIDTH;
  const designScale = rootWidth / TOP_DESIGN_WIDTH;
  const viewport = {
    width: rootWidth,
    height: EXTENDED_RENDERER_HEIGHT * designScale,
    widescreen: false
  };
  activeScene.getCamera().setViewOffset(
    viewport.width,
    viewport.height,
    0,
    LEGACY_PROJECTION_UPWARD_PX * designScale,
    viewport.width / LEGACY_PROJECTION_X_SCALE,
    viewport.height
  );
  const canvas = activeScene.getRendererElement();
  canvas.style.left = `${VIRTUAL_CANVAS_CENTER_OFFSET_X * designScale}px`;
  canvas.style.top = "0px";
  canvas.style.right = "auto";
  canvas.style.bottom = "auto";
  canvas.style.width = `${viewport.width}px`;
  canvas.style.height = `${viewport.height}px`;
  return viewport;
}

async function rebuild(
  renderPart = PocketPassRenderPart.Head,
  bodyUpdate = PocketPassBodyUpdate.None
) {
  const activeScene = scene!;
  const wasAtPreset =
    activePreset !== null && cameraMatchesPreset(activePreset);
  activeScene.mii = mii;
  if (renderPart === PocketPassRenderPart.Body) {
    await activeScene.updateBody(bodyUpdate);
    activeScene.animators.get("head_bone")?.(0, 0);
  } else {
    if (bodyUpdate !== PocketPassBodyUpdate.None) {
      await activeScene.updateBody(bodyUpdate);
    }
    await activeScene.updateMiiHead(renderPart);
  }
  activeScene.resize();
  refreshActivePresetAfterRebuild(wasAtPreset);
}

function cancelCameraMotion() {
  cameraAnimationGeneration += 1;
  if (cameraResetTimer !== null) {
    clearTimeout(cameraResetTimer);
    cameraResetTimer = null;
  }
  if (cameraAnimationFrame !== null) {
    cancelAnimationFrame(cameraAnimationFrame);
    cameraAnimationFrame = null;
  }
}

function easeInOutCubic(progress: number) {
  return progress < 0.5
    ? 4 * progress * progress * progress
    : 1 - Math.pow(-2 * progress + 2, 3) / 2;
}

function wrapPi(angle: number) {
  const tau = Math.PI * 2;
  return ((((angle + Math.PI) % tau) + tau) % tau) - Math.PI;
}

function unlockCameraConstraints(controls: CameraControls) {
  controls.minAzimuthAngle = -Infinity;
  controls.maxAzimuthAngle = Infinity;
  controls.minPolarAngle = 0;
  controls.maxPolarAngle = Math.PI;
  controls.minDistance = 0;
  controls.maxDistance = Infinity;
}

function configureCameraInput(controls: CameraControls) {
  controls.minAzimuthAngle = -Infinity;
  controls.maxAzimuthAngle = Infinity;
  controls.minPolarAngle = MIN_POLAR;
  controls.maxPolarAngle = MAX_POLAR;
  controls.azimuthRotateSpeed = 0.75;
  controls.polarRotateSpeed = 0.75;
  controls.dollySpeed = 0.75;
  controls.truckSpeed = 1;
  controls.draggingSmoothTime = 0.04;
  controls.dollyToCursor = false;
  controls.dragToOffset = false;
  controls.mouseButtons.left = CameraControls.ACTION.ROTATE;
  controls.mouseButtons.middle = CameraControls.ACTION.NONE;
  controls.mouseButtons.right = CameraControls.ACTION.NONE;
  controls.mouseButtons.wheel = CameraControls.ACTION.NONE;
  controls.touches.one = CameraControls.ACTION.NONE;
  controls.touches.two = CameraControls.ACTION.TOUCH_DOLLY_TRUCK;
  controls.touches.three = CameraControls.ACTION.NONE;
}

function cameraSnapshot(controls: CameraControls): CameraSnapshot {
  return {
    target: controls.getTarget(new THREE.Vector3()),
    distance: controls.distance,
    azimuth: wrapPi(controls.azimuthAngle),
    polar: controls.polarAngle
  };
}

function visibleHalfHeight(distance: number) {
  return Math.tan((scene!.getCamera().fov * Math.PI) / 360) * distance;
}

function worldOffsetForScreenPixels(distance: number, pixels: number) {
  return (
    (visibleHalfHeight(distance) * 2 * pixels) /
    EXTENDED_RENDERER_HEIGHT
  );
}

function tangentOfHalfFov() {
  return Math.tan((scene!.getCamera().fov * Math.PI) / 360);
}

function worldUnitsPerScreenPixel(distance: number) {
  const zoom = scene!.getCamera().zoom;
  return (
    (2 * tangentOfHalfFov() * distance) /
    (EXTENDED_RENDERER_HEIGHT * (zoom > 0 ? zoom : 1) * PROJECTION_CALIBRATION)
  );
}

function fullBodyDistance() {
  return (
    (38 + (mii.height / 127) * 2) *
    0.98 *
    FULL_BODY_DISTANCE_SCALE *
    BODY_PROJECTION_SCALE
  );
}

function groundedFullBodyFraming(focusY: number) {
  const figureWorldHeight =
    focusY * 2 - HEAD_BOX_OVERSHOOT_WORLD - SOLE_WORLD_Y;
  const pixelsPerWorldUnitAtUnitDistance = 1 / worldUnitsPerScreenPixel(1);
  const fitDistance =
    (figureWorldHeight * pixelsPerWorldUnitAtUnitDistance) /
    (GROUND_SCREEN_Y - FIGURE_TOP_MARGIN_PX);
  const distance = Math.max(fullBodyDistance(), fitDistance);
  return {
    distance,
    targetY:
      SOLE_WORLD_Y + GROUND_BELOW_AXIS_PX * worldUnitsPerScreenPixel(distance)
  };
}

function makeCameraPreset(name: CameraName): CameraPreset {
  const activeScene = scene!;
  const position =
    name === "head"
      ? CameraPosition.MiiHead
      : CameraPosition.MiiFullBody;
  const focus = activeScene.focusCamera(position, true, false, true);
  if (!focus) throw new Error("The Mii camera target is unavailable");

  if (name === "head") {
    const distance = WHOLE_HEAD_DISTANCE * HEAD_PROJECTION_SCALE;
    const target = new THREE.Vector3(
      focus.x,
      focus.y +
        HEAD_VERTICAL_OFFSET +
        worldOffsetForScreenPixels(
          distance,
          HEAD_ADDITIONAL_DOWNWARD_SCREEN_PX
        ),
      focus.z
    );
    const pan = visibleHalfHeight(distance) * PAN_FRACTION_OF_HALF_VIEW;
    return {
      name,
      position,
      target,
      distance,
      panRadius: pan,
      panY: pan
    };
  }

  const { distance, targetY } = groundedFullBodyFraming(focus.y);
  const target = new THREE.Vector3(focus.x, targetY, focus.z);
  const pan = visibleHalfHeight(distance) * PAN_FRACTION_OF_HALF_VIEW;
  return {
    name,
    position,
    target,
    distance,
    panRadius: pan,
    panY: pan
  };
}

function clampTargetToActiveBounds(controls: CameraControls) {
  const preset = activePreset;
  if (!preset) return;

  const target = controls.getTarget(new THREE.Vector3());
  let dx = target.x - preset.target.x;
  let dz = target.z - preset.target.z;
  const horizontalDistance = Math.hypot(dx, dz);
  if (horizontalDistance > activePanRadius && horizontalDistance > 0) {
    const scale = activePanRadius / horizontalDistance;
    dx *= scale;
    dz *= scale;
  }
  const y = THREE.MathUtils.clamp(
    target.y,
    preset.target.y - activePanY,
    preset.target.y + activePanY
  );
  const x = preset.target.x + dx;
  const z = preset.target.z + dz;
  if (
    Math.abs(x - target.x) > 0.0001 ||
    Math.abs(y - target.y) > 0.0001 ||
    Math.abs(z - target.z) > 0.0001
  ) {
    controls.moveTo(x, y, z, false);
    controls.update(0);
  }
}

function configureInteractionBounds(
  preset: CameraPreset,
  preserveCurrent = false
) {
  const controls = scene!.getControls();
  configureCameraInput(controls);
  const current = cameraSnapshot(controls);
  const dx = current.target.x - preset.target.x;
  const dz = current.target.z - preset.target.z;
  activePanRadius = preserveCurrent
    ? Math.max(preset.panRadius, Math.hypot(dx, dz))
    : preset.panRadius;
  activePanY = preserveCurrent
    ? Math.max(preset.panY, Math.abs(current.target.y - preset.target.y))
    : preset.panY;
  controls.minDistance = preset.distance / MAX_ZOOM_FACTOR;
  const maximumDistance = preset.distance * MAX_ZOOM_OUT_FACTOR;
  controls.maxDistance = preserveCurrent
    ? Math.max(maximumDistance, current.distance)
    : maximumDistance;
  clampTargetToActiveBounds(controls);
}

function setCameraInteractionEnabled(enabled: boolean) {
  if (!scene) return;
  const interactive =
    enabled && !cameraInteractionLocked && activeCamera === "head";
  scene.getControls().enabled = interactive;
  scene
    .getRendererElement()
    .classList.toggle("interactive", interactive);
}

function applyPresetFrame(
  controls: CameraControls,
  start: CameraSnapshot,
  preset: CameraPreset,
  eased: number
) {
  controls.moveTo(
    THREE.MathUtils.lerp(start.target.x, preset.target.x, eased),
    THREE.MathUtils.lerp(start.target.y, preset.target.y, eased),
    THREE.MathUtils.lerp(start.target.z, preset.target.z, eased),
    false
  );
  controls.dollyTo(
    THREE.MathUtils.lerp(start.distance, preset.distance, eased),
    false
  );
  controls.rotateTo(
    THREE.MathUtils.lerp(start.azimuth, FRONT_AZIMUTH, eased),
    THREE.MathUtils.lerp(start.polar, LEVEL_POLAR, eased),
    false
  );
  controls.update(0);
}

function animateCameraToPreset(
  preset: CameraPreset,
  durationMillis: number
) {
  const activeScene = scene!;
  const controls = activeScene.getControls();
  activeCamera = preset.name;
  activePreset = preset;

  cancelCameraMotion();
  controls.cancel();
  controls.stop();
  cancelCameraMotion();
  unlockCameraConstraints(controls);
  configureCameraInput(controls);
  setCameraInteractionEnabled(true);
  const start = cameraSnapshot(controls);

  if (durationMillis <= 0) {
    applyPresetFrame(controls, start, preset, 1);
    configureInteractionBounds(preset);
    setCameraInteractionEnabled(true);
    return;
  }

  const generation = ++cameraAnimationGeneration;
  const startedAt = performance.now();
  const advance = (timestamp: number) => {
    if (
      generation !== cameraAnimationGeneration ||
      cameraInteractionLocked
    ) {
      return;
    }
    const progress = Math.min(
      1,
      (timestamp - startedAt) / durationMillis
    );
    applyPresetFrame(
      controls,
      start,
      preset,
      easeInOutCubic(progress)
    );
    if (progress < 1) {
      cameraAnimationFrame = requestAnimationFrame(advance);
    } else {
      cameraAnimationFrame = null;
      applyPresetFrame(controls, start, preset, 1);
      configureInteractionBounds(preset);
      setCameraInteractionEnabled(true);
    }
  };
  cameraAnimationFrame = requestAnimationFrame(advance);
}

function scheduleCameraReset() {
  cancelCameraMotion();
  cameraResetTimer = window.setTimeout(() => {
    cameraResetTimer = null;
    if (!cameraInteractionLocked && activePreset) {
      animateCameraToPreset(activePreset, CAMERA_RESET_DURATION_MS);
    }
  }, CAMERA_RESET_DELAY_MS);
}

function attachCameraListeners() {
  if (!scene || cameraListenersAttached) return;

  const controls = scene.getControls();
  controls.addEventListener("controlstart", () => {
    const interruptedAnimation = cameraAnimationFrame !== null;
    cancelCameraMotion();
    if (activePreset) {
      configureInteractionBounds(activePreset, interruptedAnimation);
    }
  });
  controls.addEventListener("control", () => {
    if (!cameraInteractionLocked && activePreset) {
      clampTargetToActiveBounds(controls);
    }
  });
  controls.addEventListener("controlend", () => {
    if (cameraInteractionLocked || !activePreset) return;
    controls.stop();
    clampTargetToActiveBounds(controls);
    scheduleCameraReset();
  });
  cameraListenersAttached = true;
}

function cameraMatchesPreset(preset: CameraPreset) {
  const controls = scene!.getControls();
  const current = cameraSnapshot(controls);
  return (
    Math.abs(current.azimuth) < 0.0001 &&
    Math.abs(current.polar - LEVEL_POLAR) < 0.0001 &&
    Math.abs(current.distance - preset.distance) < 0.0001 &&
    current.target.distanceToSquared(preset.target) < 0.0001
  );
}

function orbitFocusPoint(preset: CameraPreset): THREE.Vector3 {
  const head = scene?.getScene().getObjectByName("MiiHead");
  if (head) {
    return head.getWorldPosition(new THREE.Vector3());
  }
  return preset.target;
}

function attachOrbitDragListeners() {
  const element = scene!.getRendererElement();
  element.addEventListener("pointerdown", (event: PointerEvent) => {
    if (!ready || cameraInteractionLocked || activeCamera !== "head") return;
    orbitPointers.add(event.pointerId);
    if (orbitPointers.size === 1) {
      const controls = scene?.getControls();
      orbitDragPointerId = event.pointerId;
      orbitDragActive = true;
      orbitDragAzimuth = wrapPi(
        (controls?.azimuthAngle ?? FRONT_AZIMUTH) - FRONT_AZIMUTH
      );
      orbitDragPolar = (controls?.polarAngle ?? LEVEL_POLAR) - LEVEL_POLAR;
      orbitDragLastX = event.clientX;
      orbitDragLastY = event.clientY;
    }
  });
  window.addEventListener("pointermove", (event: PointerEvent) => {
    if (!orbitDragActive || event.pointerId !== orbitDragPointerId) return;
    const dx = event.clientX - orbitDragLastX;
    const dy = event.clientY - orbitDragLastY;
    orbitDragLastX = event.clientX;
    orbitDragLastY = event.clientY;
    if (orbitPointers.size !== 1) return;
    orbitDragAzimuth = THREE.MathUtils.clamp(
      orbitDragAzimuth - dx / ORBIT_DRAG_PIXELS_PER_RADIAN,
      -ORBIT_AZIMUTH_RANGE,
      ORBIT_AZIMUTH_RANGE
    );
    orbitDragPolar = THREE.MathUtils.clamp(
      orbitDragPolar + dy / ORBIT_DRAG_PIXELS_PER_RADIAN,
      -ORBIT_POLAR_UP_RANGE,
      ORBIT_POLAR_DOWN_RANGE
    );
  });
  const endOrbitPointer = (event: PointerEvent) => {
    orbitPointers.delete(event.pointerId);
    if (orbitPointers.size === 0) {
      orbitDragActive = false;
      orbitDragPointerId = null;
    }
  };
  window.addEventListener("pointerup", endOrbitPointer);
  window.addEventListener("pointercancel", endOrbitPointer);
}

function runOrbitAnimator(delta: number) {
  if (!ready || !scene || cameraInteractionLocked) return;
  const preset = activePreset;
  if (!preset) return;
  if (preset.name !== "head") {
    orbitEngaged = false;
    return;
  }

  const controls = scene.getControls();
  const stickDeflected =
    Math.abs(orbitTargetX) > 0.0001 || Math.abs(orbitTargetY) > 0.0001;
  const deflected = orbitDragActive || stickDeflected;
  if (!orbitEngaged) {
    if (!deflected) return;
    orbitEngaged = true;
  }
  const touchGesture =
    controls.currentAction !== CameraControls.ACTION.NONE;
  if (!deflected && touchGesture) {
    orbitEngaged = false;
    return;
  }
  if (cameraResetTimer !== null || cameraAnimationFrame !== null) {
    cancelCameraMotion();
  }

  const targetAzimuth = orbitDragActive
    ? FRONT_AZIMUTH + orbitDragAzimuth
    : FRONT_AZIMUTH - orbitTargetX * ORBIT_AZIMUTH_RANGE;
  const targetPolar = orbitDragActive
    ? LEVEL_POLAR + orbitDragPolar
    : LEVEL_POLAR +
      (orbitTargetY > 0
        ? orbitTargetY * ORBIT_POLAR_DOWN_RANGE
        : orbitTargetY * ORBIT_POLAR_UP_RANGE);
  const smoothing =
    1 - Math.exp(-Math.max(delta, 0) / ORBIT_SMOOTHING_SECONDS);
  const azimuth =
    controls.azimuthAngle +
    smoothing * wrapPi(targetAzimuth - controls.azimuthAngle);
  const polar =
    controls.polarAngle + smoothing * (targetPolar - controls.polarAngle);
  controls.azimuthAngle = azimuth;
  controls.polarAngle = polar;
  if (!touchGesture) {
    const focusPoint = deflected ? orbitFocusPoint(preset) : preset.target;
    const target = controls.getTarget(new THREE.Vector3());
    const nextTargetY = THREE.MathUtils.lerp(target.y, focusPoint.y, smoothing);
    controls.moveTo(
      THREE.MathUtils.lerp(target.x, focusPoint.x, smoothing),
      nextTargetY,
      THREE.MathUtils.lerp(target.z, focusPoint.z, smoothing),
      false
    );
    controls.setFocalOffset(0, nextTargetY - preset.target.y, 0, false);
    controls.dollyTo(
      THREE.MathUtils.lerp(controls.distance, preset.distance, smoothing),
      false
    );
  }
  controls.update(0);

  if (
    !deflected &&
    Math.abs(wrapPi(azimuth - FRONT_AZIMUTH)) < ORBIT_SETTLE_EPSILON &&
    Math.abs(polar - LEVEL_POLAR) < ORBIT_SETTLE_EPSILON
  ) {
    controls.azimuthAngle = FRONT_AZIMUTH;
    controls.polarAngle = LEVEL_POLAR;
    controls.moveTo(preset.target.x, preset.target.y, preset.target.z, false);
    controls.setFocalOffset(0, 0, 0, false);
    controls.update(0);
    orbitEngaged = false;
    configureInteractionBounds(preset);
    setCameraInteractionEnabled(true);
    if (!cameraMatchesPreset(preset)) {
      scheduleCameraReset();
    }
  }
}

function refreshActivePresetAfterRebuild(wasAtPreset: boolean) {
  if (!scene || !activePreset) return;
  const updated = makeCameraPreset(activeCamera);
  activePreset = updated;
  if (wasAtPreset && cameraAnimationFrame === null) {
    animateCameraToPreset(updated, 0);
  } else if (cameraAnimationFrame === null) {
    configureInteractionBounds(updated, true);
  }
}

function setCamera(camera: CameraName, transitionMillis = 0) {
  const duration = Math.max(0, Math.min(2000, Math.round(transitionMillis)));
  const preset = makeCameraPreset(camera);
  attachCameraListeners();
  animateCameraToPreset(preset, duration);
}

function base64FromBytes(bytes: Uint8Array) {
  let text = "";
  const chunk = 0x8000;
  for (let index = 0; index < bytes.length; index += chunk) {
    text += String.fromCharCode(...bytes.subarray(index, index + chunk));
  }
  return btoa(text);
}

async function whiteBackgroundPortraitBase64(blob: Blob, size: number) {
  const bitmap = await createImageBitmap(blob);
  try {
    const output = document.createElement("canvas");
    output.width = size;
    output.height = size;
    const context = output.getContext("2d", { alpha: false });
    if (!context) throw new Error("Portrait canvas is unavailable");
    context.fillStyle = "#FFFFFF";
    context.fillRect(0, 0, size, size);
    context.drawImage(bitmap, 0, 0, size, size);
    const base64 = output.toDataURL("image/png").split(",", 2)[1];
    if (!base64) throw new Error("Portrait export produced no data");
    return base64;
  } finally {
    bitmap.close();
  }
}

async function capturePortrait(id: string | undefined, requestedSize?: number) {
  const activeScene = scene!;
  const size = Math.max(128, Math.min(1024, Math.round(requestedSize || 512)));
  const controls = activeScene.getControls();
  const animationState = Array.from(activeScene.anim.values()).map((action) => ({
    action,
    paused: action.paused,
    time: action.time
  }));

  try {
    cameraInteractionLocked = true;
    cancelCameraMotion();
    controls.cancel();
    controls.stop();
    setCameraInteractionEnabled(false);
    for (const state of animationState) {
      state.action.paused = true;
      state.action.time = 0;
    }
    activeScene.mixer?.setTime(0);
    activeScene.animators.get("head_bone")?.(0, 0);

    const portrait = await createMiiRender({
      data: mii.export("studioData"),
      type: ViewType.Face,
      expression: 0,
      module: getPocketPassFFL(),
      renderer: iconRenderer(),
      texResolution: Math.min(1024, Math.max(256, size)),
      additionalInfo: getAdditionalInfoFromMii(mii),
      drawBody: true,
      size
    });
    if (portrait.type !== "blob" || !(portrait.result instanceof Blob)) {
      throw new Error("Dedicated face renderer produced an invalid portrait");
    }
    const base64 = await whiteBackgroundPortraitBase64(
      portrait.result,
      size
    );

    const chunkSize = 32 * 1024;
    const total = Math.ceil(base64.length / chunkSize);
    emit({ type: "capture-start", id, total, size, mimeType: "image/png" });
    for (let index = 0; index < total; index++) {
      emit({
        type: "capture-chunk",
        id,
        index,
        total,
        data: base64.slice(index * chunkSize, (index + 1) * chunkSize)
      });
    }
    emit({ type: "capture-complete", id, total });
  } finally {
    for (const state of animationState) {
      state.action.time = state.time;
      state.action.paused = state.paused;
    }
    cameraInteractionLocked = false;
    setCameraInteractionEnabled(true);
  }
}

async function execute(command: Command) {
  if (!ready || !scene) throw new Error("Mii renderer is not ready");
  switch (command.type) {
    case "setMii": {
      if (!command.canonicalBase64) throw new Error("Missing canonical Mii data");
      mii = new Mii(command.canonicalBase64);
      await rebuild(
        PocketPassRenderPart.Head,
        PocketPassBodyUpdate.ClothingUpdate
      );
      setCamera(command.camera === "fullBody" ? "fullBody" : "head", 0);
      result(command.id, base64FromBytes(mii.export("miic")));
      return;
    }
    case "updateField": {
      const field = command.field || "";
      if (
        !field ||
        field === "__proto__" ||
        field === "prototype" ||
        field === "constructor" ||
        !Object.prototype.hasOwnProperty.call(mii, field)
      ) {
        throw new Error("Unknown Mii field");
      }
      if (
        typeof command.value !== "number" &&
        typeof command.value !== "string" &&
        typeof command.value !== "boolean"
      ) {
        throw new Error("Unsupported Mii field value");
      }
      (mii as any)[field] = command.value;
      await rebuild(command.renderPart, command.bodyUpdate);
      result(command.id);
      return;
    }
    case "applyAppearance": {
      const fields = command.fields;
      if (
        !fields ||
        typeof fields !== "object" ||
        Array.isArray(fields) ||
        Object.getPrototypeOf(fields) !== Object.prototype
      ) {
        throw new Error("Missing Mii appearance fields");
      }
      const entries = Object.entries(fields);
      if (entries.length === 0 || entries.length > APPEARANCE_FIELDS.size) {
        throw new Error("Invalid Mii appearance field count");
      }

      const candidate = new Mii(mii.export("miic"));
      let bodyScaleOnly = true;
      for (const [field, value] of entries) {
        if (
          !APPEARANCE_FIELDS.has(field) ||
          !Object.prototype.hasOwnProperty.call(candidate, field) ||
          typeof value !== "number" ||
          !Number.isSafeInteger(value)
        ) {
          throw new Error("Invalid Mii appearance field");
        }
        if (value !== (mii as any)[field] && !BODY_SCALE_FIELDS.has(field)) {
          bodyScaleOnly = false;
        }
        (candidate as any)[field] = value;
      }
      const verification = candidate.verify();
      if (!verification.valid) {
        throw new Error("Invalid Mii appearance values");
      }

      mii = candidate;
      await rebuild(
        bodyScaleOnly
          ? PocketPassRenderPart.Body
          : PocketPassRenderPart.Head,
        PocketPassBodyUpdate.ClothingUpdate
      );
      result(command.id, base64FromBytes(mii.export("miic")));
      return;
    }
    case "setCamera":
      setCamera(
        command.camera === "fullBody" ? "fullBody" : "head",
        command.transitionMillis ?? 0
      );
      result(command.id);
      return;
    case "export":
      result(command.id, base64FromBytes(mii.export("miic")));
      return;
    case "capturePortrait":
      await capturePortrait(command.id, command.size);
      return;
    case "ping":
      result(command.id, "pong");
      return;
    default:
      throw new Error("Unknown Mii renderer command");
  }
}

(globalThis as any).PocketPassMiiRenderer = {
  receiveBase64(payload: string) {
    let command: Command;
    try {
      const bytes = Uint8Array.from(atob(payload), (character) =>
        character.charCodeAt(0)
      );
      command = JSON.parse(new TextDecoder().decode(bytes));
    } catch (error) {
      emit({ type: "protocol-error", error: String(error) });
      return;
    }
    if (command.type === "setOrbit") {
      const x = Number(command.x);
      const y = Number(command.y);
      orbitTargetX = Number.isFinite(x) ? THREE.MathUtils.clamp(x, -1, 1) : 0;
      orbitTargetY = Number.isFinite(y) ? THREE.MathUtils.clamp(y, -1, 1) : 0;
      return;
    }
    operation = operation
      .then(() => execute(command))
      .catch((error) => failure(command.id, error));
  }
};

async function boot() {
  emit({ type: "state", state: "loading" });
  try {
    await preparePocketPassFFL();
    const activeScene = new Mii3DScene(
      mii,
      root,
      SetupType.Normal,
      undefined,
      false
    );
    activeScene.resizeSizeProvider = () => applyVirtualViewport(activeScene);
    scene = activeScene;
    await activeScene.init();
    root.appendChild(activeScene.getRendererElement());
    activeScene.getRendererElement().classList.add("ready");
    activeScene.getRendererElement().style.opacity = "1";
    activeScene.getRenderer().setClearAlpha(0);
    await activeScene.updateMiiHead(PocketPassRenderPart.Head);
    activeScene.cameraPan = true;
    activeScene.focusCameraUpdate();
    const IDLE_TIME_SCALE = 0.3;
    activeScene.animators.set("pocketpass-idle", () => {
      const anim = scene?.anim;
      if (!anim) {
        return;
      }
      for (const action of anim.values()) {
        if (action.timeScale === 0) {
          action.timeScale = IDLE_TIME_SCALE;
        }
      }
    });
    activeScene.animators.set("pocketpass-orbit", (_time, delta) => {
      runOrbitAnimator(delta);
    });
    attachOrbitDragListeners();
    setCamera("head", 0);
    activeScene.resize();
    ready = true;
    emit({
      type: "state",
      state: "ready",
      canonicalBase64: base64FromBytes(mii.export("miic"))
    });
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    emit({ type: "state", state: "error", error: message });
  }
}

boot();
