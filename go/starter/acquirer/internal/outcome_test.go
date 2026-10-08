package internal

import (
	"context"
	"errors"
	"fmt"
	"net/http/httptest"
	"testing"
	"time"

	"connectrpc.com/connect"
	pay "github.com/t-0-network/usdt-pay-sdk/go/sdk/gen/tzero/v1/pay"
	"github.com/t-0-network/usdt-pay-sdk/go/sdk/gen/tzero/v1/pay/acquirer"
	"github.com/t-0-network/usdt-pay-sdk/go/sdk/gen/tzero/v1/pay/acquirer/acquirerconnect"
)

func TestOutcome_Accepted(t *testing.T) {
	s := &acquirer.CreatePaymentIntentResponse_Success{PaymentIntentId: 42}
	o := Accepted[*acquirer.CreatePaymentIntentResponse_Success]{Value: s}

	val, ok := o.Accepted()
	if !ok {
		t.Fatal("Accepted.Accepted() should return true")
	}
	if val.GetPaymentIntentId() != 42 {
		t.Fatalf("got payment_intent_id=%d, want 42", val.GetPaymentIntentId())
	}
	if o.ShouldRetry() {
		t.Fatal("Accepted.ShouldRetry() should be false")
	}
}

func TestOutcome_Rejected(t *testing.T) {
	o := Rejected[*acquirer.CreatePaymentIntentResponse_Success]{Reason: "REASON_QUOTE_EXPIRED"}

	_, ok := o.Accepted()
	if ok {
		t.Fatal("Rejected.Accepted() should return false")
	}
	if o.ShouldRetry() {
		t.Fatal("Rejected.ShouldRetry() should be false — a rejection is an acknowledgment")
	}
	if o.Reason != "REASON_QUOTE_EXPIRED" {
		t.Fatalf("Reason = %q, want REASON_QUOTE_EXPIRED", o.Reason)
	}
}

func TestOutcome_Unknown(t *testing.T) {
	o := Unknown[*acquirer.CreatePaymentIntentResponse_Success]{Detail: "timeout"}

	_, ok := o.Accepted()
	if ok {
		t.Fatal("Unknown.Accepted() should return false")
	}
	if !o.ShouldRetry() {
		t.Fatal("Unknown.ShouldRetry() must be true — you do not know whether t-0 committed")
	}
}

func TestOutcomeFromError_PermanentCodesAreRejected(t *testing.T) {
	tests := []struct {
		code connect.Code
		want string
	}{
		{connect.CodeInvalidArgument, "invalid_argument: amount is required"},
		{connect.CodeUnauthenticated, "unauthenticated: amount is required"},
		{connect.CodePermissionDenied, "permission_denied: amount is required"},
		{connect.CodeUnimplemented, "unimplemented: amount is required"},
		{connect.CodeFailedPrecondition, "failed_precondition: amount is required"},
	}

	for _, tt := range tests {
		t.Run(tt.code.String(), func(t *testing.T) {
			err := fmt.Errorf("calling t-0: %w", connect.NewError(tt.code, errors.New("amount is required")))
			o := OutcomeFromError[*acquirer.GetPaymentQuoteResponse_Success](err)

			r, ok := o.(Rejected[*acquirer.GetPaymentQuoteResponse_Success])
			if !ok {
				t.Fatalf("got %T, want Rejected", o)
			}
			if r.Reason != tt.want {
				t.Errorf("Reason = %q, want %q", r.Reason, tt.want)
			}
			if o.ShouldRetry() {
				t.Error("ShouldRetry() should be false")
			}
		})
	}
}

func TestOutcomeFromError_OtherErrorsAreUnknown(t *testing.T) {
	tests := []struct {
		name string
		err  error
	}{
		{"canceled", connect.NewError(connect.CodeCanceled, context.Canceled)},
		{"unknown", connect.NewError(connect.CodeUnknown, errors.New("boom"))},
		{"deadline_exceeded", connect.NewError(connect.CodeDeadlineExceeded, context.DeadlineExceeded)},
		{"not_found", connect.NewError(connect.CodeNotFound, errors.New("no such quote"))},
		{"already_exists", connect.NewError(connect.CodeAlreadyExists, errors.New("exists"))},
		{"resource_exhausted", connect.NewError(connect.CodeResourceExhausted, errors.New("slow down"))},
		{"aborted", connect.NewError(connect.CodeAborted, errors.New("aborted"))},
		{"out_of_range", connect.NewError(connect.CodeOutOfRange, errors.New("out of range"))},
		{"internal", connect.NewError(connect.CodeInternal, errors.New("internal"))},
		{"unavailable", connect.NewError(connect.CodeUnavailable, errors.New("connection refused"))},
		{"data_loss", connect.NewError(connect.CodeDataLoss, errors.New("data loss"))},
		{"not a connect error", errors.New("dial tcp 127.0.0.1:1: connect: connection refused")},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			o := OutcomeFromError[*acquirer.GetPaymentQuoteResponse_Success](tt.err)

			u, ok := o.(Unknown[*acquirer.GetPaymentQuoteResponse_Success])
			if !ok {
				t.Fatalf("got %T, want Unknown", o)
			}
			if u.Detail != tt.err.Error() {
				t.Errorf("Detail = %q, want %q", u.Detail, tt.err.Error())
			}
			if !o.ShouldRetry() {
				t.Error("ShouldRetry() should be true")
			}
		})
	}
}

// Every RPC helper classifies a returned error with OutcomeFromError.
func TestHelpers_ClassifyErrors(t *testing.T) {
	ctx := context.Background()
	amount := &pay.Decimal{Unscaled: 100000}

	// A service that implements nothing answers every call with Unimplemented.
	_, handler := acquirerconnect.NewAcquirerServiceHandler(acquirerconnect.UnimplementedAcquirerServiceHandler{})
	server := httptest.NewServer(handler)
	defer server.Close()
	t0 := acquirerconnect.NewAcquirerServiceClient(server.Client(), server.URL)

	// Nothing listens here any more: every call fails in transport.
	closed := httptest.NewServer(handler)
	closed.Close()
	unreachable := acquirerconnect.NewAcquirerServiceClient(closed.Client(), closed.URL)

	t.Run("FetchQuote", func(t *testing.T) {
		requireRejected(t, FetchQuote(ctx, t0, "COP", amount),
			"unimplemented: tzero.v1.pay.acquirer.AcquirerService.GetPaymentQuote is not implemented")
		requireUnknown(t, FetchQuote(ctx, unreachable, "COP", amount))
	})
	t.Run("CreateIntent", func(t *testing.T) {
		requireRejected(t, CreateIntent(ctx, t0, "sale-1", "key-1", "COP", amount, 1),
			"unimplemented: tzero.v1.pay.acquirer.AcquirerService.CreatePaymentIntent is not implemented")
		requireUnknown(t, CreateIntent(ctx, unreachable, "sale-1", "key-1", "COP", amount, 1))
	})
	t.Run("ConfirmSettlement", func(t *testing.T) {
		requireRejected(t, ConfirmSettlement(ctx, t0, 1, "ref-1", "COP", amount, time.Now()),
			"unimplemented: tzero.v1.pay.acquirer.AcquirerService.SettlementReceived is not implemented")
		requireUnknown(t, ConfirmSettlement(ctx, unreachable, 1, "ref-1", "COP", amount, time.Now()))
	})
}

func requireRejected[T any](t *testing.T, o Outcome[T], reason string) {
	t.Helper()
	r, ok := o.(Rejected[T])
	if !ok {
		t.Fatalf("got %T, want Rejected", o)
	}
	if r.Reason != reason {
		t.Fatalf("Reason = %q, want %q", r.Reason, reason)
	}
}

func requireUnknown[T any](t *testing.T, o Outcome[T]) {
	t.Helper()
	if _, ok := o.(Unknown[T]); !ok {
		t.Fatalf("got %T, want Unknown", o)
	}
}
