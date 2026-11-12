"use strict";
const LISTS=[
 {key:"easylist",name:"EasyList",url:"https://easylist.to/easylist/easylist.txt"},
 {key:"easyprivacy",name:"EasyPrivacy",url:"https://easylist.to/easylist/easyprivacy.txt"}
];
const UPDATE_MS=86400000;
let compiled=NovaAdblockEngine.compile([]),enabled=true,firstParty=new Map();
let stats={blocked:0,updatedAt:0,networkRules:0,cosmeticRules:0};
async function rebuild(){
 const saved=await browser.storage.local.get(["filter_easylist","filter_easyprivacy","enabled","stats"]);
 enabled=saved.enabled!==false;
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
   console.log("[NovaAdBlock] updated",list.name,lines.length,"lines");
  }catch(error){console.warn("[NovaAdBlock] update failed for",list.name,error.message);}
 }
 await rebuild();
 stats.updatedAt=Date.now();
 await browser.storage.local.set({stats});
 console.log("[NovaAdBlock] active",stats.networkRules,"network filters and",stats.cosmeticRules,"cosmetic filters");
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
browser.runtime.onInstalled.addListener(async()=>{await rebuild();await updateLists(false)});
browser.runtime.onStartup.addListener(async()=>{await rebuild();await updateLists(false)});
(async()=>{await rebuild();await updateLists(false);browser.alarms.create("nova-filter-update",{periodInMinutes:1440})})();
browser.alarms.onAlarm.addListener(alarm=>{if(alarm.name==="nova-filter-update")updateLists(true)});
