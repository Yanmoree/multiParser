package com.parser.parser;

import com.parser.config.Config;
import com.parser.model.Product;
import com.parser.service.CookieService;
import com.parser.util.HttpUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * Парсер для сайта Goofish (闲鱼) с поддержкой динамических кук
 */
public class GoofishParser extends BaseParser {
    private static final Logger logger = LoggerFactory.getLogger(GoofishParser.class);

    // API endpoints
    private static final String SEARCH_ENDPOINT = "/h5/mtop.taobao.idlemtopsearch.pc.search/1.0/";
    private static final String APP_KEY = "34839810";

    // Статистика попыток обновления кук
    private int cookieRefreshAttempts = 0;
    private long lastCookieRefreshTime = 0;

    public GoofishParser() {
        super("goofish", Config.getGoofishBaseUrl());
    }

    @Override
    protected String buildSearchUrl(String query, int page, int rows) {
        try {
            // Используем параметры как в Python коде
            Map<String, String> params = new LinkedHashMap<>();
            params.put("jsv", "2.7.2");
            params.put("appKey", APP_KEY);
            params.put("t", String.valueOf(System.currentTimeMillis()));
            params.put("sign", "dummy_sign"); // Будет заменен при запросе
            params.put("v", "1.0");
            params.put("type", "originaljson");
            params.put("accountSite", "xianyu");
            params.put("dataType", "json");
            params.put("timeout", "20000");
            params.put("api", "mtop.taobao.idlemtopsearch.pc.search");
            params.put("sessionOption", "AutoLoginOnly");
            params.put("spm_cnt", "a21ybx.search.0.0");
            params.put("spm_pre", "a21ybx.search.searchInput.0");

            // Формируем data параметр как в Python
            JSONObject dataJson = new JSONObject();
            dataJson.put("pageNumber", page);
            dataJson.put("keyword", query);
            dataJson.put("fromFilter", false);
            dataJson.put("rowsPerPage", Math.min(rows, Config.getGoofishMaxProductsPerPage()));
            dataJson.put("sortValue", "new");
            dataJson.put("sortField", "");
            dataJson.put("customDistance", "");
            dataJson.put("gps", "");
            dataJson.put("propValueStr", new JSONObject());
            dataJson.put("customGps", "");
            dataJson.put("searchReqFromPage", "pcSearch");
            dataJson.put("extraFilterValue", "{}");
            dataJson.put("userPositionJson", "{}");

            params.put("data", dataJson.toString());

            String url = baseUrl + SEARCH_ENDPOINT;
            return HttpUtils.buildUrlWithParams(url, params);

        } catch (Exception e) {
            logger.error("Error building search URL: {}", e.getMessage(), e);
            return "";
        }
    }

    @Override
    protected List<Product> parseResponse(String response, String query) {
        List<Product> products = new ArrayList<>();

        if (response == null || response.isEmpty()) {
            logger.warn("Empty response received");
            return products;
        }

        try {
            logger.debug("Parsing response ({} chars): {}", response.length(),
                    response.length() > 200 ? response.substring(0, 200) + "..." : response);

            JSONObject json = new JSONObject(response);

            // Исправленная проверка статуса
            String ret = json.optString("ret", "");
            String status = json.optString("status", "");

            logger.debug("API response - ret: '{}', status: '{}'", ret, status);

            // Проверяем разные варианты ошибок
            if (!status.equals("SUCCESS") && !ret.isEmpty()) {
                // Если статус не SUCCESS и ret не пустой - это ошибка
                String retMsg = json.optString("msg", "");

                logger.error("API returned error: {}, msg: {}", ret, retMsg);

                // Проверка на ошибки авторизации
                if (ret.contains("FAIL_SYS_TOKEN_ILLEGAL") ||
                        ret.contains("FAIL_SYS_SESSION_EXPIRED") ||
                        ret.contains("FAIL_SYS_TOKEN") ||
                        retMsg.contains("登录") || retMsg.contains("session") ||
                        retMsg.contains("未登录") || retMsg.contains("未授权") ||
                        retMsg.contains("令牌")) {
                    logger.error("Token error detected: {}", retMsg);
                    throw new RuntimeException("Token error - need fresh cookies");
                }

                // Если это не ошибка авторизации, просто возвращаем пустой список
                return products;
            }

            // Если ret содержит SUCCESS, но в странном формате ["SUCCESS::调用成功"]
            // Это может быть валидный ответ
            if (ret.contains("SUCCESS")) {
                logger.debug("Response contains 'SUCCESS' in ret field, continuing parsing...");
            }

            // Получение данных
            JSONObject data = json.optJSONObject("data");
            if (data == null) {
                logger.warn("No data object in response");

                // Попробуем получить данные другим способом
                if (json.has("data") && !json.isNull("data")) {
                    Object dataObj = json.get("data");
                    if (dataObj instanceof JSONObject) {
                        data = (JSONObject) dataObj;
                    } else if (dataObj instanceof JSONArray) {
                        JSONArray dataArray = (JSONArray) dataObj;
                        logger.debug("Data is array with {} elements", dataArray.length());
                        // Обработка массива если нужно
                    }
                }

                if (data == null) {
                    return products;
                }
            }

            // Получение списка товаров
            JSONArray resultList = data.optJSONArray("resultList");
            if (resultList == null || resultList.length() == 0) {
                // Попробуем другие ключи
                String[] possibleKeys = {"items", "list", "result", "dataList", "resultData"};
                for (String key : possibleKeys) {
                    if (data.has(key) && data.get(key) instanceof JSONArray) {
                        resultList = data.getJSONArray(key);
                        logger.debug("Found products in key '{}': {} items", key, resultList.length());
                        break;
                    }
                }

                if (resultList == null || resultList.length() == 0) {
                    logger.info("No products found in response");
                    return products;
                }
            }

            logger.debug("Found {} items in response", resultList.length());

            // Парсинг каждого товара
            int parsedCount = 0;
            for (int i = 0; i < resultList.length(); i++) {
                try {
                    JSONObject item = resultList.getJSONObject(i);
                    Product product = parseProductItem(item, query);

                    if (product != null && isValidProduct(product)) {
                        products.add(product);
                        parsedCount++;
                    }

                } catch (Exception e) {
                    logger.warn("Error parsing item {}: {}", i, e.getMessage());
                    continue;
                }
            }

            logger.info("Successfully parsed {} products from {} items in response",
                    parsedCount, resultList.length());

        } catch (Exception e) {
            logger.error("Error parsing Goofish response: {}", e.getMessage(), e);
            // Если это ошибка сессии, пробрасываем дальше для обновления кук
            if (e.getMessage() != null && e.getMessage().contains("Token error")) {
                throw e;
            }
        }

        return products;
    }

    /**
     * Основной метод поиска товаров с динамическими куками
     */
    @Override
    public List<Product> search(String query, int maxPages, int rowsPerPage, int maxAgeMinutes) {
        List<Product> allProducts = new ArrayList<>();
        long startTime = System.currentTimeMillis();

        logger.info("🔍 Starting search for '{}' on {}, pages: {}, rows: {}, max age: {}min",
                query, siteName, maxPages, rowsPerPage, maxAgeMinutes);

        for (int page = 1; page <= maxPages; page++) {
            boolean shouldRetry = true;
            int retryCount = 0;
            int maxRetries = Config.getHttpMaxRetries();

            while (shouldRetry && retryCount <= maxRetries) {
                try {
                    // Обновляем куки перед первым запросом или если давно не обновляли
                    if (page == 1 || shouldRefreshCookies()) {
                        refreshCookiesIfNeeded();
                    }

                    // Построение URL с актуальной подписью
                    String url = buildSearchUrlWithSignature(query, page, rowsPerPage);
                    logger.debug("📡 Fetching page {} (attempt {}): {}", page, retryCount + 1, url);

                    // Выполнение запроса
                    long requestStartTime = System.currentTimeMillis();
                    String response = HttpUtils.sendGetRequest(url, userAgent, true);
                    totalRequests++;

                    long requestTime = System.currentTimeMillis() - requestStartTime;
                    logger.debug("📥 Page {} fetched in {}ms", page, requestTime);

                    // Отладочный вывод ответа
                    logger.debug("📄 Response ({} chars): {}",
                            response.length(),
                            response.length() > 500 ? response.substring(0, 500) + "..." : response);

                    // Парсинг ответа
                    long parseStartTime = System.currentTimeMillis();
                    List<Product> products = parseResponse(response, query);
                    totalParseTime += System.currentTimeMillis() - parseStartTime;

                    if (products.isEmpty()) {
                        logger.debug("📭 No products found on page {}", page);
                        // Если на первой странице нет товаров, прекращаем поиск
                        if (page == 1) {
                            logger.warn("⚠️ No products found on first page for query: '{}'", query);
                        }
                        break;
                    }

                    // Фильтрация по возрасту
                    List<Product> filtered = new ArrayList<>();
                    for (Product product : products) {
                        if (product.getAgeMinutes() <= maxAgeMinutes) {
                            filtered.add(product);
                        } else {
                            logger.trace("Product filtered by age: {}min > {}min",
                                    product.getAgeMinutes(), maxAgeMinutes);
                        }
                    }

                    allProducts.addAll(filtered);
                    logger.info("📊 Page {}: found {} products ({} after age filter)",
                            page, products.size(), filtered.size());

                    // Проверка, нужно ли продолжать
                    if (filtered.size() < rowsPerPage) {
                        logger.debug("⏹️ Last page reached (fewer products than rows per page)");
                        break;
                    }

                    // Задержка между запросами для избежания блокировки
                    int delay = getRequestDelay();
                    if (delay > 0 && page < maxPages) {
                        logger.trace("⏱️ Waiting {}ms before next request", delay);
                        try {
                            Thread.sleep(delay);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw ie;
                        }
                    }

                    shouldRetry = false; // Успешно, не нужно повторять

                } catch (Exception e) {
                    failedRequests++;
                    retryCount++;

                    if (isTokenError(e)) {
                        logger.warn("🔑 Token error on page {}: {}", page, e.getMessage());

                        if (retryCount <= maxRetries) {
                            logger.info("🔄 Attempting to refresh cookies and retry (attempt {}/{})",
                                    retryCount, maxRetries);
                            // Принудительно обновляем куки
                            forceRefreshCookies();
                            // Задержка перед повторной попыткой
                            try {
                                Thread.sleep(Config.getHttpRetryDelay());
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                throw new RuntimeException(ie);
                            }
                            continue; // Повторяем попытку
                        } else {
                            logger.error("❌ Max retries exceeded for token refresh on page {}", page);
                            try {
                                throw e;
                            } catch (Exception ex) {
                                throw new RuntimeException(ex);
                            }
                        }
                    } else {
                        logger.error("❌ Non-token error parsing page {}: {}", page, e.getMessage(), e);
                        shouldRetry = false; // Не повторяем для других ошибок

                        // Если это не последняя страница, продолжаем
                        if (page < maxPages) {
                            // Задержка при ошибке
                            try {
                                Thread.sleep(5000);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                        break;
                    }
                }
            }

            // Если вышли из цикла с shouldRetry=true, значит превышены попытки
            if (shouldRetry) {
                logger.error("❌ Failed to fetch page {} after {} retries", page, retryCount);
                break;
            }
        }

        long totalTime = System.currentTimeMillis() - startTime;
        logger.info("✅ Search completed for '{}': found {} products in {}ms ({} requests, {} cookie refreshes)",
                query, allProducts.size(), totalTime, totalRequests, cookieRefreshAttempts);

        return allProducts;
    }

    /**
     * Построение URL с подписью как в Python коде
     */
    private String buildSearchUrlWithSignature(String query, int page, int rows) {
        try {
            // Получаем токен из кук
            Map<String, String> cookies = CookieService.getCookiesForDomain("h5api.m.goofish.com");
            String tokenFull = cookies.get("_m_h5_tk");
            String token = "";

            if (tokenFull != null && tokenFull.contains("_")) {
                String[] parts = tokenFull.split("_");
                token = parts[0];
                logger.debug("Extracted token from _m_h5_tk: {} (full: {})", token, tokenFull);
            } else {
                logger.warn("No _m_h5_tk token found in cookies or invalid format");
                // Используем значение из кук или генерируем
                token = cookies.getOrDefault("_tb_token_", generateDefaultToken());
                logger.warn("Using fallback token: {}", token);
            }

            // Формируем данные как в Python
            JSONObject dataJson = new JSONObject();
            dataJson.put("pageNumber", page);
            dataJson.put("keyword", query);
            dataJson.put("fromFilter", false);
            dataJson.put("rowsPerPage", Math.min(rows, Config.getGoofishMaxProductsPerPage()));
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
            String timestamp = String.valueOf(System.currentTimeMillis());

            // Генерируем подпись как в Python
            String signString = token + "&" + timestamp + "&" + APP_KEY + "&" + dataStr;
            String signature = generateMD5(signString);
            logger.debug("Generated signature: {} for token: {}, timestamp: {}", signature, token, timestamp);

            // Формируем параметры
            Map<String, String> params = new LinkedHashMap<>();
            params.put("jsv", "2.7.2");
            params.put("appKey", APP_KEY);
            params.put("t", timestamp);
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

            String url = baseUrl + SEARCH_ENDPOINT;
            return HttpUtils.buildUrlWithParams(url, params);

        } catch (Exception e) {
            logger.error("Error building URL with signature: {}", e.getMessage());
            return buildSearchUrl(query, page, rows); // Fallback
        }
    }

    /**
     * Генерация MD5 как в Python
     */
    private String generateMD5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : digest) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            logger.error("Error generating MD5: {}", e.getMessage());
            return "dummy_signature_" + System.currentTimeMillis();
        }
    }

    /**
     * Генерация дефолтного токена
     */
    private String generateDefaultToken() {
        return "abcdef" + System.currentTimeMillis() % 100000;
    }

    /**
     * Проверка, связана ли ошибка с токеном
     */
    private boolean isTokenError(Exception e) {
        if (e == null || e.getMessage() == null) {
            return false;
        }

        String message = e.getMessage().toLowerCase();
        return message.contains("token") ||
                message.contains("illegal") ||
                message.contains("令牌") ||
                message.contains("登录") ||
                message.contains("auth") ||
                message.contains("401") ||
                message.contains("403") ||
                message.contains("unauthorized") ||
                message.contains("forbidden");
    }

    /**
     * Проверяем, нужно ли обновить куки
     */
    private boolean shouldRefreshCookies() {
        long currentTime = System.currentTimeMillis();
        long timeSinceLastRefresh = currentTime - lastCookieRefreshTime;
        // Обновляем куки каждые 30 минут или если давно не обновляли
        return timeSinceLastRefresh > (30 * 60 * 1000);
    }

    /**
     * Обновление куки если нужно
     */
    private void refreshCookiesIfNeeded() {
        if (shouldRefreshCookies()) {
            try {
                logger.info("🔄 Обновление cookies...");
                boolean success = CookieService.refreshCookies("h5api.m.goofish.com");
                if (success) {
                    cookieRefreshAttempts++;
                    lastCookieRefreshTime = System.currentTimeMillis();
                } else {
                    logger.warn("⚠️ Не удалось обновить cookies");
                }
            } catch (Exception e) {
                logger.warn("⚠️ Ошибка при обновлении cookies: {}", e.getMessage());
            }
        }
    }

    /**
     * Принудительное обновление кук
     */
    private void forceRefreshCookies() {
        try {
            logger.info("🔄 Принудительное обновление cookies...");
            boolean success = CookieService.refreshCookies("h5api.m.goofish.com");

            if (success) {
                cookieRefreshAttempts++;
                lastCookieRefreshTime = System.currentTimeMillis();
                logger.info("✅ Cookies успешно обновлены");
            } else {
                logger.error("❌ Не удалось обновить cookies");
                throw new RuntimeException("Failed to refresh cookies");
            }

        } catch (Exception e) {
            logger.error("❌ Ошибка при обновлении cookies: {}", e.getMessage());
            throw new RuntimeException("Failed to refresh cookies: " + e.getMessage(), e);
        }
    }

    /**
     * Парсинг отдельного товара
     */
    private Product parseProductItem(JSONObject item, String query) {
        try {
            // Извлечение основных данных
            JSONObject data = item.optJSONObject("data");
            if (data == null) {
                logger.trace("No data object in item");
                return null;
            }

            JSONObject itemData = data.optJSONObject("item");
            if (itemData == null) {
                logger.trace("No item object in data");
                return null;
            }

            // Получение ID товара
            String id = extractItemId(itemData);
            if (id == null || id.isEmpty() || id.equals("None")) {
                logger.trace("No item ID found");
                return null;
            }

            // Получение основной информации
            JSONObject main = itemData.optJSONObject("main");
            JSONObject clickParam = main != null ? main.optJSONObject("clickParam") : null;
            JSONObject args = clickParam != null ? clickParam.optJSONObject("args") : null;

            // Получение дополнительной информации
            JSONObject extra = itemData.optJSONObject("extra");

            // Создание объекта товара
            Product product = new Product();
            product.setId(id);
            product.setSite("goofish");
            product.setQuery(query);

            // Установка названия
            String title = extractTitle(main, args, extra);
            product.setTitle(title);

            // Установка цены
            double price = extractPrice(main, args, extra);
            product.setPrice(price);

            // Установка URL
            product.setUrl(buildProductUrl(id));

            // Установка местоположения
            String location = extractLocation(args, extra);
            product.setLocation(location);

            // Установка возраста товара
            int ageMinutes = extractAgeMinutes(args, extra);
            product.setAgeMinutes(ageMinutes);

            // Установка продавца
            String seller = extractSeller(args, extra);
            product.setSeller(seller);

            // Установка рейтинга продавца
            String sellerRating = extractSellerRating(extra);
            product.setSellerRating(sellerRating);

            // Установка категории
            String category = extractCategory(args, extra);
            product.setCategory(category);

            // Установка изображений
            List<String> images = extractImages(main, extra);
            product.setImages(images);

            logger.debug("Parsed product: {} ({}¥)", product.getShortTitle(), price);
            return product;

        } catch (Exception e) {
            logger.error("Error parsing product item: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Извлечение ID товара
     */
    private String extractItemId(JSONObject itemData) {
        // Попытка получить ID из разных мест
        JSONObject main = itemData.optJSONObject("main");
        if (main != null) {
            JSONObject clickParam = main.optJSONObject("clickParam");
            if (clickParam != null) {
                JSONObject args = clickParam.optJSONObject("args");
                if (args != null) {
                    String id = args.optString("id");
                    if (id != null && !id.isEmpty()) {
                        return id;
                    }
                }
            }
        }

        // Альтернативные пути
        JSONObject extra = itemData.optJSONObject("extra");
        if (extra != null) {
            String id = extra.optString("itemId");
            if (id != null && !id.isEmpty()) {
                return id;
            }
        }

        return null;
    }

    /**
     * Извлечение названия товара
     */
    private String extractTitle(JSONObject main, JSONObject args, JSONObject extra) {
        if (main != null) {
            String title = main.optString("title");
            if (title != null && !title.isEmpty()) {
                return cleanString(title);
            }
        }

        if (args != null) {
            String title = args.optString("title");
            if (title != null && !title.isEmpty()) {
                return cleanString(title);
            }
        }

        if (extra != null) {
            String title = extra.optString("title");
            if (title != null && !title.isEmpty()) {
                return cleanString(title);
            }
        }

        return "Без названия";
    }

    /**
     * Извлечение цены
     */
    private double extractPrice(JSONObject main, JSONObject args, JSONObject extra) {
        // Приоритет 1: из main
        if (main != null) {
            JSONObject priceInfo = main.optJSONObject("priceInfo");
            if (priceInfo != null) {
                String priceStr = priceInfo.optString("price");
                if (priceStr != null && !priceStr.isEmpty()) {
                    return extractPriceFromString(priceStr);
                }
            }
        }

        // Приоритет 2: из args
        if (args != null) {
            String priceStr = args.optString("price");
            if (priceStr != null && !priceStr.isEmpty()) {
                return extractPriceFromString(priceStr);
            }
        }

        // Приоритет 3: из extra
        if (extra != null) {
            String priceStr = extra.optString("price");
            if (priceStr != null && !priceStr.isEmpty()) {
                return extractPriceFromString(priceStr);
            }

            JSONObject priceInfo = extra.optJSONObject("price");
            if (priceInfo != null) {
                priceStr = priceInfo.optString("priceText");
                if (priceStr != null && !priceStr.isEmpty()) {
                    return extractPriceFromString(priceStr);
                }
            }
        }

        return 0.0;
    }

    /**
     * Извлечение цены из строки
     */
    private double extractPriceFromString(String priceStr) {
        if (priceStr == null || priceStr.isEmpty()) {
            return 0.0;
        }

        try {
            // Удаление всех нецифровых символов, кроме точки
            String clean = priceStr.replaceAll("[^\\d.,]", "");

            // Замена запятой на точку, если необходимо
            clean = clean.replace(',', '.');

            // Удаление лишних точек
            int firstDot = clean.indexOf('.');
            if (firstDot != -1) {
                int lastDot = clean.lastIndexOf('.');
                if (firstDot != lastDot) {
                    clean = clean.substring(0, firstDot + 1) +
                            clean.substring(firstDot + 1).replace(".", "");
                }
            }

            double price = Double.parseDouble(clean);
            // В Python коде цена делится на 100
            return price / 100.0;
        } catch (NumberFormatException e) {
            logger.warn("Failed to parse price: {}", priceStr);
            return 0.0;
        }
    }

    /**
     * Построение URL товара
     */
    private String buildProductUrl(String id) {
        return "https://www.goofish.com/item?id=" + id;
    }

    /**
     * Извлечение местоположения
     */
    private String extractLocation(JSONObject args, JSONObject extra) {
        if (args != null) {
            String location = args.optString("area");
            if (location != null && !location.isEmpty()) {
                return cleanString(location);
            }
        }

        if (extra != null) {
            String location = extra.optString("area");
            if (location != null && !location.isEmpty()) {
                return cleanString(location);
            }
        }

        return "Не указано";
    }

    /**
     * Извлечение возраста товара
     */
    private int extractAgeMinutes(JSONObject args, JSONObject extra) {
        // Попытка получить время публикации
        long publishTime = 0;

        if (args != null) {
            publishTime = args.optLong("publishTime", 0);
        }

        if (publishTime == 0 && extra != null) {
            publishTime = extra.optLong("publishTime", 0);
        }

        if (publishTime > 0) {
            return calculateAgeMinutes(publishTime);
        }

        // Альтернатива: из строки времени
        if (extra != null) {
            String timeText = extra.optString("timeText");
            if (timeText != null && !timeText.isEmpty()) {
                return parseTimeText(timeText);
            }
        }

        return 0;
    }

    /**
     * Парсинг текстового представления времени
     */
    private int parseTimeText(String timeText) {
        try {
            timeText = timeText.toLowerCase();

            if (timeText.contains("刚刚") || timeText.contains("just")) {
                return 1; // Только что
            }

            if (timeText.contains("分钟") || timeText.contains("min")) {
                String numStr = timeText.replaceAll("[^\\d]", "");
                if (!numStr.isEmpty()) {
                    return Integer.parseInt(numStr);
                }
            }

            if (timeText.contains("小时") || timeText.contains("hour")) {
                String numStr = timeText.replaceAll("[^\\d]", "");
                if (!numStr.isEmpty()) {
                    return Integer.parseInt(numStr) * 60;
                }
            }

            if (timeText.contains("天") || timeText.contains("day")) {
                String numStr = timeText.replaceAll("[^\\d]", "");
                if (!numStr.isEmpty()) {
                    return Integer.parseInt(numStr) * 1440;
                }
            }

        } catch (NumberFormatException e) {
            logger.warn("Failed to parse time text: {}", timeText);
        }

        return 0;
    }

    /**
     * Извлечение информации о продавце
     */
    private String extractSeller(JSONObject args, JSONObject extra) {
        if (args != null) {
            String seller = args.optString("nick");
            if (seller != null && !seller.isEmpty()) {
                return cleanString(seller);
            }
        }

        if (extra != null) {
            String seller = extra.optString("sellerNick");
            if (seller != null && !seller.isEmpty()) {
                return cleanString(seller);
            }
        }

        return "Не указан";
    }

    /**
     * Извлечение рейтинга продавца
     */
    private String extractSellerRating(JSONObject extra) {
        if (extra != null) {
            JSONObject sellerInfo = extra.optJSONObject("sellerInfo");
            if (sellerInfo != null) {
                String rating = sellerInfo.optString("rate");
                if (rating != null && !rating.isEmpty()) {
                    return rating;
                }
            }
        }

        return "Нет рейтинга";
    }

    /**
     * Извлечение категории товара
     */
    private String extractCategory(JSONObject args, JSONObject extra) {
        if (args != null) {
            String category = args.optString("category");
            if (category != null && !category.isEmpty()) {
                return cleanString(category);
            }
        }

        if (extra != null) {
            String category = extra.optString("categoryName");
            if (category != null && !category.isEmpty()) {
                return cleanString(category);
            }
        }

        return "Другое";
    }

    /**
     * Извлечение изображений товара
     */
    private List<String> extractImages(JSONObject main, JSONObject extra) {
        List<String> images = new ArrayList<>();

        // Попытка получить из main
        if (main != null) {
            JSONArray imageArray = main.optJSONArray("images");
            if (imageArray != null) {
                for (int i = 0; i < imageArray.length(); i++) {
                    String imageUrl = imageArray.optString(i);
                    if (imageUrl != null && !imageUrl.isEmpty()) {
                        images.add(imageUrl);
                        if (images.size() >= 5) break; // Ограничение на количество
                    }
                }
            }
        }

        // Попытка получить из extra
        if (extra != null && images.isEmpty()) {
            String imageUrl = extra.optString("picUrl");
            if (imageUrl != null && !imageUrl.isEmpty()) {
                images.add(imageUrl);
            }
        }

        return images;
    }

    /**
     * Проверка валидности товара
     */
    private boolean isValidProduct(Product product) {
        if (product == null) {
            return false;
        }

        // Проверка обязательных полей
        if (product.getId() == null || product.getId().isEmpty()) {
            logger.trace("Product rejected: missing ID");
            return false;
        }

        if (product.getTitle() == null || product.getTitle().isEmpty() ||
                product.getTitle().equals("Без названия")) {
            logger.trace("Product rejected: invalid title");
            return false;
        }

        if (product.getPrice() <= 0) {
            logger.trace("Product rejected: invalid price");
            return false;
        }

        if (product.getUrl() == null || product.getUrl().isEmpty()) {
            logger.trace("Product rejected: missing URL");
            return false;
        }

        // Дополнительные проверки
        if (product.getAgeMinutes() < 0) {
            logger.trace("Product rejected: negative age");
            return false;
        }

        return true;
    }

    @Override
    protected int getRequestDelay() {
        return Config.getGoofishDelayBetweenRequests();
    }

    @Override
    protected boolean shouldStopOnError(Exception e) {
        // Прекращаем поиск при определенных ошибках
        String message = e.getMessage();
        return message != null && (
                message.contains("403") || // Forbidden
                        message.contains("429") || // Too Many Requests
                        message.contains("401") || // Unauthorized
                        message.contains("blocked") ||
                        message.contains("captcha")
        );
    }
}