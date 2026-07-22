package metrics

import (
	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promhttp"
	"net/http"
)

// Global metric collectors, registered once in Init() and used
// throughout the application (middleware, resilience layer, etc.)
// to record request/retry/circuit-breaker behavior.
var (
	// RequestsTotal counts every HTTP request handled, labeled by
	// method, path, and status code.
	RequestsTotal *prometheus.CounterVec

	// RequestDuration tracks request latency distribution, labeled
	// by method, path, and status code.
	RequestDuration *prometheus.HistogramVec

	// RetryTotal counts retry attempts made against backend
	// services, labeled by service name.
	RetryTotal *prometheus.CounterVec

	// BreakerState reports the current circuit breaker state per
	// service (e.g. 0=closed, 1=half-open, 2=open).
	BreakerState *prometheus.GaugeVec

	// BreakerRejectedTotal counts requests rejected outright because
	// the breaker for a service is open.
	BreakerRejectedTotal *prometheus.CounterVec

	// BreakerStateChangeTotal counts breaker state transitions,
	// labeled by service and the state transitioned to.
	BreakerStateChangeTotal *prometheus.CounterVec
)

// Init creates all Prometheus collectors and registers them with the
// default registry. Must be called once at startup, before any
// metric is recorded and before Handler() is served.
func Init() {

	RequestsTotal = prometheus.NewCounterVec(
		prometheus.CounterOpts{
			Name: "gateway_http_requests_total",
			Help: "Total number of HTTP requests handled by the gateway.",
		},
		[]string{
			"method",
			"path",
			"status",
		},
	)

	RequestDuration = prometheus.NewHistogramVec(
		prometheus.HistogramOpts{
			Name:    "gateway_http_request_duration_seconds",
			Help:    "HTTP request latency in seconds.",
			Buckets: prometheus.DefBuckets,
		},
		[]string{
			"method",
			"path",
			"status",
		},
	)

	RetryTotal = prometheus.NewCounterVec(
		prometheus.CounterOpts{
			Name: "gateway_retry_total",
			Help: "Total number of retry attempts.",
		},
		[]string{
			"service",
		},
	)

	BreakerState = prometheus.NewGaugeVec(
		prometheus.GaugeOpts{
			Name: "gateway_breaker_state",
			Help: "Current circuit breaker state.",
		},
		[]string{"service"},
	)

	BreakerRejectedTotal = prometheus.NewCounterVec(
		prometheus.CounterOpts{
			Name: "gateway_breaker_rejected_total",
			Help: "Rejected requests because breaker is open.",
		},
		[]string{"service"},
	)

	BreakerStateChangeTotal = prometheus.NewCounterVec(
		prometheus.CounterOpts{
			Name: "gateway_breaker_state_change_total",
			Help: "Circuit breaker state transitions.",
		},
		[]string{
			"service",
			"state",
		},
	)

	// Register all collectors with Prometheus's default registry so
	// they get scraped when Handler() is exposed. MustRegister panics
	// on duplicate registration, which would indicate Init() was
	// called more than once — that's intentional, since double-init
	// is a programming error, not a runtime condition to recover from.
	prometheus.MustRegister(
			RequestsTotal,
			RequestDuration,
			RetryTotal,
			BreakerState,
			BreakerRejectedTotal,
			BreakerStateChangeTotal,
		)	
}

// Handler returns the HTTP handler that exposes all registered
// metrics in Prometheus text format, meant to be mounted at a scrape
// endpoint like "/metrics".
func Handler() http.Handler {
	return promhttp.Handler()
}