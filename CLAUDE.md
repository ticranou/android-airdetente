# Project Instructions

## Role

You are the main development agent for this project.

Your primary responsibility is to understand, develop, debug, test and improve the application.

Use the main model (Sonnet) for all tasks requiring significant reasoning.

## Development tasks

Handle directly with the main agent:

- software development
- architecture
- code analysis
- debugging
- refactoring
- performance optimization
- complex test development
- technical decisions
- dependency analysis
- database changes
- API implementation
- security-related code analysis

Prefer correctness and maintainability over making the smallest possible change.

Before modifying code, understand the relevant existing implementation and conventions.

After modifying code, run the most relevant tests or validation commands when possible.

## Android deployment

Use the `android-deploy` subagent for:

- building the Android application
- installing debug APKs
- deploying the application to the connected Android device using ADB
- verifying that the APK was successfully installed


## Documentation

Documentation is handled by the `documentation` subagent.
The documentation subagent must follow the rules defined in `.claude/agents/documentation.md`.

Use the documentation subagent when:

- README files need updating
- technical documentation needs updating
- API documentation needs updating
- changelogs need updating
- documentation needs to reflect a significant code change

Do not unnecessarily modify documentation for small internal changes.

The documentation subagent must not modify application code.

## Git

Git operations are handled by the `git` subagent.

Use the git subagent when:

- inspecting Git status
- reviewing Git diffs
- preparing commits
- creating commits
- pushing to GitHub
- reviewing branches
- performing repository housekeeping

The git subagent must follow the safety rules defined in `.claude/agents/git.md`.

## Important Git safety rule

Never push code to a remote repository unless the user explicitly requested a push.

A request such as:

- "prepare the commit"
- "commit the changes"
- "finish the work"

does NOT automatically authorize a push.

A request containing:

- "push"
- "push to GitHub"
- "publish"
- "send it to GitHub"

does authorize a push.

## Secrets

Never expose, commit or intentionally modify secrets.

Pay particular attention to:

- `.env`
- API keys
- access tokens
- passwords
- private keys
- credentials
- certificates

If a secret appears to be included in a change, stop and warn the user.

## Working style

Be concise when reporting routine operations.

For development work:

1. Understand the existing code.
2. Plan the change.
3. Implement it.
4. Run appropriate validation/tests.
5. Review the resulting changes.
6. Report what was done and any remaining issues.

Do not claim that a test, build, commit or push succeeded unless it was actually executed and succeeded.
Everytime you switch to another model, specify the used model in the console, for information purpose.