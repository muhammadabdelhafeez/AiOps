# AiOpsAnalysis

An AI-powered IT Operations Analysis platform built with Spring Boot that leverages Azure OpenAI for intelligent alert grouping, pattern recognition, and service topology management using Neo4j graph database.

## Table of Contents

- [Overview](#overview)
- [Key Features](#key-features)
- [Technology Stack](#technology-stack)
- [Architecture](#architecture)
- [Prerequisites](#prerequisites)
- [Installation](#installation)
- [Configuration](#configuration)
- [Project Structure](#project-structure)
- [Database Architecture](#database-architecture)
- [Incident Lifecycle Management](#incident-lifecycle-management)
- [REST API Reference](#rest-api-reference)
- [Web Interface](#web-interface)
- [Development](#development)
- [Deployment](#deployment)
- [Troubleshooting](#troubleshooting)
- [License](#license)
- [Contributing](#contributing)
- [Support](#support)

---

## Overview

AiOpsAnalysis is designed to help IT operations teams manage and analyze alerts more effectively by:

- **Intelligent Alert Grouping**: Using Azure OpenAI embeddings to semantically group related alerts
- **Service Topology Visualization**: Graph-based representation of services and their relationships using Neo4j
- **Pattern Recognition**: Identifying recurring issues and anomalies across your infrastructure
- **Incident Lifecycle Management**: Deterministic incident classification with automated state transitions
- **Resilient Architecture**: Circuit breaker patterns for reliable Azure OpenAI integration
- **SharePoint Integration**: Storing run data and reports in Microsoft SharePoint as system of record
- **Multi-Tenant Support**: Full tenant isolation for enterprise deployments

---

## Key Features

### Intelligent Alert Analysis
- Semantic similarity using Azure OpenAI text-embedding-ada-002 (1536 dimensions)
- Cosine similarity-based alert grouping with configurable thresholds
- Fingerprint-based deduplication (exact and family-level)
- Vector search in Neo4j for fast similarity queries

### Dual Deployment Failover
- Two Azure OpenAI deployments (A and B) with automatic failover
- Resilience4j circuit breakers for fault tolerance
- Configurable failure thresholds and recovery windows

### Graph-Based Topology
- Service dependency mapping with Neo4j
- Alert correlation across services
- Historical pattern analysis
- Graph-based queries for root cause analysis

### Enterprise Features
- Multi-tenant architecture with full data isolation
- Role-based access control (RBAC)
- Comprehensive audit logging
- Microsoft Teams notifications
- SharePoint document storage

---

## Technology Stack

| Category | Technology | Version |
|----------|------------|---------|
| **Framework** | Spring Boot | 3.4.1 |
| **Language** | Java | 21 |
| **Build Tool** | Maven | 3.x |
| **Relational DB** | PostgreSQL JDBC | 42.7.11 |
| **Graph DB** | Neo4j | 5.15.0 |
| **AI/ML** | Azure OpenAI SDK | 1.0.0-beta.12 |
| **Cloud Integration** | Microsoft Graph SDK | 6.4.0 |
| **Identity** | Azure Identity | 1.12.2 |
| **Resilience** | Resilience4j | 2.2.0 |
| **Caching** | Caffeine | (managed by Spring Boot) |
| **Scheduling** | Quartz | (managed by Spring Boot) |
| **Security** | Spring Security | (managed by Spring Boot) |
| **Batch Processing** | Spring Batch | (managed by Spring Boot) |

### Additional Libraries
- **Lombok**: Reduces boilerplate code
- **Jackson**: JSON processing with JSR-310 date/time support
- **Apache Commons Lang3**: Utility functions
- **Commons Codec**: Encoding/decoding utilities
- **DOMPurify**: XSS sanitization for frontend
- **TinyMCE**: Rich text editor for incident notes

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              AiOpsAnalysis                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐        │
│  │   Web UI    │  │  REST API   │  │  Scheduler  │  │   Batch     │        │
│  │  (Static)   │  │ Controllers │  │   (Quartz)  │  │  (Spring)   │        │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘        │
│         │                │                │                │               │
│  ┌──────┴────────────────┴────────────────┴────────────────┴──────┐        │
│  │                        Service Layer                           │        │
│  │  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐            │        │
│  │  │ Incident     │ │ AlertGroup   │ │ Embedding    │            │        │
│  │  │ Service      │ │ Service      │ │ Service      │            │        │
│  │  └──────────────┘ └──────────────┘ └──────────────┘            │        │
│  │  ┌──────────────┐ ┌──────────────┐                             │        │
│  │  │ Graph        │ │ Neo4jSchema  │                             │        │
│  │  │ Service      │ │ Service      │                             │        │
│  │  └──────────────┘ └──────────────┘                             │        │
│  └────────────────────────────────────────────────────────────────┘        │
│         │                │                │                │               │
│  ┌──────┴────────────────┴────────────────┴────────────────┴──────┐        │
│  │                      Data Access Layer                         │        │
│  │  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐            │        │
│  │  │ JPA          │ │ Neo4j        │ │ Azure        │            │        │
│  │  │ Repositories │ │ Repositories │ │ OpenAI       │            │        │
│  │  └──────────────┘ └──────────────┘ └──────────────┘            │        │
│  └────────────────────────────────────────────────────────────────┘        │
└─────────────────────────────────────────────────────────────────────────────┘
         │                │                │                │
    ┌────┴────┐      ┌────┴────┐      ┌────┴────┐      ┌────┴────┐
    │PostgreSQL│      │  Neo4j  │      │ Azure   │      │SharePoint│
    │   DB    │      │  Graph  │      │ OpenAI  │      │  Storage │
    └─────────┘      └─────────┘      └─────────┘      └──────────┘
```

---

## Prerequisites

Before running this application, ensure you have the following installed:

- **Java 21** or higher
  - [Download from Oracle](https://www.oracle.com/java/technologies/downloads/#java21)
  - [Adoptium (Eclipse Temurin)](https://adoptium.net/)
- **Maven 3.6+**
  - [Download Maven](https://maven.apache.org/download.cgi)
- **PostgreSQL 14+**
  - [Download PostgreSQL](https://www.postgresql.org/download/)
- **Neo4j 5.11+** (required for vector index support)
  - [Download Neo4j](https://neo4j.com/download/)
- **Azure Subscription** with Azure OpenAI access
  - [Azure Portal](https://portal.azure.com/)

---

## Installation

### 1. Clone the Repository

```bash
git clone <repository-url>
cd AiOpsAnalysis
```

### 2. Set Up PostgreSQL Database

```sql
-- Create database
CREATE DATABASE aiopsanalysis;

-- Create user (if not using default postgres user)
CREATE USER aiops_user WITH PASSWORD 'your-secure-password';
GRANT ALL PRIVILEGES ON DATABASE aiopsanalysis TO aiops_user;

-- Connect to the database and enable pgcrypto extension
\c aiopsanalysis
CREATE EXTENSION IF NOT EXISTS pgcrypto;
```

The application uses Flyway-style migrations located in `src/main/resources/db/migration/`. The schema will be automatically created on first run.

### 3. Set Up Neo4j Database

1. Start Neo4j server (Community or Enterprise Edition)
2. Open Neo4j Browser at `http://localhost:7474`
3. Set authentication credentials (default: neo4j/password)
4. Execute the schema creation script:

```cypher
// Run each statement from src/main/resources/db/neo4j/V1__init_neo4j_schema.cypher
// Example:
CREATE CONSTRAINT app_id_unique IF NOT EXISTS FOR (a:Application) REQUIRE a.appId IS UNIQUE;
CREATE CONSTRAINT group_id_unique IF NOT EXISTS FOR (g:AlertGroup) REQUIRE g.groupId IS UNIQUE;
// ... (see full script in db/neo4j folder)

// Important: Create vector index for AI-powered similarity search
CREATE VECTOR INDEX group_embedding IF NOT EXISTS 
FOR (g:AlertGroup) ON (g.embedding) 
OPTIONS {indexConfig: {`vector.dimensions`: 1536, `vector.similarity_function`: 'cosine'}};
```

### 4. Configure Azure OpenAI

1. Create Azure OpenAI resource in Azure Portal
2. Deploy `text-embedding-ada-002` model (recommended: deploy in two regions for failover)
3. Deploy GPT model (e.g., `gpt-4`) for reasoning tasks
4. Note the endpoint URLs, API keys, and deployment names

### 5. Configure Application

Copy and customize the configuration file:

```bash
cp src/main/resources/application.properties src/main/resources/application-local.properties
```

Update with your environment settings (see [Configuration](#configuration) section).

### 6. Build and Run

```bash
# Build the project
mvn clean install

# Run the application
mvn spring-boot:run

# Or run with specific profile
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

The application will start on port **8443** by default.

---

## Configuration

### Application Properties Reference

#### PostgreSQL Configuration
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/aiopsanalysis
spring.datasource.username=postgres
spring.datasource.password=your-password
spring.datasource.driver-class-name=org.postgresql.Driver

# JPA/Hibernate
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
```

#### Neo4j Configuration
```properties
spring.neo4j.uri=bolt://localhost:7687
spring.neo4j.authentication.username=neo4j
spring.neo4j.authentication.password=your-neo4j-password
spring.neo4j.database=neo4j

# Vector Index Settings
aiops.neo4j.embedding-dimension=1536
aiops.neo4j.vector-similarity=cosine
aiops.neo4j.hot-window-days=15
```

#### Azure OpenAI Configuration
```properties
# Primary Embedding Deployment
azure.openai.embedding.deployment-a.endpoint=https://your-openai-a.openai.azure.com/
azure.openai.embedding.deployment-a.key=your-api-key-a
azure.openai.embedding.deployment-a.deployment-name=text-embedding-ada-002

# Secondary/Failover Embedding Deployment
azure.openai.embedding.deployment-b.endpoint=https://your-openai-b.openai.azure.com/
azure.openai.embedding.deployment-b.key=your-api-key-b
azure.openai.embedding.deployment-b.deployment-name=text-embedding-ada-002

# GPT Configuration (for reasoning/summaries)
azure.openai.gpt.endpoint=https://your-openai-gpt.openai.azure.com/
azure.openai.gpt.key=your-gpt-api-key
azure.openai.gpt.deployment-name=gpt-4

# Embedding Settings
azure.openai.embedding.model=text-embedding-ada-002
azure.openai.embedding.dimension=1536
```

#### SharePoint Configuration
```properties
azure.sharepoint.tenant-id=your-tenant-id
azure.sharepoint.client-id=your-client-id
azure.sharepoint.client-secret=your-client-secret
azure.sharepoint.site-id=your-site-id
azure.sharepoint.drive-id=your-drive-id
azure.sharepoint.base-folder=/AIOps/runs
```

#### AIOps Platform Settings
```properties
# Data Retention
aiops.retention.window-days=15

# Similarity Thresholds
aiops.similarity.threshold=0.85

# GPT Usage Gates
aiops.gpt.top-n-groups=10
aiops.gpt.max-tokens=4000

# Scheduler
aiops.scheduler.enabled=true
aiops.scheduler.cron=0 0 * * * *
```

#### Incident Lifecycle Settings
```properties
# Reopen window: days within which closed incidents can be reopened
aiops.incident.reopen-window-days=7

# Quiet window: hours of inactivity before auto-closing an incident
aiops.incident.quiet-window-hours=6

# Number of primary groups to include in incident_key hash
aiops.incident.primary-groups-count=10
```

#### Circuit Breaker Configuration
```properties
# Deployment A Circuit Breaker
resilience4j.circuitbreaker.instances.embeddingDeploymentA.failureRateThreshold=50
resilience4j.circuitbreaker.instances.embeddingDeploymentA.waitDurationInOpenState=30s
resilience4j.circuitbreaker.instances.embeddingDeploymentA.slidingWindowSize=10

# Deployment B Circuit Breaker
resilience4j.circuitbreaker.instances.embeddingDeploymentB.failureRateThreshold=50
resilience4j.circuitbreaker.instances.embeddingDeploymentB.waitDurationInOpenState=30s
resilience4j.circuitbreaker.instances.embeddingDeploymentB.slidingWindowSize=10
```

#### Caching Configuration
```properties
spring.cache.type=caffeine
spring.cache.caffeine.spec=maximumSize=10000,expireAfterWrite=1h
```

#### Server Configuration
```properties
server.port=8443
```

---

## Project Structure

```
AiOpsAnalysis/
├── src/
│   ├── main/
│   │   ├── java/org/aiopsanalysis/
│   │   │   ├── AiOpsAnalysisApplication.java    # Main Spring Boot application
│   │   │   ├── SecurityConfig.java              # Spring Security configuration
│   │   │   ├── ServletInitializer.java          # WAR deployment initializer
│   │   │   │
│   │   │   ├── config/                          # Configuration classes
│   │   │   │   ├── AzureOpenAIConfig.java       # Azure OpenAI client setup
│   │   │   │   └── Neo4jConfig.java             # Neo4j database configuration
│   │   │   │
│   │   │   ├── controller/                      # REST API controllers
│   │   │   │   └── IncidentController.java      # Incident management endpoints
│   │   │   │
│   │   │   ├── domain/                          # Domain models
│   │   │   │   ├── model/                       # Enums and value objects
│   │   │   │   │   ├── AlertStatus.java
│   │   │   │   │   ├── CanonicalAlert.java
│   │   │   │   │   ├── Classification.java
│   │   │   │   │   ├── IncidentClassification.java
│   │   │   │   │   ├── IncidentStatus.java
│   │   │   │   │   ├── ResourceType.java
│   │   │   │   │   └── Severity.java
│   │   │   │   │
│   │   │   │   ├── neo4j/                       # Neo4j graph entities
│   │   │   │   │   ├── AlertGroupNode.java
│   │   │   │   │   ├── AlertGroupRelation.java
│   │   │   │   │   ├── AlertOccurrenceNode.java
│   │   │   │   │   ├── AppNode.java
│   │   │   │   │   ├── IncidentGroupRelation.java
│   │   │   │   │   ├── IncidentRefNode.java
│   │   │   │   │   ├── ResourceNode.java
│   │   │   │   │   ├── RunGroupRelation.java
│   │   │   │   │   ├── RunNode.java
│   │   │   │   │   └── ServiceNode.java
│   │   │   │   │
│   │   │   │   └── postgres/                    # JPA entities
│   │   │   │       ├── Application.java
│   │   │   │       ├── Incident.java
│   │   │   │       ├── IncidentEvidence.java
│   │   │   │       ├── IncidentGroup.java
│   │   │   │       ├── IncidentStatusHistory.java
│   │   │   │       ├── Resource.java
│   │   │   │       └── Service.java
│   │   │   │
│   │   │   ├── dto/                             # Data Transfer Objects
│   │   │   │   └── RetrievalPack.java
│   │   │   │
│   │   │   ├── repository/                      # Data access layer
│   │   │   │   └── IncidentRepository.java
│   │   │   │
│   │   │   └── service/                         # Business logic
│   │   │       ├── AlertGroupService.java       # Alert grouping logic
│   │   │       ├── EmbeddingService.java        # Azure OpenAI embeddings
│   │   │       ├── GraphService.java            # Neo4j graph operations
│   │   │       ├── IncidentService.java         # Incident lifecycle
│   │   │       └── Neo4jSchemaService.java      # Neo4j schema management
│   │   │
│   │   └── resources/
│   │       ├── application.properties           # Application configuration
│   │       ├── db/
│   │       │   ├── migration/                   # PostgreSQL migrations
│   │       │   │   └── V1__init_aiops_schema.sql
│   │       │   └── neo4j/                       # Neo4j schema scripts
│   │       │       └── V1__init_neo4j_schema.cypher
│   │       ├── static/                          # Web UI resources
│   │       │   ├── home.html                    # Main dashboard
│   │       │   ├── aiops.html                   # AIOps operations
│   │       │   ├── analytics.html               # Analytics dashboard
│   │       │   ├── incidents.html               # Incident management
│   │       │   ├── services.html                # Service catalog
│   │       │   ├── topology.html                # Service topology graph
│   │       │   ├── css/                         # Stylesheets
│   │       │   ├── js/                          # JavaScript files
│   │       │   ├── dompurify/                   # XSS sanitization
│   │       │   └── vendor/                      # Third-party libraries
│   │       │       └── tinymce/                 # Rich text editor
│   │       └── templates/                       # Thymeleaf templates
│   │
│   └── test/
│       └── java/org/aiopsanalysis/
│           └── AiOpsAnalysisApplicationTests.java
│
├── pom.xml                                      # Maven build configuration
├── mvnw / mvnw.cmd                              # Maven wrapper scripts
├── HELP.md                                      # Spring Boot generated help
└── README.md                                    # This documentation
```

---

## Database Architecture

### PostgreSQL Schema Overview

The PostgreSQL database uses a multi-schema architecture for clean separation of concerns:

| Schema | Purpose | Key Tables |
|--------|---------|------------|
| `public` | Base schema | `tenants` |
| `identity` | User management, RBAC, audit | `users`, `roles`, `user_roles`, `audit_log` |
| `config` | Connector and integration settings | `connectors`, `connector_secrets`, `schedules`, `integration_settings` |
| `cmdb` | Configuration Management Database | `applications`, `services`, `resources`, `service_resources`, `service_dependencies` |
| `ops` | Operational data | `runs`, `run_artifacts`, `outbox_events` |
| `incident` | Incident lifecycle | `incidents`, `incident_groups`, `incident_evidence`, `incident_status_history` |
| `report` | Reporting | `hourly_reports`, `hourly_report_items` |
| `notify` | Notifications | `teams_messages` |

### Key PostgreSQL Tables

#### Incident Table (`incident.incidents`)
```sql
incident_id           UUID PRIMARY KEY
tenant_id             UUID NOT NULL
app_id                UUID NOT NULL
incident_key          TEXT NOT NULL      -- Stable hash for deduplication
title                 TEXT NOT NULL
status                TEXT NOT NULL      -- OPEN, ACKNOWLEDGED, CLOSED, SUPPRESSED
severity              TEXT NOT NULL      -- CRITICAL, HIGH, MEDIUM, LOW
classification_label  TEXT NOT NULL      -- NEW, ONGOING, REOPENED, NEW_KNOWN_PATTERN
first_seen            TIMESTAMPTZ NOT NULL
last_seen             TIMESTAMPTZ NOT NULL
last_closed_at        TIMESTAMPTZ
reopen_count          INT NOT NULL
assigned_to           UUID
pro_summary           TEXT               -- GPT-generated summary
confidence            NUMERIC(3,2)
updated_at            TIMESTAMPTZ NOT NULL
```

### Neo4j Graph Model

#### Node Labels

| Label | Description | Key Properties |
|-------|-------------|----------------|
| `Application` | CMDB application | `appId`, `tenantId`, `name`, `criticality` |
| `Service` | Service within an application | `serviceId`, `tenantId`, `name`, `type` |
| `Resource` | Server/VM/Pod/DB resource | `resourceId`, `tenantId`, `name`, `kind`, `ip` |
| `AlertOccurrence` | Single alert event (HOT window) | `occId`, `tenantId`, `ts`, `message` |
| `AlertGroup` | Deduplicated alert pattern | `groupId`, `tenantId`, `fingerprintExact`, `fingerprintFamily`, `embedding` |
| `IncidentRef` | Mirror of PostgreSQL incident | `incidentId`, `tenantId`, `status` |
| `Run` | Processing run | `runId`, `tenantId`, `startedAt`, `status` |

#### Relationships

| Relationship | From → To | Description |
|--------------|-----------|-------------|
| `HAS_SERVICE` | Application → Service | Application contains service |
| `RUNS_ON` | Service → Resource | Service runs on resource |
| `DEPENDS_ON` | Service → Service | Service dependency |
| `INSTANCE_OF` | AlertOccurrence → AlertGroup | Alert belongs to group |
| `ON_RESOURCE` | AlertOccurrence → Resource | Alert targets resource |
| `IMPACTS_APP` | AlertGroup → Application | Group affects application |
| `RELATED_TO` | AlertGroup → AlertGroup | Correlation edge (similarity) |
| `HAS_GROUP` | IncidentRef → AlertGroup | Incident contains group |
| `CREATED_IN_RUN` | AlertGroup → Run | Group created during run |

#### Vector Index
```cypher
CREATE VECTOR INDEX group_embedding IF NOT EXISTS 
FOR (g:AlertGroup) ON (g.embedding) 
OPTIONS {indexConfig: {
  `vector.dimensions`: 1536, 
  `vector.similarity_function`: 'cosine'
}};
```

---

## Incident Lifecycle Management

### Two Lifecycle Levels

| Lifecycle | Level | Purpose |
|-----------|-------|---------|
| **AlertGroup** | Pattern-level | Groups similar alerts by semantic fingerprint |
| **Incident** | Application-level | Clusters AlertGroups impacting the same application |

### Incident States

```
     ┌─────────┐
     │   NEW   │
     └────┬────┘
          │ (created)
          ▼
     ┌─────────┐     acknowledge     ┌──────────────┐
     │  OPEN   │ ──────────────────► │ ACKNOWLEDGED │
     └────┬────┘                     └───────┬──────┘
          │                                  │
          │ close                            │ close
          │                                  │
          ▼                                  ▼
     ┌─────────┐ ◄───────────────────────────┘
     │ CLOSED  │
     └────┬────┘
          │ (within reopen window + new alerts)
          ▼
     ┌──────────┐
     │ REOPENED │ ──► (returns to OPEN)
     └──────────┘

     ┌────────────┐
     │ SUPPRESSED │  (during maintenance window)
     └────────────┘
```

| State | Description |
|-------|-------------|
| `OPEN` | Active incident requiring attention |
| `ACKNOWLEDGED` | Being worked on by an operator |
| `CLOSED` | Resolved (can be reopened within window) |
| `SUPPRESSED` | Muted during maintenance window |

### Incident Classification (Deterministic)

| Classification | Condition |
|----------------|-----------|
| `NEW` | No prior incident_key in reopen window |
| `ONGOING` | Same incident_key exists and is OPEN/ACKNOWLEDGED |
| `REOPENED` | Was CLOSED, now active within reopen window |
| `NEW_KNOWN_PATTERN` | New incident but AlertGroups have historical matches |

**Important**: Classification is purely database-driven. GPT never decides new/old/reopen - it only writes narrative summaries.

### Incident Key Generation

```
incident_key = SHA-256(tenantId + appId + sorted(primary_group_ids))
```

This stable hash prevents "one extra minor group" from creating a new incident every hour.

### Time Windows

| Window | Default | Purpose | Configuration |
|--------|---------|---------|---------------|
| Reopen Window | 7 days | Period after closure when incident can be reopened | `aiops.incident.reopen-window-days` |
| Quiet Window | 6 hours | Inactivity period before auto-closing | `aiops.incident.quiet-window-hours` |
| Hot Window | 15 days | Neo4j retention for AlertOccurrences | `aiops.neo4j.hot-window-days` |
| Retention Window | 15 days | Data retention period | `aiops.retention.window-days` |

---

## REST API Reference

### Incident Management Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/v1/incidents` | List active incidents |
| `GET` | `/api/v1/incidents/{id}` | Get incident by ID |
| `GET` | `/api/v1/incidents/app/{appId}` | List incidents by application |
| `POST` | `/api/v1/incidents/{id}/acknowledge` | Acknowledge incident |
| `POST` | `/api/v1/incidents/{id}/close` | Close incident |
| `GET` | `/api/v1/incidents/{id}/topology` | Get incident topology (nodes/edges) |
| `GET` | `/api/v1/incidents/updated` | Get recently updated incidents |
| `GET` | `/api/v1/incidents/reopened` | Get reopened incidents |
| `GET` | `/api/v1/incidents/stats` | Get incident statistics |

### Request Headers

| Header | Description | Required |
|--------|-------------|----------|
| `X-Tenant-Id` | Tenant identifier (UUID) | Yes |
| `X-User-Id` | User identifier for actions (UUID) | For mutations |
| `Authorization` | Bearer token | If security enabled |

### Example API Calls

```bash
# List all active incidents
curl -X GET "http://localhost:8443/api/v1/incidents" \
  -H "X-Tenant-Id: your-tenant-uuid" \
  -H "Content-Type: application/json"

# Acknowledge an incident
curl -X POST "http://localhost:8443/api/v1/incidents/{incident-id}/acknowledge" \
  -H "X-Tenant-Id: your-tenant-uuid" \
  -H "X-User-Id: your-user-uuid" \
  -H "Content-Type: application/json"

# Get incident topology for visualization
curl -X GET "http://localhost:8443/api/v1/incidents/{incident-id}/topology" \
  -H "X-Tenant-Id: your-tenant-uuid" \
  -H "Content-Type: application/json"
```

---

## Web Interface

Access the web interface at `http://localhost:8443/`

| Page | URL | Description |
|------|-----|-------------|
| **Home** | `/home.html` | Main dashboard with system overview |
| **AIOps** | `/aiops.html` | AI Operations dashboard - alert grouping and analysis |
| **Analytics** | `/analytics.html` | Analytics and insights - trends and patterns |
| **Incidents** | `/incidents.html` | Incident management - view, acknowledge, resolve |
| **Services** | `/services.html` | Service catalog - CMDB overview |
| **Topology** | `/topology.html` | Service topology graph - visual dependency map |

### Frontend Libraries

- **TinyMCE**: Rich text editor for incident notes and comments
- **DOMPurify**: XSS sanitization for user-generated content
- **Custom CSS/JS**: Modern, responsive design

---

## Development

### Building the Project

```bash
# Clean and build
mvn clean install

# Build without tests
mvn clean install -DskipTests

# Generate WAR file for deployment
mvn clean package

# Build with specific profile
mvn clean install -P production
```

### Running Tests

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=AiOpsAnalysisApplicationTests

# Run with coverage report
mvn test jacoco:report
```

### IDE Setup

#### Lombok Configuration

The project uses Lombok to reduce boilerplate code. Configure your IDE:

**IntelliJ IDEA:**
1. Install "Lombok" plugin from Marketplace
2. Enable annotation processing: Settings → Build → Compiler → Annotation Processors

**Eclipse:**
1. Download lombok.jar from https://projectlombok.org/
2. Run `java -jar lombok.jar` and follow installer

**VS Code:**
1. Install "Lombok Annotations Support for VS Code" extension

#### Code Style

- Follow existing code conventions in the project
- Use meaningful variable and method names
- Add Javadoc for public APIs
- Keep methods focused and under 30 lines when possible

### Local Development Profile

Create `application-local.properties` for local development:

```properties
# Override for local development
spring.datasource.url=jdbc:postgresql://localhost:5432/aiopsanalysis_dev
logging.level.org.aiopsanalysis=DEBUG
logging.level.org.neo4j.driver=DEBUG
```

Run with local profile:
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

---

## Deployment

### WAR Deployment

The application is packaged as a WAR file for deployment to external application servers (Tomcat, WildFly, etc.):

```bash
# Build WAR
mvn clean package

# Deploy to Tomcat
cp target/AiOpsAnalysis-0.0.1-SNAPSHOT.war $TOMCAT_HOME/webapps/
```

### Docker Deployment

```dockerfile
# Dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/AiOpsAnalysis-0.0.1-SNAPSHOT.war app.war
EXPOSE 8443
ENTRYPOINT ["java", "-jar", "/app/app.war"]
```

Build and run:
```bash
docker build -t aiops-analysis:latest .
docker run -p 8443:8443 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host:5432/aiops \
  -e SPRING_NEO4J_URI=bolt://neo4j:7687 \
  aiops-analysis:latest
```

### Docker Compose

```yaml
# docker-compose.yml
version: '3.8'
services:
  app:
    build: .
    ports:
      - "8443:8443"
    environment:
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/aiops
      - SPRING_NEO4J_URI=bolt://neo4j:7687
    depends_on:
      - postgres
      - neo4j

  postgres:
    image: postgres:15-alpine
    environment:
      POSTGRES_DB: aiops
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: secret
    volumes:
      - postgres_data:/var/lib/postgresql/data

  neo4j:
    image: neo4j:5.15-community
    environment:
      NEO4J_AUTH: neo4j/password
    ports:
      - "7474:7474"
      - "7687:7687"
    volumes:
      - neo4j_data:/data

volumes:
  postgres_data:
  neo4j_data:
```

### Production Considerations

1. **Secrets Management**: Use environment variables or a secrets manager (Azure Key Vault, HashiCorp Vault)
2. **HTTPS**: Configure SSL/TLS certificates
3. **Load Balancing**: Deploy behind a load balancer for high availability
4. **Monitoring**: Integrate with monitoring tools (Prometheus, Grafana)
5. **Logging**: Configure centralized logging (ELK Stack, Azure Monitor)
6. **Database Backups**: Set up automated backups for PostgreSQL and Neo4j

---

## Troubleshooting

### Common Issues

#### 1. Lombok Compilation Errors
**Problem**: IDE shows errors like "cannot find symbol" for Lombok-generated methods.
**Solution**: 
- Install Lombok plugin in your IDE
- Enable annotation processing
- Rebuild the project

#### 2. Neo4j Connection Refused
**Problem**: Application fails to connect to Neo4j.
**Solution**:
- Verify Neo4j is running: `neo4j status`
- Check bolt port (7687) is accessible
- Verify credentials in application.properties

#### 3. PostgreSQL Connection Errors
**Problem**: "Connection refused" or authentication failures.
**Solution**:
- Verify PostgreSQL service is running
- Check database credentials
- Ensure pgcrypto extension is enabled

#### 4. Azure OpenAI 401/403 Errors
**Problem**: Authentication failures when calling Azure OpenAI.
**Solution**:
- Verify API keys are correct
- Check endpoint URLs include trailing slash
- Ensure deployment names match your Azure configuration

#### 5. Vector Index Not Found
**Problem**: Similarity queries fail with index errors.
**Solution**:
- Ensure Neo4j version is 5.11 or higher
- Manually create the vector index (see Installation section)
- Verify embedding dimension matches (1536)

### Logging Configuration

Adjust logging levels for debugging:

```properties
# Application logging
logging.level.org.aiopsanalysis=DEBUG

# Database logging
logging.level.org.hibernate.SQL=DEBUG
logging.level.org.hibernate.type.descriptor.sql.BasicBinder=TRACE

# Neo4j logging
logging.level.org.neo4j.driver=DEBUG

# Azure SDK logging
logging.level.com.azure=DEBUG

# Spring Security logging
logging.level.org.springframework.security=DEBUG
```

### Health Check

The application exposes health endpoints (if Spring Actuator is enabled):

```bash
# Check application health
curl http://localhost:8443/actuator/health

# Check specific components
curl http://localhost:8443/actuator/health/db
curl http://localhost:8443/actuator/health/neo4j
```

---

## License

[Add your license information here]

---

## Contributing

We welcome contributions! Please follow these guidelines:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

### Code Review Guidelines

- All code must pass existing tests
- New features require accompanying tests
- Follow the existing code style
- Update documentation as needed

---

## Support

[Add support contact information here]

- **Issue Tracker**: [GitHub Issues URL]
- **Documentation**: [Wiki URL]
- **Email**: [support@example.com]

---

## Changelog

### Version 0.0.1-SNAPSHOT (Current)
- Initial release
- Azure OpenAI integration with dual deployment failover
- Neo4j graph-based service topology
- PostgreSQL multi-schema architecture
- Incident lifecycle management
- Web dashboard with topology visualization
- SharePoint integration for artifact storage
- Resilience4j circuit breakers
- Caffeine caching

---

*Last updated: January 2026*
