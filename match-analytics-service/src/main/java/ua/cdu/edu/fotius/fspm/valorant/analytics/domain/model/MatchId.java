package ua.cdu.edu.fotius.fspm.valorant.analytics.domain.model;

import ua.cdu.edu.fotius.fspm.valorant.analytics.domain.exception.MatchDomainException;

import java.util.UUID;

public record MatchId(String value) {

    public MatchId {
        if (value == null || value.isBlank()) {
            throw new MatchDomainException("INVALID_MATCH_ID", "MatchId cannot be null or blank");
        }
        value = value.trim();
    }

    public static MatchId generate() {
        return new MatchId(UUID.randomUUID().toString());
    }
}
