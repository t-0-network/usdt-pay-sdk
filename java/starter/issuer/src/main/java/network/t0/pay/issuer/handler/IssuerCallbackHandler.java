package network.t0.pay.issuer.handler;

import io.grpc.stub.StreamObserver;
import network.t0.pay.issuer.internal.Decimals;
import network.t0.pay.issuer.internal.Times;
import network.t0.pay.proto.tzero.v1.pay.Blockchain;
import network.t0.pay.proto.tzero.v1.pay.CreatePaymentInstructionsRequest;
import network.t0.pay.proto.tzero.v1.pay.CreatePaymentInstructionsResponse;
import network.t0.pay.proto.tzero.v1.pay.IssuerCallbackServiceGrpc;
import network.t0.pay.proto.tzero.v1.pay.QrOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

/**
 * §5 CreatePaymentInstructions — the only callback t-0 pushes to the issuer.
 *
 * <p>Synchronous and on the critical path: t-0 calls it inline while the acquirer
 * waits on §4, so answer fast and never block on anything slow.
 *
 * <p>Delivered at least once, dedup key {@code paymentIntentId}. A repeat for an
 * id you already reserved must return the <em>same</em> addresses rather than
 * burning a second set out of the pool — so reserve under the id, and look it up
 * before allocating.
 */
public class IssuerCallbackHandler extends IssuerCallbackServiceGrpc.IssuerCallbackServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(IssuerCallbackHandler.class);

    @Override
    public void createPaymentInstructions(
            CreatePaymentInstructionsRequest request,
            StreamObserver<CreatePaymentInstructionsResponse> responseObserver) {

        log.info("§5 reserve: intent={} acquirer={} amount={} USDt until {}",
                request.getPaymentIntentId(),
                request.getAcquirerId(),
                Decimals.format(request.getAmountUsdt()),
                Times.format(request.getExpiresAt()));

        // TODO: Step 2.1 — look up paymentIntentId first and return the existing
        //       reservation if you have one; only then take fresh addresses from the pool.
        // TODO: Step 2.2 — resolve the settlement wallet for request.getAcquirerId()
        //       from your onboarding mapping. You will need it for §9 SettlementSent,
        //       and resolving it yourself is what makes t-0's on-chain check a real
        //       cross-check rather than an echo of its own input.
        // TODO: Step 2.3 — hold the reservation until request.getExpiresAt(), then
        //       release the addresses and report §14 PaymentExpired.
        // TODO: Step 2.4 — no free addresses, or the amount is outside your range?
        //       Answer with the Failure variant (ADDRESS_POOL_EMPTY /
        //       AMOUNT_OUT_OF_RANGE / ISSUER_UNAVAILABLE) instead of an error status —
        //       which is what the unimplemented default below already does.

        // Declines until you implement the steps above. Deliberate: whatever addresses
        // this returns are rendered by the POS as a payable QR and the customer sends
        // real USDt to them. A decline costs one sale; an address you do not control
        // costs the customer their money, irreversibly.
        responseObserver.onNext(CreatePaymentInstructionsResponse.newBuilder()
                .setFailure(CreatePaymentInstructionsResponse.Failure.newBuilder()
                        .setReason(CreatePaymentInstructionsResponse.Failure.Reason
                                .REASON_ISSUER_UNAVAILABLE))
                .build());
        responseObserver.onCompleted();

        // TODO: Step 2.3 — delete the decline above and return this instead, once the
        //       addresses come from your own pool. One option per chain you support for
        //       this intent; the customer picks one. The two hex constants are the real
        //       USDt contracts on each chain and stay as they are — it is the deposit
        //       addresses that must become yours.
        //
        // BigDecimal amountUsdt = Decimals.toBigDecimal(request.getAmountUsdt());
        // responseObserver.onNext(CreatePaymentInstructionsResponse.newBuilder()
        //         .setSuccess(CreatePaymentInstructionsResponse.Success.newBuilder()
        //                 .addQrOptions(tron(yourTronDepositAddress, amountUsdt))
        //                 .addQrOptions(evm(Blockchain.BLOCKCHAIN_ETH, 1,
        //                         "0xdAC17F958D2ee523a2206206994597C13D831ec7",
        //                         yourEthDepositAddress, amountUsdt))
        //                 .addQrOptions(evm(Blockchain.BLOCKCHAIN_BSC, 56,
        //                         "0x55d398326f99059fF775485246999027B3197955",
        //                         yourBscDepositAddress, amountUsdt))
        //                 .setExpiresAt(request.getExpiresAt()))
        //         .build());
        // responseObserver.onCompleted();
    }

    /**
     * renderablePayload is chain-native and the POS encodes it as a QR image without
     * touching it — so it has to be complete and correct here.
     */
    private static QrOption tron(String depositAddress, BigDecimal amountUsdt) {
        // TRON wallets read a TIP-681-style URI; amount is in USDt units.
        return QrOption.newBuilder()
                .setChain(Blockchain.BLOCKCHAIN_TRON)
                .setDepositAddress(depositAddress)
                .setRenderablePayload("tron:%s?amount=%s".formatted(depositAddress, amountUsdt.toPlainString()))
                .build();
    }

    private static QrOption evm(
            Blockchain chain,
            int chainId,
            String usdtContract,
            String depositAddress,
            BigDecimal amountUsdt) {
        // ERC-681: pay <amount> of the USDt contract to <depositAddress> on <chainId>.
        // USDt is 6 decimals on both Ethereum and BSC-pegged deployments here.
        long units = amountUsdt.movePointRight(6).longValueExact();
        return QrOption.newBuilder()
                .setChain(chain)
                .setDepositAddress(depositAddress)
                .setRenderablePayload("ethereum:%s@%d/transfer?address=%s&uint256=%d"
                        .formatted(usdtContract, chainId, depositAddress, units))
                .build();
    }
}
