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


	parser := jwt.NewParser(
		jwt.WithValidMethods([]string{"HS256"}),
		jwt.WithExpirationRequired(),
		jwt.WithIssuer(j.issuer),
	)

	token, err := parser.ParseWithClaims(
		tokenString,
		claims,
		func(token *jwt.Token) (interface{}, error) {
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

	return claims, nil
}