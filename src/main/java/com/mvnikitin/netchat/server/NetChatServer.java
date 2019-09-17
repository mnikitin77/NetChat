package com.mvnikitin.netchat.server;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NetChatServer {

    private static final int THREADS_COUNT = 5;

    private Map<String, ClientSession> clientSessions;
    private ExecutorService executorService;

    private static final Logger logger = LogManager.getLogger(NetChatServer.class.getName());

    public NetChatServer() {
        clientSessions = new ConcurrentHashMap<>();
        executorService = Executors.newFixedThreadPool(THREADS_COUNT);
    }

    public static void main(String[] args) {
            new NetChatServer().run();
    }

    public void run() {
        ServerSocket server = null;
        Socket socket = null;

        try {
            DataService.connect("org.sqlite.JDBC",
                    //"jdbc:sqlite:target\\classes\\data\\netchatusers");
                    "jdbc:sqlite:target/classes/data/netchatusers");

            server = new ServerSocket(10050);
            logger.info("Сервер запущен!");

            while (true) {
                socket = server.accept();
                executorService.execute(new ClientSession(this, socket));
            }
        } catch (IOException e) {
            e.printStackTrace();
            logger.error(e.getMessage());
        } catch (SQLException e) {
            e.printStackTrace();
            logger.error(e.getMessage());
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
                logger.error(e.getMessage());
            }

            try {
                server.close();
            } catch (IOException e) {
                e.printStackTrace();
                logger.error(e.getMessage());
            }

            executorService.shutdown();
            DataService.disconnect();
        }
    };

    public void broadcastMessage(String message, String userFrom) {
        for (ClientSession c: clientSessions.values()) {
            if (!c.checkInBlackList(userFrom)) {
                c.sendMessage(message);
            }
        }
    }

    public boolean privateMessage (
            String message, String userTo, String userFrom) {
        boolean res = false;

        ClientSession c = clientSessions.get(userTo);
        if (c != null) {
            if(!c.checkInBlackList(userFrom)) {
                c.sendMessage(message);
            }
            res = true;
        }

        return res;
    }

    public void openClientSession (ClientSession session) {
            clientSessions.put(session.getUser(), session);
    }

    public void closeClientSession(ClientSession session) {
        clientSessions.remove(session.getUser());
    }

    public boolean isOnline(String userName) {
        return (clientSessions.get(userName) == null ? false : true);
    }
}
