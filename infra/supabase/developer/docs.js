(() => {
  "use strict";
  const key = typeof window.POCKETPASS_DEVELOPER_KEY === "string" ? window.POCKETPASS_DEVELOPER_KEY.trim() : "";
  if (key) for (const node of document.querySelectorAll("[data-publishable-key]")) node.textContent = key;

  function revealExample() {
    let id;
    try { id = decodeURIComponent(location.hash.slice(1)); } catch { return; }
    const target = document.getElementById(id);
    if (!target || (id !== "examples" && !id.startsWith("example-"))) return;
    for (let node = target; node; node = node.parentElement) {
      if (node instanceof HTMLDetailsElement) node.open = true;
    }
    if (id === "examples") target.querySelector(".examples-library").open = true;
    requestAnimationFrame(() => target.scrollIntoView({ block: "start" }));
  }
  window.addEventListener("hashchange", revealExample);
  document.addEventListener("click", event => {
    const link = event.target.closest("a[href^='#example']");
    if (link && link.hash === location.hash) revealExample();
  });
  revealExample();
})();
