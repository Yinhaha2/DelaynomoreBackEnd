import asyncio
import re
from collections.abc import AsyncIterator

from app.agents.base import AgentContext, AgentHandler
from app.config import settings
from app.streaming.chunks import ContentChunk, TextDeltaChunk


class StubAgentHandler(AgentHandler):
    """占位 Agent：按接口文档示例格式流式返回文字，便于前后端联调。"""

    async def stream(self, context: AgentContext) -> AsyncIterator[ContentChunk]:
        segments = self._build_segments(context.message)
        for segment in segments:
            for piece in self._split_text(segment, settings.text_chunk_max_chars):
                yield TextDeltaChunk(content=piece)
                await asyncio.sleep(0.05)

    def _build_segments(self, message: str) -> list[str]:
        if "芙莉莲" in message or "葬送" in message:
            return [
                "好的，",
                "正在为你检索",
                "《葬送的芙莉莲》相关资源...\n\n",
                "已找到 3 个可用来源。",
            ]

        url_match = re.search(r"https?://[^\s]+", message)
        if url_match:
            return [
                "正在",
                "分析",
                f"目标页面 {url_match.group()} ...\n\n",
                "页面结构已解析，后续将接入爬虫 Agent 提取资源链接。",
            ]

        return [
            "收到你的消息：",
            message[: settings.text_chunk_max_chars],
            "\n\n",
            "Agent Crawler 后端已就绪，等待接入真实 Agent / 爬虫服务。",
        ]

    def _split_text(self, text: str, max_chars: int) -> list[str]:
        if len(text) <= max_chars:
            return [text]

        pieces: list[str] = []
        start = 0
        while start < len(text):
            end = min(start + max_chars, len(text))
            if end < len(text):
                while end > start and not self._is_safe_cut(text, end):
                    end -= 1
                if end == start:
                    end = min(start + max_chars, len(text))
            pieces.append(text[start:end])
            start = end
        return pieces

    @staticmethod
    def _is_safe_cut(text: str, index: int) -> bool:
        return (ord(text[index - 1]) & 0xC0) != 0x80
