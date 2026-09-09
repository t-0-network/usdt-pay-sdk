package main

import (
	"errors"
	"os"
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

func TestRun_PostScaffoldErrorKeepsExistingDir(t *testing.T) {
	hookErr := errors.New("post-scaffold hook failed")
	prev := Config.PostScaffold
	Config.PostScaffold = func(ScaffoldOpts) error { return hookErr }
	t.Cleanup(func() { Config.PostScaffold = prev })

	lang := Config.Languages[0]
	opts := ScaffoldOpts{
		Lang:        lang,
		ProjectName: "test-project",
		ProjectDir:  t.TempDir(),
		Version:     "dev",
	}
	if Config.RoleRequired {
		roles, err := listRoles(lang)
		if err != nil || len(roles) == 0 {
			t.Fatalf("listRoles(%s): %v (roles=%v)", lang, err, roles)
		}
		opts.Role = roles[0]
	}
	if lang == "go" {
		opts.ModulePath = "github.com/test/test-project"
	}

	err := run(opts)
	if !errors.Is(err, hookErr) {
		t.Fatalf("run() error = %v, want wrapping %v", err, hookErr)
	}
	if _, statErr := os.Stat(opts.ProjectDir); statErr != nil {
		t.Fatalf("pre-existing project dir was removed on hook error: %v", statErr)
	}
}
