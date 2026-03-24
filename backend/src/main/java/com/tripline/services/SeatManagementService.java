package com.tripline.services;

import com.tripline.exceptions.BadRequestException;
import com.tripline.exceptions.ResourceNotFoundException;
import com.tripline.models.*;
import com.tripline.repositories.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SeatManagementService {

    private static final Logger log = LoggerFactory.getLogger(SeatManagementService.class);
    private static final long LOCK_DURATION_SECONDS = 300L; // 5 minutes

    private final FlightSeatRepository flightSeatRepository;
    private final BusSeatRepository busSeatRepository;
    private final TrainSeatRepository trainSeatRepository;
    private final TripRepository tripRepository;
    private final StringRedisTemplate redisTemplate;

    // ================================================================
    // SEAT GENERATION — called when admin creates a trip
    // ================================================================

    @Async
    @Transactional
    public void generateSeatsForTrip(Trip trip) {
        Vehicle vehicle = trip.getVehicle();
        if (vehicle == null || vehicle.getSeatLayout() == null) {
            log.warn("No seat layout found for vehicle {}. Skipping seat generation.",
                    vehicle != null ? vehicle.getId() : "null");
            return;
        }

        Vehicle.TransportMode mode = trip.getTransportMode();
        switch (mode) {
            case FLIGHT -> generateFlightSeats(trip, vehicle);
            case BUS -> generateBusSeats(trip, vehicle);
            case TRAIN -> generateTrainSeats(trip, vehicle);
        }
    }

    @SuppressWarnings("unchecked")
    private void generateFlightSeats(Trip trip, Vehicle vehicle) {
        if (flightSeatRepository.existsByTripId(trip.getId())) {
            log.info("Flight seats already exist for trip {}. Skipping.", trip.getId());
            return;
        }

        Map<String, Object> layout = vehicle.getSeatLayout();
        int rows = ((Number) layout.getOrDefault("rows", 30)).intValue();
        List<String> columns = (List<String>) layout.getOrDefault("columns",
                Arrays.asList("A", "B", "C", "D", "E", "F"));

        // seat_classes in layout is a map: { "className": { "rows": "1-5", "price":
        // 8000 } }
        // or legacy format: { "1-5": "Business" }
        Map<String, Object> classConfig = (Map<String, Object>) layout.getOrDefault("seat_classes", new HashMap<>());

        // Get pricing per class from vehicle.seatClasses (flat map: { "Business": 8000,
        // "Economy": 2500 })
        Map<String, Object> classPricing = vehicle.getSeatClasses() != null
                ? vehicle.getSeatClasses()
                : new HashMap<>();

        // Build rowRangeToClass map from classConfig
        // Support both formats:
        // Format A (new): { "Business": { "rows": "1-5", "price": 8000 } }
        // Format B (old): { "1-5": "Business" }
        Map<String, String> rowToClass = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : classConfig.entrySet()) {
            Object val = entry.getValue();
            if (val instanceof Map) {
                // Format A: key = class name, val = { rows, price }
                Map<String, Object> classDetail = (Map<String, Object>) val;
                String range = (String) classDetail.getOrDefault("rows", "");
                if (!range.isEmpty()) {
                    rowToClass.put(range, entry.getKey());
                    // Also save price from classDetail into classPricing
                    if (!classPricing.containsKey(entry.getKey()) && classDetail.containsKey("price")) {
                        classPricing.put(entry.getKey(), classDetail.get("price"));
                    }
                }
            } else if (val instanceof String) {
                // Format B: key = "1-5", val = "Business"
                rowToClass.put(entry.getKey(), (String) val);
            }
        }

        List<FlightSeat> seats = new ArrayList<>();
        for (int row = 1; row <= rows; row++) {
            String seatClass = determineClassForRow(row, rowToClass);
            BigDecimal price = getClassPrice(seatClass, classPricing, trip.getPrice());

            for (String col : columns) {
                FlightSeat seat = FlightSeat.builder()
                        .trip(trip)
                        .seatNo(row + col)
                        .rowNo(row)
                        .columnNo(col)
                        .seatClass(seatClass)
                        .status(FlightSeat.SeatStatus.AVAILABLE)
                        .price(price)
                        .build();
                seats.add(seat);
            }
        }

        flightSeatRepository.saveAll(seats);
        log.info("Generated {} flight seats for trip {}", seats.size(), trip.getId());
    }

    @SuppressWarnings("unchecked")
    private void generateBusSeats(Trip trip, Vehicle vehicle) {
        if (busSeatRepository.existsByTripId(trip.getId())) {
            log.info("Bus seats already exist for trip {}. Skipping.", trip.getId());
            return;
        }

        Map<String, Object> layout = vehicle.getSeatLayout();
        int rows = ((Number) layout.getOrDefault("rows", 12)).intValue();
        List<String> columns = (List<String>) layout.getOrDefault("columns",
                Arrays.asList("A", "B", "C", "D"));

        // Window columns: first and last
        Set<String> windowColumns = new HashSet<>();
        if (!columns.isEmpty()) {
            windowColumns.add(columns.get(0));
            windowColumns.add(columns.get(columns.size() - 1));
        }

        List<BusSeat> seats = new ArrayList<>();
        for (int row = 1; row <= rows; row++) {
            for (String col : columns) {
                String seatType = windowColumns.contains(col) ? "Window" : "Aisle";
                BusSeat seat = BusSeat.builder()
                        .trip(trip)
                        .seatNo(row + col)
                        .rowNo(row)
                        .columnNo(col)
                        .seatType(seatType)
                        .status(BusSeat.SeatStatus.AVAILABLE)
                        .price(trip.getPrice())
                        .build();
                seats.add(seat);
            }
        }

        busSeatRepository.saveAll(seats);
        log.info("Generated {} bus seats for trip {}", seats.size(), trip.getId());
    }

    @SuppressWarnings("unchecked")
    private void generateTrainSeats(Trip trip, Vehicle vehicle) {
        if (trainSeatRepository.existsByTripId(trip.getId())) {
            log.info("Train seats already exist for trip {}. Skipping.", trip.getId());
            return;
        }

        Map<String, Object> layout = vehicle.getSeatLayout();
        List<Map<String, Object>> coaches = (List<Map<String, Object>>) layout.getOrDefault("coaches",
                List.of(Map.of("coach_no", "B1", "class", "Sleeper", "seats", 72)));

        Map<String, Object> classPricing = vehicle.getSeatClasses() != null
                ? vehicle.getSeatClasses()
                : new HashMap<>();

        // Include class-specific pricing from coach definitions if not in seatClasses
        for (Map<String, Object> coach : coaches) {
            String className = (String) coach.getOrDefault("class", "Sleeper");
            if (!classPricing.containsKey(className) && coach.containsKey("price")) {
                classPricing.put(className, coach.get("price"));
            }
        }

        // Standard berth pattern in Indian railways: 6 main + 2 side per compartment
        List<String> berthPattern6 = Arrays.asList("L", "M", "U", "L", "M", "U");
        List<String> sidePattern = Arrays.asList("SL", "SU");

        List<TrainSeat> seats = new ArrayList<>();
        for (Map<String, Object> coach : coaches) {
            String coachNo = (String) coach.getOrDefault("coach_no", "B1");
            if (coachNo == null)
                coachNo = (String) coach.getOrDefault("coachNo", "B1");
            String seatClass = (String) coach.getOrDefault("class", "Sleeper");
            int totalSeats = ((Number) coach.getOrDefault("seats", 72)).intValue();

            BigDecimal price = getClassPrice(seatClass, classPricing, trip.getPrice());

            // Assign berths: every 8 seats form a compartment (6 main + 2 side)
            int compartmentSize = 8;
            for (int seatNum = 1; seatNum <= totalSeats; seatNum++) {
                int posInComp = ((seatNum - 1) % compartmentSize); // 0-7
                String berthType;
                if (posInComp < 6) {
                    berthType = berthPattern6.get(posInComp);
                } else {
                    berthType = sidePattern.get(posInComp - 6);
                }

                TrainSeat seat = TrainSeat.builder()
                        .trip(trip)
                        .coachNo(coachNo)
                        .seatNo(String.valueOf(seatNum))
                        .berthType(berthType)
                        .seatClass(seatClass)
                        .status(TrainSeat.SeatStatus.AVAILABLE)
                        .price(price)
                        .build();
                seats.add(seat);
            }
        }

        trainSeatRepository.saveAll(seats);
        log.info("Generated {} train seats for trip {}", seats.size(), trip.getId());
    }

    // ================================================================
    // CLASS AVAILABILITY — for search results (IRCTC-style)
    // ================================================================

    /**
     * Returns a map of class -> available count for a given trip.
     * Used by RouterService to populate search result cards.
     */
    public Map<String, Long> getClassAvailability(Long tripId) {
        Trip trip = tripRepository.findById(tripId).orElse(null);
        if (trip == null)
            return Collections.emptyMap();

        Map<String, Long> availability = new LinkedHashMap<>();

        switch (trip.getTransportMode()) {
            case FLIGHT -> {
                List<Object[]> rows = flightSeatRepository.countAvailableByClass(tripId);
                for (Object[] row : rows) {
                    availability.put((String) row[0], ((Number) row[1]).longValue());
                }
            }
            case BUS -> {
                long count = busSeatRepository.countByTripIdAndStatus(tripId, BusSeat.SeatStatus.AVAILABLE);
                if (count > 0)
                    availability.put("Standard", count);
            }
            case TRAIN -> {
                List<Object[]> rows = trainSeatRepository.countAvailableByClass(tripId);
                for (Object[] row : rows) {
                    availability.put((String) row[0], ((Number) row[1]).longValue());
                }
            }
        }

        return availability;
    }

    // ================================================================
    // GET SEAT MAP — with proactive seat generation
    // ================================================================

    @Transactional
    public Map<String, Object> getSeatMap(Long tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip", tripId));

        // Proactive generation: if no seats exist, generate them now
        boolean hasSeats = switch (trip.getTransportMode()) {
            case FLIGHT -> flightSeatRepository.existsByTripId(tripId);
            case BUS -> busSeatRepository.existsByTripId(tripId);
            case TRAIN -> trainSeatRepository.existsByTripId(tripId);
        };

        if (!hasSeats) {
            log.info("No seats found for trip {}. Generating proactively.", tripId);
            generateSeatsForTrip(trip);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("tripId", tripId);
        result.put("transportMode", trip.getTransportMode().name());

        switch (trip.getTransportMode()) {
            case FLIGHT -> {
                List<FlightSeat> seats = flightSeatRepository.findByTripIdOrderByRowNoAscColumnNoAsc(tripId);
                result.put("seats", seats.stream().map(this::mapFlightSeat).toList());
                // Class availability counts
                List<Object[]> classAvailability = flightSeatRepository.countAvailableByClass(tripId);
                result.put("availability", buildClassAvailabilityMap(classAvailability));
                // Class price map
                result.put("classPrices", buildClassPriceMap(seats));
            }
            case BUS -> {
                List<BusSeat> seats = busSeatRepository.findByTripIdOrderByRowNoAscColumnNoAsc(tripId);
                result.put("seats", seats.stream().map(this::mapBusSeat).toList());
                result.put("totalAvailable",
                        busSeatRepository.countByTripIdAndStatus(tripId, BusSeat.SeatStatus.AVAILABLE));
            }
            case TRAIN -> {
                List<String> coaches = trainSeatRepository.findDistinctCoachNosByTripId(tripId);
                result.put("coaches", coaches);
                result.put("totalAvailable",
                        trainSeatRepository.countByTripIdAndStatus(tripId, TrainSeat.SeatStatus.AVAILABLE));
                // Class availability
                List<Object[]> classAvailability = trainSeatRepository.countAvailableByClass(tripId);
                result.put("availability", buildClassAvailabilityMap(classAvailability));
                // Class prices
                result.put("classPrices", buildTrainClassPriceMap(tripId));
            }
        }
        return result;
    }

    public List<Map<String, Object>> getTrainCoachSeats(Long tripId, String coachNo) {
        List<TrainSeat> seats = trainSeatRepository.findByTripIdAndCoachNoOrderBySeatNoAsc(tripId, coachNo);
        return seats.stream().map(this::mapTrainSeat).toList();
    }

    // ================================================================
    // REDIS SEAT LOCKING
    // ================================================================

    public boolean lockSeat(Long tripId, String seatNo, String userId, Vehicle.TransportMode mode) {
        String redisKey = buildRedisKey(tripId, seatNo);
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(redisKey, userId, Duration.ofSeconds(LOCK_DURATION_SECONDS));

        if (Boolean.TRUE.equals(acquired)) {
            try {
                updateSeatStatusInDb(tripId, seatNo, mode, "LOCK", null);
            } catch (Exception e) {
                redisTemplate.delete(redisKey);
                throw e;
            }
            log.info("Seat {} on trip {} locked by user {}", seatNo, tripId, userId);
            return true;
        } else {
            log.warn("Seat {} on trip {} already locked", seatNo, tripId);
            return false;
        }
    }

    public void unlockSeat(Long tripId, String seatNo, String userId, Vehicle.TransportMode mode) {
        String redisKey = buildRedisKey(tripId, seatNo);
        String currentOwner = redisTemplate.opsForValue().get(redisKey);

        if (userId.equals(currentOwner)) {
            redisTemplate.delete(redisKey);
            updateSeatStatusInDb(tripId, seatNo, mode, "UNLOCK", null);
            log.info("Seat {} on trip {} unlocked by user {}", seatNo, tripId, userId);
        } else {
            log.warn("User {} tried to unlock seat {} but it's owned by {}", userId, seatNo, currentOwner);
            throw new BadRequestException("You do not own the lock for seat " + seatNo);
        }
    }

    @Transactional
    public void confirmSeatBooked(Long tripId, String seatNo, Vehicle.TransportMode mode) {
        String redisKey = buildRedisKey(tripId, seatNo);
        redisTemplate.delete(redisKey);
        updateSeatStatusInDb(tripId, seatNo, mode, "BOOK", null);
        log.info("Seat {} on trip {} confirmed as BOOKED", seatNo, tripId);
    }

    public long getRemainingLockSeconds(Long tripId, String seatNo) {
        String redisKey = buildRedisKey(tripId, seatNo);
        Long ttl = redisTemplate.getExpire(redisKey);
        return ttl != null && ttl > 0 ? ttl : 0;
    }

    // ================================================================
    // HELPERS
    // ================================================================

    private void updateSeatStatusInDb(Long tripId, String seatNo, Vehicle.TransportMode mode, String action,
            String coachNo) {
        switch (mode) {
            case FLIGHT -> {
                FlightSeat seat = flightSeatRepository.findByTripIdAndSeatNo(tripId, seatNo)
                        .orElseThrow(() -> new ResourceNotFoundException("FlightSeat not found: " + seatNo));
                seat.setStatus(mapActionToFlightStatus(action));
                seat.setLockedUntil(
                        "LOCK".equals(action) ? LocalDateTime.now().plusSeconds(LOCK_DURATION_SECONDS) : null);
                flightSeatRepository.save(seat);
            }
            case BUS -> {
                BusSeat seat = busSeatRepository.findByTripIdAndSeatNo(tripId, seatNo)
                        .orElseThrow(() -> new ResourceNotFoundException("BusSeat not found: " + seatNo));
                seat.setStatus(mapActionToBusStatus(action));
                seat.setLockedUntil(
                        "LOCK".equals(action) ? LocalDateTime.now().plusSeconds(LOCK_DURATION_SECONDS) : null);
                busSeatRepository.save(seat);
            }
            case TRAIN -> {
                // For trains, seatNo is passed as "coachNo:seatNo"
                String[] parts = seatNo.split(":", 2);
                String coach = parts.length == 2 ? parts[0] : coachNo;
                String actualSeatNo = parts.length == 2 ? parts[1] : seatNo;
                TrainSeat seat = trainSeatRepository.findByTripIdAndCoachNoAndSeatNo(tripId, coach, actualSeatNo)
                        .orElseThrow(() -> new ResourceNotFoundException("TrainSeat not found: " + seatNo));
                seat.setStatus(mapActionToTrainStatus(action));
                seat.setLockedUntil(
                        "LOCK".equals(action) ? LocalDateTime.now().plusSeconds(LOCK_DURATION_SECONDS) : null);
                trainSeatRepository.save(seat);
            }
        }
    }

    private FlightSeat.SeatStatus mapActionToFlightStatus(String action) {
        return switch (action) {
            case "LOCK" -> FlightSeat.SeatStatus.LOCKED;
            case "BOOK" -> FlightSeat.SeatStatus.BOOKED;
            default -> FlightSeat.SeatStatus.AVAILABLE;
        };
    }

    private BusSeat.SeatStatus mapActionToBusStatus(String action) {
        return switch (action) {
            case "LOCK" -> BusSeat.SeatStatus.LOCKED;
            case "BOOK" -> BusSeat.SeatStatus.BOOKED;
            default -> BusSeat.SeatStatus.AVAILABLE;
        };
    }

    private TrainSeat.SeatStatus mapActionToTrainStatus(String action) {
        return switch (action) {
            case "LOCK" -> TrainSeat.SeatStatus.LOCKED;
            case "BOOK" -> TrainSeat.SeatStatus.BOOKED;
            default -> TrainSeat.SeatStatus.AVAILABLE;
        };
    }

    private String buildRedisKey(Long tripId, String seatNo) {
        return "seat_lock:" + tripId + ":" + seatNo;
    }

    /**
     * Determine seat class for a row number, using a map of "start-end" ->
     * "ClassName".
     */
    private String determineClassForRow(int row, Map<String, String> rowRangeToClass) {
        for (Map.Entry<String, String> entry : rowRangeToClass.entrySet()) {
            String range = entry.getKey();
            if (range.contains("-")) {
                String[] parts = range.split("-");
                try {
                    int start = Integer.parseInt(parts[0].trim());
                    int end = Integer.parseInt(parts[1].trim());
                    if (row >= start && row <= end)
                        return entry.getValue();
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return "Economy";
    }

    /**
     * Legacy single-call version — converts nested Map to rowToClass and calls
     * determineClassForRow.
     */
    @SuppressWarnings("unused")
    private String determineSeatClass(int row, Map<String, Object> seatClassRanges) {
        Map<String, String> rangeMap = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : seatClassRanges.entrySet()) {
            if (e.getValue() instanceof String)
                rangeMap.put(e.getKey(), (String) e.getValue());
        }
        return determineClassForRow(row, rangeMap);
    }

    private BigDecimal getClassPrice(String seatClass, Map<String, Object> classPricing, BigDecimal basePrice) {
        if (classPricing.containsKey(seatClass)) {
            Object priceObj = classPricing.get(seatClass);
            if (priceObj instanceof Number) {
                return BigDecimal.valueOf(((Number) priceObj).doubleValue());
            }
        }
        return basePrice;
    }

    private Map<String, Object> mapFlightSeat(FlightSeat s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("seatNo", s.getSeatNo());
        m.put("rowNo", s.getRowNo());
        m.put("columnNo", s.getColumnNo());
        m.put("seatClass", s.getSeatClass());
        m.put("status", s.getStatus().name());
        m.put("price", s.getPrice());
        return m;
    }

    private Map<String, Object> mapBusSeat(BusSeat s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("seatNo", s.getSeatNo());
        m.put("rowNo", s.getRowNo());
        m.put("columnNo", s.getColumnNo());
        m.put("seatType", s.getSeatType());
        m.put("status", s.getStatus().name());
        m.put("price", s.getPrice());
        return m;
    }

    private Map<String, Object> mapTrainSeat(TrainSeat s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("coachNo", s.getCoachNo());
        m.put("seatNo", s.getSeatNo());
        m.put("berthType", s.getBerthType());
        m.put("seatClass", s.getSeatClass());
        m.put("status", s.getStatus().name());
        m.put("price", s.getPrice());
        return m;
    }

    private Map<String, Long> buildClassAvailabilityMap(List<Object[]> rows) {
        Map<String, Long> map = new LinkedHashMap<>();
        for (Object[] row : rows) {
            map.put((String) row[0], ((Number) row[1]).longValue());
        }
        return map;
    }

    /** Build a map of seatClass -> lowestPrice from all seats (for display). */
    private Map<String, BigDecimal> buildClassPriceMap(List<FlightSeat> seats) {
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        for (FlightSeat s : seats) {
            map.merge(s.getSeatClass(), s.getPrice(), (a, b) -> a.compareTo(b) <= 0 ? a : b);
        }
        return map;
    }

    private Map<String, BigDecimal> buildTrainClassPriceMap(Long tripId) {
        List<TrainSeat> sample = trainSeatRepository.findByTripIdOrderByCoachNoAscSeatNoAsc(tripId);
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        for (TrainSeat s : sample) {
            map.merge(s.getSeatClass(), s.getPrice(), (a, b) -> a.compareTo(b) <= 0 ? a : b);
        }
        return map;
    }
}
