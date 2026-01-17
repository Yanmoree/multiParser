package com.parser.service;

import com.parser.model.Product;
import com.parser.telegram.TelegramNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.Map;

/**
 * Сервис для отправки уведомлений различными способами
 */
public class NotificationService {
    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);

    /**
     * Отправка уведомления о найденных товарах
     */
    public static void sendProductNotification(int userId, List<Product> products, String query) {
        if (products == null || products.isEmpty()) {
            logger.debug("No products to notify for user {}", userId);
            return;
        }

        logger.info("Sending product notification for user {}, query: {}, products: {}",
                userId, query, products.size());

        // Отправка через Telegram
        sendTelegramProductNotification(userId, products, query);

        // Здесь можно добавить другие способы отправки:
        // - Email
        // - Webhook
        // - Discord/Slack
        // - SMS
    }

    /**
     * Отправка уведомления через Telegram
     */
    private static void sendTelegramProductNotification(int userId, List<Product> products, String query) {
        if (products.isEmpty()) {
            return;
        }

        try {
            // Отправляем основное уведомление
            TelegramNotificationService.sendProductsNotification(userId, products.size(), query);

            // Отправляем детали по товарам (первые 5)
            StringBuilder message = new StringBuilder();
            message.append("🛍️ **Детали найденных товаров**\n\n");
            message.append("Запрос: ").append(query).append("\n\n");

            for (int i = 0; i < Math.min(products.size(), 5); i++) {
                Product p = products.get(i);
                message.append(i + 1).append(". ").append(p.getTitle()).append("\n");
                message.append("   💰 ").append(p.getPriceDisplay()).append("\n");
                message.append("   📍 ").append(p.getLocation()).append("\n");
                message.append("   ⏳ ").append(p.getAgeMinutes()).append(" мин\n");
                message.append("   🔗 ").append(p.getUrl()).append("\n\n");
            }

            if (products.size() > 5) {
                message.append("... и еще ").append(products.size() - 5).append(" товаров\n");
            }

            TelegramNotificationService.sendMessage(userId, message.toString());

            // Отправляем изображения первых 3 товаров
            for (int i = 0; i < Math.min(products.size(), 3); i++) {
                Product p = products.get(i);
                if (p.getImages() != null && !p.getImages().isEmpty()) {
                    // Используем метод sendPhotoWithCaption вместо sendPhoto
                    TelegramNotificationService.sendPhotoWithCaption(userId,
                            p.getImages().get(0),
                            "📸 " + p.getTitle());
                }
            }

        } catch (Exception e) {
            logger.error("Error sending Telegram notification for user {}: {}",
                    userId, e.getMessage());
        }
    }

    /**
     * Отправка уведомления об ошибке
     */
    public static void sendErrorNotification(int userId, String errorMessage) {
        logger.error("Sending error notification to user {}: {}", userId, errorMessage);

        // Отправка через Telegram
        TelegramNotificationService.sendErrorNotification(userId, errorMessage);

        // Здесь можно добавить логирование ошибки в файл или систему мониторинга
        logErrorToFile(userId, errorMessage);
    }

    /**
     * Отправка уведомления о состоянии системы
     */
    public static void sendStatusNotification(int userId, String status, String details) {
        logger.info("Sending status notification to user {}: {}", userId, status);

        TelegramNotificationService.sendStatusNotification(userId, status, details);
    }

    /**
     * Отправка административного уведомления
     */
    public static void sendAdminNotification(String message) {
        logger.info("Sending admin notification: {}", message);

        TelegramNotificationService.sendAdminNotification(message);
    }

    /**
     * Отправка тестового уведомления
     */
    public static boolean sendTestNotification(int userId) {
        logger.info("Sending test notification to user {}", userId);

        return TelegramNotificationService.sendTestNotification(userId);
    }

    /**
     * Логирование ошибки в файл
     */
    private static void logErrorToFile(int userId, String errorMessage) {
        // В реальном проекте здесь будет запись в лог-файл
        String logEntry = String.format("[%s] User %d: %s",
                new java.util.Date(), userId, errorMessage);

        // Пример записи в лог
        logger.error("User error: {}", logEntry);
    }

    /**
     * Проверка доступности служб уведомлений
     */
    public static Map<String, Boolean> checkNotificationServices() {
        Map<String, Boolean> status = new java.util.HashMap<>();

        status.put("telegram", TelegramNotificationService.isBotAvailable());
        // Здесь можно добавить проверку других сервисов

        return status;
    }

    /**
     * Получение статистики уведомлений
     */
    public static String getNotificationStats() {
        // В реальном проекте здесь можно вести статистику отправленных уведомлений
        return "Notification service is operational";
    }
}