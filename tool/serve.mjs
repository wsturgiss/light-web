// Serves light-web's build for development, with a proxy so tools can reach the network.
//
//   node tool/serve.mjs [dir] [port]    (default: build/dist/wasmJs/productionExecutable, 8765)
//
// Static files are served with no-cache, so a rebuild is what the next reload gets.
//
// GET /proxy?url=<https URL> fetches the URL from here and hands it back to the page, which is
// what lets a tool reach sites that don't allow cross-origin requests (see java.net's stand-in,
// src/compat/java/net/Net.kt). It answers 200 whatever the site said, with the site's status in
// X-Proxy-Status, and doesn't follow redirects: the tool sees them and follows them itself, as
// on the phone. A failure to connect comes back as X-Proxy-Error: dns, refused, cert, tls,
// timeout or unreachable. X-Proxy-User-Agent becomes the request's User-Agent, which a page can't
// set itself.
//
// Listens on 127.0.0.1 only: a proxy reachable from the network would fetch anything for anyone.
import { createServer } from "node:http";
import { readFile, stat } from "node:fs/promises";
import { extname, join, normalize, resolve } from "node:path";

const root = resolve(process.argv[2] ?? "build/dist/wasmJs/productionExecutable");
const port = Number(process.argv[3] ?? 8765);

const types = {
  ".html": "text/html; charset=utf-8", ".js": "text/javascript", ".mjs": "text/javascript",
  ".wasm": "application/wasm", ".json": "application/json", ".map": "application/json",
  ".png": "image/png", ".jpg": "image/jpeg", ".webp": "image/webp", ".svg": "image/svg+xml",
  ".ttf": "font/ttf", ".otf": "font/otf", ".xml": "application/xml", ".txt": "text/plain; charset=utf-8",
};

// Not passed back: the body is re-sent whole and already decompressed.
const dropped = new Set(["content-encoding", "content-length", "transfer-encoding", "connection", "keep-alive", "set-cookie"]);

function failureKind(e) {
  if (e.name === "TimeoutError" || e.name === "AbortError") return "timeout";
  const code = e.cause?.code ?? e.code ?? "";
  if (code === "ENOTFOUND" || code === "EAI_AGAIN") return "dns";
  if (code === "ECONNREFUSED" || code === "ECONNRESET" || code === "EHOSTUNREACH" || code === "ENETUNREACH") return "refused";
  if (/CERT|SELF_SIGNED|UNABLE_TO_VERIFY|ALTNAME/.test(code)) return "cert";
  if (/SSL|TLS|EPROTO/.test(code)) return "tls";
  if (code === "UND_ERR_CONNECT_TIMEOUT") return "timeout";
  return "unreachable";
}

async function proxy(req, res, url) {
  if (url.searchParams.has("probe")) {
    res.writeHead(204, { "X-Lightweb-Proxy": "1" }).end();
    return;
  }
  const target = url.searchParams.get("url") ?? "";
  if (!/^https?:\/\//i.test(target)) {
    res.writeHead(400, { "X-Proxy-Error": "unreachable" }).end("only http(s) URLs");
    return;
  }
  const headers = { Accept: "*/*" };
  if (req.headers["x-proxy-user-agent"]) headers["User-Agent"] = req.headers["x-proxy-user-agent"];
  try {
    const r = await fetch(target, { redirect: "manual", headers, signal: AbortSignal.timeout(30_000) });
    const body = Buffer.from(await r.arrayBuffer());
    const out = { "X-Proxy-Status": String(r.status), "Cache-Control": "no-store" };
    for (const [k, v] of r.headers) if (!dropped.has(k)) out[k] = v;
    console.log(`proxy ${r.status} ${target} (${body.length} bytes)`);
    res.writeHead(200, out).end(body);
  } catch (e) {
    const kind = failureKind(e);
    console.log(`proxy ${kind} ${target}: ${e.cause?.code ?? e.message}`);
    res.writeHead(502, { "X-Proxy-Error": kind }).end(String(e.cause?.message ?? e.message));
  }
}

async function file(res, pathname) {
  let path = normalize(join(root, decodeURIComponent(pathname)));
  if (!path.startsWith(root)) return res.writeHead(403).end();
  try {
    if ((await stat(path)).isDirectory()) path = join(path, "index.html");
    const body = await readFile(path);
    res.writeHead(200, { "Content-Type": types[extname(path)] ?? "application/octet-stream", "Cache-Control": "no-cache" }).end(body);
  } catch {
    res.writeHead(404).end("not found");
  }
}

createServer((req, res) => {
  const url = new URL(req.url, "http://localhost");
  if (req.method !== "GET" && req.method !== "HEAD") return res.writeHead(405).end();
  if (url.pathname === "/proxy") return proxy(req, res, url);
  return file(res, url.pathname);
}).listen(port, "127.0.0.1", () => console.log(`light-web: http://127.0.0.1:${port}/ (serving ${root})`));
