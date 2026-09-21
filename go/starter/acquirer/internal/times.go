package internal

import (
	"time"

	"google.golang.org/protobuf/types/known/timestamppb"
)

// FormatTimestamp formats a protobuf Timestamp as an ISO 8601 string.
func FormatTimestamp(ts *timestamppb.Timestamp) string {
	if ts == nil {
		return "<nil>"
	}
	return ts.AsTime().UTC().Format(time.RFC3339Nano)
}
