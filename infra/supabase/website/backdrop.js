(() => {
  "use strict";

  const canvas = document.getElementById("backdrop");
  if (!canvas || !canvas.getContext) return;
  const ctx = canvas.getContext("2d", { alpha: false });
  if (!ctx) return;

  const DESIGN_WIDTH = 1240;
  const HOLD_FRACTION = 0.42;
  const TRIANGLE_ASPECT = 1.155;
  const ROW_PITCH = 70;
  const FIRST_ROW_CENTER = 8.75;
  const ROW_CYCLE_MS = 6400;
  const CELL_PERIOD = 162 / 2;
  const FIRST_CENTER = 29 / 2;
  const FRAME_INTERVAL_MS = 0;
  const BANDS = [
    [0, 35], [93, 177], [235, 320], [373, 462], [510, 604], [648, 747], [790, 889], [922, 1031],
    [1065, 1174], [1202, 1316], [1340, 1458], [1477, 1601], [1620, 1743], [1757, 1886], [1894, 2023], [2032, 2160],
  ].map(([top, bottom]) => ({ y: (top + bottom) / 4, h: (bottom - top) / 2 }));
  const DEPTH_START = BANDS[0].y;
  const DEPTH_END = BANDS[BANDS.length - 1].y;
  const FALLBACK = { top: "#e4f4ee", bottom: "#bfe9cf", triangle: "rgba(29, 89, 107, 0.055)" };

  const clamp01 = (value) => Math.min(1, Math.max(0, value));
  const lerp = (a, b, t) => a + (b - a) * t;

  const readTheme = () => {
    const style = getComputedStyle(document.documentElement);
    const read = (name, fallback) => style.getPropertyValue(name).trim() || fallback;
    return {
      top: read("--bg-0", FALLBACK.top),
      bottom: read("--bg-1", FALLBACK.bottom),
      triangle: read("--tri", FALLBACK.triangle),
    };
  };

  const envelopeAt = (y) => {
    let h = BANDS[0].h;
    for (let i = 1; i < BANDS.length; i++) {
      const y0 = BANDS[i - 1].y;
      const y1 = BANDS[i].y;
      if (y >= y0) {
        const f = clamp01((y - y0) / Math.max(y1 - y0, 0.0001));
        h = lerp(BANDS[i - 1].h, BANDS[i].h, f);
      }
    }
    return h;
  };

  let theme = readTheme();
  let width = 0;
  let height = 0;
  let dpr = 1;

  const resize = () => {
    dpr = Math.min(window.devicePixelRatio || 1, 2);
    width = Math.max(1, Math.round(window.innerWidth * dpr));
    height = Math.max(1, Math.round(window.innerHeight * dpr));
    if (canvas.width !== width) canvas.width = width;
    if (canvas.height !== height) canvas.height = height;
  };

  const draw = (phase) => {
    if (width === 0 || height === 0) return;
    ctx.setTransform(1, 0, 0, 1, 0, 0);
    const gradient = ctx.createLinearGradient(0, 0, 0, height);
    gradient.addColorStop(0, theme.top);
    gradient.addColorStop(HOLD_FRACTION, theme.top);
    gradient.addColorStop(1, theme.bottom);
    ctx.fillStyle = gradient;
    ctx.fillRect(0, 0, width, height);

    const scale = width / DESIGN_WIDTH;
    const designHeight = height / scale;
    ctx.setTransform(scale, 0, 0, scale, 0, 0);

    const travel = phase * ROW_PITCH;
    const firstRow = Math.floor((-ROW_PITCH + travel - FIRST_ROW_CENTER) / ROW_PITCH + 0.5) - 1;
    const lastRow = Math.floor((designHeight + ROW_PITCH + travel - FIRST_ROW_CENTER) / ROW_PITCH + 0.5) + 1;
    const firstColumn = Math.floor(-FIRST_CENTER / CELL_PERIOD) - 1;
    const lastColumn = Math.ceil((DESIGN_WIDTH - FIRST_CENTER) / CELL_PERIOD) + 1;

    ctx.fillStyle = theme.triangle;
    ctx.beginPath();
    for (let row = firstRow; row <= lastRow; row++) {
      const centerY = FIRST_ROW_CENTER + row * ROW_PITCH - travel;
      const envelope = envelopeAt(centerY);
      const depth = clamp01((centerY - DEPTH_START) / Math.max(DEPTH_END - DEPTH_START, 0.0001));
      const triangleHeight = envelope * (0.68 + 0.32 * depth);
      const halfWidth = triangleHeight * TRIANGLE_ASPECT * 0.5;
      const top = centerY - envelope * 0.5;
      const bottom = centerY + envelope * 0.5;
      if (bottom < 0 || top > designHeight) continue;

      for (let column = firstColumn; column <= lastColumn; column++) {
        const downX = FIRST_CENTER + column * CELL_PERIOD;
        ctx.moveTo(downX - halfWidth, top);
        ctx.lineTo(downX + halfWidth, top);
        ctx.lineTo(downX, top + triangleHeight);
        ctx.closePath();
        const upX = downX + CELL_PERIOD * 0.5;
        ctx.moveTo(upX - halfWidth, bottom);
        ctx.lineTo(upX + halfWidth, bottom);
        ctx.lineTo(upX, bottom - triangleHeight);
        ctx.closePath();
      }
    }
    ctx.fill();
  };

  const reducedMotion = window.matchMedia ? window.matchMedia("(prefers-reduced-motion: reduce)") : null;
  const darkScheme = window.matchMedia ? window.matchMedia("(prefers-color-scheme: dark)") : null;
  let lastFrame = 0;
  let frameHandle = 0;

  const tick = (now) => {
    frameHandle = 0;
    if (document.hidden) return;
    if (now - lastFrame >= FRAME_INTERVAL_MS) {
      lastFrame = now;
      draw((now % ROW_CYCLE_MS) / ROW_CYCLE_MS);
    }
    if (!(reducedMotion && reducedMotion.matches)) frameHandle = window.requestAnimationFrame(tick);
  };

  const start = () => {
    theme = readTheme();
    resize();
    if (frameHandle) window.cancelAnimationFrame(frameHandle);
    lastFrame = 0;
    frameHandle = window.requestAnimationFrame(tick);
  };

  window.addEventListener("resize", start);
  document.addEventListener("visibilitychange", () => { if (!document.hidden) start(); });
  if (reducedMotion && reducedMotion.addEventListener) reducedMotion.addEventListener("change", start);
  if (darkScheme && darkScheme.addEventListener) darkScheme.addEventListener("change", start);
  start();
})();
