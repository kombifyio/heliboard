// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import android.graphics.drawable.Drawable
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
         * The icon to draw on one of SpeechKit's toolbar keys, or null to keep
         * the fork's own.
         *
         * The host owns this because the choice is the user's: which glyph
         * stands for which mode is a SpeechKit setting, and the fork has no
         * business carrying that preference or the drawables behind it. Asked
         * on every key build, so it must be cheap and must not touch the
         * network.
         */
        fun iconFor(action: String): Drawable?

        /** Tells the host which input method service is current. */
        fun onInputViewStarted(service: InputMethodService)

        /**
         * Drops the action row when the input view goes away.
         *
         * Default empty so a standalone build, and any host that only answers
         * the voice key, still compiles. The SpeechKit host fills this in.
         */
        fun onInputViewFinished() {}

        /**
         * Handles one of SpeechKit's own toolbar keys.
         *
         * The keyboard, not SpeechKit, owns the toolbar the key sits in, so
         * the fork asks rather than hands over a container: it reports which
         * of its own keys was pressed and lets the host decide what that
         * means. [onStartInputView] has already told the host which service
         * is current, so nothing has to be threaded through here.
         *
         * Returns null when the action was taken, and a short reason to show
         * the user when it was refused - a key that needs a paired server,
         * say. The reason is the host's to word, because the host is the
         * side that knows why, and this fork carries no SpeechKit strings.
         */
        fun onToolbarAction(action: String): String?

        /**
         * Opens the more/shortcuts page over the keys (Gboard's G-logo page).
         *
         * Returns false when this host has no such page, so the fork falls
         * back to expanding its own toolbar. A second call while the page is
         * open closes it.
         */
        fun showShortcuts(service: InputMethodService): Boolean = false
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
        host?.onInputViewStarted(service)
    }

    /**
     * Drops the panel and the action row when the input view goes away.
     *
     * Without the first, the panel stays installed as the input view, and the
     * next time the keyboard is asked to appear the user gets the dictation
     * panel instead of keys. Without the second, the row keeps a composition
     * alive against a window that is being torn down.
     */
    /**
     * Answers one of SpeechKit's toolbar keys, or reports that it did not.
     *
     * Returns false when no host is installed, which is what keeps these keys
     * inert in a standalone build: the fork maps them to KeyCode.UNSPECIFIED,
     * so nothing happens and nothing crashes. A non-null [reason] is a short
     * message the caller should show; the fork words nothing itself.
     */
    /**
     * The host's icon for [action], or null when there is no host or it has
     * no opinion - in which case the fork draws the glyph it ships with.
     */
    @JvmStatic
    fun iconFor(action: String): Drawable? = host?.iconFor(action)

    @JvmStatic
    fun onToolbarAction(action: String, reason: (String) -> Unit): Boolean {
        val current = host ?: return false
        val refusal = current.onToolbarAction(action)
        if (refusal != null) reason(refusal)
        return true
    }

    @JvmStatic
    fun onFinishInputView() {
        host?.hidePanel()
        host?.onInputViewFinished()
    }

    /**
     * Opens the host's shortcuts page, or reports that it did not.
     *
     * [false] keeps upstream's expand-toolbar behaviour for a standalone
     * build and for any host that has not implemented the page.
     */
    @JvmStatic
    fun onMore(service: InputMethodService): Boolean =
        host?.showShortcuts(service) == true
}
