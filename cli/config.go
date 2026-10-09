package main

var Config = CLIConfig{
	ProductName:  "usdt-pay",
	Command:      "usdt-pay init",
	Description:  "a new usdt-pay project (acquirer, issuer or lp)",
	RoleRequired: true,
	DefaultRole:  "",
	Languages:    []string{"go", "java", "node", "python"},
	NextSteps:    []string{"NETWORK_PUBLIC_KEY in .env is the sandbox key — your t-0 onboarding contact gives you the production one"},
	RunSteps: map[string]RunStep{
		"go/acquirer": {
			Label:   "Run the application:",
			Command: "go run ./cmd",
		},
		"python/acquirer": {
			Label:   "Install dependencies and run:",
			Command: "uv sync && uv run python -m acquirer.main",
		},
	},
}
