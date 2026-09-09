package main

import (
	"flag"
	"fmt"
	"os"
	"path/filepath"
	"strings"
)

const (
	blue   = "\033[34m"
	green  = "\033[32m"
	yellow = "\033[33m"
	red    = "\033[31m"
	reset  = "\033[0m"
)

var (
	Version = "dev"
	noColor bool
)

func main() {
	initCmd := flag.NewFlagSet("init", flag.ExitOnError)
	lang := initCmd.String("lang", "", "Language/ecosystem: "+strings.Join(Config.Languages, ", "))
	dir := initCmd.String("dir", "", "Target directory (defaults to ./<project-name>)")
	noColorFlag := initCmd.Bool("no-color", false, "Disable colored output")
	showVersion := initCmd.Bool("version", false, "Show version")
	// Product-shaped flags exist only where the product has the thing.
	role := new(string)
	if Config.RoleRequired || Config.DefaultRole != "" {
		roleUsage := "Role"
		if Config.RoleRequired {
			roleUsage = "Role (required)"
		}
		role = initCmd.String("role", Config.DefaultRole, roleUsage)
	}
	modulePath := new(string)
	if Config.hasLang("go") {
		modulePath = initCmd.String("module", "", "Go module path (defaults to project name)")
	}
	javaRepo := new(string)
	if len(Config.JavaRepositories) > 0 {
		javaRepo = initCmd.String("repository", Config.JavaRepositories[0], "Java SDK repository: "+strings.Join(Config.JavaRepositories, "|"))
	}

	if len(os.Args) < 2 {
		printUsage()
		os.Exit(2)
	}

	switch os.Args[1] {
	case "init":
		if err := initCmd.Parse(os.Args[2:]); err != nil {
			os.Exit(2)
		}
		// Go's flag package stops at the first non-flag arg. Re-parse
		// remaining args so flags work in any position:
		//   t0 init demo --lang=node   (positional first)
		//   t0 init --lang=node demo   (flags first)
		projectName := initCmd.Arg(0)
		if remaining := initCmd.Args(); len(remaining) > 1 {
			initCmd.Parse(remaining[1:])
		}
		noColor = *noColorFlag

		if *showVersion {
			fmt.Printf("%s init %s\n", Config.ProductName, Version)
			return
		}
		if projectName == "" {
			fmt.Fprintf(os.Stderr, "%s project name is required\n\n", color(red, "[ERROR]"))
			fmt.Fprintf(os.Stderr, "Usage: %s <project-name> --lang=<language>\n", Config.Command)
			os.Exit(2)
		}

		projectName = sanitizeProjectName(projectName)
		if projectName == "" {
			fmt.Fprintf(os.Stderr, "%s invalid project name — use only lowercase letters, numbers, hyphens, underscores\n", color(red, "[ERROR]"))
			os.Exit(1)
		}

		pascal := toPascalCase(projectName)
		if pascal == "" || (pascal[0] >= '0' && pascal[0] <= '9') {
			fmt.Fprintf(os.Stderr, "%s project name must start with a letter (got %q)\n", color(red, "[ERROR]"), projectName)
			os.Exit(1)
		}

		if *lang == "" {
			fmt.Fprintf(os.Stderr, "%s --lang is required (options: %s)\n", color(red, "[ERROR]"), strings.Join(Config.Languages, ", "))
			os.Exit(2)
		}

		if !isValidLang(*lang) {
			fmt.Fprintf(os.Stderr, "%s unknown language %q (options: %s)\n", color(red, "[ERROR]"), *lang, strings.Join(Config.Languages, ", "))
			os.Exit(1)
		}

		if Config.RoleRequired && *role == "" {
			fmt.Fprintf(os.Stderr, "%s --role is required\n", color(red, "[ERROR]"))
			os.Exit(2)
		}

		if *lang == "java" && len(Config.JavaRepositories) > 0 && !contains(Config.JavaRepositories, *javaRepo) {
			fmt.Fprintf(os.Stderr, "%s unknown repository %q (options: %s)\n", color(red, "[ERROR]"), *javaRepo, strings.Join(Config.JavaRepositories, ", "))
			os.Exit(1)
		}

		projectDir := filepath.Join(".", projectName)
		if *dir != "" {
			projectDir = *dir
		}

		if entries, err := os.ReadDir(projectDir); err == nil && len(entries) > 0 {
			fmt.Fprintf(os.Stderr, "%s directory %q already exists and is non-empty\n", color(red, "[ERROR]"), projectDir)
			os.Exit(1)
		}

		modPath := *modulePath
		if modPath == "" && *lang == "go" {
			modPath = projectName
		}

		if err := run(ScaffoldOpts{
			Lang:        *lang,
			Role:        *role,
			ProjectName: projectName,
			ProjectDir:  projectDir,
			ModulePath:  modPath,
			JavaRepo:    *javaRepo,
			Version:     Version,
		}); err != nil {
			fmt.Fprintf(os.Stderr, "%s %v\n", color(red, "[ERROR]"), err)
			os.Exit(1)
		}

	case "keygen":
		kp, err := generateKeyPair()
		if err != nil {
			fmt.Fprintf(os.Stderr, "%s %v\n", color(red, "[ERROR]"), err)
			os.Exit(1)
		}
		fmt.Printf("Private key: %s\n", kp.PrivateKey)
		fmt.Printf("Public key:  %s\n", kp.PublicKey)

	case "--version", "-v", "version":
		fmt.Printf("%s %s\n", Config.ProductName, Version)

	case "--help", "-h", "help":
		printUsage()

	default:
		fmt.Fprintf(os.Stderr, "unknown command %q\n\n", os.Args[1])
		printUsage()
		os.Exit(2)
	}
}

func run(opts ScaffoldOpts) error {
	printHeader()

	fmt.Printf("%s Creating project: %s (%s)\n", color(blue, "[INFO]"), opts.ProjectName, opts.Lang)

	// Create project directory — track whether we created it so cleanup
	// doesn't delete a pre-existing directory the user owns.
	_, statErr := os.Stat(opts.ProjectDir)
	dirCreated := os.IsNotExist(statErr)
	if err := os.MkdirAll(opts.ProjectDir, 0777); err != nil {
		return fmt.Errorf("creating directory: %w", err)
	}
	cleanup := func() {
		if dirCreated {
			os.RemoveAll(opts.ProjectDir)
		}
	}

	// Scaffold template
	fmt.Printf("%s Extracting template files...\n", color(blue, "[INFO]"))
	if err := scaffold(opts); err != nil {
		cleanup()
		return fmt.Errorf("scaffolding: %w", err)
	}
	fmt.Printf("%s Template files extracted\n", color(green, "[OK]"))

	// Product-specific post-scaffold hook
	if Config.PostScaffold != nil {
		if err := Config.PostScaffold(opts); err != nil {
			cleanup()
			return fmt.Errorf("post-scaffold: %w", err)
		}
	}

	// Generate keypair
	fmt.Printf("%s Generating secp256k1 keypair...\n", color(blue, "[INFO]"))
	kp, err := generateKeyPair()
	if err != nil {
		cleanup()
		return fmt.Errorf("generating keypair: %w", err)
	}
	fmt.Printf("%s Keypair generated\n", color(green, "[OK]"))

	// Write .env
	fmt.Printf("%s Creating .env file...\n", color(blue, "[INFO]"))
	if err := writeEnvFile(opts.ProjectDir, kp); err != nil {
		cleanup()
		return fmt.Errorf("writing .env: %w", err)
	}
	fmt.Printf("%s Environment configured\n", color(green, "[OK]"))

	printCompletion(opts, kp)
	return nil
}

func printHeader() {
	fmt.Println()
	fmt.Println(color(blue, "+-----------------------------------------------------------+"))
	fmt.Printf("%s     %s — Project Initializer                          %s\n",
		color(blue, "|"), strings.ToUpper(Config.ProductName), color(blue, "|"))
	fmt.Println(color(blue, "+-----------------------------------------------------------+"))
	fmt.Println()
}

func printCompletion(opts ScaffoldOpts, kp KeyPair) {
	absDir, _ := filepath.Abs(opts.ProjectDir)

	fmt.Println()
	fmt.Println(color(green, "+-----------------------------------------------------------+"))
	fmt.Printf("%s                  Project Created Successfully!            %s\n",
		color(green, "|"), color(green, "|"))
	fmt.Println(color(green, "+-----------------------------------------------------------+"))
	fmt.Println()
	fmt.Printf("Your project is ready at: %s\n", color(blue, absDir))
	fmt.Println()
	fmt.Printf("%s\n", color(yellow, "Your public key (share with T-0 team):"))
	fmt.Println(color(blue, kp.PublicKey))
	fmt.Println()

	fmt.Printf("%s\n", color(yellow, "Next Steps:"))
	fmt.Println()
	step := 1
	fmt.Printf("  %d. Navigate to your project:\n", step)
	fmt.Printf("     %s\n", color(blue, "cd "+opts.ProjectDir))
	fmt.Println()

	// Product-specific steps: what must happen before the project works.
	for _, s := range Config.NextSteps {
		step++
		fmt.Printf("  %d. %s\n", step, s)
		fmt.Println()
	}

	step++
	rs := lookupRunStep(opts.Lang, opts.Role)
	fmt.Printf("  %d. %s\n", step, rs.Label)
	fmt.Printf("     %s\n", color(blue, rs.Command))
	fmt.Println()
}

var defaultRunSteps = map[string]RunStep{
	"go":     {Label: "Run the application:", Command: "go run ./cmd"},
	"node":   {Label: "Install dependencies and run:", Command: "npm install && npm run dev"},
	"python": {Label: "Install dependencies and run:", Command: "uv sync && uv run python -m provider.main"},
	"java":   {Label: "Run the application:", Command: "./gradlew run"},
	"csharp": {Label: "Run the application:", Command: "dotnet run"},
}

func lookupRunStep(lang, role string) RunStep {
	if role != "" {
		if rs, ok := Config.RunSteps[lang+"/"+role]; ok {
			return rs
		}
	}
	if rs, ok := Config.RunSteps[lang]; ok {
		return rs
	}
	return defaultRunSteps[lang]
}

func printUsage() {
	fmt.Printf("Usage: %s <command> [options]\n", Config.ProductName)
	fmt.Println()
	fmt.Println("Commands:")
	fmt.Printf("  init [options] <project-name>  Initialize %s\n", Config.description())
	fmt.Printf("  keygen                         Generate a new secp256k1 keypair\n")
	fmt.Printf("  version                        Show version\n")
	fmt.Println()
	fmt.Printf("Languages: %s\n", strings.Join(Config.Languages, ", "))
	fmt.Println()
	fmt.Println("Init options (before or after the project name):")
	fmt.Println("  --lang string        Language/ecosystem (required)")
	if Config.RoleRequired {
		fmt.Println("  --role string        Role (required)")
	} else if Config.DefaultRole != "" {
		fmt.Printf("  --role string        Role (default: %s)\n", Config.DefaultRole)
	}
	if Config.hasLang("go") {
		fmt.Println("  --module string      Go module path (Go only)")
	}
	if len(Config.JavaRepositories) > 0 {
		fmt.Printf("  --repository string  Java SDK repository: %s (Java only)\n", strings.Join(Config.JavaRepositories, "|"))
	}
	fmt.Println("  --dir string         Target directory (default: ./<project-name>)")
	fmt.Println("  --no-color           Disable colored output")
	fmt.Println("  --version            Show version")
}

func isValidLang(lang string) bool {
	return Config.hasLang(lang)
}

func contains(list []string, s string) bool {
	for _, v := range list {
		if v == s {
			return true
		}
	}
	return false
}

func color(code, text string) string {
	if noColor {
		return text
	}
	return code + text + reset
}
