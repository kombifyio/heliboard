// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import android.inputmethodservice.InputMethodService
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection

/**
 * The whole SpeechKit patch surface, kept to one file plus one call site in
 * [LatinIME].
 *
 * Upstream answers the voice key by handing the user off to another input
 * method (`switchToShortcutIme`). SpeechKit answers it in place: the keyboard
 * stays, a dictation panel takes over its window, and the text lands in the
 * same editor. That is the only behavioural difference this fork carries.
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
 * as [LatinIME], so the call site needs no import and the diff against
 * upstream is a single line.
 */
object SpeechKitVoiceBridge {

    /** What a host must be able to do for the voice key to be handled here. */
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
     * Drops the panel when the input view goes away.
     *
     * Without this the panel stays installed as the input view, and the next
     * time the keyboard is asked to appear the user gets the dictation panel
     * instead of keys.
     */
    @JvmStatic
    fun onFinishInputView() {
        host?.hidePanel()
    }
}
