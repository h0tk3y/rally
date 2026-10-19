package com.h0tk3y.rally.android.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import android.annotation.SuppressLint
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.h0tk3y.rally.BuildConfig
import com.h0tk3y.rally.SpeedKmh
import java.io.File
import java.lang.ref.WeakReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A mode for taking the user guide's screenshots in debug builds. It hides the simulation slider and shows the
 * simulated distance source as a working OBD adapter, and it lets the simulated speed be set without the slider.
 *
 * Debug builds only: it is driven by a broadcast that only adb's shell may send (it holds the DUMP permission):
 * ```
 * adb shell am broadcast -a com.h0tk3y.rally.debug.SCREENSHOT_MODE --ez on true --ei speed 53
 * ```
 * Both extras are optional. The state lives in memory, so it ends with the app's process.
 *
 * `--es dump <file name>` writes the app's UI tree to `cache/<file name>` in the format of `uiautomator dump`. It is much
 * faster than `uiautomator dump`, which waits for the screen to settle, and the race panel's clock never lets it.
 */
object ScreenshotMode {
    const val ACTION = "com.h0tk3y.rally.debug.SCREENSHOT_MODE"

    private val _isOn = MutableStateFlow(false)
    val isOn: StateFlow<Boolean> = _isOn.asStateFlow()

    /** The simulated speed requested by the last broadcast that had one; the race service applies it. */
    private val _simulatedSpeed = MutableStateFlow<SpeedKmh?>(null)
    val simulatedSpeed: StateFlow<SpeedKmh?> = _simulatedSpeed.asStateFlow()

    private var isRegistered = false

    /** Every Compose root of the process, including the windows of dialogs and menus. */
    private val roots = mutableListOf<WeakReference<ViewRootForTest>>()

    /** Registers the receiver once per process, in debug builds; release builds never turn the mode on. */
    @SuppressLint("VisibleForTests")
    fun registerReceiver(context: Context) {
        if (!BuildConfig.DEBUG || isRegistered) return
        isRegistered = true
        ViewRootForTest.onViewCreatedCallback = { root -> roots.add(WeakReference(root)) }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                intent.getStringExtra("dump")?.let { name ->
                    File(context.cacheDir, name).writeText(dumpUiTree())
                }
                if (intent.hasExtra("on")) {
                    _isOn.value = intent.getBooleanExtra("on", false)
                }
                if (intent.hasExtra("speed")) {
                    _simulatedSpeed.value = SpeedKmh(intent.getIntExtra("speed", 0).toDouble())
                }
            }
        }
        ContextCompat.registerReceiver(
            context.applicationContext,
            receiver,
            IntentFilter(ACTION),
            android.Manifest.permission.DUMP,
            null,
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    /** The semantics of all live Compose roots, as `uiautomator dump` XML with bounds on the screen. */
    private fun dumpUiTree(): String = buildString {
        append("<?xml version='1.0' encoding='UTF-8' standalone='yes' ?><hierarchy rotation=\"0\">")
        roots.removeAll { it.get()?.view?.isAttachedToWindow != true }
        for (root in roots.mapNotNull { it.get() }) {
            val location = IntArray(2).also { root.view.getLocationOnScreen(it) }
            for (node in root.semanticsOwner.getAllSemanticsNodesSafe()) {
                append(nodeXml(node, location[0], location[1]))
            }
        }
        append("</hierarchy>")
    }

    private fun androidx.compose.ui.semantics.SemanticsOwner.getAllSemanticsNodesSafe(): List<SemanticsNode> {
        val out = mutableListOf<SemanticsNode>()
        fun visit(node: SemanticsNode) {
            out.add(node)
            node.children.forEach(::visit)
        }
        visit(unmergedRootSemanticsNode)
        return out
    }

    private fun nodeXml(node: SemanticsNode, dx: Int, dy: Int): String {
        val config = node.config
        val editable = config.getOrNull(SemanticsProperties.EditableText)?.text
        val text = editable ?: config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text } ?: ""
        val desc = config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ") ?: ""
        val role = config.getOrNull(SemanticsProperties.Role)
        val cls = when {
            editable != null -> "EditText"
            role == Role.Button -> "Button"
            role == Role.Image -> "ImageView"
            text.isNotEmpty() -> "TextView"
            else -> "View"
        }
        val clickable = SemanticsActions.OnClick in config
        val focused = config.getOrNull(SemanticsProperties.Focused) == true
        val b = node.boundsInRoot
        fun esc(v: String) = v.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")
        return "<node text=\"${esc(text)}\" content-desc=\"${esc(desc)}\" class=\"android.widget.$cls\" " +
            "clickable=\"$clickable\" focused=\"$focused\" " +
            "bounds=\"[${b.left.toInt() + dx},${b.top.toInt() + dy}][${b.right.toInt() + dx},${b.bottom.toInt() + dy}]\" />"
    }
}
