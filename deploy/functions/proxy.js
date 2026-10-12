// light-web's relay on Cloudflare Pages: the hosted form of tool/serve.mjs's /proxy, with the
// same protocol (see that file and lightweb.compat.WebHttp), so the page needs no change.
//
// Hosted, it's reachable by anyone, so it's narrower than the local one:
// - It fetches only from the sites in ALLOWED_HOSTS: public sites the demo's tools use.
//   Visitors' own servers are out of scope; the one exception is Will's Plex server, whose token
//   passes through here (never logged).
// - Only light-web's own pages can use it: the same X-Lightweb-Proxy header and Sec-Fetch-Site
//   checks as locally, and no OPTIONS answer, so other origins can't send that header.
// - Bodies stream through (nothing buffered) with a size cap; no cookies, no CORS headers back.
// - Audio and video elements can't send that header, so a media request is taken instead when
//   the browser itself vouches for it: Sec-Fetch-Site same-origin and Sec-Fetch-Dest audio or
//   video, headers a page can't set. That's the audio engine playing a stream the browser can't
//   reach directly (WebAudioEngine). It gets the site's own status and range headers, and the
//   body passes straight through, costing no CPU time however long the track.
// Rate limits are a Cloudflare rule on /proxy, set in the dashboard (see deploy/README.md).

const ALLOWED_HOSTS = new Set([
  "www.gutenberg.org", "gutenberg.org", // Reader: catalogue and downloads
  "standardebooks.org",                // Reader
  "demo.navidrome.org",                // amp: the demo music server
  "lrclib.net",                        // amp: lyrics
  "plex.tv",                           // amp: linking Plex, finding servers
]);

// amp: Will's own Plex server, by the ID in its plex.direct names (the "*.<id>.plex.direct" its
// certificate covers). Other people's servers stay out.
const ALLOWED_SUFFIXES = [".b8bcfdfc1fa94089836692c05a511aad.plex.direct"];
const allowed = (host) => ALLOWED_HOSTS.has(host) || ALLOWED_SUFFIXES.some((s) => host.endsWith(s));

const MAX_BYTES = 64 * 1024 * 1024;
const MAX_REQUEST_BYTES = 1024 * 1024;
const TIMEOUT_MS = 30_000;
const methods = new Set(["GET", "HEAD", "POST", "PUT", "PATCH", "DELETE"]);
const notForwarded = /^(host|connection|keep-alive|transfer-encoding|content-length|upgrade|te|trailer|cookie|proxy-.*|cf-.*|x-forwarded-.*)$/i;
// The body arrives decompressed, so the length and encoding headers no longer describe it.
const dropped = /^(content-encoding|content-length|transfer-encoding|connection|keep-alive|set-cookie|access-control-.*)$/i;

const fail = (status, kind, message) =>
  new Response(message, { status, headers: kind ? { "X-Proxy-Error": kind } : {} });

const mediaHeaders = /^(content-type|content-length|content-range|accept-ranges|last-modified|etag)$/i;

function isMedia(request) {
  return (request.method === "GET" || request.method === "HEAD")
    && request.headers.get("Sec-Fetch-Site") === "same-origin"
    && ["audio", "video"].includes(request.headers.get("Sec-Fetch-Dest"));
}

export async function onRequest({ request }) {
  if (isMedia(request)) return media(request);
  if (request.headers.get("X-Lightweb-Proxy") !== "1") return fail(403, null, "light-web's proxy is for light-web's own pages");
  const site = request.headers.get("Sec-Fetch-Site");
  if (site && site !== "same-origin") return fail(403, null, "light-web's proxy is for light-web's own pages");

  const url = new URL(request.url);
  if (url.searchParams.has("probe")) return new Response(null, { status: 204, headers: { "X-Lightweb-Proxy": "1" } });
  if (!methods.has(request.method)) return new Response(null, { status: 405 });

  let target;
  try {
    target = new URL(url.searchParams.get("url") ?? "");
  } catch {
    return fail(400, "unreachable", "only http(s) URLs");
  }
  if (target.protocol !== "https:" && target.protocol !== "http:") return fail(400, "unreachable", "only http(s) URLs");
  if (!allowed(target.hostname.toLowerCase())) {
    return fail(403, "blocked", `${target.hostname} isn't one of the demo's sites; run light-web locally to reach it`);
  }

  const headers = new Headers({ Accept: "*/*" });
  for (const [k, v] of request.headers) {
    const name = /^x-proxy-h-(.+)$/i.exec(k)?.[1];
    if (name && !notForwarded.test(name)) headers.set(name, v);
  }
  const ua = request.headers.get("X-Proxy-User-Agent");
  if (ua) headers.set("User-Agent", ua);

  let body;
  if (request.method !== "GET" && request.method !== "HEAD") {
    body = await request.arrayBuffer();
    if (body.byteLength > MAX_REQUEST_BYTES) return fail(413, null, "request body too large");
  }

  let r;
  try {
    r = await fetch(target, { method: request.method, headers, body, redirect: "manual", signal: AbortSignal.timeout(TIMEOUT_MS) });
  } catch (e) {
    return fail(502, e?.name === "TimeoutError" ? "timeout" : "unreachable", String(e?.message ?? e));
  }
  const length = Number(r.headers.get("Content-Length") ?? 0);
  if (length > MAX_BYTES) return fail(502, "unreachable", `larger than ${MAX_BYTES >> 20} MB`);

  const out = new Headers({ "X-Proxy-Status": String(r.status), "Cache-Control": "no-store" });
  for (const [k, v] of r.headers) if (!dropped.test(k)) out.append(k, v);
  return new Response(r.body ? r.body.pipeThrough(capped(MAX_BYTES)) : null, { status: 200, headers: out });
}

/** A media element's request, made as itself: its range asked for, redirects followed, the answer as the site gave it. */
async function media(request) {
  let target;
  try {
    target = new URL(new URL(request.url).searchParams.get("url") ?? "");
  } catch {
    return new Response(null, { status: 400 });
  }
  if (!/^https?:$/.test(target.protocol) || !allowed(target.hostname.toLowerCase())) return new Response(null, { status: 403 });
  const headers = new Headers({ Accept: "*/*" });
  const range = request.headers.get("Range");
  if (range) headers.set("Range", range);
  let r;
  try {
    r = await fetch(target, { method: request.method, headers, redirect: "follow" });
  } catch {
    return new Response(null, { status: 502 });
  }
  const out = new Headers({ "Cache-Control": "no-store" });
  for (const [k, v] of r.headers) if (mediaHeaders.test(k)) out.set(k, v);
  // A compressed answer arrives decompressed, so its length no longer describes it.
  if (r.headers.get("Content-Encoding")) out.delete("Content-Length");
  return new Response(r.body, { status: r.status, headers: out });
}

/** Passes bytes through until there are more than max, then errors the stream. */
function capped(max) {
  let size = 0;
  return new TransformStream({
    transform(chunk, controller) {
      size += chunk.byteLength;
      if (size > max) controller.error(new Error(`larger than ${max >> 20} MB`));
      else controller.enqueue(chunk);
    },
  });
}
