/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.cse.deu.campusbot.util;

import java.io.InputStream;
import java.util.Properties;

/**
 * /src/main/resources/application.properties 파일의 설정 값을 읽어옴
 * @author wonsik
 */
public class ConfigReader {
    private static final Properties properties = new Properties();
    
    static {
        // application.properties 파일 찾아서 연결 
        try (InputStream input = ConfigReader.class.getClassLoader().getResourceAsStream("application.properties")){
            if(input != null){
                // = 기준으로 앞은 key 뒤는 value로 자동 분류
                properties.load(input);
                System.out.println("application.properties 파일 로드 완료 !!!");
            }
            else {
                System.out.println("파일을 찾을 수 없습니다...");
            }
        } 
        catch (Exception e) {
            System.err.println("Error: application.properties 파일 읽기 실패 - " + e.getMessage());
        }
        
    }
    
    /*
    * 문자열 설정값 가져오기
    * @param key 찾을 키
    * @param defaultValue 값이 없을 경우 사용할 기본 값 
    */
    public static String getProperty(String key, String defaultValue){
        return properties.getProperty(key, defaultValue);
    }
    
    /*
    * 숫자(정수) 설정값 가져오기
    */
    public static int getIntProperty(String key, int defaultValue){
        String value = properties.getProperty(key);
        try {
            return value != null ? Integer.parseInt(value) : defaultValue;
        }
        catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
