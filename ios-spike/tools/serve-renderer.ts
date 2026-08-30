import { networkInterfaces } from "node:os";
import { join, normalize, resolve } from "node:path";

const PORT = Number(process.env.PORT ?? 8787);
const ROOT = resolve(import.meta.dir, "../../app/src/main/assets/mii_renderer");
const SHIM_PATH = resolve(import.meta.dir, "pp-bridge-shim.js");
const SHIM_URL = "/pp-bridge-shim.js";
const DEFAULT_MII =
  "BAXGigDvV8wSNID/cJl869TJwxYAAAAAAAAAAAAAAAAAAAAAAAAAAE0AaQBpAAAAAAAAAAAAAAAAAAAA" +
  "CAAAAAAAQAMDAQYEBgIKCAQEAgIMAAAAAP8AAAAACAQACgEAIf///0AABAACFAMTBBcNBAAKBAEJ//8A/wAAAP//";

const MIME: Record<string, string> = {
  html: "text/html; charset=utf-8",
  js: "application/javascript; charset=utf-8",
  mjs: "application/javascript; charset=utf-8",
  json: "application/json; charset=utf-8",
  css: "text/css; charset=utf-8",
  wasm: "application/wasm",
  dat: "application/octet-stream",
  glb: "model/gltf-binary",
  png: "image/png",
  jpg: "image/jpeg",
  svg: "image/svg+xml",
};

function mimeFor(path: string): string {
  return MIME[path.split(".").pop()?.toLowerCase() ?? ""] ?? "application/octet-stream";
}

function safeJoin(pathname: string): string | null {
  const relative = normalize(decodeURIComponent(pathname)).replace(/^[\\/]+/, "");
  if (relative.split(/[\\/]/).includes("..")) return null;
  return join(ROOT, relative);
}

async function indexWithShim(): Promise<Response> {
  const html = await Bun.file(join(ROOT, "index.html")).text();
  const injected = html.replace(
    /<script type="module"/,
    `<script src="${SHIM_URL}"></script>\n    <script type="module"`,
  );
  if (injected === html) {
    console.warn("! could not inject the bridge shim; serving index.html unchanged");
  }
  return new Response(injected, { headers: { "Content-Type": MIME.html, "Cache-Control": "no-store" } });
}

const server = Bun.serve({
  port: PORT,
  hostname: "0.0.0.0",
  async fetch(request) {
    const url = new URL(request.url);
    if (url.pathname === "/" ) {
      return Response.redirect(`/index.html?mii=${encodeURIComponent(DEFAULT_MII)}`, 302);
    }
    if (url.pathname === SHIM_URL) {
      return new Response(Bun.file(SHIM_PATH), {
        headers: { "Content-Type": MIME.js, "Cache-Control": "no-store" },
      });
    }
    if (url.pathname === "/index.html") return indexWithShim();

    const path = safeJoin(url.pathname);
    if (!path) return new Response("Not Found", { status: 404 });
    const file = Bun.file(path);
    if (!(await file.exists())) return new Response("Not Found", { status: 404 });
    return new Response(file, {
      headers: { "Content-Type": mimeFor(path), "Cache-Control": "no-store" },
    });
  },
});

const addresses = Object.values(networkInterfaces())
  .flat()
  .filter((entry) => entry && entry.family === "IPv4" && !entry.internal)
  .map((entry) => entry!.address);

console.log(`serving ${ROOT}`);
console.log(`  local   http://localhost:${server.port}/`);
for (const address of addresses) {
  console.log(`  network http://${address}:${server.port}/`);
}
console.log("\nOpen a network URL in Safari on the iPhone (same Wi-Fi).");
console.log("Expected: a Mii head renders, and the log overlay shows a 'ready' message.");
