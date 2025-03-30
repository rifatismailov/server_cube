package org.example;


import com.fasterxml.jackson.annotation.JsonGetter;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;

public class Process {

    private final String MESSAGE = "message";
    private final String MESSAGE_STATUS = "messageStatus";
    private final String HANDSHAKE = "handshake";
    private final String PUBLICKEY = "publicKey";
    private final String KEY_EXCHANGE = "keyExchange";
    private final String GET_AVATAR = "GET_AVATAR";
    private final String AVATAR = "AVATAR";
    private final String AVATAR_ORG = "AVATAR_ORG";
    private final String IMAGE = "image";
    private final String FILE = "file";
    private final String DELIVERED = "delivered";
    private final String RECEIVED = "received";
    private final String STATUS = "status";
    private final String DELIVERED_TO_USER = "delivered_to_user";
    private final String UPDATE_TO_MESSAGE = "update_to_message";

    private final ProcessMessage processMessage;
    private static final Logger logger = LoggerFactory.getLogger(Process.class);

    public Process(ProcessMessage processMessage) {
        this.processMessage = processMessage;
    }

    /**
     * Метод для обробки вхідних повідомлень.
     *
     * @param session     WebSocketSession клієнта
     * @param jsonMessage Повідомлення у форматі JSON.
     */
    public void processMessage(WebSocketSession session, String jsonMessage) {
        // Парсимо JSON-повідомлення
        Map<String, String> messageData = new JsonGetterStr().parseMessage(jsonMessage);
        String senderId = messageData.get("senderId");
        String receiverId = messageData.get("receiverId");
        String operation = messageData.get("operation");
        String messageId = messageData.get("messageId");

        switch (operation) {
            case MESSAGE, IMAGE, FILE -> {
                processMessage.sendMessage(receiverId, jsonMessage);
                sendMessage(session, messageStatus(receiverId, senderId, messageId, "server"));
            }
            case HANDSHAKE -> {
                String message = messageData.get(MESSAGE);
                JSONObject jsonObject = new JSONObject(message);
                String publicKey = jsonObject.getString(PUBLICKEY);
                processMessage.onHandshake(senderId, receiverId, publicKey);
            }
            case AVATAR, AVATAR_ORG, GET_AVATAR, KEY_EXCHANGE -> {
                processMessage.sendMessage(receiverId, jsonMessage);
            }
            case MESSAGE_STATUS -> {
                String messageStatus = messageData.get(MESSAGE_STATUS);

                if (DELIVERED.equals(messageStatus)) {
                    processMessage.deleteSaveMessages(senderId, messageId);
                    processMessage.setMessageStatus(senderId + ":" + messageId, DELIVERED);

                } else if (DELIVERED_TO_USER.equals(messageStatus)) {
                    //logger.info(LogMessage.MESSAGE_DELIVERED.getMessage(), messageId);
                    processMessage.sendMessage(receiverId, messageStatus(senderId, receiverId, messageId, "received"));
                }
            }
        }
    }

    /**
     * Відправка повідомлення клієнту.
     *
     * @param session WebSocketSession клієнта.
     * @param message Повідомлення у форматі JSON.
     */
    private void sendMessage(WebSocketSession session, String message) {
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(message));
            } catch (IOException e) {
                logger.error("Помилка при відправці повідомлення: {}", e.getMessage());
            }
        } else {
            logger.error("Спроба відправити повідомлення, але сесія закрита або не існує.");
        }
    }

    /**
     * Формуємо статусне повідомлення.
     */
    private String messageStatus(String senderId, String receiverId, String messageId, String status) {
        Envelope envelope = new Envelope(senderId, receiverId, "messageStatus", "", messageId);
        envelope.setMessageStatus(status);
        return envelope.toJson().toString();
    }


    public interface ProcessMessage {
        void onHandshake(String senderId, String receiverId, String publicKey);


        void sendMessage(String receiverId, String jsonMessage);

        void deleteSaveMessages(String userId, String messageId);

        void setMessageStatus(String messageId, String messageStatus);
    }
}

