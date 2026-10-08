package internal

import (
	"errors"

	"connectrpc.com/connect"
)

// Outcome represents what a call to t-0 did, from the caller's point of view.
type Outcome[T any] interface {
	// Accepted returns the payload and true when t-0 accepted the call.
	Accepted() (T, bool)
	ShouldRetry() bool
}

// Accepted — t-0 accepted it. Record it and move on.
type Accepted[T any] struct {
	Value T
}

func (a Accepted[T]) Accepted() (T, bool) { return a.Value, true }
func (a Accepted[T]) ShouldRetry() bool   { return false }

// Rejected — t-0 refuses this payload and will keep refusing it. Correct the
// fields named by Reason and resend under the same key.
type Rejected[T any] struct {
	Reason string
}

func (r Rejected[T]) Accepted() (T, bool) {
	var zero T
	return zero, false
}

func (r Rejected[T]) ShouldRetry() bool { return false }

// Unknown — no answer. Retry with the same key and identical content.
type Unknown[T any] struct {
	Detail string
}

func (u Unknown[T]) Accepted() (T, bool) {
	var zero T
	return zero, false
}

func (u Unknown[T]) ShouldRetry() bool { return true }

// OutcomeFromError classifies a call that returned an error. A transport
// failure is Unknown: the call may still have committed on t-0's side, so the
// key has to be retried.
//
// Five codes are not: they mean t-0 read the request and refused it, and the
// same bytes would be refused the same way forever. Treating those as
// retryable is how you get an infinite loop against a request that has a typo
// in it.
func OutcomeFromError[T any](err error) Outcome[T] {
	switch code := connect.CodeOf(err); code {
	case connect.CodeInvalidArgument,
		connect.CodeUnauthenticated,
		connect.CodePermissionDenied,
		connect.CodeUnimplemented,
		connect.CodeFailedPrecondition:
		var connectErr *connect.Error
		if errors.As(err, &connectErr) {
			return Rejected[T]{Reason: code.String() + ": " + connectErr.Message()}
		}
	}
	return Unknown[T]{Detail: err.Error()}
}
