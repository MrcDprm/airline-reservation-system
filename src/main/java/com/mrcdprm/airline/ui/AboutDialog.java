package com.mrcdprm.airline.ui;

import atlantafx.base.theme.Styles;
import com.mrcdprm.airline.AirlineApp;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** Hakkında penceresi: sürüm, açıklama, kaynak kodu, lisans ve kullanılan kütüphaneler. */
public final class AboutDialog extends Dialog<Void> {

    public AboutDialog(AppContext ctx) {
        initOwner(ctx.window());
        setTitle(I18n.t("about.title"));
        getDialogPane().getButtonTypes().add(new ButtonType(I18n.t("action.close"), ButtonBar.ButtonData.CANCEL_CLOSE));
        Theme.style(getDialogPane());

        final ImageView icon = new ImageView(new Image(getClass().getResourceAsStream("/com/mrcdprm/airline/icon.png")));
        icon.setFitWidth(64);
        icon.setFitHeight(64);

        final Label title = new Label(I18n.t("app.name"));
        title.getStyleClass().add(Styles.TITLE_3);
        final Label version = Ui.muted(I18n.t("about.version", AirlineApp.VERSION));
        final Label description = new Label(I18n.t("about.description"));
        description.setWrapText(true);
        final Hyperlink repo = new Hyperlink(I18n.t("about.source"));
        repo.setOnAction(e -> ctx.openUrl(AirlineApp.REPO_URL));
        repo.setPadding(Insets.EMPTY);
        final Label license = Ui.muted(I18n.t("about.license"));
        final Label credits = Ui.muted("JavaFX, AtlantaFX, Ikonli, SQLite JDBC, Bouncy Castle, Apache PDFBox, Noto Sans");
        credits.setWrapText(true);

        final Label data = Ui.muted(I18n.t("about.dataFolder", Settings.dataFolder()));
        final VBox text = new VBox(6, title, version, description, repo, license, credits, data);
        // Sabit genişlik: uzun satırlar kenara taşmadan alt satıra geçer
        text.setPrefWidth(400);
        text.setMinWidth(400);
        for (javafx.scene.Node n : text.getChildren()) {
            if (n instanceof Label l) {
                l.setMaxWidth(400);
                l.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
            }
        }
        final HBox content = new HBox(18, icon, text);
        content.setAlignment(Pos.TOP_LEFT);
        content.setPadding(new Insets(12, 16, 4, 8));
        content.setPrefWidth(510);
        getDialogPane().setContent(content);
    }
}
