package ua.cdu.edu.fotius.fspm.valorant.analytics.domain.model;

public record MatchHistoryEntry(
    String matchId,
    String mapName,
    MatchResult result,
    int deltaRR,
    String tierStatus
) {}
