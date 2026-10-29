package com.onurcanogul.stampede.scenario;

public enum Strategy {
    NAIVE("Naive cache-aside"),
    SINGLE_FLIGHT("Single-flight"),
    DISTRIBUTED_LOCK("Distributed lock"),
    STALE_WHILE_REVALIDATE("Stale-while-revalidate"),
    PROBABILISTIC_EARLY_REFRESH("Probabilistic early refresh (beta=1)");

    private final String label;

    Strategy(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
