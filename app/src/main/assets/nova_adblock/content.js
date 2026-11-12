"use strict";
(async function(){
 try {
  const response=await browser.runtime.sendMessage({type:"cosmetics",host:location.hostname});
  const selectors=(response&&response.selectors||[]).filter(x=>typeof x==="string"&&x.length<600);
  if(!selectors.length)return;
  const style=document.createElement("style");
  style.id="nova-adblock-css";
  style.textContent=selectors.map(x=>x+"{display:none!important}").join("\n");
  (document.head||document.documentElement).appendChild(style);
 } catch(_) {}
})();
