package org.example.process;

import org.example.LogMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.WebSocketSession;
import java.util.Map;

public class HandshakeKeyGen {
    private static final Logger logger = LoggerFactory.getLogger(HandshakeKeyGen.class);

    private final HandshakeListener listener;

    public HandshakeKeyGen(HandshakeListener listener) {
        this.listener = listener;
    }

    /**
     * Управляє процесом рукостискання між двома користувачами для обміну публічними ключами.
     * Метод перевіряє наявність ключа у сховищі, додає його за необхідності
     * та ініціює обмін ключами між клієнтами.
     *
     * @param senderId   Ідентифікатор відправника.
     * @param receiverId Ідентифікатор отримувача.
     * @param publicKey  Публічний ключ відправника.
     */
    public void handleHandshake(String senderId, String receiverId, String publicKey) {
        // Виводимо кількість поточних пар
        // Якщо список порожній, додаємо нову пару ключів
        if (listener.getClientsKey().size() == 0) {
            addKey(senderId, receiverId, publicKey);
        } else {
            // Перевірка за ID для визначення, чи існує вже ключ для пари користувачів
            if (listener.getClientsKey().containsKey(senderId + ":" + receiverId)) {
                String value = listener.getClientsKey().get(senderId + ":" + receiverId);

                // Додаємо новий ключ, якщо він був змінений
                if (!value.equals(publicKey)) {
                    addKey(senderId, receiverId, publicKey);

                    /**
                     * Перевіряємо наявність ключа у отримувача (receiverId).
                     * Якщо ключ є, ініціюємо обмін ключами.
                     * Обмін ключами відбувається, коли отримувач підключився та ініціював процес.
                     * Логіка залишається незмінною: ключі передаються обом сторонам (від A до B і від B до A),
                     * навіть якщо у B не було змін у ключі.
                     */
                    returnKey(senderId, receiverId, publicKey);
                }
            } else {
                // Якщо пари ключів ще немає, додаємо її
                addKey(senderId, receiverId, publicKey);

                /**
                 * Перевіряємо, чи є ключ у отримувача (receiverId).
                 * Якщо ключ існує, виконуємо обмін ключами.
                 * Обмін ключами відбувається лише після підключення отримувача.
                 */
                returnKey(senderId, receiverId, publicKey);
            }
        }
    }


    /**
     * Метод checkOnline перевіряє, чи отримувач знаходиться онлайн,
     * і відповідно або надсилає йому відкритий ключ, або зберігає його для подальшого використання.
     *
     * @param senderId   Унікальний ідентифікатор відправника.
     * @param receiverId Унікальний ідентифікатор отримувача.
     * @param publicKey  Відкритий ключ відправника.
     */
    public void checkOnline(String senderId, String receiverId, String publicKey) {
        // Перевіряємо, чи отримувач знаходиться в списку онлайн-користувачів
        if (listener.getOnlineUsers().containsKey(receiverId)) {
            // Якщо отримувач онлайн, надсилаємо ключ
            sendKeyToUser(senderId, receiverId, publicKey);
        } else {
            // Якщо отримувач офлайн, зберігаємо ключ для подальшого використання
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
        logger.info(LogMessage.ADD_KAY_HANDSHAKE.getMessage(), senderId, receiverId, publicKey);

    }

    /**
     * Метод sendKeyToUser використовується для надсилання відкритого ключа іншому користувачу.
     * Якщо ключ не є null, створюється JSON-об'єкт, що містить відкритий ключ,
     * і надсилається отримувачу через listener.sendMessage.
     *
     * @param senderId   Унікальний ідентифікатор відправника.
     * @param receiverId Унікальний ідентифікатор отримувача.
     * @param publicKey  Відкритий ключ відправника.
     */
    private void sendKeyToUser(String senderId, String receiverId, String publicKey) {
        try {
            if (publicKey != null) {
                listener.sendMessage(receiverId, new Envelope.Builder().
                        setSenderId(senderId).
                        setReceiverId(receiverId).
                        setOperation("handshake").
                        setMessage(String.format("{\"publicKey\":\"%s\"}", publicKey)).
                        setMessageId("").
                        build().
                        toJson("senderId", "receiverId", "operation", "message", "messageId").
                        toString());
                logger.info(LogMessage.SEND_KAY_HANDSHAKE.getMessage(), senderId, receiverId, publicKey);
            }
        } catch (Exception e) {
            logger.error(LogMessage.ERROR_KAY_HANDSHAKE.getMessage(), senderId, receiverId, e.getMessage());
        }
    }

    /**
     * Метод saveKeyToUser використовується для збереження відкритого ключа отримувача.
     * Якщо ключ не є null, створюється JSON-об'єкт, що містить відкритий ключ,
     * і передається до listener.saveMessage для подальшого використання.
     *
     * @param senderId   Унікальний ідентифікатор відправника.
     * @param receiverId Унікальний ідентифікатор отримувача.
     * @param publicKey  Відкритий ключ відправника.
     */
    private void saveKeyToUser(String senderId, String receiverId, String publicKey) {
        if (publicKey != null) {
            listener.saveMessage(receiverId,
                    new Envelope.Builder().
                            setSenderId(senderId).
                            setReceiverId(receiverId).
                            setOperation("handshake").
                            setMessage(String.format("{\"publicKey\":\"%s\"}", publicKey)).
                            setMessageId("").
                            build().
                            toJson("senderId", "receiverId", "operation", "message", "messageId").
                            toString());
        }
    }

    /**
     * Метод returnKey використовується для обміну ключами між двома клієнтами.
     * Він перевіряє, чи існує ключ для відповідної пари клієнтів у сховищі ключів.
     * Якщо ключ існує, він виконує перевірку стану онлайн для обох клієнтів.
     * У протилежному випадку логуються дані про відсутність ключа обміну.
     *
     * @param senderId   Унікальний ідентифікатор відправника.
     * @param receiverId Унікальний ідентифікатор отримувача.
     * @param publicKey  Відкритий ключ відправника.
     */
    public void returnKey(String senderId, String receiverId, String publicKey) {
        // Перевіряємо, чи є у сховищі ключів запис для отримувача та відправника
        if (listener.getClientsKey().containsKey(receiverId + ":" + senderId)) {
            // Отримуємо збережений ключ
            String value = listener.getClientsKey().get(receiverId + ":" + senderId);

            // Логуємо обмін ключами
            logger.info(LogMessage.HANDSHAKE_EXCHANGE.getMessage(), senderId, receiverId);

            // Перевіряємо, чи клієнти знаходяться в онлайн-стані
            checkOnline(receiverId, senderId, value);
            checkOnline(senderId, receiverId, publicKey);
        } else {
            // Логуємо ситуацію, коли обмін ключами неможливий через відсутність запису
            logger.info(LogMessage.RETURN_KAY_HANDSHAKE.getMessage(), senderId, receiverId);
        }
    }


    // Інтерфейс
    public interface HandshakeListener {

        void sendMessage(String receiverId, String jsonMessage);

        Map<String, WebSocketSession> getOnlineUsers();

        Map<String, String> getClientsKey();

        void saveMessage(String receiverId, String message);
    }
}