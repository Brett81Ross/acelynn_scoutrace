(function(root,factory){
  const api=factory();
  if(typeof module!=="undefined"&&module.exports)module.exports=api;
  if(root)root.ScoutTraceRecovery=api;
})(typeof globalThis!=="undefined"?globalThis:this,function(){
  "use strict";

  const APP="Acelynn's ScoutTrace";
  const SCHEMA="scouttrace-backup-v1";
  const VERSION=1;
  const HISTORY_KEY="st-h-v2";
  const SETTINGS_KEY="st-s-v2";
  const MAX_FILE_BYTES=5*1024*1024;
  const MAX_INPUT_HISTORY=1000;
  const MAX_HISTORY=75;
  const VALID_SENSITIVITY=new Set(["high","medium","low"]);

  function text(value,max){return typeof value==="string"?value.slice(0,max):""}
  function normalizeHistoryItem(value){
    if(!value||typeof value!=="object"||Array.isArray(value))return null;
    const type=text(value.type,120);
    const level=text(value.level,40);
    const detail=text(value.detail,4000);
    const time=text(value.time,80);
    if(!type&&!level&&!detail&&!time)return null;
    return{type,level,detail,time};
  }
  function normalizeHistory(values){
    if(!Array.isArray(values))throw new Error("Backup history must be an array.");
    if(values.length>MAX_INPUT_HISTORY)throw new Error("Backup contains too many history records.");
    return values.map(normalizeHistoryItem).filter(Boolean);
  }
  function normalizeSettings(value){
    if(!value||typeof value!=="object"||Array.isArray(value))return null;
    const out={};
    if(typeof value.vibration==="boolean")out.vibration=value.vibration;
    if(typeof value.history==="boolean")out.history=value.history;
    if(VALID_SENSITIVITY.has(value.sensitivity))out.sensitivity=value.sensitivity;
    return Object.keys(out).length?out:null;
  }
  function historyKey(item){return JSON.stringify([item.type,item.level,item.detail,item.time])}
  function mergeHistory(currentValues,backupValues){
    const current=normalizeHistory(Array.isArray(currentValues)?currentValues:[]);
    const backup=normalizeHistory(Array.isArray(backupValues)?backupValues:[]);
    const seen=new Set(),merged=[];
    for(const item of current){const key=historyKey(item);if(!seen.has(key)){seen.add(key);merged.push(item)}}
    for(const item of backup){if(merged.length>=MAX_HISTORY)break;const key=historyKey(item);if(!seen.has(key)){seen.add(key);merged.push(item)}}
    return merged.slice(0,MAX_HISTORY);
  }
  function createBackup(history,settings){
    return{
      app:APP,
      schema:SCHEMA,
      version:VERSION,
      created:new Date().toISOString(),
      history:normalizeHistory(Array.isArray(history)?history:[]).slice(0,MAX_HISTORY),
      settings:normalizeSettings(settings)
    };
  }
  function parseBackupObject(payload){
    if(!payload||typeof payload!=="object"||Array.isArray(payload))throw new Error("Backup must be a JSON object.");
    if(payload.app!==APP)throw new Error("This backup belongs to a different app.");
    if(payload.schema!==SCHEMA)throw new Error("Unsupported ScoutTrace backup schema.");
    if(Number(payload.version)!==VERSION)throw new Error("Unsupported ScoutTrace backup version.");
    return{history:normalizeHistory(payload.history||[]),settings:normalizeSettings(payload.settings)};
  }
  function parseBackupText(raw){
    if(typeof raw!=="string")throw new Error("Backup must be text.");
    if(new TextEncoder().encode(raw).length>MAX_FILE_BYTES)throw new Error("Backup file is larger than 5 MB.");
    let payload;
    try{payload=JSON.parse(raw)}catch{throw new Error("Backup is not valid JSON.")}
    return parseBackupObject(payload);
  }
  function readJson(storage,key,fallback){
    const raw=storage.getItem(key);
    if(raw===null)return{raw:null,value:fallback};
    try{return{raw,value:JSON.parse(raw)}}catch{return{raw,value:fallback}}
  }
  function restore(storage,backup){
    if(!storage||typeof storage.getItem!=="function"||typeof storage.setItem!=="function")throw new Error("Storage is unavailable.");
    const parsed=backup&&Array.isArray(backup.history)?{history:normalizeHistory(backup.history),settings:normalizeSettings(backup.settings)}:parseBackupObject(backup);
    const previousHistory=readJson(storage,HISTORY_KEY,[]);
    const previousSettings=readJson(storage,SETTINGS_KEY,null);
    const mergedHistory=mergeHistory(previousHistory.value,parsed.history);
    const nextSettings=previousSettings.raw===null&&parsed.settings?parsed.settings:normalizeSettings(previousSettings.value);
    try{
      storage.setItem(HISTORY_KEY,JSON.stringify(mergedHistory));
      if(previousSettings.raw===null&&nextSettings)storage.setItem(SETTINGS_KEY,JSON.stringify(nextSettings));
    }catch(error){
      try{
        if(previousHistory.raw===null&&typeof storage.removeItem==="function")storage.removeItem(HISTORY_KEY);else storage.setItem(HISTORY_KEY,previousHistory.raw);
        if(previousSettings.raw===null&&typeof storage.removeItem==="function")storage.removeItem(SETTINGS_KEY);else storage.setItem(SETTINGS_KEY,previousSettings.raw);
      }catch{}
      throw error;
    }
    return{history:mergedHistory,settings:nextSettings};
  }

  return{APP,SCHEMA,VERSION,HISTORY_KEY,SETTINGS_KEY,MAX_FILE_BYTES,MAX_INPUT_HISTORY,MAX_HISTORY,normalizeHistoryItem,normalizeHistory,normalizeSettings,mergeHistory,createBackup,parseBackupObject,parseBackupText,restore};
});
