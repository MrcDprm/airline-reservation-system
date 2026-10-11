package com.mrcdprm.airline.core;

/**
 * Uçuşun veritabanındaki durumu. "Kalktı" ayrıca saklanmaz; kalkış saati geçmiş uçuş
 * arayüzde kalkmış olarak gösterilir (saat ilerledikçe kaydı güncellemek gerekmez).
 */
public enum FlightStatus {
    SCHEDULED,
    DELAYED,
    CANCELLED
}
