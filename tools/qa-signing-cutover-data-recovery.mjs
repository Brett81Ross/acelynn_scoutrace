import fs from 'node:fs';
import vm from 'node:vm';

const source=fs.readFileSync('scouttrace-recovery.js','utf8');
const sandbox={module:{exports:{}},exports:{},globalThis:{},TextEncoder,Date,JSON,Math,Number,String,Array,Set,Error};sandbox.globalThis=sandbox;vm.createContext(sandbox);vm.runInContext(source,sandbox,{filename:'scouttrace-recovery.js'});const R=sandbox.module.exports;
function assert(ok,msg){if(!ok)throw new Error(msg)}
function throws(fn,needle,label){let ok=false;try{fn()}catch(e){ok=String(e.message||e).includes(needle)}assert(ok,label)}

assert(R.APP==="Acelynn's ScoutTrace",'app id');assert(R.SCHEMA==='scouttrace-backup-v1','schema');assert(R.VERSION===1,'version');assert(R.HISTORY_KEY==='st-h-v2','history key');assert(R.SETTINGS_KEY==='st-s-v2','settings key');assert(R.MAX_HISTORY===75,'history cap');
const a={type:'Magnetic Sweep',level:'CLEAR',detail:'Baseline 48.1 µT',time:'2026-09-01T12:00:00.000Z'};
const b={type:'QR Threat Check',level:'REVIEW',detail:'Shortened link',time:'2026-09-01T12:05:00.000Z'};
const backup=R.createBackup([a,b],{vibration:false,sensitivity:'high',history:true});
const parsed=R.parseBackupText(JSON.stringify(backup));assert(parsed.history.length===2&&parsed.settings.sensitivity==='high','versioned roundtrip');
throws(()=>R.parseBackupObject({app:'Other',schema:R.SCHEMA,version:1,history:[]}), 'different app','wrong app rejected');
throws(()=>R.parseBackupObject({app:R.APP,schema:'bad',version:1,history:[]}), 'schema','wrong schema rejected');
throws(()=>R.parseBackupObject({app:R.APP,schema:R.SCHEMA,version:2,history:[]}), 'version','future version rejected');
throws(()=>R.parseBackupObject({app:R.APP,schema:R.SCHEMA,version:1,history:Array.from({length:1001},()=>a)}), 'too many','too many records rejected');
const polluted=JSON.parse('{"app":"Acelynn\\u0027s ScoutTrace","schema":"scouttrace-backup-v1","version":1,"history":[{"type":"x","level":"CLEAR","detail":"y","time":"z","__proto__":{"polluted":true},"constructor":{"x":1}}]}');
const clean=R.parseBackupObject(polluted).history[0];assert(!Object.prototype.hasOwnProperty.call(clean,'__proto__')&&!Object.prototype.hasOwnProperty.call(clean,'constructor'),'prototype keys stripped');
const merged=R.mergeHistory([a],[a,b]);assert(merged.length===2&&merged[0].time===a.time&&merged[1].time===b.time,'current-first dedupe merge');
assert(R.mergeHistory([],Array.from({length:100},(_,i)=>({...a,time:`t${i}`}))).length===75,'75 record cap');
assert(R.normalizeSettings({vibration:true,sensitivity:'bogus',history:false,secret:'nope'}).sensitivity===undefined,'settings allowlist');

class FakeStorage{constructor(values={},failAt=0){this.map=new Map(Object.entries(values));this.calls=0;this.failAt=failAt}getItem(k){return this.map.has(k)?this.map.get(k):null}setItem(k,v){this.calls++;if(this.failAt===this.calls)throw new Error('simulated write failure');this.map.set(k,v)}removeItem(k){this.map.delete(k)}}
let storage=new FakeStorage({[R.HISTORY_KEY]:JSON.stringify([a])});let result=R.restore(storage,parsed);assert(result.history.length===2,'restore merges history');assert(JSON.parse(storage.getItem(R.SETTINGS_KEY)).sensitivity==='high','backup settings fill clean install');
storage=new FakeStorage({[R.HISTORY_KEY]:JSON.stringify([a]),[R.SETTINGS_KEY]:JSON.stringify({vibration:true,sensitivity:'low',history:false})});result=R.restore(storage,parsed);assert(result.settings.sensitivity==='low','current device settings win');
const oldHistory=JSON.stringify([a]);const oldSettings=JSON.stringify({vibration:true,sensitivity:'medium',history:true});storage=new FakeStorage({[R.HISTORY_KEY]:oldHistory,[R.SETTINGS_KEY]:oldSettings},1);throws(()=>R.restore(storage,parsed),'simulated write failure','history write failure surfaces');assert(storage.getItem(R.HISTORY_KEY)===oldHistory&&storage.getItem(R.SETTINGS_KEY)===oldSettings,'rollback after first write failure');

const index=fs.readFileSync('index.html','utf8'),raw=fs.readFileSync('raw-index.html','utf8');assert(index===raw,'index/raw parity');
for(const html of [index,raw]){assert(html.includes('<script src="scouttrace-recovery.js"></script>'),'engine loader');assert(html.includes('Export Backup'),'export backup UI');assert(html.includes('Restore / Merge Backup'),'restore UI');assert(html.includes('scouttrace-pre-import-backup.json'),'pre-import backup');assert(html.indexOf("downloadBackupJson(scoutBackup(),'scouttrace-pre-import-backup.json')")<html.indexOf('ScoutTraceRecovery.restore(localStorage,parsed)'),'backup precedes restore');assert(html.includes("const V='1.2.0',HK='st-h-v2',SK='st-s-v2'"),'raw version/storage contract retained');assert(html.includes("h.slice(0,75)"),'production history cap retained');assert(html.includes("navigator.serviceWorker.register('sw.js')"),'existing SW parity retained for separate cleanup');assert(!html.includes('localStorage.clear('),'no destructive clear')}
const shell=fs.readFileSync('api/demo-shell.js','utf8');assert(shell.includes("replace(/v1\\.2\\.0/g,'v1.2.1')"),'current source keeps v1.2.1 shell bridge');
const vercel=fs.readFileSync('vercel.json','utf8');assert(/"deploymentEnabled"\s*:\s*false/.test(vercel),'Git deployment remains disabled');
console.log('ScoutTrace signing-cutover history recovery QA passed.');
