package com.dongeui.rag.llm;

import dev.langchain4j.model.chat.ChatLanguageModel;

public class KnowledgeCompiler {
    private final ChatLanguageModel chatModel;

    public KnowledgeCompiler(ChatLanguageModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * [🎯 변경 핵심] 파일 간의 기복을 제어하기 위해 고강도의 구조 스키마를 프롬프트에 정적으로 강제합니다.
     */
    public String compileTimetable(String preProcessedStream, String sourceName) {
        System.out.println("✨ LLM 스키마 제약 기반 마크다운 지식 컴파일 중 -> " + sourceName);
        
        String prompt = String.format(
            "너는 입력된 1차원 시간표 스트림 데이터를 '구조 유실 없이' RAG 검색 최적화 마크다운 규격 문서로 렌더링하는 완벽한 컴파일러 에이전트야.\n" +
            "어떤 상황에서도 지정된 포맷 규칙과 제목 외의 사족(예: 알겠습니다, 요약하자면 등)을 출력하는 것을 엄격히 금지한다.\n\n" +

            "📘 [컴퓨터소프트웨어공학과 정규 교과목명 복원 레퍼런스]\n" +
            "- 공학설계입문, Python프로그래밍, 컴퓨터비전응용, 그래픽스프로그래밍, 캡스톤디자인II, 지도교수멘토링VII, 기초수학, 프로젝트와기업가정신, 디지털신호처리특론, 콜라보인성의실천, 소프트웨어공학, 정보보호론, 데이터베이스이론, 데이터구조, 컴퓨터네트워크, 교과교육론(정보.컴퓨터), 컴퓨터개론, AI시대의사고와표현, 확률및통계, 멀티미디어시스템, ICT기반의창조경제\n\n" +

            "⚠️ [단어 완전화 및 가공 규칙]\n" +
            "1. 잘린 교과목명을 완전한 형태로 무조건 복원할 것:\n" +
            "   - '소프트웨어공학(키' 또는 '소프트웨어공학(키스' -> 소프트웨어공학(키스톤디자인)\n" +
            "   - 'AI시대의사고와표' -> AI시대의사고와표현\n" +
            "   - '교과교육론(정보.' -> 교과교육론(정보.컴퓨터)\n" +
            "   - '그래픽스프로그래' -> 그래픽스프로그래밍\n" +
            "   - '프로젝트와기업가' -> 프로젝트와기업가정신\n" +
            "2. 포맷 치환: '(학)'은 '(학부)'로, '(대)'는 '(대학원)'으로 일괄 변경한다.\n" +
            "3. 누락 금지: 입력 데이터에 명시된 요일별 수업 행은 단 하나도 생략되거나 중간에 짤려서는 안 되며, 1교시부터 순서대로 모두 나열하라.\n\n" +

            "🧱 [출력 마크다운 스키마 - 필수 준수]\n" +
            "# 📚 [TARGET METADATA에 기재된 학기 및 강의실 정보를 그대로 타이틀로 생성]\n" +
            "*(예: # 📚 2026학년도 1학기 시간표 - 정보공학관 914 강의실)*\n\n" +
            
            "### 월요일\n" +
            "* **N교시 (시작시간 - 종료시간):** 복원된교과목명 (학부/대학원구분) 담당교수이름\n" +
            "*(해당 요일에 수업이 없다면: '- 해당 요일에는 지정된 강의가 없습니다.' 한 줄만 출력)*\n\n" +
            
            "### 화요일\n" +
            "...\n" +
            "### 토요일까지 동일한 포맷으로 반복 빌드하라.\n\n" +

            "[입력 전처리 데이터 및 메타데이터]\n" +
            "%s\n\n" +
            "규격화된 최종 마크다운 결과물 >",
            preProcessedStream
        );

        return executeQuery(prompt, preProcessedStream);
    }

    /**
     * 2. 웹 크롤링 데이터 전용 컴파일러 (검색 최적화 태그 주입형)
     */
    public String compileWebContent(String rawContent, String sourceName) {
        System.out.println("✨ LLM이 [" + sourceName + "] 웹 크롤링 데이터를 RAG 최적화 마크다운으로 컴파일 중...");
        
        String prompt = String.format(
            "너는 학과 홈페이지에서 크롤링된 로우 텍스트를 RAG 검색에 완벽하게 적합한 구조화된 지식 문서로 정제하는 에이전트야.\n" +
            "인사말이나 사족 없이 오직 정제된 마크다운 결과물만 곧바로 출력해줘.\n\n" +
            
            "🧱 [작성 규칙]\n" +
            "1. 문서 최상단에 이 문서의 주제와 관련된 '검색용 핵심 키워드 태그'를 대량으로 배치해라.\n" +
            "   (예: 만약 교수 소개 페이지라면 #교수명단 #교수진 #교수님 #담당교수 #컴소교수 #교수소개 등을 무조건 포함)\n" +
            "2. 내용을 요약하여 생략하지 말고, 인물이나 항목이 등장하면 이름, 연구실, 연락처를 불릿 포인트로 모두 나열해라.\n\n" +
            
            "🧱 [출력 포맷 스키마]\n" +
            "## 🌐 학과 홈페이지 지식 베이스: [페이지 주제 명칭]\n" +
            "* **검색 태그:** #교수명단 #교수진 #전체교수 #교수소개 #연락처 #연구실 #컴퓨터소프트웨어공학과\n" +
            "* **문서 유형:** 학과 구성원 및 안내 정보 문서\n\n" +
            "### 📌 [주요 내용 및 리스트]\n" +
            "[입력 데이터의 내용을 기반으로 가독성 있게 마크다운화]\n\n" +
            "--- \n\n" +
            "[입력 데이터]\n" +
            "%s\n\n" +
            "마크다운 결과 >",
            rawContent
        );

        return executeQuery(prompt, rawContent);
    }

    private String executeQuery(String prompt, String fallback) {
        try {
            return this.chatModel.generate(prompt);
        } catch (Exception e) {
            System.err.println("⚠️ LLM 컴파일 프롬프트 전송 실패로 폴백 처리합니다: " + e.getMessage());
            return fallback;
        }
    }
}