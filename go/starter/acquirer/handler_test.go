package acquirer

import (
	"context"
	"testing"

	"connectrpc.com/connect"
	"github.com/t-0-network/usdt-pay-sdk/go/sdk/gen/tzero/v1/pay/acquirer"
)

func TestCallbacks_Respond(t *testing.T) {
	h := NewAcquirerCallbackHandler()
	ctx := context.Background()

	t.Run("PaymentAuthorized", func(t *testing.T) {
		resp, err := h.PaymentAuthorized(ctx, connect.NewRequest(&acquirer.PaymentAuthorizedRequest{
			PaymentIntentId: 1,
			PaymentRef:      "sale-1",
		}))
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if resp.Msg == nil {
			t.Fatal("response message is nil")
		}
	})

	t.Run("SettlementInitiated", func(t *testing.T) {
		resp, err := h.SettlementInitiated(ctx, connect.NewRequest(&acquirer.SettlementInitiatedRequest{
			FiatSettlementId: 1,
			LpId:             1,
			BankTransferRef:  "ref-1",
		}))
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if resp.Msg == nil {
			t.Fatal("response message is nil")
		}
	})

	t.Run("SettlementCompleted", func(t *testing.T) {
		resp, err := h.SettlementCompleted(ctx, connect.NewRequest(&acquirer.SettlementCompletedRequest{
			SettlementId: 1,
		}))
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if resp.Msg == nil {
			t.Fatal("response message is nil")
		}
	})

	t.Run("PaymentExpired", func(t *testing.T) {
		resp, err := h.PaymentExpired(ctx, connect.NewRequest(&acquirer.PaymentExpiredRequest{
			PaymentIntentId: 1,
			PaymentRef:      "sale-1",
		}))
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if resp.Msg == nil {
			t.Fatal("response message is nil")
		}
	})

	t.Run("PaymentFailed", func(t *testing.T) {
		resp, err := h.PaymentFailed(ctx, connect.NewRequest(&acquirer.PaymentFailedRequest{
			PaymentIntentId: 1,
			PaymentRef:      "sale-1",
		}))
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if resp.Msg == nil {
			t.Fatal("response message is nil")
		}
	})
}
