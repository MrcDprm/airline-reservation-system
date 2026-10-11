package com.mrcdprm.airline.ui;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Kullanıcı tercihleri (dil, tema, örnek veri). Dosyadan okunan değere güvenilmez: bilinmeyen değer yerine varsayılan kullanılır.
 * Şifre ya da oturum bilgisi asla saklanmaz.
 */
public final class Settings {

    private final Path file;
    private final Properties props = new Properties();

    public Settings(Path folder) {
        this.file = folder.resolve("settings.properties");
        if (Files.isRegularFile(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                props.load(in);
            } catch (IOException | IllegalArgumentException ignored) {
                // bozuk dosya: varsayılanlarla devam
            }
        }
    }

    /** Veri klasörü: %APPDATA%\MrcDprm\AirlineReservations (program klasörüne yazılmaz). */
    public static Path dataFolder() {
        final String override = System.getProperty("airline.dataDir"); // geliştirme ve ekran görüntüleri için
        if (override != null && !override.isBlank())
            return Path.of(override);
        final String appData = System.getenv("APPDATA");
        final Path base = appData != null ? Path.of(appData) : Path.of(System.getProperty("user.home"));
        return base.resolve("MrcDprm").resolve("AirlineReservations");
    }

    public String language() {
        return "en".equals(props.getProperty("language")) ? "en" : "tr";
    }

    public void setLanguage(String language) {
        props.setProperty("language", "en".equals(language) ? "en" : "tr");
        save();
    }

    public boolean darkTheme() {
        return "dark".equals(props.getProperty("theme"));
    }

    public void setDarkTheme(boolean dark) {
        props.setProperty("theme", dark ? "dark" : "light");
        save();
    }

    /** Örnek veri yüklendiyse giriş penceresinde demo personel düğmesi gösterilir. */
    public boolean sampleData() {
        return "true".equals(props.getProperty("sampleData"));
    }

    public void setSampleData(boolean loaded) {
        props.setProperty("sampleData", String.valueOf(loaded));
        save();
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream out = Files.newOutputStream(file)) {
                props.store(out, "Airline Reservations");
            }
        } catch (IOException ignored) {
            // tercih kaydedilemezse uygulama yine çalışır
        }
    }
}
