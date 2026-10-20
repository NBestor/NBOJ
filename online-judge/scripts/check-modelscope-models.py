#!/usr/bin/env python3
import json
import os
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path


def load_dotenv(path: str) -> dict[str, str]:
    values: dict[str, str] = {}
    file = Path(path)
    if not file.exists():
        return values
    for raw in file.read_text(errors="ignore").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip().strip('"').strip("'")
    return values


DOTENV = load_dotenv(".env")
BASE_URL = (
    DOTENV.get("OJ_AI_BASE_URL")
    or DOTENV.get("AI_BASE_URL")
    or os.environ.get("OJ_AI_BASE_URL")
    or os.environ.get("AI_BASE_URL")
    or "https://api-inference.modelscope.cn/v1"
)
DEFAULT_MODEL = (
    DOTENV.get("OJ_AI_MODEL")
    or DOTENV.get("AI_MODEL")
    or os.environ.get("OJ_AI_MODEL")
    or os.environ.get("AI_MODEL")
    or "Qwen/Qwen3-235B-A22B-Instruct-2507"
)


def api_key() -> str:
    return (
        DOTENV.get("OJ_MODELSCOPE_API_KEY")
        or DOTENV.get("MODELSCOPE_API_KEY")
        or os.environ.get("OJ_MODELSCOPE_API_KEY")
        or os.environ.get("MODELSCOPE_API_KEY")
        or ""
    )


def request(
    path: str,
    method: str = "GET",
    body: dict | None = None,
    base_url: str = BASE_URL,
    bearer_token: str | None = None,
) -> tuple[int, str, int]:
    key = bearer_token or api_key()
    if not key:
        raise SystemExit("Missing OJ_MODELSCOPE_API_KEY or MODELSCOPE_API_KEY.")
    url = base_url.rstrip("/") + path
    data = None if body is None else json.dumps(body, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=data,
        method=method,
        headers={
            "Content-Type": "application/json",
            "Authorization": "Bearer " + key,
        },
    )
    started = time.time()
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            return resp.status, resp.read().decode("utf-8", "replace"), int((time.time() - started) * 1000)
    except urllib.error.HTTPError as exc:
        return exc.code, exc.read().decode("utf-8", "replace"), int((time.time() - started) * 1000)


def list_models() -> None:
    status, text, latency = request("/models")
    print(f"GET /models -> HTTP {status} ({latency} ms)")
    if status != 200:
        print(text[:1200])
        raise SystemExit(1)
    data = json.loads(text)
    for item in data.get("data", []):
        model_id = item.get("id", "")
        if model_id:
            print(model_id)


def smoke(model: str) -> None:
    payload = {
        "model": model,
        "messages": [{"role": "user", "content": "Return exactly OK."}],
        "stream": False,
    }
    status, text, latency = request("/chat/completions", "POST", payload)
    print(f"POST /chat/completions model={model} -> HTTP {status} ({latency} ms)")
    print(text[:1200].replace("\n", " "))
    if status != 200:
        raise SystemExit(1)
    root = json.loads(text)
    choices = root.get("choices") or []
    content = ""
    if choices:
        first = choices[0]
        content = ((first.get("message") or {}).get("content")
                   or (first.get("delta") or {}).get("content")
                   or "")
    if "OK" not in content:
        raise SystemExit("Smoke response did not include OK.")


def main() -> None:
    command = sys.argv[1] if len(sys.argv) > 1 else "smoke"
    if command == "list":
        list_models()
    elif command == "smoke":
        smoke(sys.argv[2] if len(sys.argv) > 2 else DEFAULT_MODEL)
    else:
        raise SystemExit("Usage: scripts/check-modelscope-models.py [list|smoke MODEL_ID]")


if __name__ == "__main__":
    main()
