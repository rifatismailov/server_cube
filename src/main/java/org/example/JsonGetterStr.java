package org.example;


import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Клас для обробки JSON-повідомлень.
 * Використовується для парсингу та отримання даних з JSON-структур.
 */
public class JsonGetterStr {
    private static final Logger logger = LoggerFactory.getLogger(JsonGetterStr.class);

    /**
     * Парсер JSON-повідомлень.
     *
     * @param jsonMessage Повідомлення у форматі JSON.
     * @return Мапа з ключами та значеннями, отриманими з JSON-об'єкта.
     */
    public Map<String, String> parseMessage(String jsonMessage) {
        Map<String, String> data = new HashMap<>();
        try {
            JSONObject jsonObject = new JSONObject(jsonMessage);
            data.put("senderId", jsonObject.optString("senderId", ""));
            data.put("receiverId", jsonObject.optString("receiverId", ""));
            data.put("operation", jsonObject.optString("operation", ""));
            data.put("message", jsonObject.optString("message", ""));
            data.put("messageId", jsonObject.optString("messageId", ""));
            data.put("messageStatus", jsonObject.optString("messageStatus", ""));

            // Перевірка на вкладений JSON у "message"
            String messageContent = jsonObject.optString("message", "");
            if (messageContent.startsWith("{")) {
                JSONObject nestedMessage = new JSONObject(messageContent);
                data.put("publicKey", nestedMessage.optString("publicKey", ""));
            }

            if (jsonMessage.contains("fileUrl")) {
                data.put("fileUrl", jsonObject.optString("fileUrl", ""));
                data.put("fileHash", jsonObject.optString("fileHash", ""));
            }
        } catch (JSONException e) {
            logger.error("JSON Parsing Error: {}", e.getMessage());
        }
        return data;
    }

    /**
     * Отримує userId з повідомлення, якщо воно відповідає правильному формату.
     *
     * @param message JSON-повідомлення у форматі рядка.
     * @return userId або null, якщо формат невірний або userId відсутній.
     */
    public String getUserID(String message) {
        if (message == null || !(message.startsWith("REGISTER:") || message.startsWith("CHECK_STATUS:"))) {
            logger.error("Invalid message format: {}", message);
            return null;
        }

        String jsonString = message.replaceFirst("^(REGISTER:|CHECK_STATUS:)", "");
        try {
            if (jsonString.trim().startsWith("{")) {
                JSONObject jsonObject = new JSONObject(jsonString);
                String userId = jsonObject.optString("userId", null);
                if (userId == null || userId.isEmpty()) {
                    logger.error("userId is missing or empty.");
                    return null;
                }
                return userId;
            } else {
                logger.error("Invalid JSON format: {}", jsonString);
                return null;
            }
        } catch (JSONException e) {
            logger.error("Error parsing JSON: {} - {}", jsonString, e.getMessage());
            return null;
        }
    }

    /**
     * Отримує значення певного JSON-ключа з повідомлення.
     *
     * @param message JSON-повідомлення у форматі рядка.
     * @param jsonId Ключ JSON, значення якого потрібно отримати.
     * @return Значення ключа у вигляді рядка або null, якщо формат невірний.
     */
    public String getUserID(String message, String jsonId) {
        if (message == null || !(message.startsWith("REGISTER:") || message.startsWith("CHECK_STATUS:"))) {
            logger.error("Invalid message format: {}", message);
            return null;
        }

        String jsonString = message.replaceFirst("^(REGISTER:|CHECK_STATUS:)", "");
        try {
            if (jsonString.trim().startsWith("{")) {
                JSONObject jsonObject = new JSONObject(jsonString);
                String json = "";
                if (jsonObject.has(jsonId)) {
                    if (jsonObject.get(jsonId) instanceof JSONArray) {
                        json = jsonObject.getJSONArray(jsonId).toString();
                    } else {
                        json = jsonObject.getString(jsonId);
                    }
                }
                return json;
            } else {
                logger.error("Invalid JSON format: {}", jsonString);
                return null;
            }
        } catch (JSONException e) {
            logger.error("Error parsing JSON: {} - {}", jsonString, e.getMessage());
            return null;
        }
    }
}
