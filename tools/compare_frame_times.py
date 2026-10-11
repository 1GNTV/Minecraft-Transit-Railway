"""Describe two local frame-time CSV captures. Never estimates FPS from Spark samples."""
import argparse
import csv
import hashlib
import json
import math
from pathlib import Path
import statistics


def percentile(values, fraction):
    ordered = sorted(values)
    position = (len(ordered) - 1) * fraction
    lower = math.floor(position)
    upper = math.ceil(position)
    return ordered[lower] + (ordered[upper] - ordered[lower]) * (position - lower)


def summarize(frame_ms):
    if not frame_ms or any(not math.isfinite(v) or v <= 0 for v in frame_ms):
        raise ValueError("Every included frame time must be finite and strictly positive")
    worst = sorted(frame_ms, reverse=True)[:max(1, math.ceil(len(frame_ms) * 0.01))]
    return {
        "samples": len(frame_ms), "captured_seconds": sum(frame_ms) / 1000,
        "mean_frame_ms": statistics.mean(frame_ms),
        "median_frame_ms": statistics.median(frame_ms),
        "p95_frame_ms": percentile(frame_ms, 0.95),
        "p99_frame_ms": percentile(frame_ms, 0.99),
        "max_frame_ms": max(frame_ms),
        "average_fps": 1000 / statistics.mean(frame_ms),
        "median_frame_rate_fps": 1000 / statistics.median(frame_ms),
        "one_percent_low_fps": 1000 / statistics.mean(worst),
        "frames_over_16_67_ms": sum(v > 1000 / 60 for v in frame_ms),
        "frames_over_33_33_ms": sum(v > 1000 / 30 for v in frame_ms),
    }


def read_capture(path, column, discard):
    raw = Path(path).read_bytes()
    text = raw.decode("utf-8-sig")
    try:
        dialect = csv.Sniffer().sniff(text[:4096], delimiters=",;\t")
    except csv.Error:
        dialect = csv.excel
    reader = csv.DictReader(text.splitlines(), dialect=dialect)
    if column not in (reader.fieldnames or []):
        raise ValueError(f"Missing frame-time column {column!r} in {Path(path).name}")
    frames = []
    for row_number, row in enumerate(reader, 2):
        try:
            value = float(row[column])
        except (ValueError, TypeError):
            raise ValueError(f"Invalid frame time at row {row_number} in {Path(path).name}") from None
        if not math.isfinite(value) or value <= 0:
            raise ValueError(f"Nonpositive/nonfinite frame time at row {row_number} in {Path(path).name}")
        frames.append(value)
    if discard < 0 or discard >= len(frames):
        raise ValueError("Discard count must be nonnegative and leave at least one sample")
    return {
        "file": Path(path).name, "sha256": hashlib.sha256(raw).hexdigest(),
        "column": column, "discarded_first_samples": discard, "total_samples": len(frames),
        **summarize(frames[discard:]),
    }


def compare(baseline, candidate):
    # A positive reduction means shorter frame times; positive FPS change means faster frames.
    result = {}
    for field in ["mean_frame_ms", "median_frame_ms", "p95_frame_ms", "p99_frame_ms"]:
        result[field + "_reduction_percent"] = 100 * (1 - candidate[field] / baseline[field])
    for field in ["average_fps", "one_percent_low_fps"]:
        result[field + "_change_percent"] = 100 * (candidate[field] / baseline[field] - 1)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("baseline", type=Path)
    parser.add_argument("candidate", type=Path)
    parser.add_argument("--frame-ms-column", default="frame_ms")
    parser.add_argument("--discard-first-samples", type=int, default=0)
    parser.add_argument("--scenario", required=True, help="Local label: idle/arrival/interior and shader setting")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    try:
        baseline = read_capture(args.baseline, args.frame_ms_column, args.discard_first_samples)
        candidate = read_capture(args.candidate, args.frame_ms_column, args.discard_first_samples)
    except ValueError as error:
        parser.error(str(error))
    result = {
        "scenario": args.scenario, "baseline": baseline, "candidate": candidate,
        "changes": compare(baseline, candidate),
        "interpretation": "Descriptive statistics only; capture equivalence, repeats and variability require review. 1% low = 1000 / mean of slowest ceil(1% * n) frame times.",
    }
    output = json.dumps(result, indent=2, ensure_ascii=False) + "\n"
    if args.output:
        args.output.write_text(output, encoding="utf-8")
    else:
        print(output, end="")


if __name__ == "__main__":
    main()
