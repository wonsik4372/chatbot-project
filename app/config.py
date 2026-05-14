"""프로젝트 전체에서 사용하는 설정값을 모아둔 파일입니다.

초보자가 유지보수하기 쉽도록 모델 이름, 폴더 위치, 청크 크기 같은 값을
코드 곳곳에 직접 쓰지 않고 이 파일에서 한 번에 관리합니다.
"""

import os
from pathlib import Path

from dotenv import load_dotenv


# .env 파일이 있으면 환경변수를 읽어옵니다.
# .env 파일이 없어도 아래 기본값으로 실행할 수 있습니다.
load_dotenv()


# 프로젝트의 최상위 폴더입니다.
# app/config.py 기준으로 부모의 부모 폴더가 프로젝트 루트입니다.
BASE_DIR = Path(__file__).resolve().parent.parent


# Ollama 서버 주소입니다.
OLLAMA_HOST = os.getenv("OLLAMA_HOST", "http://localhost:11434")

# 답변 생성에 사용할 Ollama 모델입니다.
LLM_MODEL = os.getenv("LLM_MODEL", "gemma3:4b")

# 임베딩에 사용할 Ollama 모델입니다.
EMBEDDING_MODEL = os.getenv("EMBEDDING_MODEL", "nomic-embed-text")


# ChromaDB 저장 폴더입니다.
CHROMA_DB_DIR = Path(os.getenv("CHROMA_DB_DIR", BASE_DIR / "chroma_db"))

# TXT 문서가 들어 있는 폴더입니다.
TXT_DATA_DIR = Path(os.getenv("TXT_DATA_DIR", BASE_DIR / "data" / "txt"))

# PDF 문서가 들어 있는 폴더입니다.
PDF_DATA_DIR = Path(os.getenv("PDF_DATA_DIR", BASE_DIR / "data" / "pdf"))


# ChromaDB 안에서 사용할 컬렉션 이름입니다.
# PDF, 웹페이지를 추가하는 다음 단계에서도 같은 컬렉션에 source_type을 구분해 넣을 수 있습니다.
COLLECTION_NAME = "deu_cse_documents"


# TXT 파일 전용 청크 크기입니다.
TXT_CHUNK_SIZE = int(os.getenv("TXT_CHUNK_SIZE", "700"))
TXT_CHUNK_OVERLAP = int(os.getenv("TXT_CHUNK_OVERLAP", "100"))

# PDF 파일 전용 청크 크기입니다.
# PDF는 한 페이지 안에 많은 정보가 들어갈 수 있어서 TXT보다 조금 크게 잡았습니다.
PDF_CHUNK_SIZE = int(os.getenv("PDF_CHUNK_SIZE", "1000"))
PDF_CHUNK_OVERLAP = int(os.getenv("PDF_CHUNK_OVERLAP", "150"))


# 질문과 관련 있는 문서 조각을 몇 개 가져올지 정합니다.
TOP_K = int(os.getenv("TOP_K", "4"))
