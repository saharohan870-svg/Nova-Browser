const nativePort = browser.runtime.connectNative("browser");
nativePort.onMessage.addListener(async message => {
  if (!message || typeof message.type !== "string") return;
  if (message.type === "exportCookies") {
    try {
      const cookies = (await browser.cookies.getAll({})).filter(cookie => cookie.secure && !cookie.partitionKey);
      // The native app encrypts this payload before it leaves the device.
      nativePort.postMessage({ type: "cookiesExported", cookies });
    } catch (error) {
      nativePort.postMessage({ type: "cookieSyncError", message: String(error) });
    }
  } else if (message.type === "importCookies" && Array.isArray(message.cookies)) {
    let imported = 0;
    let skipped = 0;
    for (const cookie of message.cookies) {
      try {
        if (!cookie || !cookie.name || typeof cookie.value !== "string") { skipped++; continue; }
        const host = String(cookie.domain || "").replace(/^\\./, "");
        if (!host || !/^[a-z0-9.-]+$/i.test(host) || host.includes("..")) { skipped++; continue; }
        const url = (cookie.secure ? "https://" : "http://") + host + (cookie.path || "/");
        const details = {
          url, name: cookie.name, value: cookie.value,
          path: cookie.path || "/", secure: Boolean(cookie.secure),
          httpOnly: Boolean(cookie.httpOnly)
        };
        if (cookie.domain && cookie.domain.startsWith(".")) details.domain = cookie.domain;
        if (cookie.sameSite) details.sameSite = cookie.sameSite;
        if (Number.isFinite(cookie.expirationDate) && cookie.expirationDate > Date.now()/1000) {
          details.expirationDate = cookie.expirationDate;
        }
        await browser.cookies.set(details);
        imported++;
      } catch (_) { skipped++; }
    }
    nativePort.postMessage({ type: "cookiesImported", imported, skipped });
  }
});
nativePort.postMessage({ type: "novaSessionSyncReady" });
