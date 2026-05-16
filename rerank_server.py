from fastapi import FastAPI
from sentence_transformers import CrossEncoder
import uvicorn

app = FastAPI()

# 인증 요구 없는 안전한 고성능 모델 로드
print("Loading Reranker model... Please wait.")
model = CrossEncoder("BAAI/bge-reranker-large")
print("Model loaded successfully!")

@app.post("/rerank")
def rerank(request: dict): # Pydantic 클래스 대신 dict로 받아 데이터 미스매치 차단
    try:
        # 자바가 보낸 JSON에서 데이터 추출
        query = request.get("query", "")
        documents = request.get("documents", [])
        
        # 로그 출력 (자바가 진짜 뭘 보냈는지 눈으로 확인용)
        print(f"\n[수신] 질문: {query}")
        print(f"[수신] 문서 개수: {len(documents)}개")

        # 혹시 문서가 비어있거나 데이터가 제대로 안 왔을 때 방어 코드
        if not query or not documents:
            print("[경고] 질문이나 문서 리스트가 비어있습니다.")
            return {"results": []}
            
        # (질문, 문서) 쌍 매칭 및 스코어 연산
        pairs = [[query, doc] for doc in documents]
        scores = model.predict(pairs).tolist()
        
        # 스코어 높은 순 정렬
        results = [{"index": i, "score": score} for i, score in enumerate(scores)]
        results.sort(key=lambda x: x["score"], reverse=True)
        
        print(f"[성공] 리랭킹 완료! 최상위 인덱스: {results[0]['index'] if results else '없음'}")
        return {"results": results}

    except Exception as e:
        print(f"[에러 발생] 원인: {e}")
        return {"results": []}

if __name__ == "__main__":
    uvicorn.run(app, host="127.0.0.1", port=8000)