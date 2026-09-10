import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


class MediaPlaybackSemanticsTests(unittest.TestCase):
    def test_buffering_and_connecting_use_pause_side_semantics(self):
        media = (ROOT / "launcher/src/main/java/com/cbkii/ts18launcher/MediaListenerService.java").read_text(encoding="utf-8")
        self.assertIn("private static boolean usesPauseAction(int state)", media)
        self.assertIn("state == PlaybackState.STATE_PLAYING", media)
        self.assertIn("state == PlaybackState.STATE_BUFFERING", media)
        self.assertIn("state == PlaybackState.STATE_CONNECTING", media)
        self.assertIn("this.playing = usesPauseAction(state);", media)
        self.assertIn("boolean pauseSide = state != null && usesPauseAction(state.getState());", media)
        self.assertIn("if (pauseSide) controller.getTransportControls().pause();", media)


if __name__ == "__main__":
    unittest.main()
