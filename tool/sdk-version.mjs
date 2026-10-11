// Which light-sdk release a tool is built on, and whether light-web takes it.
//
//   node tool/sdk-version.mjs [--sdk ../light-sdk] [--require-latest] [--check-sdk-checkout] <tool module dir>...
//
// light-web builds every tool against the latest light-sdk release only (docs/HANDOFF.md), so a
// tool on anything else is rejected before compiling, with the reason, rather than failing deep
// in the compile. "Latest release" is the highest vX.Y.Z tag in the light-sdk checkout (fetch its
// tags to see a new one). lighttool.toml doesn't record the version, so it's read from how the
// tool pins the SDK:
//
// - A light-sdk git submodule: the tag at its commit (`git describe --tags --exact-match`; not
//   `git submodule status`, which can name an older tag for the same commit).
// - A vendored `sdk/` folder: the release whose files it matches, by content (git blob hashes,
//   committed tree), ignoring Markdown. Uncommitted changes there make it unknown.
// - A Maven dependency on light-sdk's packages: the version in the Gradle files.
//
// Anything it can't place is a rejection too. Prints one line per tool; with --require-latest,
// exits 1 if any is rejected. --check-sdk-checkout also requires the light-sdk checkout itself
// to be at the latest release, since that's what light-web compiles tools against.
import { execFileSync } from "node:child_process";
import { existsSync, readFileSync, readdirSync, statSync } from "node:fs";
import { join, resolve } from "node:path";

const args = process.argv.slice(2);
let sdkDir = resolve(new URL("../../light-sdk", import.meta.url).pathname);
let requireLatest = false;
let checkCheckout = false;
const tools = [];
for (let i = 0; i < args.length; i++) {
  if (args[i] === "--sdk") sdkDir = resolve(args[++i]);
  else if (args[i] === "--require-latest") requireLatest = true;
  else if (args[i] === "--check-sdk-checkout") checkCheckout = true;
  else tools.push(resolve(args[i]));
}

const git = (dir, ...a) => execFileSync("git", ["-C", dir, ...a], { encoding: "utf8", stdio: ["ignore", "pipe", "pipe"] }).trim();
const tryGit = (dir, ...a) => { try { return git(dir, ...a); } catch { return null; } };

const semver = (t) => /^v(\d+)\.(\d+)\.(\d+)$/.exec(t)?.slice(1).map(Number);
const releases = git(sdkDir, "tag").split("\n").filter(semver)
  .sort((a, b) => { const x = semver(a), y = semver(b); return x[0] - y[0] || x[1] - y[1] || x[2] - y[2]; });
const latest = releases.at(-1);

/** path -> blob for a tree, Markdown left out. */
const files = (dir, treeish) => new Map(git(dir, "ls-tree", "-r", treeish).split("\n").filter(Boolean)
  .map((l) => { const [meta, path] = l.split("\t"); return [path, meta.split(" ")[2]]; })
  .filter(([path]) => !path.endsWith(".md")));
const releaseFiles = new Map(releases.map((t) => [t, files(sdkDir, `${t}:sdk`)]));

function differing(a, b) {
  let n = 0;
  for (const [p, h] of a) if (b.get(p) !== h) n++;
  for (const p of b.keys()) if (!a.has(p)) n++;
  return n;
}

/** { version } or { reason }. */
function sdkOf(toolDir) {
  const root = tryGit(toolDir, "rev-parse", "--show-toplevel");
  if (!root) return { reason: "not in a git checkout" };

  const gitmodules = join(root, ".gitmodules");
  if (existsSync(gitmodules)) {
    const sub = [...readFileSync(gitmodules, "utf8").matchAll(/path\s*=\s*(\S+)\s*\n\s*url\s*=\s*(\S+)/g)]
      .find(([, , url]) => /lightphone\/light-sdk(\.git)?$/.test(url));
    if (sub) {
      const dir = join(root, sub[1]);
      const tag = tryGit(dir, "describe", "--tags", "--exact-match");
      if (tag) return { version: tag, how: `submodule ${sub[1]}` };
      const commit = tryGit(dir, "rev-parse", "--short", "HEAD");
      return { reason: commit ? `submodule ${sub[1]} is at ${commit}, which isn't a release tag` : `submodule ${sub[1]} isn't checked out` };
    }
  }

  if (existsSync(join(root, "sdk")) && statSync(join(root, "sdk")).isDirectory()) {
    if (tryGit(root, "status", "--porcelain", "--", "sdk")) return { reason: "vendored sdk/ has uncommitted changes" };
    const mine = files(root, "HEAD:sdk");
    let best = null;
    for (const [tag, theirs] of releaseFiles) {
      const n = differing(mine, theirs);
      if (n === 0) return { version: tag, how: "vendored sdk/" };
      if (!best || n < best.n) best = { tag, n };
    }
    return { reason: `vendored sdk/ matches no release (closest ${best?.tag}, ${best?.n} files differ)` };
  }

  const gradle = readdirSync(root, { recursive: true })
    .filter((f) => /\.(kts|gradle|toml)$/.test(f) && !f.includes("build/"))
    .map((f) => readFileSync(join(root, f), "utf8")).join("\n");
  const maven = /com\.thelightphone(?:\.sdk)?:(?:client|ui|shared|sdk)[\w-]*:v?(\d+\.\d+\.\d+)/.exec(gradle)
    ?? /lightSdk\w*\s*=\s*"v?(\d+\.\d+\.\d+)"/.exec(gradle);
  if (maven) return { version: `v${maven[1]}`, how: "Maven dependency" };
  return { reason: "can't tell which SDK it uses (no light-sdk submodule, vendored sdk/ or Maven dependency)" };
}

let rejected = 0;
if (checkCheckout) {
  const at = tryGit(sdkDir, "describe", "--tags", "--exact-match");
  if (at !== latest) {
    rejected++;
    console.log(`light-sdk checkout (${sdkDir}): at ${at ?? tryGit(sdkDir, "rev-parse", "--short", "HEAD")}, not ${latest} (latest release); run git -C ${sdkDir} checkout ${latest}`);
  }
}
for (const dir of tools) {
  const name = tryGit(dir, "rev-parse", "--show-toplevel")?.split("/").at(-1) ?? dir;
  const r = sdkOf(dir);
  if (r.version === latest) {
    console.log(`${name}: SDK ${r.version} (${r.how}), the latest release`);
  } else {
    rejected++;
    const why = r.version ? `SDK ${r.version} (${r.how})` : r.reason;
    console.log(`${name}: REJECTED: ${why}; light-web requires ${latest} (latest release)`);
  }
}
if (requireLatest && rejected) process.exit(1);
