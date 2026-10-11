package com.mrcdprm.airline.service;

import com.mrcdprm.airline.core.AirlineException;
import com.mrcdprm.airline.core.Names;
import com.mrcdprm.airline.core.PasswordHasher;
import com.mrcdprm.airline.core.PasswordPolicy;
import com.mrcdprm.airline.core.Validation;
import com.mrcdprm.airline.data.Records;
import com.mrcdprm.airline.data.Records.Staff;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Personel girişi. Şifreler Argon2id ile saklanır; 5 hatalı denemede hesap 15 dakika kilitlenir.
 * Hata mesajı kullanıcı adının mı şifrenin mi yanlış olduğunu söylemez.
 */
public final class AuthService {

    public static final int MAX_ATTEMPTS = 5;
    public static final int LOCK_MINUTES = 15;
    /** Olmayan kullanıcıda da aynı süre harcansın diye doğrulanan sahte özet (zamanlamadan kullanıcı adı anlaşılmasın). */
    private static final String DUMMY_HASH = PasswordHasher.hash("dummy-password-for-timing");

    private final Airline airline;

    AuthService(Airline airline) {
        this.airline = airline;
    }

    public boolean hasStaff() {
        return airline.db().read(c -> {
            try (ResultSet rs = c.createStatement().executeQuery("SELECT EXISTS(SELECT 1 FROM staff)")) {
                return rs.next() && rs.getInt(1) == 1;
            }
        });
    }

    /** İlk açılışta yönetici hesabı; personel varsa çalışmaz (kurulum ekranı ikinci kez kullanılamasın). */
    public Session createFirstAdmin(String username, String fullName, String password) {
        if (hasStaff())
            throw new AirlineException("setupDone");
        return addStaff(username, fullName, password);
    }

    /** Personel ekler (kurulum ve örnek veri için). */
    Session addStaff(String username, String fullName, String password) {
        final String user = Validation.normalizeUsername(username);
        final String name = Names.clean(fullName);
        if (!Validation.username(user))
            throw new AirlineException("invalidUsername");
        if (name.length() < 3 || name.length() > 60 || !name.matches("\\p{L}+(?:[ '\\-]\\p{L}+)*"))
            throw new AirlineException("invalidFullName");
        final List<String> rules = PasswordPolicy.violations(password, user);
        if (!rules.isEmpty())
            throw new AirlineException("weakPassword", String.join(",", rules));
        final String hash = PasswordHasher.hash(password);
        final long id = airline.db().transaction(c -> {
            try (PreparedStatement exists = c.prepareStatement("SELECT 1 FROM staff WHERE username = ?")) {
                exists.setString(1, user);
                if (exists.executeQuery().next())
                    throw new AirlineException("usernameTaken");
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO staff(username, full_name, password_hash, created_at) VALUES (?, ?, ?, ?)",
                    java.sql.Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, user);
                ps.setString(2, name);
                ps.setString(3, hash);
                ps.setString(4, Records.ts(airline.now()));
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    return keys.getLong(1);
                }
            }
        });
        return new Session(id, user, name);
    }

    public Session login(String username, String password) {
        final String user = Validation.normalizeUsername(username);
        final Optional<Staff> found = find(user);
        if (found.isEmpty()) {
            PasswordHasher.verify(password == null ? "" : password, DUMMY_HASH);
            throw new AirlineException("invalidLogin");
        }
        final Staff staff = found.get();
        final LocalDateTime now = airline.now();
        if (staff.lockedUntil() != null && staff.lockedUntil().isAfter(now))
            throw new AirlineException("accountLocked", staff.lockedUntil());
        if (!PasswordHasher.verify(password, staff.passwordHash())) {
            final int attempts = staff.failedAttempts() + 1;
            final boolean lock = attempts >= MAX_ATTEMPTS;
            airline.db().transaction(c -> {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE staff SET failed_attempts = ?, locked_until = ? WHERE id = ?")) {
                    ps.setInt(1, lock ? 0 : attempts);
                    ps.setString(2, lock ? Records.ts(now.plusMinutes(LOCK_MINUTES)) : null);
                    ps.setLong(3, staff.id());
                    return ps.executeUpdate();
                }
            });
            if (lock)
                throw new AirlineException("accountLocked", now.plusMinutes(LOCK_MINUTES));
            throw new AirlineException("invalidLogin");
        }
        airline.db().transaction(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE staff SET failed_attempts = 0, locked_until = NULL WHERE id = ?")) {
                ps.setLong(1, staff.id());
                return ps.executeUpdate();
            }
        });
        return new Session(staff.id(), staff.username(), staff.fullName());
    }

    public void changePassword(Session session, String current, String next) {
        Airline.requireStaff(session);
        final Staff staff = find(session.username()).orElseThrow(() -> new AirlineException("notAuthorized"));
        if (!PasswordHasher.verify(current, staff.passwordHash()))
            throw new AirlineException("wrongPassword");
        if (current.equals(next))
            throw new AirlineException("samePassword");
        final List<String> rules = PasswordPolicy.violations(next, staff.username());
        if (!rules.isEmpty())
            throw new AirlineException("weakPassword", String.join(",", rules));
        final String hash = PasswordHasher.hash(next);
        airline.db().transaction(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE staff SET password_hash = ? WHERE id = ?")) {
                ps.setString(1, hash);
                ps.setLong(2, staff.id());
                return ps.executeUpdate();
            }
        });
    }

    private Optional<Staff> find(String username) {
        return airline.db().read(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM staff WHERE username = ?")) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(Records.staff(rs)) : Optional.<Staff>empty();
                }
            }
        });
    }
}
