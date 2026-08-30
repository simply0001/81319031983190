const overlay = document.createElement("div");
overlay.id = "pp-probe-log";
overlay.setAttribute(
  "style",
  "position:fixed;left:0;right:0;bottom:0;max-height:45%;overflow:auto;" +
    "background:rgba(12,12,24,0.86);color:#dfe6ff;font:12px/1.45 ui-monospace,Menlo,monospace;" +
    "padding:10px 12px;z-index:2147483647;white-space:pre-wrap;word-break:break-word;",
);
const append = (line) => {
  const row = document.createElement("div");
  row.textContent = line;
  overlay.appendChild(row);
  overlay.scrollTop = overlay.scrollHeight;
};

const ready = () => {
  document.body.appendChild(overlay);
  append("probe: harness attached");
  append("probe: userAgent " + navigator.userAgent);
  append("probe: WebAssembly " + (typeof WebAssembly === "object" ? "available" : "MISSING"));
};

if (document.readyState === "loading") {
  document.addEventListener("DOMContentLoaded", ready);
} else {
  ready();
}

window.PocketPassNative = {
  postMessage(payload) {
    let text = String(payload);
    if (text.length > 400) text = text.slice(0, 400) + "…(truncated)";
    append("renderer -> native: " + text);
  },
};

window.addEventListener("error", (event) => {
  append("ERROR: " + (event.message || event.error));
});

window.addEventListener("unhandledrejection", (event) => {
  append("REJECTED: " + event.reason);
});
