package ratelimit

import (
	"time"
	"github.com/fizzisme/api-gateway/internal/logger"
	"go.uber.org/zap"
)

// Cleanup removes rate limiter entries that have been idle longer
// than expiration, to keep the client map from growing unbounded
// as new keys (users/IPs) accumulate over time.
func (m *Manager) Cleanup(expiration time.Duration) {

	m.mutex.Lock()
	defer m.mutex.Unlock()

	now := time.Now()

	removed := 0

	for key, client := range m.clients {

		if now.Sub(client.LastSeen) > expiration {

			delete(m.clients, key)
			removed++
		}
	}

	if removed > 0 && logger.Log != nil {

		logger.Log.Info(
			"cleanup idle rate limiters",
			zap.Int("removed", removed),
			zap.Int("remaining", len(m.clients)),
		)
	}
}

// StartCleanup launches a background goroutine that calls Cleanup
// on a fixed interval for the lifetime of the process. It does not
// support being stopped, since the manager is expected to live for
// the entire process lifetime.
func(m *Manager) StartCleanup(
	interval time.Duration,
	expiration time.Duration,
){
	ticker := time.NewTicker(interval)

	go func(){
		defer ticker.Stop()

		for range ticker.C{
			
			logger.Log.Debug("running rate limiter cleanup")

			m.Cleanup(expiration)
		}
	}()

	logger.Log.Info(
		"rate limiter cleanup worker started",
		zap.Duration("interval", interval),
		zap.Duration("expiration", expiration),
	)
}