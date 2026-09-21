package internal

import (
	"fmt"
	"math/big"
	"strconv"
	"strings"

	pay "github.com/t-0-network/usdt-pay-sdk/go/sdk/gen/tzero/v1/pay"
)

// DecimalFromString converts a decimal string to the wire Decimal message.
func DecimalFromString(s string) *pay.Decimal {
	parts := strings.SplitN(s, ".", 2)
	if len(parts) == 1 {
		n, err := strconv.ParseInt(s, 10, 64)
		if err != nil {
			panic(fmt.Sprintf("invalid decimal: %s", s))
		}
		return &pay.Decimal{Unscaled: n, Exponent: 0}
	}

	frac := parts[1]
	scale := int32(len(frac))
	combined := parts[0] + frac
	n, err := strconv.ParseInt(combined, 10, 64)
	if err != nil {
		panic(fmt.Sprintf("invalid decimal: %s", s))
	}
	return &pay.Decimal{Unscaled: n, Exponent: -scale}
}

// DecimalToString formats a wire Decimal as a plain decimal string.
func DecimalToString(d *pay.Decimal) string {
	if d == nil {
		return "0"
	}

	unscaled := d.GetUnscaled()
	exponent := d.GetExponent()

	if exponent >= 0 {
		result := big.NewInt(unscaled)
		pow := new(big.Int).Exp(big.NewInt(10), big.NewInt(int64(exponent)), nil)
		result.Mul(result, pow)
		return result.String()
	}

	scale := int(-exponent)
	negative := unscaled < 0
	if negative {
		unscaled = -unscaled
	}

	s := strconv.FormatInt(unscaled, 10)
	if len(s) <= scale {
		s = strings.Repeat("0", scale-len(s)+1) + s
	}

	intPart := s[:len(s)-scale]
	fracPart := s[len(s)-scale:]

	result := intPart + "." + fracPart
	if negative {
		result = "-" + result
	}
	return result
}
