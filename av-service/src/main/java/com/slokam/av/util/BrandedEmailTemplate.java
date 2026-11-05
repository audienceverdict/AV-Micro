package com.slokam.av.util;

import com.slokam.av.entity.Booking;

public final class BrandedEmailTemplate {
    public record EmailContent(
            String plainText, String html, String inlinePosterDataUri, String inlineQrDataUri) {}

    private BrandedEmailTemplate() {}

    public static EmailContent otp(String code, long expiryMinutes) {
        String title = "Your sign-in code";
        String text =
                "Your Audience Verdict sign-in code is "
                        + code
                        + ". The code expires in "
                        + expiryMinutes
                        + " minutes. If you did not request it, you can ignore this message.";
        String body =
                "<div style=\"text-align:center;padding:2px 4px 8px\"><div"
                    + " style=\"display:inline-block;padding:7px 11px;border:1px solid"
                    + " #f2d5d0;border-radius:999px;background:#fff6f4;color:#c7463c;font-size:10px;font-weight:800;letter-spacing:1.8px\">SECURE"
                    + " SIGN-IN</div><h1 style=\"margin:18px 0"
                    + " 8px;font-size:27px;line-height:1.2;color:#201c22\">Your seat is"
                    + " waiting.</h1><p style=\"margin:0"
                    + " auto;max-width:390px;font-size:15px;line-height:1.65;color:#655f67\">Enter"
                    + " this one-time code on Audience Verdict to continue.</p><div"
                    + " style=\"margin:24px auto 18px;padding:18px"
                    + " 12px;text-align:center;background:#fff8f6;border:1px dashed"
                    + " #ff6655;border-radius:14px;max-width:340px\"><div"
                    + " style=\"margin-bottom:9px;font-size:10px;letter-spacing:1.7px;font-weight:800;color:#877c83\">YOUR"
                    + " VERIFICATION CODE</div><strong class=\"otp-code\""
                    + " style=\"display:block;font-family:Arial,Helvetica,sans-serif;font-size:34px;line-height:1.2;font-weight:800;letter-spacing:9px;color:#c7463c\">"
                        + escape(code)
                        + "</strong></div><p"
                        + " style=\"margin:0;font-size:13px;line-height:1.65;color:#655f67\">This"
                        + " code expires in <strong>"
                        + expiryMinutes
                        + " minutes</strong>.</p><div style=\"margin:22px auto 0;padding:13px"
                        + " 15px;max-width:390px;text-align:left;background:#f8f6f8;border-radius:10px;color:#77717a;font-size:12px;line-height:1.6\"><strong"
                        + " style=\"color:#49444b\">Didn’t request a code?</strong><br/>You can"
                        + " safely ignore this email. Never share your code with"
                        + " anyone.</div></div>";
        return content(title, text, body, null, "");
    }

    public static EmailContent message(String title, String text, String posterUrl) {
        String[] paragraphs = text.split("\\R", -1);
        StringBuilder body =
                new StringBuilder("<h1 style=\"margin:0 0 16px;font-size:24px;color:#201c22\">")
                        .append(escape(title))
                        .append("</h1>");
        for (String paragraph : paragraphs) {
            if (!paragraph.isBlank())
                body.append(
                                "<p style=\"margin:0 0"
                                        + " 12px;font-size:15px;line-height:1.65;color:#49444b\">")
                        .append(escape(paragraph))
                        .append("</p>");
        }
        return content(title, text, body.toString(), posterUrl, "movie-poster");
    }

    public static EmailContent ticket(String name, Booking booking, String posterUrl) {
        return ticket(name, booking, posterUrl, booking.seatIds, java.util.List.of());
    }

    public static EmailContent ticket(String name, Booking booking, String posterUrl,
            java.util.List<String> seats, java.util.List<String> cancelledSeats) {
        return ticket(name, booking, posterUrl, seats, cancelledSeats, null);
    }

    public static EmailContent ticket(String name, Booking booking, String posterUrl,
            java.util.List<String> seats, java.util.List<String> cancelledSeats,
            String qrDataUri) {
        String state = booking.status == null ? "BOOKING" : booking.status.name();
        String headline =
                switch (state) {
                    case "CONFIRMED" -> "Your movie ticket is confirmed";
                    case "CANCELLED" -> "Your booking was cancelled";
                    default -> "Your booking is received";
                };
        if (!cancelledSeats.isEmpty()) headline = "Your booking cancellation update";
        String plain =
                "Hi "
                        + safe(name)
                        + ", "
                        + headline.toLowerCase()
                        + ". Movie: "
                        + booking.movie
                        + ". Venue: "
                        + booking.theatre
                        + ". Screen: "
                        + booking.screen
                        + ". Show: "
                        + booking.date
                        + " at "
                        + booking.time
                        + ". Seats: "
                        + String.join(", ", seats)
                        + ". Tickets: "
                        + booking.ticketCount
                        + ". Booking: "
                        + booking.id
                        + ". Ticket code: "
                        + booking.ticketCode;
        String statusColor =
                state.equals("CONFIRMED")
                        ? "#27835f"
                        : state.equals("CANCELLED") ? "#a94040" : "#a36c14";
        String body =
                "<div style=\"padding:0 0 10px\"><div"
                        + " style=\"font-size:11px;letter-spacing:1.8px;font-weight:800;color:"
                        + statusColor
                        + "\">"
                        + escape(state.replace('_', ' '))
                        + "</div>"
                        + "<h1 style=\"margin:8px 0 5px;font-size:24px;color:#201c22\">"
                        + escape(headline)
                        + "</h1>"
                        + "<p style=\"margin:0 0 18px;color:#655f67\">Hi "
                        + escape(safe(name))
                        + ", your seat in the conversation is waiting.</p><div"
                        + " style=\"border-top:1px dashed #c9c1c8;padding-top:16px\"><div"
                        + " style=\"font-size:19px;font-weight:800;color:#241e27\">"
                        + escape(booking.movie)
                        + "</div>"
                        + row("Venue", booking.theatre)
                        + row("Screen", booking.screen)
                        + row("Showtime", booking.date + " · " + booking.time)
                        + row("Seats", String.join(", ", seats))
                        + row("Tickets", Integer.toString(booking.ticketCount))
                        + row(
                                "Amount",
                                booking.totalAmount == null
                                        ? "0"
                                        : booking.totalAmount.toPlainString())
                        + row("Booking ID", booking.id)
                        + "<div style=\"margin:18px 0"
                        + " 8px;padding:14px;text-align:center;background:#fff5f2;border:1px dashed"
                        + " #ff6655;border-radius:10px\"><div"
                        + " style=\"font-size:10px;letter-spacing:1.4px;color:#776b73\">TICKET"
                        + " CODE</div><strong"
                        + " style=\"display:block;margin-top:5px;font-size:20px;letter-spacing:2px;color:#c7463c\">"
                        + escape(booking.ticketCode)
                        + "</strong></div></div></div>";
        if (qrDataUri != null && qrDataUri.matches("(?is)^data:image/png;base64,[a-z0-9+/=\\r\\n]+$")) {
            body +=
                    "<div style=\"margin:16px 0;text-align:center\"><img"
                            + " src=\"cid:ticket-qr\" alt=\"Booking ticket QR code\" width=\"220\""
                            + " height=\"220\" style=\"display:inline-block;width:100%;max-width:220px;height:auto;border:0\"/>"
                            + "<div style=\"font-size:12px;color:#655f67\">Present this QR code at the theater.</div></div>";
        }
        if (booking.cancellationReason != null && !booking.cancellationReason.isBlank())
            body +=
                    "<p style=\"margin:16px 0;color:#655f67\">Cancellation note: "
                            + escape(booking.cancellationReason)
                            + "</p>";
        if (!cancelledSeats.isEmpty()) {
            plain += ". Cancelled seats: " + String.join(", ", cancelledSeats);
            body += row("Cancelled seats", String.join(", ", cancelledSeats));
        }
        return content(headline, plain, body, posterUrl, "movie-poster", qrDataUri);
    }

    private static String row(String label, String value) {
        return "<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\""
                + " style=\"width:100%;table-layout:fixed;border-collapse:collapse\"><tr>"
                + "<td class=\"detail-label\" width=\"32%\" valign=\"top\""
                + " style=\"width:32%;padding:9px 12px 9px 0;border-bottom:1px solid #eee9ee;"
                + "font-size:13px;line-height:1.6;color:#77717a;word-wrap:break-word\">"
                + escape(label)
                + "</td><td class=\"detail-value\" width=\"68%\" align=\"right\" valign=\"top\""
                + " style=\"width:68%;padding:9px 0;border-bottom:1px solid #eee9ee;"
                + "font-size:13px;line-height:1.6;font-weight:bold;color:#28232a;"
                + "overflow-wrap:anywhere;word-wrap:break-word;word-break:break-word\">"
                + escape(value == null ? "" : value)
                + "</td></tr></table>";
    }

    private static EmailContent content(
            String title, String plain, String body, String posterUrl, String contentId) {
        return content(title, plain, body, posterUrl, contentId, null);
    }

    private static EmailContent content(
            String title, String plain, String body, String posterUrl, String contentId,
            String inlineQrDataUri) {
        String poster = safePoster(posterUrl);
        String inline = poster.startsWith("data:image/") ? poster : null;
        String source = inline == null ? poster : "cid:" + contentId;
        String posterHtml =
                source.isBlank()
                        ? ""
                        : "<img src=\""
                                + escape(source)
                                + "\" alt=\"Movie poster\" width=\"556\""
                                + " style=\"display:block;width:100%;max-width:100%;height:auto;border:0;border-radius:10px\"/>";
        String html =
                "<!doctype html><html lang=\"en\"><head>"
                    + "<meta charset=\"UTF-8\"/><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"/>"
                    + "<meta name=\"x-apple-disable-message-reformatting\"/><title>" + escape(title) + "</title>"
                    + "<style>table{mso-table-lspace:0pt;mso-table-rspace:0pt}"
                    + "@media only screen and (max-width:480px){"
                    + ".email-outer{padding:12px 8px!important}"
                    + ".email-header,.email-content,.email-footer{padding:18px 16px!important}"
                    + ".email-poster{padding:16px 16px 0!important}"
                    + "h1{font-size:23px!important;line-height:1.3!important}"
                    + ".otp-code{font-size:28px!important;letter-spacing:5px!important}"
                    + ".detail-label,.detail-value{display:block!important;width:100%!important;box-sizing:border-box!important;text-align:left!important}"
                    + ".detail-label{padding:10px 0 0!important;border-bottom:0!important}"
                    + ".detail-value{padding:2px 0 10px!important}}"
                    + "</style></head><body"
                    + " style=\"margin:0;padding:0;background:#f1eff2;font-family:Arial,Helvetica,sans-serif;-webkit-text-size-adjust:100%;-ms-text-size-adjust:100%\"><table"
                    + " role=\"presentation\" width=\"100%\" cellspacing=\"0\""
                    + " cellpadding=\"0\"><tr><td class=\"email-outer\" align=\"center\" style=\"padding:24px 12px\">"
                    + "<!--[if mso]><table role=\"presentation\" width=\"600\" align=\"center\"><tr><td><![endif]-->"
                    + "<table role=\"presentation\""
                    + " width=\"100%\" style=\"max-width:600px;width:100%;table-layout:fixed;background:#fff;border:1px"
                    + " solid #e7e1e8;border-radius:16px;overflow:hidden\" cellspacing=\"0\""
                    + " cellpadding=\"0\"><tr><td class=\"email-header\" style=\"padding:18px"
                    + " 22px;background:#17151a;color:#fff\"><table role=\"presentation\""
                    + " cellspacing=\"0\" cellpadding=\"0\"><tr><td"
                    + " style=\"width:48px;height:48px;vertical-align:middle\"><img"
                    + " src=\"cid:audience-verdict-logo\" alt=\"Audience Verdict\" width=\"48\" height=\"48\""
                    + " style=\"display:block;width:48px;height:48px;border:0\"/></td><td"
                    + " style=\"padding-left:12px;font-size:12px;line-height:1.45;letter-spacing:1.6px;font-weight:800\">AUDIENCE<br/>VERDICT.</td></tr></table></td></tr>"
                        + (posterHtml.isBlank()
                                ? ""
                                : "<tr><td class=\"email-poster\" style=\"padding:18px 22px 0\">"
                                        + posterHtml
                                        + "</td></tr>")
                        + "<tr><td class=\"email-content\" style=\"padding:24px 22px 28px;text-align:left;line-height:1.6;overflow-wrap:anywhere;word-wrap:break-word;word-break:break-word\">"
                        + body
                        + "</td></tr><tr><td class=\"email-footer\" style=\"padding:16px"
                        + " 22px;background:#f8f6f8;color:#77717a;font-size:11px;line-height:1.6\">Made"
                        + " for the love of cinema.<br/>Audience Verdict · This is an automated"
                        + " email.</td></tr></table><!--[if mso]></td></tr></table><![endif]--></td></tr></table></body></html>";
        return new EmailContent(plain, html, inline, inlineQrDataUri);
    }

    private static String safePoster(String value) {
        if (value == null) return "";
        if (value.matches("(?is)^data:image/(png|jpeg|webp);base64,[a-z0-9+/=\\r\\n]+$"))
            return value;
        if (value.matches("(?i)^https://[^\\s\"'<>]+$")) return value;
        return "";
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "there" : value.trim();
    }

    private static String escape(String value) {
        return value == null
                ? ""
                : value.replace("&", "&amp;")
                        .replace("<", "&lt;")
                        .replace(">", "&gt;")
                        .replace("\"", "&quot;")
                        .replace("'", "&#39;");
    }
}
