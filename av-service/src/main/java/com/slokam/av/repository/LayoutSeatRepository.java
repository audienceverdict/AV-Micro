package com.slokam.av.repository;

import com.slokam.av.entity.LayoutSeat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LayoutSeatRepository
        extends JpaRepository<LayoutSeat, String>,
                org.springframework.data.jpa.repository.JpaSpecificationExecutor<LayoutSeat> {
    List<LayoutSeat> findByLayoutVersionIdOrderByRowNumberAscColumnNumberAsc(
            String layoutVersionId);

    @org.springframework.data.jpa.repository.Query("select s from LayoutSeat s where s.layoutVersionId=:layoutVersionId and coalesce(s.seatId,s.id) in :ids")
    List<LayoutSeat> findByLayoutVersionIdAndIdIn(
            @org.springframework.data.repository.query.Param("layoutVersionId") String layoutVersionId,
            @org.springframework.data.repository.query.Param("ids") java.util.Collection<String> ids);

    void deleteByLayoutVersionId(String layoutVersionId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(
            "delete from LayoutSeat s where s.layoutVersionId=:id")
    void deleteVersionSeats(@org.springframework.data.repository.query.Param("id") String id);
}
