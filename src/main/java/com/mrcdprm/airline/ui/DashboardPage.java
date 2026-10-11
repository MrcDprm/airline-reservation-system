package com.mrcdprm.airline.ui;

import atlantafx.base.theme.Styles;
import com.mrcdprm.airline.service.FlightService.FlightRow;
import com.mrcdprm.airline.service.ReportService.Dashboard;
import com.mrcdprm.airline.service.ReportService.DaySales;
import com.mrcdprm.airline.service.ReportService.RouteLoad;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

/** Personel özet ekranı: bugünün sayıları, son 14 günün satışları, haftalık hat doluluğu ve sıradaki kalkışlar. */
public final class DashboardPage extends ScrollPane {

    public DashboardPage(AppContext ctx, StaffShell shell) {
        getStyleClass().add("transparent-scroll");
        setFitToWidth(true);
        final VBox page = new VBox(18);
        page.setPadding(new Insets(0, 4, 8, 0));
        setContent(page);
        page.getChildren().add(Ui.header(I18n.t("nav.dashboard"), I18n.t("dashboard.subtitle", I18n.date(ctx.airline().today()))));

        final Dashboard d;
        try {
            d = ctx.airline().reports().dashboard(ctx.session());
        } catch (RuntimeException e) {
            page.getChildren().add(new Label(Ui.message(e)));
            return;
        }
        final HBox tiles = new HBox(14,
                tile(Feather.SEND, I18n.t("dashboard.flightsToday"), String.valueOf(d.flightsToday()), null),
                tile(Feather.USERS, I18n.t("dashboard.passengersToday"), String.valueOf(d.passengersToday()),
                        I18n.t("dashboard.checkedIn", d.checkedInToday())),
                tile(Feather.PIE_CHART, I18n.t("dashboard.load7"), Math.round(d.loadNext7Days() * 100) + "%", null),
                tile(Feather.CREDIT_CARD, I18n.t("dashboard.salesToday"), I18n.money(d.salesToday()), null));
        tiles.getChildren().forEach(n -> HBox.setHgrow(n, Priority.ALWAYS));

        // son 14 günün net satışı (bin TL)
        final CategoryAxis x = new CategoryAxis();
        final NumberAxis y = new NumberAxis();
        y.setLabel(I18n.t("dashboard.thousandTl"));
        final BarChart<String, Number> chart = new BarChart<>(x, y);
        chart.setLegendVisible(false);
        chart.setAnimated(false);
        chart.setPrefHeight(270);
        chart.getStyleClass().add("sales-chart");
        final XYChart.Series<String, Number> series = new XYChart.Series<>();
        final DateTimeFormatter dayFormat = DateTimeFormatter.ofPattern("d MMM", I18n.locale());
        for (DaySales s : d.last14Days())
            series.getData().add(new XYChart.Data<>(s.date().format(dayFormat), s.amount() / 100_000.0));
        chart.getData().add(series);
        for (int i = 0; i < series.getData().size(); i++) {
            final DaySales s = d.last14Days().get(i);
            Tooltip.install(series.getData().get(i).getNode(),
                    new Tooltip(I18n.date(s.date()) + "\n" + I18n.money(s.amount()) + " · " + I18n.t("dashboard.tickets", s.passengers())));
        }
        final VBox salesCard = Ui.card(Ui.section(I18n.t("dashboard.sales14")), chart);
        HBox.setHgrow(salesCard, Priority.ALWAYS);

        final VBox routes = new VBox(10, Ui.section(I18n.t("dashboard.routes7")));
        for (RouteLoad r : d.routesNext7Days())
            routes.getChildren().add(loadRow(r.route(), r.load(), r.sold() + " / " + r.capacity()));
        if (d.routesNext7Days().isEmpty())
            routes.getChildren().add(Ui.muted(I18n.t("dashboard.noFlights")));
        routes.getStyleClass().add("panel");
        routes.setPadding(new Insets(16));
        routes.setPrefWidth(330);
        routes.setMinWidth(300);

        final HBox middle = new HBox(14, salesCard, routes);

        final GridPane upcoming = new GridPane();
        upcoming.setHgap(22);
        upcoming.setVgap(8);
        final LocalDateTime now = ctx.airline().now();
        int row = 0;
        for (FlightRow r : d.upcoming()) {
            final Label time = new Label(I18n.clock(r.flight().departure()));
            time.getStyleClass().add(Styles.TEXT_BOLD);
            final ProgressBar bar = new ProgressBar(r.load());
            bar.setPrefWidth(140);
            upcoming.addRow(row++, time, new Label(r.flight().number()),
                    new Label(r.flight().origin().code() + " → " + r.flight().destination().code()),
                    Ui.muted(I18n.t("booking.gate") + " " + r.flight().gate()), bar,
                    Ui.muted(r.sold() + " / " + r.capacity()), Views.flightStatus(r.flight(), now));
        }
        final javafx.scene.control.Button all = Ui.button(I18n.t("dashboard.allFlights"), Feather.ARROW_RIGHT, Styles.FLAT);
        all.setContentDisplay(javafx.scene.control.ContentDisplay.RIGHT);
        all.setOnAction(e -> shell.open("flights"));
        final HBox upcomingHead = new HBox(Ui.section(I18n.t("dashboard.upcoming")), Ui.spacer(), all);
        upcomingHead.setAlignment(Pos.CENTER_LEFT);
        final VBox upcomingCard = Ui.card(upcomingHead, d.upcoming().isEmpty() ? Ui.muted(I18n.t("dashboard.noFlights")) : upcoming);

        page.getChildren().addAll(tiles, middle, upcomingCard);
    }

    private static VBox tile(Ikon icon, String label, String value, String note) {
        final FontIcon i = new FontIcon(icon);
        i.getStyleClass().add("stat-icon");
        final Label v = new Label(value);
        v.getStyleClass().add("tile-value");
        final VBox box = Ui.card(new HBox(8, i, Ui.muted(label)), v);
        if (note != null)
            box.getChildren().add(Ui.muted(note));
        box.setMinWidth(170);
        return box;
    }

    private static VBox loadRow(String route, double load, String text) {
        final ProgressBar bar = new ProgressBar(load);
        bar.setMaxWidth(Double.MAX_VALUE);
        if (load >= 0.85)
            bar.getStyleClass().add("load-high");
        final Label name = new Label(route);
        name.getStyleClass().add(Styles.TEXT_BOLD);
        final HBox head = new HBox(name, Ui.spacer(), Ui.muted(Math.round(load * 100) + "% · " + text));
        return new VBox(4, head, bar);
    }
}
