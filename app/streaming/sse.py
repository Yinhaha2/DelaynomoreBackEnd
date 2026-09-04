import json
from collections.abc import AsyncIterator, Iterable

from app.streaming.chunks import ContentChunk, DoneChunk, ErrorChunk, SseEventName, StreamChunk


def encode_sse_event(event: SseEventName, payload: dict) -> str:
    data = json.dumps(payload, ensure_ascii=False, separators=(",", ":"))
    return f"event: {event.value}\ndata: {data}\n\n"


def chunk_to_sse_event(chunk: ContentChunk) -> str:
    return encode_sse_event(SseEventName.CHUNK, chunk.model_dump(exclude_none=True))


def done_to_sse_event(chunk: DoneChunk) -> str:
    return encode_sse_event(SseEventName.DONE, chunk.model_dump())


def error_to_sse_event(chunk: ErrorChunk) -> str:
    return encode_sse_event(SseEventName.ERROR, chunk.model_dump())


def stream_chunk_to_sse(chunk: StreamChunk) -> str:
    if isinstance(chunk, DoneChunk):
        return done_to_sse_event(chunk)
    if isinstance(chunk, ErrorChunk):
        return error_to_sse_event(chunk)
    return chunk_to_sse_event(chunk)


async def sse_stream(chunks: AsyncIterator[StreamChunk]) -> AsyncIterator[str]:
    async for chunk in chunks:
        yield stream_chunk_to_sse(chunk)


def iter_sse_frames(chunks: Iterable[StreamChunk]) -> Iterable[str]:
    for chunk in chunks:
        yield stream_chunk_to_sse(chunk)
