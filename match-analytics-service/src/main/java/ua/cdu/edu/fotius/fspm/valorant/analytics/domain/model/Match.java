package ua.cdu.edu.fotius.fspm.valorant.analytics.domain.model;

import ua.cdu.edu.fotius.fspm.valorant.analytics.domain.exception.MatchDomainException;

public class Match {

    public static final int MIN_ROUND_MARGIN = 1;
    public static final int MAX_ROUND_MARGIN = 13;
    public static final int MAX_ROSTER_ID_LENGTH = 36;
    public static final int MAX_MAP_NAME_LENGTH = 30;

    private final MatchId id;
    private final String rosterId;
    private final String mapName;
    private final MatchResult result;
    private final int roundMargin;
    private final PlayerMatchStats playerStats;
    private final RatingDelta ratingDelta;

    public Match(
        MatchId id,
        String rosterId,
        String mapName,
        MatchResult result,
        int roundMargin,
        PlayerMatchStats playerStats,
        RatingDelta ratingDelta
    ) {
        validateHeader(rosterId, mapName, roundMargin);
        if (id == null) {
            throw new MatchDomainException("INVALID_MATCH_ID", "MatchId is required");
        }
        if (result == null) {
            throw new MatchDomainException("INVALID_MATCH_RESULT", "MatchResult is required");
        }
        if (playerStats == null) {
            throw new MatchDomainException("INVALID_PLAYER_STATS", "PlayerMatchStats is required");
        }
        if (ratingDelta == null) {
            throw new MatchDomainException("INVALID_RATING_DELTA", "RatingDelta is required");
        }
        this.id = id;
        this.rosterId = rosterId.trim();
        this.mapName = mapName.trim();
        this.result = result;
        this.roundMargin = roundMargin;
        this.playerStats = playerStats;
        this.ratingDelta = ratingDelta;
    }

    public static void validateHeader(String rosterId, String mapName, int roundMargin) {
        if (rosterId == null || rosterId.isBlank() || rosterId.trim().length() > MAX_ROSTER_ID_LENGTH) {
            throw new MatchDomainException("INVALID_ROSTER_ID", "Roster ID is required and must not exceed " + MAX_ROSTER_ID_LENGTH + " characters");
        }
        if (mapName == null || mapName.isBlank() || mapName.trim().length() > MAX_MAP_NAME_LENGTH) {
            throw new MatchDomainException("INVALID_MAP_NAME", "Map name is required and must not exceed " + MAX_MAP_NAME_LENGTH + " characters");
        }
        if (roundMargin < MIN_ROUND_MARGIN || roundMargin > MAX_ROUND_MARGIN) {
            throw new MatchDomainException(
                "INVALID_ROUND_MARGIN",
                "Round margin must be between " + MIN_ROUND_MARGIN + " and " + MAX_ROUND_MARGIN
            );
        }
    }

    public static Match record(
        String rosterId,
        String mapName,
        boolean isWin,
        int roundMargin,
        PlayerMatchStats playerStats,
        RatingDelta ratingDelta
    ) {
        return new Match(
            MatchId.generate(),
            rosterId,
            mapName,
            MatchResult.fromWin(isWin),
            roundMargin,
            playerStats,
            ratingDelta
        );
    }

    public MatchId getId() {
        return id;
    }

    public String getRosterId() {
        return rosterId;
    }

    public String getMapName() {
        return mapName;
    }

    public MatchResult getResult() {
        return result;
    }

    public int getRoundMargin() {
        return roundMargin;
    }

    public PlayerMatchStats getPlayerStats() {
        return playerStats;
    }

    public RatingDelta getRatingDelta() {
        return ratingDelta;
    }
}
