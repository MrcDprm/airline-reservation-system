package com.mrcdprm.airline.core;

/**
 * Kullanıcıya gösterilecek iş kuralı hatası. Mesaj metni yerine bir kod taşır;
 * arayüz kodu seçili dilde "error.<kod>" anahtarıyla metne çevirir.
 */
public final class AirlineException extends RuntimeException {

    private final String code;
    private final transient Object[] args;

    public AirlineException(String code, Object... args) {
        super(code);
        this.code = code;
        this.args = args.clone();
    }

    public String code() {
        return code;
    }

    public Object[] args() {
        return args.clone();
    }
}
