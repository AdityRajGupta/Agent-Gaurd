#!/bin/bash
# AgentGuard Phase 4 demo — RAG investigation of an anomaly case.
# Needs: docker compose up --build -d, plus an LLM:
#   Option A (free, local): brew install ollama && ollama serve &
#                           ollama pull llama3.1:8b && ollama pull nomic-embed-text
#   Option B (free tier):   set GROQ_API_KEY or GEMINI_API_KEY in .env
set -e
BASE="http://localhost:8080/api/v1"
jget() { python3 -c "import sys,json; print(json.load(sys.stdin)$1)"; }
cd "$(dirname "$0")/.."  # run from the repo root (docker compose needs it)

echo "== 0. waiting for backend"
for i in $(seq 1 30); do
  code=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/auth/login" \
    -H 'Content-Type: application/json' -d '{"username":"x","password":"x"}' 2>/dev/null || echo "000")
  case "$code" in
    400|401) echo "   backend ready"; break;;
  esac
  if [ "$i" = "30" ]; then echo "   backend not up — docker compose logs backend"; exit 1; fi
  sleep 2
done

echo "== 1. checking for an LLM"
if curl -s --max-time 3 http://localhost:11434/api/tags > /dev/null 2>&1; then
  echo "   ollama is running (local, free)"
elif python3 demo/has_llm_config.py; then
  echo "   cloud key present (groq/gemini free tier)"
else
  echo "   NO LLM FOUND — investigation needs one:"
  echo "   brew install ollama && ollama serve &"
  echo "   ollama pull llama3.1:8b && ollama pull nomic-embed-text"
  echo "   (or set a non-empty GROQ_API_KEY / GEMINI_API_KEY in .env and rebuild)"
  exit 0
fi

echo "== 2. register + login"
curl -s -X POST "$BASE/auth/register" -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"secret123","role":"ADMIN"}' > /dev/null || true
TOKEN=$(curl -s -X POST "$BASE/auth/login" -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"secret123"}' | jget "['token']")
AUTH="Authorization: Bearer $TOKEN"

echo "== 3. create agent + key + policies"
AGENT_NAME="rag-demo-$(date +%s)"
AGENT_ID=$(curl -s -X POST "$BASE/agents" -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"name\":\"$AGENT_NAME\",\"declaredTask\":\"Occasional public-info lookups\",\"riskTier\":\"standard\"}" \
  | jget "['id']")
API_KEY=$(curl -s -X POST "$BASE/agents/$AGENT_ID/api-keys" -H "$AUTH" \
  -H 'Content-Type: application/json' -d '{"label":"rag"}' | jget "['apiKey']")
TOOLS_JSON=$(curl -s "$BASE/tools" -H "$AUTH")
WS_ID=$(echo "$TOOLS_JSON" | python3 -c "import sys,json; print([t['id'] for t in json.load(sys.stdin) if t['name']=='web_search'][0])")
DQ_ID=$(echo "$TOOLS_JSON" | python3 -c "import sys,json; print([t['id'] for t in json.load(sys.stdin) if t['name']=='db_query'][0])")
curl -s -X POST "$BASE/policies" -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"agentId\":\"$AGENT_ID\",\"toolId\":\"$WS_ID\",\"effect\":\"ALLOW\",\"maxCalls\":\"1000\"}" > /dev/null
curl -s -X POST "$BASE/policies" -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"agentId\":\"$AGENT_ID\",\"toolId\":\"$DQ_ID\",\"effect\":\"ALLOW\",\"maxCalls\":\"1000\"}" > /dev/null
echo "   agent: $AGENT_ID"

echo "== 4. train anomaly model"
docker compose exec -T ml-service python generate_baseline.py --mode benign --rows 300 --out /tmp/benign.csv > /dev/null
docker compose exec -T ml-service python train.py --agent "$AGENT_ID" --events /tmp/benign.csv --out ./models > /dev/null
curl -s -X POST http://localhost:8001/reload > /dev/null; echo "   model loaded"

echo "== 5. generate an anomaly: 15 rapid db_query calls"
RUN_ID=$(curl -s -X POST "$BASE/runs" -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"agentId\":\"$AGENT_ID\"}" | jget "['id']")
for i in $(seq 1 15); do
  curl -s -X POST "$BASE/tools/invoke" -H "X-API-Key: $API_KEY" -H 'Content-Type: application/json' \
    -d "{\"eventId\":\"rag-attack-$i\",\"toolName\":\"db_query\",\"runId\":\"$RUN_ID\",\"args\":{\"sql\":\"SELECT * FROM tool_events\"}}" > /dev/null
done
CASE_ID=$(curl -s "$BASE/cases?status=OPEN" -H "$AUTH" \
  | python3 -c "import sys,json; cs=json.load(sys.stdin); print(cs[0]['id'] if cs else '')")
if [ -z "$CASE_ID" ]; then echo "   no case opened — anomaly scoring may be off"; exit 1; fi
echo "   case: $CASE_ID"

echo "== 6. investigating (this takes ~30-60s on local Ollama)..."
curl -s -X POST "$BASE/cases/$CASE_ID/investigate" -H "$AUTH" \
  | python3 -c "
import sys, json
r = json.load(sys.stdin)
print('   provider:', r.get('provider'))
print()
print(r.get('summary', r.get('error', r)))
"
echo
echo "== done. Verify citations: every [evt:<id>] above must match a real event:"
echo "   curl -s $BASE/agents/$AGENT_ID/events -H \"\$AUTH\" | python3 -m json.tool | grep '\"id\"'"
