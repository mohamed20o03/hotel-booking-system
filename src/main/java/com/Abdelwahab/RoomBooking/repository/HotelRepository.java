package com.Abdelwahab.RoomBooking.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.Abdelwahab.RoomBooking.model.Hotel;

/**
 * Data-access layer for the {@link Hotel} aggregate — the top-level property that
 * owns room types, rooms, rate plans, and add-ons.
 *
 * <p><strong>Aggregate role.</strong> The hotel is the ownership root of the
 * catalogue side of the domain; most other aggregates reach it transitively (a room
 * type belongs to a hotel, a room to a room type, and so on). This interface declares
 * no custom finders: identity-based CRUD from {@link JpaRepository} is sufficient,
 * as hotels are located by primary key or listed in full.
 *
 * <p><strong>Uniqueness assumptions.</strong> The {@code hotel} table enforces
 * {@code unique} constraints on both {@code phone} and {@code email}. No lookup keyed
 * on those columns is exposed here, but persisting a hotel with a duplicate phone or
 * email will be rejected by the database through the inherited {@code save} operation.
 */
@Repository
public interface HotelRepository extends JpaRepository<Hotel, Long>{

}
