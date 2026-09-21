package internal

// Outcome represents what a call to t-0 did, from the caller's point of view.
type Outcome[T any] interface {
	Value() (T, bool)
	ShouldRetry() bool
}

// Accepted — t-0 accepted it. Record it and move on.
type Accepted[T any] struct {
	Payload T
}

func (a Accepted[T]) Value() (T, bool)  { return a.Payload, true }
func (a Accepted[T]) ShouldRetry() bool { return false }

// Rejected — t-0 refuses this payload and will keep refusing it. Correct the
// fields named by Reason and resend under the same key.
type Rejected[T any] struct {
	Reason string
}

func (r Rejected[T]) Value() (T, bool) {
	var zero T
	return zero, false
}

func (r Rejected[T]) ShouldRetry() bool { return false }

// Unknown — no answer. Retry with the same key and identical content.
type Unknown[T any] struct {
	Detail string
}

func (u Unknown[T]) Value() (T, bool) {
	var zero T
	return zero, false
}

func (u Unknown[T]) ShouldRetry() bool { return true }
