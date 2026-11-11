# Zorvi Engine Baseline and Benchmark Log

This file records measurements. Do not invent or estimate device results. Record device model, Android version, build SHA, test URL/fixture, network conditions and repeated-run statistics.

## Reference device profile

- Device class: Android ARM64, 4 GB RAM target.
- Primary device measurement: pending a real-device run.
- OS/build: pending.
- Engine build SHA: record per run.

## Measurements

| Metric | Method | Result |
|---|---|---|
| APK download size | CI artifact bytes | Pending successful engine build |
| Installed size | Android package manager / device storage | Pending real-device install |
| Cold start | 10 launches, median and p95 | Pending |
| Warm start | 10 launches, median and p95 | Pending |
| Peak process RSS | Android process memory during fixture suite | Pending |
| Foreground page memory | Fixed fixture after idle | Pending |
| Parser throughput | Fixed local HTML corpus, bytes/s | Pending |
| Time to first content | Local deterministic fixture | Pending |
| Scroll frame time | 10-second scripted scroll, median/p95 | Pending |
| CPU/battery | Fixed 10-minute workload | Pending |

## Benchmark rules

- Compare Zorvi, Chrome and Firefox on the same device and network.
- Use identical pages, viewport, cache state and test sequence.
- Separate cold-cache and warm-cache results.
- Run each test at least five times; report median and p95 where meaningful.
- Never compare a debug build to a release build without stating it.
- Do not optimize against one synthetic page at the expense of correctness or security.
