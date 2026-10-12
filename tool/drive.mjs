// Drives a headless Chromium over CDP, for checking light-web by hand without a real browser.
// Needs nothing installed beyond Node and Chromium.
//
//   chromium --headless=new --remote-debugging-port=9333 --window-size=1000,900 \
//     --force-device-scale-factor=1 --user-data-dir=<dir> --enable-unsafe-swiftshader about:blank &
//   node tool/drive.mjs open:http://localhost:8765/ wait:4000 shot:home click:500,300 ...
//
// It attaches to that Chromium and leaves it running, so the page (and its localStorage) carries
// over from one run to the next. Screenshots go to $DRIVE_OUT (default: the current directory).
//
// Steps:
//   open:URL           navigate, then wait for the page to settle
//   click:X,Y          left click at page coordinates
//   drag:X1,Y1,X2,Y2   press, move in steps, release (wheel pickers, lists)
//   swipe:X1,Y1,X2,Y2  the same as a touch
//   wheel:X,Y,DY       mouse wheel at X,Y
//   key:Name           a key by its KeyboardEvent.key (Escape, Enter, Backspace, ArrowUp...)
//   type:TEXT          type TEXT one character at a time
//   wait:MS            sleep
//   shot:NAME          screenshot to NAME.png
//   clip:X,Y,W,H       limit later screenshots to that rectangle (clip: alone clears it)
//   eval:JS            evaluate JS in the page and print the result
//   tz:ZONE            override the time zone (e.g. America/New_York), before open:
// Console output and uncaught exceptions are printed as they arrive.
import { writeFileSync } from "node:fs";
import { join } from "node:path";

const port = Number(process.env.DRIVE_PORT ?? 9333);
const out = process.env.DRIVE_OUT ?? process.cwd();
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

const targets = await (await fetch(`http://127.0.0.1:${port}/json`)).json();
const page = targets.find((t) => t.type === "page");
const ws = new WebSocket(page.webSocketDebuggerUrl);
await new Promise((r) => ws.addEventListener("open", r));

let id = 0;
const pending = new Map();
const t0 = Date.now();
const log = (s) => console.log(`[${((Date.now() - t0) / 1000).toFixed(1)}s] ${s}`);
ws.addEventListener("message", ({ data }) => {
  const m = JSON.parse(data);
  if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); return; }
  if (m.method === "Runtime.consoleAPICalled")
    log(`console.${m.params.type}: ${m.params.args.map((a) => a.value ?? a.description).join(" ").slice(0, 800)}`);
  if (m.method === "Runtime.exceptionThrown") {
    const d = m.params.exceptionDetails;
    log(`EXCEPTION: ${(d.exception?.description ?? d.text).slice(0, 6000)}`);
  }
});
const send = (method, params = {}) => new Promise((r) => {
  const i = ++id; pending.set(i, r); ws.send(JSON.stringify({ id: i, method, params }));
});
const mouse = (type, x, y, extra = {}) =>
  send("Input.dispatchMouseEvent", { type, x, y, button: "left", clickCount: 1, ...extra });

// Virtual key codes for the named keys tools listen for; printable keys carry their own text.
const keyCodes = { Escape: 27, Enter: 13, Backspace: 8, Tab: 9, ArrowLeft: 37, ArrowUp: 38, ArrowRight: 39, ArrowDown: 40, " ": 32 };
async function key(name) {
  const printable = name.length === 1;
  const code = keyCodes[name] ?? (printable ? name.toUpperCase().charCodeAt(0) : 0);
  const text = printable ? name : name === "Enter" ? "\r" : undefined;
  await send("Input.dispatchKeyEvent", { type: text ? "keyDown" : "rawKeyDown", key: name, windowsVirtualKeyCode: code, text });
  await send("Input.dispatchKeyEvent", { type: "keyUp", key: name, windowsVirtualKeyCode: code });
}

let clip;
await send("Runtime.enable");
await send("Page.enable");
for (const step of process.argv.slice(2)) {
  const colon = step.indexOf(":");
  const [kind, arg] = colon < 0 ? [step, ""] : [step.slice(0, colon), step.slice(colon + 1)];
  const nums = () => arg.split(",").map(Number);
  log(`> ${step}`);
  if (kind === "open") { await send("Page.navigate", { url: arg }); await sleep(4000); }
  else if (kind === "click") { const [x, y] = nums(); await mouse("mousePressed", x, y); await sleep(60); await mouse("mouseReleased", x, y); }
  else if (kind === "drag") {
    const [x1, y1, x2, y2] = nums();
    // Hover first, as a real mouse would: pressing where the pointer has never been makes the
    // browser send a "pointer entered" with the button already down, which Compose takes for a tap.
    await mouse("mouseMoved", x1, y1, { button: "none" });
    await mouse("mousePressed", x1, y1);
    for (let i = 1; i <= 12; i++) { await mouse("mouseMoved", x1 + (x2 - x1) * i / 12, y1 + (y2 - y1) * i / 12); await sleep(16); }
    await mouse("mouseReleased", x2, y2);
  }
  else if (kind === "swipe") {
    const [x1, y1, x2, y2] = nums();
    const touch = (type, x, y) => send("Input.dispatchTouchEvent", { type, touchPoints: type === "touchEnd" ? [] : [{ x, y }] });
    await touch("touchStart", x1, y1);
    for (let i = 1; i <= 12; i++) { await touch("touchMove", x1 + (x2 - x1) * i / 12, y1 + (y2 - y1) * i / 12); await sleep(16); }
    await touch("touchEnd");
  }
  else if (kind === "wheel") { const [x, y, dy] = nums(); await send("Input.dispatchMouseEvent", { type: "mouseWheel", x, y, deltaX: 0, deltaY: dy }); }
  else if (kind === "key") await key(arg);
  else if (kind === "type") for (const c of arg) { await key(c); await sleep(30); }
  else if (kind === "wait") await sleep(Number(arg));
  else if (kind === "clip") { const [x, y, width, height] = nums(); clip = arg ? { x, y, width, height, scale: 1 } : undefined; }
  else if (kind === "tz") await send("Emulation.setTimezoneOverride", { timezoneId: arg });
  else if (kind === "shot") {
    const r = await Promise.race([send("Page.captureScreenshot", { format: "png", ...(clip && { clip }) }), sleep(10000).then(() => null)]);
    if (r?.result) { writeFileSync(join(out, `${arg}.png`), Buffer.from(r.result.data, "base64")); log(`saved ${arg}.png`); }
    else log("screenshot timed out");
  } else if (kind === "eval") {
    const r = await send("Runtime.evaluate", { expression: arg, returnByValue: true, awaitPromise: true });
    log(`= ${JSON.stringify(r.result?.result?.value ?? r.result)}`);
  } else throw new Error(`unknown step: ${step}`);
  await sleep(400);
}
ws.close();
