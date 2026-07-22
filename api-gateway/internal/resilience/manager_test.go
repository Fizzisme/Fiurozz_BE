package resilience
import (
	"testing"
	"net/http"
	"errors"
	"github.com/sony/gobreaker/v2"
)

func TestBreaker_OpenAfterFailures(t *testing.T){
	breaker := NewBreaker("auth")

	for i := 0; i < 5; i++ {

	_, _ = breaker.Execute(func() (*http.Response, error) {

		return nil, errors.New("boom")

	})

}
_, err := breaker.Execute(func() (*http.Response, error) {

	return &http.Response{
		StatusCode: http.StatusOK,
	}, nil

})
if !errors.Is(err, gobreaker.ErrOpenState) {

	t.Fatalf(
		"expected ErrOpenState, got %v",
		err,
	)

}
}