// The API scanner: what a tool uses from outside its own code that light-web doesn't provide,
// read from the tool's Android build (its bytecode records every reference exactly), before
// light-web ever compiles it.
//
//   node tool/api-scan/scan.mjs [options] <tool module dir>...
//     --json <file>       also write the results as JSON
//     --lightweb <dir>    the light-web tree whose stand-ins count as provided (default: this one);
//                         a worktree of an older commit shows what that commit was missing
//     --no-build          use the classes already built
//     --libs              also scan inside the tool's own library dependencies
//
// With several tools, it also ranks the missing names by how many tools use them.
//
// How it works:
// 1. Builds the module for Android (`compileDebugKotlin`, `compileDebugJavaWithJavac`) with the
//    tool's own Gradle, and reads its debug runtime classpath (classpath.init.gradle).
// 2. Reads every class, field and method reference from the compiled classes' constant pools
//    (`javap -v`). Libraries the SDK brings in (its whole runtime classpath) count as supplied,
//    as do Compose, kotlinx and the other libraries light-web builds with, except their Android-
//    only parts (`*_androidKt` files). A library only the tool adds is reported as such.
// 3. Indexes what light-web declares: src/compat, src/sdk, the JVM-names template, and the SDK and
//    keyboard sources it compiles unchanged.
// 4. Reports each reference light-web lacks, by package, with the classes that make it.
//
// Bytecode isn't source, and the differences are where it can be wrong:
// - Kotlin compiles some common calls to java.lang (`"1".toInt()` -> Integer.parseInt), so
//   java.lang and Kotlin's mapped collection types are only checked for members known to be
//   JVM-only (JAVA_ONLY_MEMBERS) and for classes that aren't mapped (Thread, Runtime...).
// - Inline functions are expanded into the tool: an inlined stdlib or library function's body
//   shows up as the tool's own references (`use {}` -> Throwable.addSuppressed).
// - Java platform types, and overloads that resolve differently on the web (a Locale taken as a
//   vararg), compile on both sides; nothing here sees them.
import { execFileSync } from "node:child_process";
import { existsSync, mkdtempSync, readFileSync, readdirSync, statSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { basename, dirname, join, relative, resolve } from "node:path";

const here = dirname(new URL(import.meta.url).pathname);
const args = process.argv.slice(2);
let lightweb = resolve(here, "../..");
let jsonOut = null, build = true, scanLibs = false;
const tools = [];
for (let i = 0; i < args.length; i++) {
  if (args[i] === "--lightweb") lightweb = resolve(args[++i]);
  else if (args[i] === "--json") jsonOut = resolve(args[++i]);
  else if (args[i] === "--no-build") build = false;
  else if (args[i] === "--libs") scanLibs = true;
  else tools.push(resolve(args[i]));
}
if (!tools.length) {
  console.error("usage: node tool/api-scan/scan.mjs [--json f] [--lightweb dir] [--no-build] [--libs] <tool module dir>...");
  process.exit(2);
}

const env = {
  ...process.env,
  ANDROID_HOME: process.env.ANDROID_HOME ?? "/home/will/Projects/Android",
  JAVA_HOME: process.env.JAVA_HOME ?? "/home/will/.jdks/jbr-21.0.11",
};
const javap = join(env.JAVA_HOME, "bin", "javap");
// The SDK and keyboard sources come from this checkout's settings, whatever --lightweb says: they
// aren't part of a light-web snapshot.
const repo = resolve(here, "../..");
const props = Object.fromEntries(readFileSync(join(repo, "gradle.properties"), "utf8").split("\n")
  .map((l) => /^([\w.]+)=(.*)$/.exec(l)).filter(Boolean).map((m) => [m[1], m[2]]));
const sdkDir = resolve(repo, props["lightSdk.dir"] ?? "../light-sdk");
const keyboardDir = resolve(repo, props["lightKeyboard.dir"] ?? "../light-keyboard");

// ---------------------------------------------------------------------------------------------
// What light-web provides.

/** Kotlin files under dir. */
const ktFiles = (dir) => existsSync(dir)
  ? readdirSync(dir, { recursive: true }).filter((f) => f.endsWith(".kt")).map((f) => join(dir, f))
  : [];

/**
 * Declarations by JVM class name ("java/net/URI" -> member names), package-level names by package
 * ("java/net" -> names), and the template's names, which every package gets.
 */
function indexLightweb() {
  const classes = new Map();
  const packageNames = new Map();
  const supertypes = new Map(); // class -> supertype simple names
  const add = (map, key, name) => (map.get(key) ?? map.set(key, new Set()).get(key)).add(name);
  const sources = [
    ...ktFiles(join(lightweb, "src/compat")),
    ...ktFiles(join(lightweb, "src/sdk")),
    ...ktFiles(join(sdkDir, "sdk/ui/src/main/kotlin")),
    ...ktFiles(join(sdkDir, "sdk/shared/src/main/kotlin")),
    ...ktFiles(join(keyboardDir, "ui/src/main/java")),
  ];
  const decl = /^(\s*)(?:(?:public|internal|private|protected|open|abstract|sealed|data|enum|annotation|value|inline|fun|const|override|operator|infix|suspend|lateinit|external|actual|expect|companion)\s+)*(class|interface|object|typealias|fun|val|var)\s+(?:<[^>]*>\s*)?(?:[\w.<>?, *]+\.)?(`?\w+`?)/;
  for (const file of sources) {
    const text = readFileSync(file, "utf8");
    const pkg = (/^package\s+([\w.]+)/m.exec(text)?.[1] ?? "").replaceAll(".", "/");
    let current = null; // the top-level class being read
    let enumEntries = false; // reading an enum class's entries
    for (const line of text.split("\n")) {
      if (enumEntries && current) {
        for (const e of line.split(",")) { const n = /^\s*(\w+)\s*(\(|\{|$)/.exec(e)?.[1]; if (n && /^[A-Z]/.test(n)) add(classes, current, n); }
        if (/;|^\}/.test(line)) enumEntries = false;
        if (!decl.test(line)) continue;
      }
      const m = decl.exec(line);
      if (!m) {
        // Enum entries and constructor properties: `val name` inside a class header.
        if (current) for (const p of line.matchAll(/\b(?:val|var)\s+(\w+)/g)) add(classes, current, p[1]);
        continue;
      }
      const [, indent, kind, rawName] = m;
      const name = rawName.replaceAll("`", "");
      if (indent.length === 0) {
        if (["class", "interface", "object", "typealias"].includes(kind)) {
          current = `${pkg}/${name}`;
          classes.has(current) || classes.set(current, new Set());
          enumEntries = /\benum\s+class\b/.test(line) && line.includes("{");
          const supers = /\)?\s*:\s*([^{=]+)/.exec(line.slice(line.indexOf(rawName) + rawName.length).replace(/\([^()]*\)/g, ""))?.[1];
          if (supers && kind !== "typealias") supertypes.set(current, supers.split(",").map((t) => t.trim().replace(/[<(].*$/, "").split(".").at(-1)).filter(Boolean));
          // Constructor parameters declared as properties.
          for (const p of line.matchAll(/\b(?:val|var)\s+(\w+)/g)) add(classes, current, p[1]);
        } else {
          current = null;
          add(packageNames, pkg, name);
        }
      } else if (current) {
        if (["class", "interface", "object"].includes(kind)) classes.has(`${current}$${name}`) || classes.set(`${current}$${name}`, new Set());
        add(classes, current, name);
      }
    }
  }
  // Stand-ins that are typealiases to Kotlin types: their JVM counterpart decides members.
  const compatAliases = new Map();
  const kotlinToJava = { HashMap: "java/util/HashMap", ArrayList: "java/util/ArrayList", HashSet: "java/util/HashSet", LinkedHashMap: "java/util/LinkedHashMap", MutableMap: "java/util/Map", MutableList: "java/util/List" };
  for (const file of sources) {
    const text = readFileSync(file, "utf8");
    const pkg = (/^package\s+([\w.]+)/m.exec(text)?.[1] ?? "").replaceAll(".", "/");
    for (const m of text.matchAll(/^typealias (\w+)(?:<[^>]*>)? = (\w+)/gm)) if (kotlinToJava[m[2]]) compatAliases.set(`${pkg}/${m[1]}`, kotlinToJava[m[2]]);
  }
  const template = new Set();
  const typealiases = new Map(); // template typealias -> its target's JVM name
  const templateText = readFileSync(join(lightweb, "src/templates/JvmBuiltins.kt.template"), "utf8");
  for (const m of templateText.matchAll(/^internal\s+(?:inline\s+|annotation\s+)?(?:fun|val|typealias|class)\s+(?:<[^>]*>\s*)?(?:[\w.<>?, *]+\.)?(\w+)/gm)) template.add(m[1]);
  for (const m of templateText.matchAll(/^internal typealias (\w+)(?:<[^>]*>)? = ([\w.]+)/gm)) typealiases.set(m[1], m[2].replaceAll(".", "/"));
  return { classes, packageNames, template, typealiases, compatAliases, supertypes };
}

// ---------------------------------------------------------------------------------------------
// Kotlin's stdlib: what's JVM-only.

function stdlibJvmOnly() {
  const cache = join(process.env.HOME, ".gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib");
  const jar = existsSync(cache) && readdirSync(cache, { recursive: true }).filter((f) => /kotlin-stdlib-2[\d.]+-sources\.jar$/.test(f)).sort().at(-1);
  if (!jar) return null;
  const dir = mkdtempSync(join(tmpdir(), "stdlib-"));
  execFileSync("unzip", ["-q", "-o", join(cache, jar), "-d", dir]);
  // @PublishedApi internals exist for inline functions to call; the inline function is the API,
  // and it's in common code. Their calls in a tool are inlined bodies, not the tool's choice.
  const publishedApi = new Set();
  const names = (sub) => {
    const out = new Set();
    for (const f of ktFiles(join(dir, sub))) {
      const text = readFileSync(f, "utf8");
      const pkg = (/^package\s+([\w.]+)/m.exec(text)?.[1] ?? "kotlin").replaceAll(".", "/");
      for (const m of text.matchAll(/^((?:@[\w.]+(?:\([^)]*\))?\s+)*)(?:(?:public|internal|private|actual|expect|inline|infix|operator|suspend|const|data|sealed|open|abstract|value|annotation|enum)\s+)*(?:fun|val|var|class|object|interface|typealias)\s+(?:<[^>]*>\s*)?(?:[\w.<>?, *]+\.)?(\w+)/gm)) {
        out.add(`${pkg}.${m[2]}`);
        if (m[1].includes("@PublishedApi")) publishedApi.add(`${pkg}.${m[2]}`);
      }
    }
    return out;
  };
  const common = names("commonMain");
  const jvm = names("jvmMain");
  return new Set([...jvm].filter((n) => !common.has(n) && !publishedApi.has(n)));
}

// java.lang and java.util types Kotlin maps to its own: only these members are JVM-only.
const MAPPED = new Set([
  "java/lang/Object", "java/lang/String", "java/lang/CharSequence", "java/lang/Comparable", "java/lang/Number",
  "java/lang/Integer", "java/lang/Long", "java/lang/Short", "java/lang/Byte", "java/lang/Double", "java/lang/Float",
  "java/lang/Character", "java/lang/Boolean", "java/lang/Enum", "java/lang/Iterable", "java/lang/Throwable",
  "java/lang/Exception", "java/lang/RuntimeException", "java/lang/Error", "java/lang/StringBuilder", "java/lang/Appendable",
  "java/lang/Math", "java/lang/Void", "java/lang/Cloneable", "java/lang/annotation/Annotation", "java/lang/AutoCloseable",
  "java/util/Collection", "java/util/List", "java/util/Set", "java/util/Map", "java/util/Map$Entry", "java/util/Iterator",
  "java/util/ListIterator", "java/util/ArrayList", "java/util/HashMap", "java/util/HashSet", "java/util/LinkedHashMap",
  "java/util/LinkedHashSet", "java/util/Comparator", "java/util/RandomAccess", "java/util/AbstractList",
  "java/util/AbstractMap", "java/util/AbstractSet", "java/util/AbstractCollection", "java/util/NoSuchElementException",
  "java/lang/IllegalArgumentException", "java/lang/IllegalStateException", "java/lang/IndexOutOfBoundsException",
  "java/lang/UnsupportedOperationException", "java/lang/NullPointerException", "java/lang/ClassCastException",
  "java/lang/ArithmeticException", "java/lang/NumberFormatException", "java/lang/AssertionError",
  "java/util/ConcurrentModificationException", "java/lang/ArrayIndexOutOfBoundsException",
  "java/util/concurrent/CancellationException",
  // Kotlin's copyOf, fill, sort, contentEquals... compile to these, so a reference doesn't show the
  // tool called them. (A tool's own explicit java.util.Arrays use is missed for the same reason.)
  "java/util/Arrays",
]);
// Functional interfaces appear as parameter types of the JVM methods that take lambdas; the
// method is what's checked.
const PARAMETER_ONLY = /^java\/util\/(function\/|SortedMap$|NavigableMap$|SortedSet$|NavigableSet$|concurrent\/ConcurrentMap$|Optional$)/;
// Members of mapped types a tool calls that Kotlin off the JVM doesn't have, and the template
// name that provides them if any.
const JAVA_ONLY_MEMBERS = {
  "java/util/Map.putIfAbsent": "putIfAbsent", "java/util/Map.computeIfAbsent": "computeIfAbsent",
  "java/util/Map.computeIfPresent": null, "java/util/Map.compute": null, "java/util/Map.merge": null,
  "java/util/Map.forEach": null, "java/util/Map.replaceAll": null, "java/util/Map.getOrDefault": null,
  "java/util/List.replaceAll": "replaceAll", "java/util/List.sort": null, "java/util/Collection.removeIf": null,
  "java/util/Collection.stream": null, "java/lang/Iterable.forEach": null,
  "java/lang/String.format": "format", "java/lang/String.getBytes": "toByteArray", "java/lang/String.intern": null,
  "java/lang/String.codePointAt": "codePointAt", "java/lang/String.codePoints": null, "java/lang/String.chars": null,
  "java/lang/String.<init>": "String", "java/lang/StringBuilder.appendCodePoint": "appendCodePoint",
  "java/lang/Object.wait": null, "java/lang/Object.notify": null, "java/lang/Object.notifyAll": null,
  "java/lang/Throwable.printStackTrace": null, "java/lang/Throwable.getStackTrace": null,
  "java/lang/Throwable.setStackTrace": null, "java/lang/Throwable.initCause": null,
};
// java.lang classes whose references are the compiler's, not the tool's.
// (NoSuchFieldError: `when` over an enum; the rest: lambdas, annotations, `::class`.)
const COMPILER_ONLY = /^java\/lang\/(invoke\/|Class$|reflect\/Array$|Deprecated$|SuppressWarnings$|SafeVarargs$|FunctionalInterface$|Override$|annotation\/|NoSuchFieldError$|NoSuchMethodError$)/;
// Inlined stdlib bodies: `use {}` closes through these, which a web build has its own of.
const INLINE_ARTIFACTS = new Set(["kotlin/io/CloseableKt.closeFinally", "kotlin/AutoCloseableKt.closeFinally", "kotlin/jdk7/AutoCloseableKt.closeFinally"]);
// Libraries light-web builds with, in their web form; their Android-only files are still checked.
// The Light SDK isn't one of them: light-web reimplements its client (src/sdk), so references into
// it are checked against that like any other.
// Room is Room 3 on the web (toRoom3 in build.gradle.kts), and so is the androidx.sqlite under it,
// except SupportSQLite (androidx.sqlite.db), which Room 3 dropped.
const SUPPLIED = /^(androidx\/room\/|androidx\/sqlite\/(?!db\/)|androidx\/compose\/|kotlinx\/coroutines\/|kotlinx\/serialization\/|androidx\/lifecycle\/|androidx\/datastore\/|androidx\/annotation\/|androidx\/collection\/|org\/jetbrains\/annotations\/|org\/intellij\/lang\/annotations\/|okio\/|kotlinx\/datetime\/|co\/touchlab\/kermit\/)/;

// ---------------------------------------------------------------------------------------------
// Building and reading a tool.

function gradleProject(moduleDir) {
  let root = moduleDir;
  while (!existsSync(join(root, "settings.gradle.kts")) && !existsSync(join(root, "settings.gradle")) && dirname(root) !== root) root = dirname(root);
  const rel = relative(root, moduleDir);
  return { root, path: rel ? `:${rel.replaceAll("/", ":")}` : "" };
}

function gradle(root, ...tasks) {
  return execFileSync(join(root, "gradlew"), ["-q", "--console=plain", ...tasks], { cwd: root, env, encoding: "utf8", maxBuffer: 1 << 28, stdio: ["ignore", "pipe", "pipe"] });
}

/** The tool's class directories, after building them. */
function buildTool(moduleDir) {
  const { root, path } = gradleProject(moduleDir);
  if (build) {
    try {
      gradle(root, `${path}:compileDebugKotlin`, `${path}:compileDebugJavaWithJavac`);
    } catch (e) {
      throw new Error(`Android build failed: ${(e.stderr ?? e.message).split("\n").filter((l) => /error|wrong|failed/i.test(l)).slice(0, 5).join(" / ")}`);
    }
  }
  const dirs = [join(moduleDir, "build/tmp/kotlin-classes/debug"), join(moduleDir, "build/intermediates/javac/debug/compileDebugJavaWithJavac/classes"), join(moduleDir, "build/intermediates/javac/debug/classes")]
    .filter((d) => existsSync(d));
  if (!dirs.length) throw new Error("no compiled classes found");
  return { root, path, dirs };
}

/** project path -> [{ id, file }] for every project's debug runtime classpath. */
function classpaths(root) {
  const out = gradle(root, "-I", join(here, "classpath.init.gradle"), "lightwebClasspath");
  const byProject = new Map();
  for (const line of out.split("\n")) {
    const [tag, project, id, file] = line.split("\t");
    if (tag !== "LIGHTWEB-CP") continue;
    (byProject.get(project) ?? byProject.set(project, []).get(project)).push({ id, file });
  }
  return byProject;
}

const classFiles = (dir) => readdirSync(dir, { recursive: true }).filter((f) => f.endsWith(".class")).map((f) => join(dir, f));

/** Every reference in the classes: [{ owner, member, kind, from }]. */
function references(files, cp = null) {
  const refs = [];
  for (let i = 0; i < files.length; i += 200) {
    const batch = files.slice(i, i + 200);
    const out = execFileSync(javap, ["-v", "-p", ...(cp ? ["-cp", cp] : []), ...batch], { encoding: "utf8", maxBuffer: 1 << 30, stdio: ["ignore", "pipe", "ignore"] });
    let from = null;
    for (const line of out.split("\n")) {
      const header = /^(?:public |final |abstract |private |protected |static |synthetic |)*(?:class|interface|enum) ([\w.$]+)/.exec(line) ?? /^Classfile .*\/([\w$]+)\.class$/.exec(line);
      if (header) { from = header[1].replaceAll(".", "/"); continue; }
      const descriptorTypes = (desc) => {
        for (const t of desc.matchAll(/L([\w/$]+);/g)) if (/^[a-z]/.test(t[1])) refs.push({ owner: t[1], member: null, kind: "Class", from });
      };
      // Classes from the constant pool, and a lambda's interface (`invokedynamic` names it only
      // in its descriptor).
      const pool = /^\s+#\d+ = (Class|InvokeDynamic)\s+\S+\s+\/\/\s+(.+)$/.exec(line);
      if (pool) {
        const [, kind, target] = pool;
        if (kind === "InvokeDynamic") { descriptorTypes(target); continue; }
        const owner = target.replace(/^"?\[*L?/, "").replace(/;?"?$/, "");
        if (/^[a-z]/.test(owner)) refs.push({ owner, member: null, kind, from });
        continue;
      }
      // Methods and fields from the instructions that use them, where the opcode shows a bridge:
      // `invokespecial` of an interface method is the JVM compiler giving a class an interface's
      // default method, which the tool neither calls nor overrides.
      const code = /^\s+\d+: (\w+)\s+#\d+(?:,\s*\d+)?\s+\/\/ (Method|InterfaceMethod|Field) (.+)$/.exec(line);
      if (!code) continue;
      const [, op, kind, target] = code;
      if (op === "invokespecial" && kind === "InterfaceMethod") continue;
      const [ownerMember, desc] = target.split(":");
      const dot = ownerMember.lastIndexOf(".");
      if (dot < 0) continue; // the class's own member
      descriptorTypes(desc ?? "");
      refs.push({ owner: ownerMember.slice(0, dot).replace(/^"|"$/g, ""), member: ownerMember.slice(dot + 1).replace(/^"|"$/g, ""), desc, kind, from });
    }
  }
  return refs;
}

// ---------------------------------------------------------------------------------------------
// Deciding what's missing.

/** A member's source name: without `$default`, and without the hash Kotlin appends to functions taking inline value classes (`getBackground-0d7_KjU`). */
const demangle = (member) => member.replace(/\$default$/, "").replace(/-[\w]{4,8}$/, "");

/** Kotlin's name for a Java getter's property: the leading capitals lowercased (getFD -> fd, getXMLReader -> xmlReader). */
function decapitalize(s) {
  const run = /^[A-Z]+/.exec(s)[0];
  if (run.length === s.length) return s.toLowerCase();
  if (run.length === 1) return s[0].toLowerCase() + s.slice(1);
  return run.slice(0, -1).toLowerCase() + s.slice(run.length - 1);
}

/** The source names a JVM member can have: itself, and a property's for a getter or setter. */
function beanNames(member) {
  member = demangle(member);
  const names = [member];
  const m = /^(get|set|is)([A-Z]\w*)$/.exec(member);
  if (m) {
    const prop = decapitalize(m[2]);
    names.push(prop, `is${m[2]}`, m[2]);
  }
  return names;
}

// Kotlin's own supertypes: what a stand-in inherits from these is known, so nothing is assumed.
const KOTLIN_BASICS = new Set(["Any", "AutoCloseable", "Comparable", "Exception", "RuntimeException", "Throwable", "Error", "Iterable", "Collection", "List", "Map", "Set", "CharSequence", "Number", "Enum", "Annotation", "IllegalArgumentException", "IllegalStateException"]);
// For JAVA_ONLY_MEMBERS: a concrete collection's members are its interface's.
const COLLECTION_INTERFACE = { "java/util/HashMap": "java/util/Map", "java/util/LinkedHashMap": "java/util/Map", "java/util/AbstractMap": "java/util/Map", "java/util/ArrayList": "java/util/List", "java/util/AbstractList": "java/util/List", "java/util/HashSet": "java/util/Collection", "java/util/LinkedHashSet": "java/util/Collection", "java/util/Set": "java/util/Collection", "java/util/List": "java/util/Collection" };

function makeChecker(index, jvmOnly) {
  const hasClass = (owner) => index.classes.has(owner) || index.classes.has(owner.replace(/\$.*/, ""));
  const bySimpleName = new Map([...index.classes.keys()].map((c) => [c.split("/").at(-1), c]));
  const hasMember = (owner, member, seen = new Set()) => {
    if (member === "<init>" || member === "<clinit>") return hasClass(owner);
    member = demangle(member);
    if (member.startsWith("$") || member === "INSTANCE" || member === "Companion") return hasClass(owner); // compiler fields
    // What the compiler writes for data classes and enums.
    if (/^(copy|component\d+|values|valueOf|ordinal|name|getEntries|getOrdinal|getName|hashCode|equals|toString)$/.test(member) && hasClass(owner)) return true;
    const key = index.classes.has(owner) ? owner : owner.replace(/\$.*/, "");
    const members = index.classes.get(key);
    const companion = index.classes.get(`${owner}$Companion`);
    if (beanNames(member).some((n) => members?.has(n) || companion?.has(n))) return true;
    // Inherited: from an indexed supertype, or from one light-web doesn't declare (a library's
    // class, such as ViewModel), which is assumed to have it.
    seen.add(key);
    for (const sup of index.supertypes.get(key) ?? []) {
      const c = bySimpleName.get(sup);
      if (!c && KOTLIN_BASICS.has(sup)) continue;
      if (!c) return true;
      if (!seen.has(c) && hasMember(c, member, seen)) return true;
    }
    return false;
  };
  const facadePackage = (owner) => owner.slice(0, owner.lastIndexOf("/"));
  const isFacade = (owner) => /Kt$|Kt__\w+$/.test(owner.split("/").at(-1));

  /** Null if provided; else the name to report as missing. */
  return function missing(ref) {
    let { owner, member } = ref;
    // light-web generates every tool's R.
    if (/\/R(\$\w+)?$/.test(owner)) return null;
    // A stand-in that's a typealias to a Kotlin collection (ConcurrentHashMap = HashMap).
    const aliased = index.compatAliases.get(owner);
    if (aliased) owner = aliased;
    if (!owner || owner.startsWith("[")) return null;
    if (/^kotlin\/(jvm\/internal|jvm\/functions|coroutines\/jvm\/internal|internal)\//.test(owner)) return null;
    if (owner.startsWith("kotlin/")) {
      const pkg = facadePackage(owner);
      if (isFacade(owner)) {
        if (!member || INLINE_ARTIFACTS.has(`${owner}.${member}`)) return null;
        // `appendText$default` is appendText with defaults; `getNameWithoutExtension` a property.
        const names = beanNames(member);
        const jvmName = names.find((n) => jvmOnly?.has(`${pkg}.${n}`));
        if (!jvmName) return null;
        if (names.some((n) => index.template.has(n))) return null;
        // A kotlin.io extension on File etc.: provided if the stand-in has it as a member.
        const receiver = /^\(L([\w/$]+);/.exec(ref.desc ?? "")?.[1];
        if (receiver && names.some((n) => hasMember(receiver, n))) return null;
        return `${pkg}.${jvmName}`;
      }
      const name = owner.split("/").at(-1);
      if (!jvmOnly?.has(`${pkg}.${name}`)) return null;
      if (index.template.has(name)) return null;
      return owner;
    }
    if (PARAMETER_ONLY.test(owner)) return null;
    if (owner.startsWith("java/lang/") || MAPPED.has(owner)) {
      if (COMPILER_ONLY.test(owner)) return null;
      if (MAPPED.has(owner)) {
        let type = owner;
        while (!(`${type}.${member}` in JAVA_ONLY_MEMBERS) && COLLECTION_INTERFACE[type]) type = COLLECTION_INTERFACE[type];
        const key = `${type}.${member}`;
        if (!(key in JAVA_ONLY_MEMBERS)) return null;
        const provider = JAVA_ONLY_MEMBERS[key];
        return provider && index.template.has(provider) ? null : `${ref.owner}.${member}`;
      }
      const simple = owner.split("/").at(-1).replace(/\$.*/, "");
      const alias = index.typealiases.get(simple);
      if (alias) return !member || hasMember(alias + owner.slice(owner.indexOf("$") >= 0 ? owner.indexOf("$") : owner.length), member) || hasMember(alias, member) ? null : `${owner}.${member}`;
      if (!hasClass(owner)) return owner;
      return !member || hasMember(owner, member) ? null : `${owner}.${member}`;
    }
    if (SUPPLIED.test(owner)) {
      const file = owner.split("/").at(-1);
      if (!/_android(Kt)?$|_androidKt|\$android/i.test(file) || !member) return null;
      // An Android-only file of a supplied library: provided if light-web declares the name.
      if (beanNames(member).some((n) => index.packageNames.get(facadePackage(owner))?.has(n))) return null;
      return member ? `${owner}.${member}` : owner;
    }
    // Top-level functions and properties, through their file's facade class.
    if (isFacade(owner) && !hasClass(owner)) {
      if (!member || member.startsWith("$")) return null;
      const names = beanNames(member);
      return names.some((n) => index.packageNames.get(facadePackage(owner))?.has(n) || index.template.has(n)) ? null : `${facadePackage(owner)}.${names.at(-1)}`;
    }
    if (!hasClass(owner)) return owner;
    return !member || hasMember(owner, member) ? null : `${owner}.${demangle(member)}`;
  };
}

// ---------------------------------------------------------------------------------------------

const index = indexLightweb();
const jvmOnly = stdlibJvmOnly();
if (!jvmOnly) console.error("warning: no kotlin-stdlib sources jar in the Gradle cache; JVM-only stdlib calls aren't checked");
const missing = makeChecker(index, jvmOnly);

const results = [];
for (const moduleDir of tools) {
  const name = basename(gradleProject(moduleDir).root) + (gradleProject(moduleDir).path.replace(/^:tool$/, "") || "");
  process.stderr.write(`${name}: building and scanning...\n`);
  try {
    const { root, path, dirs } = buildTool(moduleDir);
    const cps = classpaths(root);
    const own = cps.get(path || ":") ?? [];
    const supplied = new Set([...cps].filter(([p]) => /(^|:)sdk(:|$)/.test(p) && p !== path).flatMap(([, entries]) => entries.map((e) => e.id.replace(/:[^:]+$/, ""))));
    const extraLibs = own.filter((e) => !e.id.startsWith("project ") && !supplied.has(e.id.replace(/:[^:]+$/, "")));

    const files = dirs.flatMap(classFiles);
    const ownClasses = new Set(files.map((f) => relative(dirs.find((d) => f.startsWith(d)), f).replace(/\.class$/, "")));
    let refs = references(files);
    if (scanLibs) {
      for (const lib of extraLibs.filter((l) => l.file.endsWith(".jar"))) {
        const list = execFileSync("unzip", ["-Z1", lib.file], { encoding: "utf8" }).split("\n").filter((f) => f.endsWith(".class") && !f.startsWith("META-INF"));
        for (const c of list) ownClasses.add(c.replace(/\.class$/, ""));
        refs = refs.concat(references(list.map((c) => c.replace(/\.class$/, "").replaceAll("/", ".")), lib.file).map((r) => ({ ...r, from: `${lib.id}: ${r.from}` })));
      }
    }

    const found = new Map(); // missing name -> Set of classes using it
    for (const ref of refs) {
      if (ownClasses.has(ref.owner) || ownClasses.has(ref.owner.replace(/\$.*/, ""))) continue;
      // Room's generated database code (KSP, *_Impl): Room 3's processor writes it anew for the web.
      if (/_Impl(\$|$)/.test(ref.from) && /^androidx\/(room|sqlite)\//.test(ref.owner)) continue;
      let m = missing(ref);
      if (!m) continue;
      // A library light-web doesn't supply is one need, whatever the tool calls in it.
      const lib = libraryOf(m);
      if (lib) m = `${lib} (library: light-web doesn't supply it)`;
      (found.get(m) ?? found.set(m, new Set()).get(m)).add(ref.from);
    }
    // A missing class makes its members missing too; report the class once.
    for (const n of [...found.keys()]) {
      const dot = n.lastIndexOf(".");
      if (dot > 0 && found.has(n.slice(0, dot))) {
        for (const f of found.get(n)) found.get(n.slice(0, dot)).add(f);
        found.delete(n);
      }
    }
    results.push({ tool: name, moduleDir, extraLibraries: extraLibs.map((l) => l.id), missing: Object.fromEntries([...found].sort().map(([k, v]) => [k, [...v].sort()])) });
  } catch (e) {
    results.push({ tool: name, moduleDir, error: e.message });
  }
}

// ---------------------------------------------------------------------------------------------
// Report.

/** A third-party library's name (its first package segments) for a missing name, or null for the platform's. */
function libraryOf(name) {
  const parts = name.split(/[/.]/);
  if (/^(java|javax|android|kotlin|org\/xml|org\/w3c|dalvik)$/.test(parts[0]) || name.startsWith("org/xml/") || name.startsWith("org/w3c/") || name.startsWith("com/thelightphone/")) return null;
  if (parts[0] === "androidx" && parts[1] === "sqlite") return null; // SupportSQLite, by name
  if (SUPPLIED.test(name)) return null; // an Android-only part of a supplied library
  // io.ktor, androidx.media3, okhttp3...; com.google.x and the like by their third segment.
  const pkg = parts.slice(0, Math.max(1, parts.findIndex((p) => /^[A-Z]/.test(p)) === -1 ? parts.length - 1 : parts.findIndex((p) => /^[A-Z]/.test(p))));
  return pkg.slice(0, /^(com|org|net)$/.test(pkg[0]) && /^(google|squareup|jetbrains|apache)$/.test(pkg[1]) ? 3 : 2).join(".");
}

/** "a/b/C.m" -> "a.b.C.m"; its package is the segments before the first capitalised one. */
const dotted = (n) => n.replaceAll("/", ".");
function pkgOf(n) {
  if (n.includes("(")) return n.split(" ")[0];
  const parts = dotted(n).split(".");
  const cls = parts.findIndex((p) => /^[A-Z<]/.test(p));
  return (cls > 0 ? parts.slice(0, cls) : parts.slice(0, -1)).join(".");
}
for (const r of results) {
  console.log(`\n## ${r.tool}`);
  if (r.error) { console.log(`  scan failed: ${r.error}`); continue; }
  const names = Object.keys(r.missing);
  console.log(`missing: ${names.length} names; libraries only this tool adds: ${r.extraLibraries.length ? r.extraLibraries.join(", ") : "none"}`);
  const groups = new Map();
  for (const n of names) (groups.get(pkgOf(n)) ?? groups.set(pkgOf(n), []).get(pkgOf(n))).push(n);
  for (const [g, ns] of [...groups].sort()) {
    console.log(`  ${g} (${ns.length})`);
    for (const n of ns) {
      const users = r.missing[n].map((c) => c.split("/").at(-1));
      console.log(`    ${dotted(n)}  <- ${users.slice(0, 3).join(", ")}${users.length > 3 ? ` +${users.length - 3}` : ""}`);
    }
  }
}
if (results.length > 1) {
  const count = new Map();
  for (const r of results) for (const n of Object.keys(r.missing ?? {})) count.set(n, [...(count.get(n) ?? []), r.tool]);
  console.log(`\n## Ranked: missing names by how many tools use them`);
  for (const [n, ts] of [...count].sort((a, b) => b[1].length - a[1].length || a[0].localeCompare(b[0]))) {
    console.log(`  ${ts.length}  ${dotted(n)}  (${ts.join(", ")})`);
  }
}
if (jsonOut) writeFileSync(jsonOut, JSON.stringify(results, null, 2));
