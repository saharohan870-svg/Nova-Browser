"use strict";
const LISTS = [
 {name:"EasyList",url:"https://easylist.to/easylist/easylist.txt"},
 {name:"EasyPrivacy",url:"https://easylist.to/easylist/easyprivacy.txt"}
];
const UPDATE_MS=86400000;
let compiled=NovaAdblockEngine.compile([]), enabled=true, firstParty=new Map();
let stats={blocked:0,updatedAt:0,networkRules:0,cosmeticRules:0};
async function loadSaved(){
 const s=await browser.storage.local.get(["filters","enabled","stats"]);
 enabled=s.enabled!==false;
 if(s.stats)stats={...stats,...s.stats};
 if(Array.isArray(s.filters)){
  compiled=NovaAdblockEngine.compile(s.filters);
  stats.networkRules=compiled.network;stats.cosmeticRules=compiled.cosmetic;
 }
}
async function updateLists(force){
 const old=await browser.storage.local.get(["filters","updatedAt"]);
 if(!force&&old.updatedAt&&Date.now()-old.updatedAt<UPDATE_MS&&old.filters&&old.filters.length)return;
 const all=[];
 for(const list of LISTS){
  try{
   const response=await fetch(list.url,{cache:"no-cache"});
   if(!response.ok)throw new Error("HTTP "+response.status);
   const body=await response.text();
   if(body.length>8*1024*1024)throw new Error("list exceeds size limit");
   all.push(...body.split(/\r?\n/));
   console.log("[NovaAdBlock] downloaded",list.name);
  }catch(error){console.warn("[NovaAdBlock]",list.name,error.message);}
 }
 if(all.length){
  await browser.storage.local.set({filters:all,updatedAt:Date.now()});
  compiled=NovaAdblockEngine.compile(all);
  stats.updatedAt=Date.now();stats.networkRules=compiled.network;stats.cosmeticRules=compiled.cosmetic;
  await browser.storage.local.set({stats});
  console.log("[NovaAdBlock] active",stats.networkRules,"network rules",stats.cosmeticRules,"cosmetic rules");
 }
}
browser.webRequest.onBeforeRequest.addListener(details=>{
 if(!enabled)return {};
 if(NovaAdblockEngine.decide(compiled,details,firstParty.get(details.tabId)||"")){
  stats.blocked++;return {cancel:true};
 }
 return {};
},{urls:["http://*/*","https://*/*"]},["blocking"]);
browser.webNavigation.onCommitted.addListener(details=>{
 if(details.frameId===0){try{firstParty.set(details.tabId,new URL(details.url).hostname)}catch(_){}}
});
browser.tabs.onRemoved.addListener(id=>firstParty.delete(id));
browser.runtime.onMessage.addListener(async message=>{
 if(message&&message.type==="cosmetics")return {selectors:NovaAdblockEngine.cosmetic(compiled,message.host)};
 if(message&&message.type==="status")return {enabled,stats};
 if(message&&message.type==="toggle"){enabled=!!message.enabled;await browser.storage.local.set({enabled});return {enabled};}
 if(message&&message.type==="update"){await updateLists(true);return {enabled,stats};}
 return undefined;
});
browser.runtime.onInstalled.addListener(async()=>{await loadSaved();await updateLists(false)});
browser.runtime.onStartup.addListener(async()=>{await loadSaved();await updateLists(false)});
(async()=>{await loadSaved();await updateLists(false);browser.alarms.create("nova-update",{periodInMinutes:1440});})();
browser.alarms.onAlarm.addListener(a=>{if(a.name==="nova-update")updateLists(true)});
