package com.mygameshelf;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class GameShelf extends Application {
    private static final List<String> STATUSES = List.of(
        "Playing", "Completed", "Wishlist"
    );

    private final List<Game> games = new ArrayList<>();
    private final VBox gameList = new VBox(12);
    private final Label summary = new Label();

    private final Path saveFile = Path.of(
        System.getProperty("user.home"),
        ".mygameshelf",
        "games.properties"
    );

    @Override
    public void start(Stage stage) {
        loadGames();

        Label title = new Label("My Game Shelf");
        title.getStyleClass().add("title");

        Label subtitle = new Label("A little home for your favorite worlds.");
        subtitle.getStyleClass().add("subtitle");

        TextField titleInput = new TextField();
        titleInput.setPromptText("Game title");
        titleInput.setPrefWidth(280);
        HBox.setHgrow(titleInput, Priority.ALWAYS);

        ComboBox<String> statusInput = new ComboBox<>();
        statusInput.getItems().addAll(STATUSES);
        statusInput.setValue("Wishlist");

        Button addButton = new Button("+ Add game");
        addButton.getStyleClass().add("primary-button");

        Runnable addGame = () -> {
            String name = titleInput.getText().strip();

            if (name.isEmpty()) {
                showMessage("Enter a game title first.");
                return;
            }

            boolean exists = games.stream().anyMatch(
                game -> game.title.equalsIgnoreCase(name)
            );

            if (exists) {
                showMessage("This game is already on your shelf.");
                return;
            }

            games.add(new Game(name, statusInput.getValue()));
            titleInput.clear();
            saveGames();
            renderGames();
        };

        addButton.setOnAction(event -> addGame.run());
        titleInput.setOnAction(event -> addGame.run());

        HBox form = new HBox(12, titleInput, statusInput, addButton);
        form.setAlignment(Pos.CENTER_LEFT);

        summary.getStyleClass().add("summary");

        ScrollPane scroll = new ScrollPane(gameList);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        VBox root = new VBox(
            12, title, subtitle, form, summary, scroll
        );

        root.setPadding(new Insets(30));

        Scene scene = new Scene(root, 820, 600);
        scene.getStylesheets().add(
            GameShelf.class.getResource("/style.css").toExternalForm()
        );

        stage.setTitle("MyGameShelf");
        stage.setMinWidth(720);
        stage.setMinHeight(450);
        stage.setScene(scene);

        renderGames();
        stage.show();
    }

    private void renderGames() {
        gameList.getChildren().clear();

        long completed = games.stream()
            .filter(game -> game.status.equals("Completed"))
            .count();

        summary.setText(
            games.size() + " games  •  " + completed + " completed"
        );

        if (games.isEmpty()) {
            Label empty = new Label(
                "Your shelf is empty. Add your first game."
            );
            empty.getStyleClass().add("empty-message");
            gameList.getChildren().add(empty);
            return;
        }
      for (Game game : games) {
            Label name = new Label(game.title);
            name.getStyleClass().add("game-title");
            name.setWrapText(true);
            name.setMaxWidth(Double.MAX_VALUE);

            ComboBox<String> status = new ComboBox<>();
            status.getItems().addAll(STATUSES);
            status.setValue(game.status);

            status.setOnAction(event -> {
                game.status = status.getValue();
                saveGames();
                renderGames();
            });

            Button delete = new Button("Remove");
            delete.getStyleClass().add("remove-button");

            delete.setOnAction(event -> {
                games.remove(game);
                saveGames();
                renderGames();
            });

            HBox card = new HBox(16, name, status, delete);
            card.setAlignment(Pos.CENTER_LEFT);
            card.getStyleClass().add("game-card");

            HBox.setHgrow(name, Priority.ALWAYS);

            gameList.getChildren().add(card);
        }
    }

    private void loadGames() {
        if (!Files.exists(saveFile)) {
            games.add(new Game("Red Dead Redemption 2", "Playing"));
            games.add(new Game("Fortnite", "Playing"));
            games.add(new Game("Stardew Valley", "Wishlist"));
            return;
        }

        Properties data = new Properties();

        try (Reader reader = Files.newBufferedReader(
                saveFile, StandardCharsets.UTF_8)) {

            data.load(reader);

            int count = Integer.parseInt(
                data.getProperty("count", "0")
            );

            List<Game> loaded = new ArrayList<>();

            for (int i = 0; i < count; i++) {
                String title = data.getProperty(i + ".title");
                String status = data.getProperty(i + ".status");

                if (title == null || title.isBlank()
                        || !STATUSES.contains(status)) {
                    throw new IllegalArgumentException(
                        "Invalid saved game."
                    );
                }

                loaded.add(new Game(title, status));
            }

            games.addAll(loaded);

        } catch (IOException | IllegalArgumentException error) {
            showMessage(
                "Could not read your saved shelf. "
                + "The app will close to protect the saved file."
            );
            throw new IllegalStateException(
                "Could not load saved games.", error
            );
        }
    }
  private void saveGames() {
        Properties data = new Properties();
        data.setProperty("count", String.valueOf(games.size()));

        for (int i = 0; i < games.size(); i++) {
            data.setProperty(i + ".title", games.get(i).title);
            data.setProperty(i + ".status", games.get(i).status);
        }

        Path temporary = null;

        try {
            Files.createDirectories(saveFile.getParent());

            temporary = Files.createTempFile(
                saveFile.getParent(), "shelf-", ".tmp"
            );

            try (Writer writer = Files.newBufferedWriter(
                    temporary, StandardCharsets.UTF_8)) {
                data.store(writer, "MyGameShelf");
            }

            try {
                Files.move(
                    temporary,
                    saveFile,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                );
            } catch (AtomicMoveNotSupportedException error) {
                Files.move(
                    temporary,
                    saveFile,
                    StandardCopyOption.REPLACE_EXISTING
                );
            }

        } catch (IOException error) {
            showMessage(
                "Your changes are visible, but could not be saved."
            );
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private void showMessage(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("MyGameShelf");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private static class Game {
        private final String title;
        private String status;

        private Game(String title, String status) {
            this.title = title;
            this.status = status;
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
