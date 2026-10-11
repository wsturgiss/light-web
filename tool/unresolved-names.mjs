// Lists the distinct names a Kotlin/Wasm compile couldn't resolve, grouped by package: what a
// tool needs from light-web that isn't there yet. One missing class makes an error at every line
// that uses it, so the raw error count overstates the work; this counts names.
//
//   ./gradlew compileKotlinWasmJs > compile.log 2>&1; node tool/unresolved-names.mjs compile.log [pack]
//
// [pack] limits it to one tool's sources (build/generated/lightweb/upstream/<pack>). Each error is
// read at its position in the source: on an import line the name is the imported path; elsewhere
// it's the identifier at the error, and its package comes from the file's imports (no import:
// an unqualified JVM name, java.lang or JVM-only stdlib). Errors that follow from another (a
// type that can't be inferred because its source didn't resolve) are counted apart, not as names.
import { readFileSync } from "node:fs";

const [logPath, pack] = process.argv.slice(2);
if (!logPath) {
  console.error("usage: node tool/unresolved-names.mjs <compile.log> [pack]");
  process.exit(2);
}

const errorLine = /^e: file:\/\/(\S+?):(\d+):(\d+) (.*)$/;
const sources = new Map();
const lines = (f) => sources.get(f) ?? sources.set(f, readFileSync(f, "utf8").split("\n")).get(f);
const imports = (f) => lines(f).filter((l) => l.startsWith("import ")).map((l) => l.slice(7).trim().split(" as ")[0]);

const names = new Map(); // qualified name -> Set of "file:line"
let raw = 0, unresolved = 0, cascades = 0, other = [];
for (const line of readFileSync(logPath, "utf8").split("\n")) {
  const m = errorLine.exec(line);
  if (!m) continue;
  const [, file, ln, col, message] = m;
  if (pack && !file.includes(`/upstream/${pack}/`)) continue;
  raw++;
  const src = lines(file)[ln - 1] ?? "";
  const ident = /^[A-Za-z_]\w*/.exec(src.slice(col - 1))?.[0];
  const at = `${file.replace(/.*\/upstream\//, "")}:${ln}`;
  let name;
  // `it` with no type, `super` of a class that didn't resolve, `use` on one: follow-ons.
  if (/^Unresolved reference/.test(message) && (!ident || ["it", "this", "super", "use", "let", "also", "apply", "run"].includes(ident))) {
    cascades++;
    continue;
  }
  if (/^Unresolved reference '/.test(message) || /^Unresolved reference\. /.test(message) || /is internal in file/.test(message)) {
    unresolved++;
    if (src.startsWith("import ")) {
      name = src.slice(7).trim().split(" as ")[0];
    } else {
      const imported = imports(file).find((i) => i.endsWith(`.${ident}`));
      const before = src.slice(0, col - 1);
      // `Thread.getDefault...`: a class name before the dot, so a static of that class;
      // `connection.connect()`: a value before it, so a member of whatever type it has.
      const receiver = /([\w)\]]+)\??\.$/.exec(before)?.[1];
      if (imported) name = imported;
      else if (!receiver) name = `(unqualified) ${ident}`;
      else if (/^[A-Z]/.test(receiver)) name = `(unqualified) ${receiver}.${ident}`;
      else name = `(member) .${ident}`;
    }
  } else if (/Cannot infer|Argument type mismatch|Type mismatch|operator' modifier is required|None of the following candidates/.test(message)) {
    cascades++;
    continue;
  } else {
    other.push(`${at}: ${message}`);
    continue;
  }
  (names.get(name) ?? names.set(name, new Set()).get(name)).add(at);
}

// Package-level fragments of an import that also resolves to a class (e.g. 'xml' of
// org.xml.sax.Attributes) are the same missing thing; keep the longest.
const all = [...names.keys()];
for (const n of all) if (all.some((o) => o !== n && o.startsWith(`${n}.`))) names.delete(n);

const area = (n) => {
  if (n.startsWith("(unqualified)")) return "no receiver written: java.lang, JVM-only stdlib, a class's static, or a member through an implicit this";
  if (n.startsWith("(member)")) return "members of a value (the receiver's type isn't in the log; often a missing class's)";
  const parts = n.split(".");
  return parts.length > 2 && /^[a-z]/.test(parts[parts.length - 1]) === false ? parts.slice(0, -1).join(".") : "members (receiver.name)";
};
const groups = new Map();
for (const [n, at] of [...names].sort()) (groups.get(area(n)) ?? groups.set(area(n), []).get(area(n))).push([n, at]);

console.log(`raw errors: ${raw}; unresolved-reference errors: ${unresolved}; follow-on errors: ${cascades}; other: ${other.length}`);
const members = [...names.keys()].filter((n) => n.startsWith("(member)")).length;
console.log(`distinct names: ${names.size - members} (plus ${members} members called on values)\n`);
for (const [g, items] of [...groups].sort()) {
  console.log(`${g} (${items.length})`);
  for (const [n, at] of items) console.log(`  ${n}  [${at.size}x, e.g. ${[...at][0]}]`);
}
if (other.length) {
  console.log(`\nother errors (${other.length}):`);
  for (const o of other) console.log(`  ${o}`);
}
