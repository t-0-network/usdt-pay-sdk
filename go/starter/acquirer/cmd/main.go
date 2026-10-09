package main

import (
	"context"
	"fmt"
	"log"
	"os"
	"os/signal"
	"syscall"

	"github.com/google/uuid"
	"github.com/t-0-network/provider-sdk/go/provider"
	usdtpay "github.com/t-0-network/usdt-pay-sdk/go/sdk"
	"github.com/t-0-network/usdt-pay-sdk/go/sdk/gen/tzero/v1/pay/acquirer/acquirerconnect"

	root "github.com/t-0-network/usdt-pay-sdk/go/starter/acquirer"
	"github.com/t-0-network/usdt-pay-sdk/go/starter/acquirer/internal"
)

func main() {
	config, err := root.LoadConfig()
	if err != nil {
		exit(err)
	}

	log.Printf("Acquirer public key: %s", config.PublicKey)
	// TODO: Step 1.2 — send this public key to the t-0 team so they can verify your calls.

	// Outbound: GetPaymentQuote, CreatePaymentIntent, SettlementReceived.
	t0, err := usdtpay.CreateClient(
		config.TzeroEndpoint, config.PrivateKey,
		acquirerconnect.NewAcquirerServiceClient,
	)
	if err != nil {
		exit(err)
	}

	// Inbound: PaymentAuthorized, SettlementInitiated, SettlementCompleted,
	// PaymentExpired, PaymentFailed.
	// Every inbound signature is verified against NETWORK_PUBLIC_KEY.
	handler, err := usdtpay.NewHTTPHandler(
		config.NetworkPublicKey,
		provider.Handler(acquirerconnect.NewAcquirerCallbackServiceHandler, acquirerconnect.AcquirerCallbackServiceHandler(root.NewAcquirerCallbackHandler())),
	)
	if err != nil {
		exit(root.NetworkKeyError(err))
	}

	// StartServer returns once the address is bound, so the server is
	// listening by the time it returns.
	addr := fmt.Sprintf(":%d", config.Port)
	shutdown, err := provider.StartServer(handler, provider.WithAddr(addr))
	if err != nil {
		exit(err)
	}

	// Before the demo sale, so that a signal during its calls cancels them.
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	log.Printf("Callback server listening on %s", addr)

	// ──────────────────────────────────────────────────────────────
	// Phase 2 — price a sale, then open an intent for it.
	//
	// This runs a single demo sale at startup so you can see the round
	// trip. Move it behind your POS integration once it works.
	// ──────────────────────────────────────────────────────────────

	runDemoSale(ctx, t0)

	// TODO: Step 2.3 — deploy this service and give the t-0 team its base URL,
	//       so the Phase 3 callbacks can reach you.

	<-ctx.Done()
	log.Println("Shutting down")
	// Stops accepting calls and drains the ones in flight, up to the SDK's
	// shutdown timeout.
	if err := shutdown(context.Background()); err != nil {
		log.Printf("Shutdown: %v", err)
	}
}

// exit prints why the acquirer did not start and exits with 1.
func exit(err error) {
	root.WriteStartupError(os.Stderr, err)
	os.Exit(1)
}

func runDemoSale(ctx context.Context, t0 acquirerconnect.AcquirerServiceClient) {
	// TODO: Step 2.1 — replace the demo sale with a real one from your POS. One
	//       sale is one currency, one amount and one paymentRef: quote and intent
	//       must describe the same sale or you price one thing and charge another.
	//       On-chain settlement: skip GetPaymentQuote and send the amount in USDt.
	localCurrency := "COP"
	localAmount, err := internal.DecimalFromString("100000")
	if err != nil {
		log.Printf("Demo sale skipped: %v", err)
		return
	}
	paymentRef := uuid.New().String()
	idempotencyKey := uuid.New().String()

	quoted := internal.FetchQuote(ctx, t0, localCurrency, localAmount)
	if quoted.ShouldRetry() {
		log.Println("No answer from GetPaymentQuote — the lookup is safe to retry")
	}

	quote, ok := quoted.Accepted()
	if !ok {
		return
	}

	intent := internal.CreateIntent(
		ctx, t0,
		paymentRef, idempotencyKey,
		localCurrency, localAmount,
		quote.GetQuoteId(),
	)

	if intent.ShouldRetry() {
		log.Printf("Intent for sale %s is unresolved — retry the same idempotencyKey", paymentRef)
	}
}
