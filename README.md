# Hangout Availability Planner Discord Bot

A Spring Boot Discord bot that helps coordinate group hangouts by creating interactive availability polls with timezone support.

## Features

- 🗓️ **Interactive Hangout Planning**: Create events with multiple time options
- 🌍 **Timezone Support**: Automatic UTC conversion with user-specific timezone display
- 📊 **Real-time Voting**: React with emojis to vote for your availability
- 🎯 **Smart Results**: Automatically identifies the most popular time slots
- 💾 **Persistent Storage**: PostgreSQL database for reliable data storage
- 🔄 **Live Updates**: Vote counts update in real-time as people react

## How It Works

1. **Create an Event**: Use `/hangout` slash command to create a new availability poll
2. **Vote with Reactions**: Users react with number emojis (1️⃣, 2️⃣, etc.) to vote
3. **View Results**: The embed updates to show current vote counts
4. **Find Best Time**: The bot highlights the most popular time slot

## Quick Start (Local)

```bash
# 1) Copy env template and edit values
cp .env.example .env && ${EDITOR:-nano} .env

# 2) (Optional) Create local Postgres DB + user
./scripts/setup-db.sh

# 3) Run locally (Maven + Postgres)
./scripts/dev.sh
```

## Commands

### `/hangout` - Create Availability Poll

Creates a new hangout event with up to 3 time options.

**Required Parameters:**
- `title`: Event title
- `time1_start`: First time option start (format: `YYYY-MM-DD HH:MM`)
- `time1_end`: First time option end (format: `YYYY-MM-DD HH:MM`)

**Optional Parameters:**
- `description`: Event description
- `time1_desc`: Description for first time option
- `time2_start/end/desc`: Second time option
- `time3_start/end/desc`: Third time option

**Example:**
```
 /hangout title:"Movie Night" 
          description:"Let's watch the new Marvel movie!" 
          time1_start:"2024-09-01 19:00" 
          time1_end:"2024-09-01 22:00"
          time1_desc:"Friday evening"
          time2_start:"2024-09-02 14:00" 
          time2_end:"2024-09-02 17:00"
          time2_desc:"Saturday afternoon"
```

### `/help` - List Commands

Shows all available slash and text commands with short descriptions. Output is ephemeral.

### `!help` - List Commands (Text)

Posts the same command list to the channel.

## Voting

### How to Vote
- **Available**: React with the number emoji (1️⃣, 2️⃣, 3️⃣)
- **Maybe Available**: React with ❓ (coming soon)
- **Remove Vote**: React with ❌ to clear all your votes

### Vote Management
- You can vote for multiple time slots
- Votes are automatically saved to the database
- Remove individual votes by unreacting with the number
- Remove all votes by reacting with ❌

## Setup & Installation

### Prerequisites
- Java 17+
- PostgreSQL database
- Discord Bot Token

### Database Setup
1. Create PostgreSQL database named `discordbot`
2. Update `application.yml` with your database credentials:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/discordbot
    username: your_username
    password: your_password
```

### Discord Bot Setup
1. Create a Discord application at https://discord.com/developers/applications
2. Create a bot and copy the token
3. Update `application.yml` with your bot token:

```yaml
token: "YOUR_BOT_TOKEN_HERE"
```

4. Invite bot to your server with these permissions:
   - Send Messages
   - Use Slash Commands
   - Add Reactions
   - Read Message History
   - Embed Links

## Command Registration (Fast Dev Loop)

- Guild-scoped registration is enabled when `discord.bot.guild-id` (or env var `DISCORD_GUILD_ID`) is set. This makes slash command updates appear almost instantly in that guild.
- If not set, the bot falls back to global registration which can take up to an hour to propagate.

Configure for development:

```bash
# Option 1: set environment variable (preferred for local dev)
export DISCORD_GUILD_ID=123456789012345678

# Option 2: set in application.yml
discord:
  bot:
    guild-id: 123456789012345678
```

Commands are defined under `src/main/resources/commands/slash/*.json` and are auto-registered on startup.

### Running the Application

```bash
# Using Maven
./mvnw spring-boot:run

# Or build and run JAR
./mvnw clean package
java -jar target/hangout-0.0.1-SNAPSHOT.jar
```

## API Endpoints

The bot also provides REST API endpoints for external integrations:

### Events
- `GET /api/hangout/events/channel/{channelId}` - Get active events for channel
- `GET /api/hangout/events/{eventId}/timeslots` - Get timeslots for event
- `PUT /api/hangout/events/{eventId}/close` - Close an event

### Voting
- `POST /api/hangout/timeslots/{timeslotId}/vote` - Vote for timeslot
- `DELETE /api/hangout/timeslots/{timeslotId}/vote` - Remove vote
- `GET /api/hangout/events/{eventId}/users/{userDiscordId}/votes` - Get user votes

### Users
- `PUT /api/hangout/users/{userDiscordId}/timezone` - Update user timezone

## Architecture

### Entities
- **Event**: Hangout events with title, description, and metadata
- **Timeslot**: Proposed time slots with emoji reactions
- **User**: Discord users with timezone preferences
- **Availability**: User votes for specific timeslots

### Services
- **HangoutService**: Core business logic for event management
- **DiscordEmbedService**: Creates beautiful Discord embeds
- **TimezoneUtil**: Handles UTC conversion and formatting

### Discord Integration
- **HangoutSlashCommand**: Handles `/hangout` command
- **ReactionListener**: Processes emoji reactions for voting
- **DiscordEventListener**: Registers commands and event handlers

## Timezone Support

The bot stores all times in UTC and converts them for display based on user preferences:

- Default timezone: UTC
- Users can set personal timezone via API
- Time displays include timezone information
- Automatic daylight saving time handling

## Database Schema

```sql
-- Events table
CREATE TABLE events (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    creator_discord_id VARCHAR(255) NOT NULL,
    channel_id VARCHAR(255) NOT NULL,
    message_id VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    deadline TIMESTAMP,
    status VARCHAR(50) NOT NULL
);

-- Timeslots table
CREATE TABLE timeslots (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL REFERENCES events(id),
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NOT NULL,
    description TEXT,
    emoji VARCHAR(10) NOT NULL
);

-- Users table
CREATE TABLE users (
    discord_id VARCHAR(255) PRIMARY KEY,
    username VARCHAR(255) NOT NULL,
    display_name VARCHAR(255),
    timezone VARCHAR(100)
);

-- Availabilities table
CREATE TABLE availabilities (
    id BIGSERIAL PRIMARY KEY,
    user_discord_id VARCHAR(255) NOT NULL REFERENCES users(discord_id),
    event_id BIGINT NOT NULL REFERENCES events(id),
    timeslot_id BIGINT NOT NULL REFERENCES timeslots(id),
    voted_at TIMESTAMP NOT NULL,
    status VARCHAR(50) NOT NULL,
    UNIQUE(user_discord_id, timeslot_id)
);
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

## License

This project is licensed under the MIT License.

## Support

For issues and questions:
1. Check existing GitHub issues
2. Create a new issue with detailed description
3. Include logs and error messages if applicable
