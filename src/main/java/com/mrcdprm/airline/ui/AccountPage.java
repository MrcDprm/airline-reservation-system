package com.mrcdprm.airline.ui;

import atlantafx.base.controls.PasswordTextField;
import atlantafx.base.theme.Styles;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;

/** Hesabım (personel): ad, kullanıcı adı ve şifre değiştirme. */
public final class AccountPage extends VBox {

    public AccountPage(AppContext ctx) {
        super(16);
        final PasswordTextField current = new PasswordTextField();
        final PasswordTextField next = new PasswordTextField();
        final PasswordTextField repeat = new PasswordTextField();
        final PasswordRulesView rules = new PasswordRulesView(next, new SimpleStringProperty(ctx.session().username()));
        final Label error = Ui.errorLabel();
        final Button save = Ui.button(I18n.t("account.save"), Feather.CHECK, Styles.ACCENT);
        save.setOnAction(e -> {
            Ui.showError(error, null);
            if (!next.getPassword().equals(repeat.getPassword())) {
                Ui.showError(error, I18n.t("error.passwordMismatch"));
                return;
            }
            final String oldValue = current.getPassword();
            final String newValue = next.getPassword();
            Ui.background(save, () -> {
                ctx.airline().auth().changePassword(ctx.session(), oldValue, newValue);
                return true;
            }, ok -> {
                current.setText("");
                next.setText("");
                repeat.setText("");
                Ui.info(ctx.window(), I18n.t("account.changed"));
            }, ex -> Ui.showError(error, Ui.message(ex)));
        });

        final VBox profile = Ui.card(Ui.section(I18n.t("account.profile")),
                new Label(ctx.session().fullName()), Ui.muted("@" + ctx.session().username()));
        final VBox password = Ui.card(Ui.section(I18n.t("account.password")),
                Ui.field(I18n.t("account.current"), current), Ui.field(I18n.t("account.new"), next), rules,
                Ui.field(I18n.t("field.passwordRepeat"), repeat), error, save);
        profile.setMaxWidth(460);
        password.setMaxWidth(460);
        getChildren().addAll(Ui.header(I18n.t("nav.account"), I18n.t("account.subtitle")), profile, password);
    }
}
