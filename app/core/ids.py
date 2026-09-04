from datetime import datetime, timezone
from uuid import uuid4


def generate_conversation_id() -> str:
    return f"conv_{uuid4().hex[:12]}"


def generate_message_id() -> str:
    return f"msg_{uuid4().hex[:12]}"


def utc_now_iso() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")
