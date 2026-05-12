package com.cse.deu.campusbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CampusbotApplication {

    public static void main(String[] args) {
        // 이 한 줄이 서버를 실행하고 모든 설정을 자동으로 처리합니다.
        SpringApplication.run(CampusbotApplication.class, args);
        
        System.out.println("======================================");
        System.out.println("   Campusbot 서버가 성공적으로 가동되었습니다!   ");
        System.out.println("   접속 주소: http://localhost:8080       ");
        System.out.println("======================================");
    }

}