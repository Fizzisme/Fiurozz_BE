# API Gateway Code Review

- Ngày review: 2026-07-23
- Phạm vi: toàn bộ 37 file Go trong `api-gateway`, `go.mod`, `go.sum`, cấu hình môi trường và thư mục `deploy/`
- Cách kiểm tra: đọc code, compile/test từng phần, kiểm tra Git/Compose, chạy `gofmt` và `go mod tidy` ở chế độ không ghi, rồi đối chiếu tài liệu chính thức
- Giới hạn: review này chỉ tạo tài liệu; chưa sửa source code, secret hay lịch sử Git

## Kết luận

Gateway có nền tảng tốt cho một service nhỏ: tách package rõ, có graceful shutdown, timeout, request ID, structured log, metrics, tracing, retry, circuit breaker và cleanup rate limiter. Tuy nhiên phiên bản hiện tại **chưa production-ready**.

Ba việc P0 phải xử lý ngay là secret đang nằm trong file được Git track, access token bị in nguyên văn và header định danh/rate-limit key có thể bị client giả mạo. Ngoài ra bộ test hiện không build; test circuit breaker panic; proxy dùng API `Director` đã bị Go đánh dấu deprecated/insecure; JWT, trusted proxies, config validation và telemetry propagation đều chưa đủ chặt.

Mức ưu tiên dùng trong tài liệu:

- **P0 — Khẩn cấp:** có khả năng làm lộ credential hoặc vượt trust boundary.
- **P1 — Trước khi merge/deploy:** lỗi build, hành vi sai hoặc thiếu kiểm soát quan trọng.
- **P2 — Nên làm sớm:** độ tin cậy, observability, portability và maintainability.

## Phát hiện chi tiết

### AGW-001 — P0: `.env` chứa secret đang được Git theo dõi

**Bằng chứng:** `git ls-files configs/.env` trả về `configs/.env`. Kiểm tra chỉ theo trạng thái cho thấy `JWT_SECRET` đã có giá trị, không rỗng; review không in hoặc sao chép giá trị đó.

**Tác động:** nếu repository từng được push, backup hoặc chia sẻ, phải coi secret là đã bị lộ. Xóa file trong commit mới không xóa secret khỏi lịch sử.

**Cần làm:** rotate secret trước; bỏ file khỏi Git index; thêm `configs/.env` vào `.gitignore`; tạo `configs/.env.example` chỉ có placeholder; dùng secret manager/CI secret cho môi trường deploy. Nếu cần làm sạch lịch sử, phối hợp với tất cả người dùng repository trước khi rewrite history.

### AGW-002 — P0: access token bị ghi nguyên văn ra stdout

**Bằng chứng:** [`internal/middleware/jwt.go:34`](internal/middleware/jwt.go#L34) gọi `fmt.Println("Token received:", token)`.

**Tác động:** bearer token có thể xuất hiện trong terminal, CI log, Docker log hoặc log aggregation; ai đọc được log có thể replay token còn hạn.

**Cần làm:** xóa hoàn toàn dòng log này. Không hash một access token rồi log mặc định; nếu cần debug chỉ log request ID và kết quả verification, không log credential.

### AGW-003 — P0: client có thể spoof identity header và né rate limit

**Bằng chứng theo luồng code:** 

1. [`internal/router/builder.go:32`](internal/router/builder.go#L32) gắn rate-limit middleware trước JWT ở dòng 43.
2. [`internal/helper/rate_limit.go:15`](internal/helper/rate_limit.go#L15) lấy rate-limit key trực tiếp từ request header `X-User-ID`.
3. [`internal/middleware/jwt.go:22`](internal/middleware/jwt.go#L22) cho request không có `Authorization` đi tiếp mà không xóa `X-User-ID`, `X-User-Email`, `X-User-Roles`.

**Tác động:** client có thể đổi `X-User-ID` theo từng request để nhận bucket mới. Cùng các header giả này còn được proxy xuống backend khi request không có JWT. Ngay cả request có JWT, rate limiter vẫn đọc header giả trước khi JWT middleware ghi đè.

**Cần làm:** đặt một middleware trust-boundary đầu route để luôn xóa các identity header do client gửi; verify JWT trước; lưu identity đã xác thực trong Gin context; rate-limit dựa trên claims/context thay vì header. Route public phải dùng client IP đã được xác định qua trusted-proxy config. Thêm test chứng minh spoofed header không ảnh hưởng key và không tới upstream.

### AGW-004 — P1: package test không build

**Bằng chứng:** `go test -run '^$' ./...` fail tại 5 lời gọi trong [`internal/proxy/reverse_proxy_test.go`](internal/proxy/reverse_proxy_test.go). Các test vẫn gọi `New(target, prefix, timeout)` trong khi hàm hiện yêu cầu thêm breaker, retry config và service name.

**Tác động:** CI không thể chạy; regression của proxy, timeout, header và query forwarding không được bảo vệ.

**Cần làm:** tạo test helper dựng breaker/retry config hợp lệ hoặc đổi constructor sang options/default an toàn. Sau đó chạy cả `go test ./...` và `go test -race ./...`.

### AGW-005 — P1: test circuit breaker panic vì metrics toàn cục chưa init

**Bằng chứng:** `go test ./internal/resilience` panic `nil pointer dereference` tại [`internal/resilience/breaker.go:45`](internal/resilience/breaker.go#L45). `OnStateChange` sử dụng `metrics.BreakerState`/`BreakerStateChangeTotal` toàn cục nhưng test không gọi `metrics.Init()`.

**Tác động:** package phụ thuộc vào thứ tự khởi tạo ẩn. `main` hiện gọi `metrics.Init()` trước `NewBreaker()`, nhưng unit test hoặc code tái sử dụng package có thể panic.

**Cần làm:** inject metrics recorder vào resilience layer hoặc cung cấp no-op recorder; tránh biến collector toàn cục nullable. Test phải dùng registry riêng để không gặp duplicate registration.

### AGW-006 — P1: chính sách JWT chưa chặt và trạng thái public/protected không rõ

**Bằng chứng:** [`internal/middleware/jwt.go:22`](internal/middleware/jwt.go#L22) xem thiếu token là hợp lệ; mọi proxy route đều dùng cùng middleware. [`internal/auth/jwt.go`](internal/auth/jwt.go) chỉ kiểm tra “một HMAC method” thay vì đúng algorithm, kiểm tra issuer thủ công và không bắt buộc `exp`.

Theo tài liệu `jwt/v5`, `exp` là optional mặc định; package có sẵn `WithExpirationRequired`, `WithValidMethods`, `WithIssuer` và `WithAudience`: [`golang-jwt/jwt/v5` parser options](https://pkg.go.dev/github.com/golang-jwt/jwt/v5#ParserOption).

**Tác động:** token không có hạn dùng có thể được chấp nhận; token dùng HMAC algorithm khác policy dự kiến có thể qua bước method check; route mới dễ vô tình trở thành public.

**Cần làm:** cấu hình rõ algorithm (ví dụ chỉ `HS256` nếu đó là contract), issuer, required expiration, clock leeway và audience nếu có; định nghĩa `AuthMode` cho từng route (`public`, `optional`, `required`) và role policy. Dùng `RegisteredClaims.Subject` làm user ID thay vì khai báo thêm field JSON `sub`. Xóa hoặc triển khai đúng `ACCESS_TOKEN_EXPIRE` đang unused.

### AGW-007 — P1: reverse proxy dùng `Director` deprecated/insecure

**Bằng chứng:** [`internal/proxy/reverse_proxy.go:55`](internal/proxy/reverse_proxy.go#L55) bọc `NewSingleHostReverseProxy` rồi sửa `rp.Director`.

Tài liệu chuẩn Go đánh dấu `Director` deprecated vì client có thể tác động hop-by-hop header và các `X-Forwarded-*` inbound được giữ mặc định, tạo rủi ro spoofing. API thay thế là `Rewrite`, `ProxyRequest.SetURL` và `SetXForwarded`: [Go `httputil.ReverseProxy`](https://pkg.go.dev/net/http/httputil#ReverseProxy).

**Cần làm:** chuyển sang `Rewrite`; xóa forwarding headers từ nguồn không tin cậy rồi tạo lại từ request đã tin cậy; test `X-Forwarded-For`, target có base path, escaped path và query bất thường.

### AGW-008 — P1: chưa cấu hình trusted proxies cho Gin

**Bằng chứng:** [`internal/router/router.go`](internal/router/router.go) tạo `gin.New()` nhưng không gọi `SetTrustedProxies`; `c.ClientIP()` lại được dùng cho log và rate limiting.

Gin cảnh báo attacker có thể giả `X-Forwarded-For` để vượt IP-based access control/rate limit nếu proxy trust không được cấu hình: [Gin Security Best Practices](https://gin-gonic.com/en/docs/middleware/security-guide/).

**Cần làm:** nếu gateway nhận trực tiếp thì không trust proxy; nếu đứng sau load balancer thì chỉ trust CIDR/IP của proxy đó. Thêm integration test với forged `X-Forwarded-For`.

### AGW-009 — P1: config bắt buộc `.env` nhưng không validate giá trị

**Bằng chứng:** [`internal/config/config.go:38`](internal/config/config.go#L38) trả lỗi ngay nếu `configs/.env` không tồn tại. Sau khi load, code không kiểm tra `PORT`, `JWT_SECRET`, `JWT_ISSUER`, `OTEL_ENDPOINT` hay service URL. `url.Parse("")` cũng không trả lỗi, nên target rỗng có thể lọt qua startup.

**Tác động:** container/CI inject env chuẩn vẫn không chạy nếu thiếu file; config sai chỉ lộ ra muộn khi nhận traffic; secret rỗng/yếu có thể được dùng.

**Cần làm:** chỉ load dotenv tùy chọn cho local; coi process env/secret injection là nguồn chính; validate required fields, port, duration và URL `http/https` có host; tách config theo environment; bỏ field service chưa dùng hoặc đăng ký route tương ứng.

### AGW-010 — P1: fresh checkout không khởi động nếu thiếu thư mục `logs/`

**Bằng chứng:** [`internal/logger/logger.go`](internal/logger/logger.go) mở `logs/gateway.log` bằng `os.OpenFile`, nhưng không tạo parent directory. `logs/` đang bị ignore và không tồn tại trong checkout được review.

**Tác động:** `logger.Init()` fail ngay khi chạy service lần đầu.

**Cần làm:** ưu tiên log ra stdout trong container; nếu vẫn cần file local thì `MkdirAll` trước khi mở và đóng file đúng cách. README đã ghi workaround tạo `logs/` để phản ánh hành vi hiện tại.

### AGW-011 — P1: circuit breaker không coi upstream 5xx là failure

**Bằng chứng:** `RetryTransport` retry `500/502/503/504`, nhưng `BreakerTransport` trả `(*http.Response, nil)` cho các response này. `gobreaker` vì thế ghi nhận success; breaker chỉ tăng failure khi transport trả error. Do retry nằm ngoài breaker, mỗi retry do network error còn được tính như một breaker request riêng.

**Tác động:** backend liên tục trả 5xx vẫn không làm breaker mở, trái với ý nghĩa bảo vệ backend mà comment/config hiện mô tả.

**Cần làm:** xác định rõ failure policy, chuyển status cần thiết thành breaker failure mà vẫn đóng/forward response đúng cách, làm rõ thứ tự retry/breaker và thêm test cho 5xx, timeout, open/half-open, context cancellation.

### AGW-012 — P2: request log luôn thiếu user ID đã xác thực

**Bằng chứng:** [`internal/middleware/logger.go`](internal/middleware/logger.go) đọc `ContextClaims` trước `c.Next()`. JWT middleware là route middleware nằm phía sau global Logger nên claims chưa tồn tại ở thời điểm đó.

**Tác động:** trường `user_id` trong log rỗng dù JWT hợp lệ, làm giảm khả năng điều tra sự cố.

**Cần làm:** lấy claims sau `c.Next()` hoặc đổi thiết kế middleware; bổ sung trace/span ID vào structured log. Tránh log query string nguyên văn nếu query có thể chứa token/PII.

### AGW-013 — P2: distributed tracing chưa nối đầy đủ qua gateway

**Bằng chứng:** [`internal/tracing/tracer.go`](internal/tracing/tracer.go) set tracer provider nhưng không set global `TextMapPropagator`. Outbound proxy transport không được bọc bằng `otelhttp.Transport`. `AlwaysSample()` và `WithInsecure()` bị hard-code.

OpenTelemetry hướng dẫn set `TraceContext` + `Baggage` propagator và instrument HTTP boundaries: [OpenTelemetry Go Getting Started](https://opentelemetry.io/docs/languages/go/getting-started/).

**Tác động:** inbound `traceparent` có thể không được nối đúng, call từ gateway sang backend không có client span/propagation, còn production có thể sample 100% và gửi plaintext ngoài ý muốn.

**Cần làm:** set propagator, bọc outbound transport bằng `otelhttp.NewTransport`, cấu hình sampler/TLS bằng environment, thêm deployment attributes và ghi nhận lỗi shutdown/export.

### AGW-014 — P2: stack observability dùng component EOL và image không pin version

**Bằng chứng:** `deploy/docker-compose.yml` dùng `jaegertracing/all-in-one:1.71.0`, `grafana/promtail:3.0.0`, cùng `prom/prometheus:latest` và `grafana/grafana:latest`. `docker compose ... config` hợp lệ nhưng cảnh báo top-level `version` obsolete.

- Promtail đã EOL từ 2026-03-02 và Grafana yêu cầu chuyển sang Alloy: [Grafana Promtail documentation](https://grafana.com/docs/loki/latest/send-data/promtail/).
- Jaeger v1 đã EOL từ 2025-12-31: [Jaeger downloads](https://www.jaegertracing.io/download/).
- Docker xác nhận top-level `version` chỉ còn tính thông tin và đã obsolete: [Compose `version` element](https://docs.docker.com/reference/compose-file/version-and-name/).

**Tác động:** không còn security/support update, build không tái lập do mutable tag, networking/data persistence khác nhau theo máy.

**Cần làm:** migrate Promtail sang Grafana Alloy; Jaeger sang v2 hoặc OTel Collector + backend; pin image bằng version/digest; bỏ `version`; thêm healthcheck, volume và network rõ ràng; ghi điều kiện Docker host networking hoặc dùng `host.docker.internal`/service network phù hợp.

### AGW-015 — P2: format và module metadata chưa sạch

**Bằng chứng:** `gofmt -l .` liệt kê cả 37 file Go. `go mod tidy -diff` cho thấy toàn bộ direct dependency đang bị đánh dấu `// indirect` và `go.sum` có entry thừa.

**Tác động:** diff khó review, import layout không chuẩn, dependency intent không rõ.

**Cần làm:** chạy `gofmt -w .`, `go mod tidy`, review diff và thêm CI gate cho format, test, vet. Không gộp thay đổi dependency/format lớn với fix bảo mật nếu muốn review dễ hơn.

### AGW-016 — P2: backlog kiến trúc và vận hành

- Chỉ Auth Service được register; Project/Member/Chat/Notification config chưa dùng.
- `/health` chỉ là liveness; chưa có readiness kiểm tra cấu hình/dependency.
- Rate limiter nằm trong memory nên không nhất quán khi scale nhiều instance; key cũng không chứa route/policy, nên route đầu tiên có thể quyết định limiter nếu sau này nhiều route dùng cùng user/IP.
- `/metrics` public; cần giới hạn bằng private network hoặc auth phù hợp.
- `Route.Burst`, `Route.Timeout`, role helper và một số context constants chưa dùng.
- Chưa có test cho auth, middleware order, config, router, metrics, tracing và graceful shutdown.

## Kết quả lệnh kiểm tra

| Lệnh | Kết quả |
| --- | --- |
| `go version` | `go1.26.4 windows/amd64` |
| `go test -run '^$' ./...` | Fail: 5 lời gọi `proxy.New` thiếu 3 argument |
| `go test ./internal/resilience` | Fail: panic nil metrics trong breaker state change |
| `go test ./internal/ratelimit` | Pass |
| `gofmt -l .` | 37/37 file Go cần format |
| `go mod tidy -diff` | Có diff; lệnh không sửa file |
| `docker compose -f deploy/docker-compose.yml config` | Parse thành công; cảnh báo `version` obsolete |
| `git ls-files configs/.env` | File đang được track |

`go vet ./...` chưa có giá trị làm gate cho tới khi lỗi compile của test được sửa.

## Thứ tự sửa đề xuất

1. Rotate `JWT_SECRET`, ngừng track `.env`, xóa log token.
2. Thiết lập trust boundary cho identity header; JWT trước rate limit; cấu hình trusted proxies.
3. Sửa test constructor và loại bỏ coupling metrics toàn cục để toàn bộ test chạy được.
4. Siết JWT policy và public/protected route policy; chuyển proxy sang `Rewrite`.
5. Validate config, sửa logger startup, rồi thêm test auth/router/proxy/resilience.
6. Hoàn thiện tracing propagation, readiness và chiến lược rate limit khi scale.
7. Migrate stack EOL, pin image; cuối cùng chạy format/tidy và bật CI gate.

## Definition of done tối thiểu

```powershell
gofmt -w .
go mod tidy
go test ./...
go test -race ./...
go vet ./...
docker compose -f deploy/docker-compose.yml config
```

Ngoài các lệnh trên, cần có test hồi quy cho spoofed `X-User-*`/`X-Forwarded-For`, JWT thiếu `exp` hoặc sai algorithm/issuer, breaker trên 5xx, proxy timeout và config thiếu/sai.
