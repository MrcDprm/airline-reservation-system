package com.mrcdprm.airline.ui;

import com.mrcdprm.airline.core.BookingStatus;
import com.mrcdprm.airline.core.FlightStatus;
import com.mrcdprm.airline.data.Records.Booking;
import com.mrcdprm.airline.data.Records.Flight;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

/** Uçuş ve rezervasyon için ortak görünümler: durum rozeti, saat-güzergâh bloğu. */
public final class Views {

    private Views() {
    }

    /** Uçuş durumu: zamanında, rötarlı (+45 dk), iptal, kalktı. Kalkış saati geçmişse "kalktı" sayılır. */
    public static Label flightStatus(Flight f, LocalDateTime now) {
        final String text;
        final String style;
        if (f.status() == FlightStatus.CANCELLED) {
            text = I18n.t("status.cancelled");
            style = "badge-danger";
        } else if (f.departed(now)) {
            text = I18n.t("status.departed");
            style = "badge-muted";
        } else if (f.status() == FlightStatus.DELAYED) {
            text = I18n.t("status.delayed", I18n.duration((int) f.delayMinutes()));
            style = "badge-warning";
        } else {
            text = I18n.t("status.onTime");
            style = "badge-active";
        }
        return badge(text, style);
    }

    public static Label bookingStatus(Booking b) {
        if (b.status() == BookingStatus.CANCELLED)
            return badge(I18n.t("booking.cancelledBy." + b.cancelledBy()), "badge-danger");
        return badge(I18n.t("booking.confirmed"), "badge-active");
    }

    public static Label badge(String text, String style) {
        final Label label = new Label(text);
        label.getStyleClass().addAll("badge", style);
        label.setMinWidth(Label.USE_PREF_SIZE);
        return label;
    }

    /**
     * Arama sonucu ve rezervasyon kartındaki blok:
     * 07:00 ——✈—— 07:55
     * IST   55 dk   ESB
     */
    public static HBox timeline(Flight f) {
        final VBox from = endpoint(I18n.clock(f.departure()), f.origin().code(), Pos.CENTER_LEFT);
        final LocalDateTime arrival = f.arrival();
        final long nextDay = ChronoUnit.DAYS.between(f.departure().toLocalDate(), arrival.toLocalDate());
        final VBox to = endpoint(I18n.clock(arrival) + (nextDay > 0 ? " +" + nextDay : ""), f.destination().code(), Pos.CENTER_RIGHT);

        final FontIcon plane = new FontIcon(Feather.SEND);
        plane.getStyleClass().add("timeline-plane");
        final Label duration = Ui.muted(I18n.duration(f.minutes()));
        duration.getStyleClass().add("timeline-duration");
        final HBox line = new HBox(6, Ui.line(), plane, Ui.line());
        line.setAlignment(Pos.CENTER);
        final VBox middle = new VBox(2, line, duration);
        middle.setAlignment(Pos.CENTER);
        middle.setMinWidth(120);
        HBox.setHgrow(middle, javafx.scene.layout.Priority.ALWAYS);

        final HBox box = new HBox(14, from, middle, to);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    private static VBox endpoint(String time, String code, Pos alignment) {
        final Label t = new Label(time);
        t.getStyleClass().add("timeline-time");
        final Label c = Ui.muted(code);
        c.getStyleClass().add("timeline-code");
        final VBox box = new VBox(0, t, c);
        box.setAlignment(alignment);
        box.setMinWidth(64);
        return box;
    }

    /** "İstanbul (IST) → Ankara (ESB)" */
    public static String route(Flight f) {
        return I18n.airport(f.origin()) + "  →  " + I18n.airport(f.destination());
    }
}
