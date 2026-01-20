package com.parser.test;

import com.parser.service.CookieService;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

// Для zstd декомпрессии - две опции:
// Вариант 1: Используем Apache Commons Compress (уже есть в зависимостях через docker-java)
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream;
// Или Вариант 2: Используем чистую Java библиотеку (добавить в pom.xml)
// import com.github.luben.zstd.ZstdInputStream;

public class ApiTester {
    private static final Logger logger = LoggerFactory.getLogger(ApiTester.class);
    private static final String APP_KEY = "34839810";

    public static void main(String[] args) {
        try {
            System.out.println("🧪 Тестирование API Goofish с динамической подписью...");

            // Инициализируем куки
            CookieService.initialize();

            // Получаем свежие куки
            String domain = "h5api.m.goofish.com";
            String cookieHeader = CookieService.getCookieHeader(domain);
            System.out.println("🍪 Куки получены (длина: " + cookieHeader.length() + " символов)");

            // Извлекаем токен из куки _m_h5_tk
            String token = extractTokenFromCookies(cookieHeader);
            if (token.isEmpty()) {
                System.err.println("❌ Не удалось извлечь токен из куки");
                return;
            }
            System.out.println("🔑 Токен: " + (token.length() > 20 ? token.substring(0, 17) + "..." : token));

            // Используем текущее время
            long timestamp = System.currentTimeMillis();
            System.out.println("⏰ Текущее время: " + timestamp);

            // Формируем данные для запроса
            String data = buildSearchData("stone island", 1, 30);
            System.out.println("📝 Данные запроса: " + data.substring(0, Math.min(100, data.length())) + "...");

            // Генерируем подпись
            String sign = generateSignature(token, timestamp, data);
            System.out.println("🔐 Подпись: " + sign);

            // Строим URL с параметрами
            String url = buildApiUrl(timestamp, sign);
            System.out.println("📤 URL запроса: " + url);

            // Создаем POST запрос
            HttpPost request = new HttpPost(url);

            // Устанавливаем заголовки как в реальном браузере
            setBrowserHeaders(request);

            // Добавляем куки
            if (cookieHeader != null && !cookieHeader.isEmpty()) {
                request.setHeader("Cookie", cookieHeader);
                System.out.println("✅ Добавлены куки в заголовок");
            } else {
                System.err.println("⚠️ Куки пустые!");
            }

            // Формируем тело запроса (application/x-www-form-urlencoded)
            String formData = "data=" + java.net.URLEncoder.encode(data, "UTF-8");
            request.setEntity(new StringEntity(formData, StandardCharsets.UTF_8));

            // Выполняем запрос
            try (CloseableHttpClient client = HttpClients.createDefault();
                 var response = client.execute(request)) {

                int statusCode = response.getStatusLine().getStatusCode();
                String contentType = response.getFirstHeader("Content-Type") != null ?
                        response.getFirstHeader("Content-Type").getValue() : "unknown";
                String contentEncoding = response.getFirstHeader("Content-Encoding") != null ?
                        response.getFirstHeader("Content-Encoding").getValue() : "unknown";

                System.out.println("\n📥 Ответ сервера:");
                System.out.println("Статус: " + statusCode);
                System.out.println("Content-Type: " + contentType);
                System.out.println("Content-Encoding: " + contentEncoding);

                // Выводим заголовки ответа
                System.out.println("\n📋 Заголовки ответа:");
                for (org.apache.http.Header header : response.getAllHeaders()) {
                    String headerName = header.getName();
                    String headerValue = header.getValue();

                    // Сокращаем длинные значения
                    if (headerValue.length() > 100) {
                        headerValue = headerValue.substring(0, 97) + "...";
                    }

                    System.out.println("  " + headerName + ": " + headerValue);

                    // Отслеживаем обновление куки
                    if ("Set-Cookie".equalsIgnoreCase(headerName)) {
                        System.out.println("  ⚠️ Сервер обновил куки!");
                    }
                }

                // Получаем сырые байты ответа
                byte[] responseBytes = EntityUtils.toByteArray(response.getEntity());
                String responseBody;

                // Обрабатываем сжатие в зависимости от Content-Encoding
                if ("zstd".equalsIgnoreCase(contentEncoding)) {
                    System.out.println("\n🔄 Распаковываем zstd сжатие...");
                    responseBody = decompressZstd(responseBytes);
                } else if ("gzip".equalsIgnoreCase(contentEncoding) || "deflate".equalsIgnoreCase(contentEncoding)) {
                    // HttpClient обычно автоматически обрабатывает gzip/deflate
                    responseBody = new String(responseBytes, StandardCharsets.UTF_8);
                } else {
                    // Без сжатия
                    responseBody = new String(responseBytes, StandardCharsets.UTF_8);
                }

                System.out.println("\n📄 Тело ответа:");
                if (responseBody.length() > 1000) {
                    System.out.println(responseBody.substring(0, 1000) + "...");
                    System.out.println("... (полный ответ: " + responseBody.length() + " символов)");
                } else {
                    System.out.println(responseBody);
                }

                // Парсим JSON ответ
                if (responseBody.trim().startsWith("{") || responseBody.trim().startsWith("[")) {
                    try {
                        JSONObject json = new JSONObject(responseBody);
                        System.out.println("\n✅ JSON успешно распарсен");

                        if (json.has("ret")) {
                            Object ret = json.get("ret");
                            System.out.println("ret: " + ret);

                            if (ret instanceof org.json.JSONArray) {
                                org.json.JSONArray retArray = (org.json.JSONArray) ret;
                                for (int i = 0; i < retArray.length(); i++) {
                                    String retItem = retArray.getString(i);
                                    System.out.println("  ret[" + i + "]: " + retItem);

                                    if (retItem.contains("FAIL_SYS_ILLEGAL_ACCESS")) {
                                        System.err.println("❌ ОШИБКА: НЕЗАКОННЫЙ ЗАПРОС!");
                                        System.err.println("   Причина: неверная подпись или устаревшие параметры");
                                    } else if (retItem.contains("被挤爆啦")) {
                                        System.err.println("❌ ОШИБКА: СЕРВЕР ПЕРЕГРУЖЕН!");
                                        System.err.println("   Сообщение: " + retItem);
                                    }
                                }
                            }
                        }

                        if (json.has("data")) {
                            Object dataObj = json.get("data");
                            System.out.println("data тип: " + dataObj.getClass().getSimpleName());

                            if (dataObj instanceof JSONObject) {
                                JSONObject dataJson = (JSONObject) dataObj;

                                // Проверяем различные возможные структуры данных
                                if (dataJson.has("resultList")) {
                                    System.out.println("✅ Найдены товары в resultList!");
                                    Object resultList = dataJson.get("resultList");
                                    if (resultList instanceof JSONArray) {
                                        JSONArray items = (JSONArray) resultList;
                                        System.out.println("   Количество товаров: " + items.length());
                                        for (int i = 0; i < Math.min(3, items.length()); i++) {
                                            System.out.println("   Товар " + (i+1) + ": " + items.get(i));
                                        }
                                    }
                                } else if (dataJson.has("items")) {
                                    System.out.println("✅ Найдены товары в items!");
                                } else if (dataJson.has("list")) {
                                    System.out.println("✅ Найдены товары в list!");
                                } else {
                                    System.out.println("📊 Структура data: " + dataJson.toString(2));
                                }
                            } else if (dataObj instanceof String) {
                                System.out.println("data (строка): " + dataObj);
                                // Проверяем, не является ли это URL для редиректа/авторизации
                                String dataStr = (String) dataObj;
                                if (dataStr.contains("http") || dataStr.contains("login")) {
                                    System.err.println("⚠️  Возможно требуется авторизация!");
                                }
                            }
                        } else {
                            System.out.println("📊 Полная структура JSON: " + json.toString(2));
                        }

                    } catch (Exception e) {
                        System.err.println("❌ Ошибка парсинга JSON: " + e.getMessage());
                        e.printStackTrace();

                        // Показываем начало ответа для отладки
                        if (responseBody.length() > 200) {
                            System.err.println("Первые 200 символов ответа: " + responseBody.substring(0, 200));
                        }
                    }
                } else {
                    System.err.println("❌ Ответ не в формате JSON");
                    if (responseBody.contains("<html") || responseBody.contains("<!DOCTYPE")) {
                        System.err.println("⚠️ Получена HTML страница вместо JSON. Возможно, куки недействительны.");
                    } else if (responseBody.length() < 100) {
                        System.err.println("Короткий ответ: " + responseBody);
                    }
                }

                // Анализ ответа
                analyzeResponse(statusCode, responseBody);

            }

        } catch (Exception e) {
            System.err.println("❌ Критическая ошибка: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Распаковка zstd сжатых данных
     */
    /**
     * Распаковка zstd сжатых данных
     */
    private static String decompressZstd(byte[] compressedData) {
        try {
            System.out.println("📦 Размер сжатых данных: " + compressedData.length + " байт");

            try (ByteArrayInputStream bais = new ByteArrayInputStream(compressedData);
                 org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream zstdIn =
                         new org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream(bais);
                 ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

                byte[] buffer = new byte[8192];
                int len;
                while ((len = zstdIn.read(buffer)) > 0) {
                    baos.write(buffer, 0, len);
                }

                String result = baos.toString("UTF-8");
                System.out.println("✅ Zstd успешно распакован");
                System.out.println("📄 Размер распакованных данных: " + result.length() + " символов");
                return result;
            }

        } catch (Exception e) {
            System.err.println("❌ Ошибка распаковки zstd: " + e.getMessage());
            e.printStackTrace();

            // Пробуем прочитать как обычную строку (на случай, если это не zstd)
            try {
                String fallback = new String(compressedData, StandardCharsets.UTF_8);
                System.out.println("⚠️  Используем fallback чтение как UTF-8");
                return fallback;
            } catch (Exception e2) {
                return "Ошибка при обработке ответа: " + e.getMessage();
            }
        }
    }

    /**
     * Извлечение токена из строки куки
     */
    private static String extractTokenFromCookies(String cookieHeader) {
        if (cookieHeader == null || cookieHeader.isEmpty()) {
            return "";
        }

        // Разделяем куки по точке с запятой
        String[] cookiePairs = cookieHeader.split("; ");
        for (String pair : cookiePairs) {
            if (pair.startsWith("_m_h5_tk=")) {
                String value = pair.substring(9); // Убираем "_m_h5_tk="
                // Токен - часть до первого подчеркивания
                int underscoreIndex = value.indexOf('_');
                if (underscoreIndex != -1) {
                    return value.substring(0, underscoreIndex);
                }
                return value;
            }
        }

        return "";
    }

    /**
     * Генерация подписи MD5
     */
    private static String generateSignature(String token, long timestamp, String data) {
        try {
            String signString = token + "&" + timestamp + "&" + APP_KEY + "&" + data;

            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(signString.getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) {
                    hex.append('0');
                }
                hex.append(h);
            }

            return hex.toString();
        } catch (Exception e) {
            System.err.println("❌ Ошибка генерации подписи: " + e.getMessage());
            return "";
        }
    }

    /**
     * Формирование данных для поиска
     */
    private static String buildSearchData(String query, int page, int rows) {
        JSONObject data = new JSONObject();
        data.put("pageNumber", page);
        data.put("keyword", query);
        data.put("fromFilter", false);
        data.put("rowsPerPage", rows);
        data.put("sortValue", "");
        data.put("sortField", "");
        data.put("customDistance", "");
        data.put("gps", "");
        data.put("propValueStr", new JSONObject());
        data.put("customGps", "");
        data.put("searchReqFromPage", "pcSearch");
        data.put("extraFilterValue", "{}");
        data.put("userPositionJson", "{}");

        return data.toString();
    }

    /**
     * Построение URL API с параметрами
     */
    private static String buildApiUrl(long timestamp, String sign) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("jsv", "2.7.2");
        params.put("appKey", APP_KEY);
        params.put("t", String.valueOf(timestamp));
        params.put("sign", sign);
        params.put("v", "1.0");
        params.put("type", "originaljson");
        params.put("accountSite", "xianyu");
        params.put("dataType", "json");
        params.put("timeout", "20000");
        params.put("api", "mtop.taobao.idlemtopsearch.pc.search");
        params.put("sessionOption", "AutoLoginOnly");
        params.put("spm_cnt", "a21ybx.search.0.0");
        params.put("spm_pre", "a21ybx.search.searchInput.0");

        StringBuilder url = new StringBuilder("https://h5api.m.goofish.com/h5/mtop.taobao.idlemtopsearch.pc.search/1.0/?");

        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!first) {
                url.append("&");
            }
            url.append(entry.getKey())
                    .append("=")
                    .append(java.net.URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
            first = false;
        }

        return url.toString();
    }

    /**
     * Установка заголовков браузера
     */
    private static void setBrowserHeaders(HttpPost request) {
        request.setHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 YaBrowser/25.10.0.0 Safari/537.36");
        request.setHeader("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        request.setHeader("Accept", "application/json");
        request.setHeader("Accept-Encoding", "gzip, deflate, br, zstd");
        request.setHeader("Accept-Language", "ru,en;q=0.9");
        request.setHeader("Origin", "https://www.goofish.com");
        request.setHeader("Referer", "https://www.goofish.com/");
        request.setHeader("Sec-Fetch-Dest", "empty");
        request.setHeader("Sec-Fetch-Mode", "cors");
        request.setHeader("Sec-Fetch-Site", "same-site");
        request.setHeader("sec-ch-ua", "\"Chromium\";v=\"140\", \"Not=A?Brand\";v=\"24\", \"YaBrowser\";v=\"25.10\", \"Yowser\";v=\"2.5\", \"YaBrowserCorp\";v=\"140\"");
        request.setHeader("sec-ch-ua-mobile", "?0");
        request.setHeader("sec-ch-ua-platform", "\"macOS\"");
        request.setHeader("x-accept-terminal", "pc");
        request.setHeader("Connection", "keep-alive");
        request.setHeader("Pragma", "no-cache");
        request.setHeader("Cache-Control", "no-cache");
    }

    /**
     * Анализ ответа сервера
     */
    private static void analyzeResponse(int statusCode, String responseBody) {
        System.out.println("\n🔍 Анализ ответа:");

        if (statusCode == 200) {
            System.out.println("✅ HTTP статус: 200 OK");

            if (responseBody.contains("FAIL_SYS_ILLEGAL_ACCESS")) {
                System.err.println("❌ Содержимое: НЕЗАКОННЫЙ ЗАПРОС");
                System.err.println("   Возможные причины:");
                System.err.println("   1. Неверная подпись (sign)");
                System.err.println("   2. Устаревший timestamp (t)");
                System.err.println("   3. Неверный токен из куки _m_h5_tk");
                System.err.println("   4. Неверный формат данных (data)");
            } else if (responseBody.contains("FAIL_SYS_SESSION_EXPIRED")) {
                System.err.println("❌ Содержимое: СЕССИЯ ИСТЕКЛА");
                System.err.println("   Требуется обновление куки");
            } else if (responseBody.contains("SUCCESS")) {
                System.out.println("✅ Содержимое: УСПЕШНЫЙ ЗАПРОС");
            } else if (responseBody.contains("resultList") || responseBody.contains("\"data\":")) {
                System.out.println("✅ Содержимое: НАЙДЕНЫ ДАННЫЕ");
            } else if (responseBody.contains("\"ret\":") && responseBody.contains("SUCCESS")) {
                System.out.println("✅ Содержимое: API УСПЕШНО ВЫПОЛНЕНО");
            } else {
                System.out.println("⚠️  Содержимое: НЕИЗВЕСТНЫЙ ОТВЕТ");
                System.out.println("   Первые 200 символов: " +
                        (responseBody.length() > 200 ? responseBody.substring(0, 200) + "..." : responseBody));
            }
        } else if (statusCode == 403) {
            System.err.println("❌ HTTP статус: 403 FORBIDDEN");
            System.err.println("   Доступ запрещен. Возможно, куки недействительны.");
        } else if (statusCode == 429) {
            System.err.println("❌ HTTP статус: 429 TOO MANY REQUESTS");
            System.err.println("   Слишком много запросов. Нужно добавить задержку.");
        } else if (statusCode == 401) {
            System.err.println("❌ HTTP статус: 401 UNAUTHORIZED");
            System.err.println("   Требуется авторизация. Куки устарели.");
        } else {
            System.err.println("❌ HTTP статус: " + statusCode);
        }
    }

    /**
     * Дополнительный метод для отладки куки
     */
    private static void debugCookies(String cookieHeader) {
        System.out.println("\n🔍 Отладка куки:");

        if (cookieHeader == null || cookieHeader.isEmpty()) {
            System.err.println("❌ Куки пустые");
            return;
        }

        String[] cookies = cookieHeader.split("; ");
        System.out.println("📊 Всего куки: " + cookies.length);

        // Важные куки для проверки
        String[] importantCookies = {"_m_h5_tk", "_m_h5_tk_enc", "_tb_token_", "cna", "cookie2", "t", "tfstk"};

        for (String cookie : cookies) {
            String[] parts = cookie.split("=", 2);
            if (parts.length == 2) {
                String name = parts[0];
                String value = parts[1];

                // Проверяем, важная ли это кука
                boolean isImportant = Arrays.asList(importantCookies).contains(name);

                if (isImportant) {
                    System.out.print("⭐ ");
                } else {
                    System.out.print("   ");
                }

                System.out.print(String.format("%-25s", name + ":"));

                // Обрезаем длинные значения
                if (value.length() > 50) {
                    System.out.println(value.substring(0, 47) + "...");
                } else {
                    System.out.println(value);
                }

                // Особый анализ для _m_h5_tk
                if ("_m_h5_tk".equals(name)) {
                    if (value.contains("_")) {
                        String[] tokenParts = value.split("_", 2);
                        System.out.println("        Токен: " + tokenParts[0]);
                        System.out.println("        Время: " + tokenParts[1]);
                        System.out.println("        Статус: " + (tokenParts[0].length() == 32 ? "✅ Корректный" : "❌ Некорректный"));
                    } else {
                        System.err.println("        ⚠️ Нет временной метки в _m_h5_tk!");
                    }
                }
            }
        }
    }
}