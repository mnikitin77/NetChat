package com.mvnikitin.netchat.server;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.SQLException;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NetChatServer {

    private static final String DBDRIVER = "org.sqlite.JDBC";
    private static final String CONNECT_STRING =
            "jdbc:sqlite:target/classes/data/netchatusers";
    private static final int PORT = 10050;

    private static final Logger logger =
            LogManager.getLogger(NetChatServer.class.getName());

    private Map<String, ClientSession> clientSessions;
    private Queue<ClientSession> processingQueue;
    private ExecutorService executorService;
    //private boolean isToStop;  // TODO

    public NetChatServer() {
        clientSessions = new ConcurrentHashMap<>();
        processingQueue = new ConcurrentLinkedQueue<>();
        executorService = Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors() + 1);
        //isToStop = false; //TODO
    }

    public static void main(String[] args) {
            new NetChatServer().start();
    }

    //TODO продумать, как реализовать stop();

    public void start() {
        ServerSocket server = null;
        Socket socket = null;

        try {
            DataService.connect(DBDRIVER, CONNECT_STRING);

            server = new ServerSocket(PORT);
            logger.info("Сервер запущен!");

            // Создаём поток-диспетчер для обработки клиентских сессий.
            new Thread(() -> {
                // TODO придумать, как останавливать сервер!!! isToStop
                while(true) {
                    ClientSession clientSession = processingQueue.poll();
                    if (clientSession != null) {
                        try {
                            if (clientSession.hasNewData() > 0) {
                                // Отправляем клиентскую сессию в пул потоков.
                                executorService.execute(clientSession);
                            } else {
                                // Клиент ничего не прислал,
                                // перемещаем сессию в хвост.
                                processingQueue.offer(clientSession);
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }).start();
            logger.info("Диспетчер клиентских сессий запущен.");

            // Слушаем серверный сокети и создаём клиентскую сессию.
            while (true) {
                socket = server.accept();
                passClientSessionToProcessing(new ClientSession(this, socket));
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
    }

    public void passClientSessionToProcessing(ClientSession clientSession) {
        processingQueue.add(clientSession);
    }

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
