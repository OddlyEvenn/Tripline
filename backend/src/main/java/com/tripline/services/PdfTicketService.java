package com.tripline.services;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.tripline.models.Ticket;
import com.tripline.models.Trip;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class PdfTicketService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("hh:mm a");

    public byte[] generateTicketPdf(Ticket ticket) throws IOException {
        Trip trip = ticket.getTrip();
        String seatDisplay = buildSeatDisplay(ticket);

        String html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8"/>
                    <style>
                        body { font-family: 'Arial', sans-serif; margin: 0; padding: 0; color: #1f2937; }
                        .ticket { width: 100%%; max-width: 800px; margin: auto; border: 1px solid #e5e7eb; border-radius: 12px; overflow: hidden; }
                        .header { background: #2563eb; color: white; padding: 24px; text-align: center; }
                        .header h1 { margin: 0; font-size: 28px; letter-spacing: 2px; }
                        .content { padding: 32px; }
                        .journey { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; }
                        .city { font-size: 24px; font-weight: bold; color: #111827; }
                        .time { font-size: 16px; color: #6b7280; margin-top: 4px; }
                        .seat-highlight { background: #eff6ff; border: 1px solid #93c5fd; border-radius: 8px; padding: 12px 16px; margin: 16px 0; }
                        .seat-highlight .seat-main { font-size: 22px; font-weight: bold; color: #1d4ed8; }
                        .seat-highlight .seat-sub { font-size: 13px; color: #3b82f6; margin-top: 4px; }
                        .details { display: grid; grid-template-columns: 1fr 1fr; gap: 24px; margin-top: 16px; }
                        .label { font-size: 12px; text-transform: uppercase; color: #9ca3af; font-weight: bold; margin-bottom: 4px; }
                        .value { font-size: 16px; font-weight: 600; color: #374151; }
                        .qr-section { text-align: center; margin-top: 32px; padding-top: 32px; border-top: 2px dashed #e5e7eb; }
                        .qr-code { width: 150px; height: 150px; }
                        .footer { background: #f9fafb; padding: 16px; text-align: center; font-size: 12px; color: #9ca3af; }
                    </style>
                </head>
                <body>
                    <div class="ticket">
                        <div class="header">
                            <h1>&#128506; TRIPLINE</h1>
                            <p>Official Travel Electronic Ticket</p>
                        </div>
                        <div class="content">
                            <div class="journey">
                                <div>
                                    <div class="city">%s</div>
                                    <div class="time">%s</div>
                                </div>
                                <div style="font-size: 24px; color: #2563eb;">&#8594;</div>
                                <div style="text-align: right;">
                                    <div class="city">%s</div>
                                    <div class="time">%s</div>
                                </div>
                            </div>

                            <div class="seat-highlight">
                                <div class="label">Seat Assignment</div>
                                <div class="seat-main">%s</div>
                                <div class="seat-sub">%s</div>
                            </div>

                            <div class="details">
                                <div>
                                    <div class="label">Passenger Name</div>
                                    <div class="value">%s</div>
                                </div>
                                <div>
                                    <div class="label">Booking ID</div>
                                    <div class="value">#%d</div>
                                </div>
                                <div>
                                    <div class="label">Transport Mode</div>
                                    <div class="value">%s</div>
                                </div>
                                <div>
                                    <div class="label">Seat Class</div>
                                    <div class="value">%s</div>
                                </div>
                                <div>
                                    <div class="label">Travel Date</div>
                                    <div class="value">%s</div>
                                </div>
                                <div>
                                    <div class="label">Ticket ID</div>
                                    <div class="value">TL-%d</div>
                                </div>
                            </div>

                            <div class="qr-section">
                                <img class="qr-code" src="data:image/png;base64,%s" />
                                <p style="margin-top: 12px; font-weight: bold; color: #2563eb;">SCAN FOR ENTRY</p>
                            </div>
                        </div>
                        <div class="footer">
                            This is a computer-generated ticket. No signature is required.<br/>
                            Tripline Global Services &#169; 2026
                        </div>
                    </div>
                </body>
                </html>
                """
                .formatted(
                        trip.getOriginStation().getCity(),
                        trip.getDepartureTime().format(TIME_FORMATTER),
                        trip.getDestinationStation().getCity(),
                        trip.getArrivalTime().format(TIME_FORMATTER),
                        seatDisplay,
                        buildSeatSubtext(ticket),
                        ticket.getPassengerName(),
                        ticket.getBooking().getId(),
                        trip.getTransportMode().name(),
                        ticket.getSeatClass() != null ? ticket.getSeatClass() : "General",
                        trip.getDepartureTime().format(DATE_FORMATTER),
                        ticket.getId(),
                        ticket.getQrCode() != null ? ticket.getQrCode() : "");

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.useFastMode();
        builder.withHtmlContent(html, null);
        builder.toStream(os);
        builder.run();

        return os.toByteArray();
    }

    private String buildSeatDisplay(Ticket ticket) {
        StringBuilder sb = new StringBuilder();
        if (ticket.getCoachNumber() != null) {
            sb.append(ticket.getCoachNumber()).append(" - ");
        }
        sb.append(ticket.getSeatNumber() != null ? ticket.getSeatNumber() : "General");
        if (ticket.getBerthType() != null) {
            sb.append(" (").append(expandBerth(ticket.getBerthType())).append(")");
        }
        return sb.toString();
    }

    private String buildSeatSubtext(Ticket ticket) {
        StringBuilder parts = new StringBuilder();
        if (ticket.getSeatClass() != null)
            parts.append(ticket.getSeatClass());
        if (ticket.getBerthType() != null) {
            if (!parts.isEmpty())
                parts.append(" | ");
            parts.append(expandBerth(ticket.getBerthType())).append(" Berth");
        }
        if (ticket.getCoachNumber() != null) {
            if (!parts.isEmpty())
                parts.append(" | ");
            parts.append("Coach ").append(ticket.getCoachNumber());
        }
        return !parts.isEmpty() ? parts.toString() : "General Seating";
    }

    private String expandBerth(String berth) {
        return switch (berth) {
            case "L" -> "Lower";
            case "M" -> "Middle";
            case "U" -> "Upper";
            case "SL" -> "Side Lower";
            case "SU" -> "Side Upper";
            default -> berth;
        };
    }
}
