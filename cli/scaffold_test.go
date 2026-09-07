package main

import (
	"testing"
)

func TestEmbedFS_RejectsBackslashPaths(t *testing.T) {
	// embed.FS requires forward-slash paths. On Windows, filepath.Join
	// produces backslash-separated paths that silently fail to match.
	// This test verifies the bug exists at the embed.FS level so the
	// path.Join fix in scaffold() remains guarded against regression.
	backslashPath := "internal\\embed\\go"
	_, err := embeddedTemplates.ReadDir(backslashPath)
	if err == nil {
		t.Fatalf("ReadDir(%q) succeeded; embed.FS should reject backslash paths", backslashPath)
	}
}

func TestEmbedFS_ForwardSlashWorks(t *testing.T) {
	for _, lang := range Config.Languages {
		t.Run(lang, func(t *testing.T) {
			dir := "internal/embed/" + lang
			entries, err := embeddedTemplates.ReadDir(dir)
			if err != nil {
				t.Fatalf("ReadDir(%q): %v", dir, err)
			}
			if len(entries) == 0 {
				t.Errorf("ReadDir(%q) returned 0 entries", dir)
			}
		})
	}
}

func TestToPascalCase_EdgeCases(t *testing.T) {
	tests := []struct {
		input string
		want  string
	}{
		{"my-provider", "MyProvider"},
		{"3rd-provider", "3rdProvider"},
		{"---", ""},
		{"hello", "Hello"},
		{"a-b-c", "ABC"},
	}
	for _, tt := range tests {
		t.Run(tt.input, func(t *testing.T) {
			got := toPascalCase(tt.input)
			if got != tt.want {
				t.Errorf("toPascalCase(%q) = %q, want %q", tt.input, got, tt.want)
			}
		})
	}
}

func TestSanitizeProjectName(t *testing.T) {
	tests := []struct {
		input string
		want  string
	}{
		{"My Provider", "my-provider"},
		{"hello_world", "hello_world"},
		{"test@123!", "test123"},
		{"---", "---"},
		{"  ", ""},
	}
	for _, tt := range tests {
		t.Run(tt.input, func(t *testing.T) {
			got := sanitizeProjectName(tt.input)
			if got != tt.want {
				t.Errorf("sanitizeProjectName(%q) = %q, want %q", tt.input, got, tt.want)
			}
		})
	}
}
