# Room Booking API

A RESTful API built with Spring Boot for managing room bookings. This application allows users to book rooms for specific time windows, includes validation to prevent overlapping bookings, and is backed by an in-memory database for easy testing.

## Features

- **Create Bookings:** Book a room for a specific user within a specific time slot.
- **Overlap Prevention:** Robust date/time interval checking to prevent double-booking the same room.
- **Data Validation:** Built-in payload validation using Jakarta Bean Validation to ensure dates are in the future and required fields are present.
- **Clean Error Handling:** Global exception handling that returns structured, readable JSON error responses (including `400 Bad Request` maps).
- **Auto-Initialization:** Automatically populates the database with initial Users, Rooms, and Bookings upon startup using SQL scripts.

## Tech Stack

- **Java** 
- **Spring Boot 3.x**
- **Spring Data JPA / Hibernate** (For database ORM)
- **H2 Database** (In-Memory database for testing)
- **Maven** (Build tool)
- **Lombok** (Boilerplate reduction)

## Getting Started

### Prerequisites
- Java Development Kit (JDK) installed (17 or higher)
- Maven (Optional, as the project includes the Maven Wrapper `./mvnw`)

### Running the Application

1. Open a terminal and navigate to the root directory of the project:
   ```bash
   cd RoomBooking
   ```
2. Start the application using the Maven wrapper:
   ```bash
   ./mvnw spring-boot:run
   ```
   *(On Windows, use `mvnw.cmd spring-boot:run`)*

3. The server will start on `http://localhost:8080`.

### Database Console
Because the application uses an in-memory H2 database, you can view the tables and data through your browser while the application is running:

- **URL:** `http://localhost:8080/h2-console`
- **JDBC URL:** `jdbc:h2:mem:roombookingdb`
- **Username:** `sa`
- **Password:** *(leave blank)*

## API Documentation

### Create a Booking
Creates a new booking for a user.

- **URL:** `/api/bookings`
- **Method:** `POST`
- **Headers:** `Content-Type: application/json`

**Request Payload Example:**
```json
{
  "roomId": 1,
  "userId": 2,
  "startDate": "2026-07-20T10:00:00",
  "endDate": "2026-07-20T12:00:00",
  "numOfAttendance": 2
}
```

**Success Response (HTTP 201 Created):**
```json
{
  "bookingId": 4,
  "roomId": 1,
  "userId": 2,
  "startDate": "2026-07-20T10:00:00",
  "endDate": "2026-07-20T12:00:00",
  "status": "CONFIRMED"
}
```

**Error Responses:**
- `400 Bad Request`: If the input validation fails (e.g., missing fields, dates in the past).
- `500 Internal Server Error`: If there is a scheduling conflict (room already booked) or the User/Room does not exist. *(Note: This can be improved to a `409 Conflict` or `404 Not Found` in future iterations).*

## Architecture

The project follows a standard 3-tier architecture:
- **Controllers (`BookingController`):** Handles incoming HTTP requests and responses.
- **Services (`BookingService`):** Contains the core business logic (e.g., checking for overlaps, orchestrating database saves).
- **Repositories (`BookingRepository`, `RoomRepository`, `UserRepository`):** Interfaces extending `JpaRepository` for data access, including custom JPQL queries.
- **Models / DTOs:** Entities map to the database, while Java `record` classes (DTOs) safely transfer data between the client and server.
