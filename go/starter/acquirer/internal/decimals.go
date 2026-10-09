package internal

import (
	"fmt"
	"math/big"
	"regexp"
	"strconv"
	"strings"

	pay "github.com/t-0-network/usdt-pay-sdk/go/sdk/gen/tzero/v1/pay"
)

// The contract constrains exponent to this range; anything else is rejected on the wire.
const (
	minExponent = -8
	maxExponent = 8
)

// An optional minus, ASCII digits, an optional fraction. No exponent notation:
// 1e9 would have to be a float first, which is the point.
var plainDecimal = regexp.MustCompile(`^-?[0-9]+(\.[0-9]+)?$`)

// DecimalFromString converts a plain decimal string — "100000.00", "-0.5",
// "7" — to the wire Decimal message. It returns an error if the value carries
// more precision, or more magnitude, than the contract can hold. Round to the
// precision you mean before calling this; truncating money silently is not
// this function's decision to make.
func DecimalFromString(value string) (*pay.Decimal, error) {
	if !plainDecimal.MatchString(value) {
		return nil, fmt.Errorf("'%s' is not a plain decimal number", value)
	}

	whole, fraction, _ := strings.Cut(value, ".")
	exponent := -len(fraction)
	if exponent < minExponent {
		return nil, fmt.Errorf("%s needs exponent %d, outside the contract's [%d, %d] — round it first",
			value, exponent, minExponent, maxExponent)
	}

	// "-0.5" splits to whole="-0", fraction="5", and ParseInt("-05") is -5. The
	// pattern leaves a range error as the only one ParseInt can return.
	unscaled, err := strconv.ParseInt(whole+fraction, 10, 64)
	if err != nil {
		return nil, fmt.Errorf("%s does not fit the contract's 64-bit unscaled value", value)
	}

	return &pay.Decimal{Unscaled: unscaled, Exponent: int32(exponent)}, nil
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
