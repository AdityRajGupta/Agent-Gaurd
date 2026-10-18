#!/bin/bash
# AgentGuard Phase 3 demo — anomaly detection end to end.
# Run AFTER: docker compose up --build -d   (from the repo root)
# Flow: train per-agent Isolation Forest → benign run (low scores) →
#       red-team burst (cases open) → precision/recall report.
set -e
BASE="http://localhost:8080/api/v1"
jget() { python3 -c "import sys,json; print(json.load(sys.stdin)$1)"; }

echo "== 0. waiting for backend to boot"
for i in $(seq 1 30); do
  code=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/auth/login" \
    -H 'Content-Type: application/json' -d '{"username":"x","password":"x"}' 2>/dev/null || echo "000")
  case "$code" in
    400|401) echo "   backend ready"; break;;
  esac
  if [ "$i" = "30" ]; then
    echo "   backend did not start in 60s — check: docker compose logs backend"
    exit 1
  fi
  sleep 2
done

echo "== 1. register admin (ok if already exists)"
curl -s -X POST "$BASE/auth/register" -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"secret123","role":"ADMIN"}' > /dev/null || true

echo "== 2. login"
TOKEN=$(curl -s -X POST "$BASE/auth/login" -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"secret123"}' | jget "['token']")
AUTH="Authorization: Bearer $TOKEN"

DEMO_AGENT_NAME="ml-demo-$(date +%s)"
echo "== 3. create agent (standard tier: $DEMO_AGENT_NAME)"
AGENT_ID=$(curl -s -X POST "$BASE/agents" -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"name\":\"$DEMO_AGENT_NAME\",\"declaredTask\":\"Occasional public-info lookups\",\"riskTier\":\"standard\"}" \
  | jget "['id']")
echo "   agent: $AGENT_ID"

echo "== 4. issue API key"
API_KEY=$(curl -s -X POST "$BASE/agents/$AGENT_ID/api-keys" -H "$AUTH" \
  -H 'Content-Type: application/json' -d '{"label":"ml-demo"}' | jget "['apiKey']")

echo "== 5. policies: ALLOW web_search + db_query (high limits for the demo)"
TOOLS_JSON=$(curl -s "$BASE/tools" -H "$AUTH")
WS_ID=$(echo "$TOOLS_JSON" | python3 -c "import sys,json; print([t['id'] for t in json.load(sys.stdin) if t['name']=='web_search'][0])")
DQ_ID=$(echo "$TOOLS_JSON" | python3 -c "import sys,json; print([t['id'] for t in json.load(sys.stdin) if t['name']=='db_query'][0])")
curl -s -X POST "$BASE/policies" -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"agentId\":\"$AGENT_ID\",\"toolId\":\"$WS_ID\",\"effect\":\"ALLOW\",\"maxCalls\":\"1000\"}" > /dev/null
curl -s -X POST "$BASE/policies" -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"agentId\":\"$AGENT_ID\",\"toolId\":\"$DQ_ID\",\"effect\":\"ALLOW\",\"maxCalls\":\"1000\"}" > /dev/null
echo "   policies set"

echo "== 6. train per-agent Isolation Forest (synthetic benign baseline — documented)"
docker compose exec -T ml-service python generate_baseline.py --mode benign --rows 300 --out /tmp/benign.csv
docker compose exec -T ml-service python train.py --agent "$AGENT_ID" --events /tmp/benign.csv --out ./models
curl -s -X POST http://localhost:8001/reload
echo; echo "   model trained + loaded"

echo "== 7. benign run: 3 calm web_search calls"
DEMO_EVENT_PREFIX="phase3-$(date +%s)"
RUN_ID=$(curl -s -X POST "$BASE/runs" -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"agentId\":\"$AGENT_ID\"}" | jget "['id']")
for i in 1 2 3; do
  curl -s -X POST "$BASE/tools/invoke" -H "X-API-Key: $API_KEY" -H 'Content-Type: application/json' \
    -d "{\"eventId\":\"$DEMO_EVENT_PREFIX-benign-$i\",\"toolName\":\"web_search\",\"runId\":\"$RUN_ID\",\"args\":{\"query\":\"weather\"}}" > /dev/null
  sleep 2
done
echo "   anomaly scores (expect low):"
curl -s "$BASE/agents/$AGENT_ID/events" -H "$AUTH" \
  | python3 -c "import sys,json; [print('   ', round(e['anomalyScore'] or -1, 3)) for e in json.load(sys.stdin)[:3]]"

echo "== 8. RED TEAM: 25 rapid db_query calls (burst + sensitive)"
for i in $(seq 1 25); do
  curl -s -X POST "$BASE/tools/invoke" -H "X-API-Key: $API_KEY" -H 'Content-Type: application/json' \
    -d "{\"eventId\":\"$DEMO_EVENT_PREFIX-attack-$i\",\"toolName\":\"db_query\",\"runId\":\"$RUN_ID\",\"args\":{\"sql\":\"SELECT * FROM tool_events\"}}" > /dev/null
done
echo "   done"

echo "== 9. open anomaly cases (expect >= 1)"
curl -s "$BASE/cases?status=OPEN" -H "$AUTH" \
  | python3 -c "
import sys, json
cases = json.load(sys.stdin)
print(f'   open cases: {len(cases)}')
for c in cases[:5]:
    print(f\"    - {c['id'][:8]} score={c['score']:.3f} agent={c['agentId'][:8]}\")
"

echo "== 10. red-team eval: precision/recall on labeled data"
docker compose exec -T ml-service python generate_baseline.py --mode attack --rows 100 --out /tmp/attack.csv > /dev/null
docker compose exec -T ml-service python eval.py --model "./models/$AGENT_ID.joblib" \
  --benign /tmp/benign.csv --attack /tmp/attack.csv --threshold 0.7

echo "== done. Investigate a case: GET $BASE/cases/<id>  (RAG summaries arrive in Phase 4)"
