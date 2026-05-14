"""TXT와 PDF 파일을 RAG 색인용 청크 목록으로 변환하는 파일입니다.

이 파일은 파일을 읽고 청크로 나누는 일만 담당합니다.
ChromaDB 저장은 app/chroma_store.py에서 처리합니다.
"""

from pathlib import Path
from uuid import uuid4

from app.config import (
    BASE_DIR,
    PDF_CHUNK_OVERLAP,
    PDF_CHUNK_SIZE,
    PDF_DATA_DIR,
    TXT_CHUNK_OVERLAP,
    TXT_CHUNK_SIZE,
    TXT_DATA_DIR,
)
from app.pdf_loader import is_schedule_pdf, read_pdf_for_rag
from app.text_splitter import split_text


DocumentChunks = tuple[list[str], list[str], list[dict[str, str | int]]]


def read_txt_file(path: Path) -> str:
    """TXT 파일 하나를 읽어서 문자열로 반환합니다."""

    return path.read_text(encoding="utf-8")


def collect_txt_chunks() -> DocumentChunks:
    """data/txt 폴더의 모든 TXT 파일을 읽고 청크 목록을 만듭니다."""

    ids = []
    texts = []
    metadatas = []

    txt_files = sorted(TXT_DATA_DIR.glob("*.txt"))

    for file_path in txt_files:
        text = read_txt_file(file_path)
        chunks = split_text(
            text=text,
            chunk_size=TXT_CHUNK_SIZE,
            chunk_overlap=TXT_CHUNK_OVERLAP,
        )

        for chunk_index, chunk in enumerate(chunks):
            ids.append(str(uuid4()))
            texts.append(chunk)
            metadatas.append(
                {
                    "source": str(file_path.relative_to(BASE_DIR)),
                    "source_type": "txt",
                    "chunk_index": chunk_index,
                }
            )

    return ids, texts, metadatas


def split_pdf_page_for_index(file_path: Path, page_text: str) -> list[str]:
    """PDF 페이지 텍스트를 색인용 청크로 나눕니다."""

    if is_schedule_pdf(file_path):
        # 시간표 PDF는 한 줄이 하나의 수업 정보입니다.
        # 여러 요일을 한 청크에 묶으면 특정 요일 질문에 다른 요일 수업이 섞일 수 있습니다.
        return [line.strip() for line in page_text.splitlines() if line.strip()]

    return split_text(
        text=page_text,
        chunk_size=PDF_CHUNK_SIZE,
        chunk_overlap=PDF_CHUNK_OVERLAP,
    )


def collect_pdf_chunks() -> DocumentChunks:
    """data/pdf 폴더의 모든 PDF 파일을 읽고 청크 목록을 만듭니다."""

    ids = []
    texts = []
    metadatas = []

    pdf_files = sorted(PDF_DATA_DIR.glob("*.pdf"))

    for file_path in pdf_files:
        pages = read_pdf_for_rag(file_path)

        for page_number, page_text in pages:
            chunks = split_pdf_page_for_index(file_path=file_path, page_text=page_text)

            for chunk_index, chunk in enumerate(chunks):
                ids.append(str(uuid4()))
                texts.append(chunk)
                metadatas.append(
                    {
                        "source": str(file_path.relative_to(BASE_DIR)),
                        "source_type": "pdf",
                        "page": page_number,
                        "chunk_index": chunk_index,
                    }
                )

    return ids, texts, metadatas


def merge_document_chunks(*collections: DocumentChunks) -> DocumentChunks:
    """여러 문서 수집 결과를 하나로 합칩니다."""

    all_ids = []
    all_texts = []
    all_metadatas = []

    for ids, texts, metadatas in collections:
        all_ids.extend(ids)
        all_texts.extend(texts)
        all_metadatas.extend(metadatas)

    return all_ids, all_texts, all_metadatas
