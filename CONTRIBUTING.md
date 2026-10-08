# Contributing to Sekai Tune

Thank you for your interest in contributing to Sekai Tune! Whether you are reporting bugs, requesting new features, or submitting code improvements, your help is welcome.

---

## Reporting Issues & Requesting Features

Before creating a new issue, please check existing [Issues](https://github.com/rgsekai/sekai-tune/issues) to see if the topic has already been discussed or reported.

- 🐛 **Bug Reports:** Use the [Bug Report template](https://github.com/rgsekai/sekai-tune/issues/new?template=bug_report.yml) and include detailed steps to reproduce, logs (if applicable), device model, and app version.
- 💡 **Feature Requests:** Use the [Feature Request template](https://github.com/rgsekai/sekai-tune/issues/new?template=feature_request.yml) to describe the idea, why it would be beneficial, and any mockups or implementation thoughts.

---

## Submitting Pull Requests

### 1. Workflow
1. **Fork** the repository to your own GitHub account.
2. Create a new topic branch from `main` (e.g., `git checkout -b fix/lyrics-sync`).
3. Follow the build and environment instructions in the [README](README.md#%EF%B8%8F-building-from-source).
4. Keep pull requests focused on a single change or bug fix. Avoid combining unrelated refactors or formatting changes in one PR.
5. Verify and test your changes thoroughly on a **real Android device** or emulator before opening a PR.

### 2. Critical Rules on Secrets & Configurations
To protect the integrity and security of the repository:
- **Never commit private credentials:** Do **not** commit `google-services.json`, custom keystores (`*.keystore`, `*.jks`), `local.properties`, or hardcoded API keys and tokens.
- Keep build scripts and dependencies clean, modular, and aligned with standard Jetpack Compose / Kotlin patterns.

---

## Licensing & Contributions

Sekai Tune is released under the **GNU General Public License v3.0** (GPL-3.0). By submitting a pull request or patch to this repository, you agree that your contributions will be licensed under the terms of the [GPL-3.0 License](LICENSE).

---

## Maintainer Notice

Sekai Tune is a community project maintained by a solo developer in personal spare time. 

Reviewing pull requests, verifying behavior across multiple Android versions, and testing edge cases takes time. Please be patient while your PR is being reviewed!
