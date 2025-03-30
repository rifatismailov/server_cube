package org.example;

import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MessageWebSocketHandler extends TextWebSocketHandler implements Process.ProcessMessage {

    private static final Logger logger = LoggerFactory.getLogger(MessageWebSocketHandler.class);
    private static final String REGISTER = "REGISTER:";
    private static final String REGISTER_OK = "REGISTER_OK";
    private static final String CHECK_STATUS = "CHECK_STATUS:";
    private static final String REGISTER_FAILED = "REGISTER_FAILED";

    private final ConcurrentHashMap<String, WebSocketSession> clients;
    private final ConcurrentHashMap<String, List<String>> saveMessages;
    private final ConcurrentHashMap<String, String> clientStatus;
    private final ConcurrentHashMap<String, String> messageStatusInfo = new ConcurrentHashMap<>();

    public MessageWebSocketHandler(ConcurrentHashMap<String, WebSocketSession> clients, ConcurrentHashMap<String, List<String>> saveMessages, ConcurrentHashMap<String, String> clientStatus) {
        this.clients = clients;
        this.saveMessages = saveMessages;
        this.clientStatus = clientStatus;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        logger.info(LogMessage.NEW_CONNECT.getMessage(), session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, @NotNull TextMessage message) throws IOException {
        String payload = message.getPayload();

        if (payload.startsWith(REGISTER)) {
            JSONObject json = new JSONObject(payload.substring(REGISTER.length()));
            String userId = json.optString("userId", null);
            String life = json.optString("life", "unknown");
            String contacts = json.optString("contacts", "[]");

            if (userId == null || clients.containsKey(userId)) {
                session.sendMessage(new TextMessage(REGISTER_FAILED));
                return;
            }

            clients.put(userId, session);
            clientStatus.put(userId, life);
            session.sendMessage(new TextMessage(REGISTER_OK + ":" + getContactStatus(contacts)));
        } else if (payload.startsWith(CHECK_STATUS)) {
            JSONObject json = new JSONObject(payload.substring(CHECK_STATUS.length()));
            String userId = json.optString("userId", null);
            String life = json.optString("life", "unknown");
            String contacts = json.optString("contacts", "[]");

            if (userId != null) {
                clients.putIfAbsent(userId, session);
                clientStatus.put(userId, life);
                session.sendMessage(new TextMessage(REGISTER_OK + ":" + getContactStatus(contacts)));
                sendSavedMessages(userId);
            }
        } else {
            //logger.info("Processing message: {}", payload);
            new Process(this).processMessage(session, payload);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        clients.values().removeIf(s -> s.getId().equals(session.getId()));
        logger.info(LogMessage.CONNECT_CLOSED.getMessage(), session.getId());
    }

    private String getContactStatus(String contacts) {
        JSONArray jsonArray = new JSONArray(contacts);
        List<String> resultList = new ArrayList<>();

        for (int i = 0; i < jsonArray.length(); i++) {
            String id = jsonArray.getString(i);
            String status = clientStatus.getOrDefault(id, "unknown");
            resultList.add(id + "=" + status);
        }

        return new JSONArray(resultList).toString();
    }


    private final ExecutorService executor = Executors.newFixedThreadPool(10); // Потоки для швидкої обробки

    /**
     * Надсилає повідомлення користувачу.
     * Використати ExecutorService для асинхронної відправки повідомлень – щоб не блокувати потік.
     *
     * @param receiverId  Ідентифікатор отримувача.
     * @param jsonMessage Повідомлення у форматі JSON.
     */
    @Override
    public void sendMessage(String receiverId, String jsonMessage) {
        executor.execute(() -> {
            try {
                WebSocketSession recipient = clients.get(receiverId);
                JSONObject jsonObject = new JSONObject(jsonMessage);
                String messageId = jsonObject.optString("messageId");

                if (recipient != null && recipient.isOpen()) {
                    //Якщо кілька потоків надсилають повідомлення через один і той самий WebSocket, потрібно синхронізувати доступ:
                    synchronized (recipient) {
                        recipient.sendMessage(new TextMessage(jsonMessage));
                    }

                    if ("delivered".equals(getMessageStatus(receiverId + ":" + messageId))) {
                        deleteSaveMessages(receiverId, messageId);
                    } else {
                        saveMessage(receiverId, jsonMessage);  // Якщо не підтверджене, зберігаємо
                    }
                } else {
                    saveMessage(receiverId, jsonMessage);
                    logger.warn(LogMessage.STATUS_CONNECT.getMessage(), receiverId);

                }
            } catch (IOException e) {
                saveMessage(receiverId, jsonMessage);
                logger.error(LogMessage.ERROR_SENDING_MESSAGE.getMessage(), e.getMessage());
            }
        });
    }



    /**
     * Метод для збереження офлайн-повідомлень.
     *
     * @param receiverId Ідентифікатор отримувача.
     * @param message    Повідомлення для збереження.
     */
    public void saveMessage(String receiverId, String message) {
        logger.info(LogMessage.SAVE_MESSAGE.getMessage(), receiverId, LogMessage.colorizeJson(message));
        saveMessages.computeIfAbsent(receiverId, k -> new ArrayList<>()).add(message);
    }


    /**
     * @param umId обєдання userID messageID для унеможливлення співпадіння ID повідомлень
     */
    @Override
    public void setMessageStatus(String umId, String messageStatus) {
        messageStatusInfo.computeIfAbsent(umId, k -> messageStatus);
    }

    /**
     * Отримує статус повідомлення за унікальним ідентифікатором (userID + messageID).
     *
     * @param umId об'єднання userID і messageID для уникнення збігів ID повідомлень.
     * @return статус повідомлення або null, якщо такого повідомлення немає.
     */
    public String getMessageStatus(String umId) {
        return messageStatusInfo.get(umId);
    }

    /**
     * відправка повідомлення
     *
     * @param userId його ідентіфікаційний номер
     */
    private void sendSavedMessages(String userId) {
        try {
            if (saveMessages.containsKey(userId)) {
                List<String> messages = new ArrayList<>(saveMessages.get(userId)); // Уникаємо ConcurrentModificationException
                for (String message : messages) {
                    JSONObject jsonObject = new JSONObject(message);
                    String messageId = jsonObject.optString("messageId");

                    if (!"delivered".equals(getMessageStatus(userId + ":" + messageId))) {
                        sendMessage(userId, message);
                        logger.info(LogMessage.SEND_MESSAGE.getMessage(), userId, LogMessage.colorizeJson(message));
                    } else {
                        deleteSaveMessages(userId, messageId);
                        logger.info(LogMessage.MESSAGE_DELIVERED.getMessage(), messageId);
                    }
                }
            }
        } catch (Exception e) {
            logger.error(LogMessage.ERROR_SENDING_MESSAGE.getMessage(), e.getMessage());
        }
    }


    /**
     * Видаляємо збережені офлайн-повідомлення, коли користувач підтвердив отримання.
     *
     * @param userId    ідентифікаційний номер користувача за яким збережено повідомлення
     * @param messageId ідентифікаційний номер повідомлення яикй збережено
     */
    @Override
    public void deleteSaveMessages(String userId, String messageId) {
        if (saveMessages.containsKey(userId)) {
            saveMessages.computeIfPresent(userId, (key, messages) -> {
                messages.removeIf(msg -> new JSONObject(msg).optString("messageId").equals(messageId));
                return messages.isEmpty() ? null : messages;
            });
            logger.info(LogMessage.DELETE_MESSAGE.getMessage(), userId, messageId);
        }
    }


    @Override
    public void onHandshake(String senderId, String receiverId, String publicKey) {
        // new Handshake(this).handleHandshake(senderId, receiverId, publicKey);
    }
}
