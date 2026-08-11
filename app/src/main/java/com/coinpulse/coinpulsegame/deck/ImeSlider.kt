package com.coinpulse.coinpulsegame.deck

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import com.coinpulse.coinpulsegame.BuildConfig
import com.coinpulse.coinpulsegame.strongbox.Locker

/**
 * Keeps the focused field clear of the keyboard by sliding the page, never by
 * shortening it.
 *
 * Shortening is where every simpler attempt goes wrong. Take height off a
 * WebView and the engine re-lays out the page, scrolls the focused field into
 * view against the layout it had a moment ago, then clamps that scroll to the
 * layout that finally lands; the last two steps disagree, and the disagreement
 * is a visible jerk. Adding a scroll of our own on top only gives the page two
 * things moving it at once, and their corrections add up rather than cancel.
 *
 * So: the view keeps its full height and is moved by a transform, and the
 * engine is told the keyboard does not exist. One hand on the wheel, one
 * compositor frame per move, no reflow.
 */
internal class ImeSlider(private val root: View, private val locker: Locker) {

    private var page: WebView? = null

    /** Focused field, in device pixels down the WebView. Negative means unknown. */
    private var fieldTop = -1f
    private var fieldFoot = -1f

    /** The page could only point at a frame, not at the field inside it. */
    private var insideFrame = false

    private var keyboard = 0
    private var riding = false

    /**
     * Where the keyboard will stop.
     *
     * Mid-animation the system reports heights well above the one it settles
     * on, and a slide that believes them overshoots and drops back at the very
     * end. The declared upper bound is no better: it is right the first time an
     * orientation is used and quietly wrong afterwards. A height an earlier
     * opening actually came to rest at has never lied, so that is what is kept,
     * per orientation, across runs.
     */
    private var declared = 0
    private var resting = 0

    private val reread = Runnable {
        page?.evaluateJavascript("window.${BuildConfig.MARK_IME}Ask && window.${BuildConfig.MARK_IME}Ask();", null)
    }

    // ── Wiring ──────────────────────────────────────────────────────────────

    /** Once, on the window root. */
    fun attach() {
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            if (!riding) {
                remember(insets.getInsets(WindowInsetsCompat.Type.ime()).bottom)
                slide(smooth = keyboard > 0)
                if (keyboard > 0) askAgain()
            }
            hideKeyboardFromPage(insets)
        }

        ViewCompat.setWindowInsetsAnimationCallback(
            root,
            object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_STOP) {

                override fun onPrepare(animation: WindowInsetsAnimationCompat) {
                    if (isKeyboard(animation)) riding = true
                }

                override fun onStart(
                    animation: WindowInsetsAnimationCompat,
                    bounds: WindowInsetsAnimationCompat.BoundsCompat
                ): WindowInsetsAnimationCompat.BoundsCompat {
                    if (isKeyboard(animation)) declared = bounds.upperBound.bottom
                    return bounds
                }

                override fun onProgress(
                    state: WindowInsetsCompat,
                    inFlight: MutableList<WindowInsetsAnimationCompat>
                ): WindowInsetsCompat {
                    if (riding) {
                        rideTo(state.getInsets(WindowInsetsCompat.Type.ime()).bottom)
                        // Assigned, never animated: the pan has to move in step
                        // with the keyboard rather than chase it a frame behind.
                        slide(smooth = false)
                    }
                    return state
                }

                override fun onEnd(animation: WindowInsetsAnimationCompat) {
                    if (!isKeyboard(animation)) return
                    riding = false
                    declared = 0
                    ViewCompat.requestApplyInsets(root)
                    // The page may have moved the field while the keyboard was
                    // on its way up; this reading has the last word.
                    if (keyboard > 0) askAgain() else slide(smooth = false)
                }

                private fun isKeyboard(animation: WindowInsetsAnimationCompat) =
                    animation.typeMask and WindowInsetsCompat.Type.ime() != 0
            }
        )

        ViewCompat.requestApplyInsets(root)
    }

    /** For every WebView put on screen, replacements after a renderer death included. */
    @SuppressLint("JavascriptInterface")
    fun follow(view: WebView) {
        page = view
        forgetField()
        view.translationY = 0f
        view.addJavascriptInterface(Reporter(), BuildConfig.BRIDGE_ID)
    }

    /** A fresh page has nothing focused. Call from onPageStarted. */
    fun forgetField() {
        root.removeCallbacks(reread)
        fieldTop = -1f
        fieldFoot = -1f
        insideFrame = false
        slide(smooth = false)
    }

    /** After a rotation the pixels belong to the old viewport and the keyboard to the old shape. */
    fun reorient() {
        forgetField()
        resting = 0
        if (keyboard > 0) askAgain()
    }

    // ── Heights ─────────────────────────────────────────────────────────────

    private fun askAgain() {
        root.removeCallbacks(reread)
        root.postDelayed(reread, SETTLE_MS)
    }

    private fun remember(height: Int) {
        keyboard = height
        if (height <= 0 || height == resting) return
        resting = height
        locker.rememberKeyboardRest(upright(), height)
    }

    private fun rideTo(height: Int) {
        val ceiling = restingHeight()
        keyboard = if (ceiling > 0) minOf(height, ceiling) else height
    }

    private fun restingHeight(): Int {
        if (resting <= 0) {
            val kept = locker.keyboardRest(upright())
            // A stored height at least as tall as the window belongs to another
            // device or another window and is no use here.
            if (kept in 1 until root.height) resting = kept
        }
        return if (resting > 0) resting else declared
    }

    private fun upright(): Boolean =
        root.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE

    /**
     * Rebuilds the insets with the keyboard taken out before they reach the
     * page. Left in, the engine acts on them and hauls the page up by an amount
     * that differs from one opening to the next on the very same field.
     */
    private fun hideKeyboardFromPage(arriving: WindowInsetsCompat): WindowInsetsCompat =
        runCatching {
            val ime = WindowInsetsCompat.Type.ime()
            WindowInsetsCompat.Builder(arriving)
                .setInsets(ime, Insets.NONE)
                .setVisible(ime, false)
                .build()
        }.getOrDefault(arriving)

    // ── The slide ───────────────────────────────────────────────────────────

    private fun slide(smooth: Boolean) {
        val view = page ?: return
        val target = -lift(view)
        view.animate().cancel()
        if (smooth && view.translationY != target) {
            view.animate().translationY(target).setDuration(SLIDE_MS).start()
        } else {
            view.translationY = target
        }
    }

    private fun lift(view: View): Float {
        val height = keyboard
        val span = view.height
        if (height <= 0 || span <= 0 || fieldFoot < 0f) return 0f

        val aimAt: Float
        val most: Float
        if (insideFrame) {
            // Somewhere in that frame is a field we cannot measure, so the
            // frame's own foot is the target — but never so far that its head
            // leaves the screen, because the field may be up there.
            aimAt = fieldFoot
            most = minOf(height.toFloat(), maxOf(0f, fieldTop))
        } else {
            // A tall field is aimed at by its head, where the caret starts.
            // Hauling a long box up by its foot takes its head off screen.
            aimAt = minOf(fieldFoot, fieldTop + HEAD_ROOM_DP * view.resources.displayMetrics.density)
            most = height.toFloat()
        }
        return (aimAt - (span - height)).coerceIn(0f, most)
    }

    private inner class Reporter {
        /**
         * @param framed the page could only point at a nested frame.
         * @param top top edge of the field, device pixels down the WebView, with
         *   anything the page has already done to the viewport folded in.
         */
        @JavascriptInterface
        fun at(framed: Boolean, top: Double, foot: Double) {
            val view = page ?: return
            view.post {
                insideFrame = framed
                fieldTop = top.toFloat()
                fieldFoot = foot.toFloat()
                if (keyboard > 0) slide(smooth = !riding)
            }
        }
    }

    /**
     * Asks the page where the focused field is, in device pixels — deliberately
     * not as a share of the viewport, because the viewport is the one thing that
     * can change out from under the measurement. Injected on every finished page.
     */
    val probe: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val mark = BuildConfig.MARK_IME
        val seen = mark + "Seen"
        val bridge = BuildConfig.BRIDGE_ID
        """
        (function(){
          if (window.$mark) return;
          window.$mark = true;

          var typable = function(node){
            if (!node) return false;
            if (node.tagName === 'INPUT') {
              var kind = (node.type || 'text').toLowerCase();
              return ['checkbox','radio','button','submit','reset','file','range','image','color']
                       .indexOf(kind) === -1;
            }
            return node.tagName === 'TEXTAREA' || node.isContentEditable === true;
          };

          var rectOf = function(node, view){
            if (node.isContentEditable) {
              try {
                var picked = view.getSelection();
                if (picked && picked.rangeCount) {
                  var caret = picked.getRangeAt(0).getBoundingClientRect();
                  if (caret && caret.height > 0) return caret;
                }
              } catch (e) {}
            }
            return node.getBoundingClientRect();
          };

          var listen = function(doc){
            try {
              if (!doc || doc.$seen) return;
              doc.$seen = true;
              doc.addEventListener('focusin', nudge, true);
            } catch (e) {}
          };

          var find = function(){
            var node = document.activeElement, view = window, shift = 0, depth = 0;
            while (node && (node.tagName === 'IFRAME' || node.tagName === 'FRAME') && depth++ < 4) {
              var frame = node.getBoundingClientRect(), doc = null;
              try { doc = node.contentDocument; } catch (e) { doc = null; }
              var inner = doc ? doc.activeElement : null;
              if (!inner || inner === doc.body) {
                return { framed: true, top: shift + frame.top, foot: shift + frame.bottom };
              }
              listen(doc);
              view = node.contentWindow || view;
              shift += frame.top;
              node = inner;
            }
            if (!typable(node)) return null;
            var box = rectOf(node, view);
            return { framed: false, top: shift + box.top, foot: shift + box.bottom };
          };

          var tell = function(){
            var spot = find();
            if (!spot) return;
            var visual = window.visualViewport;
            var raised = visual ? visual.offsetTop : 0;
            var zoom = (visual && visual.scale) ? visual.scale : 1;
            var scale = (window.devicePixelRatio || 1) * zoom;
            try {
              $bridge.at(spot.framed,
                         (spot.top - raised) * scale,
                         (spot.foot - raised + $BREATH_CSS) * scale);
            } catch (e) {}
          };

          var nudge = function(){ tell(); setTimeout(tell, 200); };

          var waiting = false;
          var soon = function(){
            if (waiting) return;
            waiting = true;
            var run = function(){ waiting = false; tell(); };
            if (window.requestAnimationFrame) requestAnimationFrame(run); else setTimeout(run, 16);
          };
          if (window.visualViewport) {
            window.visualViewport.addEventListener('resize', soon);
            window.visualViewport.addEventListener('scroll', soon);
          }

          window.${mark}Ask = tell;
          listen(document);
        })();
        """.trimIndent()
    }

    private companion object {
        /** Breathing room under the field, in CSS pixels. */
        const val BREATH_CSS = 10

        /** How much of a tall field has to stay visible for typing to make sense. */
        const val HEAD_ROOM_DP = 96f

        /** Grace for the page to finish any scrolling of its own before it is read. */
        const val SETTLE_MS = 140L

        const val SLIDE_MS = 160L
    }
}
