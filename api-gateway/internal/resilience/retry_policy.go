package resilience

import (
	"context"
	"errors"
	"net/http"

	"github.com/sony/gobreaker/v2"
)

func ShouldRetry(
	resp *http.Response,
	err error,
) bool {
	if err != nil {

		if errors.Is(err, context.Canceled) {
			return false
		}

		if errors.Is(err, context.DeadlineExceeded) {
			return false
		}

		if errors.Is(err, gobreaker.ErrOpenState) {
			return false
		}

		return true
	}
	if resp == nil {

	return false

	}
	switch resp.StatusCode {

	case
		http.StatusInternalServerError,
		http.StatusBadGateway,
		http.StatusServiceUnavailable,
		http.StatusGatewayTimeout:

		return true

	default:

		return false
	}
}