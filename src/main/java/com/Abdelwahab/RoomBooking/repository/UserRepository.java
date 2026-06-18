package com.Abdelwahab.RoomBooking.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.Abdelwahab.RoomBooking.model.User;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
}
