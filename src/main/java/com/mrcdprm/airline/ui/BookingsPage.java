package com.mrcdprm.airline.ui;

import atlantafx.base.theme.Styles;
import atlantafx.base.theme.Tweaks;
import com.mrcdprm.airline.pdf.Documents;
import com.mrcdprm.airline.service.BookingService.BookingDetail;
import com.mrcdprm.airline.service.BookingService.BookingRow;
import com.mrcdprm.airline.service.BookingService.RefundQuote;
import java.util.List;
import java.util.function.Function;
import javafx.animation.PauseTransition;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.kordamp.ikonli.feather.Feather;

/** Rezervasyonlar (personel): kod, ad-soyad ya da e-postayla arama; ayrıntı, e-bilet ve iptal. */
public final class BookingsPage extends VBox {

    private final AppContext ctx;
    private final TextField query = new TextField();
    private final TableView<BookingRow> table = new TableView<>();
    private final Label count = Ui.muted("");

    public BookingsPage(AppContext ctx) {
        super(14);
        this.ctx = ctx;
        query.setPromptText(I18n.t("bookings.searchHint"));
        // her tuşta değil, yazma bitince ara
        final PauseTransition wait = new PauseTransition(Duration.millis(250));
        wait.setOnFinished(e -> reload());
        query.textProperty().addListener((obs, o, n) -> wait.playFromStart());
        HBox.setHgrow(query, Priority.ALWAYS);
        final Button open = Ui.button(I18n.t("bookings.open"), Feather.EYE);
        open.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        open.setOnAction(e -> openSelected());
        final HBox bar = new HBox(10, query, open);
        bar.setAlignment(Pos.CENTER_LEFT);

        table.getStyleClass().addAll(Styles.STRIPED, Tweaks.EDGE_TO_EDGE, "panel");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label(I18n.t("bookings.empty")));
        final TableColumn<BookingRow, BookingRow> status = new TableColumn<>(I18n.t("flights.col.status"));
        status.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue()));
        status.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(BookingRow r, boolean empty) {
                super.updateItem(r, empty);
                setGraphic(empty || r == null ? null : Views.bookingStatus(r.booking()));
            }
        });
        status.setPrefWidth(150);
        table.getColumns().setAll(List.of(
                col("booking.code", r -> r.booking().pnr(), 80),
                col("flights.col.number", r -> r.flight().number(), 80),
                col("flights.col.date", r -> I18n.shortDate(r.flight().departure().toLocalDate()) + " " + I18n.clock(r.flight().departure()), 130),
                col("flights.col.route", r -> r.flight().origin().code() + " → " + r.flight().destination().code(), 90),
                col("bookings.col.passengers", r -> r.leadName() + (r.passengers() > 1 ? "  +" + (r.passengers() - 1) : ""), 190),
                col("bookings.col.cabin", r -> I18n.of(r.booking().cabin()), 90),
                col("bookings.col.total", r -> I18n.money(r.booking().total()), 100),
                status));
        table.setRowFactory(t -> {
            final TableRow<BookingRow> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty())
                    openSelected();
            });
            return row;
        });
        VBox.setVgrow(table, Priority.ALWAYS);
        getChildren().addAll(Ui.header(I18n.t("nav.bookings"), I18n.t("bookings.subtitle")), bar, table, count);
        reload();
    }

    private static TableColumn<BookingRow, String> col(String key, Function<BookingRow, String> value, double w) {
        final TableColumn<BookingRow, String> c = new TableColumn<>(I18n.t(key));
        c.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        c.setPrefWidth(w);
        return c;
    }

    private void reload() {
        try {
            final List<BookingRow> rows = ctx.airline().bookings().search(ctx.session(), query.getText());
            table.getItems().setAll(rows);
            count.setText(I18n.t(rows.size() >= 200 ? "bookings.countLimited" : "bookings.count", rows.size()));
        } catch (RuntimeException e) {
            table.getItems().clear();
            count.setText(Ui.message(e));
        }
    }

    private void openSelected() {
        final BookingRow row = table.getSelectionModel().getSelectedItem();
        if (row == null)
            return;
        try {
            showDetail(ctx.airline().bookings().detail(ctx.session(), row.booking().id()));
        } catch (RuntimeException e) {
            Ui.error(ctx.window(), e);
        }
    }

    /** Ayrıntı penceresi: yolcu ekranındakiyle aynı kart; personel iptal edebilir ve e-bilet alabilir. */
    private void showDetail(BookingDetail d) {
        final Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(ctx.window());
        dialog.setTitle(I18n.t("bookings.detail", d.booking().pnr()));
        Theme.style(dialog.getDialogPane());
        dialog.setResizable(true);
        final BookingCard card = new BookingCard(d, ctx.airline().now(), null);
        final Button ticket = Ui.button(I18n.t("booking.eTicket"), Feather.DOWNLOAD);
        ticket.setOnAction(e -> PdfSaver.save(ctx, ticket, Documents.eTicketFileName(d),
                file -> Documents.eTicket(d, ctx.airline().now(), file)));
        final Button cancel = Ui.button(I18n.t("manage.cancel"), Feather.X_CIRCLE, Styles.DANGER, Styles.FLAT);
        final boolean active = !d.cancelled() && d.flight().status() != com.mrcdprm.airline.core.FlightStatus.CANCELLED
                && !d.flight().departed(ctx.airline().now());
        cancel.setVisible(active);
        cancel.setOnAction(e -> {
            try {
                final RefundQuote q = ctx.airline().bookings().refundQuote(ctx.session(), d.booking().id());
                if (!Ui.confirm(dialog.getDialogPane().getScene().getWindow(),
                        I18n.t("manage.cancelConfirm", I18n.money(q.paid()), q.farePercent(), I18n.money(q.refund())),
                        I18n.t("manage.cancelYes")))
                    return;
                final RefundQuote done = ctx.airline().bookings().cancel(ctx.session(), d.booking().id());
                dialog.close();
                reload();
                Ui.info(ctx.window(), I18n.t("manage.cancelled", I18n.money(done.refund())));
            } catch (RuntimeException ex) {
                Ui.error(ctx.window(), ex);
            }
        });
        final Label contact = Ui.muted(I18n.t("wizard.contactLine", d.booking().email(), d.booking().phone()));
        final HBox actions = new HBox(10, ticket, Ui.spacer(), cancel);
        final VBox content = new VBox(14, card, contact, actions);
        content.setPadding(new Insets(4));
        content.setPrefWidth(860);
        final ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(600);
        scroll.getStyleClass().add("transparent-scroll");
        dialog.getDialogPane().setContent(scroll);
        dialog.getDialogPane().getButtonTypes().add(new ButtonType(I18n.t("action.close"), ButtonBar.ButtonData.CANCEL_CLOSE));
        dialog.showAndWait();
    }
}
