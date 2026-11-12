#!/usr/bin/env python3
"""Verify immutable upstream refs for the Zorvi Brave/Chromium baseline.

This uses only git ls-remote; it does not clone or download Chromium sources.
"""
from __future__ import annotations

import subprocess
import sys

PINS = (
    (
        "Brave browser",
        "https://github.com/brave/brave-browser.git",
        "v1.99.56",
        "f14b4ba5b0256728ce74ca32bb0ccb43e3e6cff8",
    ),
    (
        "Brave core",
        "https://github.com/brave/brave-core.git",
        "v1.99.56",
        "4d1dfc472152bcc8f3217be190c8a7ad3007039f",
    ),
    (
        "Chromium",
        "https://github.com/chromium/chromium.git",
        "157.0.8103.50",
        "049750111b1e5c11565c792b2aeed5541f332bad",
    ),
)


def resolve_tag(repository: str, tag: str) -> str:
    result = subprocess.run(
        [
            "git", "ls-remote", repository,
            f"refs/tags/{tag}", f"refs/tags/{tag}^{{}}",
        ],
        check=True,
        capture_output=True,
        text=True,
        timeout=60,
    )
    refs = {}
    for line in result.stdout.splitlines():
        fields = line.split()
        if len(fields) == 2:
            refs[fields[1]] = fields[0]
    # Annotated tags have a peeled ^{} ref. Lightweight tags resolve directly.
    return refs.get(f"refs/tags/{tag}^{{}}", refs.get(f"refs/tags/{tag}", ""))


def main() -> int:
    failed = False
    for name, repository, tag, expected in PINS:
        try:
            actual = resolve_tag(repository, tag)
        except (subprocess.SubprocessError, OSError) as error:
            print(f"ERROR {name}: unable to query upstream: {error}")
            failed = True
            continue
        if actual == expected:
            print(f"OK    {name} {tag} -> {actual}")
        else:
            print(
                f"FAIL  {name} {tag}: expected {expected}, "
                f"received {actual or 'no tag ref'}"
            )
            failed = True
    if failed:
        print("STOP: review upstream refs and update pins deliberately.")
        return 1
    print("All immutable upstream refs match. No source checkout was performed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
