package resilience

import "time"

type RetryConfig struct {
	MaxAttempts int

	BaseDelay time.Duration

	MaxDelay time.Duration
}