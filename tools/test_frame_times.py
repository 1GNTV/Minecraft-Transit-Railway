"""Known stutter traces distinguish average/median from worst-frame performance."""
import math
import tempfile
import unittest
from pathlib import Path
from compare_frame_times import compare, read_capture, summarize

class FrameTimeTest(unittest.TestCase):
    def test_known_stutter_trace(self):
        stable = summarize([10] * 1000)
        stuttering = summarize([10] * 990 + [100] * 10)
        self.assertEqual(stable["average_fps"], 100)
        self.assertEqual(stuttering["median_frame_rate_fps"], 100)
        self.assertEqual(stuttering["one_percent_low_fps"], 10)
        self.assertAlmostEqual(stuttering["average_fps"], 1000 / 10.9)
        self.assertEqual(stuttering["frames_over_33_33_ms"], 10)
        self.assertLess(compare(stable, stuttering)["one_percent_low_fps_change_percent"], 0)

    def test_csv_preserves_long_frames_and_declares_exclusions(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "trace.csv"
            path.write_text("frame_ms;note\n1000;arrival\n10;idle\n10;idle\n", encoding="utf-8-sig")
            full = read_capture(path, "frame_ms", 0)
            self.assertEqual(full["max_frame_ms"], 1000)
            trimmed = read_capture(path, "frame_ms", 1)
            self.assertEqual(trimmed["discarded_first_samples"], 1)
            self.assertEqual(trimmed["total_samples"], 3)
            self.assertEqual(trimmed["samples"], 2)
            self.assertEqual(trimmed["average_fps"], 100)
            self.assertEqual(full["sha256"], trimmed["sha256"])

    def test_invalid_data_is_rejected(self):
        for trace in [[], [0], [-1], [math.nan], [math.inf]]:
            with self.assertRaises(ValueError): summarize(trace)

if __name__ == "__main__":
    unittest.main()
