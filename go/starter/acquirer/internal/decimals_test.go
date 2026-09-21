package internal

import (
	"testing"

	pay "github.com/t-0-network/usdt-pay-sdk/go/sdk/gen/tzero/v1/pay"
)

func TestDecimalRoundTrip(t *testing.T) {
	tests := []struct {
		input string
		want  string
	}{
		{"100000", "100000"},
		{"100000.00", "100000.00"},
		{"24.39", "24.39"},
		{"0", "0"},
		{"1", "1"},
		{"0.12345678", "0.12345678"},
	}

	for _, tt := range tests {
		t.Run(tt.input, func(t *testing.T) {
			d := DecimalFromString(tt.input)
			got := DecimalToString(d)
			if got != tt.want {
				t.Errorf("DecimalToString(DecimalFromString(%q)) = %q, want %q (unscaled=%d, exponent=%d)",
					tt.input, got, tt.want, d.GetUnscaled(), d.GetExponent())
			}
		})
	}
}

func TestDecimalToString_WireValues(t *testing.T) {
	tests := []struct {
		name     string
		unscaled int64
		exponent int32
		want     string
	}{
		{"integer", 100000, 0, "100000"},
		{"two decimals", 10000000, -2, "100000.00"},
		{"positive exponent", 10, 8, "1000000000"},
		{"nil", 0, 0, "0"},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			d := &pay.Decimal{Unscaled: tt.unscaled, Exponent: tt.exponent}
			got := DecimalToString(d)
			if got != tt.want {
				t.Errorf("DecimalToString(%d, %d) = %q, want %q", tt.unscaled, tt.exponent, got, tt.want)
			}
		})
	}
}

func TestDecimalToString_Nil(t *testing.T) {
	if got := DecimalToString(nil); got != "0" {
		t.Errorf("DecimalToString(nil) = %q, want %q", got, "0")
	}
}
