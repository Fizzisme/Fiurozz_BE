package resilience

import (
	"net/http"
	"github.com/sony/gobreaker/v2"
	"github.com/fizzisme/api-gateway/internal/metrics"
)

// BreakerTransport wraps an http.RoundTripper with circuit-breaker
// protection: calls are routed through Breaker, which tracks failures
// and short-circuits (rejects immediately) once the failure threshold
// trips, instead of letting requests keep hitting a failing backend.
type BreakerTransport struct {
	Base    http.RoundTripper
	Breaker *gobreaker.CircuitBreaker[*http.Response]
	Service string
}

// RoundTrip implements http.RoundTripper. It executes the underlying
// request through the circuit breaker, tracking a rejected-request
// metric whenever the breaker is open and short-circuits the call.
func (t *BreakerTransport) RoundTrip(
	req *http.Request,
) (*http.Response, error) {

	result, err := t.Breaker.Execute(
		func() (*http.Response, error) {

			return t.Base.RoundTrip(req)

		},
	)

	// ErrOpenState means the breaker rejected this call without even
	// attempting the request (backend presumed unhealthy). Track this
	// separately from ordinary request failures for observability.
	if err == gobreaker.ErrOpenState {
		metrics.BreakerRejectedTotal.
			WithLabelValues(
				t.Service,
			).
			Inc()
	}

	if err != nil {
		return nil, err
	}

	return result, nil
}