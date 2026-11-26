package com.example.paintquality.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<InspectorUser, Long> {

    Optional<InspectorUser> findByInspectorNameAndArea(String inspectorName, String area);

    List<InspectorUser> findAllByInspectorName(String inspectorName);

    Optional<InspectorUser> findByRole(Role role);

    boolean existsByInspectorNameAndArea(String inspectorName, String area);

    boolean existsByInspectorNameAndAreaAndIdNot(String inspectorName, String area, Long id);

    long countByRole(Role role);

    List<InspectorUser> findAllByOrderByIdAsc();
}
