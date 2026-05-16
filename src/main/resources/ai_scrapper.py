import requests
from bs4 import BeautifulSoup
import re
import os

def run_ai_cleaner(target_url, save_filename):
    print(f"\n[1/3] 웹페이지 데이터 읽는 중...")
    
    try:
        # 브라우저인 척 헤더 구성 (접근 차단 방지)
        headers = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"}
        response = requests.get(target_url, headers=headers, timeout=15)
        response.encoding = 'utf-8'
        
        soup = BeautifulSoup(response.text, 'html.parser')
        
        # 불필요한 스크립트 및 스타일 태그 제거
        for script in soup(["script", "style"]):
            script.decompose()
            
        # 날것의 전체 텍스트 추출 및 공백 압축
        raw_text = soup.get_text()
        clean_raw_text = re.sub(r'\s+', ' ', raw_text).strip()
        clean_raw_text = clean_raw_text[:4000] # LLM 토큰 제한 방지 안전장치

        print("[2/3] 로컬 AI(Ollama)가 마크다운 문서로 변환 중... (잠시만 기다려주세요)")
        
        system_prompt = (
            "너는 웹페이지 텍스트 정제 및 마크다운 변환 전문가야.\n"
            "입력되는 텍스트는 웹사이트를 통째로 긁어온 날것의 데이터야. 여기에는 메뉴, 카테고리 리스트, 로그인 버튼, 광고, 푸터 등의 잡음이 섞여있어.\n"
            "너의 임무는 이 잡음들을 완벽하게 무시하고, 오직 '핵심 본문 정보'만 추출하는 거야.\n"
            "추출한 본문은 제목(##), 강조(**), 목록(-) 등을 적절히 활용하여 가독성이 뛰어난 마크다운(Markdown) 양식으로 재조립해줘.\n"
            "인사말이나 부연설명 없이 오직 정제된 마크다운 결과물만 반환해."
        )

        ollama_url = "http://localhost:11434/api/generate"
        ollama_payload = {
            "model": "gemma4:latest", 
            "prompt": f"{system_prompt}\n\n[원본 웹 텍스트]:\n{clean_raw_text}",
            "stream": False
        }

        ollama_response = requests.post(ollama_url, json=ollama_payload, timeout=300)
        
        if ollama_response.status_code == 200:
            ai_markdown = ollama_response.json().get("response", "")
            
            print(f"[3/3] 변환 완료! 파일 저장 중...")
            
            # 저장할 폴더 지정 (현재 폴더 안의 rag_docs 폴더)
            save_dir = "./crawl_docs" 
            if not os.path.exists(save_dir):
                os.makedirs(save_dir)
                
            # 사용자가 확장자를 안 붙였거나 .txt로 붙였어도 강제로 .md로 통일
            base_name = os.path.splitext(save_filename)[0]
            final_filename = f"{base_name}.md"
                
            final_path = os.path.join(save_dir, final_filename)
            with open(final_path, "w", encoding="utf-8") as f:
                f.write(ai_markdown)
                
            print(f"🎉 성공적으로 저장되었습니다! -> {os.path.abspath(final_path)}\n")
            return True
        else:
            print(f"❌ Ollama 응답 실패 (오류 코드: {ollama_response.status_code})\n")
            return False
            
    except Exception as e:
        print(f"❌ 오류 발생: {e}\n")
        return False

if __name__ == "__main__":
    print("==================================================")
    print("🤖 AI 웹페이지 본문 마크다운(.md) 추출 프로그램")
    print("종료하려면 주소 입력창에 'exit'를 입력하세요.")
    print("==================================================")
    
    while True:
        # 1. 터미널에서 주소 입력받기
        url = input("🔗 가공할 웹페이지 주소(URL)를 붙여넣으세요:\n> ").strip()
        
        if url.lower() == 'exit':
            print("프로그램을 종료합니다.")
            break
            
        if not url:
            print("🚨 주소가 입력되지 않았습니다.\n")
            continue
            
        if not url.startswith("http://") and not url.startswith("https://"):
            print("🚨 올바른 주소 형식이 아닙니다. (http:// 또는 https:// 필요)\n")
            continue

        # 2. 터미널에서 저장할 파일 이름 입력받기
        filename = input("💾 저장할 파일 이름을 적으세요 (확장자 생략 가능):\n> ").strip()
        
        if not filename:
            print("🚨 파일 이름이 비어있어 'output_document'로 지정합니다.")
            filename = "output_document"

        # 3. 크롤링 및 변환 실행
        run_ai_cleaner(url, filename)
        print("-" * 50)