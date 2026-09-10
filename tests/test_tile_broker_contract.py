from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
BROKER = ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/TileBroker.java"


class TileBrokerContractTests(unittest.TestCase):
    def test_cache_policy_is_bounded_http_aware_and_low_io(self):
        source = BROKER.read_text(encoding="utf-8")
        self.assertIn('TILE_HOST = "tile.openstreetmap.org"', source)
        self.assertIn("CONNECT_TIMEOUT_MS", source)
        self.assertIn("READ_TIMEOUT_MS", source)
        self.assertIn("MAX_TILE_BYTES = 512 * 1024", source)
        self.assertIn("MAX_CACHE_BYTES = 64L * 1024L * 1024L", source)
        self.assertIn('setRequestProperty("User-Agent", userAgent)', source)
        self.assertIn('setRequestProperty("If-None-Match"', source)
        self.assertIn('setRequestProperty("If-Modified-Since"', source)
        self.assertIn('hasCacheDirective(cacheControl, "no-store")', source)
        self.assertIn('hasCacheDirective(cacheControl, "no-cache")', source)
        self.assertIn("seconds >= 0L", source)
        self.assertIn("HttpURLConnection.HTTP_NOT_MODIFIED", source)
        self.assertIn("java.util.Locale.US", source)
        self.assertNotIn("getFD().sync()", source)
        self.assertNotIn("touch(tile", source)


if __name__ == "__main__":
    unittest.main()
