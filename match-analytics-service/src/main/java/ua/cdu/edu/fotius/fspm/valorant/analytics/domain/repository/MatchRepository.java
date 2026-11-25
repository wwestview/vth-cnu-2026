package ua.cdu.edu.fotius.fspm.valorant.analytics.domain.repository;

import ua.cdu.edu.fotius.fspm.valorant.analytics.domain.model.Match;
import ua.cdu.edu.fotius.fspm.valorant.analytics.domain.model.MatchHistoryEntry;

import java.util.List;

public interface MatchRepository {
    void save(Match match);
    List<MatchHistoryEntry> findHistoryByPlayerId(String playerId);
}
