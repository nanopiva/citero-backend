package com.nanopiva.citero.repository;

import com.nanopiva.citero.entity.Business;
import com.nanopiva.citero.entity.BusinessSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BusinessScheduleRepository extends JpaRepository<BusinessSchedule, Long> {
    List<BusinessSchedule> findByBusiness(Business business);
}