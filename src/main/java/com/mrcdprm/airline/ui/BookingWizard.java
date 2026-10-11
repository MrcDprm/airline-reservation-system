package com.mrcdprm.airline.ui;

import atlantafx.base.theme.Styles;
import com.mrcdprm.airline.core.AirlineException;
import com.mrcdprm.airline.core.CabinClass;
import com.mrcdprm.airline.core.Names;
import com.mrcdprm.airline.core.PassengerType;
import com.mrcdprm.airline.core.Pricing;
import com.mrcdprm.airline.core.RefundPolicy;
import com.mrcdprm.airline.core.Seat;
import com.mrcdprm.airline.core.Validation;
import com.mrcdprm.airline.data.Records.Flight;
import com.mrcdprm.airline.pdf.Documents;
import com.mrcdprm.airline.service.BookingService.BookingDetail;
import com.mrcdprm.airline.service.BookingService.BookingKey;
import com.mrcdprm.airline.service.BookingService.BookingRequest;
import com.mrcdprm.airline.service.BookingService.PassengerInput;
import com.mrcdprm.airline.service.BookingService.Quote;
import com.mrcdprm.airline.service.FlightService.Offer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Satın alma adımları: 1) yolcu bilgileri, 2) koltuk seçimi, 3) özet ve onay, 4) rezervasyon kodu.
 * Fiyat her adımda servisten yeniden alınır; onayda servis fiyatı tekrar hesaplar ve değiştiyse uyarır.
 */
public final class BookingWizard extends VBox {

    /** Yolcu formundaki bir satır. */
    private static final class PassengerForm {
        final PassengerType type;
        final TextField first = new TextField();
        final TextField last = new TextField();
        String seat;

        PassengerForm(PassengerType type) {
            this.type = type;
            first.setPrefColumnCount(14);
            last.setPrefColumnCount(14);
            for (TextField f : new TextField[] {first, last})
                f.textProperty().addListener((obs, old, text) -> {
                    if (text.length() > Names.MAX_LENGTH)
                        f.setText(old);
                });
        }

        PassengerInput input() {
            return new PassengerInput(first.getText(), last.getText(), type, seat);
        }
    }

    private final AppContext ctx;
    private final Offer offer;
    private final Flight flight;
    private final CabinClass cabin;
    private final Runnable back;
    private final Consumer<BookingKey> manage;
    private final List<PassengerForm> passengers = new ArrayList<>();
    private final TextField email = new TextField();
    private final TextField phone = new TextField();
    private final HBox steps = new HBox(8);
    private final VBox body = new VBox(16);
    private final Label error = Ui.errorLabel();
    private int current;

    public BookingWizard(AppContext ctx, Offer offer, int adults, int children, Runnable back, Consumer<BookingKey> manage) {
        super(16);
        this.ctx = ctx;
        this.offer = offer;
        this.flight = offer.flight();
        this.cabin = offer.cabin();
        this.back = back;
        this.manage = manage;
        for (int i = 0; i < adults; i++)
            passengers.add(new PassengerForm(PassengerType.ADULT));
        for (int i = 0; i < children; i++)
            passengers.add(new PassengerForm(PassengerType.CHILD));
        email.setPromptText("ornek@eposta.com");
        phone.setPromptText("05xx xxx xx xx");

        final Button cancel = Ui.button(I18n.t("wizard.backToResults"), Feather.ARROW_LEFT, Styles.FLAT);
        cancel.setOnAction(e -> back.run());
        steps.setAlignment(Pos.CENTER_LEFT);
        getChildren().addAll(new HBox(cancel), flightSummary(), steps, body);
        showStep(0);
    }

    /** Üstteki uçuş özeti: güzergâh, tarih, saatler, kabin. */
    private Node flightSummary() {
        final Label route = new Label(Views.route(flight));
        route.getStyleClass().add(Styles.TITLE_4);
        final Label when = Ui.muted(I18n.date(flight.departure().toLocalDate()) + " · " + flight.number() + " · "
                + flight.aircraft().displayName() + " · " + I18n.of(cabin));
        final VBox text = new VBox(2, route, when);
        final HBox timeline = Views.timeline(flight);
        timeline.setPrefWidth(340);
        timeline.setMinWidth(300);
        final HBox box = new HBox(20, text, Ui.spacer(), timeline);
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().add("panel");
        box.setPadding(new Insets(14, 18, 14, 18));
        return box;
    }

    private void showStep(int step) {
        current = step;
        steps.getChildren().clear();
        final String[] keys = {"wizard.step.passengers", "wizard.step.seats", "wizard.step.review", "wizard.step.done"};
        for (int i = 0; i < keys.length; i++) {
            final Label l = new Label((i + 1) + "  " + I18n.t(keys[i]));
            l.getStyleClass().add("step");
            if (i == step)
                l.getStyleClass().add("step-current");
            else if (i < step)
                l.getStyleClass().add("step-done");
            steps.getChildren().add(l);
            if (i < keys.length - 1)
                steps.getChildren().add(new FontIcon(Feather.CHEVRON_RIGHT));
        }
        Ui.showError(error, null);
        body.getChildren().clear();
        switch (step) {
            case 0 -> passengerStep();
            case 1 -> seatStep();
            case 2 -> reviewStep();
            default -> {
            }
        }
    }

    // ---------------------------------------------------------------- 1. yolcular

    private void passengerStep() {
        final GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        int row = 0;
        int adultNo = 0;
        int childNo = 0;
        for (PassengerForm p : passengers) {
            final String label = p.type == PassengerType.ADULT ? I18n.t("wizard.adultN", ++adultNo) : I18n.t("wizard.childN", ++childNo);
            final Label who = new Label(label);
            who.getStyleClass().add("field-label");
            who.setMinWidth(110);
            grid.add(who, 0, row);
            p.first.setPromptText(I18n.t("field.firstName"));
            p.last.setPromptText(I18n.t("field.lastName"));
            p.first.setAccessibleText(label + " " + I18n.t("field.firstName"));
            p.last.setAccessibleText(label + " " + I18n.t("field.lastName"));
            grid.add(p.first, 1, row);
            grid.add(p.last, 2, row);
            GridPane.setHgrow(p.first, Priority.ALWAYS);
            GridPane.setHgrow(p.last, Priority.ALWAYS);
            row++;
        }
        final Label hint = Ui.muted(I18n.t("wizard.nameHint"));
        final VBox people = Ui.card(Ui.section(I18n.t("wizard.passengers")), hint, grid);

        final HBox contact = new HBox(12, grow(Ui.field(I18n.t("field.email"), email)), grow(Ui.field(I18n.t("field.phone"), phone)));
        final VBox contactCard = Ui.card(Ui.section(I18n.t("wizard.contact")), Ui.muted(I18n.t("wizard.contactHint")), contact);

        final Button next = Ui.button(I18n.t("wizard.toSeats"), Feather.ARROW_RIGHT, Styles.ACCENT);
        next.setContentDisplay(javafx.scene.control.ContentDisplay.RIGHT);
        next.setDefaultButton(true);
        next.setOnAction(e -> {
            final String problem = validatePassengers();
            if (problem != null) {
                Ui.showError(error, problem);
                return;
            }
            showStep(1);
        });
        body.getChildren().addAll(people, contactCard, error, footer(null, next));
        if (!passengers.isEmpty())
            javafx.application.Platform.runLater(passengers.get(0).first::requestFocus);
    }

    private static VBox grow(VBox box) {
        HBox.setHgrow(box, Priority.ALWAYS);
        return box;
    }

    /** Arayüzdeki ön kontrol (kullanıcıya hızlı geri bildirim); asıl kontrol serviste tekrar yapılır. */
    private String validatePassengers() {
        for (int i = 0; i < passengers.size(); i++) {
            final PassengerForm p = passengers.get(i);
            if (!Names.valid(p.first.getText()) || !Names.valid(p.last.getText()))
                return I18n.t("error.invalidName", i + 1);
        }
        if (!Validation.email(email.getText()))
            return I18n.t("error.invalidEmail");
        if (Validation.phone(phone.getText()) == null)
            return I18n.t("error.invalidPhone");
        return null;
    }

    // ---------------------------------------------------------------- 2. koltuklar

    private PassengerForm selected;

    private void seatStep() {
        final Set<String> occupied = ctx.airline().flights().occupiedSeats(flight.id());
        // daha önce seçilmiş ama bu arada başkasına satılmış koltuk bırakılır
        for (PassengerForm p : passengers)
            if (p.seat != null && occupied.contains(p.seat))
                p.seat = null;
        final SeatMapView map = new SeatMapView(flight.aircraft(), cabin);
        map.setOccupied(occupied);

        final VBox list = new VBox(6);
        final ToggleGroup group = new ToggleGroup();
        final Label fees = new Label();
        fees.getStyleClass().add(Styles.TEXT_BOLD);
        final Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> {
            list.getChildren().clear();
            final Map<String, String> assigned = new LinkedHashMap<>();
            long seatFees = 0;
            for (int i = 0; i < passengers.size(); i++) {
                final PassengerForm p = passengers.get(i);
                if (p.seat != null) {
                    assigned.put(p.seat, String.valueOf(i + 1));
                    seatFees += flight.aircraft().seat(p.seat).map(Pricing::seatFee).orElse(0L);
                }
                final Label name = new Label((i + 1) + ". " + Names.clean(p.first.getText()) + " " + Names.clean(p.last.getText()));
                name.getStyleClass().add(Styles.TEXT_BOLD);
                final Label seat = p.seat == null ? Ui.muted(I18n.t("wizard.noSeat")) : new Label(p.seat + seatNote(p.seat));
                final VBox text = new VBox(2, name, seat);
                final ToggleButton b = new ToggleButton();
                b.setGraphic(text);
                b.setToggleGroup(group);
                b.getStyleClass().add("passenger-pick");
                b.setMaxWidth(Double.MAX_VALUE);
                b.setAlignment(Pos.CENTER_LEFT);
                b.setSelected(p == selected);
                b.setOnAction(e -> {
                    selected = p;
                    refresh[0].run();
                });
                list.getChildren().add(b);
            }
            map.setAssigned(assigned, selected == null ? null : selected.seat);
            fees.setText(I18n.t("wizard.seatFees", I18n.money(seatFees)));
        };
        if (selected == null || !passengers.contains(selected))
            selected = passengers.get(0);
        map.setOnSeat(seat -> {
            // başka yolcunun koltuğuna tıklanırsa o yolcu seçilir
            for (PassengerForm p : passengers)
                if (seat.code().equals(p.seat) && p != selected) {
                    selected = p;
                    refresh[0].run();
                    return;
                }
            selected.seat = seat.code();
            // koltuğu olmayan sıradaki yolcuya geç
            passengers.stream().filter(p -> p.seat == null).findFirst().ifPresent(p -> selected = p);
            refresh[0].run();
        });
        refresh[0].run();

        final Button auto = Ui.button(I18n.t("wizard.autoSeats"), Feather.ZAP, Styles.FLAT);
        auto.setOnAction(e -> {
            autoAssign(occupied);
            refresh[0].run();
        });
        final VBox side = new VBox(12, Ui.section(I18n.t("wizard.chooseSeats")), Ui.muted(I18n.t("wizard.seatHint")),
                list, auto, fees);
        side.setPrefWidth(300);
        side.setMinWidth(260);

        final javafx.scene.layout.StackPane mapHolder = new javafx.scene.layout.StackPane(map);
        final ScrollPane mapScroll = new ScrollPane(mapHolder);
        mapScroll.setFitToWidth(true);
        mapScroll.setPrefViewportHeight(470);
        mapScroll.getStyleClass().add("transparent-scroll");
        final VBox mapBox = new VBox(10, SeatMapView.legend(false), mapScroll);
        HBox.setHgrow(mapBox, Priority.ALWAYS);
        final HBox content = new HBox(24, side, mapBox);
        content.getStyleClass().add("panel");
        content.setPadding(new Insets(18));

        final Button backButton = Ui.button(I18n.t("action.back"), Feather.ARROW_LEFT);
        backButton.setOnAction(e -> showStep(0));
        final Button next = Ui.button(I18n.t("wizard.toReview"), Feather.ARROW_RIGHT, Styles.ACCENT);
        next.setContentDisplay(javafx.scene.control.ContentDisplay.RIGHT);
        next.setOnAction(e -> {
            if (passengers.stream().anyMatch(p -> p.seat == null)) {
                Ui.showError(error, I18n.t("wizard.seatsMissing"));
                return;
            }
            showStep(2);
        });
        body.getChildren().addAll(content, error, footer(backButton, next));
    }

    private String seatNote(String code) {
        return flight.aircraft().seat(code).filter(Seat::extraLegroom)
                .map(s -> " · " + I18n.t("seat.extraLegroom") + " +" + I18n.money(Pricing.EXTRA_LEGROOM_FEE)).orElse("");
    }

    /**
     * Otomatik seçim: önden arkaya, geniş bacak mesafeli (ücretli) olmayan koltuklardan, bütün grubun aynı sırada
     * yan yana oturabileceği ilk yer; yoksa boş ilk koltuklar.
     */
    private void autoAssign(Set<String> occupied) {
        final List<Seat> free = flight.aircraft().seats(cabin).stream()
                .filter(s -> !occupied.contains(s.code()) && !s.extraLegroom()).toList();
        final int n = passengers.size();
        List<Seat> chosen = null;
        final int lastRow = flight.aircraft().lastRow();
        for (int row = 1; row <= lastRow && chosen == null; row++) {
            final int r = row;
            final List<Seat> inRow = free.stream().filter(s -> s.row() == r).toList();
            // aynı taraftaki (koridorun aynı yanı) bitişik koltuklar tercih edilir
            for (List<Character> side : sides()) {
                final List<Seat> sideSeats = inRow.stream().filter(s -> side.contains(s.letter())).toList();
                if (sideSeats.size() >= n && n <= side.size()) {
                    chosen = sideSeats.subList(0, n);
                    break;
                }
            }
            if (chosen == null && inRow.size() >= n)
                chosen = inRow.subList(0, n);
        }
        if (chosen == null && free.size() >= n)
            chosen = free.subList(0, n);
        if (chosen == null)
            return;
        for (int i = 0; i < n; i++)
            passengers.get(i).seat = chosen.get(i).code();
        selected = passengers.get(0);
    }

    private List<List<Character>> sides() {
        return cabin == CabinClass.BUSINESS ? List.of(List.of('A', 'C'), List.of('D', 'F'))
                : List.of(List.of('A', 'B', 'C'), List.of('D', 'E', 'F'));
    }

    // ---------------------------------------------------------------- 3. özet

    private void reviewStep() {
        final List<PassengerInput> inputs = passengers.stream().map(PassengerForm::input).toList();
        final Quote quote;
        try {
            quote = ctx.airline().bookings().quote(flight.id(), cabin, inputs);
        } catch (RuntimeException e) {
            Ui.showError(error, Ui.message(e));
            body.getChildren().add(error);
            return;
        }
        final GridPane table = new GridPane();
        table.getStyleClass().add("summary-table");
        table.setHgap(24);
        table.setVgap(8);
        table.addRow(0, head("wizard.col.passenger"), head("wizard.col.type"), head("wizard.col.seat"), head("wizard.col.fare"));
        for (int i = 0; i < passengers.size(); i++) {
            final PassengerForm p = passengers.get(i);
            final long seatFee = flight.aircraft().seat(p.seat).map(Pricing::seatFee).orElse(0L);
            table.addRow(i + 1, new Label(Names.ticketName(p.first.getText(), p.last.getText())), new Label(I18n.of(p.type)),
                    new Label(p.seat + (seatFee > 0 ? " (+" + I18n.money(seatFee) + ")" : "")),
                    new Label(I18n.money(p.type == PassengerType.CHILD ? quote.childFare() : quote.adultFare())));
        }
        final VBox passengersCard = Ui.card(Ui.section(I18n.t("wizard.passengers")), table,
                Ui.muted(I18n.t("wizard.contactLine", email.getText().strip(), phone.getText().strip())));

        final VBox price = new VBox(8,
                Ui.section(I18n.t("wizard.priceTitle")),
                priceRow(I18n.t("wizard.fares", quote.adults() + quote.children()), quote.fares()),
                priceRow(I18n.t("wizard.taxes"), quote.taxes()),
                priceRow(I18n.t("wizard.seatFeesShort"), quote.seatFees()));
        final Label totalLabel = new Label(I18n.t("wizard.total"));
        totalLabel.getStyleClass().add(Styles.TITLE_4);
        final Label total = new Label(I18n.money(quote.total()));
        total.getStyleClass().add("offer-price");
        final HBox totalRow = new HBox(totalLabel, Ui.spacer(), total);
        totalRow.setAlignment(Pos.CENTER_LEFT);
        price.getChildren().addAll(new javafx.scene.control.Separator(), totalRow);
        price.getStyleClass().add("panel");
        price.setPadding(new Insets(16));
        price.setPrefWidth(340);
        price.setMinWidth(300);

        final Label rules = Ui.muted(I18n.t(cabin == CabinClass.BUSINESS ? "wizard.rulesBusiness" : "wizard.rulesEconomy",
                I18n.money(Pricing.TAX_PER_PASSENGER)));
        final VBox rulesCard = Ui.card(Ui.section(I18n.t("wizard.rulesTitle")), rules,
                Ui.muted(I18n.t("wizard.refundNow", RefundPolicy.farePercent(cabin,
                        java.time.Duration.between(ctx.airline().now(), flight.departure()).toMinutes()))));
        final CheckBox accept = new CheckBox(I18n.t("wizard.accept"));
        accept.setWrapText(true);
        final Label demo = Ui.muted(I18n.t("wizard.noPayment"));

        final VBox left = new VBox(16, passengersCard, rulesCard);
        HBox.setHgrow(left, Priority.ALWAYS);
        final HBox content = new HBox(16, left, price);

        final Button backButton = Ui.button(I18n.t("action.back"), Feather.ARROW_LEFT);
        backButton.setOnAction(e -> showStep(1));
        final Button confirm = Ui.button(I18n.t("wizard.confirm", I18n.money(quote.total())), Feather.CHECK, Styles.SUCCESS);
        confirm.disableProperty().bind(accept.selectedProperty().not());
        confirm.setDefaultButton(true);
        confirm.setOnAction(e -> {
            Ui.showError(error, null);
            final BookingRequest request = new BookingRequest(flight.id(), cabin, inputs, email.getText(), phone.getText());
            Ui.background(body, () -> ctx.airline().bookings().book(request, quote.total()), this::done, ex -> {
                if (ex instanceof AirlineException a && (a.code().equals("seatTaken") || a.code().equals("seatJustTaken"))) {
                    Ui.error(ctx.window(), ex);
                    showStep(1);
                } else if (ex instanceof AirlineException a && a.code().equals("priceChanged")) {
                    Ui.error(ctx.window(), ex);
                    showStep(2);
                } else {
                    Ui.showError(error, Ui.message(ex));
                }
            });
        });
        body.getChildren().addAll(content, accept, demo, error, footer(backButton, confirm));
    }

    private static Label head(String key) {
        final Label l = new Label(I18n.t(key));
        l.getStyleClass().addAll(Styles.TEXT_MUTED, "field-label");
        return l;
    }

    private static HBox priceRow(String label, long amount) {
        final HBox row = new HBox(new Label(label), Ui.spacer(), new Label(I18n.money(amount)));
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    // ---------------------------------------------------------------- 4. onay

    private void done(BookingDetail detail) {
        showStep(3);
        final FontIcon ok = new FontIcon(Feather.CHECK_CIRCLE);
        ok.getStyleClass().add("success-icon");
        final Label title = new Label(I18n.t("wizard.doneTitle"));
        title.getStyleClass().add(Styles.TITLE_2);
        final Label text = Ui.muted(I18n.t("wizard.doneText", detail.booking().email()));
        text.setMaxWidth(520);
        text.setAlignment(Pos.CENTER);
        text.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        final Label codeLabel = Ui.muted(I18n.t("booking.code"));
        final Label code = new Label(detail.booking().pnr());
        code.getStyleClass().add("pnr-big");
        final Button copy = Ui.iconButton(Feather.COPY, I18n.t("action.copy"));
        copy.setOnAction(e -> Ui.copy(detail.booking().pnr()));
        final HBox codeRow = new HBox(8, code, copy);
        codeRow.setAlignment(Pos.CENTER);

        final Button ticket = Ui.button(I18n.t("booking.eTicket"), Feather.DOWNLOAD);
        ticket.setOnAction(e -> PdfSaver.save(ctx, ticket, Documents.eTicketFileName(detail),
                file -> Documents.eTicket(detail, ctx.airline().now(), file)));
        final String surname = detail.tickets().get(0).lastName();
        final Button open = Ui.button(I18n.t("wizard.manage"), Feather.BRIEFCASE, Styles.ACCENT);
        open.setOnAction(e -> manage.accept(new BookingKey(detail.booking().pnr(), surname)));
        final Button again = Ui.button(I18n.t("wizard.newSearch"), Feather.SEARCH, Styles.FLAT);
        again.setOnAction(e -> back.run());
        final HBox actions = new HBox(10, ticket, open, again);
        actions.setAlignment(Pos.CENTER);

        final Label checkIn = Ui.muted(I18n.t("wizard.checkInInfo",
                I18n.time(com.mrcdprm.airline.service.BookingService.checkInOpens(detail.flight()))));
        final VBox box = new VBox(12, ok, title, text, codeLabel, codeRow, checkIn, actions);
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("panel");
        box.setPadding(new Insets(36));
        body.getChildren().add(box);
    }

    private HBox footer(Button left, Button right) {
        final HBox box = new HBox(10);
        if (left != null)
            box.getChildren().add(left);
        box.getChildren().addAll(Ui.spacer(), right);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }
}
