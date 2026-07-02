package org.kfh.aiops.topology;

import java.util.List;

/**
 * A component on an application's flow (Channel / Gateway / Service / Database / Storage …).
 *
 * @param id             stable component id
 * @param name           display name
 * @param stage          flow stage/tier (used for readability + causal ordering)
 * @param dependsOn      component ids this component depends on (its foundations); following
 *                       {@code dependsOn} walks toward the root cause, the reverse walks the blast radius
 * @param applicationIds ids of the business applications whose flow includes this component
 */
public record Component(String id, String name, String stage, List<String> dependsOn, List<String> applicationIds) {

    public Component {
        dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
        applicationIds = applicationIds == null ? List.of() : List.copyOf(applicationIds);
    }
}
