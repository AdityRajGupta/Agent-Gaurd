"""Generate synthetic feature CSVs for training / evaluation (Phase 3 demo).

Benign rows mimic calm agent usage, INCLUDING the quiet corner that fresh
agents live in (low call rate, zeros elsewhere) — without these, a new agent's
first calm actions look anomalous. Attack rows mimic realistic bursts:
hammering tools (high call frequency) with heavy sensitive-tool use.

The SCORING pipeline is fully live — only the training baseline is synthetic,
which is documented, not hidden.

Usage:
    python generate_baseline.py --mode benign --rows 300 --out /tmp/benign.csv
    python generate_baseline.py --mode attack  --rows 100 --out /tmp/attack.csv

Columns: call_freq, rare_tool_rate, sensitive_access_count, ngram_novelty,
         tod_deviation   (10-minute sliding window; see FeatureService.java)
"""
import argparse
import csv

import numpy as np


def benign_row(rng):
    if rng.random() < 0.3:
        # fresh / low-activity agent: quiet corner of the feature space
        return [
            float(rng.uniform(0.05, 0.30)),  # call_freq (calls/min)
            0.0,                             # rare_tool_rate
            0.0,                             # sensitive_access_count
            0.0,                             # ngram_novelty
            float(rng.uniform(0.0, 0.20)),   # tod_deviation
        ]
    return [
        float(rng.uniform(0.05, 0.50)),
        float(rng.uniform(0.0, 0.10)),
        float(rng.integers(0, 2)),
        float(rng.uniform(0.0, 0.15)),
        float(rng.uniform(0.0, 0.30)),
    ]


def attack_row(rng):
    # realistic burst: rapid calls hammering (sensitive) tools
    return [
        float(rng.uniform(2.0, 5.0)),    # call_freq: burst
        float(rng.uniform(0.2, 0.6)),    # rare_tool_rate: unusual tools
        float(rng.integers(5, 26)),      # sensitive_access_count: hammering
        float(rng.uniform(0.3, 0.8)),    # ngram_novelty: unusual sequences
        float(rng.uniform(0.0, 0.5)),    # tod_deviation
    ]


HEADER = ["call_freq", "rare_tool_rate", "sensitive_access_count",
          "ngram_novelty", "tod_deviation"]


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--mode", choices=["benign", "attack"], required=True)
    ap.add_argument("--rows", type=int, default=300)
    ap.add_argument("--out", required=True)
    ap.add_argument("--seed", type=int, default=42)
    args = ap.parse_args()

    rng = np.random.default_rng(args.seed)
    gen = benign_row if args.mode == "benign" else attack_row
    with open(args.out, "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(HEADER)
        w.writerows(gen(rng) for _ in range(args.rows))
    print(f"wrote {args.rows} {args.mode} rows to {args.out}")


if __name__ == "__main__":
    main()
