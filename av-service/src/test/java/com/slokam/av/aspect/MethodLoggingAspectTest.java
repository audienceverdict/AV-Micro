package com.slokam.av.aspect;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.slokam.av.service.PaymentService;
import com.slokam.av.repository.PaymentRepository;
import com.slokam.av.repository.BookingRepository;
import com.slokam.av.entity.Payment;
import com.slokam.av.exception.custom.ApiException;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MethodLoggingAspectTest {
    @Test
    void tracesInheritedRepositoryMethods() {
        var repository = mock(PaymentRepository.class);
        when(repository.findById("private-id")).thenReturn(Optional.empty());
        var factory = new AspectJProxyFactory(repository);
        factory.setInterfaces(PaymentRepository.class);
        factory.addAspect(new MethodLoggingAspect());
        PaymentRepository proxy = factory.getProxy();
        Logger logger = (Logger) LoggerFactory.getLogger("com.slokam.av.repository");
        Level previous = logger.getLevel();
        var events = new ListAppender<ILoggingEvent>();
        events.start();
        logger.addAppender(events);
        logger.setLevel(Level.TRACE);
        try {
            assertThat(proxy.findById("private-id")).isEmpty();
            assertThat(events.list).hasSize(2);
            assertThat(events.list.get(0).getFormattedMessage()).startsWith("Start ").doesNotContain("private-id");
            assertThat(events.list.get(1).getFormattedMessage()).contains("outcome=success");
        } finally {
            logger.setLevel(previous);
            logger.detachAppender(events);
            events.stop();
        }
    }

    @Test
    void tracesSuccessAndFailureThroughProxyWithoutLoggingPayloads() {
        var payments = mock(PaymentRepository.class);
        var payment = new Payment();
        when(payments.findByBookingId("sensitive-booking-id"))
                .thenReturn(Optional.of(payment)).thenReturn(Optional.empty());
        var factory = new AspectJProxyFactory(new PaymentService(payments, mock(BookingRepository.class)));
        factory.addAspect(new MethodLoggingAspect());
        PaymentService proxy = factory.getProxy();
        Logger logger = (Logger) LoggerFactory.getLogger(PaymentService.class);
        Level previous = logger.getLevel();
        var events = new ListAppender<ILoggingEvent>();
        events.start();
        logger.addAppender(events);
        logger.setLevel(Level.TRACE);
        try {
            assertThat(proxy.getForBooking("sensitive-booking-id")).isSameAs(payment);
            assertThatThrownBy(() -> proxy.getForBooking("sensitive-booking-id"))
                    .isInstanceOf(ApiException.class);
            var trace = events.list.stream().filter(e -> e.getLevel() == Level.TRACE)
                    .map(ILoggingEvent::getFormattedMessage).toList();
            assertThat(trace).hasSize(4);
            assertThat(trace.get(0)).startsWith("Start PaymentService.getForBooking(");
            assertThat(trace.get(1)).contains("outcome=success", "durationMs=");
            assertThat(trace.get(3)).contains("outcome=failure:ApiException", "durationMs=");
            assertThat(trace).allSatisfy(message -> assertThat(message).doesNotContain("sensitive-booking-id"));
            logger.setLevel(Level.INFO);
            events.list.clear();
            when(payments.findByBookingId("sensitive-booking-id")).thenReturn(Optional.of(payment));
            assertThat(proxy.getForBooking("sensitive-booking-id")).isSameAs(payment);
            assertThat(events.list).isEmpty();
        } finally {
            logger.setLevel(previous);
            logger.detachAppender(events);
            events.stop();
        }
    }
}
