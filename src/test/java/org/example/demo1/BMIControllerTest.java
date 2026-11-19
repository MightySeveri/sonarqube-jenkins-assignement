package org.example.demo1;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.NodeOrientation;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.mockito.ArgumentMatchers.doubleThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

class BMIControllerTest {
    private static final String TIME_PATTERN = " (0[1-9]|1[0-2]):[0-5][0-9]:[0-5][0-9]";
    private static boolean toolkitStarted;

    private VBox root;
    private Label weightLabel;
    private Label heightLabel;
    private Label resultLabel;
    private Label timeLabel;
    private TextField weightField;
    private TextField heightField;
    private Button calculate;

    @BeforeAll
    static void startJavaFx() throws InterruptedException {
        CountDownLatch ready = new CountDownLatch(1);
        try {
            Platform.startup(() -> {
                Platform.setImplicitExit(false);
                ready.countDown();
            });
            toolkitStarted = true;
        } catch (UnsupportedOperationException exception) {
            assumeFalse("Unable to open DISPLAY".equals(exception.getMessage()),
                    "JavaFX requires a display. Run with xvfb-run on headless Linux.");
            throw exception;
        }
        assertTrue(ready.await(10, TimeUnit.SECONDS), "JavaFX did not start");
    }

    @AfterAll
    static void stopJavaFx() {
        if (toolkitStarted) {
            Platform.exit();
        }
    }

    @BeforeEach
    void loadView() throws Exception {
        onFxThread(() -> {
            try (MockedStatic<LocalizationService> translations = mockStatic(LocalizationService.class)) {
                translations.when(() -> LocalizationService.getLocalizedStrings(Locale.US))
                        .thenReturn(Map.of());
                FXMLLoader loader = new FXMLLoader(HelloApplication.class.getResource("hello-view.fxml"));
                assertDoesNotThrow(() -> {
                    root = loader.load();
                });
                translations.verify(() -> LocalizationService.getLocalizedStrings(Locale.US));

                Map<String, Object> controls = loader.getNamespace();
                weightLabel = (Label) controls.get("lblWeight");
                heightLabel = (Label) controls.get("lblHeight");
                resultLabel = (Label) controls.get("lblResult");
                timeLabel = (Label) controls.get("lblLocalTime");
                weightField = (TextField) controls.get("tfWeight");
                heightField = (TextField) controls.get("tfHeight");
                calculate = (Button) controls.get("btnCalculate");
            }
        });
    }

    @Test
    void applicationOpensTheBmiView() throws Exception {
        onFxThread(() -> {
            try (MockedStatic<LocalizationService> translations = mockStatic(LocalizationService.class)) {
                translations.when(() -> LocalizationService.getLocalizedStrings(Locale.US))
                        .thenReturn(Map.of());
                Stage stage = new Stage();
                try {
                    assertDoesNotThrow(() -> new HelloApplication().start(stage));

                    assertTrue(stage.isShowing());
                    Button button = (Button) stage.getScene().lookup("#btnCalculate");
                    assertEquals("Calculate", button.getText());
                } finally {
                    stage.close();
                }
            }
        });
    }

    @Test
    void startsInEnglishWithFallbackLabelsAndLeftToRightLayout() throws Exception {
        onFxThread(() -> {
            assertEquals("Weight", weightLabel.getText());
            assertEquals("Height", heightLabel.getText());
            assertEquals("Calculate", calculate.getText());
            assertEquals("", resultLabel.getText());
            assertTrue(timeLabel.getText().matches("Local Time" + TIME_PATTERN));
            assertDirection(NodeOrientation.LEFT_TO_RIGHT, "left");
        });
    }

    @ParameterizedTest
    @CsvSource({
            "FA!, fa-IR, وزن, قد, محاسبه, زمان محلی, RIGHT_TO_LEFT, right",
            "UR!, ur-PA, وزن, اونچائی, حساب, مقامی وقت, RIGHT_TO_LEFT, right",
            "EN!, en-US, Weight, Height, Calculate, Local Time, LEFT_TO_RIGHT, left",
            "FR!, fr-FR, Poids, Taille, Calculer, Heure locale, LEFT_TO_RIGHT, left",
            "VI!, vi-VI, Cân nặng, Chiều cao, Tính BMI, Giờ địa phương, LEFT_TO_RIGHT, left"
    })
    void languageButtonsTranslateTheViewAndSetLayoutDirection(
            String button, String languageTag, String weight, String height, String buttonText,
            String time, NodeOrientation orientation, String alignment) throws Exception {
        Locale locale = Locale.forLanguageTag(languageTag);
        onFxThread(() -> {
            try (MockedStatic<LocalizationService> translations = mockStatic(LocalizationService.class)) {
                if (orientation == NodeOrientation.LEFT_TO_RIGHT) {
                    translations.when(() -> LocalizationService.getLocalizedStrings(Locale.forLanguageTag("fa-IR")))
                            .thenReturn(Map.of());
                    clickLanguage("FA!");
                }
                translations.when(() -> LocalizationService.getLocalizedStrings(locale))
                        .thenReturn(Map.of("weight", weight, "height", height, "calculate", buttonText,
                                "localTime", time));
                resultLabel.setText("Previous result");

                clickLanguage(button);

                translations.verify(() -> LocalizationService.getLocalizedStrings(locale));
                assertEquals(weight, weightLabel.getText());
                assertEquals(height, heightLabel.getText());
                assertEquals(buttonText, calculate.getText());
                assertEquals("", resultLabel.getText());
                assertTrue(timeLabel.getText().matches(time + TIME_PATTERN));
            }
        });

        onFxThread(() -> {
            assertDirection(orientation, alignment);
        });
    }

    @ParameterizedTest
    @CsvSource({
            "80, 200, 80, 2.0, 20.0, 20.00",
            "72.5, 175, 72.5, 1.75, 23.6734693877551, 23.67",
            "' 65 ', 182.5, 65, 1.825, 19.51585663351473, 19.52"
    })
    void calculatesBmiFromCentimetersAndSavesTheUnroundedResult(
            String weightText, String heightText, double weight, double height,
            double bmi, double displayedBmi) throws Exception {
        onFxThread(() -> {
            try (MockedStatic<BMIResultService> results = mockStatic(BMIResultService.class)) {
                weightField.setText(weightText);
                heightField.setText(heightText);
                calculate.fire();

                assertEquals(String.format("Your BMI is %.2f", displayedBmi), resultLabel.getText());
                results.verify(() -> BMIResultService.saveResult(eq(weight), eq(height),
                        doubleThat(savedBmi -> Math.abs(savedBmi - bmi) < 0.000001), eq("en")));
            }
        });
    }

    @ParameterizedTest
    @CsvSource({"'', 175", "70, ''", "heavy, 175", "70, tall"})
    void invalidInputReplacesThePreviousResultAndDoesNotSave(String weight, String height) throws Exception {
        onFxThread(() -> {
            try (MockedStatic<BMIResultService> results = mockStatic(BMIResultService.class)) {
                resultLabel.setText("Previous result");
                weightField.setText(weight);
                heightField.setText(height);

                calculate.fire();

                assertEquals("Invalid input", resultLabel.getText());
                results.verifyNoInteractions();
            }
        });
    }

    @Test
    void usesTheSelectedLanguageForResultMessagesAndPersistence() throws Exception {
        onFxThread(() -> {
            try (MockedStatic<LocalizationService> translations = mockStatic(LocalizationService.class);
                 MockedStatic<BMIResultService> results = mockStatic(BMIResultService.class)) {
                translations.when(() -> LocalizationService.getLocalizedStrings(Locale.FRANCE))
                        .thenReturn(Map.of("result", "Votre IMC est", "invalid", "Saisie incorrecte"));
                clickLanguage("FR!");
                weightField.setText("80");
                heightField.setText("200");

                calculate.fire();

                assertEquals(String.format("Votre IMC est %.2f", 20.0), resultLabel.getText());
                results.verify(() -> BMIResultService.saveResult(80, 2, 20, "fr"));

                heightField.setText("incorrect");
                calculate.fire();

                assertEquals("Saisie incorrecte", resultLabel.getText());
                results.verifyNoMoreInteractions();
            }
        });
    }

    private void clickLanguage(String text) {
        Button button = root.lookupAll(".button").stream()
                .map(Button.class::cast)
                .filter(candidate -> candidate.getText().equals(text))
                .findFirst().orElseThrow();
        button.fire();
    }

    private void assertDirection(NodeOrientation orientation, String alignment) {
        assertEquals(orientation, root.getNodeOrientation());
        assertEquals("-fx-text-alignment: " + alignment + ";", weightField.getStyle());
        assertEquals("-fx-text-alignment: " + alignment + ";", heightField.getStyle());
    }

    private static void onFxThread(Runnable action) throws Exception {
        FutureTask<Void> task = new FutureTask<>(action, null);
        Platform.runLater(task);
        task.get(15, TimeUnit.SECONDS);
    }
}
