package main

import (
	"context"
	"errors"
	"fmt"
	"log"
	"os"
	"os/signal"
	"syscall"

	"github.com/google/uuid"
	usdtpay "github.com/t-0-network/usdt-pay-sdk/go/sdk"
	"github.com/t-0-network/usdt-pay-sdk/go/sdk/gen/tzero/v1/pay/acquirer/acquirerconnect"
	"github.com/t-0-network/provider-sdk/go/provider"

	root "github.com/t-0-network/usdt-pay-sdk/go/starter/acquirer"
	"github.com/t-0-network/usdt-pay-sdk/go/starter/acquirer/internal"
)

func main() {
	config, err := root.LoadConfig()
	if err != nil {
		var ce *root.ConfigurationError
		if errors.As(err, &ce) {
			log.Printf("ERROR: %s", ce.Error())
			log.Printf("%s", ce.HelpMessage)
		} else {
			log.Printf("ERROR: %v", err)
		}
		os.Exit(1)
	}

	// Outbound: GetPaymentQuote, CreatePaymentIntent, SettlementReceived.
	t0, err := usdtpay.CreateClient(
		config.TzeroEndpoint, config.PrivateKey,
		acquirerconnect.NewAcquirerServiceClient,
	)
	if err != nil {
		log.Fatalf("Failed to create t-0 client: %v", err)
	}

	// Inbound: PaymentAuthorized, SettlementInitiated, SettlementCompleted,
	// PaymentExpired, PaymentFailed.
	// Every inbound signature is verified against NETWORK_PUBLIC_KEY.
	addr := fmt.Sprintf(":%d", config.Port)
	shutdown, err := usdtpay.StartServer(
		addr, config.NetworkPublicKey,
		provider.Handler(acquirerconnect.NewAcquirerCallbackServiceHandler, acquirerconnect.AcquirerCallbackServiceHandler(root.NewAcquirerCallbackHandler())),
	)
	if err != nil {
		log.Fatalf("Failed to start callback server: %v", err)
	}
	defer shutdown(context.Background())

	log.Printf("Callback server listening on %s", addr)

	// ──────────────────────────────────────────────────────────────
	// Phase 2 — price a sale, then open an intent for it.
	//
	// This runs a single demo sale at startup so you can see the round
	// trip. Move it behind your POS integration once it works.
	// ──────────────────────────────────────────────────────────────

	runDemoSale(t0)

	// TODO: Step 2.3 — deploy this service and give the t-0 team its base URL,
	//       so the Phase 3 callbacks can reach you.

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	<-ctx.Done()
	log.Println("Shutting down")
}

func runDemoSale(t0 acquirerconnect.AcquirerServiceClient) {
	// TODO: Step 2.1 — replace the demo sale with a real one from your POS. One
	//       sale is one currency, one amount and one paymentRef: quote and intent
	//       must describe the same sale or you price one thing and charge another.
	//       On-chain settlement: skip GetPaymentQuote and send the amount in USDt.
	localCurrency := "COP"
	localAmount := internal.DecimalFromString("100000")
	paymentRef := uuid.New().String()
	idempotencyKey := uuid.New().String()

	quoted := internal.FetchQuote(context.Background(), t0, localCurrency, localAmount)
	if quoted.ShouldRetry() {
		log.Println("No answer from GetPaymentQuote — the lookup is safe to retry")
	}

	val, ok := quoted.Value()
	if !ok {
		return
	}

	intent := internal.CreateIntent(
		context.Background(), t0,
		paymentRef, idempotencyKey,
		localCurrency, localAmount,
		val.GetQuoteId(),
	)

	if intent.ShouldRetry() {
		log.Printf("Intent for sale %s is unresolved — retry the same idempotencyKey", paymentRef)
	}
}

