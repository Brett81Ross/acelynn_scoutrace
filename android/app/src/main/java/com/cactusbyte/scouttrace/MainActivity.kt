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
    private val nativeBridge by lazy { ScoutTraceBridge(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webView = WebView(this)
        setContentView(webView)
        WebView.setWebContentsDebuggingEnabled(false)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
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
                    if (request.origin.scheme != "https" || request.origin.host != trustedHost) { request.deny(); return@runOnUiThread }
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
                    (origin.port != -1 && origin.port != 443)) return@addWebMessageListener
                val raw = message.data ?: return@addWebMessageListener
                if (raw.length > 8192) return@addWebMessageListener
                val request = try { JSONObject(raw) } catch (_: Exception) { return@addWebMessageListener }
                val id = request.optString("id").takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,64}")) }
                    ?: return@addWebMessageListener
                val method = request.optString("method")
                val args = request.optJSONArray("args")
                val packageName = args?.optString(0) ?: ""
                if (method in setOf("openAppSettings", "requestUninstall", "setAppDisposition") &&
                    !packageName.matches(Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)+"))) {
                    reply.postMessage(JSONObject().put("id", id).put("ok", false)
                        .put("error", "Invalid package name").toString())
                    return@addWebMessageListener
                }
                Thread {
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
                            else -> throw IllegalArgumentException("Unsupported native operation")
                        }
                        response.put("ok", true).put("value", value)
                    } catch (_: Exception) {
                        response.put("ok", false).put("error", "Native operation unavailable")
                    }
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed) reply.postMessage(response.toString())
                    }
                }.start()
            }
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
        Thread {
            val result = try { ApkInspector(applicationContext).inspect(uri).toString() }
                catch (e: Exception) { org.json.JSONObject().put("ok", false).put("error", e.message ?: "APK inspection failed").toString() }
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    webView.evaluateJavascript("window.dispatchEvent(new CustomEvent(\"scouttrace-apk-result\",{detail:"+org.json.JSONObject.quote(result)+"}));", null)
                }
            }
        }.start()
    }

    private fun injectPhoneSecuritySweep() {
        val js = """
        (() => {
          if (window.__scoutTracePhoneSweepInjected) return;
          window.__scoutTracePhoneSweepInjected = true;
          const nativePending=new Map();
          let nativeSequence=0;
          const nativePort=window.ScoutTraceNative;
          if(!nativePort || typeof nativePort.postMessage!=='function'){
            console.warn('ScoutTrace secure native messaging unavailable');
            return;
          }
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
            const timeout=setTimeout(()=>{nativePending.delete(id);reject(new Error('Native operation timed out'));},60000);
            nativePending.set(id,{resolve:v=>{clearTimeout(timeout);resolve(v);},reject:e=>{clearTimeout(timeout);reject(e);}});
            try{nativePort.postMessage(JSON.stringify({id,method,args}));}
            catch(e){clearTimeout(timeout);nativePending.delete(id);reject(e);}
          });
          const grid=document.querySelector('.grid'); if(!grid) return;
          // Approved ScoutTrace ten-screen visual language: scoped, reversible styling.
          if(!document.getElementById('scouttrace-design-lock')) {
            const theme=document.createElement('style');
            theme.id='scouttrace-design-lock';
            theme.textContent=`
              .scouttrace-detail{margin-top:12px;padding:12px;border-radius:12px;background:rgba(43,118,187,.08);border:1px solid rgba(68,137,197,.25)}
              .scouttrace-detail summary{cursor:pointer;min-height:42px;display:flex;align-items:center;font-weight:750;color:inherit}
              .scouttrace-detail p{margin:8px 0;line-height:1.55}
              .scouttrace-detail .scouttrace-evidence{overflow-wrap:anywhere;font-size:.88rem}
              .scouttrace-finding{padding:16px!important;margin:10px 0!important;border:1px solid rgba(100,149,193,.3)!important;border-left:4px solid #3989d6!important;border-radius:15px!important;background:rgba(49,111,167,.06)}
              .scouttrace-finding .status{display:inline-flex;margin:8px 0;max-width:100%}
              .scouttrace-finding .acts{display:flex;flex-wrap:wrap;gap:8px;margin-top:12px}
              .scouttrace-finding .acts button{min-height:42px;border-radius:10px}
              .scouttrace-finding .muted{line-height:1.55;overflow-wrap:anywhere}
              .scouttrace-bottom-nav{position:sticky;bottom:0;z-index:10;display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:3px;padding:8px 6px max(8px,env(safe-area-inset-bottom));background:#0b1c30;border:1px solid #24425d;border-radius:16px;margin:12px 0;box-shadow:0 8px 24px rgba(0,0,0,.2)}
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
            body.innerHTML='<div class="scouttrace-native-head"><div class="scouttrace-mark">⛨</div><div><strong>Your Security Investigator</strong><small>Detect · Correlate · Explain · Prioritize · Remediate</small></div></div><div class="panel"><div class="notice"><b>Native engine connected.</b> Analysis is local to this device.</div><div class="acts"><button id="nativePhoneScan" class="btn primary">Run Complete ScoutTrace</button><button id="saveBaseline" class="btn">Save Trusted Baseline</button><button id="privacyInfo" class="btn">Privacy</button><button id="exportReport" class="btn">Export Report</button><button id="apkInspect" class="btn">Inspect APK</button></div><pre id="apkResult" hidden style="white-space:pre-wrap;overflow-wrap:anywhere"></pre><div id="nativePhoneResult" class="result" hidden></div></div>';
            document.getElementById('saveBaseline').onclick=async()=>{const r=JSON.parse(await nativeCall('saveSecurityBaseline'));alert(r.ok?'Trusted baseline saved on this device.':'Baseline could not be saved.');};
            document.getElementById('privacyInfo').onclick=async()=>{const p=JSON.parse(await nativeCall('privacySummary'));alert(p.statement);};
            document.getElementById('apkInspect').onclick=()=>nativeCall('openApkPicker');
            window.addEventListener('scouttrace-apk-result',event=>{const out=document.getElementById('apkResult');if(!out)return;try{const result=JSON.parse(event.detail);out.hidden=false;out.textContent=result.ok?JSON.stringify(result,null,2):'APK inspection failed: '+result.error;}catch(err){out.hidden=false;out.textContent='Unable to display APK result.';}});
            document.getElementById('exportReport').onclick=async()=>{if(!await nativeCall('shareSecurityReport'))alert('Android share sheet could not be opened.');};
            document.getElementById('nativePhoneScan').onclick=async()=>{
              const out=document.getElementById('nativePhoneResult'); out.hidden=false; out.innerHTML='<p class="muted">Running ScoutTrace…</p>';
              try{
                const r=JSON.parse(await nativeCall('runSecurityScan')),c=r.counts||{},fs=r.findings||[],b=r.baseline||{},pm=r.permissionMatrix||{},tl=r.timeline||[],disp=r.dispositions||{},cov=r.coverage||{},n=r.narrative||{};
                const changes=b.exists?((b.newApps||[]).length+(b.removedApps||[]).length+(b.riskChanges||[]).length+(b.metadataChanges||[]).length):0,bn=b.narrative||{};
                const quickSummary='<div class="scouttrace-summary-grid"><div class="scouttrace-metric"><b>'+esc(c.appsScanned??0)+'</b><small>Visible apps</small></div><div class="scouttrace-metric"><b>'+esc(changes)+'</b><small>Baseline changes</small></div><div class="scouttrace-metric"><b>'+esc(c.findings??0)+'</b><small>Findings</small></div><div class="scouttrace-metric"><b>'+esc((n.priorities||[]).length)+'</b><small>Priorities</small></div></div>';
                const diffItems=b.exists?[...(b.newApps||[]).map(x=>({title:'Newly visible app',detail:x})),...(b.removedApps||[]).map(x=>({title:'App no longer visible',detail:x})),...(b.riskChanges||[]).map(x=>({title:'Risk classification changed',detail:x.packageName+' · '+x.fromLevel+' → '+x.toLevel})),...(b.metadataChanges||[]).map(x=>({title:'App metadata changed',detail:x.packageName+' · '+(x.fields||[]).join(', ')}))]:[];
                const diffHtml=diffItems.length?diffItems.slice(0,15).map(x=>'<div class="hist"><strong>'+esc(x.title)+'</strong><div class="muted">'+esc(x.detail)+'</div></div>').join(''):'<div class="hist"><div class="muted">'+esc(b.exists?'No observed changes in available baseline fields.':'Save a trusted baseline to begin tracking observed changes.')+'</div></div>';
                const permissionHtml=Object.entries(pm).map(([k,v])=>'<div class="hist"><strong>'+esc(k.toUpperCase())+'</strong><div class="muted">'+(v||[]).length+' app(s) request this permission</div></div>').join('');
                const visibleFindings=fs.filter(f=>!(disp[f.packageName]==='IGNORE' && f.level!=='HIGH CAUTION'));
                const suppressedCount=fs.length-visibleFindings.length;
                visibleFindings.sort((a,b)=>{const rank=x=>x.level==='HIGH CAUTION'?4:x.level==='ELEVATED'?3:x.level==='REVIEW'?2:1;return rank(b)-rank(a)||Number(disp[b.packageName]==='WATCH')-Number(disp[a.packageName]==='WATCH');});
                const priority=visibleFindings[0],headline=n.headline||'ScoutTrace investigation complete';
                const findingHtml=visibleFindings.length?visibleFindings.map(f=>'<div class="hist scouttrace-finding"><strong>'+esc(f.title||f.packageName)+'</strong><div class="status '+klass(f.level)+'">'+esc(f.level)+'</div><div class="muted">'+esc(f.detail)+'</div>'+(f.remediation?.nextStep?'<div class="muted"><b>Next:</b> '+esc(f.remediation.nextStep)+'</div>':'')+'<details class="scouttrace-detail"><summary>Finding details &amp; guidance</summary><p>'+esc(f.detail||'No further evidence supplied by this scan.')+'</p>'+(f.remediation?.nextStep?'<p><b>Recommended next step:</b> '+esc(f.remediation.nextStep)+'</p>':'<p>Review the available evidence before making changes.</p>')+'<p class="scouttrace-evidence"><b>Observed app:</b> '+esc(f.packageName||'Device-level indicator')+'</p><p class="muted">An indicator is not proof of malware. Android visibility and permissions may limit this finding.</p></details>'+(f.packageName?'<div class="acts"><button class="btn appSettings" data-pkg="'+esc(f.packageName)+'">Review App</button><button class="btn trustApp" data-pkg="'+esc(f.packageName)+'">Trust</button><button class="btn watchApp" data-pkg="'+esc(f.packageName)+'">Watch</button><button class="btn ignoreApp" data-pkg="'+esc(f.packageName)+'">Ignore</button><button class="btn resetApp" data-pkg="'+esc(f.packageName)+'">Reset</button><button class="btn uninstallApp" data-pkg="'+esc(f.packageName)+'">Uninstall…</button></div><div class="muted">Classification: '+esc(disp[f.packageName]||'DEFAULT')+'</div>':'')+'</div>').join(''):'<div class="notice"><b>No elevated app indicators found.</b></div>';
                out.innerHTML=quickSummary+'<div class="status '+klass(r.level)+'">'+esc(r.level)+'</div><h3>'+esc(headline)+'</h3><div class="notice"><b>Coverage:</b> '+esc(cov.explanation||'Android may limit package visibility.')+'</div><div class="notice"><b>'+c.appsScanned+'</b> visible packages inspected • <b>'+c.findings+'</b> findings • <b>'+c.sideloaded+'</b> sideloaded indicators • <b>'+changes+'</b> baseline changes</div>'+(priority?'<div class="notice"><b>Priority:</b> '+esc(priority.title)+' — '+esc(priority.detail)+'</div>':'')+'<h3>Findings</h3>'+(suppressedCount?'<div class="muted">'+suppressedCount+' ignored lower-priority finding(s) hidden. Reset their classification to show them again.</div>':'')+findingHtml+'<h3>Permission Matrix</h3>'+permissionHtml+'<h3>Baseline</h3><div class="hist"><div class="muted">'+esc(bn.headline||(b.exists?'Baseline comparison ready':'No trusted baseline saved'))+' — '+esc(bn.detail||'')+'</div></div><h3>Security Diff</h3>'+diffHtml+'<h3>Security Timeline</h3>'+(tl.length?tl.slice(0,10).map(e=>'<div class="hist"><strong>'+esc(e.title)+'</strong><div class="muted">'+new Date(e.time).toLocaleString()+' • '+esc(e.detail)+'</div></div>').join(''):'<p class="muted">No security-state changes recorded yet.</p>')+'<div class="acts"><button id="openA11y" class="btn">Accessibility Settings</button><button id="openSecurity" class="btn">Security Settings</button><button id="clearNativeHistory" class="btn">Clear Local Security Data</button></div>';
                out.querySelectorAll('.appSettings').forEach(x=>x.onclick=()=>nativeCall('openAppSettings',x.dataset.pkg));
                out.querySelectorAll('.trustApp').forEach(x=>x.onclick=()=>{nativeCall('setAppDisposition',x.dataset.pkg,'TRUSTED');x.textContent='Trusted ✓';});
                out.querySelectorAll('.watchApp').forEach(x=>x.onclick=()=>{nativeCall('setAppDisposition',x.dataset.pkg,'WATCH');x.textContent='Watching ✓';});
                out.querySelectorAll('.ignoreApp').forEach(x=>x.onclick=()=>{nativeCall('setAppDisposition',x.dataset.pkg,'IGNORE');x.textContent='Ignored ✓';});
                out.querySelectorAll('.resetApp').forEach(x=>x.onclick=()=>{nativeCall('setAppDisposition',x.dataset.pkg,'DEFAULT');x.textContent='Reset ✓';});
                out.querySelectorAll('.uninstallApp').forEach(x=>x.onclick=()=>nativeCall('requestUninstall',x.dataset.pkg));
                document.getElementById('openA11y').onclick=()=>nativeCall('openAccessibilitySettings');
                document.getElementById('openSecurity').onclick=()=>nativeCall('openSecuritySettings');
                document.getElementById('clearNativeHistory').onclick=()=>{if(confirm('Clear ScoutTrace baseline and local security timeline?')){nativeCall('clearSecurityHistory');out.innerHTML='<div class="notice">Local ScoutTrace security data cleared.</div>';}}
              }catch(e){out.innerHTML='<div class="status review">REVIEW</div><p class="muted">Native scan failed: '+esc(e.message)+'</p>';}
            };
          };
          const launcher=document.createElement('section');
          launcher.className='scouttrace-launcher';
          launcher.setAttribute('aria-label','ScoutTrace quick actions');
          launcher.innerHTML='<div class="scouttrace-launcher-title"><strong>YOUR SECURITY INVESTIGATOR</strong><span>Real evidence. Clear next steps.</span></div><button type="button" class="scouttrace-run">▶ Run Investigation</button><div class="scouttrace-shortcuts"><button type="button" data-tool="scan">Investigation</button><button type="button" data-tool="timeline">Security Diff</button><button type="button" data-tool="privacy">Privacy</button><button type="button" data-tool="apk">Inspect APK</button></div>';
          const launch=()=>card.click();
          launcher.querySelector('.scouttrace-run').onclick=launch;
          launcher.querySelector('[data-tool="scan"]').onclick=launch;
          launcher.querySelector('[data-tool="timeline"]').onclick=()=>{launch();const run=document.getElementById('nativePhoneScan');if(run)run.click();};
          launcher.querySelector('[data-tool="privacy"]').onclick=()=>{launch();document.getElementById('privacyInfo')?.click();};
          launcher.querySelector('[data-tool="apk"]').onclick=()=>{launch();document.getElementById('apkInspect')?.click();};
          grid.parentNode?.insertBefore(launcher,grid);
          const nav=document.createElement('nav');
          nav.className='scouttrace-bottom-nav';
          nav.setAttribute('aria-label','ScoutTrace investigation navigation');
          nav.innerHTML='<button type="button" data-view="home" aria-current="page">⌂ Home</button><button type="button" data-view="investigate">⌕ Investigate</button><button type="button" data-view="changes">⇄ Changes</button><button type="button" data-view="tools">⚙ Tools</button>';
          const select=view=>{
            nav.querySelectorAll('button').forEach(b=>{if(b.dataset.view===view)b.setAttribute('aria-current','page');else b.removeAttribute('aria-current');});
            if(view==='home'){document.getElementById('home')?.classList.add('active');document.getElementById('scan')?.classList.remove('active');}
            else if(view==='investigate'){launch();}
            else if(view==='changes'){launch();document.getElementById('nativePhoneScan')?.click();const result=document.getElementById('nativePhoneResult');const heading=[...(result?.querySelectorAll('h3')||[])].find(h=>h.textContent?.trim()==='Security Diff');heading?.scrollIntoView({block:'start',behavior:'smooth'});}
            else if(view==='tools'){launch();document.getElementById('privacyInfo')?.focus();}
          };
          nav.querySelectorAll('button').forEach(b=>b.onclick=()=>select(b.dataset.view));
          launcher.insertAdjacentElement('afterend',nav);

          grid.appendChild(card);
        })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        if(requestCode==201){val req=pendingPermissionRequest;pendingPermissionRequest=null;if(grantResults.firstOrNull()==PackageManager.PERMISSION_GRANTED)req?.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))else req?.deny()}
    }
    override fun onDestroy(){webView.destroy();super.onDestroy()}
}
