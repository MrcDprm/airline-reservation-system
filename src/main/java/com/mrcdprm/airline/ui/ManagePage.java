package com.mrcdprm.airline.ui;

import atlantafx.base.theme.Styles;
import com.mrcdprm.airline.core.FlightStatus;
import com.mrcdprm.airline.data.Records.Ticket;
import com.mrcdprm.airline.pdf.Documents;
import com.mrcdprm.airline.service.BookingService;
import com.mrcdprm.airline.service.BookingService.BookingDetail;
import com.mrcdprm.airline.service.BookingService.BookingKey;
import com.mrcdprm.airline.service.BookingService.CheckInState;
import com.mrcdprm.airline.service.BookingService.RefundQuote;
import com.mrcdprm.airline.service.SampleData;
import java.time.LocalDateTime;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;

/**
 * Rezervasyonum: rezervasyon kodu ve soyad ile bulunur. Koltuk değiştirme, online check-in,
 * biniş kartı ve e-bilet PDF'i, iade bilgisiyle iptal buradan yapılır.
 */
public final class ManagePage extends VBox {

    private final AppContext ctx;
    private final TextField pnr = new TextField();
    private final TextField surname = new TextField();
    private final Label error = Ui.errorLabel();
    private final VBox result = new VBox(14);
    private BookingKey key;

    public ManagePage(AppContext ctx, BookingKey initial) {
        super(18);
        this.ctx = ctx;

        final Label title = new Label(I18n.t("manage.title"));
        title.getStyleClass().add("hero-title");
        final Label subtitle = new Label(I18n.t("manage.subtitle"));
        subtitle.getStyleClass().add("hero-subtitle");

        pnr.setPromptText("ABC234");
        pnr.textProperty().addListener((obs, old, text) -> {
            final String up = text.toUpperCase(java.util.Locale.ROOT);
            if (up.length() > 6)
                pnr.setText(old);
            else if (!up.equals(text))
                pnr.setText(up);
        });
        surname.setPromptText(I18n.t("field.lastName"));
        final Button find = Ui.button(I18n.t("manage.find"), Feather.SEARCH, Styles.ACCENT);
        find.setDefaultButton(true);
        find.setOnAction(e -> lookup(new BookingKey(pnr.getText(), surname.getText())));
        final VBox pnrField = Ui.field(I18n.t("booking.code"), pnr);
        final VBox nameField = Ui.field(I18n.t("field.lastName"), surname);
        HBox.setHgrow(pnrField, Priority.ALWAYS);
        HBox.setHgrow(nameField, Priority.ALWAYS);
        final VBox findBox = new VBox(find);
        findBox.setAlignment(Pos.BOTTOM_LEFT);
        final HBox form = new HBox(12, pnrField, nameField, findBox);
        final VBox card = new VBox(12, form, error);
        if (ctx.settings().sampleData())
            card.getChildren().add(Ui.muted(I18n.t("manage.demoHint", SampleData.DEMO_PNR, SampleData.DEMO_SURNAME)));
        card.getStyleClass().add("search-card");
        card.setPadding(new Insets(20, 22, 18, 22));

        getChildren().addAll(new VBox(4, title, subtitle), card, result);
        if (initial != null) {
            pnr.setText(initial.pnr());
            surname.setText(initial.lastName());
            lookup(initial);
        }
    }

    private void lookup(BookingKey k) {
        Ui.showError(error, null);
        try {
            final BookingDetail detail = ctx.airline().bookings().find(k);
            key = k;
            show(detail);
        } catch (RuntimeException e) {
            result.getChildren().clear();
            Ui.showError(error, Ui.message(e));
        }
    }

    private void reload() {
        if (key != null)
            lookup(key);
    }

    private void show(BookingDetail d) {
        result.getChildren().clear();
        final LocalDateTime now = ctx.airline().now();
        final boolean active = !d.cancelled() && d.flight().status() != FlightStatus.CANCELLED && !d.flight().departed(now);
        final BookingService service = ctx.airline().bookings();
        final CheckInState state = service.checkInState(d.flight());

        final Button checkIn = Ui.button(I18n.t("manage.checkIn"), Feather.CHECK_SQUARE, Styles.ACCENT);
        checkIn.setVisible(active && !d.allCheckedIn());
        checkIn.managedProperty().bind(checkIn.visibleProperty());
        checkIn.setDisable(state != CheckInState.OPEN);
        checkIn.setOnAction(e -> {
            try {
                final BookingDetail checked = service.checkIn(key);
                show(checked);
                Ui.info(ctx.window(), I18n.t("manage.checkInDone"));
            } catch (RuntimeException ex) {
                Ui.error(ctx.window(), ex);
            }
        });
        final Button boarding = Ui.button(I18n.t("manage.boardingPass"), Feather.SMARTPHONE, Styles.ACCENT);
        boarding.setVisible(d.tickets().stream().anyMatch(Ticket::checkedIn) && !d.cancelled());
        boarding.managedProperty().bind(boarding.visibleProperty());
        boarding.setOnAction(e -> PdfSaver.save(ctx, boarding, Documents.boardingFileName(d),
                file -> Documents.boardingPasses(d, file)));
        final Button ticket = Ui.button(I18n.t("booking.eTicket"), Feather.DOWNLOAD);
        ticket.setOnAction(e -> PdfSaver.save(ctx, ticket, Documents.eTicketFileName(d),
                file -> Documents.eTicket(d, ctx.airline().now(), file)));
        final Button cancel = Ui.button(I18n.t("manage.cancel"), Feather.X_CIRCLE, Styles.DANGER, Styles.FLAT);
        cancel.setVisible(active);
        cancel.managedProperty().bind(cancel.visibleProperty());
        cancel.setOnAction(e -> cancel());

        final HBox actions = new HBox(10, checkIn, boarding, ticket, Ui.spacer(), cancel);
        actions.setAlignment(Pos.CENTER_LEFT);

        final Label info = Ui.muted(checkInText(d, state, active));
        result.getChildren().addAll(new BookingCard(d, now, active ? this::changeSeat : null), info, actions);
    }

    private String checkInText(BookingDetail d, CheckInState state, boolean active) {
        if (d.cancelled())
            return I18n.t("manage.cancelledInfo");
        if (!active)
            return I18n.t("manage.closedInfo");
        if (d.allCheckedIn())
            return I18n.t("manage.allCheckedIn", I18n.clock(d.flight().boardingTime()), d.flight().gate());
        return switch (state) {
            case NOT_OPEN -> I18n.t("manage.checkInOpensAt", I18n.time(BookingService.checkInOpens(d.flight())));
            case OPEN -> I18n.t("manage.checkInOpen", I18n.clock(d.flight().departure().minusMinutes(60)));
            case CLOSED -> I18n.t("manage.checkInClosed");
        };
    }

    private void changeSeat(Ticket t) {
        try {
            final BookingDetail d = ctx.airline().bookings().find(key);
            new SeatChangeDialog(ctx, d, t).showAndWait().ifPresent(seat -> {
                try {
                    final long fee = ctx.airline().bookings().changeSeat(key, t.id(), seat);
                    reload();
                    Ui.info(ctx.window(), fee > 0 ? I18n.t("manage.seatChangedFee", seat, I18n.money(fee))
                            : I18n.t("manage.seatChanged", seat));
                } catch (RuntimeException ex) {
                    Ui.error(ctx.window(), ex);
                    reload();
                }
            });
        } catch (RuntimeException ex) {
            Ui.error(ctx.window(), ex);
        }
    }

    private void cancel() {
        try {
            final RefundQuote q = ctx.airline().bookings().refundQuote(key);
            final String text = I18n.t("manage.cancelConfirm", I18n.money(q.paid()), q.farePercent(), I18n.money(q.refund()));
            if (!Ui.confirm(ctx.window(), text, I18n.t("manage.cancelYes")))
                return;
            final RefundQuote done = ctx.airline().bookings().cancel(key);
            reload();
            Ui.info(ctx.window(), I18n.t("manage.cancelled", I18n.money(done.refund())));
        } catch (RuntimeException ex) {
            Ui.error(ctx.window(), ex);
            reload();
        }
    }
}
