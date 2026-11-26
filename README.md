# Warehouse Broken Number Ranges: Live Arrival Index

A production-ready, backend-only Spring Boot service designed for high-throughput warehouse logistics. The system tracks package arrivals arriving in arbitrary order and dynamically maintains a sorted collection of non-overlapping consecutive intervals using an incremental `TreeMap` data structure.

---

## 1. Project Overview

In high-volume fulfillment centers, hundreds of thousands of packages are received out of sequential order. Rather than storing every individual ID or repeatedly sorting massive collections upon every arrival, the **Live Arrival Index** merges consecutive numbers on-the-fly into compact intervals $[start, end]$. 

This backend service provides:
- Real-time incremental interval management in $O(\log n)$ time per package arrival.
- Automatic left-extension, right-extension, bridge-merging, and duplicate detection.
- Fast bounded range queries and missing-gap detection in $O(\log k + r)$ time.
- Thread-safe in-memory operations using `ReentrantReadWriteLock`.
- Full OpenAPI / Swagger documentation and Postman API test collection.

---

## 2. Problem Statement

A warehouse receives package IDs as nonnegative integers up to $1,000,000,000$. Packages arrive sparsely and out of sequence. Storing individual IDs or maintaining full boolean arrays of 1 billion elements would exhaust memory and CPU resources. 

The system maintains consecutive arrivals as compact intervals. For example, when IDs arrive in the order `1, 3, 7, 2, 6`:
- `1` $\rightarrow$ `[[1, 1]]`
- `3` $\rightarrow$ `[[1, 1], [3, 3]]`
- `7` $\rightarrow$ `[[1, 1], [3, 3], [7, 7]]`
- `2` connects `1` and `3` $\rightarrow$ `[[1, 3], [7, 7]]`
- `6` and `7` merge $\rightarrow$ `[[1, 3], [6, 7]]`

When ID `4` arrives next to bridge `[[1, 3], [5, 8]]`, it connects both intervals into a single range `[[1, 8]]`.

---

## 3. Technologies Used

- **Java Version**: Java 21 (LTS)
- **Framework**: Spring Boot 3.3.5
- **Build Tool**: Apache Maven 3.9.9
- **Core Index Structure**: `java.util.TreeMap<Long, Long>`
- **Concurrency**: `java.util.concurrent.locks.ReentrantReadWriteLock`
- **Validation**: Jakarta Validation (`spring-boot-starter-validation`)
- **API Documentation**: SpringDoc OpenAPI 3 / Swagger UI (`org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0`)
- **Testing**: JUnit 5, Mockito, AssertJ, Spring MockMvc

---

## 4. Project Architecture

The application adopts a clean, layered architectural pattern:

```
com.warehouse.arrivalindex
├── config
│   └── OpenApiConfig.java               # Swagger / OpenAPI documentation configuration
├── controller
│   └── PackageController.java           # REST Controller exposing endpoints
├── dto
│   ├── BatchResponse.java               # Batch insertion response DTO
│   ├── ErrorResponse.java               # Standardized error payload
│   ├── MissingIntervalsResponse.java    # Bounded missing range response
│   └── RangeIntervalsResponse.java      # Bounded received intervals response
├── exception
│   ├── GlobalExceptionHandler.java      # Centralized HTTP exception translation
│   ├── InvalidPackageIdException.java   # Bad package ID exception
│   └── InvalidRangeException.java       # Invalid query boundary exception
├── model
│   ├── Interval.java                    # Interval domain model [start, end]
│   ├── IntervalResponse.java            # All intervals response model
│   ├── PackageArrival.java              # Single package request body
│   ├── PackageBatchRequest.java         # Batch package request body
│   ├── PackageResponse.java             # Single package response model
│   └── StatisticsResponse.java          # Aggregated system metrics model
├── repository
│   ├── PackageIntervalRepository.java   # In-memory repository interface
│   └── PackageIntervalRepositoryImpl.java # Thread-safe TreeMap interval store
├── service
│   ├── PackageIndexService.java         # Business logic interface
│   └── PackageIndexServiceImpl.java     # Core incremental interval algorithm
└── LiveArrivalIndexApplication.java     # Spring Boot application entry point
```

---

## 5. TreeMap Data Structure Explanation

The system uses `java.util.TreeMap<Long, Long>` as the primary index:
- **Key**: Starting ID of the consecutive interval (`start`).
- **Value**: Ending ID of the consecutive interval (`end`).

### Why TreeMap?
1. **Self-Balancing Red-Black Tree**: Guarantees $O(\log n)$ performance for insertions, removals, and lookups where $n$ is the number of disjoint intervals (at most $50,000$), not the total ID space.
2. **Key Navigation Methods**:
   - `floorEntry(x)`: Locates the entry with the greatest start key $\le x$ in $O(\log n)$ time.
   - `ceilingEntry(x)`: Locates the entry with the least start key $\ge x$ in $O(\log n)$ time.
   - `tailMap(fromKey, true)`: Allows sub-range traversal without scanning unaffected intervals.
3. **Memory Footprint**: Only active disjoint intervals are stored. Storing 10,000 intervals requires less than 2 MB of heap, compared to an 8 GB boolean/long array for IDs up to $1,000,000,000$.

---

## 6. Interval Insertion Algorithm

When package ID $x$ arrives:

1. **Duplicate Check & Preceding Interval**:
   Find $L = \text{floorEntry}(x)$.
   - If $L \neq \text{null}$ and $x \le L.end$, $x$ already belongs to interval $[L.start, L.end]$.
   - Action: TreeMap is unmodified; return status `DUPLICATE`.

2. **Left Adjacency Check**:
   - `connectsLeft` = $(L \neq \text{null} \land L.end + 1 = x)$.

3. **Right Adjacency Check**:
   Find $R = \text{ceilingEntry}(x + 1)$.
   - `connectsRight` = $(R \neq \text{null} \land R.start = x + 1)$.

4. **Operation Execution**:
   - **Case 1: Bridge Merge** (`connectsLeft && connectsRight`):
     Package $x$ bridges $[L.start, L.end]$ and $[R.start, R.end]$.
     Remove $L$ and $R$.
     Insert merged interval $[L.start, R.end]$.
   - **Case 2: Left Extension** (`connectsLeft && !connectsRight`):
     Update $L.end = x$ by replacing entry: `put(L.start, x)`.
   - **Case 3: Right Extension** (`!connectsLeft && connectsRight`):
     Remove entry at $R.start$.
     Insert `put(x, R.end)`.
   - **Case 4: New Interval Creation** (`!connectsLeft && !connectsRight`):
     Insert standalone interval: `put(x, x)`.

---

## 7. Time and Space Complexity

| Operation | Time Complexity | Space Complexity | Description |
|---|---|---|---|
| Single Package Registration | $O(\log n)$ | $O(1)$ | TreeMap floor and ceiling lookup followed by constant updates. |
| Duplicate Check | $O(\log n)$ | $O(1)$ | Direct floorEntry comparison. |
| Batch Registration ($m$ IDs) | $O(m \log n)$ | $O(1)$ | Sequential incremental updates. |
| Get All Intervals | $O(k)$ | $O(k)$ | Linear scan of $k$ active intervals. |
| Bounded Overlapping Intervals | $O(\log k + r)$ | $O(r)$ | Navigates to query start and iterates only the $r$ overlapping intervals. |
| Bounded Missing Ranges | $O(\log k + r)$ | $O(r)$ | Emits missing gaps directly between relevant intervals. |
| Statistics Query | $O(1)$ | $O(1)$ | Counters and `firstKey` / `lastEntry` queries. |

*Notation:*
- $n$: Current number of disjoint intervals ($n \le 50,000$).
- $k$: Total number of stored intervals.
- $r$: Number of intervals overlapping the requested query range ($r \le k$).

---

## 8. REST API Endpoints

| Method | Endpoint | Description | Expected Status |
|---|---|---|---|
| `POST` | `/api/packages` | Register a single package ID | `201 Created` / `200 OK` |
| `POST` | `/api/packages/batch` | Register multiple packages incrementally in order | `200 OK` |
| `GET` | `/api/packages/intervals` | Get all received consecutive intervals | `200 OK` |
| `GET` | `/api/packages/missing` | Get missing intervals in range `[start, end]` | `200 OK` |
| `GET` | `/api/packages/intervals/range` | Get received intervals clipped to `[start, end]` | `200 OK` |
| `GET` | `/api/packages/statistics` | Get unique counts, interval count, min and max ID | `200 OK` |

---

## 9. Example Requests and Responses

### 1. Register Single Package
**Request**: `POST /api/packages`
```json
{
  "packageId": 4
}
```
**Response (Newly Added)** `201 Created`:
```json
{
  "packageId": 4,
  "status": "ADDED",
  "message": "Package successfully registered",
  "currentIntervals": [
    {
      "start": 1,
      "end": 8
    }
  ]
}
```

**Response (Duplicate)** `200 OK`:
```json
{
  "packageId": 4,
  "status": "DUPLICATE",
  "message": "Package ID already exists",
  "currentIntervals": [
    {
      "start": 1,
      "end": 8
    }
  ]
}
```

### 2. Register Batch
**Request**: `POST /api/packages/batch`
```json
{
  "packageIds": [1, 3, 7, 2, 6]
}
```
**Response** `200 OK`:
```json
{
  "totalProcessed": 5,
  "newlyAdded": 5,
  "duplicates": 0,
  "currentIntervals": [
    {
      "start": 1,
      "end": 3
    },
    {
      "start": 6,
      "end": 7
    }
  ]
}
```

### 3. Get All Received Intervals
**Request**: `GET /api/packages/intervals`
**Response** `200 OK`:
```json
{
  "totalReceived": 5,
  "intervalCount": 2,
  "intervals": [
    {
      "start": 1,
      "end": 3
    },
    {
      "start": 6,
      "end": 7
    }
  ]
}
```

### 4. Get Missing Intervals in Range
**Request**: `GET /api/packages/missing?start=1&end=7`
**Response** `200 OK`:
```json
{
  "rangeStart": 1,
  "rangeEnd": 7,
  "missingIntervals": [
    {
      "start": 4,
      "end": 5
    }
  ]
}
```

### 5. Get Received Intervals Clipped to Range
**Request**: `GET /api/packages/intervals/range?start=3&end=10`
**Response** `200 OK`:
```json
{
  "rangeStart": 3,
  "rangeEnd": 10,
  "intervals": [
    {
      "start": 3,
      "end": 5
    },
    {
      "start": 8,
      "end": 10
    }
  ]
}
```

### 6. Get Statistics
**Request**: `GET /api/packages/statistics`
**Response** `200 OK`:
```json
{
  "totalUniquePackages": 5,
  "intervalCount": 2,
  "smallestId": 1,
  "largestId": 7
}
```

---

## 10. Instructions to Run the Project

### Prerequisites
- Java 17+ or Java 21 (LTS)
- Apache Maven 3.8+

### Execution Steps
1. Navigate to the project root directory:
   ```powershell
   cd c:\Users\harsh\Downloads\DSA
   ```
2. Build and run via Maven:
   ```powershell
   mvn spring-boot:run
   ```
   Or execute the packaged executable JAR:
   ```powershell
   java -jar target/arrivalindex-1.0.0.jar
   ```
3. The server starts on port `8080`.
4. Open the Swagger UI in your browser:
   `http://localhost:8080/swagger-ui.html`
5. OpenAPI raw JSON spec is available at:
   `http://localhost:8080/v3/api-docs`

---

## 11. Instructions to Execute Unit Tests

To run all 27 unit, integration, and performance benchmark tests:

```powershell
mvn test
```

To clean and package while executing tests:
```powershell
mvn clean package
```

Test suite breakdown:
- `PackageIndexServiceTest`: Tests all 14 core scenarios (bridge merge, extensions, duplicates, bounds, sparse IDs).
- `PackageIndexPerformanceTest`: Validates 50,000 package IDs insertion under random arrival order and ensures heap consumption remains minimal.
- `PackageControllerTest`: Validates HTTP layer with MockMvc, ensuring correct HTTP status codes, JSON serialization, and error translation.

---

## 12. Postman Testing Instructions

1. Locate the provided Postman collection file in the project root:
   `Warehouse_Live_Arrival_Index.postman_collection.json`
2. Open Postman.
3. Click **Import** and select `Warehouse_Live_Arrival_Index.postman_collection.json`.
4. Ensure the application is running on `http://localhost:8080`.
5. Execute requests sequentially from `1` through `12` to observe real-time interval building, bridge merges, range queries, and validation responses.

---

## 13. Bridge Merge Demonstration

### Initial State
Suppose intervals `[1, 3]` and `[5, 8]` exist in the index:
```
Index: [[1, 3], [5, 8]]
TreeMap Keys: { 1: 3, 5: 8 }
```

### Incoming Arrival: `packageId = 4`
1. The algorithm performs `floorEntry(4)`, returning entry `(1 -> 3)`.
2. Adjacency check to left: `3 + 1 == 4` $\rightarrow$ `connectsLeft = true`.
3. The algorithm performs `ceilingEntry(4 + 1)`, returning entry `(5 -> 8)`.
4. Adjacency check to right: `5 == 4 + 1` $\rightarrow$ `connectsRight = true`.
5. Since both connect:
   - Start of merged interval: `1`
   - End of merged interval: `8`
   - Old entries removed: `remove(1)` and `remove(5)`.
   - New entry stored: `put(1, 8)`.

### Result
```
Index: [[1, 8]]
```

### Algorithmic Advantage
Only the adjacent intervals `[1, 3]` and `[5, 8]` are examined and modified. The rest of the tree remains completely untouched. This avoids any global sorting ($O(N \log N)$) or full array traversals, maintaining strict $O(\log n)$ updates regardless of how many packages have arrived.
