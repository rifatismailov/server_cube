package org.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HandshakeKeyGen {
    private static final Logger logger = LoggerFactory.getLogger(HandshakeKeyGen.class);

    HandshakeListener listener;

    HandshakeKeyGen(HandshakeListener listener) {
        this.listener = listener;
    }

    /**
     * Управляє процесом рукостискання між двома користувачами для обміну публічними ключами.
     *
     * @param senderId   Ідентифікатор відправника.
     * @param receiverId Ідентифікатор отримувача.
     * @param publicKey  Публічний ключ відправника.
     */
    public void handleHandshake(String senderId, String receiverId, String publicKey) {
        // Виводимо кількість поточних пар
        // Якщо список порожній, додаємо нову пару
        if (listener.getClientsKey().size() == 0) {
            addKey(senderId, receiverId, publicKey);
        } else {

            // Перевірка за ID
            if (listener.getClientsKey().containsKey(senderId + ":" + receiverId)) {
                String value = listener.getClientsKey().get(senderId + ":" + receiverId);
                //Додаємо ключ  якщо він був змінений
                if (!value.equals(publicKey)) {

                    addKey(senderId, receiverId, publicKey);
                    // Первіряємо чи є ключ від (receiverId) отримувача. Якщо є робимо обмін ключами.
                    // Обмін ключами проходе коли отримувачь підключився та зробив обмін ключами.
                    // Я не став змінювати логику обміну ключів якщо була зміна ключа відправити тілки отримувачу, а залишив так як є
                    // проходе стандартний обмін ключами від А до Б та навпаки від Б до А Не зважаючи на те що в Б не було зміни ключа
                    returnKey(senderId, receiverId, publicKey);
                }

            } else {
                addKey(senderId, receiverId, publicKey);
                // первіряємо чи є ключ від (receiverId) отримувача. Якщо є робимо обмін ключами
                // обмін ключами проходе коли отримувачь підключився та зробив обмін ключами
                returnKey(senderId, receiverId, publicKey);
            }
        }

    }

    // перевірка чи доступний клієнт для відправки ключа
    public void checkOnline(String senderId, String receiverId, String publicKey) {
        if (listener.getOnlineUsers().containsKey(receiverId)) {
            // Якщо отримувач онлайн, надсилаємо ключ
            sendKeyToUser(senderId, receiverId, publicKey);

        } else {
            // Якщо отримувач офлайн, зберігаємо ключ для подальшого використання
            logger.info(LogMessage.HANDSHAKE.getMessage(), senderId,receiverId);
            saveKeyToUser(senderId, receiverId, publicKey);
        }
    }

    /**
     * Додає нову пару користувачів до списку клієнтів із зазначеним публічним ключем.
     *
     * @param senderId   Ідентифікатор відправника.
     * @param receiverId Ідентифікатор отримувача.
     * @param publicKey  Публічний ключ відправника.
     */
    private void addKey(String senderId, String receiverId, String publicKey) {
        listener.getClientsKey().put(senderId + ":" + receiverId, publicKey);
        //System.out.printf("[ДОДАНО КЛЮЧ] Відправник: %s, Отримувач: %s, Ключ: %s\n", senderId, receiverId, publicKey);
        logger.info(LogMessage.ADD_KAY_HANDSHAKE.getMessage(), senderId, receiverId, publicKey);

    }

    private void sendKeyToUser(String senderId, String receiverId, String publicKey) {
        try {
            if (publicKey != null) {
                String handshakeMessage = "{\"publicKey\": \"" + publicKey + "\" }";
                listener.sendMessage(receiverId, new Envelope(senderId, receiverId, "handshake", handshakeMessage, "").toJson().toString());
                logger.info(LogMessage.SEND_KAY_HANDSHAKE.getMessage(), senderId, receiverId, publicKey);

                //listener.getClientsKey().remove(receiverId + ":" + senderId);
            }
        } catch (IOException e) {
            logger.error(LogMessage.ERROR_KAY_HANDSHAKE.getMessage(), senderId, receiverId, e.getMessage());

        }
    }

    private void saveKeyToUser(String senderId, String receiverId, String publicKey) {
        if (publicKey != null) {
            String handshakeMessage =String.format("{\"publicKey\":\"%s\"}", publicKey);
            listener.saveMessage(receiverId, new Envelope(senderId, receiverId, "handshake", handshakeMessage, "").toJson().toString());
            logger.info(LogMessage.SAVE_KAY_HANDSHAKE.getMessage(), senderId, receiverId, publicKey);
        }
    }

    public void returnKey(String senderId, String receiverId, String publicKey) {
        if (listener.getClientsKey().containsKey(receiverId + ":" + senderId)) {
            String value = listener.getClientsKey().get(receiverId + ":" + senderId);
            logger.info(LogMessage.HANDSHAKE_EXCHANGE.getMessage(), senderId,receiverId);

            checkOnline(receiverId, senderId, value);
            checkOnline(senderId, receiverId, publicKey);
        } else {
            logger.info(LogMessage.RETURN_KAY_HANDSHAKE.getMessage(), senderId,receiverId);
        }
    }


    // Інтерфейс
    public interface HandshakeListener {

        void sendMessage(String receiverId, String jsonMessage) throws IOException;

        Map<String, WebSocketSession> getOnlineUsers();

        Map<String, String> getClientsKey();

        void saveMessage(String receiverId, String message);
    }
}