package config

import (
	"os"
	"github.com/joho/godotenv"
)

// Config holds all runtime configuration for the API Gateway,
// loaded from environment variables.
type Config struct {
	AppName string

	Port string

	OTelEndpoint string

	JWTSecret string

	JWTIssuer string

	AccessTokenExpire string

	AuthService string

	ProjectService string

	MemberService string

	ChatService string

	NotificationService string
}

// Load reads environment variables (via a .env file) into a Config.
func Load() (*Config, error) {	

	// Load .env into the process environment before reading values.
	err := godotenv.Load("configs/.env")

	if err != nil {
		return nil, err
	}

	cfg := &Config{
		AppName: os.Getenv("APP_NAME"),

		Port: os.Getenv("PORT"),

		OTelEndpoint: os.Getenv("OTEL_ENDPOINT"),

		JWTSecret: os.Getenv("JWT_SECRET"),

		JWTIssuer: os.Getenv("JWT_ISSUER"),

		AccessTokenExpire: os.Getenv("ACCESS_TOKEN_EXPIRE"),

		AuthService: os.Getenv("AUTH_SERVICE"),

		ProjectService: os.Getenv("PROJECT_SERVICE"),

		MemberService: os.Getenv("MEMBER_SERVICE"),

		ChatService: os.Getenv("CHAT_SERVICE"),

		NotificationService: os.Getenv("NOTIFICATION_SERVICE"),
	}
	
	return cfg, nil
}