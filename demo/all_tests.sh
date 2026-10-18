#!/bin/bash
# AgentGuard full test run — Phases 2, 3, 4 end to end.
# Run AFTER: docker compose up --build -d   (from the repo root)
# Phase 4 needs an LLM: local Ollama (brew install ollama, ollama pull
# llama3.1:8b + nomic-embed-text) or GROQ_API_KEY / GEMINI_API_KEY in .env.
set -e
cd "$(dirname "$0")/.."

pass=0; fail=0
run() {
  echo; echo "########## $1 ##########"
  if "./demo/$2"; then pass=$((pass+1)); else fail=$((fail+1)); echo "FAILED: $2"; fi
}

run "Phase 2 — policy proxy"  "phase2_demo.sh"
run "Phase 3 — anomaly detection" "phase3_demo.sh"

if curl -s --max-time 3 http://localhost:11434/api/tags > /dev/null 2>&1 \
   || python3 demo/has_llm_config.py; then
  run "Phase 4 — RAG investigation" "phase4_demo.sh"
else
  echo; echo "########## Phase 4 — SKIPPED (no LLM found) ##########"
  echo "Install Ollama or set GROQ_API_KEY / GEMINI_API_KEY, then run ./demo/phase4_demo.sh"
fi

echo; echo "=================================="
echo "RESULT: $pass passed, $fail failed"
[ "$fail" = "0" ]
