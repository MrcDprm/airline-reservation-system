package com.mrcdprm.airline.service;

/** Giriş yapmış personel. Personel işlemi yapan her servis metodu bu nesneyi ister. */
public record Session(long staffId, String username, String fullName) {
}
