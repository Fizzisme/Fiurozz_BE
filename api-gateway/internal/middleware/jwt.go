package middleware

import (
	"net/http"
	"github.com/gin-gonic/gin"
	"github.com/fizzisme/api-gateway/internal/auth"
	"strings"
	"github.com/fizzisme/api-gateway/internal/constants"
	"fmt"
)

// JWTAuth returns a middleware that authenticates requests via a
// Bearer JWT. On success it stores the decoded claims in the Gin
// context and re-injects trusted user headers (for downstream
// services) after stripping any client-supplied ones. Requests
// without a valid token are rejected with 401.
func JWTAuth(jwtService *auth.JWTService) gin.HandlerFunc {
	return func(c *gin.Context) {

		authHeader := c.GetHeader("Authorization")

		if authHeader == "" {
			c.Next()
			return	
		}

		if !strings.HasPrefix(authHeader, "Bearer ") {
			c.AbortWithStatusJSON(http.StatusUnauthorized, gin.H{"error": "Authorization header format must be Bearer {token}"})
			return
		}

		token := strings.TrimPrefix(authHeader, "Bearer ")

		fmt.Println("Token received:", token)

		claims, err := jwtService.Verify(token)

		if err != nil {
			c.AbortWithStatusJSON(http.StatusUnauthorized, gin.H{"message": "Invalid access token"})
			return
		}

		// Make claims available to downstream Gin handlers/middleware
		// (e.g. Authorize) via the request context.
		c.Set(constants.ContextClaims, claims)

		// Strip any client-supplied identity headers first, so a
		// caller can't spoof another user by setting these directly.
		c.Request.Header.Del(constants.HeaderUserID)
		c.Request.Header.Del(constants.HeaderUserEmail)
		c.Request.Header.Del(constants.HeaderUserRoles)

		// Re-inject identity headers derived from the verified token,
		// so downstream proxied services can trust them.
		c.Request.Header.Set(constants.HeaderUserID, claims.UserID)
		c.Request.Header.Set(constants.HeaderUserEmail, claims.Email)
		c.Request.Header.Set(constants.HeaderUserRoles, strings.Join(claims.Roles, ","))

		c.Next()
	}
}