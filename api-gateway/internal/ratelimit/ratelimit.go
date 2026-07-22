package ratelimit

import "time"

type RateLimitConfig struct {
	Requests int
    Window   time.Duration
    Burst    int
}