"use strict";
const LISTS=[
 {key:"easylist",name:"EasyList",url:"https://easylist.to/easylist/easylist.txt"},
 {key:"easyprivacy",name:"EasyPrivacy",url:"https://easylist.to/easylist/easyprivacy.txt"}
];
const UPDATE_MS=86400000;
let compiled=NovaAdblockEngine.compile([]),mode="standard",siteOverrides={},firstParty=new Map(),nativePort=null;
let stats={blocked:0,updatedAt:0,networkRules:0,cosmeticRules:0};
function state(host){return {type:"shieldState",host:host||"",enabled:mode!=="off",mode,siteMode:siteOverrides[host||""]||"default",stats:{...stats}};}
async function rebuild(){
 const saved=await browser.storage.local.get(["filter_easylist","filter_easyprivacy","mode","enabled","siteOverrides","stats"]);
 mode=["standard","aggressive","off"].includes(saved.mode)?saved.mode:(saved.enabled===false?"off":"standard");
 siteOverrides=saved.siteOverrides||{};
 if(saved.stats)stats={...stats,...saved.stats};
 const lines=[];
 for(const list of LISTS)if(Array.isArray(saved["filter_"+list.key]))lines.push(...saved["filter_"+list.key]);
 compiled=NovaAdblockEngine.compile(lines);
 stats.networkRules=compiled.network;stats.cosmeticRules=compiled.cosmetic;
}
async function updateLists(force){
 const keys=LISTS.flatMap(x=>["filter_"+x.key,"updated_"+x.key]);
 const saved=await browser.storage.local.get(keys);
 for(const list of LISTS){
  const key="filter_"+list.key,stamp=saved["updated_"+list.key]||0;
  if(!force&&saved[key]&&Date.now()-stamp<UPDATE_MS)continue;
  try{
   const response=await fetch(list.url,{cache:"no-cache"});
   if(!response.ok)throw new Error("HTTP "+response.status);
   const body=await response.text();
   if(body.length>8*1024*1024)throw new Error("list exceeds 8 MB safety limit");
   const lines=body.split(/\r?\n/);
   await browser.storage.local.set({[key]:lines,["updated_"+list.key]:Date.now()});
   console.log("[NovaShields] updated",list.name,lines.length,"lines");
  }catch(error){console.warn("[NovaShields] update failed for",list.name,error.message);}
 }
 await rebuild();
 stats.updatedAt=Date.now();
 await browser.storage.local.set({stats});
}
browser.webRequest.onBeforeRequest.addListener(details=>{
 let host=firstParty.get(details.tabId)||"";
 if(!host){try{host=new URL(details.url).hostname;}catch(_){}}
 const override=siteOverrides[host]||"default";
 if(mode==="off"||override==="off")return {};
 const aggressive=mode==="aggressive"||override==="aggressive";
 if(NovaAdblockEngine.decide(compiled,details,host,aggressive)){
  stats.blocked++;
  return {cancel:true};
 }
 return {};
},{urls:["http://*/*","https://*/*"]},["blocking"]);
browser.webNavigation.onCommitted.addListener(details=>{
 if(details.frameId===0){try{firstParty.set(details.tabId,new URL(details.url).hostname)}catch(_){}}
});
browser.tabs.onRemoved.addListener(id=>firstParty.delete(id));
browser.runtime.onMessage.addListener(async message=>{
 if(message&&message.type==="cosmetics"){
  const host=String(message.host||"").toLowerCase();
  if(mode==="off"||siteOverrides[host]==="off")return {selectors:[]};
  return {selectors:NovaAdblockEngine.cosmetic(compiled,host)};
 }
 if(message&&message.type==="status")return state(message.host);
 if(message&&message.type==="toggle"){
  mode=message.enabled?"standard":"off";
  await browser.storage.local.set({enabled:mode!=="off",mode});
  return state(message.host);
 }
 if(message&&message.type==="setMode"){
  mode=["standard","aggressive","off"].includes(message.mode)?message.mode:"standard";
  await browser.storage.local.set({enabled:mode!=="off",mode});
  return state(message.host);
 }
 if(message&&message.type==="setSiteMode"){
  const host=String(message.host||"").toLowerCase();
  if(!host||host.length>253||!/^[a-z0-9.-]+$/.test(host))return {error:"Invalid site"};
  const next={...siteOverrides};
  if(message.mode==="default")delete next[host];
  else if(["off","aggressive"].includes(message.mode))next[host]=message.mode;
  else return {error:"Invalid mode"};
  siteOverrides=next;await browser.storage.local.set({siteOverrides});
  return state(host);
 }
 if(message&&message.type==="update"){await updateLists(true);return state(message.host);}
 return undefined;
});
browser.runtime.onInstalled.addListener(async()=>{await rebuild();await updateLists(false)});
browser.runtime.onStartup.addListener(async()=>{await rebuild();await updateLists(false)});
(async()=>{await rebuild();await updateLists(false);browser.alarms.create("nova-filter-update",{periodInMinutes:1440})})();
browser.alarms.onAlarm.addListener(alarm=>{if(alarm.name==="nova-filter-update")updateLists(true)});
try{
 nativePort=browser.runtime.connectNative("browser");
 nativePort.onMessage.addListener(async message=>{
  if(!message)return;
  const host=String(message.host||"").toLowerCase();
  if(message.type==="getShield")nativePort.postMessage(state(host));
  else if(message.type==="setMode"){
   mode=["standard","aggressive","off"].includes(message.mode)?message.mode:"standard";
   await browser.storage.local.set({enabled:mode!=="off",mode});
   nativePort.postMessage(state(host));
  }else if(message.type==="setSiteMode"){
   if(host&&host.length<=253&&/^[a-z0-9.-]+$/.test(host)){
    const next={...siteOverrides};
    if(message.mode==="default")delete next[host];
    else if(["off","aggressive"].includes(message.mode))next[host]=message.mode;
    siteOverrides=next;await browser.storage.local.set({siteOverrides});
    nativePort.postMessage(state(host));
   }
  }else if(message.type==="update"){
   await updateLists(true);nativePort.postMessage(state(host));
  }
 });
}catch(error){console.warn("[NovaShields] native settings bridge unavailable",error);}
