package logger

import (
	"os"
	"go.uber.org/zap"
	"go.uber.org/zap/zapcore"
)


// Log is the global structured logger used across the application.
// It must be initialized via Init() before use.
var Log *zap.Logger


// Init configures the global Log with two outputs:
//   - stdout: human-readable console format, for local dev / container logs
//   - logs/gateway.log: JSON format, for log shippers like Loki/Promtail
//
// Both outputs are combined via zapcore.NewTee so every log call
// writes to both destinations simultaneously.
func Init() error {

	// JSON encoder config, tuned for log-aggregator parsing (e.g. Loki):
	// uses "ts" as the timestamp key with Unix epoch encoding instead
	// of the default RFC3339 string.
	encoderConfig := zap.NewProductionEncoderConfig()
	encoderConfig.TimeKey = "ts"
	encoderConfig.EncodeTime = zapcore.EpochTimeEncoder

	jsonEncoder := zapcore.NewJSONEncoder(encoderConfig)

	// Console encoder: colored, human-friendly format for local development.
	consoleEncoder := zapcore.NewConsoleEncoder(zap.NewDevelopmentEncoderConfig())

	// Core writing console-formatted logs to stdout.
	consoleCore := zapcore.NewCore(
		consoleEncoder,
		zapcore.AddSync(os.Stdout),
		zap.InfoLevel,
	)

	
	// Open (or create) the log file in append mode so restarts don't
	// truncate prior logs.
	file, err := os.OpenFile(
		"logs/gateway.log",
		os.O_CREATE|os.O_APPEND|os.O_WRONLY,
		0644,
	)

	if err != nil {
		return err
	}

	fileCore := zapcore.NewCore(
		jsonEncoder,
		zapcore.AddSync(file),
		zap.InfoLevel,
	)

	core := zapcore.NewTee(
		consoleCore,
		fileCore,
	)

	Log = zap.New(
		core,
		zap.AddCaller(),
	)

	return nil
}