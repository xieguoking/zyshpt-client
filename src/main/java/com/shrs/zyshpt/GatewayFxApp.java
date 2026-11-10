package com.shrs.zyshpt;

import com.shrs.zyshpt.client.GatewayClient;
import com.shrs.zyshpt.util.HttpUtil;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * JavaFX GUI entry point for calling the gateway by the same protocol as GatewayMain.
 */
public class GatewayFxApp extends Application {

    private static final String DEFAULT_ENDPOINT =
            "http://172.22.22.13:8081/b120b58edde14c2c9e10f9e553034887";
    private static final String DEFAULT_APP_KEY = "2000000";
    private static final String DEFAULT_APP_SECRET =
            "aef67455-ccc7-479f-8a03-735f87df3439";
    private static final String DEFAULT_SERVER_PUBLIC_KEY =
            "040ad9e595055f6263835da12f30e99bdd9639b14a796dfe893c9771741e1812af0aea"
                    + "6535b899605d54d4af60dfc5ee34cf7d49bb6534f8632adb4acd36aa3cd9";
    private static final String DEFAULT_CLIENT_PRIVATE_KEY =
            "a0f44dd4ab1cfd004c9a0d131356a16ffc501d4897b8456a42ea04ad945e9ba3";
    private static final String DEFAULT_PAYLOAD = "{\"name\":\"xieguojun\"}";

    private TextField endpointField;
    private TextField appKeyField;
    private PasswordField appSecretField;
    private TextField serverPublicKeyField;
    private PasswordField clientPrivateKeyField;
    private TextArea payloadArea;
    private CheckBox ignoreCertificateBox;
    private Button sendButton;
    private TextArea responseArea;
    private Label statusLabel;
    private ProgressIndicator progressIndicator;

    @Override
    public void start(Stage stage) {
        endpointField = new TextField(DEFAULT_ENDPOINT);
        appKeyField = new TextField(DEFAULT_APP_KEY);
        appSecretField = new PasswordField();
        appSecretField.setText(DEFAULT_APP_SECRET);
        serverPublicKeyField = new TextField(DEFAULT_SERVER_PUBLIC_KEY);
        clientPrivateKeyField = new PasswordField();
        clientPrivateKeyField.setText(DEFAULT_CLIENT_PRIVATE_KEY);
        payloadArea = new TextArea(DEFAULT_PAYLOAD);
        ignoreCertificateBox = new CheckBox("忽略 HTTPS 证书校验");

        sendButton = new Button("发送请求");
        sendButton.setDefaultButton(true);
        sendButton.setOnAction(event -> sendRequest());

        responseArea = new TextArea();
        responseArea.setEditable(false);
        responseArea.setPromptText("网关应答明文将显示在这里");
        responseArea.setWrapText(true);

        statusLabel = new Label("请输入配置后发送请求");
        progressIndicator = new ProgressIndicator();
        progressIndicator.setPrefSize(18, 18);
        progressIndicator.setVisible(false);

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(16));
        root.setTop(buildConfigPane());
        root.setCenter(buildResponsePane());
        root.setBottom(buildStatusBar());

        Scene scene = new Scene(root, 920, 720);
        stage.setTitle("网关客户端");
        stage.setScene(scene);
        stage.setMinWidth(760);
        stage.setMinHeight(580);
        stage.show();
    }

    private VBox buildConfigPane() {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);

        ColumnConstraints labelColumn = new ColumnConstraints();
        labelColumn.setMinWidth(110);
        ColumnConstraints valueColumn = new ColumnConstraints();
        valueColumn.setPercentWidth(45);
        ColumnConstraints secondLabelColumn = new ColumnConstraints();
        secondLabelColumn.setMinWidth(110);
        ColumnConstraints secondValueColumn = new ColumnConstraints();
        secondValueColumn.setPercentWidth(55);
        grid.getColumnConstraints().addAll(
                labelColumn, valueColumn, secondLabelColumn, secondValueColumn);

        addField(grid, "接口URL:", endpointField, 0, 0);
        addField(grid, "AppKey:", appKeyField, 2, 0);
        addField(grid, "SecretKey:", appSecretField, 0, 1);
        addField(grid, "服务端SM2公钥:", serverPublicKeyField, 2, 1);
        addField(grid, "客户端SM2私钥:", clientPrivateKeyField, 0, 2);

        Label payloadTitle = new Label("请求参数（JSON明文）:");
        payloadArea.setPrefRowCount(6);
        payloadArea.setWrapText(true);

        VBox configPane = new VBox(10, grid, payloadTitle, payloadArea);
        VBox.setVgrow(payloadArea, Priority.ALWAYS);
        configPane.setPadding(new Insets(0, 0, 12, 0));
        return configPane;
    }

    private void addField(GridPane grid, String label, Control field,
                          int columnIndex, int rowIndex) {
        grid.add(new Label(label), columnIndex, rowIndex);
        grid.add(field, columnIndex + 1, rowIndex);
        GridPane.setHgrow(field, Priority.ALWAYS);
        field.setMinWidth(Region.USE_PREF_SIZE);
    }

    private VBox buildResponsePane() {
        HBox actionBar = new HBox(12, sendButton, ignoreCertificateBox);
        actionBar.setAlignment(Pos.CENTER_LEFT);

        Label responseTitle = new Label("网关应答:");
        VBox responsePane = new VBox(8, actionBar, responseTitle, responseArea);
        VBox.setVgrow(responseArea, Priority.ALWAYS);
        responseArea.setPrefHeight(260);
        return responsePane;
    }

    private HBox buildStatusBar() {
        HBox statusBar = new HBox(8, progressIndicator, statusLabel);
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.setPadding(new Insets(12, 0, 0, 0));
        return statusBar;
    }

    private void sendRequest() {
        String endpoint;
        String appKey;
        String appSecret;
        String serverPublicKey;
        String clientPrivateKey;
        String payload;
        try {
            endpoint = requireValue(endpointField.getText(), "接口URL");
            appKey = requireValue(appKeyField.getText(), "AppKey");
            appSecret = requireValue(appSecretField.getText(), "SecretKey");
            serverPublicKey = requireValue(serverPublicKeyField.getText(), "服务端SM2公钥");
            clientPrivateKey = requireValue(clientPrivateKeyField.getText(), "客户端SM2私钥");
            payload = requireValue(payloadArea.getText(), "请求参数");
        } catch (MissingInputException e) {
            showFailure(e.getMessage());
            return;
        }

        HttpUtil.setTrustAll(ignoreCertificateBox.isSelected());
        sendButton.setDisable(true);
        progressIndicator.setVisible(true);
        statusLabel.setText("正在调用网关...");
        long startTime = System.nanoTime();

        javafx.concurrent.Task<String> task = new javafx.concurrent.Task<String>() {
            @Override
            protected String call() throws Exception {
                String[] endpointParts = GatewayEndpoint.split(endpoint);
                GatewayClient client = new GatewayClient(
                        endpointParts[0], appKey, appSecret, serverPublicKey, clientPrivateKey);
                return client.call(endpointParts[1], payload);
            }
        };

        task.setOnSucceeded(event -> {
            long elapsed = (System.nanoTime() - startTime) / 1_000_000L;
            responseArea.setText(task.getValue());
            responseArea.positionCaret(0);
            showReady("调用成功，耗时 " + elapsed + " ms");
        });
        task.setOnFailed(event -> {
            Throwable error = task.getException();
            String message = error == null ? "未知错误" : error.getMessage();
            if (message == null || message.trim().isEmpty()) {
                message = error.getClass().getName();
            }
            responseArea.setText("调用失败:\n" + message);
            responseArea.positionCaret(0);
            showReady("调用失败: " + message);
        });

        Thread thread = new Thread(task, "gateway-request");
        thread.setDaemon(true);
        thread.start();
    }

    private String requireValue(String value, String label) {
        if (value == null || value.trim().isEmpty()) {
            throw new MissingInputException(label + "不能为空");
        }
        return value.trim();
    }

    private void showFailure(String message) {
        sendButton.setDisable(false);
        progressIndicator.setVisible(false);
        statusLabel.setText("配置错误: " + message);
    }

    private void showReady(String message) {
        sendButton.setDisable(false);
        progressIndicator.setVisible(false);
        statusLabel.setText(message);
    }

    public static void main(String[] args) {
        launch(args);
    }

    /** Signals the first required input that has not been entered. */
    private static final class MissingInputException extends RuntimeException {
        private MissingInputException(String message) {
            super(message);
        }
    }
}
