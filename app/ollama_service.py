"""Ollama와 통신하는 기능을 모아둔 파일입니다.

이 파일은 Ollama 서버에 요청을 보내는 일만 담당합니다.
문서 검색이나 ChromaDB 저장 로직은 다른 파일에서 처리합니다.
"""

import ollama

from app.config import EMBEDDING_MODEL, LLM_MODEL, OLLAMA_HOST


def get_ollama_client() -> ollama.Client:
    """Ollama 서버와 통신하는 클라이언트를 만듭니다."""

    return ollama.Client(host=OLLAMA_HOST)


def embed_text(text: str) -> list[float]:
    """텍스트 하나를 Ollama 임베딩 모델로 벡터로 변환합니다."""

    client = get_ollama_client()
    response = client.embeddings(model=EMBEDDING_MODEL, prompt=text)
    return response["embedding"]


def generate_answer(system_prompt: str, user_prompt: str) -> str:
    """Ollama LLM에게 프롬프트를 보내고 답변 문자열을 반환합니다."""

    client = get_ollama_client()
    response = client.chat(
        model=LLM_MODEL,
        messages=[
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_prompt},
        ],
    )

    return response["message"]["content"].strip()
