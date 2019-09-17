package com.mvnikitin.netchat.client;


import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception{

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/NetChat.fxml"));
        Parent root = loader.load();
        primaryStage.setTitle("-~=* Super Chat *=~-");
        primaryStage.setScene(new Scene(root, 640, 720));

        primaryStage.setOnCloseRequest(new EventHandler<WindowEvent>() {
            @Override
            public void handle(WindowEvent event) {
                Controller controller = (Controller)loader.getController();
                controller.disconnect();
                controller.saveChatHistory();

                Platform.exit();
                System.exit(0);
            }
        });

        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
