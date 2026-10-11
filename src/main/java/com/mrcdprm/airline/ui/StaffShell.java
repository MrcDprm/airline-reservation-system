package com.mrcdprm.airline.ui;

import atlantafx.base.theme.Styles;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Personel paneli: solda menü (özet, uçuşlar, rezervasyonlar, hesabım) ve kullanıcı kutusu, sağda sayfa.
 * Sayfa her açılışta yeniden kurulur; başka sayfada yapılan değişiklik (ör. iptal) hemen görünür.
 */
public final class StaffShell extends BorderPane {

    private record Page(String key, Ikon icon, Supplier<Node> factory) {
    }

    private final AppContext ctx;
    private final Map<String, Page> pages = new LinkedHashMap<>();
    private final Map<String, ToggleButton> buttons = new LinkedHashMap<>();
    private final StackPane content = new StackPane();

    public StaffShell(AppContext ctx) {
        this.ctx = ctx;
        getStyleClass().add("shell");
        final List<Page> list = List.of(
                new Page("dashboard", Feather.GRID, () -> new DashboardPage(ctx, this)),
                new Page("flights", Feather.SEND, () -> new FlightsPage(ctx)),
                new Page("bookings", Feather.BOOK_OPEN, () -> new BookingsPage(ctx)),
                new Page("account", Feather.USER, () -> new AccountPage(ctx)));

        final ToggleGroup group = new ToggleGroup();
        final VBox nav = new VBox(2);
        for (Page page : list) {
            pages.put(page.key(), page);
            final ToggleButton button = new ToggleButton(I18n.t("nav." + page.key()), new FontIcon(page.icon()));
            button.setToggleGroup(group);
            button.getStyleClass().add("nav-item");
            button.setMaxWidth(Double.MAX_VALUE);
            button.setAlignment(Pos.CENTER_LEFT);
            button.setOnAction(e -> open(page.key()));
            buttons.put(page.key(), button);
            nav.getChildren().add(button);
        }
        group.selectedToggleProperty().addListener((obs, old, now) -> {
            if (now == null)
                group.selectToggle(old);
        });

        final ImageView logo = new ImageView(new Image(getClass().getResourceAsStream("/com/mrcdprm/airline/icon.png")));
        logo.setFitWidth(30);
        logo.setFitHeight(30);
        final Label appName = new Label(I18n.t("app.name"));
        appName.getStyleClass().add("sidebar-title");
        final VBox titles = new VBox(0, appName, Ui.muted(I18n.t("shell.staffSide")));
        final HBox brand = new HBox(10, logo, titles);
        brand.setAlignment(Pos.CENTER_LEFT);
        brand.setPadding(new Insets(4, 6, 14, 6));

        final VBox grow = new VBox();
        VBox.setVgrow(grow, Priority.ALWAYS);
        final VBox sidebar = new VBox(6, brand, nav, grow, userBox());
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPadding(new Insets(16, 12, 14, 12));
        sidebar.setPrefWidth(236);
        sidebar.setMinWidth(236);
        setLeft(sidebar);

        final Button passenger = Ui.button(I18n.t("shell.passengerSide"), Feather.EXTERNAL_LINK, Styles.FLAT);
        passenger.setOnAction(e -> ctx.nav().logout());
        final HBox top = new HBox(Ui.muted(I18n.t("shell.greeting", ctx.session().fullName().split(" ")[0])),
                Ui.spacer(), passenger, new TopTools(ctx));
        top.setAlignment(Pos.CENTER_LEFT);
        top.setPadding(new Insets(10, 16, 4, 28));
        content.setPadding(new Insets(6, 28, 24, 28));
        content.setAlignment(Pos.TOP_LEFT);
        final BorderPane main = new BorderPane(content);
        main.setTop(top);
        setCenter(main);

        final String last = ctx.page();
        open(last != null && pages.containsKey(last) ? last : "dashboard");
    }

    private VBox userBox() {
        final String name = ctx.session().fullName();
        final String[] parts = name.split(" ");
        final String initials = (parts[0].substring(0, 1) + (parts.length > 1 ? parts[parts.length - 1].substring(0, 1) : ""))
                .toUpperCase(I18n.locale());
        final Label avatar = new Label(initials);
        avatar.getStyleClass().add("avatar");
        avatar.setMinSize(36, 36);
        avatar.setAlignment(Pos.CENTER);
        final Label who = new Label(name);
        who.getStyleClass().add(Styles.TEXT_BOLD);
        final VBox text = new VBox(0, who, Ui.muted("@" + ctx.session().username()));
        final HBox row = new HBox(10, avatar, text);
        row.setAlignment(Pos.CENTER_LEFT);
        final Button logout = Ui.button(I18n.t("shell.logout"), Feather.LOG_OUT, Styles.FLAT);
        logout.setMaxWidth(Double.MAX_VALUE);
        logout.setAlignment(Pos.CENTER_LEFT);
        logout.setOnAction(e -> ctx.nav().logout());
        final VBox box = new VBox(10, row, logout);
        box.getStyleClass().add("user-box");
        box.setPadding(new Insets(12, 6, 0, 6));
        return box;
    }

    void open(String key) {
        final Page page = pages.get(key);
        ctx.setPage(key);
        buttons.get(key).setSelected(true);
        content.getChildren().setAll(page.factory().get());
    }
}
