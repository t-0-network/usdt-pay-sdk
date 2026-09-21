package internal

import (
	"context"
	"log"
	"time"

	"connectrpc.com/connect"
	pay "github.com/t-0-network/usdt-pay-sdk/go/sdk/gen/tzero/v1/pay"
	"github.com/t-0-network/usdt-pay-sdk/go/sdk/gen/tzero/v1/pay/acquirer"
	"github.com/t-0-network/usdt-pay-sdk/go/sdk/gen/tzero/v1/pay/acquirer/acquirerconnect"
)

const quoteTimeout = 5 * time.Second

// FetchQuote prices an upcoming fiat sale. Stateless lookup, no idempotency
// key: an Unknown here is safe to retry as often as you like.
func FetchQuote(
	ctx context.Context,
	t0 acquirerconnect.AcquirerServiceClient,
	localCurrency string,
	localAmount *pay.Decimal,
) Outcome[*acquirer.GetPaymentQuoteResponse_Success] {
	ctx, cancel := context.WithTimeout(ctx, quoteTimeout)
	defer cancel()

	resp, err := t0.GetPaymentQuote(ctx, connect.NewRequest(&acquirer.GetPaymentQuoteRequest{
		LocalCurrency: localCurrency,
		LocalAmount:   localAmount,
	}))
	if err != nil {
		log.Printf("GetPaymentQuote failed: %v", err)
		return Unknown[*acquirer.GetPaymentQuoteResponse_Success]{Detail: err.Error()}
	}

	msg := resp.Msg
	switch msg.GetResult().(type) {
	case *acquirer.GetPaymentQuoteResponse_Success_:
		s := msg.GetSuccess()
		log.Printf("Quote %d: %s %s costs %s USDt at rate %s, expires at %s",
			s.GetQuoteId(),
			DecimalToString(localAmount), localCurrency,
			DecimalToString(s.GetSettlementAmount()),
			DecimalToString(s.GetFxRate()),
			FormatTimestamp(s.GetExpiresAt()))
		return Accepted[*acquirer.GetPaymentQuoteResponse_Success]{Payload: s}

	case *acquirer.GetPaymentQuoteResponse_Failure_:
		reason := msg.GetFailure().GetReason().String()
		log.Printf("No quote for %s %s: %s", DecimalToString(localAmount), localCurrency, reason)
		return Rejected[*acquirer.GetPaymentQuoteResponse_Success]{Reason: reason}

	default:
		return Unknown[*acquirer.GetPaymentQuoteResponse_Success]{Detail: "response carried an unrecognised result variant"}
	}
}
