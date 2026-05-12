/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cse.deu.campusbot.util;

// lanchain4j 사용 
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * In-Memory Embedding 싱글톤 관리
 * @author wonsik
 */
public class EmbeddingStoreManager {
    // 싱글톤 인스턴스
    private static InMemoryEmbeddingStore<TextSegment> store;
    
    // private 생성자: 외부에서 객체 생성하는 것을 보호
    private EmbeddingStoreManager() {}
    
    public static synchronized InMemoryEmbeddingStore<TextSegment> getInstance() {
        // store가 아직 메모리에 로드되지 않은 최초의 호출일 경우 실행
        if (store == null) {
            String storePath = ConfigReader.getProperty("rag.store.path", "data/rag-store.json");
            
            if (Files.exists(Paths.get(storePath))) {
                try {
                    // 파일이 있는 경우, 기존 파일 가져오기
                    store = InMemoryEmbeddingStore.fromFile(storePath);
                    System.out.println("기존 임베딩 store file 불러오기 성공: " + storePath);
                }
                catch (Exception e) {
                    // 파일이 없는 경우, 새 파일 생성
                    System.out.println("저장된 임베딩 store가 없습니다. 새로운 store를 생성합니다.");
                    store = new InMemoryEmbeddingStore<>();
                }
            }
        }
        // 이미 생성 된 경우, 기존 스토어 반환
        return store;
    }
    
}
