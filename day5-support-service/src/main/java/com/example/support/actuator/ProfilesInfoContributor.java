package com.example.support.actuator;

import java.util.Arrays;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Adds the active Spring profiles to {@code /actuator/info}. */
@Component
public class ProfilesInfoContributor implements InfoContributor {

    private final Environment environment;

    public ProfilesInfoContributor(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void contribute(Info.Builder builder) {
        String[] active = environment.getActiveProfiles();
        builder.withDetail("profiles", Arrays.asList(active.length > 0 ? active : environment.getDefaultProfiles()));
    }
}
