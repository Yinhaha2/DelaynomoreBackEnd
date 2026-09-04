from collections.abc import AsyncIterator

from fastapi import APIRouter, Header, HTTPException
from fastapi.responses import StreamingResponse

from app.core.exceptions import AppError, InvalidRequestError
from app.deps import get_container
from app.schemas.chat import ChatStreamRequest
from app.streaming.chunks import ErrorChunk
from app.streaming.sse import error_to_sse_event

router = APIRouter(prefix="/chat", tags=["chat"])


@router.post("/stream")
async def chat_stream(
    request: ChatStreamRequest,
    accept: str | None = Header(default=None),
) -> StreamingResponse:
    if accept and "text/event-stream" not in accept:
        raise HTTPException(status_code=406, detail="Accept 必须为 text/event-stream")

    service = get_container().chat_service

    async def event_generator() -> AsyncIterator[str]:
        try:
            async for frame in service.stream_chat(request):
                yield frame
        except InvalidRequestError as exc:
            yield error_to_sse_event(
                ErrorChunk(code=exc.code.value, message=exc.message)
            )
        except AppError as exc:
            yield error_to_sse_event(
                ErrorChunk(code=exc.code.value, message=exc.message)
            )

    return StreamingResponse(
        event_generator(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )
