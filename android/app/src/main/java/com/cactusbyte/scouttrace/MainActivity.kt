package com.cactusbyte.scouttrace

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private var pendingPermissionRequest: PermissionRequest? = null
    private val trustedHost = "acelynn-scoutrace.vercel.app"
    private val apkPickerCode = 301
    private var pendingSharedText: String? = null
    private var pendingDestructiveConfirmation: android.app.AlertDialog? = null
    private var activityResumedForConfirmation = false

    /**
     * Native confirmation cannot be forged by a script from the trusted WebView origin.
     * Requires an active foreground Activity; cancellation never deletes anything.
     */
    fun confirmDestructiveAction(title: String, explanation: String): Boolean {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) return false
        val latch = java.util.concurrent.CountDownLatch(1)
        val approved = java.util.concurrent.atomic.AtomicBoolean(false)
        val dialogRef = java.util.concurrent.atomic.AtomicReference<android.app.AlertDialog?>()
        runOnUiThread {
            if (isFinishing || isDestroyed || !activityResumedForConfirmation) {
                latch.countDown()
                return@runOnUiThread
            }
            try {
                val dialog = android.app.AlertDialog.Builder(this)
                    .setTitle(title)
                    .setMessage(explanation)
                    .setPositiveButton("Delete permanently") { _, _ -> approved.set(true) }
                    .setNegativeButton("Cancel") { _, _ -> }
                    .create()
                dialogRef.set(dialog)
                pendingDestructiveConfirmation = dialog
                dialog.setOnDismissListener {
                    if (pendingDestructiveConfirmation === dialog) pendingDestructiveConfirmation = null
                    latch.countDown()
                }
                dialog.show()
            } catch (_: Exception) {
                pendingDestructiveConfirmation = null
                latch.countDown()
            }
        }
        val completed = try {
            latch.await(90, java.util.concurrent.TimeUnit.SECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
        if (!completed) runOnUiThread { dialogRef.get()?.dismiss() }
        return completed && approved.get()
    }
    private val nativeBridge by lazy { ScoutTraceBridge(this) }
    private val nativeExecutor = java.util.concurrent.ThreadPoolExecutor(
        1, 1, 0L, java.util.concurrent.TimeUnit.MILLISECONDS,
        java.util.concurrent.ArrayBlockingQueue<Runnable>(24),
        java.util.concurrent.ThreadPoolExecutor.AbortPolicy()
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        acceptSharedText(intent)
        webView = WebView(this)
        setContentView(webView)
        WebView.setWebContentsDebuggingEnabled(false)
        webView.settings.apply {
            javaScriptEnabled = true
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            domStorageEnabled = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            allowFileAccess = false
            allowContentAccess = true
            mediaPlaybackRequiresUserGesture = true
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest): Boolean {
                val uri = request.url
                if (uri.scheme == "https" && uri.host == trustedHost &&
                    (uri.port == -1 || uri.port == 443)) return false
                if (request.isForMainFrame && uri.scheme in setOf("http", "https", "mailto", "tel")) {
                    val external = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
                    try { startActivity(external) } catch (_: Exception) { }
                }
                // Refuse external file/content/intent/javascript schemes and untrusted subframes.
                return true
            }
            override fun onReceivedSslError(view: WebView, handler: android.webkit.SslErrorHandler, error: android.net.http.SslError) {
                handler.cancel()
                android.widget.Toast.makeText(this@MainActivity, "ScoutTrace blocked a connection with an invalid security certificate.", android.widget.Toast.LENGTH_LONG).show()
            }
            override fun shouldInterceptRequest(view: WebView, request: android.webkit.WebResourceRequest): android.webkit.WebResourceResponse? {
                val uri = request.url
                if (uri.scheme != "https") {
                    return android.webkit.WebResourceResponse("text/plain", "UTF-8",
                        java.io.ByteArrayInputStream(ByteArray(0)))
                }
                return super.shouldInterceptRequest(view, request)
            }
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                val page = Uri.parse(url)
                if (page.host == trustedHost && page.scheme == "https" && (page.port == -1 || page.port == 443)) injectPhoneSecuritySweep()
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequestCanceled(request: PermissionRequest) {
                if (pendingPermissionRequest === request) pendingPermissionRequest = null
            }
            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread {
                    if (request.origin.scheme != "https" || request.origin.host != trustedHost ||
                        (request.origin.port != -1 && request.origin.port != 443) ||
                        webView.url?.let {
                            val page = Uri.parse(it)
                            page.scheme == "https" && page.host == trustedHost &&
                                (page.port == -1 || page.port == 443)
                        } != true) {
                        request.deny(); return@runOnUiThread
                    }
                    if (request.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) {
                        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) request.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
                        else { pendingPermissionRequest = request; requestPermissions(arrayOf(Manifest.permission.CAMERA), 201) }
                    } else request.deny()
                }
            }
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(webView, "ScoutTraceNative",
                setOf("https://acelynn-scoutrace.vercel.app")
            ) { _, message, origin, isMainFrame, reply ->
                if (!isMainFrame || origin.scheme != "https" || origin.host != trustedHost ||
                    (origin.port != -1 && origin.port != 443) ||
                    webView.url?.let { Uri.parse(it).host != trustedHost || Uri.parse(it).scheme != "https" } != false) return@addWebMessageListener
                val raw = message.data ?: return@addWebMessageListener
                if (raw.length > 8192) return@addWebMessageListener
                val request = try { JSONObject(raw) } catch (_: Exception) { return@addWebMessageListener }
                val id = request.optString("id").takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,64}")) }
                    ?: return@addWebMessageListener
                val method = request.optString("method")
                val args = request.optJSONArray("args")
                val allowedMethods = setOf("runSecurityScan", "saveSecurityBaseline", "privacySummary",
                    "openApkPicker", "shareSecurityReport", "openAppSettings", "requestUninstall",
                    "setAppDisposition", "openAccessibilitySettings", "openSecuritySettings", "clearSecurityHistory",
                    "clearAppDispositions")
                if (method !in allowedMethods) {
                    reply.postMessage(JSONObject().put("id", id).put("ok", false).put("error", "Unsupported native operation").toString())
                    return@addWebMessageListener
                }
                if (args != null && args.length() > 2) {
                    reply.postMessage(JSONObject().put("id", id).put("ok", false).put("error", "Too many arguments").toString())
                    return@addWebMessageListener
                }
                val expectedArgs = when (method) {
                    "openAppSettings", "requestUninstall" -> 1
                    "setAppDisposition" -> 2
                    else -> 0
                }
                if ((args?.length() ?: 0) != expectedArgs) {
                    reply.postMessage(JSONObject().put("id", id).put("ok", false).put("error", "Invalid argument count").toString())
                    return@addWebMessageListener
                }
                if (method == "setAppDisposition" &&
                    args?.optString(1) !in setOf("TRUSTED", "WATCH", "IGNORE", "DEFAULT")) {
                    reply.postMessage(JSONObject().put("id", id).put("ok", false).put("error", "Invalid classification").toString())
                    return@addWebMessageListener
                }
                val packageName = args?.optString(0) ?: ""
                if (method in setOf("openAppSettings", "requestUninstall", "setAppDisposition") &&
                    !packageName.matches(Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+"))) {
                    reply.postMessage(JSONObject().put("id", id).put("ok", false)
                        .put("error", "Invalid package name").toString())
                    return@addWebMessageListener
                }
                try {
                    nativeExecutor.execute {
                    val response = JSONObject().put("id", id)
                    try {
                        val value: Any = when (method) {
                            "runSecurityScan" -> nativeBridge.runSecurityScan()
                            "saveSecurityBaseline" -> nativeBridge.saveSecurityBaseline()
                            "privacySummary" -> nativeBridge.privacySummary()
                            "openApkPicker" -> nativeBridge.openApkPicker()
                            "shareSecurityReport" -> nativeBridge.shareSecurityReport()
                            "openAppSettings" -> nativeBridge.openAppSettings(args?.optString(0) ?: "")
                            "requestUninstall" -> nativeBridge.requestUninstall(args?.optString(0) ?: "")
                            "setAppDisposition" -> nativeBridge.setAppDisposition(args?.optString(0) ?: "", args?.optString(1) ?: "")
                            "openAccessibilitySettings" -> nativeBridge.openAccessibilitySettings()
                            "openSecuritySettings" -> nativeBridge.openSecuritySettings()
                            "clearSecurityHistory" -> nativeBridge.clearSecurityHistory()
                            "clearAppDispositions" -> nativeBridge.clearAppDispositions()
                            else -> throw IllegalArgumentException("Unsupported native operation")
                        }
                        response.put("ok", true).put("value", value)
                    } catch (_: Exception) {
                        response.put("ok", false).put("error", "Native operation unavailable")
                    }
                    runOnUiThread {
                        val current = webView.url?.let(Uri::parse)
                        if (!isFinishing && !isDestroyed && current?.scheme == "https" && current.host == trustedHost &&
                            (current.port == -1 || current.port == 443)) reply.postMessage(response.toString())
                    }
                    }
                } catch (_: java.util.concurrent.RejectedExecutionException) {
                    reply.postMessage(JSONObject().put("id", id).put("ok", false)
                        .put("error", "Investigation service is closing").toString())
                }
            }
        }
        else {
            android.widget.Toast.makeText(this, "ScoutTrace requires an updated Android System WebView for secure investigations.", android.widget.Toast.LENGTH_LONG).show()
            webView.settings.javaScriptEnabled = false
        }
        webView.loadUrl("https://acelynn-scoutrace.vercel.app/")
    }

    fun requestApkInspection() = runOnUiThread { openApkPicker() }

    private fun openApkPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, apkPickerCode)
    }

    @Deprecated("Activity result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != apkPickerCode || resultCode != RESULT_OK) return
        val uri: Uri = data?.data ?: return
        if (uri.scheme != "content") return
        try {
            nativeExecutor.execute {
                val result = try {
                    ApkInspector(applicationContext).inspect(uri).toString()
                } catch (e: Exception) {
                    // Do not expose document-provider paths, private URI data, or exception details to web content.
                    val explanation = if (e is IllegalArgumentException &&
                        e.message?.contains("size limit") == true)
                        "Selected APK exceeds the 256 MB inspection limit."
                    else "Selected APK could not be safely read or parsed."
                    JSONObject().put("ok", false).put("error", explanation).toString()
                }
                runOnUiThread {
                    val current = webView.url?.let(Uri::parse)
                    if (!isFinishing && !isDestroyed && current?.scheme == "https" && current.host == trustedHost &&
                        (current.port == -1 || current.port == 443)) {
                        webView.evaluateJavascript("window.dispatchEvent(new CustomEvent(\"scouttrace-apk-result\",{detail:"+JSONObject.quote(result)+"}));", null)
                    }
                }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            android.widget.Toast.makeText(this, "ScoutTrace is busy. Try APK inspection again.", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private fun injectPhoneSecuritySweep() {
        val js = """
        (() => {
          const mount=()=>{
            if(window.__scoutTracePhoneSweepInjected) return true;
            const grid=document.querySelector('.grid');
            if(!grid)return false;
            const nativePort=window.ScoutTraceNative;
            if(!nativePort || typeof nativePort.postMessage!=='function'){
              console.warn('ScoutTrace secure native messaging unavailable');
              return true;
            }
            window.__scoutTracePhoneSweepInjected=true;
            const nativePending=new Map();
            let nativeSequence=0;
          nativePort.onmessage=event=>{
            try{
              const msg=JSON.parse(event.data),p=nativePending.get(msg.id);
              if(!p)return;
              nativePending.delete(msg.id);
              if(msg.ok)p.resolve(msg.value);else p.reject(new Error(msg.error||'Native request failed'));
            }catch(e){console.warn('ScoutTrace native response rejected');}
          };
          const nativeCall=(method,...args)=>new Promise((resolve,reject)=>{
            const id='st'+(++nativeSequence);
            const timeout=setTimeout(()=>{nativePending.delete(id);reject(new Error('Native operation timed out'));},method==='clearSecurityHistory'||method==='clearAppDispositions'?110000:60000);
            nativePending.set(id,{resolve:v=>{clearTimeout(timeout);resolve(v);},reject:e=>{clearTimeout(timeout);reject(e);}});
            try{nativePort.postMessage(JSON.stringify({id,method,args}));}
            catch(e){clearTimeout(timeout);nativePending.delete(id);reject(e);}
          });
          // Grid presence was checked before native handlers were installed.
          // Approved ScoutTrace ten-screen visual language: scoped, reversible styling.
          if(!document.getElementById('scouttrace-design-lock')) {
            const theme=document.createElement('style');
            theme.id='scouttrace-design-lock';
            theme.textContent=`
              .scouttrace-diff-filters{display:flex;flex-wrap:wrap;gap:8px;margin:10px 0}
              .scouttrace-diff-filters button{min-height:44px;padding:9px 12px;border-radius:11px;border:1px solid #4a7296;background:#eff7ff;color:#163553;font-weight:650}
              .scouttrace-diff-filters button[aria-pressed="true"]{background:#13578e;color:#fff;border-color:#0a4273}
              .scouttrace-diff-filters button:focus-visible{outline:3px solid #58adff;outline-offset:2px}
              .scouttrace-diff-item[hidden]{display:none!important}
              .scouttrace-detail{margin-top:12px;padding:12px;border-radius:12px;background:rgba(43,118,187,.08);border:1px solid rgba(68,137,197,.25)}
              .scouttrace-permission-count{margin-left:auto;padding-left:10px;font-size:.86rem;opacity:.82}
              .scouttrace-permission ul{list-style:none;padding:0;margin:8px 0}
              .scouttrace-permission li{padding:10px 0;border-top:1px solid rgba(95,143,186,.24);overflow-wrap:anywhere}
              .scouttrace-permission li button{display:block;min-height:44px;margin-top:7px;border-radius:10px;padding:8px 12px;border:1px solid #5491c2;background:rgba(56,136,203,.13);color:inherit;font-weight:650}
              .scouttrace-permission li button:focus-visible{outline:3px solid #55b5ff;outline-offset:2px}
              .scouttrace-detail summary{cursor:pointer;min-height:42px;display:flex;align-items:center;font-weight:750;color:inherit}
              .scouttrace-detail p{margin:8px 0;line-height:1.55}
              .scouttrace-detail .scouttrace-evidence{overflow-wrap:anywhere;font-size:.88rem}
              .scouttrace-finding{padding:16px!important;margin:10px 0!important;border:1px solid rgba(100,149,193,.3)!important;border-left:4px solid #3989d6!important;border-radius:15px!important;background:rgba(49,111,167,.06)}
              .scouttrace-finding .status{display:inline-flex;margin:8px 0;max-width:100%}
              .scouttrace-finding .acts{display:flex;flex-wrap:wrap;gap:8px;margin-top:12px}
              .scouttrace-finding .acts button{min-height:42px;border-radius:10px}
              .scouttrace-finding .muted{line-height:1.55;overflow-wrap:anywhere}
              .scouttrace-bottom-nav{position:sticky;bottom:0;z-index:10;display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:3px;padding:8px 6px max(8px,env(safe-area-inset-bottom));background:#0b1c30;border:1px solid #24425d;border-radius:16px;margin:12px 0;box-shadow:0 8px 24px rgba(0,0,0,.2)}
              .scouttrace-bottom-nav button{min-height:48px;padding:5px 2px;border:0;border-radius:10px;background:transparent;color:#c6d7e8;font-size:.78rem;font-weight:700;cursor:pointer}
              .scouttrace-bottom-nav button[aria-current="page"]{background:#23496e;color:#fff}
              .scouttrace-bottom-nav button:focus-visible{outline:3px solid #6cbcff;outline-offset:1px}
              .scouttrace-launcher{padding:20px;margin:14px 0 20px;background:#f8fafc;color:#0c2036;border:1px solid #dce5ee;border-radius:22px;box-shadow:0 10px 24px rgba(12,32,54,.08)}
              .scouttrace-launcher-title{display:flex;flex-direction:column;gap:4px;margin-bottom:14px}
              .scouttrace-launcher-title strong{font-size:1.02rem;letter-spacing:.03em;color:#10273f}
              .scouttrace-launcher-title span{font-size:.85rem;color:#536b80}
              .scouttrace-run{width:100%;min-height:50px;background:#0c2036;color:white;border:0;border-radius:12px;font-weight:750;font-size:1rem;cursor:pointer}
              .scouttrace-shortcuts{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:9px;margin-top:12px}
              .scouttrace-shortcuts button{min-height:44px;border:1px solid #dce5ee;border-radius:12px;background:white;color:#16324d;font-weight:650;cursor:pointer}
              .scouttrace-run:focus-visible,.scouttrace-shortcuts button:focus-visible{outline:3px solid #3687ea;outline-offset:2px}
              @media(min-width:650px){.scouttrace-shortcuts{grid-template-columns:repeat(4,minmax(0,1fr))}}
              .scouttrace-priority{margin:14px 0;padding:18px;border:1px solid #3976aa;border-radius:17px;background:linear-gradient(140deg,#0b1e34,#153b5d);color:#eaf6ff}
              .scouttrace-priority strong{display:block;color:#fff;font-size:1.06rem;margin-bottom:8px}
              .scouttrace-priority p{line-height:1.5;margin:7px 0}
              .scouttrace-priority small{color:#b9d4e9}
              .scouttrace-priority button{margin-top:8px;min-height:44px;padding:8px 15px;border-radius:10px;background:#e6f4ff;color:#103353;border:0;font-weight:750}
              .scouttrace-screen{padding:18px;margin:14px 0;border-radius:17px;border:1px solid rgba(80,145,195,.3);background:rgba(65,126,185,.06)}
              .scouttrace-screen h3{margin-top:0}
              .scouttrace-screen p{line-height:1.55}
              .scouttrace-screen textarea{display:block;width:100%;min-height:90px;box-sizing:border-box;border-radius:12px;padding:12px;margin:10px 0;font:inherit}
              .scouttrace-screen button{min-height:44px;border-radius:10px;padding:8px 14px}
              .scouttrace-summary-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px;margin:12px 0 18px}
              .scouttrace-metric{display:flex;flex-direction:column;gap:5px;padding:14px;border:1px solid rgba(111,153,201,.23);background:rgba(85,147,214,.08);border-radius:15px}
              .scouttrace-metric b{font-size:1.6rem;font-weight:800;font-variant-numeric:tabular-nums}
              .scouttrace-metric small{font-size:.77rem;opacity:.8}
              @media(min-width:620px){.scouttrace-summary-grid{grid-template-columns:repeat(4,minmax(0,1fr))}}
              .scouttrace-entry{position:relative;overflow:hidden;border:1px solid rgba(65,142,213,.35)!important;background:linear-gradient(140deg,#0c1b2d,#152c46)!important;color:#f4f8ff!important;border-radius:20px!important;box-shadow:0 12px 32px rgba(5,15,28,.25)!important}
              .scouttrace-entry strong{font-weight:800;letter-spacing:.01em;color:#fff!important}
              .scouttrace-entry span{color:#c6d7e8!important}
              .scouttrace-entry .ico{background:rgba(47,134,242,.18)!important;color:#71b9ff!important;border:1px solid rgba(113,185,255,.4)!important}
              .scouttrace-entry:focus-visible{outline:3px solid #61b8ff;outline-offset:3px}
              .scouttrace-native-head{display:flex;align-items:center;gap:12px;margin:0 0 16px;padding:16px;border-radius:18px;background:linear-gradient(125deg,#0c1b2d,#173653);color:#fff}
              .scouttrace-native-head .scouttrace-mark{display:grid;place-items:center;width:44px;height:44px;border-radius:14px;background:#23476b;font-size:25px}
              .scouttrace-native-head strong{display:block;color:#fff;font-size:1.15rem}
              .scouttrace-native-head small{display:block;color:#c3d9ed;line-height:1.5}
              .scouttrace-native-head + .panel{border-radius:18px}
              #nativePhoneResult .hist{border-radius:14px;overflow-wrap:anywhere}
              #nativePhoneResult .status{font-weight:800;letter-spacing:.04em}
              @media(max-width:380px){.scouttrace-native-head{padding:12px;gap:9px}.scouttrace-native-head strong{font-size:1rem}}
              @media(min-width:700px){#nativePhoneResult{max-width:960px;margin:auto}}
            `;
            document.head.appendChild(theme);
          }
          const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
          const klass=l=>l==='CLEAR'?'clear':l==='REVIEW'?'review':l==='ELEVATED'?'elevated':'high';
          const card=document.createElement('button'); card.className='card';
          card.classList.add('scouttrace-entry');
          card.innerHTML='<div class="ico">⛨</div><strong>ScoutTrace Investigation</strong><span>Security Diff · Evidence · Priorities · Guided actions</span>';
          card.onclick=()=>{
            const home=document.getElementById('home'),scan=document.getElementById('scan'),body=document.getElementById('scanBody');
            if(!home||!scan||!body)return; home.classList.remove('active');scan.classList.add('active');
            document.getElementById('scanType').textContent='COMPLETE SCOUTTRACE';
            document.getElementById('scanTitle').textContent='Android security investigation';
            document.getElementById('scanDesc').textContent='Detect → correlate → explain → prioritize → remediate. Findings are evidence for review, not proof of malware.';
            body.innerHTML='<div class="scouttrace-native-head"><div class="scouttrace-mark">⛨</div><div><strong>Your Security Investigator</strong><small>Detect · Correlate · Explain · Prioritize · Remediate</small></div></div><div class="panel"><div class="notice"><b>Native investigation ready.</b> App evidence analysis is processed on this device. The ScoutTrace web interface still needs an internet connection.</div><div class="acts"><button id="nativePhoneScan" class="btn primary">Run Complete ScoutTrace</button><button id="saveBaseline" class="btn">Save Trusted Baseline</button><button id="privacyInfo" class="btn">Privacy</button><button id="exportReport" class="btn">Export Report</button><button id="apkInspect" class="btn">Inspect APK</button><button id="scamOpinion" class="btn">Scam Second Opinion</button><button id="familyAssist" class="btn">Family Assistance</button></div><div id="scouttraceUtility" aria-live="polite"></div><pre id="apkResult" hidden style="white-space:pre-wrap;overflow-wrap:anywhere"></pre><div id="nativePhoneResult" class="result" hidden></div></div>';
            document.getElementById('scamOpinion').onclick=()=>{
              const target=document.getElementById('scouttraceUtility');
              target.innerHTML='<section class="scouttrace-screen"><h3>Scam Second Opinion</h3><p>Paste a suspicious message or link. This is a local checklist, not a reputation lookup or definitive scam detector. Do not enter passwords, codes, or personal information.</p><textarea id="scouttraceScamText" maxlength="4000" aria-label="Suspicious message or link" placeholder="Paste text to review locally"></textarea><button type="button" id="scouttraceScamReview">Review warning signs</button><div id="scouttraceScamResult" role="status"></div></section>';
              document.getElementById('scouttraceScamReview').onclick=()=>{
                const value=document.getElementById('scouttraceScamText').value.slice(0,4000);
                const signs=[];
                if(/(?:urgent|immediately|act now|account (?:suspended|locked)|final warning|within 24 hours)/i.test(value))signs.push('Urgency or account-pressure language');
                if(/(?:gift card|crypto(?:currency)?|bitcoin|wire transfer|send money)/i.test(value))signs.push('Payment or transfer request');
                if(/(?:password|verification code|one.time code|otp|login credentials)/i.test(value))signs.push('Credential or verification-code request');
                if(/https?:\/\/|www\./i.test(value))signs.push('Link present: verify the destination independently');
                const result=document.getElementById('scouttraceScamResult');
                result.replaceChildren();
                const p=document.createElement('p');p.textContent=signs.length?'Warning signs to verify: '+signs.join('; ')+'.':'No checklist patterns matched. That does not establish safety.';
                const note=document.createElement('p');note.textContent='Do not use the supplied link to verify the claim. Contact the organization through an independently obtained official channel.';
                result.append(p,note);
              };
            };
            document.getElementById('familyAssist').onclick=()=>{
              document.getElementById('scouttraceUtility').innerHTML='<section class="scouttrace-screen"><h3>Family Assistance</h3><p>Support another person only with their informed consent. ScoutTrace does not silently monitor, remotely access, or inspect another device.</p><p>Ask them to run their own investigation, review findings together, and choose whether to share a redacted report. Sharing opens Android’s system share sheet and requires a user action.</p><button type="button" id="scouttraceFamilyShare">Share redacted report…</button><p class="muted">The report hides app identities and finding details. Review the recipient before sending.</p></section>';
              document.getElementById('scouttraceFamilyShare').onclick=async()=>{try{if(!await nativeCall('shareSecurityReport'))alert('Unable to open the Android share sheet.');}catch(e){alert('Report sharing unavailable.');}};
            };
            document.getElementById('saveBaseline').onclick=async()=>{try{const r=JSON.parse(await nativeCall('saveSecurityBaseline'));alert(r.ok?'Trusted baseline saved on this device.':'Baseline could not be saved.');}catch(e){alert('Baseline could not be saved. Please retry.');}};
            document.getElementById('privacyInfo').onclick=async()=>{try{const p=JSON.parse(await nativeCall('privacySummary'));alert(p.statement||'Privacy information is unavailable.');}catch(e){alert('Privacy information is unavailable.');}};
            document.getElementById('apkInspect').onclick=async()=>{try{if(await nativeCall('openApkPicker')!==true)throw new Error('Unavailable');}catch(_){alert('Android APK picker could not be opened.');}};
            window.addEventListener('scouttrace-apk-result',event=>{const out=document.getElementById('apkResult');if(!out)return;try{const result=JSON.parse(event.detail);out.hidden=false;out.textContent=result.ok?JSON.stringify(result,null,2):'APK inspection failed: '+result.error;}catch(err){out.hidden=false;out.textContent='Unable to display APK result.';}});
            document.getElementById('exportReport').onclick=async()=>{try{if(await nativeCall('shareSecurityReport')!==true)throw new Error('Unavailable');}catch(_){alert('Android report sharing is unavailable right now.');}};
            document.getElementById('nativePhoneScan').onclick=async()=>{
              const out=document.getElementById('nativePhoneResult'); out.hidden=false; out.innerHTML='<p class="muted">Running ScoutTrace…</p>';
              try{
                const r=JSON.parse(await nativeCall('runSecurityScan')),c=r.counts||{},fs=r.findings||[],b=r.baseline||{},pm=r.permissionMatrix||{},tl=r.timeline||[],disp=r.dispositions||{},cov=r.coverage||{},n=r.narrative||{};
                const changes=b.exists?((b.newApps||[]).length+(b.removedApps||[]).length+(b.riskChanges||[]).length+(b.metadataChanges||[]).length):0,bn=b.narrative||{};
                const quickSummary='<div class="scouttrace-summary-grid"><div class="scouttrace-metric"><b>'+esc(c.appsScanned??0)+'</b><small>Analyzed apps</small></div><div class="scouttrace-metric"><b>'+esc(changes)+'</b><small>Baseline changes</small></div><div class="scouttrace-metric"><b>'+esc(c.findings??0)+'</b><small>Findings</small></div><div class="scouttrace-metric"><b>'+esc((n.priorities||[]).length)+'</b><small>Priorities</small></div></div>';
                const fieldNames={versionCode:'App version',installer:'Reported install source',installSourceStatus:'Install-source classification',requestedPermissions:'Declared permissions',grantedRuntimePermissions:'Granted permissions',overlayDeclared:'Overlay declaration',accessibilityEnabled:'Enabled Accessibility service',deviceAdminActive:'Active Device Administrator'};
                const permissionName=x=>String(x).split('.').pop().replace(/_/g,' ').toLowerCase();
                const diffItems=b.exists?[
                  ...(b.newApps||[]).map(x=>({title:'Newly visible app',detail:x,category:'apps'})),
                  ...(b.removedApps||[]).map(x=>({title:'App no longer visible',detail:x,category:'apps'})),
                  ...(b.riskChanges||[]).map(x=>({title:'Risk classification changed',detail:x.packageName+' · '+x.fromLevel+' → '+x.toLevel,category:'risk'})),
                  ...(b.metadataChanges||[]).map(x=>{
                    const labels=(x.fields||[]).map(k=>fieldNames[k]||k);
                    const granted=(x.grantsAdded||[]).map(permissionName),revoked=(x.grantsRevoked||[]).map(permissionName);
                    const access=(x.fields||[]).some(k=>['requestedPermissions','grantedRuntimePermissions','overlayDeclared','accessibilityEnabled','deviceAdminActive'].includes(k));
                    return {title:'App access or configuration changed',category:access?'permissions':'apps',detail:x.packageName+' · '+labels.join(', ')+(granted.length?' · Now granted: '+granted.join(', '):'')+(revoked.length?' · No longer granted: '+revoked.join(', '):'')};
                  })
                ]:[];
                const visibleDiff=diffItems.slice(0,40);
                const diffFilters=diffItems.length?
                  '<div class="scouttrace-diff-filters" role="group" aria-label="Filter Security Diff"><button type="button" data-diff-filter="all" aria-pressed="true">All changes</button><button type="button" data-diff-filter="apps" aria-pressed="false">App inventory</button><button type="button" data-diff-filter="permissions" aria-pressed="false">Access changes</button><button type="button" data-diff-filter="risk" aria-pressed="false">Risk changes</button></div><p class="muted" id="scouttraceDiffCount" role="status">Showing '+visibleDiff.length+' of '+diffItems.length+' recorded differences</p>':'';
                const diffHtml=diffFilters+(visibleDiff.length?
                  visibleDiff.map(x=>'<div class="hist scouttrace-diff-item" data-diff-category="'+esc(x.category)+'"><strong>'+esc(x.title)+'</strong><div class="muted">'+esc(x.detail)+'</div></div>').join(''):
                  '<div class="hist"><div class="muted">'+esc(b.exists?'No observed changes in available baseline fields.':'Save a trusted baseline to begin tracking observed changes.')+'</div></div>');
                const diffContext='<p class="muted">Compared with your locally saved baseline. Visibility can change without an app being installed or removed. Scanner upgrades can also change classifications. Grant changes cover only the Android runtime permissions ScoutTrace observes, not every special-access setting.</p>';
                const permissionHtml=Object.entries(pm).map(([k,v])=>{
                  const list=Array.isArray(v)?v:[], granted=list.filter(item=>item.granted===true).length;
                  const names={accessibilityEnabled:'Enabled Accessibility',deviceAdminActive:'Active Device Administrators',overlayDeclared:'Overlay declarations',installPackagesDeclared:'Install-request declarations'};
                  const special={accessibilityEnabled:list.length+' enabled service(s) reported by Android',deviceAdminActive:list.length+' active administrator(s) reported by Android',overlayDeclared:list.length+' app(s) declare overlay access; active AppOps permission is not verified',installPackagesDeclared:list.length+' app(s) declare install-request capability; actual access is not verified'};
                  const label=names[k]||k.toUpperCase();
                  const detail=special[k]||list.length+' analyzed app(s) request this permission · '+granted+' currently granted (as Android reports).';
                  const rows=list.slice(0,80).map(item=>{
                    const status=item.granted===true?'Android reports granted or active':item.granted===false?'Requested; not granted':'Declared capability; active access unverified';
                    return '<li><strong>'+esc(item.name||item.packageName||'App')+'</strong><span class="muted"> — '+esc(status)+'</span><button type="button" class="permissionSettings" data-pkg="'+esc(item.packageName||'')+'">App settings</button></li>';
                  }).join('');
                  const omitted=list.length>80?'<p class="muted">Only the first 80 visible apps are listed for this category.</p>':'';
                  return '<details class="scouttrace-detail scouttrace-permission"><summary>'+esc(label)+' <span class="scouttrace-permission-count">'+list.length+' app(s)</span></summary><p class="muted">'+esc(detail)+'</p>'+(list.length?'<ul>'+rows+'</ul>'+omitted:'<p class="muted">No matching non-system apps are visible to ScoutTrace.</p>')+'</details>';
                }).join('');
                const visibleFindings=fs.filter(f=>!(disp[f.packageName]==='IGNORE' && f.level!=='HIGH CAUTION'));
                const suppressedCount=fs.length-visibleFindings.length;
                visibleFindings.sort((a,b)=>{const rank=x=>x.level==='HIGH CAUTION'?4:x.level==='ELEVATED'?3:x.level==='REVIEW'?2:1;return rank(b)-rank(a)||Number(disp[b.packageName]==='WATCH')-Number(disp[a.packageName]==='WATCH');});
                const priority=visibleFindings[0],headline=n.headline||'ScoutTrace investigation complete';
                const nextAction=priority?.remediation?.nextStep||n.recommendedAction||'Save a trusted baseline and review future changes.';
                const priorityPanel='<section class="scouttrace-priority" aria-label="Recommended next action"><strong>What should I do next?</strong><p>'+esc(priority?.title||'No elevated indicator identified')+'</p><p>'+esc(nextAction)+'</p><small>Evidence-based triage only. This is not a malware verdict.</small>'+(priority?'<button type="button" id="reviewPriority">Review first finding</button>':'')+'</section>';
                const findingHtml=visibleFindings.length?visibleFindings.map(f=>'<div class="hist scouttrace-finding"><strong>'+esc(f.title||f.packageName)+'</strong><div class="status '+klass(f.level)+'">'+esc(f.level)+'</div><div class="muted">'+esc(f.detail)+'</div>'+(f.remediation?.nextStep?'<div class="muted"><b>Next:</b> '+esc(f.remediation.nextStep)+'</div>':'')+'<details class="scouttrace-detail"><summary>Finding details &amp; guidance</summary><p>'+esc(f.detail||'No further evidence supplied by this scan.')+'</p>'+(f.remediation?.nextStep?'<p><b>Recommended next step:</b> '+esc(f.remediation.nextStep)+'</p>':'<p>Review the available evidence before making changes.</p>')+'<p class="scouttrace-evidence"><b>Observed app:</b> '+esc(f.packageName||'Device-level indicator')+'</p><p class="muted">An indicator is not proof of malware. Android visibility and permissions may limit this finding.</p></details>'+(f.packageName?'<div class="acts"><button class="btn appSettings" data-pkg="'+esc(f.packageName)+'">Review App</button><button class="btn trustApp" data-pkg="'+esc(f.packageName)+'">Trust</button><button class="btn watchApp" data-pkg="'+esc(f.packageName)+'">Watch</button><button class="btn ignoreApp" data-pkg="'+esc(f.packageName)+'">Ignore</button><button class="btn resetApp" data-pkg="'+esc(f.packageName)+'">Reset</button><button class="btn uninstallApp" data-pkg="'+esc(f.packageName)+'">Uninstall…</button></div><div class="muted">Classification: '+esc(disp[f.packageName]||'DEFAULT')+'</div>':'')+'</div>').join(''):'<div class="notice"><b>No elevated app indicators found.</b></div>';
                out.innerHTML=quickSummary+priorityPanel+'<div class="status '+klass(r.level)+'">'+esc(r.level)+'</div><h3>'+esc(headline)+'</h3><div class="notice"><b>Coverage:</b> '+esc(cov.explanation||'Android may limit package visibility.')+'</div><div class="notice"><b>'+esc(c.appsScanned)+'</b> non-system apps analyzed of <b>'+esc(c.visiblePackages)+'</b> visible packages • <b>'+esc(c.findings)+'</b> findings • <b>'+esc(c.otherInstallers)+'</b> other installer IDs • <b>'+esc(c.unknownInstallers)+'</b> unavailable installer IDs • <b>'+esc(changes)+'</b> baseline changes. Other or unknown install source is not proof of malware.</div>'+(priority?'<div class="notice"><b>Priority:</b> '+esc(priority.title)+' — '+esc(priority.detail)+'</div>':'')+'<h3>Findings</h3>'+(suppressedCount?'<div class="muted">'+suppressedCount+' ignored lower-priority finding(s) hidden. Reset their classification to show them again.</div>':'')+findingHtml+'<h3>Permission Matrix</h3>'+permissionHtml+'<h3>Baseline</h3><div class="hist"><div class="muted">'+esc(bn.headline||(b.exists?'Baseline comparison ready':'No trusted baseline saved'))+' — '+esc(bn.detail||'')+'</div></div><h3>Security Diff</h3>'+diffContext+diffHtml+'<h3>Security Timeline</h3><p class="muted">Entries are recorded at observation time; they do not establish when a change originally occurred. Recent observations appear first.</p>'+(tl.length?tl.slice(0,50).map(e=>'<div class="hist"><strong>'+esc(e.title)+'</strong><div class="muted">'+new Date(e.time).toLocaleString()+' • '+esc(e.detail)+'</div></div>').join(''):'<p class="muted">No security-state changes recorded yet. Timeline entries reflect observation time, not the exact time an Android permission or setting changed.</p>')+'<div class="acts"><button id="openA11y" class="btn">Accessibility Settings</button><button id="openSecurity" class="btn">Security Settings</button><button id="clearNativeHistory" class="btn">Clear Local Security Data</button><button id="clearNativeClassifications" class="btn">Clear Trust / Watch / Ignore</button></div>';
                out.querySelectorAll('[data-diff-filter]').forEach(button=>button.addEventListener('click',()=>{
                  const selected=button.dataset.diffFilter;
                  out.querySelectorAll('[data-diff-filter]').forEach(item=>item.setAttribute('aria-pressed',String(item===button)));
                  let shown=0;
                  out.querySelectorAll('.scouttrace-diff-item').forEach(item=>{
                    item.hidden=selected!=='all'&&item.dataset.diffCategory!==selected;
                    if(!item.hidden)shown++;
                  });
                  const message=out.querySelector('#scouttraceDiffCount');
                  if(message)message.textContent=shown+' of '+visibleDiff.length+' displayed changes match this filter.'+
                    (diffItems.length>visibleDiff.length?' Only the first '+visibleDiff.length+' changes are shown.':'');
                }));
                out.querySelector('#reviewPriority')?.addEventListener('click',()=>out.querySelector('.scouttrace-finding')?.scrollIntoView({block:'center',behavior:'smooth'}));
                out.querySelectorAll('.appSettings').forEach(x=>x.onclick=async()=>{try{if(await nativeCall('openAppSettings',x.dataset.pkg)!==true)throw new Error('Unavailable');}catch(_){alert('Android app settings could not be opened.');}});
                out.querySelectorAll('.permissionSettings').forEach(x=>x.onclick=async()=>{try{if(await nativeCall('openAppSettings',x.dataset.pkg)!==true)throw new Error('Unavailable');}catch(_){alert('Android app settings could not be opened.');}});
                const setClassification=async(button,state,label)=>{
                  button.disabled=true;
                  try{const ok=await nativeCall('setAppDisposition',button.dataset.pkg,state);if(ok!==true)throw new Error('Not saved');button.textContent=label+' ✓';}
                  catch(e){alert('Classification could not be saved.');}
                  finally{button.disabled=false;}
                };
                out.querySelectorAll('.trustApp').forEach(x=>x.onclick=()=>setClassification(x,'TRUSTED','Trusted'));
                out.querySelectorAll('.watchApp').forEach(x=>x.onclick=()=>setClassification(x,'WATCH','Watching'));
                out.querySelectorAll('.ignoreApp').forEach(x=>x.onclick=()=>setClassification(x,'IGNORE','Ignored'));
                out.querySelectorAll('.resetApp').forEach(x=>x.onclick=()=>setClassification(x,'DEFAULT','Reset'));
                out.querySelectorAll('.uninstallApp').forEach(x=>x.onclick=async()=>{try{if(await nativeCall('requestUninstall',x.dataset.pkg)!==true)throw new Error('Unavailable');}catch(_){alert('Android uninstall confirmation could not be opened. No app was removed by ScoutTrace.');}});
                document.getElementById('openA11y').onclick=async()=>{try{if(await nativeCall('openAccessibilitySettings')!==true)throw new Error('Unavailable');}catch(_){alert('Android Accessibility Settings could not be opened.');}};
                document.getElementById('openSecurity').onclick=async()=>{try{if(await nativeCall('openSecuritySettings')!==true)throw new Error('Unavailable');}catch(_){alert('Android Security Settings could not be opened.');}};
                document.getElementById('clearNativeClassifications').onclick=async()=>{/* Native Android confirmation is mandatory; a web confirmation alone is insufficient. */try{const ok=await nativeCall('clearAppDispositions');if(ok!==true)throw new Error('Cancelled or unavailable');alert('Saved classifications cleared. Run a new investigation to refresh the results.');}catch(e){alert('Could not clear saved classifications.');}};
                document.getElementById('clearNativeHistory').onclick=async()=>{/* Native Android confirmation is mandatory; a web confirmation alone is insufficient. */try{const ok=await nativeCall('clearSecurityHistory');if(ok!==true)throw new Error('Cancelled or unavailable');out.innerHTML='<div class="notice">Local ScoutTrace security data cleared.</div>';}catch(e){alert('Could not clear local security data.');}}
              }catch(e){
                out.innerHTML='<div class="status review">INCOMPLETE</div><p class="muted">Investigation could not complete. ScoutTrace has not declared the device safe or unsafe.</p><p class="muted">If locally stored encrypted records have become unreadable, you can choose to erase only the affected category below. These actions cannot be undone.</p><div class="acts"><button type="button" id="retryInvestigation">Try Again</button><button type="button" id="recoverHistory">Erase Local History / Baseline</button><button type="button" id="recoverClassifications">Erase App Classifications</button></div>';
                out.querySelector('#retryInvestigation').onclick=()=>document.getElementById('nativePhoneScan')?.click();
                out.querySelector('#recoverHistory').onclick=async()=>{
                  /* Native Android confirmation is mandatory; a web confirmation alone is insufficient. */
                  try{if(await nativeCall('clearSecurityHistory')!==true)throw new Error('Cancelled or unavailable');out.innerHTML='<p class="muted">Local investigation history erased. You may start a fresh scan.</p>';}
                  catch(_){alert('Deletion failed. Existing records may remain on this device.');}
                };
                out.querySelector('#recoverClassifications').onclick=async()=>{
                  /* Native Android confirmation is mandatory; a web confirmation alone is insufficient. */
                  try{if(await nativeCall('clearAppDispositions')!==true)throw new Error('Cancelled or unavailable');out.innerHTML='<p class="muted">Saved app classifications erased. Run a new investigation to continue.</p>';}
                  catch(_){alert('Deletion failed. Existing classifications may remain on this device.');}
                };
              }
            };
          };
          const launcher=document.createElement('section');
          launcher.className='scouttrace-launcher';
          launcher.setAttribute('aria-label','ScoutTrace quick actions');
          launcher.innerHTML='<div class="scouttrace-launcher-title"><strong>YOUR SECURITY INVESTIGATOR</strong><span>Real evidence. Clear next steps.</span></div><button type="button" class="scouttrace-run">▶ Run Investigation</button><div class="scouttrace-shortcuts"><button type="button" data-tool="scan">Investigation</button><button type="button" data-tool="timeline">Security Diff</button><button type="button" data-tool="privacy">Privacy</button><button type="button" data-tool="apk">Inspect APK</button><button type="button" data-tool="scam">Scam Check</button><button type="button" data-tool="family">Family Help</button></div>';
          const launch=()=>card.click();
          launcher.querySelector('.scouttrace-run').onclick=launch;
          launcher.querySelector('[data-tool="scan"]').onclick=launch;
          launcher.querySelector('[data-tool="timeline"]').onclick=()=>{launch();const run=document.getElementById('nativePhoneScan');if(run)run.click();};
          launcher.querySelector('[data-tool="privacy"]').onclick=()=>{launch();document.getElementById('privacyInfo')?.click();};
          launcher.querySelector('[data-tool="apk"]').onclick=()=>{launch();document.getElementById('apkInspect')?.click();};
          launcher.querySelector('[data-tool="scam"]').onclick=()=>{launch();document.getElementById('scamOpinion')?.click();};
          launcher.querySelector('[data-tool="family"]').onclick=()=>{launch();document.getElementById('familyAssist')?.click();};
          grid.parentNode?.insertBefore(launcher,grid);
          const nav=document.createElement('nav');
          nav.className='scouttrace-bottom-nav';
          nav.setAttribute('aria-label','ScoutTrace investigation navigation');
          nav.innerHTML='<button type="button" data-view="home" aria-current="page">⌂ Home</button><button type="button" data-view="investigate">⌕ Investigate</button><button type="button" data-view="changes">⇄ Changes</button><button type="button" data-view="timeline">◷ Timeline</button><button type="button" data-view="tools">⚙ Tools</button>';
          const select=view=>{
            nav.querySelectorAll('button').forEach(b=>{if(b.dataset.view===view)b.setAttribute('aria-current','page');else b.removeAttribute('aria-current');});
            if(view==='home'){document.getElementById('home')?.classList.add('active');document.getElementById('scan')?.classList.remove('active');}
            else if(view==='investigate'){launch();}
            else if(view==='changes'){launch();const result=document.getElementById('nativePhoneResult');const scrollToDiff=()=>{const heading=[...(result?.querySelectorAll('h3')||[])].find(h=>h.textContent?.trim()==='Security Diff');heading?.scrollIntoView({block:'start',behavior:'smooth'});};if(result?.querySelector('h3'))scrollToDiff();else{document.getElementById('nativePhoneScan')?.click();const observer=new MutationObserver(()=>{if([...(result?.querySelectorAll('h3')||[])].some(h=>h.textContent?.trim()==='Security Diff')){observer.disconnect();scrollToDiff();}});if(result)observer.observe(result,{childList:true,subtree:true});setTimeout(()=>observer.disconnect(),60000);}}
            else if(view==='timeline'){launch();const result=document.getElementById('nativePhoneResult');const scroll=()=>[...(result?.querySelectorAll('h3')||[])].find(h=>h.textContent?.trim()==='Security Timeline')?.scrollIntoView({block:'start',behavior:'smooth'});if(result?.querySelector('h3'))scroll();else{document.getElementById('nativePhoneScan')?.click();const observer=new MutationObserver(()=>{if([...(result?.querySelectorAll('h3')||[])].some(h=>h.textContent?.trim()==='Security Timeline')){observer.disconnect();scroll();}});if(result)observer.observe(result,{childList:true,subtree:true});setTimeout(()=>observer.disconnect(),60000);}}
            else if(view==='tools'){launch();document.getElementById('privacyInfo')?.focus();}
          };
          nav.querySelectorAll('button').forEach(b=>b.onclick=()=>select(b.dataset.view));
          launcher.insertAdjacentElement('afterend',nav);

          grid.appendChild(card);
          window.addEventListener('scouttrace-shared-text',event=>{
            if(typeof event.detail!=='string')return;
            launch();
            document.getElementById('scamOpinion')?.click();
            const field=document.getElementById('scouttraceScamText');
            if(field){field.value=event.detail.slice(0,4000);field.focus();}
          });
            const queued=window.__scoutTracePendingSharedText;
            if(typeof queued==='string'){
              delete window.__scoutTracePendingSharedText;
              window.dispatchEvent(new CustomEvent('scouttrace-shared-text',{detail:queued}));
            }
            return true;
          };
          if(mount())return;
          if(window.__scoutTraceMountObserver)return;
          const observer=new MutationObserver(()=>{
            if(mount()){observer.disconnect();window.__scoutTraceMountObserver=null;}
          });
          window.__scoutTraceMountObserver=observer;
          observer.observe(document.documentElement,{childList:true,subtree:true});
          setTimeout(()=>{observer.disconnect();window.__scoutTraceMountObserver=null;},30000);
        })();
        """.trimIndent()
        webView.evaluateJavascript(js) { deliverSharedText() }
    }

    private fun acceptSharedText(incoming: Intent?) {
        if (incoming?.action != Intent.ACTION_SEND || incoming.type != "text/plain") return
        // Never read attachment URIs or grant arbitrary access from share intents.
        pendingSharedText = SharedTextReview.normalize(incoming.getCharSequenceExtra(Intent.EXTRA_TEXT))
    }

    private fun deliverSharedText() {
        val current = webView.url?.let(Uri::parse)
        if (current?.scheme != "https" || current.host != trustedHost ||
            (current.port != -1 && current.port != 443)) return
        val text = pendingSharedText ?: return
        val literal = JSONObject.quote(text)
        // The remote app may mount asynchronously. Hold the text only in page memory
        // until ScoutTrace installs its local-review event handler; never auto-open links.
        webView.evaluateJavascript(
            "(function(){window.__scoutTracePendingSharedText=" + literal +
            ";if(window.__scoutTracePhoneSweepInjected){" +
            "const text=window.__scoutTracePendingSharedText;" +
            "delete window.__scoutTracePendingSharedText;" +
            "window.dispatchEvent(new CustomEvent('scouttrace-shared-text',{detail:text}));}})();"
        ) { if (pendingSharedText == text) pendingSharedText = null }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptSharedText(intent)
        deliverSharedText()
    }

    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        if (requestCode == 201) {
            val req = pendingPermissionRequest
            pendingPermissionRequest = null
            if (req != null) {
                val current = webView.url?.let(Uri::parse)
                val trusted = current?.scheme == "https" && current.host == trustedHost &&
                    (current.port == -1 || current.port == 443)
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED &&
                    trusted && req.origin.scheme == "https" && req.origin.host == trustedHost &&
                    (req.origin.port == -1 || req.origin.port == 443)) {
                    req.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
                } else req.deny()
            }
        }
    }
    override fun onResume() {
        super.onResume()
        activityResumedForConfirmation = true
        if (::webView.isInitialized) webView.onResume()
    }
    override fun onPause() {
        activityResumedForConfirmation = false
        if (::webView.isInitialized) webView.onPause()
        super.onPause()
    }
    override fun onDestroy(){pendingDestructiveConfirmation?.dismiss();nativeExecutor.shutdownNow();webView.destroy();super.onDestroy()}
}
