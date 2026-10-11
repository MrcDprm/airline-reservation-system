package com.mrcdprm.airline.core;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.OptionalLong;

/**
 * Para tutarları (TL). Bütün hesaplar kuruş cinsinden long ile yapılır; double kullanılmaz,
 * çünkü 0.1 + 0.2 gibi sayılar ikilik sistemde tam gösterilemez ve kuruş kaybolur.
 */
public final class Money {

    /** Personelin girebileceği en yüksek bilet ücreti: 1 milyon TL (yazım hatasına karşı). */
    public static final long MAX_FARE = 100_000_000L;

    private Money() {
    }

    /** 124900 kuruş -> "1.249 ₺"; küsuratlı tutarlar iki ondalıkla: "1.249,50 ₺". */
    public static String format(long minor, Locale locale) {
        final DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(locale);
        final String pattern = minor % 100 == 0 ? "#,##0" : "#,##0.00";
        return new DecimalFormat(pattern, symbols).format(BigDecimal.valueOf(minor, 2)) + " ₺";
    }

    /**
     * Kullanıcının yazdığı tutarı kuruşa çevirir: "1250", "1.250", "1250,50" (Türkçe) ya da "1,250.50" (İngilizce).
     * Seçili dilin binlik ayırıcısı atılır, ondalık ayırıcısı noktaya çevrilir. Geçersiz, sıfır, negatif,
     * ikiden fazla ondalıklı ya da çok büyük tutarlarda boş döner.
     */
    public static OptionalLong parse(String text, Locale locale) {
        if (text == null)
            return OptionalLong.empty();
        String s = text.strip().replace("₺", "").replace("TL", "").replace(" ", "").replace(" ", "");
        if (s.isEmpty() || !s.matches("[0-9.,]+"))
            return OptionalLong.empty();
        final boolean turkish = "tr".equals(locale.getLanguage());
        final String grouping = turkish ? "." : ",";
        final String decimal = turkish ? "," : ".";
        // binlik ayırıcı varsa gruplar 3 basamaklı olmalı ("1.2.3" kabul edilmez)
        final String integerPart = s.contains(decimal) ? s.substring(0, s.indexOf(decimal)) : s;
        if (integerPart.contains(grouping) && !integerPart.matches("\\d{1,3}(" + java.util.regex.Pattern.quote(grouping) + "\\d{3})+"))
            return OptionalLong.empty();
        s = s.replace(grouping, "").replace(decimal, ".");
        if (s.chars().filter(c -> c == '.').count() > 1 || s.startsWith(".") || s.endsWith("."))
            return OptionalLong.empty();
        try {
            final BigDecimal value = new BigDecimal(s);
            if (value.stripTrailingZeros().scale() > 2)
                return OptionalLong.empty();
            final long minor = value.movePointRight(2).setScale(0).longValueExact();
            if (minor <= 0 || minor > MAX_FARE)
                return OptionalLong.empty();
            return OptionalLong.of(minor);
        } catch (ArithmeticException | NumberFormatException e) {
            return OptionalLong.empty();
        }
    }
}
