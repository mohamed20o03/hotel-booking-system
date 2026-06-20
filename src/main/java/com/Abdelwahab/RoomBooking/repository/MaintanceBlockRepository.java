package com.Abdelwahab.RoomBooking.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.Abdelwahab.RoomBooking.model.MaintenanceBlock;

@Repository
public interface MaintanceBlockRepository extends JpaRepository<MaintenanceBlock, Long>{
    
}
