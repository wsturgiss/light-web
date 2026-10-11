package lightweb.shell

/**
 * In the LP3 emulator, the mouse is a finger: pressing and dragging scrolls a list or spins a
 * number wheel, as a drag does on the phone.
 *
 * Compose only scrolls on touch (and the mouse wheel); a mouse drag over a list does nothing,
 * which on a desktop is what people expect and on a phone screen isn't. Compose decides from
 * the pointer event's `pointerType`, so while the left button is down, mouse pointer events
 * aimed at the canvas are stopped and sent again as a touch. Hovering, other buttons, and
 * everything in [lightweb.DeviceMode.Computer] are left alone (there, a drag selects text).
 *
 * The rest of the press goes to the canvas it started on, even if the mouse leaves it, as a
 * finger's touches stay with the view they started on.
 */
object MouseAsTouch {
    fun install() = installListeners()

    var enabled: Boolean = false
        set(value) {
            field = value
            setEnabled(value)
        }
}

private fun setEnabled(on: Boolean): Unit = js("globalThis.lightwebMouseAsTouch = on")

private fun installListeners(): Unit = js(
    """{
        let target = null;
        for (const type of ["pointerdown", "pointermove", "pointerup", "pointercancel"]) {
            window.addEventListener(type, (e) => {
                if (!e.isTrusted || e.pointerType !== "mouse") return;
                if (type === "pointerdown") {
                    const t = e.composedPath()[0];
                    if (!globalThis.lightwebMouseAsTouch || e.button !== 0 || !(t instanceof HTMLCanvasElement)) return;
                    target = t;
                } else if (!target) return;
                e.stopImmediatePropagation();
                e.preventDefault();
                const down = type === "pointerdown" || type === "pointermove";
                target.dispatchEvent(new PointerEvent(type, {
                    bubbles: true, cancelable: true, composed: true,
                    pointerId: 4242, pointerType: "touch", isPrimary: true,
                    clientX: e.clientX, clientY: e.clientY, screenX: e.screenX, screenY: e.screenY,
                    button: type === "pointermove" ? -1 : 0, buttons: down ? 1 : 0,
                    width: 1, height: 1, pressure: down ? 0.5 : 0,
                    ctrlKey: e.ctrlKey, shiftKey: e.shiftKey, altKey: e.altKey, metaKey: e.metaKey,
                }));
                if (!down) target = null;
            }, true);
        }
    }"""
)
