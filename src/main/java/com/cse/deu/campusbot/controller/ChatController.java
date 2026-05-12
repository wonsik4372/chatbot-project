/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cse.deu.campusbot.controller;

import com.cse.deu.campusbot.service.RagService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*") // 테스트 시 CORS 허용
public class ChatController {

    private final RagService ragService;

    public ChatController(RagService ragService) {
        this.ragService = ragService;
    }

    @PostMapping("/chat")
    public Map<String, String> chat(@RequestBody Map<String, String> request) {
        String userQuestion = request.get("question");
        
        // 민종님의 RAG 엔진 서비스 호출
        String answer = ragService.askQuestion(userQuestion);

        return Map.of("answer", answer); // Java 9+ 편리한 Map 생성
    }
}