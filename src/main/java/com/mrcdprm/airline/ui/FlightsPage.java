package com.mrcdprm.airline.ui;

import atlantafx.base.theme.Styles;
import atlantafx.base.theme.Tweaks;
import com.mrcdprm.airline.core.FlightStatus;
import com.mrcdprm.airline.data.Records.Flight;
import com.mrcdprm.airline.service.FlightService.FlightRow;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;

/** Uçuş listesi (personel): tarih aralığı, arama ve durum filtresi; yeni uçuş, rötar, iptal ve yolcu listesi. */
public final class FlightsPage extends VBox {

    private final AppContext ctx;
    private final DatePicker from = new DatePicker();
    private final DatePicker to = new DatePicker();
    private final TextField query = new TextField();
    private final ComboBox<FlightStatus> status = new ComboBox<>();
    private final TableView<FlightRow> table = new TableView<>();
    private final Label count = Ui.muted("");

    public FlightsPage(AppContext ctx) {
        super(14);
        this.ctx = ctx;
        final LocalDate today = ctx.airline().today();
        from.setValue(today);
        to.setValue(today.plusDays(2));
        query.setPromptText(I18n.t("flights.searchHint"));
        status.getItems().add(null);
        status.getItems().addAll(FlightStatus.values());
        status.setButtonCell(statusCell());
        status.setCellFactory(l -> statusCell());
        for (var c : List.of(from, to))
            c.valueProperty().addListener((obs, old, v) -> reload());
        status.valueProperty().addListener((obs, old, v) -> reload());
        query.textProperty().addListener((obs, old, v) -> reload());
        from.setPrefWidth(150);
        to.setPrefWidth(150);
        HBox.setHgrow(query, Priority.ALWAYS);
        final HBox filters = new HBox(10, Ui.field(I18n.t("flights.from"), from), Ui.field(I18n.t("flights.to"), to),
                grow(Ui.field(I18n.t("flights.search"), query)), Ui.field(I18n.t("flights.status"), status));
        filters.setAlignment(Pos.BOTTOM_LEFT);

        final Button add = Ui.button(I18n.t("flights.new"), Feather.PLUS, Styles.ACCENT);
        add.setOnAction(e -> new NewFlightDialog(ctx).showAndWait().ifPresent(created -> {
            from.setValue(created.get(0).departure().toLocalDate());
            reload();
            Ui.info(ctx.window(), I18n.t("flights.created", created.size(), created.get(0).number()));
        }));
        final Button manifest = Ui.button(I18n.t("flights.manifest"), Feather.USERS);
        final Button delay = Ui.button(I18n.t("flights.delay"), Feather.CLOCK);
        final Button cancel = Ui.button(I18n.t("flights.cancel"), Feather.X_CIRCLE, Styles.DANGER);
        for (Button b : List.of(manifest, delay, cancel))
            b.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        manifest.setOnAction(e -> openManifest());
        delay.setOnAction(e -> delaySelected());
        cancel.setOnAction(e -> cancelSelected());
        final HBox actions = new HBox(8, add, Ui.spacer(), manifest, delay, cancel);
        actions.setAlignment(Pos.CENTER_LEFT);

        buildTable();
        VBox.setVgrow(table, Priority.ALWAYS);
        getChildren().addAll(Ui.header(I18n.t("nav.flights"), I18n.t("flights.subtitle")), filters, actions, table, count);
        reload();
    }

    private static VBox grow(VBox box) {
        HBox.setHgrow(box, Priority.ALWAYS);
        return box;
    }

    private static ListCell<FlightStatus> statusCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(FlightStatus s, boolean empty) {
                super.updateItem(s, empty);
                setText(empty ? null : s == null ? I18n.t("flights.allStatuses") : I18n.of(s));
            }
        };
    }

    private void buildTable() {
        final LocalDateTime now = ctx.airline().now();
        table.getStyleClass().addAll(Styles.STRIPED, Tweaks.EDGE_TO_EDGE);
        table.getStyleClass().add("panel");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label(I18n.t("flights.empty")));

        final TableColumn<FlightRow, String> number = text("flights.col.number", r -> r.flight().number(), 80);
        final TableColumn<FlightRow, String> date = text("flights.col.date", r -> I18n.shortDate(r.flight().departure().toLocalDate()), 100);
        final TableColumn<FlightRow, String> route = text("flights.col.route",
                r -> r.flight().origin().code() + " → " + r.flight().destination().code(), 100);
        final TableColumn<FlightRow, String> times = text("flights.col.times",
                r -> I18n.clock(r.flight().departure()) + " – " + I18n.clock(r.flight().arrival()), 110);
        final TableColumn<FlightRow, String> aircraft = text("flights.col.aircraft", r -> r.flight().aircraft().displayName(), 120);
        final TableColumn<FlightRow, FlightRow> load = new TableColumn<>(I18n.t("flights.col.load"));
        load.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        load.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(FlightRow r, boolean empty) {
                super.updateItem(r, empty);
                if (empty || r == null) {
                    setGraphic(null);
                    return;
                }
                final ProgressBar bar = new ProgressBar(r.load());
                bar.setPrefWidth(90);
                if (r.load() >= 0.85)
                    bar.getStyleClass().add("load-high");
                final HBox box = new HBox(8, bar, new Label(r.sold() + "/" + r.capacity()));
                box.setAlignment(Pos.CENTER_LEFT);
                setGraphic(box);
            }
        });
        load.setPrefWidth(170);
        final TableColumn<FlightRow, String> checkIn = text("flights.col.checkIn", r -> r.checkedIn() + "/" + r.sold(), 80);
        final TableColumn<FlightRow, FlightRow> state = new TableColumn<>(I18n.t("flights.col.status"));
        state.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        state.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(FlightRow r, boolean empty) {
                super.updateItem(r, empty);
                setGraphic(empty || r == null ? null : Views.flightStatus(r.flight(), now));
            }
        });
        state.setPrefWidth(140);
        table.getColumns().setAll(List.of(number, date, route, times, aircraft, load, checkIn, state));
        table.setRowFactory(t -> {
            final TableRow<FlightRow> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty())
                    openManifest();
            });
            return row;
        });
    }

    private static TableColumn<FlightRow, String> text(String key, java.util.function.Function<FlightRow, String> value, double width) {
        final TableColumn<FlightRow, String> c = new TableColumn<>(I18n.t(key));
        c.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        c.setPrefWidth(width);
        return c;
    }

    private void reload() {
        if (from.getValue() == null || to.getValue() == null)
            return;
        try {
            final List<FlightRow> rows = ctx.airline().flights().list(ctx.session(), from.getValue(), to.getValue(),
                    query.getText(), status.getValue());
            table.getItems().setAll(rows);
            count.setText(I18n.t("flights.count", rows.size()));
        } catch (RuntimeException e) {
            table.getItems().clear();
            count.setText(Ui.message(e));
        }
    }

    private Flight selected() {
        final FlightRow r = table.getSelectionModel().getSelectedItem();
        return r == null ? null : r.flight();
    }

    private void openManifest() {
        final Flight f = selected();
        if (f != null)
            new ManifestDialog(ctx, f).showAndWait();
    }

    private void delaySelected() {
        final Flight f = selected();
        if (f == null)
            return;
        new DelayDialog(ctx, f).showAndWait().ifPresent(minutes -> {
            try {
                ctx.airline().flights().delay(ctx.session(), f.id(), minutes);
                reload();
            } catch (RuntimeException e) {
                Ui.error(ctx.window(), e);
            }
        });
    }

    private void cancelSelected() {
        final Flight f = selected();
        if (f == null)
            return;
        final FlightRow row = table.getSelectionModel().getSelectedItem();
        if (!Ui.confirm(ctx.window(), I18n.t("flights.cancelConfirm", f.number(), I18n.time(f.departure()), row.sold()),
                I18n.t("flights.cancelYes")))
            return;
        try {
            final int bookings = ctx.airline().flights().cancel(ctx.session(), f.id());
            reload();
            Ui.info(ctx.window(), I18n.t("flights.cancelled", f.number(), bookings));
        } catch (RuntimeException e) {
            Ui.error(ctx.window(), e);
        }
    }
}
