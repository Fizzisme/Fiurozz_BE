package middleware

import (
	"github.com/gin-gonic/gin"
	"github.com/google/uuid"
	"github.com/fizzisme/api-gateway/internal/constants"
)


// RequestID returns a middleware that generates a unique ID for each
// incoming request, storing it in the Gin context for use by other
// middleware/handlers (e.g. Logger) and echoing it back to the client
// via a response header for correlation/debugging.
func RequestID() gin.HandlerFunc {

	return func(c *gin.Context) {

		requestID := c.GetHeader(constants.HeaderRequestID)

		if requestID == "" {
			requestID = uuid.NewString()
		}

		c.Set(constants.ContextRequestID, requestID)

		c.Request.Header.Set(constants.HeaderRequestID, requestID)

		c.Writer.Header().Set(constants.HeaderRequestID, requestID)

		c.Next()
	}
}