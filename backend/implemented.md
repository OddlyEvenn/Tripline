# Backend - Implemented Features

This document provides an overview of the backend services, models, and controllers implemented in the TripLine project.

## 🏗️ Architecture
The backend is built using **Java with Spring Boot**, following a standard MVC architecture:
- **Controllers**: Handle HTTP requests and map them to service methods.
- **Services**: Contain business logic.
- **Repositories**: Interface with the database using Spring Data JPA.
- **Models**: Represent the data entities.
- **DTOs**: Data Transfer Objects for communication between layers.

## 🚀 Core Features

### 🔐 Authentication & Security
- **JWT Authentication**: Secure API access using JSON Web Tokens.
- **AuthController**: 
    - Login and Registration.
    - Password Reset / Forgot Password functionality.
- **Security Config**: Configured with `JwtAuthenticationFilter`.

### 🚌 Trip & Station Management
- **StationController**: CRUD operations for transport stations.
- **SearchController**: Advanced searching for available trips between stations.
- **Trip Model**: Manages trip schedules, pricing, and carrier information.

### 📅 Booking & Tickets
- **BookingController**: Handles seat reservations and booking management.
- **Ticket Model**: Manages individual ticket issuance and details.
- **PaymentController**: Integration for processing payments (Success callback handling).

### 🛠️ Administration
- **AdminController**: Restricted endpoints for managing system configuration, carriers, and vehicles.
- **SystemConfig**: Dynamic system-wide settings.

## 🗃️ Data Models
- `User`: Handles identity and roles.
- `Trip`: Core entity for travel schedules.
- `Station`: Departure and arrival points.
- `Booking` & `Ticket`: Reservation lifecycle.
- `Carrier` & `Vehicle`: Transport infrastructure details.

## 🛠️ Tech Stack
- **Spring Boot 3**
- **Spring Security (JWT)**
- **Spring Data JPA**
- **Maven**
