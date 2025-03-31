package org.example.process;

import org.example.LogMessage;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;

/**
 * Клас Process відповідає за обробку вхідних повідомлень та виконання відповідних дій.
 * Він працює з WebSocket-з'єднанням та обробляє операції, такі як повідомлення, статуси повідомлень, обмін ключами тощо.
 */
public class Process {

    private final ProcessMessage processMessage;
    private static final Logger logger = LoggerFactory.getLogger(Process.class);

    /**
     * Конструктор класу Process.
     *
     * @param processMessage Об'єкт, що реалізує інтерфейс ProcessMessage для обробки повідомлень.
     */
    public Process(ProcessMessage processMessage) {
        this.processMessage = processMessage;
    }

    /**
     * Метод для обробки вхідних повідомлень.
     * Використовується для маршрутизації повідомлень залежно від типу операції.
     *
     * @param session     WebSocketSession клієнта
     * @param jsonMessage Повідомлення у форматі JSON.
     */
    public void processMessage(WebSocketSession session, String jsonMessage) {
        // Парсимо JSON-повідомлення
        Envelope envelope = new Envelope(new JSONObject(jsonMessage));
        String senderId = envelope.getSenderId();
        String receiverId = envelope.getReceiverId();
        String operation = envelope.getOperation();
        String messageId = envelope.getMessageId();

        switch (operation) {
            case OperationType.MESSAGE, OperationType.IMAGE, OperationType.FILE -> {
                // Відправляємо повідомлення отримувачу
                processMessage.sendMessage(receiverId, jsonMessage);
                // Відправляємо відправнику підтвердження отримання повідомлення сервером
                sendMessage(session, messageStatus(receiverId, senderId, messageId, "server"));
            }
            case OperationType.HANDSHAKE -> {
                // Обробляємо обмін ключами
                JSONObject jsonObject = new JSONObject(envelope.getMessage());
                String publicKey = jsonObject.getString(OperationType.PUBLICKEY);
                processMessage.onHandshake(senderId, receiverId, publicKey);
            }
            case OperationType.AVATAR, OperationType.AVATAR_ORG, OperationType.GET_AVATAR, OperationType.KEY_EXCHANGE ->
                // Відправляємо аватари або ключі отримувачу
                    processMessage.sendMessage(receiverId, jsonMessage);
            case OperationType.MESSAGE_STATUS -> {
                // Обробка статусів повідомлень
                if (OperationType.DELIVERED.equals(envelope.getMessageStatus())) {
                    // Видаляємо збережені повідомлення після підтвердження доставки
                    processMessage.deleteSaveMessages(senderId, messageId);
                    processMessage.setMessageStatus(senderId + ":" + messageId, OperationType.DELIVERED);
                } else if (OperationType.DELIVERED_TO_USER.equals(envelope.getMessageStatus())) {
                    // Повідомляємо відправника, що отримувач переглянув повідомлення
                    String received_message = messageStatus(senderId, receiverId, messageId, "received");
                    processMessage.sendMessage(receiverId, received_message);
                }
            }
        }
    }

    /**
     * Відправка повідомлення клієнту через WebSocket.
     *
     * @param session WebSocketSession клієнта.
     * @param message Повідомлення у форматі JSON.
     */
    private void sendMessage(WebSocketSession session, String message) {
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(message));
            } catch (IOException e) {
                logger.error(LogMessage.ERROR_SENDING_MESSAGE.getMessage(), e.getMessage());
            }
        } else {
            logger.error("Attempting to send a message, but the session is closed or does not exist.");
        }
    }

    /**
     * Формує статусне повідомлення для повідомлення.
     *
     * @param senderId   Ідентифікатор відправника.
     * @param receiverId Ідентифікатор отримувача.
     * @param messageId  Ідентифікатор повідомлення.
     * @param status     Статус повідомлення.
     * @return JSON-рядок із сформованим статусом.
     */
    private String messageStatus(String senderId, String receiverId, String messageId, String status) {
        return new Envelope.Builder().
                setSenderId(senderId).
                setReceiverId(receiverId).
                setOperation("messageStatus").
                setMessageStatus(status).
                setMessageId(messageId).
                build().
                toJson("senderId", "receiverId", "operation", "messageStatus", "messageId").
                toString();
    }

    /**
     * Інтерфейс для обробки повідомлень.
     */
    public interface ProcessMessage {

        void sendMessage(String receiverId, String jsonMessage);

        void setMessageStatus(String messageId, String messageStatus);

        void deleteSaveMessages(String userId, String messageId);

        void onHandshake(String senderId, String receiverId, String publicKey);
    }
}
