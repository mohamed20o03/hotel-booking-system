package com.Abdelwahab.RoomBooking.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A property in the hotel chain and the top of the inventory ownership tree.
 *
 * <p><strong>Domain concept.</strong> Maps to the {@code hotel} table. A hotel is the
 * organizational root every sellable and priced artifact hangs off of: it owns its
 * {@link RoomType}s, {@link Addon} catalogue, and (transitively) its physical
 * {@link Room}s, rate plans, and inventory. Each hotel manages its own room types,
 * pricing, and extras independently of every other property.
 *
 * <p><strong>Relationships.</strong> The owning (parent) side of these associations is
 * modelled on the child entities as {@code @ManyToOne} back-references
 * ({@code RoomType.hotel}, {@code Addon.hotel}); this class holds no inverse collections.
 *
 * <p><strong>Invariants.</strong> {@link #phone} and {@link #email} are each
 * <strong>unique</strong> across all hotels, so a property cannot be registered twice
 * under the same contact details.
 */
@Entity
@Table(name = "hotel")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Hotel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 200)
    private String address;

    @Column(nullable = false, length = 50)
    private String city;

    @Column(nullable = false, length = 100)
    private String country;

    /** Contact phone number; <strong>unique</strong> across all hotels. */
    @Column(nullable = false, length = 50, unique = true)
    private String phone;

    /** Contact email; <strong>unique</strong> across all hotels. */
    @Column(nullable = false, length = 100, unique = true)
    private String email;

    @Column(name = "star_rating", nullable = false)
    private Integer starRating;

    /** IANA timezone identifier (e.g. {@code Africa/Cairo}) used to resolve the property's local calendar dates. */
    @Column(length = 20)
    private String timezone;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

}
