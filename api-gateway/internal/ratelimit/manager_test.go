package ratelimit

import (
	"testing"
	"time"
	"golang.org/x/time/rate"
)

func TestGetLimiter_ReturnSameLimiter(t *testing.T) {

	manager := NewManager()

	l1 := manager.GetLimiter(
		"user:123",
		rate.Limit(5),
		5,
	)

	l2 := manager.GetLimiter(
		"user:123",
		rate.Limit(5),
		5,
	)

	if l1 != l2 {
		t.Fatal("expected same limiter instance")
	}
}

func TestGetLimiter_CreateNewLimiter(t *testing.T) {

	manager := NewManager()

	l1 := manager.GetLimiter(
		"user:123",
		rate.Limit(5),
		5,
	)

	l2 := manager.GetLimiter(
		"user:456",
		rate.Limit(5),
		5,
	)

	if l1 == l2 {
		t.Fatal("expected different limiter")
	}
}

func TestCleanup(t *testing.T) {

	manager := NewManager()

	manager.GetLimiter(
		"user:123",
		rate.Limit(5),
		5,
	)

	manager.mutex.Lock()

	manager.clients["user:123"].LastSeen =
		time.Now().Add(-1 * time.Hour)

	manager.mutex.Unlock()

	manager.Cleanup(30 * time.Minute)

	if len(manager.clients) != 0 {

		t.Fatal("cleanup should remove expired client")

	}
}

func TestCleanup_NotRemoveActiveClient(t *testing.T) {

	manager := NewManager()

	manager.GetLimiter(
		"user:123",
		rate.Limit(5),
		5,
	)

	manager.Cleanup(30 * time.Minute)

	if len(manager.clients) != 1 {

		t.Fatal("active client should not be removed")

	}
}