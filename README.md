# Arena Browser Agent (Android)

This project was rebuilt from the original repo configuration into a **safe browser-assistant Android app**.

## What it does

- Chat-first UI
- Real Android `WebView` browser preview
- Multi-tab browsing, including background tabs
- Generic browser actions: open URL, new tab, switch tab, close tab, back, forward, refresh, search
- DOM actions: click CSS selector, type into selector, extract text from selector
- Automatic retry for selector actions
- Live DOM preview updates
- Action history
- Memory notes
- Clipboard aliases such as `{{headline}}`
- Optional Mistral planning from natural-language prompts

## Safety limits

This app intentionally **does not automate**:

- temporary email workflows
- account creation abuse
- OTP / verification harvesting
- bypassing site restrictions

## Setup

1. Open in Android Studio.
2. Create a `.env` file from `.env.example`.
3. Add your own `MISTRAL_API_KEY`.
4. Build and run.

## Example prompts

- `open https://example.com`
- `click button.primary`
- `type hello world into input[name='q']`
- `extract h1 as headline`
- `new tab https://developer.android.com`
- `remember use the second tab for docs`

## Notes

- The previous camera-specific code was removed.
- Do **not** commit real API keys or GitHub tokens.
