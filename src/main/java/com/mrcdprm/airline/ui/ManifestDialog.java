package com.mrcdprm.airline.ui;

import atlantafx.base.theme.Styles;
import com.mrcdprm.airline.data.Records.Flight;
import com.mrcdprm.airline.data.Records.Ticket;
import com.mrcdprm.airline.service.FlightService.ManifestRow;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** Yolcu listesi: solda doluluk haritası (check-in yapılanlar ayrı renkte), sağda koltuk sırasına göre yolcular. */
public final class ManifestDialog extends Dialog<Void> {

    public ManifestDialog(AppContext ctx, Flight f) {
        initOwner(ctx.window());
        setTitle(I18n.t("flights.manifest") + " · " + f.number());
        Theme.style(getDialogPane());
        setResizable(true);

        final List<ManifestRow> rows = ctx.airline().flights().manifest(ctx.session(), f.id());
        final SeatMapView map = new SeatMapView(f.aircraft(), null);
        map.setOccupied(rows.stream().map(r -> r.ticket().seat()).collect(Collectors.toSet()));
        final Set<String> checked = rows.stream().filter(r -> r.ticket().checkedIn()).map(r -> r.ticket().seat())
                .collect(Collectors.toSet());
        map.setHighlighted(checked);
        final StackPane mapHolder = new StackPane(map);
        mapHolder.setPadding(new Insets(0, 8, 0, 0));
        final ScrollPane mapScroll = new ScrollPane(mapHolder);
        mapScroll.setPrefViewportHeight(500);
        mapScroll.setMinViewportWidth(400);
        mapScroll.setFitToWidth(true);
        mapScroll.getStyleClass().add("transparent-scroll");
        final VBox left = new VBox(10, SeatMapView.legend(true), mapScroll);
        left.setMinWidth(Region.USE_PREF_SIZE);

        final TableView<ManifestRow> table = new TableView<>();
        table.getStyleClass().addAll(Styles.STRIPED, Styles.DENSE);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label(I18n.t("manifest.empty")));
        table.getColumns().setAll(List.of(
                col("wizard.col.seat", r -> r.ticket().seat(), 55),
                col("wizard.col.passenger", r -> r.ticket().ticketName(), 180),
                col("wizard.col.type", r -> I18n.of(r.ticket().type()), 80),
                col("booking.code", ManifestRow::pnr, 80),
                col("manifest.boardingNo", r -> {
                    final Ticket t = r.ticket();
                    return t.checkedIn() ? String.format("%03d", t.boardingNo()) : "—";
                }, 80)));
        table.getItems().setAll(rows);
        table.setPrefSize(560, 540);
        table.setMinWidth(540);
        HBox.setHgrow(table, Priority.ALWAYS);

        final Label summary = new Label(I18n.t("manifest.summary", Views.route(f), I18n.time(f.departure()), rows.size(),
                f.aircraft().capacity(), checked.size()));
        summary.getStyleClass().add(Styles.TEXT_BOLD);
        final VBox content = new VBox(12, summary, new HBox(16, left, table));
        content.setPadding(new Insets(6, 4, 0, 4));
        getDialogPane().setContent(content);
        getDialogPane().getButtonTypes().add(new ButtonType(I18n.t("action.close"), ButtonBar.ButtonData.CANCEL_CLOSE));
    }

    private static TableColumn<ManifestRow, String> col(String key, java.util.function.Function<ManifestRow, String> value, double w) {
        final TableColumn<ManifestRow, String> c = new TableColumn<>(I18n.t(key));
        c.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        c.setPrefWidth(w);
        return c;
    }
}
