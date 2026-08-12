import { Code, ConnectError } from "@connectrpc/connect";

/**
 * What a call to t-0 did, from the caller's point of view. Every state-changing
 * endpoint has an idempotency key, and which of these three you got decides what to
 * do with it.
 *
 * `shouldRetry` is true only for `unknown`.
 */
export type Outcome<T> =
  /** t-0 accepted it. The operation is done; record it and move on. */
  | { readonly kind: "accepted"; readonly value: T; readonly shouldRetry: false }
  /**
   * t-0 refused this payload and will keep refusing it — resending it unchanged is
   * pointless. The key is *not* consumed: correct the fields named by `reason` (and
   * `failingIds`, where the endpoint reports them) and resend under the same key.
   *
   * Covers both the synchronous `failure` and the asynchronous `rejected` response
   * variants — from the caller's side they mean the same thing.
   */
  | {
      readonly kind: "rejected";
      readonly reason: string;
      readonly failingIds: readonly bigint[];
      readonly shouldRetry: false;
    }
  /**
   * No answer came back. t-0 may or may not have committed the call — this is exactly
   * the case the idempotency key exists for. Retry with the same key and identical
   * content until you get an `accepted` or a `rejected`.
   */
  | { readonly kind: "unknown"; readonly detail: string; readonly shouldRetry: true };

export const accepted = <T>(value: T): Outcome<T> => ({
  kind: "accepted",
  value,
  shouldRetry: false,
});

export const rejected = (reason: string, failingIds: readonly bigint[] = []): Outcome<never> => ({
  kind: "rejected",
  reason,
  failingIds,
  shouldRetry: false,
});

export const unknown = (detail: string): Outcome<never> => ({
  kind: "unknown",
  detail,
  shouldRetry: true,
});

/**
 * A response whose `result` oneof is empty — a contract change this build does not
 * know about. Reported as rejected rather than retried: resending would get the same
 * unreadable answer.
 */
export const noResultVariant = (): Outcome<never> => rejected("response carried no result variant");

/**
 * A thrown call, classified. Transport failures are `unknown` — the call may still
 * have committed on t-0's side, so the key has to be retried.
 *
 * Four codes are not: they mean t-0 read the request and refused it, and the same
 * bytes would be refused the same way forever. Treating those as retryable is how you
 * get an infinite loop against a request that has a typo in it.
 */
export function outcomeFromError(error: unknown): Outcome<never> {
  if (!(error instanceof ConnectError)) {
    // Not an answer from t-0 at all — a bug on this side. Let it surface.
    throw error;
  }

  const permanent =
    error.code === Code.InvalidArgument ||
    error.code === Code.Unauthenticated ||
    error.code === Code.PermissionDenied ||
    error.code === Code.Unimplemented;

  return permanent ? rejected(`${Code[error.code]}: ${error.rawMessage}`) : unknown(error.message);
}
