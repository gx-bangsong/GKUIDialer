#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
"""Builds java/com/android/dialer/yellowpage/assets/yellowpage/yellowpage.db from a CSV of business numbers.

CSV columns (UTF-8; lines starting with '#' are comments):
    number,name,category,verified,source_url
e.g.
    95311,中通快递,快递物流,1,https://www.zto.com/companyIntroduce/contactUs.html

Policy: a row is shipped only with a non-empty source_url (an official page
that states the number). Rows without one are skipped and reported. A row can
only be verified=1 when it has a source.

Usage:
    python3 tools/gen_yellowpage_db.py INPUT.csv OUTPUT.db [--version N]

Numbers must already be in the app's normalized form (national CN digits,
see NumberNormalizer): mobile 13800138000, hotline 95338/4008205555,
landline with area code 01012345678.
"""

import argparse
import csv
import sqlite3
import sys
import time


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input_csv")
    parser.add_argument("output_db")
    parser.add_argument("--version", type=int, default=int(time.time()),
                        help="meta.version (defaults to current epoch seconds)")
    args = parser.parse_args()

    conn = sqlite3.connect(args.output_db)
    cur = conn.cursor()
    cur.execute("DROP TABLE IF EXISTS yellow_page")
    cur.execute("DROP TABLE IF EXISTS meta")
    cur.execute(
        "CREATE TABLE yellow_page ("
        " number TEXT PRIMARY KEY,"
        " name TEXT NOT NULL,"
        " category TEXT,"
        " verified INTEGER NOT NULL DEFAULT 0)")
    cur.execute("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT)")

    rows = 0
    unsourced = 0
    with open(args.input_csv, encoding="utf-8") as f:
        for lineno, row in enumerate(csv.reader(f), start=1):
            if not row or row[0].startswith("#"):
                continue
            if len(row) < 2:
                print(f"line {lineno}: skipping malformed row {row!r}",
                      file=sys.stderr)
                continue
            number = row[0].strip()
            name = row[1].strip()
            category = row[2].strip() if len(row) > 2 and row[2].strip() else None
            source_url = row[4].strip() if len(row) > 4 else ""
            if not number or not name or not number.isdigit():
                print(f"line {lineno}: skipping invalid row {row!r}",
                      file=sys.stderr)
                continue
            if not source_url:
                # No official source recorded: not shipped (see module docstring).
                unsourced += 1
                print(f"line {lineno}: skipping {number} {name}: no source_url",
                      file=sys.stderr)
                continue
            verified = 1 if len(row) > 3 and row[3].strip() == "1" else 0
            cur.execute(
                "INSERT OR REPLACE INTO yellow_page VALUES (?, ?, ?, ?)",
                (number, name, category, verified))
            rows += 1

    cur.execute("INSERT INTO meta VALUES ('version', ?)", (str(args.version),))
    cur.execute("INSERT INTO meta VALUES ('source', ?)", (args.input_csv,))
    conn.commit()
    cur.execute("VACUUM")
    conn.close()
    print(f"wrote {rows} entries to {args.output_db} (version {args.version}); "
          f"skipped {unsourced} without a source")
    return 0


if __name__ == "__main__":
    sys.exit(main())
