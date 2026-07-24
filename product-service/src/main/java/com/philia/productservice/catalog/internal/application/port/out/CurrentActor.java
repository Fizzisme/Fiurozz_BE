package com.philia.productservice.catalog.internal.application.port.out;

import java.util.UUID;

public interface CurrentActor {

    Actor getRequiredActor();

    record Actor(UUID id, String displayName, String avatarUrl) {
    }
}
