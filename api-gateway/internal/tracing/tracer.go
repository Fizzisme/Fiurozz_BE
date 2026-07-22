package tracing

import (
	"context"
	"time"

	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracegrpc"
	"go.opentelemetry.io/otel/sdk/resource"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	semconv "go.opentelemetry.io/otel/semconv/v1.37.0"
)

func InitTracer(
	ctx context.Context,
	serviceName string,
	endpoint string,
) (func(context.Context) error, error) {
	resource, err := resource.New(
		ctx,
		resource.WithAttributes(
			semconv.ServiceName(serviceName),
		),
	) 

	if err != nil {
		return nil, err
	}

	exporter, err := otlptracegrpc.New(
		ctx,
		otlptracegrpc.WithEndpoint(endpoint),

		otlptracegrpc.WithInsecure(), // dev evironment

		// otlptracegrpc.WithTLSCredentials(...) // prod environment
	)

	if err != nil {
		return nil, err
	}

	provider := sdktrace.NewTracerProvider(
		sdktrace.WithSampler(sdktrace.AlwaysSample()),
		sdktrace.WithBatcher(
			exporter,
			sdktrace.WithBatchTimeout(500*time.Millisecond),
		),
		sdktrace.WithResource(resource),
	)

	otel.SetTracerProvider(provider)

	return provider.Shutdown, nil
}