# Contributing to AdSim

Thank you for your interest in contributing to AdSim!

## Development Setup

### Prerequisites

- JDK 21+
- Node.js 20+
- npm
- Docker & Docker Compose

### Backend (Kotlin)

```bash
cd server
./gradlew bootRun
```

### Frontend (React + Vite)

```bash
cd web
npm install
npm run dev
```

## How to Contribute

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/your-feature`)
3. Make your changes
4. Run tests (`cd server && ./gradlew test`)
5. Commit your changes
6. Push to your fork
7. Open a Pull Request against `main`

## Commit Message Format

```
type: description
```

Types: `feat`, `fix`, `refactor`, `docs`, `test`, `chore`

## Code Style

- **Kotlin**: Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- **TypeScript/React**: ESLint (configured in project)

## Reporting Issues

Use GitHub Issues. Please include:
- Steps to reproduce
- Expected vs actual behavior
- Environment details (OS, JDK version, Node version)

## License

By contributing, you agree that your contributions will be licensed under the Apache License 2.0.
