package helper

// HasAnyRole reports whether userRoles contains at least one role
// present in requiredRoles (logical OR match).
func HasAnyRole(
	userRoles []string,
	requiredRoles []string,
) bool{
	// O(n*m) nested scan; fine for small role lists like these.
	// If role lists grow large, consider a map[string]struct{} lookup instead.
	for _, userRole := range userRoles {
		for _, requiredRole := range requiredRoles {
			if userRole == requiredRole {
				return true
			}
		}
	}
	return false
}