#!/usr/bin/env python3
"""Extract version sections from CHANGELOG_zh.md and CHANGELOG.md for GitHub Releases."""

from __future__ import annotations

import argparse
import os
import re
import sys

SECTION_HEADER_RE = re.compile(r"^##\s+\[([^\]]+)\](?:\s*-\s*.*)?\s*$")


def normalize_candidates(tag: str) -> list[str]:
    clean = tag.strip()
    candidates = [clean]
    if clean.startswith(("v", "V")):
        candidates.append(clean[1:])
    else:
        candidates.append(f"v{clean}")
    if clean.startswith("core-v"):
        candidates.append(clean[len("core-v") :])
    return list(dict.fromkeys(candidates))


def extract_section(path: str, tag: str) -> str:
    if not path or not os.path.isfile(path):
        return ""
    with open(path, "r", encoding="utf-8") as f:
        lines = f.read().splitlines()

    candidates = {c.lower() for c in normalize_candidates(tag)}
    capturing = False
    captured: list[str] = []

    for line in lines:
        m = SECTION_HEADER_RE.match(line)
        if m:
            if capturing:
                break
            heading_tag = m.group(1).strip().lower()
            if heading_tag in candidates:
                capturing = True
                continue
        elif capturing:
            captured.append(line)

    # Trim leading/trailing blank lines and trailing horizontal rules.
    while captured and (not captured[0].strip() or captured[0].strip() == "---"):
        captured.pop(0)
    while captured and (not captured[-1].strip() or captured[-1].strip() == "---"):
        captured.pop()

    return "\n".join(captured).strip()


def format_bilingual(tag: str, zh_body: str, en_body: str) -> str:
    title = (
        f"## RabbitControl Core {tag}"
        if tag.startswith("core-v")
        else f"## RabbitControl Android {tag}"
    )
    parts = [title]
    if zh_body:
        parts.extend(["", "### 中文更新说明", "", zh_body])
    if en_body:
        if zh_body:
            parts.extend(["", "---"])
        parts.extend(["", "### English Release Notes", "", en_body])
    if len(parts) == 1:
        return ""
    return "\n".join(parts).strip() + "\n"


def write_text(path: str, text: str) -> None:
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(text)


def main() -> int:
    parser = argparse.ArgumentParser(description="Extract bilingual changelog section for a release tag.")
    parser.add_argument("--tag", required=True, help="Release tag, e.g. v0.2.1 or core-v0.1.24")
    parser.add_argument("--zh", default="CHANGELOG_zh.md", help="Path to Chinese changelog")
    parser.add_argument("--en", default="CHANGELOG.md", help="Path to English changelog")
    parser.add_argument("--out", default="", help="Output path for combined bilingual notes")
    parser.add_argument("--zh-out", default="", help="Output path for Chinese-only notes (fallback to EN)")
    args = parser.parse_args()

    zh_body = extract_section(args.zh, args.tag)
    en_body = extract_section(args.en, args.tag)
    combined = format_bilingual(args.tag, zh_body, en_body)

    if args.out:
        write_text(args.out, combined)
    if args.zh_out:
        manifest_notes = (zh_body or en_body).strip()
        if manifest_notes:
            manifest_notes += "\n"
        write_text(args.zh_out, manifest_notes)

    has_notes = bool(combined.strip())
    print(f"has_notes={'true' if has_notes else 'false'}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
