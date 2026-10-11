package com.mrcdprm.airline.ui;

import atlantafx.base.theme.Styles;
import com.mrcdprm.airline.data.Records.Booking;
import com.mrcdprm.airline.data.Records.Flight;
import com.mrcdprm.airline.data.Records.Ticket;
import com.mrcdprm.airline.service.BookingService.BookingDetail;
import java.time.LocalDateTime;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Rezervasyon ayrıntısı: kod ve durum, uçuş bilgisi (kapı, biniş saati), yolcular ve ödeme.
 * Yolcu ekranında ve personel panelinde aynı görünüm kullanılır.
 */
public final class BookingCard extends VBox {

    /** @param onChangeSeat null değilse yolcu satırlarında "Koltuğu değiştir" düğmesi görünür */
    public BookingCard(BookingDetail d, LocalDateTime now, Consumer<Ticket> onChangeSeat) {
        super(14);
        final Booking b = d.booking();
        final Flight f = d.flight();

        // ---- başlık
        final Label codeLabel = Ui.muted(I18n.t("booking.code"));
        final Label code = new Label(b.pnr());
        code.getStyleClass().add("pnr");
        final Button copy = Ui.iconButton(Feather.COPY, I18n.t("action.copy"));
        copy.setOnAction(e -> Ui.copy(b.pnr()));
        final HBox codeRow = new HBox(6, code, copy, Views.bookingStatus(b));
        codeRow.setAlignment(Pos.CENTER_LEFT);
        final VBox codeBox = new VBox(0, codeLabel, codeRow);
        final Label created = Ui.muted(I18n.t("booking.createdAt", I18n.time(b.createdAt())));
        final HBox header = new HBox(codeBox, Ui.spacer(), created);
        header.setAlignment(Pos.BOTTOM_LEFT);

        // ---- uçuş
        final Label route = new Label(Views.route(f));
        route.getStyleClass().add(Styles.TITLE_4);
        final Label when = Ui.muted(I18n.date(f.departure().toLocalDate()) + " · " + f.number() + " · "
                + f.aircraft().displayName() + " · " + I18n.of(b.cabin()));
        final HBox titleRow = new HBox(10, new VBox(2, route, when), Ui.spacer(), Views.flightStatus(f, now));
        titleRow.setAlignment(Pos.TOP_LEFT);
        final HBox timeline = Views.timeline(f);
        timeline.setMaxWidth(460);
        final HBox facts = new HBox(28,
                fact(Feather.LOG_IN, I18n.t("booking.gate"), f.gate()),
                fact(Feather.CLOCK, I18n.t("booking.boarding"), I18n.clock(f.boardingTime())),
                fact(Feather.MAP_PIN, I18n.t("booking.from"), f.origin().airportName(I18n.locale())),
                fact(Feather.FLAG, I18n.t("booking.to"), f.destination().airportName(I18n.locale())));
        final VBox flightCard = Ui.card(titleRow, timeline, facts);

        // ---- yolcular
        final GridPane grid = new GridPane();
        grid.setHgap(22);
        grid.setVgap(10);
        grid.addRow(0, head("wizard.col.passenger"), head("wizard.col.type"), head("wizard.col.seat"),
                head("booking.ticketNo"), head("booking.checkIn"));
        int row = 1;
        for (Ticket t : d.tickets()) {
            final Label name = new Label(t.ticketName());
            name.getStyleClass().add(Styles.TEXT_BOLD);
            final Label seat = new Label(t.seat());
            seat.getStyleClass().add("seat-chip");
            final Label checkIn = t.checkedIn()
                    ? Views.badge(I18n.t("booking.checkedIn", t.boardingNo()), "badge-active")
                    : Views.badge(I18n.t("booking.notCheckedIn"), "badge-muted");
            grid.addRow(row, name, new Label(I18n.of(t.type())), seat, new Label(formatTicket(t.ticketNo())), checkIn);
            if (onChangeSeat != null && !d.cancelled() && !t.checkedIn()) {
                final Button change = Ui.button(I18n.t("booking.changeSeat"), Feather.GRID, Styles.FLAT, Styles.SMALL);
                change.setOnAction(e -> onChangeSeat.accept(t));
                grid.add(change, 5, row);
            }
            row++;
        }
        final VBox passengers = Ui.card(Ui.section(I18n.t("wizard.passengers")), grid);

        // ---- ödeme
        final VBox payment = new VBox(6, Ui.section(I18n.t("booking.payment")),
                line(I18n.t("wizard.fares", d.tickets().size()), I18n.money(d.fares())),
                line(I18n.t("wizard.taxes"), I18n.money(d.taxes())),
                line(I18n.t("wizard.seatFeesShort"), I18n.money(d.seatFees())),
                new javafx.scene.control.Separator(),
                bold(line(I18n.t("booking.paid"), I18n.money(b.total()))));
        if (d.cancelled())
            payment.getChildren().add(bold(line(I18n.t("booking.refunded"), I18n.money(b.refund()))));
        payment.getStyleClass().add("panel");
        payment.setPadding(new Insets(16));
        payment.setMinWidth(260);
        payment.setPrefWidth(300);
        final HBox lower = new HBox(14, passengers, payment);
        HBox.setHgrow(passengers, Priority.ALWAYS);

        getChildren().addAll(header, flightCard, lower);
    }

    /** "9901234567890" -> "990-1234567890" */
    static String formatTicket(String no) {
        return no.length() == 13 ? no.substring(0, 3) + "-" + no.substring(3) : no;
    }

    private static VBox fact(Feather icon, String label, String value) {
        final Label l = Ui.muted(label);
        l.setGraphic(new FontIcon(icon));
        final Label v = new Label(value);
        v.getStyleClass().add(Styles.TEXT_BOLD);
        return new VBox(2, l, v);
    }

    private static Label head(String key) {
        final Label l = new Label(I18n.t(key));
        l.getStyleClass().addAll(Styles.TEXT_MUTED, "field-label");
        return l;
    }

    private static HBox line(String label, String value) {
        final HBox row = new HBox(new Label(label), Ui.spacer(), new Label(value));
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static HBox bold(HBox row) {
        row.getChildren().forEach(n -> n.getStyleClass().add(Styles.TEXT_BOLD));
        return row;
    }
}
