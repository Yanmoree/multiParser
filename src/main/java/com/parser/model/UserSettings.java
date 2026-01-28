package com.parser.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.parser.config.Config;
import com.parser.config.ParserSettings;
import java.io.Serializable;

/**
 * Класс настроек пользователя для парсера
 */
public class UserSettings implements Serializable {
    private static final long serialVersionUID = 1L;

    // Основные настройки
    private int checkInterval = 300; // секунды
    private int maxAgeMinutes = 1440; // 24 часа
    private int maxPages = 3;
    private int rowsPerPage = 100;

    // Валютные настройки
    private String priceCurrency = ParserSettings.CURRENCY_RUBLES;

    // Настройки уведомлений
    private boolean notifyNewOnly = true;
    private boolean notifyTelegram = true;
    private boolean notifyEmail = false;
    private boolean notifySound = true;

    // Настройки фильтрации
    private double minPrice = 0;
    private double maxPrice = 0; // 0 = без ограничения
    private String[] locations = {};
    private String[] excludedKeywords = {};

    // Расширенные настройки
    private boolean enableProxy = false;
    private String proxyAddress = "";
    private int proxyPort = 0;
    private int requestDelay = 2000; // мс
    private int maxRetries = 3;

    public UserSettings() {
        // Дефолты берём из config.properties (чтобы совпадало с тем, что тестируется в браузере)
        setCheckInterval(Config.getDefaultCheckInterval());
        setMaxAgeMinutes(Config.getDefaultMaxAgeMinutes());
        setMaxPages(Config.getDefaultMaxPages());
        setRowsPerPage(Config.getDefaultRowsPerPage());
    }

    // Геттеры и сеттеры с валидацией

    @JsonProperty("checkInterval")
    public int getCheckInterval() {
        return checkInterval;
    }

    public void setCheckInterval(int checkInterval) {
        this.checkInterval = ParserSettings.normalizeCheckInterval(checkInterval);
    }

    @JsonProperty("maxAgeMinutes")
    public int getMaxAgeMinutes() {
        return maxAgeMinutes;
    }

    public void setMaxAgeMinutes(int maxAgeMinutes) {
        this.maxAgeMinutes = ParserSettings.normalizeMaxAge(maxAgeMinutes);
    }

    @JsonProperty("maxPages")
    public int getMaxPages() {
        return maxPages;
    }

    public void setMaxPages(int maxPages) {
        this.maxPages = Math.max(ParserSettings.MIN_MAX_PAGES,
                Math.min(maxPages, ParserSettings.MAX_MAX_PAGES));
    }

    @JsonProperty("rowsPerPage")
    public int getRowsPerPage() {
        return rowsPerPage;
    }

    public void setRowsPerPage(int rowsPerPage) {
        this.rowsPerPage = Math.max(ParserSettings.MIN_ROWS_PER_PAGE,
                Math.min(rowsPerPage, ParserSettings.MAX_ROWS_PER_PAGE));
    }

    @JsonProperty("priceCurrency")
    public String getPriceCurrency() {
        return priceCurrency;
    }

    public void setPriceCurrency(String priceCurrency) {
        if (ParserSettings.CURRENCY_YUAN.equals(priceCurrency) ||
                ParserSettings.CURRENCY_RUBLES.equals(priceCurrency)) {
            this.priceCurrency = priceCurrency;
        }
    }

    @JsonProperty("notifyNewOnly")
    public boolean isNotifyNewOnly() {
        return notifyNewOnly;
    }

    public void setNotifyNewOnly(boolean notifyNewOnly) {
        this.notifyNewOnly = notifyNewOnly;
    }

    @JsonProperty("notifyTelegram")
    public boolean isNotifyTelegram() {
        return notifyTelegram;
    }

    public void setNotifyTelegram(boolean notifyTelegram) {
        this.notifyTelegram = notifyTelegram;
    }

    @JsonProperty("notifyEmail")
    public boolean isNotifyEmail() {
        return notifyEmail;
    }

    public void setNotifyEmail(boolean notifyEmail) {
        this.notifyEmail = notifyEmail;
    }

    @JsonProperty("notifySound")
    public boolean isNotifySound() {
        return notifySound;
    }

    public void setNotifySound(boolean notifySound) {
        this.notifySound = notifySound;
    }

    @JsonProperty("minPrice")
    public double getMinPrice() {
        return minPrice;
    }

    public void setMinPrice(double minPrice) {
        this.minPrice = Math.max(0, minPrice);
    }

    @JsonProperty("maxPrice")
    public double getMaxPrice() {
        return maxPrice;
    }

    public void setMaxPrice(double maxPrice) {
        this.maxPrice = maxPrice < 0 ? 0 : maxPrice;
    }

    @JsonProperty("locations")
    public String[] getLocations() {
        return locations != null ? locations.clone() : new String[0];
    }

    public void setLocations(String[] locations) {
        this.locations = locations != null ? locations.clone() : new String[0];
    }

    @JsonProperty("excludedKeywords")
    public String[] getExcludedKeywords() {
        return excludedKeywords != null ? excludedKeywords.clone() : new String[0];
    }

    public void setExcludedKeywords(String[] excludedKeywords) {
        this.excludedKeywords = excludedKeywords != null ? excludedKeywords.clone() : new String[0];
    }

    @JsonProperty("enableProxy")
    public boolean isEnableProxy() {
        return enableProxy;
    }

    public void setEnableProxy(boolean enableProxy) {
        this.enableProxy = enableProxy;
    }

    @JsonProperty("proxyAddress")
    public String getProxyAddress() {
        return proxyAddress;
    }

    public void setProxyAddress(String proxyAddress) {
        this.proxyAddress = proxyAddress != null ? proxyAddress.trim() : "";
    }

    @JsonProperty("proxyPort")
    public int getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(int proxyPort) {
        this.proxyPort = Math.max(0, Math.min(proxyPort, 65535));
    }

    @JsonProperty("requestDelay")
    public int getRequestDelay() {
        return requestDelay;
    }

    public void setRequestDelay(int requestDelay) {
        this.requestDelay = Math.max(500, Math.min(requestDelay, 10000));
    }

    @JsonProperty("maxRetries")
    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = Math.max(0, Math.min(maxRetries, 10));
    }

    // Методы для удобства

    @JsonIgnore
    public boolean hasPriceFilter() {
        return maxPrice > 0 && maxPrice > minPrice;
    }

    @JsonIgnore
    public boolean hasLocationFilter() {
        return locations != null && locations.length > 0;
    }

    @JsonIgnore
    public boolean hasKeywordFilter() {
        return excludedKeywords != null && excludedKeywords.length > 0;
    }

    @JsonIgnore
    public boolean isPriceInRange(double price) {
        if (minPrice > 0 && price < minPrice) {
            return false;
        }
        if (maxPrice > 0 && price > maxPrice) {
            return false;
        }
        return true;
    }

    @JsonIgnore
    public boolean isLocationAllowed(String location) {
        if (!hasLocationFilter()) {
            return true;
        }

        if (location == null || location.trim().isEmpty()) {
            return false;
        }

        String locationLower = location.toLowerCase();
        for (String allowedLocation : locations) {
            if (locationLower.contains(allowedLocation.toLowerCase())) {
                return true;
            }
        }

        return false;
    }

    @JsonIgnore
    public boolean isKeywordAllowed(String text) {
        if (!hasKeywordFilter() || text == null) {
            return true;
        }

        String textLower = text.toLowerCase();
        for (String keyword : excludedKeywords) {
            if (keyword != null && !keyword.trim().isEmpty() &&
                    textLower.contains(keyword.toLowerCase())) {
                return false;
            }
        }

        return true;
    }

    @JsonIgnore
    public boolean isValid() {
        return ParserSettings.isValidCheckInterval(checkInterval) &&
                ParserSettings.isValidMaxAge(maxAgeMinutes) &&
                maxPages >= ParserSettings.MIN_MAX_PAGES &&
                rowsPerPage >= ParserSettings.MIN_ROWS_PER_PAGE;
    }

    @JsonIgnore
    public String getSummary() {
        return String.format(
                "Интервал: %d сек, Возраст: %d мин, Страниц: %d, Товаров на странице: %d",
                checkInterval, maxAgeMinutes, maxPages, rowsPerPage
        );
    }

    @Override
    public String toString() {
        return String.format(
                "UserSettings{checkInterval=%d, maxAge=%d, maxPages=%d, rowsPerPage=%d}",
                checkInterval, maxAgeMinutes, maxPages, rowsPerPage
        );
    }
}