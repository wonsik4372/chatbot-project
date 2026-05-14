"""질문과 관련 있는 문서 청크를 찾는 파일입니다.

검색은 두 단계로 진행합니다.
1. 키워드 검색: 교수명, 이메일, 전화번호처럼 정확한 문자열을 찾습니다.
2. 벡터 검색: 키워드로 못 찾으면 의미가 비슷한 문서를 찾습니다.
"""

from typing import Any
import unicodedata

import httpx
import ollama

from app.chroma_store import get_all_documents, query_by_embedding
from app.ollama_service import embed_text


def normalize_text(text: str) -> str:
    """한글 자모 분리 문제를 줄이고 대소문자 차이를 없앱니다."""

    return unicodedata.normalize("NFC", text).lower()


def keyword_search(question: str, top_k: int) -> list[dict[str, Any]]:
    """질문 단어가 그대로 들어 있는 문서 청크를 찾습니다."""

    query = normalize_text(question.strip())
    if not query:
        return []

    # 짧은 질문은 전체 문장을 하나의 검색어로 쓰고,
    # 긴 질문은 2글자 이상 단어도 함께 확인합니다.
    query_terms = {query}
    query_terms.update(term for term in query.split() if len(term) >= 2)

    result = get_all_documents()
    documents = result.get("documents", [])
    metadatas = result.get("metadatas", [])

    matches = []

    for document, metadata in zip(documents, metadatas):
        source = str(metadata.get("source", ""))
        searchable_text = normalize_text(f"{source}\n{document}")

        score = 0
        for term in query_terms:
            if term in searchable_text:
                # 전체 질문이 그대로 들어 있으면 가장 강한 근거로 봅니다.
                score += 10 if term == query else 3

        if score == 0:
            continue

        matches.append(
            {
                "text": document,
                "metadata": metadata,
                "distance": 0.0,
                "keyword_score": score,
            }
        )

    matches.sort(key=lambda item: item["keyword_score"], reverse=True)
    return matches[:top_k]


def vector_search(question: str, top_k: int) -> list[dict[str, Any]]:
    """Ollama 임베딩으로 질문과 의미가 가까운 문서 청크를 찾습니다."""

    try:
        question_embedding = embed_text(question)
    except (httpx.HTTPError, ollama.ResponseError):
        # Ollama가 꺼져 있으면 벡터 검색을 할 수 없으므로 빈 결과를 반환합니다.
        return []

    result = query_by_embedding(question_embedding=question_embedding, top_k=top_k)

    documents = result.get("documents", [[]])[0]
    metadatas = result.get("metadatas", [[]])[0]
    distances = result.get("distances", [[]])[0]

    found_docs = []
    for document, metadata, distance in zip(documents, metadatas, distances):
        found_docs.append(
            {
                "text": document,
                "metadata": metadata,
                "distance": distance,
            }
        )

    return found_docs


def search_documents(question: str, top_k: int) -> list[dict[str, Any]]:
    """질문과 관련 있는 문서 청크를 찾습니다."""

    keyword_results = keyword_search(question=question, top_k=top_k)

    # 정확한 문자열이 발견되면 벡터 검색 결과를 섞지 않습니다.
    # 이름 검색에서 엉뚱한 교수님 정보가 뒤에 붙는 문제를 막기 위해서입니다.
    if keyword_results:
        return keyword_results

    return vector_search(question=question, top_k=top_k)
