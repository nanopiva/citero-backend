package com.nanopiva.citero.repository;

import com.nanopiva.citero.entity.Business;
import com.nanopiva.citero.entity.Service;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ServiceRepository extends JpaRepository<Service, Long> {
    List<Service> findByBusiness(Business business);
}