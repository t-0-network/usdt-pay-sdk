package acquirer

import (
	"context"
	"log"

	"connectrpc.com/connect"
	"github.com/t-0-network/usdt-pay-sdk/go/sdk/gen/tzero/v1/pay/acquirer"

	"github.com/t-0-network/usdt-pay-sdk/go/starter/acquirer/internal"
)

// AcquirerCallbackHandler implements the five callbacks t-0 pushes to the
// acquirer. Every one of these is delivered at least once — t-0 retries with
// backoff until you acknowledge. Each method must write the event under its
// dedup key before returning.
type AcquirerCallbackHandler struct{}

func NewAcquirerCallbackHandler() *AcquirerCallbackHandler {
	return &AcquirerCallbackHandler{}
}

func (h *AcquirerCallbackHandler) PaymentAuthorized(
	ctx context.Context,
	req *connect.Request[acquirer.PaymentAuthorizedRequest],
) (*connect.Response[acquirer.PaymentAuthorizedResponse], error) {
	r := req.Msg
	log.Printf("PaymentAuthorized: intent=%d sale=%s settlement=%s USDt approvedAt=%s",
		r.GetPaymentIntentId(),
		r.GetPaymentRef(),
		internal.DecimalToString(r.GetSettlementAmount()),
		internal.FormatTimestamp(r.GetApprovedAt()))

	// TODO: Step 3.1 — dedup on paymentIntentId, mark the sale authorized, tell
	//       the POS. Write it before returning; the ack stops the retries.

	return connect.NewResponse(&acquirer.PaymentAuthorizedResponse{}), nil
}

func (h *AcquirerCallbackHandler) SettlementInitiated(
	ctx context.Context,
	req *connect.Request[acquirer.SettlementInitiatedRequest],
) (*connect.Response[acquirer.SettlementInitiatedResponse], error) {
	r := req.Msg
	log.Printf("SettlementInitiated: fiatSettlementId=%d lp=%d ref=%s %s %s acquirerId=%d covering %v",
		r.GetFiatSettlementId(),
		r.GetLpId(),
		r.GetBankTransferRef(),
		internal.DecimalToString(r.GetLocal().GetValue()),
		r.GetLocal().GetCurrency(),
		r.GetAcquirerId(),
		r.GetSettledPaymentIntentIds())

	// TODO: Step 3.2 — dedup on fiatSettlementId, then record (lpId, bankTransferRef)
	//       as a transfer to watch for on the bank statement.

	return connect.NewResponse(&acquirer.SettlementInitiatedResponse{}), nil
}

func (h *AcquirerCallbackHandler) SettlementCompleted(
	ctx context.Context,
	req *connect.Request[acquirer.SettlementCompletedRequest],
) (*connect.Response[acquirer.SettlementCompletedResponse], error) {
	r := req.Msg
	log.Printf("SettlementCompleted: settlementId=%d amount=%s acquirerId=%d covering %v",
		r.GetSettlementId(),
		internal.DecimalToString(r.GetSettlementAmount()),
		r.GetAcquirerId(),
		r.GetSettledPaymentIntentIds())

	// USDt only — leave as a no-op in fiat mode. Dedup on settlementId, then
	// close out every intent in settledPaymentIntentIds.

	return connect.NewResponse(&acquirer.SettlementCompletedResponse{}), nil
}

func (h *AcquirerCallbackHandler) PaymentExpired(
	ctx context.Context,
	req *connect.Request[acquirer.PaymentExpiredRequest],
) (*connect.Response[acquirer.PaymentExpiredResponse], error) {
	r := req.Msg
	log.Printf("PaymentExpired: intent=%d sale=%s at %s",
		r.GetPaymentIntentId(),
		r.GetPaymentRef(),
		internal.FormatTimestamp(r.GetExpiredAt()))

	// TODO: Step 3.4 — dedup on paymentIntentId, cancel the pending sale, take the
	//       QR off the POS.

	return connect.NewResponse(&acquirer.PaymentExpiredResponse{}), nil
}

func (h *AcquirerCallbackHandler) PaymentFailed(
	ctx context.Context,
	req *connect.Request[acquirer.PaymentFailedRequest],
) (*connect.Response[acquirer.PaymentFailedResponse], error) {
	r := req.Msg
	log.Printf("PaymentFailed: intent=%d sale=%s amount=%s disposition=%s",
		r.GetPaymentIntentId(),
		r.GetPaymentRef(),
		internal.DecimalToString(r.GetAmountUsdt()),
		r.GetDisposition().String())

	// TODO: Step 3.5 — dedup on paymentIntentId, cancel the pending sale, take the QR off the
	//   POS, and tell the customer what to expect based on disposition (RETURNED_TO_SENDER
	//   or RETAINED_BY_ISSUER).

	return connect.NewResponse(&acquirer.PaymentFailedResponse{}), nil
}
