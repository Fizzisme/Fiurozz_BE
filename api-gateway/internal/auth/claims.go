package auth

import (
	"github.com/golang-jwt/jwt/v5"
)

type Claims struct {

    Email string `json:"email"`

    Roles []string `json:"roles"`

    // Embeds standard JWT fields (exp, iat, iss, sub, etc.)
    jwt.RegisteredClaims
}