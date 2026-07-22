package auth

import (
	"github.com/golang-jwt/jwt/v5"
)

type JWTService struct {
	secret []byte
	issuer string
}


func NewJWTService(
	secret string,
	issuer string,
) *JWTService {

	return &JWTService{
		secret: []byte(secret),
		issuer: issuer,
	}
}

// Verify parses and validates a JWT, checking the signing method,
// signature, and issuer, and returns the decoded claims if valid.
func (j *JWTService) Verify(
	tokenString  string,
) (*Claims, error) {

	claims := &Claims{}

	token, err := jwt.ParseWithClaims(
		tokenString,
		claims,
		func(token *jwt.Token) (interface{}, error) {
			// Reject tokens signed with an unexpected algorithm
			// (e.g. "none" or RSA) to prevent algorithm-confusion attacks.
			if _, ok := token.Method.(*jwt.SigningMethodHMAC); !ok {
				return nil, ErrInvalidToken
			}
			return j.secret, nil
		},
	)

	if err != nil {
		return nil, ErrInvalidToken
	}

	// ParseWithClaims can return a non-nil token with Valid == false
	// in some edge cases, so this check is checked explicitly.
	if !token.Valid {
		return nil, ErrInvalidToken
	}

	// Extra guard: make sure the token was issued by this service,
	// not by some other issuer using the same secret.
	if claims.Issuer != j.issuer {
		return nil, ErrInvalidToken
	}

	return claims, nil
}