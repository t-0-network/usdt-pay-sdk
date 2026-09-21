package main

import (
	"errors"
	"os"
	"strings"
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

func TestReplaceModulePath(t *testing.T) {
	t.Run("own path replaced", func(t *testing.T) {
		re := moduleReplacer("example.com/app")
		got := replaceModulePath(`import "example.com/app/pkg"`, re, "github.com/acme/foo")
		if !strings.Contains(got, `"github.com/acme/foo/pkg"`) {
			t.Errorf("own path not replaced:\n%s", got)
		}
	})

	t.Run("app-extra untouched", func(t *testing.T) {
		re := moduleReplacer("example.com/app")
		got := replaceModulePath(`import "example.com/app-extra/lib"`, re, "github.com/acme/foo")
		if !strings.Contains(got, `"example.com/app-extra/lib"`) {
			t.Errorf("app-extra was replaced:\n%s", got)
		}
	})

	t.Run("app~tilde untouched", func(t *testing.T) {
		re := moduleReplacer("example.com/app")
		got := replaceModulePath(`import "example.com/app~tilde/x"`, re, "github.com/acme/foo")
		if !strings.Contains(got, `"example.com/app~tilde/x"`) {
			t.Errorf("tilde-suffixed path was replaced:\n%s", got)
		}
	})

	t.Run("app+plus untouched", func(t *testing.T) {
		re := moduleReplacer("example.com/app")
		got := replaceModulePath(`import "example.com/app+plus/y"`, re, "github.com/acme/foo")
		if !strings.Contains(got, `"example.com/app+plus/y"`) {
			t.Errorf("plus-suffixed path was replaced:\n%s", got)
		}
	})

	t.Run("dollar in user path survives", func(t *testing.T) {
		re := moduleReplacer("example.com/app")
		got := replaceModulePath("module example.com/app\n", re, "example.com/$pecial")
		if !strings.Contains(got, "module example.com/$pecial") {
			t.Errorf("dollar in user path was corrupted:\n%s", got)
		}
	})

	t.Run("end of string boundary", func(t *testing.T) {
		re := moduleReplacer("example.com/app")
		got := replaceModulePath("module example.com/app", re, "github.com/acme/foo")
		if got != "module github.com/acme/foo" {
			t.Errorf("end-of-string replacement wrong:\n%s", got)
		}
	})

	t.Run("needle containing my-provider matches after name rewrite", func(t *testing.T) {
		// The template module path is "example.com/my-provider".
		// After processPlaceholders with project name "foo", the file content
		// has "example.com/foo" and the needle is also rewritten to
		// "example.com/foo", so the replacement matches.
		needle := processPlaceholders("example.com/my-provider",
			ScaffoldOpts{ProjectName: "foo"}, toPascalCase("foo"))
		re := moduleReplacer(needle)
		content := `module example.com/foo` + "\n" + `import "example.com/foo/pkg"` + "\n"
		got := replaceModulePath(content, re, "github.com/acme/foo-project")
		if !strings.Contains(got, "module github.com/acme/foo-project") {
			t.Errorf("module line not replaced:\n%s", got)
		}
		if !strings.Contains(got, `"github.com/acme/foo-project/pkg"`) {
			t.Errorf("import not replaced:\n%s", got)
		}
	})
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
