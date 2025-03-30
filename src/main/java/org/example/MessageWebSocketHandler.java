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

public class MessageWebSocketHandler extends TextWebSocketHandler implements Process.ProcessMessage, HandshakeKeyGen.HandshakeListener {

    private static final Logger logger = LoggerFactory.getLogger(MessageWebSocketHandler.class);
    private static final String REGISTER = "REGISTER:";
    private static final String REGISTER_OK = "REGISTER_OK";
    private static final String CHECK_STATUS = "CHECK_STATUS:";
    private static final String REGISTER_FAILED = "REGISTER_FAILED";

    private final Map<String, WebSocketSession> clients;
    private final Map<String, List<String>> saveMessages;
    private final Map<String, String> clientStatus;
    private final Map<String, String> clientsKey;

    private final Map<String, String> messageStatusInfo = new ConcurrentHashMap<>();
    private final Map<String, Long> lastPingTime = new ConcurrentHashMap<>();

    public MessageWebSocketHandler(Map<String, WebSocketSession> clients, Map<String, List<String>> saveMessages, Map<String, String> clientStatus, Map<String, String> clientsKey) {
        this.clients = clients;
        this.saveMessages = saveMessages;
        this.clientStatus = clientStatus;
        this.clientsKey = clientsKey;
    }

    /**
     * Викликається після встановлення WebSocket-з'єднання з клієнтом.
     * Логує нове підключення.
     *
     * @param session WebSocket-сесія клієнта
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        logger.info(LogMessage.NEW_CONNECT.getMessage(), session.getId());
    }

    /**
     * Обробляє вхідні текстові повідомлення від клієнтів.
     * Визначає тип повідомлення (реєстрація, перевірка статусу чи інше) і відповідним чином реагує.
     *
     * @param session WebSocket-сесія клієнта
     * @param message Вхідне повідомлення від клієнта
     * @throws IOException Якщо виникає помилка при надсиланні відповіді
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, @NotNull TextMessage message) throws IOException {
        String payload = message.getPayload();

        if (payload.startsWith(REGISTER)) {
            JSONObject json = new JSONObject(payload.substring(REGISTER.length()));
            String userId = json.optString("userId", null);
            String life = json.optString("life", "unknown");
            String contacts = json.optString("contacts", "[]");

            // Перевіряємо, чи користувач вже зареєстрований
            if (userId == null || clients.containsKey(userId)) {
                session.sendMessage(new TextMessage(REGISTER_FAILED));
                return;
            }

            // Додаємо користувача до списку підключених клієнтів
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
                lastPingTime.put(userId, System.currentTimeMillis());
                session.sendMessage(new TextMessage(REGISTER_OK + ":" + getContactStatus(contacts)));
                logger.info(LogMessage.CHECK_CONTACTS.getMessage(), getContactStatus(contacts));

                // Відправляємо клієнту збережені повідомлення
                sendSavedMessages(userId);
            }
        } else {
            // Обробка інших типів повідомлень
            new Process(this).processMessage(session, payload);
        }
    }


    /**
     * Викликається після закриття WebSocket-з'єднання.
     * Видаляє клієнта зі списку активних сесій та очищає дані про останній пінг.
     *
     * @param session WebSocket-сесія, яка була закрита.
     * @param status  Статус закриття з'єднання.
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String clientId = getClientId(session);

        if (clientId != null) {
            lastPingTime.remove(clientId); // Видаляємо інформацію про останній пінг клієнта
            clients.remove(clientId); // Видаляємо клієнта зі списку активних сесій
            logger.info(LogMessage.CONNECT_CLOSED.getMessage(), clientId);
        } else {
            logger.warn("Unknown session {} disconnected", session.getId());
        }
    }

    /**
     * Отримує унікальний ідентифікатор клієнта за його WebSocket-сесією.
     *
     * @param session WebSocket-сесія клієнта.
     * @return Ідентифікатор клієнта або null, якщо клієнт не знайдений.
     */
    private String getClientId(WebSocketSession session) {
        return clients.entrySet()
                .stream()
                .filter(entry -> entry.getValue().equals(session))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }


    /**
     * Перевіряє статус підключення контактів у WebSocket-сесії.
     * <p>
     * Метод отримує JSON-масив із списком ідентифікаторів клієнтів,
     * перевіряє їхню активність у WebSocket-з'єднанні та визначає їхній статус.
     *
     * @param contacts JSON-рядок, що містить масив ідентифікаторів клієнтів.
     * @return JSON-рядок, що містить список контактів і їхній статус ("online" або "disconnect").
     */
    private String getContactStatus(String contacts) {
        // Перетворюємо вхідний JSON-рядок у масив JSON
        JSONArray jsonArray = new JSONArray(contacts);
        List<String> resultList = new ArrayList<>();
        long currentTime = System.currentTimeMillis(); // Отримуємо поточний час у мілісекундах

        // Перебираємо кожен ідентифікатор у JSON-масиві
        for (int i = 0; i < jsonArray.length(); i++) {
            String id = jsonArray.getString(i);
            WebSocketSession recipient = clients.get(id); // Отримуємо сесію клієнта

            // Перевіряємо, чи клієнт підключений та чи сесія активна
            if (recipient != null && recipient.isOpen()) {

                for (Map.Entry<String, Long> entry : lastPingTime.entrySet()) {
                    String clientId = entry.getKey();
                    long lastSeen = entry.getValue();

                    // Перевіряємо, чи останній пінг клієнта був більше ніж 6 секунд тому
                    if ((currentTime - lastSeen) > 6000) {
                        logger.warn(LogMessage.CONNECT_CLOSED.getMessage(), clientId);
                        lastPingTime.remove(clientId); // Видаляємо клієнта з останніх пінгів
                        clients.remove(clientId); // Видаляємо клієнта зі списку сесій
                        resultList.add(id + "=" + "disconnect");
                    } else {
                        // Отримуємо статус клієнта або встановлюємо "disconnect" за замовчуванням
                        String status = clientStatus.getOrDefault(id, "disconnect");
                        resultList.add(id + "=" + status);
                    }
                }
            } else {
                // Якщо клієнт не знайдений або з'єднання закрите, вказуємо "disconnect"
                resultList.add(id + "=" + "disconnect");
            }
        }

        // Повертаємо результат у вигляді JSON-масиву
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

    @Override
    public Map<String, WebSocketSession> getOnlineUsers() {
        return clients;
    }

    @Override
    public Map<String, String> getClientsKey() {
        return clientsKey;
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
         new HandshakeKeyGen(this).handleHandshake(senderId, receiverId, publicKey);
    }
}
