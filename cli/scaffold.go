package main

import (
	"embed"
	"errors"
	"fmt"
	"io/fs"
	"os"
	"path"
	"path/filepath"
	"strings"
	"unicode"
)

//go:embed all:internal/embed
var embeddedTemplates embed.FS

// CLIConfig is the product's side of the contract: everything the synced
// scaffolder needs to know about the product it is built into lives here,
// in the product's config.go, never in the synced files.
type CLIConfig struct {
	ProductName string
	Command     string
	// Description of what `init` creates, shown in usage. Empty means
	// "a new <ProductName> project".
	Description  string
	RoleRequired bool
	DefaultRole  string
	Languages    []string
	// JavaRepositories are the values `--repository` accepts, the first one
	// being the default the Java template declares as `val sdkRepository`.
	// Empty: the flag does not exist and nothing is rewritten.
	JavaRepositories []string
	// JavaSDKArtifacts are the Java SDK coordinates the template depends on
	// at version `+`; a release build pins them to the CLI's own version.
	// Empty: the template pins its SDK itself and nothing is rewritten.
	JavaSDKArtifacts []string
	// NextSteps are printed after "cd <dir>" and before the run command —
	// what the user must do before the project works, e.g. a value to fill
	// in .env.
	NextSteps []string
	// OverlayFS, when set, holds product files written over the scaffolded
	// output after template extraction. Layout: overlay/<lang>[/<role>]/...
	// Files are copied verbatim (no placeholder or filename transforms).
	// Products typically set this to an embed.FS of their overlay/ directory.
	OverlayFS    fs.FS
	PostScaffold func(ScaffoldOpts) error
}

type ScaffoldOpts struct {
	Lang        string
	Role        string
	ProjectName string
	ProjectDir  string
	// Go-specific: module path for import rewriting
	ModulePath string
	// Java-specific: the chosen Config.JavaRepositories entry
	JavaRepo string
	// CLI version; pins Config.JavaSDKArtifacts in the Java template
	Version string
}

func (c CLIConfig) hasLang(lang string) bool {
	for _, l := range c.Languages {
		if l == lang {
			return true
		}
	}
	return false
}

func (c CLIConfig) description() string {
	if c.Description != "" {
		return c.Description
	}
	return "a new " + c.ProductName + " project"
}

func scaffold(opts ScaffoldOpts) error {
	templateRoot := path.Join("internal/embed", opts.Lang)
	if opts.Role != "" {
		templateRoot = path.Join(templateRoot, opts.Role)
	}

	// Verify the template exists in the embed
	if _, err := embeddedTemplates.ReadDir(templateRoot); err != nil {
		if opts.Role != "" {
			available, _ := listRoles(opts.Lang)
			return fmt.Errorf("template not found for lang=%s role=%s (available roles: %s)", opts.Lang, opts.Role, strings.Join(available, ", "))
		}
		return fmt.Errorf("template not found for lang=%s — run 'go generate ./...' first", opts.Lang)
	}

	pascalName := toPascalCase(opts.ProjectName)

	return fs.WalkDir(embeddedTemplates, templateRoot, func(src string, d fs.DirEntry, err error) error {
		if err != nil {
			return err
		}

		rel, err := filepath.Rel(templateRoot, src)
		if err != nil {
			return err
		}
		if rel == "." {
			return nil
		}

		// Filename transforms
		destName := rel
		destName = transformFilename(destName, opts.ProjectName, pascalName)

		destPath := filepath.Join(opts.ProjectDir, destName)

		if d.IsDir() {
			return os.MkdirAll(destPath, 0777)
		}

		data, err := embeddedTemplates.ReadFile(src)
		if err != nil {
			return fmt.Errorf("reading embedded file %s: %w", src, err)
		}

		if err := os.MkdirAll(filepath.Dir(destPath), 0777); err != nil {
			return err
		}

		// Binary files: copy without processing
		if isBinaryFile(filepath.Base(src)) {
			return writeFileWithMode(destPath, data, src)
		}

		// Text files: process placeholders
		content := string(data)
		content = processPlaceholders(content, opts, pascalName)

		return writeFileWithMode(destPath, []byte(content), src)
	})
}

// applyOverlay writes every file under overlay/<lang>[/<role>] in overlayFS
// over the scaffolded project, verbatim. A missing overlay root is not an
// error: most lang/role combinations have no overlay.
func applyOverlay(overlayFS fs.FS, opts ScaffoldOpts) error {
	overlayRoot := path.Join("overlay", opts.Lang)
	if opts.Role != "" {
		overlayRoot = path.Join(overlayRoot, opts.Role)
	}

	if _, err := fs.ReadDir(overlayFS, overlayRoot); err != nil {
		if errors.Is(err, fs.ErrNotExist) {
			return nil
		}
		return fmt.Errorf("reading overlay %s: %w", overlayRoot, err)
	}

	return fs.WalkDir(overlayFS, overlayRoot, func(src string, d fs.DirEntry, err error) error {
		if err != nil {
			return err
		}
		if d.IsDir() {
			return nil
		}

		rel := strings.TrimPrefix(src, overlayRoot+"/")

		data, err := fs.ReadFile(overlayFS, src)
		if err != nil {
			return fmt.Errorf("reading overlay file %s: %w", src, err)
		}

		destPath := filepath.Join(opts.ProjectDir, filepath.FromSlash(rel))
		if err := os.MkdirAll(filepath.Dir(destPath), 0777); err != nil {
			return err
		}
		return writeFileWithMode(destPath, data, src)
	})
}

func transformFilename(name, projectName, pascalName string) string {
	// .tmpl suffix (sync tool renames Go files to avoid compilation)
	name = strings.TrimSuffix(name, ".tmpl")

	// dot-gitignore → .gitignore (dotfiles stripped by Gradle/NuGet packaging)
	name = strings.ReplaceAll(name, "dot-gitignore", ".gitignore")
	name = strings.ReplaceAll(name, "dot-dockerignore", ".dockerignore")

	// All templates use "my-provider" as the project name literal
	name = strings.ReplaceAll(name, "my-provider", projectName)

	// C#: PascalCase in csproj filename (MyProvider.csproj → <pascal>.csproj)
	name = strings.ReplaceAll(name, "MyProvider", pascalName)

	return name
}

func processPlaceholders(content string, opts ScaffoldOpts, pascalName string) string {
	// All templates use "my-provider" as the project name literal
	content = strings.ReplaceAll(content, "my-provider", opts.ProjectName)

	// C#: PascalCase namespace (MyProvider → <PascalName>)
	content = strings.ReplaceAll(content, "MyProvider", pascalName)

	// Go: module path replacement (injected by sync tool as {{MODULE_PATH}})
	if opts.ModulePath != "" {
		content = strings.ReplaceAll(content, "{{MODULE_PATH}}", opts.ModulePath)
	}

	// Java: pin the SDK artifacts to this release, select the repository
	if opts.Lang == "java" {
		if opts.Version != "" && opts.Version != "dev" {
			for _, artifact := range Config.JavaSDKArtifacts {
				content = strings.ReplaceAll(content, `"`+artifact+`:+"`, `"`+artifact+`:`+opts.Version+`"`)
			}
		}
		if len(Config.JavaRepositories) > 0 && opts.JavaRepo != "" && opts.JavaRepo != Config.JavaRepositories[0] {
			content = strings.ReplaceAll(content,
				`val sdkRepository = "`+Config.JavaRepositories[0]+`"`,
				`val sdkRepository = "`+opts.JavaRepo+`"`)
		}
	}

	return content
}

var binaryExts = map[string]bool{
	".jar": true, ".class": true, ".exe": true,
	".png": true, ".jpg": true, ".gif": true, ".ico": true,
	".zip": true, ".gz": true, ".tar": true,
	".woff": true, ".woff2": true, ".ttf": true,
}

func isBinaryFile(name string) bool {
	ext := strings.ToLower(filepath.Ext(name))
	return binaryExts[ext]
}

func writeFileWithMode(dest string, data []byte, src string) error {
	mode := os.FileMode(0666)
	// Restore executable bit for known executables
	base := filepath.Base(dest)
	if base == "gradlew" || strings.HasSuffix(base, ".sh") {
		mode = 0755
	}
	return os.WriteFile(dest, data, mode)
}

func listRoles(lang string) ([]string, error) {
	templateRoot := path.Join("internal/embed", lang)
	entries, err := embeddedTemplates.ReadDir(templateRoot)
	if err != nil {
		return nil, err
	}
	var roles []string
	for _, e := range entries {
		if e.IsDir() {
			roles = append(roles, e.Name())
		}
	}
	return roles, nil
}

func toPascalCase(kebab string) string {
	var b strings.Builder
	upper := true
	for _, r := range kebab {
		if r == '-' || r == '_' {
			upper = true
			continue
		}
		if upper {
			b.WriteRune(unicode.ToUpper(r))
			upper = false
		} else {
			b.WriteRune(r)
		}
	}
	return b.String()
}

func sanitizeProjectName(name string) string {
	name = strings.ToLower(strings.TrimSpace(name))
	name = strings.ReplaceAll(name, " ", "-")
	// Remove invalid characters
	var b strings.Builder
	for _, r := range name {
		if (r >= 'a' && r <= 'z') || (r >= '0' && r <= '9') || r == '-' || r == '_' {
			b.WriteRune(r)
		}
	}
	return b.String()
}
