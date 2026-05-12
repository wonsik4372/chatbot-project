/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cse.deu.campusbot.api;

import com.cse.deu.campusbot.model.ChatRequest;
import com.cse.deu.campusbot.model.ChatResponse;
import com.cse.deu.campusbot.service.RagChatService;
import io.javalin.http.Context;

/**
 * HTTP 요청 처리 
 * @author wonsik
 */
public class ChatHandler {
    private final RagChatService chatService;

    public ChatHandler(RagChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * POST /api/chat 요청이 들어오면 실행되는 메서드
     */
    public void handleChat(Context ctx) {
        try {
            // 프론트엔드에서 보낸 JSON을 ChatRequest 레코드 객체로 자동 파싱
            ChatRequest request = ctx.bodyAsClass(ChatRequest.class);
            
            // 서비스 로직에 질문을 넘겨 답변 생성
            String answer = chatService.askQuestion(request.query());
            
            // 답변을 ChatResponse 객체로 포장해서 JSON 형태로 응답 반환
            ctx.json(new ChatResponse(answer));
            
        } catch (IllegalArgumentException e) {
            // 빈 질문 같은 잘못된 요청이 왔을 때 400 에러 처리
            ctx.status(400).json(new ChatResponse("잘못된 요청입니다: " + e.getMessage()));
        } catch (Exception e) {
            // 서버 내부 에러가 터졌을 때 500 에러 처리
            ctx.status(500).json(new ChatResponse("서버 내부 오류가 발생했습니다."));
        }
    }
}
