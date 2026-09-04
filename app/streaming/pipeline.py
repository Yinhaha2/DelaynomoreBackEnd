from collections.abc import AsyncIterator

from app.core.exceptions import AppError, ErrorCode
from app.core.ids import generate_message_id
from app.streaming.chunks import ContentChunk, DoneChunk, ErrorChunk, StreamChunk
from app.streaming.sse import sse_stream


class StreamPipeline:
    """将 Agent 产出的内容块转换为符合接口协议的 SSE 流。"""

    async def run(
        self,
        content_chunks: AsyncIterator[ContentChunk],
        *,
        conversation_id: str,
        message_id: str | None = None,
    ) -> AsyncIterator[str]:
        resolved_message_id = message_id or generate_message_id()

        async def emit() -> AsyncIterator[StreamChunk]:
            try:
                async for chunk in content_chunks:
                    yield chunk
                yield DoneChunk(
                    message_id=resolved_message_id,
                    conversation_id=conversation_id,
                )
            except AppError as exc:
                yield ErrorChunk(code=exc.code.value, message=exc.message)
            except Exception:
                yield ErrorChunk(
                    code=ErrorCode.AGENT_ERROR.value,
                    message="Agent 内部错误，请稍后重试",
                )

        async for frame in sse_stream(emit()):
            yield frame
