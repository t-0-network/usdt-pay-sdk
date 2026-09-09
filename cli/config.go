package main

var Config = CLIConfig{
	ProductName:  "usdt-pay",
	Command:      "usdt-pay init",
	Description:  "a new usdt-pay project (acquirer or issuer)",
	RoleRequired: true,
	DefaultRole:  "",
	Languages:    []string{"java", "node", "python"},
	NextSteps:    []string{"Add NETWORK_PUBLIC_KEY to .env — your t-0 onboarding contact gives you this"},
	RunSteps: map[string]RunStep{
		"python/acquirer": {
			Label:   "Install dependencies and run:",
			Command: "uv sync && uv run python -m acquirer.main",
		},
	},
}
