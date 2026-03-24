# Tripline: Multi-Modal Travel Planning & Database System

Tripline is an advanced database-driven platform designed to revolutionize travel planning by integrating diverse transportation modes—Flights, Trains, and Buses—into a single, optimized itinerary. Unlike traditional booking systems that focus on a single mode of transport, Tripline treats travel as a graph-based optimization problem, providing users with the fastest, cheapest, or most balanced routes by combining different carriers and modes.

---

## 📝 Project Overview

### 1. Vision & Problem Statement
Current travel platforms cater primarily to siloed transportation modes. Tripline addresses the fragmentation by unifying the transport infrastructure into a cohesive network model. By integrating **Spring Boot**'s robust processing power with **PostgreSQL**'s relational reliability, the system provides a seamless experience for cross-modal itinerary generation.

---

## 🛠️ Technology Stack

The selection of the technology stack for Tripline is centered around reliability, geospatial efficiency, and high-performance transaction management.

### 1. Backend: Spring Boot (Java)
**Spring Boot** serves as the core backend framework, providing a robust ecosystem for building enterprise-grade REST APIs. It is utilized for:
- **API Management**: Orchestrating multi-modal route requests and managing user interactions.
- **Business Logic**: Implementing complex routing algorithms and fare calculation engines.
- **Transactional Integrity**: Leveraging Spring's `@Transactional` abstraction to ensure ACID compliance during multi-leg booking processes.
- **Database Integration**: Utilizing Spring Data JPA and Hibernate for seamless interaction with the PostgreSQL persistence layer.

### 2. Database: PostgreSQL on Neon DB
The primary persistence layer is **PostgreSQL**, hosted on **Neon DB**, a serverless cloud-resident database platform. 
- **Serverless Scalability**: Neon DB allows for independent scaling of compute and storage, providing high availability and performance for analytical search queries.
- **Cloud Native**: Ensures that the database remains highly available and provides efficient branching for development and testing environments.

### 3. Spatial Extension: PostGIS
**PostGIS** is integrated into the PostgreSQL environment to handle the complex geospatial requirements of a global travel system.
- **Spatial Indexing**: Utilizing GIST indexes to accelerate geographic searches for stations and hubs.
- **Nearest Station Search**: Employing KNN (K-Nearest Neighbor) algorithms to find the closest transport nodes relative to a user's location.
- **Geographic Calculations**: Performing precise distance and trajectory calculations across diverse coordinate systems.

### 4. Payment Gateway: Stripe (Developer Integration)
**Stripe** is integrated as the primary payment processing gateway, utilizing the **Secret Key (SK)** for secure server-side transactions.
- **Server-Side Authentication**: All payment operations are authenticated using the Stripe **SK key** (`sk_live_*` / `sk_test_*`), ensuring that sensitive payment logic is never exposed to the client.
- **Payment Intents API**: Implements the `PaymentIntent` workflow to handle the full lifecycle of a booking payment—creation, confirmation, and capture—with support for multi-currency transactions.
- **Webhook Event Handling**: Listens for Stripe webhook events (`payment_intent.succeeded`, `charge.refunded`, `payment_intent.payment_failed`) to asynchronously update booking statuses and trigger downstream processes like ticket generation.
- **Refund & Cancellation Management**: Supports full and partial refunds via the Stripe Refunds API, tightly coupled with the booking cancellation logic in the Spring Boot service layer.
- **Secure Tokenization**: Leverages Stripe Elements on the frontend to tokenize card details, ensuring the backend only handles tokens—never raw card numbers—maintaining **PCI-DSS** compliance.

---

## 🌐 Graph-Based Routing Engine

Tripline treats the entire transportation infrastructure as a massive, directed, and weighted **Multi-Modal Graph Network**.

### 1. The Graph Infrastructure
- **Nodes**: Represent specific transport hubs like international airports, railway stations, and metropolitan bus terminals.
- **Edges**: Represent scheduled routes between nodes. Each edge carries weights representing **Time**, **Cost**, and **Carbon Footprint**.
- **Mode Switching Edges**: Special zero-distance or short-walking edges that connect different transport modes at the same hub (e.g., an airport terminal to its integrated train station).

### 2. Core Routing Algorithms

#### A. Dijkstra's Algorithm
Dijkstra's is the backbone for finding the **guaranteed shortest path**. 
- **Use Case**: Best for scenarios where the user prioritizes the absolute lowest cost or shortest travel time across a dense network of local connections (e.g., inter-city train networks).
- **Optimization**: We implement Dijkstra with a priority queue to ensure $O((E + V) \log V)$ performance.

#### B. A* Search Algorithm
A* enhances Dijkstra by using a **Heuristic function** $h(n)$ to guide the search towards the destination.
- **Heuristic**: We use the **Haversine Distance** (Great Circle distance) as a lower bound for $h(n)$ to ensure the algorithm remains "admissible" and "consistent."
- **Use Case**: Primarily used for long-distance multi-modal travel where the search space is vast. A* significantly reduces the number of nodes visited by focusing on the geographic direction of the destination.

### 3. Dynamic Weighting & Re-Routing
The system supports **Dynamic Cost Functions**, allowing users to balance efficiency:
$f(n) = w_1 \cdot \text{time} + w_2 \cdot \text{cost} + w_3 \cdot \text{transfers}$

---

## 🚀 What We're Gonna Do (Core Features)

- **Intelligent Route Generation**: Uses algorithms (A*, Dijkstra) to find optimal multi-modal paths.
- **Transaction Integrity**: Implements robust concurrency control and two-phase commits to ensure that seat bookings across different carriers are atomic.
- **Dynamic Pricing & Availability**: Real-time integration with provider APIs to reflect current inventory.
- **Geospatial Route Optimization**: Leveraging **PostGIS** to calculate the most efficient connections based on real-world geographic proximity between airports, railway stations, and bus terminals.
- **Secure Payment Processing**: End-to-end payment handling via **Stripe Payment Gateway** using server-side SK key authentication, Payment Intents API for multi-leg booking charges, webhook-driven status updates, and PCI-DSS compliant card tokenization.
