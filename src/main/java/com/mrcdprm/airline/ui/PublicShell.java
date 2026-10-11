package com.mrcdprm.airline.ui;

import atlantafx.base.theme.Styles;
import com.mrcdprm.airline.service.BookingService.BookingKey;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Yolcu ekranı: üstte uygulama adı, "Uçuş ara" ve "Rezervasyonum" sekmeleri, personel girişi;
 * altta seçili sayfa. Satın alma adımları da bu iskeletin içinde açılır.
 */
public final class PublicShell extends BorderPane {

    private final AppContext ctx;
    private final ToggleButton searchTab = new ToggleButton(I18n.t("nav.search"), new FontIcon(Feather.SEARCH));
    private final ToggleButton manageTab = new ToggleButton(I18n.t("nav.manage"), new FontIcon(Feather.BRIEFCASE));
    private final ScrollPane scroll = new ScrollPane();
    private SearchPage searchPage;

    public PublicShell(AppContext ctx) {
        this.ctx = ctx;
        getStyleClass().add("shell");

        final ImageView logo = new ImageView(new Image(getClass().getResourceAsStream("/com/mrcdprm/airline/icon.png")));
        logo.setFitWidth(30);
        logo.setFitHeight(30);
        final Label name = new Label(I18n.t("app.name"));
        name.getStyleClass().add("topbar-title");
        final HBox brand = new HBox(10, logo, name);
        brand.setAlignment(Pos.CENTER_LEFT);

        final ToggleGroup group = new ToggleGroup();
        for (ToggleButton tab : new ToggleButton[] {searchTab, manageTab}) {
            tab.setToggleGroup(group);
            tab.getStyleClass().add("top-tab");
        }
        group.selectedToggleProperty().addListener((obs, old, now) -> {
            if (now == null)
                group.selectToggle(old);
        });
        searchTab.setOnAction(e -> showSearch());
        manageTab.setOnAction(e -> showManage(null));

        final Button staff = Ui.button(I18n.t("staff.signIn"), Feather.LOCK, Styles.FLAT);
        staff.setOnAction(e -> new StaffLoginDialog(ctx).showAndWait().ifPresent(session -> ctx.nav().showStaff(session)));

        final HBox top = new HBox(18, brand, new HBox(4, searchTab, manageTab), Ui.spacer(), staff, new TopTools(ctx));
        top.setAlignment(Pos.CENTER_LEFT);
        top.getStyleClass().add("topbar");
        top.setPadding(new Insets(10, 16, 10, 22));
        setTop(top);

        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("transparent-scroll");
        setCenter(scroll);

        if ("manage".equals(ctx.page()))
            showManage(null);
        else
            showSearch();
    }

    void showSearch() {
        ctx.setPage("search");
        searchTab.setSelected(true);
        if (searchPage == null)
            searchPage = new SearchPage(ctx, this);
        show(searchPage);
    }

    /** Rezervasyonum sayfası; kod ve soyad verilirse (satın almanın sonunda) hemen açılır. */
    void showManage(BookingKey key) {
        ctx.setPage("manage");
        manageTab.setSelected(true);
        show(new ManagePage(ctx, key));
    }

    /** Sayfa içeriğini değiştirir; içerik ortalanır ve en fazla 1080 px genişler. */
    void show(Node page) {
        final BorderPane holder = new BorderPane(page);
        holder.setPadding(new Insets(22, 28, 32, 28));
        holder.setMaxWidth(1080);
        final javafx.scene.layout.StackPane center = new javafx.scene.layout.StackPane(holder);
        center.setAlignment(Pos.TOP_CENTER);
        scroll.setContent(center);
        scroll.setVvalue(0);
    }
}
