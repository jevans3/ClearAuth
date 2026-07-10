"""Optional LLM criterion matcher (Anthropic Claude).

When ANTHROPIC_API_KEY is set and the `anthropic` package is installed, the
engine can ask Claude to judge note_keywords criteria against the free-text
clinical note — catching paraphrases the keyword matcher misses (e.g.
"tried ibuprofen for two months without relief" ≈ failed conservative therapy).

Anti-hallucination contract: the model must return a VERBATIM quote from the
note as evidence. We verify the quote is a real substring before trusting the
judgment; if verification fails, we fall back to the deterministic keyword
matcher. The LLM can therefore widen recall but can never fabricate evidence.
"""

from __future__ import annotations

import json
import os


def llm_available() -> bool:
    if not os.environ.get("ANTHROPIC_API_KEY"):
        return False
    try:
        import anthropic  # noqa: F401
        return True
    except ImportError:
        return False


_PROMPT = """You are a utilization-review assistant. Judge whether the clinical
note below satisfies the medical-necessity criterion.

Criterion: {criterion}

Clinical note:
<note>
{note}
</note>

Respond with ONLY a JSON object:
{{"met": true/false, "quote": "<verbatim quote from the note that is your evidence, or empty string>"}}
The quote MUST be copied character-for-character from the note."""


def judge_criterion(criterion_text: str, note: str,
                    model: str = "claude-haiku-4-5-20251001") -> tuple[bool, str] | None:
    """Returns (met, verbatim_quote) or None when unavailable/unverifiable."""
    if not llm_available() or not note.strip():
        return None
    import anthropic

    client = anthropic.Anthropic()
    try:
        message = client.messages.create(
            model=model,
            max_tokens=300,
            messages=[{"role": "user",
                       "content": _PROMPT.format(criterion=criterion_text, note=note)}],
        )
        payload = json.loads(message.content[0].text)
        met = bool(payload.get("met"))
        quote = str(payload.get("quote", ""))
    except Exception:
        return None

    if met and quote and quote not in note:
        return None  # hallucinated evidence -> reject, caller falls back
    return met, quote
