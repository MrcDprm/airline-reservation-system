package com.mrcdprm.airline.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Personel şifresi için güçlü şifre kuralları. */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 10;
    public static final int MAX_LENGTH = 128;

    private PasswordPolicy() {
    }

    /**
     * Şifrenin uymadığı kuralların kodları; boş liste şifrenin geçerli olduğunu gösterir.
     * Kodlar arayüzde "password.rule.<kod>" anahtarıyla metne çevrilir.
     */
    public static List<String> violations(String password, String username) {
        final String p = password == null ? "" : password;
        final List<String> rules = new ArrayList<>();
        if (p.length() < MIN_LENGTH)
            rules.add("length");
        if (p.length() > MAX_LENGTH)
            rules.add("tooLong");
        if (p.chars().noneMatch(Character::isUpperCase))
            rules.add("upper");
        if (p.chars().noneMatch(Character::isLowerCase))
            rules.add("lower");
        if (p.chars().noneMatch(Character::isDigit))
            rules.add("digit");
        if (username != null && username.strip().length() >= 3
                && p.toLowerCase(Locale.ROOT).contains(username.strip().toLowerCase(Locale.ROOT)))
            rules.add("containsLogin");
        return rules;
    }
}
