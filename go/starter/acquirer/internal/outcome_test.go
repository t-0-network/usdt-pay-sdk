package internal

import (
	"testing"

	"github.com/t-0-network/usdt-pay-sdk/go/sdk/gen/tzero/v1/pay/acquirer"
)

func TestOutcome_Accepted(t *testing.T) {
	s := &acquirer.CreatePaymentIntentResponse_Success{PaymentIntentId: 42}
	o := Accepted[*acquirer.CreatePaymentIntentResponse_Success]{Payload: s}

	val, ok := o.Value()
	if !ok {
		t.Fatal("Accepted.Value() should return true")
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

	_, ok := o.Value()
	if ok {
		t.Fatal("Rejected.Value() should return false")
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

	_, ok := o.Value()
	if ok {
		t.Fatal("Unknown.Value() should return false")
	}
	if !o.ShouldRetry() {
		t.Fatal("Unknown.ShouldRetry() must be true — you do not know whether t-0 committed")
	}
}
