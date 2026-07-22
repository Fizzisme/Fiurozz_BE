package main

import (
	"context"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"
	"github.com/fizzisme/api-gateway/internal/auth"
	"github.com/fizzisme/api-gateway/internal/config"
	"github.com/fizzisme/api-gateway/internal/logger"
	"github.com/fizzisme/api-gateway/internal/metrics"
	"github.com/fizzisme/api-gateway/internal/proxy"
	"github.com/fizzisme/api-gateway/internal/ratelimit"
	"github.com/fizzisme/api-gateway/internal/resilience"
	"github.com/fizzisme/api-gateway/internal/router"
	"github.com/fizzisme/api-gateway/internal/tracing"
	"go.uber.org/zap"
)

// main is the entry point of the API Gateway. It wires up all core
// dependencies (config, logging, auth, rate limiting, routing),
// starts the HTTP server, and handles graceful shutdown on
// SIGINT/SIGTERM.
func main() {

	// Initialize the global logger first, since almost every
	// subsequent step depends on it for structured logging.
	err := logger.Init()
	if err != nil {
		// logger.Log is not available yet (init itself failed),
		// so fall back to the standard library logger here.
		log.Fatal(err)
	}

	// Flush any buffered log entries before the process exits.
	defer logger.Log.Sync()

	// Load application configuration (env vars / config file).
	cfg, err := config.Load()

	if err != nil {
		logger.Log.Fatal(
			"cannot load config",
			zap.Error(err),
		)
	}

	// Initialize Prometheus metrics collectors so they're
	// ready before the server starts handling traffic.
	metrics.Init()

	// Set up distributed tracing (OpenTelemetry) for this service.
	// shutdownTracer is a cleanup function that flushes any buffered
	// spans and closes the exporter connection; it must be called
	// before the process exits or trailing spans can be lost.
	shutdownTracer, err := tracing.InitTracer(
		context.Background(),
		cfg.AppName, // service name shown in tracing backend
		cfg.OTelEndpoint, // OTel collector endpoint to export spans to
	)

	if err != nil {
		logger.Log.Fatal(
			"cannot initialize tracer",
			zap.Error(err),
		)
	}

	// Ensure buffered spans are flushed on shutdown. Using a fresh
	// context here (not the request context) so this still runs even
	// if the original context was already canceled.
	defer shutdownTracer(context.Background())

	logger.Log.Info(
		"Application starting",
		zap.String("app", cfg.AppName), 
		zap.String("port", cfg.Port),
	)


	// Circuit breaker for the Auth Service backend. Trips after
	// repeated failures to stop sending requests to a backend that's
	// already down, giving it time to recover instead of piling on load.
	authBreaker := resilience.NewBreaker("auth")

	// Retry policy applied to Auth Service calls that fail transiently
	// (e.g. timeouts, connection errors): up to 3 attempts total, with
	// delay increasing from 1s up to a cap of 3s between retries
	// (presumably exponential/backoff, capped by MaxDelay).
	authRetryConfig := &resilience.RetryConfig{
		MaxAttempts: 3,               
		BaseDelay:   1 * time.Second, 
		MaxDelay:    3 * time.Second,
	}

	// Create a reverse proxy targeting the Auth Service. Requests under
	// "/auth" are forwarded to cfg.AuthService; each request is bounded
	// by a 5s timeout, and outbound calls go through the auth circuit
	// breaker + retry policy configured above. "auth" labels this
	// service in the breaker/retry logs and metrics.
	authProxy, err := proxy.New(cfg.AuthService, "/auth", 5 * time.Second, authBreaker, authRetryConfig, "auth")

	if err != nil {
		logger.Log.Fatal(
			"cannot create auth proxy",
			zap.Error(err),
		)
	}

	// Registry holds the mapping between route prefixes and their
	// backing services/proxies.
	registry := proxy.NewRegistry()

	// Register the Auth Service route.
	err = registry.Register(proxy.Route{
		Name: "Auth Service",
		Prefix: "/auth",
		Proxy: authProxy,

		RateLimit: &ratelimit.RateLimitConfig{
			Requests: 10,
			Window: 1 * time.Minute,
			Burst: 5,
		},
	
	})

	if err != nil {
		logger.Log.Fatal(
			"cannot register auth service",
			zap.Error(err),
		)
	}

	// JWT service used to issue/validate access tokens across the gateway.
	jwtService := auth.NewJWTService(cfg.JWTSecret, cfg.JWTIssuer)

	// Rate limit manager
	manager := ratelimit.NewManager()

	// Periodically evict stale rate-limit entries to prevent unbounded
	// memory growth (e.g. from short-lived or one-off client IPs).
	// Runs every 10m, evicting entries idle for more than 30m.
	manager.StartCleanup(10*time.Minute, 30*time.Minute)

	// Build the top-level HTTP router, wiring in middleware
	// (auth, rate limiting, etc.) and all registered routes.
	r := router.SetupRouter(cfg, registry, jwtService, manager)


	// HTTP server with conservative timeouts to guard against
	// slowloris-style attacks and hanging connections.
	srv := &http.Server{
		Addr:    ":" + cfg.Port,
		Handler: r,

		ReadTimeout:  10 * time.Second, // max time to read the full request
		WriteTimeout: 10 * time.Second, // max time to write the response
		IdleTimeout:  60 * time.Second,	// max keep-alive idle time
	}

	// Run the server in a separate goroutine so the main goroutine
	// remains free to listen for shutdown signals below.
	go func() {

		logger.Log.Info(
			"Server started",
			zap.String("port", cfg.Port),
		)

		// ListenAndServe blocks until the server stops. On graceful
		// shutdown it returns http.ErrServerClosed, which is expected
		// and must not be treated as a fatal error.
		if err := srv.ListenAndServe(); err != nil &&
			err != http.ErrServerClosed {

			logger.Log.Fatal(
				"cannot start server",
				zap.Error(err),
			)
		}
	}()

	// Channel to receive OS termination signals. Buffered with size 1
	// so signal.Notify never blocks while sending.
	quit := make(chan os.Signal, 1)

	// Listen for:
	//  - os.Interrupt: Ctrl+C in a terminal
	//  - syscall.SIGTERM: sent by Docker/Kubernetes on container stop
	signal.Notify(
		quit,
		os.Interrupt,
		syscall.SIGTERM,
	)

	defer signal.Stop(quit)

	// Block until a shutdown signal is received.
	<-quit

	logger.Log.Info("Shutting down server...")


	// Allow in-flight requests up to 5 seconds to complete before
	// forcing shutdown.
	ctx, cancel := context.WithTimeout(
		context.Background(),
		5*time.Second,
	)

	defer cancel()

	// Shutdown stops accepting new connections and waits for active
	// requests to finish (bounded by ctx) before returning.
	if err := srv.Shutdown(ctx); err != nil {
		logger.Log.Fatal(
			"server forced to shutdown",
			zap.Error(err),
		)
	}

	logger.Log.Info("Server stopped gracefully")

}