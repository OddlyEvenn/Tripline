package com.tripline.services;

import com.tripline.dto.BookingDTO;
import com.tripline.exceptions.BadRequestException;
import com.tripline.exceptions.ResourceNotFoundException;
import com.tripline.models.*;
import com.tripline.repositories.*;
import com.tripline.utils.QrCodeUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final TripRepository tripRepository;
    private final UserRepository userRepository;
    private final TicketRepository ticketRepository;
    private final QrCodeUtils qrCodeUtils;
    private final SeatManagementService seatManagementService;
    private final FlightSeatRepository flightSeatRepository;
    private final BusSeatRepository busSeatRepository;
    private final TrainSeatRepository trainSeatRepository;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    // -------------------------------------------------------
    // Create Booking (PENDING status — awaiting payment)
    // -------------------------------------------------------

    @Transactional
    public BookingDTO.BookingResponse createBooking(String userEmail,
            BookingDTO.CreateBookingRequest request) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Validate all trips exist and have seats
        List<Trip> trips = new ArrayList<>();
        for (Long tripId : request.getTripIds()) {
            Trip trip = tripRepository.findById(tripId)
                    .orElseThrow(() -> new ResourceNotFoundException("Trip", tripId));
            if (trip.getAvailableSeats() < 1) {
                throw new BadRequestException("No available seats on trip " + tripId);
            }
            trips.add(trip);
        }

        // Calculate total price — use seat prices if passengers have seat assignments
        BigDecimal totalPrice = BigDecimal.ZERO;
        for (int i = 0; i < trips.size(); i++) {
            Trip trip = trips.get(i);
            BookingDTO.PassengerDetail passenger = request.getPassengers().size() > i
                    ? request.getPassengers().get(i)
                    : request.getPassengers().get(0);

            BigDecimal legPrice = resolveLegPrice(trip, passenger);
            totalPrice = totalPrice.add(legPrice);
        }

        // Create booking
        Booking booking = Booking.builder()
                .user(user)
                .totalPrice(totalPrice)
                .status(Booking.BookingStatus.PENDING)
                .build();
        booking = bookingRepository.save(booking);

        // Create tickets for each leg
        List<Ticket> tickets = new ArrayList<>();
        for (int i = 0; i < trips.size(); i++) {
            Trip trip = trips.get(i);
            BookingDTO.PassengerDetail passenger = request.getPassengers().size() > i
                    ? request.getPassengers().get(i)
                    : request.getPassengers().get(0);

            BigDecimal legPrice = resolveLegPrice(trip, passenger);

            Ticket ticket = Ticket.builder()
                    .booking(booking)
                    .trip(trip)
                    .passengerName(passenger.getName())
                    .passengerEmail(passenger.getEmail())
                    .passengerPhone(passenger.getPhone())
                    .seatNumber(passenger.getSeatNumber())
                    .coachNumber(passenger.getCoachNumber())
                    .seatClass(passenger.getSeatClass())
                    .berthType(passenger.getBerthType())
                    .legPrice(legPrice)
                    .legOrder(i + 1)
                    .build();
            tickets.add(ticketRepository.save(ticket));
        }

        booking.setTickets(tickets);
        return mapToResponse(booking);
    }

    // -------------------------------------------------------
    // Confirm Booking after successful Stripe payment
    // -------------------------------------------------------

    @Transactional
    public void confirmBooking(Long bookingId, String stripePaymentIntentId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));

        booking.setStatus(Booking.BookingStatus.PAID);
        booking.setStripePaymentIntentId(stripePaymentIntentId);

        // Reduce available seats, generate QR codes, and confirm seats booked
        for (Ticket ticket : booking.getTickets()) {
            Trip trip = ticket.getTrip();
            trip.setAvailableSeats(trip.getAvailableSeats() - 1);
            tripRepository.save(trip);

            // Confirm seat as BOOKED in the relevant seat table
            if (ticket.getSeatNumber() != null && !ticket.getSeatNumber().isBlank()) {
                try {
                    // For trains, build the composite key: "coachNo:seatNo" that
                    // SeatManagementService expects
                    String seatKeyForConfirm = ticket.getSeatNumber();
                    if (trip.getTransportMode() == Vehicle.TransportMode.TRAIN
                            && ticket.getCoachNumber() != null && !ticket.getCoachNumber().isBlank()) {
                        seatKeyForConfirm = ticket.getCoachNumber() + ":" + ticket.getSeatNumber();
                    }
                    seatManagementService.confirmSeatBooked(
                            trip.getId(), seatKeyForConfirm, trip.getTransportMode());
                } catch (Exception e) {
                    // Log but don't abort — booking must still be confirmed
                    org.slf4j.LoggerFactory.getLogger(getClass())
                            .error("Failed to confirm seat {} for trip {}: {}",
                                    ticket.getSeatNumber(), trip.getId(), e.getMessage());
                }
            }

            // Generate QR code
            try {
                String qrContent = "TRIPLINE|TICKET:" + ticket.getId()
                        + "|BOOKING:" + bookingId
                        + "|PASSENGER:" + ticket.getPassengerName()
                        + "|TRIP:" + trip.getId()
                        + "|SEAT:" + (ticket.getSeatNumber() != null ? ticket.getSeatNumber() : "General")
                        + (ticket.getCoachNumber() != null ? "|COACH:" + ticket.getCoachNumber() : "");
                ticket.setQrCode(qrCodeUtils.generateQrCodeBase64(qrContent));
                ticketRepository.save(ticket);
            } catch (Exception e) {
                // QR generation failure should not abort the transaction
            }
        }

        bookingRepository.save(booking);
    }

    // -------------------------------------------------------
    // Cancel Booking
    // -------------------------------------------------------

    @Transactional
    public void cancelBooking(Long bookingId, String userEmail) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));

        if (!booking.getUser().getEmail().equals(userEmail)) {
            throw new BadRequestException("You are not authorized to cancel this booking");
        }
        if (booking.getStatus() == Booking.BookingStatus.CANCELLED) {
            throw new BadRequestException("Booking is already cancelled");
        }
        booking.setStatus(Booking.BookingStatus.CANCELLED);
        bookingRepository.save(booking);
    }

    // -------------------------------------------------------
    // Get user bookings
    // -------------------------------------------------------

    @Transactional(readOnly = true)
    public List<BookingDTO.BookingResponse> getUserBookings(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return bookingRepository.findByUserIdWithDetails(user.getId())
                .stream().map(this::mapToResponse).toList();
    }

    @Transactional(readOnly = true)
    public BookingDTO.BookingResponse getBookingById(Long bookingId, String userEmail) {
        Booking booking = bookingRepository.findByIdWithDetails(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking", bookingId));
        if (!booking.getUser().getEmail().equals(userEmail)) {
            throw new BadRequestException("Access denied");
        }
        return mapToResponse(booking);
    }

    // -------------------------------------------------------
    // Mapper
    // -------------------------------------------------------

    private BookingDTO.BookingResponse mapToResponse(Booking b) {
        BookingDTO.BookingResponse res = new BookingDTO.BookingResponse();
        res.setBookingId(b.getId());
        res.setStatus(b.getStatus().name());
        res.setTotalPrice(b.getTotalPrice());
        res.setCreatedAt(b.getCreatedAt() != null ? b.getCreatedAt().format(FORMATTER) : null);

        List<BookingDTO.TicketDetail> ticketDetails = b.getTickets().stream().map(t -> {
            BookingDTO.TicketDetail td = new BookingDTO.TicketDetail();
            td.setTicketId(t.getId());
            td.setPassengerName(t.getPassengerName());
            td.setSeatNumber(t.getSeatNumber());
            td.setCoachNumber(t.getCoachNumber());
            td.setSeatClass(t.getSeatClass());
            td.setBerthType(t.getBerthType());
            td.setLegOrder(String.valueOf(t.getLegOrder()));
            td.setLegPrice(t.getLegPrice());
            td.setQrCode(t.getQrCode());

            Trip trip = t.getTrip();
            td.setOriginCity(trip.getOriginStation().getCity());
            td.setDestinationCity(trip.getDestinationStation().getCity());
            td.setDepartureTime(trip.getDepartureTime().format(FORMATTER));
            td.setArrivalTime(trip.getArrivalTime().format(FORMATTER));
            td.setTransportMode(trip.getTransportMode().name());
            td.setCarrierName(trip.getVehicle().getCarrier().getName());
            return td;
        }).toList();

        res.setTickets(ticketDetails);
        return res;
    }

    /**
     * Resolves the leg price for a passenger on a trip.
     * If the passenger has a seat number assigned and the seat has its own price
     * (e.g., Business class flight), use that. Otherwise fall back to trip base
     * price.
     */
    private BigDecimal resolveLegPrice(Trip trip, BookingDTO.PassengerDetail passenger) {
        if (passenger.getSeatNumber() == null || passenger.getSeatNumber().isBlank()) {
            return trip.getPrice();
        }
        try {
            switch (trip.getTransportMode()) {
                case FLIGHT -> {
                    return flightSeatRepository
                            .findByTripIdAndSeatNo(trip.getId(), passenger.getSeatNumber())
                            .map(FlightSeat::getPrice)
                            .orElse(trip.getPrice());
                }
                case BUS -> {
                    return busSeatRepository
                            .findByTripIdAndSeatNo(trip.getId(), passenger.getSeatNumber())
                            .map(BusSeat::getPrice)
                            .orElse(trip.getPrice());
                }
                case TRAIN -> {
                    if (passenger.getCoachNumber() != null) {
                        return trainSeatRepository
                                .findByTripIdAndCoachNoAndSeatNo(trip.getId(),
                                        passenger.getCoachNumber(), passenger.getSeatNumber())
                                .map(TrainSeat::getPrice)
                                .orElse(trip.getPrice());
                    }
                }
            }
        } catch (Exception ignored) {
            // Fall back to base price on any error
        }
        return trip.getPrice();
    }
}
