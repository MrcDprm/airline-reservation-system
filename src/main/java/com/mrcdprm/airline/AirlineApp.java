package com.mrcdprm.airline;

import com.mrcdprm.airline.data.Database;
import com.mrcdprm.airline.service.Airline;
import com.mrcdprm.airline.service.Session;
import com.mrcdprm.airline.ui.AppContext;
import com.mrcdprm.airline.ui.I18n;
import com.mrcdprm.airline.ui.PublicShell;
import com.mrcdprm.airline.ui.SetupView;
import com.mrcdprm.airline.ui.Settings;
import com.mrcdprm.airline.ui.StaffShell;
import com.mrcdprm.airline.ui.Theme;
import com.mrcdprm.airline.ui.Ui;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Locale;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.image.Image;
import javafx.scene.input.InputEvent;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * Uygulama girişi: veritabanını açar; ilk açılışta kurulum ekranını, sonra yolcu ekranını gösterir.
 * Personel oturumu açıkken 10 dakika hareket olmazsa oturum kapanır (ortak kontuar bilgisayarı için).
 */
public class AirlineApp extends Application implements AppContext.Navigator {

    public static final String VERSION = "1.0.0";
    public static final String REPO_URL = "https://github.com/MrcDprm/airline-reservation-system";
    private static final Duration IDLE_LIMIT = Duration.minutes(10);

    private Stage stage;
    private Scene scene;
    private Database db;
    private AppContext ctx;
    private Settings settings;
    private final PauseTransition idle = new PauseTransition(IDLE_LIMIT);

    @Override
    public void start(Stage primaryStage) {
        stage = primaryStage;
        final Path folder = Settings.dataFolder();
        settings = new Settings(folder);
        I18n.setLocale(Locale.forLanguageTag(settings.language()));
        Theme.apply(settings.darkTheme());
        stage.getIcons().add(new Image(getClass().getResourceAsStream("/com/mrcdprm/airline/icon.png")));
        stage.setTitle(I18n.t("app.name"));

        try {
            db = Database.open(folder.resolve("airline.db"));
        } catch (RuntimeException e) {
            new Alert(Alert.AlertType.ERROR, I18n.t("error.databaseOpen", folder)).showAndWait();
            Platform.exit();
            return;
        }
        final Airline airline = new Airline(db, Clock.systemDefaultZone());
        ctx = new AppContext(airline, settings, this, url -> getHostServices().showDocument(url));

        scene = new Scene(new javafx.scene.layout.StackPane(), 1240, 800);
        Theme.addStylesheet(scene);
        scene.addEventFilter(InputEvent.ANY, e -> {
            if (ctx.session() != null)
                idle.playFromStart();
        });
        idle.setOnFinished(e -> {
            // Açık bir diyalog varken oturum kapatılmaz, süre yeniden başlar
            if (javafx.stage.Window.getWindows().size() > 1) {
                idle.playFromStart();
                return;
            }
            if (ctx.session() != null) {
                logout();
                Ui.info(stage, I18n.t("staff.idleLogout"));
            }
        });
        stage.setScene(scene);
        stage.setMinWidth(1040);
        stage.setMinHeight(680);
        if (airline.auth().hasStaff())
            showPublic();
        else
            show(new SetupView(ctx));
        stage.show();
    }

    private void show(Parent root) {
        scene.setRoot(root);
        ctx.setWindow(stage);
    }

    @Override
    public void showPublic() {
        ctx.setSession(null);
        idle.stop();
        stage.setTitle(I18n.t("app.name"));
        show(new PublicShell(ctx));
    }

    @Override
    public void showStaff(Session session) {
        ctx.setSession(session);
        ctx.setPage(null);
        idle.playFromStart();
        stage.setTitle(I18n.t("app.name") + " - " + I18n.t("shell.staffSide"));
        show(new StaffShell(ctx));
    }

    @Override
    public void rebuild() {
        Theme.apply(settings.darkTheme());
        stage.setTitle(I18n.t("app.name"));
        if (!ctx.airline().auth().hasStaff())
            show(new SetupView(ctx));
        else if (ctx.session() != null)
            show(new StaffShell(ctx));
        else
            show(new PublicShell(ctx));
    }

    @Override
    public void logout() {
        ctx.setPage(null);
        showPublic();
    }

    @Override
    public void stop() {
        if (db != null)
            db.close();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
