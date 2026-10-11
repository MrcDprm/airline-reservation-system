package com.mrcdprm.airline.ui;

import com.mrcdprm.airline.core.AircraftType;
import com.mrcdprm.airline.core.CabinClass;
import com.mrcdprm.airline.core.Pricing;
import com.mrcdprm.airline.core.Seat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Görsel koltuk haritası. Uçak önden arkaya çizilir; her sıra "sıra no | sol grup | koridor | sağ grup".
 * Business koltukları (2-2) ekonomi koltuklarından (3-3) geniştir, iki grup da aynı genişlikte kalır.
 * Dolu koltuklar ve seçilen kabin dışındaki koltuklar tıklanamaz.
 */
public final class SeatMapView extends VBox {

    private static final double ECONOMY_SEAT = 32;
    private static final double GAP = 4;
    private static final double GROUP = 3 * ECONOMY_SEAT + 2 * GAP;
    private static final double BUSINESS_SEAT = (GROUP - GAP) / 2;

    private final AircraftType aircraft;
    private final CabinClass cabin;
    private final Map<String, Button> buttons = new HashMap<>();
    private Set<String> occupied = Set.of();
    private Set<String> highlighted = Set.of();
    private Map<String, String> assigned = Map.of();
    private String current;
    private Consumer<Seat> onSeat = s -> {
    };

    /** @param cabin seçilebilir kabin; null ise harita sadece gösterim içindir (personel yolcu listesi) */
    public SeatMapView(AircraftType aircraft, CabinClass cabin) {
        super(GAP);
        this.aircraft = aircraft;
        this.cabin = cabin;
        getStyleClass().add("seat-map");
        setPadding(new Insets(18, 22, 22, 22));
        setAlignment(Pos.TOP_CENTER);
        setMaxWidth(Region.USE_PREF_SIZE);

        final Label front = Ui.muted(I18n.t("seat.front"));
        front.getStyleClass().add("seat-front");
        getChildren().add(front);
        getChildren().add(letterRow(AircraftType.letters(CabinClass.BUSINESS), BUSINESS_SEAT));
        int lastRow = 0;
        for (Seat seat : aircraft.seats()) {
            if (seat.row() == lastRow)
                continue;
            lastRow = seat.row();
            if (seat.row() == aircraft.businessRows() + 1) {
                final Label economy = Ui.muted(I18n.t("seat.economyCabin"));
                economy.getStyleClass().add("seat-cabin-label");
                getChildren().addAll(economy, letterRow(AircraftType.letters(CabinClass.ECONOMY), ECONOMY_SEAT));
            }
            getChildren().add(row(seat.row()));
        }
        refresh();
    }

    private HBox letterRow(List<Character> letters, double width) {
        final HBox left = new HBox(GAP);
        final HBox right = new HBox(GAP);
        final int half = letters.size() / 2;
        for (int i = 0; i < letters.size(); i++) {
            final Label l = new Label(String.valueOf(letters.get(i)));
            l.getStyleClass().add("seat-letter");
            l.setPrefWidth(width);
            l.setAlignment(Pos.CENTER);
            (i < half ? left : right).getChildren().add(l);
        }
        return rowBox(new Label(), left, right);
    }

    private HBox row(int rowNo) {
        final List<Seat> seats = aircraft.seats().stream().filter(s -> s.row() == rowNo).toList();
        final HBox left = new HBox(GAP);
        final HBox right = new HBox(GAP);
        final int half = seats.size() / 2;
        for (int i = 0; i < seats.size(); i++) {
            final Seat seat = seats.get(i);
            final Button b = new Button();
            b.getStyleClass().add("seat");
            if (seat.cabin() == CabinClass.BUSINESS)
                b.getStyleClass().add("seat-business");
            if (seat.extraLegroom())
                b.getStyleClass().add("seat-extra");
            final double w = seat.cabin() == CabinClass.BUSINESS ? BUSINESS_SEAT : ECONOMY_SEAT;
            b.setPrefSize(w, seat.cabin() == CabinClass.BUSINESS ? 38 : ECONOMY_SEAT);
            b.setMinSize(w, ECONOMY_SEAT);
            b.setFocusTraversable(cabin != null);
            b.setOnAction(e -> onSeat.accept(seat));
            buttons.put(seat.code(), b);
            (i < half ? left : right).getChildren().add(b);
        }
        final Label number = Ui.muted(String.valueOf(rowNo));
        final boolean exit = seats.stream().anyMatch(Seat::extraLegroom);
        if (exit) {
            number.getStyleClass().add("seat-exit-row");
            number.setTooltip(new Tooltip(I18n.t("seat.exitRow")));
        }
        return rowBox(number, left, right);
    }

    private HBox rowBox(Label number, HBox left, HBox right) {
        number.setPrefWidth(26);
        number.setAlignment(Pos.CENTER);
        left.setPrefWidth(GROUP);
        right.setPrefWidth(GROUP);
        left.setAlignment(Pos.CENTER_LEFT);
        right.setAlignment(Pos.CENTER_RIGHT);
        final Region aisle = new Region();
        aisle.setPrefWidth(22);
        final Region balance = new Region();
        balance.setPrefWidth(26);
        final HBox row = new HBox(0, number, left, aisle, right, balance);
        row.setAlignment(Pos.CENTER);
        return row;
    }

    /** Koltuk tıklanınca çağrılır (sadece seçilebilir koltuklar için). */
    public void setOnSeat(Consumer<Seat> handler) {
        this.onSeat = handler;
    }

    /** Dolu koltuklar (bu rezervasyonun kendi koltukları hariç). */
    public void setOccupied(Set<String> seats) {
        this.occupied = Set.copyOf(seats);
        refresh();
    }

    /** Personel haritasında check-in yapılmış koltuklar. */
    public void setHighlighted(Set<String> seats) {
        this.highlighted = Set.copyOf(seats);
        refresh();
    }

    /** Bu rezervasyonda yolculara atanmış koltuklar: koltuk kodu -> üstünde yazacak etiket (1, 2...). */
    public void setAssigned(Map<String, String> seats, String currentSeat) {
        this.assigned = Map.copyOf(seats);
        this.current = currentSeat;
        refresh();
    }

    private void refresh() {
        for (Seat seat : aircraft.seats()) {
            final Button b = buttons.get(seat.code());
            final boolean mine = assigned.containsKey(seat.code());
            final boolean taken = occupied.contains(seat.code()) && !mine;
            final boolean selectable = cabin != null && seat.cabin() == cabin && !taken;
            b.getStyleClass().removeAll("seat-taken", "seat-mine", "seat-current", "seat-other", "seat-checked");
            if (taken)
                b.getStyleClass().add("seat-taken");
            if (mine)
                b.getStyleClass().add("seat-mine");
            if (seat.code().equals(current))
                b.getStyleClass().add("seat-current");
            if (cabin != null && seat.cabin() != cabin)
                b.getStyleClass().add("seat-other");
            if (highlighted.contains(seat.code()))
                b.getStyleClass().add("seat-checked");
            b.setText(mine ? assigned.get(seat.code()) : "");
            b.setDisable(cabin != null && !selectable);
            b.setMouseTransparent(cabin == null);
            b.setTooltip(new Tooltip(describe(seat, taken)));
            b.setAccessibleText(describe(seat, taken));
        }
    }

    private String describe(Seat seat, boolean taken) {
        final StringBuilder sb = new StringBuilder(seat.code());
        sb.append(" · ").append(I18n.t(seat.window() ? "seat.window" : seat.aisle() ? "seat.aisle" : "seat.middle"));
        if (seat.extraLegroom())
            sb.append(" · ").append(I18n.t("seat.extraLegroom")).append(" (").append(I18n.money(Pricing.EXTRA_LEGROOM_FEE)).append(")");
        if (taken)
            sb.append(" · ").append(I18n.t("seat.taken"));
        return sb.toString();
    }

    /** Harita altındaki açıklama: boş, dolu, seçili, geniş bacak mesafesi. */
    public static HBox legend(boolean staff) {
        final HBox box = new HBox(16);
        box.setAlignment(Pos.CENTER);
        box.getChildren().addAll(
                legendItem("legend-free", I18n.t("seat.free")),
                legendItem("legend-taken", I18n.t(staff ? "seat.sold" : "seat.taken")),
                staff ? legendItem("legend-checked", I18n.t("seat.checkedIn")) : legendItem("legend-mine", I18n.t("seat.selected")),
                legendItem("legend-extra", I18n.t("seat.extraLegroomFee", I18n.money(Pricing.EXTRA_LEGROOM_FEE))));
        return box;
    }

    private static HBox legendItem(String style, String text) {
        final Region swatch = new Region();
        swatch.getStyleClass().addAll("legend-swatch-seat", style);
        swatch.setPrefSize(14, 14);
        swatch.setMinSize(14, 14);
        final HBox item = new HBox(6, swatch, new Label(text));
        item.setAlignment(Pos.CENTER_LEFT);
        return item;
    }
}
