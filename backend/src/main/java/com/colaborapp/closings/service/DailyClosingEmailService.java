package com.colaborapp.closings.service;

import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Service;

import com.colaborapp.closings.web.dto.DailyClosingResponse;
import com.colaborapp.mail.service.MailService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DailyClosingEmailService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private final MailService mailService;

    public void sendClosingSummary(String marketEmail, DailyClosingResponse response) {
        String subject = "Daily Closing - " + response.marketName() + " - " + DATE_FORMATTER.format(response.closingDate());
        StringBuilder body = new StringBuilder()
                .append("Market: ").append(response.marketName()).append(System.lineSeparator())
                .append("Closing date: ").append(DATE_FORMATTER.format(response.closingDate())).append(System.lineSeparator())
                .append("Total sales: ").append(response.totalSalesAmount()).append(System.lineSeparator())
                .append("Total commission: ").append(response.totalCommissionAmount()).append(System.lineSeparator())
                .append("Total net: ").append(response.totalNetAmount()).append(System.lineSeparator())
                .append("Sale count: ").append(response.saleCount()).append(System.lineSeparator())
                .append(System.lineSeparator())
                .append("Stores").append(System.lineSeparator());

        response.stores().forEach(store -> body
                .append("- ")
                .append(store.storeName())
                .append(": sales=")
                .append(store.totalSalesAmount())
                .append(", commission=")
                .append(store.totalCommissionAmount())
                .append(", net=")
                .append(store.totalNetAmount())
                .append(", saleCount=")
                .append(store.saleCount())
                .append(", items=")
                .append(store.totalItems())
                .append(System.lineSeparator()));

        mailService.send(marketEmail, subject, body.toString());
    }
}
