package com.limiteddrop.persistence;

import com.limiteddrop.domain.Drop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DropRepository extends JpaRepository<Drop, String> {
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Drop d set d.availableUnits = d.availableUnits - :quantity where d.id = :dropId and d.availableUnits >= :quantity and d.opensAt <= CURRENT_TIMESTAMP")
    int reserveIfAvailable(@Param("dropId") String dropId, @Param("quantity") int quantity);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Drop d set d.availableUnits = d.availableUnits + :quantity where d.id = :dropId")
    int returnUnits(@Param("dropId") String dropId, @Param("quantity") int quantity);
}
