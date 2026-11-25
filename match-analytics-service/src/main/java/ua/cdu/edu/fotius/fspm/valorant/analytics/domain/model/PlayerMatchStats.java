package ua.cdu.edu.fotius.fspm.valorant.analytics.domain.model;

import ua.cdu.edu.fotius.fspm.valorant.analytics.domain.exception.MatchDomainException;

public record PlayerMatchStats(
    String playerId,
    int kills,
    int deaths,
    int assists,
    int combatScore,
    boolean isAfk
) {
    public static final int MAX_PLAYER_ID_LENGTH = 36;
    public static final int MAX_KDA = 100;
    public static final int MAX_REGULATION_DEATHS = 24;
    public static final int MAX_COMBAT_SCORE = 1000;

    public PlayerMatchStats {
        if (playerId == null || playerId.isBlank()) {
            throw new MatchDomainException("INVALID_PLAYER_ID", "Player ID is required");
        }
        playerId = playerId.trim();
        if (playerId.length() > MAX_PLAYER_ID_LENGTH) {
            throw new MatchDomainException("INVALID_PLAYER_ID", "Player ID must not exceed " + MAX_PLAYER_ID_LENGTH + " characters");
        }
        if (kills < 0 || deaths < 0 || assists < 0 || combatScore < 0) {
            throw new MatchDomainException(
                "INVALID_PLAYER_STATS",
                "Kills, deaths, assists, and combatScore cannot be negative"
            );
        }
        if (kills > MAX_KDA || assists > MAX_KDA || deaths > MAX_REGULATION_DEATHS || combatScore > MAX_COMBAT_SCORE) {
            throw new MatchDomainException(
                "INVALID_PLAYER_STATS",
                "Match stats exceed valid competitive bounds"
            );
        }
    }
}
