package ua.cdu.edu.fotius.fspm.valorant.analytics.domain.model;

public enum MatchResult {
    WIN,
    LOSS;

    public static MatchResult fromWin(boolean isWin) {
        return isWin ? WIN : LOSS;
    }
}
