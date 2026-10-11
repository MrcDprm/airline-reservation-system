package com.mrcdprm.airline.ui;

import com.mrcdprm.airline.core.AircraftType;
import com.mrcdprm.airline.core.Airport;
import com.mrcdprm.airline.core.Money;
import com.mrcdprm.airline.core.Pricing;
import com.mrcdprm.airline.data.Records.Flight;
import com.mrcdprm.airline.service.FlightService;
import com.mrcdprm.airline.service.FlightService.NewFlight;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.OptionalLong;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

/**
 * Yeni uçuş: güzergâh, ilk tarih, saat, uçak, ücretler ve kaç gün tekrarlanacağı. Ücretler mesafeye göre önerilir,
 * süre mesafeden hesaplanır. Kayıt servis tarafında tek işlemde yapılır.
 */
public final class NewFlightDialog extends Dialog<List<Flight>> {

    private final AppContext ctx;
    private final ComboBox<Airport> origin = airportBox();
    private final ComboBox<Airport> destination = airportBox();
    private final DatePicker date = new DatePicker();
    private final TextField time = new TextField("09:00");
    private final ComboBox<AircraftType> aircraft = new ComboBox<>();
    private final TextField economy = Ui.amountField();
    private final TextField business = Ui.amountField();
    private final Spinner<Integer> days = new Spinner<>(1, FlightService.MAX_REPEAT_DAYS, 7);
    private final Label info = Ui.muted("");
    private final Label error = Ui.errorLabel();
    private List<Flight> created;

    public NewFlightDialog(AppContext ctx) {
        this.ctx = ctx;
        initOwner(ctx.window());
        setTitle(I18n.t("flights.new"));
        Theme.style(getDialogPane());

        origin.setValue(Airport.IST);
        destination.setValue(Airport.ADB);
        date.setValue(ctx.airline().today().plusDays(1));
        time.setPromptText("HH:mm");
        aircraft.getItems().setAll(AircraftType.values());
        aircraft.setValue(AircraftType.A320);
        aircraft.setCellFactory(l -> aircraftCell());
        aircraft.setButtonCell(aircraftCell());
        days.setEditable(true);
        origin.valueProperty().addListener((obs, o, n) -> suggest());
        destination.valueProperty().addListener((obs, o, n) -> suggest());
        time.textProperty().addListener((obs, o, n) -> updateInfo());
        suggest();

        final GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.addRow(0, Ui.field(I18n.t("search.from"), origin), Ui.field(I18n.t("search.to"), destination));
        grid.addRow(1, Ui.field(I18n.t("flights.firstDate"), date), Ui.field(I18n.t("flights.time"), time));
        grid.addRow(2, Ui.field(I18n.t("flights.col.aircraft"), aircraft), Ui.field(I18n.t("flights.repeatDays"), days));
        grid.addRow(3, Ui.field(I18n.t("flights.economyFare"), economy), Ui.field(I18n.t("flights.businessFare"), business));
        for (var n : List.of(origin, destination, date, aircraft, days))
            n.setMaxWidth(Double.MAX_VALUE);
        final VBox content = new VBox(12, grid, info, Ui.muted(I18n.t("flights.fareHint")), error);
        content.setPadding(new Insets(6, 4, 0, 4));
        content.setPrefWidth(480);
        getDialogPane().setContent(content);

        final ButtonType save = new ButtonType(I18n.t("flights.create"), ButtonBar.ButtonData.OK_DONE);
        final ButtonType cancel = new ButtonType(I18n.t("action.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        getDialogPane().getButtonTypes().addAll(save, cancel);
        getDialogPane().lookupButton(save).addEventFilter(ActionEvent.ACTION, e -> {
            if (!create())
                e.consume();
        });
        setResultConverter(b -> b == save ? created : null);
    }

    private static ComboBox<Airport> airportBox() {
        final ComboBox<Airport> box = new ComboBox<>();
        box.getItems().setAll(Airport.values());
        box.setCellFactory(l -> airportCell());
        box.setButtonCell(airportCell());
        return box;
    }

    private static ListCell<Airport> airportCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(Airport a, boolean empty) {
                super.updateItem(a, empty);
                setText(empty || a == null ? null : I18n.airport(a));
            }
        };
    }

    private static ListCell<AircraftType> aircraftCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(AircraftType a, boolean empty) {
                super.updateItem(a, empty);
                setText(empty || a == null ? null : I18n.t("flights.aircraftSeats", a.displayName(), a.capacity()));
            }
        };
    }

    /** Mesafeye göre önerilen ücretleri alanlara yazar. */
    private void suggest() {
        if (origin.getValue() == null || destination.getValue() == null || origin.getValue() == destination.getValue()) {
            updateInfo();
            return;
        }
        final double km = Airport.distanceKm(origin.getValue(), destination.getValue());
        economy.setText(Money.format(Pricing.suggestedEconomyBase(km), I18n.locale()).replace(" ₺", ""));
        business.setText(Money.format(Pricing.suggestedBusinessBase(km), I18n.locale()).replace(" ₺", ""));
        updateInfo();
    }

    private void updateInfo() {
        final Airport a = origin.getValue();
        final Airport b = destination.getValue();
        if (a == null || b == null || a == b) {
            info.setText("");
            return;
        }
        final String number = parseTime() == null ? "-" : ctx.airline().flights().suggestNumber(ctx.session(), a, b, parseTime());
        info.setText(I18n.t("flights.newInfo", number, Math.round(Airport.distanceKm(a, b)), I18n.duration(Airport.flightMinutes(a, b))));
    }

    private LocalTime parseTime() {
        try {
            return LocalTime.parse(time.getText().strip());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private boolean create() {
        Ui.showError(error, null);
        final LocalTime t = parseTime();
        if (t == null) {
            Ui.showError(error, I18n.t("error.invalidTime"));
            return false;
        }
        final OptionalLong eco = Ui.amount(economy);
        final OptionalLong bus = Ui.amount(business);
        if (eco.isEmpty() || bus.isEmpty()) {
            Ui.showError(error, I18n.t("error.invalidFare"));
            return false;
        }
        try {
            created = ctx.airline().flights().create(ctx.session(), new NewFlight(origin.getValue(),
                    destination.getValue(), date.getValue(), t, aircraft.getValue(), eco.getAsLong(), bus.getAsLong(),
                    days.getValue()));
            return true;
        } catch (RuntimeException e) {
            Ui.showError(error, Ui.message(e));
            return false;
        }
    }
}
