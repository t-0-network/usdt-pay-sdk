package internal

import (
	"context"
	"log"

	"connectrpc.com/connect"
	pay "github.com/t-0-network/usdt-pay-sdk/go/sdk/gen/tzero/v1/pay"
	"github.com/t-0-network/usdt-pay-sdk/go/sdk/gen/tzero/v1/pay/acquirer"
	"github.com/t-0-network/usdt-pay-sdk/go/sdk/gen/tzero/v1/pay/acquirer/acquirerconnect"
)

// CreateIntent opens a payment intent for a sale. Idempotency key: mint it when
// the sale is created and persist it with the sale — a fresh key on a retry opens
// a second intent for one sale.
func CreateIntent(
	ctx context.Context,
	t0 acquirerconnect.AcquirerServiceClient,
	paymentRef, idempotencyKey string,
	localCurrency string,
	localAmount *pay.Decimal,
	quoteID uint64,
) Outcome[*acquirer.CreatePaymentIntentResponse_Success] {
	resp, err := t0.CreatePaymentIntent(ctx, connect.NewRequest(&acquirer.CreatePaymentIntentRequest{
		PaymentRef:     paymentRef,
		IdempotencyKey: idempotencyKey,
		Amount: &acquirer.CreatePaymentIntentRequest_Local{
			Local: &acquirer.LocalAmount{
				Value:    localAmount,
				Currency: localCurrency,
			},
		},
		SettlementQuote: &acquirer.CreatePaymentIntentRequest_QuoteId{
			QuoteId: quoteID,
		},
	}))
	if err != nil {
		log.Printf("CreatePaymentIntent failed for sale %s: %v", paymentRef, err)
		return Unknown[*acquirer.CreatePaymentIntentResponse_Success]{Detail: err.Error()}
	}

	msg := resp.Msg
	switch msg.GetResult().(type) {
	case *acquirer.CreatePaymentIntentResponse_Success_:
		s := msg.GetSuccess()
		log.Printf("Intent %d for sale %s: %s USDt, expires at %s",
			s.GetPaymentIntentId(),
			paymentRef,
			DecimalToString(s.GetSettlementAmount()),
			FormatTimestamp(s.GetExpiresAt()))

		if fiat := s.GetFiat(); fiat != nil {
			log.Printf("  fiat settlement: %s %s at rate %s, quoteId=%d",
				DecimalToString(fiat.GetLocal().GetValue()),
				fiat.GetLocal().GetCurrency(),
				DecimalToString(fiat.GetFxRate()),
				fiat.GetQuoteId())
		}

		// TODO: Step 2.2 — store paymentIntentId against your sale, then render
		//       one deposit option per chain. Build the QR from the option and the
		//       settlement amount; the wallet URI carries settlementAmount × 10^tokenDecimals,
		//       scaled by that option's own decimals.
		if usdt := s.GetUsdtOnChain(); usdt != nil {
			for _, opt := range usdt.GetDepositOptions() {
				log.Printf("  deposit option — chain=%s address=%s contract=%s decimals=%d",
					opt.GetChain(), opt.GetDepositAddress(),
					opt.GetTokenContract(), opt.GetTokenDecimals())
			}
		}
		return Accepted[*acquirer.CreatePaymentIntentResponse_Success]{Payload: s}

	case *acquirer.CreatePaymentIntentResponse_Failure_:
		reason := msg.GetFailure().GetReason().String()
		log.Printf("Intent for sale %s declined: %s", paymentRef, reason)
		return Rejected[*acquirer.CreatePaymentIntentResponse_Success]{Reason: reason}

	default:
		return Unknown[*acquirer.CreatePaymentIntentResponse_Success]{Detail: "response carried an unrecognised result variant"}
	}
}
