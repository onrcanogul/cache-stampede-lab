package com.onurcanogul.stampede;

import com.onurcanogul.stampede.redis.RedisClient;
import com.onurcanogul.stampede.redis.RedisConfig;
import com.onurcanogul.stampede.redis.RedisException;
import com.onurcanogul.stampede.scenario.ExpiryBurstScenario;
import com.onurcanogul.stampede.scenario.Scenario;
import com.onurcanogul.stampede.scenario.SustainedTrafficScenario;

import java.util.ArrayList;
import java.util.List;

public final class Lab {

    private static final List<Scenario> ALL = List.of(
            new ExpiryBurstScenario(),
            new SustainedTrafficScenario());

    private Lab() {
    }

    public static void main(String[] args) {
        List<Scenario> scenarios = select(args);
        RedisConfig config = RedisConfig.fromEnvironment();

        try (RedisClient redis = connect(config)) {
            System.out.println("Redis " + config.address() + ", " + config.poolSize() + " pooled connections");
            for (Scenario scenario : scenarios) {
                System.out.println();
                System.out.println("## " + scenario.title());
                System.out.println();
                scenario.run(redis);
            }
        }
    }

    private static RedisClient connect(RedisConfig config) {
        try {
            return RedisClient.connect(config);
        } catch (RedisException e) {
            System.err.println("Redis is not reachable at " + config.address() + ". Start it with: docker compose up -d");
            System.exit(1);
            throw e;
        }
    }

    private static List<Scenario> select(String[] args) {
        if (args.length == 0) {
            return ALL;
        }
        List<Scenario> selected = new ArrayList<>();
        for (String arg : args) {
            Scenario match = ALL.stream().filter(s -> s.id().equalsIgnoreCase(arg)).findFirst().orElse(null);
            if (match == null) {
                System.err.println("Unknown scenario '" + arg + "'. Available: "
                        + String.join(", ", ALL.stream().map(Scenario::id).toList()));
                System.exit(2);
            }
            selected.add(match);
        }
        return selected;
    }
}
