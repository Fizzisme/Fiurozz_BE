package proxy

import (
	"errors"
)

// Registry holds the set of registered routes (prefix -> backend
// proxy mapping) used to build the gateway's router.
type Registry struct {
	routes []Route
}

// NewRegistry creates an empty route registry.
func NewRegistry() *Registry {
	return &Registry{
		routes: make([]Route, 0),
	}
}

// Register adds a route to the registry. It only validates that a
// proxy is set; prefix collisions/ordering are not checked here.
func (r *Registry) Register(route Route) error {

	if route.Proxy == nil {
		return errors.New("proxy is required")
	}

	r.routes = append(
		r.routes,
		route,
	)

	return nil
}

// Routes returns all registered routes, in registration order.
func (r *Registry) Routes() []Route {
	return r.routes
}