package auth

import "errors"

var (
	ErrMissingToken = errors.New("missing access token")

	ErrInvalidToken = errors.New("invalid access token")

	ErrExpiredToken = errors.New("access token expired")
)