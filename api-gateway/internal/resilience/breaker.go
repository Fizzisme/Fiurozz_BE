package resilience

import (
	"time"
	"github.com/sony/gobreaker/v2"
	"net/http"
	"github.com/fizzisme/api-gateway/internal/metrics"
)

func NewBreaker(name string) *gobreaker.CircuitBreaker[*http.Response] {

	settings := gobreaker.Settings{
		Name: name,

		MaxRequests: 5,

		Interval: time.Minute,

		Timeout: 30 * time.Second,

		ReadyToTrip: func(counts gobreaker.Counts) bool {

			return counts.ConsecutiveFailures >= 5

		},

		OnStateChange: func(name string, from, to gobreaker.State) {
			switch to {

			case gobreaker.StateClosed:

				metrics.BreakerState.
					WithLabelValues(name).
					Set(0)

			case gobreaker.StateHalfOpen:

				metrics.BreakerState.
					WithLabelValues(name).
					Set(0.5)

			case gobreaker.StateOpen:

				metrics.BreakerState.
					WithLabelValues(name).
					Set(1)

			}

			metrics.BreakerStateChangeTotal.
				WithLabelValues(
					name,
					to.String(),
				).
				Inc()
		},
	}

	return gobreaker.NewCircuitBreaker[*http.Response](settings)
}