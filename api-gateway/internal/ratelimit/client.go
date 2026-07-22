package ratelimit

import (
	"time"
	"golang.org/x/time/rate"
)

type ClientLimiter struct {
	Limiter  *rate.Limiter
	LastSeen time.Time
}