package com.parser.test;

import com.parser.service.CookieService;
import com.parser.util.HttpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class TestApi {
    private static final Logger logger = LoggerFactory.getLogger(TestApi.class);

    public static void main(String[] args) {
        try {
            logger.info("=== Testing Goofish API ===");

            // Тест 1: Проверка cookies
            logger.info("1. Getting cookies...");
            Map<String, String> cookies = CookieService.getCookiesForDomain("h5api.m.goofish.com");
            logger.info("Cookies: {}", cookies.keySet());

            // Тест 2: Простой запрос
            logger.info("2. Making test request...");
            String testUrl = "https://h5api.m.goofish.com/h5/mtop.taobao.idlemtopsearch.pc.search/1.0/?jsv=2.7.2&appKey=34839810&t=" +
                    System.currentTimeMillis() + "&sign=test&v=1.0&type=originaljson&data=%7B%22pageNumber%22%3A1%2C%22keyword%22%3A%22test%22%2C%22rowsPerPage%22%3A10%7D";

            String response = HttpUtils.sendGetRequest(testUrl,
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
                    true);

            logger.info("Response length: {} chars", response.length());
            logger.info("Response preview: {}", response.length() > 300 ?
                    response.substring(0, 300) + "..." : response);

            // Тест 3: Проверка структуры ответа
            if (response.contains("SUCCESS")) {
                logger.info("✅ API returned SUCCESS");
            } else {
                logger.warn("⚠️ API did not return SUCCESS");
            }

            if (response.contains("resultList")) {
                logger.info("✅ Response contains resultList");
            } else {
                logger.warn("⚠️ Response does not contain resultList");
            }

        } catch (Exception e) {
            logger.error("Test failed: {}", e.getMessage(), e);
        }
    }
}