# Kế hoạch nâng cấp API Gateway

Tài liệu này mô tả các khả năng nên bổ sung sau khi sửa những lỗi hiện tại trong [CODE_REVIEW.md](CODE_REVIEW.md). Mục tiêu là đưa gateway từ reverse proxy đơn giản thành một gateway an toàn, dễ vận hành và có thể scale.

## Giai đoạn 0 — Tạo security baseline

Ưu tiên này phải hoàn thành trước khi deploy hoặc cho service khác tin tưởng gateway:

- Xóa token khỏi log; rotate secret đã từng nằm trong Git và chuyển secret sang secret manager/CI environment.
- Luôn xóa `X-User-ID`, `X-User-Email`, `X-User-Roles` do client gửi trước khi xử lý request.
- Xác thực JWT trước khi tạo rate-limit key; lấy user ID từ verified claims thay vì request header.
- Khai báo auth policy riêng cho từng route: `public`, `optional`, `required`, kèm roles/scopes nếu cần.
- Chỉ chấp nhận JWT algorithm đã chọn, bắt buộc `exp`, kiểm tra issuer/audience và hỗ trợ clock leeway nhỏ.
- Cấu hình chính xác trusted proxy CIDR/IP để không thể giả `X-Forwarded-For`.
- Chuyển reverse proxy từ `Director` sang `Rewrite`, đồng thời dựng lại forwarding headers từ nguồn đáng tin cậy.
- Đặt giới hạn request body/header và bảo vệ `/metrics` bằng private network hoặc authentication phù hợp.

**Tiêu chí hoàn thành:** không còn secret trong Git/log; test spoofed identity và forwarded IP đều pass; route protected không thể truy cập khi thiếu token.

## Giai đoạn 1 — Production-ready cơ bản

### Routing và policy theo cấu hình

- Chuyển cấu hình route ra file hoặc typed config thay vì hard-code trong `main.go`.
- Cho mỗi route cấu hình được upstream URL, prefix rewrite, method, auth mode, roles, timeout, retry và rate limit.
- Đăng ký Project, Member, Chat và Notification Service; fail-fast nếu có prefix trùng hoặc URL không hợp lệ.
- Hỗ trợ versioned route như `/api/v1/auth/*path` và một error response schema thống nhất.

Ví dụ cấu hình mục tiêu:

```yaml
routes:
  - name: auth
    prefix: /api/v1/auth
    upstream: http://auth-service:8081
    auth: optional
    timeout: 5s
    rate_limit:
      requests: 10
      window: 1m
      burst: 5
```

### Resilience theo từng upstream

- Dùng connection pool/transport dùng chung với timeout rõ ràng thay vì `http.Transport{}` mặc định rỗng.
- Chỉ retry method idempotent; thêm exponential backoff có jitter và tôn trọng request deadline.
- Cho circuit breaker tính cả nhóm status `5xx` đã chọn là failure; test đủ closed/open/half-open.
- Thêm bulkhead/concurrency limit để một backend chậm không chiếm hết tài nguyên gateway.
- Cấu hình timeout, retry budget và breaker riêng cho từng service; tránh retry storm.

### Health và shutdown

- Giữ `/health` làm liveness và thêm `/ready` kiểm tra config, dependency bắt buộc và trạng thái shutdown.
- Khi shutdown, ngừng nhận request mới, dừng cleanup worker, chờ request đang chạy và flush log/trace có timeout.
- Bổ sung `ReadHeaderTimeout`, `MaxHeaderBytes` và giới hạn body theo route.

**Tiêu chí hoàn thành:** fresh checkout chạy được bằng hướng dẫn README; config sai bị từ chối ngay khi startup; test/vet/race pass; health/readiness phản ánh đúng trạng thái service.

## Giai đoạn 2 — Scale nhiều instance

### Distributed rate limiting

- Chuyển limiter sang Redis hoặc một distributed store khi chạy nhiều replica.
- Tạo key theo `route + tenant + user`, fallback sang trusted client IP cho route public.
- Cho phép policy khác nhau theo endpoint, role hoặc subscription plan.
- Trả `Retry-After` và các rate-limit response header để client biết lúc thử lại.
- Theo dõi số request bị chặn và cảnh báo khi một route có tỷ lệ `429` bất thường.

### Service discovery và load balancing

- Hỗ trợ nhiều instance cho mỗi upstream qua DNS/service discovery.
- Cân bằng tải round-robin/least-connections tùy môi trường.
- Loại instance không healthy khỏi pool và đưa trở lại sau health probe thành công.
- Thêm canary/weighted routing để chuyển một phần traffic sang phiên bản mới.

### High availability

- Gateway phải stateless; không lưu session hoặc limiter quan trọng chỉ trong memory.
- Chạy tối thiểu nhiều replica sau load balancer, có readiness probe và graceful termination.
- Dùng timeout/retry budget nhất quán để tránh request amplification giữa các service.

**Tiêu chí hoàn thành:** tăng/giảm replica không làm reset quota; một upstream instance hỏng không làm toàn route gián đoạn; canary có thể rollback bằng config.

## Giai đoạn 3 — Observability và vận hành chủ động

- Propagate W3C `traceparent`/baggage từ client qua gateway tới backend bằng OpenTelemetry HTTP transport.
- Gắn `trace_id`, `span_id`, `request_id`, service và route vào structured log.
- Bổ sung RED metrics theo route/upstream: request rate, error rate và duration; thêm retry, timeout, breaker, active requests và rejected requests.
- Tạo Grafana dashboard cho tổng quan gateway và từng upstream.
- Định nghĩa SLI/SLO, ví dụ availability và p95/p99 latency; tạo alert cho error-rate, latency, breaker-open và retry spike.
- Chuyển Promtail sang Grafana Alloy, Jaeger v1 sang Jaeger v2 hoặc OpenTelemetry Collector; pin version/digest cho mọi image.
- Thêm retention và persistent volume phù hợp cho Prometheus, Grafana, Loki và tracing backend.

**Tiêu chí hoàn thành:** một request có thể được lần theo liên tục từ gateway tới backend; dashboard thể hiện sức khỏe từng route; alert có runbook xử lý.

## Giai đoạn 4 — Nền tảng API nâng cao

Các mục này chỉ cần triển khai khi có nhu cầu sản phẩm rõ ràng:

- JWT key rotation bằng asymmetric signing (`RS256`/`ES256`) và JWKS cache thay cho shared HMAC secret giữa nhiều service.
- Request/response transformation có giới hạn và được test; không cho chạy script tùy ý trong gateway.
- API composition/BFF cho use case thật sự cần tổng hợp nhiều backend, có concurrency và partial-failure policy.
- Response caching cho endpoint đọc phù hợp, hỗ trợ cache key, TTL, invalidation và chống cache dữ liệu riêng tư.
- Idempotency key cho API tạo tài nguyên hoặc thanh toán cần chống gửi trùng.
- API versioning, deprecation header và policy ngừng hỗ trợ phiên bản cũ.
- Developer portal/OpenAPI aggregation nếu nhiều team hoặc client cần khám phá API.
- Audit log riêng cho hành động bảo mật/quản trị; không trộn với access log thông thường.

## Cải thiện quy trình phát triển

- Thêm unit test cho auth, middleware order, config, router, proxy và resilience.
- Thêm integration test với upstream giả để kiểm tra timeout, retry, header sanitization và cancellation.
- Fuzz test JWT parsing, URL/path rewriting và proxy headers.
- Benchmark/load test để xác định throughput, p95/p99 latency, memory và connection-pool limit.
- Bật CI cho `gofmt`, `go mod tidy`, `go test`, race detector, `go vet`, linter và vulnerability scan.
- Dùng Docker image multi-stage, chạy non-root, read-only filesystem khi có thể và tạo SBOM cho bản release.

## Thứ tự triển khai khuyến nghị

| Thứ tự | Nhóm công việc | Kết quả mong muốn |
| --- | --- | --- |
| 1 | Security baseline | Đóng lỗ hổng trust boundary và credential |
| 2 | Test, config, startup | Gateway build/test được và fail-fast |
| 3 | Routing + resilience theo route | Thêm service mới mà không hard-code hoặc ảnh hưởng service khác |
| 4 | Distributed rate limit + HA | Scale nhiều replica nhất quán |
| 5 | Tracing, dashboard, SLO/alert | Phát hiện và điều tra sự cố nhanh |
| 6 | Canary, cache, API platform | Bổ sung khả năng nâng cao theo nhu cầu thực tế |
