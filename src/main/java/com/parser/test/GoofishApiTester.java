package com.parser.test;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Тестовый класс для проверки API Goofish без изменения основного кода
 */
public class GoofishApiTester {
    private static final Logger logger = LoggerFactory.getLogger(GoofishApiTester.class);

    // Cookies из вашего лога
    private static final Map<String, String> TEST_COOKIES = new HashMap<>();

    static {
        TEST_COOKIES.put("t", "9b5bc20853edf1135c962865b5abd953");
        TEST_COOKIES.put("cna", "/n/xISWRjHMCAbJFkxQ5tuX3");
        TEST_COOKIES.put("_m_h5_tk_enc", "826611c91d1b74de9be1895797b07a4c");
        TEST_COOKIES.put("_tb_token_", "55934eba57e13");
        TEST_COOKIES.put("_m_h5_tk", "e1c5649386e939a9bd30466a94213c01_1768599430381");
        TEST_COOKIES.put("cookie2", "1ea3755cad27c7a58bdbd4681e5c93c6");
    }

    public static void main(String[] args) {
        try {
            logger.info("=== Тестирование API Goofish ===");

            // Тест 1: Проверка текущего API
            testCurrentApi();

            // Тест 2: Анализ ответа API
            testApiResponseAnalysis();

            // Тест 3: Проверка различных запросов
            testDifferentQueries();

        } catch (Exception e) {
            logger.error("Ошибка тестирования: {}", e.getMessage(), e);
        }
    }

    /**
     * Тест 1: Проверка текущего API с реальными cookies
     */
    private static void testCurrentApi() throws Exception {
        logger.info("\n=== Тест 1: Проверка API с текущими cookies ===");

        String query = "stone island";
        int page = 1;
        int rowsPerPage = 10;

        // Построим URL как в вашем коде
        String url = buildTestUrl(query, page, rowsPerPage);
        logger.info("URL запроса: {}", url);

        // Отправляем запрос
        String response = sendHttpRequest(url);
        logger.info("Длина ответа: {} символов", response.length());
        logger.info("Первые 500 символов ответа:\n{}",
                response.substring(0, Math.min(500, response.length())));

        // Анализируем ответ
        analyzeResponse(response);
    }

    /**
     * Тест 2: Детальный анализ структуры ответа
     */
    private static void testApiResponseAnalysis() throws Exception {
        logger.info("\n=== Тест 2: Детальный анализ структуры ответа ===");

        String query = "iphone";
        String url = buildTestUrl(query, 1, 5);
        String response = sendHttpRequest(url);

        try {
            JSONObject json = new JSONObject(response);

            // Выводим все ключи и их типы
            logger.info("Ключи в JSON ответе:");
            for (String key : json.keySet()) {
                Object value = json.get(key);
                String type = value.getClass().getSimpleName();
                String valueStr = value.toString();

                logger.info("  '{}': {} ({} chars)",
                        key, type,
                        valueStr.length());

                // Если это короткое значение, покажем его
                if (valueStr.length() < 100) {
                    logger.info("    Значение: {}", valueStr);
                }

                // Особое внимание к полю 'ret'
                if ("ret".equals(key)) {
                    logger.info("    === АНАЛИЗ ПОЛЯ ret ===");
                    logger.info("    Полное значение: {}", valueStr);

                    // Попробуем парсить как JSONArray
                    if (valueStr.startsWith("[") && valueStr.endsWith("]")) {
                        try {
                            JSONArray retArray = new JSONArray(valueStr);
                            logger.info("    Это JSON массив с {} элементами:", retArray.length());
                            for (int i = 0; i < retArray.length(); i++) {
                                logger.info("      [{}]: {}", i, retArray.getString(i));
                            }
                        } catch (Exception e) {
                            logger.info("    Не удалось парсить как JSON массив");
                        }
                    }
                }

                // Проверяем наличие данных
                if ("data".equals(key) && value instanceof JSONObject) {
                    JSONObject data = (JSONObject) value;
                    logger.info("    Ключи в 'data': {}", data.keySet());

                    // Ищем возможные поля с товарами
                    for (String dataKey : data.keySet()) {
                        Object dataValue = data.get(dataKey);
                        if (dataValue instanceof JSONArray) {
                            JSONArray array = (JSONArray) dataValue;
                            logger.info("    '{}' - JSONArray с {} элементами", dataKey, array.length());
                        }
                    }
                }
            }

        } catch (Exception e) {
            logger.error("Ошибка парсинга JSON: {}", e.getMessage());
        }
    }

    /**
     * Тест 3: Проверка разных запросов
     */
    private static void testDifferentQueries() throws Exception {
        logger.info("\n=== Тест 3: Проверка разных запросов ===");

        String[] queries = {"stone island", "adidas", "手机", "nike"};

        for (String query : queries) {
            logger.info("\n--- Запрос: '{}' ---", query);

            String url = buildTestUrl(query, 1, 3);
            String response = sendHttpRequest(url);

            try {
                JSONObject json = new JSONObject(response);

                // Проверяем поле ret
                String ret = json.optString("ret", "");
                String status = json.optString("status", "");
                String msg = json.optString("msg", "");

                logger.info("  ret: '{}'", ret);
                logger.info("  status: '{}'", status);
                logger.info("  msg: '{}'", msg);

                // Проверяем есть ли данные
                if (json.has("data")) {
                    JSONObject data = json.getJSONObject("data");
                    logger.info("  data keys: {}", data.keySet());

                    // Ищем товары
                    findProductsInData(data, query);
                }

            } catch (Exception e) {
                logger.error("  Ошибка анализа ответа: {}", e.getMessage());
            }

            // Пауза между запросами
            Thread.sleep(2000);
        }
    }

    /**
     * Поиск товаров в объекте data
     */
    private static void findProductsInData(JSONObject data, String query) {
        // Попробуем разные возможные ключи
        String[] possibleProductKeys = {
                "resultList", "items", "list", "result",
                "dataList", "resultData", "itemList", "goodsList"
        };

        for (String key : possibleProductKeys) {
            if (data.has(key) && data.get(key) instanceof JSONArray) {
                JSONArray products = data.getJSONArray(key);
                logger.info("  Нашли товары в ключе '{}': {} элементов", key, products.length());

                // Покажем первые 3 товара
                if (products.length() > 0) {
                    logger.info("  Примеры товаров:");
                    for (int i = 0; i < Math.min(3, products.length()); i++) {
                        try {
                            Object item = products.get(i);
                            if (item instanceof JSONObject) {
                                JSONObject product = (JSONObject) item;
                                logger.info("    [{}]: {}", i, product.toString().substring(0,
                                        Math.min(100, product.toString().length())) + "...");
                            } else {
                                logger.info("    [{}]: {}", i, item.toString());
                            }
                        } catch (Exception e) {
                            logger.info("    [{}]: Ошибка парсинга", i);
                        }
                    }
                }
                return;
            }
        }

        logger.info("  Не нашли товары в стандартных ключах");

        // Выведем все ключи data для диагностики
        logger.info("  Все ключи в data:");
        for (String key : data.keySet()) {
            Object value = data.get(key);
            logger.info("    '{}': {}", key, value.getClass().getSimpleName());
        }
    }

    /**
     * Построение тестового URL
     */
    private static String buildTestUrl(String query, int page, int rowsPerPage) {
        try {
            // Извлекаем токен из _m_h5_tk
            String mh5tk = TEST_COOKIES.get("_m_h5_tk");
            String token = "";
            if (mh5tk != null && mh5tk.contains("_")) {
                token = mh5tk.split("_")[0];
            }

            String appKey = "34839810";
            long timestamp = System.currentTimeMillis();

            // Формируем data как в вашем коде
            JSONObject dataJson = new JSONObject();
            dataJson.put("pageNumber", page);
            dataJson.put("keyword", query);
            dataJson.put("fromFilter", false);
            dataJson.put("rowsPerPage", rowsPerPage);
            dataJson.put("sortValue", "new");
            dataJson.put("sortField", "");
            dataJson.put("customDistance", "");
            dataJson.put("gps", "");
            dataJson.put("propValueStr", new JSONObject());
            dataJson.put("customGps", "");
            dataJson.put("searchReqFromPage", "pcSearch");
            dataJson.put("extraFilterValue", "{}");
            dataJson.put("userPositionJson", "{}");

            String dataStr = dataJson.toString();

            // Генерируем подпись
            String signString = token + "&" + timestamp + "&" + appKey + "&" + dataStr;
            String signature = generateMD5(signString);

            // Формируем параметры
            Map<String, String> params = new java.util.LinkedHashMap<>();
            params.put("jsv", "2.7.2");
            params.put("appKey", appKey);
            params.put("t", String.valueOf(timestamp));
            params.put("sign", signature);
            params.put("v", "1.0");
            params.put("type", "originaljson");
            params.put("accountSite", "xianyu");
            params.put("dataType", "json");
            params.put("timeout", "20000");
            params.put("api", "mtop.taobao.idlemtopsearch.pc.search");
            params.put("sessionOption", "AutoLoginOnly");
            params.put("spm_cnt", "a21ybx.search.0.0");
            params.put("spm_pre", "a21ybx.search.searchInput.0");
            params.put("data", dataStr);

            // Собираем URL
            StringBuilder url = new StringBuilder("https://h5api.m.goofish.com/h5/mtop.taobao.idlemtopsearch.pc.search/1.0/?");
            boolean first = true;
            for (Map.Entry<String, String> entry : params.entrySet()) {
                if (!first) url.append("&");
                url.append(java.net.URLEncoder.encode(entry.getKey(), "UTF-8"))
                        .append("=")
                        .append(java.net.URLEncoder.encode(entry.getValue(), "UTF-8"));
                first = false;
            }

            return url.toString();

        } catch (Exception e) {
            logger.error("Ошибка построения URL: {}", e.getMessage());
            return "";
        }
    }

    /**
     * Генерация MD5 (как в вашем коде)
     */
    private static String generateMD5(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : digest) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            logger.error("Ошибка генерации MD5: {}", e.getMessage());
            return "dummy_signature_" + System.currentTimeMillis();
        }
    }

    /**
     * Отправка HTTP запроса
     */
    private static String sendHttpRequest(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        // Настраиваем соединение
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
        connection.setRequestProperty("Connection", "keep-alive");

        // Добавляем cookies
        StringBuilder cookieHeader = new StringBuilder();
        for (Map.Entry<String, String> entry : TEST_COOKIES.entrySet()) {
            if (cookieHeader.length() > 0) cookieHeader.append("; ");
            cookieHeader.append(entry.getKey()).append("=").append(entry.getValue());
        }
        connection.setRequestProperty("Cookie", cookieHeader.toString());

        // Логируем заголовки
        logger.debug("Отправляю запрос с cookies: {} cookies", TEST_COOKIES.size());

        // Читаем ответ
        int responseCode = connection.getResponseCode();
        logger.debug("Код ответа: {}", responseCode);

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {

            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }

            return response.toString();
        }
    }

    /**
     * Анализ ответа API
     */
    private static void analyzeResponse(String response) {
        try {
            JSONObject json = new JSONObject(response);

            // Ключевые поля
            String ret = json.optString("ret", "NOT_FOUND");
            String status = json.optString("status", "NOT_FOUND");
            String msg = json.optString("msg", "NOT_FOUND");

            logger.info("Анализ ответа:");
            logger.info("  ret: {}", ret);
            logger.info("  status: {}", status);
            logger.info("  msg: {}", msg);

            // Проверяем логику из вашего кода
            boolean isErrorInCurrentLogic = !status.equals("SUCCESS") && !ret.isEmpty();
            logger.info("  Текущая логика считает это ошибкой: {}", isErrorInCurrentLogic);

            // Проверяем наличие данных
            if (json.has("data")) {
                JSONObject data = json.optJSONObject("data");
                logger.info("  data присутствует, ключи: {}",
                        data != null ? data.keySet() : "null");
            } else {
                logger.info("  data отсутствует");
            }

        } catch (Exception e) {
            logger.error("Ошибка анализа ответа: {}", e.getMessage());
        }
    }
}