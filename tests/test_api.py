import pytest
from httpx import ASGITransport, AsyncClient

from app.deps import reset_container
from app.main import app
from app.streaming.chunks import DoneChunk, ErrorChunk, TextDeltaChunk
from app.streaming.sse import iter_sse_frames, stream_chunk_to_sse


@pytest.fixture(autouse=True)
def _reset_container():
    reset_container()
    yield
    reset_container()


@pytest.fixture
async def client():
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as ac:
        yield ac


@pytest.mark.asyncio
async def test_health(client):
    response = await client.get("/health")
    assert response.status_code == 200
    assert response.json()["status"] == "ok"


@pytest.mark.asyncio
async def test_create_conversation(client):
    response = await client.post("/api/v1/conversations", json={})
    assert response.status_code == 200
    body = response.json()
    assert body["conversation_id"].startswith("conv_")
    assert body["created_at"].endswith("Z")


@pytest.mark.asyncio
async def test_chat_stream_text_delta_and_done(client):
    create_resp = await client.post("/api/v1/conversations", json={})
    conversation_id = create_resp.json()["conversation_id"]

    async with client.stream(
        "POST",
        "/api/v1/chat/stream",
        headers={"Accept": "text/event-stream"},
        json={
            "conversation_id": conversation_id,
            "message": "帮我找《葬送的芙莉莲》的播放资源",
        },
    ) as response:
        assert response.status_code == 200
        assert response.headers["content-type"].startswith("text/event-stream")
        body = await response.aread()

    text = body.decode("utf-8")
    assert "event: chunk" in text
    assert '"type":"text_delta"' in text
    assert "芙莉莲" in text
    assert "event: done" in text
    assert conversation_id in text


@pytest.mark.asyncio
async def test_chat_stream_auto_create_conversation(client):
    async with client.stream(
        "POST",
        "/api/v1/chat/stream",
        headers={"Accept": "text/event-stream"},
        json={"message": "你好"},
    ) as response:
        assert response.status_code == 200
        body = await response.aread()

    text = body.decode("utf-8")
    assert "event: done" in text
    assert '"conversation_id":"conv_' in text


@pytest.mark.asyncio
async def test_chat_stream_conversation_not_found(client):
    async with client.stream(
        "POST",
        "/api/v1/chat/stream",
        headers={"Accept": "text/event-stream"},
        json={
            "conversation_id": "conv_not_exists",
            "message": "你好",
        },
    ) as response:
        assert response.status_code == 200
        body = await response.aread()

    text = body.decode("utf-8")
    assert "event: error" in text
    assert "CONVERSATION_NOT_FOUND" in text


def test_sse_encoding():
    frame = stream_chunk_to_sse(TextDeltaChunk(content="你好"))
    assert frame == 'event: chunk\ndata: {"type":"text_delta","content":"你好"}\n\n'

    done_frame = stream_chunk_to_sse(
        DoneChunk(message_id="msg_abc", conversation_id="conv_abc")
    )
    assert "event: done" in done_frame
    assert '"message_id":"msg_abc"' in done_frame

    error_frame = stream_chunk_to_sse(
        ErrorChunk(code="AGENT_ERROR", message="失败")
    )
    assert "event: error" in error_frame


def test_sse_stream_helper():
    frames = list(
        iter_sse_frames(
            [
                TextDeltaChunk(content="a"),
                DoneChunk(message_id="msg_1", conversation_id="conv_1"),
            ]
        )
    )
    assert len(frames) == 2
    assert frames[0].startswith("event: chunk")
    assert frames[1].startswith("event: done")
