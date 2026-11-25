package ua.cdu.edu.fotius.fspm.valorant.analytics.infrastructure.persistence;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jdbi.v3.core.Jdbi;
import ua.cdu.edu.fotius.fspm.valorant.analytics.domain.model.Match;
import ua.cdu.edu.fotius.fspm.valorant.analytics.domain.model.MatchHistoryEntry;
import ua.cdu.edu.fotius.fspm.valorant.analytics.domain.model.MatchResult;
import ua.cdu.edu.fotius.fspm.valorant.analytics.domain.repository.MatchRepository;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Singleton
public class JdbiMatchRepository implements MatchRepository {

    private static final int DEFAULT_HISTORY_LIMIT = 100;

    private final Jdbi jdbi;

    @Inject
    public JdbiMatchRepository(Jdbi jdbi) {
        this.jdbi = jdbi;
    }

    @Override
    public void save(Match match) {
        Objects.requireNonNull(match, "Match is required");
        var stats = match.getPlayerStats();
        var rating = match.getRatingDelta();

        jdbi.useTransaction(handle -> {
            handle.createUpdate(
                    "INSERT INTO matches (id, roster_id, map_name, result, round_margin) " +
                    "VALUES (:id, :rid, :map, :res, :margin)")
                .bind("id", match.getId().value())
                .bind("rid", match.getRosterId())
                .bind("map", match.getMapName())
                .bind("res", match.getResult().name())
                .bind("margin", match.getRoundMargin())
                .execute();

            handle.createUpdate(
                    "INSERT INTO match_players (id, match_id, player_id, kills, deaths, assists, combat_score, delta_rr, tier_status) " +
                    "VALUES (:id, :mid, :pid, :k, :d, :a, :score, :rr, :tier)")
                .bind("id", UUID.randomUUID().toString())
                .bind("mid", match.getId().value())
                .bind("pid", stats.playerId())
                .bind("k", stats.kills())
                .bind("d", stats.deaths())
                .bind("a", stats.assists())
                .bind("score", stats.combatScore())
                .bind("rr", rating.deltaRR())
                .bind("tier", rating.tierStatus())
                .execute();
        });
    }

    @Override
    public List<MatchHistoryEntry> findHistoryByPlayerId(String playerId) {
        return jdbi.withHandle(handle ->
            handle.createQuery(
                    "SELECT m.id, m.map_name, m.result, mp.delta_rr, mp.tier_status " +
                    "FROM matches m JOIN match_players mp ON m.id = mp.match_id " +
                    "WHERE mp.player_id = :pid ORDER BY m.played_at DESC LIMIT :limit")
                .bind("pid", playerId)
                .bind("limit", DEFAULT_HISTORY_LIMIT)
                .map((rs, ctx) -> new MatchHistoryEntry(
                    rs.getString("id"),
                    rs.getString("map_name"),
                    MatchResult.valueOf(rs.getString("result")),
                    rs.getInt("delta_rr"),
                    rs.getString("tier_status")
                ))
                .list()
        );
    }
}
