package com.parser.telegram;

import com.parser.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.net.URL;
import java.util.Date;

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
     * Отправка простого текстового сообщения
     */
    public static boolean sendMessage(long userId, String text) {
        return sendMessage(userId, text, false);
    }

    /**
     * Отправка текстового сообщения с HTML форматированием
     */
    public static boolean sendHtmlMessage(long userId, String htmlText) {
        return sendMessage(userId, htmlText, true);
    }

    /**
     * Основной метод отправки сообщения
     */
    private static boolean sendMessage(long userId, String text, boolean useHtml) {
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

            if (useHtml) {
                message.setText(text);
                message.setParseMode("HTML");
                message.disableWebPagePreview();
            } else {
                message.setText(text);
            }

            botInstance.execute(message);
            logger.debug("Message sent to user {}", userId);
            return true;

        } catch (TelegramApiException e) {
            logger.error("Error sending message to user {}: {}", userId, e.getMessage());

            if (useHtml) {
                logger.info("Retrying without HTML formatting...");
                String plainText = stripHtml(text);
                return sendMessage(userId, plainText, false);
            }
            return false;
        }
    }

    /**
     * Отправка фото с подписью (HTML форматирование)
     */
    public static boolean sendPhotoWithHtmlCaption(long userId, String photoUrl, String htmlCaption) {
        if (botInstance == null) {
            logger.error("Bot instance not set for TelegramNotificationService");
            return false;
        }

        if (photoUrl == null || photoUrl.isEmpty()) {
            logger.warn("Empty photo URL for user {}", userId);
            return false;
        }

        try {
            // Проверяем, является ли URL валидным
            if (!isValidUrl(photoUrl)) {
                logger.warn("Invalid photo URL: {}", photoUrl);
                return sendHtmlMessage(userId, htmlCaption);
            }

            SendPhoto photo = new SendPhoto();
            photo.setChatId(String.valueOf(userId));

            // Используем URL напрямую
            photo.setPhoto(new InputFile(photoUrl));

            if (htmlCaption != null && !htmlCaption.isEmpty()) {
                // Обрезаем подпись если она слишком длинная (макс 1024 символа для Telegram)
                if (htmlCaption.length() > 1024) {
                    htmlCaption = htmlCaption.substring(0, 1020) + "...";
                }
                photo.setCaption(htmlCaption);
                photo.setParseMode("HTML");
            }

            botInstance.execute(photo);
            logger.debug("Photo with caption sent to user {}", userId);
            return true;

        } catch (TelegramApiException e) {
            logger.error("Error sending photo to user {}: {}", userId, e.getMessage());

            // Если не удалось отправить фото, отправляем текстовое сообщение
            if (htmlCaption != null && !htmlCaption.isEmpty()) {
                String textMessage = "📸 " + stripHtml(htmlCaption);
                return sendMessage(userId, textMessage);
            }
            return false;
        } catch (Exception e) {
            logger.error("Unexpected error sending photo to user {}: {}", userId, e.getMessage());
            return sendHtmlMessage(userId, htmlCaption);
        }
    }

    /**
     * Проверка валидности URL
     */
    private static boolean isValidUrl(String url) {
        try {
            new URL(url).toURI();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Удаление HTML тегов из текста
     */
    private static String stripHtml(String html) {
        if (html == null) return "";
        return html.replaceAll("<[^>]*>", "")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
    }

    /**
     * Экранирование для HTML
     */
    public static String escapeHtml(String text) {
        if (text == null) return "";

        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /**
     * Отправка тестового уведомления
     */
    public static boolean sendTestNotification(long userId) {
        logger.info("Sending test notification to user {}", userId);

        String message = "<b>✅ Test notification</b>\n\n" +
                "Parser is working correctly!\n" +
                "This is a test message to confirm that the notification system is functioning.\n\n" +
                "<i>Time: " + new Date() + "</i>";

        return sendHtmlMessage(userId, message);
    }

    /**
     * Отправка уведомления о найденных товарах
     */
    public static boolean sendProductsNotification(long userId, int count, String query) {
        String message = String.format("<b>🛍️ Found products!</b>\n\n" +
                "Query: %s\n" +
                "Products found: %d\n\n" +
                "<i>Sending details...</i>", escapeHtml(query), count);

        return sendHtmlMessage(userId, message);
    }

    /**
     * Отправка уведомления об ошибке
     */
    public static boolean sendErrorNotification(long userId, String errorMessage) {
        String message = String.format("<b>❌ Parser error</b>\n\n" +
                        "An error occurred:\n" +
                        "<code>%s</code>\n\n" +
                        "<i>The parser will be restarted automatically.</i>",
                escapeHtml(errorMessage));

        return sendHtmlMessage(userId, message);
    }

    /**
     * Отправка уведомления о состоянии парсера
     */
    public static boolean sendStatusNotification(long userId, String status, String details) {
        String emoji = "🟢";
        if (status.contains("stopped")) emoji = "🔴";
        if (status.contains("paused")) emoji = "⏸️";
        if (status.contains("error")) emoji = "❌";

        String message = String.format("%s <b>Parser status changed</b>\n\n" +
                "New status: %s\n\n" +
                "%s", emoji, status, escapeHtml(details));

        return sendHtmlMessage(userId, message);
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

        String adminMessage = String.format("<b>👑 Admin notification</b>\n\n" +
                        "%s\n\n" +
                        "<i>Time: %s</i>",
                escapeHtml(message),
                new Date());

        return sendHtmlMessage(adminId, adminMessage);
    }

    /**
     * Проверка доступности бота
     */
    public static boolean isBotAvailable() {
        return botInstance != null;
    }

    /**
     * Отправка фото с подписью (удобный метод для использования из других классов)
     */
    public static boolean sendPhotoWithCaption(long userId, String photoUrl, String caption) {
        return sendPhotoWithHtmlCaption(userId, photoUrl, caption);
    }
}