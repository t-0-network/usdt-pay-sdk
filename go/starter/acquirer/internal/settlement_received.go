package internal

import (
	"context"
	"log"
	"time"

	"connectrpc.com/connect"
	pay "github.com/t-0-network/usdt-pay-sdk/go/sdk/gen/tzero/v1/pay"
	"github.com/t-0-network/usdt-pay-sdk/go/sdk/gen/tzero/v1/pay/acquirer"
	"github.com/t-0-network/usdt-pay-sdk/go/sdk/gen/tzero/v1/pay/acquirer/acquirerconnect"
	"google.golang.org/protobuf/types/known/timestamppb"
)

// ConfirmSettlement confirms that a fiat settlement landed in the acquirer's
// bank account. Fiat mode only. The pair (lpId, bankTransferRef) is the
// idempotency key — on Unknown, resend the same pair with identical content.
func ConfirmSettlement(
	ctx context.Context,
	t0 acquirerconnect.AcquirerServiceClient,
	lpID uint64,
	bankTransferRef string,
	localCurrency string,
	amountReceived *pay.Decimal,
	receivedAt time.Time,
) Outcome[*acquirer.SettlementReceivedResponse_Accepted] {
	resp, err := t0.SettlementReceived(ctx, connect.NewRequest(&acquirer.SettlementReceivedRequest{
		LpId:            lpID,
		BankTransferRef: bankTransferRef,
		LocalCurrency:   localCurrency,
		AmountReceived:  amountReceived,
		ReceivedAt:      timestamppb.New(receivedAt),
	}))
	if err != nil {
		log.Printf("SettlementReceived failed for %s: %v", bankTransferRef, err)
		return Unknown[*acquirer.SettlementReceivedResponse_Accepted]{Detail: err.Error()}
	}

	msg := resp.Msg
	switch msg.GetResult().(type) {
	case *acquirer.SettlementReceivedResponse_Accepted_:
		log.Printf("Settlement %s from LP %d confirmed: %s %s",
			bankTransferRef, lpID, DecimalToString(amountReceived), localCurrency)
		return Accepted[*acquirer.SettlementReceivedResponse_Accepted]{Payload: msg.GetAccepted()}

	case *acquirer.SettlementReceivedResponse_Rejected_:
		reason := msg.GetRejected().GetReason().String()
		log.Printf("Settlement %s from LP %d rejected: %s", bankTransferRef, lpID, reason)
		return Rejected[*acquirer.SettlementReceivedResponse_Accepted]{Reason: reason}

	default:
		return Unknown[*acquirer.SettlementReceivedResponse_Accepted]{Detail: "response carried an unrecognised result variant"}
	}
}
