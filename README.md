# 동의대학교 컴퓨터소프트웨어공학전공 안내 RAG 챗봇 - 1단계

이 프로젝트는 `TXT` 파일만 대상으로 동작하는 최소 RAG 챗봇입니다.

- 웹 서버: FastAPI
- LLM: Ollama `gemma3:4b`
- 임베딩: Ollama `nomic-embed-text`
- 벡터DB: ChromaDB
- 문서 소스: `data/txt` 폴더의 `.txt` 파일

## 1. 프로젝트 폴더 구조

```text
project_chatbot/
├── app/
│   ├── __init__.py
│   ├── config.py
│   ├── main.py
│   ├── rag.py
│   └── vector_store.py
├── data/
│   └── txt/
│       └── sample.txt
├── scripts/
│   └── ingest_txt.py
├── .env.example
├── requirements.txt
└── README.md
```

## 2. 설치 명령어

### 2-1. Python 가상환경 만들기

```bash
python3 -m venv .venv
source .venv/bin/activate
```

### 2-2. Python 패키지 설치

```bash
python -m pip install --upgrade pip
python -m pip install -r requirements.txt
```

`pip install -r requirements.txt`에서 오류가 나면 대부분 가상환경이 켜지지 않았거나,
컴퓨터에 설치된 `pip` 경로가 깨진 경우입니다. 이 프로젝트에서는 항상 `python -m pip` 형태를 권장합니다.

가상환경이 켜졌는지 확인하려면 아래 명령을 실행합니다.

```bash
which python
```

결과에 `.venv`가 들어 있어야 합니다.

예:

```text
/Users/.../project_chatbot/.venv/bin/python
```

만약 Python 3.14에서 ChromaDB 설치 오류가 나면 Python 3.11 또는 3.12 가상환경 사용을 권장합니다.

### 2-3. Ollama 모델 준비

Ollama가 설치되어 있고 실행 중이어야 합니다.

```bash
ollama pull gemma3:4b
ollama pull nomic-embed-text
```

## 3. 실행 방법

### 3-1. TXT 문서 넣기

안내 자료 `.txt` 파일을 아래 폴더에 넣습니다.

```text
data/txt/
```

예시 파일 `data/txt/sample.txt`가 이미 들어 있습니다.

### 3-2. 문서 색인하기

```bash
python scripts/ingest_txt.py
```

성공하면 `chroma_db` 폴더가 생성됩니다.

### 3-3. FastAPI 서버 실행

```bash
uvicorn app.main:app --reload
```

서버 주소:

```text
http://127.0.0.1:8000
```

API 문서 화면:

```text
http://127.0.0.1:8000/docs
```

## 4. 테스트 방법

### 방법 A: 브라우저에서 테스트

1. `http://127.0.0.1:8000/docs` 접속
2. `POST /chat` 클릭
3. `Try it out` 클릭
4. 아래처럼 입력

```json
{
  "question": "컴퓨터소프트웨어공학전공은 무엇을 배우나요?"
}
```

### 방법 B: 터미널에서 테스트

```bash
curl -X POST "http://127.0.0.1:8000/chat" \
  -H "Content-Type: application/json" \
  -d '{"question":"컴퓨터소프트웨어공학전공은 무엇을 배우나요?"}'
```

## 5. 동작 순서

1. `data/txt` 폴더에 TXT 문서를 넣습니다.
2. `scripts/ingest_txt.py`를 실행합니다.
3. TXT 파일을 읽고 작은 청크로 나눕니다.
4. 각 청크를 Ollama `nomic-embed-text`로 임베딩합니다.
5. 임베딩과 원문 청크를 ChromaDB에 저장합니다.
6. 사용자가 `/chat` API로 질문합니다.
7. 질문도 같은 임베딩 모델로 임베딩합니다.
8. ChromaDB에서 질문과 가까운 문서 청크를 찾습니다.
9. 찾은 청크를 근거 자료로 `gemma3:4b`에 전달합니다.
10. 모델은 제공된 자료 안에서만 답변하고, 참고 출처를 함께 반환합니다.

## 6. 중요한 규칙

자료에 없는 내용은 추측하지 않고 아래 문장으로 답하도록 프롬프트에 명시했습니다.

```text
제공된 자료에서 확인할 수 없습니다
```

응답에는 항상 `sources` 필드가 포함됩니다.
