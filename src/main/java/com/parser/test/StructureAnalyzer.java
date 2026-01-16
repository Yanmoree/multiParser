package com.parser.test;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.util.Map;

public class StructureAnalyzer {
    private static final Logger logger = LoggerFactory.getLogger(StructureAnalyzer.class);

    public static void main(String[] args) {
        try {
            logger.info("=== Анализ структуры ответа API ===");

            // Используем cookies из вашего лога
            Map<String, String> cookies = Map.of(
                    "t", "f4f5da655c13e3a37752519a28a64272",
                    "cna", "J4PxIXPk7GsCAbJFkxS1hXhK",
                    "_m_h5_tk_enc", "9e54ee158dfb20c4133458b4ba6f4a57",
                    "_tb_token_", "e6eb53b56333",
                    "_m_h5_tk", "4cfcdfcd0acb82607cbe54cba7441a33_1768601319324",
                    "cookie2", "11429dc109576674d9c9a0d93b8e7081"
            );

            // Простой запрос
            String url = buildSimpleUrl("stone island");
            logger.info("URL: {}", url);

            // Отправляем запрос
            String response = sendRequest(url, cookies);

            // Сохраняем для анализа
            File output = new File("api_response.json");
            Files.write(output.toPath(), response.getBytes());
            logger.info("Ответ сохранен в: {}", output.getAbsolutePath());

            // Анализируем структуру
            analyzeStructure(response);

        } catch (Exception e) {
            logger.error("Ошибка: {}", e.getMessage(), e);
        }
    }

    private static String buildSimpleUrl(String query) {
        try {
            String appKey = "34839810";
            long timestamp = System.currentTimeMillis();

            // Простой data объект
            JSONObject data = new JSONObject();
            data.put("pageNumber", 1);
            data.put("keyword", query);
            data.put("rowsPerPage", 10);
            data.put("sortValue", "new");

            String dataStr = data.toString();

            // Простая подпись для теста
            String sign = "test_sign_" + timestamp;

            return "https://h5api.m.goofish.com/h5/mtop.taobao.idlemtopsearch.pc.search/1.0/" +
                    "?jsv=2.7.2" +
                    "&appKey=" + appKey +
                    "&t=" + timestamp +
                    "&sign=" + sign +
                    "&v=1.0" +
                    "&type=originaljson" +
                    "&data=" + java.net.URLEncoder.encode(dataStr, "UTF-8");

        } catch (Exception e) {
            return "";
        }
    }

    private static String sendRequest(String url, Map<String, String> cookies) throws Exception {
        // Простая реализация HTTP запроса
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        conn.setRequestProperty("Cookie", buildCookieString(cookies));

        try (java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(conn.getInputStream()))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            return response.toString();
        }
    }

    private static String buildCookieString(Map<String, String> cookies) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : cookies.entrySet()) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(entry.getKey()).append("=").append(entry.getValue());
        }
        return sb.toString();
    }

    private static void analyzeStructure(String response) {
        try {
            JSONObject json = new JSONObject(response);

            logger.info("\n=== ОСНОВНЫЕ КЛЮЧИ ===");
            for (String key : json.keySet()) {
                logger.info("{}: {}", key, json.get(key).getClass().getSimpleName());
            }

            if (json.has("data")) {
                JSONObject data = json.getJSONObject("data");
                logger.info("\n=== КЛЮЧИ В DATA ===");
                for (String key : data.keySet()) {
                    Object value = data.get(key);
                    logger.info("{}: {} ({})",
                            key,
                            value.getClass().getSimpleName(),
                            value instanceof JSONArray ? ((JSONArray)value).length() + " items" : "single");
                }

                if (data.has("resultList")) {
                    JSONArray resultList = data.getJSONArray("resultList");
                    logger.info("\n=== АНАЛИЗ ПЕРВОГО ТОВАРА (из {}) ===", resultList.length());

                    if (resultList.length() > 0) {
                        JSONObject firstItem = resultList.getJSONObject(0);
                        logger.info("Ключи в первом item: {}", firstItem.keySet());

                        // Детальный анализ
                        analyzeItem(firstItem, 0);
                    }
                }
            }

        } catch (Exception e) {
            logger.error("Ошибка анализа: {}", e.getMessage());
        }
    }

    private static void analyzeItem(JSONObject item, int index) {
        try {
            logger.info("\n--- Детальный анализ item {} ---", index);

            for (String key : item.keySet()) {
                Object value = item.get(key);
                logger.info("{}: {} = {}", key, value.getClass().getSimpleName(),
                        value.toString().length() > 100 ?
                                value.toString().substring(0, 100) + "..." :
                                value.toString());

                // Рекурсивный анализ вложенных объектов
                if (value instanceof JSONObject) {
                    JSONObject obj = (JSONObject) value;
                    logger.info("  Вложенные ключи: {}", obj.keySet());

                    // Особое внимание к data и item
                    if ("data".equals(key)) {
                        analyzeDataObject(obj);
                    }
                }
            }

        } catch (Exception e) {
            logger.error("Ошибка анализа item: {}", e.getMessage());
        }
    }

    private static void analyzeDataObject(JSONObject data) {
        try {
            logger.info("\n  === АНАЛИЗ DATA ОБЪЕКТА ===");
            logger.info("  Ключи в data: {}", data.keySet());

            for (String key : data.keySet()) {
                Object value = data.get(key);
                logger.info("  {}: {}", key, value.getClass().getSimpleName());

                if ("item".equals(key) && value instanceof JSONObject) {
                    analyzeItemObject((JSONObject) value);
                }

                if ("template".equals(key) && value instanceof JSONObject) {
                    JSONObject template = (JSONObject) value;
                    logger.info("    Template name: {}", template.optString("name", "NO_NAME"));
                    logger.info("    Template url: {}", template.optString("url", "NO_URL"));
                }
            }

        } catch (Exception e) {
            logger.error("Ошибка анализа data объекта: {}", e.getMessage());
        }
    }

    private static void analyzeItemObject(JSONObject item) {
        try {
            logger.info("\n    === АНАЛИЗ ITEM ОБЪЕКТА ===");
            logger.info("    Ключи в item: {}", item.keySet());

            // Проверяем основные поля
            String[] importantKeys = {"id", "title", "price", "location", "publishTime"};
            for (String key : importantKeys) {
                if (item.has(key)) {
                    logger.info("    {}: {}", key, item.get(key));
                }
            }

            // Проверяем наличие вложенных объектов
            if (item.has("main")) {
                logger.info("    main присутствует");
            }
            if (item.has("extra")) {
                logger.info("    extra присутствует");
            }

        } catch (Exception e) {
            logger.error("Ошибка анализа item объекта: {}", e.getMessage());
        }
    }
}