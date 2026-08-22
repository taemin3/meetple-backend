package com.meetple.backend.domain.auth.service;

import java.util.Locale;

final class EmailAddressNormalizer {

    private EmailAddressNormalizer() {
    }

    static String normalize(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
