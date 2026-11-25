package ua.cdu.edu.fotius.fspm.valorant.analytics.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ua.cdu.edu.fotius.fspm.valorant.analytics.domain.exception.MatchDomainException;
import ua.cdu.edu.fotius.fspm.valorant.analytics.domain.model.*;

import static org.junit.jupiter.api.Assertions.*;

class MatchDomainTest {

    @Test
    @DisplayName("GIVEN valid inputs WHEN recording a match THEN creates aggregate with generated ID")
    void testRecordValidMatch() {
        var stats = new PlayerMatchStats("p-001", 22, 12, 5, 310, false);
        var rating = new RatingDelta(25, "PROMOTED");

        var match = Match.record("r-101", "Ascent", true, 5, stats, rating);

        assertNotNull(match.getId());
        assertFalse(match.getId().value().isBlank());
        assertEquals("r-101", match.getRosterId());
        assertEquals("Ascent", match.getMapName());
        assertEquals(MatchResult.WIN, match.getResult());
        assertEquals(5, match.getRoundMargin());
        assertEquals(stats, match.getPlayerStats());
        assertEquals(rating, match.getRatingDelta());
    }

    @Test
    @DisplayName("GIVEN isWin=false WHEN recording Match THEN result is LOSS")
    void testRecordLossMatch() {
        var stats = new PlayerMatchStats("p-001", 0, 24, 0, 0, true);
        var rating = new RatingDelta(-20, "DEMOTED");

        var match = Match.record("r-101", "Bind", false, 13, stats, rating);

        assertEquals(MatchResult.LOSS, match.getResult());
        assertEquals(13, match.getRoundMargin());
    }

    @Test
    @DisplayName("GIVEN blank MatchId WHEN created THEN throws INVALID_MATCH_ID")
    void testBlankMatchIdRejected() {
        var ex = assertThrows(MatchDomainException.class, () -> new MatchId("   "));
        assertEquals("INVALID_MATCH_ID", ex.getCode());
    }

    @Test
    @DisplayName("GIVEN negative or out-of-bounds stats WHEN creating PlayerMatchStats THEN throws INVALID_PLAYER_STATS")
    void testInvalidPlayerStatsRejected() {
        assertEquals("INVALID_PLAYER_STATS", assertThrows(
            MatchDomainException.class,
            () -> new PlayerMatchStats("p-001", -1, 10, 2, 200, false)
        ).getCode());

        assertEquals("INVALID_PLAYER_STATS", assertThrows(
            MatchDomainException.class,
            () -> new PlayerMatchStats("p-001", 10, 25, 2, 200, false)
        ).getCode());

        assertEquals("INVALID_PLAYER_STATS", assertThrows(
            MatchDomainException.class,
            () -> new PlayerMatchStats("p-001", 10, 10, 2, 1001, false)
        ).getCode());
    }

    @Test
    @DisplayName("GIVEN blank or oversized playerId WHEN creating PlayerMatchStats THEN throws INVALID_PLAYER_ID")
    void testBlankOrOversizedPlayerIdRejected() {
        assertEquals("INVALID_PLAYER_ID", assertThrows(
            MatchDomainException.class,
            () -> new PlayerMatchStats("   ", 10, 10, 2, 200, false)
        ).getCode());

        assertEquals("INVALID_PLAYER_ID", assertThrows(
            MatchDomainException.class,
            () -> new PlayerMatchStats("p".repeat(37), 10, 10, 2, 200, false)
        ).getCode());
    }

    @Test
    @DisplayName("GIVEN invalid round margin WHEN recording Match THEN throws INVALID_ROUND_MARGIN")
    void testInvalidRoundMarginRejected() {
        var stats = new PlayerMatchStats("p-001", 20, 10, 4, 250, false);
        var rating = new RatingDelta(18, "MAINTAINED");

        assertEquals("INVALID_ROUND_MARGIN", assertThrows(
            MatchDomainException.class,
            () -> Match.record("r-101", "Ascent", true, 0, stats, rating)
        ).getCode());

        assertEquals("INVALID_ROUND_MARGIN", assertThrows(
            MatchDomainException.class,
            () -> Match.record("r-101", "Ascent", true, 14, stats, rating)
        ).getCode());
    }

    @Test
    @DisplayName("GIVEN blank rosterId or mapName WHEN recording Match THEN throws MatchDomainException")
    void testBlankRosterIdOrMapNameRejected() {
        var stats = new PlayerMatchStats("p-001", 20, 10, 4, 250, false);
        var rating = new RatingDelta(-15, "DEMOTED");

        assertEquals("INVALID_ROSTER_ID", assertThrows(
            MatchDomainException.class,
            () -> Match.record(" ", "Ascent", false, 4, stats, rating)
        ).getCode());

        assertEquals("INVALID_MAP_NAME", assertThrows(
            MatchDomainException.class,
            () -> Match.record("r-101", "", false, 4, stats, rating)
        ).getCode());
    }

    @Test
    @DisplayName("GIVEN blank tierStatus WHEN creating RatingDelta THEN throws INVALID_TIER_STATUS")
    void testBlankTierStatusRejected() {
        var ex = assertThrows(MatchDomainException.class, () -> new RatingDelta(10, ""));
        assertEquals("INVALID_TIER_STATUS", ex.getCode());
    }
}
