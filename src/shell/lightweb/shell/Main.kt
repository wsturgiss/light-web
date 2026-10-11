package lightweb.shell

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    CrashGuard.install()
    ComposeViewport(document.body!!) {
        LightOsShell()
    }
}
