const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");
const source = fs.readFileSync("app/src/main/assets/nova_adblock/filter-engine.js", "utf8");
const context = { URL, console };
context.globalThis = context;
vm.runInNewContext(source, context, { filename: "filter-engine.js" });
const engine = context.NovaAdblockEngine;
assert.ok(engine, "engine exports API");
function compile(lines) { return engine.compile(lines.split("\n")); }
function blocked(c, url, type = "script", party = "news.example") {
  return engine.decide(c, { url, type }, party);
}
{
  const c = compile("||ads.example^\n||tracker.example^");
  assert.equal(blocked(c, "https://ads.example/ad.js"), true);
  assert.equal(blocked(c, "https://sub.ads.example/banner.png", "image"), true);
  assert.equal(blocked(c, "https://content.example/article.js"), false);
}
{
  const c = compile("||ads.example^\n@@||ads.example/allow.js$script");
  assert.equal(blocked(c, "https://ads.example/allow.js"), false, "exception overrides block");
  assert.equal(blocked(c, "https://ads.example/other.js"), true);
}
{
  const c = compile("||cdn.example.com^$third-party");
  assert.equal(blocked(c, "https://cdn.example.com/a.js", "script", "www.example.com"), true, "unknown sibling subdomains are conservatively treated as third-party");
  assert.equal(blocked(c, "https://cdn.example.com/a.js", "script", "news.other"), true);
}
{
  const c = compile("||ads.example^$image");
  assert.equal(blocked(c, "https://ads.example/a.png", "image"), true);
  assert.equal(blocked(c, "https://ads.example/a.js", "script"), false);
}
{
  const c = compile("||ads.example^$domain=news.example");
  assert.equal(blocked(c, "https://ads.example/a.js", "script", "news.example"), true);
  assert.equal(blocked(c, "https://ads.example/a.js", "script", "shop.example"), false);
}
{
  const c = compile("news.example##.sponsored\nnews.example###ad-slot\nnews.example#@#.sponsored");
  const selectors = engine.cosmetic(c, "news.example");
  assert.ok(selectors.includes("#ad-slot"));
  assert.ok(!selectors.includes(".sponsored"), "cosmetic exception removes selector");
}
{
  const c = compile("||important.example^$important\n@@||important.example^");
  assert.equal(blocked(c, "https://important.example/ad.js"), true, "important block survives exception");
  const reversed = compile("@@||important.example^\n||important.example^$important");
  assert.equal(blocked(reversed, "https://important.example/ad.js"), true, "important block wins even when exception appears first");
}
{
  const c = compile("||cdn.example^$third-party");
  assert.equal(blocked(c, "https://cdn.example/a.js", "script", "cdn.example"), false, "same host remains first-party");
  assert.equal(blocked(c, "https://cdn.example/a.js", "script", "shop.example"), true, "different host is third-party");
}
console.log("Nova AdBlock engine tests passed: 8 groups");
