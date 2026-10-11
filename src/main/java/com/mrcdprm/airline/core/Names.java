package com.mrcdprm.airline.core;

import java.util.Locale;

/**
 * Yolcu adları. Biletlerde havayolu geleneğine uygun olarak ad büyük harf ve Türkçe karaktersiz yazılır
 * ("YILMAZ/AYSE"); rezervasyon ararken soyadı aynı kurala göre karşılaştırılır, böylece "yilmaz",
 * "Yılmaz" ve "YILMAZ" aynı kabul edilir.
 */
public final class Names {

    public static final int MAX_LENGTH = 40;
    private static final Locale TR = Locale.forLanguageTag("tr");

    private Names() {
    }

    /** Baştaki/sondaki boşlukları atar, aradaki birden fazla boşluğu teke indirir. */
    public static String clean(String name) {
        return name == null ? "" : name.strip().replaceAll("\\s+", " ");
    }

    /** Harfler; arada tek boşluk, kısa çizgi ya da kesme işareti olabilir. 2-40 karakter. */
    public static boolean valid(String name) {
        final String n = clean(name);
        return n.length() >= 2 && n.length() <= MAX_LENGTH && n.matches("\\p{L}+(?:[ '\\-]\\p{L}+)*");
    }

    /** Türkçe büyük harfe çevirip Türkçe karakterleri sadeleştirir: "Ayşe Gül" -> "AYSE GUL". */
    public static String fold(String text) {
        final String upper = clean(text).toUpperCase(TR);
        final StringBuilder sb = new StringBuilder(upper.length());
        for (char c : upper.toCharArray()) {
            sb.append(switch (c) {
                case 'İ' -> 'I';
                case 'Ş' -> 'S';
                case 'Ğ' -> 'G';
                case 'Ü' -> 'U';
                case 'Ö' -> 'O';
                case 'Ç' -> 'C';
                case 'Â' -> 'A';
                case 'Î' -> 'I';
                case 'Û' -> 'U';
                default -> c;
            });
        }
        return sb.toString();
    }

    /** Biletteki ad: "SOYAD/AD". */
    public static String ticketName(String firstName, String lastName) {
        return fold(lastName) + "/" + fold(firstName);
    }

    /** İki soyad aynı mı? Boşluk, kesme ve kısa çizgi de yok sayılır. */
    public static boolean sameSurname(String a, String b) {
        final String x = fold(a).replaceAll("[^\\p{L}]", "");
        final String y = fold(b).replaceAll("[^\\p{L}]", "");
        return !x.isEmpty() && x.equals(y);
    }
}
