package com.mrcdprm.airline.ui;

import com.mrcdprm.airline.core.AirlineException;
import com.mrcdprm.airline.core.Airport;
import com.mrcdprm.airline.core.Money;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Arayüz metinleri (Türkçe / İngilizce). Metinler i18n/messages_*.properties dosyalarındadır.
 * {0}, {1} yer tutucuları basit metin değiştirmeyle doldurulur; MessageFormat kullanılmaz çünkü
 * Türkçedeki kesme işaretini ("İstanbul'dan") özel karakter sayıp metni bozar.
 */
public final class I18n {

    public static final Locale TR = Locale.forLanguageTag("tr");
    public static final Locale EN = Locale.ENGLISH;

    private static Locale locale = TR;
    private static ResourceBundle bundle = load(TR);

    private I18n() {
    }

    public static void setLocale(Locale newLocale) {
        locale = "en".equals(newLocale.getLanguage()) ? EN : TR;
        bundle = load(locale);
    }

    public static Locale locale() {
        return locale;
    }

    public static boolean isTurkish() {
        return locale == TR;
    }

    private static ResourceBundle load(Locale l) {
        return ResourceBundle.getBundle("com.mrcdprm.airline.i18n.messages", l, ResourceBundle.Control.getNoFallbackControl(
                ResourceBundle.Control.FORMAT_PROPERTIES));
    }

    /** Anahtarın metni; eksik anahtar ekranda !anahtar! olarak görünür (geliştirirken hemen fark edilsin). */
    public static String t(String key, Object... args) {
        String text;
        try {
            text = bundle.getString(key);
        } catch (MissingResourceException e) {
            return "!" + key + "!";
        }
        for (int i = 0; i < args.length; i++)
            text = text.replace("{" + i + "}", String.valueOf(args[i]));
        return text;
    }

    /** Enum değerinin metni: "enum.CabinClass.BUSINESS" gibi. */
    public static String of(Enum<?> value) {
        return t("enum." + value.getDeclaringClass().getSimpleName() + "." + value.name());
    }

    /** Servis hatasını okunur cümleye çevirir; tutar ve tarih argümanları biçimlenir. */
    public static String error(AirlineException e) {
        final Object[] args = e.args();
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof LocalDateTime time)
                args[i] = time(time);
            else if (args[i] instanceof Long amount)
                args[i] = money(amount);
        }
        if ("weakPassword".equals(e.code()) && args.length == 1)
            return t("error.weakPassword") + "\n" + passwordRules(String.valueOf(args[0]));
        return t("error." + e.code(), args);
    }

    /** "length,upper" -> her kural ayrı satırda, madde imli. */
    public static String passwordRules(String codes) {
        final StringBuilder out = new StringBuilder();
        for (String code : codes.split(",")) {
            if (code.isBlank())
                continue;
            if (!out.isEmpty())
                out.append('\n');
            out.append("• ").append(t("password.rule." + code));
        }
        return out.toString();
    }

    public static String money(long minor) {
        return Money.format(minor, locale);
    }

    /** "İstanbul (IST)" */
    public static String airport(Airport a) {
        return a.city(locale) + " (" + a.code() + ")";
    }

    /** "İstanbul Havalimanı (IST)" */
    public static String airportLong(Airport a) {
        return a.city(locale) + " · " + a.airportName(locale) + " (" + a.code() + ")";
    }

    /** "1 sa 25 dk" / "1h 25m" */
    public static String duration(int minutes) {
        final int h = minutes / 60;
        final int m = minutes % 60;
        if (h == 0)
            return t("time.minutes", m);
        return m == 0 ? t("time.hours", h) : t("time.hoursMinutes", h, m);
    }

    public static String date(LocalDate date) {
        return date == null ? "" : date.format(DateTimeFormatter.ofPattern(isTurkish() ? "d MMMM yyyy" : "MMMM d, yyyy", locale));
    }

    /** "12 Eki Pzt" / "Mon, Oct 12" (tarih şeridi ve kartlar için). */
    public static String shortDate(LocalDate date) {
        return date == null ? "" : date.format(DateTimeFormatter.ofPattern(isTurkish() ? "d MMM EEE" : "EEE, MMM d", locale));
    }

    public static String clock(LocalDateTime time) {
        return time == null ? "" : time.format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    public static String clock(LocalTime time) {
        return time == null ? "" : time.format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    public static String time(LocalDateTime time) {
        return time == null ? "" : time.format(DateTimeFormatter.ofPattern(isTurkish() ? "d MMM yyyy HH:mm" : "MMM d, yyyy HH:mm", locale));
    }
}
