const fs = require("node:fs");
const vm = require("node:vm");
const test = require("node:test");
const assert = require("node:assert/strict");
vm.runInThisContext(fs.readFileSync("app/src/main/assets/nova_adblock/filter-engine.js", "utf8"));
const Engine = globalThis.NovaAdblockEngine;
const req = (url, type="script") => ({url,type});
test("blocks a third-party script by host-anchored rule",()=>{
 const c=Engine.compile(["||ads.example^$script,third-party"]);
 assert.equal(Engine.decide(c,req("https://ads.example/track.js"),"publisher.com"),true);
});
test("does not block first-party request with third-party option",()=>{
 const c=Engine.compile(["||cdn.publisher.com^$script,third-party"]);
 assert.equal(Engine.decide(c,req("https://cdn.publisher.com/app.js"),"publisher.com"),false);
});
test("respects resource type options",()=>{
 const c=Engine.compile(["||ads.example^$image"]);
 assert.equal(Engine.decide(c,req("https://ads.example/banner.jpg","image"),"publisher.com"),true);
 assert.equal(Engine.decide(c,req("https://ads.example/script.js","script"),"publisher.com"),false);
});
test("exception rules override ordinary block rules",()=>{
 const c=Engine.compile(["||ads.example^$script","@@||ads.example/allowed^$script"]);
 assert.equal(Engine.decide(c,req("https://ads.example/allowed/abc"),"publisher.com"),false);
 assert.equal(Engine.decide(c,req("https://ads.example/other.js"),"publisher.com"),true);
});
test("important block rules override exception rules",()=>{
 const c=Engine.compile(["||ads.example/important^$script,important","@@||ads.example/important^$script"]);
 assert.equal(Engine.decide(c,req("https://ads.example/important/abc"),"publisher.com"),true);
});
test("domain options limit a rule to matching first-party domains",()=>{
 const c=Engine.compile(["||ads.example^$script,domain=publisher.com"]);
 assert.equal(Engine.decide(c,req("https://ads.example/a.js"),"news.publisher.com"),true);
 assert.equal(Engine.decide(c,req("https://ads.example/a.js"),"other.com"),false);
});
test("global and site-specific cosmetic selectors and exceptions work",()=>{
 const c=Engine.compile(["##.generic-ad","publisher.com##.sponsor","publisher.com#@#.sponsor"]);
 assert.deepEqual(Engine.cosmetic(c,"publisher.com").sort(),[".generic-ad"]);
 assert.deepEqual(Engine.cosmetic(c,"other.com"),[".generic-ad"]);
});
test("unsupported procedural or redirect options are ignored safely",()=>{
 assert.equal(Engine.parse("||ads.example^$script,redirect=noop.js"),null);
 assert.equal(Engine.parse("||ads.example^$script")!==null,true);
});

test("aggressive mode also evaluates first-party filter rules",()=>{
 const c=Engine.compile(["||cdn.publisher.com^$script,third-party"]);
 assert.equal(Engine.decide(c,req("https://cdn.publisher.com/app.js"),"publisher.com"),false);
 assert.equal(Engine.decide(c,req("https://cdn.publisher.com/app.js"),"publisher.com",true),true);
});

test("standard mode treats unqualified network rules as third-party by default",()=>{
 const c=Engine.compile(["||cdn.publisher.com^"]);
 assert.equal(Engine.decide(c,req("https://cdn.publisher.com/app.js"),"publisher.com"),false);
 assert.equal(Engine.decide(c,req("https://cdn.publisher.com/app.js"),"publisher.com",true),true);
});
