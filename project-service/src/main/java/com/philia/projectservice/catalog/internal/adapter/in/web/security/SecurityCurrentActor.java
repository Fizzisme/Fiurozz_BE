package com.philia.projectservice.catalog.internal.adapter.in.web.security;

import com.philia.projectservice.catalog.internal.application.port.out.CurrentActor;
import com.philia.projectservice.shared.security.GatewayActorPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public final class SecurityCurrentActor implements CurrentActor {

    @Override
    public Optional<Actor> findActor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof GatewayActorPrincipal principal)) {
            return Optional.empty();
        }

        return Optional.of(new Actor(principal.id(), principal.displayName(), principal.avatarUrl()));
    }
}
