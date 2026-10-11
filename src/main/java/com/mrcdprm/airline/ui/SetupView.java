package com.mrcdprm.airline.ui;

import atlantafx.base.controls.PasswordTextField;
import atlantafx.base.theme.Styles;
import com.mrcdprm.airline.service.SampleData;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;

/** İlk açılış: yönetici hesabı oluşturulur, istenirse örnek uçuşlar ve rezervasyonlar yüklenir. */
public final class SetupView extends BorderPane {

    public SetupView(AppContext ctx) {
        getStyleClass().add("auth-background");
        final TopTools tools = new TopTools(ctx);
        tools.setPadding(new Insets(10, 14, 0, 0));
        setTop(tools);

        final Label title = new Label(I18n.t("setup.title"));
        title.getStyleClass().add(Styles.TITLE_2);
        final Label intro = Ui.muted(I18n.t("setup.intro"));

        final TextField fullName = new TextField();
        final TextField username = new TextField();
        username.setPromptText("yonetici");
        final PasswordTextField password = new PasswordTextField();
        final PasswordTextField repeat = new PasswordTextField();
        final PasswordRulesView rules = new PasswordRulesView(password, username.textProperty());
        final CheckBox sample = new CheckBox(I18n.t("setup.sample"));
        sample.setSelected(true);
        sample.setWrapText(true);
        final Label sampleHint = Ui.muted(I18n.t("setup.sampleHint"));
        final Label error = Ui.errorLabel();
        final Button create = Ui.button(I18n.t("setup.create"), Feather.CHECK, Styles.ACCENT);
        create.setDefaultButton(true);
        create.setMaxWidth(Double.MAX_VALUE);

        create.setOnAction(e -> {
            if (!password.getPassword().equals(repeat.getPassword())) {
                Ui.showError(error, I18n.t("error.passwordMismatch"));
                return;
            }
            Ui.showError(error, null);
            final boolean loadSample = sample.isSelected();
            final String user = username.getText();
            final String name = fullName.getText();
            final String pass = password.getPassword();
            create.setText(I18n.t(loadSample ? "setup.loading" : "setup.create"));
            Ui.background(this, () -> {
                ctx.airline().auth().createFirstAdmin(user, name, pass);
                if (loadSample)
                    SampleData.load(ctx.airline());
                return loadSample;
            }, loaded -> {
                ctx.settings().setSampleData(loaded);
                ctx.nav().showPublic();
                Ui.info(ctx.window(), I18n.t(loaded ? "setup.doneSample" : "setup.done", SampleData.DEMO_PNR,
                        SampleData.DEMO_SURNAME));
            }, ex -> {
                create.setText(I18n.t("setup.create"));
                Ui.showError(error, Ui.message(ex));
            });
        });

        final VBox form = new VBox(12, title, intro,
                Ui.field(I18n.t("field.fullName"), fullName),
                Ui.field(I18n.t("field.username"), username),
                Ui.field(I18n.t("field.password"), password), rules,
                Ui.field(I18n.t("field.passwordRepeat"), repeat),
                sample, sampleHint, error, create);
        form.getStyleClass().add("auth-card");
        form.setMaxWidth(440);
        form.setMaxHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        form.setPadding(new Insets(28));

        final StackPane center = new StackPane(form);
        center.setAlignment(Pos.CENTER);
        center.setPadding(new Insets(20));
        final ScrollPane scroll = new ScrollPane(center);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.getStyleClass().add("transparent-scroll");
        setCenter(scroll);
    }
}
