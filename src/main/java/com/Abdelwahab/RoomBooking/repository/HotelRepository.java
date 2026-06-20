package com.Abdelwahab.RoomBooking.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.Abdelwahab.RoomBooking.model.Hotel;

@Repository
public interface HotelRepository extends JpaRepository<Hotel, Long>{

}
