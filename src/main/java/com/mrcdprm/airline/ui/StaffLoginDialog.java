package com.mrcdprm.airline.ui;

import atlantafx.base.controls.PasswordTextField;
import atlantafx.base.theme.Styles;
import com.mrcdprm.airline.service.SampleData;
import com.mrcdprm.airline.service.Session;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;

/**
 * Personel girişi. Şifre doğrulaması (Argon2id) arka planda yapılır; pencere bu sırada donmaz.
 * Örnek veri yüklüyse demo hesabıyla tek tıkla giriş düğmesi görünür.
 */
public final class StaffLoginDialog extends Dialog<Session> {

    private final AppContext ctx;
    private final TextField username = new TextField();
    private final PasswordTextField password = new PasswordTextField();
    private final Label error = Ui.errorLabel();
    private Session result;

    public StaffLoginDialog(AppContext ctx) {
        this.ctx = ctx;
        initOwner(ctx.window());
        setTitle(I18n.t("staff.signIn"));
        Theme.style(getDialogPane());

        final Label title = new Label(I18n.t("staff.loginTitle"));
        title.getStyleClass().add(Styles.TITLE_3);
        final VBox content = new VBox(12, title, Ui.muted(I18n.t("staff.loginSubtitle")),
                Ui.field(I18n.t("field.username"), username), Ui.field(I18n.t("field.password"), password), error);
        if (ctx.settings().sampleData()) {
            final Button demo = Ui.button(I18n.t("staff.demo", SampleData.STAFF_USERNAME), Feather.PLAY, Styles.FLAT);
            demo.setOnAction(e -> {
                username.setText(SampleData.STAFF_USERNAME);
                password.setText(SampleData.STAFF_PASSWORD);
            });
            content.getChildren().add(demo);
        }
        content.setPadding(new Insets(8, 6, 0, 6));
        content.setPrefWidth(380);
        getDialogPane().setContent(content);

        final ButtonType signIn = new ButtonType(I18n.t("staff.signInButton"), ButtonBar.ButtonData.OK_DONE);
        final ButtonType cancel = new ButtonType(I18n.t("action.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        getDialogPane().getButtonTypes().addAll(signIn, cancel);
        final Node signInButton = getDialogPane().lookupButton(signIn);
        // Pencere hemen kapanmasın: önce giriş arka planda denenir, başarılıysa kapanır
        signInButton.addEventFilter(ActionEvent.ACTION, e -> {
            e.consume();
            login();
        });
        setResultConverter(b -> b == signIn ? result : null);
        javafx.application.Platform.runLater(username::requestFocus);
    }

    private void login() {
        Ui.showError(error, null);
        final String user = username.getText();
        final String pass = password.getPassword();
        Ui.background(getDialogPane(), () -> ctx.airline().auth().login(user, pass), session -> {
            result = session;
            setResult(session);
            close();
        }, ex -> {
            password.setText("");
            Ui.showError(error, Ui.message(ex));
        });
    }
}
