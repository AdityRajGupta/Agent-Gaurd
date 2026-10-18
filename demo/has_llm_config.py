#!/usr/bin/env python3
"""Exit successfully when a supported free-tier LLM key is configured."""

import os
from pathlib import Path


def configured(key: str) -> bool:
    value = os.environ.get(key, "").strip()
    if value:
        return True

    env_file = Path(__file__).resolve().parents[1] / ".env"
    if not env_file.is_file():
        return False
    for line in env_file.read_text(errors="replace").splitlines():
        entry = line.strip()
        if not entry or entry.startswith("#"):
            continue
        if entry.startswith("export "):
            entry = entry[7:].lstrip()
        name, separator, raw_value = entry.partition("=")
        if not separator or name.strip() != key:
            continue
        value = raw_value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
            value = value[1:-1]
        return bool(value.strip())
    return False


raise SystemExit(0 if any(configured(k) for k in ("GROQ_API_KEY", "GEMINI_API_KEY")) else 1)
