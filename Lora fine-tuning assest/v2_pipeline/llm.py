"""
One function, ask_json(), that sends a prompt to an AI model and returns JSON.

Two backends:
  ollama  - free, runs on your own PC (your Intel Arc). Slower, lower quality.
            Needs the Ollama app running. Default model: gemma4:12b
  claude  - Anthropic's Claude API. Fast and much better at writing natural
            dialogue, but costs money. Needs:  py -m pip install anthropic
            and an API key from https://console.anthropic.com
            set in PowerShell with:  $env:ANTHROPIC_API_KEY = "sk-ant-..."
"""
import json
import time

import requests

DEFAULT_MODELS = {"ollama": "gemma4:12b", "claude": "claude-opus-5-5"}
OLLAMA_URL = "http://localhost:11434/api/chat"

_claude_client = None


def ask_json(prompt: str, schema: dict, backend: str = "ollama", model: str | None = None,
             temperature: float = 0.8, retries: int = 3) -> dict:
    """Send `prompt`, get back a dict that matches `schema` (a JSON Schema)."""
    model = model or DEFAULT_MODELS[backend]
    last_error = None
    for attempt in range(retries):
        try:
            if backend == "ollama":
                return _ollama(prompt, schema, model, temperature)
            if backend == "claude":
                return _claude(prompt, schema, model)
            raise ValueError(f"Unknown backend: {backend}")
        except (requests.RequestException, json.JSONDecodeError, KeyError, ValueError) as e:
            last_error = e
            time.sleep(2 * (attempt + 1))
    raise RuntimeError(f"AI call failed after {retries} tries: {last_error}")


# ---------------------------------------------------------------------------
def _ollama(prompt, schema, model, temperature):
    r = requests.post(OLLAMA_URL, timeout=900, json={
        "model": model,
        "messages": [{"role": "user", "content": prompt}],
        "format": schema,          # Ollama forces the reply to match this schema
        "stream": False,
        "think": False,            # skip slow "thinking" mode on models that have it
        "options": {"temperature": temperature, "num_ctx": 8192},
    })
    r.raise_for_status()
    return json.loads(r.json()["message"]["content"])


# ---------------------------------------------------------------------------
def _claude(prompt, schema, model):
    global _claude_client
    import anthropic   # only needed for this backend

    if _claude_client is None:
        _claude_client = anthropic.Anthropic()   # reads ANTHROPIC_API_KEY
    params = dict(
        model=model,
        max_tokens=16000,
        messages=[{"role": "user", "content": prompt}],
        # Structured output: the reply is guaranteed to be JSON matching `schema`.
        # Low effort = less thinking = cheaper; this is a writing task, not a puzzle.
        output_config={"effort": "low",
                       "format": {"type": "json_schema", "schema": schema}},
    )
    try:
        # If a safety check ever declines a line, the API retries it on another
        # model automatically instead of failing.
        resp = _claude_client.beta.messages.create(
            betas=["server-side-fallback-2026-07-01"],
            extra_body={"fallbacks": "default"}, **params)
    except anthropic.BadRequestError as e:
        if "fallback" not in str(e).lower():
            raise
        resp = _claude_client.messages.create(**params)

    if resp.stop_reason == "refusal":
        raise ValueError("Claude declined this item")
    if resp.stop_reason == "max_tokens":
        raise ValueError("Reply was cut off (max_tokens)")
    text = next(b.text for b in resp.content if b.type == "text")
    return json.loads(text)


# ---------------------------------------------------------------------------
# Plain chat (used by roleplay mode: the model just talks, no JSON)
# ---------------------------------------------------------------------------
_system_support = {}


def _ollama_supports_system(model: str) -> bool:
    """Some roleplay models (Mistral-based, like Rocinante) have no real
    system-prompt slot. For those we put the character card at the top of
    the first message instead, which is how their authors recommend it."""
    if model not in _system_support:
        try:
            info = requests.post("http://localhost:11434/api/show", json={"model": model}, timeout=60).json()
            tpl = info.get("template", "")
            if "[SYSTEM_PROMPT]" in tpl and info.get("template_source") == "go_template":
                # Mistral "v7" format, which Nemo-based roleplay models are NOT trained on.
                _system_support[model] = False
            else:
                _system_support[model] = ".System" in tpl or '"system"' in tpl or "system<" in tpl
        except requests.RequestException:
            _system_support[model] = True
    return _system_support[model]


def chat(system: str, messages: list, backend: str = "ollama", model: str | None = None,
         temperature: float = 0.9, max_tokens: int = 160, retries: int = 3) -> str:
    """messages = [{"role": "user"/"assistant", "content": ...}], must start with user."""
    model = model or DEFAULT_MODELS[backend]
    last_error = None
    for attempt in range(retries):
        try:
            if backend == "ollama":
                if _ollama_supports_system(model):
                    msgs = [{"role": "system", "content": system}] + messages
                else:
                    first = dict(messages[0])
                    first["content"] = f"{system}\n\n{first['content']}"
                    msgs = [first] + messages[1:]
                r = requests.post(OLLAMA_URL, timeout=900, json={
                    "model": model, "messages": msgs, "stream": False, "think": False,
                    "options": {"temperature": temperature, "num_predict": max_tokens,
                                "repeat_penalty": 1.1, "num_ctx": 4096}})
                if r.status_code == 400 and "think" in r.text:
                    r = requests.post(OLLAMA_URL, timeout=900, json={
                        "model": model, "messages": msgs, "stream": False,
                        "options": {"temperature": temperature, "num_predict": max_tokens,
                                    "repeat_penalty": 1.1, "num_ctx": 4096}})
                r.raise_for_status()
                return r.json()["message"]["content"]
            if backend == "claude":
                global _claude_client
                import anthropic
                if _claude_client is None:
                    _claude_client = anthropic.Anthropic()
                resp = _claude_client.messages.create(
                    model=model, max_tokens=4000, system=system, messages=messages,
                    output_config={"effort": "low"})
                if resp.stop_reason == "refusal":
                    raise ValueError("Claude declined")
                return next(b.text for b in resp.content if b.type == "text")
            raise ValueError(f"Unknown backend: {backend}")
        except (requests.RequestException, KeyError, ValueError) as e:
            last_error = e
            time.sleep(2 * (attempt + 1))
    raise RuntimeError(f"AI call failed after {retries} tries: {last_error}")


def strict_object(properties: dict) -> dict:
    """Helper to build a JSON Schema object where every field is required."""
    return {"type": "object", "properties": properties,
            "required": list(properties), "additionalProperties": False}
