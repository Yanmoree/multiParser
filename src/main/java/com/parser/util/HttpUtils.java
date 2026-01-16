package com.parser.util;

import com.parser.config.Config;
import com.parser.config.CookieConfig;
import com.parser.service.CookieService;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Утилиты для работы с HTTP запросами
 */
public class HttpUtils {
    private static final Logger logger = LoggerFactory.getLogger(HttpUtils.class);
    private static final Random random = new Random();

    // Константы для заголовков кук
    private static final String HEADER_COOKIE = "Cookie";
    private static final String HEADER_SET_COOKIE = "Set-Cookie";
    private static final String HEADER_CONTENT_LENGTH = "Content-Length";

    // Конфигурация HTTP клиента
    private static final RequestConfig requestConfig = RequestConfig.custom()
            .setConnectTimeout(Config.getHttpConnectTimeout())
            .setSocketTimeout(Config.getHttpReadTimeout())
            .setConnectionRequestTimeout(5000)
            .setRedirectsEnabled(true)
            .setCookieSpec(CookieSpecs.STANDARD)
            .build();

    // Пул HTTP клиентов
    private static volatile CloseableHttpClient httpClient = null;

    /**
     * Получение HTTP клиента
     */
    private static synchronized CloseableHttpClient getHttpClient() {
        if (httpClient == null) {
            httpClient = HttpClients.custom()
                    .setDefaultRequestConfig(requestConfig)
                    .setRedirectStrategy(new LaxRedirectStrategy())
                    .setUserAgent(getRandomUserAgent())
                    .setMaxConnTotal(100)
                    .setMaxConnPerRoute(20)
                    .disableCookieManagement() // Управляем куками вручную
                    .build();
            logger.debug("HTTP client initialized");
        }
        return httpClient;
    }

    /**
     * Отправка GET запроса
     */
    public static String sendGetRequest(String url) throws Exception {
        return sendGetRequest(url, getDefaultUserAgent(), true);
    }

    /**
     * Отправка GET запроса с указанием User-Agent
     */
    public static String sendGetRequest(String url, String userAgent) throws Exception {
        return sendGetRequest(url, userAgent, true);
    }

    /**
     * Отправка GET запроса с расширенными параметрами
     */
    public static String sendGetRequest(String url, String userAgent, boolean useCookies) throws Exception {
        if (url == null || url.isEmpty()) {
            throw new IllegalArgumentException("URL cannot be empty");
        }

        logger.debug("Sending GET request to: {}", url);

        HttpGet request = new HttpGet(url);
        request.setHeader(HttpHeaders.USER_AGENT, userAgent != null ? userAgent : getDefaultUserAgent());
        request.setHeader(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        request.setHeader(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9,ru;q=0.8");
        request.setHeader(HttpHeaders.ACCEPT_ENCODING, "gzip, deflate, br");
        request.setHeader(HttpHeaders.CONNECTION, "keep-alive");
        request.setHeader("Upgrade-Insecure-Requests", "1");

        // Добавляем куки, если требуется
        if (useCookies) {
            String domain = extractDomain(url);
            try {
                // Используем динамические куки из CookieService
                String cookieHeader = CookieService.getCookieHeader(domain);
                if (cookieHeader != null && !cookieHeader.isEmpty()) {
                    request.setHeader(HEADER_COOKIE, cookieHeader);
                    logger.debug("Added dynamic cookies for domain {} ({} chars)",
                            domain, cookieHeader.length());
                } else {
                    logger.warn("No dynamic cookies available for domain: {}", domain);
                    // Fallback на статические куки
                    String cookies = CookieConfig.getCookiesForDomain(domain);
                    if (cookies != null && !cookies.isEmpty()) {
                        request.setHeader(HEADER_COOKIE, cookies);
                        logger.debug("Using static cookies for domain: {}", domain);
                    }
                }
            } catch (Exception e) {
                logger.warn("Failed to get dynamic cookies for {}, falling back to static: {}",
                        domain, e.getMessage());
                // Fallback на статические куки
                String cookies = CookieConfig.getCookiesForDomain(domain);
                if (cookies != null && !cookies.isEmpty()) {
                    request.setHeader(HEADER_COOKIE, cookies);
                    logger.debug("Using static cookies for domain: {}", domain);
                }
            }
        }

        // Добавляем случайные заголовки для имитации браузера
        addRandomHeaders(request);

        try (CloseableHttpResponse response = getHttpClient().execute(request)) {
            int statusCode = response.getStatusLine().getStatusCode();
            logger.debug("Response status: {} for URL: {}", statusCode, url);

            // Обновляем куки из ответа, если требуется
            if (Config.getCookieAutoUpdate() && useCookies) {
                updateCookiesFromResponse(url, response);
            }

            if (statusCode == HttpStatus.SC_OK) {
                String content = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                logger.trace("Response content length: {} chars for URL: {}", content.length(), url);
                return content;
            } else if (statusCode == HttpStatus.SC_TOO_MANY_REQUESTS) {
                logger.warn("Rate limit exceeded for URL: {}", url);
                throw new Exception("Rate limit exceeded (429)");
            } else if (statusCode == HttpStatus.SC_FORBIDDEN) {
                logger.warn("Access forbidden for URL: {}", url);
                throw new Exception("Access forbidden (403) - May need fresh cookies");
            } else if (statusCode == HttpStatus.SC_NOT_FOUND) {
                logger.warn("URL not found: {}", url);
                throw new Exception("Page not found (404)");
            } else if (statusCode == HttpStatus.SC_UNAUTHORIZED) {
                logger.warn("Unauthorized for URL: {}", url);
                throw new Exception("Unauthorized (401) - Check cookies");
            } else {
                logger.warn("HTTP error {} for URL: {}", statusCode, url);
                throw new Exception("HTTP error: " + statusCode);
            }
        } catch (Exception e) {
            logger.error("Error sending request to {}: {}", url, e.getMessage());
            throw e;
        }
    }

    /**
     * Отправка POST запроса
     */
    public static String sendPostRequest(String url, String jsonBody) throws Exception {
        return sendPostRequest(url, jsonBody, getDefaultUserAgent(), true);
    }

    /**
     * Отправка POST запроса с расширенными параметрами
     */
    public static String sendPostRequest(String url, String jsonBody, String userAgent, boolean useCookies) throws Exception {
        logger.debug("Sending POST request to: {}", url);

        HttpPost request = new HttpPost(url);
        request.setHeader(HttpHeaders.USER_AGENT, userAgent != null ? userAgent : getDefaultUserAgent());
        request.setHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        request.setHeader(HttpHeaders.ACCEPT, "application/json");
        request.setHeader(HttpHeaders.ACCEPT_ENCODING, "gzip, deflate, br");

        if (jsonBody != null && !jsonBody.isEmpty()) {
            request.setEntity(new StringEntity(jsonBody, StandardCharsets.UTF_8));
        }

        // Добавляем куки
        if (useCookies) {
            String domain = extractDomain(url);
            String cookieHeader = CookieService.getCookieHeader(domain);
            if (cookieHeader != null && !cookieHeader.isEmpty()) {
                request.setHeader(HEADER_COOKIE, cookieHeader);
            }
        }

        try (CloseableHttpResponse response = getHttpClient().execute(request)) {
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == HttpStatus.SC_OK) {
                String content = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                logger.debug("POST response length: {} chars", content.length());
                return content;
            } else {
                throw new Exception("HTTP POST error: " + statusCode);
            }
        }
    }

    /**
     * Извлечение домена из URL
     */
    public static String extractDomain(String url) {
        try {
            URL urlObj = new URL(url);
            return urlObj.getHost();
        } catch (Exception e) {
            logger.warn("Failed to extract domain from URL: {}", url);
            return "";
        }
    }

    /**
     * Обновление куки из заголовков ответа
     */
    private static void updateCookiesFromResponse(String url, CloseableHttpResponse response) {
        try {
            String domain = extractDomain(url);
            org.apache.http.Header[] setCookieHeaders = response.getHeaders(HEADER_SET_COOKIE);

            if (setCookieHeaders.length > 0) {
                logger.debug("Found {} Set-Cookie headers for domain: {}", setCookieHeaders.length, domain);
                for (org.apache.http.Header header : setCookieHeaders) {
                    CookieConfig.parseSetCookieHeader(domain, header.getValue());
                }
                CookieConfig.saveCookies();
            }
        } catch (Exception e) {
            logger.warn("Failed to update cookies from response: {}", e.getMessage());
        }
    }

    /**
     * Получение стандартного User-Agent
     */
    public static String getDefaultUserAgent() {
        return Config.getHttpUserAgent();
    }

    /**
     * Получение случайного User-Agent
     */
    public static String getRandomUserAgent() {
        String[] userAgents = {
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/121.0",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Mobile/15E148 Safari/604.1",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 YaBrowser/25.10.0.0 Safari/537.36"
        };

        return userAgents[random.nextInt(userAgents.length)];
    }

    /**
     * Добавление случайных заголовков для имитации браузера
     */
    private static void addRandomHeaders(HttpGet request) {
        if (random.nextBoolean()) {
            request.setHeader("Sec-Fetch-Dest", "document");
            request.setHeader("Sec-Fetch-Mode", "navigate");
            request.setHeader("Sec-Fetch-Site", "none");
            request.setHeader("Sec-Fetch-User", "?1");
        }

        if (random.nextBoolean()) {
            request.setHeader(HttpHeaders.CACHE_CONTROL, "max-age=0");
        }

        // Случайный Referer
        String[] referers = {
                "https://www.google.com/",
                "https://www.bing.com/",
                "https://www.yahoo.com/",
                "https://www.baidu.com/",
                "https://h5.m.goofish.com/",
                "https://www.goofish.com/",
                ""
        };
        String referer = referers[random.nextInt(referers.length)];
        if (!referer.isEmpty()) {
            request.setHeader(HttpHeaders.REFERER, referer);
        }
    }

    /**
     * Кодирование URL параметров
     */
    public static String encodeUrl(String value) {
        if (value == null) {
            return "";
        }
        try {
            return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8.toString());
        } catch (Exception e) {
            logger.error("Error encoding URL value: {}", e.getMessage());
            return value;
        }
    }

    /**
     * Декодирование URL параметров
     */
    public static String decodeUrl(String value) {
        if (value == null) {
            return "";
        }
        try {
            return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8.toString());
        } catch (Exception e) {
            logger.error("Error decoding URL value: {}", e.getMessage());
            return value;
        }
    }

    /**
     * Создание случайной задержки для имитации человеческого поведения
     */
    public static void randomDelay(int minMs, int maxMs) {
        try {
            int delay = minMs + random.nextInt(maxMs - minMs);
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Delay interrupted");
        }
    }

    /**
     * Проверка доступности URL
     */
    public static boolean isUrlAccessible(String url) {
        return isUrlAccessible(url, getDefaultUserAgent(), true);
    }

    /**
     * Проверка доступности URL с указанием использования куки
     */
    public static boolean isUrlAccessible(String url, String userAgent, boolean useCookies) {
        try {
            HttpGet request = new HttpGet(url);
            request.setHeader(HttpHeaders.USER_AGENT, userAgent != null ? userAgent : getDefaultUserAgent());

            if (useCookies) {
                String domain = extractDomain(url);
                String cookies = CookieConfig.getCookiesForDomain(domain);
                if (cookies != null && !cookies.isEmpty()) {
                    request.setHeader(HEADER_COOKIE, cookies);
                }
            }

            try (CloseableHttpResponse response = getHttpClient().execute(request)) {
                int statusCode = response.getStatusLine().getStatusCode();
                return statusCode == HttpStatus.SC_OK;
            }
        } catch (Exception e) {
            logger.debug("URL not accessible {}: {}", url, e.getMessage());
            return false;
        }
    }

    /**
     * Получение размера содержимого по URL
     */
    public static long getContentLength(String url) throws Exception {
        return getContentLength(url, getDefaultUserAgent(), true);
    }

    /**
     * Получение размера содержимого по URL с указанием использования куки
     */
    public static long getContentLength(String url, String userAgent, boolean useCookies) throws Exception {
        try {
            HttpGet request = new HttpGet(url);
            request.setHeader(HttpHeaders.USER_AGENT, userAgent != null ? userAgent : getDefaultUserAgent());

            if (useCookies) {
                String domain = extractDomain(url);
                String cookies = CookieConfig.getCookiesForDomain(domain);
                if (cookies != null && !cookies.isEmpty()) {
                    request.setHeader(HEADER_COOKIE, cookies);
                }
            }

            try (CloseableHttpResponse response = getHttpClient().execute(request)) {
                if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                    org.apache.http.Header[] headers = response.getHeaders(HEADER_CONTENT_LENGTH);
                    if (headers.length > 0) {
                        try {
                            return Long.parseLong(headers[0].getValue());
                        } catch (NumberFormatException e) {
                            return -1;
                        }
                    }
                }
                return -1;
            }
        } catch (Exception e) {
            logger.error("Error getting content length for {}: {}", url, e.getMessage());
            throw e;
        }
    }

    /**
     * Проверка валидности куки
     */
    public static boolean testCookies(String url) {
        try {
            logger.info("Testing cookies for URL: {}", url);
            String response = sendGetRequest(url, getDefaultUserAgent(), true);

            // Проверяем ответ на признаки того, что мы авторизованы
            if (response.contains("登录") || response.contains("login") ||
                    response.contains("未登录") || response.contains("未授权") ||
                    response.contains("请登录") || response.contains("signin")) {
                logger.warn("Cookies appear to be invalid or expired");
                return false;
            }

            logger.info("Cookies test successful for: {}", url);
            return true;
        } catch (Exception e) {
            logger.error("Cookies test failed for {}: {}", url, e.getMessage());
            return false;
        }
    }

    /**
     * Закрытие HTTP клиента
     */
    public static synchronized void closeHttpClient() {
        if (httpClient != null) {
            try {
                httpClient.close();
                httpClient = null;
                logger.debug("HTTP client closed");
            } catch (Exception e) {
                logger.error("Error closing HTTP client: {}", e.getMessage());
            }
        }
    }

    /**
     * Создание URL с параметрами
     */
    public static String buildUrlWithParams(String baseUrl, Map<String, String> params) {
        StringBuilder url = new StringBuilder(baseUrl);
        if (params != null && !params.isEmpty()) {
            url.append("?");
            boolean first = true;
            for (Map.Entry<String, String> entry : params.entrySet()) {
                if (!first) {
                    url.append("&");
                }
                url.append(encodeUrl(entry.getKey()))
                        .append("=")
                        .append(encodeUrl(entry.getValue()));
                first = false;
            }
        }
        return url.toString();
    }

    /**
     * Создание URL с параметрами из массива
     */
    public static String buildUrlWithParams(String baseUrl, String[][] params) {
        Map<String, String> paramMap = new HashMap<>();
        if (params != null) {
            for (String[] param : params) {
                if (param.length >= 2) {
                    paramMap.put(param[0], param[1]);
                }
            }
        }
        return buildUrlWithParams(baseUrl, paramMap);
    }

    /**
     * Получение HTTP клиента для внешнего использования
     */
    public static CloseableHttpClient getHttpClientInstance() {
        return getHttpClient();
    }
}