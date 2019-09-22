package com.mvnikitin.netchat.server;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.sql.SQLException;

public class ClientSession implements Runnable{

    private Socket socket;
    private DataOutputStream out;
    private DataInputStream in;
    private NetChatServer server;

    private String user;
    private boolean isAuth;

    private BlackList blackList;

    private static final Logger logger = LogManager.getLogger(ClientSession.class.getName());

    public String getUser() {
        return user;
    }

    public boolean checkInBlackList(String nick) {
        return blackList.isInBlackList(nick);
    }

    public ClientSession(NetChatServer server, Socket socket) {
        try {
            this.socket = socket;
            this.server = server;
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());

            user = "";
            blackList = null;
            isAuth = false;

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {

        try {
            // Выполняем предварительные действия по аутентификации
            // или регистрации, если клиент не аутентифицирован.
            if (!isAuth) {
                logon();
            } else {
                // Обработка сообщений чата.
                String messageReceived = in.readUTF();
                String[] commandString =
                        messageReceived.split(" ", 2);

                switch (commandString[0]) {
                    // Разрываем соединение.
                    case "/end":
                        logoff();
                        break;
                    // Отправляем персональное сообщение.
                    case "/w":
                        sendPrivateMessage(messageReceived);
                        break;
                    // Отправляем в чёрный список указанного собеседника.
                    case "/block":
                        addToBlackList(messageReceived);
                        break;
                    // Исключить собеседника из чёрного списка.
                    case "/unblock":
                        removeFromBlackList(messageReceived);
                        break;
                    // Отправить сообщение в чёт на всех.
                    default:
                        server.broadcastMessage(user + ": " +
                                messageReceived, user);
                }
            }
        } catch (IOException | SQLException e) {
            logger.error(e.getMessage());
            e.printStackTrace();

            // Если сломались, разрываем соединение.
            if(isAuth) {
                logoff();
            }
        }

        // Ставим клиентскую сессию в очередь обработки сообщений
        // если клиент аутентифицирован.
        if(isAuth) {
            server.passClientSessionToProcessing(this);
        }
    }


    public int hasNewData() throws IOException {
        return socket.getInputStream().available();
    }

    public void sendMessage(String message) {
        try {
            out.writeUTF(message);
        } catch (IOException e) {
            e.printStackTrace();
            logger.error(e.getMessage());
        }
    }

    private void logon()
            throws IOException, SQLException {
        // Цикл обработки начальных команд чата.
        while (true) {
            String firstMessage = in.readUTF();
            String[] commandString =
                    firstMessage.split(" ", 2);

            switch (commandString[0]) {
                case "/auth":
                    isAuth = authenticate(firstMessage);
                    break;
                case "/reg":
                    register(firstMessage);
                    break;
                default:
                    logger.warn("Incorrect command.");
                    sendMessage("Incorrect command.");
            }

            // Создаём и инициализируем чёрный список.
            if(isAuth) {
                blackList = new BlackList(user);
                break;
            }
        }
    }

    private void logoff() {
        // Снимаем признак "аутентифицирован".
        isAuth = false;

        try {
            out.writeUTF("/serverClosed");
        } catch (IOException e) {
            logger.error(e.getMessage());
            e.printStackTrace();
        }

        logger.info("User " + user + " disconnected.");

        // Удаляем текущий объек из коллекции сервера.
        server.closeClientSession(ClientSession.this);

        try {
            if (in != null) {
                in.close();
            }
        } catch (IOException e) {
            logger.error(e.getMessage());
            e.printStackTrace();
        }
        try {
            if (out != null) {
                out.close();
            }
        } catch (IOException e) {
            logger.error(e.getMessage());
            e.printStackTrace();
        }
        try {
            if (socket != null && socket.isConnected()) {
                socket.close();
            }
        } catch (IOException e) {
            logger.error(e.getMessage());
            e.printStackTrace();
        }
    }

    private boolean authenticate(String message) throws SQLException {

        boolean res = false;

        String[] tokens = message.split(" ");
        String newNick =
                AuthService.getNickByLoginAndPass(
                        tokens[1], tokens[2]);

        if (newNick == null) {
            sendMessage("Wrong username or password.");
        } else if (server.isOnline(newNick)) {
            sendMessage("You are already in the chat.");
        }
        else {
            user = newNick;
            res = true;

            sendMessage("/authok " + tokens[1] + " " + user);
            server.openClientSession(ClientSession.this);
            logger.info("User " + user + " connected.");
        }

        return res;
    }

    private void register(String message) throws SQLException {

        String[] tokens = message.split(" ");

        if (RegService.register(tokens[1], tokens[2], tokens[3])) {
            sendMessage(tokens[1] + " (username " + tokens[2] +
                    ") successfully registered.\n" +
                    "Welcome to the chat!");
            logger.info(tokens[1] + "(username " +
                    tokens[2] + ") successfully registered.");
        } else {
            sendMessage("The user with nick '" + tokens[1] +
                    "' and/or username '" + tokens[2] +
                    "' was registered before.\n" +
                    "Please choose other nick and/or username");
            logger.warn("The user with nick '" + tokens[1] +
                    "' and/or username '" + tokens[2] +
                    " is already registered!");
        }
    }

    private void sendPrivateMessage(String message) {

        String[] tokens =
                message.split(" ", 3);
        String messageText = user + " to " +
                tokens[1] + ": " +
                tokens[2];

        // ему
        if (server.privateMessage(
                messageText, tokens[1], user)) {
            // себе
            sendMessage(messageText);
        } else {
            sendMessage("User " +
                    tokens[1] +
                    " is not in the chat.");
        }
    }

    private void addToBlackList(String message) {
        String[] tokens =
                message.split(" ", 2);

        if(blackList.isInBlackList(tokens[1])) {
            sendMessage(tokens[1] + " is already in the blacklist." );
            return;
        }

        if (blackList.addToBlackList(tokens[1])) {
            sendMessage(tokens[1] + " successfully added to the blacklist." );
        } else {
            sendMessage("Failed to add " + tokens[1] + " to the blacklist." );
        }
    }

    private void removeFromBlackList(String message) {
        String[] tokens =
                message.split(" ", 2);

        if (blackList.removeFromBlackList(tokens[1])) {
            sendMessage(tokens[1] +
                    " successfully removed from the blacklist.");
        } else {
            sendMessage("Failed to remove " +
                    tokens[1] + " from the blacklist.");
        }
    }
}
