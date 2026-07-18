package com.Abdelwahab.RoomBooking.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import com.Abdelwahab.RoomBooking.model.Guest;
import com.Abdelwahab.RoomBooking.model.Hotel;
import com.Abdelwahab.RoomBooking.model.Payment;
import com.Abdelwahab.RoomBooking.model.RatePlan;
import com.Abdelwahab.RoomBooking.model.Reservation;
import com.Abdelwahab.RoomBooking.model.ReservationStatus;
import com.Abdelwahab.RoomBooking.model.RoomType;

/**
 * Repository test for PaymentRepository.
 *
 * The method worth testing is sumSuccessfulPaymentsByReservationId — hand-written
 * JPQL with a SUM aggregate, a COALESCE(..., 0) for the no-rows case, and a
 * status = 'SUCCESS' filter. Every branch of that is a decision only a real DB
 * confirms. findByReservationId is a Spring-derived query, so it isn't tested.
 *
 * See RoomRepositoryTest for why @AutoConfigureTestDatabase(replace = NONE) is used.
 * Per-context DB isolation comes from the global ${random.uuid} datasource URL in
 * src/test/resources/application.properties.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
public class PaymentRepositoryTest {

    @Autowired private PaymentRepository paymentRepository;
    @Autowired private TestEntityManager em;

    // Static so unique fixture fields never collide (see RoomTypeInventoryRepositoryTest).
    private static final AtomicInteger SEQ = new AtomicInteger();

    private Reservation reservation;

    @BeforeEach
    public void setup() {
        reservation = persistReservation();
    }

    @Test
    public void sum_returnsZero_whenNoPaymentsExist() {
        // COALESCE must turn SUM's NULL (no rows) into 0, not blow up.
        BigDecimal total = paymentRepository.sumSuccessfulPaymentsByReservationId(reservation.getId());

        assertThat(total).isEqualByComparingTo("0");
    }

    @Test
    public void sum_addsOnlySuccessfulPayments() {
        persistPayment(reservation, "150.00", "SUCCESS");
        persistPayment(reservation, "100.00", "SUCCESS");

        BigDecimal total = paymentRepository.sumSuccessfulPaymentsByReservationId(reservation.getId());

        assertThat(total).isEqualByComparingTo("250.00");
    }

    @Test
    public void sum_ignoresNonSuccessfulStatuses() {
        persistPayment(reservation, "150.00", "SUCCESS");
        persistPayment(reservation, "999.00", "PENDING"); // must be excluded
        persistPayment(reservation, "999.00", "FAILED");  // must be excluded
        persistPayment(reservation, "999.00", "REFUNDED"); // must be excluded

        BigDecimal total = paymentRepository.sumSuccessfulPaymentsByReservationId(reservation.getId());

        assertThat(total).isEqualByComparingTo("150.00");
    }

    @Test
    public void sum_returnsZero_whenPaymentsExistButNoneSucceeded() {
        persistPayment(reservation, "150.00", "PENDING");
        persistPayment(reservation, "100.00", "FAILED");

        BigDecimal total = paymentRepository.sumSuccessfulPaymentsByReservationId(reservation.getId());

        assertThat(total).isEqualByComparingTo("0");
    }

    @Test
    public void sum_isScopedToTheGivenReservation() {
        persistPayment(reservation, "150.00", "SUCCESS");
        Reservation other = persistReservation();
        persistPayment(other, "500.00", "SUCCESS"); // different reservation, must not count

        BigDecimal total = paymentRepository.sumSuccessfulPaymentsByReservationId(reservation.getId());

        assertThat(total).isEqualByComparingTo("150.00");
    }

    // ── fixtures ──────────────────────────────────────────────────

    private Payment persistPayment(Reservation res, String amount, String status) {
        return em.persistAndFlush(Payment.builder()
                .reservation(res)
                .amount(new BigDecimal(amount))
                .currency("EGP")
                .type("FINAL_PAYMENT")
                .method("VISA")
                .provider("STRIPE")
                .transactionReference("txn-" + SEQ.incrementAndGet()) // unique
                .status(status)
                .build());
    }

    private Reservation persistReservation() {
        RoomType type = persistRoomType();
        return em.persistAndFlush(Reservation.builder()
                .guest(persistGuest())
                .ratePlan(persistRatePlan(type))
                .confirmationNumber("CONF-" + SEQ.incrementAndGet()) // unique
                .checkInDate(LocalDate.of(2026, 6, 10))
                .checkOutDate(LocalDate.of(2026, 6, 13))
                .numGuests(2)
                .totalPrice(new BigDecimal("300.00"))
                .status(ReservationStatus.CONFIRMED)
                .build());
    }

    private Hotel persistHotel() {
        int n = SEQ.incrementAndGet();
        return em.persistAndFlush(Hotel.builder()
                .name("Test Hotel " + n)
                .address("1 Test St")
                .city("Cairo")
                .country("Egypt")
                .phone("phone-" + n)                     // unique
                .email("hotel-" + n + "@test.example")   // unique
                .starRating(5)
                .build());
    }

    private RoomType persistRoomType() {
        int n = SEQ.incrementAndGet();
        return em.persistAndFlush(RoomType.builder()
                .hotel(persistHotel())
                .name("Type " + n)
                .maxOccupancy(2)
                .totalRooms(5)
                .basePricePerNight(new BigDecimal("100.00"))
                .currency("EGP")
                .build());
    }

    private RatePlan persistRatePlan(RoomType type) {
        int n = SEQ.incrementAndGet();
        return em.persistAndFlush(RatePlan.builder()
                .roomType(type)
                .name("Plan " + n)
                .minStayNights(1)
                .breakfastIncluded(false)
                .isRefundable(true)
                .build());
    }

    private Guest persistGuest() {
        int n = SEQ.incrementAndGet();
        return em.persistAndFlush(Guest.builder()
                .firstName("Test")
                .lastName("Guest")
                .email("guest-" + n + "@test.example") // unique
                .password("hash")
                .phone("+200000" + n)
                .nationality("EG")
                .documentType("PASSPORT")
                .documentNumber("DOC" + n)
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .build());
    }
}
