package config

import "time"

type RouteConfig struct {
    Name     string `yaml:"name"`
    Prefix   string `yaml:"prefix"`
    Upstream string `yaml:"upstream"`

    Timeout time.Duration `yaml:"timeout"`

    Retry RetryConfig `yaml:"retry"`

    Breaker BreakerConfig `yaml:"breaker"`

    RateLimit RateLimitConfig `yaml:"rate_limit"`
}

type RetryConfig struct {
    Attempts int           `yaml:"attempts"`
    BaseDelay time.Duration `yaml:"base_delay"`
    MaxDelay  time.Duration `yaml:"max_delay"`
}

type BreakerConfig struct {
    Enabled bool `yaml:"enabled"`
}

type RateLimitConfig struct {
    Requests int           `yaml:"requests"`
    Window   time.Duration `yaml:"window"`
    Burst    int           `yaml:"burst"`
}