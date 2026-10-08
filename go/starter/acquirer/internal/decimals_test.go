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
		{"-0.5", "-0.5"},
		{"9223372036854775807", "9223372036854775807"},
		{"-9223372036854775808", "-9223372036854775808"},
	}

	for _, tt := range tests {
		t.Run(tt.input, func(t *testing.T) {
			d, err := DecimalFromString(tt.input)
			if err != nil {
				t.Fatalf("DecimalFromString(%q): %v", tt.input, err)
			}
			got := DecimalToString(d)
			if got != tt.want {
				t.Errorf("DecimalToString(DecimalFromString(%q)) = %q, want %q (unscaled=%d, exponent=%d)",
					tt.input, got, tt.want, d.GetUnscaled(), d.GetExponent())
			}
		})
	}
}

func TestDecimalFromString_WireValues(t *testing.T) {
	tests := []struct {
		input    string
		unscaled int64
		exponent int32
	}{
		{"100000.00", 10000000, -2},
		{"-0.5", -5, -1},
		{"7", 7, 0},
		{"0.00000001", 1, -8},
	}

	for _, tt := range tests {
		t.Run(tt.input, func(t *testing.T) {
			d, err := DecimalFromString(tt.input)
			if err != nil {
				t.Fatalf("DecimalFromString(%q): %v", tt.input, err)
			}
			if d.GetUnscaled() != tt.unscaled || d.GetExponent() != tt.exponent {
				t.Errorf("DecimalFromString(%q) = (%d, %d), want (%d, %d)",
					tt.input, d.GetUnscaled(), d.GetExponent(), tt.unscaled, tt.exponent)
			}
		})
	}
}

func TestDecimalFromString_Errors(t *testing.T) {
	tests := []struct {
		input string
		want  string
	}{
		{"", "'' is not a plain decimal number"},
		{"1e9", "'1e9' is not a plain decimal number"},
		{"+1", "'+1' is not a plain decimal number"},
		{"1.", "'1.' is not a plain decimal number"},
		{".5", "'.5' is not a plain decimal number"},
		{" 1", "' 1' is not a plain decimal number"},
		{"1_000", "'1_000' is not a plain decimal number"},
		{"0x10", "'0x10' is not a plain decimal number"},
		{"１", "'１' is not a plain decimal number"}, // fullwidth digit one
		{"1\n", "'1\n' is not a plain decimal number"},
		{"0.123456789", "0.123456789 needs exponent -9, outside the contract's [-8, 8] — round it first"},
		{"9223372036854775808", "9223372036854775808 does not fit the contract's 64-bit unscaled value"},
		{"-9223372036854775809", "-9223372036854775809 does not fit the contract's 64-bit unscaled value"},
		{"92233720368547758.08", "92233720368547758.08 does not fit the contract's 64-bit unscaled value"},
	}

	for _, tt := range tests {
		t.Run(tt.input, func(t *testing.T) {
			d, err := DecimalFromString(tt.input)
			if err == nil {
				t.Fatalf("DecimalFromString(%q) = %v, want an error", tt.input, d)
			}
			if err.Error() != tt.want {
				t.Errorf("error = %q, want %q", err.Error(), tt.want)
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
