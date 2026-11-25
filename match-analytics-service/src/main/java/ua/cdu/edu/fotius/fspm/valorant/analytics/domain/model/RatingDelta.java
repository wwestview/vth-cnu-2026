package ua.cdu.edu.fotius.fspm.valorant.analytics.domain.model;

import ua.cdu.edu.fotius.fspm.valorant.analytics.domain.exception.MatchDomainException;

public record RatingDelta(int deltaRR, String tierStatus) {

    public RatingDelta {
        if (tierStatus == null || tierStatus.isBlank()) {
            throw new MatchDomainException("INVALID_TIER_STATUS", "Tier status is required");
        }
        tierStatus = tierStatus.trim();
    }
}
