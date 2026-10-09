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

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private var pendingPermissionRequest: PermissionRequest? = null
    private val trustedHost = "acelynn-scoutrace.vercel.app"
    private val apkPickerCode = 301

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
            override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest): Boolean {
                val uri = request.url
                if (uri.scheme == "https" && uri.host == trustedHost) return false
                if (request.isForMainFrame) {
                    try { startActivity(Intent(Intent.ACTION_VIEW, uri)) } catch (_: Exception) { }
                }
                return true
            }
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                if (android.net.Uri.parse(url).host == trustedHost && android.net.Uri.parse(url).scheme == "https") injectPhoneSecuritySweep()
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread {
                    if (request.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) {
                        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) request.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
                        else { pendingPermissionRequest = request; requestPermissions(arrayOf(Manifest.permission.CAMERA), 201) }
                    } else request.deny()
                }
            }
        }
        webView.addJavascriptInterface(ScoutTraceBridge(this), "ScoutTraceNative")
        webView.loadUrl("https://acelynn-scoutrace.vercel.app/")
    }

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
        val result = try { ApkInspector(this).inspect(uri).toString() }
            catch (e: Exception) { org.json.JSONObject().put("ok", false).put("error", e.message ?: "APK inspection failed").toString() }
        webView.evaluateJavascript("window.dispatchEvent(new CustomEvent(\"scouttrace-apk-result\",{detail:"+org.json.JSONObject.quote(result)+"}));", null)
    }

    private fun injectPhoneSecuritySweep() {
        val js = """
        (() => {
          if (window.__scoutTracePhoneSweepInjected) return;
          window.__scoutTracePhoneSweepInjected = true;
          const grid=document.querySelector('.grid'); if(!grid) return;
          const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
          const klass=l=>l==='CLEAR'?'clear':l==='REVIEW'?'review':l==='ELEVATED'?'elevated':'high';
          const card=document.createElement('button'); card.className='card';
          card.innerHTML='<div class="ico">⌾</div><strong>Complete ScoutTrace</strong><span>Native Android device sweep, baseline comparison, permissions and prioritized remediation.</span>';
          card.onclick=()=>{
            const home=document.getElementById('home'),scan=document.getElementById('scan'),body=document.getElementById('scanBody');
            if(!home||!scan||!body)return; home.classList.remove('active');scan.classList.add('active');
            document.getElementById('scanType').textContent='COMPLETE SCOUTTRACE';
            document.getElementById('scanTitle').textContent='Android security investigation';
            document.getElementById('scanDesc').textContent='Detect → correlate → explain → prioritize → remediate. Findings are evidence for review, not proof of malware.';
            body.innerHTML='<div class="panel"><div class="notice"><b>Native engine connected.</b> Analysis is local to this device.</div><div class="acts"><button id="nativePhoneScan" class="btn primary">Run Complete ScoutTrace</button><button id="saveBaseline" class="btn">Save Trusted Baseline</button><button id="privacyInfo" class="btn">Privacy</button><button id="exportReport" class="btn">Export Report</button></div><div id="nativePhoneResult" class="result" hidden></div></div>';
            document.getElementById('saveBaseline').onclick=()=>{const r=JSON.parse(window.ScoutTraceNative.saveSecurityBaseline());alert(r.ok?'Trusted baseline saved on this device.':'Baseline could not be saved.');};
            document.getElementById('privacyInfo').onclick=()=>{const p=JSON.parse(window.ScoutTraceNative.privacySummary());alert(p.statement);};
            document.getElementById('exportReport').onclick=()=>{if(!window.ScoutTraceNative.shareSecurityReport())alert('Android share sheet could not be opened.');};
            document.getElementById('nativePhoneScan').onclick=()=>{
              const out=document.getElementById('nativePhoneResult'); out.hidden=false; out.innerHTML='<p class="muted">Running ScoutTrace…</p>';
              try{
                const r=JSON.parse(window.ScoutTraceNative.runSecurityScan()),c=r.counts||{},fs=r.findings||[],b=r.baseline||{},pm=r.permissionMatrix||{},tl=r.timeline||[],disp=r.dispositions||{},cov=r.coverage||{},n=r.narrative||{};
                const changes=b.exists?((b.newApps||[]).length+(b.removedApps||[]).length+(b.riskChanges||[]).length):0,bn=b.narrative||{};
                const permissionHtml=Object.entries(pm).map(([k,v])=>'<div class="hist"><strong>'+esc(k.toUpperCase())+'</strong><div class="muted">'+(v||[]).length+' app(s) request this permission</div></div>').join('');
                const visibleFindings=fs.filter(f=>!(disp[f.packageName]==='IGNORE' && f.level!=='HIGH CAUTION'));
                const suppressedCount=fs.length-visibleFindings.length;
                visibleFindings.sort((a,b)=>{const rank=x=>x.level==='HIGH CAUTION'?4:x.level==='ELEVATED'?3:x.level==='REVIEW'?2:1;return rank(b)-rank(a)||Number(disp[b.packageName]==='WATCH')-Number(disp[a.packageName]==='WATCH');});
                const priority=visibleFindings[0],headline=n.headline||'ScoutTrace investigation complete';
                const findingHtml=visibleFindings.length?visibleFindings.map(f=>'<div class="hist"><strong>'+esc(f.title||f.packageName)+'</strong><div class="status '+klass(f.level)+'">'+esc(f.level)+'</div><div class="muted">'+esc(f.detail)+'</div>'+(f.remediation?.nextStep?'<div class="muted"><b>Next:</b> '+esc(f.remediation.nextStep)+'</div>':'')+''+(f.packageName?'<div class="acts"><button class="btn appSettings" data-pkg="'+esc(f.packageName)+'">Review App</button><button class="btn trustApp" data-pkg="'+esc(f.packageName)+'">Trust</button><button class="btn watchApp" data-pkg="'+esc(f.packageName)+'">Watch</button><button class="btn ignoreApp" data-pkg="'+esc(f.packageName)+'">Ignore</button><button class="btn resetApp" data-pkg="'+esc(f.packageName)+'">Reset</button><button class="btn uninstallApp" data-pkg="'+esc(f.packageName)+'">Uninstall…</button></div><div class="muted">Classification: '+esc(disp[f.packageName]||'DEFAULT')+'</div>':'')+'</div>').join(''):'<div class="notice"><b>No elevated app indicators found.</b></div>';
                out.innerHTML='<div class="status '+klass(r.level)+'">'+esc(r.level)+'</div><h3>'+esc(headline)+'</h3><div class="notice"><b>Coverage:</b> '+esc(cov.explanation||'Android may limit package visibility.')+'</div><div class="notice"><b>'+c.appsScanned+'</b> visible packages inspected • <b>'+c.findings+'</b> findings • <b>'+c.sideloaded+'</b> sideloaded indicators • <b>'+changes+'</b> baseline changes</div>'+(priority?'<div class="notice"><b>Priority:</b> '+esc(priority.title)+' — '+esc(priority.detail)+'</div>':'')+'<h3>Findings</h3>'+(suppressedCount?'<div class="muted">'+suppressedCount+' ignored lower-priority finding(s) hidden. Reset their classification to show them again.</div>':'')+findingHtml+'<h3>Permission Matrix</h3>'+permissionHtml+'<h3>Baseline</h3><div class="hist"><div class="muted">'+esc(bn.headline||(b.exists?'Baseline comparison ready':'No trusted baseline saved'))+' — '+esc(bn.detail||'')+'</div></div><h3>Security Timeline</h3>'+(tl.length?tl.slice(0,10).map(e=>'<div class="hist"><strong>'+esc(e.title)+'</strong><div class="muted">'+new Date(e.time).toLocaleString()+' • '+esc(e.detail)+'</div></div>').join(''):'<p class="muted">No security-state changes recorded yet.</p>')+'<div class="acts"><button id="openA11y" class="btn">Accessibility Settings</button><button id="openSecurity" class="btn">Security Settings</button><button id="clearNativeHistory" class="btn">Clear Local Security Data</button></div>';
                out.querySelectorAll('.appSettings').forEach(x=>x.onclick=()=>window.ScoutTraceNative.openAppSettings(x.dataset.pkg));
                out.querySelectorAll('.trustApp').forEach(x=>x.onclick=()=>{window.ScoutTraceNative.setAppDisposition(x.dataset.pkg,'TRUSTED');x.textContent='Trusted ✓';});
                out.querySelectorAll('.watchApp').forEach(x=>x.onclick=()=>{window.ScoutTraceNative.setAppDisposition(x.dataset.pkg,'WATCH');x.textContent='Watching ✓';});
                out.querySelectorAll('.ignoreApp').forEach(x=>x.onclick=()=>{window.ScoutTraceNative.setAppDisposition(x.dataset.pkg,'IGNORE');x.textContent='Ignored ✓';});
                out.querySelectorAll('.resetApp').forEach(x=>x.onclick=()=>{window.ScoutTraceNative.setAppDisposition(x.dataset.pkg,'DEFAULT');x.textContent='Reset ✓';});
                out.querySelectorAll('.uninstallApp').forEach(x=>x.onclick=()=>window.ScoutTraceNative.requestUninstall(x.dataset.pkg));
                document.getElementById('openA11y').onclick=()=>window.ScoutTraceNative.openAccessibilitySettings();
                document.getElementById('openSecurity').onclick=()=>window.ScoutTraceNative.openSecuritySettings();
                document.getElementById('clearNativeHistory').onclick=()=>{if(confirm('Clear ScoutTrace baseline and local security timeline?')){window.ScoutTraceNative.clearSecurityHistory();out.innerHTML='<div class="notice">Local ScoutTrace security data cleared.</div>';}}
              }catch(e){out.innerHTML='<div class="status review">REVIEW</div><p class="muted">Native scan failed: '+esc(e.message)+'</p>';}
            };
          };
          grid.appendChild(card);
        })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        if(requestCode==201){val req=pendingPermissionRequest;pendingPermissionRequest=null;if(grantResults.firstOrNull()==PackageManager.PERMISSION_GRANTED)req?.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))else req?.deny()}
    }
    override fun onDestroy(){webView.removeJavascriptInterface("ScoutTraceNative");webView.destroy();super.onDestroy()}
}
