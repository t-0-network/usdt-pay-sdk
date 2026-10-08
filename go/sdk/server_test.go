package usdtpay

import (
	"context"
	"errors"
	"fmt"
	"net"
	"net/http/httptest"
	"strings"
	"sync/atomic"
	"testing"
	"time"

	"connectrpc.com/connect"
	"github.com/t-0-network/provider-sdk/go/provider"
	"google.golang.org/protobuf/types/known/timestamppb"

	pay "github.com/t-0-network/usdt-pay-sdk/go/sdk/gen/tzero/v1/pay"
	"github.com/t-0-network/usdt-pay-sdk/go/sdk/gen/tzero/v1/pay/acquirer"
	"github.com/t-0-network/usdt-pay-sdk/go/sdk/gen/tzero/v1/pay/acquirer/acquirerconnect"
	"github.com/t-0-network/usdt-pay-sdk/go/sdk/gen/tzero/v1/pay/issuer"
	"github.com/t-0-network/usdt-pay-sdk/go/sdk/gen/tzero/v1/pay/issuer/issuerconnect"
)

func TestNewHTTPHandler_RejectsEmptyKey(t *testing.T) {
	_, err := NewHTTPHandler("")
	if err == nil {
		t.Fatal("expected error for empty network public key")
	}
}

func TestNewHTTPHandler_AcceptsValidKey(t *testing.T) {
	const networkKey = "0x04dd549e27ef8acc1c9d2027d7973b1e5d8e014ce5ac36424ef9f7a3ee8a44029dee77d7acbc8e8980453eadc4e60db153403febd147e14c8444de806943524d3e"
	h, err := NewHTTPHandler(networkKey)
	if err != nil {
		t.Fatalf("NewHTTPHandler: %v", err)
	}
	if h == nil {
		t.Fatal("handler is nil")
	}
}

func TestStartServer_StartsAndShuts(t *testing.T) {
	const networkKey = "0x04dd549e27ef8acc1c9d2027d7973b1e5d8e014ce5ac36424ef9f7a3ee8a44029dee77d7acbc8e8980453eadc4e60db153403febd147e14c8444de806943524d3e"

	shutdown, err := StartServer(":0", networkKey)
	if err != nil {
		t.Fatalf("StartServer: %v", err)
	}
	if err := shutdown(context.Background()); err != nil {
		t.Fatalf("shutdown: %v", err)
	}
}

// The pay contract's custom rules (valid_address, valid_tx_hash) live in this SDK's
// generated code, not in provider-sdk's. These tests make them fire through the
// wrapper's server and through provider-sdk's Validate helper, so a rule that could
// not be resolved would show up as a different error here.

// The test plays t-0: it signs with the network key, and the server verifies against
// the matching public key.
const networkPrivateKey = "0x6b30303de7b26bfb1222b317a52113357f8bb06de00160b4261a2fef9c8b9bd8"

const (
	badResponseMessage = "response validation failed: success.deposit_options[0].deposit_address: must be 34-42 characters"
	badRequestMessage  = "validation error: usdt_on_chain.on_chain_tx_hash: must be 64 hex characters, optionally 0x-prefixed"
)

// badInstructions breaks exactly one rule: deposit_address is too short.
func badInstructions() *issuer.CreatePaymentInstructionsResponse {
	return &issuer.CreatePaymentInstructionsResponse{
		Result: &issuer.CreatePaymentInstructionsResponse_Success_{
			Success: &issuer.CreatePaymentInstructionsResponse_Success{
				ExpiresAt: timestamppb.New(time.Now().Add(time.Hour)),
				DepositOptions: []*issuer.CreatePaymentInstructionsResponse_Success_DepositOption{{
					Chain:          pay.Blockchain_BLOCKCHAIN_ETH,
					DepositAddress: "bad",
					TokenContract:  "0x" + strings.Repeat("bb", 20),
				}},
			},
		},
	}
}

func validInstructionsRequest() *issuer.CreatePaymentInstructionsRequest {
	return &issuer.CreatePaymentInstructionsRequest{
		PaymentIntentId: 1,
		AcquirerId:      2,
		AmountUsdt:      &pay.Decimal{Unscaled: 1000, Exponent: -2},
		ExpiresAt:       timestamppb.New(time.Now().Add(time.Hour)),
	}
}

// badAuthorized breaks exactly one rule: on_chain_tx_hash is not 64 hex characters.
func badAuthorized() *acquirer.PaymentAuthorizedRequest {
	now := timestamppb.Now()
	return &acquirer.PaymentAuthorizedRequest{
		PaymentIntentId: 1,
		PaymentRef:      "order-1",
		PaymentMethod: &acquirer.PaymentAuthorizedRequest_UsdtOnChain{
			UsdtOnChain: &pay.UsdtOnChainPayment{
				Chain:         pay.Blockchain_BLOCKCHAIN_ETH,
				OnChainTxHash: "0xabc",
				SenderAddress: "0x" + strings.Repeat("aa", 20),
			},
		},
		ApprovedAt:       now,
		SettlementAmount: &pay.Decimal{Unscaled: 1000, Exponent: -2},
		ReceivedAt:       now,
	}
}

type badIssuer struct {
	issuerconnect.UnimplementedIssuerCallbackServiceHandler
}

func (badIssuer) CreatePaymentInstructions(
	context.Context, *connect.Request[issuer.CreatePaymentInstructionsRequest],
) (*connect.Response[issuer.CreatePaymentInstructionsResponse], error) {
	return connect.NewResponse(badInstructions()), nil
}

type countingAcquirer struct {
	acquirerconnect.UnimplementedAcquirerCallbackServiceHandler
	calls atomic.Int32
}

func (a *countingAcquirer) PaymentAuthorized(
	context.Context, *connect.Request[acquirer.PaymentAuthorizedRequest],
) (*connect.Response[acquirer.PaymentAuthorizedResponse], error) {
	a.calls.Add(1)
	return connect.NewResponse(&acquirer.PaymentAuthorizedResponse{}), nil
}

func networkPublicKey(t *testing.T) string {
	t.Helper()
	pub, err := PublicKeyFromPrivateKey(networkPrivateKey)
	if err != nil {
		t.Fatalf("PublicKeyFromPrivateKey: %v", err)
	}
	return pub
}

func issuerHandler() provider.BuildHandler {
	return provider.Handler(issuerconnect.NewIssuerCallbackServiceHandler,
		issuerconnect.IssuerCallbackServiceHandler(badIssuer{}))
}

func assertConnectError(t *testing.T, err error, code connect.Code, message string) {
	t.Helper()
	var ce *connect.Error
	if !errors.As(err, &ce) {
		t.Fatalf("expected a *connect.Error, got %v", err)
	}
	if ce.Code() != code {
		t.Fatalf("code = %v, want %v (message %q)", ce.Code(), code, ce.Message())
	}
	if ce.Message() != message {
		t.Fatalf("message = %q, want %q", ce.Message(), message)
	}
}

func callInstructions(t *testing.T, baseURL string) error {
	t.Helper()
	client, err := CreateClient(baseURL, networkPrivateKey, issuerconnect.NewIssuerCallbackServiceClient)
	if err != nil {
		t.Fatalf("CreateClient: %v", err)
	}
	_, err = client.CreatePaymentInstructions(context.Background(), connect.NewRequest(validInstructionsRequest()))
	return err
}

func TestNewHTTPHandler_RefusesResponseThatBreaksCustomRule(t *testing.T) {
	h, err := NewHTTPHandler(networkPublicKey(t), issuerHandler())
	if err != nil {
		t.Fatalf("NewHTTPHandler: %v", err)
	}
	srv := httptest.NewServer(h)
	defer srv.Close()

	assertConnectError(t, callInstructions(t, srv.URL), connect.CodeInternal, badResponseMessage)
}

func TestStartServer_RefusesResponseThatBreaksCustomRule(t *testing.T) {
	addr := freeAddr(t)
	shutdown, err := StartServer(addr, networkPublicKey(t), issuerHandler())
	if err != nil {
		t.Fatalf("StartServer: %v", err)
	}
	defer func() { _ = shutdown(context.Background()) }()

	assertConnectError(t, callInstructions(t, "http://"+addr), connect.CodeInternal, badResponseMessage)
}

func TestNewHTTPHandler_RefusesRequestThatBreaksCustomRule(t *testing.T) {
	impl := &countingAcquirer{}
	h, err := NewHTTPHandler(networkPublicKey(t), provider.Handler(
		acquirerconnect.NewAcquirerCallbackServiceHandler, acquirerconnect.AcquirerCallbackServiceHandler(impl)))
	if err != nil {
		t.Fatalf("NewHTTPHandler: %v", err)
	}
	srv := httptest.NewServer(h)
	defer srv.Close()

	client, err := CreateClient(srv.URL, networkPrivateKey, acquirerconnect.NewAcquirerCallbackServiceClient)
	if err != nil {
		t.Fatalf("CreateClient: %v", err)
	}
	_, err = client.PaymentAuthorized(context.Background(), connect.NewRequest(badAuthorized()))

	assertConnectError(t, err, connect.CodeInvalidArgument, badRequestMessage)
	if n := impl.calls.Load(); n != 0 {
		t.Fatalf("handler ran %d times for a request that breaks a rule", n)
	}
}

func TestValidate_FiresCustomRule(t *testing.T) {
	_, err := provider.Validate(badInstructions())
	assertConnectError(t, err, connect.CodeInternal, badResponseMessage)
}

func freeAddr(t *testing.T) string {
	t.Helper()
	l, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("listen: %v", err)
	}
	defer l.Close()
	return fmt.Sprintf("127.0.0.1:%d", l.Addr().(*net.TCPAddr).Port)
}
