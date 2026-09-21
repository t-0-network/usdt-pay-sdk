package gomod

import "strings"

// ModulePath returns the module directive from go.mod content,
// or "" when there is none.
func ModulePath(goMod []byte) string {
	for line := range strings.Lines(string(goMod)) {
		if f := strings.Fields(line); len(f) >= 2 && f[0] == "module" {
			p := f[1]
			if i := strings.Index(p, "//"); i >= 0 {
				p = p[:i]
			}
			p = strings.Trim(p, `"`)
			if strings.Contains(p, ".") {
				return p
			}
		}
	}
	return ""
}
