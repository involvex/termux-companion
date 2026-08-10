# Termux Companion

A companion app for Termux with GUI, file explorer, file editor, and AI-powered command autocomplete.

## Features

- **Terminal**: Execute commands in Termux with autocomplete suggestions
- **File Explorer**: Browse Termux home and shared storage
- **File Editor**: Edit files with syntax highlighting
- **AI Autocomplete**: Smart command suggestions using Zen/LLM API

## Setup

1. Install Termux from [F-Droid](https://f-droid.org/packages/com.termux/)
2. In Termux, enable external apps: `echo "allow-external-apps=true" >> ~/.termux/termux.properties`
3. Grant `RUN_COMMAND` permission to this app in Android Settings
4. (Optional) Configure Zen API key in Settings for AI suggestions

## Build

```bash
./gradlew assembleDebug
```

## Architecture

- Kotlin + Jetpack Compose + Material 3
- Hilt for dependency injection
- Room for local database
- MVVM with StateFlow
