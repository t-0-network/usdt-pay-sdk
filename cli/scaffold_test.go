package main

import (
	"os"
	"path/filepath"
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

func TestScaffold_AllLanguages(t *testing.T) {
	for _, lang := range Config.Languages {
		roles, _ := listRoles(lang)
		if len(roles) == 0 {
			roles = []string{""}
		}
		for _, role := range roles {
			name := lang
			if role != "" {
				name = lang + "/" + role
			}
			t.Run(name, func(t *testing.T) {
				dir := t.TempDir()
				projectDir := filepath.Join(dir, "test-project")

				opts := ScaffoldOpts{
					Lang:        lang,
					Role:        role,
					ProjectName: "test-project",
					ProjectDir:  projectDir,
					Version:     "dev",
				}
				if lang == "go" {
					opts.ModulePath = "github.com/test/test-project"
				}

				if err := os.MkdirAll(projectDir, 0777); err != nil {
					t.Fatal(err)
				}
				if err := scaffold(opts); err != nil {
					t.Fatalf("scaffold(%s): %v", name, err)
				}

				// .gitignore must exist (dot-gitignore renamed)
				if _, err := os.Stat(filepath.Join(projectDir, ".gitignore")); err != nil {
					t.Errorf(".gitignore missing in %s scaffold", name)
				}
				// dot-gitignore must NOT exist
				if _, err := os.Stat(filepath.Join(projectDir, "dot-gitignore")); err == nil {
					t.Errorf("dot-gitignore still present in %s scaffold", name)
				}
			})
		}
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
