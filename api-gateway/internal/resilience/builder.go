package resilience

import (
	"net/http"
	"github.com/sony/gobreaker/v2"
)

type Builder struct {
	base http.RoundTripper
}

func NewBuilder() *Builder{
	return &Builder{
		base: &http.Transport{},
	}
}

func (b *Builder) WithBreaker(
	breaker *gobreaker.CircuitBreaker[*http.Response],
	service string,
) *Builder{
	b.base = &BreakerTransport{
		Base: b.base,
		Breaker: breaker,
		Service: service,
	}
	return b
}

func (b *Builder) WithRetry(
	config *RetryConfig,
	service string,
) *Builder{
	b.base = NewRetryTransport(
		b.base,
		config,
		service,
	)
	return b
}

func (b *Builder) Build() http.RoundTripper{
	return b.base
}