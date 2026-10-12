// Copies the production build into deploy/dist for Cloudflare Pages, leaving out source maps,
// and adds the licence notices a public build must carry.
//
//   node tool/stage-deploy.mjs     (after ./gradlew wasmJsBrowserDistribution)
import { cpSync, readFileSync, rmSync, writeFileSync } from "node:fs";

const from = "build/dist/wasmJs/productionExecutable";
const to = "deploy/dist";
rmSync(to, { recursive: true, force: true });
cpSync(from, to, { recursive: true, filter: (p) => !p.endsWith(".map") });

const props = readFileSync("gradle.properties", "utf8");
const dirs = [
  ...(/^tools=(.*)$/m.exec(props)?.[1].trim().split(/\s+/).map((t) => t.replace(/\/tool$/, "")) ?? []),
  /^lightSdk\.dir=(.*)$/m.exec(props)?.[1].trim(),
  /^lightKeyboard\.dir=(.*)$/m.exec(props)?.[1].trim(),
].filter(Boolean);
const notices = dirs.map((d) => `== ${d.replace(/^\.\.\//, "")}\n\n${readFileSync(`${d}/LICENSE`, "utf8").trim()}\n`);
notices.push(`== Other components (Kotlin, Compose, kotlinx, SQLite and more): see lightweb.js.LICENSE.txt\n`);
writeFileSync(`${to}/LICENSES.txt`, notices.join("\n"));
// Marks the page as the hosted demo, so tools can start with something to show (lightweb.DemoSeed).
const index = `${to}/index.html`;
writeFileSync(index, readFileSync(index, "utf8").replace("<script", "<script>globalThis.lightwebDemo = true;</script>\n    <script"));
// Hashed wasm names never change content, so they can be cached for good.
writeFileSync(`${to}/_headers`, "/*.wasm\n  Cache-Control: public, max-age=31536000, immutable\n");
console.log(`staged ${to} from ${from}, with LICENSES.txt for ${dirs.length} repos`);
