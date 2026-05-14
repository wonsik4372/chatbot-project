"""RAG 답변 생성 로직을 담은 파일입니다.

RAG는 Retrieval Augmented Generation의 줄임말입니다.
간단히 말하면, 먼저 관련 문서를 검색하고 그 문서를 근거로 답변을 생성하는 방식입니다.
"""

from typing import Any

from app.config import TOP_K
from app.ollama_service import generate_answer
from app.retriever import search_documents


SYSTEM_PROMPT = """당신은 동의대학교 컴퓨터소프트웨어공학전공 안내 챗봇입니다.

반드시 아래 규칙을 지키세요.
1. 제공된 참고 자료 안에서만 답변하세요.
2. 참고 자료에 없는 내용은 추측하지 마세요.
3. 질문과 참고 자료의 표현이 완전히 같지 않아도, 같은 의미의 내용이 참고 자료에 있으면 답변하세요.
4. 참고 자료 일부에만 답이 있으면 확인 가능한 범위까지만 답변하세요.
5. 자료에서 확인할 수 없으면 정확히 "제공된 자료에서 확인할 수 없습니다"라고 답변하세요.
6. 답변은 한국어로 작성하세요.
7. 답변 마지막에 참고한 출처를 간단히 언급하세요.
"""


def build_context(documents: list[dict[str, Any]]) -> str:
    """검색된 문서 청크를 LLM에게 전달하기 좋은 문자열로 합칩니다."""

    context_parts = []

    for index, doc in enumerate(documents, start=1):
        metadata = doc["metadata"]
        source = metadata.get("source", "알 수 없는 출처")
        chunk_index = metadata.get("chunk_index", "알 수 없는 청크")
        text = doc["text"]

        context_parts.append(
            f"[문서 {index}]\n"
            f"출처: {source}\n"
            f"청크 번호: {chunk_index}\n"
            f"내용:\n{text}"
        )

    return "\n\n".join(context_parts)


def make_sources(documents: list[dict[str, Any]]) -> list[dict[str, str | int]]:
    """API 응답에 포함할 출처 목록을 만듭니다."""

    sources = []
    seen = set()

    for doc in documents:
        metadata = doc["metadata"]
        source = str(metadata.get("source", "알 수 없는 출처"))
        chunk_index = int(metadata.get("chunk_index", 0))
        key = (source, chunk_index)

        # 같은 출처와 청크가 중복 표시되지 않도록 처리합니다.
        if key in seen:
            continue

        seen.add(key)
        sources.append(
            {
                "source": source,
                "chunk_index": chunk_index,
            }
        )

    return sources


def answer_question(question: str) -> dict[str, Any]:
    """질문을 받아 RAG 방식으로 답변을 생성합니다."""

    documents = search_documents(question=question, top_k=TOP_K)

    if not documents:
        return {
            "answer": "제공된 자료에서 확인할 수 없습니다",
            "sources": [],
        }

    context = build_context(documents)

    user_prompt = f"""아래 참고 자료를 사용해서 질문에 답변하세요.

[참고 자료]
{context}

[질문]
{question}
"""

    answer = generate_answer(system_prompt=SYSTEM_PROMPT, user_prompt=user_prompt)

    return {
        "answer": answer,
        "sources": make_sources(documents),
    }
