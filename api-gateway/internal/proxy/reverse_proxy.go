package proxy

import (
	"context"
	"errors"
	"net/http"
	"net/http/httputil"
	"net/url"
	"strings"
	"time"
	"github.com/fizzisme/api-gateway/internal/logger"
	"go.uber.org/zap"
	"github.com/sony/gobreaker/v2"
	"github.com/fizzisme/api-gateway/internal/resilience"
)

// ReverseProxy wraps httputil.ReverseProxy for a single backend
// service, stripping a route prefix before forwarding requests and
// enforcing a per-request timeout on top of circuit breaking/retry.
type ReverseProxy struct {
	targetURL *url.URL
	proxy     *httputil.ReverseProxy
	timeout   time.Duration
}

// New creates a ReverseProxy that forwards requests to target, after
// stripping prefix from the incoming request path. Outbound calls go
// through a circuit breaker + retry policy (see resilience.Builder),
// and each request is bounded by timeout (see ServeHTTP).
func New(target string,
	prefix string,
	timeout time.Duration,
	breaker *gobreaker.CircuitBreaker[*http.Response],
	retryCfg *resilience.RetryConfig,
	service string,
) (*ReverseProxy, error) {

	u, err := url.Parse(target)
	if err != nil {
		return nil, err
	}

	rp := httputil.NewSingleHostReverseProxy(u)

	// Wrap the transport with circuit breaking and retry logic so
	// failing/unhealthy backends don't get hammered with requests.
	rp.Transport = resilience.
					NewBuilder().
					WithBreaker(breaker, service).
					WithRetry(retryCfg, service).
					Build()

	// Keep the default Director (sets scheme/host/path join with target)
	// and layer our own path-rewriting logic on top of it.
	originalDirector := rp.Director

	// Override Director
	rp.Director = func(req *http.Request) {

		originalDirector(req)

		// Strip the gateway-facing prefix so the backend sees paths
		// relative to its own root (e.g. "/auth/login" -> "/login").
		req.URL.Path = strings.TrimPrefix(
			req.URL.Path,
			prefix,
		)
	}

	// Return a generic 502 to the client on backend failures
	// (connection refused, timeout, etc.) instead of leaking
	// internal error details.
	// Return a generic 504 to timeout requests
	rp.ErrorHandler = func(
		w http.ResponseWriter,
		r *http.Request,
		err error,
	) {

		logger.Log.Error(
			"proxy error",
			zap.Error(err),
		)

		if errors.Is(err, context.DeadlineExceeded) {
			http.Error(
				w,
				"Gateway timeout",
				http.StatusGatewayTimeout,
			)
			return
		}

		http.Error(
			w,
			"Bad Gateway",
			http.StatusBadGateway,
		)
	}

	// Log response
	rp.ModifyResponse = func(resp *http.Response) error {

		logger.Log.Info(
			"proxy response",
			zap.Int("status", resp.StatusCode),
		)

		return nil
	}

	return &ReverseProxy{
		targetURL: u,
		proxy:     rp,
		timeout: timeout,
	}, nil
}

// ReverseProxy implement http.Handler
func (p *ReverseProxy) ServeHTTP(
	w http.ResponseWriter,
	r *http.Request,
) {

	if p.timeout <= 0 {
		p.proxy.ServeHTTP(w,r)
		return
	}

	ctx, cancel := context.WithTimeout(
		r.Context(),
		p.timeout,
	)

	defer cancel()

	newReq := r.Clone(ctx)

	p.proxy.ServeHTTP(w, newReq)
}