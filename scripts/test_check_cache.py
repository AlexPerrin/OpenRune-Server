import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from check_cache import bad_ids  # noqa: E402


def groups(stock, custom):
    g = {i: {"ver": 15947 if i < 9000 else 1791883882} for i in range(stock + 1)}
    g[8070] = {"ver": 49359}
    g.update({i: {"ver": 1} for i in custom})
    return g


class CheckCache(unittest.TestCase):
    def test_stock_above_9999_and_custom_right_after_it_pass(self):
        self.assertEqual(bad_ids(groups(10196, [10197, 10198])), (10196, []))

    def test_no_custom_passes(self):
        self.assertEqual(bad_ids(groups(9972, [])), (9972, []))

    def test_custom_away_from_the_stock_end_fails(self):
        self.assertEqual(bad_ids(groups(10196, [10197, 45500])), (10196, [45500]))
        self.assertEqual(bad_ids(groups(10196, [10198])), (10196, [10198]))


if __name__ == "__main__":
    unittest.main()
