package com.Abdelwahab.RoomBooking.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.Abdelwahab.RoomBooking.model.RoomType;

@Repository
public interface RoomTypeRepository extends JpaRepository<RoomType, Long>{

    public List<RoomType> findByHotelId (Long hotelId);

}