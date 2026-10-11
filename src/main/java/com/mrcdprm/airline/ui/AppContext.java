package com.mrcdprm.airline.ui;

import com.mrcdprm.airline.service.Airline;
import com.mrcdprm.airline.service.Session;
import java.util.function.Consumer;
import javafx.stage.Window;

/** Ekranların ortak ihtiyaçları: servisler, ayarlar, personel oturumu ve uygulama düzeyindeki geçişler. */
public final class AppContext {

    public interface Navigator {
        /** Yolcu ekranı (uçuş arama, rezervasyonum). */
        void showPublic();

        /** Personel paneli. */
        void showStaff(Session session);

        /** Dil ya da tema değişince mevcut ekranı baştan kurar (oturum ve açık sayfa korunur). */
        void rebuild();

        void logout();
    }

    private final Airline airline;
    private final Settings settings;
    private final Navigator navigator;
    private final Consumer<String> openUrl;
    private Window window;
    private Session session;
    private String page;

    public AppContext(Airline airline, Settings settings, Navigator navigator, Consumer<String> openUrl) {
        this.airline = airline;
        this.settings = settings;
        this.navigator = navigator;
        this.openUrl = openUrl;
    }

    public Airline airline() {
        return airline;
    }

    public Settings settings() {
        return settings;
    }

    public Navigator nav() {
        return navigator;
    }

    public Window window() {
        return window;
    }

    public void setWindow(Window window) {
        this.window = window;
    }

    public Session session() {
        return session;
    }

    public void setSession(Session session) {
        this.session = session;
    }

    /** Son açık sayfa; dil/tema değişince ekran baştan kurulurken aynı sayfaya dönülür. */
    public String page() {
        return page;
    }

    public void setPage(String page) {
        this.page = page;
    }

    public void openUrl(String url) {
        openUrl.accept(url);
    }
}
