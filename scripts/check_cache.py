#!/usr/bin/env python3
"""Fails if OpenRune's JS5 cache (src/.data/cache/LIVE) holds clientscript groups (archive 12) the
Android client (241.4) crashes on: ids away from the stock range. The stock range is 1:1 with the
official cache and ends at the highest id of a group the packer did not add (OpenRune's packer
gives the groups it adds version 1). Custom scripts must take the ids right after it.
Usage: check_cache.py [CACHE_DIR]
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "tools", "openrune"))
from cachediff import Disk, decompress  # noqa: E402
import reftable  # noqa: E402

CUSTOM_VERSION = 1


def stock_max(groups):
    return max(i for i, g in groups.items() if g["ver"] != CUSTOM_VERSION)


def bad_ids(groups):
    top = stock_max(groups)
    ids = sorted(i for i in groups if i > top)
    return top, [i for n, i in enumerate(ids) if i != top + 1 + n]


if __name__ == "__main__":
    cache = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "..", "src", ".data", "cache", "LIVE")
    table = reftable.decode(decompress(Disk(cache).raw(255, 12)))
    top, bad = bad_ids(table["groups"])
    print(f"clientscripts: {len(table['groups'])} groups, stock max id {top}, max id {max(table['groups'])}")
    if bad:
        sys.exit(f"clientscript ids not right after the stock maximum {top} break the Android client: {bad}")
