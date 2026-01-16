package com.parser.telegram;

import com.parser.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

/**
 * Сервис для отправки уведомлений через Telegram
 */
public class TelegramNotificationService {
    private static final Logger logger = LoggerFactory.getLogger(TelegramNotificationService.class);

    private static TelegramBotService botInstance;

    /**
     * Установка экземпляра бота для отправки уведомлений
     */
    public static void setBotInstance(TelegramBotService bot) {
        botInstance = bot;
        logger.info("TelegramNotificationService initialized with bot instance");
    }

    /**
     * Отправка текстового сообщения
     */
    public static boolean sendMessage(int userId, String text) {
        return sendMessage(userId, text, false);
    }

    /**
     * Отправка текстового сообщения с опцией Markdown
     */
    public static boolean sendMessage(int userId, String text, boolean useMarkdown) {
        if (botInstance == null) {
            logger.error("Bot instance not set for TelegramNotificationService");
            return false;
        }

        if (text == null || text.trim().isEmpty()) {
            logger.warn("Attempted to send empty message to user {}", userId);
            return false;
        }

        try {
            SendMessage message = new SendMessage();
            message.setChatId(String.valueOf(userId));
            message.setText(text);

            if (useMarkdown) {
                // Экранируем специальные символы для Markdown
                text = escapeMarkdown(text);
                message.setText(text);
                message.enableMarkdown(true);
            }

            botInstance.execute(message);
            logger.debug("Message sent to user {}: {}", userId, text.substring(0, Math.min(50, text.length())));
            return true;

        } catch (TelegramApiException e) {
            logger.error("Error sending message to user {}: {}", userId, e.getMessage());

            // Пробуем отправить без Markdown если была ошибка форматирования
            if (e.getMessage().contains("can't parse entities") && useMarkdown) {
                logger.info("Retrying without Markdown formatting...");
                return sendMessage(userId, text, false);
            }
            return false;
        }
    }

    /**
     * Экранирование специальных символов для Markdown
     */
    private static String escapeMarkdown(String text) {
        if (text == null) return "";

        // Экранируем символы которые могут сломать Markdown
        String[] specialChars = {"_", "*", "[", "]", "(", ")", "~", "`", ">", "#", "+", "-", "=", "|", "{", "}", ".", "!"};

        for (String ch : specialChars) {
            text = text.replace(ch, "\\" + ch);
        }

        return text;
    }

    /**
     * Отправка сообщения с фото
     */
    public static boolean sendPhoto(int userId, String photoUrl, String caption) {
        if (botInstance == null) {
            logger.error("Bot instance not set for TelegramNotificationService");
            return false;
        }

        try {
            org.telegram.telegrambots.meta.api.methods.send.SendPhoto photo =
                    new org.telegram.telegrambots.meta.api.methods.send.SendPhoto();
            photo.setChatId(String.valueOf(userId));
            photo.setPhoto(new org.telegram.telegrambots.meta.api.objects.InputFile(photoUrl));

            if (caption != null && !caption.isEmpty()) {
                photo.setCaption(caption);
                if (caption.length() > 1024) {
                    photo.setCaption(caption.substring(0, 1024));
                }
            }

            botInstance.execute(photo);
            logger.debug("Photo sent to user {}: {}", userId, photoUrl);
            return true;

        } catch (Exception e) {
            logger.error("Error sending photo to user {}: {}", userId, e.getMessage());
            return false;
        }
    }

    /**
     * Отправка тестового уведомления
     */
    public static boolean sendTestNotification(int userId) {
        logger.info("Sending test notification to user {}", userId);

        String message = "✅ Test notification\n\n" +
                "Parser is working correctly!\n" +
                "This is a test message to confirm that the notification system is functioning.\n\n" +
                "Time: " + new java.util.Date();

        return sendMessage(userId, message);
    }

    /**
     * Отправка уведомления о найденных товарах
     */
    public static boolean sendProductsNotification(int userId, int count, String query) {
        String message = String.format("🛍️ Found products!\n\n" +
                "Query: %s\n" +
                "Products found: %d\n\n" +
                "Details in the next message...", query, count);

        return sendMessage(userId, message);
    }

    /**
     * Отправка уведомления об ошибке
     */
    public static boolean sendErrorNotification(int userId, String errorMessage) {
        String message = String.format("❌ Parser error\n\n" +
                "An error occurred:\n" +
                "%s\n\n" +
                "The parser will be restarted automatically.", errorMessage);

        return sendMessage(userId, message);
    }

    /**
     * Отправка уведомления о состоянии парсера
     */
    public static boolean sendStatusNotification(int userId, String status, String details) {
        String emoji = "🟢";
        if (status.contains("stopped")) emoji = "🔴";
        if (status.contains("paused")) emoji = "⏸️";
        if (status.contains("error")) emoji = "❌";

        String message = String.format("%s Parser status changed\n\n" +
                "New status: %s\n\n" +
                "%s", emoji, status, details);

        return sendMessage(userId, message);
    }

    /**
     * Отправка уведомления администратору
     */
    public static boolean sendAdminNotification(String message) {
        long adminId = Config.getTelegramAdminId();
        if (adminId == 0) {
            logger.warn("Admin ID not configured");
            return false;
        }

        String adminMessage = String.format("👑 Admin notification\n\n" +
                "%s\n\n" +
                "Time: %s", message, new java.util.Date());

        return sendMessage((int) adminId, adminMessage);
    }

    /**
     * Проверка доступности бота
     */
    public static boolean isBotAvailable() {
        return botInstance != null;
    }

    /**
     * Получение статистики отправки уведомлений
     */
    public static String getStats() {
        if (botInstance == null) {
            return "Bot not initialized";
        }
        return "TelegramNotificationService is operational";
    }
}