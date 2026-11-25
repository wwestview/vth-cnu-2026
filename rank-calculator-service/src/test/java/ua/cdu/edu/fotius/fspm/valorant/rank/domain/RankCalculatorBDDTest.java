package ua.cdu.edu.fotius.fspm.valorant.rank.domain;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ua.cdu.edu.fotius.fspm.valorant.rank.infrastructure.rest.RankController;

import static org.junit.jupiter.api.Assertions.*;

@MicronautTest
@DisplayName("Competitive Rating Policy & Endpoint Validation (ua.cdu.edu.fotius.fspm.valorant)")
class RankCalculatorBDDTest {

    private final RatingPolicy policy = RatingPolicy::defaultCompetitivePolicy;

    @Inject
    @Client("/")
    HttpClient client;

    @Nested
    @DisplayName("GIVEN a Victory match")
    class VictoryTests {
        @Test
        @DisplayName("WHEN player has MVP combat score (>=300) THEN applies performance bonus")
        void testWinWithMvpBonus() {
            var stats = new RatingPolicy.MatchStats(true, 8, 24, 10, 5, 320, false);
            var result = policy.calculate(stats);
            assertTrue(result.deltaRR() >= 23);
        }

        @Test
        @DisplayName("WHEN player has higher K/D THEN gains more RR than lower K/D in same match")
        void testHigherKdGivesMoreRrOnWin() {
            var highKd = policy.calculate(new RatingPolicy.MatchStats(true, 5, 24, 12, 4, 260, false));
            var midKd = policy.calculate(new RatingPolicy.MatchStats(true, 5, 15, 15, 4, 200, false));
            var lowKd = policy.calculate(new RatingPolicy.MatchStats(true, 5, 6, 18, 2, 130, false));

            assertTrue(highKd.deltaRR() > midKd.deltaRR());
            assertTrue(midKd.deltaRR() > lowKd.deltaRR());
            assertTrue(lowKd.deltaRR() >= 10);
        }

        @Test
        @DisplayName("WHEN roundMargin is out of bounds (>13) THEN endpoint returns 400 Bad Request")
        void testEndpointRejectsInvalidRoundMargin() {
            var invalidPayload = new RankController.RankCalculationRequest(true, 20, 15, 10, 4, 250, false);
            var req = HttpRequest.POST("/internal/v1/calculate-rating", invalidPayload);

            var ex = assertThrows(HttpClientResponseException.class,
                    () -> client.toBlocking().exchange(req, String.class));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        }
    }

    @Nested
    @DisplayName("GIVEN a Defeat match")
    class DefeatTests {
        @Test
        @DisplayName("WHEN player has higher K/D THEN loses less RR, and loss is never positive")
        void testHigherKdCushionsLossAndNeverPositive() {
            var highKdLoss = policy.calculate(new RatingPolicy.MatchStats(false, 2, 26, 12, 6, 320, false));
            var midKdLoss = policy.calculate(new RatingPolicy.MatchStats(false, 2, 14, 14, 4, 200, false));
            var lowKdLoss = policy.calculate(new RatingPolicy.MatchStats(false, 2, 5, 18, 1, 120, false));

            assertTrue(highKdLoss.deltaRR() < 0);
            assertTrue(highKdLoss.deltaRR() > midKdLoss.deltaRR());
            assertTrue(midKdLoss.deltaRR() > lowKdLoss.deltaRR());
        }

        @Test
        @DisplayName("WHEN player is AFK THEN applies -10 AFK penalty")
        void testAfkPenaltyApplied() {
            var normalLoss = policy.calculate(new RatingPolicy.MatchStats(false, 5, 15, 15, 4, 200, false));
            var afkLoss = policy.calculate(new RatingPolicy.MatchStats(false, 5, 15, 15, 4, 200, true));

            assertEquals(normalLoss.deltaRR() - 10, afkLoss.deltaRR());
        }
    }
}
