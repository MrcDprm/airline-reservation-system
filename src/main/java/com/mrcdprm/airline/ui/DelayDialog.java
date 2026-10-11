package com.mrcdprm.airline.ui;

import com.mrcdprm.airline.data.Records.Flight;
import com.mrcdprm.airline.service.FlightService;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.layout.VBox;

/** Rötar girişi: kaç dakika gecikeceği; yeni kalkış ve varış saati anında gösterilir. */
public final class DelayDialog extends Dialog<Integer> {

    public DelayDialog(AppContext ctx, Flight f) {
        initOwner(ctx.window());
        setTitle(I18n.t("flights.delay"));
        Theme.style(getDialogPane());

        final Spinner<Integer> minutes = Ui.numberSpinner(5, FlightService.MAX_DELAY_MINUTES, 30, 5);
        final Label preview = new Label();
        final Runnable update = () -> preview.setText(I18n.t("delay.preview",
                I18n.clock(f.departure().plusMinutes(minutes.getValue())), I18n.clock(f.arrival().plusMinutes(minutes.getValue()))));
        minutes.valueProperty().addListener((obs, o, n) -> update.run());
        update.run();

        final VBox content = new VBox(12, new Label(f.number() + " · " + Views.route(f) + " · " + I18n.time(f.departure())),
                Ui.field(I18n.t("delay.minutes"), minutes), preview, Ui.muted(I18n.t("delay.hint")));
        content.setPadding(new Insets(6, 4, 0, 4));
        content.setPrefWidth(420);
        getDialogPane().setContent(content);
        final ButtonType ok = new ButtonType(I18n.t("delay.save"), ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(ok, new ButtonType(I18n.t("action.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE));
        setResultConverter(b -> b == ok ? minutes.getValue() : null);
    }
}
