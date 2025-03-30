package org.example;


public enum LogMessage {
    MESSAGE_DELIVERED("Message delivered. Message ID: \u001B[34m{}\u001B[0m"),
    HANDSHAKE("HANDSHAKE. sender ID: \u001B[33m{}\u001B[0m receiver ID: \u001B[36m{}\u001B[0m"),
    HANDSHAKE_EXCHANGE("HANDSHAKE_EXCHANGE. sender ID: \u001B[33m{}\u001B[0m receiver ID: \u001B[36m{}\u001B[0m"),
    SAVE_KAY_HANDSHAKE("[\u001B[32mSAVE KEY\u001B[0m] KEY WITH \u001B[33m{}\u001B[0m для \u001B[36m{}\u001B[0m: \u001B[34m{}\u001B[0m"),
    SEND_KAY_HANDSHAKE("[\u001B[32mSEND KEY\u001B[0m] KEY WITH  \u001B[33m{}\u001B[0m для \u001B[36m{}\u001B[0m: \u001B[34m{}\u001B[0m"),
    ERROR_KAY_HANDSHAKE("[\u001B[31mERROR\u001B[0m] KEY WITH  \u001B[33m{}\u001B[0m для \u001B[36m{}\u001B[0m: \u001B[34m{}\u001B[0m"),
    ADD_KAY_HANDSHAKE("[\u001B[32mADD KAY\u001B[0m] SENDER: \u001B[33m{}\u001B[0m, RECEIVER: \u001B[36m{}\u001B[0m, Ключ: \u001B[34m{}\u001B[0m"),
    RETURN_KAY_HANDSHAKE("[\u001B[32mRETURN KAY\u001B[0m] SENDER: \u001B[33m{}\u001B[0m, RECEIVER: \u001B[36m{}\u001B[0m, Ключ: \u001B[34m{}\u001B[0m"),
    NEW_CONNECT("New connection established: \u001B[31m{}\u001B[0m"),
    CONNECT_CLOSED("Connection closed: \u001B[31m{}\u001B[0m"),
    SAVE_MESSAGE("Save message   : \u001B[31m{} \u001B[34m{}\u001B[0m"),
    SEND_MESSAGE("Send message   : \u001B[31m{} \u001B[34m{}\u001B[0m"),
    STATUS_CONNECT("Status connect : \u001B[31m{}\u001B[0m Session closed or client offline"),
    SEND_SAVE_MESSAGE("Send save message : \u001B[31m{} \u001B[34m{}\u001B[0m"),
    NOT_FOUND("Not found. ID: \u001B[31m{}\u001B[0m"),
    CLIENT_CLOSE("Client close: {}"),
    ERROR_TO_CHECK_ID("Error to check ID: \u001B[31m{}\u001B[0m"),
    SUCCESSFUL("Registration successful! ID: \u001B[31m{}\u001B[0m"),
    CHECK_CONTACTS("Check contact! Array ID: \u001B[31m{}\u001B[0m"),
    ERROR_SENDING_MESSAGE("Error sending message: {}"),
    NOT_CONNECTED_OR_CLOSE("Client not connected or socket closed ID: \u001B[31m{}\u001B[0m"),
    REGISTRATION_FILED("Registration failed: Invalid or duplicate client ID. \u001B[31m{}\u001B[0m"),
    DELETE_MESSAGE("Dell message   : \u001B[31m{}\u001B[0m \u001B[36m{}\u001B[0m");

    private final String message;

    LogMessage(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public static String colorizeJson(String json) {
        json = json.replaceAll("(\"[^\"]+\"):", "\u001B[36m$1\u001B[0m:"); // Блакитний для ключів
        json = json.replaceAll(":\\s*(\"[^\"]+\")", ": \u001B[33m$1\u001B[0m"); // Жовтий для рядкових значень
        json = json.replaceAll(":\\s*(\\d+)", ": \u001B[32m$1\u001B[0m"); // Зелений для чисел
        return json;
    }
}
