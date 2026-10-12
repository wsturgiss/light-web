# Public demo on Cloudflare Pages

One Pages project serves the production build and, at `/proxy`, the relay
(`functions/proxy.js`). Being on the same origin as the page, it's the same `/proxy` that
`tool/serve.mjs` offers locally, so the build needs no change.

The relay only fetches from the sites in its `ALLOWED_HOSTS` (Gutenberg, Standard Ebooks,
Navidrome's demo, lrclib). Visitors' own music servers aren't reachable from the demo; for those,
run light-web locally.

## Once

1. Make a free Cloudflare account at https://dash.cloudflare.com/sign-up.
2. From `deploy/`: `npx wrangler login` (opens a browser to authorise Wrangler).
3. `npx wrangler pages project create light-web --production-branch main`. The name becomes
   `light-web.pages.dev`; if it's taken, choose another and change `name` in `wrangler.toml`.

## Each deploy

From the repo root:

```bash
JAVA_HOME=/home/will/.jdks/jbr-21.0.11 ./gradlew wasmJsBrowserDistribution
node tool/stage-deploy.mjs      # build → deploy/dist, without source maps, with LICENSES.txt
cd deploy && npx wrangler pages deploy
```

To try it locally first: `npx wrangler pages dev` in `deploy/` (http://localhost:8788).

## Limits and cost

The free plan has no billing, so it can't run up a bill: past 100,000 relay requests a day, the
relay fails until the next day (static files are unlimited). Per-visitor rate limits need a
Cloudflare rate-limiting rule, which needs a custom domain on Cloudflare; `*.pages.dev` can't have
one. Add it if the demo gets a domain.
