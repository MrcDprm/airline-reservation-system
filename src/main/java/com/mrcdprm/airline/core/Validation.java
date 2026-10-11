package com.mrcdprm.airline.core;

import java.util.Locale;

/** Girdi doğrulama. Arayüz de aynı kontrolleri yapar, ama asıl kontrol servis katmanındadır. */
public final class Validation {

    public static final int MAX_EMAIL = 100;

    private Validation() {
    }

    /** Basit ama işe yarar e-posta kontrolü: bir @, noktalı alan adı, boşluk yok. */
    public static boolean email(String email) {
        return email != null && email.length() <= MAX_EMAIL
                && email.strip().matches("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9\\-]+(\\.[A-Za-z0-9\\-]+)*\\.[A-Za-z]{2,}");
    }

    /**
     * Telefon numarasını sadeleştirir: boşluk, parantez ve tire atılır. Türkiye cep numaraları
     * (05xx..., 5xx..., +90 5xx...) "+905xxxxxxxxx" olur; uluslararası numara + ile 10-15 rakam olabilir.
     * Geçersizse null döner.
     */
    public static String phone(String phone) {
        if (phone == null)
            return null;
        final String p = phone.strip().replaceAll("[\\s()\\-]", "");
        if (p.matches("05\\d{9}"))
            return "+9" + p;
        if (p.matches("5\\d{9}"))
            return "+90" + p;
        if (p.matches("\\+\\d{10,15}"))
            return p;
        return null;
    }

    /** Personel kullanıcı adı: 3-30 karakter, harf, rakam, nokta ve alt çizgi. */
    public static boolean username(String username) {
        return username != null && username.strip().matches("[A-Za-z0-9._]{3,30}");
    }

    public static String normalizeUsername(String username) {
        return username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
    }
}
