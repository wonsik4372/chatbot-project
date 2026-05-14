package com.mycompany.chatbot.rag.project;

import dev.langchain4j.data.segment.TextSegment;

import java.io.IOException;
import java.util.List;
import java.util.Scanner;

public class RAGApplication {

    public static void main(String[] args) {
        try {
            System.setOut(new java.io.PrintStream(System.out, true, "UTF-8"));
        } catch (java.io.UnsupportedEncodingException e) {
        }

        System.out.println("=================================================");
        System.out.println("    LangChain4j RAG 비서 (Gemma 기반)");
        System.out.println("=================================================\n");

        Scanner scanner = new Scanner(System.in);

        TimetableStore timetableStore = new TimetableStore();
        DocumentLoader loader = new DocumentLoader(timetableStore);

        TextChunker chunker = new TextChunker();
        VectorStoreManager vectorStore = new VectorStoreManager();

        int totalChunks = 0;

        // 1단계
        System.out.println("  [1단계] 문서 폴더 경로를 입력하세요.");
        System.out.println("     (TXT / PDF / XLSX 파일이 있는 디렉토리)");
        System.out.print("  경로 > ");

        String dirPath = scanner.nextLine().trim();

        if (!dirPath.isEmpty()) {
            try {
                List<DocumentLoader.RawDocument> docs
                        = loader.loadDirectory(dirPath);

                if (docs.isEmpty()) {
                    System.out.println("  지원 파일(txt/pdf/xlsx)이 없습니다.");
                } else {
                    List<TextSegment> segments
                            = chunker.chunkAll(docs);

                    vectorStore.addSegments(segments);
                    totalChunks += segments.size();
                }

            } catch (IOException e) {
                System.err.println("디렉토리 로드 오류: " + e.getMessage());
            }
        }

        // 2단계
        System.out.println("\n[2단계] 학과 웹 문서 자동 로딩 중...");

        String[] autoUrls = {
            // 학과소개
            "https://se.deu.ac.kr/se/sub01_01.do",
            "https://se.deu.ac.kr/se/sub01_02.do",
            "https://se.deu.ac.kr/se/sub01_03.do",
            "https://se.deu.ac.kr/se/sub01_04.do",
            "https://se.deu.ac.kr/se/sub01_05.do",
            // 교수소개
            "https://se.deu.ac.kr/se/sub02.do",
            // 교육과정
            "https://se.deu.ac.kr/se/sub03_01.do",
            "https://se.deu.ac.kr/se/sub03_02.do",
            "https://se.deu.ac.kr/se/sub03_03.do",
            // 취업
            "https://se.deu.ac.kr/se/sub05_03.do",
            "https://se.deu.ac.kr/se/sub05_04.do",
            // 이종민 교수님 개인 사이트
            "https://compnet.deu.ac.kr/personal_intro.html",
            "https://compnet.deu.ac.kr/research_intro.html"
        };

        for (String url : autoUrls) {
            try {
                System.out.println("  [URL 로드] " + url);

                DocumentLoader.RawDocument urlDoc
                        = loader.loadUrl(url);

                List<TextSegment> segments
                        = chunker.chunk(urlDoc.source(), urlDoc.text());

                vectorStore.addSegments(segments);
                totalChunks += segments.size();

            } catch (IOException e) {
                System.err.println("URL 로드 실패: " + url);
            }
        }

        if (totalChunks == 0) {
            System.out.println("인덱싱된 문서가 없습니다.");
            scanner.close();
            return;
        }

        System.out.println("\n인덱싱 완료: " + totalChunks + "개 청크");

        RAGAssistant assistant
                = new RAGAssistant(vectorStore, timetableStore);

        System.out.println("\n=================================================");
        System.out.println("질의응답 시작 (/bye 종료)");
        System.out.println("=================================================");

        while (true) {
            System.out.print("\n질문 > ");

            String input = scanner.nextLine().trim();

            if (input.equalsIgnoreCase("/bye")
                    || input.equalsIgnoreCase("/quit")) {
                break;
            }

            if (input.isEmpty()) {
                continue;
            }

            System.out.println("\n답변 생성 중...\n");

            try {
                String answer = assistant.answer(input);
                System.out.println("답변 > " + answer);

            } catch (Exception e) {
                System.err.println("오류: " + e.getMessage());
            }
        }

        scanner.close();
    }
}
