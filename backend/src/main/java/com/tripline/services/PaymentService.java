package com.tripline.services;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import com.tripline.config.StripeConfig;
import com.tripline.dto.PaymentDTO;
import com.tripline.exceptions.BadRequestException;
import com.tripline.exceptions.ResourceNotFoundException;
import com.tripline.models.Booking;
import com.tripline.models.User;
import com.tripline.repositories.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final EmailService emailService;
    private final PdfTicketService pdfTicketService;
    private final StripeConfig stripeConfig;  // injected so Spring eagerly creates it

    @Value("${stripe.webhook.secret}")
    private String stripeWebhookSecret;

    @Value("${stripe.success.url}")
    private String successUrl;

    @Value("${stripe.cancel.url}")
    private String cancelUrl;

    // -------------------------------------------------------
    // Create Stripe Checkout Session
    // -------------------------------------------------------

    @Transactional
    public PaymentDTO.SessionResponse createCheckoutSession(String userEmail,
            PaymentDTO.CreateSessionRequest request)
            throws StripeException {

        if (request.getBookingId() == null) {
            throw new BadRequestException("Booking ID is required");
        }
        Booking booking = bookingRepository.findById(request.getBookingId())
                .orElseThrow(() -> new ResourceNotFoundException("Booking", request.getBookingId()));

        if (!booking.getUser().getEmail().equals(userEmail)) {
            throw new BadRequestException("Access denied");
        }
        if (booking.getStatus() != Booking.BookingStatus.PENDING &&
                booking.getStatus() != Booking.BookingStatus.PAYMENT_INITIATED) {
            throw new BadRequestException(
                    "This booking is not in a payable state (Current status: " + booking.getStatus() + ")");
        }

        // Amount in paise (INR) or cents (USD) — multiply by 100
        long amountInMinorUnit = booking.getTotalPrice()
                .multiply(java.math.BigDecimal.valueOf(100))
                .longValue();

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(successUrl + "?session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(cancelUrl)
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency("inr")
                                .setUnitAmount(amountInMinorUnit)
                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName("Tripline Journey - Booking #" + booking.getId())
                                        .build())
                                .build())
                        .build())
                .putMetadata("bookingId", String.valueOf(booking.getId()))
                .setCustomerEmail(userEmail)
                .build();

        // Explicitly pass API key via RequestOptions — avoids relying on global Stripe.apiKey
        // which may not be set if StripeConfig was not yet initialized (lazy-init issue).
        RequestOptions requestOptions = RequestOptions.builder()
                .setApiKey(stripeConfig.getStripeSecretKey())
                .build();

        log.info("Creating Stripe session for booking {} with amount {} paise",
                booking.getId(), amountInMinorUnit);
        Session session = Session.create(params, requestOptions);

        booking.setStripeSessionId(session.getId());
        booking.setStatus(Booking.BookingStatus.PAYMENT_INITIATED);
        bookingRepository.save(booking);

        return new PaymentDTO.SessionResponse(session.getId(), session.getUrl());
    }

    // -------------------------------------------------------
    // Handle Stripe Webhook
    // -------------------------------------------------------

    @Transactional
    public void handleWebhook(String payload, String sigHeader) {
        log.info("Received webhook payload (first 100 chars): {}",
                payload.length() > 100 ? payload.substring(0, 100) : payload);
        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, stripeWebhookSecret);
        } catch (SignatureVerificationException e) {
            log.error("Webhook Signature Verification Failed! Secret used matches length: {}",
                    stripeWebhookSecret != null ? stripeWebhookSecret.length() : "null");
            throw new BadRequestException("Invalid Stripe webhook signature");
        } catch (Exception e) {
            log.error("Webhook event construction failed: {}", e.getMessage(), e);
            throw new BadRequestException("Invalid Webhook payload");
        }

        log.info("Successfully verified Stripe event: {}", event.getType());

        try {
            switch (event.getType()) {
                case "checkout.session.completed" -> {
                    EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
                    Session session;
                    if (deserializer.getObject().isPresent()) {
                        session = (Session) deserializer.getObject().get();
                    } else {
                        log.warn("Stripe API version mismatch. Fallback deserialization used.");
                        session = (Session) deserializer.deserializeUnsafe();
                    }

                    String bookingIdStr = session.getMetadata().get("bookingId");
                    log.info("Webhook: Session completed. BookingId Metadata: {}", bookingIdStr);

                    if (bookingIdStr == null) {
                        log.error("Webhook Error: bookingId missing in metadata!");
                        return;
                    }

                    Long bookingId = Long.parseLong(bookingIdStr);
                    String paymentIntentId = session.getPaymentIntent();
                    log.info("Webhook: Confirming booking {} with PI {}", bookingId, paymentIntentId);

                    bookingService.confirmBooking(bookingId, paymentIntentId);

                    // Send confirmation email with PDF tickets
                    Booking booking = bookingRepository.findByIdWithDetails(bookingId).orElse(null);
                    if (booking != null) {
                        User user = booking.getUser();

                        List<Map<String, String>> ticketPdfs = new java.util.ArrayList<>();
                        for (com.tripline.models.Ticket ticket : booking.getTickets()) {
                            try {
                                byte[] pdfBytes = pdfTicketService.generateTicketPdf(ticket);
                                String base64Pdf = java.util.Base64.getEncoder().encodeToString(pdfBytes);
                                ticketPdfs.add(Map.of(
                                        "content", base64Pdf,
                                        "name", "Ticket_" + ticket.getId() + ".pdf"));
                            } catch (java.io.IOException e) {
                                log.error("Failed to generate PDF for ticket {}: {}", ticket.getId(), e.getMessage());
                            }
                        }

                        emailService.sendBookingConfirmation(
                                user.getEmail(), user.getName(), bookingId,
                                buildJourneySummary(booking), booking.getTotalPrice().doubleValue(),
                                ticketPdfs);

                        emailService.sendPaymentReceipt(
                                user.getEmail(), user.getName(), bookingId,
                                booking.getTotalPrice().doubleValue(), paymentIntentId);
                    }
                }
                case "payment_intent.payment_failed" -> {
                    log.warn("Payment failed event received");
                    // Could cancel the booking or notify the user
                }
                default -> log.debug("Unhandled event type: {}", event.getType());
            }
        } catch (Exception e) {
            log.error("Unexpected error while processing webhook event ({}): {}", event.getType(), e.getMessage(), e);
            // We do not rethrow the exception. Rethrowing causes Stripe to infinitely
            // retry.
            // If the booking confirmation failed, we log it but return 200 so Stripe stops
            // retrying.
            // In a real system, you'd queue this to a DLQ or retry table.
        }
    }

    private String buildJourneySummary(Booking booking) {
        if (booking.getTickets() == null || booking.getTickets().isEmpty())
            return "";
        StringBuilder sb = new StringBuilder();
        booking.getTickets().stream()
                .sorted(java.util.Comparator.comparingInt(t -> (t.getLegOrder() == null ? 0 : t.getLegOrder())))
                .forEach(t -> sb.append(t.getTrip().getOriginStation().getCity())
                        .append(" → ")
                        .append(t.getTrip().getDestinationStation().getCity())
                        .append(" (").append(t.getTrip().getTransportMode()).append(")\n"));
        return sb.toString();
    }
}
