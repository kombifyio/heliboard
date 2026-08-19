// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import android.inputmethodservice.InputMethodService
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection

/**
 * The whole SpeechKit patch surface, kept to one file plus three call sites in
 * [LatinIME].
 *
 * Upstream answers the voice key by handing the user off to another input
 * method (`switchToShortcutIme`). SpeechKit answers it in place: the keyboard
 * stays, a dictation panel takes over its window, and the text lands in the
 * same editor.
 *
 * The second difference is the action row above the keys. It is not a reaction
 * to a key press, so it cannot ride on the voice key: it has to be mounted
 * while the input view is being started and dropped when it finishes, which is
 * why the bridge carries the input view's lifecycle as well.
 *
 * The bridge deliberately declares its own [Host] interface instead of
 * depending on SpeechKit. Two reasons, and both are about keeping the fork
 * cheap to rebase onto an actively developed upstream:
 *
 * - No new Gradle dependency, so the fork still builds standalone exactly as
 *   upstream does. With no host installed the voice key behaves as it always
 *   did.
 * - Nothing here needs to know about sessions, providers or transports. The
 *   SpeechKit side adapts its own `VoiceInputHost` to this interface; the
 *   adapter lives on that side of the licence boundary.
 *
 * It lives in the `helium314.keyboard.latin` package on purpose: same package
 * as [LatinIME], so no call site needs an import and the diff against upstream
 * is one line per hook.
 */
object SpeechKitVoiceBridge {

    /** What a host must be able to do for this fork to defer to it. */
    interface Host {
        /**
         * Takes over the keyboard window for dictation into [inputConnection].
         *
         * Returns false when this editor must not receive voice input — a
         * password field, for instance — so the caller can fall back rather
         * than leave the key dead.
         */
        fun showPanel(
            service: InputMethodService,
            inputConnection: InputConnection,
            editorInfo: EditorInfo,
        ): Boolean

        /** Releases the editor and stops any capture. */
        fun hidePanel()

        /**
         * Fills and shows the `speechkit_action_row` container in the
         * keyboard's input view.
         *
         * The fork owns the container and its place in the layout; the host
         * owns everything drawn in it. The container is GONE until a host
         * shows it, so a keyboard with no host keeps upstream's geometry to
         * the pixel.
         *
         * Called from every `onStartInputView`, so it has to be idempotent.
         * That is the earliest hook that runs before the user presses
         * anything, which is what an always-visible row needs and what no
         * voice-key callback can offer.
         */
        fun attachActionRow(service: InputMethodService)

        /** Empties the action row container again and releases what fed it. */
        fun detachActionRow()
    }

    /**
     * Written from the SpeechKit side when its IME component starts, cleared
     * when it stops. Volatile because those are not necessarily the thread
     * that reads it on a key press.
     */
    @Volatile
    @JvmStatic
    var host: Host? = null

    /**
     * Handles the voice key, or reports that it did not.
     *
     * [fallback] runs whenever there is no host, the editor is not bound yet,
     * or the host refuses this editor — so the key keeps upstream's behaviour
     * in every case this fork does not improve on.
     */
    @JvmStatic
    fun onVoiceKey(service: InputMethodService, fallback: Runnable) {
        val current = host
        val connection = service.currentInputConnection
        val editor = service.currentInputEditorInfo
        val handled = current != null &&
            connection != null &&
            editor != null &&
            current.showPanel(service, connection, editor)
        if (!handled) fallback.run()
    }

    /**
     * Offers the input view to the host as it starts.
     *
     * The action row is the reason this exists: it is drawn before anything is
     * pressed, so nothing on the voice key's path can put it there. Passing
     * the service rather than the container keeps the fork out of the host's
     * view lookups, exactly as [onVoiceKey] does.
     *
     * A no-op with no host, so the fork still starts input views the way
     * upstream does.
     */
    @JvmStatic
    fun onStartInputView(service: InputMethodService) {
        host?.attachActionRow(service)
    }

    /**
     * Drops the panel and the action row when the input view goes away.
     *
     * Without the first, the panel stays installed as the input view, and the
     * next time the keyboard is asked to appear the user gets the dictation
     * panel instead of keys. Without the second, the row keeps a composition
     * alive against a window that is being torn down.
     */
    @JvmStatic
    fun onFinishInputView() {
        val current = host ?: return
        current.hidePanel()
        current.detachActionRow()
    }
}
