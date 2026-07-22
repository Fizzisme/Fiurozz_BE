package router

import (
	"github.com/fizzisme/api-gateway/internal/auth"
	"github.com/fizzisme/api-gateway/internal/proxy"
	"github.com/gin-gonic/gin"
	"github.com/fizzisme/api-gateway/internal/ratelimit"
)

func registerProxyRoutes(
	r *gin.Engine,
	registry *proxy.Registry,
	jwtService *auth.JWTService,
	manager *ratelimit.Manager,
) {

	for _, route := range registry.Routes() {

		handlers := buildHandlers(
			route,
			jwtService,
			manager,
		)

		r.Any(
			route.Prefix+"/*path",
			handlers...,
		)

	}

}