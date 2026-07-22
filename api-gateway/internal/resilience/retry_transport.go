package resilience

import (
	"net/http"
	"time"
	"github.com/fizzisme/api-gateway/internal/metrics"
)

type RetryTransport struct {
	Base   http.RoundTripper
	Config *RetryConfig
	Service string
}

func NewRetryTransport(
	base http.RoundTripper,
	config *RetryConfig,
	service string,
) *RetryTransport {

	if base == nil {
		base = http.DefaultTransport
	}

	return &RetryTransport{
		Base:   base,
		Config: config,
		Service: service,
	}
}

func (t *RetryTransport) RoundTrip(
	req *http.Request,
) (*http.Response, error) {

	switch req.Method {
	case http.MethodGet,
		http.MethodHead,
		http.MethodOptions:
	default:
		return t.Base.RoundTrip(req)
	}

	var (
		resp *http.Response
		err  error
	)

	for attempt := 1; attempt <= t.Config.MaxAttempts; attempt++ {

		resp, err = t.Base.RoundTrip(req)

		if !ShouldRetry(resp, err) {
			return resp, err
		}

		if attempt == t.Config.MaxAttempts {
			break
		}

		metrics.RetryTotal.
			WithLabelValues(
				t.Service,
			).
			Inc()

		if resp != nil && resp.Body != nil {
			resp.Body.Close()
		}

		delay := t.Config.BaseDelay * (1 << (attempt - 1))
		if t.Config.MaxDelay > 0 && delay > t.Config.MaxDelay {
			delay = t.Config.MaxDelay
		}

		select {
		case <-req.Context().Done():
			return nil, req.Context().Err()
		case <-time.After(delay):
		}
	}

	return resp, err
}