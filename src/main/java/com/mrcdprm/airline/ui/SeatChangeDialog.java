package com.mrcdprm.airline.ui;

import com.mrcdprm.airline.core.Pricing;
import com.mrcdprm.airline.data.Records.Ticket;
import com.mrcdprm.airline.service.BookingService.BookingDetail;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;

/** Bir yolcunun koltuğunu değiştirme penceresi: harita üzerinde yeni koltuk seçilir, seçilen kod döner. */
public final class SeatChangeDialog extends Dialog<String> {

    private String chosen;

    public SeatChangeDialog(AppContext ctx, BookingDetail d, Ticket ticket) {
        initOwner(ctx.window());
        setTitle(I18n.t("booking.changeSeat"));
        Theme.style(getDialogPane());
        chosen = ticket.seat();

        final Set<String> occupied = new HashSet<>(ctx.airline().flights().occupiedSeats(d.flight().id()));
        occupied.remove(ticket.seat());
        final SeatMapView map = new SeatMapView(d.flight().aircraft(), d.booking().cabin());
        map.setOccupied(occupied);
        map.setAssigned(Map.of(chosen, "✓"), chosen);
        final Label info = new Label();
        final Runnable update = () -> {
            final long fee = d.flight().aircraft().seat(chosen).map(Pricing::seatFee).orElse(0L);
            final long extra = Math.max(0, fee - ticket.seatFee());
            info.setText(I18n.t("seatChange.selected", ticket.ticketName(), chosen)
                    + (extra > 0 ? "  ·  " + I18n.t("seatChange.fee", I18n.money(extra)) : ""));
        };
        map.setOnSeat(seat -> {
            chosen = seat.code();
            map.setAssigned(Map.of(chosen, "✓"), chosen);
            update.run();
        });
        update.run();

        final ScrollPane scroll = new ScrollPane(new javafx.scene.layout.StackPane(map));
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(440);
        scroll.setPrefViewportWidth(420);
        final VBox content = new VBox(10, info, Ui.muted(I18n.t("seatChange.hint")), SeatMapView.legend(false), scroll);
        content.setPadding(new Insets(6, 4, 0, 4));
        getDialogPane().setContent(content);

        final ButtonType ok = new ButtonType(I18n.t("seatChange.save"), ButtonBar.ButtonData.OK_DONE);
        final ButtonType cancel = new ButtonType(I18n.t("action.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        getDialogPane().getButtonTypes().addAll(ok, cancel);
        setResultConverter(b -> b == ok && !chosen.equals(ticket.seat()) ? chosen : null);
    }
}
