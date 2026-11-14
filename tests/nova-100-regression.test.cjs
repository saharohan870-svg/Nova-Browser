#!/usr/bin/env node
"use strict";

// 100 source-contract regression checks for Nova's Android integration.
// These checks verify that critical safeguards and feature paths remain wired.
// They are not a substitute for emulator, network, or real-device interaction tests.
const fs = require("node:fs");
const path = require("node:path");
const root = process.cwd();
const read = (p) => fs.readFileSync(path.join(root, p), "utf8");
const main = read("app/src/main/java/com/zorvi/browser/MainActivity.kt");
const tor = read("app/src/main/java/com/zorvi/browser/NovaTorController.kt");
const auth = read("app/src/main/java/com/zorvi/browser/NovaSupabaseAuth.kt");
const sync = read("app/src/main/java/com/zorvi/browser/NovaCloudSync.kt");
const shields = read("app/src/main/java/com/zorvi/browser/NovaShieldsBridge.kt");
const crypto = read("app/src/main/java/com/zorvi/browser/NovaVaultCrypto.kt");
const store = read("app/src/main/java/com/zorvi/browser/NovaLoginStore.kt");
const config = read("app/src/main/java/com/zorvi/browser/NovaSupabaseConfig.kt");
const manifest = read("app/src/main/AndroidManifest.xml");
const home = read("app/src/main/assets/home.html");
const filter = read("app/src/main/assets/nova_adblock/filter-engine.js");
const checks = [
  // App lifecycle and browser shell (1-10)
  ["MainActivity extends Android Activity", /class MainActivity\s*:\s*Activity\(\)/.test(main)],
  ["GeckoView is the rendering view", /GeckoView/.test(main)],
  ["GeckoRuntime is initialized", /getOrCreateRuntime|GeckoRuntime\.create/.test(main)],
  ["onCreate initializes the app", /override fun onCreate\(savedInstanceState: Bundle\?\)/.test(main)],
  ["browser UI builder is present", /private fun buildUi\(\)/.test(main)],
  ["saved tabs restore on startup", /restoreTabs\(\)/.test(main)],
  ["home tab opens when restore is empty", /if \(tabs\.isEmpty\(\)\) newTab\(homePage\(\)\)/.test(main)],
  ["activity saves tabs on pause or stop", /override fun onPause\(\)[\s\S]{0,500}saveTabs\(\)/.test(main)],
  ["activity handles destruction", /override fun onDestroy\(\)/.test(main)],
  ["low-memory callback exists", /override fun onTrimMemory/.test(main)],
  // Navigation and address bar (11-20)
  ["address field exists", /private lateinit var address: EditText/.test(main)],
  ["search engine selector exists", /private lateinit var engine: Spinner/.test(main)],
  ["supported search engines are defined", /Google.*Bing.*DuckDuckGo.*Brave Search/.test(main)],
  ["search engine preference is restored", /setSelection\(prefs\.getInt\("engine"/.test(main)],
  ["search query is URL encoded", /URLEncoder\.encode/.test(main)],
  ["Google search route exists", /google\.com\/search\?q=/.test(main)],
  ["Bing search route exists", /bing\.com\/search\?q=/.test(main)],
  ["DuckDuckGo search route exists", /duckduckgo\.com\/\?q=/.test(main)],
  ["Brave Search route exists", /search\.brave\.com\/search\?q=/.test(main)],
  ["navigation method exists", /private fun navigate\(/.test(main)],
  // Tab lifecycle and organization (21-30)
  ["tabs are held in a mutable collection", /private val tabs = mutableListOf<Tab>\(\)/.test(main)],
  ["each tab owns a GeckoSession", /data class Tab\(val session: GeckoSession/.test(main)],
  ["tab creation method exists", /private fun newTab\(/.test(main)],
  ["tab switcher method exists", /private fun showTabs\(/.test(main)],
  ["tab close method exists", /private fun closeTab\(/.test(main)],
  ["tab duplication method exists", /private fun duplicateCurrentTab\(/.test(main)],
  ["close-other-tabs action exists", /private fun closeOtherTabs\(/.test(main)],
  ["tab pinning action exists", /private fun togglePinned\(/.test(main)],
  ["tab search exists", /private fun searchTabs\(/.test(main)],
  ["recently closed tabs can be restored", /private fun showRecentlyClosed\(/.test(main)],
  // Tab groups and persistence (31-38)
  ["tab group creation exists", /private fun createGroup\(/.test(main)],
  ["tab group management exists", /private fun manageGroups\(/.test(main)],
  ["tab group rename exists", /private fun renameGroup\(/.test(main)],
  ["tab group deletion exists", /private fun deleteGroup\(/.test(main)],
  ["tab can be moved to a group", /private fun moveCurrentTabToGroup\(/.test(main)],
  ["active group is persisted", /active_group/.test(main)],
  ["tab state serialization is represented", /serializedState/.test(main)],
  ["recently closed state is persisted", /recently_closed/.test(main)],
  // Private and guest isolation (39-50)
  ["private mode state exists", /privateMode/.test(main)],
  ["guest mode state exists", /guestMode/.test(main)],
  ["private tab state is explicit", /private: Boolean = false/.test(main)],
  ["guest tab state is explicit", /guest: Boolean = false/.test(main)],
  ["private mode toggle exists", /private fun togglePrivate\(/.test(main)],
  ["Guest Mode entry exists", /private fun startGuestMode\(/.test(main)],
  ["Guest Mode exit exists", /private fun endGuestMode\(/.test(main)],
  ["Guest Mode blocks bookmark saving", /Bookmarks are disabled in Guest Mode/.test(main)],
  ["Guest Mode hides personal history", /Personal history and bookmarks are hidden in Guest Mode/.test(main)],
  ["Guest Mode blocks account actions", /Nova account actions are disabled in Guest Mode/.test(main)],
  ["private and guest sessions block downloads", /Downloads are disabled in private and guest sessions/.test(main)],
  ["Guest Mode blocks external app links", /if \(tab\\.guest\) return GeckoResult\\.deny\\(\)/.test(main)],
  // Tor service and routing (51-60)
  ["Tor controller class exists", /class NovaTorController/.test(tor)],
  ["Tor binds the Android service", /bindService\(/.test(tor)],
  ["Tor waits for bootstrap completion", /PROGRESS=100/.test(tor)],
  ["Tor SOCKS port is validated", /port in 1\.\.65535/.test(tor)],
  ["Tor bootstrap has a timeout", /120_000L/.test(tor)],
  ["Tor polling avoids overlapping work", /pollInFlight/.test(tor)],
  ["Tor stop clears callbacks", /callback = null/.test(tor)],
  ["Tor service disconnect is handled", /onServiceDisconnected/.test(tor)],
  ["Tor runtime has proxy configuration", /torProxyEnabled|proxy.*socks|socks.*proxy/i.test(main)],
  ["Tor DNS and WebRTC hardening is configured", /network\.proxy\.socks_remote_dns|media\.peerconnection\.enabled|http3/i.test(main)],
  // File picking and downloads (61-70)
  ["website file prompt is handled", /override fun onFilePrompt/.test(main)],
  ["website file picker uses ACTION_OPEN_DOCUMENT", /ACTION_OPEN_DOCUMENT/.test(main)],
  ["file picker result is delivered to Gecko", /pendingFileResult/.test(main)],
  ["search attachment button is visible", /button\("＋"\) \{ pickSearchAttachment\(\) \}/.test(main)],
  ["search attachment picker method exists", /private fun pickSearchAttachment\(\)/.test(main)],
  ["search attachment uses openable documents", /CATEGORY_OPENABLE/.test(main)],
  ["attachment sharing uses ACTION_SEND", /ACTION_SEND/.test(main)],
  ["attachment shares URI stream", /EXTRA_STREAM/.test(main)],
  ["attachment grants read permission", /FLAG_GRANT_READ_URI_PERMISSION/.test(main)],
  ["download queue method exists", /private fun queueDownload\(/.test(main)],
  // Bookmarks, history, permissions, recovery (71-80)
  ["bookmark save method exists", /private fun bookmark\(\)/.test(main)],
  ["history and bookmark list method exists", /private fun showSavedList\(history: Boolean\)/.test(main)],
  ["history write method exists", /private fun saveHistory\(/.test(main)],
  ["site permission dashboard exists", /private fun showPermissionDashboard\(/.test(main)],
  ["website permission request delegate exists", /onContentPermissionRequest/.test(main)],
  ["Android permission result is handled", /override fun onRequestPermissionsResult/.test(main)],
  ["page crash callback exists", /override fun onCrash/.test(main)],
  ["crash recovery dialog is present", /Page crashed/.test(main)],
  ["back navigation callback exists", /override fun onBackPressed/.test(main)],
  ["downloads screen exists", /private fun showDownloads\(/.test(main)],
  // Authentication and cloud sync (81-90)
  ["Supabase auth client exists", /class NovaSupabaseAuth/.test(auth)],
  ["auth requires HTTPS project URL", /PROJECT_URL\.startsWith\("https:\/\/"\)/.test(auth)],
  ["password sign-in endpoint exists", /grant_type=password/.test(auth)],
  ["email OTP request endpoint exists", /\/auth\/v1\/otp/.test(auth)],
  ["OTP verification endpoint exists", /\/auth\/v1\/verify/.test(auth)],
  ["password update enforces minimum length", /newPassword\.length < 8/.test(auth)],
  ["session restoration exists", /fun restoreSession/.test(auth)],
  ["sign-out clears stored session", /fun signOut[\s\S]{0,700}sessionStore\.clear\(\)/.test(auth)],
  ["cloud sync prevents concurrent syncs", /AtomicBoolean|compareAndSet\(false, true\)/.test(sync)],
  ["cloud sync uses user access token", /withFreshAccessToken/.test(sync)],
  // Security, shields, vault and configuration (91-100)
  ["vault uses AES-GCM", /AES\/GCM\/NoPadding/.test(crypto)],
  ["vault uses PBKDF2 HMAC SHA-256", /PBKDF2WithHmacSHA256/.test(crypto)],
  ["vault enforces passphrase length", /passphrase\.size >= 12/.test(crypto)],
  ["vault validates salt and nonce sizes", /salt\.size == SALT_BYTES && nonce\.size == NONCE_BYTES/.test(crypto)],
  ["login store uses Android Keystore", /AndroidKeyStore/.test(store)],
  ["Shields extension is installed", /ensureBuiltIn/.test(shields)],
  ["Shields bridge handles disconnect", /onDisconnect/.test(shields)],
  ["adblock parser rejects unsupported rules safely", /opt\.unsupported/.test(filter)],
  ["manifest declares internet permission", /android\.permission\.INTERNET/.test(manifest)],
  ["home page declares English language", /<html[^>]*lang="en"/i.test(home)],
];
let passed = 0;
for (let i = 0; i < checks.length; i++) {
  const [name, ok] = checks[i];
  if (!ok) {
    console.error("FAIL " + String(i + 1).padStart(3, "0") + " " + name);
    process.exitCode = 1;
  } else {
    passed++;
    console.log("PASS " + String(i + 1).padStart(3, "0") + " " + name);
  }
}
console.log("\nNova 100-case source-contract suite: " + passed + "/" + checks.length + " passed.");
if (checks.length !== 100) {
  console.error("Suite definition error: expected exactly 100 checks.");
  process.exitCode = 1;
}
