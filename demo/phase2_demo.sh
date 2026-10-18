#!/bin/bash
# AgentGuard Phase 2 demo — exercises the policy proxy end to end.
# Run AFTER: docker compose up --build   (from the repo root)
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

echo "== 3. create agent"
DEMO_AGENT_NAME="demo-agent-$(date +%s)"
AGENT_ID=$(curl -s -X POST "$BASE/agents" -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"name\":\"$DEMO_AGENT_NAME\",\"declaredTask\":\"Look up public info via web search\",\"riskTier\":\"standard\"}" \
  | jget "['id']")
echo "   agent: $AGENT_ID"

echo "== 4. issue API key (shown once)"
API_KEY=$(curl -s -X POST "$BASE/agents/$AGENT_ID/api-keys" -H "$AUTH" \
  -H 'Content-Type: application/json' -d '{"label":"demo"}' | jget "['apiKey']")
echo "   key: ${API_KEY:0:12}... (truncated)"

echo "== 5. tool ids"
TOOLS_JSON=$(curl -s "$BASE/tools" -H "$AUTH")
WS_ID=$(echo "$TOOLS_JSON" | python3 -c "import sys,json; print([t['id'] for t in json.load(sys.stdin) if t['name']=='web_search'][0])")
CE_ID=$(echo "$TOOLS_JSON" | python3 -c "import sys,json; print([t['id'] for t in json.load(sys.stdin) if t['name']=='code_exec'][0])")
DQ_ID=$(echo "$TOOLS_JSON" | python3 -c "import sys,json; print([t['id'] for t in json.load(sys.stdin) if t['name']=='db_query'][0])")

echo "== 6. policies: ALLOW web_search, DENY code_exec, APPROVE db_query"
mkpolicy() { curl -s -X POST "$BASE/policies" -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"agentId\":\"$AGENT_ID\",\"toolId\":\"$1\",\"effect\":\"$2\"}"; echo; }
mkpolicy "$WS_ID" "ALLOW"
mkpolicy "$CE_ID" "DENY"
mkpolicy "$DQ_ID" "APPROVE"

echo "== 7. start run"
DEMO_EVENT_PREFIX="phase2-$(date +%s)"
RUN_ID=$(curl -s -X POST "$BASE/runs" -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"agentId\":\"$AGENT_ID\"}" | jget "['id']")
echo "   run: $RUN_ID"

echo "== 8. invoke web_search (expect ALLOW + tool result)"
curl -s -X POST "$BASE/tools/invoke" -H "X-API-Key: $API_KEY" -H 'Content-Type: application/json' \
  -d "{\"eventId\":\"$DEMO_EVENT_PREFIX-1\",\"toolName\":\"web_search\",\"runId\":\"$RUN_ID\",\"args\":{\"query\":\"MCP protocol\"}}"
echo; echo "== 9. invoke code_exec (expect DENY with cited policy)"
curl -s -X POST "$BASE/tools/invoke" -H "X-API-Key: $API_KEY" -H 'Content-Type: application/json' \
  -d "{\"eventId\":\"$DEMO_EVENT_PREFIX-2\",\"toolName\":\"code_exec\",\"runId\":\"$RUN_ID\",\"args\":{\"code\":\"print(1)\"}}"
echo; echo "== 10. invoke db_query (expect APPROVE → needs human)"
curl -s -X POST "$BASE/tools/invoke" -H "X-API-Key: $API_KEY" -H 'Content-Type: application/json' \
  -d "{\"eventId\":\"$DEMO_EVENT_PREFIX-3\",\"toolName\":\"db_query\",\"runId\":\"$RUN_ID\",\"args\":{\"sql\":\"SELECT 1\"}}"
echo
echo "== done. Approve evt-3 via: POST $BASE/decisions"
echo "   {\"eventId\":\"<id from step 10>\",\"decision\":\"APPROVE\",\"reason\":\"demo\"}"
