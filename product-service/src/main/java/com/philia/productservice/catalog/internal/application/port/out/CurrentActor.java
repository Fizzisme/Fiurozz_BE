package com.philia.productservice.catalog.internal.application.port.out;

import com.philia.productservice.catalog.internal.application.exception.CurrentActorUnavailableException;

import java.util.Optional;
import java.util.UUID;

public interface CurrentActor {

    /**
     * Returns the trusted actor when authentication is present. Read-only public use cases
     * use the empty value to apply anonymous access rules.
     */
    Optional<Actor> findActor();

    /**
     * Requires authentication for commands such as Create Project.
     */
    default Actor getRequiredActor() {
        return findActor().orElseThrow(() ->
                new CurrentActorUnavailableException("A trusted authenticated actor is required.")
        );
    }

    record Actor(UUID id, String displayName, String avatarUrl) {
    }
}
