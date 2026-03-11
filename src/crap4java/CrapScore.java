package crap4java;

final class CrapScore {

    private CrapScore() {
    }

    static Double calculate(int complexity, Double coveragePercent) {
        if (coveragePercent == null) {
            return null;
        }
        double cc = complexity;
        double uncovered = 1.0 - (coveragePercent / 100.0);
        return (cc * cc * uncovered * uncovered * uncovered) + cc;
    }
}
