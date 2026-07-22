package helper

import (
	"github.com/gin-gonic/gin"
	"github.com/fizzisme/api-gateway/internal/constants"
)

// BuildRateLimitKey returns the key used to bucket rate-limit state
// for a request. Authenticated requests are keyed by user ID (so a
// single user is limited consistently across IPs); unauthenticated
// requests fall back to client IP.
func BuildRateLimitKey(
	c *gin.Context,
) string {
	userID := c.Request.Header.Get(constants.HeaderUserID)
	if userID != "" {
		return "userid:" + userID
	}
	return "ip:" + c.ClientIP()
}