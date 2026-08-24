package com.cactusbyte.scouttrace

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private var pendingPermissionRequest: PermissionRequest? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webView = WebView(this)
        setContentView(webView)
        WebView.setWebContentsDebuggingEnabled(false)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            allowFileAccess = false
            allowContentAccess = true
            mediaPlaybackRequiresUserGesture = true
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                injectPhoneSecuritySweep()
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread {
                    if (request.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) {
                        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            request.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
                        } else {
                            pendingPermissionRequest = request
                            requestPermissions(arrayOf(Manifest.permission.CAMERA), 201)
                        }
                    } else request.deny()
                }
            }
        }
        webView.addJavascriptInterface(ScoutTraceBridge(this), "ScoutTraceNative")
        webView.loadUrl("https://acelynn-scoutrace.vercel.app/")
    }

    private fun injectPhoneSecuritySweep() {
        val js = """
            (() => {
              if (window.__scoutTracePhoneSweepInjected) return;
              window.__scoutTracePhoneSweepInjected = true;
              const grid = document.querySelector('.grid');
              if (!grid) return;
              const card = document.createElement('button');
              card.className = 'card';
              card.innerHTML = '<div class="ico">⌾</div><strong>Phone Security Sweep</strong><span>Native Android inspection of installed apps and security-relevant device metadata.</span>';
              card.addEventListener('click', () => {
                const home = document.getElementById('home'), scan = document.getElementById('scan'), body = document.getElementById('scanBody');
                if (!home || !scan || !body) return;
                home.classList.remove('active'); scan.classList.add('active');
                document.getElementById('scanType').textContent='PHONE SECURITY SWEEP';
                document.getElementById('scanTitle').textContent='Android device security';
                document.getElementById('scanDesc').textContent='Inspects Android-exposed installed-app and security metadata. Findings are indicators for review, not proof of malware.';
                body.innerHTML='<div class="panel"><div class="notice"><b>Native engine connected:</b> ScoutTrace can inspect installed apps and Android security signals on this device.</div><div class="acts"><button id="nativePhoneScan" class="btn primary">Scan This Phone</button></div><div id="nativePhoneResult" class="result" hidden></div></div>';
                document.getElementById('nativePhoneScan').onclick = () => {
                  const out=document.getElementById('nativePhoneResult');
                  try {
                    const r=JSON.parse(window.ScoutTraceNative.runSecurityScan()), counts=r.counts||{}, fs=r.findings||[];
                    const klass=l=>l==='CLEAR'?'clear':l==='REVIEW'?'review':l==='ELEVATED'?'elevated':'high';
                    const esc=s=>String(s??'').replace(/[&<>\"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','\"':'&quot;',"'":'&#39;'}[c]));
                    out.hidden=false;
                    out.innerHTML='<div class="status '+klass(r.level)+'">'+esc(r.level)+'</div><p class="muted">'+esc(r.summary)+'</p><div class="notice"><b>'+counts.appsScanned+'</b> apps inspected • <b>'+counts.findings+'</b> findings • <b>'+counts.sideloaded+'</b> sideloaded indicators</div>'+fs.map(f=>'<div class="hist"><strong>'+esc(f.title||f.packageName)+'</strong><div class="status '+klass(f.level)+'">'+esc(f.level)+'</div><div class="muted">'+esc(f.detail)+'</div></div>').join('');
                  } catch(e) { out.hidden=false; out.innerHTML='<div class="status review">REVIEW</div><p class="muted">Native scan failed: '+e.message+'</p>'; }
                };
              });
              grid.appendChild(card);
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 201) {
            val req = pendingPermissionRequest
            pendingPermissionRequest = null
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) req?.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) else req?.deny()
        }
    }

    override fun onDestroy() {
        webView.removeJavascriptInterface("ScoutTraceNative")
        webView.destroy()
        super.onDestroy()
    }
}
