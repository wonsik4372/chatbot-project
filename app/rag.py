"""RAG 답변 생성 로직을 담은 파일입니다.

RAG는 Retrieval Augmented Generation의 줄임말입니다.
간단히 말하면, 먼저 관련 문서를 검색하고 그 문서를 근거로 답변을 생성하는 방식입니다.
"""

from typing import Any
import re

from app.config import TOP_K
from app.ollama_service import generate_answer
from app.retriever import is_schedule_question, search_documents


SYSTEM_PROMPT = """당신은 동의대학교 컴퓨터소프트웨어공학전공 안내 챗봇입니다.

반드시 아래 규칙을 지키세요.
1. 제공된 참고 자료 안에서만 답변하세요.
2. 참고 자료에 없는 내용은 추측하지 마세요.
3. 질문과 참고 자료의 표현이 완전히 같지 않아도, 같은 의미의 내용이 참고 자료에 있으면 답변하세요.
4. 참고 자료 일부에만 답이 있으면 확인 가능한 범위까지만 답변하세요.
5. 자료에서 확인할 수 없으면 정확히 "제공된 자료에서 확인할 수 없습니다"라고 답변하세요.
6. 답변은 한국어로 작성하세요.
7. 시간표를 답변할 때는 교시를 반드시 오름차순으로 정렬하세요.
8. 답변 마지막에 참고한 출처를 간단히 언급하세요.
"""


def build_context(documents: list[dict[str, Any]]) -> str:
    """검색된 문서 청크를 LLM에게 전달하기 좋은 문자열로 합칩니다."""

    context_parts = []

    for index, doc in enumerate(documents, start=1):
        metadata = doc["metadata"]
        source = metadata.get("source", "알 수 없는 출처")
        chunk_index = metadata.get("chunk_index", "알 수 없는 청크")
        page = metadata.get("page")
        text = doc["text"]

        page_text = f"페이지: {page}\n" if page is not None else ""

        context_parts.append(
            f"[문서 {index}]\n"
            f"출처: {source}\n"
            f"{page_text}"
            f"청크 번호: {chunk_index}\n"
            f"내용:\n{text}"
        )

    return "\n\n".join(context_parts)


def make_sources(documents: list[dict[str, Any]]) -> list[dict[str, str | int | None]]:
    """API 응답에 포함할 출처 목록을 만듭니다."""

    sources = []
    seen = set()

    for doc in documents:
        metadata = doc["metadata"]
        source = str(metadata.get("source", "알 수 없는 출처"))
        chunk_index = int(metadata.get("chunk_index", 0))
        page = metadata.get("page")
        key = (source, page, chunk_index)

        # 같은 출처와 청크가 중복 표시되지 않도록 처리합니다.
        if key in seen:
            continue

        seen.add(key)
        sources.append(
            {
                "source": source,
                "page": page,
                "chunk_index": chunk_index,
            }
        )

    return sources


def parse_schedule_entry(text: str) -> dict[str, str | int] | None:
    """시간표 청크 한 줄에서 요일, 교시, 시간, 과목을 추출합니다."""

    match = re.search(
        r"(?P<day>[월화수목금토]요일)\s+"
        r"(?P<period>\d+)교시\s+"
        r"\((?P<time>[^)]+)\):\s+"
        r"(?P<subject>[^,.]+)",
        text,
    )

    if not match:
        return None

    return {
        "day": match.group("day"),
        "period": int(match.group("period")),
        "time": match.group("time"),
        "subject": match.group("subject").strip(),
    }


def build_schedule_answer(documents: list[dict[str, Any]]) -> str | None:
    """검색된 시간표 청크를 교시 오름차순 답변으로 변환합니다.

    시간표는 과목 누락이 치명적이므로 LLM이 목록을 재작성하게 두지 않고,
    서버 코드에서 직접 정렬된 답변을 만듭니다.
    """

    entries = []
    seen = set()

    for doc in documents:
        metadata = doc["metadata"]
        source = str(metadata.get("source", ""))

        if metadata.get("source_type") != "pdf" or "시간표" not in source:
            continue

        entry = parse_schedule_entry(doc["text"])
        if entry is None:
            continue

        key = (entry["day"], entry["period"], entry["time"], entry["subject"])
        if key in seen:
            continue

        seen.add(key)
        entries.append(entry)

    if not entries:
        return None

    entries.sort(key=lambda item: (str(item["day"]), int(item["period"])))

    lines = ["제공된 자료에서 확인할 수 있습니다. 해당 수업은 다음과 같습니다."]
    for entry in entries:
        lines.append(
            f"- {entry['period']}교시 ({entry['time']}): {entry['subject']}"
        )

    return "\n".join(lines)


def answer_question(question: str) -> dict[str, Any]:
    """질문을 받아 RAG 방식으로 답변을 생성합니다."""

    top_k = max(TOP_K, 10) if is_schedule_question(question) else TOP_K
    documents = search_documents(question=question, top_k=top_k)

    if not documents:
        return {
            "answer": "제공된 자료에서 확인할 수 없습니다",
            "sources": [],
        }

    if is_schedule_question(question):
        schedule_answer = build_schedule_answer(documents)
        if schedule_answer is not None:
            return {
                "answer": schedule_answer,
                "sources": make_sources(documents),
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
