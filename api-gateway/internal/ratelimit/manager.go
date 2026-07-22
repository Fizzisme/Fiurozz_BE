package ratelimit

import (
	"golang.org/x/time/rate"
	"sync"
	"time"
)

// Manager holds per-key rate limiters (e.g. one per user/IP),
// keyed by helper.BuildRateLimitKey, and tracks last-access time
// so idle entries can be evicted by Cleanup/StartCleanup.
type Manager struct {
	clients map[string]*ClientLimiter
	mutex sync.RWMutex
}

// NewManager creates an empty rate limiter manager.
func NewManager() *Manager {
	return &Manager{
		clients: make(map[string]*ClientLimiter),
	}
}

// GetLimiter returns the rate.Limiter for key, creating one with the
// given limit/burst if it doesn't exist yet, and refreshes LastSeen
// so the entry isn't evicted as idle.
func (m *Manager) GetLimiter(key string, limit rate.Limit, burst int) *rate.Limiter {
	m.mutex.Lock()
	defer m.mutex.Unlock()

	client, exists := m.clients[key]

	if !exists {
		client = &ClientLimiter{
			Limiter:  rate.NewLimiter(limit, burst),
		}
		m.clients[key] = client
	}
	client.LastSeen = time.Now()

	return client.Limiter
}