"""Phase 3 — train per-agent Isolation Forest models.

Usage:
    python train.py --agent <agent-id> --events events.csv --out ./models

events.csv columns: call_freq, rare_tool_rate, sensitive_access_count,
                    ngram_novelty, tod_deviation   (benign runs only)

The trained model is saved as ./models/<agent-id>.joblib and loaded by app.py.
Evaluate on red-team scenarios and report precision/recall/F1 (PRD §6).
"""
import argparse
import os

import joblib
import pandas as pd
from sklearn.ensemble import IsolationForest


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--agent", required=True)
    ap.add_argument("--events", required=True)
    ap.add_argument("--out", default="./models")
    ap.add_argument("--contamination", type=float, default=0.05)
    args = ap.parse_args()

    df = pd.read_csv(args.events)
    expected = ["call_freq", "rare_tool_rate", "sensitive_access_count",
                "ngram_novelty", "tod_deviation"]
    X = df[expected].values

    model = IsolationForest(n_estimators=200, contamination=args.contamination,
                            random_state=42)
    model.fit(X)

    os.makedirs(args.out, exist_ok=True)
    path = os.path.join(args.out, f"{args.agent}.joblib")
    joblib.dump(model, path)
    print(f"saved {path} ({len(X)} training rows)")


if __name__ == "__main__":
    main()
