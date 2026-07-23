# API Gateway

API Gateway của Fiurozz là service Go/Gin đứng trước các backend nội bộ. Phiên bản hiện tại đăng ký một route proxy cho Auth Service, đồng thời cung cấp request ID, JWT, rate limiting, retry, circuit breaker, Prometheus metrics, OpenTelemetry tracing và structured logging.

> Trạng thái: chưa nên dùng production. Bộ test hiện không build, cấu hình đang có rủi ro lộ secret và chuỗi middleware có thể bị spoof header định danh/rate-limit key. Xem [CODE_REVIEW.md](CODE_REVIEW.md) trước khi tích hợp hoặc deploy.

## Luồng request hiện tại

```text
Client
  -> Recovery -> Request ID -> OpenTelemetry -> Logger -> Metrics
  -> Rate limit -> JWT (đang là optional)
  -> Reverse proxy -> Auth Service
```

Thứ tự trên phản ánh đúng code hiện tại. Rate limit chạy trước JWT là một lỗi cần sửa, không phải kiến trúc được khuyến nghị.

## Endpoint

| Method | Path | Chức năng | Ghi chú |
| --- | --- | --- | --- |
| `GET` | `/health` | Liveness check | Luôn trả `{"status":"ok"}`; chưa kiểm tra dependency |
| `GET` | `/metrics` | Prometheus metrics | Hiện chưa có authentication/network restriction |
| `ANY` | `/auth/*path` | Proxy tới Auth Service | Bỏ prefix `/auth` trước khi forward |

Ví dụ: `/auth/login` được forward thành `/login` ở Auth Service.

JWT hiện là optional: request không có `Authorization` vẫn được proxy; header có dạng sai hoặc token không hợp lệ sẽ nhận `401`. Cơ chế này có thể phù hợp với login/register, nhưng cần cấu hình public/protected rõ ràng cho từng route trước khi thêm service khác.

## Yêu cầu

- Go `1.26.4` theo `go.mod`.
- Auth Service đang chạy và truy cập được từ gateway.
- OTLP gRPC receiver (ví dụ Jaeger hoặc OpenTelemetry Collector) nếu bật tracing.
- Thư mục `logs/` phải tồn tại vì logger hiện ghi vào `logs/gateway.log`.
- Docker Compose nếu muốn chạy stack quan sát cục bộ.

## Cấu hình

`config.Load()` hiện bắt buộc file `configs/.env` tồn tại, kể cả khi biến môi trường hệ điều hành đã được inject. Tạo file này chỉ ở máy local và không commit secret.

```dotenv
APP_NAME=api-gateway
PORT=8080
OTEL_ENDPOINT=localhost:4317
JWT_SECRET=<long-random-secret>
JWT_ISSUER=<issuer-used-by-auth-service>
ACCESS_TOKEN_EXPIRE=<currently-unused>
AUTH_SERVICE=http://localhost:<auth-service-port>
PROJECT_SERVICE=
MEMBER_SERVICE=
CHAT_SERVICE=
NOTIFICATION_SERVICE=
```

| Biến | Bắt buộc theo nghiệp vụ | Trạng thái sử dụng |
| --- | --- | --- |
| `APP_NAME` | Có | Tên log/trace middleware |
| `PORT` | Có | HTTP listen port |
| `OTEL_ENDPOINT` | Có khi bật tracing | OTLP gRPC endpoint |
| `JWT_SECRET` | Có | Xác minh JWT HMAC; phải dùng secret đủ dài và ngẫu nhiên |
| `JWT_ISSUER` | Có | So khớp claim `iss` |
| `ACCESS_TOKEN_EXPIRE` | Chưa | Được load nhưng chưa dùng |
| `AUTH_SERVICE` | Có | Backend duy nhất đang được đăng ký |
| `PROJECT_SERVICE` | Chưa | Được load nhưng chưa đăng ký route |
| `MEMBER_SERVICE` | Chưa | Được load nhưng chưa đăng ký route |
| `CHAT_SERVICE` | Chưa | Được load nhưng chưa đăng ký route |
| `NOTIFICATION_SERVICE` | Chưa | Được load nhưng chưa đăng ký route |

Repository hiện đang track `configs/.env`. Cần bỏ tracking, thêm `configs/.env.example` chỉ chứa placeholder và rotate `JWT_SECRET` nếu repository từng được chia sẻ hoặc push lên remote. Không chỉ xóa file ở commit mới vì secret có thể vẫn còn trong lịch sử Git.

## Chạy local

Từ thư mục `api-gateway`:

```powershell
New-Item -ItemType Directory -Force logs | Out-Null
go mod download
go run ./cmd/server
```

Kiểm tra nhanh:

```powershell
Invoke-RestMethod http://localhost:8080/health
Invoke-WebRequest http://localhost:8080/metrics
```

Thay `8080` nếu `PORT` có giá trị khác.

## Observability local

Stack trong `deploy/docker-compose.yml` gồm Prometheus, Grafana, Jaeger, Loki và Promtail.

```powershell
docker compose -f deploy/docker-compose.yml config
docker compose -f deploy/docker-compose.yml up -d
```

Các địa chỉ dự kiến khi Docker host networking đã được hỗ trợ/bật:

- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000`
- Jaeger UI: `http://localhost:16686`
- Loki: `http://localhost:3100`

Stack này chỉ phù hợp development: `network_mode: host` không portable trên mọi môi trường, dữ liệu chưa có volume bền vững, image Prometheus/Grafana dùng tag `latest`, Jaeger v1 đã EOL và Promtail đã EOL. Xem kế hoạch migration trong [CODE_REVIEW.md](CODE_REVIEW.md).

## Kiểm tra chất lượng

Các lệnh mục tiêu sau khi sửa các lỗi trong báo cáo:

```powershell
gofmt -w .
go mod tidy
go test ./...
go vet ./...
```

Kết quả audit ngày 2026-07-23:

- `go test -run '^$' ./...`: fail vì 5 lời gọi `proxy.New` trong `reverse_proxy_test.go` dùng chữ ký cũ.
- `go test ./internal/resilience`: panic vì metrics toàn cục chưa được khởi tạo.
- `go test ./internal/ratelimit`: pass.
- `gofmt -l .`: liệt kê toàn bộ 37 file Go.
- `go mod tidy -diff`: có diff; các dependency trực tiếp đang bị đánh dấu `// indirect` và `go.sum` còn entry thừa.
- `docker compose -f deploy/docker-compose.yml config`: hợp lệ nhưng cảnh báo thuộc tính `version` đã obsolete.

## Cấu trúc

```text
cmd/server/          Khởi tạo dependency và HTTP server
configs/             Cấu hình local (không được commit secret)
deploy/              Prometheus/Grafana/Jaeger/Loki/Promtail cho development
internal/auth/       JWT claims và verification
internal/config/     Load biến môi trường
internal/logger/     Zap logger
internal/metrics/    Prometheus collectors
internal/middleware/ Gin middleware
internal/proxy/      Route registry và reverse proxy
internal/ratelimit/  Token-bucket limiter theo client
internal/resilience/ Retry và circuit breaker transport
internal/router/     Khai báo route/middleware chain
internal/tracing/    OpenTelemetry tracer provider
```

## Tài liệu liên quan

- [Báo cáo code review và kế hoạch cải thiện](CODE_REVIEW.md)
- [Go `httputil.ReverseProxy`](https://pkg.go.dev/net/http/httputil#ReverseProxy)
- [Gin security best practices](https://gin-gonic.com/en/docs/middleware/security-guide/)
- [`golang-jwt/jwt/v5` parser options](https://pkg.go.dev/github.com/golang-jwt/jwt/v5#ParserOption)
- [OpenTelemetry Go getting started](https://opentelemetry.io/docs/languages/go/getting-started/)
