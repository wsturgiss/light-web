// Serves light-web's build for local use, with a proxy so tools can reach the network.
//
//   node tool/serve.mjs [dir] [port] [--allow-host <name>]...
//     dir    default: build/dist/wasmJs/productionExecutable
//     port   default: 8765
//     --allow-host  a host the proxy may reach although it's on a private network (a music
//                   server on the LAN, say): a name, as tools use it, or an IP address, which
//                   allows any name that resolves to it (Plex reaches a LAN server through
//                   per-server *.plex.direct names; allowing the server's address covers them).
//
// Static files are served with no-cache, so a rebuild is what the next reload gets.
//
// /proxy?url=<http(s) URL> makes the request from here and hands the answer back to the page,
// which is what lets a tool reach sites that don't allow cross-origin requests (see
// lightweb.compat.WebHttp). GET, HEAD, POST, PUT, PATCH and DELETE, with the request's body. The
// tool's own request headers come as X-Proxy-H-<name> (some can't be set by a page under their
// own name, and this keeps them apart from the browser's); X-Proxy-User-Agent is the older form
// of X-Proxy-H-User-Agent. It answers 200 whatever the site said, with the site's status in
// X-Proxy-Status, and doesn't follow redirects: the tool sees them and follows them itself, as
// on the phone, each hop through here again. A failure comes back as X-Proxy-Error: dns, refused,
// cert, tls, timeout, blocked or unreachable.
//
// For one person on one machine. What keeps the proxy theirs:
// - It listens on 127.0.0.1, so other machines can't connect.
// - Only light-web's own pages can use it. Other sites open in the same browser can send
//   requests to localhost too, so /proxy wants an X-Lightweb-Proxy header, which a browser lets
//   another origin send only after asking (a preflight), and this server never says yes. Requests
//   the browser marks cross-site are refused outright.
// - Any Host but localhost is refused, so a site can't point its own name at 127.0.0.1 (DNS
//   rebinding) and become "same origin" with the proxy.
// - It won't fetch from private, loopback or link-local addresses (your router, NAS, other local
//   servers), judged by the address a name resolves to when connecting, not by the URL. Hosts
//   named with --allow-host are let through.
// - It passes back no cookies and none of the site's CORS headers.
// Hosting it anywhere else needs more: an allowlist of sites, rate and size limits. See the
// handoff's "Network" notes.
import { createServer } from "node:http";
import { request as httpRequest } from "node:http";
import { request as httpsRequest } from "node:https";
import { BlockList, isIP } from "node:net";
import { lookup as dnsLookup } from "node:dns";
import { readFile, stat } from "node:fs/promises";
import { extname, join, normalize, resolve } from "node:path";
import { createBrotliDecompress, createGunzip, createInflate } from "node:zlib";

const args = process.argv.slice(2);
const positional = [];
const allowedHosts = new Set();
for (let i = 0; i < args.length; i++) {
  if (args[i] === "--allow-host") allowedHosts.add(hostKey(args[++i] ?? ""));
  else positional.push(args[i]);
}
const root = resolve(positional[0] ?? "build/dist/wasmJs/productionExecutable");
const port = Number(positional[1] ?? 8765);
const localHosts = new Set([`127.0.0.1:${port}`, `localhost:${port}`, `[::1]:${port}`]);

const types = {
  ".html": "text/html; charset=utf-8", ".js": "text/javascript", ".mjs": "text/javascript",
  ".wasm": "application/wasm", ".json": "application/json", ".map": "application/json",
  ".png": "image/png", ".jpg": "image/jpeg", ".webp": "image/webp", ".svg": "image/svg+xml",
  ".ttf": "font/ttf", ".otf": "font/otf", ".xml": "application/xml", ".txt": "text/plain; charset=utf-8",
};

// Not passed back. The body is re-sent whole and already decompressed; cookies stay out of the
// page; and a site's CORS headers must never make a proxied answer readable to another origin.
const dropped = /^(content-encoding|content-length|transfer-encoding|connection|keep-alive|set-cookie|access-control-.*)$/i;

const TIMEOUT_MS = 30_000;
const MAX_BYTES = 256 * 1024 * 1024;
const MAX_REQUEST_BYTES = 16 * 1024 * 1024;
const methods = new Set(["GET", "HEAD", "POST", "PUT", "PATCH", "DELETE"]);
// Request headers a tool can't pass on: the connection's own, and the proxy's.
const notForwarded = /^(host|connection|keep-alive|transfer-encoding|content-length|upgrade|te|trailer|proxy-.*)$/i;

// Addresses the proxy won't connect to: everything that isn't the public internet.
const privateAddresses = new BlockList();
for (const [net, bits] of [
  ["0.0.0.0", 8], ["10.0.0.0", 8], ["100.64.0.0", 10], ["127.0.0.0", 8], ["169.254.0.0", 16],
  ["172.16.0.0", 12], ["192.0.0.0", 24], ["192.168.0.0", 16], ["198.18.0.0", 15], ["224.0.0.0", 4], ["240.0.0.0", 4],
]) privateAddresses.addSubnet(net, bits, "ipv4");
for (const [net, bits] of [["::", 128], ["::1", 128], ["fc00::", 7], ["fe80::", 10], ["ff00::", 8]]) {
  privateAddresses.addSubnet(net, bits, "ipv6");
}

function isPrivate(address) {
  // An IPv4 address written as IPv6 (::ffff:127.0.0.1) is judged as the IPv4 one.
  const mapped = /^::ffff:(\d+\.\d+\.\d+\.\d+)$/i.exec(address);
  if (mapped) address = mapped[1];
  const family = isIP(address);
  return family === 0 || privateAddresses.check(address, family === 4 ? "ipv4" : "ipv6");
}

/** A URL's hostname as --allow-host is compared with it: lower case, no IPv6 brackets. */
function hostKey(host) {
  return host.toLowerCase().replace(/^\[(.*)\]$/, "$1");
}

class Blocked extends Error {
  code = "LIGHTWEB_BLOCKED";
}

/**
 * dns.lookup for outgoing connections, refusing a name that resolves to a private address.
 * Checked here, where the connection gets its address, so a name can't resolve one way when
 * checked and another when connected.
 */
function publicLookup(hostname, options, callback) {
  dnsLookup(hostname, { ...options, all: true }, (err, addresses) => {
    if (err) return callback(err);
    const bad = addresses.find((a) => isPrivate(a.address) && !allowedHosts.has(hostKey(a.address)));
    if (bad) return callback(new Blocked(`${hostname} is ${bad.address}, a private address; allow it with --allow-host ${bad.address}`));
    if (options.all) callback(null, addresses);
    else callback(null, addresses[0].address, addresses[0].family);
  });
}

function failureKind(e) {
  if (e.name === "TimeoutError" || e.name === "AbortError") return "timeout";
  const code = e.cause?.code ?? e.code ?? "";
  if (code === "LIGHTWEB_BLOCKED") return "blocked";
  if (code === "ENOTFOUND" || code === "EAI_AGAIN") return "dns";
  if (code === "ECONNREFUSED" || code === "ECONNRESET" || code === "EHOSTUNREACH" || code === "ENETUNREACH") return "refused";
  if (/CERT|SELF_SIGNED|UNABLE_TO_VERIFY|ALTNAME/.test(code)) return "cert";
  if (/SSL|TLS|EPROTO/.test(code)) return "tls";
  return "unreachable";
}

/** One request, no redirects followed, the body decompressed. Resolves to { status, headers, body }. */
function send(method, target, headers, body) {
  const url = new URL(target);
  const host = hostKey(url.hostname);
  const allowed = allowedHosts.has(host);
  // An address in the URL is never looked up, so it's checked here instead.
  if (!allowed && isIP(host) && isPrivate(host)) {
    return Promise.reject(new Blocked(`${host} is a private address; allow it with --allow-host ${host}`));
  }
  const request = url.protocol === "https:" ? httpsRequest : httpRequest;
  return new Promise((done, fail) => {
    const req = request(url, {
      method,
      headers: { ...headers, "Accept-Encoding": "gzip, deflate, br" },
      lookup: allowed ? undefined : publicLookup,
      signal: AbortSignal.timeout(TIMEOUT_MS),
    }, (res) => {
      const encoding = (res.headers["content-encoding"] ?? "").toLowerCase();
      const stream = encoding === "gzip" ? res.pipe(createGunzip())
        : encoding === "deflate" ? res.pipe(createInflate())
        : encoding === "br" ? res.pipe(createBrotliDecompress())
        : res;
      const chunks = [];
      let size = 0;
      stream.on("data", (c) => {
        size += c.length;
        if (size > MAX_BYTES) stream.destroy(new Error(`larger than ${MAX_BYTES >> 20} MB`));
        else chunks.push(c);
      });
      stream.on("end", () => done({ status: res.statusCode, headers: res.headers, body: Buffer.concat(chunks) }));
      stream.on("error", fail);
      res.on("error", fail);
    });
    req.on("error", fail);
    req.end(body);
  });
}

/** A request's body, up to MAX_REQUEST_BYTES. */
function readBody(req) {
  return new Promise((done, fail) => {
    const chunks = [];
    let size = 0;
    req.on("data", (c) => {
      size += c.length;
      if (size > MAX_REQUEST_BYTES) { req.destroy(); fail(new Error("request body too large")); } else chunks.push(c);
    });
    req.on("end", () => done(Buffer.concat(chunks)));
    req.on("error", fail);
  });
}

/** A media element's own request, vouched for by the browser (see deploy/functions/proxy.js). */
function isMedia(req) {
  return (req.method === "GET" || req.method === "HEAD")
    && req.headers["sec-fetch-site"] === "same-origin"
    && ["audio", "video"].includes(req.headers["sec-fetch-dest"]);
}

/** Why a request for /proxy isn't from one of light-web's own pages, or null if it is. */
function foreign(req) {
  if (req.headers["x-lightweb-proxy"] !== "1") return "missing X-Lightweb-Proxy";
  const site = req.headers["sec-fetch-site"];
  if (site && site !== "same-origin") return `Sec-Fetch-Site: ${site}`;
  return null;
}

async function proxy(req, res, url) {
  if (isMedia(req)) return media(req, res, url);
  const why = foreign(req);
  if (why) {
    console.log(`proxy refused (${why}): ${req.headers.origin ?? req.headers.referer ?? "no origin"}`);
    return res.writeHead(403).end("light-web's proxy is for light-web's own pages");
  }
  if (url.searchParams.has("probe")) return res.writeHead(204, { "X-Lightweb-Proxy": "1" }).end();
  const target = url.searchParams.get("url") ?? "";
  if (!/^https?:\/\//i.test(target)) {
    return res.writeHead(400, { "X-Proxy-Error": "unreachable" }).end("only http(s) URLs");
  }
  if (!methods.has(req.method)) return res.writeHead(405).end();
  const headers = { Accept: "*/*" };
  if (req.headers["x-proxy-user-agent"]) headers["User-Agent"] = req.headers["x-proxy-user-agent"];
  for (const [k, v] of Object.entries(req.headers)) {
    const name = /^x-proxy-h-(.+)$/i.exec(k)?.[1];
    if (name && !notForwarded.test(name)) headers[name] = v;
  }
  try {
    const body = req.method === "GET" || req.method === "HEAD" ? undefined : await readBody(req);
    const r = await send(req.method, target, headers, body);
    const out = { "X-Proxy-Status": String(r.status), "Cache-Control": "no-store" };
    for (const [k, v] of Object.entries(r.headers)) if (!dropped.test(k)) out[k] = v;
    console.log(`proxy ${req.method} ${r.status} ${target} (${r.body.length} bytes)`);
    res.writeHead(200, out).end(r.body);
  } catch (e) {
    const kind = failureKind(e);
    console.log(`proxy ${kind} ${target}: ${e.cause?.code ?? e.code ?? ""} ${e.message}`);
    res.writeHead(502, { "X-Proxy-Error": kind }).end(String(e.cause?.message ?? e.message));
  }
}

/**
 * An audio or video element's request, which can't carry X-Lightweb-Proxy: its range is asked for
 * and the site's status and range headers come back as they are, so the element can seek.
 * Redirects are followed here, since the element can't see them through the proxy.
 */
async function media(req, res, url) {
  let target = url.searchParams.get("url") ?? "";
  if (!/^https?:\/\//i.test(target)) return res.writeHead(400).end();
  const headers = { Accept: "*/*" };
  if (req.headers.range) headers.Range = req.headers.range;
  try {
    let r;
    for (let hops = 0; ; hops++) {
      r = await send(req.method, target, headers);
      const location = r.headers.location;
      if (r.status < 300 || r.status >= 400 || !location || hops >= 5) break;
      target = new URL(location, target).href;
    }
    const out = { "Cache-Control": "no-store", "Content-Length": String(r.body.length) };
    for (const k of ["content-type", "content-range", "accept-ranges", "last-modified", "etag"]) if (r.headers[k]) out[k] = r.headers[k];
    console.log(`proxy media ${r.status} ${target} (${r.body.length} bytes)`);
    res.writeHead(r.status, out).end(req.method === "HEAD" ? undefined : r.body);
  } catch (e) {
    console.log(`proxy media ${failureKind(e)} ${target}: ${e.message}`);
    res.writeHead(502).end();
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
  if (!localHosts.has((req.headers.host ?? "").toLowerCase())) {
    console.log(`refused Host: ${req.headers.host}`);
    return res.writeHead(403).end("light-web answers only at localhost");
  }
  const url = new URL(req.url, "http://localhost");
  // Never OPTIONS: refusing every preflight is what keeps other origins from sending the proxy's
  // header (see the top of this file).
  if (url.pathname === "/proxy") return proxy(req, res, url);
  if (req.method !== "GET" && req.method !== "HEAD") return res.writeHead(405).end();
  return file(res, url.pathname);
}).listen(port, "127.0.0.1", () => {
  console.log(`light-web: http://localhost:${port}/ (serving ${root})`);
  if (allowedHosts.size) console.log(`proxy may also reach: ${[...allowedHosts].join(", ")}`);
});
