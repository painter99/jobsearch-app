#!/usr/bin/env python3
"""Emit CI failure details as GitHub check-run annotations.

Annotations are publicly readable via the API even when logs are not,
which keeps the mobile-only workflow debuggable (M1.5, 2026-09-21).
"""
import glob
import os
import sys
from xml.etree import ElementTree as ET

MAX = 8  # GitHub caps error annotations per step; keep under the limit


def emit(msg):
    msg = msg.replace("%", "%25").replace("\r", " ").replace("\n", " ")[:1000]
    print(f"::error::{msg}")


def main():
    build_output, results_dir = sys.argv[1], sys.argv[2]
    seen = 0

    # Kotlin compiler errors ("e: file://...") and failed Gradle tasks/tests
    if os.path.exists(build_output):
        for line in open(build_output, errors="replace"):
            line = line.rstrip()
            if line.startswith("e: ") or ": error: " in line or line.startswith(
                "FAILURE: ") or line.startswith("* What went wrong:") or line.startswith(
                "Caused by:") or line.startswith("A problem was") or (
                line.startswith("> Task ") and "FAILED" in line) or (
                line.endswith(" FAILED") and " > " in line
            ):
                emit(line)
                seen += 1
                if seen >= MAX:
                    return

    # JUnit XML results: one annotation per failed test
    if seen == 0 and os.path.isdir(results_dir):
        for path in sorted(glob.glob(os.path.join(results_dir, "*.xml"))):
            try:
                root = ET.parse(path).getroot()
            except ET.ParseError:
                continue
            for tc in root.iter("testcase"):
                for f in tc.findall("failure") + tc.findall("error"):
                    text = (f.get("message") or "") + " || " + (f.text or "")[:600]
                    emit(f"{tc.get('classname')}#{tc.get('name')}: {text}")
                    seen += 1
                    if seen >= MAX:
                        return


if __name__ == "__main__":
    main()
