package com.tripline.controllers;

import com.tripline.models.Ticket;
import com.tripline.repositories.TicketRepository;
import com.tripline.services.PdfTicketService;
import com.tripline.exceptions.ResourceNotFoundException;
import com.tripline.exceptions.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketRepository ticketRepository;
    private final PdfTicketService pdfTicketService;

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> downloadTicket(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) throws IOException {

        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", id));

        // Security check: only the owner of the booking can download the ticket
        if (!ticket.getBooking().getUser().getEmail().equals(userDetails.getUsername())) {
            throw new BadRequestException("Access denied");
        }

        if (ticket.getQrCode() == null) {
            throw new BadRequestException("Ticket is not yet confirmed or QR code is missing");
        }

        byte[] pdfBytes = pdfTicketService.generateTicketPdf(ticket);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=ticket_" + id + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }
}
