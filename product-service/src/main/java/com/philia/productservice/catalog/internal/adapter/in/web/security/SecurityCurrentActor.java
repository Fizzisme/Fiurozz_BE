package com.philia.productservice.catalog.internal.adapter.in.web.security;

import com.philia.productservice.catalog.internal.application.exception.CurrentActorUnavailableException;
import com.philia.productservice.catalog.internal.application.port.out.CurrentActor;
import com.philia.productservice.shared.security.GatewayActorPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public final class SecurityCurrentActor implements CurrentActor {

    @Override
    public Actor getRequiredActor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof GatewayActorPrincipal principal)) {
            throw new CurrentActorUnavailableException("A trusted authenticated actor is required.");
        }

        return new Actor(principal.id(), principal.displayName(), principal.avatarUrl());
    }
}
