package com.mrcdprm.airline.service;

import com.mrcdprm.airline.data.Records;
import com.mrcdprm.airline.service.FlightService.FlightRow;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Personel özet ekranının sayıları ve grafik verileri. */
public final class ReportService {

    public record DaySales(LocalDate date, long amount, int passengers) {
    }

    public record RouteLoad(String route, int sold, int capacity) {

        public double load() {
            return capacity == 0 ? 0 : (double) sold / capacity;
        }
    }

    public record Dashboard(int flightsToday, int passengersToday, int checkedInToday, long salesToday,
            double loadNext7Days, List<DaySales> last14Days, List<RouteLoad> routesNext7Days, List<FlightRow> upcoming) {
    }

    private final Airline airline;

    ReportService(Airline airline) {
        this.airline = airline;
    }

    public Dashboard dashboard(Session session) {
        Airline.requireStaff(session);
        final LocalDate today = airline.today();
        final LocalDateTime now = airline.now();
        return airline.db().read(c -> {
            final List<FlightRow> todayRows = FlightService.rows(c,
                    "WHERE f.departure >= ? AND f.departure < ? AND f.status <> 'CANCELLED'",
                    Records.ts(today.atStartOfDay()), Records.ts(today.plusDays(1).atStartOfDay()));
            final int passengers = todayRows.stream().mapToInt(FlightRow::sold).sum();
            final int checkedIn = todayRows.stream().mapToInt(FlightRow::checkedIn).sum();

            // Son 14 günün net satışı (iade düşülmüş) ve satılan bilet sayısı, satış tarihine göre
            final Map<LocalDate, long[]> byDay = new LinkedHashMap<>();
            for (int i = 13; i >= 0; i--)
                byDay.put(today.minusDays(i), new long[2]);
            try (PreparedStatement ps = c.prepareStatement("""
                    SELECT substr(b.created_at, 1, 10) AS day, SUM(b.total - b.refund) AS amount,
                           SUM((SELECT COUNT(*) FROM tickets t WHERE t.booking_id = b.id)) AS pax
                    FROM bookings b WHERE b.created_at >= ? GROUP BY day""")) {
                ps.setString(1, Records.ts(today.minusDays(13).atStartOfDay()));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        final long[] v = byDay.get(LocalDate.parse(rs.getString("day")));
                        if (v != null) {
                            v[0] = rs.getLong("amount");
                            v[1] = rs.getLong("pax");
                        }
                    }
                }
            }
            final List<DaySales> days = new ArrayList<>();
            byDay.forEach((d, v) -> days.add(new DaySales(d, v[0], (int) v[1])));

            // Önümüzdeki 7 günün doluluğu, hat bazında
            final List<FlightRow> week = FlightService.rows(c,
                    "WHERE f.departure >= ? AND f.departure < ? AND f.status <> 'CANCELLED'",
                    Records.ts(now), Records.ts(today.plusDays(8).atStartOfDay()));
            final Map<String, int[]> routes = new HashMap<>();
            int sold = 0;
            int capacity = 0;
            for (FlightRow r : week) {
                final String route = r.flight().origin().code() + " - " + r.flight().destination().code();
                final int[] v = routes.computeIfAbsent(route, k -> new int[2]);
                v[0] += r.sold();
                v[1] += r.capacity();
                sold += r.sold();
                capacity += r.capacity();
            }
            final List<RouteLoad> routeLoads = routes.entrySet().stream()
                    .map(e -> new RouteLoad(e.getKey(), e.getValue()[0], e.getValue()[1]))
                    .sorted(Comparator.comparingDouble(RouteLoad::load).reversed())
                    .limit(8)
                    .toList();
            final List<FlightRow> upcoming = week.stream().limit(8).toList();
            final long salesToday = days.get(days.size() - 1).amount();
            return new Dashboard(todayRows.size(), passengers, checkedIn, salesToday,
                    capacity == 0 ? 0 : (double) sold / capacity, days, routeLoads, upcoming);
        });
    }
}
