package middleware

import (
	"github.com/gin-gonic/gin"
	"github.com/fizzisme/api-gateway/internal/metrics"
	"time"
	"strconv"
)

// Metrics returns a middleware that records request count and
// latency into Prometheus, labeled by method, matched route pattern,
// and status code.
func Metrics() gin.HandlerFunc {

    return func(c *gin.Context) {

        start := time.Now()

		// Run the rest of the chain first so status and duration
		// reflect the final outcome of the request.
        c.Next()

		status := strconv.Itoa(c.Writer.Status())

		method := c.Request.Method

		// FullPath returns the matched route pattern (e.g.
		// "/auth/user/:id"), not the raw request path, so requests
		// with different dynamic segments don't create separate
		// time-series and blow up metric cardinality.
		path := c.FullPath()
		
		// FullPath is empty when no route matched (e.g. 404), so
		// group all of those under a single "unknown" label.
		if path == "" {
			path = "unknown"
		}

		metrics.RequestsTotal.
			WithLabelValues(
				method,
				path,
				status,
			).
			Inc()

		metrics.RequestDuration.
			WithLabelValues(
				method,
				path,
				status,
			).
			Observe(
				time.Since(start).Seconds(),
			)

    }

}