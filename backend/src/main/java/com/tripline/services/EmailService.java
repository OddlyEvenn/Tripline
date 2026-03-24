package com.tripline.services;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * EmailService sends transactional emails through the Brevo v3 REST API.
 * Uses RestTemplate with the X-API-Key header — no SMTP, no SDK.
 *
 * Brevo API docs: https://developers.brevo.com/reference/sendtransacemail
 */
@Service
@RequiredArgsConstructor
public class EmailService {

  private static final Logger log = LoggerFactory.getLogger(EmailService.class);
  private static final String BREVO_API_URL = "https://api.brevo.com/v3/smtp/email";

  private final RestTemplate restTemplate;

  @Value("${brevo.api.key}")
  private String brevoApiKey;

  @Value("${brevo.sender.email}")
  private String senderEmail;

  @Value("${brevo.sender.name}")
  private String senderName;

  @Value("${frontend.url}")
  private String frontendUrl;

  // -------------------------------------------------------
  // Booking Confirmation with PDF Attachments
  // -------------------------------------------------------

  @Async
  public void sendBookingConfirmation(String toEmail, String toName, Long bookingId,
      String journeySummary, double totalPrice, List<Map<String, String>> attachments) {
    String subject = "✈️ Your TripLine Tickets - Booking #" + bookingId;
    String html = """
        <html><body style="font-family:Arial,sans-serif;max-width:600px;margin:auto;color:#374151;">
          <div style="background:#2563EB;color:white;padding:32px;text-align:center;border-radius:12px 12px 0 0;">
            <h1 style="margin:0;font-size:32px;letter-spacing:2px;">🗺️ TRIPLINE</h1>
            <h2 style="margin:8px 0 0;font-weight:300;">Booking Confirmed!</h2>
          </div>
          <div style="padding:32px;border:1px solid #e5e7eb;border-top:none;border-radius:0 0 12px 12px;background:#ffffff;">
            <p style="font-size:18px;">Hi <strong>%s</strong>,</p>
            <p>Pack your bags! Your journey <strong>#%d</strong> is officially confirmed.</p>

            <div style="background:#f9fafb;padding:20px;border-radius:12px;margin:24px 0;border:1px solid #f3f4f6;">
              <h3 style="margin-top:0;color:#111827;font-size:14px;text-transform:uppercase;letter-spacing:1px;">Journey Summary</h3>
              <pre style="white-space:pre-wrap;font-family:inherit;font-size:16px;color:#4b5563;margin:0;">%s</pre>
            </div>

            <div style="display:flex;justify-content:space-between;align-items:center;padding:16px 0;border-top:1px solid #e5e7eb;">
              <span style="color:#6b7280;">Total Paid:</span>
              <span style="font-size:24px;font-weight:bold;color:#2563EB;">₹%.2f</span>
            </div>

            <p style="margin-top:32px;font-size:14px;color:#6b7280;">
              Your electronic tickets are attached to this email as PDF files. You can also view them anytime in your dashboard.
            </p>

            <div style="text-align:center;margin-top:24px;">
              <a href="%s/dashboard"
                 style="display:inline-block;background:#2563EB;color:white;padding:14px 32px;border-radius:8px;text-decoration:none;font-weight:bold;box-shadow:0 4px 6px -1px rgba(37, 99, 235, 0.2);">
                View and Manage Booking
              </a>
            </div>
          </div>
          <div style="text-align:center;padding:24px;font-size:12px;color:#9ca3af;">
            Tripline Global Services &copy; 2026 | Safe travels!
          </div>
        </body></html>
        """
        .formatted(toName, bookingId, journeySummary, totalPrice, frontendUrl);

    sendEmail(toEmail, toName, subject, html, attachments);
  }

  // -------------------------------------------------------
  // Payment Receipt
  // -------------------------------------------------------

  @Async
  public void sendPaymentReceipt(String toEmail, String toName, Long bookingId,
      double amount, String transactionId) {
    String subject = "💳 Payment Receipt - Tripline Booking #" + bookingId;
    String html = """
        <html><body style="font-family:Arial,sans-serif;max-width:600px;margin:auto;">
          <div style="background:#16a34a;color:white;padding:20px;text-align:center;border-radius:8px 8px 0 0;">
            <h1>🗺️ Tripline</h1><h2>Payment Successful</h2>
          </div>
          <div style="padding:24px;border:1px solid #e5e7eb;border-top:none;border-radius:0 0 8px 8px;">
            <p>Hi <strong>%s</strong>, payment for booking <strong>#%d</strong> received.</p>
            <table style="width:100%%;border-collapse:collapse;margin:16px 0;">
              <tr>
                <td style="padding:8px;border:1px solid #e5e7eb;">Amount Paid</td>
                <td style="padding:8px;border:1px solid #e5e7eb;font-weight:bold;">₹%.2f</td>
              </tr>
              <tr>
                <td style="padding:8px;border:1px solid #e5e7eb;">Transaction ID</td>
                <td style="padding:8px;border:1px solid #e5e7eb;">%s</td>
              </tr>
            </table>
          </div>
        </body></html>
        """.formatted(toName, bookingId, amount, transactionId);

    sendEmail(toEmail, toName, subject, html, null);
  }

  @Async
  public void sendVerificationOtp(String toEmail, String toName, String otp) {
    String subject = "Verification OTP - Tripline";
    String html = """
        <html><body style="font-family:Arial,sans-serif;max-width:600px;margin:auto;">
          <div style="background:#2563EB;color:white;padding:20px;text-align:center;border-radius:8px 8px 0 0;">
            <h1>🗺️ Tripline</h1><h2>Email Verification</h2>
          </div>
          <div style="padding:24px;border:1px solid #e5e7eb;border-top:none;border-radius:0 0 8px 8px;">
            <p>Hi <strong>%s</strong>,</p>
            <p>Your verification OTP is:</p>
            <div style="background:#f3f4f6;padding:16px;border-radius:6px;margin:16px 0;text-align:center;">
              <h1 style="letter-spacing:8px;font-family:monospace;margin:0;color:#2563EB;">%s</h1>
            </div>
            <p>This code will expire in 10 minutes.</p>
          </div>
        </body></html>
        """
        .formatted(toName, otp);

    sendEmail(toEmail, toName, subject, html, null);
  }

  // -------------------------------------------------------
  // Password Reset
  // -------------------------------------------------------

  @Async
  public void sendPasswordResetEmail(String toEmail, String toName, String token) {
    String resetLink = frontendUrl + "/reset-password?token=" + token;
    String subject = "🔐 Reset Your Tripline Password";
    String html = """
        <html><body style="font-family:Arial,sans-serif;max-width:600px;margin:auto;">
          <div style="background:#7c3aed;color:white;padding:20px;text-align:center;border-radius:8px 8px 0 0;">
            <h1>🗺️ Tripline</h1><h2>Password Reset Request</h2>
          </div>
          <div style="padding:24px;border:1px solid #e5e7eb;border-top:none;border-radius:0 0 8px 8px;">
            <p>Hi <strong>%s</strong>,</p>
            <p>Click below to reset your password. This link expires in 1 hour.</p>
            <a href="%s"
               style="display:inline-block;background:#7c3aed;color:white;padding:12px 24px;border-radius:6px;text-decoration:none;">
              Reset Password
            </a>
            <p style="margin-top:20px;color:#6b7280;font-size:12px;">
              Didn't request this? You can safely ignore this email.
            </p>
          </div>
        </body></html>
        """
        .formatted(toName, resetLink);

    sendEmail(toEmail, toName, subject, html, null);
  }

  // -------------------------------------------------------
  // Internal helper — calls Brevo v3 REST API
  // -------------------------------------------------------

  private void sendEmail(String toEmail, String toName, String subject, String htmlContent,
      List<Map<String, String>> attachments) {
    try {
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      headers.set("api-key", brevoApiKey);
      headers.setAccept(List.of(MediaType.APPLICATION_JSON));

      // Brevo request payload
      Map<String, Object> body = new java.util.HashMap<>(Map.of(
          "sender", Map.of("name", senderName, "email", senderEmail),
          "to", List.of(Map.of("email", toEmail, "name", toName)),
          "subject", subject,
          "htmlContent", htmlContent));

      if (attachments != null && !attachments.isEmpty()) {
        body.put("attachment", attachments);
      }

      HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
      ResponseEntity<String> response = restTemplate.postForEntity(BREVO_API_URL, request, String.class);

      if (response.getStatusCode().is2xxSuccessful()) {
        log.info("Email sent via Brevo API to {}: {}", toEmail, subject);
      } else {
        log.warn("Brevo API returned non-2xx for {}: {}", toEmail, response.getStatusCode());
      }
    } catch (Exception e) {
      log.error("Failed to send email via Brevo API to {}: {}", toEmail, e.getMessage(), e);
    }
  }
}
