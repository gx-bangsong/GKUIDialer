#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 The LineageOS Project
# SPDX-License-Identifier: Apache-2.0
"""Checks the shipped yellow-page seed: generator output and sourced entries.

Run from the repository root:  python3 -m unittest tools/test_gen_yellowpage_db.py
"""

import csv
import os
import sqlite3
import subprocess
import sys
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
SEED = os.path.join(HERE, "yellowpage_seed.csv")
GEN = os.path.join(HERE, "gen_yellowpage_db.py")


def build(csv_path: str, out_db: str) -> str:
    result = subprocess.run(
        [sys.executable, GEN, csv_path, out_db, "--version", "1"],
        capture_output=True, text=True, check=True)
    return result.stderr


def seed_rows():
    with open(SEED, encoding="utf-8") as f:
        return [r for r in csv.reader(f) if r and not r[0].startswith("#")]


class YellowPageSeedTest(unittest.TestCase):

    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.db = os.path.join(self._tmp.name, "yp.db")
        self.skipped_log = build(SEED, self.db)
        self.conn = sqlite3.connect(self.db)

    def tearDown(self):
        self.conn.close()
        self._tmp.cleanup()

    def lookup(self, number):
        return self.conn.execute(
            "SELECT number, name, category, verified FROM yellow_page WHERE number = ?",
            (number,)).fetchone()

    def test_zhongtong_95311_is_zhongtong_express(self):
        row = self.lookup("95311")
        self.assertIsNotNone(row)
        self.assertEqual("中通快递", row[1])
        self.assertEqual(1, row[3])

    def test_meituan_numbers_match_official_hotlines(self):
        # meituan.com/hotline: 消费者服务热线 1010-7888, 外卖热线 1010-9777
        self.assertEqual("美团客服", self.lookup("10107888")[1])
        self.assertEqual("美团外卖客服", self.lookup("10109777")[1])

    def test_unverified_numbers_are_not_shipped(self):
        # Removed or unsourced: 95013 (联通在信), 95106 (滴滴), 10101111 (wrong 美团 number).
        for number in ("95013", "95106", "4001000000", "10101111"):
            self.assertIsNone(self.lookup(number), number)

    def test_every_shipped_row_has_a_source_in_the_seed(self):
        shipped = {r[0] for r in self.conn.execute("SELECT number FROM yellow_page")}
        sourced = {r[0] for r in seed_rows() if len(r) > 4 and r[4].strip()}
        self.assertEqual(sourced, shipped)
        for r in seed_rows():
            if len(r) > 4 and r[4].strip():
                self.assertTrue(r[4].startswith("https://"), r)

    def test_verified_rows_have_sources(self):
        for r in seed_rows():
            verified = len(r) > 3 and r[3].strip() == "1"
            if verified:
                self.assertTrue(len(r) > 4 and r[4].strip(), r)

    def test_generator_reports_skipped_rows(self):
        self.assertIn("no source_url", self.skipped_log)


if __name__ == "__main__":
    unittest.main()
