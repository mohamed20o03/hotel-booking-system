package com.Abdelwahab.RoomBooking.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.Abdelwahab.RoomBooking.model.RoomType;

/**
 * Data-access layer for the {@link RoomType} aggregate — a sellable category of room
 * (for example "Standard King" or "Deluxe Suite") that groups physically
 * interchangeable rooms and anchors inventory and pricing.
 *
 * <p><strong>Aggregate role.</strong> The room type sits between the {@link
 * com.Abdelwahab.RoomBooking.model.Hotel} and the individual
 * {@link com.Abdelwahab.RoomBooking.model.Room}. Availability is tracked per room
 * type and per night through {@link com.Abdelwahab.RoomBooking.model.RoomTypeInventory},
 * and pricing is attached through its {@link com.Abdelwahab.RoomBooking.model.RatePlan}s;
 * this interface exposes the catalogue read side while inherited {@link JpaRepository}
 * operations cover CRUD.
 */
@Repository
public interface RoomTypeRepository extends JpaRepository<RoomType, Long>{

    /**
     * Lists the room types belonging to a hotel, navigating the {@code RoomType →
     * Hotel} association by hotel identifier. Backs catalogue display and the
     * availability search's enumeration of bookable categories.
     *
     * @param hotelId the identifier of the owning {@link com.Abdelwahab.RoomBooking.model.Hotel}.
     * @return the hotel's room types; an empty list if the hotel has none defined.
     *         Never {@code null}.
     */
    public List<RoomType> findByHotelId (Long hotelId);

}