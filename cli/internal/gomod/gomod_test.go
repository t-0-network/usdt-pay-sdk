package gomod

import "testing"

func TestModulePath(t *testing.T) {
	tests := []struct {
		name    string
		content string
		want    string
	}{
		{"plain", "module example.com/foo\n\ngo 1.27.0\n", "example.com/foo"},
		{"tab separated", "module\texample.com/foo\n", "example.com/foo"},
		{"quoted", "module \"example.com/foo\"\n", "example.com/foo"},
		{"comment before module", "// generated\nmodule example.com/foo\n", "example.com/foo"},
		{"trailing comment", "module example.com/foo // indirect\n", "example.com/foo"},
		{"adjacent comment", "module example.com/foo//comment\n", "example.com/foo"},
		{"block form", "module (\n\texample.com/foo\n)\n", ""},
		{"no module directive", "go 1.27.0\n\nrequire (\n)\n", ""},
		{"empty", "", ""},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := ModulePath([]byte(tt.content)); got != tt.want {
				t.Errorf("ModulePath() = %q, want %q", got, tt.want)
			}
		})
	}
}
