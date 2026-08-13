package com.coinpulse.coinpulsegame.deck

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.coinpulse.coinpulsegame.BuildConfig
import com.coinpulse.coinpulsegame.charter.Agent
import com.coinpulse.coinpulsegame.charter.Charter
import com.coinpulse.coinpulsegame.charter.Echo
import com.coinpulse.coinpulsegame.charter.HostGate
import com.coinpulse.coinpulsegame.dispatchbox.LiveLink
import com.coinpulse.coinpulsegame.relaynet.LinkWatch
import com.coinpulse.coinpulsegame.strongbox.Locker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The shell the page lives in.
 *
 * Black at every layer, so no system white or the launch preview window can
 * flash through while the engine warms up. Padded away from the cutout in both
 * orientations. Panned rather than resized when the keyboard opens. Every URL
 * scheme the WebView cannot take is handed to the system instead of producing
 * an unknown-scheme error page.
 *
 * The one thing here that regularly gets designed backwards is the cover. It
 * belongs over an *empty* view — the session's first page, where there is
 * nothing underneath to look at. Every later navigation, a redirect hop
 * included, resolves behind the page the user is already reading, because that
 * page is a better thing to look at than a scrim and an affiliate click fires
 * several navigations in a row.
 */
class WebDeck : AppCompatActivity() {

    private lateinit var frame: FrameLayout
    private lateinit var page: WebView
    private lateinit var locker: Locker
    private lateinit var link: LinkWatch
    private lateinit var slider: ImeSlider

    private val scope = CoroutineScope(Dispatchers.Main)

    /** The last main-frame URL that actually settled. What a renderer recovery reloads. */
    private var settledPage: String? = null

    /**
     * The deepest main-frame URL seen, settled or not. A redirect loop resumes
     * from here: reloading the chain's entry point walks the same hops into the
     * same loop and spends the budget on nothing.
     */
    private var deepestHop: String? = null

    private var hopsRetried = 0
    private var entryRetried = false
    private var renderRecoveries = 0

    /** A failed load still reaches onPageFinished, with the error page committed. */
    private var loadFailed = false
    private var retryQueued = false
    private var firstPageDone = false

    @Volatile private var walkedOffline = false

    private var chooser: ValueCallback<Array<Uri>>? = null

    private val filePicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val waiting = chooser ?: return@registerForActivityResult
        chooser = null

        val uris: Array<Uri> = if (result.resultCode != Activity.RESULT_OK) {
            emptyArray()
        } else {
            WebChromeClient.FileChooserParams.parseResult(
                result.resultCode, result.data
            ) ?: emptyArray()
        }
        waiting.onReceiveValue(uris)
    }

    // ── Life cycle ──────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        locker = Locker(applicationContext)
        link = LinkWatch(applicationContext)
        LiveLink.shellExists = true

        frame = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            fitsSystemWindows = false
        }
        setContentView(frame)

        EdgeFit.immerse(this)
        EdgeFit.spanCutout(this)
        keepOffTheCutout()

        slider = ImeSlider(window.decorView, locker)
        slider.attach()
        buildPage()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // One step at a time through the WebView's own history, the
                // way the Flutter reference does it: canGoBack() gates the
                // move, and the first page swallows the press so the shell
                // does not close under the user. Sub-pages and test menus
                // therefore step back naturally rather than jumping to the
                // entry URL on a single press.
                if (page.canGoBack()) page.goBack()
            }
        })

        val opening = openingUrl()
        if (opening.isNullOrBlank()) {
            Echo.odd(TAG, "nothing to open — leaving")
            finish()
            return
        }
        page.loadUrl(opening)

        watchTheLink()
        scope.launch {
            delay(Charter.Wait.edgeInjection)
            neutraliseSafeAreaCss()
        }
    }

    private fun openingUrl(): String? {
        val warm = intent.takeIf { it.getBooleanExtra(EXTRA_PUSH_WARM, false) }
            ?.getStringExtra(EXTRA_PUSH_URL)
        val parked = locker.takeParkedPush()
        val chosen = warm ?: parked ?: intent.getStringExtra(EXTRA_PAGE_URL) ?: locker.target
        Echo.note(TAG, "opening (warm=${warm != null}, parked=${parked != null})")
        return chosen
    }

    override fun onStart() {
        super.onStart()
        walkedOffline = false
        LiveLink.listener = { url ->
            runOnUiThread {
                Echo.note(TAG, "push handed to the live shell")
                runCatching { page.loadUrl(url) }
            }
        }
        // A tap that arrived while this shell was in the background was queued
        // rather than delivered, because the listener above did not exist then.
        LiveLink.collect()?.let { queued ->
            Echo.note(TAG, "queued push collected")
            runCatching { page.loadUrl(queued) }
        }
    }

    override fun onStop() {
        LiveLink.listener = null
        super.onStop()
    }

    override fun onDestroy() {
        LiveLink.listener = null
        LiveLink.shellExists = false
        scope.cancel()
        runCatching { page.destroy() }
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        walkedOffline = false

        if (intent.getBooleanExtra(EXTRA_PUSH_WARM, false)) {
            val pushed = intent.getStringExtra(EXTRA_PUSH_URL)
            if (!pushed.isNullOrBlank() && HostGate.permits(pushed)) {
                Echo.note(TAG, "warm push → loading")
                page.loadUrl(pushed)
                return
            }
        }

        // Coming back from the offline board: the view was blanked on the way
        // out, so without this the user returns to an empty black screen.
        val wanted = intent.getStringExtra(EXTRA_PAGE_URL) ?: locker.target
        val showing = page.url
        if (!wanted.isNullOrBlank() &&
            (showing.isNullOrBlank() || showing == BLANK || showing != wanted)
        ) {
            Echo.note(TAG, "new intent → reloading")
            page.loadUrl(wanted)
        }
    }

    // ── The view itself ─────────────────────────────────────────────────────

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildPage() {
        page = WebView(this).apply {
            val rules: WebSettings = settings

            // Who we are. The same string the endpoint was asked with: a
            // cashier that keys the session on the UA drops it otherwise.
            rules.userAgentString = Agent.line

            // What the page is allowed to run and keep.
            rules.javaScriptEnabled = true
            rules.javaScriptCanOpenWindowsAutomatically = true
            rules.domStorageEnabled = true
            rules.cacheMode = WebSettings.LOAD_DEFAULT

            // What it is allowed to fetch. Partner pages routinely serve their
            // assets over plain http from an https document.
            rules.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            rules.loadsImagesAutomatically = true
            rules.blockNetworkImage = false
            rules.allowFileAccess = true
            rules.allowContentAccess = true

            // Media that starts on its own, as a promo page expects.
            rules.mediaPlaybackRequiresUserGesture = false

            // Multiple windows stay off. With them on, a target=_blank asks for
            // a host window, and answering that with this same view throws
            // "Parent WebView cannot host its own popup window". Off, the
            // navigation simply happens in place.
            rules.setSupportMultipleWindows(false)

            // Pinch-zoom belongs to a document, not to a product page.
            rules.setSupportZoom(false)
            rules.builtInZoomControls = false
            rules.displayZoomControls = false
            setBackgroundColor(Color.BLACK)
            isHorizontalScrollBarEnabled = false
            isVerticalScrollBarEnabled = false
            webViewClient = pageClient()
            webChromeClient = chromeClient()
        }

        frame.addView(
            page,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(page, true)
        }
        slider.follow(page)
    }

    /** Rebuilds the view after a renderer death and puts the last good page back. */
    private fun rebuildPage() {
        val resumeAt = settledPage ?: locker.target ?: return
        val dead = page
        frame.removeView(dead)
        runCatching { dead.destroy() }
        buildPage()
        page.loadUrl(resumeAt)
    }

    // ── Clients ─────────────────────────────────────────────────────────────

    private fun pageClient() = object : WebViewClient() {

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            return when (val scheme = url.substringBefore(':').lowercase()) {
                in PAGE_SCHEMES -> {
                    if (request.isForMainFrame) deepestHop = url
                    false
                }

                "intent" -> {
                    handOverIntentUri(url)
                    true
                }
                // Everything else belongs to some app: banks, wallets,
                // messengers, stores. Listing the ones worth knowing about is a
                // game with no end, and a WebView handed one of them can only
                // show an unknown-scheme error.
                else -> {
                    Echo.note(TAG, "handing $scheme: to the system")
                    handOverToSystem(url)
                    true
                }
            }
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            loadFailed = false
            // retryQueued is *not* cleared here. A partner's chain is longer
            // than Chromium will follow in one navigation, and the retry that
            // resumes it also goes through onPageStarted before it either
            // settles or trips the same limit again — clearing the flag now
            // would let the cover come down between two hops of the same
            // chain and flash the error document that lives underneath.
            slider.forgetField()
            // shouldOverrideUrlLoading does not see every server-side 30x, so
            // the URL the engine committed to is the other half of the trail.
            if (url != BLANK) deepestHop = url
            // Cover every navigation start, not just the first. Without this,
            // clicking a link after the first page is done leaves Chromium
            // visible during the load — including the ERR_TOO_MANY_REDIRECTS
            // document that appears for one frame before resumeRedirectChain
            // raises it. The cover is lightweight (cancelled immediately on
            // fast loads), so the cost on normal navigations is one frame of
            // dark background.
            if (url != BLANK) cover.raise()
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError
        ) {
            if (!request.isForMainFrame) return
            loadFailed = true

            val code = error.errorCode
            val text = error.description?.toString().orEmpty()
            Echo.odd(TAG, "main frame error $code")

            // The URL was already handed to the system, so the page behind this
            // is perfectly fine — give it straight back.
            if (code == ERROR_UNSUPPORTED_SCHEME) {
                retryQueued = false
                cover.drop(0L, force = true)
                return
            }

            if (code == ERROR_REDIRECT_LOOP || code == NET_TOO_MANY_REDIRECTS ||
                text.contains("too_many", ignoreCase = true)
            ) {
                resumeRedirectChain(view, request.url.toString())
                return
            }

            if (code in NETWORK_ERRORS || !link.up()) {
                view.stopLoading()
                view.loadUrl(BLANK)
                walkToOfflineBoard()
                return
            }

            // Anything else is a page that is what it is. No branch of this
            // handler may end without either going somewhere or letting go.
            retryQueued = false
            cover.drop(0L, force = true)
        }

        override fun onPageFinished(view: WebView, url: String) {
            if (loadFailed || url == BLANK) return
            hopsRetried = 0
            entryRetried = false
            retryQueued = false
            firstPageDone = true
            settledPage = url
            deepestHop = url
            neutraliseSafeAreaCss()
            view.evaluateJavascript(slider.probe, null)
            cover.drop()
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            Echo.odd(TAG, "renderer gone, crashed=${detail.didCrash()}")
            // Returning false here takes the whole application down with it.
            if (isFinishing || view !== page) {
                runCatching { view.destroy() }
                return true
            }
            if (renderRecoveries >= MAX_RENDER_RECOVERIES) {
                walkToOfflineBoard()
                return true
            }
            renderRecoveries++
            rebuildPage()
            return true
        }
    }

    private fun chromeClient() = object : WebChromeClient() {

        override fun onProgressChanged(view: WebView, newProgress: Int) {
            // Backstop for a page that reports progress but never finishes.
            // about:blank is only ever loaded on the way to the offline board,
            // so its progress says nothing about what the user is waiting for.
            if (newProgress >= 100 && view.url != BLANK) cover.drop()
        }

        override fun onShowFileChooser(
            view: WebView,
            callback: ValueCallback<Array<Uri>>,
            params: FileChooserParams
        ): Boolean {
            chooser?.onReceiveValue(arrayOf())
            chooser = callback
            return runCatching {
                filePicker.launch(assemblePicker(params))
                true
            }.getOrElse { failure ->
                Echo.odd(TAG, "file picker refused to launch: ${failure.javaClass.simpleName}")
                chooser = null
                false
            }
        }
    }

    /**
     * A file-picker intent that is friendlier than the one the framework hands
     * out.
     *
     * `FileChooserParams.createIntent()` is honest about what the page asked
     * for, but partner pages routinely ask for something so narrow — no MIME
     * types at all, or an odd single one — that the picker opens with nothing
     * to show and the user gives up.  This builds an ACTION_GET_CONTENT intent
     * with a broadened MIME list so the system Files picker always has
     * something to offer. The shell never launches the camera itself: doing
     * so would need the CAMERA permission and a FileProvider that a
     * privacy-minded release intentionally does not carry.
     */
    private fun assemblePicker(params: WebChromeClient.FileChooserParams): Intent {
        val types = params.acceptTypes.orEmpty().filter { it.isNotBlank() }
        val content = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            when {
                types.isEmpty() -> type = "*/*"
                types.size == 1 -> type = types.first()
                else -> {
                    type = "*/*"
                    putExtra(Intent.EXTRA_MIME_TYPES, types.toTypedArray())
                }
            }
            if (params.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) {
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
        }

        return Intent.createChooser(content, params.title)
    }

    // ── Redirect chains ─────────────────────────────────────────────────────

    /**
     * Chromium gives up after twenty hops and affiliate chains are routinely
     * longer, so this is an ordinary condition to carry on from rather than a
     * failure to report.
     *
     * The retry resumes from the deepest hop, and it is posted rather than
     * called: the engine is still unwinding the failed navigation, and a load
     * issued from inside this callback is deferred by seconds — which then
     * looks like a slow partner rather than a bug here. When the budget is
     * spent, the entry point the backend named is worth one attempt, because
     * the chain is stuck but its start usually still resolves and the cookies
     * picked up along the way are often what it was missing.
     */
    private fun resumeRedirectChain(view: WebView, failedUrl: String) {
        if (hopsRetried < Charter.hopBudget) {
            hopsRetried++
            retryQueued = true
            // Chromium has already committed the error document for the failed
            // hop, so without a cover the user watches the green robot until
            // the resumed load paints something. Raising it here — for every
            // planned retry, not just the first — hides that fact.
            cover.raise()
            val resumeAt = deepestHop ?: failedUrl
            Echo.note(TAG, "redirect loop, resuming attempt $hopsRetried")
            postLoad(view, resumeAt)
            return
        }

        val entry = locker.target
        if (!entryRetried && !entry.isNullOrBlank() && entry != deepestHop) {
            entryRetried = true
            retryQueued = true
            cover.raise()
            Echo.odd(TAG, "redirect budget spent → the configured entry point")
            postLoad(view, entry)
            return
        }

        Echo.odd(TAG, "redirect chain unresolvable — handing the page back")
        retryQueued = false
        cover.drop(0L, force = true)
    }

    private fun postLoad(view: WebView, url: String) {
        view.postDelayed({
            if (!isFinishing && !isDestroyed) view.loadUrl(url)
        }, RETRY_PAUSE_MS)
    }

    // ── Links this view cannot take ─────────────────────────────────────────

    private fun handOverToSystem(url: String) {
        runCatching {
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }.getOrNull()?.let { start(it) }
    }

    /**
     * An intent:// URI names an app and usually carries a fallback page, so
     * there are three things to try before the user is left looking at nothing.
     */
    private fun handOverIntentUri(url: String) {
        val parsed = runCatching { Intent.parseUri(url, Intent.URI_INTENT_SCHEME) }.getOrNull() ?: return
        val fallback = parsed.getStringExtra("browser_fallback_url")
        parsed.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addCategory(Intent.CATEGORY_BROWSABLE)
            component = null
            selector = null
        }
        if (start(parsed)) return

        // The named app may be missing while another one handles the scheme.
        parsed.`package` = null
        if (start(parsed)) return

        if (!fallback.isNullOrBlank()) page.loadUrl(fallback)
    }

    private fun start(intent: Intent): Boolean = runCatching { startActivity(intent) }.isSuccess

    // ── Connectivity ────────────────────────────────────────────────────────

    private fun watchTheLink() {
        scope.launch {
            link.changes.collect { up ->
                if (!up) {
                    Echo.note(TAG, "the link dropped")
                    walkToOfflineBoard()
                }
            }
        }
        // The callback above covers a link that goes away. This covers the case
        // it cannot see: a page that finished loading long ago, with nothing in
        // flight to fail, on a network that quietly stopped working.
        scope.launch {
            while (true) {
                delay(Charter.Wait.connectionProbe)
                if (walkedOffline) continue
                if (!link.up()) {
                    Echo.note(TAG, "probe found no link")
                    walkToOfflineBoard()
                }
            }
        }
    }

    private fun walkToOfflineBoard() {
        if (walkedOffline) return
        walkedOffline = true
        val comeBackTo = settledPage ?: page.url
        runCatching {
            page.stopLoading()
            page.loadUrl(BLANK)
        }
        startActivity(
            Intent(this, LinkLostDeck::class.java).apply {
                if (!comeBackTo.isNullOrBlank() && comeBackTo != BLANK) {
                    putExtra(LinkLostDeck.EXTRA_RETURN_URL, comeBackTo)
                }
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        )
        handOverFlat()
    }

    // ── Safe area ───────────────────────────────────────────────────────────

    /**
     * Keeps the page out from under the camera cutout: the top inset upright,
     * and both side insets on a landscape screen, where the cutout sits on one
     * of the short edges. The bottom is left alone — padding there would fight
     * the keyboard slide.
     */
    private fun keepOffTheCutout() {
        frame.setOnApplyWindowInsetsListener { view, insets ->
            val wide = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val cutout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) insets.displayCutout else null
            val bars = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                insets.getInsets(WindowInsets.Type.systemBars())
            } else null

            val top = if (wide) 0 else maxOf(cutout?.safeInsetTop ?: 0, bars?.top ?: 0)
            val left = if (wide) maxOf(cutout?.safeInsetLeft ?: 0, bars?.left ?: 0) else 0
            val right = if (wide) maxOf(cutout?.safeInsetRight ?: 0, bars?.right ?: 0) else 0
            view.setPadding(left, top, right, 0)
            insets
        }
        frame.requestApplyInsets()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        frame.requestApplyInsets()
        slider.reorient()
        EdgeFit.immerse(this)
    }

    /**
     * Stops the page adding a second inset of its own on top of the padding the
     * window already applies, and does nothing else.
     *
     * The restraint is the point. An earlier generation of this injection zeroed
     * `padding-left`, `padding-right` and `margin` on `html`, `body` and the
     * usual app roots — which are exactly the declarations sites build their
     * side gutters with, so every layout it touched ended up flattened against
     * both screen edges. Only the safe-area variables are cleared here, plus
     * `padding-top` on the two chrome wrappers that are known to add a
     * status-bar offset of their own.
     */
    private fun neutraliseSafeAreaCss() {
        val mark = BuildConfig.MARK_EDGE
        val running = mark + "On"
        page.evaluateJavascript(
            """
            (function(){
              if (window.$running) return;
              window.$running = true;

              var STYLE_ID = '$mark';

              // The names sites read their safe area through, in the three
              // spellings that are actually in use. Every one of them is set to
              // zero; nothing else about the page's box model is touched.
              var EDGES = ['top','right','bottom','left'];
              var SHAPES = ['safe-area-inset-', 'safe-'];
              var SHORT = { top:'sat', right:'sar', bottom:'sab', left:'sal' };

              var zeroed = '';
              EDGES.forEach(function(edge){
                SHAPES.forEach(function(shape){
                  zeroed += '--' + shape + edge + ':0px!important;';
                });
                zeroed += '--' + SHORT[edge] + ':0px!important;';
              });

              // The only box-model declaration here, and only for the two
              // wrappers known to add a status-bar offset of their own.
              var RULES = ':root{' + zeroed + '}' +
                '.app-header,.gameview-mobile-header{padding-top:0!important;}';

              function place(){
                var head = document.head || document.documentElement;
                if (!head) return;

                var meta = document.querySelector('meta[name="viewport"]');
                if (meta && !/viewport-fit\s*=\s*contain/i.test(meta.getAttribute('content') || '')) {
                  var content = (meta.getAttribute('content') || '')
                    .replace(/,?\s*viewport-fit\s*=\s*\w+/ig, '').trim();
                  meta.setAttribute('content', content + (content ? ', ' : '') + 'viewport-fit=contain');
                }

                var sheet = document.getElementById(STYLE_ID);
                if (!sheet) {
                  sheet = document.createElement('style');
                  sheet.id = STYLE_ID;
                  head.appendChild(sheet);
                }
                if (sheet.textContent !== RULES) sheet.textContent = RULES;
                if (head.lastElementChild !== sheet) head.appendChild(sheet);
              }

              place();
              ['pushState','replaceState'].forEach(function(name){
                var original = history[name];
                history[name] = function(){
                  var result = original.apply(this, arguments);
                  setTimeout(place, 80);
                  setTimeout(place, 400);
                  return result;
                };
              });
              window.addEventListener('popstate', function(){ setTimeout(place, 80); });
              setInterval(place, 2500);
            })();
            """.trimIndent(),
            null
        )
    }

    // ── The cover ───────────────────────────────────────────────────────────

    private val cover = Cover()

    /**
     * A full loading screen — solid dark ground, a single spinner in the middle
     * — that goes over the WebView while the session's first page resolves and
     * while a chain of redirects walks itself out from under an
     * ERR_TOO_MANY_REDIRECTS. The old version was a translucent scrim on top of
     * the WebView's black background, which read as an empty black screen with
     * a barely visible spinner during exactly the moments the cover was there
     * to smooth over. Never a snapshot of the WebView: drawing a hardware-
     * accelerated view into a software canvas yields solid black anyway.
     */
    private inner class Cover {
        private var sheet: View? = null
        private var pending: Job? = null

        fun raise() {
            pending?.cancel()
            pending = null
            sheet?.let { existing ->
                existing.animate().cancel()
                existing.alpha = 1f
                return
            }
            val spinner = ProgressBar(this@WebDeck).apply {
                isIndeterminate = true
                indeterminateTintList = ColorStateList.valueOf(COVER_ACCENT)
            }
            val spinnerSize = (56f * resources.displayMetrics.density + 0.5f).toInt()
            val fresh = FrameLayout(this@WebDeck).apply {
                setBackgroundColor(COVER_FILL)
                isClickable = true
                addView(
                    spinner,
                    FrameLayout.LayoutParams(spinnerSize, spinnerSize, Gravity.CENTER)
                )
            }
            sheet = fresh
            frame.addView(
                fresh,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
            // A page that never reports back must not hold the screen for good.
            scope.launch {
                delay(COVER_LIMIT_MS)
                if (sheet === fresh) {
                    Echo.odd(TAG, "cover timed out")
                    drop(0L, force = true)
                }
            }
        }

        /**
         * @param after a grace before the page is handed back. A redirect hop
         *   finishes and starts the next load within a frame or two, and this is
         *   what stops the cover blinking off and on between them.
         * @param force drop even if a retry is queued. The queued flag is the
         *   normal reason a drop is refused (a failed load hits onPageFinished
         *   and progressChanged(100) on its way out, and letting either one
         *   through would flash the error document between two hops of the same
         *   chain). Non-recoverable branches — the timeout and the point where
         *   there is nothing left to retry — set this to override the guard.
         */
        fun drop(after: Long = COVER_LINGER_MS, force: Boolean = false) {
            if (!force && retryQueued) return
            val current = sheet ?: return
            pending?.cancel()
            pending = scope.launch {
                delay(after)
                // The flag can flip between the call and this point: a new
                // redirect error tripping resumeRedirectChain again, or another
                // onPageStarted racing in. Re-check before we uncover the page —
                // dropping into a queued retry is exactly what leaves the error
                // document visible for the seconds the retry needs.
                if (!force && retryQueued) return@launch
                if (sheet !== current) return@launch
                sheet = null
                current.animate().alpha(0f).setDuration(150L).withEndAction {
                    frame.removeView(current)
                }.start()
            }
        }
    }

    companion object {
        const val EXTRA_PAGE_URL = "page_url"
        const val EXTRA_PUSH_URL = "push_target"
        const val EXTRA_PUSH_WARM = "push_warm"

        private const val TAG = "WebDeck"
        private const val BLANK = "about:blank"

        /** Everything this view can take. Anything else belongs to an app. */
        private val PAGE_SCHEMES =
            setOf("http", "https", "about", "data", "blob", "file", "javascript")

        /** Codes that mean "there is no way out right now", not "this page is broken". */
        private val NETWORK_ERRORS =
            setOf(
                WebViewClient.ERROR_HOST_LOOKUP,
                WebViewClient.ERROR_CONNECT,
                WebViewClient.ERROR_IO,
                WebViewClient.ERROR_TIMEOUT,
                WebViewClient.ERROR_FAILED_SSL_HANDSHAKE
            )

        /** The raw Chromium net error, which sometimes arrives instead of -9. */
        private const val NET_TOO_MANY_REDIRECTS = -1007

        private const val COVER_LINGER_MS = 120L
        private const val COVER_LIMIT_MS = 20_000L
        // Opaque ground the loading screen paints. Not translucent: the layer
        // behind is a black WebView showing an ERR_TOO_MANY_REDIRECTS document.
        private const val COVER_FILL = 0xFF0B0B0F.toInt()
        // The spinner colour is the picture's own gold, so the loading state
        // reads as part of the app rather than as the system's grey ring.
        private const val COVER_ACCENT = 0xFFF2C464.toInt()
        private const val MAX_RENDER_RECOVERIES = 3

        /** Long enough for the engine to unwind, short enough to go unnoticed. */
        private const val RETRY_PAUSE_MS = 60L
    }
}
