/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cse.deu.campusbot.model;

/**
 * 프론트엔드에서 백엔드로 오는 데이터 매핑
 * @author wonsik
 */
public record ChatRequest (String query){
    public ChatRequest {
        if (query == null || query.trim().isEmpty()) {
            throw new IllegalArgumentException("ERROR: 질문(query)은 비어있을 수 없습니다.");
        }
    }
}
