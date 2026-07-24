package com.philia.productservice.shared.security;

import java.util.UUID;

public record GatewayActorPrincipal(
        UUID id,
        String email,
        String displayName,
        String avatarUrl
) {
}
