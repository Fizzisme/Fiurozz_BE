package helper

import (
	"fmt"

	"github.com/fizzisme/api-gateway/internal/auth"
	"github.com/fizzisme/api-gateway/internal/constants"
	"github.com/gin-gonic/gin"
)

// BuildRateLimitKey returns the key used to bucket rate-limit state
// for a request. Authenticated requests are keyed by user ID (so a
// single user is limited consistently across IPs); unauthenticated
// requests fall back to client IP.
func BuildRateLimitKey(
	c *gin.Context,
) string {

	if value, ok := c.Get(constants.ContextClaims); ok {

        if claims, ok := value.(*auth.Claims); ok {
			fmt.Println("user: ", claims.Subject)

            return "user:" + claims.Subject
        }
    }

	return "ip:" + c.ClientIP()
}