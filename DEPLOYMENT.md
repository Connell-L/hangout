# Deployment Guide

This guide covers both local development and production deployment to Fly.io with automatic GitHub Actions deployment.

## 🔧 Local Development

### Quick Start
```bash
# Copy environment template
cp .env.example .env

# Edit .env with your Discord bot token and database credentials
${EDITOR:-nano} .env

# (Optional) Create local Postgres DB/user using psql
./scripts/setup-db.sh

# Start locally (Maven + local Postgres)
./scripts/dev.sh
```

### Development Features
- Hot reload via Spring DevTools
- Liquibase migrations applied automatically on startup
- Debug logging enabled on `dev` profile

### Development Commands
```bash
# Start locally
./scripts/dev.sh

# Build and run JAR
./mvnw clean package
java -jar target/hangout-*.jar
```

## 🚀 Production Deployment to Fly.io

### Initial Setup

1. **Install Fly CLI**
   ```bash
   curl -L https://fly.io/install.sh | sh
   ```

2. **Login and Create App**
   ```bash
   flyctl auth login
   flyctl launch --no-deploy
   ```
   - Choose your app name (e.g., `hangout-discord-bot`)
   - Select region (e.g., `lhr` for London)
   - Don't deploy yet - we need to set secrets first

3. **Set Environment Variables**
   ```bash
   # Set your Discord bot token
   flyctl secrets set DISCORD_BOT_TOKEN="your_discord_bot_token_here"
   
   # Optional: Set database URL if using external database
   flyctl secrets set DATABASE_URL="postgresql://user:pass@host:port/db"
   ```

### Manual Deployment
```bash
# Deploy manually
./scripts/deploy-fly.sh
# or
flyctl deploy
```

## 🤖 Automatic Deployment with GitHub Actions

### Setup GitHub Actions

1. **Get Fly.io API Token**
   ```bash
   flyctl auth token
   ```
   Copy the token that's displayed.

2. **Add GitHub Secret**
   - Go to your GitHub repository
   - Navigate to **Settings** → **Secrets and variables** → **Actions**
   - Click **New repository secret**
   - Name: `FLY_API_TOKEN`
   - Value: Paste the token from step 1
   - Click **Add secret**

3. **Push to Main Branch**
   ```bash
   git add .
   git commit -m "Add deployment configuration"
   git push origin main
   ```

### GitHub Actions Workflow

The workflow (`.github/workflows/fly-deploy.yml`) will:
- ✅ Run tests on every push to main
- 🚀 Deploy to Fly.io if tests pass
- 📧 Send notifications on deployment status

### Workflow Features
- **Automatic Testing**: Runs Maven tests before deployment
- **Dependency Caching**: Speeds up builds with Maven cache
- **Manual Trigger**: Can be triggered manually from GitHub Actions tab
- **Deployment Protection**: Only deploys if tests pass

## 📊 Monitoring and Management

### Fly.io Commands
```bash
# View application status
flyctl status

# View logs
flyctl logs

# SSH into container
flyctl ssh console

# Scale application
flyctl scale count 2

# View secrets
flyctl secrets list

# Update secrets
flyctl secrets set KEY="value"
```

### Health Checks
- **Local**: http://localhost:8080/actuator/health
- **Production**: https://your-app.fly.dev/actuator/health

## 🔄 Deployment Workflow

### Development → Production Flow
1. **Develop locally** with hot reloading
2. **Commit changes** to feature branch
3. **Create pull request** to main branch
4. **Merge to main** triggers automatic deployment
5. **Monitor deployment** in GitHub Actions
6. **Verify deployment** on Fly.io

### Environment Variables by Environment

| Variable | Development | Production |
|----------|-------------|------------|
| `SPRING_PROFILES_ACTIVE` | `dev` | `prod` |
| `DISCORD_BOT_TOKEN` | From `.env` | Fly.io secret |
| `DATABASE_URL` | Local PostgreSQL | Fly.io PostgreSQL |
| `PORT` | `8080` | Set by Fly.io |

## 🛠️ Troubleshooting

### Common Issues

**Development not starting:**
- Check if `.env` file exists and has correct values
- Ensure Docker is running
- Check if ports 8080 and 5433 are available

**GitHub Actions failing:**
- Verify `FLY_API_TOKEN` is set in repository secrets
- Check if tests are passing locally
- Review GitHub Actions logs for specific errors

**Fly.io deployment failing:**
- Verify secrets are set: `flyctl secrets list`
- Check application logs: `flyctl logs`
- Ensure `fly.toml` configuration is correct

**Discord bot not responding:**
- Verify `DISCORD_BOT_TOKEN` is correct
- Check bot permissions in Discord server
- Review application logs for connection errors

### Getting Help
- **Fly.io Docs**: https://fly.io/docs/
- **GitHub Actions**: https://docs.github.com/en/actions
- **Spring Boot**: https://spring.io/guides
