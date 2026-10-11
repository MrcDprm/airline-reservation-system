package com.mrcdprm.airline.ui;

import atlantafx.base.theme.Styles;
import com.mrcdprm.airline.core.Airport;
import com.mrcdprm.airline.core.CabinClass;
import com.mrcdprm.airline.data.Records.Flight;
import com.mrcdprm.airline.service.Airline;
import com.mrcdprm.airline.service.FlightService.Offer;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DateCell;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Spinner;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Uçuş arama: nereden, nereye, tarih, yolcu sayısı ve kabin. Altında ±3 günün en düşük fiyatlarını gösteren
 * tarih şeridi ve sonuç kartları. Sayfa ilk açılışta yarının İstanbul-Ankara uçuşlarını gösterir.
 */
public final class SearchPage extends VBox {

    private final AppContext ctx;
    private final PublicShell shell;
    private final ComboBox<Airport> from = airportBox();
    private final ComboBox<Airport> to = airportBox();
    private final DatePicker date = new DatePicker();
    private final Spinner<Integer> adults = new Spinner<>(1, Airline.MAX_PASSENGERS, 1);
    private final Spinner<Integer> children = new Spinner<>(0, Airline.MAX_PASSENGERS - 1, 0);
    private final ToggleButton economy = new ToggleButton(I18n.of(CabinClass.ECONOMY));
    private final ToggleButton business = new ToggleButton(I18n.of(CabinClass.BUSINESS));
    private final HBox dateStrip = new HBox(6);
    private final VBox results = new VBox(10);
    private final Label error = Ui.errorLabel();

    public SearchPage(AppContext ctx, PublicShell shell) {
        super(18);
        this.ctx = ctx;
        this.shell = shell;

        final Label title = new Label(I18n.t("search.title"));
        title.getStyleClass().add("hero-title");
        final Label subtitle = new Label(I18n.t("search.subtitle"));
        subtitle.getStyleClass().add("hero-subtitle");

        from.setValue(Airport.IST);
        to.setValue(Airport.ESB);
        final Button swap = Ui.iconButton(Feather.REPEAT, I18n.t("search.swap"));
        swap.setOnAction(e -> {
            final Airport a = from.getValue();
            from.setValue(to.getValue());
            to.setValue(a);
        });

        final LocalDate today = ctx.airline().today();
        date.setValue(today.plusDays(1));
        date.setEditable(false);
        date.setDayCellFactory(p -> new DateCell() {
            @Override
            public void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                setDisable(empty || item.isBefore(today) || item.isAfter(today.plusDays(330)));
            }
        });
        adults.setPrefWidth(84);
        children.setPrefWidth(84);
        // toplam yolcu en fazla 6: yetişkin arttıkça çocuk sınırı düşer
        adults.valueProperty().addListener((obs, old, n) -> limitChildren());
        limitChildren();

        final ToggleGroup cabinGroup = new ToggleGroup();
        economy.setToggleGroup(cabinGroup);
        business.setToggleGroup(cabinGroup);
        economy.getStyleClass().add(Styles.LEFT_PILL);
        business.getStyleClass().add(Styles.RIGHT_PILL);
        economy.setSelected(true);
        cabinGroup.selectedToggleProperty().addListener((obs, old, now) -> {
            if (now == null)
                cabinGroup.selectToggle(old);
        });

        final Button search = Ui.button(I18n.t("search.button"), Feather.SEARCH, Styles.ACCENT, "search-button");
        search.setDefaultButton(true);
        search.setOnAction(e -> search(date.getValue()));

        from.setMaxWidth(Double.MAX_VALUE);
        to.setMaxWidth(Double.MAX_VALUE);
        date.setMaxWidth(Double.MAX_VALUE);
        final HBox route = new HBox(8, grow(Ui.field(I18n.t("search.from"), from)), swapBox(swap),
                grow(Ui.field(I18n.t("search.to"), to)), grow(Ui.field(I18n.t("search.date"), date)));
        route.setAlignment(Pos.BOTTOM_LEFT);
        final HBox options = new HBox(14, Ui.field(I18n.t("search.adults"), adults),
                Ui.field(I18n.t("search.children"), children),
                Ui.field(I18n.t("search.cabin"), new HBox(economy, business)), Ui.spacer(), search);
        options.setAlignment(Pos.BOTTOM_LEFT);
        final Label childHint = Ui.muted(I18n.t("search.childHint"));

        final VBox form = new VBox(14, route, options, childHint, error);
        form.getStyleClass().add("search-card");
        form.setPadding(new Insets(20, 22, 18, 22));

        final VBox hero = new VBox(4, title, subtitle);
        hero.setPadding(new Insets(4, 0, 0, 2));

        dateStrip.setAlignment(Pos.CENTER_LEFT);
        getChildren().addAll(hero, form, dateStrip, results);
        search(date.getValue());
    }

    private static VBox grow(VBox box) {
        HBox.setHgrow(box, Priority.ALWAYS);
        box.setPrefWidth(200);
        return box;
    }

    private static VBox swapBox(Button swap) {
        final VBox box = new VBox(swap);
        box.setAlignment(Pos.BOTTOM_CENTER);
        return box;
    }

    private void limitChildren() {
        final int max = Airline.MAX_PASSENGERS - adults.getValue();
        final int value = Math.min(children.getValue(), max);
        children.setValueFactory(new javafx.scene.control.SpinnerValueFactory.IntegerSpinnerValueFactory(0, max, value));
    }

    private static ComboBox<Airport> airportBox() {
        final ComboBox<Airport> box = new ComboBox<>();
        box.getItems().setAll(Airport.values());
        box.getItems().sort((a, b) -> java.text.Collator.getInstance(I18n.locale()).compare(I18n.airportLong(a), I18n.airportLong(b)));
        box.setConverter(new StringConverter<>() {
            @Override
            public String toString(Airport a) {
                return a == null ? "" : I18n.airport(a);
            }

            @Override
            public Airport fromString(String s) {
                return null;
            }
        });
        box.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(Airport a, boolean empty) {
                super.updateItem(a, empty);
                setText(empty || a == null ? null : I18n.airportLong(a));
            }
        });
        box.setVisibleRowCount(12);
        return box;
    }

    private CabinClass cabin() {
        return business.isSelected() ? CabinClass.BUSINESS : CabinClass.ECONOMY;
    }

    private int passengers() {
        return adults.getValue() + children.getValue();
    }

    private void search(LocalDate day) {
        Ui.showError(error, null);
        results.getChildren().clear();
        dateStrip.getChildren().clear();
        if (day == null)
            return;
        try {
            final Map<LocalDate, Long> lowest = ctx.airline().flights().lowestFares(from.getValue(), to.getValue(), day,
                    cabin(), passengers());
            final List<Offer> offers = ctx.airline().flights().search(from.getValue(), to.getValue(), day, cabin(),
                    passengers());
            date.setValue(day);
            fillDateStrip(day, lowest);
            fillResults(day, offers);
        } catch (RuntimeException e) {
            Ui.showError(error, Ui.message(e));
        }
    }

    private void fillDateStrip(LocalDate selected, Map<LocalDate, Long> lowest) {
        final ToggleGroup group = new ToggleGroup();
        lowest.forEach((d, price) -> {
            final Label day = new Label(I18n.shortDate(d));
            day.getStyleClass().add("strip-date");
            final Label value = new Label(price == null ? "—" : I18n.money(price));
            value.getStyleClass().add("strip-price");
            final VBox text = new VBox(2, day, value);
            text.setAlignment(Pos.CENTER);
            final ToggleButton b = new ToggleButton();
            b.setGraphic(text);
            b.setToggleGroup(group);
            b.getStyleClass().add("strip-item");
            b.setSelected(d.equals(selected));
            b.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(b, Priority.ALWAYS);
            b.setOnAction(e -> search(d));
            dateStrip.getChildren().add(b);
        });
    }

    private void fillResults(LocalDate day, List<Offer> offers) {
        final Label heading = Ui.section(I18n.t("search.resultsTitle", I18n.airport(from.getValue()),
                I18n.airport(to.getValue()), I18n.date(day)));
        results.getChildren().add(heading);
        if (offers.isEmpty()) {
            final VBox empty = Ui.card(new FontIcon(Feather.CALENDAR), new Label(I18n.t("search.noFlights")),
                    Ui.muted(I18n.t("search.noFlightsHint")));
            empty.setAlignment(Pos.CENTER);
            empty.setPadding(new Insets(30));
            results.getChildren().add(empty);
            return;
        }
        // "En uygun" rozeti sadece bir uçuşta: fiyatı eşit olanlardan en erken kalkan
        final Offer cheapest = offers.stream().filter(Offer::bookable)
                .min(java.util.Comparator.comparingLong(Offer::adultFare)).orElse(null);
        for (Offer offer : offers)
            results.getChildren().add(card(offer, offer == cheapest));
    }

    private HBox card(Offer offer, boolean cheapest) {
        final Flight f = offer.flight();
        final HBox timeline = Views.timeline(f);
        HBox.setHgrow(timeline, Priority.ALWAYS);
        timeline.setMaxWidth(420);

        final Label number = new Label(f.number());
        number.getStyleClass().add("flight-number");
        final HBox meta = new HBox(8, number, Ui.muted("· " + f.aircraft().displayName()));
        meta.setAlignment(Pos.CENTER_LEFT);
        if (f.delayMinutes() > 0)
            meta.getChildren().add(Views.flightStatus(f, ctx.airline().now()));
        if (cheapest)
            meta.getChildren().add(Views.badge(I18n.t("search.cheapest"), "badge-info"));
        final VBox left = new VBox(10, meta, timeline);
        HBox.setHgrow(left, Priority.ALWAYS);

        final Label price = new Label(I18n.money(offer.adultFare()));
        price.getStyleClass().add("offer-price");
        final Label per = Ui.muted(I18n.t("search.perAdult"));
        final Label seats;
        if (!offer.bookable()) {
            final boolean closed = !f.departure().isAfter(ctx.airline().now().plusMinutes(Airline.CLOSE_MINUTES));
            seats = Views.badge(I18n.t(closed ? "search.salesClosed" : "search.notEnoughSeats"), "badge-muted");
        } else if (offer.seatsLeft() <= 9) {
            seats = Views.badge(I18n.t("search.lastSeats", offer.seatsLeft()), "badge-warning");
        } else {
            seats = new Label();
        }
        final Button select = Ui.button(I18n.t("search.select"), Feather.ARROW_RIGHT, Styles.ACCENT);
        select.setContentDisplay(javafx.scene.control.ContentDisplay.RIGHT);
        select.setDisable(!offer.bookable());
        select.setOnAction(e -> shell.show(new BookingWizard(ctx, offer, adults.getValue(), children.getValue(),
                () -> {
                    shell.show(this);
                    search(date.getValue());
                }, shell::showManage)));
        final VBox right = new VBox(4, seats, price, per, select);
        right.setAlignment(Pos.CENTER_RIGHT);
        right.setMinWidth(170);

        final HBox card = new HBox(24, left, right);
        card.getStyleClass().addAll("panel", "offer-card");
        if (!offer.bookable())
            card.getStyleClass().add("offer-disabled");
        card.setPadding(new Insets(16, 20, 16, 20));
        card.setAlignment(Pos.CENTER_LEFT);
        return card;
    }
}
