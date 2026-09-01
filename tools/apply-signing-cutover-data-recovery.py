from pathlib import Path

FILES=[Path('index.html'),Path('raw-index.html')]

replacement = r'''function downloadBackupJson(payload,name){const blob=new Blob([JSON.stringify(payload,null,2)],{type:'application/json'}),a=document.createElement('a');a.href=URL.createObjectURL(blob);a.download=name;a.click();setTimeout(()=>URL.revokeObjectURL(a.href),500)}function scoutBackup(){return ScoutTraceRecovery.createBackup(hist(),settings)}async function restoreHistoryFile(file){if(!file)return;try{if(file.size>ScoutTraceRecovery.MAX_FILE_BYTES)throw new Error('Backup file is larger than 5 MB.');const parsed=ScoutTraceRecovery.parseBackupText(await file.text());downloadBackupJson(scoutBackup(),'scouttrace-pre-import-backup.json');const result=ScoutTraceRecovery.restore(localStorage,parsed);settings={vibration:true,sensitivity:'medium',history:true,...(result.settings||{})};toast(`Restored ${result.history.length} history records`);showHistory()}catch(e){toast(e instanceof Error?e.message:'Backup could not be restored.')}finally{const input=$('restoreHistFile');if(input)input.value=''}}function showHistory(){let h=hist();modal(`<div class="mh"><h2>Scan History</h2><button id="x" class="close">×</button></div>${h.length?h.map(a=>`<div class="hist"><b>${esc(a.type)}</b> <span class="status ${cls(a.level)}">${esc(a.level)}</span><div class="muted" style="font-size:11px">${new Date(a.time).toLocaleString()}<br>${esc(a.detail)}</div></div>`).join(''):'<p class="muted">No scans saved yet.</p>'}<div class="acts"><button id="backupHist" class="btn">Export Backup</button><button id="restoreHist" class="btn">Restore / Merge Backup</button><button id="shareHist" class="btn">Share Summary</button><button id="clearHist" class="btn danger">Clear History</button></div><input id="restoreHistFile" type="file" accept="application/json,.json" hidden><div class="notice" style="margin-top:12px"><b>Signing migration:</b> Export Backup creates a machine-restorable copy of up to 75 saved scans. Restore merges without deleting history already on this device.</div>`);$('x').onclick=close;$('clearHist').onclick=()=>{localStorage.removeItem(HK);showHistory()};$('backupHist').onclick=()=>downloadBackupJson(scoutBackup(),'scouttrace-backup.json');$('restoreHist').onclick=()=>$('restoreHistFile').click();$('restoreHistFile').onchange=e=>restoreHistoryFile(e.target.files&&e.target.files[0]);$('shareHist').onclick=async()=>{let text=`Acelynn's ScoutTrace™ v${V} scan summary\n`+hist().slice(0,12).map(a=>`${new Date(a.time).toLocaleString()} — ${a.type}: ${a.level} — ${a.detail}`).join('\n');if(navigator.share)try{await navigator.share({title:'ScoutTrace Scan Summary',text})}catch{}else navigator.clipboard?.writeText(text).then(()=>toast('Summary copied'))}}
'''

for path in FILES:
    text=path.read_text(encoding='utf-8')
    original=text

    loader='<script src="scouttrace-recovery.js"></script>\n<script>\n(()=>'
    anchor='<script>\n(()=>'
    if loader not in text:
        if anchor not in text:
            raise SystemExit(f'{path}: missing inline script anchor')
        text=text.replace(anchor,loader,1)

    start=text.find('function showHistory(){')
    end=text.find('function showSettings(){',start)
    if start<0 or end<0:
        raise SystemExit(f'{path}: missing history/settings function boundary')
    if 'Restore / Merge Backup' not in text[start:end]:
        text=text[:start]+replacement+text[end:]

    if 'localStorage.clear(' in text:
        raise SystemExit(f'{path}: destructive localStorage.clear() is forbidden')
    if "const V='1.2.0',HK='st-h-v2',SK='st-s-v2'" not in text:
        raise SystemExit(f'{path}: live ScoutTrace v1.2.0 storage contract changed unexpectedly')
    if "h.slice(0,75)" not in text:
        raise SystemExit(f'{path}: 75-record production history retention marker missing')

    path.write_text(text,encoding='utf-8')
    print(f"{path}: {'patched' if text!=original else 'already deterministic'}")

if FILES[0].read_text(encoding='utf-8')!=FILES[1].read_text(encoding='utf-8'):
    raise SystemExit('index.html and raw-index.html must remain identical')
