package router

import (
	"github.com/fizzisme/api-gateway/internal/auth"
	"github.com/fizzisme/api-gateway/internal/config"
	"github.com/fizzisme/api-gateway/internal/metrics"
	"github.com/fizzisme/api-gateway/internal/middleware"
	"github.com/fizzisme/api-gateway/internal/proxy"
	"github.com/fizzisme/api-gateway/internal/ratelimit"
	"github.com/gin-gonic/gin"
	"go.opentelemetry.io/contrib/instrumentation/github.com/gin-gonic/gin/otelgin"
)

// SetupRouter builds the Gin engine for the gateway: global
// middleware (recovery, request ID, logging), health check routes,
// and all proxied service routes from the registry.
func SetupRouter(
	cfg *config.Config,
	registry *proxy.Registry,
	jwtService *auth.JWTService,
	manager *ratelimit.Manager) *gin.Engine {
	r := gin.New()

	// Global middleware chain, applied to every route, in order:
	//   1. Recovery    - recovers panics from route handlers
	//   2. RequestID   - assigns a request ID, used by Logger below
	//   3. otelgin     - starts a tracing span for the request
	//   4. Logger      - logs method/path/status/duration/request ID
	//   5. Metrics     - records request count + latency in Prometheus
	r.Use(
		gin.Recovery(),
		middleware.RequestID(),
		otelgin.Middleware(cfg.AppName),
		middleware.Logger(),
		middleware.Metrics(),
	)

	registerHealthRoutes(r)

	r.GET(
    "/metrics",
    gin.WrapH(metrics.Handler()),
	)

	registerProxyRoutes(
		r,
		registry,
		jwtService,
		manager,
	)

	return r
}

