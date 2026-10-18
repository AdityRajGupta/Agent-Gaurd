"""Red-team evaluation for a trained per-agent model (Phase 3).

Scores labeled benign + attack feature CSVs, applies the risk-tier threshold,
and reports precision / recall / F1 (attack = positive class).

Usage:
    python eval.py --model ./models/<agent-id>.joblib \\
        --benign /tmp/benign.csv --attack /tmp/attack.csv --threshold 0.7
"""
import argparse

import joblib
import pandas as pd
from app import anomaly_score

HEADER = ["call_freq", "rare_tool_rate", "sensitive_access_count",
          "ngram_novelty", "tod_deviation"]


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--model", required=True)
    ap.add_argument("--benign", required=True)
    ap.add_argument("--attack", required=True)
    ap.add_argument("--threshold", type=float, default=0.7)
    args = ap.parse_args()

    model = joblib.load(args.model)
    benign = pd.read_csv(args.benign)[HEADER].values
    attack = pd.read_csv(args.attack)[HEADER].values

    tp = sum(anomaly_score(model, r) >= args.threshold for r in attack)
    fp = sum(anomaly_score(model, r) >= args.threshold for r in benign)
    fn = len(attack) - tp

    precision = tp / (tp + fp) if (tp + fp) else 0.0
    recall = tp / (tp + fn) if (tp + fn) else 0.0
    f1 = 2 * precision * recall / (precision + recall) if (precision + recall) else 0.0

    print(f"threshold : {args.threshold}")
    print(f"benign    : {len(benign)} rows, {fp} flagged")
    print(f"attack    : {len(attack)} rows, {tp} caught, {fn} missed")
    print(f"precision : {precision:.3f}")
    print(f"recall    : {recall:.3f}")
    print(f"F1        : {f1:.3f}")


if __name__ == "__main__":
    main()
