package ua.cdu.edu.fotius.fspm.valorant.rank.domain;

@FunctionalInterface
public interface RatingPolicy {

    record MatchStats(
        boolean isWin,
        int roundMargin,
        int kills,
        int deaths,
        int assists,
        int combatScore,
        boolean isAfk
    ) {
        public MatchStats {
            if (roundMargin < 1 || roundMargin > 13) {
                throw new IllegalArgumentException("roundMargin must be between 1 and 13");
            }
            if (kills < 0 || deaths < 0 || assists < 0 || combatScore < 0) {
                throw new IllegalArgumentException("Match statistics cannot be negative");
            }
        }
    }

    record CalculationResult(int deltaRR, String tierStatus) {}

    CalculationResult calculate(MatchStats stats);

    static CalculationResult defaultCompetitivePolicy(MatchStats stats) {
        int margin = Math.min(Math.max(stats.roundMargin(), 1), 13);
        int baseMagnitude = 12 + margin;
        int baseRR = stats.isWin() ? baseMagnitude : -baseMagnitude;

        double kd = stats.deaths() == 0 ? stats.kills() : (double) stats.kills() / stats.deaths();
        int performanceAdjustment = calculateKdAdjustment(kd)
            + calculateCombatScoreAdjustment(stats.combatScore())
            + (stats.assists() >= 8 ? 1 : 0);

        int rawDelta = baseRR + performanceAdjustment;
        int clampedDelta = stats.isWin()
            ? Math.clamp(rawDelta, 10, 35)
            : Math.clamp(rawDelta, -35, -5);

        int afkPenalty = stats.isAfk() ? -10 : 0;
        int finalDelta = clampedDelta + afkPenalty;

        String tierStatus = "MAINTAINED";
        if (finalDelta >= 25) {
            tierStatus = "PROMOTED";
        } else if (finalDelta <= -20) {
            tierStatus = "DEMOTED";
        }

        return new CalculationResult(finalDelta, tierStatus);
    }

    private static int calculateKdAdjustment(double kd) {
        if (kd >= 2.0) return 4;
        if (kd >= 1.5) return 3;
        if (kd >= 1.2) return 2;
        if (kd >= 1.0) return 1;
        if (kd >= 0.8) return 0;
        if (kd >= 0.5) return -2;
        return -4;
    }

    private static int calculateCombatScoreAdjustment(int combatScore) {
        if (combatScore >= 300) return 3;
        if (combatScore >= 240) return 2;
        if (combatScore >= 180) return 0;
        return -1;
    }
}
