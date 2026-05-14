"""질문과 관련 있는 문서 청크를 찾는 파일입니다.

검색은 두 단계로 진행합니다.
1. 키워드 검색: 교수명, 이메일, 전화번호처럼 정확한 문자열을 찾습니다.
2. 벡터 검색: 키워드로 못 찾으면 의미가 비슷한 문서를 찾습니다.
"""

from typing import Any
import re
import unicodedata

import httpx
import ollama

from app.chroma_store import get_all_documents, query_by_embedding
from app.ollama_service import embed_text


DAY_WORDS = ["월요일", "화요일", "수요일", "목요일", "금요일", "토요일"]
GENERIC_QUERY_TERMS = {
    "교수님",
    "수업",
    "있으신가",
    "있으신가?",
    "있으면",
    "언제언제",
    "하시는지도",
    "알려줘",
    "알려주세요",
}


def normalize_text(text: str) -> str:
    """한글 자모 분리 문제를 줄이고 대소문자 차이를 없앱니다."""

    return unicodedata.normalize("NFC", text).lower()


def get_requested_days(question: str) -> list[str]:
    """질문에 들어 있는 요일을 찾습니다."""

    normalized_question = normalize_text(question)
    return [day for day in DAY_WORDS if day in normalized_question]


def get_requested_rooms(question: str) -> list[str]:
    """질문에 들어 있는 강의실 호수를 찾습니다."""

    return re.findall(r"\d{3}호", question)


def get_requested_professors(question: str) -> list[str]:
    """질문에 들어 있는 교수명을 찾습니다."""

    return re.findall(r"([가-힣]{2,4})\s*교수님", question)


def is_schedule_question(question: str) -> bool:
    """질문이 시간표 관련 질문인지 확인합니다."""

    normalized_question = normalize_text(question)
    schedule_keywords = ["시간표", "교시", "언제", "요일", "강의실", "몇시", "몇 시"]

    return (
        bool(get_requested_days(question))
        or bool(get_requested_rooms(question))
        or any(keyword in normalized_question for keyword in schedule_keywords)
    )


def is_schedule_result(result: dict[str, Any]) -> bool:
    """검색 결과가 시간표 PDF에서 온 것인지 확인합니다."""

    metadata = result["metadata"]
    return metadata.get("source_type") == "pdf" and "시간표" in str(metadata.get("source", ""))


def extract_period(text: str) -> int:
    """시간표 문장에서 교시 숫자를 추출합니다."""

    match = re.search(r"(\d+)교시", text)
    if not match:
        return 999

    return int(match.group(1))


def get_schedule_dedupe_key(result: dict[str, Any]) -> tuple[str, int, str]:
    """시간표 중복 제거에 사용할 키를 만듭니다."""

    text = normalize_text(result["text"])
    day = next((day for day in DAY_WORDS if day in text), "")
    period = extract_period(text)

    # 같은 요일/교시/과목이면 교수님 시간표와 강의실 시간표 중 하나만 남깁니다.
    subject_match = re.search(r":\s*([^,.]+)", result["text"])
    subject = normalize_text(subject_match.group(1).strip()) if subject_match else ""

    return day, period, subject


def sort_and_dedupe_schedule_results(
    results: list[dict[str, Any]],
    schedule_only: bool,
) -> list[dict[str, Any]]:
    """시간표 검색 결과를 교시 오름차순으로 정렬하고 중복을 제거합니다."""

    schedule_results = [result for result in results if is_schedule_result(result)]
    other_results = [result for result in results if not is_schedule_result(result)]

    if not schedule_results:
        return results

    deduped_schedule_results = []
    seen = set()

    for result in sorted(schedule_results, key=lambda item: (extract_period(item["text"]), item["text"])):
        key = get_schedule_dedupe_key(result)
        if key in seen:
            continue

        seen.add(key)
        deduped_schedule_results.append(result)

    if schedule_only:
        return deduped_schedule_results

    return deduped_schedule_results + other_results


def keyword_search(question: str, top_k: int) -> list[dict[str, Any]]:
    """질문 단어가 그대로 들어 있는 문서 청크를 찾습니다."""

    query = normalize_text(question.strip())
    if not query:
        return []

    # 짧은 질문은 전체 문장을 하나의 검색어로 쓰고,
    # 긴 질문은 2글자 이상 단어도 함께 확인합니다.
    query_terms = {query}
    query_terms.update(term for term in query.split() if len(term) >= 2)
    query_terms = {term for term in query_terms if term not in GENERIC_QUERY_TERMS}

    result = get_all_documents()
    documents = result.get("documents", [])
    metadatas = result.get("metadatas", [])
    requested_days = get_requested_days(question)
    requested_rooms = get_requested_rooms(question)
    requested_professors = get_requested_professors(question)

    matches = []

    for document, metadata in zip(documents, metadatas):
        source = str(metadata.get("source", ""))
        searchable_text = normalize_text(f"{source}\n{document}")

        score = 0
        for term in query_terms:
            if term in searchable_text:
                # 전체 질문이 그대로 들어 있으면 가장 강한 근거로 봅니다.
                score += 10 if term == query else 3

        # 시간표 질문에 특정 요일이 들어 있으면 해당 요일 청크를 강하게 우선합니다.
        # 반대로 다른 요일 청크는 제외해 월요일 질문에 수요일 수업이 섞이는 것을 줄입니다.
        if requested_days and metadata.get("source_type") == "pdf" and "시간표" in source:
            has_requested_day = any(day in searchable_text for day in requested_days)

            if not has_requested_day:
                continue

            score += 20

        if requested_rooms and metadata.get("source_type") == "pdf" and "시간표" in source:
            if not any(room in searchable_text for room in requested_rooms):
                continue

            score += 10

        if requested_professors and metadata.get("source_type") == "pdf" and "시간표" in source:
            if not any(professor in searchable_text for professor in requested_professors):
                continue

            score += 10

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
    matches = sort_and_dedupe_schedule_results(
        results=matches,
        schedule_only=is_schedule_question(question),
    )
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
