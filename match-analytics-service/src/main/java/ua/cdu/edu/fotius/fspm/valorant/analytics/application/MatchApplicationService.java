package ua.cdu.edu.fotius.fspm.valorant.analytics.application;

import io.micronaut.http.client.exceptions.HttpClientResponseException;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.cdu.edu.fotius.fspm.valorant.analytics.domain.exception.MatchDomainException;
import ua.cdu.edu.fotius.fspm.valorant.analytics.domain.model.Match;
import ua.cdu.edu.fotius.fspm.valorant.analytics.domain.model.MatchHistoryEntry;
import ua.cdu.edu.fotius.fspm.valorant.analytics.domain.model.PlayerMatchStats;
import ua.cdu.edu.fotius.fspm.valorant.analytics.domain.model.RatingDelta;
import ua.cdu.edu.fotius.fspm.valorant.analytics.domain.repository.MatchRepository;
import ua.cdu.edu.fotius.fspm.valorant.analytics.infrastructure.client.RankCalculatorClient;
import ua.cdu.edu.fotius.fspm.valorant.analytics.infrastructure.client.RosterServiceClient;

import java.util.List;

@Singleton
public class MatchApplicationService {

    private static final Logger log = LoggerFactory.getLogger(MatchApplicationService.class);

    private final MatchRepository matchRepository;
    private final RankCalculatorClient rankClient;
    private final RosterServiceClient rosterClient;

    @Inject
    public MatchApplicationService(
        MatchRepository matchRepository,
        RankCalculatorClient rankClient,
        RosterServiceClient rosterClient
    ) {
        this.matchRepository = matchRepository;
        this.rankClient = rankClient;
        this.rosterClient = rosterClient;
    }

    public record SubmitMatchCommand(
        String rosterId,
        String playerId,
        String mapName,
        boolean isWin,
        int roundMargin,
        int kills,
        int deaths,
        int assists,
        int combatScore,
        boolean isAfk
    ) {}

    public Match submitMatch(SubmitMatchCommand command, String bearerToken) {
        if (command == null) {
            throw new MatchDomainException("INVALID_REQUEST", "Match submission command is required");
        }

        // 1. Validate domain invariants BEFORE any outbound network calls
        Match.validateHeader(command.rosterId(), command.mapName(), command.roundMargin());
        var playerStats = new PlayerMatchStats(
            command.playerId(),
            command.kills(),
            command.deaths(),
            command.assists(),
            command.combatScore(),
            command.isAfk()
        );
        String rosterId = command.rosterId().trim();

        // 2. Verify roster is LOCKED (fail-fast)
        verifyRosterIsLocked(rosterId, bearerToken);

        // 3. Calculate rating delta via Rank Calculator
        var ratingDelta = calculateRating(command);
        log.info("Rank calculated for roster {}: deltaRR={}, tierStatus={}",
            rosterId, ratingDelta.deltaRR(), ratingDelta.tierStatus());

        var match = Match.record(
            rosterId,
            command.mapName(),
            command.isWin(),
            command.roundMargin(),
            playerStats,
            ratingDelta
        );

        // 4. Archive roster first, then persist match with compensation on DB failure
        archiveRoster(rosterId, bearerToken);
        persistWithCompensation(match, bearerToken);

        log.info("Match recorded: matchId={}, rosterId={}", match.getId().value(), match.getRosterId());
        return match;
    }

    public List<MatchHistoryEntry> getMatchHistory(String playerId) {
        if (playerId == null || playerId.isBlank() || playerId.trim().length() > PlayerMatchStats.MAX_PLAYER_ID_LENGTH) {
            throw new MatchDomainException("INVALID_PLAYER_ID", "Valid player ID is required");
        }
        try {
            return matchRepository.findHistoryByPlayerId(playerId.trim());
        } catch (Exception e) {
            log.error("Failed to fetch match history for player {}", playerId.trim(), e);
            throw new MatchDomainException("PERSISTENCE_FAILED", "Failed to load match history", e);
        }
    }

    private void verifyRosterIsLocked(String rosterId, String bearerToken) {
        try {
            var roster = rosterClient.getRoster(rosterId, bearerToken);
            if (roster == null || roster.status() == null) {
                throw new MatchDomainException("ROSTER_VERIFICATION_FAILED", "Empty response from roster service");
            }
            if ("ARCHIVED".equals(roster.status())) {
                log.warn("Roster {} is ARCHIVED (already submitted), rejecting", rosterId);
                throw new MatchDomainException("ROSTER_ALREADY_ARCHIVED", "Match already submitted for this roster");
            }
            if (!"LOCKED".equals(roster.status())) {
                log.warn("Roster {} is {} (not LOCKED), rejecting", rosterId, roster.status());
                throw new MatchDomainException(
                    "ROSTER_NOT_LOCKED",
                    "Roster must be LOCKED before match submission"
                );
            }
        } catch (MatchDomainException e) {
            throw e;
        } catch (HttpClientResponseException e) {
            int code = e.getStatus().getCode();
            log.warn("Roster verification failed for {} with HTTP {}", rosterId, code);
            if (code == 401) {
                throw new MatchDomainException("UNAUTHORIZED", "Invalid or expired Bearer token", e);
            }
            if (code == 403) {
                throw new MatchDomainException("FORBIDDEN", "Not authorized to access this roster", e);
            }
            if (code == 404) {
                throw new MatchDomainException("ROSTER_NOT_FOUND", "Roster not found", e);
            }
            throw new MatchDomainException("ROSTER_VERIFICATION_FAILED", "Could not verify roster status", e);
        } catch (Exception e) {
            log.warn("Failed to verify roster {}", rosterId, e);
            throw new MatchDomainException("ROSTER_VERIFICATION_FAILED", "Could not verify roster status", e);
        }
    }

    private RatingDelta calculateRating(SubmitMatchCommand command) {
        try {
            var rankRes = rankClient.calculateRating(new RankCalculatorClient.RankCalculationRequest(
                command.isWin(),
                command.roundMargin(),
                command.kills(),
                command.deaths(),
                command.assists(),
                command.combatScore(),
                command.isAfk()
            ));
            if (rankRes == null) {
                throw new MatchDomainException("RANK_CALCULATION_FAILED", "Empty response from rank calculator");
            }
            return new RatingDelta(rankRes.deltaRR(), rankRes.tierStatus());
        } catch (MatchDomainException e) {
            throw e;
        } catch (HttpClientResponseException e) {
            log.warn("Rank calculation rejected with HTTP {}", e.getStatus().getCode(), e);
            throw new MatchDomainException("RANK_CALCULATION_FAILED", "Could not calculate match rating", e);
        } catch (Exception e) {
            log.error("Rank calculator service unavailable", e);
            throw new MatchDomainException("RANK_CALCULATION_FAILED", "Could not calculate match rating", e);
        }
    }

    private void archiveRoster(String rosterId, String bearerToken) {
        try {
            rosterClient.archiveRoster(rosterId, bearerToken);
            log.info("Roster {} archived", rosterId);
        } catch (HttpClientResponseException e) {
            int code = e.getStatus().getCode();
            if (code == 409) {
                log.warn("Roster {} already archived (409 race condition)", rosterId);
                throw new MatchDomainException("ROSTER_ALREADY_ARCHIVED", "Match already submitted for this roster", e);
            }
            if (code == 401) {
                throw new MatchDomainException("UNAUTHORIZED", "Invalid or expired Bearer token", e);
            }
            if (code == 403) {
                throw new MatchDomainException("FORBIDDEN", "Not authorized to archive this roster", e);
            }
            log.warn("Failed to archive roster {} with HTTP {}", rosterId, code, e);
            throw new MatchDomainException("ROSTER_ARCHIVE_FAILED", "Failed to archive roster", e);
        } catch (Exception e) {
            log.warn("Failed to archive roster {}", rosterId, e);
            throw new MatchDomainException("ROSTER_ARCHIVE_FAILED", "Failed to archive roster", e);
        }
    }

    private void persistWithCompensation(Match match, String bearerToken) {
        try {
            matchRepository.save(match);
        } catch (Exception e) {
            log.error("Failed to persist match {} for roster {}", match.getId().value(), match.getRosterId(), e);
            try {
                rosterClient.unarchiveRoster(match.getRosterId(), bearerToken);
                log.info("Compensated: roster {} unarchived", match.getRosterId());
            } catch (Exception compensationEx) {
                log.error("Compensation failed for roster {}", match.getRosterId(), compensationEx);
            }
            throw new MatchDomainException("PERSISTENCE_FAILED", "Failed to persist match", e);
        }
    }
}
