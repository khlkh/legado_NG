#!/usr/bin/env python3
"""Generate the Smart Glosses fusion prototype's frequency-rank asset.

Source
------
Wiktionary: Frequency lists / English / Project Gutenberg
https://en.wiktionary.org/wiki/Wiktionary:Frequency_lists/English/Project_Gutenberg

The 16 April 2006 snapshot is a straight frequency count of the Project
Gutenberg English texts then available (~24,197 files, ~1.7 billion words;
pre-1923-heavy and including Project Gutenberg boilerplate). The canonical
wikitext is used here directly: the convenient CSV extracts in the wild are
not clean (the 20k CSV duplicates rows with missing counts; the 36k CSV
repeats its header every 1,000 rows).

License
-------
The Wiktionary page text is CC BY-SA 4.0:
https://creativecommons.org/licenses/by-sa/4.0/
The underlying Project Gutenberg texts are public domain in the US. This
script emits a factual rank extraction and keeps the source/license notice in
the generated asset header and metadata file.

Usage
-----
    python3 -I tools/word-annotation/generate_frequency_rank.py \
        --input-dir ~/Downloads/words/gutenberg \
        --output app/src/main/assets/word_annotation/frequency_rank_2006_04.txt \
        --meta-output app/src/main/assets/word_annotation/frequency_rank_2006_04.meta.json

The input directory must contain the four canonical snapshot pages:
    wiktionary_pg_2006_04_1-10000.wiki
    wiktionary_pg_2006_04_10001-20000.wiki
    wiktionary_pg_2006_04_20001-30000.wiki
    wiktionary_pg_2006_04_30001-40000.wiki

Output format: a header whose lines start with '#', then one ranked word per
line. The asset loader drops '#' lines and lets every remaining line consume a
rank, including abbreviation/number tokens the index itself will skip. That
keeps the canonical rank numbers stable.
"""

from __future__ import annotations

import argparse
import hashlib
import html
import json
import re
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path

PARSER_VERSION = "1.0"
DEFAULT_SOURCE = (
    "https://en.wiktionary.org/wiki/"
    "Wiktionary:Frequency_lists/English/Project_Gutenberg"
)
DEFAULT_LICENSE = "CC BY-SA 4.0"
DEFAULT_LICENSE_URL = "https://creativecommons.org/licenses/by-sa/4.0/"

ROW = re.compile(r"\|\s*(\d+)\s*\|\|\s*(.*?)\s*\|\|\s*([\d,.]+)\s*$")
LINK = re.compile(r"\[\[([^\]|]+)(?:\|([^\]]+))?\]\](.*)")
FILE_RANGE = re.compile(r"_(\d+)-(\d+)\.wiki$")


def parse_word(raw: str) -> str:
    raw = raw.strip()
    match = LINK.match(raw)
    if match:
        word = (match.group(2) or match.group(1)) + (match.group(3) or "")
    else:
        word = raw.strip("' ")
    word = html.unescape(word).strip()
    return re.sub(r"\s+", " ", word)


def snapshot_files(input_dir: Path) -> list[Path]:
    files = []
    for path in input_dir.glob("wiktionary_pg_2006_04_*.wiki"):
        match = FILE_RANGE.search(path.name)
        if match:
            files.append((int(match.group(1)), path))
    if not files:
        raise SystemExit(f"no wiktionary_pg_2006_04_*.wiki files in {input_dir}")
    return [path for _, path in sorted(files)]


def parse_snapshot(input_dir: Path) -> list[tuple[int, str, float]]:
    rows: list[tuple[int, str, float]] = []
    seen_ranks: set[int] = set()
    for path in snapshot_files(input_dir):
        for line_number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            text = line.strip()
            if not text.startswith("|"):
                continue
            match = ROW.match(text)
            if not match:
                continue  # table header rows and other markup
            rank = int(match.group(1))
            word = parse_word(match.group(2))
            count = float(match.group(3).replace(",", ""))
            if not word:
                raise SystemExit(f"{path.name}:{line_number}: empty word at rank {rank}")
            if rank in seen_ranks:
                raise SystemExit(f"{path.name}:{line_number}: duplicate rank {rank}")
            seen_ranks.add(rank)
            rows.append((rank, word, count))

    rows.sort(key=lambda item: item[0])
    expected = list(range(1, len(rows) + 1))
    actual = [rank for rank, _, _ in rows]
    if actual != expected:
        missing = sorted(set(expected) - set(actual))[:10]
        raise SystemExit(f"rank sequence is not contiguous; first gaps: {missing}")
    words = [word for _, word, _ in rows]
    if len(words) != len(set(words)):
        duplicates = [word for word, count in Counter(words).items() if count > 1][:10]
        raise SystemExit(f"duplicate words in snapshot: {duplicates}")
    if [word for _, word, _ in rows[:3]] != ["the", "of", "and"]:
        raise SystemExit("top-three sanity check failed: expected the, of, and")
    if any(count <= 0 for _, _, count in rows):
        raise SystemExit("non-positive count in snapshot")
    return rows


def write_asset(
    rows: list[tuple[int, str, float]],
    output: Path,
    meta_output: Path | None,
    source: str,
    snapshot: str,
    version: str,
    license_name: str,
    license_url: str,
) -> str:
    word_lines = [word.replace("\n", " ") for _, word, _ in rows]
    payload = ("\n".join(word_lines) + "\n").encode("utf-8")
    digest = hashlib.sha256(payload).hexdigest()

    header = [
        "# Word frequency rank asset - Smart Glosses fusion prototype.",
        f"# Source: Wiktionary:Frequency_lists/English/Project_Gutenberg, {snapshot} snapshot",
        f"#   {source}",
        "# Underlying texts: Project Gutenberg (~24,197 files, ~1.7B words; mostly pre-1923).",
        "# Project Gutenberg boilerplate is part of the original counts; archaic words skew high.",
        f"# License: {license_name} - {license_url}",
        "# Generated by tools/word-annotation/generate_frequency_rank.py; do not edit by hand.",
        f"# Version: {version}; rows: {len(rows)}; sha256: {digest}",
        "# Format: one ranked word per line; lines starting with '#' are not ranked.",
    ]
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("wb") as handle:
        handle.write(("\n".join(header) + "\n").encode("utf-8"))
        handle.write(payload)

    if meta_output is not None:
        meta = {
            "version": version,
            "source_url": source,
            "snapshot": snapshot,
            "license": license_name,
            "license_url": license_url,
            "underlying_texts": "Project Gutenberg (public domain in the US)",
            "rows": len(rows),
            "first": rows[0][1],
            "last": rows[-1][1],
            "sha256": digest,
            "parser": f"generate_frequency_rank.py {PARSER_VERSION}",
            "generated_utc": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        }
        meta_output.parent.mkdir(parents=True, exist_ok=True)
        meta_output.write_text(json.dumps(meta, indent=2) + "\n", encoding="utf-8")
    return digest


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input-dir", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--meta-output", type=Path, default=None)
    parser.add_argument("--source-url", default=DEFAULT_SOURCE)
    parser.add_argument("--snapshot", default="2006-04-16")
    parser.add_argument("--version", default="gutenberg-2006-04")
    parser.add_argument("--license", default=DEFAULT_LICENSE)
    parser.add_argument("--license-url", default=DEFAULT_LICENSE_URL)
    args = parser.parse_args()

    rows = parse_snapshot(args.input_dir)
    digest = write_asset(
        rows,
        output=args.output,
        meta_output=args.meta_output,
        source=args.source_url,
        snapshot=args.snapshot,
        version=args.version,
        license_name=args.license,
        license_url=args.license_url,
    )
    print(f"parsed {len(rows)} rows from {args.input_dir}")
    print(f"first: {rows[0][0]} {rows[0][1]!r} ({rows[0][2]})")
    print(f"rank 280: {next(word for rank, word, _ in rows if rank == 280)!r}")
    print(f"last: {rows[-1][0]} {rows[-1][1]!r} ({rows[-1][2]})")
    print(f"sha256: {digest}")
    print(f"wrote {args.output}")
    if args.meta_output is not None:
        print(f"wrote {args.meta_output}")


if __name__ == "__main__":
    main()
