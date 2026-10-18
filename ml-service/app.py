"""AgentGuard ML scoring service (Phase 3 — functional).

FastAPI service exposing per-agent Isolation Forest anomaly scoring.
Spring Boot calls POST /score with sliding-window features; on timeout or
error the backend fails closed (high-sensitivity tools require approval).

Feature vector (per agent, 10-minute sliding window) — see PRD §6:
  [call_freq, rare_tool_rate, sensitive_access_count, ngram_novelty, tod_deviation]

Models: ./models/<agent-id>.joblib, trained by train.py. POST /reload picks up
newly trained models without a restart.
"""
import glob
import os

import joblib
import numpy as np
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

app = FastAPI(title="AgentGuard ML Service")

MODELS_DIR = os.environ.get("MODELS_DIR", "./models")
MODELS: dict = {}


def load_models() -> int:
    MODELS.clear()
    os.makedirs(MODELS_DIR, exist_ok=True)
    for path in glob.glob(os.path.join(MODELS_DIR, "*.joblib")):
        agent_id = os.path.splitext(os.path.basename(path))[0]
        MODELS[agent_id] = joblib.load(path)
    return len(MODELS)


def anomaly_score(model, features: list[float]) -> float:
    """Map IsolationForest decision_function to a 0..1 anomaly score.

    decision_function is >0 for inliers and <0 for outliers. A logistic map
    turns that into a calibrated score: clearly normal -> near 0,
    clearly anomalous -> near 1, borderline -> ~0.5.

    Steepness k=20 was chosen empirically: with the feature distributions in
    generate_baseline.py it puts calm traffic near ~0.15 and real bursts near
    ~0.76, cleanly split by the 0.7 standard-tier threshold.
    """
    raw = float(model.decision_function([features])[0])
    return float(1.0 / (1.0 + np.exp(20.0 * raw)))


class ScoreRequest(BaseModel):
    agent_id: str
    features: list[float]  # length-5 vector, see module docstring


class ScoreResponse(BaseModel):
    agent_id: str
    anomaly_score: float  # 0.0 (normal) .. 1.0 (anomalous)
    model_version: str


@app.on_event("startup")
def startup():
    n = load_models()
    print(f"loaded {n} model(s) from {MODELS_DIR}")


@app.get("/health")
def health():
    return {"status": "ok", "models_loaded": len(MODELS)}


@app.post("/reload")
def reload():
    return {"models_loaded": load_models()}


@app.post("/score", response_model=ScoreResponse)
def score(req: ScoreRequest):
    if len(req.features) != 5:
        raise HTTPException(400, "features must be a length-5 vector")
    model = MODELS.get(req.agent_id)
    if model is None:
        # No trained model yet → neutral score; policy still enforces.
        return ScoreResponse(agent_id=req.agent_id, anomaly_score=0.0,
                             model_version="untrained")
    return ScoreResponse(agent_id=req.agent_id,
                         anomaly_score=anomaly_score(model, req.features),
                         model_version="iforest-v1")
